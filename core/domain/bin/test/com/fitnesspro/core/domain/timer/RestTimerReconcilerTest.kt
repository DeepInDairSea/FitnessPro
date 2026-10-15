package com.fitnesspro.core.domain.timer

import com.fitnesspro.core.model.RestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 休息计时的断点恢复与时钟异常兜底（DEV WBS M4-15）。
 *
 * 这些用例覆盖的都是**真实发生过或极易发生**的场景：
 *  - 进程被杀后重新进入训练页
 *  - 用户在休息中途按了暂停，然后杀进程
 *  - 用户为了解锁某个功能把系统时间往前调
 *  - 息屏 10 分钟后回到 App（休息早就结束了）
 *
 * 判定只要错了任意一条，用户看到的倒计时就是错的，
 * 而"计时器不可信"会直接摧毁整个训练记录的可信度。
 */
class RestTimerReconcilerTest {

    private val now = 1_700_000_000_000L

    // ------------------------------------------------------------------
    // 基线：没有在休息
    // ------------------------------------------------------------------

    @Test
    fun `没有 endsAt 也没有暂停值 时 应视为未在休息`() {
        val state = reconcile(endsAtEpochMs = null, pausedRemainingMs = null)
        assertEquals(RestState.Idle, state)
    }

    // ------------------------------------------------------------------
    // 正常计时
    // ------------------------------------------------------------------

    @Test
    fun `终点在未来 时 应恢复为计时中并保留动作名与下一组提示`() {
        val state = reconcile(
            endsAtEpochMs = now + 45_000,
            pausedRemainingMs = null,
            exerciseName = "卧推",
            nextSetHint = "60 × 8",
        )

        assertTrue(state is RestState.Running)
        state as RestState.Running
        assertEquals(now + 45_000, state.endsAtEpochMs)
        assertEquals(45, state.plannedSeconds)
        assertEquals("卧推", state.exerciseName)
        assertEquals("60 × 8", state.nextSetHint)
        assertEquals(45_000L, state.remainingMs(now))
        assertEquals(false, state.isPaused)
    }

    @Test
    fun `剩余不足 1 秒 时 plannedSeconds 应向上取整为 1 而不是 0`() {
        val state = reconcile(endsAtEpochMs = now + 1, pausedRemainingMs = null)

        state as RestState.Running
        // 显示 0 秒但计时器还没结束，是用户最容易注意到的显示 bug
        assertEquals(1, state.plannedSeconds)
    }

    @Test
    fun `剩余时间带零头 时 plannedSeconds 应向上取整`() {
        val state = reconcile(endsAtEpochMs = now + 90_001, pausedRemainingMs = null)

        state as RestState.Running
        assertEquals(91, state.plannedSeconds)
    }

    // ------------------------------------------------------------------
    // 断点恢复：休息早已结束
    // ------------------------------------------------------------------

    @Test
    fun `终点已过 时 应恢复为已结束 而不是静默回到未在休息`() {
        val state = reconcile(
            endsAtEpochMs = now - 1,
            pausedRemainingMs = null,
            exerciseName = "深蹲",
        )

        // Finished 会显式提示"休息已结束"；返回 Idle 会让用户以为计时器丢了
        assertEquals(RestState.Finished, state)
    }

    @Test
    fun `息屏很久后回到 App 时 应恢复为已结束`() {
        val state = reconcile(endsAtEpochMs = now - 10 * 60 * 1000, pausedRemainingMs = null)
        assertEquals(RestState.Finished, state)
    }

    // ------------------------------------------------------------------
    // 暂停中杀进程 / 暂停中重启
    // ------------------------------------------------------------------

    @Test
    fun `暂停中恢复 时 应保持暂停且以冻结值作为唯一真理`() {
        val state = reconcile(endsAtEpochMs = null, pausedRemainingMs = 30_000)

        state as RestState.Running
        assertTrue(state.isPaused)
        assertEquals(30_000L, state.pausedRemainingMs)
        assertEquals(30, state.plannedSeconds)
        // 暂停期间剩余时间不随时间流逝
        assertEquals(30_000L, state.remainingMs(now))
        assertEquals(30_000L, state.remainingMs(now + 60_000))
    }

    @Test
    fun `暂停剩余为零 时 应视为已结束`() {
        val state = reconcile(endsAtEpochMs = null, pausedRemainingMs = 0L)
        assertEquals(RestState.Finished, state)
    }

    @Test
    fun `暂停剩余为负 时 应视为已结束`() {
        val state = reconcile(endsAtEpochMs = null, pausedRemainingMs = -5_000L)
        assertEquals(RestState.Finished, state)
    }

    @Test
    fun `同时存在暂停值与终点 时 以暂停值为准`() {
        // 正常流程里暂停会清掉 endsAt，但历史数据或异常写入都可能让两者并存。
        // 用户看到的应该是"停在暂停的地方"，而不是拿一个可能已过期的终点去算。
        val state = reconcile(endsAtEpochMs = now - 60_000, pausedRemainingMs = 20_000)

        state as RestState.Running
        assertTrue(state.isPaused)
        assertEquals(20_000L, state.pausedRemainingMs)
    }

    // ------------------------------------------------------------------
    // 时钟异常兜底
    // ------------------------------------------------------------------

    @Test
    fun `用户把系统时间调早 导致剩余超过上限 时 应放弃计时`() {
        // 用户把时间往回调了 1 天：endsAt - now 变成 24 小时以上
        val state = reconcile(endsAtEpochMs = now + 24 * 60 * 60 * 1000, pausedRemainingMs = null)

        // 否则计时器会永久卡住，用户以为自己坏了
        assertEquals(RestState.Idle, state)
    }

    @Test
    fun `剩余刚好等于上限 时 仍应正常计时`() {
        val state = reconcile(
            endsAtEpochMs = now + RestTimerReconciler.MAX_REST_MS,
            pausedRemainingMs = null,
        )

        state as RestState.Running
        assertEquals(900, state.plannedSeconds)
    }

    @Test
    fun `剩余超过上限 1 毫秒 时 应放弃计时`() {
        val state = reconcile(
            endsAtEpochMs = now + RestTimerReconciler.MAX_REST_MS + 1,
            pausedRemainingMs = null,
        )
        assertEquals(RestState.Idle, state)
    }

    @Test
    fun `时钟被调晚很多 时 终点已过仍应判为已结束而不是未在休息`() {
        // 判定顺序要求"剩余 <= 0"优先于上限判断：
        // 否则一个负得很多的剩余时间会走进上限分支，被误判成 Idle，
        // 用户就看不到"休息已结束"这个本该出现的提示
        val state = reconcile(
            endsAtEpochMs = now - 48 * 60 * 60 * 1000,
            pausedRemainingMs = null,
        )
        assertEquals(RestState.Finished, state)
    }

    @Test
    fun `剩余等于注入的上限 时 仍应正常计时`() {
        val state = reconcile(
            endsAtEpochMs = now + 60_000,
            pausedRemainingMs = null,
            maxRestMs = 60_000,
        )
        assertTrue(state is RestState.Running)
    }

    // ------------------------------------------------------------------
    // 幂等性 / 唯一真理
    // ------------------------------------------------------------------

    @Test
    fun `同一份持久化字段反复重建 结果必须完全一致`() {
        val endsAt = now + 30_000
        val first = reconcile(endsAtEpochMs = endsAt, pausedRemainingMs = null)
        val second = reconcile(endsAtEpochMs = endsAt, pausedRemainingMs = null)
        val third = reconcile(endsAtEpochMs = endsAt, pausedRemainingMs = null)

        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `剩余时间只由终点与当前时刻决定 与重建次数无关`() {
        val endsAt = now + 60_000
        // 每次重建都传入同一时刻：即使重建 100 次，剩余时间也不能被"消耗"
        repeat(100) {
            val state = reconcile(endsAtEpochMs = endsAt, pausedRemainingMs = null)
            state as RestState.Running
            assertEquals(60_000L, state.remainingMs(now))
        }
    }

    private fun reconcile(
        endsAtEpochMs: Long?,
        pausedRemainingMs: Long?,
        exerciseName: String = "",
        nextSetHint: String = "",
        maxRestMs: Long = RestTimerReconciler.MAX_REST_MS,
    ): RestState = RestTimerReconciler.reconcile(
        nowEpochMs = now,
        endsAtEpochMs = endsAtEpochMs,
        pausedRemainingMs = pausedRemainingMs,
        exerciseName = exerciseName,
        nextSetHint = nextSetHint,
        maxRestMs = maxRestMs,
    )
}
