package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/**
 * 一组记录的"业务视图"。
 *
 * 这是容量计算、PR 判定、对比展示的统一输入 —— 计划侧（PlannedSet + 目标）与实际侧
 * （WorkoutSet）都映射到它，从而让计算器保持纯函数、可测试、不依赖任何持久化结构。
 */
@Serializable
data class SetRecord(
    val exerciseType: ExerciseType,
    val setType: SetType = SetType.NORMAL,
    val status: SetStatus = SetStatus.COMPLETED,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val leftReps: Int? = null,
    val rightReps: Int? = null,
    val rpe: Double? = null,
) {
    /** 是否为"有效完成"的正式组 —— PR 判定与容量的基础门槛 */
    val isWorkingAndCompleted: Boolean
        get() = setType != SetType.WARMUP && status == SetStatus.COMPLETED

    /** 总次数。单侧动作（保加利亚分腿蹲、单臂划船）用左右次数之和，否则就是 reps */
    val totalReps: Int
        get() = reps ?: ((leftReps ?: 0) + (rightReps ?: 0)).takeIf { it > 0 } ?: 0

    /**
     * 用于 1RM 估算的次数。
     * 单侧动作不能把左右次数相加后再估算 —— 那是两次独立的单侧发力，
     * 相加会把 1RM 算高一倍。所以取单侧次数（左或右，相等时任意）。
     */
    val repsForEstimation: Int?
        get() = reps ?: leftReps ?: rightReps
}
