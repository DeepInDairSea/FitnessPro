package com.fitnesspro.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.designsystem.R as DesignR
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.ScheduleRepository
import com.fitnesspro.core.domain.repository.ScheduledPlan
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 自由训练的哨兵 planId 与展示名：无绑定/开练语义，在日历上只读展示 */
private const val FREESTYLE_PLAN_ID = -1L
private const val FREESTYLE_NAME = "自由训练"

/** 日历单元格：某一天的排程（含相邻月份的补位日） */
data class ScheduleDayCell(
    val epochDay: Long,
    val dayOfMonth: Int,
    val inCurrentMonth: Boolean,
    val plans: List<ScheduledPlan>,
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    val monthOffset: Int = 0,
    val cells: List<ScheduleDayCell> = emptyList(),
    val todayEpochDay: Long = 0L,
    val selectedEpochDay: Long = 0L,
    val selectedPlans: List<ScheduledPlan> = emptyList(),
    val allPlans: List<PlanCard> = emptyList(),
)

/**
 * 训练日历：把计划绑定到**具体日期**（一天可绑多个）。
 *
 * 与计划里的"每周几"循环排程互补：循环排程是节奏，日历绑定是日程。
 * 网格固定 6 行 × 7 列（含相邻月补位），切换月份不改变页面高度，避免跳动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
    private val sessionManager: WorkoutSessionManager,
    private val time: TimeProvider,
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0)
    private val selected = MutableStateFlow(time.todayEpochDay())

    private val monthSchedules = monthOffset.flatMapLatest { offset ->
        val (from, to) = gridRangeOf(offset, time.todayEpochDay())
        scheduleRepository.observeRange(from, to)
    }

    /** 可见月份内完成的自由训练（planId = -1 哨兵）：与既定计划在日历上同权展示 */
    private val freestyleEntries = monthOffset.flatMapLatest { offset ->
        val (from, to) = gridRangeOf(offset, time.todayEpochDay())
        workoutRepository.observeFreestyleStartsInRange(from, to)
    }.map { starts ->
        starts.map { ms ->
            ScheduledPlan(
                epochDay = time.epochMsToEpochDay(ms),
                planId = FREESTYLE_PLAN_ID,
                planName = FREESTYLE_NAME,
            )
        }
    }

    private data class MonthScheduleData(
        val rows: List<ScheduledPlan>,
        val freestyle: List<ScheduledPlan>,
    )

    private val monthScheduleData = combine(monthSchedules, freestyleEntries) { rows, freestyle ->
        MonthScheduleData(rows, freestyle)
    }

    private val selectedSchedules = selected.flatMapLatest(scheduleRepository::observeDay)

    val uiState: StateFlow<ScheduleUiState> = combine(
        monthOffset,
        selected,
        monthScheduleData,
        selectedSchedules,
        planRepository.observePlanCards(PlanSortOrder.MANUAL, ""),
    ) { offset, selectedDay, data, dayRows, plans ->
        val today = time.todayEpochDay()
        // 既定计划 + 已完成的自由训练合并为日历条目
        val allRows = data.rows + data.freestyle
        val byDay = allRows.groupBy { it.epochDay }
        // 网格是纯计算，不占上游流 —— combine 最多只支持 5 个上游
        val cells = buildCells(offset, today).map { it.copy(plans = byDay[it.epochDay].orEmpty()) }
        // 选中日的明细：既定计划 + 当天完成的自由训练（哨兵 id 只读展示，不参与绑定/开练）
        val freestyleForDay = data.freestyle.filter { it.epochDay == selectedDay }
        ScheduleUiState(
            isLoading = false,
            monthLabel = monthLabelOf(offset, today),
            monthOffset = offset,
            cells = cells,
            todayEpochDay = today,
            selectedEpochDay = selectedDay,
            selectedPlans = dayRows + freestyleForDay,
            allPlans = plans,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun selectDay(epochDay: Long) {
        selected.value = epochDay
    }

    fun shiftMonth(delta: Int) {
        monthOffset.value += delta
    }

    fun backToToday() {
        monthOffset.value = 0
        selected.value = time.todayEpochDay()
    }

    /** 勾选 = 绑定，取消勾选 = 解绑；两个动作都是幂等的 */
    fun setBound(epochDay: Long, planId: Long, bound: Boolean) {
        viewModelScope.launch {
            if (bound) {
                scheduleRepository.bind(epochDay, planId)
            } else {
                scheduleRepository.unbind(epochDay, planId)
            }
        }
    }

    /** 从日历直接开练：先落库生成会话，再交给导航打开训练页 */
    fun startWorkout(planId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            sessionManager.startWorkout(planId = planId).getOrNull()?.let(onStarted)
        }
    }

    private companion object {
        /** 固定 6 周：足够容纳任何月份布局，切换月份时高度稳定 */
        const val WEEKS = 6
        const val DAYS_IN_WEEK = 7

        fun monthLabelOf(offset: Int, today: Long): String {
            val month = LocalDate.ofEpochDay(today).plusMonths(offset.toLong())
            return "${month.year}年${month.monthValue}月"
        }

        /** 网格覆盖的日期区间：用于只订阅"可见范围"的排程，避免整表加载 */
        fun gridRangeOf(offset: Int, today: Long): Pair<Long, Long> {
            val cells = buildCells(offset, today)
            return cells.first().epochDay to cells.last().epochDay
        }

        /** 生成 6×7 网格：从本月 1 号所在周的周一开始，含相邻月补位 */
        fun buildCells(offset: Int, today: Long): List<ScheduleDayCell> {
            val firstOfMonth = LocalDate.ofEpochDay(today).plusMonths(offset.toLong())
                .withDayOfMonth(1)
            // ISO：周一 = 1，因此 1 号前面要补 (dayOfWeek - 1) 天
            val gridStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
            return (0 until WEEKS * DAYS_IN_WEEK).map { index ->
                val date = gridStart.plusDays(index.toLong())
                ScheduleDayCell(
                    epochDay = date.toEpochDay(),
                    dayOfMonth = date.dayOfMonth,
                    inCurrentMonth = date.month == firstOfMonth.month,
                    plans = emptyList(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    onWorkoutStarted: (Long) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var bindDialogForDay by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(DesignR.string.common_close),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::backToToday) {
                        Text(stringResource(R.string.schedule_today))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FitnessTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
        ) {
            item(key = "month") {
                SoftCard(contentPadding = PaddingValues(FitnessTheme.spacing.s)) {
                    MonthHeader(
                        label = state.monthLabel,
                        onPrev = { viewModel.shiftMonth(-1) },
                        onNext = { viewModel.shiftMonth(1) },
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    WeekdayHeader()
                    state.cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { cell ->
                                DayCell(
                                    cell = cell,
                                    isToday = cell.epochDay == state.todayEpochDay,
                                    isSelected = cell.epochDay == state.selectedEpochDay,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.selectDay(cell.epochDay) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "day-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDayTitle(state.selectedEpochDay),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = { bindDialogForDay = state.selectedEpochDay }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(FitnessTheme.spacing.xs))
                        Text(stringResource(R.string.schedule_bind))
                    }
                }
            }

            if (state.selectedPlans.isEmpty()) {
                item(key = "day-empty") {
                    SoftCard {
                        Text(
                            stringResource(R.string.schedule_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.selectedPlans, key = { "day-${it.planId}" }) { plan ->
                    if (plan.planId == FREESTYLE_PLAN_ID) {
                        // 自由训练完成条目：只读展示（没有绑定/开练语义）
                        SoftCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                                Spacer(Modifier.width(FitnessTheme.spacing.s))
                                Text(
                                    stringResource(R.string.schedule_freestyle_entry),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.schedule_freestyle_done),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(planAccent(plan.planId), CircleShape),
                            )
                            Spacer(Modifier.width(FitnessTheme.spacing.s))
                            Text(
                                plan.planName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { viewModel.setBound(state.selectedEpochDay, plan.planId, false) },
                            ) {
                                Text(stringResource(R.string.schedule_unbind))
                            }
                        }
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        Button(
                            onClick = { viewModel.startWorkout(plan.planId, onWorkoutStarted) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(FitnessTheme.spacing.xs))
                            Text(stringResource(R.string.schedule_start))
                        }
                    }
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(FitnessTheme.spacing.l)) }
        }
    }

    // 绑定计划：勾选即绑定，取消勾选即解绑（幂等，不需要"保存"按钮）
    bindDialogForDay?.let { day ->
        val bound = state.selectedPlans.map { it.planId }.filter { it > 0 }.toSet()
        AlertDialog(
            onDismissRequest = { bindDialogForDay = null },
            title = { Text(stringResource(R.string.schedule_bind_title, formatDayTitle(day))) },
            text = {
                if (state.allPlans.isEmpty()) {
                    Text(stringResource(R.string.schedule_no_plans))
                } else {
                    Column {
                        state.allPlans.forEach { plan ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setBound(day, plan.id, plan.id !in bound)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = plan.id in bound,
                                    onCheckedChange = { checked ->
                                        viewModel.setBound(day, plan.id, checked)
                                    },
                                )
                                Text(
                                    plan.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bindDialogForDay = null }) {
                    Text(stringResource(DesignR.string.common_done))
                }
            },
        )
    }
}

@Composable
private fun MonthHeader(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.schedule_prev_month))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.schedule_next_month))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf(
        R.string.schedule_weekday_mon,
        R.string.schedule_weekday_tue,
        R.string.schedule_weekday_wed,
        R.string.schedule_weekday_thu,
        R.string.schedule_weekday_fri,
        R.string.schedule_weekday_sat,
        R.string.schedule_weekday_sun,
    )
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: ScheduleDayCell,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        cell.inCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            // 最多两个色点：日历格只有这么大，第三个点会挤成一团
            if (cell.plans.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    cell.plans.take(2).forEach { plan ->
                        Box(
                            Modifier
                                .size(5.dp)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        planAccent(plan.planId)
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 计划在日历上的颜色：优先用计划自己的颜色，取不到就按 id 从主题强调色里挑一个稳定的 */
@Composable
private fun planAccent(planId: Long): Color {
    val accents = FitnessTheme.layout.accents
    if (accents.isEmpty()) return MaterialTheme.colorScheme.primary
    return accents[((planId % accents.size).toInt() + accents.size) % accents.size]
}

/** 日期标题：10月20日 周一 */
private fun formatDayTitle(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return "${date.monthValue}月${date.dayOfMonth}日 ${weekday[date.dayOfWeek.value - 1]}"
}
