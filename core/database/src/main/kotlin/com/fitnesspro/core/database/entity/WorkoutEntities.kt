package com.fitnesspro.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "workout_session",
    indices = [
        Index("planId"),
        Index("startedAt"),
        Index("status"),
        Index("isDeleted"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            // 删除计划不得连带删除训练历史（历史是用户的资产）
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null 表示"空训练/自由训练" */
    val planId: Long? = null,
    val planNameSnapshot: String,
    val status: String = "IN_PROGRESS",
    val startedAt: Long,
    val completedAt: Long? = null,
    /** 暂停累计秒数（暂停时间不计入训练时长），仅用于展示 */
    val pausedTotalSeconds: Int = 0,
    /**
     * 已累计的有效训练秒数。
     *
     * 设计说明（ADR-07 修订）：训练时长**不能**用 elapsedRealtime 差值，因为它无法跨进程持久化。
     * 因此采用「分段累加」：暂停时把当前分段时长累加进本字段并清空 activeSegmentStartedAt，
     * 恢复时重新记起点，结束时累加最后一段。这样进程被杀再恢复也不会丢失已训练时间，
     * 且系统时间只有在"正在训练的那一段"内被修改才会影响结果。
     */
    val totalDurationSeconds: Int = 0,
    /** 当前分段起点（墙钟）；为 null 表示已暂停或未开始 */
    val activeSegmentStartedAt: Long? = null,
    /** 缓存字段，结束训练时一次算好 */
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    /** 估算消耗（kcal，分组累加：已完成组之和），随每组落库累加；v11 新增 */
    val estimatedCalories: Double? = null,
    /** 估算所用层级（EstimationLevel 枚举名）：数据完整度决定精度，v12 新增 */
    val calorieEstimationLevel: String? = null,
    /** 累计消耗对应的"有效训练秒数"基准：UI 实时跳字 = 基准值 + 当前组 MET 估算；v13 新增 */
    val calorieElapsedBaseSeconds: Int? = null,
    /** 训练当时的体重快照，用于自重动作的容量计算（体重之后变化不影响历史容量） */
    val bodyWeightKgSnapshot: Double? = null,
    val perceivedEffort: Int? = null,
    val mood: String? = null,
    val notes: String? = null,
    /** 软删除 */
    val isDeleted: Boolean = false,
    /** 回收站：进入回收站的时间（30 天后由清理 Worker 彻底删除） */
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "workout_exercise",
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "orderIndex"], unique = true),
        Index("exerciseDefinitionId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseDefinitionId"],
            // 动作被删除时历史仍要能显示（靠 nameSnapshot），因此置空而不是级联
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseDefinitionId: Long?,
    val nameSnapshot: String,
    val typeSnapshot: String,
    val orderIndex: Double,
    val supersetTag: Int? = null,
    val restSeconds: Int = 90,
    val notes: String? = null,
    val status: String = "PENDING",
    val mediaType: String = "NONE",
    val mediaPath: String? = null,
    val thumbnailPath: String? = null,
    /** 训练中临时添加、不写回计划 */
    val isAdhoc: Boolean = false,
    /**
     * 跳过是**运行时事件**，不是计划配置。
     * v1.1 把 isSkipped 放在 PlanExercise 上是模型错误：跳过一次会导致计划被永久标记为已跳过。
     */
    val skippedAt: Long? = null,
)

@Entity(
    tableName = "workout_set",
    indices = [
        Index("workoutExerciseId"),
        Index(value = ["workoutExerciseId", "setIndex"], unique = true),
        Index("status"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val setType: String = "NORMAL",
    val status: String = "PENDING",

    /** 内部统一 kg（ADR-03） */
    val weightKg: Double? = null,
    /** 用户原始输入值与单位，保证单位切换后原值不丢 */
    val displayWeight: Double? = null,
    val displayUnit: String? = null,

    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    /** 单侧动作分别记录 */
    val leftReps: Int? = null,
    val rightReps: Int? = null,
    val rpe: Double? = null,
    /** 本组结束后实际休息了多久（用于复盘） */
    val restTakenSeconds: Int? = null,

    val isPersonalRecord: Boolean = false,
    val prType: String? = null,

    /** 关联的计划处方，用于"目标 vs 实际"对比 */
    val planSetId: Long? = null,
    val targetWeightKg: Double? = null,
    val targetReps: String? = null,

    val completedAt: Long? = null,
    val notes: String? = null,
)

/**
 * 训练中断恢复状态（单行/会话）。
 *
 * 这是"进程被杀后可恢复"的关键：当前光标位置与休息计时终点都持久化，
 * 重新进入 App 时由 WorkoutSessionManager 重建全部状态。
 */
@Entity(
    tableName = "workout_session_state",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutSessionStateEntity(
    @PrimaryKey val sessionId: Long,
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    /**
     * 休息计时终点（墙钟 UTC millis）。
     * 跨进程恢复用墙钟；进程内计算则同时参考 elapsedRealtime 差值。
     */
    val restTimerEndsAtEpochMs: Long? = null,
    val restTimerPlannedSeconds: Int? = null,
    /** 非空表示已暂停，值为暂停时的剩余毫秒 */
    val restTimerPausedRemainingMs: Long? = null,
    val restExerciseName: String? = null,
    val restNextSetHint: String? = null,
    val isPaused: Boolean = false,
    val updatedAt: Long,
)

// ---------------------------------------------------------------------------
// 关系 POJO
// ---------------------------------------------------------------------------

data class WorkoutSetWithExercise(
    @Embedded val set: WorkoutSetEntity,
    val exerciseDefinitionId: Long?,
    val exerciseName: String,
    val exerciseType: String,
)

data class WorkoutExerciseWithSets(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutSessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<WorkoutExerciseWithSets>,
)

/** 上次训练某动作的成绩行 */
data class PreviousSetRow(
    val setIndex: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val setType: String,
)

/** 历史列表投影 */
data class SessionSummaryRow(
    val id: Long,
    val planId: Long?,
    val planName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val totalReps: Int,
)
