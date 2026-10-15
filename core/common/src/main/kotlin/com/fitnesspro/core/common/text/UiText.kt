package com.fitnesspro.core.common.text

import androidx.annotation.StringRes

/**
 * 可展示文本。
 *
 * 存在的理由：ViewModel 与领域层不能持有 Context，但错误信息又需要本地化。
 * 因此在 ViewModel 里只构造 UiText，真正的取字符串发生在 Compose 层
 * （core:designsystem 的 asString()）。
 */
sealed interface UiText {
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any): UiText = Resource(resId, args.toList())
        fun plain(value: String): UiText = Plain(value)
    }
}

/** 一次性 UI 错误：消费后必须置空，避免旋转屏幕后重复弹窗 */
data class UiError(
    val code: String,
    val text: UiText,
    val retryable: Boolean = true,
)
