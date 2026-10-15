package com.fitnesspro.core.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 草稿新鲜度判定（C10 草稿恢复）。
 *
 * 判定只依赖 updatedAt/startedAt 与"现在"，不碰数据库与时钟实现，
 * 因此用纯 JVM 单测覆盖，不需要 Robolectric。
 */
class DraftRecoveryPolicyTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun draft(updatedAt: Long, startedAt: Long = now - day, completedSets: Int = 3) =
        WorkoutDraft(
            sessionId = 1L,
            planName = "推日",
            startedAt = startedAt,
            completedSets = completedSets,
            isResting = false,
            updatedAt = updatedAt,
        )

    @Test fun `刚中断的草稿 应可恢复`() {
        val d = draft(updatedAt = now - 60_000L)
        assertEquals(DraftFreshness.RESUMABLE, DraftRecoveryPolicy.freshness(d, now))
    }

    @Test fun `恰好落在保留期边界 应可恢复`() {
        val d = draft(updatedAt = now - DraftRecoveryPolicy.DEFAULT_MAX_AGE_MS)
        assertEquals(DraftFreshness.RESUMABLE, DraftRecoveryPolicy.freshness(d, now))
    }

    @Test fun `超过默认保留期 应视为过期`() {
        val d = draft(updatedAt = now - DraftRecoveryPolicy.DEFAULT_MAX_AGE_MS - 1)
        assertEquals(DraftFreshness.EXPIRED, DraftRecoveryPolicy.freshness(d, now))
    }

    @Test fun `没有状态行时 用会话更新时间兜底`() {
        // state 缺失：updatedAt 退化为 session.updatedAt，仍然新鲜
        val d = draft(updatedAt = now - day, startedAt = now - 2 * day)
        assertEquals(DraftFreshness.RESUMABLE, DraftRecoveryPolicy.freshness(d, now))
    }

    @Test fun `刚开始就被搁置很久 应视为过期`() {
        val d = draft(updatedAt = now - 30 * day, startedAt = now - 30 * day, completedSets = 0)
        assertEquals(DraftFreshness.EXPIRED, DraftRecoveryPolicy.freshness(d, now))
    }

    @Test fun `自定义保留期 应生效`() {
        val d = draft(updatedAt = now - 2 * day)
        assertEquals(
            DraftFreshness.EXPIRED,
            DraftRecoveryPolicy.freshness(d, now, maxAgeMillis = day),
        )
        assertEquals(
            DraftFreshness.RESUMABLE,
            DraftRecoveryPolicy.freshness(d, now, maxAgeMillis = 3 * day),
        )
    }

    @Test fun `时钟回拨导致的未来时间戳 应视为过期`() {
        val d = draft(updatedAt = now + day)
        assertEquals(DraftFreshness.EXPIRED, DraftRecoveryPolicy.freshness(d, now))
    }
}
