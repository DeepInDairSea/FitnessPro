package com.fitnesspro.ui

import com.fitnesspro.R

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.muscleColor
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.domain.repository.CompletedSessionSummary
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.ScheduleRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 今日训练条目：循环排程与日历绑定在这里抹平差异 */
data class TodayPlanItem(
    val planId: Long,
    val name: String,
    val colorHex: String? = null,
    /** true = 这条来自日历绑定（临时安排）；false = 每周循环排程 */
    val fromCalendar: Boolean = false,
)

/** 周视图某一天的小结面板：每天聚合多张计划卡，每张卡带详情（动作 + 肌群） */
data class DayPlanSummary(
    val weekday: Int,
    val weekdayLabel: String,
    val dateLabel: String,
    val plans: List<DayPlanSummaryItem>,
    /** 该日期有已完成的自由训练（空手开练），弹层追加"自由训练 · 已完成"条目 */
    val freestyleDone: Boolean = false,
)

/** 单张计划卡的"日视图"详情：只装载点击时需要的字段（名字 + 动作 + 肌群） */
data class DayPlanSummaryItem(
    val planId: Long,
    val name: String,
    val colorHex: String?,
    val totalSets: Int,
    val exerciseCount: Int,
    val muscles: List<com.fitnesspro.core.model.MuscleGroup>,
    val exercises: List<DayPlanExerciseRow>,
    /** 该计划在小结对应日期已有完成的训练 */
    val completed: Boolean = false,
)

/** 小结里的单个动作行：只展示名称 + 组数 + 次数 + 肌群标签 */
data class DayPlanExerciseRow(
    val name: String,
    val targetSets: Int,
    val targetReps: String?,
    val muscle: com.fitnesspro.core.model.MuscleGroup?,
)

data class TodayUiState(
    val isLoading: Boolean = true,
    /** 进行中的训练（可继续） */
    val activeSessions: List<com.fitnesspro.core.domain.repository.ActiveSessionInfo> = emptyList(),
    /** 今天要练的（循环排程 ∪ 日历绑定） */
    val todayPlans: List<TodayPlanItem> = emptyList(),
    /** 本周总览：weekday（1=周一…7=周日）→ 当天计划 */
    val weekPlans: Map<Int, List<TodayPlanItem>> = emptyMap(),
    val todayWeekday: Int = 1,
    val isStarting: Boolean = false,
    val planCount: Int = 0,
    /** 当天已完成训练的计划 → 聚合数据（用于"完成训练"状态块） */
    val completedToday: Map<Long, CompletedSessionSummary> = emptyMap(),
    /** 本周已完成训练：planId → 完成日（epochDay）集合（周视图绿色标识 / 小结"已完成"标注） */
    val completedDays: Map<Long, Set<Long>> = emptyMap(),
    /** 当天自由训练（空手开练）的完成聚合；与计划完成块同权展示 */
    val completedFreestyle: CompletedSessionSummary? = null,
    /** 本周自由训练的完成日集合（小结弹层展示"自由训练 · 已完成"条目） */
    val freestyleDays: Set<Long> = emptySet(),
    /** 今天的 epochDay（周视图换算"哪天已完成"用） */
    val todayEpochDay: Long = 0L,
    /** 点击周视图某天后展示的小结面板（null = 未展开） */
    val daySummary: DayPlanSummary? = null,
    /** 点击的 plan 是否正在加载详情（用于 sheet 内的 loading 占位） */
    val isDaySummaryLoading: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
    private val sessionManager: WorkoutSessionManager,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val starting = MutableStateFlow(false)
    val messageRes = MutableStateFlow<Int?>(null)

    /**
     * 周视图小结面板状态：点击周几 → 用本地缓存的 `TodayPlanItem` 立刻生成骨架展示，
     * 然后并发拉每张卡的 `PlanDetail` 补全 exercises/muscles。
     *
     * 拆成两个流：
     *  - `_daySummaryBase` 只在 openDaySummary/dismissDaySummary 时被改；
     *  - `_daySummaryDetails` 跟随 `_daySummaryBase`，用 flatMapLatest 拉详情。
     * UI 把两者 combine 成 `DayPlanSummary`。
     */
    private data class DaySummaryRequest(val weekday: Int, val items: List<TodayPlanItem>)

    private val _daySummaryBase = MutableStateFlow<DaySummaryRequest?>(null)

    private val _daySummaryDetails: StateFlow<Map<Long, DayPlanDetailPayload>> =
        _daySummaryBase.flatMapLatest { req ->
            if (req == null) {
                kotlinx.coroutines.flow.flowOf(emptyMap())
            } else {
                kotlinx.coroutines.flow.flow {
                    // flow builder 内没有 CoroutineScope，需要显式包一层 coroutineScope 才能并发 async
                    val map = coroutineScope {
                        req.items.map { item ->
                            async {
                                val detail = planRepository.getPlanDetail(item.planId)
                                val exercises = detail?.exercises.orEmpty().map { ex ->
                                    DayPlanExerciseRow(
                                        name = ex.exercise.name,
                                        targetSets = ex.targetSets,
                                        // ExerciseSnapshot 不带 defaultReps，取第一个组的次数处方
                                        targetReps = ex.sets.firstNotNullOfOrNull { it.targetReps },
                                        muscle = ex.exercise.primaryMuscle,
                                    )
                                }
                                val muscles = exercises.mapNotNull { it.muscle }.distinct()
                                val payload = DayPlanDetailPayload(
                                    totalSets = detail?.totalSets ?: 0,
                                    exerciseCount = exercises.size,
                                    muscles = muscles,
                                    exercises = exercises,
                                )
                                item.planId to payload
                            }
                        }.awaitAll()
                    }.toMap()
                    emit(map)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 把"是否打开 + 已加载的详情"打包成单个流，给 5-arg combine 用 */
    private val daySummaryFlow: kotlinx.coroutines.flow.Flow<Pair<DaySummaryRequest?, Map<Long, DayPlanDetailPayload>>> =
        _daySummaryBase.combine(_daySummaryDetails) { req, details -> req to details }

    private data class DayPlanDetailPayload(
        val totalSets: Int,
        val exerciseCount: Int,
        val muscles: List<com.fitnesspro.core.model.MuscleGroup>,
        val exercises: List<DayPlanExerciseRow>,
    )

    /**
     * 触发某一天的"训练计划小结"弹层。
     *
     * 关键点：先用 `weekPlans` 同步拿到当天的 `TodayPlanItem` 列表（planId + 名称 + 颜色），
     * 立即把骨架态推给 UI 让 sheet 弹起来；详情异步加载完成后自动填进 exercises 字段。
     */
    fun openDaySummary(weekday: Int) {
        val items = uiState.value.weekPlans[weekday].orEmpty()
        val today = time.todayEpochDay()
        val date = today + (weekday - uiState.value.todayWeekday)
        // 该日有自由训练完成时也允许打开（即使没有排程计划）
        val hasFreestyle = date in uiState.value.freestyleDays
        if (items.isEmpty() && !hasFreestyle) return  // 既没排程也没练过的天直接忽略
        _daySummaryBase.value = DaySummaryRequest(weekday, items)
    }

    /** 关闭小结面板 */
    fun dismissDaySummary() {
        _daySummaryBase.value = null
    }

    /** 本周一（ISO）的 epochDay：周内任何一天进入页面，总览都是同一周 */
    private val weekStartEpochDay: Long
        get() = run {
            val today = time.todayEpochDay()
            val weekday = LocalDate.ofEpochDay(today).dayOfWeek.value
            today - (weekday - 1)
        }

    /** 排序跟随用户设置（与首页一致） */
    private val planCards = settings.settings.flatMapLatest {
        planRepository.observePlanCards(it.planSortOrder, query = "")
    }

    /** 当天已完成训练按计划聚合（observeActiveSessions 所在仓库），随训练完成实时刷新 */
    private val completedByPlan =
        workoutRepository.observeCompletedByPlanOnDay(time.todayEpochDay())
            .map { list -> list.associateBy { it.planId } }

    /** 本周内已完成训练：planId → 完成日（epochDay）集合，用于周视图/小结的"已完成"标识 */
    private val completedPlanDays =
        workoutRepository.observeCompletedPlanDaysInRange(weekStartEpochDay, weekStartEpochDay + 6)

    /** 本周内自由训练（空手开练）的完成日集合：小结弹层展示"自由训练 · 已完成"条目 */
    private val freestyleCompletedDays =
        workoutRepository.observeFreestyleStartsInRange(weekStartEpochDay, weekStartEpochDay + 6)
            .map { starts -> starts.map { time.epochMsToEpochDay(it) }.toSet() }

    /** 当天自由训练（空手开练）的完成聚合：与计划完成块同权展示 */
    private val freestyleCompleted =
        workoutRepository.observeFreestyleCompletedOnDay(time.todayEpochDay())

    /** 本周日历绑定（一天可绑多个）。挂在 settings 流上：跨周/换周时会随任意设置变化重建观察区间 */
    private val calendarPlans = settings.settings.flatMapLatest {
        scheduleRepository.observeRange(weekStartEpochDay, weekStartEpochDay + 6)
    }

    private data class CalendarData(
        val calendar: List<com.fitnesspro.core.domain.repository.ScheduledPlan>,
        val completed: Map<Long, CompletedSessionSummary>,
        val completedDays: Map<Long, Set<Long>>,
        val freestyle: CompletedSessionSummary?,
        val freestyleDays: Set<Long>,
    )

    /** 日历绑定与完成状态打包：combine 参数上限 5，多个同源流先合并 */
    private val calendarData = combine(
        calendarPlans, completedByPlan, completedPlanDays, freestyleCompleted, freestyleCompletedDays,
    ) { calendar, completed, completedDays, freestyle, freestyleDays ->
        CalendarData(calendar, completed, completedDays, freestyle, freestyleDays)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        planCards,
        calendarData,
        workoutRepository.observeActiveSessions(),
        starting,
        daySummaryFlow,
    ) { cards, data, active, isStarting, daySummary ->
        val (summaryReq, details) = daySummary

        val today = time.todayEpochDay()
        val todayWeekday = LocalDate.ofEpochDay(today).dayOfWeek.value
        val cardById = cards.associateBy { it.id }

        fun itemsFor(epochDay: Long): List<TodayPlanItem> {
            val weekday = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
            // 循环排程：每周几固定出现；日历绑定：仅当天出现。同一计划两者都中时按日历标记。
            val cycle = cards.filter { weekday in it.scheduledDays }
            val bound = data.calendar.filter { it.epochDay == epochDay }
            val merged = buildList {
                cycle.forEach { add(TodayPlanItem(it.id, it.name, it.colorHex, fromCalendar = false)) }
                bound.forEach { bound ->
                    if (none { it.planId == bound.planId }) {
                        add(TodayPlanItem(bound.planId, bound.planName, bound.colorHex, fromCalendar = true))
                    }
                }
            }
            return merged
        }

        val weekPlans = (0L..6L).associate { offset ->
            val epochDay = weekStartEpochDay + offset
            LocalDate.ofEpochDay(epochDay).dayOfWeek.value to itemsFor(epochDay)
        }

        val daySummaryUi = summaryReq?.let { req ->
            val date = time.todayEpochDay() + (req.weekday - todayWeekday)
            DayPlanSummary(
                weekday = req.weekday,
                weekdayLabel = WEEKDAY_LABELS[req.weekday - 1],
                dateLabel = dateLabel(date),
                freestyleDone = date in data.freestyleDays,
                plans = req.items.map { item ->
                    val payload = details[item.planId]
                    DayPlanSummaryItem(
                        planId = item.planId,
                        name = item.name,
                        colorHex = item.colorHex,
                        totalSets = payload?.totalSets ?: 0,
                        exerciseCount = payload?.exerciseCount ?: 0,
                        muscles = payload?.muscles.orEmpty(),
                        exercises = payload?.exercises.orEmpty(),
                        completed = date in data.completedDays[item.planId].orEmpty(),
                    )
                },
            )
        }
        val isDaySummaryLoading = summaryReq != null && details.isEmpty()

        TodayUiState(
            isLoading = false,
            activeSessions = active,
            todayPlans = weekPlans[todayWeekday].orEmpty(),
            weekPlans = weekPlans,
            todayWeekday = todayWeekday,
            isStarting = isStarting,
            planCount = cardById.size,
            completedToday = data.completed,
            completedDays = data.completedDays,
            completedFreestyle = data.freestyle,
            freestyleDays = data.freestyleDays,
            todayEpochDay = today,
            daySummary = daySummaryUi,
            isDaySummaryLoading = isDaySummaryLoading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /**
     * 直接开练。
     * @param planId null = 自由训练（不按计划，训练页里想练什么加什么）
     */
    fun startWorkout(planId: Long?, onStarted: (Long) -> Unit) {
        if (starting.value) return
        viewModelScope.launch {
            starting.value = true
            val outcome = sessionManager.startWorkout(planId = planId)
            starting.value = false
            outcome.getOrNull()?.let(onStarted)
                ?: run { messageRes.value = R.string.today_start_failed }
        }
    }

    fun consumeMessage() = messageRes.update { null }
}

/** 周一~周日的中文缩写（与"训练日 chips"复用） */
private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** epochDay → "10月14日 周三" */
private fun dateLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return "${date.monthValue}月${date.dayOfMonth}日 ${WEEKDAY_LABELS[date.dayOfWeek.value - 1]}"
}

/**
 * 「训练」tab = 今日训练页：今天练什么直接开炼，
 * 计划与动作的配置收进右上角「训练库」—— 管理与执行分离。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onWorkoutStarted: (Long) -> Unit,
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageRes = viewModel.messageRes.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    // LaunchedEffect 不是 @Composable 上下文，不能在里面调 stringResource —— 用 context.getString
    LaunchedEffect(messageRes) {
        messageRes?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    // 训练库入口：计划/动作的配置管理都在里面
                    IconButton(onClick = onOpenLibrary) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = stringResource(R.string.today_open_library),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(
                                com.fitnesspro.core.designsystem.R.string.common_settings,
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = FitnessTheme.spacing.m,
                end = FitnessTheme.spacing.m,
                top = FitnessTheme.spacing.s,
                bottom = FitnessTheme.spacing.l + FitnessTheme.layout.bottomBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            // 进行中的训练永远置顶：恢复/继续比新开重要
            items(state.activeSessions.size, key = { "active-${state.activeSessions[it].id}" }) { index ->
                val session = state.activeSessions[index]
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { onOpenActiveWorkout(session.id) },
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(FitnessTheme.spacing.s))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.planName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.today_active_sets, session.completedSets),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.today_continue),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item(key = "today-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.today_plans_header),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            todayLabel(java.time.LocalDate.now()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.todayPlans.isEmpty() && state.completedFreestyle == null) {
                item(key = "today-empty") {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(FitnessTheme.spacing.m)) {
                            Text(
                                stringResource(R.string.today_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.xs))
                            Text(
                                stringResource(R.string.today_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.todayPlans.size, key = { "plan-${state.todayPlans[it].planId}" }) { index ->
                    val item = state.todayPlans[index]
                    TodayPlanRow(
                        item = item,
                        isStarting = state.isStarting,
                        completed = state.completedToday[item.planId],
                        onStart = { viewModel.startWorkout(item.planId, onWorkoutStarted) },
                        onOpen = { onOpenPlan(item.planId) },
                    )
                }
            }

            // 自由训练（空手开练）完成块：与计划完成块同权展示
            state.completedFreestyle?.let { done ->
                item(key = "freestyle-completed") {
                    FreestyleCompletedCard(done = done)
                }
            }

            item(key = "freestyle") {
                Button(
                    onClick = { viewModel.startWorkout(planId = null, onStarted = onWorkoutStarted) },
                    enabled = !state.isStarting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(FitnessTheme.spacing.xs))
                    Text(stringResource(R.string.today_freestyle))
                }
            }

            item(key = "week-header") {
                Text(
                    stringResource(R.string.today_week_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = FitnessTheme.spacing.s),
                )
            }

            item(key = "week") {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    WeekOverview(
                        weekPlans = state.weekPlans,
                        todayWeekday = state.todayWeekday,
                        todayEpochDay = state.todayEpochDay,
                        completedDays = state.completedDays,
                        onOpenPlan = onOpenPlan,
                        onDayClick = { weekday -> viewModel.openDaySummary(weekday) },
                    )
                }
            }

            item(key = "calendar-entry") {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSchedule,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.today_calendar_entry),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.today_calendar_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 周视图点击展开的训练计划小结 —— 放在 Scaffold 外层，与 LazyColumn 同级
    state.daySummary?.let { summary ->
        DayPlanSummarySheet(
            summary = summary,
            isLoading = state.isDaySummaryLoading,
            isStarting = state.isStarting,
            onDismiss = { viewModel.dismissDaySummary() },
            onStart = { planId ->
                viewModel.startWorkout(planId, onWorkoutStarted)
                viewModel.dismissDaySummary()
            },
            onOpenPlan = { planId ->
                viewModel.dismissDaySummary()
                onOpenPlan(planId)
            },
        )
    }
}

/** "完成训练"状态块的强调色：取活泼绿（VIVID_GREEN）主色 */
private val CompletedGreen = Color(0xFF34D399)

/** 自由训练（空手开练）完成卡：与计划完成块同权展示（绿色强调 + 本次训练数据） */
@Composable
private fun FreestyleCompletedCard(done: CompletedSessionSummary) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(start = FitnessTheme.spacing.s, top = FitnessTheme.spacing.m, bottom = FitnessTheme.spacing.m)
                        .size(width = 5.dp, height = 44.dp)
                        .background(CompletedGreen, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.today_freestyle_completed_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.today_freestyle_completed_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FitnessTheme.spacing.s)
                    .background(CompletedGreen.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = FitnessTheme.spacing.s, vertical = FitnessTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👊", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.today_completed_banner),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CompletedGreen.copy(alpha = 0.9f),
                    )
                    Text(
                        stringResource(
                            R.string.today_completed_detail,
                            done.totalSets,
                            formatVolume(done.totalVolumeKg),
                            done.totalDurationSeconds / 60,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("👍", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
        }
    }
}

/** 今天要练的一行：身份色条 + 名称 + 来源标记 + 开始按钮；当天已练完则展示绿色"完成训练"详情块 */
@Composable
private fun TodayPlanRow(
    item: TodayPlanItem,
    isStarting: Boolean,
    completed: CompletedSessionSummary?,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        onClick = onOpen,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(start = FitnessTheme.spacing.s, top = FitnessTheme.spacing.m, bottom = FitnessTheme.spacing.m)
                        .size(width = 5.dp, height = 44.dp)
                        .background(
                            if (completed != null) CompletedGreen else stripeColor(item.colorHex),
                            RoundedCornerShape(3.dp),
                        ),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            if (item.fromCalendar) R.string.today_from_calendar else R.string.today_from_weekly,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (completed == null) {
                    Spacer(Modifier.width(FitnessTheme.spacing.s))
                    Button(
                        onClick = onStart,
                        enabled = !isStarting,
                        contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.xs),
                    ) {
                        Text(stringResource(R.string.today_start))
                    }
                    Spacer(Modifier.width(FitnessTheme.spacing.s))
                }
            }
            // 训练详情块：当天已完成 → 绿色强调横幅 + 本次训练数据
            completed?.let { done ->
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FitnessTheme.spacing.s)
                        .background(CompletedGreen.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .padding(horizontal = FitnessTheme.spacing.s, vertical = FitnessTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("👊", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(FitnessTheme.spacing.s))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.today_completed_banner),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CompletedGreen.copy(alpha = 0.9f),
                        )
                        Text(
                            stringResource(
                                R.string.today_completed_detail,
                                done.totalSets,
                                formatVolume(done.totalVolumeKg),
                                done.totalDurationSeconds / 60,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("👍", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(FitnessTheme.spacing.s))
            }
        }
    }
}

/** 容量显示：整数不带小数位，其余保留 1 位 */
private fun formatVolume(volumeKg: Double): String =
    if (volumeKg % 1.0 == 0.0) "%.0f".format(volumeKg) else "%.1f".format(volumeKg)

/** 本周总览：七列，每列显示当天计划（缩写 chip），今天高亮；当天计划已全部/部分完成 → 色条变绿 */
@Composable
private fun WeekOverview(
    weekPlans: Map<Int, List<TodayPlanItem>>,
    todayWeekday: Int,
    todayEpochDay: Long,
    completedDays: Map<Long, Set<Long>>,
    onOpenPlan: (Long) -> Unit,
    onDayClick: (Int) -> Unit,
) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
    ) {
        labels.forEachIndexed { index, label ->
            val weekday = index + 1
            val plans = weekPlans[weekday].orEmpty()
            val isToday = weekday == todayWeekday
            // 该列对应日期：只要有计划的训练已完成，整列色条变绿（强调色）
            val epochDay = todayEpochDay + (weekday - todayWeekday)
            val dayCompleted = plans.any { epochDay in completedDays[it.planId].orEmpty() }
            // 整个列可点击：当天有排程 → 弹小结；没有 → 什么都不做（让用户自己明白"空的"）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = plans.isNotEmpty()) { onDayClick(weekday) }
                    .padding(vertical = FitnessTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                ) {
                    plans.forEach { plan ->
                        val planCompleted = epochDay in completedDays[plan.planId].orEmpty()
                        Box(
                            Modifier
                                .size(width = 24.dp, height = 8.dp)
                                .background(
                                    if (planCompleted) CompletedGreen else stripeColor(plan.colorHex),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** PlanCard 的 colorHex → Color；无色时回退到第一个强调色（与计划列表同一策略的轻量版） */
@Composable
private fun stripeColor(colorHex: String?): Color {
    val accents = FitnessTheme.layout.accents
    val fallback = accents.firstOrNull() ?: MaterialTheme.colorScheme.primary
    if (colorHex.isNullOrBlank()) return fallback
    return runCatching {
        val value = colorHex.removePrefix("#").toLong(16)
        Color(0xFF000000 or value)
    }.getOrDefault(fallback)
}

/** 顶栏/标题下的日期副标题：10月12日 周一（与"周视图小结"复用 WEEKDAY_LABELS） */
private fun todayLabel(date: LocalDate): String = dateLabel(date.toEpochDay())

// ---------------------------------------------------------------------------
// 周视图点击展开的训练计划小结面板
// ---------------------------------------------------------------------------

/**
 * 周视图"某一天的训练计划小结"弹层。
 *
 * 设计：使用 Material3 ModalBottomSheet，半屏高，标题显示日期，
 * 主体按"计划卡 + 目标肌群 chips + 动作列表"逐张展示。
 * 加载详情时整列给个 CircularProgressIndicator 兜底，避免空等。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPlanSummarySheet(
    summary: DayPlanSummary,
    isLoading: Boolean,
    isStarting: Boolean,
    onDismiss: () -> Unit,
    onStart: (Long) -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FitnessTheme.spacing.m)
                .padding(bottom = FitnessTheme.spacing.l),
        ) {
            // 标题：周X · 训练计划
            Text(
                text = stringResource(R.string.today_day_summary_title, summary.weekdayLabel),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isLoading) {
                Spacer(Modifier.height(FitnessTheme.spacing.m))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FitnessTheme.spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                Spacer(Modifier.height(FitnessTheme.spacing.m))
                summary.plans.forEachIndexed { index, plan ->
                    if (index > 0) Spacer(Modifier.height(FitnessTheme.spacing.m))
                    DayPlanCard(
                        plan = plan,
                        isStarting = isStarting,
                        onStart = { onStart(plan.planId) },
                        onOpen = { onOpenPlan(plan.planId) },
                    )
                }
                // 当天有已完成的自由训练（空手开练）→ 追加"自由训练 · 已完成"只读条目
                if (summary.freestyleDone) {
                    Spacer(Modifier.height(FitnessTheme.spacing.m))
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(width = 5.dp, height = 36.dp)
                                    .background(CompletedGreen, RoundedCornerShape(3.dp)),
                            )
                            Spacer(Modifier.width(FitnessTheme.spacing.s))
                            Text(
                                text = stringResource(R.string.today_freestyle_completed_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.today_day_summary_completed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CompletedGreen,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小结里的单张计划卡：色条 + 名称 + 顶部肌群 chips + 动作列表 + 底部双按钮 */
@Composable
private fun DayPlanCard(
    plan: DayPlanSummaryItem,
    isStarting: Boolean,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth()) {
            // 顶：色条 + 计划名 + 副信息
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = FitnessTheme.spacing.s, end = FitnessTheme.spacing.s,
                        top = FitnessTheme.spacing.m, bottom = FitnessTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 5.dp, height = 36.dp)
                        .background(
                            if (plan.completed) CompletedGreen else stripeColor(plan.colorHex),
                            RoundedCornerShape(3.dp),
                        ),
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plan.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 当天已完成 → 绿色"已完成"标注
                        if (plan.completed) {
                            Spacer(Modifier.width(FitnessTheme.spacing.xs))
                            Text(
                                text = stringResource(R.string.today_day_summary_completed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CompletedGreen,
                                modifier = Modifier
                                    .background(CompletedGreen.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = if (plan.exerciseCount > 0) {
                            stringResource(
                                R.string.today_day_summary_meta,
                                plan.exerciseCount,
                                plan.totalSets,
                            )
                        } else {
                            // 还没加载完时，给个"加载中"提示，避免显示 0
                            stringResource(R.string.today_day_summary_loading)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 肌群 chips：一行内排列，超出换行
            if (plan.muscles.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FitnessTheme.spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                ) {
                    plan.muscles.forEach { muscle ->
                        MuscleChip(muscle = muscle)
                    }
                }
            }

            // 动作列表
            if (plan.exercises.isNotEmpty()) {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FitnessTheme.spacing.s),
                ) {
                    plan.exercises.forEachIndexed { i, ex ->
                        if (i > 0) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                        DayPlanExerciseRow(row = ex)
                    }
                }
            }

            // 底部双按钮
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FitnessTheme.spacing.s, vertical = FitnessTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
            ) {
                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = FitnessTheme.spacing.xs),
                ) {
                    Text(stringResource(R.string.today_day_summary_open))
                }
                Button(
                    onClick = onStart,
                    enabled = !isStarting,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = FitnessTheme.spacing.xs),
                ) {
                    Text(stringResource(R.string.today_start))
                }
            }
        }
    }
}

/** 单个动作行：名称 + 组数×次数 + 肌群标签 */
@Composable
private fun DayPlanExerciseRow(row: DayPlanExerciseRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FitnessTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(FitnessTheme.spacing.s))
        Text(
            text = if (row.targetReps.isNullOrBlank()) {
                "${row.targetSets} 组"
            } else {
                "${row.targetSets} × ${row.targetReps}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.muscle?.let {
            Spacer(Modifier.width(FitnessTheme.spacing.s))
            MuscleChip(muscle = it, compact = true)
        }
    }
}

/** 目标肌群 chip：跟随 designsystem 主题色（CHEST 橙色、BACK 蓝、SHOULDERS 紫 等） */
@Composable
private fun MuscleChip(
    muscle: com.fitnesspro.core.model.MuscleGroup,
    compact: Boolean = false,
) {
    val (bg, fg) = muscleChipColors(muscle)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(muscle.labelRes()),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

/** 肌群色：复用 designsystem 的肌肉色阶，但取"最浅"档位当 chip 底色 */
@Composable
private fun muscleChipColors(muscle: com.fitnesspro.core.model.MuscleGroup): Pair<Color, Color> {
    val color = com.fitnesspro.core.designsystem.theme.muscleColor(muscle)
    val bg = color.copy(alpha = 0.18f)
    val fg = color
    return bg to fg
}
