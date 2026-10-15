package com.fitnesspro.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.theme.FitnessTheme

/**
 * Soft UI 悬浮卡片：大圆角 + 柔和阴影 + 纯色容器。
 *
 * 三项都来自设计令牌而不是写死数值：
 *  - 圆角 `FitnessTheme.layout.cardRadius`（便当盒主题 26dp，其余 18dp）
 *  - 阴影 `FitnessTheme.layout.cardElevation`（便当盒主题 6dp，其余 0dp = 平面）
 *  - 内边距 `FitnessTheme.spacing.m`
 *
 * 阴影在浅灰画布上才看得出"浮起"，这也是便当盒主题把背景从绿色渐变
 * 换成中性浅灰的原因；两者缺一，卡片就会退回成一张普通色块。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    // 注意：本设计系统里 surfaceVariant 才是"卡片色"（surface 恒等于 background，
    // 用它当卡片底色会与画布同色，只剩阴影能区分，看起来就是"平"的）
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = contentColorFor(containerColor),
    contentPadding: PaddingValues = PaddingValues(FitnessTheme.spacing.m),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(FitnessTheme.layout.cardRadius)
    val elevation = FitnessTheme.layout.cardElevation

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = elevation,
            // tonalElevation 会在深色模式下把白卡染成灰紫，破坏"纯白悬浮卡"的观感
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        // 可点击卡片自动获得"按下去轻微收缩"的手感，不需要调用方操心
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = modifier.softPressedScale(interactionSource),
            onClick = onClick,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = elevation,
            tonalElevation = 0.dp,
            interactionSource = interactionSource,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
