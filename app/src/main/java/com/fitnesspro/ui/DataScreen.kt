package com.fitnesspro.ui

import com.fitnesspro.R

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.LocalFitnessAccent
import com.fitnesspro.core.model.SessionSummary
import com.fitnesspro.feature.history.HistorySnackbarEffects
import com.fitnesspro.feature.history.HistoryViewModel
import com.fitnesspro.feature.history.historyItems
import com.fitnesspro.feature.stats.StatsViewModel
import com.fitnesspro.feature.stats.bentoStatsItems
import com.fitnesspro.feature.stats.statsItems

/** 数据页顶部分段：统计 / 历史 两个同级分支（与训练库的 计划/动作 同构） */
enum class DataSegment(val routeValue: String) {
    STATS("stats"),
    HISTORY("history"),
}

/**
 * 数据页：统计与历史为两个同级分支页面，由顶部分段菜单切换（同训练库的计划/动作）。
 *
 * 两个分支的内容组件来自不同 feature 的公开 LazyListScope 扩展；
 * 横向边距在本页 contentPadding 统一提供，两段内容的左右边距保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    segment: DataSegment,
    onSegmentChange: (DataSegment) -> Unit,
    onSessionClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val statsState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }

    // LazyColumn 的 content lambda 不是 @Composable，组合令牌要先取出来
    val bento = FitnessTheme.layout.bentoGrid
    val statsAccent = FitnessTheme.layout.accents.getOrNull(2)
    val historyAccent = FitnessTheme.layout.accents.getOrNull(3)

    HistorySnackbarEffects(historyState, historyViewModel, snackbarHostState)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.data_title)) },
                actions = {
                    TrashAction(onOpenTrash)
                    SettingsAction(onOpenSettings)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (statsState.isLoading) {
            LoadingState(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        // 分段跟随强调色：统计=蓝、历史=黄（与列表内容一致）
        val segmentAccent = FitnessTheme.layout.accents.getOrElse(
            if (segment == DataSegment.STATS) 2 else 3,
        ) { MaterialTheme.colorScheme.primary }

        CompositionLocalProvider(LocalFitnessAccent provides segmentAccent) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val itemColors = SegmentedButtonDefaults.colors(
                activeContainerColor = segmentAccent.copy(alpha = 0.16f),
                activeContentColor = segmentAccent,
            )
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.s),
            ) {
                SegmentedButton(
                    selected = segment == DataSegment.STATS,
                    onClick = { onSegmentChange(DataSegment.STATS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = itemColors,
                ) { Text(stringResource(R.string.segment_stats)) }
                SegmentedButton(
                    selected = segment == DataSegment.HISTORY,
                    onClick = { onSegmentChange(DataSegment.HISTORY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = itemColors,
                ) { Text(stringResource(R.string.segment_history)) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 卡片之间的纵向间距：历史记录此前贴在一起（合并分段时把
                // HistoryScreen 自带的 spacedBy 弄丢了），这里补齐
                verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
                // 顶部与顶栏/分段条保持距离；横向边距在此统一提供，statsItems/historyItems 不再自带
                contentPadding = PaddingValues(
                    start = FitnessTheme.spacing.m,
                    end = FitnessTheme.spacing.m,
                    top = FitnessTheme.spacing.s,
                    // 悬浮菜单压在内容之上，底部要额外留出菜单高度（其它主题为 0）
                    bottom = FitnessTheme.spacing.l + FitnessTheme.layout.bottomBarInset,
                ),
            ) {
                when (segment) {
                    // 同页两段各用各的强调色：统计=蓝、历史=黄，风格统一但一眼可辨
                    DataSegment.STATS -> if (bento) {
                        bentoStatsItems(
                            state = statsState,
                            viewModel = statsViewModel,
                            accent = statsAccent,
                        )
                    } else {
                        statsItems(
                            state = statsState,
                            viewModel = statsViewModel,
                            accent = statsAccent,
                        )
                    }
                    DataSegment.HISTORY -> historyItems(
                        state = historyState,
                        viewModel = historyViewModel,
                        onSessionClick = onSessionClick,
                        onRequestDelete = { pendingDelete = it },
                        accent = historyAccent,
                    )
                }
            }
        }
        }
    }

    pendingDelete?.let { session ->
        ConfirmDialog(
            title = stringResource(
                com.fitnesspro.feature.history.R.string.history_delete_confirm_title,
            ),
            message = stringResource(
                com.fitnesspro.feature.history.R.string.history_delete_confirm_message,
            ),
            confirmLabel = stringResource(
                com.fitnesspro.core.designsystem.R.string.common_delete,
            ),
            destructive = true,
            onConfirm = {
                val id = session.id
                pendingDelete = null
                historyViewModel.delete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
