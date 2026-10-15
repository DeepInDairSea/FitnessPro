package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType

/**
 * 容量计算。
 *
 * 容量（Volume）是衡量训练量的核心指标，单位统一为 kg。
 * 六种动作类型各有不同算法 —— 这是必须在领域层统一实现、由单元测试锁住的地方，
 * 绝不允许在 UI 或 SQL 里各写一份。
 */
object VolumeCalculator {

    /**
     * 单组容量。
     *
     * @param bodyWeightKg 自身体重。REPS_ONLY 类（引体、俯卧撑）的容量需要体重参与，
     *                     否则"75kg 的人做 8 个引体"容量为 0，统计就失真了。
     * @param includeWarmup 热身组是否计入容量。默认 false（PRD FR-07）。
     * @return 容量 kg。TIME / DISTANCE_TIME 类不参与重量容量，返回 0.0
     */
    fun setVolume(
        set: SetRecord,
        bodyWeightKg: Double?,
        includeWarmup: Boolean = false,
    ): Double {
        if (!includeWarmup && set.setType == SetType.WARMUP) return 0.0
        if (set.status != SetStatus.COMPLETED) return 0.0

        val weight = set.weightKg ?: 0.0
        // 统一使用 totalReps：单侧动作必须把左右次数相加，否则容量会少算一半
        val reps = set.totalReps
        return when (set.exerciseType) {
            ExerciseType.WEIGHT_REPS -> weight * reps

            // 自重动作：体重 + 附加重量（负重腰带时为正值，助力时为负值）
            ExerciseType.REPS_ONLY -> ((bodyWeightKg ?: 0.0) + weight) * reps

            // 按分钟计，避免以秒为单位时数值不可读
            ExerciseType.WEIGHT_TIME -> weight * ((set.durationSeconds ?: 0) / 60.0)

            ExerciseType.WEIGHT_DISTANCE_TIME -> weight * (set.distanceMeters ?: 0.0)

            // 纯时间 / 纯距离动作没有"重量容量"概念，单独统计总时长
            ExerciseType.TIME, ExerciseType.DISTANCE_TIME -> 0.0
        }
    }

    fun totalVolume(
        sets: List<SetRecord>,
        bodyWeightKg: Double?,
        includeWarmup: Boolean = false,
    ): Double = sets.sumOf { setVolume(it, bodyWeightKg, includeWarmup) }

    /** 单侧动作的左右次数之和；普通动作直接返回次数 */
    fun effectiveReps(set: SetRecord): Int = set.totalReps

    /** 总次数（用于训练总结） */
    fun totalReps(sets: List<SetRecord>): Int =
        sets.filter { it.status == SetStatus.COMPLETED }.sumOf { effectiveReps(it) }

    /** 总时长（秒），包含 TIME 类动作的计量时长 */
    fun totalDurationSeconds(sets: List<SetRecord>): Int =
        sets.filter { it.status == SetStatus.COMPLETED }.sumOf { it.durationSeconds ?: 0 }
}
