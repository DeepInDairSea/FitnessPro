package com.fitnesspro.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessSearchField
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.SwipeRevealBox
import com.fitnesspro.core.designsystem.component.deleteRevealAction
import com.fitnesspro.core.designsystem.component.SkeletonList
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.SessionStatus
import com.fitnesspro.core.model.SessionSummary
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

import com.fitnesspro.feature.history.R as HistoryR

data class HistoryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val sessions: List<SessionSummary> = emptyList(),
    /** 日历模式：按月网格展示，点选日期筛选当天记录 */
    val calendarMode: Boolean = false,
    val month: java.time.YearMonth = java.time.YearMonth.now(),
    val selectedDay: Long? = null,
    /** 当月有训练的日期（epochDay 集合），日历打点用 */
    val trainedDays: Set<Long> = emptySet(),
    val lastDeletedId: Long? = null,
    val actionFailed: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val lastDeletedId = MutableStateFlow<Long?>(null)
    private val actionFailed = MutableStateFlow(false)

    // ---- 日历模式 ----
    private val calendarMode = MutableStateFlow(false)
    private val selectedDay = MutableStateFlow<Long?>(null)
    private val month = MutableStateFlow(java.time.YearMonth.now())

    private data class SessionQuery(
        val text: String,
        val calendarMode: Boolean,
        val selectedDay: Long?,
        val month: java.time.YearMonth,
    )

    private data class SessionPage(
        val sessions: List<SessionSummary>,
        val trainedDays: Set<Long>,
    )

    /**
     * 注意：`lastDeletedId` / `actionFailed` 必须**合进** uiState。
     * 之前它们只是两个独立的 StateFlow，uiState 里永远是默认值 null，
     * 于是"已删除"提示从未出现过 —— 删除动作连成功反馈都没有。
     */
    val uiState: StateFlow<HistoryUiState> = combine(
        combine(query, calendarMode, selectedDay, month, ::SessionQuery)
            .flatMapLatest { req ->
                if (!req.calendarMode) {
                    workoutRepository.observeHistory(req.text)
                        .map { SessionPage(it, emptySet()) }
                } else {
                    val zone = java.time.ZoneId.systemDefault()
                    val monthStart = req.month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val monthEnd = req.month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val daySessions = if (req.selectedDay != null) {
                        val dayStart = java.time.LocalDate.ofEpochDay(req.selectedDay)
                            .atStartOfDay(zone).toInstant().toEpochMilli()
                        workoutRepository.observeHistoryInRange(dayStart, dayStart + 86_400_000L)
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    combine(
                        workoutRepository.observeHistoryInRange(monthStart, monthEnd),
                        daySessions,
                    ) { monthSessions, daySessions ->
                        SessionPage(
                            sessions = if (req.selectedDay != null) daySessions else monthSessions,
                            trainedDays = monthSessions
                                .map {
                                    java.time.Instant.ofEpochMilli(it.startedAt)
                                        .atZone(zone).toLocalDate().toEpochDay()
                                }
                                .toSet(),
                        )
                    }
                }
            },
        lastDeletedId,
        actionFailed,
    ) { page, deletedId, failed ->
        HistoryUiState(
            isLoading = false,
            query = if (calendarMode.value) "" else query.value,
            sessions = page.sessions,
            calendarMode = calendarMode.value,
            month = month.value,
            selectedDay = selectedDay.value,
            trainedDays = page.trainedDays,
            lastDeletedId = deletedId,
            actionFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onToggleCalendarMode() {
        calendarMode.value = !calendarMode.value
        selectedDay.value = null
    }

    fun onMonthShift(delta: Int) {
        month.value = month.value.plusMonths(delta.toLong())
        selectedDay.value = null
    }

    fun onDaySelect(epochDay: Long) {
        selectedDay.value = if (selectedDay.value == epochDay) null else epochDay
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun delete(sessionId: Long) {
        viewModelScope.launch {
            val outcome = workoutRepository.deleteSession(sessionId)
            // 只有真的删掉了才提示成功。失败也提示"已删除"的话，
            // 用户下次进来发现记录还在，会比没有提示更困惑。
            if (outcome.isSuccess) {
                lastDeletedId.value = sessionId
            } else {
                Timber.e("删除训练记录失败：%s", outcome.errorOrNull())
                actionFailed.value = true
            }
        }
    }

    fun consumeDeletedEvent() {
        lastDeletedId.value = null
    }

    fun consumeActionFailed() {
        actionFailed.value = false
    }

    /** 相对日期：训练记录用"3 天前"比"10 月 4 日"更容易理解 */
    fun relativeDay(epochMs: Long): String {
        val diff = time.todayEpochDay() - time.epochMsToEpochDay(epochMs)
        return when (diff) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            in 3L..30L -> "$diff 天前"
            else -> java.time.LocalDate.ofEpochDay(time.epochMsToEpochDay(epochMs))
                .let { "%d-%02d-%02d".format(it.year, it.monthValue, it.dayOfMonth) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }
    HistorySnackbarEffects(state, viewModel, snackbarHostState)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 底部导航的一级页面：不放返回键（返回键用于二级页面）
            FitnessTopAppBar(
                title = { Text(stringResource(HistoryR.string.history_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
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
            historyItems(state, viewModel, onSessionClick, onRequestDelete = { pendingDelete = it })
        }
    }

    pendingDelete?.let { session ->
        ConfirmDialog(
            title = stringResource(HistoryR.string.history_delete_confirm_title),
            message = stringResource(HistoryR.string.history_delete_confirm_message),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(session.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 删除结果的 Snackbar 反馈：独立成组件，数据页（统计+历史合并）与独立页共用 */
@Composable
fun HistorySnackbarEffects(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val deletedToast = stringResource(HistoryR.string.history_deleted)
    val failedToast = stringResource(HistoryR.string.history_action_failed)

    LaunchedEffect(state.lastDeletedId) {
        if (state.lastDeletedId == null) return@LaunchedEffect
        viewModel.consumeDeletedEvent()
        snackbarHostState.showSnackbar(deletedToast, actionLabel = null)
    }

    LaunchedEffect(state.actionFailed) {
        if (!state.actionFailed) return@LaunchedEffect
        viewModel.consumeActionFailed()
        snackbarHostState.showSnackbar(failedToast, actionLabel = null)
    }
}

/**
 * 历史内容片段：数据页（统计在上、历史在下）与本页复用同一段 LazyListScope。
 * 删除确认由宿主弹窗（onRequestDelete）—— 对话框状态不能放在 item 内部。
 */
fun LazyListScope.historyItems(
    state: HistoryUiState,
    viewModel: HistoryViewModel,
    onSessionClick: (Long) -> Unit,
    onRequestDelete: (SessionSummary) -> Unit,
    /** 页面强调色：历史页用黄色系区分（黄只做色条/标记，不做文字色） */
    accent: Color? = null,
) {
    run {
    item(key = "history-mode") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = FitnessTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
        ) {
            FilterChip(
                selected = !state.calendarMode,
                onClick = viewModel::onToggleCalendarMode,
                label = { Text(stringResource(HistoryR.string.history_mode_list)) },
            )
            FilterChip(
                selected = state.calendarMode,
                onClick = viewModel::onToggleCalendarMode,
                label = { Text(stringResource(HistoryR.string.history_mode_calendar)) },
            )
        }
    }
    if (state.calendarMode) {
        item(key = "history-calendar") { MonthCalendar(state, viewModel) }
    } else {
        item(key = "history-search") {
            FitnessSearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                hint = stringResource(HistoryR.string.history_search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when {
        state.isLoading -> item(key = "history-loading") { SkeletonList() }
        state.sessions.isEmpty() -> item(key = "history-empty") {
            EmptyState(
                title = if (state.query.isBlank()) {
                    stringResource(HistoryR.string.history_empty)
                } else {
                    stringResource(HistoryR.string.history_no_result)
                },
                description = if (state.query.isBlank()) {
                    stringResource(HistoryR.string.history_empty_desc)
                } else {
                    null
                },
            )
        }
        else -> items(state.sessions, key = { "history-${it.id}" }) { session ->
            SessionCard(
                session = session,
                relativeDay = viewModel.relativeDay(session.startedAt),
                onClick = { onSessionClick(session.id) },
                onDelete = { onRequestDelete(session) },
                accent = accent,
            )
        }
    }
    item(key = "history-bottom") {
        Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
    }
    }
}

/**
 * 月历视图：有训练的日期打点，点选日期筛选当天记录（再点取消）。
 * 周一起始（与设置里的"每周起始日"默认一致，此处固定周一保持简单）。
 */
@Composable
private fun MonthCalendar(state: HistoryUiState, viewModel: HistoryViewModel) {
    val month = state.month
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.onMonthShift(-1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = { viewModel.onMonthShift(1) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val firstOffset = month.atDay(1).dayOfWeek.value - 1 // 周一 = 0
        val daysInMonth = month.lengthOfMonth()
        val rowCount = (firstOffset + daysInMonth + 6) / 7
        for (row in 0 until rowCount) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - firstOffset + 1
                    Box(Modifier.weight(1f)) {
                        if (day in 1..daysInMonth) {
                            val epochDay = month.atDay(day).toEpochDay()
                            val trained = epochDay in state.trainedDays
                            val selected = state.selectedDay == epochDay
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onDaySelect(epochDay) }
                                    .padding(vertical = FitnessTheme.spacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                trained -> MaterialTheme.colorScheme.primary
                                                else -> androidx.compose.ui.graphics.Color.Transparent
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.selectedDay != null) {
            Text(
                stringResource(HistoryR.string.history_selected_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = FitnessTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionSummary,
    relativeDay: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    accent: Color? = null,
) {
    // 删除改到左滑（与计划/动作列表一致），并保留二次确认
    SwipeRevealBox(
        modifier = Modifier.fillMaxWidth(),
        actions = listOf(deleteRevealAction(onDelete = onDelete)),
    ) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(0.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(FitnessTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 页面强调色色条：历史页（黄）与计划/统计一眼区分
                Box(
                    Modifier
                        .width(6.dp)
                        .height(40.dp)
                        .background(
                            accent ?: MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(3.dp),
                        ),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.m))
                Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.planName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (session.status == SessionStatus.ABANDONED) {
                        Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
                        LabelChip(stringResource(HistoryR.string.history_abandoned))
                    }
                }
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    relativeDay + " · " + formatDuration(session.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        HistoryR.string.session_detail_summary,
                        formatVolume(session.totalVolumeKg),
                        session.totalSets,
                        session.totalReps,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}

