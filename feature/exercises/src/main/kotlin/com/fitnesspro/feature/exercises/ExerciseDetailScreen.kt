package com.fitnesspro.feature.exercises

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.ErrorState
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.TrendLineChart
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.domain.repository.PersonalRecordRepository
import com.fitnesspro.core.domain.repository.StatsRepository
import com.fitnesspro.core.media.MediaAccess
import com.fitnesspro.core.mediaui.ExerciseMedia
import com.fitnesspro.core.mediaui.MEDIA_SIZE_LARGE
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseTrendPoint
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.PrType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

import com.fitnesspro.feature.exercises.R as ExercisesR

enum class TrendMetric { WEIGHT, ESTIMATED_1RM, VOLUME }

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
    val records: List<PersonalRecord> = emptyList(),
    val trend: List<ExerciseTrendPoint> = emptyList(),
    val metric: TrendMetric = TrendMetric.WEIGHT,
    val thumbnailDir: File? = null,
    val notFound: Boolean = false,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val prRepository: PersonalRecordRepository,
    private val statsRepository: StatsRepository,
    mediaAccess: MediaAccess,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L
    private val metric = MutableStateFlow(TrendMetric.WEIGHT)

    /** 趋势数据只加载一次（不是 Flow）：它需要聚合整段历史，无法用轻量查询订阅 */
    private val trend = MutableStateFlow<List<ExerciseTrendPoint>>(emptyList())
    private val loaded = MutableStateFlow(false)

    private val thumbnailDir = mediaAccess.thumbnailDir()

    init {
        viewModelScope.launch {
            trend.value = runCatching { statsRepository.exerciseTrend(exerciseId) }
                .getOrDefault(emptyList())
            loaded.value = true
        }
    }

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        prRepository.observeForExercise(exerciseId),
        trend,
        metric,
        loaded,
    ) { exercise, records, trendPoints, metricValue, isLoaded ->
        ExerciseDetailUiState(
            isLoading = !isLoaded,
            exercise = exercise,
            records = records,
            trend = trendPoints,
            metric = metricValue,
            thumbnailDir = thumbnailDir,
            notFound = isLoaded && exercise == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun setMetric(value: TrendMetric) {
        metric.value = value
    }

    fun toggleFavorite() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            exerciseRepository.setFavorite(exercise.id, !exercise.isFavorite)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onNavigateBack: () -> Unit,
    /** 打开全屏播放页（视频/动图/图片都走这里） */
    onPlayMedia: (MediaRef) -> Unit,
    /** 演示文件丢失时的"重新关联"入口：参数是动作 id，宿主跳到编辑页重新绑定 */
    onRelinkMedia: (Long) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        state.exercise?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    state.exercise?.let { exercise ->
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (exercise.isFavorite) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Filled.StarBorder
                                },
                                contentDescription = stringResource(
                                    if (exercise.isFavorite) {
                                        ExercisesR.string.exercises_detail_favorite_remove
                                    } else {
                                        ExercisesR.string.exercises_detail_favorite_add
                                    },
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val exercise = state.exercise
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            exercise == null -> ErrorState(
                message = stringResource(R.string.common_error_default),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 统一组件：文件丢失时给出"重新关联"入口，而不是空白
                        ExerciseMedia(
                            media = exercise.media,
                            size = MEDIA_SIZE_LARGE,
                            onRelink = { onRelinkMedia(exercise.id) },
                            onClick = { exercise.media?.let(onPlayMedia) },
                        )
                        Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.s))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                                LabelChip(exercise.majorGroup.displayName)
                                exercise.subGroup?.let { LabelChip(it) }
                                LabelChip(stringResource(exercise.primaryMuscle.labelRes()))
                                LabelChip(stringResource(exercise.equipment.labelRes()))
                            }
                            Spacer(Modifier.height(FitnessTheme.spacing.xs))
                            Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                                LabelChip(stringResource(exercise.type.labelRes()))
                                if (exercise.isUnilateral) {
                                    LabelChip(stringResource(ExercisesR.string.exercises_field_unilateral))
                                }
                            }
                        }
                        if (exercise.media != null) {
                            IconButton(onClick = { exercise.media?.let(onPlayMedia) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_video),
                                )
                            }
                        }
                    }
                }

                exercise.instructions?.takeIf { it.isNotBlank() }?.let { text ->
                    item(key = "instructions") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(
                                text,
                                modifier = Modifier.padding(FitnessTheme.spacing.m),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                item(key = "pr-title") {
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Text(
                        stringResource(ExercisesR.string.exercises_detail_pr),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (state.records.isEmpty()) {
                    item(key = "pr-empty") {
                        Text(
                            stringResource(ExercisesR.string.exercises_detail_no_pr),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.records.size, key = { "pr-$it" }) { index ->
                        PrCard(state.records[index])
                    }
                }

                item(key = "trend-title") {
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "趋势",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                            MetricChip("重量", state.metric == TrendMetric.WEIGHT) {
                                viewModel.setMetric(TrendMetric.WEIGHT)
                            }
                            MetricChip("估算 1RM", state.metric == TrendMetric.ESTIMATED_1RM) {
                                viewModel.setMetric(TrendMetric.ESTIMATED_1RM)
                            }
                        }
                    }
                }

                item(key = "trend-chart") {
                    val series = when (state.metric) {
                        TrendMetric.WEIGHT -> state.trend.mapNotNull { it.bestWeightKg?.toFloat() }
                        TrendMetric.ESTIMATED_1RM -> state.trend.mapNotNull { it.estimatedOneRmKg?.toFloat() }
                        TrendMetric.VOLUME -> state.trend.map { it.volumeKg.toFloat() }
                    }
                    TrendLineChart(
                        points = series,
                        emptyText = stringResource(R.string.chart_empty),
                        valueFormatter = { formatWeightCompact(it.toDouble()) },
                        contentDescription = "趋势图，共 ${series.size} 个数据点",
                    )
                }

                item(key = "history-title") {
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Text(
                        stringResource(ExercisesR.string.exercises_detail_history),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (state.trend.isEmpty()) {
                    item(key = "history-empty") {
                        Text(
                            stringResource(ExercisesR.string.exercises_detail_no_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.trend.size, key = { "trend-$it" }) { index ->
                        // 倒序展示：最近的训练在最上面
                        val point = state.trend[state.trend.lastIndex - index]
                        TrendRow(point)
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun PrCard(record: PersonalRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(FitnessTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrBadge(compact = true)
            Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(record.prType.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                record.reps?.let {
                    Text(
                        "$it 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                when (record.prType) {
                    PrType.MAX_REPS -> "${record.value.toInt()} 次"
                    else -> formatWeightCompact(record.value)
                },
                style = numericTextStyle(NumericSize.Medium),
            )
        }
    }
}

@Composable
private fun TrendRow(point: ExerciseTrendPoint) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = FitnessTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            epochDayLabel(point.epochDay),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            buildString {
                point.bestWeightKg?.let { append("${formatWeightCompact(it)} kg") }
                point.totalReps.takeIf { it > 0 }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("$it 次")
                }
            }.ifEmpty { "—" },
            style = numericTextStyle(NumericSize.Small),
        )
        Spacer(Modifier.padding(horizontal = FitnessTheme.spacing.s))
        Text(
            formatWeightCompact(point.volumeKg) + " kg",
            style = numericTextStyle(NumericSize.Small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun epochDayLabel(epochDay: Long): String {
    val date = java.time.LocalDate.ofEpochDay(epochDay)
    return "%d/%d".format(date.monthValue, date.dayOfMonth)
}
