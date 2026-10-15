package com.fitnesspro.core.mediaui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * 屏幕方向模式。
 *
 * 播放页默认竖屏（演示内容多数是竖拍的），但用户必须能切到横屏 ——
 * 左右两个方向都要支持（有人习惯 Home 键在左，有人习惯在右），
 * 还要能交还给系统传感器（"随系统"），不能把方向锁死。
 */
enum class MediaOrientationMode(val orientation: Int) {
    PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE_LEFT(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_RIGHT(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    SYSTEM(ActivityInfo.SCREEN_ORIENTATION_SENSOR),
}

@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val fileName: String? = savedStateHandle["fileName"]
    val url: String? = savedStateHandle["url"]
    val title: String = savedStateHandle["title"] ?: ""
    val kind: MediaType = MediaType.entries.firstOrNull { it.name == savedStateHandle["kind"] }
        ?: MediaType.VIDEO_LOCAL

    val isVideo: Boolean get() = kind == MediaType.VIDEO_LOCAL

    /** 本地文件是否存在（外链不做存在性判断） */
    val fileExists: Boolean
        get() {
            val name = fileName ?: return true
            return File(File(appContext.filesDir, "media"), name).exists()
        }

    /**
     * ExoPlayer 放在 ViewModel 而不是 Composable：
     * 旋转会重建 Activity（本项目没有给 Activity 声明 configChanges），
     * 而 ViewModel 在配置变更中存活 —— 播放器不重建，播放位置就不断。
     */
    val player: ExoPlayer? = if (isVideo) {
        val uri = fileName?.let { android.net.Uri.fromFile(File(File(appContext.filesDir, "media"), it)) }
            ?: url?.let { android.net.Uri.parse(it) }
        if (uri == null) {
            null
        } else {
            ExoPlayer.Builder(appContext).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                // 演示动作需要反复看：单条循环，看完自动重播
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        }
    } else {
        null
    }

    override fun onCleared() {
        player?.release()
        super.onCleared()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 全屏演示播放页：视频（Media3）、动图（GIF/动态 WebP）、图片（可缩放查看）。
 *
 * 三种内容共用一个入口，是因为用户在训练中只想"看一眼这个动作怎么做"，
 * 不该关心它是 mp4 还是 GIF。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: MediaPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var orientationMode by rememberSaveable { mutableStateOf(MediaOrientationMode.PORTRAIT) }

    // 方向切换：进入页面时是竖屏，离开时把方向交还系统，
    // 否则从播放页返回后整个 App 会被锁在横屏
    DisposableEffect(orientationMode) {
        activity?.requestedOrientation = orientationMode.orientation
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 横屏时隐藏系统栏，真正用满屏幕；竖屏与退出时恢复
    val immersive = orientationMode != MediaOrientationMode.PORTRAIT
    DisposableEffect(immersive) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Scaffold(
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        viewModel.title.ifBlank { stringResource(R.string.media_player_title) },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_media_player_back),
                        )
                    }
                },
                actions = { OrientationMenu(orientationMode) { orientationMode = it } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            when {
                viewModel.isVideo -> VideoStage(viewModel.player)
                viewModel.fileExists || viewModel.url != null -> ImageStage(
                    fileName = viewModel.fileName,
                    url = viewModel.url,
                )
                else -> Text(
                    stringResource(R.string.media_player_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoStage(player: ExoPlayer?) {
    if (player == null) {
        Text(
            stringResource(R.string.media_player_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // PlayerView 用 AndroidView 包一层：Media3 的 Compose 版 Surface 还在演进，
    // 而 PlayerView 自带进度条/播放暂停控制器，正好是演示场景需要的
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 3_000
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        modifier = Modifier.fillMaxSize(),
        onRelease = { view -> view.player = null },
    )
}

/**
 * 图片 / 动图查看。
 *
 * 动图（GIF、动态 WebP）由 Coil 的 AnimatedImageDecoder 直接循环播放，
 * 不需要播放器；静态图支持双指缩放与拖动 —— 动作要领图经常要看局部细节。
 */
@Composable
private fun ImageStage(fileName: String?, url: String?) {
    val context = LocalContext.current
    val model: Any? = fileName?.let { File(File(context.filesDir, "media"), it) } ?: url
    if (model == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 双指缩放 + 拖动；松手不回弹是为了让用户停在想看的细节上
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.cd_media_image),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        // 外链（YouTube/Bilibili 等）内嵌播放器播不了，也不该播：
        // 交回浏览器，并让用户明确知道这是跳转到第三方
        if (url != null) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.media_open_in_browser))
            }
        }
    }
}

@Composable
private fun OrientationMenu(current: MediaOrientationMode, onSelect: (MediaOrientationMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Filled.ScreenRotation,
            contentDescription = stringResource(R.string.cd_media_orientation),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MediaOrientationMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(labelOf(mode))) },
                onClick = {
                    expanded = false
                    onSelect(mode)
                },
                trailingIcon = if (mode == current) {
                    {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun labelOf(mode: MediaOrientationMode): Int = when (mode) {
    MediaOrientationMode.PORTRAIT -> R.string.media_orientation_portrait
    MediaOrientationMode.LANDSCAPE_LEFT -> R.string.media_orientation_landscape_left
    MediaOrientationMode.LANDSCAPE_RIGHT -> R.string.media_orientation_landscape_right
    MediaOrientationMode.SYSTEM -> R.string.media_orientation_system
}
