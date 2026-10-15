package com.fitnesspro.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.common.navigation.NavResultBus
import com.fitnesspro.core.common.navigation.NavResults
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.exerciseIdentityColor
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseFilter
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.mediaui.ExerciseMedia
import com.fitnesspro.core.mediaui.MEDIA_SIZE_SMALL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// 显式导入本模块自己的 R：否则会误用 core:designsystem 的 R，
// 导致本模块的字符串资源全部"找不到"。
import com.fitnesspro.feature.exercises.R
import com.fitnesspro.core.designsystem.R as DesignSystemR

data class ExercisePickerUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: ExerciseFilter = ExerciseFilter(),
    val exercises: List<Exercise> = emptyList(),
)

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val navResultBus: NavResultBus,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ExerciseFilter())

    /**
     * 必须用 flatMapLatest：filter 变化时取消旧查询的订阅。
     * 否则多次切换筛选后，旧查询的结果流仍活着，列表会出现重复项与旧结果回闪。
     */
    val uiState: StateFlow<ExercisePickerUiState> = filterFlow
        .flatMapLatest { filter ->
            exerciseRepository.observeFiltered(filter).map { list ->
                ExercisePickerUiState(
                    isLoading = false,
                    query = filter.query,
                    filter = filter,
                    exercises = list,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExercisePickerUiState(),
        )

    fun onQueryChange(query: String) {
        filterFlow.value = filterFlow.value.copy(query = query)
    }

    /** 选择大肌群：切换时清掉不属于它的细分筛选；再点同一次 = 取消 */
    fun onMajorGroupChange(major: MajorGroup?) {
        val current = filterFlow.value
        filterFlow.value = if (current.majorGroup == major) {
            current.copy(majorGroup = null, subGroup = null)
        } else {
            current.copy(majorGroup = major, subGroup = null)
        }
    }

    fun onSubGroupChange(sub: String?) {
        filterFlow.value = filterFlow.value.copy(subGroup = sub)
    }

    fun onEquipmentToggle(equipment: Equipment) {
        filterFlow.value = filterFlow.value.copy(
            equipment = if (filterFlow.value.equipment == equipment) null else equipment,
        )
    }

    fun clearFilters() {
        filterFlow.value = filterFlow.value.copy(
            muscle = null, majorGroup = null, subGroup = null, equipment = null,
        )
    }

    /** 选中即代表"用过一次"，自增频次用于「最近使用」排序 */
    fun markUsed(id: Long) {
        viewModelScope.launch { exerciseRepository.markUsed(id) }
    }

    /**
     * 选中动作：记录使用频次，并把结果投递给上一个页面。
     *
     * 结果走 [NavResultBus] 而不是 `NavBackStackEntry.savedStateHandle` ——
     * 后者在 Hilt 下与 ViewModel 拿到的 handle 不是同一实例，实测会导致
     * "选了动作但上一页收不到"且完全无提示。详见 NavResultBus 的注释。
     *
     * 注意：本方法在**选择器自己的 ViewModel** 里投递，因此不需要宿主页面
     * 参与传值，宿主只负责 pop。
     */
    fun onExerciseSelected(exercise: Exercise) {
        markUsed(exercise.id)
        navResultBus.publish(NavResults.PICKED_EXERCISE_ID, exercise.id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    onExerciseSelected: (Exercise) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.exercises_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(DesignSystemR.string.common_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FitnessTheme.spacing.m),
                placeholder = { Text(stringResource(R.string.exercises_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            FilterRow(
                majorGroup = state.filter.majorGroup,
                subGroup = state.filter.subGroup,
                equipment = state.filter.equipment,
                onMajorGroupChange = viewModel::onMajorGroupChange,
                onSubGroupChange = viewModel::onSubGroupChange,
                onEquipmentToggle = viewModel::onEquipmentToggle,
                onClear = viewModel::clearFilters,
            )

            when {
                state.isLoading -> LoadingState()
                state.exercises.isEmpty() -> EmptyState(
                    title = stringResource(R.string.exercises_empty),
                    description = state.query.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.exercises_empty_desc, it) },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = {
                                // 先投递结果（唯一真理），再由宿主决定怎么 pop
                                viewModel.onExerciseSelected(exercise)
                                onExerciseSelected(exercise)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    majorGroup: MajorGroup?,
    subGroup: String?,
    equipment: Equipment?,
    onMajorGroupChange: (MajorGroup?) -> Unit,
    onSubGroupChange: (String?) -> Unit,
    onEquipmentToggle: (Equipment) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(vertical = FitnessTheme.spacing.s)) {
        // 大肌群 / 类别（第一层）
        LazyRow(
            contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            items(MajorGroup.entries.toList(), key = { it.name }) { item ->
                FilterChip(
                    selected = majorGroup == item,
                    onClick = { onMajorGroupChange(item) },
                    label = { Text(item.displayName) },
                )
            }
        }
        // 细分肌群（第二层）：仅选中大肌群后出现
        val subOptions = majorGroup?.let { ExerciseTaxonomy.subGroupsOf(it) }.orEmpty()
        if (subOptions.isNotEmpty()) {
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            LazyRow(
                contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
            ) {
                items(subOptions, key = { it }) { sub ->
                    FilterChip(
                        selected = subGroup == sub,
                        onClick = { onSubGroupChange(if (subGroup == sub) null else sub) },
                        label = { Text(sub) },
                    )
                }
            }
        }
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            item(key = "clear") {
                AssistChip(
                    onClick = onClear,
                    label = { Text(stringResource(R.string.exercises_filter_clear)) },
                )
            }
            items(Equipment.entries.toList(), key = { it.name }) { item ->
                FilterChip(
                    selected = equipment == item,
                    onClick = { onEquipmentToggle(item) },
                    label = { Text(stringResource(item.labelRes())) },
                )
            }
        }
    }
}

@Composable
internal fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    /** 左缘身份色条：自定义主题色或肌群色，扫一眼就能认出部位 */
    stripeColor: Color? = null,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        // 列表里也能看到演示图：动作库/选择器共用这一个行组件
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stripeColor != null) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .size(width = 4.dp, height = MEDIA_SIZE_SMALL)
                            .background(stripeColor, RoundedCornerShape(2.dp)),
                    )
                }
                ExerciseMedia(media = exercise.media, size = MEDIA_SIZE_SMALL)
            }
        },
        headlineContent = {
            Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                LabelChip(exercise.majorGroup.displayName)
                LabelChip(stringResource(exercise.primaryMuscle.labelRes()))
                LabelChip(stringResource(exercise.equipment.labelRes()))
                if (exercise.isUnilateral) {
                    LabelChip(stringResource(R.string.exercises_field_unilateral))
                }
            }
        },
        trailingContent = trailing,
    )
}
