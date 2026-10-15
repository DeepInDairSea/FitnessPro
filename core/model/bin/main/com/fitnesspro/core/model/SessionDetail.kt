package com.fitnesspro.core.model

/**
 * 一次训练的完整明细（用于历史记录的"复盘"页）。
 *
 * 为什么与 [WorkoutSummary] 分开：
 *  - `WorkoutSummary` 是**刚训练完**看到的总结（含 PR 命中、主观强度录入），是"庆祝页"
 *  - `SessionDetail` 是**事后回看**的数据（逐动作逐组的"目标 vs 实际"），是"复盘页"
 * 两者的字段重合度不高，硬塞进一个类会造出一堆 nullable 字段，让调用方到处判空。
 */
data class SessionDetail(
    val sessionId: Long,
    val planName: String,
    val status: SessionStatus,
    val startedAt: Long,
    val completedAt: Long?,
    val durationSeconds: Int,
    val pausedSeconds: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val totalReps: Int,
    val notes: String?,
    val perceivedEffort: Int?,
    val exercises: List<SessionExerciseDetail>,
) {
    val isCompleted: Boolean get() = status == SessionStatus.COMPLETED
}

data class SessionExerciseDetail(
    val id: Long,
    val name: String,
    val type: ExerciseType,
    val orderIndex: Double,
    val supersetTag: Int?,
    val restSeconds: Int,
    val status: ExerciseStatus,
    val notes: String?,
    val sets: List<SessionSetDetail>,
) {
    /** 该动作实际完成的正式组数（不含热身） */
    val completedWorkingSets: Int
        get() = sets.count { it.status == SetStatus.COMPLETED && it.setType != SetType.WARMUP }

    val volumeKg: Double
        get() = sets.filter { it.status == SetStatus.COMPLETED }
            .sumOf { (it.weightKg ?: 0.0) * it.totalReps }
}

data class SessionSetDetail(
    val id: Long,
    val setIndex: Int,
    val setType: SetType,
    val status: SetStatus,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val leftReps: Int?,
    val rightReps: Int?,
    val rpe: Double?,
    /** 来自计划的处方，用于"目标 vs 实际"对比 */
    val targetWeightKg: Double?,
    val targetReps: String?,
    val isPersonalRecord: Boolean,
) {
    val totalReps: Int
        get() = reps ?: ((leftReps ?: 0) + (rightReps ?: 0))

    /** 是否达成了处方目标（重量达到且次数落在区间内） */
    val achievedTarget: Boolean?
        get() {
            if (status != SetStatus.COMPLETED) return false
            val targetWeightOk = targetWeightKg == null ||
                (weightKg != null && weightKg + 0.001 >= targetWeightKg)
            val targetRepsOk = targetReps == null || totalReps >= parseRepsLow(targetReps)
            return targetWeightOk && targetRepsOk
        }

    private fun parseRepsLow(raw: String): Int =
        raw.split('-', '~').firstOrNull()?.trim()?.toIntOrNull() ?: 0
}
