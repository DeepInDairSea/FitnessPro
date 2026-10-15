package com.fitnesspro.core.domain.workout

/**
 * 训练草稿：进程被杀/退出后仍未结束的训练。
 *
 * 数据来自 `workout_session`（IN_PROGRESS）+ `workout_session_state`（光标与休息计时），
 * 本身不需要额外建表；这里只做"是否值得继续"的判断与展示用的摘要。
 */
data class WorkoutDraft(
    val sessionId: Long,
    val planName: String,
    val startedAt: Long,
    /** 已完成组数：为 0 说明刚开练就退出，提示语会区分 */
    val completedSets: Int,
    /** 退出时是否停在休息计时中 */
    val isResting: Boolean,
    /**
     * 最后一次落库时间（会话更新或光标/计时更新的较新者）。
     * 注意：不要用 startedAt 兜底 —— 开练后立刻搁置的场景里，
     * startedAt 会比 updatedAt 更新，反而把过期草稿判成新鲜。
     */
    val updatedAt: Long,
)

/** 草稿新鲜度判定结果 */
enum class DraftFreshness {
    /** 近期中断，值得提示继续 */
    RESUMABLE,

    /** 过期太久（或从未有进度），不再主动打扰用户 */
    EXPIRED,
}

/**
 * 草稿恢复策略：纯函数，可脱离 Android 单测。
 *
 * 过期判定用 updatedAt 而不是 startedAt：用户练了一半中途休息很久，
 * 只要期间有过落库就说明会话还"活着"；反之开了训练立刻退出又放了一周，
 * 再弹恢复提示没有意义。
 */
object DraftRecoveryPolicy {

    /** 默认最长保留期：7 天 */
    const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun freshness(
        draft: WorkoutDraft,
        now: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MS,
    ): DraftFreshness {
        // 只看 updatedAt：startedAt 只用于展示，参与取最大会把陈旧草稿误判为新鲜
        val age = now - draft.updatedAt
        return if (age in 0..maxAgeMillis) DraftFreshness.RESUMABLE else DraftFreshness.EXPIRED
    }

    fun isResumable(
        draft: WorkoutDraft,
        now: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MS,
    ): Boolean = freshness(draft, now, maxAgeMillis) == DraftFreshness.RESUMABLE
}
