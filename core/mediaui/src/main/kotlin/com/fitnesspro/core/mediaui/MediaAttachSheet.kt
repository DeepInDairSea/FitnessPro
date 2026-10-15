package com.fitnesspro.core.mediaui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.media.MediaRegistrar
import timber.log.Timber
import com.fitnesspro.core.media.ImportProgress
import com.fitnesspro.core.media.ImportResult
import com.fitnesspro.core.media.MediaFileStore
import com.fitnesspro.core.media.MediaImporter
import com.fitnesspro.core.media.MediaPickerResolver
import com.fitnesspro.core.media.MediaTranscoder
import com.fitnesspro.core.media.MediaPickerSource
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

import com.fitnesspro.core.mediaui.R

/** 待用户确认的转码请求（视频超限时） */
data class PendingTranscode(
    val sourceFile: File,
    val sizeBytes: Long,
    val estimatedBytes: Long,
)

data class MediaAttachUiState(
    val isImporting: Boolean = false,
    val progressPercent: Int? = null,
    val pendingTranscode: PendingTranscode? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MediaAttachViewModel @Inject constructor(
    private val importer: MediaImporter,
    private val registrar: MediaRegistrar,
    private val pickerResolver: MediaPickerResolver,
    private val transcoder: MediaTranscoder,
    private val fileStore: MediaFileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaAttachUiState())
    val uiState: StateFlow<MediaAttachUiState> = _uiState.asStateFlow()

    /** 供 UI 决定用 Photo Picker 还是 SAF */
    val pickerSource: MediaPickerSource get() = pickerResolver.resolve()

    fun safIntent() = pickerResolver.buildSafIntent(
        listOf("image/*", "video/*"),
    )

    fun captureTarget(): Pair<Uri, File> = pickerResolver.captureTargetFile()

    fun importUri(uri: Uri, onDone: (MediaRef) -> Unit) {
        viewModelScope.launch {
            _uiState.value = MediaAttachUiState(isImporting = true, progressPercent = 0)

            var result: ImportResult? = null
            runCatching {
                importer.import(uri).collect { progress ->
                    when (progress) {
                        is ImportProgress.Copying ->
                            _uiState.value = _uiState.value.copy(
                                progressPercent = (progress.fraction * 100).toInt(),
                            )
                        is ImportProgress.Compressing ->
                            _uiState.value = _uiState.value.copy(
                                progressPercent = 70 + (progress.fraction * 20).toInt(),
                            )
                        is ImportProgress.Thumbnailing ->
                            _uiState.value = _uiState.value.copy(progressPercent = 95)
                        is ImportProgress.Completed -> result = progress.result
                        else -> Unit
                    }
                }
            }.onFailure {
                _uiState.value = MediaAttachUiState(error = it.message ?: "导入失败")
                return@launch
            }

            when (val outcome = result) {
                is ImportResult.Success -> {
                    // 文件已经落盘，登记失败不能静默：未登记的文件会成为 GC 盲区
                    // （被当成"从未登记的孤儿"清理掉），而且用户会以为关联成功了
                    runCatching { registrar.register(outcome.media) }
                        .onFailure {
                            Timber.e(it, "登记媒体引用失败：%s", outcome.media.fileName)
                            _uiState.value = MediaAttachUiState(
                                error = "关联失败，请重试（文件已导入但未生效）",
                            )
                            return@launch
                        }
                    _uiState.value = MediaAttachUiState(message = "已关联演示资源")
                    onDone(
                        MediaRef.Local(
                            fileName = outcome.media.fileName,
                            thumbnailName = outcome.media.thumbnailName,
                            type = outcome.media.type,
                        )
                    )
                }

                is ImportResult.Rejected -> {
                    // 超限是**拍摄路径的关键分支**：用户刚拍的视频不能直接拒绝，
                    // 必须给出"压缩后导入"的出口，否则等于告诉他白拍了
                    if (outcome.reason == "TOO_LARGE") {
                        val captured = pickerResolver.captureTargetFile().second
                        if (!captured.exists() || captured.length() <= 0L) {
                            _uiState.value = MediaAttachUiState(
                                error = "视频超过上限，且未找到可压缩的源文件",
                            )
                            return@launch
                        }
                        _uiState.value = MediaAttachUiState(
                            pendingTranscode = PendingTranscode(
                                sourceFile = captured,
                                sizeBytes = captured.length(),
                                estimatedBytes = transcoder.estimateCompressedBytes(captured.length()),
                            ),
                        )
                    } else {
                        _uiState.value = MediaAttachUiState(error = outcome.detail)
                    }
                }

                is ImportResult.Failed -> {
                    _uiState.value = MediaAttachUiState(
                        error = outcome.error.code,
                    )
                }

                null -> {
                    _uiState.value = MediaAttachUiState(error = "导入未完成")
                }
            }
        }
    }

    fun confirmTranscode(onDone: (MediaRef) -> Unit) {
        val pending = _uiState.value.pendingTranscode ?: return
        viewModelScope.launch {
            _uiState.value = MediaAttachUiState(isImporting = true, progressPercent = 0)

            when (val outcome = transcoder.transcode(pending.sourceFile)) {
                is Outcome.Success -> {
                    // 转码产物在共享的逻辑里走同一条导入流水线：
                    // 这样"登记 + 缩略图 + 落盘"只有一份实现，不会出现两条路径行为不一致
                    importUri(Uri.fromFile(outcome.data), onDone)
                }
                is Outcome.Failure -> {
                    _uiState.value = MediaAttachUiState(
                        error = "压缩失败：${outcome.error.code}",
                    )
                }
            }
        }
    }

    fun dismissTranscodePrompt() {
        _uiState.value = _uiState.value.copy(pendingTranscode = null)
    }

    fun attachUrl(url: String, onDone: (MediaRef) -> Unit): Boolean {
        val normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            _uiState.value = _uiState.value.copy(error = "请输入有效的 http(s) 链接")
            return false
        }
        onDone(MediaRef.Remote(normalized))
        _uiState.value = MediaAttachUiState(message = "已关联演示资源")
        return true
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}

/**
 * 动作演示资源关联面板。
 *
 * 三条路径全部零存储权限：
 *  - Photo Picker（首选）
 *  - SAF（无 GMS 机型的降级）
 *  - 系统相机（不需要 CAMERA 权限）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAttachSheet(
    title: String,
    current: MediaRef?,
    onDismiss: () -> Unit,
    onAttach: (MediaRef) -> Unit,
    onRemove: () -> Unit,
    viewModel: MediaAttachViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }

    // 拍摄目标需要提前准备好，launcher 回调里才能拿到 uri
    var captureTarget by remember { mutableStateOf<Pair<Uri, File>?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importUri(it) { ref -> onAttach(ref); onDismiss() } } }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        // 必须持久化授权：导入过程中会多次读取该 uri，进程回收后临时授权会失效
        viewModel.let { }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModel.importUri(uri) { ref -> onAttach(ref); onDismiss() }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        // 不能只看 success：部分 ROM 在用户取消时仍返回 true。
        // 真正的兜底在导入流水线里（识别空文件），这里只处理"确实拿到了 uri"的情况。
        val target = captureTarget
        if (target != null && target.second.exists() && target.second.length() > 0) {
            viewModel.importUri(target.first) { ref -> onAttach(ref); onDismiss() }
        } else if (success) {
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            current?.let {
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    stringResource(
                        R.string.media_attach_current,
                        when (it) {
                            is MediaRef.Local -> it.fileName
                            is MediaRef.Remote -> it.url
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(FitnessTheme.spacing.m))

            if (state.isImporting) {
                Text(
                    state.progressPercent?.let {
                        stringResource(R.string.media_attach_importing_percent, it)
                    } ?: stringResource(R.string.media_attach_importing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                LinearProgressIndicator(
                    progress = { (state.progressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FitnessTheme.spacing.m))
            }

            SheetAction(
                title = stringResource(R.string.media_attach_from_gallery),
                subtitle = stringResource(R.string.media_attach_from_gallery_desc),
                onClick = {
                    when (viewModel.pickerSource) {
                        MediaPickerSource.PHOTO_PICKER -> photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                        MediaPickerSource.SAF_DOCUMENT -> safLauncher.launch(viewModel.safIntent())
                    }
                },
            )

            SheetAction(
                title = stringResource(R.string.media_attach_capture),
                subtitle = stringResource(R.string.media_attach_capture_desc),
                onClick = {
                    val target = viewModel.captureTarget()
                    captureTarget = target
                    captureLauncher.launch(target.first)
                },
            )

            SheetAction(
                title = stringResource(R.string.media_attach_from_url),
                subtitle = stringResource(R.string.media_attach_from_url_desc),
                onClick = {
                    urlInput = ""
                    urlError = null
                    showUrlDialog = true
                },
            )

            if (current != null) {
                SheetAction(
                    title = stringResource(R.string.media_attach_remove),
                    subtitle = null,
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                )
            }

            state.error?.let {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(R.string.media_attach_failed, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(FitnessTheme.spacing.l))
        }
    }

    // 视频超限 → 提示压缩（拍摄路径的关键出口）
    state.pendingTranscode?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTranscodePrompt() },
            title = { Text(stringResource(R.string.media_attach_too_large_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.media_attach_too_large_message,
                        pending.sizeBytes / 1024 / 1024,
                        MediaFileStore.VIDEO_LIMIT_BYTES / 1024 / 1024,
                        pending.estimatedBytes / 1024 / 1024,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissTranscodePrompt()
                        viewModel.confirmTranscode { ref -> onAttach(ref); onDismiss() }
                    },
                ) {
                    Text(stringResource(R.string.media_attach_compress))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTranscodePrompt() }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.media_attach_from_url)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            urlError = null
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.media_attach_url_label)) },
                        isError = urlError != null,
                        supportingText = urlError?.let { { Text(it) } },
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Text(
                        stringResource(R.string.media_attach_url_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.attachUrl(urlInput) { ref -> onAttach(ref) }) {
                            showUrlDialog = false
                            onDismiss()
                        } else {
                            urlError = context.getString(R.string.media_attach_url_error)
                        }
                    },
                ) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SheetAction(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FitnessTheme.spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 供列表项展示：把 MediaRef 转成简短描述 */
internal fun MediaRef.shortLabel(): String = when (this) {
    is MediaRef.Local -> fileName
    is MediaRef.Remote -> url.take(40)
}

private val MediaType.unused: Unit get() = Unit
