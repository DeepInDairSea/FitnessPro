package com.fitnesspro.feature.plans

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.SkeletonList
import com.fitnesspro.core.designsystem.formatBytes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.data.repository.daysLeftInTrash
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

import com.fitnesspro.feature.plans.R as PlansR

data class RecycleBinUiState(
    val isLoading: Boolean = true,
    val plans: List<PlanCard> = emptyList(),
    /** 每项剩余保留天数，key = planId */
    val daysLeft: Map<Long, Long> = emptyMap(),
    val lastRestoredId: Long? = null,
    val actionFailed: Boolean = false,
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val lastRestoredId = MutableStateFlow<Long?>(null)
    private val actionFailed = MutableStateFlow(false)

    val uiState: StateFlow<RecycleBinUiState> = combine(
        planRepository.observeTrash(),
        lastRestoredId,
        actionFailed,
    ) { plans, restored, failed ->
        val today = time.nowEpochMs()
        RecycleBinUiState(
            isLoading = false,
            plans = plans,
            daysLeft = plans.mapNotNull { plan ->
                plan.deletedAt?.let { plan.id to daysLeftInTrash(it, today) }
            }.toMap(),
            lastRestoredId = restored,
            actionFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecycleBinUiState())

    /** 恢复：失败时不能提示"已恢复"，否则用户以为计划回来了 */
    fun restore(planId: Long) {
        viewModelScope.launch {
            val outcome = planRepository.restoreFromTrash(planId)
            if (outcome.isSuccess) {
                lastRestoredId.value = planId
            } else {
                Timber.e("恢复计划失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }

    fun consumeRestoredEvent() {
        lastRestoredId.value = null
    }

    fun consumeActionFailed() {
        actionFailed.value = false
    }

    /** 永久删除：失败时必须说出来，否则用户以为计划已经清掉了 */
    fun deleteForever(planId: Long) {
        viewModelScope.launch {
            val outcome = planRepository.permanentlyDelete(planId)
            if (outcome.isFailure) {
                Timber.e("永久删除计划失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }
}

/**
 * 回收站。
 *
 * "保留 30 天"这个承诺有两半：UI 展示剩余天数（本页），以及到点真正清理
 * （RecycleBinCleanupWorker）。两者共用 `TRASH_RETENTION_DAYS` 常量，
 * 避免出现"界面说还有 3 天、后台已经删了"这种不一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<PlanCard?>(null) }
    val restoredToast = stringResource(PlansR.string.plans_restored)
    val failedToast = stringResource(PlansR.string.plans_action_failed)

    LaunchedEffect(state.lastRestoredId) {
        if (state.lastRestoredId == null) return@LaunchedEffect
        viewModel.consumeRestoredEvent()
        snackbarHostState.showSnackbar(restoredToast, actionLabel = null)
    }

    LaunchedEffect(state.actionFailed) {
        if (!state.actionFailed) return@LaunchedEffect
        viewModel.consumeActionFailed()
        snackbarHostState.showSnackbar(failedToast, actionLabel = null)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(PlansR.string.plans_trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> SkeletonList(modifier = Modifier.padding(padding))
            state.plans.isEmpty() -> EmptyState(
                title = stringResource(PlansR.string.plans_trash_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(FitnessTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
            ) {
                items(state.plans, key = { it.id }) { plan ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(FitnessTheme.spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plan.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                                Text(
                                    stringResource(
                                        PlansR.string.plans_card_meta,
                                        plan.exerciseCount,
                                        plan.totalSets,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val days = state.daysLeft[plan.id]
                                if (days != null) {
                                    Text(
                                        stringResource(PlansR.string.plans_trash_days_left, days),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (days <= 3) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.restore(plan.id) }) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = stringResource(PlansR.string.plans_trash_restore),
                                )
                            }
                            IconButton(onClick = { pendingDelete = plan }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        PlansR.string.plans_trash_delete_forever,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { plan ->
        ConfirmDialog(
            title = stringResource(PlansR.string.plans_trash_delete_forever),
            message = stringResource(PlansR.string.plans_delete_title, plan.name),
            confirmLabel = stringResource(PlansR.string.plans_trash_delete_forever),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.deleteForever(plan.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 供设置页展示媒体占用时复用（保持单位文案一致） */
internal fun humanBytes(bytes: Long): String = formatBytes(bytes)
