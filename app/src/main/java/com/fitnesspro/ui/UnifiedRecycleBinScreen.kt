package com.fitnesspro.ui

import com.fitnesspro.R

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.feature.history.R as HistoryR
import com.fitnesspro.feature.plans.R as PlansR

/** 统一回收站：计划/动作/历史共用，入口处 type 决定默认分段；30 天过期由清理 Worker 负责 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRecycleBinScreen(
    type: TrashType,
    onNavigateBack: () -> Unit,
    viewModel: UnifiedTrashViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) { viewModel.setType(type) }

    val items by viewModel.items.collectAsStateWithLifecycle()
    val currentType by viewModel.type.collectAsStateWithLifecycle()
    val messageRes by viewModel.messageRes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingPurge by remember { mutableStateOf<TrashItemUi?>(null) }
    val context = LocalContext.current

    LaunchedEffect(messageRes) {
        messageRes?.let {
            snackbarHostState.showSnackbar(context.getString(it), actionLabel = null)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (currentType) {
                                TrashType.PLAN -> R.string.trash_title_plan
                                TrashType.EXERCISE -> R.string.trash_title_exercise
                                TrashType.HISTORY -> R.string.trash_title_history
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                com.fitnesspro.core.designsystem.R.string.common_back,
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TrashType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = currentType == t,
                        onClick = { viewModel.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TrashType.entries.size,
                        ),
                    ) {
                        Text(
                            stringResource(
                                when (t) {
                                    TrashType.PLAN -> R.string.segment_plans
                                    TrashType.EXERCISE -> R.string.segment_exercises
                                    TrashType.HISTORY -> R.string.segment_history
                                },
                            ),
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.trash_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(FitnessTheme.spacing.m),
                ) {
                    items(items.size, key = { items[it].type.name + items[it].id }) { index ->
                        val item = items[index]
                        TrashCard(
                            item = item,
                            onRestore = { viewModel.restore(item) },
                            onRequestPurge = { pendingPurge = item },
                        )
                    }
                }
            }
        }
    }

    pendingPurge?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.trash_purge_confirm_title, item.title),
            message = stringResource(R.string.trash_purge_confirm_message),
            confirmLabel = stringResource(R.string.trash_delete_forever),
            destructive = true,
            onConfirm = {
                val target = item
                pendingPurge = null
                viewModel.deleteForever(target)
            },
            onDismiss = { pendingPurge = null },
        )
    }
}
@Composable
private fun TrashCard(
    item: TrashItemUi,
    onRestore: () -> Unit,
    onRequestPurge: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FitnessTheme.spacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(FitnessTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (item.type) {
                        TrashType.PLAN -> stringResource(
                            PlansR.string.plans_card_meta,
                            item.exerciseCount,
                            item.totalSets,
                        )
                        TrashType.EXERCISE -> stringResource(
                            if (item.isCustom) R.string.trash_custom else R.string.trash_builtin,
                        )
                        TrashType.HISTORY -> stringResource(
                            HistoryR.string.session_detail_summary,
                            formatVolume(item.totalVolumeKg),
                            item.durationSeconds / 60,
                            0,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.trash_days_left, item.daysLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.daysLeft <= 3) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore),
                )
            }
            IconButton(onClick = onRequestPurge) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.trash_delete_forever),
                )
            }
        }
    }
}