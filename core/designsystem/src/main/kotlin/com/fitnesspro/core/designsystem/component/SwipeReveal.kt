package com.fitnesspro.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 左滑显露出的单个动作按钮 */
data class RevealAction(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

/**
 * 左滑显露操作（QQ / 邮件列表那种）：向右滑动内容，露出背后的动作按钮。
 *
 * 为什么不用 `SwipeToDismissBox`：它表达的是"滑到底 = 立即执行"，
 * 而本应用的动作里包含**删除**与**收藏**两种语义完全不同的操作，
 * 滑到底就执行会误删。这里改成"滑开 → 点按钮 →（删除再二次确认）"，
 * 和成熟 IM/邮箱的交互一致：手势只负责"把按钮请出来"。
 *
 * 实现只用 `draggable` + `Animatable`：手势与吸附动画都可控，
 * 不依赖会随 Compose 版本变动的锚点 API。
 */
@Composable
fun SwipeRevealBox(
    actions: List<RevealAction>,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 76.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }

    val scope = rememberCoroutineScope()
    val maxOffsetPx = with(LocalDensity.current) { (actionWidth * actions.size).toPx() }
    // 用 Animatable 而不是 animateFloatAsState：需要在拖动中直接 snapTo 跟手
    val offset = remember { Animatable(0f) }

    Box(modifier) {
        // 背后的动作层：始终贴在右侧，被内容盖住，滑开多少露多少
        // 必须用 matchParentSize：fillMaxSize 会参与 Box 尺寸计算，把列表项撑成一屏高
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                RevealActionButton(
                    action = action,
                    width = actionWidth,
                    onDone = { scope.launch { offset.animateTo(0f, spring()) } },
                )
            }
        }

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(offset.value.roundToInt(), 0)
                    }
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-maxOffsetPx, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        // 过半即吸附打开；快速一甩也打开（速度阈值），否则回弹
                        val open = offset.value < -maxOffsetPx / 2f || velocity < -900f
                        scope.launch {
                            offset.animateTo(
                                targetValue = if (open) -maxOffsetPx else 0f,
                                animationSpec = spring(),
                            )
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun RevealActionButton(
    action: RevealAction,
    width: Dp,
    onDone: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(FitnessTheme.layout.cardRadius))
            .background(action.containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                onDone()
                action.onClick()
            }
            .padding(vertical = FitnessTheme.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            color = action.contentColor,
            maxLines = 2,
        )
    }
}

/** 常用动作：收藏 / 取消收藏（非破坏性，点了立即生效） */
@Composable
fun favoriteRevealAction(
    isFavorite: Boolean,
    onToggle: () -> Unit,
): RevealAction = RevealAction(
    label = stringResource(
        if (isFavorite) R.string.common_unfavorite else R.string.common_favorite,
    ),
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick = onToggle,
)

/** 常用动作：删除（破坏性，调用方负责再弹一次确认） */
@Composable
fun deleteRevealAction(onDelete: () -> Unit): RevealAction = RevealAction(
    label = stringResource(R.string.common_delete),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    onClick = onDelete,
)
