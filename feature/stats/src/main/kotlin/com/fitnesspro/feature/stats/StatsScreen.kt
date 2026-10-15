package com.fitnesspro.feature.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.component.BentoRow
import com.fitnesspro.core.designsystem.component.BentoSectionCard
import com.fitnesspro.core.designsystem.component.BentoStatTile
import com.fitnesspro.core.designsystem.component.CalendarHeatmap
import com.fitnesspro.core.designsystem.component.DistributionBarChart
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.TrendLineChart
import com.fitnesspro.core.designsystem.component.bentoAppear
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.muscleColor
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.domain.repository.ExerciseFrequency
import com.fitnesspro.core.domain.repository.StatsRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.domain.repository.PersonalRecordRepository
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnesspro.feature.stats.R as StatsR

enum class StatsRange(val days: Long) {
    D30(30),
    D90(90),
    D180(180),
    ALL(3650),
}

data class StatsUiState(
    val isLoading: Boolean = true,
    val range: StatsRange = StatsRange.D90,
    val weekSessions: Int = 0,
    val streakWeeks: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val totalSessions: Int = 0,
    val volumePoints: List<Float> = emptyList(),
    // 保存枚举而不是已翻译的字符串：翻译必须在 @Composable 上下文里做，
    // 而这里的数据来自 ViewModel（非 Composable）
    val muscleEntries: List<Pair<com.fitnesspro.core.model.MuscleGroup, Float>> = emptyList(),
    val muscleBySets: Boolean = false,
    val topExercises: List<ExerciseFrequency> = emptyList(),
    val heatmap: Map<Long, Int> = emptyMap(),
    val records: List<PersonalRecord> = emptyList(),
    val todayEpochDay: Long = 0L,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // PR 列表与热力图与时间范围无关，订阅一次即可
            personalRecordRepository.observeAll().collect { records ->
                _uiState.update { it.copy(records = records) }
            }
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    heatmap = statsRepository.calendarHeatmap().associate { day -> day.epochDay to day.count },
                )
            }
        }
        viewModelScope.launch { reload(StatsRange.D90) }
    }

    fun setRange(range: StatsRange) {
        _uiState.update { it.copy(range = range) }
        viewModelScope.launch { reload(range) }
    }

    fun toggleMuscleMetric() {
        val bySets = !_uiState.value.muscleBySets
        _uiState.update { it.copy(muscleBySets = bySets) }
        viewModelScope.launch { loadMuscle(StatsRange.valueOf(_uiState.value.range.name)) }
    }

    private suspend fun reload(range: StatsRange) {
        val now = time.nowEpochMs()
        val since = now - range.days * 24L * 60 * 60 * 1000
        val today = time.todayEpochDay()
        val userSettings = settings.settings.first()
        val weekStart = time.weekStartEpochDay(today, userSettings.firstDayOfWeek)

        val daily = runCatching { statsRepository.dailyVolume(since) }.getOrDefault(emptyList())
        val thisWeek = daily.filter { it.epochDay >= weekStart }

        _uiState.update {
            it.copy(
                isLoading = false,
                range = range,
                weekSessions = thisWeek.sumOf { point -> point.sessionCount },
                weekVolumeKg = thisWeek.sumOf { point -> point.volumeKg },
                streakWeeks = runCatching {
                    statsRepository.streakDays(userSettings.firstDayOfWeek)
                }.getOrDefault(0),
                totalSessions = runCatching { statsRepository.totalCompletedSessions() }.getOrDefault(0),
                volumePoints = daily.map { point -> point.volumeKg.toFloat() },
                topExercises = runCatching { statsRepository.topExercises(since, limit = 10) }
                    .getOrDefault(emptyList()),
                todayEpochDay = today,
            )
        }

        loadMuscle(range)
    }

    private suspend fun loadMuscle(range: StatsRange) {
        val now = time.nowEpochMs()
        val since = now - range.days * 24L * 60 * 60 * 1000
        val bySets = _uiState.value.muscleBySets

        val distribution = runCatching { statsRepository.muscleDistribution(since) }
            .getOrDefault(emptyList())

        // 按组数还是按容量：两个口径回答的是不同问题。
        // "容量"反映训练量（重量×次数），"组数"反映训练频次 —— 练 20 组轻重量和高重量 5 组
        // 在这两个口径下的排序完全不同，所以必须让用户能切。
        val sorted = if (bySets) {
            distribution.sortedByDescending { it.setCount }
                .map { it.muscle to it.setCount.toFloat() }
        } else {
            distribution.sortedByDescending { it.volumeKg }
                .map { it.muscle to it.volumeKg.toFloat() }
        }.filter { it.second > 0f }

        _uiState.update { it.copy(muscleEntries = sorted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // LazyColumn 的 content lambda 不是 @Composable，令牌必须在组合作用域里先取出来
    val bento = FitnessTheme.layout.bentoGrid
    val bentoGap = FitnessTheme.layout.cardGap
    val baseGap = FitnessTheme.spacing.s

    // 统计页强调色 = 蓝（便当盒主题的四色板之一）：图表/热力图随之变蓝
    val pageAccent = FitnessTheme.layout.accents.getOrNull(2)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { FitnessTopAppBar(title = { Text(stringResource(StatsR.string.stats_title)) }) },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 与顶栏保持一屏可见的距离；横向边距由宿主提供（本页与数据页共用 statsItems）
            contentPadding = PaddingValues(
                start = FitnessTheme.spacing.m,
                end = FitnessTheme.spacing.m,
                top = FitnessTheme.spacing.m,
                bottom = FitnessTheme.spacing.l,
            ),
            verticalArrangement = Arrangement.spacedBy(if (bento) bentoGap else baseGap),
        ) {
            if (bento) {
                bentoStatsItems(state, viewModel, accent = pageAccent)
            } else {
                statsItems(state, viewModel, accent = pageAccent)
            }
        }
    }
}

/**
 * 统计内容片段：数据页（统计在上、历史在下）与本页复用同一段 LazyListScope。
 * 公开给 :app —— feature 间禁止依赖，但 :app 是组装层，可以引用任意 feature 的公开 API。
 */
fun LazyListScope.statsItems(
    state: StatsUiState,
    viewModel: StatsViewModel,
    /** 页面强调色：传给图表，让统计页的图与其它页面区分开 */
    accent: Color? = null,
) {
    // 整段统计收进一个 item + Column：横向边距由宿主页的 contentPadding 提供
    // （独立统计页与数据页各自声明，避免这里叠加出双份边距）。
    // 各分区的子列表（最常练/PR）都很短，失去懒加载无感知。
    item(key = "stats") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(FitnessTheme.spacing.m)) {
                    Row(Modifier.fillMaxWidth()) {
                        OverviewMetric(
                            stringResource(StatsR.string.stats_week_sessions),
                            stringResource(
                                StatsR.string.stats_week_sessions_value,
                                state.weekSessions,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetric(
                            stringResource(StatsR.string.stats_streak),
                            stringResource(StatsR.string.stats_streak_value, state.streakWeeks),
                            modifier = Modifier.weight(1f),
                        )
                        OverviewMetric(
                            stringResource(StatsR.string.stats_total_sessions),
                            stringResource(
                                StatsR.string.stats_total_sessions_value,
                                state.totalSessions,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Text(
                        stringResource(StatsR.string.stats_week_volume) +
                            " " + formatVolume(state.weekVolumeKg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.weekSessions == 0) {
                        Text(
                            stringResource(StatsR.string.stats_empty_week),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 窄屏放不下时横向滚动，不换行不溢出
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
            ) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { viewModel.setRange(range) },
                        label = {
                            Text(
                                stringResource(
                                    when (range) {
                                        StatsRange.D30 -> StatsR.string.stats_range_30d
                                        StatsRange.D90 -> StatsR.string.stats_range_90d
                                        StatsRange.D180 -> StatsR.string.stats_range_180d
                                        StatsRange.ALL -> StatsR.string.stats_range_all
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            SectionTitle(stringResource(StatsR.string.stats_volume_trend))
            TrendLineChart(
                points = state.volumePoints,
                emptyText = stringResource(StatsR.string.stats_chart_empty),
                valueFormatter = { formatWeightCompact(it.toDouble()) },
                contentDescription = "容量趋势图，共 ${state.volumePoints.size} 个数据点",
                accent = accent,
            )

            // 标题占满剩余宽度并允许换行，切换胶囊不挤压标题（窄屏自适应）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SectionTitle(
                        stringResource(StatsR.string.stats_muscle_distribution),
                        subtitle = stringResource(StatsR.string.stats_muscle_distribution_desc),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                    FilterChip(
                        selected = !state.muscleBySets,
                        onClick = { if (state.muscleBySets) viewModel.toggleMuscleMetric() },
                        label = { Text(stringResource(StatsR.string.stats_by_volume)) },
                    )
                    FilterChip(
                        selected = state.muscleBySets,
                        onClick = { if (!state.muscleBySets) viewModel.toggleMuscleMetric() },
                        label = { Text(stringResource(StatsR.string.stats_by_sets)) },
                    )
                }
            }

            // 肌群名走 labelRes，保证与动作库里的用词完全一致。
            // 注意：valueFormatter 是普通 lambda，里面**不能**调用 stringResource
            // （@Composable 只能在 Composable 上下文执行），所以这里用纯字符串拼接。
            val labels = state.muscleEntries.map { (muscle, _) ->
                stringResource(muscle.labelRes())
            }
            val bySets = state.muscleBySets
            DistributionBarChart(
                entries = labels.zip(state.muscleEntries.map { it.second }),
                emptyText = stringResource(StatsR.string.stats_chart_empty),
                valueFormatter = { value ->
                    if (bySets) "${value.toInt()} 组" else formatWeightCompact(value.toDouble())
                },
                accent = accent,
                barColors = state.muscleEntries.map { muscleColor(it.first) },
            )

            SectionTitle(stringResource(StatsR.string.stats_calendar))
            CalendarHeatmap(
                counts = state.heatmap,
                todayEpochDay = state.todayEpochDay,
                accent = accent,
            )

            SectionTitle(stringResource(StatsR.string.stats_top_exercises))
            if (state.topExercises.isEmpty()) {
                Text(
                    stringResource(StatsR.string.stats_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.topExercises.forEachIndexed { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = FitnessTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FitnessTheme.spacing.s),
                        )
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(StatsR.string.stats_session_count, item.sessionCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
                        Text(
                            formatWeightCompact(item.volumeKg),
                            style = numericTextStyle(NumericSize.Small),
                        )
                    }
                }
            }

            SectionTitle(stringResource(StatsR.string.stats_pr_list))
            if (state.records.isEmpty()) {
                Text(
                    stringResource(StatsR.string.stats_pr_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.records.forEach { RecordRow(it) }
            }

            Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
        }
    }
}

/** 概览 2×2 色块网格：便当盒最上面那两排"实心小块" */
@Composable
private fun BentoOverviewGrid(state: StatsUiState, modifier: Modifier = Modifier) {
    val accents = FitnessTheme.layout.accents
    val fallback = MaterialTheme.colorScheme.primary
    // 提前取出普通值：局部函数里直接读 MaterialTheme 会要求它本身是 @Composable
    fun accent(i: Int) = accents.getOrElse(i) { fallback }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
    ) {
        BentoRow(Modifier.height(104.dp)) {
            BentoStatTile(
                label = stringResource(StatsR.string.stats_week_sessions),
                value = stringResource(StatsR.string.stats_week_sessions_value, state.weekSessions),
                container = accent(2),
                modifier = Modifier.weight(1f),
            )
            BentoStatTile(
                label = stringResource(StatsR.string.stats_streak),
                value = stringResource(StatsR.string.stats_streak_value, state.streakWeeks),
                container = accent(0),
                modifier = Modifier.weight(1f),
            )
        }
        BentoRow(Modifier.height(104.dp)) {
            BentoStatTile(
                label = stringResource(StatsR.string.stats_total_sessions),
                value = stringResource(
                    StatsR.string.stats_total_sessions_value,
                    state.totalSessions,
                ),
                container = accent(1),
                modifier = Modifier.weight(1f),
            )
            BentoStatTile(
                label = stringResource(StatsR.string.stats_week_volume),
                value = formatWeightCompact(state.weekVolumeKg),
                unit = "kg",
                container = accent(3),
                contentColor = Color(0xFF3A2A00),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.weekSessions == 0) {
            Text(
                stringResource(StatsR.string.stats_empty_week),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 时间范围切换胶囊：便当盒里保持裸组件，避免再多一层容器噪音 */
@Composable
private fun BentoRangeChips(state: StatsUiState, viewModel: StatsViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
    ) {
        StatsRange.entries.forEach { range ->
            FilterChip(
                selected = state.range == range,
                onClick = { viewModel.setRange(range) },
                label = {
                    Text(
                        stringResource(
                            when (range) {
                                StatsRange.D30 -> StatsR.string.stats_range_30d
                                StatsRange.D90 -> StatsR.string.stats_range_90d
                                StatsRange.D180 -> StatsR.string.stats_range_180d
                                StatsRange.ALL -> StatsR.string.stats_range_all
                            },
                        ),
                    )
                },
            )
        }
    }
}

/**
 * 便当盒版统计内容（BENTO_SOFT 主题专用）。
 *
 * 与经典版的信息完全一致，差别在排版：概览变成 2×2 实心色块，
 * 每个图表各自住进一张悬浮卡，并按格错峰入场。
 */
fun LazyListScope.bentoStatsItems(
    state: StatsUiState,
    viewModel: StatsViewModel,
    /** 页面强调色：传给图表 */
    accent: Color? = null,
) {
    item(key = "bento-stats") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
        ) {
            Box(Modifier.bentoAppear(index = 0)) { BentoOverviewGrid(state) }

            Box(Modifier.bentoAppear(index = 1)) { BentoRangeChips(state, viewModel) }

            BentoSectionCard(
                title = stringResource(StatsR.string.stats_volume_trend),
                modifier = Modifier.bentoAppear(index = 2),
            ) {
                TrendLineChart(
                    points = state.volumePoints,
                    height = 180.dp,
                    emptyText = stringResource(StatsR.string.stats_chart_empty),
                    valueFormatter = { formatWeightCompact(it.toDouble()) },
                    contentDescription = "容量趋势图，共 ${state.volumePoints.size} 个数据点",
                    accent = accent,
                )
            }

            BentoSectionCard(
                title = stringResource(StatsR.string.stats_muscle_distribution),
                modifier = Modifier.bentoAppear(index = 3),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                        FilterChip(
                            selected = !state.muscleBySets,
                            onClick = { if (state.muscleBySets) viewModel.toggleMuscleMetric() },
                            label = { Text(stringResource(StatsR.string.stats_by_volume)) },
                        )
                        FilterChip(
                            selected = state.muscleBySets,
                            onClick = { if (!state.muscleBySets) viewModel.toggleMuscleMetric() },
                            label = { Text(stringResource(StatsR.string.stats_by_sets)) },
                        )
                    }
                },
            ) {
                // 肌群名走 labelRes，保证与动作库用词一致；valueFormatter 内不能调 stringResource
                val labels = state.muscleEntries.map { (muscle, _) ->
                    stringResource(muscle.labelRes())
                }
                val bySets = state.muscleBySets
                DistributionBarChart(
                    entries = labels.zip(state.muscleEntries.map { it.second }),
                    emptyText = stringResource(StatsR.string.stats_chart_empty),
                    valueFormatter = { value ->
                        if (bySets) "${value.toInt()} 组" else formatWeightCompact(value.toDouble())
                    },
                    accent = accent,
                    barColors = state.muscleEntries.map { muscleColor(it.first) },
                )
            }

            BentoSectionCard(
                title = stringResource(StatsR.string.stats_calendar),
                modifier = Modifier.bentoAppear(index = 4),
            ) {
                CalendarHeatmap(
                    counts = state.heatmap,
                    todayEpochDay = state.todayEpochDay,
                    accent = accent,
                )
            }

            BentoSectionCard(
                title = stringResource(StatsR.string.stats_top_exercises),
                modifier = Modifier.bentoAppear(index = 5),
            ) {
                if (state.topExercises.isEmpty()) {
                    Text(
                        stringResource(StatsR.string.stats_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.topExercises.forEachIndexed { index, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = FitnessTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = FitnessTheme.spacing.s),
                            )
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(StatsR.string.stats_session_count, item.sessionCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
                            Text(
                                formatWeightCompact(item.volumeKg),
                                style = numericTextStyle(NumericSize.Small),
                            )
                        }
                    }
                }
            }

            BentoSectionCard(
                title = stringResource(StatsR.string.stats_pr_list),
                modifier = Modifier.bentoAppear(index = 6),
            ) {
                if (state.records.isEmpty()) {
                    Text(
                        stringResource(StatsR.string.stats_pr_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.records.forEach { RecordRow(it) }
                }
            }

            Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = numericTextStyle(NumericSize.Medium))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = FitnessTheme.spacing.s, bottom = FitnessTheme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordRow(record: PersonalRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = FitnessTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrBadge(compact = true)
        Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
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
            when (record.prType) {
                PrType.MAX_REPS -> "${record.value.toInt()} 次"
                else -> formatWeightCompact(record.value)
            },
            style = numericTextStyle(NumericSize.Small),
        )
    }
}


