package com.fitnesspro.core.data.workout

import androidx.room.withTransaction
import com.fitnesspro.core.data.mapper.mediaRefOf
import com.fitnesspro.core.data.mapper.toEnum
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.PersonalRecordDao
import com.fitnesspro.core.database.dao.PlanDao
import com.fitnesspro.core.database.dao.WorkoutDao
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.PersonalRecordEntity
import com.fitnesspro.core.database.entity.WorkoutExerciseEntity
import com.fitnesspro.core.database.entity.WorkoutSessionEntity
import com.fitnesspro.core.database.entity.WorkoutSessionStateEntity
import com.fitnesspro.core.database.entity.WorkoutSetEntity
import com.fitnesspro.core.domain.calculator.PersonalRecordDetector
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.domain.calculator.AdvancedCalorieEstimator
import com.fitnesspro.core.domain.calculator.VolumeCalculator
import com.fitnesspro.core.domain.repository.BodyRepository
import com.fitnesspro.core.domain.timer.RestTimerGateway
import com.fitnesspro.core.domain.timer.RestTimerReconciler
import com.fitnesspro.core.domain.workout.DraftRecoveryPolicy
import com.fitnesspro.core.domain.workout.WorkoutDraft
import com.fitnesspro.core.model.ActiveExercise
import com.fitnesspro.core.model.ActiveSet
import com.fitnesspro.core.model.ActiveWorkout
import com.fitnesspro.core.model.ExerciseSnapshot
import com.fitnesspro.core.model.ExerciseStatus
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.EstimationLevel
import com.fitnesspro.core.model.IntensityZone
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PrHit
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.RestState
import com.fitnesspro.core.model.SessionDetail
import com.fitnesspro.core.model.SessionExerciseDetail
import com.fitnesspro.core.model.SessionSetDetail
import com.fitnesspro.core.model.SessionStatus
import com.fitnesspro.core.model.SummaryExercise
import com.fitnesspro.core.model.SetLogResult
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.UnitConversion
import com.fitnesspro.core.model.WeightUnit
import com.fitnesspro.core.model.WorkoutSummary
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 开始训练时的数据填充模式（PRD FR-10）。
 *
 * 曾有第三个值 `BLANK`（不带入任何数据），但它从未被实现：
 * 填充逻辑会直接落到"按计划处方"的分支，行为与 PLAN_TARGETS 完全相同。
 * 一个名字与行为不符的死值是埋雷 —— 谁将来传了 BLANK 都不会报错，
 * 只会得到错误的填充结果。已删除；将来需要时先实现再提供。
 */
enum class PrefillMode { LAST_SESSION, PLAN_TARGETS, BLANK }

data class LogSetInput(
    val workoutExerciseId: Long,
    val setIndex: Int,
    val setType: SetType = SetType.NORMAL,
    /** 用户输入的重量（原始单位）；内部统一换算为 kg 存储 */
    val weightInput: Double? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val leftReps: Int? = null,
    val rightReps: Int? = null,
    val rpe: Double? = null,
    val notes: String? = null,
)

data class SummaryInput(
    val completedFully: Boolean = true,
    val notes: String? = null,
    val perceivedEffort: Int? = null,
    val mood: String? = null,
)

/**
 * 训练执行引擎 —— 全项目风险最高的模块（PRD M4-2 / 风险 R3）。
 *
 * 三条不变量：
 *  1. **完成一组 = 一次数据库事务**。不存在"训练结束才批量保存"，因此任何时刻杀进程，
 *     最多丢失"正在输入但未提交"的那一组。
 *  2. **正确性不依赖任何后台机制**。倒计时永远由持久化的 endsAtEpochMs 计算；
 *     通知丢失、闹钟延迟、进程被杀都只影响"提示"，不影响"数据"。
 *  3. **所有写操作串行化**。用户可能连点两次"完成一组"，Mutex 保证光标推进与 PR 判定无竞态。
 */
@Singleton
class WorkoutSessionManager @Inject constructor(
    private val database: FitnessDatabase,
    private val workoutDao: WorkoutDao,
    private val planDao: PlanDao,
    private val exerciseDao: ExerciseDao,
    private val prDao: PersonalRecordDao,
    private val settings: SettingsRepository,
    private val bodyRepository: BodyRepository,
    private val prDetector: PersonalRecordDetector,
    private val restTimerGateway: RestTimerGateway,
    private val time: TimeProvider,
    @Named("workoutWriteMutex") private val writeMutex: Mutex,
) {

    // ==================================================================
    // 读取
    // ==================================================================

    /**
     * 按 sessionId 观察单个训练。
     * 多个会话并行时，训练页必须只看自己的会话 —— 全局 observeActive()
     * 永远返回"最新的一条"，会导致 A 会话的页面渲染出 B 会话的数据。
     */
    fun observeWorkout(sessionId: Long): Flow<ActiveWorkout?> =
        workoutDao.observeSession(sessionId).flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    workoutDao.observeExercises(sessionId),
                    workoutDao.observeState(sessionId),
                    // 第三个流只用于失效联动："加一组"只写 workout_set 表，
                    // 上面两个被观察的表都没变，不并入它 UI 就刷新不出新加的组
                    workoutDao.observeSetCount(sessionId),
                ) { exercises, state, _ -> exercises to state }
                    .flatMapLatest { (exercises, state) ->
                        // 休息是否结束取决于墙钟与 endsAtEpochMs 的比较，而不是数据库变更：
                        // 休息期间没有任何写入，没有这个节拍，"休息结束"提示条要等到
                        // 下一次数据库写入才会出现（用户感知为"倒计时结束后很久才提示"）
                        if (state?.restTimerEndsAtEpochMs != null || state?.restTimerPausedRemainingMs != null) {
                            combine(flowOf(exercises to state), restTick()) { pair, _ -> pair }
                        } else {
                            flowOf(exercises to state)
                        }
                    }
                    .map { (exercises, state) -> buildActiveWorkout(session, exercises, state) }
            }
        }

    /** 休息进行中的时钟节拍：每秒重估一次休息状态，让"休息结束"准时出现 */
    private fun restTick(intervalMs: Long = 1_000L): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }

    private suspend fun buildActiveWorkout(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutExerciseEntity>,
        state: WorkoutSessionStateEntity?,
    ): ActiveWorkout {
        // 部位用于训练页"按部位分区"，自重标定用于"附加负重"输入提示：
        // 一次批量查询，避免逐条回查动作库
        val defs = exercises
            .mapNotNull { it.exerciseDefinitionId }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { ids -> exerciseDao.getByIds(ids).associateBy { it.id } }
            .orEmpty()
        val muscles = defs.mapValues { (_, def) -> def.primaryMuscle.toEnum(MuscleGroup.OTHER) }

        val activeExercises = exercises.map { entity ->
            val type = entity.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS)
            val sets = workoutDao.getSets(entity.id)
            val definitionId = entity.exerciseDefinitionId

            // 只在该动作还没有任何成绩时去查上次成绩：一旦开始了，回显就失去意义，
            // 也避免每次落库都触发历史查询。
            val lastSets = if (definitionId == null || sets.any { it.status != SetStatus.PENDING.name }) {
                emptyList()
            } else {
                loadLastSessionRecords(definitionId, type)
            }

            ActiveExercise(
                id = entity.id,
                exercise = ExerciseSnapshot(
                    definitionId = definitionId,
                    name = entity.nameSnapshot,
                    type = type,
                    isUnilateral = false,
                    isBodyweight = definitionId?.let { defs[it]?.isBodyweight } ?: false,
                    selfWeightCoefficient = definitionId?.let { defs[it]?.selfWeightCoefficient } ?: 0.5,
                    primaryMuscle = definitionId?.let { muscles[it] },
                ),
                orderIndex = entity.orderIndex,
                supersetTag = entity.supersetTag,
                restSeconds = entity.restSeconds,
                notes = entity.notes,
                status = entity.status.toEnum(ExerciseStatus.PENDING),
                media = mediaRefOf(entity.mediaType, entity.mediaPath, entity.thumbnailPath),
                isAdhoc = entity.isAdhoc,
                sets = sets.map { it.toActiveSet() },
                lastSessionSets = lastSets,
            )
        }

        return ActiveWorkout(
            sessionId = session.id,
            planId = session.planId,
            planName = session.planNameSnapshot,
            startedAt = session.startedAt,
            status = session.status.toEnum(SessionStatus.IN_PROGRESS),
            pausedTotalSeconds = session.pausedTotalSeconds,
            isPaused = session.activeSegmentStartedAt == null && session.completedAt == null,
            bodyWeightKg = session.bodyWeightKgSnapshot,
            exercises = activeExercises,
            currentExerciseIndex = state?.currentExerciseIndex ?: 0,
            currentSetIndex = state?.currentSetIndex ?: 0,
            rest = restoreRestState(state),
            estimatedCalories = session.estimatedCalories,
            caloriesAccruedAtSeconds = session.calorieElapsedBaseSeconds,
        )
    }

    // ==================================================================
    // 卡路里估算（kcal-develp.md 分组计算方案 A：逐组累加 + 实时跳字）
    // ==================================================================

    /**
     * 汇总会话内各动作的完成工作量（结束训练时算 EPOC 用）。
     * 结合动作字典的能耗标定（基础 MET / 自重系数 / 器械效率 / 复合度 /
     * 节奏 / 强度区）；动作定义缺失（已删除/自定义无标定）时逐字段回落保守默认值。
     */
    private suspend fun buildCalorieWork(
        sessionId: Long,
        allSets: List<WorkoutSetEntity>,
    ): List<AdvancedCalorieEstimator.ExerciseWork> {
        val exercises = workoutDao.getExercises(sessionId)
        val defIds = exercises.mapNotNull { it.exerciseDefinitionId }.distinct()
        val defs = if (defIds.isEmpty()) {
            emptyMap()
        } else {
            exerciseDao.getByIds(defIds).associateBy { it.id }
        }
        return exercises.mapNotNull { ex ->
            buildWorkForExercise(ex, defs[ex.exerciseDefinitionId], allSets)
        }
    }

    /** 单个动作的工作量（结束训练时汇总 EPOC 用） */
    private fun buildWorkForExercise(
        exercise: WorkoutExerciseEntity,
        definition: ExerciseDefinitionEntity?,
        allSets: List<WorkoutSetEntity>,
    ): AdvancedCalorieEstimator.ExerciseWork? {
        val completed = allSets.filter {
            it.workoutExerciseId == exercise.id && it.status == SetStatus.COMPLETED.name
        }
        if (completed.isEmpty()) return null
        val reps = completed.sumOf { (it.reps ?: 0) + (it.leftReps ?: 0) + (it.rightReps ?: 0) }
        return AdvancedCalorieEstimator.ExerciseWork.fromCalibration(
            metValue = definition?.metValue,
            isBodyweight = definition?.isBodyweight,
            selfWeightCoefficient = definition?.selfWeightCoefficient,
            equipmentEfficiency = definition?.equipmentEfficiency,
            isCompound = definition?.isCompound,
            // 单双侧：训练快照未存该标志，取动作库当前值
            isUnilateral = definition?.isUnilateral ?: false,
            tempoFactor = definition?.tempoFactor,
            intensityZone = definition?.intensityZone?.let { IntensityZone.fromNameOrNull(it) },
            averageRepDurationSeconds = definition?.averageRepDurationSeconds,
            completedSets = completed.size,
            totalReps = reps,
            externalWeightKg = completed.maxOfOrNull { it.weightKg ?: 0.0 } ?: 0.0,
        )
    }

    /**
     * 刚完成这一组的工作量（逐组累加的估算输入）。
     * 只包含本组的次数与配重——每组完成时只算这一组的消耗。
     */
    private fun buildWorkForSingleSet(
        exercise: WorkoutExerciseEntity,
        definition: ExerciseDefinitionEntity?,
        weightKg: Double?,
        reps: Int,
    ): AdvancedCalorieEstimator.ExerciseWork =
        AdvancedCalorieEstimator.ExerciseWork.fromCalibration(
            metValue = definition?.metValue,
            isBodyweight = definition?.isBodyweight,
            selfWeightCoefficient = definition?.selfWeightCoefficient,
            equipmentEfficiency = definition?.equipmentEfficiency,
            isCompound = definition?.isCompound,
            isUnilateral = definition?.isUnilateral ?: false,
            tempoFactor = definition?.tempoFactor,
            intensityZone = definition?.intensityZone?.let { IntensityZone.fromNameOrNull(it) },
            averageRepDurationSeconds = definition?.averageRepDurationSeconds,
            completedSets = 1,
            totalReps = reps,
            externalWeightKg = weightKg ?: 0.0,
        )

    /**
     * 估算用身体数据快照：体重优先取会话快照（训练后体重变化不影响本次估算），
     * 身高/体成分取最近一条身体记录，年龄取设置。
     */
    private suspend fun buildEstimatorProfile(
        session: WorkoutSessionEntity,
    ): AdvancedCalorieEstimator.EstimatorProfile {
        val latestBody = runCatching { bodyRepository.observeAll().first().firstOrNull() }.getOrNull()
        val userSettings = settings.current()
        return AdvancedCalorieEstimator.EstimatorProfile(
            weightKg = session.bodyWeightKgSnapshot ?: latestBody?.weightKg,
            heightCm = latestBody?.heightCm,
            age = userSettings.age,
            bodyFatPercent = latestBody?.bodyFatPercent,
            skeletalMuscleKg = latestBody?.muscleMassKg,
        )
    }

    private suspend fun loadLastSessionRecords(
        exerciseDefinitionId: Long,
        type: ExerciseType,
    ): List<SetRecord> = runCatching {
        workoutDao.previousSessionSets(exerciseDefinitionId).map { row ->
            SetRecord(
                exerciseType = type,
                setType = row.setType.toEnum(SetType.NORMAL),
                status = SetStatus.COMPLETED,
                weightKg = row.weightKg,
                reps = row.reps,
                durationSeconds = row.durationSeconds,
                distanceMeters = row.distanceMeters,
            )
        }
    }.getOrDefault(emptyList())

    private fun WorkoutSetEntity.toActiveSet(): ActiveSet = ActiveSet(
        id = id,
        setIndex = setIndex,
        setType = setType.toEnum(SetType.NORMAL),
        status = status.toEnum(SetStatus.PENDING),
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        leftReps = leftReps,
        rightReps = rightReps,
        rpe = rpe,
        isPersonalRecord = isPersonalRecord,
        prType = prType?.let { raw -> PrType.entries.firstOrNull { it.name == raw } },
    )

    private fun WorkoutSetEntity.toRecord(type: ExerciseType): SetRecord = SetRecord(
        exerciseType = type,
        setType = setType.toEnum(SetType.NORMAL),
        status = status.toEnum(SetStatus.PENDING),
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        leftReps = leftReps,
        rightReps = rightReps,
    )

    // ==================================================================
    // 开始训练
    // ==================================================================

    suspend fun startWorkout(
        planId: Long?,
        planNameFallback: String = "自由训练",
        mode: PrefillMode = PrefillMode.LAST_SESSION,
    ): Outcome<Long> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val settingsSnapshot = settings.current()
            val bodyWeight = bodyRepository.latestWeightKg()

            restTimerGateway.cancel()

            database.withTransaction {
                // 允许同时存在多个未完成训练：不再静默放弃已有会话。
                // 旧逻辑开新训练时把旧会话直接打成"已放弃"，用户没练完的进度
                // 会从首页消失（用户以为恢复回收站记录把它弄丢了）。
                // 各会话在首页"继续训练"列表并存，互不影响。

                val plan = planId?.let { planDao.getPlan(it) }
                val sessionId = workoutDao.insertSession(
                    WorkoutSessionEntity(
                        planId = planId,
                        planNameSnapshot = plan?.name ?: planNameFallback,
                        status = SessionStatus.IN_PROGRESS.name,
                        startedAt = now,
                        bodyWeightKgSnapshot = bodyWeight,
                        activeSegmentStartedAt = now,
                        createdAt = now,
                        updatedAt = now,
                    )
                )

                if (planId != null) {
                    seedExercisesFromPlan(
                        sessionId = sessionId,
                        planId = planId,
                        mode = mode,
                        defaultGlobalRest = settingsSnapshot.defaultRestSeconds,
                        autoFill = settingsSnapshot.autoFillFromLastSession,
                    )
                }

                workoutDao.upsertState(
                    WorkoutSessionStateEntity(sessionId = sessionId, updatedAt = now)
                )
                sessionId
            }
        }
    }

    private suspend fun seedExercisesFromPlan(
        sessionId: Long,
        planId: Long,
        mode: PrefillMode,
        defaultGlobalRest: Int,
        autoFill: Boolean,
    ) {
        val planExercises = planDao.getPlanExercises(planId)
        if (planExercises.isEmpty()) return

        val planSets = planDao.getPlanSets(planExercises.map { it.id }).groupBy { it.planExerciseId }
        val listRest = planDao.getPlan(planId)?.defaultRestSeconds ?: defaultGlobalRest
        val unit = settings.current().weightUnit

        planExercises.forEach { pe ->
            val type = pe.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS)
            // 计划动作没单独设演示（mediaPath 为空）时，回退到动作定义自带的演示 ——
            // 否则用户在动作库里绑了演示图/视频，按计划开练却什么也看不到。
            val definition = pe.exerciseDefinitionId?.let { exerciseDao.getById(it) }
            val mediaPath = pe.mediaPath ?: definition?.mediaPath
            val mediaType = if (pe.mediaPath != null) {
                pe.mediaType ?: MediaType.NONE.name
            } else {
                definition?.mediaType ?: MediaType.NONE.name
            }
            val thumbnailPath = pe.thumbnailPath ?: definition?.thumbnailPath

            val exerciseId = workoutDao.insertExercise(
                WorkoutExerciseEntity(
                    sessionId = sessionId,
                    exerciseDefinitionId = pe.exerciseDefinitionId,
                    nameSnapshot = pe.nameSnapshot,
                    typeSnapshot = pe.typeSnapshot,
                    orderIndex = pe.orderIndex,
                    supersetTag = pe.supersetTag,
                    restSeconds = (pe.restSecondsOverride ?: listRest).coerceIn(0, 900),
                    notes = pe.notes,
                    status = ExerciseStatus.PENDING.name,
                    mediaType = mediaType,
                    mediaPath = mediaPath,
                    thumbnailPath = thumbnailPath,
                )
            )

            val prescription = planSets[pe.id].orEmpty().sortedBy { it.setIndex }
            val baseSets = if (prescription.isEmpty()) {
                (0 until pe.targetSets.coerceIn(0, 30)).map { index ->
                    WorkoutSetEntity(
                        workoutExerciseId = exerciseId,
                        setIndex = index,
                        setType = SetType.NORMAL.name,
                        status = SetStatus.PENDING.name,
                        targetReps = pe.targetReps,
                        targetWeightKg = pe.targetWeight,
                    )
                }
            } else {
                prescription.map { ps ->
                    WorkoutSetEntity(
                        workoutExerciseId = exerciseId,
                        setIndex = ps.setIndex,
                        setType = ps.setType,
                        status = SetStatus.PENDING.name,
                        targetReps = ps.targetReps,
                        targetWeightKg = ps.targetWeight,
                        planSetId = ps.id,
                    )
                }
            }

            // 预填值写入 target* 字段，实际成绩（weightKg/reps）保持为空 ——
            // 这样"计划目标"与"实际完成"在数据层是可区分的，汇总时才不会把预填当成完成。
            // 赋给局部变量：跨模块的 public 属性无法智能转换（smart cast）
            val prefillDefinitionId = pe.exerciseDefinitionId
            val prefill = if (mode == PrefillMode.LAST_SESSION && autoFill && prefillDefinitionId != null) {
                loadLastSessionRecords(prefillDefinitionId, type)
            } else {
                emptyList()
            }

            val finalSets = baseSets.mapIndexed { index, set ->
                // BLANK：只保留组数骨架，目标重量/次数留空，训练中现填
                if (mode == PrefillMode.BLANK) {
                    return@mapIndexed set.copy(targetWeightKg = null, targetReps = null)
                }
                val source = prefill.getOrNull(index) ?: return@mapIndexed set
                set.copy(
                    targetWeightKg = source.weightKg ?: set.targetWeightKg,
                    targetReps = source.reps?.toString() ?: set.targetReps,
                )
            }
            if (finalSets.isNotEmpty()) workoutDao.insertSets(finalSets)

            // 用于通知文案的单位
            @Suppress("UNUSED_EXPRESSION") unit
        }
    }

    // ==================================================================
    // 记录一组（核心路径）
    // ==================================================================

    suspend fun logSet(input: LogSetInput): Outcome<SetLogResult> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val weightKg = input.weightInput?.let { UnitConversion.toKg(it, input.unit) }
            val nowSettings = settings.current()

            var restSeconds = 0
            var exerciseName = ""
            var nextSetHint = ""
            var prHits: List<PrHit> = emptyList()

            val setId = database.withTransaction {
                val exercise = workoutDao.getExercise(input.workoutExerciseId)
                    ?: error("训练动作不存在：${input.workoutExerciseId}")
                val type = exercise.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS)
                val session = workoutDao.getSession(exercise.sessionId)
                    ?: error("训练会话不存在")

                val existingSet = workoutDao.getSets(exercise.id)
                    .firstOrNull { it.setIndex == input.setIndex }

                val record = SetRecord(
                    exerciseType = type,
                    setType = input.setType,
                    status = SetStatus.COMPLETED,
                    weightKg = weightKg,
                    reps = input.reps,
                    durationSeconds = input.durationSeconds,
                    distanceMeters = input.distanceMeters,
                    leftReps = input.leftReps,
                    rightReps = input.rightReps,
                    rpe = input.rpe,
                )

                // PR 判定必须在写入前进行，否则会拿"自己"跟"自己"比较
                val hits = detectPrs(exercise, record, session, nowSettings.oneRmFormula)

                val entity = (existingSet ?: WorkoutSetEntity(
                    workoutExerciseId = exercise.id,
                    setIndex = input.setIndex,
                )).copy(
                    setType = input.setType.name,
                    status = SetStatus.COMPLETED.name,
                    weightKg = weightKg,
                    displayWeight = input.weightInput,
                    displayUnit = input.unit.name,
                    reps = input.reps,
                    durationSeconds = input.durationSeconds,
                    distanceMeters = input.distanceMeters,
                    leftReps = input.leftReps,
                    rightReps = input.rightReps,
                    rpe = input.rpe,
                    notes = input.notes,
                    completedAt = now,
                    isPersonalRecord = hits.isNotEmpty(),
                    prType = hits.firstOrNull()?.first?.name,
                )

                val rowId = if (existingSet != null) {
                    workoutDao.updateSet(entity)
                    existingSet.id
                } else {
                    workoutDao.insertSet(entity)
                }

                if (exercise.status != ExerciseStatus.IN_PROGRESS.name) {
                    workoutDao.setExerciseStatus(exercise.id, ExerciseStatus.IN_PROGRESS.name)
                }

                hits.forEach { (prType, hit, previous) ->
                    val definitionId = exercise.exerciseDefinitionId ?: return@forEach
                    writePersonalRecord(
                        definitionId = definitionId,
                        prType = prType,
                        value = hit.value,
                        reps = hit.reps,
                        weightKg = weightKg,
                        sessionId = session.id,
                        setId = rowId,
                        now = now,
                    )
                    prHits = prHits + PrHit(
                        exerciseName = exercise.nameSnapshot,
                        prType = prType,
                        value = hit.value,
                        reps = hit.reps,
                        previousValue = previous,
                    )
                }

                // 会话汇总
                val allSets = workoutDao.allSets(session.id)
                val typeByExercise = workoutDao.getExercises(session.id)
                    .associate { it.id to it.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS) }
                val volume = VolumeCalculator.totalVolume(
                    sets = allSets.map { it.toRecord(typeByExercise[it.workoutExerciseId] ?: type) },
                    bodyWeightKg = session.bodyWeightKgSnapshot,
                    includeWarmup = nowSettings.includeWarmupInVolume,
                )
                // 消耗采用分组累加（方案 A）：只在完成一组时计算该组消耗，
                // 而不是按整场重复计算——否则短时训练会被整场公式放大成荒谬数字。
                // 该组时长用"距上次落库的间隔"近似（即本组实际用时），限幅避免异常值。
                val activeSecondsNow = session.totalDurationSeconds + (session.activeSegmentStartedAt
                    ?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) } ?: 0)
                val setDurationSeconds = ((now - session.updatedAt) / 1000).toInt().coerceIn(3, 600)
                val setReps = (input.reps ?: 0) + (input.leftReps ?: 0) + (input.rightReps ?: 0)
                val setWork = buildWorkForSingleSet(
                    exercise = exercise,
                    definition = exercise.exerciseDefinitionId?.let { exerciseDao.getById(it) },
                    weightKg = weightKg,
                    reps = setReps,
                )
                val setResult = if (setWork != null) {
                    AdvancedCalorieEstimator.estimateSet(setWork, buildEstimatorProfile(session), setDurationSeconds)
                } else {
                    null
                }
                workoutDao.updateSession(
                    session.copy(
                        totalVolumeKg = volume,
                        totalSets = allSets.count { it.status == SetStatus.COMPLETED.name },
                        totalReps = allSets
                            .filter { it.status == SetStatus.COMPLETED.name }
                            .sumOf { (it.reps ?: 0) + (it.leftReps ?: 0) + (it.rightReps ?: 0) },
                        // 累加该组消耗；UI 实时跳字基于 calorieElapsedBaseSeconds 之后的时间
                        estimatedCalories = (session.estimatedCalories ?: 0.0) + (setResult?.calories ?: 0.0),
                        calorieEstimationLevel = (setResult?.level ?: EstimationLevel.L4_WEIGHT_ONLY).name,
                        calorieElapsedBaseSeconds = activeSecondsNow,
                        updatedAt = now,
                    )
                )

                // 推进光标
                val siblingSets = workoutDao.getSets(exercise.id)
                val nextPending = siblingSets.firstOrNull { it.status == SetStatus.PENDING.name }
                val exercises = workoutDao.getExercises(session.id)
                val currentIndex = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
                val nextExerciseIndex = if (nextPending == null) {
                    exercises.drop(currentIndex + 1)
                        .firstOrNull {
                            it.status == ExerciseStatus.PENDING.name ||
                                it.status == ExerciseStatus.IN_PROGRESS.name
                        }
                        ?.let { exercises.indexOf(it) } ?: currentIndex
                } else {
                    currentIndex
                }

                workoutDao.upsertState(
                    WorkoutSessionStateEntity(
                        sessionId = session.id,
                        currentExerciseIndex = nextExerciseIndex,
                        // 跨动作推进时光标落到目标动作的第一个未完成组；
                // 沿用刚记录那组的下标会让新动作高亮错误的一组
                currentSetIndex = nextPending?.setIndex ?: 0,
                        updatedAt = now,
                    )
                )

                // 热身组减半，但不设置下限：动作本身配置了 0 秒休息时，
                // 强制 30 秒会覆盖用户的配置，凭空多出一个没人要的倒计时
                restSeconds = if (input.setType == SetType.WARMUP) {
                    exercise.restSeconds / 2
                } else {
                    exercise.restSeconds
                }
                exerciseName = exercise.nameSnapshot
                nextSetHint = buildSetHint(nextPending, nowSettings.weightUnit)
                rowId
            }

            // 事务外启动休息计时：涉及系统通知与闹钟，不应持有数据库事务
            if (restSeconds > 0 && nowSettings.autoStartRestTimer) {
                val endsAt = time.nowEpochMs() + restSeconds * 1000L
                persistRestState(input.workoutExerciseId, endsAt, restSeconds, exerciseName, nextSetHint)
                restTimerGateway.start(
                    RestState.Running(
                        endsAtEpochMs = endsAt,
                        plannedSeconds = restSeconds,
                        exerciseName = exerciseName,
                        nextSetHint = nextSetHint,
                    )
                )
            }

            SetLogResult(setId = setId, prHits = prHits, restSeconds = restSeconds)
        }
    }

    private suspend fun writePersonalRecord(
        definitionId: Long,
        prType: PrType,
        value: Double,
        reps: Int?,
        weightKg: Double?,
        sessionId: Long,
        setId: Long,
        now: Long,
    ) {
        // 先查再写：本表主键是自增 id，@Upsert 无法命中 (exerciseDefinitionId, prType) 唯一索引
        val existing = prDao.getByType(definitionId, prType.name)
        val entity = PersonalRecordEntity(
            id = existing?.id ?: 0L,
            exerciseDefinitionId = definitionId,
            prType = prType.name,
            value = value,
            unit = unitFor(prType),
            reps = reps,
            weightKg = weightKg,
            achievedAt = now,
            sessionId = sessionId,
            setId = setId,
        )
        if (existing == null) prDao.insert(entity) else prDao.update(entity)
    }

    private fun unitFor(prType: PrType): String = when (prType) {
        PrType.MAX_REPS -> "reps"
        else -> "kg"
    }

    private suspend fun detectPrs(
        exercise: WorkoutExerciseEntity,
        record: SetRecord,
        session: WorkoutSessionEntity,
        oneRmFormula: com.fitnesspro.core.model.OneRmFormula,
    ): List<Triple<PrType, PersonalRecordDetector.Result.Hit, Double?>> {
        val definitionId = exercise.exerciseDefinitionId ?: return emptyList()
        val existing = prDao.getForExercise(definitionId)
            .associate { it.prType.toEnum(PrType.MAX_WEIGHT) to it.value }

        return prDetector.detect(
            set = record,
            bodyWeightKg = session.bodyWeightKgSnapshot,
            existing = existing,
            oneRmFormula = oneRmFormula,
        )
            .filterIsInstance<PersonalRecordDetector.Result.Hit>()
            .map { Triple(it.prType, it, existing[it.prType]) }
    }

    private fun buildSetHint(set: WorkoutSetEntity?, unit: WeightUnit): String {
        if (set == null) return ""
        val weight = set.targetWeightKg?.let { UnitConversion.fromKg(it, unit) }
        val reps = set.targetReps
        return when {
            weight != null && reps != null -> "${formatNumber(weight)} × $reps"
            reps != null -> reps
            else -> ""
        }
    }

    private fun formatNumber(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    // ==================================================================
    // 组 / 动作操作
    // ==================================================================

    suspend fun skipSet(workoutSetId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val set = workoutDao.getSet(workoutSetId) ?: error("组不存在")
            workoutDao.updateSet(set.copy(status = SetStatus.SKIPPED.name))
            advanceCursor(set.workoutExerciseId, set.setIndex)
        }
    }

    suspend fun addSetToExercise(workoutExerciseId: Long): Outcome<Long> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val sets = workoutDao.getSets(workoutExerciseId)
            val last = sets.lastOrNull()
            workoutDao.insertSet(
                WorkoutSetEntity(
                    workoutExerciseId = workoutExerciseId,
                    setIndex = (sets.maxOfOrNull { it.setIndex } ?: -1) + 1,
                    setType = last?.setType ?: SetType.NORMAL.name,
                    status = SetStatus.PENDING.name,
                    targetReps = last?.targetReps,
                    targetWeightKg = last?.targetWeightKg,
                )
            )
        }
    }

    /**
     * 复制一组：把某一组**已经做出来的成绩**变成新一组的目标，追加到该动作末尾。
     *
     * 与 [addSetToExercise] 的区别很重要：
     *  - addSetToExercise 复制的是**计划处方**（targetWeight/targetReps）
     *  - 这里复制的是**用户刚才实际做出来的数值**
     *
     * "再做一组一样的"是训练中出现频率最高的操作，应该是一次点击而不是重新输入两个数字。
     * 只在末尾追加、不重排已有 setIndex：`(workoutExerciseId, setIndex)` 是唯一索引，
     * 中途插入要整段重排，收益为零而风险不小。
     *
     * 未记录的组不允许复制 —— 没有数值可复制，那是 [addSetToExercise] 的语义。
     */
    suspend fun duplicateSet(workoutSetId: Long): Outcome<Long> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val source = workoutDao.getSet(workoutSetId) ?: error("组不存在")
            // 跳过组没有数值可复制，未完成组同理 —— 只有记录下来的成绩才值得复制
            require(
                source.status == SetStatus.COMPLETED.name || source.status == SetStatus.FAILED.name,
            ) { "只有已完成或失败的组才有可复制的成绩" }
            val sets = workoutDao.getSets(source.workoutExerciseId)
            workoutDao.insertSet(
                WorkoutSetEntity(
                    workoutExerciseId = source.workoutExerciseId,
                    setIndex = (sets.maxOfOrNull { it.setIndex } ?: -1) + 1,
                    setType = source.setType,
                    status = SetStatus.PENDING.name,
                    // 实际值优先，回退到处方值
                    targetWeightKg = source.weightKg ?: source.targetWeightKg,
                    targetReps = source.reps?.toString() ?: source.targetReps,
                )
            )
        }
    }

    /** 只能删除尚未完成的组 —— 已记录的成绩不允许被一键抹掉 */
    suspend fun deleteLastPendingSet(workoutExerciseId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                val sets = workoutDao.getSets(workoutExerciseId)
                require(sets.size > 1) { "至少保留一组" }
                val lastPending = sets.lastOrNull { it.status == SetStatus.PENDING.name }
                    ?: error("没有可删除的未完成组")
                workoutDao.deletePendingSet(lastPending.id)
            }
        }

    suspend fun skipExercise(workoutExerciseId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            database.withTransaction {
                workoutDao.skipExercise(workoutExerciseId, now)
                workoutDao.skipPendingSets(workoutExerciseId)
            }
            workoutDao.getExercise(workoutExerciseId)?.sessionId?.let { clearRestState(it) }
            restTimerGateway.cancel()
        }
    }

    /**
     * 训练中替换动作。
     *
     * 两条规则：
     *  1. **已记录的成绩永远不动**。换动作是"我不想做这个了"，不是"把我刚才做的删掉"。
     *     数据被静默删除会直接摧毁用户对记录可信度的信任。
     *  2. **未完成的组按新动作的处方重建**。旧处方的"8-12 次 / 60kg"留在新动作上，
     *     会变成一个错误的目标提示，比没有提示更糟。
     *
     * 因此这里没有"是否保留已记录数据"的开关 —— 那个开关本身就是设计错误。
     */
    suspend fun replaceExercise(
        workoutExerciseId: Long,
        newDefinitionId: Long,
    ): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val target = exerciseDao.getById(newDefinitionId) ?: error("动作不存在")
            val now = time.nowEpochMs()
            database.withTransaction {
                val current = workoutDao.getExercise(workoutExerciseId) ?: error("训练动作不存在")
                val existing = workoutDao.getSets(workoutExerciseId)
                val loggedCount = existing.count { it.status != SetStatus.PENDING.name }

                existing.filter { it.status == SetStatus.PENDING.name }
                    .forEach { workoutDao.deletePendingSet(it.id) }

                // 已完成的组占据 setIndex [0, loggedCount)，未完成的从其后编号，避免唯一索引冲突
                val plannedSets = target.defaultSets.coerceIn(1, 30)
                val pendingCount = (plannedSets - loggedCount).coerceAtLeast(1)
                workoutDao.insertSets(
                    (0 until pendingCount).map { offset ->
                        WorkoutSetEntity(
                            workoutExerciseId = workoutExerciseId,
                            setIndex = loggedCount + offset,
                            setType = SetType.NORMAL.name,
                            status = SetStatus.PENDING.name,
                            targetReps = target.defaultReps,
                        )
                    }
                )

                workoutDao.updateExercise(
                    current.copy(
                        exerciseDefinitionId = newDefinitionId,
                        nameSnapshot = target.name,
                        typeSnapshot = target.type,
                        status = ExerciseStatus.REPLACED.name,
                        mediaType = target.mediaType,
                        mediaPath = target.mediaPath,
                        thumbnailPath = target.thumbnailPath,
                    )
                )
                exerciseDao.bumpUsage(newDefinitionId, now)
            }
        }
    }

    suspend fun addAdhocExercise(sessionId: Long, definitionId: Long): Outcome<Long> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                val definition = exerciseDao.getById(definitionId) ?: error("动作不存在")
                val now = time.nowEpochMs()
                database.withTransaction {
                    val exerciseId = workoutDao.insertExercise(
                        WorkoutExerciseEntity(
                            sessionId = sessionId,
                            exerciseDefinitionId = definitionId,
                            nameSnapshot = definition.name,
                            typeSnapshot = definition.type,
                            orderIndex = workoutDao.maxExerciseOrderIndex(sessionId) + 1.0,
                            restSeconds = definition.defaultRestSeconds.coerceIn(0, 900),
                            status = ExerciseStatus.PENDING.name,
                            mediaType = definition.mediaType,
                            mediaPath = definition.mediaPath,
                            thumbnailPath = definition.thumbnailPath,
                            isAdhoc = true,
                        )
                    )
                    workoutDao.insertSets(
                        (0 until definition.defaultSets.coerceIn(1, 30)).map { index ->
                            WorkoutSetEntity(
                                workoutExerciseId = exerciseId,
                                setIndex = index,
                                setType = SetType.NORMAL.name,
                                status = SetStatus.PENDING.name,
                                targetReps = definition.defaultReps,
                            )
                        }
                    )
                    exerciseDao.bumpUsage(definitionId, now)
                    exerciseId
                }
            }
        }

    suspend fun updateExerciseNotes(workoutExerciseId: Long, notes: String?): Outcome<Unit> =
        runCatchingOutcomeSuspend { workoutDao.updateExerciseNotes(workoutExerciseId, notes) }

    /**
     * 移动"当前组"光标。
     *
     * 与其余写入一样必须持锁：这里是对 `WorkoutSessionStateEntity` 的读-改-写，
     * 与 `logSet` / `pause` 并发时会用**陈旧的休息计时字段**覆盖新值，
     * 结果是倒计时被复活或抹掉 —— 而它正是"断点恢复"的依据。
     */
    suspend fun moveCursor(sessionId: Long, exerciseIndex: Int, setIndex: Int): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                val state = workoutDao.getState(sessionId)
                    ?: WorkoutSessionStateEntity(sessionId = sessionId, updatedAt = 0L)
                workoutDao.upsertState(
                    state.copy(
                        currentExerciseIndex = exerciseIndex.coerceAtLeast(0),
                        currentSetIndex = setIndex.coerceAtLeast(0),
                        updatedAt = time.nowEpochMs(),
                    )
                )
            }
        }

    private suspend fun advanceCursor(workoutExerciseId: Long, fallbackSetIndex: Int) {
        val exercise = workoutDao.getExercise(workoutExerciseId) ?: return
        val nextPending = workoutDao.getSets(workoutExerciseId)
            .firstOrNull { it.status == SetStatus.PENDING.name }
        val index = workoutDao.getExercises(exercise.sessionId)
            .indexOfFirst { it.id == workoutExerciseId }
        workoutDao.upsertState(
            WorkoutSessionStateEntity(
                sessionId = exercise.sessionId,
                currentExerciseIndex = index.coerceAtLeast(0),
                currentSetIndex = nextPending?.setIndex ?: fallbackSetIndex,
                updatedAt = time.nowEpochMs(),
            )
        )
    }

    // ==================================================================
    // 暂停 / 恢复
    // ==================================================================

    /** 暂停指定会话：多会话并存时必须按 id 定位，禁止用"最新一条"代替 */
    suspend fun pause(sessionId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val session = workoutDao.getSession(sessionId)
                ?.takeIf { it.status == SessionStatus.IN_PROGRESS.name }
                ?: error("会话不存在或已结束")
            val segmentStart = session.activeSegmentStartedAt ?: return@withLock

            workoutDao.updateTiming(
                sessionId = session.id,
                accumulated = session.totalDurationSeconds +
                    ((now - segmentStart) / 1000).coerceAtLeast(0).toInt(),
                segmentStart = null,
                pausedTotal = session.pausedTotalSeconds + 1,
                now = now,
            )
            workoutDao.setPaused(session.id, true, now)

            // 暂停时冻结休息剩余时间，避免"暂停了但休息还在走"
            val state = workoutDao.getState(session.id)
            val endsAt = state?.restTimerEndsAtEpochMs
            if (state != null && endsAt != null) {
                workoutDao.upsertState(
                    state.copy(
                        restTimerPausedRemainingMs = (endsAt - now).coerceAtLeast(0L),
                        restTimerEndsAtEpochMs = null,
                        isPaused = true,
                        updatedAt = now,
                    )
                )
                restTimerGateway.cancel()
            }
        }
    }

    suspend fun resume(sessionId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val session = workoutDao.getSession(sessionId)
                ?.takeIf { it.status == SessionStatus.IN_PROGRESS.name }
                ?: error("会话不存在或已结束")
            if (session.activeSegmentStartedAt != null) return@withLock

            workoutDao.updateTiming(
                sessionId = session.id,
                accumulated = session.totalDurationSeconds,
                segmentStart = now,
                pausedTotal = session.pausedTotalSeconds,
                now = now,
            )
            workoutDao.setPaused(session.id, false, now)

            val state = workoutDao.getState(session.id)
            val frozen = state?.restTimerPausedRemainingMs
            if (state != null && frozen != null && frozen > 0) {
                val newEndsAt = now + frozen
                workoutDao.upsertState(
                    state.copy(
                        restTimerEndsAtEpochMs = newEndsAt,
                        restTimerPausedRemainingMs = null,
                        isPaused = false,
                        updatedAt = now,
                    )
                )
                restTimerGateway.start(
                    RestState.Running(
                        endsAtEpochMs = newEndsAt,
                        plannedSeconds = ((frozen + 999) / 1000).toInt(),
                        exerciseName = state.restExerciseName.orEmpty(),
                        nextSetHint = state.restNextSetHint.orEmpty(),
                    )
                )
            }
        }
    }

    private fun accumulateDuration(session: WorkoutSessionEntity, now: Long): Int {
        val segmentStart = session.activeSegmentStartedAt ?: return session.totalDurationSeconds
        return session.totalDurationSeconds + ((now - segmentStart) / 1000).coerceAtLeast(0).toInt()
    }

    // ==================================================================
    // 休息计时
    // ==================================================================

    /** 延长休息。对同一行做读-改-写，必须持锁（见 [moveCursor] 的说明） */
    suspend fun extendRest(sessionId: Long, deltaSeconds: Int): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val state = workoutDao.getState(sessionId) ?: return@withLock

            val frozen = state.restTimerPausedRemainingMs
            val endsAt = state.restTimerEndsAtEpochMs

            when {
                frozen != null -> {
                    val newFrozen = (frozen + deltaSeconds * 1000L).coerceAtLeast(0L)
                    workoutDao.upsertState(state.copy(restTimerPausedRemainingMs = newFrozen, updatedAt = now))
                    restTimerGateway.update(
                        RestState.Running(
                            endsAtEpochMs = now + newFrozen,
                            plannedSeconds = ((newFrozen + 999) / 1000).toInt(),
                            pausedRemainingMs = newFrozen,
                            exerciseName = state.restExerciseName.orEmpty(),
                            nextSetHint = state.restNextSetHint.orEmpty(),
                        )
                    )
                }
                endsAt != null -> {
                    val newEndsAt = (endsAt + deltaSeconds * 1000L).coerceAtLeast(now)
                    workoutDao.upsertState(state.copy(restTimerEndsAtEpochMs = newEndsAt, updatedAt = now))
                    restTimerGateway.update(
                        RestState.Running(
                            endsAtEpochMs = newEndsAt,
                            plannedSeconds = ((newEndsAt - now + 999) / 1000).toInt(),
                            exerciseName = state.restExerciseName.orEmpty(),
                            nextSetHint = state.restNextSetHint.orEmpty(),
                        )
                    )
                }
            }
        }
    }

    /** 暂停/恢复休息。对同一行做读-改-写，必须持锁（见 [moveCursor] 的说明） */
    suspend fun toggleRestPause(sessionId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            val now = time.nowEpochMs()
            val state = workoutDao.getState(sessionId) ?: return@withLock

            val frozen = state.restTimerPausedRemainingMs
            if (frozen != null) {
                val newEndsAt = now + frozen
                workoutDao.upsertState(
                    state.copy(restTimerEndsAtEpochMs = newEndsAt, restTimerPausedRemainingMs = null, updatedAt = now)
                )
                restTimerGateway.start(
                    RestState.Running(
                        endsAtEpochMs = newEndsAt,
                        plannedSeconds = ((frozen + 999) / 1000).toInt(),
                        exerciseName = state.restExerciseName.orEmpty(),
                        nextSetHint = state.restNextSetHint.orEmpty(),
                    )
                )
            } else {
                val endsAt = state.restTimerEndsAtEpochMs ?: return@withLock
                val remaining = (endsAt - now).coerceAtLeast(0L)
                workoutDao.upsertState(
                    state.copy(restTimerEndsAtEpochMs = null, restTimerPausedRemainingMs = remaining, updatedAt = now)
                )
                restTimerGateway.cancel()
            }
        }
    }

    suspend fun skipRest(sessionId: Long): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            clearRestState(sessionId)
            restTimerGateway.cancel()
        }
    }

    private suspend fun persistRestState(
        workoutExerciseId: Long,
        endsAt: Long,
        plannedSeconds: Int,
        exerciseName: String,
        nextSetHint: String,
    ) {
        // 从动作行反查会话 id：logSet 的事务外拿不到 session 变量，且多会话下不允许全局猜测
        val sessionId = workoutDao.getExercise(workoutExerciseId)?.sessionId ?: return
        val state = workoutDao.getState(sessionId)
            ?: WorkoutSessionStateEntity(sessionId = sessionId, updatedAt = 0L)
        workoutDao.upsertState(
            state.copy(
                restTimerEndsAtEpochMs = endsAt,
                restTimerPlannedSeconds = plannedSeconds,
                restTimerPausedRemainingMs = null,
                restExerciseName = exerciseName,
                restNextSetHint = nextSetHint,
                updatedAt = time.nowEpochMs(),
            )
        )
    }

    private suspend fun clearRestState(sessionId: Long) {
        workoutDao.clearRestTimer(sessionId, time.nowEpochMs())
    }

    /**
     * 恢复休息状态。
     *
     * 判定逻辑全部在 [RestTimerReconciler] 里（纯函数、可穷举测试），
     * 这里只负责把持久化字段喂给它 —— 时钟异常、暂停中返回、重启后剩余时间过期
     * 这些分支都有单测覆盖，见 `RestTimerReconcilerTest`。
     */
    /** 正在休息的会话 id（通知上的跳过/延长/暂停操作由此定位归属） */
    suspend fun activeRestSessionId(): Long? = workoutDao.getSessionIdWithActiveRest()

    /** 正在休息的会话的结束时间戳（闹钟到点时的幂等 token 比对用） */
    suspend fun activeRestEndsAt(): Long? {
        val sessionId = workoutDao.getSessionIdWithActiveRest() ?: return null
        return workoutDao.getState(sessionId)?.restTimerEndsAtEpochMs
    }

    private fun restoreRestState(state: WorkoutSessionStateEntity?): RestState {
        if (state == null) return RestState.Idle
        return RestTimerReconciler.reconcile(
            nowEpochMs = time.nowEpochMs(),
            endsAtEpochMs = state.restTimerEndsAtEpochMs,
            pausedRemainingMs = state.restTimerPausedRemainingMs,
            exerciseName = state.restExerciseName.orEmpty(),
            nextSetHint = state.restNextSetHint.orEmpty(),
        )
    }

    // ==================================================================
    // 结束训练
    // ==================================================================

    /**
     * 历史复盘：修改某组实际重量/次数（kg 已在 UI 层换算），并重算会话汇总。
     * displayWeight 是记录时的显示快照，保持原值（通知文案用，改写反而会伪造历史）。
     */
    suspend fun updateSetResult(
        sessionId: Long,
        setId: Long,
        weightKg: Double?,
        reps: Int?,
    ): Outcome<Unit> = runCatchingOutcomeSuspend {
        writeMutex.withLock {
            database.withTransaction {
                val set = workoutDao.getSet(setId) ?: error("组记录不存在")
                workoutDao.updateSet(
                    set.copy(
                        weightKg = weightKg ?: set.weightKg,
                        reps = reps ?: set.reps,
                    )
                )
                recalcSessionTotals(sessionId)
            }
        }
    }

    /** 历史复盘：删除某组，并重算会话汇总 */
    suspend fun deleteSetFromHistory(sessionId: Long, setId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                database.withTransaction {
                    workoutDao.deleteSet(setId)
                    recalcSessionTotals(sessionId)
                }
            }
        }

    /** 重算会话容量/组数/次数汇总（编辑、删除组之后必须调用，否则详情页与统计对不上） */
    private suspend fun recalcSessionTotals(sessionId: Long) {
        val session = workoutDao.getSession(sessionId) ?: return
        val nowSettings = settings.current()
        val exercises = workoutDao.getExercises(sessionId)
        val typeByExercise = exercises.associate {
            it.id to it.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS)
        }
        val allSets = workoutDao.allSets(sessionId)
        val volume = VolumeCalculator.totalVolume(
            sets = allSets.map {
                it.toRecord(typeByExercise[it.workoutExerciseId] ?: ExerciseType.WEIGHT_REPS)
            },
            bodyWeightKg = session.bodyWeightKgSnapshot,
            includeWarmup = nowSettings.includeWarmupInVolume,
        )
        workoutDao.updateSession(
            session.copy(
                totalVolumeKg = volume,
                totalSets = allSets.count { it.status == SetStatus.COMPLETED.name },
                totalReps = allSets
                    .filter { it.status == SetStatus.COMPLETED.name }
                    .sumOf { (it.reps ?: 0) + (it.leftReps ?: 0) + (it.rightReps ?: 0) },
                updatedAt = time.nowEpochMs(),
            )
        )
    }

    suspend fun finish(sessionId: Long, input: SummaryInput): Outcome<WorkoutSummary> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                val now = time.nowEpochMs()
                val nowSettings = settings.current()
                restTimerGateway.cancel()

                database.withTransaction {
                    val session = workoutDao.getSession(sessionId) ?: error("会话不存在")
                    val exercises = workoutDao.getExercises(sessionId)
                    val typeByExercise = exercises.associate {
                        it.id to it.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS)
                    }
                    val allSets = workoutDao.allSets(sessionId)
                    val completed = allSets.filter { it.status == SetStatus.COMPLETED.name }

                    val volume = VolumeCalculator.totalVolume(
                        sets = completed.map {
                            it.toRecord(typeByExercise[it.workoutExerciseId] ?: ExerciseType.WEIGHT_REPS)
                        },
                        bodyWeightKg = session.bodyWeightKgSnapshot,
                        includeWarmup = nowSettings.includeWarmupInVolume,
                    )
                    val reps = completed.sumOf { (it.reps ?: 0) + (it.leftReps ?: 0) + (it.rightReps ?: 0) }
                    val duration = accumulateDuration(session, now)
                    val status = if (input.completedFully) SessionStatus.COMPLETED else SessionStatus.ABANDONED
                    // 最终消耗 = 逐组累加值 + EPOC（训练结束后的恢复消耗，一次性追加）。
                    // 不再整场重算：累加值已经按组算准，整场回归公式在这里只会重复计入。
                    val work = buildCalorieWork(sessionId, allSets)
                    val weight = session.bodyWeightKgSnapshot
                        ?: buildEstimatorProfile(session).weightKg
                    val calories = (session.estimatedCalories ?: 0.0) +
                        AdvancedCalorieEstimator.estimateEPOCFor(work, weight ?: 0.0, duration / 60.0)
                    val level = session.calorieEstimationLevel
                        ?.let { lvl -> EstimationLevel.entries.firstOrNull { it.name == lvl } }
                        ?: EstimationLevel.L4_WEIGHT_ONLY

                    workoutDao.finalizeSession(
                        sessionId = sessionId,
                        status = status.name,
                        completedAt = now,
                        duration = duration,
                        volume = volume,
                        sets = completed.size,
                        reps = reps,
                        calories = calories,
                        calorieLevel = level.name,
                        now = now,
                    )

                    if (input.notes != null || input.perceivedEffort != null || input.mood != null) {
                        workoutDao.updateSessionSummaryInput(
                            id = sessionId,
                            notes = input.notes,
                            rpe = input.perceivedEffort,
                            mood = input.mood,
                            now = now,
                        )
                    }

                    session.planId?.let { planDao.touchLastPerformed(it, now, now) }
                    workoutDao.deleteState(sessionId)

                    val prHits = prDao.getBySession(sessionId).mapNotNull { row ->
                        val prType = PrType.entries.firstOrNull { it.name == row.prType }
                            ?: return@mapNotNull null
                        val name = exercises.firstOrNull { it.exerciseDefinitionId == row.exerciseDefinitionId }
                            ?.nameSnapshot
                            ?: exerciseDao.getById(row.exerciseDefinitionId)?.name
                            ?: ""
                        PrHit(
                            exerciseName = name,
                            prType = prType,
                            value = row.value,
                            reps = row.reps,
                        )
                    }

                    WorkoutSummary(
                        sessionId = sessionId,
                        planId = session.planId,
                        planName = session.planNameSnapshot,
                        status = status,
                        durationSeconds = duration,
                        pausedSeconds = session.pausedTotalSeconds,
                        totalVolumeKg = volume,
                        totalSets = completed.size,
                        totalReps = reps,
                        estimatedCalories = calories,
                        estimationLevel = level,
                        prs = prHits,
                        startedAt = session.startedAt,
                        completedAt = now,
                    )
                }
            }
        }

    /** 提前结束：本质上就是"标记为未完整完成"的 finish */
    suspend fun abandon(sessionId: Long): Outcome<Unit> =
        finish(sessionId, SummaryInput(completedFully = false)).let { outcome ->
            when (outcome) {
                is Outcome.Success -> Outcome.Success(Unit)
                is Outcome.Failure -> outcome
            }
        }

    /**
     * 丢弃进行中的会话（"不保存进度"）：移入回收站，可随时恢复继续练。
     *
     * 恢复后会话回到首页"进行中训练"列表（状态从未离开 IN_PROGRESS），
     * 不判 PR、不计统计。彻底删除由回收站的既有清理链路完成（30 天）。
     */
    suspend fun discard(sessionId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                restTimerGateway.cancel()
                database.withTransaction {
                    // 清掉会话内残留的休息计时状态，避免恢复后弹出过期的"休息结束"
                    workoutDao.clearRestTimer(sessionId, time.nowEpochMs())
                    workoutDao.discardSession(sessionId, time.nowEpochMs())
                }
            }
        }

    /**
     * 设置本次训练使用的体重（自重动作的有效负荷 = 体重 × 系数 + 附加负重）。
     *
     * 默认取最近一条身体记录的体重快照；没有记录（或体重刚变化）时，
     * 用户在训练中直接改，后续组数的消耗与总容量按新体重计算。
     * 已累计的消耗不回溯重算——那部分是按当时体重算的，回溯只会制造混乱。
     */
    suspend fun setSessionBodyWeight(sessionId: Long, weightKg: Double): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            writeMutex.withLock {
                database.withTransaction {
                    val session = workoutDao.getSession(sessionId) ?: error("会话不存在")
                    workoutDao.updateSession(
                        session.copy(
                            bodyWeightKgSnapshot = weightKg.coerceIn(20.0, 300.0),
                            updatedAt = time.nowEpochMs(),
                        )
                    )
                }
            }
        }

    /**
     * 查找可恢复的草稿：返回最近一条"未过期"的未完成训练，没有则 null。
     *
     * 每个会话只看自己那行 [WorkoutSessionStateEntity]（[WorkoutDao.getState]），
     * 不走全局 observeActive()，多会话并存时不会串数据。
     */
    suspend fun findResumableDraft(): WorkoutDraft? {
        val now = time.nowEpochMs()
        for (session in workoutDao.getActiveSessions()) {
            val state = workoutDao.getState(session.id)
            val draft = WorkoutDraft(
                sessionId = session.id,
                planName = session.planNameSnapshot,
                startedAt = session.startedAt,
                completedSets = workoutDao.countCompletedSets(session.id),
                isResting = state?.restTimerEndsAtEpochMs != null ||
                    state?.restTimerPausedRemainingMs != null,
                updatedAt = maxOf(state?.updatedAt ?: 0L, session.updatedAt),
            )
            if (DraftRecoveryPolicy.isResumable(draft, now)) return draft
        }
        return null
    }

    suspend fun getSummary(sessionId: Long): WorkoutSummary? = runCatching {
        val session = workoutDao.getSession(sessionId) ?: return@runCatching null
        val prHits = prDao.getBySession(sessionId).mapNotNull { row ->
            val prType = PrType.entries.firstOrNull { it.name == row.prType } ?: return@mapNotNull null
            PrHit(
                exerciseName = exerciseDao.getById(row.exerciseDefinitionId)?.name.orEmpty(),
                prType = prType,
                value = row.value,
                reps = row.reps,
            )
        }
        val summaryExercises = workoutDao.getExercises(sessionId)
            // 自由训练可以把同一动作加多次：汇总时按动作名合并为一行，
            // 否则分享卡会出现两行同名动作、组数被拆散显示"0 组 · 0 kg"
            .map { entity ->
                val sets = workoutDao.getSets(entity.id)
                    .filter { it.status == SetStatus.COMPLETED.name }
                entity to sets
            }
            .groupBy({ it.first.nameSnapshot }) { it.second }
            .map { (name, setsGroups) ->
                val sets = setsGroups.flatten()
                val volume = sets.sumOf { set ->
                    val weight = set.weightKg ?: 0.0
                    val reps = set.reps ?: 0
                    // 自重动作按 0 重量计，容量口径与训练页一致（体重折算在统计页做）
                    weight * reps + (set.distanceMeters ?: 0.0)
                }
                val bestWeight = sets.mapNotNull { it.weightKg }.maxOrNull()
                val bestReps = sets.mapNotNull { it.reps }.maxOrNull()
                val bestDistance = sets.mapNotNull { it.distanceMeters }.maxOrNull()
                SummaryExercise(
                    name = name,
                    completedSets = sets.size,
                    totalReps = sets.sumOf { it.reps ?: 0 },
                    volumeKg = volume,
                    bestWeightKg = bestWeight,
                    bestReps = bestReps,
                    distanceMeters = bestDistance,
                )
            }
        WorkoutSummary(
            sessionId = sessionId,
            planId = session.planId,
            planName = session.planNameSnapshot,
            status = session.status.toEnum(SessionStatus.COMPLETED),
            durationSeconds = session.totalDurationSeconds,
            pausedSeconds = session.pausedTotalSeconds,
            totalVolumeKg = session.totalVolumeKg,
            totalSets = session.totalSets,
            totalReps = session.totalReps,
            prs = prHits,
            exercises = summaryExercises,
            startedAt = session.startedAt,
            completedAt = session.completedAt,
        )
    }.getOrNull()

    /**
     * 一次训练的完整明细，供历史记录的复盘页使用。
     *
     * 与 [getSummary] 的区别：这里保留**每一组**的目标与实际，
     * 让用户能看出"第 3 组本来要 8 次，我只做了 6 次"。
     * 这正是训练记录的价值所在 —— 只给一个总容量是没法复盘的。
     */
    suspend fun getSessionDetail(sessionId: Long): SessionDetail? = runCatching {
        val session = workoutDao.getSession(sessionId) ?: return@runCatching null
        val exercises = workoutDao.getExercises(sessionId)
        val setsByExercise = exercises.associate { it.id to workoutDao.getSets(it.id) }

        SessionDetail(
            sessionId = session.id,
            planName = session.planNameSnapshot,
            status = session.status.toEnum(SessionStatus.COMPLETED),
            startedAt = session.startedAt,
            completedAt = session.completedAt,
            durationSeconds = session.totalDurationSeconds,
            pausedSeconds = session.pausedTotalSeconds,
            totalVolumeKg = session.totalVolumeKg,
            totalSets = session.totalSets,
            totalReps = session.totalReps,
            notes = session.notes,
            perceivedEffort = session.perceivedEffort,
            exercises = exercises.map { entity ->
                SessionExerciseDetail(
                    id = entity.id,
                    name = entity.nameSnapshot,
                    type = entity.typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS),
                    orderIndex = entity.orderIndex,
                    supersetTag = entity.supersetTag,
                    restSeconds = entity.restSeconds,
                    status = entity.status.toEnum(ExerciseStatus.PENDING),
                    notes = entity.notes,
                    sets = setsByExercise[entity.id].orEmpty().map { set ->
                        SessionSetDetail(
                            id = set.id,
                            setIndex = set.setIndex,
                            setType = set.setType.toEnum(SetType.NORMAL),
                            status = set.status.toEnum(SetStatus.PENDING),
                            weightKg = set.weightKg,
                            reps = set.reps,
                            durationSeconds = set.durationSeconds,
                            distanceMeters = set.distanceMeters,
                            leftReps = set.leftReps,
                            rightReps = set.rightReps,
                            rpe = set.rpe,
                            targetWeightKg = set.targetWeightKg,
                            targetReps = set.targetReps,
                            isPersonalRecord = set.isPersonalRecord,
                        )
                    },
                )
            },
        )
    }.getOrNull()

    /**
     * 设备重启后的清理：系统闹钟已被清除，休息早已过期，因此只清理状态，不做恢复。
     * 多会话并存：必须遍历全部进行中的会话，只看"最新一条"会漏掉其余会话的残留状态。
     */
    suspend fun clearStaleRestStateOnBoot(): Unit = runCatching {
        val now = time.nowEpochMs()
        workoutDao.getActiveSessions().forEach { session ->
            val state = workoutDao.getState(session.id) ?: return@forEach

            // 复用同一套判定：重启后闹钟已被系统清除，无法补一次"休息结束"提醒，
            // 因此只要不是"真的还在休息中"，就清掉状态，避免留下一个永远停在 0 的倒计时。
            val rest = RestTimerReconciler.reconcile(
                nowEpochMs = now,
                endsAtEpochMs = state.restTimerEndsAtEpochMs,
                pausedRemainingMs = state.restTimerPausedRemainingMs,
            )
            if (rest !is RestState.Running) {
                workoutDao.clearRestTimer(session.id, now)
            }
        }
        restTimerGateway.cancel()
    }.let { }
}
