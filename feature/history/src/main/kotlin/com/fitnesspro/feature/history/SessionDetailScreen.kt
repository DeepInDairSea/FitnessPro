package com.fitnesspro.feature.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.ErrorState
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.share.WorkoutShareCard
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.model.ExerciseStatus
import com.fitnesspro.core.model.SessionDetail
import com.fitnesspro.core.model.SessionExerciseDetail
import com.fitnesspro.core.model.SessionSetDetail
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

import com.fitnesspro.feature.history.R as HistoryR

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val detail: SessionDetail? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** 个人资料：分享卡片署名用 */
    val nickname: String = "",
    val avatarUri: String? = null,
    /** 本次会话（该 VM 实例存续期间）是否已分享/保存过：第 2 次起分享前要用户确认 */
    val hasSharedBefore: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: WorkoutSessionManager,
    private val settings: com.fitnesspro.core.domain.repository.SettingsRepository,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val state = MutableStateFlow(SessionDetailUiState())
    /** 分享卡片用的训练汇总，点分享时才加载（详情页本身用不到） */
    private var cachedSummary: com.fitnesspro.core.model.WorkoutSummary? = null

    val uiState: StateFlow<SessionDetailUiState> = state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionDetailUiState(),
    )

    init {
        reload()
    }

    /** 编辑/删除后需要重载：详情是一次性聚合查询，没有增量 Flow 可用 */
    private fun reload() {
        viewModelScope.launch {
            val user = runCatching { settings.current() }.getOrNull()
            val detail = sessionManager.getSessionDetail(sessionId)
            state.value = SessionDetailUiState(
                isLoading = false,
                detail = detail,
                weightUnit = user?.weightUnit ?: WeightUnit.KG,
                nickname = user?.nickname.orEmpty(),
                avatarUri = user?.avatarUri,
                hasSharedBefore = state.value.hasSharedBefore ||
                    (user?.shareSavedSessionIds?.contains(sessionId) ?: false),
                message = state.value.message,
            )
            // 训练内容可能被改：分享汇总缓存作废，下次分享时重新聚合
            cachedSummary = null
        }
    }

    /** 分享用的训练汇总（缓存到本次编辑为止） */
    suspend fun summaryForShare(): com.fitnesspro.core.model.WorkoutSummary? {
        cachedSummary?.let { return it }
        val summary = sessionManager.getSummary(sessionId)
        cachedSummary = summary
        return summary
    }

    /** 分享成功后调用：下次再点分享时弹"已经保存过咯"确认 */
    fun markShared() {
        state.value = state.value.copy(hasSharedBefore = true)
        viewModelScope.launch { settings.markShareCardSaved(sessionId) }
    }

    /** 修改某组的实际重量/次数。输入为用户单位，这里换算为 kg 落库 */
    fun updateSet(setId: Long, weightDisplay: String, repsInput: String) {
        viewModelScope.launch {
            val unit = state.value.weightUnit
            val weightKg = weightDisplay.trim().toDoubleOrNull()
                ?.let { com.fitnesspro.core.model.UnitConversion.toKg(it, unit) }
            val reps = repsInput.trim().toIntOrNull()
            val outcome = sessionManager.updateSetResult(sessionId, setId, weightKg, reps)
            if (outcome.isSuccess) {
                reload()
            } else {
                state.value = state.value.copy(message = EDIT_FAILED)
            }
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            val outcome = sessionManager.deleteSetFromHistory(sessionId, setId)
            if (outcome.isSuccess) {
                reload()
            } else {
                state.value = state.value.copy(message = EDIT_FAILED)
            }
        }
    }

    fun consumeMessage() {
        state.value = state.value.copy(message = null)
    }

    private companion object {
        const val EDIT_FAILED = "修改失败，请重试"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = state.detail
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingSet by remember { mutableStateOf<SessionSetDetail?>(null) }
    var deletingSet by remember { mutableStateOf<SessionSetDetail?>(null) }

    // ---------------- 分享 ----------------
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showShareRepeat by remember { mutableStateOf(false) }
    val shareSavedToast = stringResource(HistoryR.string.session_detail_share_saved)
    val shareFailedToast = stringResource(HistoryR.string.session_detail_share_failed)
    val shareOnlyToast = stringResource(HistoryR.string.session_detail_share_only)

    fun doShare(saveToGallery: Boolean = true) {
        scope.launch {
            val summary = viewModel.summaryForShare()
            if (summary == null) {
                snackbarHostState.showSnackbar(shareFailedToast)
                return@launch
            }
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
                snackbarHostState.showSnackbar(shareFailedToast)
                return@launch
            }
            // 写相册：失败不阻断分享（卡片仍经 FileProvider 交给系统分享）
            // 第 2 次及以后只分享、不再往相册重复塞图，因此跳过保存
            val saved = if (saveToGallery) {
                withContext(Dispatchers.IO) {
                    runCatching { WorkoutShareCard.saveToGallery(context, bitmap) }
                        .getOrDefault(false)
                }
            } else {
                false
            }
            val uri = withContext(Dispatchers.IO) {
                WorkoutShareCard.createShareUri(context, bitmap)
            }
            if (uri == null) {
                snackbarHostState.showSnackbar(shareFailedToast)
                return@launch
            }
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                .onFailure {
                    Timber.e(it, "启动系统分享失败")
                    snackbarHostState.showSnackbar(shareFailedToast)
                    return@launch
                }
            viewModel.markShared()
            val toast = when {
                saveToGallery && saved -> shareSavedToast
                saveToGallery -> shareFailedToast
                else -> shareOnlyToast
            }
            snackbarHostState.showSnackbar(toast)
        }
    }

    // Android 9 及以下写相册需要存储权限；Android 10+ 的 MediaStore 不需要
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) doShare() else scope.launch { snackbarHostState.showSnackbar(shareFailedToast) }
    }

    fun onShareClick() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        if (state.hasSharedBefore) {
            showShareRepeat = true
            return
        }
        doShare()
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(HistoryR.string.session_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = { onShareClick() }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(HistoryR.string.session_detail_share),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            detail == null -> ErrorState(
                message = stringResource(HistoryR.string.session_detail_not_found),
                modifier = Modifier.padding(padding),
            )
            detail.exercises.isEmpty() -> EmptyState(
                title = stringResource(HistoryR.string.history_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(FitnessTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
            ) {
                item(key = "header") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(FitnessTheme.spacing.m)) {
                            Text(
                                detail.planName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.xs))
                            Text(
                                stringResource(
                                    HistoryR.string.session_detail_summary,
                                    formatVolume(detail.totalVolumeKg),
                                    detail.totalSets,
                                    detail.totalReps,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                formatDuration(detail.durationSeconds) +
                                    if (detail.pausedSeconds > 0) {
                                        " · " + stringResource(
                                            HistoryR.string.session_detail_paused,
                                            formatDuration(detail.pausedSeconds),
                                        )
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(detail.exercises.size, key = { detail.exercises[it].id }) { index ->
                    ExerciseDetailCard(
                        exercise = detail.exercises[index],
                        unit = state.weightUnit,
                        onEdit = { editingSet = it },
                        onDelete = { deletingSet = it },
                    )
                }

                if (detail.notes != null || detail.perceivedEffort != null) {
                    item(key = "notes") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(FitnessTheme.spacing.m)) {
                                detail.perceivedEffort?.let {
                                    Text(
                                        stringResource(HistoryR.string.session_detail_rpe, it),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                detail.notes?.let {
                                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
            }
        }
    }

    editingSet?.let { set ->
        SetEditDialog(
            set = set,
            unit = state.weightUnit,
            onDismiss = { editingSet = null },
            onConfirm = { weightDisplay, repsInput ->
                viewModel.updateSet(set.id, weightDisplay, repsInput)
                editingSet = null
            },
        )
    }

    // 重复分享确认：提示"已经保存过咯"，用户确认后才再次保存并发起分享
    if (showShareRepeat) {
        AlertDialog(
            onDismissRequest = { showShareRepeat = false },
            title = { Text(stringResource(HistoryR.string.session_detail_share_repeat_title)) },
            text = { Text(stringResource(HistoryR.string.session_detail_share_repeat_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareRepeat = false
                    doShare(saveToGallery = false)
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showShareRepeat = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    deletingSet?.let { set ->
        AlertDialog(
            onDismissRequest = { deletingSet = null },
            title = { Text(stringResource(HistoryR.string.session_detail_delete_confirm_title)) },
            text = { Text(stringResource(HistoryR.string.session_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSet(set.id)
                    deletingSet = null
                }) {
                    Text(
                        stringResource(com.fitnesspro.core.designsystem.R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSet = null }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SetEditDialog(
    set: SessionSetDetail,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onConfirm: (weightDisplay: String, reps: String) -> Unit,
) {
    val initialWeight = set.weightKg
        ?.let { "%.1f".format(com.fitnesspro.core.model.UnitConversion.fromKg(it, unit)) }
        ?: ""
    var weight by remember { mutableStateOf(initialWeight) }
    var reps by remember { mutableStateOf(if (set.totalReps > 0) "${set.totalReps}" else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(HistoryR.string.session_detail_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { raw -> weight = raw.filter { it.isDigit() || it == '.' }.take(6) },
                    label = { Text(stringResource(HistoryR.string.session_detail_edit_weight, if (unit == WeightUnit.KG) "kg" else "lb")) },
                    singleLine = true,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                OutlinedTextField(
                    value = reps,
                    onValueChange = { raw -> reps = raw.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(HistoryR.string.session_detail_edit_reps)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weight, reps) }) {
                Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ExerciseDetailCard(
    exercise: SessionExerciseDetail,
    unit: WeightUnit,
    onEdit: (SessionSetDetail) -> Unit,
    onDelete: (SessionSetDetail) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (exercise.status) {
                    ExerciseStatus.SKIPPED -> LabelChip(
                        stringResource(HistoryR.string.session_detail_set_skipped),
                    )
                    ExerciseStatus.COMPLETED -> LabelChip(
                        stringResource(
                            HistoryR.string.session_detail_sets_progress,
                            exercise.completedWorkingSets,
                            exercise.sets.count { it.setType != SetType.WARMUP },
                        ),
                    )
                    else -> Unit
                }
            }

            exercise.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(FitnessTheme.spacing.s))
            HorizontalDivider()
            exercise.sets.forEach { set ->
                SetDetailRow(
                    set = set,
                    unit = unit,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SetDetailRow(
    set: SessionSetDetail,
    unit: WeightUnit,
    onEdit: (SessionSetDetail) -> Unit,
    onDelete: (SessionSetDetail) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${set.setIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (set.setType != SetType.NORMAL) {
            LabelChip(
                stringResource(
                    com.fitnesspro.core.designsystem.R.string.set_type_warmup,
                ),
            )
            Spacer(Modifier.width(FitnessTheme.spacing.xs))
        }

        // 目标（来自计划处方）
        Text(
            set.targetReps?.let { "${formatWeightCompact(set.targetWeightKg ?: 0.0)}×$it" } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )

        Spacer(Modifier.weight(1f))

        // 实际
        Text(
            when {
                set.status == SetStatus.SKIPPED ->
                    stringResource(HistoryR.string.session_detail_set_skipped)
                set.status != SetStatus.COMPLETED ->
                    stringResource(HistoryR.string.session_detail_set_pending)
                else -> buildString {
                    set.weightKg?.let { append(formatWeightCompact(com.fitnesspro.core.model.UnitConversion.fromKg(it, unit))) }
                    if (set.totalReps > 0) append(" × ${set.totalReps}")
                    set.durationSeconds?.let { append(" ${it}s") }
                    set.distanceMeters?.let { append(" ${it.toInt()}m") }
                }.ifEmpty { "—" }
            },
            style = numericTextStyle(NumericSize.Small),
        )

        if (set.isPersonalRecord) {
            Spacer(Modifier.width(FitnessTheme.spacing.xs))
            PrBadge(compact = true)
        }

        // 达成情况：不只靠颜色，用文字标注
        set.achievedTarget?.let { achieved ->
            Spacer(Modifier.width(FitnessTheme.spacing.xs))
            Text(
                stringResource(
                    if (achieved) {
                        HistoryR.string.session_detail_achieved
                    } else {
                        HistoryR.string.session_detail_missed
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (achieved) {
                    FitnessTheme.colors.setCompleted
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        // 逐组复盘：已完成组可改重量/次数，任意组可删除（PRD M5-2）
        if (set.status == SetStatus.COMPLETED) {
            IconButton(onClick = { onEdit(set) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(HistoryR.string.session_detail_edit),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(onClick = { onDelete(set) }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(HistoryR.string.session_detail_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
