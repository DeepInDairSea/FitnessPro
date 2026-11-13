package com.fitnesspro.core.data.repository

import androidx.room.withTransaction
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.data.mapper.mediaRefOf
import com.fitnesspro.core.data.mapper.toDomain
import com.fitnesspro.core.data.mapper.toFocusString
import com.fitnesspro.core.data.mapper.toScheduledDaysString
import com.fitnesspro.core.data.mapper.toEnum
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.PlanDao
import com.fitnesspro.core.database.entity.PlanExerciseEntity
import com.fitnesspro.core.database.entity.PlanFolderEntity
import com.fitnesspro.core.database.entity.PlanSetEntity
import com.fitnesspro.core.database.entity.TrainingPlanEntity
import com.fitnesspro.core.domain.calculator.OrderIndexCalculator
import com.fitnesspro.core.domain.calculator.PlanDurationCalculator
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.PlanDetail
import com.fitnesspro.core.model.PlanFolder
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.PlannedSet
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val database: FitnessDatabase,
    private val planDao: PlanDao,
    private val exerciseDao: ExerciseDao,
    private val time: TimeProvider,
) : PlanRepository {

    override fun observePlanCards(sortOrder: PlanSortOrder, query: String): Flow<List<PlanCard>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            planDao.observePlanCards(sortOrder.name).map { rows -> rows.map { it.toDomain() } }
        } else {
            planDao.searchPlanCards(trimmed).map { rows -> rows.map { it.toDomain() } }
        }
    }

    override fun observeFolders(): Flow<List<PlanFolder>> =
        planDao.observeFolders().map { folders -> folders.map { it.toDomain() } }

    override fun observePlanDetail(planId: Long): Flow<PlanDetail?> =
        planDao.observePlan(planId).flatMapLatest { plan ->
            if (plan == null) {
                flowOf(null)
            } else {
                planDao.observePlanExercises(planId).map { exercises ->
                    buildDetail(plan, exercises)
                }
            }
        }

    override fun observeTrash(): Flow<List<PlanCard>> =
        planDao.observeTrash().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPlanDetail(planId: Long): PlanDetail? = runCatching {
        val plan = planDao.getPlan(planId) ?: return@runCatching null
        buildDetail(plan, planDao.getPlanExercises(planId))
    }.getOrNull()

    private suspend fun buildDetail(
        plan: TrainingPlanEntity,
        exercises: List<PlanExerciseEntity>,
    ): PlanDetail {
        val sets = if (exercises.isEmpty()) emptyList() else planDao.getPlanSets(exercises.map { it.id })
        val setsByExercise = sets.groupBy { it.planExerciseId }
        // 部位用于"按部位分区"显示：一次批量查询补齐，避免逐条回查动作库
        val muscles = exerciseDao
            .getByIds(exercises.mapNotNull { it.exerciseDefinitionId }.distinct())
            .associate { it.id to it.primaryMuscle.toEnum(MuscleGroup.OTHER) }
        val items = exercises.map { entity ->
            entity.toDomain(
                sets = setsByExercise[entity.id].orEmpty(),
                media = mediaRefOf(entity.mediaType, entity.mediaPath, entity.thumbnailPath),
                primaryMuscle = entity.exerciseDefinitionId?.let { muscles[it] },
            )
        }
        return plan.toDomain(items)
    }

    override suspend fun createPlan(
        name: String,
        description: String?,
        scheduledDays: Set<Int>,
        defaultRestSeconds: Int?,
        folderId: Long?,
        copyFromPlanId: Long?,
        focus: List<MajorGroup>,
    ): Outcome<Long> = runCatchingOutcomeSuspend {
        val now = time.nowEpochMs()
        database.withTransaction {
            val newPlanId = planDao.insertPlan(
                TrainingPlanEntity(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    folderId = folderId,
                    defaultRestSeconds = defaultRestSeconds,
                    scheduledDays = scheduledDays.toScheduledDaysString(),
                    focus = focus.toFocusString(),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (copyFromPlanId != null) {
                copyExercises(fromPlanId = copyFromPlanId, toPlanId = newPlanId, now = now)
            }
            newPlanId
        }
    }

    /**
     * 复制计划结构（含组级处方与媒体引用）。
     * 媒体文件为**共享引用**，不会被物理复制；删除任一侧时由 MediaGarbageCollector
     * 按引用计数判定是否可清理，因此共享是安全的，也避免了重复占空间。
     */
    private suspend fun copyExercises(fromPlanId: Long, toPlanId: Long, now: Long) {
        val sourceExercises = planDao.getPlanExercises(fromPlanId)
        if (sourceExercises.isEmpty()) return

        val setsByExercise = planDao.getPlanSets(sourceExercises.map { it.id })
            .groupBy { it.planExerciseId }

        sourceExercises.forEach { source ->
            val newId = planDao.insertExercise(source.copy(id = 0, planId = toPlanId))
            val sets = setsByExercise[source.id].orEmpty()
            if (sets.isNotEmpty()) {
                planDao.insertSets(sets.map { it.copy(id = 0, planExerciseId = newId) })
            }
        }
        touchPlan(toPlanId)
    }

    override suspend fun savePlanDetail(detail: PlanDetail): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            val now = time.nowEpochMs()
            database.withTransaction {
                val existing = planDao.getPlan(detail.id) ?: error("计划不存在：${detail.id}")

                val existingExercises = planDao.getPlanExercises(detail.id)
                val incomingIds = detail.exercises.map { it.id }.filter { it > 0 }.toSet()
                // 移除被删掉的动作（plan_set 由外键 CASCADE 自动清理）
                existingExercises
                    .filter { it.id !in incomingIds }
                    .forEach { planDao.deleteExercise(it.id) }

                // 唯一索引 (planId, orderIndex) 不允许中间态冲突：
                // 调整过顺序后，直接按新 orderIndex 逐条 update 会在交换相邻两项时
                // 撞上尚未更新的旧值 → 整个事务失败 → 表现为"调了顺序保存不生效"。
                // 先把本次保留的所有动作统一挪到负数临时区间，下方循环再写入最终顺序。
                existingExercises
                    .filter { it.id in incomingIds }
                    .forEachIndexed { index, entity ->
                        planDao.updateExerciseOrder(entity.id, -(index + 1).toDouble())
                    }

                detail.exercises.forEach { item ->
                    val entity = PlanExerciseEntity(
                        id = item.id,
                        planId = detail.id,
                        exerciseDefinitionId = item.exercise.definitionId,
                        nameSnapshot = item.exercise.name,
                        typeSnapshot = item.exercise.type.name,
                        orderIndex = item.orderIndex,
                        supersetTag = item.supersetTag,
                        targetSets = item.targetSets,
                        targetReps = item.sets.firstOrNull()?.targetReps,
                        targetWeight = item.sets.firstOrNull { it.targetWeightKg != null }?.targetWeightKg,
                        restSecondsOverride = item.restSecondsOverride,
                        notes = item.notes,
                        mediaType = item.mediaOverride?.mediaTypeName(),
                        mediaPath = item.mediaOverride?.path(),
                        thumbnailPath = item.mediaOverride?.thumbnail(),
                    )
                    val exerciseId = if (entity.id > 0L) {
                        planDao.updateExercise(entity)
                        entity.id
                    } else {
                        planDao.insertExercise(entity)
                    }

                    // 组级处方采用全量重写：数量小（通常 ≤ 30 组），逻辑简单且不易出现"少一组/多一组"
                    planDao.deleteSetsOfExercise(exerciseId)
                    val sets = normalizeSets(exerciseId, item.targetSets, item.sets)
                    if (sets.isNotEmpty()) planDao.insertSets(sets)
                }

                val estimated = PlanDurationCalculator.estimate(
                    exercises = detail.exercises,
                    planDefaultRestSeconds = detail.defaultRestSeconds,
                    globalDefaultRestSeconds = DEFAULT_GLOBAL_REST,
                ).highSeconds

                planDao.updatePlan(
                    existing.copy(
                        name = detail.name.trim(),
                        description = detail.description,
                        folderId = detail.folderId,
                        defaultRestSeconds = detail.defaultRestSeconds,
                        scheduledDays = detail.scheduledDays.toScheduledDaysString(),
                        focus = detail.focus.toFocusString(),
                        estimatedDurationSeconds = estimated,
                        updatedAt = now,
                    )
                )
            }
        }

    /**
     * 保证落库的组数与 targetSets 一致：多出来的组丢弃，缺少的组用处方默认值补齐。
     * 用户在 UI 上调"组数"时不需要手动逐行补齐。
     */
    private fun normalizeSets(
        planExerciseId: Long,
        targetSets: Int,
        provided: List<PlannedSet>,
    ): List<PlanSetEntity> {
        val count = targetSets.coerceIn(0, 30)
        if (count == 0) return emptyList()
        val byIndex = provided.associateBy { it.setIndex }
        val fallback = provided.minByOrNull { it.setIndex }
        return (0 until count).map { index ->
            val source = byIndex[index] ?: fallback
            PlanSetEntity(
                planExerciseId = planExerciseId,
                setIndex = index,
                setType = (source?.setType ?: com.fitnesspro.core.model.SetType.NORMAL).name,
                targetReps = source?.targetReps ?: "10",
                targetWeight = source?.targetWeightKg,
                targetDurationSeconds = source?.targetDurationSeconds,
                targetRpe = source?.targetRpe,
            )
        }
    }

    override suspend fun renamePlan(planId: Long, name: String): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            val plan = planDao.getPlan(planId) ?: error("计划不存在")
            planDao.updatePlan(plan.copy(name = name.trim(), updatedAt = time.nowEpochMs()))
        }

    override suspend fun setFavorite(planId: Long, favorite: Boolean): Outcome<Unit> =
        runCatchingOutcomeSuspend { planDao.setFavorite(planId, favorite, time.nowEpochMs()) }

    override suspend fun duplicatePlan(planId: Long): Outcome<Long> = runCatchingOutcomeSuspend {
        val source = planDao.getPlan(planId) ?: error("计划不存在")
        val now = time.nowEpochMs()
        database.withTransaction {
            val newId = planDao.insertPlan(
                source.copy(
                    id = 0,
                    name = "${source.name} 副本",
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    lastPerformedAt = null,
                    estimatedDurationSeconds = source.estimatedDurationSeconds,
                )
            )
            copyExercises(fromPlanId = planId, toPlanId = newId, now = now)
            newId
        }
    }

    override suspend fun moveToTrash(planId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { planDao.moveToTrash(planId, time.nowEpochMs()) }

    override suspend fun restoreFromTrash(planId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { planDao.restoreFromTrash(planId, time.nowEpochMs()) }

    override suspend fun permanentlyDelete(planId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction { planDao.hardDelete(planId) }
            // 媒体文件不在此处删除：可能被其他计划/动作共享，
            // 统一交给 MediaGarbageCollector 按引用计数清理（DEV 文档 §6.4）。
        }

    override suspend fun addExerciseToPlan(
        planId: Long,
        exerciseDefinitionId: Long,
        exerciseName: String,
        exerciseType: ExerciseType,
        isUnilateral: Boolean,
        defaultSets: Int,
        defaultReps: String?,
        defaultRestSeconds: Int,
    ): Outcome<Long> = runCatchingOutcomeSuspend {
        database.withTransaction {
            val existing = planDao.getPlanExercises(planId)
            val orderIndex = OrderIndexCalculator.between(existing.maxOfOrNull { it.orderIndex }, null)
            val sets = defaultSets.coerceIn(1, 30)

            val exerciseId = planDao.insertExercise(
                PlanExerciseEntity(
                    planId = planId,
                    exerciseDefinitionId = exerciseDefinitionId,
                    nameSnapshot = exerciseName,
                    typeSnapshot = exerciseType.name,
                    orderIndex = orderIndex,
                    targetSets = sets,
                    targetReps = defaultReps,
                )
            )
            planDao.insertSets(
                (0 until sets).map { index ->
                    PlanSetEntity(
                        planExerciseId = exerciseId,
                        setIndex = index,
                        setType = "NORMAL",
                        targetReps = defaultReps,
                    )
                }
            )
            touchPlan(planId)
            exerciseId
        }
    }

    override suspend fun removeExercise(planExerciseId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction {
                val planId = planDao.planIdOf(planExerciseId)
                planDao.deleteExercise(planExerciseId)
                planId?.let { touchPlan(it) }
            }
        }

    override suspend fun reorderExercises(planId: Long, orderedIds: List<Long>): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction {
                // 唯一索引 (planId, orderIndex) 要求不能出现中间态冲突：
                // 先把待重排的项挪到负数区间，再统一写入目标顺序。
                orderedIds.forEachIndexed { index, id ->
                    planDao.updateExerciseOrder(id, -(index + 1).toDouble())
                }
                orderedIds.forEachIndexed { index, id ->
                    planDao.updateExerciseOrder(id, index.toDouble())
                }
                touchPlan(planId)
            }
        }

    override suspend fun setExerciseRestOverride(planExerciseId: Long, seconds: Int?): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction {
                val exercise = findExerciseOrThrow(planExerciseId)
                planDao.updateExercise(exercise.copy(restSecondsOverride = seconds?.coerceIn(0, 900)))
                touchPlan(exercise.planId)
            }
        }

    override suspend fun setSupersetTag(planExerciseIds: List<Long>, tag: Int?): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction {
                planExerciseIds.forEach { id ->
                    val exercise = findExerciseOrThrow(id)
                    planDao.updateExercise(exercise.copy(supersetTag = tag))
                    touchPlan(exercise.planId)
                }
            }
        }

    override suspend fun updateExerciseTarget(
        planExerciseId: Long,
        targetSets: Int,
        targetReps: String?,
        targetWeightKg: Double?,
    ): Outcome<Unit> = runCatchingOutcomeSuspend {
        database.withTransaction {
            val exercise = findExerciseOrThrow(planExerciseId)
            val count = targetSets.coerceIn(1, 30)
            planDao.updateExercise(
                exercise.copy(targetSets = count, targetReps = targetReps, targetWeight = targetWeightKg)
            )
            planDao.deleteSetsOfExercise(planExerciseId)
            planDao.insertSets(
                (0 until count).map { index ->
                    PlanSetEntity(
                        planExerciseId = planExerciseId,
                        setIndex = index,
                        setType = "NORMAL",
                        targetReps = targetReps,
                        targetWeight = targetWeightKg,
                    )
                }
            )
            touchPlan(exercise.planId)
        }
    }

    override suspend fun setPlanExerciseMedia(planExerciseId: Long, media: MediaRef?): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            database.withTransaction {
                val exercise = findExerciseOrThrow(planExerciseId)
                planDao.updateExercise(
                    exercise.copy(
                        mediaType = media?.mediaTypeName(),
                        mediaPath = media?.path(),
                        thumbnailPath = media?.thumbnail(),
                    )
                )
                touchPlan(exercise.planId)
            }
        }

    override suspend fun createFolder(name: String): Outcome<Long> = runCatchingOutcomeSuspend {
        planDao.insertFolder(PlanFolderEntity(name = name.trim()))
    }

    override suspend fun deleteFolder(folderId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            // 外键 onDelete = SET_NULL：删除分组只把计划移出分组，不会删除计划本身
            database.withTransaction { planDao.deleteFolder(folderId) }
        }

    override suspend fun assignFolder(planId: Long, folderId: Long?): Outcome<Unit> =
        runCatchingOutcomeSuspend { planDao.setFolder(planId, folderId, time.nowEpochMs()) }

    override suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int> =
        runCatchingOutcomeSuspend {
            val threshold = time.nowEpochMs() - retentionDays * MILLIS_PER_DAY
            val expired = planDao.getTrashOlderThan(threshold)
            database.withTransaction { expired.forEach { planDao.hardDelete(it.id) } }
            expired.size
        }

    private suspend fun findExerciseOrThrow(planExerciseId: Long): PlanExerciseEntity =
        planDao.findPlanExercise(planExerciseId) ?: error("计划动作不存在：$planExerciseId")

    private suspend fun touchPlan(planId: Long) {
        planDao.getPlan(planId)?.let { planDao.updatePlan(it.copy(updatedAt = time.nowEpochMs())) }
    }

    // ---- 媒体引用 → 列值 ----

    private fun MediaRef.mediaTypeName(): String = when (this) {
        is MediaRef.Local -> type.name
        is MediaRef.Remote -> MediaType.VIDEO_LINK.name
    }

    private fun MediaRef.path(): String = when (this) {
        is MediaRef.Local -> fileName
        is MediaRef.Remote -> url
    }

    private fun MediaRef.thumbnail(): String? = (this as? MediaRef.Local)?.thumbnailName

    private companion object {
        const val DEFAULT_GLOBAL_REST = 90
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** 供 UI 层复用的保留期常量：回收站保留天数（PRD FR-42） */
const val TRASH_RETENTION_DAYS = 30

/** 回收站剩余保留天数 */
fun daysLeftInTrash(deletedAt: Long, nowEpochMs: Long): Long {
    val elapsedDays = (nowEpochMs - deletedAt) / (24L * 60 * 60 * 1000)
    return (TRASH_RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
}
