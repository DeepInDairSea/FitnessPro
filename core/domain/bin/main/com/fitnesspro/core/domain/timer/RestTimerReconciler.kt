package com.fitnesspro.core.domain.timer

import com.fitnesspro.core.model.RestState

/**
 * 休息计时状态的**重建**逻辑。
 *
 * ## 为什么这段逻辑值得单独存在
 *
 * 休息计时是全项目唯一"正确性不能依赖后台机制"的地方（DEV §8.3）：
 * 通知丢了、闹钟延迟了、进程被杀、用户把系统时间改到了 2030 年 ——
 * 这些情况下计时器都必须给出**正确**的剩余时间，而不是卡住或乱跳。
 *
 * 唯一的真理是持久化的 `endsAtEpochMs`，其余全部由它推导。
 * 因此"从持久化字段推导出当前应该显示什么"是一个纯函数：
 * 给定 `now`、`endsAt`、冻结的剩余毫秒，输出 [RestState]。
 *
 * 把它放在 `:core:domain` 而不是 `WorkoutSessionManager` 的私有方法里，
 * 是因为它必须能被穷举测试：时钟被改、闹钟迟到、重启、暂停中返回……
 * 每一种都在下面 [reconcile] 的分支里有明确归属。
 */
object RestTimerReconciler {

    /** 休息上限 15 分钟。超过它只可能是时钟异常，不是用户真的在休息 */
    const val MAX_REST_MS: Long = 15L * 60 * 1000

    /**
     * 由持久化字段重建当前休息状态。
     *
     * 判定顺序（顺序本身是需求的一部分）：
     *  1. **暂停态优先**：用户主动暂停时 [pausedRemainingMs] 是真理，
     *     `endsAt` 已被清空，此时不应再去看它。
     *  2. 暂停剩余 ≤ 0 → [RestState.Finished]（暂停期间被外部改小过）。
     *  3. 无 `endsAt` → [RestState.Idle]（从未开始过休息）。
     *  4. 剩余 ≤ 0 → [RestState.Finished]。**注意不是 Idle**：
     *     用户回到训练页时需要知道"休息已经结束"，静默回到 Idle 会让它以为计时器丢了。
     *  5. 剩余 > [maxRestMs] → [RestState.Idle]。这是**时钟异常兜底**：
     *     用户把系统时间往前调，会让 `endsAt - now` 变成一个巨大的数，
     *     如果照单全收，计时器就永久卡住了。宁可当作没在休息。
     *  6. 否则 → [RestState.Running]，`plannedSeconds` 由剩余时间向上取整得出
     *     （它只用于画进度圈，不参与正确性）。
     *
     * @param nowEpochMs 当前时间（由 `TimeProvider` 提供，便于测试注入）
     * @param endsAtEpochMs 休息终点；null 表示未在休息
     * @param pausedRemainingMs 暂停时冻结的剩余毫秒；非 null 表示处于暂停态
     * @param maxRestMs 异常上限，默认 [MAX_REST_MS]
     */
    fun reconcile(
        nowEpochMs: Long,
        endsAtEpochMs: Long?,
        pausedRemainingMs: Long?,
        exerciseName: String = "",
        nextSetHint: String = "",
        maxRestMs: Long = MAX_REST_MS,
    ): RestState {
        // 1./2. 暂停态
        if (pausedRemainingMs != null) {
            if (pausedRemainingMs <= 0L) return RestState.Finished
            return RestState.Running(
                endsAtEpochMs = nowEpochMs + pausedRemainingMs,
                plannedSeconds = secondsCeil(pausedRemainingMs),
                pausedRemainingMs = pausedRemainingMs,
                exerciseName = exerciseName,
                nextSetHint = nextSetHint,
            )
        }

        // 3. 从未开始
        val endsAt = endsAtEpochMs ?: return RestState.Idle

        val remaining = endsAt - nowEpochMs
        return when {
            // 4. 已经结束（含用户把系统时间往后调的情况：remaining 变成负数）
            remaining <= 0L -> RestState.Finished
            // 5. 时钟异常：系统时间被往前调，剩余时间离谱地大
            remaining > maxRestMs -> RestState.Idle
            // 6. 正常计时中
            else -> RestState.Running(
                endsAtEpochMs = endsAt,
                plannedSeconds = secondsCeil(remaining),
                exerciseName = exerciseName,
                nextSetHint = nextSetHint,
            )
        }
    }

    /** 毫秒向上取整到秒：还剩 1ms 也要显示 1 秒，显示 0 秒但计时器没结束是明显的 bug */
    private fun secondsCeil(millis: Long): Int = ((millis + 999L) / 1000L).toInt()
}
