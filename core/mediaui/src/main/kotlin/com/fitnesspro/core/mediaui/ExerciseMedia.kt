package com.fitnesspro.core.mediaui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fitnesspro.core.media.MediaFileStore
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import java.io.File

/**
 * 动作演示媒体的**唯一渲染入口**。
 *
 * 之前只有一张详情页用了缩略图，计划里挂的 mediaOverride、训练执行页、动作库列表
 * 全都看不到演示资源 —— 用户会以为"存了没生效"。所以收敛成这一个组件，
 * 所有界面都用它，行为（占位、缺失提示、可点击）保证一致。
 *
 * 三态语义必须显式表达：
 *  - 有文件 → 优先加载预生成的缩略图（绝不解码视频原文件，否则列表滚动掉帧）
 *  - 外链 → 交给 Coil 拉网络封面
 *  - 文件丢失 → 显示"演示文件丢失 + 重新关联"，而不是空白。
 *    文件丢失是**会发生**的（用户清了缓存、备份恢复缺文件），必须可恢复。
 */
@Composable
fun ExerciseMedia(
    media: MediaRef?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    /** 文件丢失时的"重新关联"入口；为 null 时只显示占位 */
    onRelink: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val mediaDir = remember { File(context.filesDir, MediaFileStore.DIR_MEDIA) }
    val thumbDir = remember { File(context.filesDir, MediaFileStore.DIR_THUMBS) }

    val shape = RoundedCornerShape(10.dp)
    var loadFailed by remember(media) { mutableStateOf(false) }

    // 分层解码：小尺寸一律走预生成的缩略图，绝不解码原文件。
    // 磁盘上只有"媒体文件 + 缩略图"两份，展示层再分一次工——
    // 列表/卡片解码 4000×3000 的图会掉帧甚至 OOM，而 512px 缩略图在 200dp 内完全够看。
    val thumbOnly = size <= THUMB_ONLY_MAX_SIZE
    val originFile = (media as? MediaRef.Local)?.let { File(mediaDir, it.fileName) }
    val thumbFile = (media as? MediaRef.Local)?.thumbnailName
        ?.let { File(thumbDir, it) }
        ?.takeIf { it.exists() }

    val model: Any? = when (media) {
        null -> null
        is MediaRef.Remote -> media.url
        is MediaRef.Local -> when {
            thumbFile != null -> thumbFile
            // 没有缩略图时：小尺寸宁可显示占位（等下次导入补缩略图），也不解码原文件；
            // 大尺寸（用户主动要看清动作要领）才用原文件，视频除外——视频原文件交给播放器
            !thumbOnly && media.type != MediaType.VIDEO_LOCAL ->
                originFile?.takeIf { it.exists() }
            else -> null
        }
    }

    val missing = media is MediaRef.Local && originFile?.exists() != true

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            missing -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = stringResource(R.string.media_missing_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size((size.value / 2).dp),
                    )
                    if (onRelink != null) {
                        Text(
                            stringResource(R.string.media_missing_action),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable(onClick = onRelink),
                        )
                    }
                }
            }

            model == null || loadFailed -> {
                Icon(
                    imageVector = if (media is MediaRef.Remote) {
                        Icons.Outlined.PlayCircle
                    } else {
                        Icons.Outlined.Image
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((size.value / 2).dp),
                )
            }

            else -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_exercise_media),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        loadFailed = state is coil3.compose.AsyncImagePainter.State.Error
                    },
                )
            }
        }
    }
}

/** 列表项里的紧凑尺寸 */
val MEDIA_SIZE_SMALL: Dp = 44.dp

/** 详情页/编辑页的展示尺寸 */
val MEDIA_SIZE_LARGE: Dp = 96.dp

/** 全屏查看尺寸：只有它才会解码原文件 */
val MEDIA_SIZE_FULL: Dp = 320.dp

/**
 * 该尺寸及以下一律使用缩略图。
 * 96dp@3x = 288px，缩略图长边 512px 足够；超过这个尺寸（例如全屏）才需要原始质量。
 */
private val THUMB_ONLY_MAX_SIZE: Dp = 200.dp
