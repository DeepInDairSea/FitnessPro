package com.fitnesspro.core.data.mapper

import com.fitnesspro.core.database.entity.BodyMeasurementEntity
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.ExerciseMuscleEntity
import com.fitnesspro.core.database.entity.PersonalRecordEntity
import com.fitnesspro.core.database.entity.PlanCardRow
import com.fitnesspro.core.database.entity.PlanExerciseEntity
import com.fitnesspro.core.database.entity.PlanScheduleRow
import com.fitnesspro.core.database.entity.PlanSetEntity
import com.fitnesspro.core.database.entity.PreviousSetRow
import com.fitnesspro.core.database.entity.SessionSummaryRow
import com.fitnesspro.core.database.entity.TrainingPlanEntity
import com.fitnesspro.core.database.entity.WorkoutExerciseEntity
import com.fitnesspro.core.database.entity.WorkoutSessionEntity
import com.fitnesspro.core.database.entity.WorkoutSetEntity
import com.fitnesspro.core.domain.repository.DayCount
import com.fitnesspro.core.domain.repository.ExerciseFrequency
import com.fitnesspro.core.domain.repository.MuscleVolume
import com.fitnesspro.core.domain.repository.ScheduledPlan
import com.fitnesspro.core.domain.repository.VolumePoint
import com.fitnesspro.core.database.dao.DayCountRow
import com.fitnesspro.core.database.dao.ExerciseFrequencyRow
import com.fitnesspro.core.database.dao.ExerciseTrendRow
import com.fitnesspro.core.database.dao.MuscleVolumeRow
import com.fitnesspro.core.database.dao.VolumeBucketRow
import com.fitnesspro.core.model.BodyMeasurement
import com.fitnesspro.core.model.DataSource
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseSnapshot
import com.fitnesspro.core.model.ExerciseStatus
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.ExerciseTrendPoint
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.PlanDetail
import com.fitnesspro.core.model.PlanExerciseItem
import com.fitnesspro.core.model.PlannedSet
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.SessionStatus
import com.fitnesspro.core.model.SessionSummary
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.WeightUnit

/**
 * 字符串 → 枚举的安全转换。
 * 枚举以 TEXT 存名称，好处是新增枚举值不需要数据库迁移；
 * 代价是数据库里可能出现历史遗留的未知值，因此必须提供默认值兜底，不能直接 valueOf。
 */
internal inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default

internal fun Set<Int>.toScheduledDaysString(): String? =
    if (isEmpty()) null else sorted().joinToString(",")

internal fun String?.toScheduledDays(): Set<Int> =
    this?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()

/** List<MajorGroup> ↔ 逗号分隔 TEXT（training_plan.focus） */
internal fun List<MajorGroup>.toFocusString(): String? =
    if (isEmpty()) null else joinToString(",") { it.name }

internal fun String?.toFocusList(): List<MajorGroup> =
    this?.split(",")?.mapNotNull { MajorGroup.fromKey(it.trim()) }?.distinct() ?: emptyList()

// ---------------------------------------------------------------------------
// 动作库
// ---------------------------------------------------------------------------

fun ExerciseDefinitionEntity.toDomain(
    secondaryMuscles: List<MuscleGroup> = emptyList(),
): Exercise {
    val type = type.toEnum(ExerciseType.WEIGHT_REPS)
    val mediaType = mediaType.toEnum(MediaType.NONE)
    val primaryMuscle = primaryMuscle.toEnum(MuscleGroup.OTHER)
    // majorGroup 为 null 的历史数据：按旧细分肌群回填推导
    val majorGroup = MajorGroup.fromKey(majorGroup)
        ?: ExerciseTaxonomy.legacyMajorOf(primaryMuscle)
    return Exercise(
        id = id,
        name = name,
        aliases = aliases?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        type = type,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles,
        majorGroup = majorGroup,
        subGroup = subGroup,
        equipment = equipment.toEnum(Equipment.OTHER),
        isUnilateral = isUnilateral,
        defaultRestSeconds = defaultRestSeconds,
        defaultSets = defaultSets,
        defaultReps = defaultReps,
        instructions = instructions,
        media = MediaRef.localOrNull(mediaType, mediaPath, thumbnailPath),
        colorKey = colorKey,
        isCustom = isCustom,
        isFavorite = isFavorite,
        deletedAt = deletedAt,
    )
}

fun List<ExerciseMuscleEntity>.groupIntoMuscles(): Map<Long, List<MuscleGroup>> =
    groupBy { it.exerciseId }
        .mapValues { (_, rows) -> rows.map { it.muscle.toEnum(MuscleGroup.OTHER) } }

fun ExerciseDefinitionEntity.toSnapshot(): ExerciseSnapshot = ExerciseSnapshot(
    definitionId = id,
    name = name,
    type = type.toEnum(ExerciseType.WEIGHT_REPS),
    isUnilateral = isUnilateral,
    primaryMuscle = primaryMuscle.toEnum(MuscleGroup.OTHER),
)

// ---------------------------------------------------------------------------
// 计划
// ---------------------------------------------------------------------------

fun com.fitnesspro.core.database.entity.PlanFolderEntity.toDomain(): com.fitnesspro.core.model.PlanFolder =
    com.fitnesspro.core.model.PlanFolder(
        id = id,
        name = name,
        colorHex = colorHex,
        sortOrder = sortOrder,
    )

fun PlanScheduleRow.toDomain(): ScheduledPlan = ScheduledPlan(
    epochDay = epochDay,
    planId = planId,
    planName = planName,
    colorHex = colorHex,
    startMinuteOfDay = startMinuteOfDay,
)

fun PlanCardRow.toDomain(): PlanCard = PlanCard(
    id = id,
    name = name,
    folderId = folderId,
    colorHex = colorHex,
    exerciseCount = exerciseCount,
    totalSets = totalSets,
    estimatedDurationSeconds = estimatedDurationSeconds,
    isFavorite = isFavorite,
    lastPerformedAt = lastPerformedAt,
    updatedAt = updatedAt,
    scheduledDays = scheduledDays.toScheduledDays(),
    focus = focus.toFocusList(),
    deletedAt = deletedAt,
)

fun PlanExerciseEntity.toDomain(
    sets: List<PlanSetEntity>,
    media: MediaRef?,
    /** 动作库里的主要肌群；由仓库用一次批量查询补上，缺失时不做分区 */
    primaryMuscle: MuscleGroup? = null,
): PlanExerciseItem = PlanExerciseItem(
    id = id,
    exercise = ExerciseSnapshot(
        definitionId = exerciseDefinitionId,
        name = nameSnapshot,
        type = typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS),
        isUnilateral = false,
        primaryMuscle = primaryMuscle,
    ),
    orderIndex = orderIndex,
    supersetTag = supersetTag,
    targetSets = targetSets,
    sets = sets.sortedBy { it.setIndex }.map { it.toDomain() },
    restSecondsOverride = restSecondsOverride,
    notes = notes,
    mediaOverride = media,
)

fun PlanSetEntity.toDomain(): PlannedSet = PlannedSet(
    id = id,
    setIndex = setIndex,
    setType = setType.toEnum(SetType.NORMAL),
    targetReps = targetReps,
    targetWeightKg = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRpe = targetRpe,
)

fun TrainingPlanEntity.toDomain(
    exercises: List<PlanExerciseItem>,
): PlanDetail = PlanDetail(
    id = id,
    name = name,
    description = description,
    folderId = folderId,
    colorHex = colorHex,
    defaultRestSeconds = defaultRestSeconds,
    estimatedDurationSeconds = estimatedDurationSeconds,
    scheduledDays = scheduledDays.toScheduledDays(),
    focus = focus.toFocusList(),
    isFavorite = isFavorite,
    exercises = exercises,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun mediaRefOf(type: String?, path: String?, thumb: String?): MediaRef? =
    MediaRef.localOrNull(type?.toEnum(MediaType.NONE) ?: MediaType.NONE, path, thumb)

// ---------------------------------------------------------------------------
// 训练
// ---------------------------------------------------------------------------

fun PreviousSetRow.toRecord(): SetRecord = SetRecord(
    exerciseType = ExerciseType.WEIGHT_REPS, // 由调用方按动作类型覆盖
    setType = setType.toEnum(SetType.NORMAL),
    status = SetStatus.COMPLETED,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
)

fun WorkoutSetEntity.toRecord(exerciseType: ExerciseType): SetRecord = SetRecord(
    exerciseType = exerciseType,
    setType = setType.toEnum(SetType.NORMAL),
    status = status.toEnum(SetStatus.PENDING),
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    leftReps = leftReps,
    rightReps = rightReps,
    rpe = rpe,
)

fun WorkoutExerciseEntity.toSnapshot(): ExerciseSnapshot = ExerciseSnapshot(
    definitionId = exerciseDefinitionId,
    name = nameSnapshot,
    type = typeSnapshot.toEnum(ExerciseType.WEIGHT_REPS),
    isUnilateral = false,
)

internal fun WorkoutExerciseEntity.statusOrPending(): ExerciseStatus =
    status.toEnum(ExerciseStatus.PENDING)

fun SessionSummaryRow.toDomain(): SessionSummary = SessionSummary(
    id = id,
    planId = planId,
    planName = planName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status.toEnum(SessionStatus.COMPLETED),
    durationSeconds = durationSeconds,
    totalVolumeKg = totalVolumeKg,
    totalSets = totalSets,
    totalReps = totalReps,
)

internal fun WorkoutSessionEntity.statusOrDefault(): SessionStatus =
    status.toEnum(SessionStatus.IN_PROGRESS)

/** 回收站用：从实体直接映射摘要（回收站行没有 PR 数，prCount 恒为 0） */
fun WorkoutSessionEntity.toTrashSummary(): SessionSummary = SessionSummary(
    id = id,
    planId = planId,
    planName = planNameSnapshot,
    startedAt = startedAt,
    completedAt = completedAt,
    status = statusOrDefault(),
    durationSeconds = totalDurationSeconds,
    totalVolumeKg = totalVolumeKg,
    totalSets = totalSets,
    totalReps = totalReps,
    deletedAt = deletedAt,
)

// ---------------------------------------------------------------------------
// 成就与身体数据
// ---------------------------------------------------------------------------

fun PersonalRecordEntity.toDomain(exerciseName: String = ""): PersonalRecord = PersonalRecord(
    id = id,
    exerciseDefinitionId = exerciseDefinitionId,
    exerciseName = exerciseName,
    prType = prType.toEnum(PrType.MAX_WEIGHT),
    value = value,
    unit = unit,
    reps = reps,
    weightKg = weightKg,
    achievedAt = achievedAt,
    sessionId = sessionId,
)

fun BodyMeasurementEntity.toDomain(): BodyMeasurement = BodyMeasurement(
    id = id,
    epochDay = epochDay,
    recordedAt = recordedAt,
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    muscleMassKg = muscleMassKg,
    heightCm = heightCm,
    visceralFatLevel = visceralFatLevel,
    chestCm = chestCm,
    waistCm = waistCm,
    hipCm = hipCm,
    armCm = armCm,
    thighCm = thighCm,
    calfCm = calfCm,
    bmr = bmr,
    source = source.toEnum(DataSource.MANUAL),
    notes = notes,
)

// ---------------------------------------------------------------------------
// 统计
// ---------------------------------------------------------------------------

fun VolumeBucketRow.toDomain(): VolumePoint = VolumePoint(
    epochDay = bucketEpochDay,
    volumeKg = volumeKg,
    sessionCount = sessionCount,
    totalSets = totalSets,
)

fun MuscleVolumeRow.toDomain(): MuscleVolume = MuscleVolume(
    muscle = muscle.toEnum(MuscleGroup.OTHER),
    volumeKg = volumeKg,
    setCount = setCount,
)

fun ExerciseFrequencyRow.toDomain(): ExerciseFrequency = ExerciseFrequency(
    exerciseDefinitionId = exerciseDefinitionId,
    name = exerciseName,
    sessionCount = sessionCount,
    setCount = setCount,
    volumeKg = volumeKg ?: 0.0,
)

fun DayCountRow.toDomain(): DayCount = DayCount(epochDay = epochDay, count = sessionCount)

fun ExerciseTrendRow.toDomain(): ExerciseTrendPoint = ExerciseTrendPoint(
    epochDay = epochDay,
    bestWeightKg = bestWeightKg,
    estimatedOneRmKg = maxEstimatedOneRmKg,
    volumeKg = volumeKg,
    totalReps = totalReps,
)

internal fun displayUnitOrDefault(unit: String?): WeightUnit =
    if (unit == WeightUnit.LB.name) WeightUnit.LB else WeightUnit.KG
