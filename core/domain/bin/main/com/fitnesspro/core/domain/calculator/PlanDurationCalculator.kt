package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.DurationRange
import com.fitnesspro.core.model.PlanExerciseItem
import com.fitnesspro.core.model.SetType

/**
 * 计划总时长估算。
 *
 * v1.1 的公式 `Σ(每组次数 × 动作时长) + Σ(组间休息 × (组数-1))` 有三处错误：
 *  1. 未计入组内动作本身之外的切换开销
 *  2. 超级组内部动作之间**没有**休息，原公式会重复计入
 *  3. 对时长类动作（平板支撑）没有用 durationSeconds，而是硬套次数公式
 *
 * 修正后的算法见 DEV 文档 FR-08。输出区间而非精确值 —— 热身、找器械、聊天的时间无法预测。
 */
object PlanDurationCalculator {

    /** 若处方未给出单次动作时长，按每次 3 秒估算 */
    const val DEFAULT_SECONDS_PER_REP = 3

    /** 动作之间的换器械/调整开销 */
    const val EXERCISE_SWITCH_OVERHEAD_SECONDS = 5

    /** 时长估算的浮动区间 */
    private const val LOW_FACTOR = 0.90
    private const val HIGH_FACTOR = 1.15

    fun estimate(
        exercises: List<PlanExerciseItem>,
        planDefaultRestSeconds: Int?,
        globalDefaultRestSeconds: Int = 90,
    ): DurationRange {
        if (exercises.isEmpty()) return DurationRange(0, 0)

        val effectiveDefaultRest = planDefaultRestSeconds ?: globalDefaultRestSeconds

        val workAndRest = exercises.sumOf { ex ->
            exerciseSeconds(ex, effectiveDefaultRest)
        }
        // 超级组：每个超级组在"最后一个动作"之后才计一次休息
        val supersetTailRest = supersetTailRestSeconds(exercises, effectiveDefaultRest)
        val overhead = exercises.size * EXERCISE_SWITCH_OVERHEAD_SECONDS

        val total = workAndRest + supersetTailRest + overhead
        return DurationRange(
            lowSeconds = (total * LOW_FACTOR).toInt(),
            highSeconds = (total * HIGH_FACTOR).toInt(),
        )
    }

    private fun exerciseSeconds(exercise: PlanExerciseItem, defaultRest: Int): Int {
        val work = exercise.sets.sumOf { set ->
            set.targetDurationSeconds
                ?: RepsRange.parse(set.targetReps).midpoint?.times(DEFAULT_SECONDS_PER_REP)?.toInt()
                ?: RepsRange.parse(exercise.sets.firstOrNull()?.targetReps).midpoint
                    ?.times(DEFAULT_SECONDS_PER_REP)?.toInt()
                ?: 0
        }
        // 超级组内动作不计休息（由 supersetTailRestSeconds 统一在组尾计一次）
        val restCount = if (exercise.supersetTag == null) {
            maxOf(0, exercise.sets.size - 1)
        } else {
            0
        }
        return work + restCount * effectiveRest(exercise, defaultRest)
    }

    /** 超级组尾部休息：每个超级组只在最后一个动作之后计 (组数-1) 次休息 */
    private fun supersetTailRestSeconds(
        exercises: List<PlanExerciseItem>,
        defaultRest: Int,
    ): Int = exercises
        .filter { it.supersetTag != null }
        .groupBy { it.supersetTag }
        .values
        .sumOf { group ->
            val last = group.maxByOrNull { it.orderIndex } ?: return@sumOf 0
            val restCount = maxOf(0, last.sets.size - 1)
            restCount * effectiveRest(last, defaultRest)
        }

    private fun effectiveRest(exercise: PlanExerciseItem, defaultRest: Int): Int =
        (exercise.restSecondsOverride ?: defaultRest).coerceIn(0, 900)

    /** 热身组通常耗时更短，单独估算时可换算：忽略热身组的休息 */
    fun estimateWorkOnly(exercises: List<PlanExerciseItem>): Int = exercises.sumOf { ex ->
        ex.sets.filter { it.setType != SetType.WARMUP }.sumOf { set ->
            set.targetDurationSeconds
                ?: RepsRange.parse(set.targetReps).midpoint?.times(DEFAULT_SECONDS_PER_REP)?.toInt()
                ?: 0
        }
    }
}
