package com.fitnesspro.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.FitnessSearchField
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.SwipeRevealBox
import com.fitnesspro.core.designsystem.component.deleteRevealAction
import com.fitnesspro.core.designsystem.component.favoriteRevealAction
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.exerciseIdentityColor
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.feature.exercises.R as ExercisesR

/** 动作库的分组 Tab。用枚举而不是字符串：让"新增一个分组"变成编译器能提示的改动。 */
enum class ExerciseTab { ALL, FAVORITES, RECENT, CUSTOM }

/**
 * 动作库内容：训练库页（计划/动作分段）复用。
 * 顶栏与 FAB 由宿主（:app 组装层）提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryContent(
    onExerciseClick: (Long) -> Unit,
    onCreateExercise: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }

    // 选中大肌群后展示其细分肌群二级筛选
    val subOptions = state.majorGroup
        ?.let { ExerciseTaxonomy.subGroupsOf(it) }
        .orEmpty()

    // 文案必须在组合作用域里取好：LazyRow 的 content lambda 不是 @Composable
    val tabEntries = listOf(
        ExerciseTab.ALL to stringResource(ExercisesR.string.exercises_tab_all),
        ExerciseTab.FAVORITES to stringResource(ExercisesR.string.exercises_tab_favorites),
        ExerciseTab.RECENT to stringResource(ExercisesR.string.exercises_tab_recent),
        ExerciseTab.CUSTOM to stringResource(ExercisesR.string.exercises_tab_custom),
    )

    Column(
        Modifier.fillMaxSize(),
    ) {
            // 原来用 TabRow：四个 Tab 平分宽度、彼此紧贴，没有间距也没有边界感。
            // 改成有间距的胶囊行 —— 与下面的肌群筛选是同一套视觉语言，选中态也更明确。
            LazyRow(
                contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
            ) {
                items(tabEntries, key = { it.first.name }) { (tab, label) ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(label) },
                    )
                }
            }

            FitnessSearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                hint = stringResource(ExercisesR.string.exercises_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.s),
            )

            // 大肌群 / 类别筛选（v2 分类体系第一层）
            LazyRow(
                contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                    FitnessTheme.spacing.xs,
                ),
            ) {
                items(MajorGroup.entries.toList(), key = { it.name }) { major ->
                    FilterChip(
                        selected = state.majorGroup == major,
                        onClick = { viewModel.onMajorGroupChange(major) },
                        label = { Text(major.displayName) },
                    )
                }
            }

            // 细分肌群筛选（第二层）：仅在选中大肌群后出现
            if (subOptions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FitnessTheme.spacing.m),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                        FitnessTheme.spacing.xs,
                    ),
                    modifier = Modifier.padding(top = FitnessTheme.spacing.xs),
                ) {
                    items(subOptions, key = { it }) { sub ->
                        FilterChip(
                            selected = state.subGroup == sub,
                            onClick = {
                                viewModel.onSubGroupChange(if (state.subGroup == sub) null else sub)
                            },
                            label = { Text(sub) },
                        )
                    }
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.exercises.isEmpty() -> EmptyState(
                    title = stringResource(ExercisesR.string.exercises_empty),
                    description = state.query.takeIf { it.isNotBlank() }
                        ?.let { stringResource(ExercisesR.string.exercises_empty_desc, it) },
                    actionLabel = state.query.takeIf { it.isNotBlank() }
                        ?.let { stringResource(ExercisesR.string.exercises_create_new, it) },
                    onAction = state.query.takeIf { it.isNotBlank() }?.let { { onCreateExercise() } },
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    // 行变成独立卡片后必须留间距，否则卡片会贴成一整块
                    verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
                    // 左右留白与搜索框/筛选行对齐；悬浮菜单压在内容之上（其它主题为 0）
                    contentPadding = PaddingValues(
                        start = FitnessTheme.spacing.m,
                        end = FitnessTheme.spacing.m,
                        top = FitnessTheme.layout.cardGap,
                        bottom = FitnessTheme.spacing.l + FitnessTheme.layout.bottomBarInset,
                    ),
                ) {
                    items(state.exercises, key = { it.id }) { exercise ->
                        // 收藏/删除改到左滑里（与计划列表一致），行内只留内容
                        SwipeRevealBox(
                            modifier = Modifier.fillMaxWidth(),
                            actions = listOf(
                                favoriteRevealAction(
                                    isFavorite = exercise.isFavorite,
                                    onToggle = { viewModel.toggleFavorite(exercise) },
                                ),
                                // 删除 = 移入回收站（30 天可恢复），与计划/历史语义一致，
                                // 且必须再弹一次确认（破坏性操作不做"一滑即删"）
                                deleteRevealAction(onDelete = { pendingDelete = exercise }),
                            ),
                        ) {
                            SoftCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                ExerciseRow(
                                    exercise = exercise,
                                    onClick = { onExerciseClick(exercise.id) },
                                    stripeColor = exerciseIdentityColor(
                                        exercise.colorKey,
                                        exercise.primaryMuscle,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        pendingDelete?.let { exercise ->
            ConfirmDialog(
                title = stringResource(ExercisesR.string.exercises_delete_confirm_title, exercise.name),
                message = stringResource(ExercisesR.string.exercises_delete_confirm_message),
                confirmLabel = stringResource(R.string.common_delete),
                destructive = true,
                onConfirm = {
                    val id = exercise.id
                    pendingDelete = null
                    viewModel.moveToTrash(id)
                },
                onDismiss = { pendingDelete = null },
            )
        }
}

/** 新建自定义动作的对话框由 :app 层用独立页面承载；此处保留入口签名以避免 feature 间依赖。 */
@Composable
internal fun CreateExerciseHintDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ExercisesR.string.exercises_create_title)) },
        text = { Text(stringResource(ExercisesR.string.exercises_min_select_hint)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_confirm)) }
        },
    )
}

/** 供详情页复用的展示模型转换 */
internal fun Exercise.muscleChips(): List<String> = buildList {
    add(primaryMuscle.name)
    addAll(secondaryMuscles.map { it.name })
}
