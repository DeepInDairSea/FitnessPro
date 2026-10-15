package com.fitnesspro.feature.plans

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.ErrorState
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.calculator.PlanDurationCalculator
import com.fitnesspro.core.domain.calculator.RepsRange
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PlanDetail
import com.fitnesspro.core.model.PlanExerciseItem
import com.fitnesspro.core.data.workout.PrefillMode
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlanDetailUiState(
    val isLoading: Boolean = true,
    val plan: PlanDetail? = null,
    val durationText: String = "",
    val globalRestSeconds: Int = 90,
    val isStarting: Boolean = false,
    /** 开始训练失败，需要提示用户 */
    val startFailed: Boolean = false,
)

@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val settings: SettingsRepository,
    private val sessionManager: WorkoutSessionManager,
) : ViewModel() {

    private val planId: Long = checkNotNull(savedStateHandle.get<Long>("planId"))
    private val starting = MutableStateFlow(false)
    private val startFailed = MutableStateFlow(false)

    val uiState: StateFlow<PlanDetailUiState> = combine(
        planRepository.observePlanDetail(planId),
        settings.settings,
        starting,
        startFailed,
    ) { plan, userSettings, isStarting, failed ->
        val duration = plan?.let {
            PlanDurationCalculator.estimate(
                exercises = it.exercises,
                planDefaultRestSeconds = it.defaultRestSeconds,
                globalDefaultRestSeconds = userSettings.defaultRestSeconds,
            )
        }
        PlanDetailUiState(
            isLoading = false,
            plan = plan,
            durationText = duration?.let { range ->
                "${formatDuration(range.lowSeconds)} - ${formatDuration(range.highSeconds)}"
            } ?: "",
            globalRestSeconds = userSettings.defaultRestSeconds,
            isStarting = isStarting,
            startFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDetailUiState())

    fun onStartWorkout(onStarted: (Long) -> Unit, mode: PrefillMode? = null) {
        val plan = uiState.value.plan ?: return
        if (plan.exercises.isEmpty()) return
        viewModelScope.launch {
            starting.value = true
            val effectiveMode = mode ?: if (settings.current().autoFillFromLastSession) {
                PrefillMode.LAST_SESSION
            } else {
                PrefillMode.PLAN_TARGETS
            }
            val outcome = sessionManager.startWorkout(planId = plan.id, mode = effectiveMode)
            starting.value = false
            // 失败时必须说出来：按钮转一圈又变回可点，用户只会以为"点了没反应"
            outcome.getOrNull()?.let(onStarted) ?: run {
                Timber.e("开始训练失败：%s", outcome.errorOrNull())
                startFailed.value = true
            }
        }
    }

    /** 单计划分享：把处方导出为文本（PRD M5-12 的轻量路径） */
    fun sharePlanText(): String? {
        val plan = uiState.value.plan ?: return null
        return buildString {
            appendLine(plan.name)
            plan.exercises.forEach { item ->
                appendLine()
                appendLine("${item.exercise.name} · ${item.targetSets} 组")
                item.sets.forEach { set ->
                    val weight = set.targetWeightKg?.let { "%.1fkg ".format(it) } ?: ""
                    val reps = set.targetReps?.let { "×$it" } ?: ""
                    if (weight.isNotEmpty() || reps.isNotEmpty()) {
                        appendLine("  ${set.setIndex + 1}. $weight$reps")
                    }
                }
            }
        }
    }

    fun consumeStartFailed() {
        startFailed.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onWorkoutStarted: (Long) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val startFailedToast = stringResource(com.fitnesspro.feature.plans.R.string.plans_start_failed)

    LaunchedEffect(state.startFailed) {
        if (!state.startFailed) return@LaunchedEffect
        viewModel.consumeStartFailed()
        snackbarHostState.showSnackbar(message = startFailedToast, actionLabel = null)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        state.plan?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_close),
                        )
                    }
                },
                actions = {
                    state.plan?.let { plan ->
                        // 单计划分享：生成处方卡片图 → 系统分享选择器（微信/QQ/相册都能收）
                        IconButton(onClick = { sharePlanCard(context, plan) }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(
                                    com.fitnesspro.feature.plans.R.string.plan_detail_share,
                                ),
                            )
                        }
                        IconButton(onClick = { onEdit(plan.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    com.fitnesspro.feature.plans.R.string.plan_detail_edit,
                                ),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if ((state.plan?.exercises?.size ?: 0) > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.onStartWorkout(onWorkoutStarted) },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        text = {
                            Text(
                                stringResource(
                                    if (state.isStarting) {
                                        com.fitnesspro.feature.plans.R.string.plan_edit_saving
                                    } else {
                                        com.fitnesspro.feature.plans.R.string.plan_detail_start
                                    },
                                )
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    // 三种填充模式（M4-1）：按上次成绩 / 按计划处方 / 空白训练
                    var modeMenu by remember { mutableStateOf(false) }
                    SmallFloatingActionButton(onClick = { modeMenu = true }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_start_mode_last)) },
                            onClick = {
                                modeMenu = false
                                viewModel.onStartWorkout(
                                    onWorkoutStarted,
                                    com.fitnesspro.core.data.workout.PrefillMode.LAST_SESSION,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_start_mode_targets)) },
                            onClick = {
                                modeMenu = false
                                viewModel.onStartWorkout(
                                    onWorkoutStarted,
                                    com.fitnesspro.core.data.workout.PrefillMode.PLAN_TARGETS,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_start_mode_blank)) },
                            onClick = {
                                modeMenu = false
                                viewModel.onStartWorkout(
                                    onWorkoutStarted,
                                    com.fitnesspro.core.data.workout.PrefillMode.BLANK,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val plan = state.plan
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            plan == null -> ErrorState(
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(FitnessTheme.spacing.m)) {
                            Text(
                                stringResource(
                                    com.fitnesspro.feature.plans.R.string.plan_detail_stats,
                                    plan.exercises.size,
                                    plan.totalSets,
                                    state.durationText,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            plan.description?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (plan.exercises.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(com.fitnesspro.feature.plans.R.string.plan_detail_empty_exercises),
                            description = stringResource(
                                com.fitnesspro.feature.plans.R.string.plan_detail_empty_exercises_desc,
                            ),
                            actionLabel = stringResource(com.fitnesspro.feature.plans.R.string.plan_detail_add_exercise),
                            onAction = { onEdit(plan.id) },
                        )
                    }
                } else {
                    // 按部位分区：计划同时练肩+背时，分成"肩 / 背"两块显示
                    // （只有一个部位时不插标题，避免噪音）
                    items(sectionedPlanRows(plan.exercises), key = { it.key }) { row ->
                        when (row) {
                            is PlanRow.Section -> MuscleSectionHeader(row.muscle)
                            is PlanRow.Item -> PlanExerciseCard(
                                item = row.item,
                                globalRestSeconds = state.globalRestSeconds,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 计划详情列表的一行：部位分区标题 / 动作卡 */
private sealed interface PlanRow {
    val key: String

    data class Section(val muscle: MuscleGroup?) : PlanRow {
        override val key: String = "section-${muscle?.name ?: "none"}"
    }

    data class Item(val item: PlanExerciseItem) : PlanRow {
        override val key: String = "item-${item.id}"
    }
}

/** 按主要肌群切分计划动作；只有一个部位时平铺，不插标题 */
private fun sectionedPlanRows(exercises: List<PlanExerciseItem>): List<PlanRow> {
    val groups = exercises.groupBy { it.exercise.primaryMuscle }
    if (groups.size <= 1) return exercises.map { PlanRow.Item(it) }
    return buildList {
        groups.forEach { (muscle, list) ->
            add(PlanRow.Section(muscle))
            list.forEach { add(PlanRow.Item(it)) }
        }
    }
}

/** 部位分区标题：强调色短竖条 + 部位名 */
@Composable
private fun MuscleSectionHeader(muscle: MuscleGroup?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FitnessTheme.spacing.s, start = FitnessTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(18.dp)
                .background(FitnessTheme.accent, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(FitnessTheme.spacing.s))
        Text(
            text = stringResource((muscle ?: MuscleGroup.OTHER).labelRes()),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun PlanExerciseCard(item: PlanExerciseItem, globalRestSeconds: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                        LabelChip(stringResource(item.exercise.type.labelRes()))
                        if (item.supersetTag != null) {
                            LabelChip(
                                stringResource(com.fitnesspro.feature.plans.R.string.plan_detail_superset),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Text(
                stringResource(
                    com.fitnesspro.feature.plans.R.string.plan_edit_target_sets,
                    item.targetSets,
                ) + " · " +
                    stringResource(
                        com.fitnesspro.feature.plans.R.string.plan_edit_target_reps,
                        RepsRange.parse(item.sets.firstOrNull()?.targetReps).display(),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    com.fitnesspro.feature.plans.R.string.plan_detail_rest,
                    item.restSecondsOverride ?: globalRestSeconds,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 供按钮样式统一使用 */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled) { Text(text) }
}

// ---------------------------------------------------------------------------
// 计划分享卡片
// ---------------------------------------------------------------------------

private const val SHARE_CARD_WIDTH = 1000
private const val SHARE_HEADER_HEIGHT = 240
private const val SHARE_ROW_HEIGHT = 88
private const val SHARE_FOOTER_HEIGHT = 90

/**
 * 单计划分享：生成一张处方卡片图，再交给系统分享选择器。
 *
 * 为什么不是纯文本：处方在 IM 里会被折叠成一行，图片才能一眼看清动作与组数。
 * 也不是"截当前页"：页面里有滚动位置、按钮和主题色块，发出去不像一张卡片。
 * 生成后走 `ACTION_SEND`，微信/QQ/相册等已安装应用都能收到。
 */
private fun sharePlanCard(context: Context, plan: PlanDetail) {
    runCatching {
        val bitmap = buildPlanCard(plan)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "plan_${plan.id}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }.onFailure { Timber.w(it, "生成计划分享卡片失败") }
}

/** 处方卡片：顶部品牌色带 + 计划名/规模 + 动作列表 + 页脚 */
private fun buildPlanCard(plan: PlanDetail): Bitmap {
    val rows = plan.exercises.take(12)
    val height = SHARE_HEADER_HEIGHT + rows.size * SHARE_ROW_HEIGHT + SHARE_FOOTER_HEIGHT
    val bitmap = Bitmap.createBitmap(SHARE_CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val accent = Color.parseColor("#F0653C")
    val ink = Color.parseColor("#1B1A1F")
    val muted = Color.parseColor("#6B6975")
    val hairline = Color.parseColor("#EDECF1")
    val left = 60f

    canvas.drawRect(0f, 0f, SHARE_CARD_WIDTH.toFloat(), 12f, Paint().apply { color = accent })

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 56f
        isFakeBoldText = true
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 32f }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 36f
        isFakeBoldText = true
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 28f }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hairline; strokeWidth = 2f }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9B99A4")
        textSize = 28f
    }

    canvas.drawText(
        titlePaint.fitText(plan.name, SHARE_CARD_WIDTH - left * 2),
        left,
        120f,
        titlePaint,
    )
    val meta = buildString {
        append("${plan.exercises.size} 个动作 · ${plan.totalSets} 组")
        plan.estimatedDurationSeconds?.let { append(" · 约 ${formatDuration(it)}") }
    }
    canvas.drawText(meta, left, 178f, metaPaint)

    var y = SHARE_HEADER_HEIGHT.toFloat()
    rows.forEach { item ->
        canvas.drawCircle(left + 8f, y + 30f, 8f, dotPaint)
        canvas.drawText(
            namePaint.fitText(item.exercise.name, SHARE_CARD_WIDTH - left * 2 - 40f),
            left + 34f,
            y + 42f,
            namePaint,
        )
        // 只展示第一组的目标：完整逐组处方在 IM 里没人逐行看，卡片要给"练什么、练多少"
        val first = item.sets.firstOrNull()
        val target = buildString {
            append("${item.targetSets} 组")
            first?.targetWeightKg?.let { append(" · ${"%.1f".format(it)} kg") }
            first?.targetReps?.let { append(" × $it") }
        }
        canvas.drawText(target, left + 34f, y + 78f, subPaint)
        canvas.drawLine(
            left,
            y + SHARE_ROW_HEIGHT - 6f,
            SHARE_CARD_WIDTH - left,
            y + SHARE_ROW_HEIGHT - 6f,
            linePaint,
        )
        y += SHARE_ROW_HEIGHT
    }
    if (plan.exercises.size > rows.size) {
        canvas.drawText("… 还有 ${plan.exercises.size - rows.size} 个动作", left, y + 40f, subPaint)
    }
    canvas.drawText("Fitness Pro · 离线训练记录", left, height - 34f, footerPaint)
    return bitmap
}

/** 超宽文本截断成"…"，避免长动作名画出卡片外 */
private fun Paint.fitText(text: String, maxWidth: Float): String {
    if (measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    val count = breakText(text, true, maxWidth - measureText(ellipsis), null)
    return text.take(count.coerceAtLeast(1)) + ellipsis
}
