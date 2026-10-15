package com.fitnesspro.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.model.MediaRef
import java.io.File

// ---------------------------------------------------------------------------
// 三态：空 / 加载 / 错误
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FitnessTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null, // 纯装饰
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.m))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(FitnessTheme.spacing.l))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FitnessTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(FitnessTheme.spacing.m))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** 骨架屏：形状与真实条目一致，避免数据到达时布局跳动 */
@Composable
fun SkeletonList(itemCount: Int = 5, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FitnessTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
    ) {
        repeat(itemCount) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 容器
// ---------------------------------------------------------------------------

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) { content() }
    }
}

@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor ?: MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** PR 徽标：图标 + 文字双通道，不只靠颜色传达状态（无障碍要求 NFR-A4） */
@Composable
fun PrBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = FitnessTheme.colors
    val desc = stringResource(R.string.cd_pr_badge)
    Surface(
        modifier = modifier.semantics { contentDescription = desc },
        shape = RoundedCornerShape(6.dp),
        color = colors.prHighlight.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.prHighlight,
            )
            if (!compact) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "PR",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.prHighlight,
                )
            }
        }
    }
}

/** 组状态圆点 + 无障碍描述：颜色之外必须有语义 */
@Composable
fun SetStatusDot(
    completed: Boolean,
    modifier: Modifier = Modifier,
    isPersonalRecord: Boolean = false,
) {
    val colors = FitnessTheme.colors
    val desc = stringResource(
        if (completed) R.string.cd_set_completed else R.string.cd_set_pending
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                when {
                    isPersonalRecord -> colors.prHighlight
                    completed -> colors.setCompleted
                    else -> colors.setPending
                }
            )
            .semantics { contentDescription = desc },
    )
}

// ---------------------------------------------------------------------------
// 数值输入
// ---------------------------------------------------------------------------

/**
 * 数值步进输入。
 *
 * 训练场景下用户常常只调 2.5kg，用 ± 按钮比调出键盘快得多；
 * 长按连续加减则用于大跨度调整。这是"完成一组 ≤ 2 次点击"（PRD AC-2）的关键组件。
 */
@Composable
fun NumberStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    placeholder: String = "—",
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val touch = FitnessTheme.spacing.touchTargetMin
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onStepDown,
            enabled = enabled,
            modifier = Modifier.size(touch),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少${contentDescription.orEmpty()}")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(touch),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = numericTextStyle(NumericSize.Medium).copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = numericTextStyle(NumericSize.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (suffix != null) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        suffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(
            onClick = onStepUp,
            enabled = enabled,
            modifier = Modifier.size(touch),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加${contentDescription.orEmpty()}")
        }
    }
}

// ---------------------------------------------------------------------------
// 对话框
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.common_confirm),
    dismissLabel: String = stringResource(R.string.common_cancel),
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

// ---------------------------------------------------------------------------
// 媒体缩略图
// ---------------------------------------------------------------------------

/**
 * 统一缩略图。
 *
 * 关键点：**只加载预生成的缩略图，绝不直接解码视频原文件**。
 * 直接解码视频首帧会让列表滚动掉帧（与 PRD NFR-P5 自相矛盾）。
 */
@Composable
fun ExerciseThumbnail(
    media: MediaRef?,
    mediaRootDir: File?,
    modifier: Modifier = Modifier,
    size: Int = 64,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val desc = stringResource(R.string.cd_exercise_thumbnail)
    val shape = RoundedCornerShape(10.dp)

    val model: Any? = when (media) {
        null -> null
        is MediaRef.Local -> {
            val fileName = media.thumbnailName ?: media.fileName.takeIf {
                media.type.name == "GIF" || media.type.name == "IMAGE"
            }
            if (fileName == null || mediaRootDir == null) {
                File(mediaRootDir ?: context.filesDir, fileName ?: "")
            } else {
                File(mediaRootDir, fileName)
            }
        }
        is MediaRef.Remote -> media.url
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size / 2).dp),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = desc,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
