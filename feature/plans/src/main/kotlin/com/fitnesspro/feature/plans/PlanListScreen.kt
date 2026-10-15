package com.fitnesspro.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessSearchField
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.fitnesspro.core.common.text.UiText
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.asString
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.SkeletonList
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.SwipeRevealBox
import com.fitnesspro.core.designsystem.component.deleteRevealAction
import com.fitnesspro.core.designsystem.component.favoriteRevealAction
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatRelativeDay
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.LocalFitnessAccent
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.ScheduleRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.model.PlanCard
import java.time.LocalDate
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlanListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val plans: List<PlanCard> = emptyList(),
    /** 今天排程里的计划：列表顶部单独突出，训练时不用去找 */
    val todayPlans: List<PlanCard> = emptyList(),
    /** 非空表示刚刚删除了某个计划，用于触发带 Undo 的 Snackbar */
    val lastDeletedId: Long? = null,
    /** 收藏/复制/撤销失败：这类操作没有独立的视觉结果，失败必须显式告知 */
    val actionFailed: Boolean = false,
)

@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val settings: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val sessionManager: WorkoutSessionManager,
    private val time: TimeProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val lastDeletedId = MutableStateFlow<Long?>(null)
    private val actionFailed = MutableStateFlow(false)

    /** 今天在日历上绑定的计划 id：与"每周几"循环排程取并集，都算"今日训练" */
    private val todayBoundIds = scheduleRepository
        .observeDay(time.todayEpochDay())
        .map { rows -> rows.map { it.planId }.toSet() }

    val uiState: StateFlow<PlanListUiState> = combine(
        query.flatMapLatest { q ->
            settings.settings.flatMapLatest { s ->
                planRepository.observePlanCards(s.planSortOrder, q)
            }
        },
        query,
        lastDeletedId,
        actionFailed,
        todayBoundIds,
    ) { plans, q, deleted, failed, boundToday ->
        // 排程语义 1 = 周一（ISO），与计划编辑页、提醒保持一致
        val todayWeekday = LocalDate.ofEpochDay(time.todayEpochDay()).dayOfWeek.value
        PlanListUiState(
            isLoading = false,
            query = q,
            plans = plans,
            todayPlans = plans.filter {
                todayWeekday in it.scheduledDays || it.id in boundToday
            },
            lastDeletedId = deleted,
            actionFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanListUiState())

    /** 从"今日训练"直接开练：先生成会话，再交给导航打开训练页 */
    fun startWorkout(planId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val outcome = sessionManager.startWorkout(planId = planId)
            outcome.getOrNull()?.let(onStarted)
                ?: run {
                    Timber.e("开始训练失败：%s", outcome.errorOrNull())
                    actionFailed.value = true
                }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onToggleFavorite(plan: PlanCard) {
        viewModelScope.launch {
            val outcome = planRepository.setFavorite(plan.id, !plan.isFavorite)
            if (outcome.isFailure) {
                Timber.e("收藏状态保存失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }

    fun onDuplicate(planId: Long) {
        viewModelScope.launch {
            val outcome = planRepository.duplicatePlan(planId)
            if (outcome.isFailure) {
                Timber.e("复制计划失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }

    fun consumeActionFailed() {
        actionFailed.value = false
    }

    /**
     * 删除 → 移入回收站，并由 Snackbar 提供 Undo。
     *
     * 两种保护机制并存是有意为之：Undo 解决"手滑"，回收站解决"想了两天才后悔"。
     * 只做其中一个都会让用户在某类场景下失去数据。
     */
    fun onDelete(planId: Long) {
        viewModelScope.launch {
            val outcome = planRepository.moveToTrash(planId)
            if (outcome.isSuccess) lastDeletedId.value = planId
        }
    }

    fun onUndoDelete() {
        val id = lastDeletedId.value ?: return
        lastDeletedId.value = null
        viewModelScope.launch {
            // 撤销删除失败必须说：用户会以为计划已经回来了
            val outcome = planRepository.restoreFromTrash(id)
            if (outcome.isFailure) {
                Timber.e("撤销删除失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }

    fun consumeDeletedEvent() {
        lastDeletedId.value = null
    }

    fun relativeDay(epochMs: Long?): String? = epochMs?.let {
        formatRelativeDay(time.epochMsToEpochDay(it), time.todayEpochDay())
    }
}

/**
 * 计划列表内容：训练库页（计划/动作分段）复用。
 * 顶栏与 FAB 由宿主（:app 组装层）提供 —— 顶栏按钮随当前分段变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListContent(
    viewModel: PlanListViewModel,
    snackbarHostState: SnackbarHostState,
    onPlanClick: (Long) -> Unit,
    onCreatePlan: () -> Unit,
    /** 从「今日训练」直接开练：会话已生成，这里只负责导航 */
    onWorkoutStarted: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PlanCard?>(null) }
    val deletedToast = stringResource(com.fitnesspro.feature.plans.R.string.plans_deleted_toast)
    val undoLabel = stringResource(R.string.common_undo)

    LaunchedEffect(state.lastDeletedId) {
        if (state.lastDeletedId == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedToast,
            actionLabel = undoLabel,
        )
        viewModel.consumeDeletedEvent()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete()
        }
    }

    val actionFailedToast = stringResource(com.fitnesspro.feature.plans.R.string.plans_action_failed)
    LaunchedEffect(state.actionFailed) {
        if (!state.actionFailed) return@LaunchedEffect
        viewModel.consumeActionFailed()
        snackbarHostState.showSnackbar(message = actionFailedToast, actionLabel = null)
    }

    // 计划页强调色 = 橙：分区条/今日训练卡/图表都用它，与动作库（紫）、统计（蓝）区分
    val pageAccent = FitnessTheme.layout.accents.firstOrNull() ?: MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalFitnessAccent provides pageAccent) {
        Column(modifier.fillMaxSize()) {
            FitnessSearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            hint = stringResource(com.fitnesspro.feature.plans.R.string.plans_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FitnessTheme.spacing.m),
        )

        when {
            state.isLoading -> SkeletonList()
            state.plans.isEmpty() -> EmptyState(
                    title = if (state.query.isBlank()) {
                        stringResource(com.fitnesspro.feature.plans.R.string.plans_empty_title)
                    } else {
                        stringResource(com.fitnesspro.feature.plans.R.string.plans_no_result)
                    },
                    description = if (state.query.isBlank()) {
                        stringResource(com.fitnesspro.feature.plans.R.string.plans_empty_desc)
                    } else {
                        null
                    },
                    actionLabel = if (state.query.isBlank()) {
                        stringResource(com.fitnesspro.feature.plans.R.string.plans_empty_action)
                    } else {
                        null
                    },
                    onAction = if (state.query.isBlank()) onCreatePlan else null,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = FitnessTheme.spacing.m,
                        end = FitnessTheme.spacing.m,
                        top = FitnessTheme.spacing.m,
                        // 悬浮菜单压在内容之上（其它主题为 0）
                        bottom = FitnessTheme.spacing.m + FitnessTheme.layout.bottomBarInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
                ) {
                    // 今日排程置顶：练的那天不用在计划列表里翻找，直接开练
                    if (state.todayPlans.isNotEmpty()) {
                        item(key = "today-schedule") {
                            TodayScheduleCard(
                                plans = state.todayPlans,
                                onStart = { planId -> viewModel.startWorkout(planId, onWorkoutStarted) },
                                onOpen = onPlanClick,
                            )
                        }
                    }
                    items(state.plans, key = { it.id }) { plan ->
                        PlanCardItem(
                            plan = plan,
                            lastPerformedText = viewModel.relativeDay(plan.lastPerformedAt),
                            onClick = { onPlanClick(plan.id) },
                            onToggleFavorite = { viewModel.onToggleFavorite(plan) },
                            onDelete = { pendingDelete = plan },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { plan ->
        ConfirmDialog(
            title = stringResource(com.fitnesspro.feature.plans.R.string.plans_delete_title, plan.name),
            message = stringResource(com.fitnesspro.feature.plans.R.string.plans_delete_message, plan.exerciseCount),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                val id = plan.id
                pendingDelete = null
                viewModel.onDelete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * 「今日训练」：把今天排程里的计划置顶，并用**实心强调色**与普通白卡明显区分。
 *
 * 一天可以排多个计划（上午推、晚上拉），所以每个计划各占一行、各自一个开练按钮，
 * 而不是合成一句"今天有 2 个计划"。
 */
@Composable
private fun TodayScheduleCard(
    plans: List<PlanCard>,
    onStart: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val accent = FitnessTheme.layout.accents.firstOrNull() ?: MaterialTheme.colorScheme.primary
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = accent,
        contentColor = Color.White,
    ) {
        Text(
            stringResource(com.fitnesspro.feature.plans.R.string.plans_today_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.s))
        plans.forEach { plan ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(plan.id) }
                    .padding(vertical = FitnessTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        plan.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            com.fitnesspro.feature.plans.R.string.plans_card_meta,
                            plan.exerciseCount,
                            plan.totalSets,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                // 白底 + 强调色文字：在实心色块上比默认按钮更清晰
                Button(
                    onClick = { onStart(plan.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = accent,
                    ),
                ) {
                    Text(stringResource(com.fitnesspro.feature.plans.R.string.plans_today_start))
                }
            }
        }
    }
}

@Composable
private fun PlanCardItem(
    plan: PlanCard,
    lastPerformedText: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val layout = FitnessTheme.layout
    val accents = layout.accents
    // 便当盒主题下给每张计划卡一道分类色条：用 id 取模，保证同一张卡颜色稳定
    val stripe = if (layout.bentoGrid && accents.isNotEmpty()) {
        accents[((plan.id % accents.size).toInt() + accents.size) % accents.size]
    } else {
        null
    }

    // 收藏/删除从行内图标改成左滑显露：列表本身更干净，破坏性操作也不在误触位置
    SwipeRevealBox(
        modifier = Modifier.fillMaxWidth(),
        actions = listOf(
            favoriteRevealAction(isFavorite = plan.isFavorite, onToggle = onToggleFavorite),
            deleteRevealAction(onDelete = onDelete),
        ),
    ) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(FitnessTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            stripe?.let {
                Box(
                    Modifier
                        .width(6.dp)
                        .height(48.dp)
                        .background(it, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.m))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    stringResource(
                        com.fitnesspro.feature.plans.R.string.plans_card_meta,
                        plan.exerciseCount,
                        plan.totalSets,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 当日主要训练部位（如 胸 · 腿）：仅标记展示，未设置则不占位
                if (plan.focus.isNotEmpty()) {
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Text(
                        plan.focus.joinToString(" + ") { it.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    plan.estimatedDurationSeconds?.let {
                        add(stringResource(
                            com.fitnesspro.feature.plans.R.string.plans_card_duration,
                            formatDuration(it),
                        ))
                    }
                    add(
                        lastPerformedText?.let {
                            stringResource(com.fitnesspro.feature.plans.R.string.plans_card_last_performed, it)
                        } ?: stringResource(com.fitnesspro.feature.plans.R.string.plans_card_never_performed)
                    )
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            }
        }
    }
}


