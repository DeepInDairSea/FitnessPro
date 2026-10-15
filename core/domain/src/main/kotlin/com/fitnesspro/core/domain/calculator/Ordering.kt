package com.fitnesspro.core.domain.calculator

/**
 * 排序索引计算。
 *
 * 为什么不用 Int + 全表重写：拖拽排序如果每次都把后续所有行的 orderIndex 加一，
 * 一次拖拽就是 N 次 UPDATE，且在并发下容易冲突。
 * 改用 Double 取相邻两项中值，正常情况下一次拖拽只需 1 次 UPDATE。
 * 当中值精度耗尽（相邻差值小于阈值）时抛 [NeedReindexException]，
 * 由调用方在事务里做一次全表规范化后重试。
 */
object OrderIndexCalculator {

    /** 相邻差值小于该值即认为精度不足 */
    const val REINDEX_THRESHOLD = 1e-6

    const val STEP = 1.0

    class NeedReindexException : IllegalStateException("orderIndex 精度不足，需要重新规范化")

    /**
     * 计算插入到 [prev] 与 [next] 之间应使用的新索引。
     *
     * @param prev 前一项的 orderIndex；null 表示插入到最前
     * @param next 后一项的 orderIndex；null 表示插入到最后
     */
    fun between(prev: Double?, next: Double?): Double {
        if (prev == null && next == null) return STEP
        if (prev == null) return next!! - STEP
        if (next == null) return prev + STEP

        require(next > prev) { "orderIndex 顺序异常：prev=$prev next=$next" }
        if (next - prev <= REINDEX_THRESHOLD) throw NeedReindexException()
        return (prev + next) / 2.0
    }

    /** 全表重新规范化：0.0, 1.0, 2.0, ... */
    fun reindexed(size: Int): List<Double> = List(size) { it * STEP }

    /** 移动到首位 */
    fun toFirst(current: List<Double>): Double {
        val min = current.minOrNull() ?: 0.0
        return min - STEP
    }

    /** 移动到末位 */
    fun toLast(current: List<Double>): Double {
        val max = current.maxOrNull() ?: 0.0
        return max + STEP
    }
}
