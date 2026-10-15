package com.fitnesspro.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 便当盒排版的公共构件。
 *
 * 这些函数本身对所有主题都安全：间距取 `FitnessTheme.layout.cardGap`，
 * 非便当盒主题下 gap = 16dp、无阴影、无动效，视觉与改动前一致；
 * 只有 BENTO_SOFT 主题才会因为令牌不同而变成"大圆角 + 悬浮 + 错落入场"。
 */

/** 便当盒行：统一卡片沟槽，避免每处手写 spacedBy */
@Composable
fun BentoRow(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** 便当盒列：统一卡片沟槽 */
@Composable
fun BentoColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
        content = content,
    )
}

/**
 * 便当盒数据小块：饱和底色 + 白字。
 *
 * 参考图里最有辨识度的元素就是这种"实心色块"，用它把关键数字从白卡里拎出来。
 */
@Composable
fun BentoStatTile(
    label: String,
    value: String,
    unit: String? = null,
    container: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
) {
    val shape = RoundedCornerShape(FitnessTheme.layout.cardRadius)
    Surface(
        modifier = modifier,
        shape = shape,
        // 底色交给渐变画笔：纯色块显得"塑料"，加了高光到暗角的过渡才有实体感
        color = Color.Transparent,
        contentColor = contentColor,
        shadowElevation = FitnessTheme.layout.cardElevation,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(softBlockBrush(container), shape)
                .softNoise()
                .padding(FitnessTheme.spacing.m),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    unit?.let {
                        Spacer(Modifier.width(FitnessTheme.spacing.xs))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 白卡数字块：标签 + 强调色大数字。
 *
 * 用于"不需要色块吸引注意、但数字本身就是重点"的格子 ——
 * 一屏里全是饱和色块会互相打架，留几张白卡才能让色块重新变成重点。
 */
@Composable
fun BentoNumberCard(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FitnessTheme.layout.cardRadius)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = FitnessTheme.layout.cardElevation,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(FitnessTheme.spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(accent, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                unit?.let {
                    Spacer(Modifier.width(FitnessTheme.spacing.xs))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** 实心色块的质感画笔：左上高光 → 本色 → 右下暗角 */
fun softBlockBrush(base: Color): Brush = Brush.linearGradient(
    colors = listOf(
        lerp(base, Color.White, 0.18f),
        base,
        lerp(base, Color.Black, 0.12f),
    ),
)

/**
 * 细噪点：给纯色块加一层颗粒，避免大色块在深色/浅色下都显得"塑料"。
 *
 * 用固定种子生成点位并缓存在 `drawWithCache` 里：图案稳定不闪烁，
 * 每帧只重放已有的点，不会因为滚动导致性能抖动。点数有上限，
 * 超大色块也不会退化成几万个圆。
 */
fun Modifier.softNoise(
    alpha: Float = 0.05f,
    seed: Int = 21,
    maxDots: Int = 900,
): Modifier = this.drawWithCache {
    val step = 11.dp.toPx()
    val random = Random(seed)
    val count = (((size.width / step).toInt() + 1) * ((size.height / step).toInt() + 1))
        .coerceIn(0, maxDots)
    val dots = List(count) {
        Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
    }
    val radius = 0.9.dp.toPx()
    onDrawBehind {
        dots.forEach { drawCircle(Color.White.copy(alpha = alpha), radius, it) }
    }
}

/**
 * 便当盒内容卡：标题 + 可选右侧操作 + 内容（图表/列表）。
 *
 * 图表类区块统一用它包裹，保证"每张图都住在一张悬浮卡里"。
 */
@Composable
fun BentoSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            trailing?.invoke()
        }
        Spacer(Modifier.height(FitnessTheme.spacing.s))
        Column(content = content)
    }
}

/**
 * 按压微缩放：Soft UI 的"软"很大一部分来自触感 —— 按下去轻微收缩再弹回。
 *
 * 用 spring 而不是 tween：松手回弹有过冲，手感更"Q"。
 */
@Composable
fun Modifier.softPressedScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "softPressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 错峰入场：卡片自上而下依次淡入并轻微上移。
 *
 * 只在便当盒主题下生效 —— 其它主题保持"瞬间出现"，
 * 避免给老用户一种整个应用突然变慢的错觉。
 *
 * @param index 同一屏内的顺序，用来错开延迟（每格 45ms，最多错到 400ms）
 */
@Composable
fun Modifier.bentoAppear(index: Int = 0): Modifier =
    if (!FitnessTheme.layout.bentoGrid) {
        this
    } else {
        bentoAppearAnimated(index)
    }

@Composable
private fun Modifier.bentoAppearAnimated(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { 26.dp.toPx() }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(400L))
        progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
