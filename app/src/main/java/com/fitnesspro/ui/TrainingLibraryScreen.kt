package com.fitnesspro.ui

import com.fitnesspro.R

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.LocalFitnessAccent
import com.fitnesspro.feature.exercises.ExerciseLibraryContent
import com.fitnesspro.feature.exercises.ExerciseLibraryViewModel
import com.fitnesspro.feature.plans.PlanListContent
import com.fitnesspro.feature.plans.PlanListViewModel

/** 训练库顶部分段 */
enum class LibrarySegment(val routeValue: String) {
    PLANS("plans"),
    EXERCISES("exercises"),
}

/**
 * 训练库：计划列表与动作库合并为一页，由顶部分段菜单切换。
 *
 * 两个分段的内容组件来自不同 feature（:app 是唯一允许同时依赖它们的组装层）；
 * 两个 ViewModel 同页共存，切换分段不销毁状态（搜索词、分组停留位置都保留）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingLibraryScreen(
    segment: LibrarySegment,
    onSegmentChange: (LibrarySegment) -> Unit,
    onPlanClick: (Long) -> Unit,
    onCreatePlan: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    onCreateExercise: () -> Unit,
    /** 「今日训练」直接开练：会话已生成，导航打开训练页 */
    onWorkoutStarted: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: (LibrarySegment) -> Unit,
    /** 从哪里来回哪里：今日页/首页都可进入本页 */
    onBack: () -> Unit,
) {
    val planViewModel: PlanListViewModel = hiltViewModel()
    val exerciseViewModel: ExerciseLibraryViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                com.fitnesspro.core.designsystem.R.string.common_close,
                            ),
                        )
                    }
                },
                actions = {
                    CreateAction(
                        label = stringResource(
                            if (segment == LibrarySegment.PLANS) {
                                R.string.library_new_plan
                            } else {
                                R.string.library_new_exercise
                            },
                        ),
                        onClick = if (segment == LibrarySegment.PLANS) onCreatePlan else onCreateExercise,
                    )
                    TrashAction { onOpenTrash(segment) }
                    SettingsAction(onOpenSettings)
                },
            )
        },
        // 不再用悬浮 FAB：列表页的 FAB 会盖住最后一条内容、并和底部菜单争夺右下角，
        // 改为顶栏的填色"新建"按钮 —— 列表页主操作放顶栏是更成熟的惯性位置。

        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // 每个分段有自己的强调色：计划=橙、动作=紫（与详情页一致）
        val accent = FitnessTheme.layout.accents.getOrElse(
            if (segment == LibrarySegment.PLANS) 0 else 1,
        ) { MaterialTheme.colorScheme.primary }

        CompositionLocalProvider(LocalFitnessAccent provides accent) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val itemColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = accent.copy(alpha = 0.16f),
                    activeContentColor = accent,
                )
                SegmentedButton(
                    selected = segment == LibrarySegment.PLANS,
                    onClick = { onSegmentChange(LibrarySegment.PLANS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = itemColors,
                ) { Text(stringResource(R.string.segment_plans)) }
                SegmentedButton(
                    selected = segment == LibrarySegment.EXERCISES,
                    onClick = { onSegmentChange(LibrarySegment.EXERCISES) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = itemColors,
                ) { Text(stringResource(R.string.segment_exercises)) }
            }

            when (segment) {
                LibrarySegment.PLANS -> PlanListContent(
                    viewModel = planViewModel,
                    snackbarHostState = snackbarHostState,
                    onPlanClick = onPlanClick,
                    onCreatePlan = onCreatePlan,
                    onWorkoutStarted = onWorkoutStarted,
                    modifier = Modifier.fillMaxSize(),
                )
                LibrarySegment.EXERCISES -> ExerciseLibraryContent(
                    onExerciseClick = onExerciseClick,
                    onCreateExercise = onCreateExercise,
                )
            }
        }
        }
    }
}

/**
 * 顶栏的主操作：新建计划 / 新建动作。
 *
 * 用填色圆形按钮与两侧的普通图标动作区分主次 —— 顶栏放主操作是列表页的成熟做法，
 * 既不遮挡列表内容，也不会和底部悬浮菜单抢右下角。
 */
@Composable
internal fun CreateAction(label: String, onClick: () -> Unit) {
    Box(Modifier.padding(end = FitnessTheme.spacing.xs)) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = label)
        }
    }
}

/** 顶栏右上角的设置入口：所有一级页面共用（设置从底部导航移除后走这里） */
@Composable
internal fun SettingsAction(onOpenSettings: () -> Unit) {
    IconButton(onClick = onOpenSettings) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = stringResource(
                com.fitnesspro.core.designsystem.R.string.common_settings,
            ),
        )
    }
}

/** 顶栏右上角的回收站入口 */
@Composable
internal fun TrashAction(onOpenTrash: () -> Unit) {
    IconButton(onClick = onOpenTrash) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.recycle_bin_cd),
        )
    }
}