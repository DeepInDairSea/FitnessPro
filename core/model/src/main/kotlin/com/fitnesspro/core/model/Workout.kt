package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/** 休息计时状态 —— 唯一真理是 endAtEpochMs，其余都是它的视图 */
@Serializable
sealed interface RestState {
    @Serializable
    data object Idle : RestState

    /** 计时进行中或已暂停。pausedRemainingMs 非空表示已暂停 */
    @Serializable
    data class Running(
        val endsAtEpochMs: Long,
        val plannedSeconds: Int,
        val pausedRemainingMs: Long? = null,
        /** 触发本次休息的动作名，用于通知与 TTS 播报 */
        val exerciseName: String = "",
        /** 下一组的简短描述，如 "60kg × 8" */
        val nextSetHint: String = "",
    ) : RestState {

        fun remainingMs(nowEpochMs: Long): Long =
            pausedRemainingMs ?: (endsAtEpochMs - nowEpochMs).coerceAtLeast(0L)

        val isPaused: Boolean get() = pausedRemainingMs != null
    }

    /** 已结束但用户尚未回到训练页 —— 需要显式提示"休息已结束"，而不是静默回到 Idle */
    @Serializable
    data object Finished : RestState
}

/** 训练中一组的状态视图 */
@Serializable
data class ActiveSet(
    val id: Long,
    val setIndex: Int,
    val setType: SetType,
    val status: SetStatus,
    val targetReps: String? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    /** 实际成绩 */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val leftReps: Int? = null,
    val rightReps: Int? = null,
    val rpe: Double? = null,
    val isPersonalRecord: Boolean = false,
    val prType: PrType? = null,
) {
    fun toRecord(exerciseType: ExerciseType): SetRecord = SetRecord(
        exerciseType = exerciseType,
        setType = setType,
        status = status,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        leftReps = leftReps,
        rightReps = rightReps,
        rpe = rpe,
    )
}

/** 训练中一个动作的状态视图 */
@Serializable
data class ActiveExercise(
    val id: Long,
    val exercise: ExerciseSnapshot,
    val orderIndex: Double,
    val supersetTag: Int? = null,
    val restSeconds: Int,
    val notes: String? = null,
    val status: ExerciseStatus = ExerciseStatus.PENDING,
    val media: MediaRef? = null,
    val isAdhoc: Boolean = false,
    val sets: List<ActiveSet> = emptyList(),
    /** 上次训练该动作的成绩，用于"上次：60kg × 8, 8, 7"回显 */
    val lastSessionSets: List<SetRecord> = emptyList(),
) {
    val completedSetCount: Int get() = sets.count { it.status == SetStatus.COMPLETED }
    val totalSetCount: Int get() = sets.size
    val nextPendingSetIndex: Int? get() = sets.firstOrNull { it.status == SetStatus.PENDING }?.setIndex
}

/** 训练会话的完整状态 —— 由 WorkoutSessionManager 重建，不缓存在内存之外的地方 */
@Serializable
data class ActiveWorkout(
    val sessionId: Long,
    val planId: Long? = null,
    val planName: String,
    val startedAt: Long,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val pausedTotalSeconds: Int = 0,
    val isPaused: Boolean = false,
    val bodyWeightKg: Double? = null,
    val exercises: List<ActiveExercise> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val rest: RestState = RestState.Idle,
) {
    val currentExercise: ActiveExercise? get() = exercises.getOrNull(currentExerciseIndex)
    val totalCompletedSets: Int get() = exercises.sumOf { it.completedSetCount }
    val totalSets: Int get() = exercises.sumOf { it.totalSetCount }
    val totalVolumeKg: Double
        get() = VolumePreview.totalVolume(exercises, bodyWeightKg, includeWarmup = false)
}

/** 上一次训练成绩摘要 */
data class LastPerformance(
    val sets: List<SetRecord> = emptyList(),
) {
    val isEmpty: Boolean get() = sets.isEmpty()

    /** 形如 "60kg × 8, 8, 7" */
    fun summaryText(weightUnit: WeightUnit): String {
        if (sets.isEmpty()) return ""
        return sets.joinToString(", ") { s ->
            val w = s.weightKg?.let { UnitConversion.fromKg(it, weightUnit) }
            when {
                w != null && s.reps != null -> "${formatWeight(w)}×${s.reps}"
                s.reps != null -> "${s.reps}次"
                s.durationSeconds != null -> "${s.durationSeconds}s"
                else -> "-"
            }
        }
    }

    private fun formatWeight(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

/** 训练总结 */
@Serializable
data class WorkoutSummary(
    val sessionId: Long,
    /**
     * 来源计划。为 null 表示自由训练 —— 总结页的"与上次对比"只在同一计划内比较，
     * 拿自由训练去和历史计划比是在制造噪音。
     */
    val planId: Long? = null,
    val planName: String,
    val status: SessionStatus,
    val durationSeconds: Int,
    val pausedSeconds: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val totalReps: Int,
    val prs: List<PrHit> = emptyList(),
    /** 逐动作明细：分享卡片用它列出"练了什么、练了多少" */
    val exercises: List<SummaryExercise> = emptyList(),
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
)

/** 总结页/分享卡片的单动作汇总 */
@Serializable
data class SummaryExercise(
    val name: String,
    val completedSets: Int,
    /** 该动作完成的次数合计（组数 × 次数的纵向汇总） */
    val totalReps: Int = 0,
    val volumeKg: Double,
    val bestWeightKg: Double? = null,
    val bestReps: Int? = null,
    val distanceMeters: Double? = null,
)

@Serializable
data class PrHit(
    val exerciseName: String,
    val prType: PrType,
    val value: Double,
    val reps: Int? = null,
    val previousValue: Double? = null,
)

/** 历史记录列表项 */
@Serializable
data class SessionSummary(
    val id: Long,
    val planId: Long? = null,
    val planName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: SessionStatus,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val totalReps: Int,
    val prCount: Int = 0,
    /** 回收站：非 null 表示已删除（仅回收站列表使用） */
    val deletedAt: Long? = null,
)

/**
 * 完成一组的结果。
 * 注意：PR 命中列表可能为空（首次训练或未破纪录），这不是失败。
 */
data class SetLogResult(
    val setId: Long,
    val prHits: List<PrHit> = emptyList(),
    val restSeconds: Int = 0,
)

/** 容量预览（供 UI 顶部实时显示用，避免跨模块依赖计算器） */
internal object VolumePreview {
    fun totalVolume(exercises: List<ActiveExercise>, bodyWeightKg: Double?, includeWarmup: Boolean): Double =
        exercises.sumOf { ex ->
            ex.sets.sumOf { s ->
                if (!includeWarmup && s.setType == SetType.WARMUP) return@sumOf 0.0
                if (s.status != SetStatus.COMPLETED) return@sumOf 0.0
                val w = s.weightKg ?: 0.0
                when (ex.exercise.type) {
                    ExerciseType.WEIGHT_REPS -> w * (s.reps ?: 0)
                    ExerciseType.REPS_ONLY -> ((bodyWeightKg ?: 0.0) + w) * (s.reps ?: 0)
                    ExerciseType.WEIGHT_TIME -> w * ((s.durationSeconds ?: 0) / 60.0)
                    ExerciseType.WEIGHT_DISTANCE_TIME -> w * (s.distanceMeters ?: 0.0)
                    ExerciseType.TIME, ExerciseType.DISTANCE_TIME -> 0.0
                }
            }
        }
}
