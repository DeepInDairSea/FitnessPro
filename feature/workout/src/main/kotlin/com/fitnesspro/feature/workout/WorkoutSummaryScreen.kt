package com.fitnesspro.feature.workout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.share.WorkoutShareCard
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.PrHit
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.SessionStatus
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.UserSettings
import com.fitnesspro.core.model.WeightUnit
import com.fitnesspro.core.model.WorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

import com.fitnesspro.feature.workout.R
import com.fitnesspro.core.designsystem.R as DesignSystemR

data class SummaryUiState(
    val isLoading: Boolean = true,
    val summary: WorkoutSummary? = null,
    val previousVolumeKg: Double? = null,
    /** 个人资料：分享卡片署名用 */
    val nickname: String = "",
    val avatarUri: String? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val shareDisclaimerAcknowledged: Boolean = false,
    /** 本次会话（该 VM 实例存续期间）是否已分享/保存过：决定后续分享是否要弹确认 */
    val hasSharedBefore: Boolean = false,
    val rpe: Float = 7f,
    val notes: String = "",
    /** 是否存在尚未保存的备注/强度改动。决定"完成"是否要拦一下 */
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    /** 保存成功后请求离开（"保存并离开"） */
    val exitRequested: Boolean = false,
)

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: WorkoutSessionManager,
    private val workoutRepository: WorkoutRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val editable = MutableStateFlow(EditableSummary())
    private val summaryFlow = MutableStateFlow<WorkoutSummary?>(null)
    private val previousVolumeFlow = MutableStateFlow<Double?>(null)
    private val userSettings = MutableStateFlow(UserSettings())
    /** 是否已分享过（VM 内存态）：第 2 次起分享前要求用户确认"已经保存过咯" */
    private val sharedBefore = MutableStateFlow(false)

    init {
        // getSummary 是挂起函数，不能在 Flow 初始化器里调用，因此先异步加载再合流
        viewModelScope.launch {
            val summary = sessionManager.getSummary(sessionId)
            summaryFlow.value = summary
            // "与上次对比"只在同一计划内比较；自由训练没有可比对象
            previousVolumeFlow.value = summary?.planId?.let { planId ->
                workoutRepository.previousSessionVolumeKg(planId, sessionId)
            }
            userSettings.value = settings.current().also {
                // 持久化的"已保存卡片"判定：退出重进也不会再往相册重复塞图
                sharedBefore.value = it.shareSavedSessionIds.contains(sessionId)
            }
        }
    }

    val uiState: StateFlow<SummaryUiState> = combine(
        summaryFlow,
        editable,
        previousVolumeFlow,
        userSettings,
        sharedBefore,
    ) { summary, edit, previousVolume, user, shared ->
        SummaryUiState(
            isLoading = summary == null,
            summary = summary,
            previousVolumeKg = previousVolume,
            nickname = user.nickname,
            avatarUri = user.avatarUri,
            weightUnit = user.weightUnit,
            shareDisclaimerAcknowledged = user.shareDisclaimerAcknowledged,
            hasSharedBefore = shared,
            rpe = edit.rpe,
            notes = edit.notes,
            isDirty = edit.dirty,
            isSaving = edit.saving,
            isSaved = edit.saved,
            errorMessage = edit.error,
            exitRequested = edit.exitRequested,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    /** 首次分享的"仅本地"提示确认后不再出现 */
    fun acknowledgeShareDisclaimer() {
        viewModelScope.launch { settings.setShareDisclaimerAcknowledged(true) }
    }

    /** 分享成功后调用：下次再点分享时弹"已经保存过咯"确认 */
    fun markShared() {
        sharedBefore.value = true
        viewModelScope.launch { settings.markShareCardSaved(sessionId) }
    }

    fun onRpeChange(value: Float) {
        editable.value = editable.value.copy(rpe = value, dirty = true)
    }

    fun onNotesChange(value: String) {
        editable.value = editable.value.copy(notes = value.take(1000), dirty = true)
    }

    fun onSaveClick() {
        viewModelScope.launch { save() }
    }

    /**
     * "保存并离开"：**保存成功之后**才真正离开。
     * 失败就留在页面（Snackbar 说明原因），否则一次失败的保存会顺带把输入丢掉。
     */
    fun onSaveAndExitClick() {
        viewModelScope.launch {
            if (save()) {
                editable.value = editable.value.copy(exitRequested = true)
            }
        }
    }

    fun onConsumeExitRequested() {
        editable.value = editable.value.copy(exitRequested = false)
    }

    /**
     * 保存备注与主观强度。
     *
     * **返回值是"是否真的写进数据库"。** 以前这里丢弃了 `updateSessionNotes` 的 Outcome
     * 并把按钮置为"已保存" —— 写失败时用户看到的是"存好了"，而那两行备注其实没进去。
     */
    suspend fun save(): Boolean {
        val edit = editable.value
        if (edit.saving) return false
        editable.value = edit.copy(saving = true, error = null)

        val outcome = workoutRepository.updateSessionNotes(
            sessionId = sessionId,
            notes = edit.notes.takeIf { it.isNotBlank() },
            rpe = edit.rpe.toInt(),
            mood = null,
        )

        return if (outcome.isSuccess) {
            editable.value = editable.value.copy(saving = false, saved = true, dirty = false)
            true
        } else {
            Timber.e("保存训练备注失败：%s", outcome.errorOrNull())
            editable.value = editable.value.copy(
                saving = false,
                saved = false,
                error = ERROR_SAVE_FAILED,
            )
            false
        }
    }

    fun onConsumeError() {
        editable.value = editable.value.copy(error = null)
    }

    private data class EditableSummary(
        val rpe: Float = 7f,
        val notes: String = "",
        /** 有改动但还没写库 */
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
        /** 保存成功后请求出栈（"保存并离开"） */
        val exitRequested: Boolean = false,
    )

    private companion object {
        const val ERROR_SAVE_FAILED = "备注没能保存，请重试"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onConsumeError()
    }

    /** 有未保存的备注/强度时才拦一下，否则直接走 */
    fun requestDone() {
        if (state.isDirty) showExitDialog = true else onDone()
    }

    // 系统返回键与"完成"必须一致，否则两种离开方式有两种结果
    BackHandler(enabled = state.isDirty && !showExitDialog) {
        showExitDialog = true
    }

    // "保存并离开"：保存成功后才出栈
    LaunchedEffect(state.exitRequested) {
        if (!state.exitRequested) return@LaunchedEffect
        viewModel.onConsumeExitRequested()
        onDone()
    }

    if (showExitDialog) {
        ExitSummaryDialog(
            onSaveAndExit = {
                showExitDialog = false
                viewModel.onSaveAndExitClick()
            },
            onDiscard = {
                showExitDialog = false
                onDone()
            },
            onStay = { showExitDialog = false },
        )
    }

    // ---------------- 分享 ----------------
    val scope = rememberCoroutineScope()
    var showShareDisclaimer by remember { mutableStateOf(false) }
    var showShareRepeat by remember { mutableStateOf(false) }
    val savedToast = stringResource(R.string.summary_saved_toast)
    val savedFailedToast = stringResource(R.string.summary_save_failed_toast)
    val sharedOnlyToast = stringResource(R.string.summary_shared_only_toast)

    fun doShare(saveToGallery: Boolean = true) {
        val summary = state.summary ?: return
        scope.launch {
            // 卡片绘制是 CPU 密集（Canvas + PNG），放 Default 线程避免主线程卡顿
            val bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    WorkoutShareCard.buildCard(
                        context = context,
                        summary = summary,
                        nickname = state.nickname,
                        avatarUri = state.avatarUri,
                        weightUnit = state.weightUnit,
                    )
                }
            }.getOrElse {
                Timber.e(it, "生成分享卡片失败")
                snackbarHostState.showSnackbar(savedFailedToast)
                return@launch
            }
            // 写相册：能写就写、写不进就把缓存路径交给系统分享，至少保证分享能成
            // 第 2 次及以后只分享、不再往相册重复塞图，因此跳过保存
            val saved = if (saveToGallery) {
                val saveResult = withContext(Dispatchers.IO) {
                    runCatching { WorkoutShareCard.saveToGallery(context, bitmap) }
                }
                val saveReason = saveResult.exceptionOrNull()?.message
                val ok = saveResult.getOrDefault(false)
                if (!ok) {
                    Timber.e(saveReason?.let { RuntimeException("saveToGallery failed: $it") }, "保存到相册失败")
                }
                ok
            } else {
                false
            }
            val uri = withContext(Dispatchers.IO) {
                WorkoutShareCard.createShareUri(context, bitmap)
            }
            if (uri == null) {
                Timber.e("写入缓存失败，分享无法发起")
                snackbarHostState.showSnackbar(savedFailedToast)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                .onFailure {
                    Timber.e(it, "启动系统分享失败")
                    snackbarHostState.showSnackbar(savedFailedToast)
                    return@launch
                }
            viewModel.markShared()
            val toast = when {
                saveToGallery && saved -> savedToast
                saveToGallery -> savedFailedToast
                else -> sharedOnlyToast
            }
            snackbarHostState.showSnackbar(toast)
        }
    }

    // Android 9 及以下写相册需要存储权限；Android 10+ 的 MediaStore 不需要
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) doShare() else scope.launch { snackbarHostState.showSnackbar(savedFailedToast) }
    }

    fun onShareClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        // 第一次分享先说明"只保存到本机，不会上传"，确认后不再出现
        if (!state.shareDisclaimerAcknowledged) {
            showShareDisclaimer = true
            return
        }
        // 本次会话已经分享/保存过：后续每次都先确认，避免重复往相册塞图
        if (state.hasSharedBefore) {
            showShareRepeat = true
            return
        }
        doShare()
    }

    // 重复分享确认：提示"已经保存过咯"，用户确认后才再次保存并发起分享
    if (showShareRepeat) {
        AlertDialog(
            onDismissRequest = { showShareRepeat = false },
            title = { Text(stringResource(R.string.summary_share_repeat_title)) },
            text = { Text(stringResource(R.string.summary_share_repeat_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareRepeat = false
                    doShare(saveToGallery = false)
                }) { Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showShareRepeat = false }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }

    // 首次分享提示：说清"只写本机相册，不上传"，确认后不再出现
    if (showShareDisclaimer) {
        AlertDialog(
            onDismissRequest = { showShareDisclaimer = false },
            title = { Text(stringResource(R.string.summary_share_disclaimer_title)) },
            text = { Text(stringResource(R.string.summary_share_disclaimer)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareDisclaimer = false
                    viewModel.acknowledgeShareDisclaimer()
                    // 注意：不能走 onShareClick() —— state 的 acknowledged 标记是异步刷新的，
                    // 这里立即读仍是 false，会再次弹声明导致"确认不了"；直接发起分享
                    doShare()
                }) { Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showShareDisclaimer = false }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.summary_title)) },
                actions = {
                    IconButton(onClick = { onShareClick() }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.summary_share),
                        )
                    }
                    TextButton(onClick = { requestDone() }) {
                        Text(stringResource(R.string.summary_done))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val summary = state.summary
        if (state.isLoading || summary == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FitnessTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            item(key = "title") {
                Text(
                    summary.planName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.status == SessionStatus.ABANDONED) {
                    Text(
                        "（提前结束）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item(key = "metrics") {
                SoftCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SummaryMetric(
                                stringResource(R.string.summary_duration),
                                formatDuration(summary.durationSeconds),
                            )
                            SummaryMetric(
                                stringResource(R.string.summary_volume),
                                formatVolume(summary.totalVolumeKg),
                            )
                            SummaryMetric(
                                stringResource(R.string.summary_sets),
                                summary.totalSets.toString(),
                            )
                            SummaryMetric(
                                stringResource(R.string.summary_reps),
                                summary.totalReps.toString(),
                            )
                        }
                        if (summary.pausedSeconds > 0) {
                            Spacer(Modifier.height(FitnessTheme.spacing.xs))
                            Text(
                                "其中暂停 ${formatDuration(summary.pausedSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (summary.planId != null) {
                item(key = "compare") {
                    CompareCard(
                        currentVolumeKg = summary.totalVolumeKg,
                        previousVolumeKg = state.previousVolumeKg,
                    )
                }
            }

            item(key = "pr") {
                SoftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column {
                        Text(
                            stringResource(R.string.summary_pr_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        if (summary.prs.isEmpty()) {
                            Text(
                                stringResource(R.string.summary_no_pr),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(summary.prs, key = { "${it.exerciseName}-${it.prType}" }) { hit ->
                PrRow(hit)
            }

            item(key = "rpe") {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(R.string.summary_effort),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = state.rpe,
                        onValueChange = viewModel::onRpeChange,
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        state.rpe.toInt().toString(),
                        style = numericTextStyle(NumericSize.Medium),
                    )
                }
            }

            item(key = "notes") {
                androidx.compose.material3.OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.summary_notes_hint)) },
                    minLines = 3,
                )
            }

            item(key = "save") {
                androidx.compose.material3.Button(
                    onClick = viewModel::onSaveClick,
                    // 没有改动就不可点：让"现在没什么要存的"这件事一眼可见，
                    // 而不是点一下才发现按钮只是变了个字
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            when {
                                state.isSaving -> R.string.summary_saving
                                !state.isDirty -> R.string.summary_saved
                                else -> DesignSystemR.string.common_save
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(FitnessTheme.spacing.xl))
            }
        }
    }
}

/**
 * 离开总结页前的询问：保存 / 不保存 / 继续编辑。
 *
 * 之前没有这一层：用户改了 RPE 或备注，点右上角"完成"就直接走了，
 * 输入被静默丢掉 —— 而页面上明明摆着一个"保存"按钮，
 * 等于告诉用户"这个按钮可点可不点"，那它就没有存在的意义。
 */
@Composable
private fun ExitSummaryDialog(
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.summary_exit_title)) },
        text = { Text(stringResource(R.string.summary_exit_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(R.string.summary_exit_save))
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        stringResource(R.string.summary_exit_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onStay) {
                    Text(stringResource(R.string.summary_exit_stay))
                }
            }
        },
    )
}

/**
 * 「与上次对比」。
 *
 * 只比**同一个计划**的上一次训练：容量是高度依赖计划内容的指标，
 * 拿自由训练或别的计划来比，数字越大越误导。
 * 首次执行该计划时明确说"这是第一次"，而不是编一个 0 出来。
 */
@Composable
private fun CompareCard(currentVolumeKg: Double, previousVolumeKg: Double?) {
    val delta = previousVolumeKg?.let { currentVolumeKg - it }
    SoftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Text(
                stringResource(R.string.summary_compare_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            Text(
                when {
                    delta == null -> stringResource(R.string.summary_first_time)
                    delta > VOLUME_EPSILON ->
                        stringResource(R.string.summary_volume_delta_up, formatVolume(delta))
                    delta < -VOLUME_EPSILON ->
                        stringResource(R.string.summary_volume_delta_down, formatVolume(-delta))
                    else -> stringResource(R.string.summary_volume_delta_same)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta != null && delta > VOLUME_EPSILON) {
                    FitnessTheme.colors.prHighlight
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 容量是浮点累加值，用 0.5kg 作为"持平"阈值，
 * 避免 1e-9 级的浮点误差被显示成"容量增长"。
 */
private const val VOLUME_EPSILON = 0.5

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = numericTextStyle(NumericSize.Medium))
    }
}

@Composable
private fun PrRow(hit: PrHit) {
    SoftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = FitnessTheme.colors.prHighlight,
            )
            Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    hit.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(hit.prType.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 1RM 对普通用户不直观：给出一句解释（估算值 = 按该组重量×次数推算的单次极限）
                if (hit.prType == PrType.MAX_E1RM) {
                    Text(
                        stringResource(DesignSystemR.string.pr_e1rm_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (hit.prType) {
                        PrType.MAX_REPS -> "${hit.value.toInt()} 次"
                        else -> formatWeightCompact(hit.value)
                    },
                    style = numericTextStyle(NumericSize.Medium),
                )
                hit.previousValue?.let { previous ->
                    Text(
                        "上次 ${formatWeightCompact(previous)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
            PrBadge(compact = true)
        }
    }
}
