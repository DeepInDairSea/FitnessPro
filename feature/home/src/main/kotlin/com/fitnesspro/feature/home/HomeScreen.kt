package com.fitnesspro.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fitnesspro.core.designsystem.component.BentoNumberCard
import com.fitnesspro.core.designsystem.component.BentoRow
import com.fitnesspro.core.designsystem.component.BentoSectionCard
import com.fitnesspro.core.designsystem.component.BentoStatTile
import com.fitnesspro.core.designsystem.component.CalendarHeatmap
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.UserAvatar
import com.fitnesspro.core.designsystem.component.bentoAppear
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.muscleColor
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.designsystem.component.TrendLineChart
import com.fitnesspro.core.designsystem.formatWeightCompact
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.repository.ActiveSessionInfo
import com.fitnesspro.core.domain.repository.CompletedSessionSummary
import com.fitnesspro.core.domain.repository.PersonalRecordRepository
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.domain.repository.StatsRepository
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.domain.workout.WorkoutDraft
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val hourOfDay: Int = 9,
    /** 进行中的会话列表：允许同时存在多个未完成训练，逐条展示互不挤占 */
    val activeSessions: List<ActiveSessionInfo> = emptyList(),
    val todayPlans: List<PlanCard> = emptyList(),
    val streakWeeks: Int = 0,
    val totalSessions: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val weekSessionCount: Int = 0,
    /** 近 7 天每日容量，首页迷你趋势图用；点击跳转数据页看详情 */
    val recentPoints: List<Float> = emptyList(),
    val isStarting: Boolean = false,
    /** 训练热力图：epochDay -> 当天训练次数（便当盒格） */
    val heatmap: Map<Long, Int> = emptyMap(),
    /** 今天的 epochDay，热力图定位"今天"用 */
    val todayEpochDay: Long = 0L,
    /** 当天已完成训练的计划 → 聚合数据（"完成训练"状态块） */
    val completedToday: Map<Long, CompletedSessionSummary> = emptyMap(),
    /** 当天自由训练（空手开练）的完成聚合；与计划完成块同权展示 */
    val completedFreestyle: CompletedSessionSummary? = null,
    /** 最近几条个人纪录（便当盒格） */
    val topPrs: List<PersonalRecord> = emptyList(),
    /** 肌群容量 Top（肌群 -> 容量 kg），便当盒格 */
    val muscleEntries: List<Pair<MuscleGroup, Float>> = emptyList(),
    /** 顶栏头像与昵称（个人资料，设置页可改） */
    val nickname: String = "",
    val avatarUri: String? = null,
    @androidx.annotation.StringRes val lastMessageRes: Int? = null,
)

/**
 * 首页聚合。
 *
 * 设计立场：首页只回答三个问题 ——
 *  1. 我是不是有一场没练完的训练？（继续训练卡）
 *  2. 今天该练什么？（今日排程）
 *  3. 我最近坚持得怎么样？（本周概览）
 * 其它一切（计划管理、数据浏览）都是二级入口，放在快捷入口区。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    private val statsRepository: StatsRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    private val settings: SettingsRepository,
    private val sessionManager: WorkoutSessionManager,
    private val time: TimeProvider,
) : ViewModel() {

    private data class WeekStats(
        val streakWeeks: Int = 0,
        val totalSessions: Int = 0,
        val volumeKg: Double = 0.0,
        val sessionCount: Int = 0,
        /** 近 7 天每日容量（kg），首页迷你图表用 */
        val recentPoints: List<Float> = emptyList(),
        val heatmap: Map<Long, Int> = emptyMap(),
        val topPrs: List<PersonalRecord> = emptyList(),
        val muscleEntries: List<Pair<MuscleGroup, Float>> = emptyList(),
    )

    private val weekStats = MutableStateFlow(WeekStats())
    private val starting = MutableStateFlow(false)

    /** 消息用资源 id：ViewModel 层不硬编码文案，可测试性更好 */
    private val messageRes = MutableStateFlow<Int?>(null)

    /**
     * 待恢复的训练草稿（C10）：进入首页时探测一次，用户选择"继续"或"丢弃"后清空。
     * 只提示一次 —— 每次回首页都弹会变成骚扰。
     */
    private val draft = MutableStateFlow<WorkoutDraft?>(null)
    val draftPrompt: StateFlow<WorkoutDraft?> = draft.asStateFlow()

    /** 消息 + 用户资料打包成一个流：combine 的重载最多 5 个流，这里合并第 5 位 */
    private val messageAndUser = combine(messageRes, settings.settings) { msg, user ->
        msg to user
    }

    /** 当天已完成训练按计划聚合：随训练完成实时刷新 */
    private val completedByPlan =
        workoutRepository.observeCompletedByPlanOnDay(time.todayEpochDay())
            .map { list -> list.associateBy { it.planId } }

    /** 当天自由训练（空手开练）的完成聚合：与计划完成块同权展示 */
    private val freestyleCompleted = workoutRepository.observeFreestyleCompletedOnDay(time.todayEpochDay())

    /** 计划卡与完成状态打包：combine 的重载最多 5 个流，这里合并第 1 位 */
    private val plansAndCompleted = combine(
        settings.settings.flatMapLatest { planRepository.observePlanCards(it.planSortOrder, query = "") },
        completedByPlan,
        freestyleCompleted,
    ) { plans, completed, freestyle -> Triple(plans, completed, freestyle) }

    val uiState: StateFlow<HomeUiState> = combine(
        plansAndCompleted,
        workoutRepository.observeActiveSessions(),
        weekStats,
        starting,
        messageAndUser,
    ) { (plans, completed, freestyle), activeSessions, stats, isStarting, (msgRes, user) ->
        val today = LocalDate.ofEpochDay(time.todayEpochDay())
        HomeUiState(
            isLoading = false,
            hourOfDay = Instant.ofEpochMilli(time.nowEpochMs())
                .atZone(ZoneId.systemDefault()).hour,
            activeSessions = activeSessions,
            // 排程语义 1 = 周一（ISO），与提醒、计划编辑一致
            todayPlans = plans.filter { today.dayOfWeek.value in it.scheduledDays },
            streakWeeks = stats.streakWeeks,
            totalSessions = stats.totalSessions,
            weekVolumeKg = stats.volumeKg,
            weekSessionCount = stats.sessionCount,
            recentPoints = stats.recentPoints,
            heatmap = stats.heatmap,
            todayEpochDay = time.todayEpochDay(),
            topPrs = stats.topPrs,
            muscleEntries = stats.muscleEntries,
            isStarting = isStarting,
            completedToday = completed,
            completedFreestyle = freestyle,
            nickname = user.nickname,
            avatarUri = user.avatarUri,
            lastMessageRes = msgRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshStats()
        probeDraft()
    }

    /** 冷启动探测：上一次没练完的训练（进程被杀、中途退出都算） */
    private fun probeDraft() {
        viewModelScope.launch {
            // 失败就当"没有草稿"：首页卡片仍能看到进行中的训练，不阻塞主流程
            draft.value = runCatching { sessionManager.findResumableDraft() }.getOrNull()
        }
    }

    /** 继续草稿：带上 sessionId 交给导航，并立刻撤销提示 */
    fun resumeDraft(onResume: (Long) -> Unit) {
        val id = draft.value?.sessionId ?: return
        draft.value = null
        onResume(id)
    }

    /** 稍后再说：本次不再提示，但训练仍在首页"进行中"列表里 */
    fun dismissDraft() {
        draft.value = null
    }

    /** 丢弃草稿：移入回收站，之后仍可从回收站恢复 */
    fun discardDraft() {
        val id = draft.value?.sessionId ?: return
        viewModelScope.launch {
            sessionManager.discard(id)
            draft.value = null
            messageRes.value = R.string.home_draft_discarded
        }
    }

    /**
     * 每次进入首页都重算本周概览：数据可能刚在上一次训练里产生。
     *
     * 失败自动重试：刚安装后的首次启动偶发 DataStore/DB 未就绪（实测会
     * 静默失败），重试可避免首页停在"0 次"—— 用户对 0 的理解是"我没练过"，
     * 这是一条错误信息，比查询失败本身更糟。
     */
    fun refreshStats() {
        viewModelScope.launch {
            for (attempt in 0 until 3) {
                runCatching {
                    val firstDayOfWeek = settings.current().firstDayOfWeek
                    val today = time.todayEpochDay()
                    val weekStartDay = time.weekStartEpochDay(today, firstDayOfWeek)
                    val sinceMs = weekStartDay * 24L * 60 * 60 * 1000
                    val points = statsRepository.dailyVolume(sinceMs)
                    val recent = statsRepository.dailyVolume(time.nowEpochMs() - 7L * 24 * 60 * 60 * 1000)
                    weekStats.value = WeekStats(
                        streakWeeks = statsRepository.streakDays(firstDayOfWeek),
                        totalSessions = statsRepository.totalCompletedSessions(),
                        volumeKg = points.sumOf { it.volumeKg },
                        sessionCount = points.sumOf { it.sessionCount },
                        recentPoints = recent.map { it.volumeKg.toFloat() },
                        heatmap = statsRepository.calendarHeatmap()
                            .associate { it.epochDay to it.count },
                        // 首页只展示最近 4 条：完整列表在数据页，避免首页变成数据仓库
                        topPrs = personalRecordRepository.observeAll().first().take(4),
                        muscleEntries = statsRepository.muscleDistribution(sinceMs)
                            .take(4)
                            .map { it.muscle to it.volumeKg.toFloat() },
                    )
                }.onSuccess { return@launch }
                    .onFailure { if (attempt < 2) delay(2_000L * (attempt + 1)) }
            }
        }
    }

    /**
     * 从首页直接开练（跳过计划详情页）。
     * 预填模式用默认值 LAST_SESSION —— 想改处方的人会去详情页编辑，首页不提供该能力。
     */
    fun startWorkout(plan: PlanCard, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            starting.value = true
            val outcome = sessionManager.startWorkout(planId = plan.id)
            starting.value = false
            outcome.getOrNull()?.let(onStarted)
                ?: run { messageRes.value = R.string.home_start_failed }
        }
    }

    fun consumeMessage() {
        messageRes.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onWorkoutStarted: (Long) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExerciseLibrary: () -> Unit,
    /** 迷你图表 → 数据页（统计详情） */
    onOpenStats: () -> Unit,
    /** 训练日历：把计划绑定到具体日期 */
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.lastMessageRes) {
        val res = state.lastMessageRes ?: return@LaunchedEffect
        viewModel.consumeMessage()
        // LaunchedEffect 非组合作用域，stringResource 用不了，改走 Context
        snackbarHostState.showSnackbar(context.getString(res), actionLabel = null)
    }

    // 上一次没练完的训练：只提示一次，用户决定继续还是丢弃
    val draft by viewModel.draftPrompt.collectAsStateWithLifecycle()
    draft?.let { pending ->
        DraftRecoveryDialog(
            draft = pending,
            onResume = { viewModel.resumeDraft(onOpenActiveWorkout) },
            onDismiss = viewModel::dismissDraft,
            onDiscard = viewModel::discardDraft,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                // 顶栏 = 圆形头像 + 昵称；问候语与日期移到右侧，填满原先的大片空缺
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            nickname = state.nickname,
                            avatarUri = state.avatarUri,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.nickname.ifBlank { stringResource(R.string.home_title) },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(greetingRes(state.hourOfDay)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            formatDate(java.time.LocalDate.now()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(FitnessTheme.spacing.xs))
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
        // 便当盒主题走独立的网格排版（大小卡错落），其余主题保持原有一列布局
        if (FitnessTheme.layout.bentoGrid) {
            BentoHomeBody(
                state = state,
                scaffoldPadding = padding,
                onOpenActiveWorkout = onOpenActiveWorkout,
                onStartWorkout = { viewModel.startWorkout(it, onWorkoutStarted) },
                onOpenPlan = onOpenPlan,
                onOpenPlans = onOpenPlans,
                onOpenHistory = onOpenHistory,
                onOpenExerciseLibrary = onOpenExerciseLibrary,
                onOpenStats = onOpenStats,
                onOpenSchedule = onOpenSchedule,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FitnessTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.m),
        ) {
            // 顶部优先级最高的卡片：没练完的训练永远排在"今天练什么"之前。
            // 支持多个并存：每条未完成训练一张卡，恢复回收站记录不会再挤掉它们
            items(state.activeSessions, key = { "active-${it.id}" }) { session ->
                ActiveSessionCard(
                    session = session,
                    onOpen = { onOpenActiveWorkout(session.id) },
                )
            }

            item(key = "today-header") {
                Text(
                    stringResource(R.string.home_today_plans),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.todayPlans.isEmpty() && state.completedFreestyle == null) {
                item(key = "today-empty") {
                    Text(
                        stringResource(R.string.home_today_plans_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.todayPlans.size, key = { state.todayPlans[it].id }) { index ->
                    val plan = state.todayPlans[index]
                    TodayPlanCard(
                        plan = plan,
                        isStarting = state.isStarting,
                        completed = state.completedToday[plan.id],
                        onStart = { viewModel.startWorkout(plan, onWorkoutStarted) },
                        onOpen = { onOpenPlan(plan.id) },
                    )
                }
            }
            // 自由训练（空手开练）完成块：与计划完成块同权展示
            state.completedFreestyle?.let { done ->
                item(key = "freestyle-completed") {
                    Card(onClick = {}) {
                        Column(Modifier.padding(FitnessTheme.spacing.m)) {
                            Text(
                                stringResource(R.string.home_completed_freestyle_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.s))
                            CompletedBanner(done = done)
                        }
                    }
                }
            }

            item(key = "week") {
                WeekOverviewCard(
                    streakWeeks = state.streakWeeks,
                    totalSessions = state.totalSessions,
                    weekVolumeKg = state.weekVolumeKg,
                    weekSessionCount = state.weekSessionCount,
                )
            }

            // 迷你趋势卡：点击进数据页看完整统计（趋势/肌群/日历/PR）
            item(key = "trend") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStats),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(FitnessTheme.spacing.m)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.home_recent_trend),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.home_view_stats),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        TrendLineChart(
                            points = state.recentPoints,
                            emptyText = stringResource(R.string.home_trend_empty),
                            valueFormatter = { formatWeightCompact(it.toDouble()) },
                            contentDescription = stringResource(R.string.home_recent_trend),
                        )
                    }
                }
            }

            item(key = "quick-header") {
                Text(
                    stringResource(R.string.home_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item(key = "quick") {
                Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                    OutlinedButton(onClick = onOpenPlans, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_action_plans), maxLines = 1)
                    }
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_action_history), maxLines = 1)
                    }
                    OutlinedButton(onClick = onOpenExerciseLibrary, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_action_exercises), maxLines = 1)
                    }
                }
            }

            item(key = "bottom") {
                Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
            }
        }
    }
}

private fun greetingRes(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_afternoon
    else -> R.string.home_greeting_evening
}

// 星期用显式映射而不是 DateTimeFormatter+Locale：
// 设备上 zh locale 数据缺失时 EEEE 会退回英文，而这里必须确定性地显示中文
private val chineseWeekDays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun formatDate(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日 ${chineseWeekDays[date.dayOfWeek.value - 1]}"

// ---------------------------------------------------------------------------
// 便当盒布局（BENTO_SOFT 主题专用）
//
// 排版与经典布局完全不同：一张带图表的主卡 + 右侧竖排彩色小块 + 2×2 快捷网格，
// 大小错落制造"便当盒"的节奏；数据与交互与经典布局完全一致。
// ---------------------------------------------------------------------------

@Composable
private fun BentoHomeBody(
    state: HomeUiState,
    scaffoldPadding: PaddingValues,
    onOpenActiveWorkout: (Long) -> Unit,
    onStartWorkout: (PlanCard) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExerciseLibrary: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val gap = FitnessTheme.layout.cardGap
    val accents = FitnessTheme.layout.accents

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(FitnessTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(state.activeSessions, key = { "active-${it.id}" }) { session ->
            BentoActiveSessionCard(
                session = session,
                onOpen = { onOpenActiveWorkout(session.id) },
            )
        }

        // 主卡（左，带趋势图）+ 两块彩色小卡（右）—— 便当盒的视觉重心
        item(key = "bento-hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp)
                    .bentoAppear(index = 1),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                BentoWeeklyCard(
                    state = state,
                    onOpenStats = onOpenStats,
                    modifier = Modifier.weight(1.62f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    BentoStatTile(
                        label = stringResource(R.string.home_streak_weeks),
                        value = state.streakWeeks.toString(),
                        unit = stringResource(R.string.home_streak_unit),
                        container = accents.getOrElse(0) { MaterialTheme.colorScheme.primary },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    BentoStatTile(
                        label = stringResource(R.string.home_total_sessions),
                        value = state.totalSessions.toString(),
                        unit = stringResource(R.string.home_times_unit),
                        container = accents.getOrElse(1) { MaterialTheme.colorScheme.secondary },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }

        // 训练热力图：一眼看到"坚持程度"，是便当盒里最有科技感的一格
        item(key = "bento-heatmap") {
            BentoSectionCard(
                title = stringResource(R.string.home_heatmap),
                modifier = Modifier.bentoAppear(index = 2),
                onClick = onOpenStats,
                trailing = {
                    Text(
                        stringResource(R.string.home_view_stats),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                CalendarHeatmap(
                    counts = state.heatmap,
                    weeks = 22,
                    todayEpochDay = state.todayEpochDay,
                )
            }
        }

        // 肌群分布（宽格） + PR 数量（窄格）：大小错落，避免一列等宽卡片。
        // 高度取两者中较高者（IntrinsicSize.Min），窄格才能撑到与宽格齐平
        item(key = "bento-muscle") {
            BentoRow(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top,
            ) {
                BentoMuscleCard(
                    entries = state.muscleEntries,
                    modifier = Modifier
                        .weight(1.55f)
                        .bentoAppear(index = 3),
                )
                // 一屏里饱和色块已经够多，这一格改用白卡 + 强调色数字，色块才重新成为重点
                BentoNumberCard(
                    label = stringResource(R.string.home_pr_count),
                    value = state.topPrs.size.toString(),
                    unit = stringResource(R.string.home_pr_unit),
                    accent = accents.getOrElse(2) { MaterialTheme.colorScheme.primary },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bentoAppear(index = 4),
                )
            }
        }

        item(key = "bento-prs") {
            BentoPrCard(
                prs = state.topPrs,
                modifier = Modifier.bentoAppear(index = 5),
                onClick = onOpenStats,
            )
        }

        item(key = "today-header") {
            Text(
                stringResource(R.string.home_today_plans),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = FitnessTheme.spacing.s),
            )
        }

        if (state.todayPlans.isEmpty() && state.completedFreestyle == null) {
            item(key = "today-empty") {
                SoftCard(contentPadding = PaddingValues(FitnessTheme.spacing.m)) {
                    Text(
                        stringResource(R.string.home_today_plans_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.todayPlans.size, key = { state.todayPlans[it].id }) { index ->
                val plan = state.todayPlans[index]
                BentoPlanCard(
                    plan = plan,
                    isStarting = state.isStarting,
                    accent = accents.getOrElse(index + 2) { MaterialTheme.colorScheme.primary },
                    completed = state.completedToday[plan.id],
                    onStart = { onStartWorkout(plan) },
                    onOpen = { onOpenPlan(plan.id) },
                )
            }
            // 自由训练（空手开练）完成块：与计划完成块同权展示
            state.completedFreestyle?.let { done ->
                item(key = "freestyle-completed") {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                stringResource(R.string.home_completed_freestyle_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.s))
                            CompletedBanner(done = done)
                        }
                    }
                }
            }
        }

        item(key = "quick-header") {
            Text(
                stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = FitnessTheme.spacing.s),
            )
        }

        // 2×2 快捷网格：小方块是便当盒里"填充空隙"的那几格
        item(key = "quick-grid") {
            Column(
                modifier = Modifier.bentoAppear(index = 6),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BentoQuickTile(
                        label = stringResource(R.string.home_action_plans),
                        accent = accents.getOrElse(2) { MaterialTheme.colorScheme.primary },
                        onClick = onOpenPlans,
                        modifier = Modifier.weight(1f),
                    )
                    BentoQuickTile(
                        label = stringResource(R.string.home_action_history),
                        accent = accents.getOrElse(0) { MaterialTheme.colorScheme.primary },
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BentoQuickTile(
                        label = stringResource(R.string.home_action_exercises),
                        accent = accents.getOrElse(3) { MaterialTheme.colorScheme.primary },
                        onClick = onOpenExerciseLibrary,
                        modifier = Modifier.weight(1f),
                    )
                    BentoQuickTile(
                        label = stringResource(R.string.home_action_schedule),
                        accent = accents.getOrElse(1) { MaterialTheme.colorScheme.secondary },
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 悬浮菜单压在内容之上：留白要够最后一排格子完全露出并可继续上滚
        item(key = "bottom") {
            Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
        }
    }
}

/** 便当盒主卡：本周容量 + 近 7 天趋势，点击进统计页 */
@Composable
private fun BentoWeeklyCard(
    state: HomeUiState,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier = modifier, onClick = onOpenStats) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_week_volume),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.home_view_stats),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        Text(
            text = formatVolume(state.weekVolumeKg),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_sessions_count, state.weekSessionCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.s))
        // 高度必须显式给：用 weight 会让图表被压到几十 dp，坐标轴标签直接被裁掉
        TrendLineChart(
            points = state.recentPoints,
            modifier = Modifier.fillMaxWidth(),
            height = 100.dp,
            emptyText = stringResource(R.string.home_trend_empty),
            valueFormatter = { formatWeightCompact(it.toDouble()) },
            contentDescription = stringResource(R.string.home_recent_trend),
        )
    }
}

/** 彩色数据小块与便当盒构件已上移到 designsystem（BentoStatTile/BentoRow/BentoSectionCard） */

/** 肌群分布（便当盒宽格）：用彩色迷你条代替完整图表，窄格里也能读 */
@Composable
private fun BentoMuscleCard(
    entries: List<Pair<MuscleGroup, Float>>,
    modifier: Modifier = Modifier,
) {
    val max = entries.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f

    BentoSectionCard(
        title = stringResource(R.string.home_muscle_distribution),
        modifier = modifier,
    ) {
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.home_muscle_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@BentoSectionCard
        }
        entries.forEach { (muscle, value) ->
            Column(Modifier.padding(vertical = FitnessTheme.spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(muscle.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatWeightCompact(value.toDouble())} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(3.dp),
                        ),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((value / max).coerceIn(0.04f, 1f))
                            .height(6.dp)
                            .background(
                                muscleColor(muscle),
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}

/** 个人纪录（便当盒长条格）：最近几条 PR，点击进数据页看完整列表 */
@Composable
private fun BentoPrCard(
    prs: List<PersonalRecord>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BentoSectionCard(
        title = stringResource(R.string.home_pr_list),
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Text(
                stringResource(R.string.home_view_stats),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        if (prs.isEmpty()) {
            Text(
                stringResource(R.string.home_pr_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@BentoSectionCard
        }
        prs.forEach { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FitnessTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrBadge(compact = true)
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        record.exerciseName.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(record.prType.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = when (record.prType) {
                        PrType.MAX_REPS -> "${record.value.toInt()} 次"
                        else -> formatWeightCompact(record.value)
                    },
                    style = numericTextStyle(NumericSize.Small),
                )
            }
        }
    }
}

/** 便当盒计划卡：左侧一道粗色条做分类，右侧信息与主操作；当天已练完则展示"完成训练"详情块 */
@Composable
private fun BentoPlanCard(
    plan: PlanCard,
    isStarting: Boolean,
    accent: Color,
    completed: CompletedSessionSummary?,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 色条：让多张计划卡在便当盒里也能一眼区分
            Box(
                Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .background(if (completed != null) CompletedGreen else accent, RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(FitnessTheme.spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    buildString {
                        append("${plan.exerciseCount} 个动作 · ${plan.totalSets} 组")
                        plan.estimatedDurationSeconds?.let { append(" · 约 ${formatDuration(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(FitnessTheme.spacing.m))
        if (completed != null) {
            CompletedBanner(done = completed)
        } else {
            Button(
                onClick = onStart,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (isStarting) R.string.home_starting else R.string.home_start_workout,
                    ),
                )
            }
        }
    }
}

/** "完成训练"状态块的强调色：取活泼绿（VIVID_GREEN）主色 */
private val CompletedGreen = Color(0xFF34D399)

/** 绿色"完成训练！👊GOT IT！👍"横幅 + 本次训练数据（今日页与首页共用） */
@Composable
private fun CompletedBanner(done: CompletedSessionSummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CompletedGreen.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = FitnessTheme.spacing.s, vertical = FitnessTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("👊", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(FitnessTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_completed_banner),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CompletedGreen.copy(alpha = 0.9f),
            )
            Text(
                stringResource(
                    R.string.home_completed_detail,
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
}

/** 容量显示：整数不带小数位，其余保留 1 位 */
private fun formatVolume(volumeKg: Double): String =
    if (volumeKg % 1.0 == 0.0) "%.0f".format(volumeKg) else "%.1f".format(volumeKg)

/** 便当盒快捷格：顶部一道强调色 + 文字，点击跳转 */
@Composable
private fun BentoQuickTile(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier = modifier, onClick = onClick) {
        Box(
            Modifier
                .width(28.dp)
                .height(6.dp)
                .background(accent, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(FitnessTheme.spacing.s))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 便当盒里的"训练进行中"卡：优先级最高，用强调色实心卡占位 */
@Composable
private fun BentoActiveSessionCard(session: ActiveSessionInfo, onOpen: () -> Unit) {
    val startedText = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault()).toLocalDate()
        .let { formatDate(it) }

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            stringResource(R.string.home_active_session),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        Text(
            session.planName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.home_active_progress, session.completedSets, startedText),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.m))
        Text(
            text = stringResource(R.string.home_active_session_cta) + "  →",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TodayPlanCard(
    plan: PlanCard,
    isStarting: Boolean,
    completed: CompletedSessionSummary?,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Text(
                plan.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            Text(
                buildString {
                    append("${plan.exerciseCount} 个动作 · ${plan.totalSets} 组")
                    plan.estimatedDurationSeconds?.let {
                        append(" · 约 ${formatDuration(it)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            if (completed != null) {
                CompletedBanner(done = completed)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onStart, enabled = !isStarting) {
                        Text(
                            stringResource(
                                if (isStarting) R.string.home_starting else R.string.home_start_workout,
                            ),
                        )
                    }
                    Spacer(Modifier.width(FitnessTheme.spacing.s))
                    plan.lastPerformedAt?.let { last ->
                        val date = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(
                            stringResource(R.string.home_started_at, formatDate(date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 草稿恢复询问框：上次训练没练完，让用户选择继续还是丢弃 */
@Composable
private fun DraftRecoveryDialog(
    draft: WorkoutDraft,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_draft_title)) },
        text = {
            Text(
                stringResource(
                    R.string.home_draft_message,
                    draft.planName,
                    draft.completedSets,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.home_draft_resume))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.home_draft_discard))
            }
        },
    )
}

/** 未完成训练卡：显示计划名、已完成组数与开始时间，点击回到训练页 */
@Composable
private fun ActiveSessionCard(session: ActiveSessionInfo, onOpen: () -> Unit) {
    val startedText = java.time.Instant.ofEpochMilli(session.startedAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .let { formatDate(it) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Text(
                stringResource(R.string.home_active_session),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            Text(
                session.planName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.home_active_progress, session.completedSets, startedText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Button(onClick = onOpen) {
                Text(stringResource(R.string.home_active_session_cta))
            }
        }
    }
}

@Composable
private fun WeekOverviewCard(
    streakWeeks: Int,
    totalSessions: Int,
    weekVolumeKg: Double,
    weekSessionCount: Int,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Text(
                stringResource(R.string.home_week_overview),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.s))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewCell(
                    label = stringResource(R.string.home_week_volume),
                    value = formatVolume(weekVolumeKg),
                )
                OverviewCell(
                    label = stringResource(R.string.home_week_sessions),
                    value = stringResource(R.string.home_sessions_count, weekSessionCount),
                )
                OverviewCell(
                    label = stringResource(R.string.home_streak_weeks),
                    value = stringResource(R.string.home_streak_week, streakWeeks),
                )
                OverviewCell(
                    label = stringResource(R.string.home_total_sessions),
                    value = stringResource(R.string.home_sessions_count, totalSessions),
                )
            }
        }
    }
}

@Composable
private fun OverviewCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
