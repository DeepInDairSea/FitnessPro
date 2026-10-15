package com.fitnesspro.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.theme.FitnessTheme

/**
 * 品牌顶栏：绿底标题区，48dp 紧凑高度。
 *
 * 不用 M3 TopAppBar 的两个原因：
 *  1. 高度写死 64dp 且不可调（顶部占用太多屏幕）
 *  2. 没有独立的"顶栏"色彩角色，surface 染绿会全局走样
 *
 * 视觉只在此处定义：绿底取 FitnessColors.appBar，前景取 onAppBar。
 * 状态栏区域保持同色（windowInsetsPadding 把内容压到状态栏之下）。
 *
 * 便当盒主题下换成「扁平中性标题区」：不画色条，直接用画布色，
 * 靠更大的标题字重做层次 —— 参考图里标题就是直接放在画布上的。
 */
@Composable
fun FitnessTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val fitnessColors = FitnessTheme.colors
    val bento = FitnessTheme.layout.bentoGrid

    val container = if (bento) MaterialTheme.colorScheme.background else fitnessColors.appBar
    val onContainer = if (bento) MaterialTheme.colorScheme.onBackground else fitnessColors.onAppBar

    Surface(color = container, modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(if (bento) 56.dp else 48.dp)
                    .padding(horizontal = if (bento) 20.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon?.let { nav ->
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { nav() }
                }
                ProvideTextStyle(
                    if (bento) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                ) {
                    Box(Modifier.weight(1f)) { title() }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        }
    }
}