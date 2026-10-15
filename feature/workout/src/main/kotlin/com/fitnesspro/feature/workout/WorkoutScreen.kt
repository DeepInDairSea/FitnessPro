package com.fitnesspro.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.common.navigation.NavResultBus
import com.fitnesspro.core.common.navigation.NavResults
import com.fitnesspro.core.data.workout.LogSetInput
import com.fitnesspro.core.data.workout.SummaryInput
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.domain.calculator.RepsRange
import com.fitnesspro.core.domain.calculator.TrainingAdvisor
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.SetStatusDot
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.LocalFitnessAccent
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.model.ActiveExercise
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.ActiveSet
import com.fitnesspro.core.model.ActiveWorkout
import com.fitnesspro.core.mediaui.ExerciseMedia
import com.fitnesspro.core.mediaui.MEDIA_SIZE_SMALL
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.RestState
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.UnitConversion
import com.fitnesspro.core.model.UserSettings
import com.fitnesspro.core.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.onFailure
import timber.log.Timber
import kotlinx.coroutines.launch
import javax.inject.Inject

// 本模块自己的 R（namespace = com.fitnesspro.feature.workout）
import com.fitnesspro.feature.workout.R
// 设计系统字符串（关闭、完成、热身组等）显式别名，避免与上面混淆
import com.fitnesspro.core.designsystem.R as DesignSystemR

/** 破纪录庆祝：一次性事件，UI 播完动画后调用 [WorkoutViewModel.onConsumeCelebration] 消费掉 */
data class PrCelebration(
    val exerciseName: String,
    val prType: PrType,
    val value: Double,
    /** 用于让连续两次破纪录都能重新触发动画 */
    val token: Long,
)

data class WorkoutUiState(
    val isLoading: Boolean = true,
    val workout: ActiveWorkout? = null,
    val settings: UserSettings = UserSettings(),
    val celebration: PrCelebration? = null,
    /**
     * 每个动作的渐进超负荷建议，key = workoutExerciseId。
     * 只作为**文案提示**，绝不自动改写用户输入（PRD FR-14）。
     */
    val loadSuggestions: Map<Long, TrainingAdvisor.LoadSuggestion> = emptyMap(),
    /** 写入失败的提示（目前主要是"结束训练"失败）。非空时用 Snackbar 展示 */
    val errorMessage: String? = null,
    /** 正在结束训练：禁用按钮防连点，避免提交两次 */
    val isFinishing: Boolean = false,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: WorkoutSessionManager,
    private val settings: SettingsRepository,
    private val navResultBus: NavResultBus,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    /**
     * 瞬时状态（庆祝 / 错误 / 提交中）。
     *
     * 合并成一个流而不是三个独立的流：`combine` 的参数数量是有上限的，
     * 每多一个流就要多一个参数，很快就不可读了。
     */
    private data class TransientState(
        val celebration: PrCelebration? = null,
        val errorMessage: String? = null,
        val isFinishing: Boolean = false,
    )

    private val transient = MutableStateFlow(TransientState())

    /**
     * 打开动作选择器之前先记住"为什么打开"：
     * 选择器只回传一个动作 id，是"加进去"还是"换掉某个动作"由调用方决定。
     */
    private var pendingPick: PendingPick? = null

    private sealed interface PendingPick {
        data object Adhoc : PendingPick
        data class Replace(val workoutExerciseId: Long) : PendingPick
    }

    init {
        // 动作选择器的结果通过 NavResultBus 投递。
        // 不用 SavedStateHandle：实测它与本 ViewModel 拿到的不是同一实例，
        // 会导致"选了动作但这里收不到"且完全静默（见 NavResultBus 注释）。
        viewModelScope.launch {
            navResultBus.observe(NavResults.PICKED_EXERCISE_ID)
                .filterIsInstance<Long>()
                .collect { definitionId ->
                    when (val action = pendingPick) {
                        is PendingPick.Replace ->
                            sessionManager.replaceExercise(action.workoutExerciseId, definitionId)
                        else -> sessionManager.addAdhocExercise(sessionId, definitionId)
                    }
                    pendingPick = null
                }
        }
    }

    val uiState: StateFlow<WorkoutUiState> = combine(
        // 只观察本页 sessionId 的会话：多个未完成训练并存时，
        // 全局"最新一条"的观察方式会让 A 会话的页面渲染出 B 会话的数据
        sessionManager.observeWorkout(sessionId),
        settings.settings,
        transient,
    ) { active, userSettings, transientState ->
        WorkoutUiState(
            isLoading = false,
            workout = active,
            settings = userSettings,
            celebration = transientState.celebration,
            errorMessage = transientState.errorMessage,
            isFinishing = transientState.isFinishing,
            loadSuggestions = active?.let { suggestLoads(it, userSettings.weightUnit) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /**
     * 双进阶（Double Progression）建议：以计划处方的次数区间为基准，用上次成绩判断
     * 该加重、维持还是减重。
     *
     * 只在"有上次成绩"且"能确定基准重量"时给建议 —— 缺任何一项都返回空，
     * 界面不显示建议行。宁可不说，也不要给一个没有依据的数字。
     */
    private fun suggestLoads(
        workout: ActiveWorkout,
        unit: WeightUnit,
    ): Map<Long, TrainingAdvisor.LoadSuggestion> = workout.exercises
        .mapNotNull { exercise ->
            val range = RepsRange.parse(exercise.sets.firstOrNull()?.targetReps)
            val base = exercise.sets.firstNotNullOfOrNull { it.targetWeightKg ?: it.weightKg }
                ?: exercise.lastSessionSets.firstNotNullOfOrNull { it.weightKg }
            if (range.isEmpty || base == null || exercise.lastSessionSets.isEmpty()) {
                return@mapNotNull null
            }
            exercise.id to TrainingAdvisor.suggestNextLoad(
                lastSessionSets = exercise.lastSessionSets,
                repRange = range,
                currentWeightKg = base,
                weightUnit = unit,
            )
        }
        .toMap()

    /**
     * 完成一组。
     *
     * UI 采用**乐观更新**的思路：这里先不做本地状态镜像，而是依赖 Room 的 Flow 回流
     * （写入通常 < 20ms，用户感知不到）。真正需要注意的是失败路径：
     * 落库失败时按钮不清空输入，用户可以再点一次，而不是丢掉刚填的数据。
     */
    fun onCompleteSet(
        exercise: ActiveExercise,
        set: ActiveSet,
        weightInput: String,
        repsInput: String,
        durationInput: String,
        distanceInput: String,
        leftInput: String,
        rightInput: String,
        rpeInput: String,
    ) {
        val unit = uiState.value.settings.weightUnit
        val weight = weightInput.toDoubleOrNull()
        val type = exercise.exercise.type

        viewModelScope.launch {
            when (val outcome = sessionManager.logSet(
                LogSetInput(
                    workoutExerciseId = exercise.id,
                    setIndex = set.setIndex,
                    setType = set.setType,
                    weightInput = weight,
                    unit = unit,
                    reps = repsInput.toIntOrNull(),
                    durationSeconds = durationInput.toIntOrNull(),
                    distanceMeters = distanceInput.toDoubleOrNull(),
                    leftReps = leftInput.toIntOrNull(),
                    rightReps = rightInput.toIntOrNull(),
                    rpe = if (uiState.value.settings.showRpe) rpeInput.toDoubleOrNull() else null,
                )
            )) {
                is Outcome.Success -> {
                    // 一组里最多命中一个 PR；只庆祝第一个，避免同时弹两条把用户淹没
                    outcome.data.prHits.firstOrNull()?.let { hit ->
                        transient.update {
                            it.copy(
                                celebration = PrCelebration(
                                    exerciseName = hit.exerciseName,
                                    prType = hit.prType,
                                    value = hit.value,
                                    token = System.nanoTime(),
                                ),
                            )
                        }
                    }
                }
                is Outcome.Failure -> {
                    // 记录一组失败时组仍是 PENDING：不提示的话用户会以为记上了，
                    // 到总结页才发现少了一组
                    Timber.e(outcome.error.toString(), "记录训练组失败")
                    transient.update { it.copy(errorMessage = ERROR_LOG_SET_FAILED) }
                }
            }
        }
    }

    fun onConsumeCelebration() {
        transient.update { it.copy(celebration = null) }
    }

    /**
     * 统一执行一次组/动作级写操作。
     *
     * 这些操作（删除末组、复制一组、跳过…）在业务规则拒绝时返回 Failure
     * （例如"至少保留一组"），没有独立的视觉结果 —— 不提示的话用户只会
     * 看到"点了没反应"，并把它当成偶发卡顿多点几次。
     */
    private fun launchLogged(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            val outcome = block()
            outcome.errorOrNull()?.let { error ->
                Timber.e("训练操作失败：%s", error)
                transient.update { it.copy(errorMessage = ERROR_ACTION_FAILED) }
            }
        }
    }

    /** 复制一组：以刚才那组的实际数值作为新组目标 */
    fun onDuplicateSet(setId: Long) {
        launchLogged { sessionManager.duplicateSet(setId) }
    }

    /** 打开动作选择器，回来后把选中的动作作为临时动作追加到本次训练 */
    fun onBeginAddAdhoc() {
        pendingPick = PendingPick.Adhoc
    }

    /** 打开动作选择器，回来后替换指定动作（已记录的成绩保留） */
    fun onBeginReplace(workoutExerciseId: Long) {
        pendingPick = PendingPick.Replace(workoutExerciseId)
    }

    fun onSkipSet(set: ActiveSet) {
        launchLogged { sessionManager.skipSet(set.id) }
    }

    fun onSkipExercise(exerciseId: Long) {
        launchLogged { sessionManager.skipExercise(exerciseId) }
    }

    fun onAddSet(exerciseId: Long) {
        launchLogged { sessionManager.addSetToExercise(exerciseId) }
    }

    fun onDeleteLastSet(exerciseId: Long) {
        launchLogged { sessionManager.deleteLastPendingSet(exerciseId) }
    }

    fun onPauseToggle() {
        launchLogged {
            val workout = uiState.value.workout ?: return@launchLogged Outcome.Success(Unit)
            if (workout.isPaused) sessionManager.resume(sessionId) else sessionManager.pause(sessionId)
        }
    }

    /**
     * 结束训练。
     *
     * **只有写入成功才允许离开。** 这里以前是：
     * ```
     * sessionManager.finish(...)   // Outcome 被丢弃
     * onFinished(sessionId)        // 无条件跳转
     * ```
     * 一旦 `finish` 失败（会话没被置为 COMPLETED、汇总与 PR 都没提交），用户已经被带到总结页，
     * 而训练页又被 `popUpTo(..., inclusive = true)` 从返回栈里移除了 ——
     * 于是留下一个**永远无法收尾的活动会话**：统计不计、PR 不判、也没有重试入口。
     * 结束训练是整个训练流程的最后一次写入，失败必须留在原地并说清楚。
     */
    fun onFinish(onFinished: (Long) -> Unit) {
        if (transient.value.isFinishing) return
        viewModelScope.launch {
            transient.update { it.copy(isFinishing = true) }
            val outcome = sessionManager.finish(sessionId, SummaryInput(completedFully = true))
            if (outcome.isSuccess) {
                onFinished(sessionId)
            } else {
                transient.update {
                    it.copy(isFinishing = false, errorMessage = ERROR_FINISH_FAILED)
                }
            }
        }
    }

    fun onConsumeError() {
        transient.update { it.copy(errorMessage = null) }
    }

    /** 不保存进度：彻底丢弃本次会话后离开。失败留在原地（与 onFinish 同一条原则） */
    fun onDiscard(onDone: () -> Unit) {
        if (transient.value.isFinishing) return
        viewModelScope.launch {
            transient.update { it.copy(isFinishing = true) }
            val outcome = sessionManager.discard(sessionId)
            if (outcome.isSuccess) {
                onDone()
            } else {
                transient.update {
                    it.copy(isFinishing = false, errorMessage = ERROR_DISCARD_FAILED)
                }
            }
        }
    }

    fun onExtendRest(seconds: Int) {
        viewModelScope.launch { sessionManager.extendRest(sessionId, seconds) }
    }

    fun onToggleRestPause() {
        viewModelScope.launch { sessionManager.toggleRestPause(sessionId) }
    }

    fun onSkipRest() {
        viewModelScope.launch { sessionManager.skipRest(sessionId) }
    }

    private companion object {
        const val ERROR_FINISH_FAILED = "结束训练失败，本次训练还没有保存。请重试"
        const val ERROR_DISCARD_FAILED = "丢弃失败，请重试"
        const val ERROR_LOG_SET_FAILED = "这一组没能记录，请重试"
        const val ERROR_ACTION_FAILED = "操作失败，请重试"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onFinished: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    /** 打开动作选择器；选中结果通过 NavResults 回传到本页的 ViewModel */
    onOpenExercisePicker: () -> Unit,
    /** 打开全屏演示播放（组间休息时看动作要领） */
    onPlayMedia: (MediaRef) -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var elapsedSeconds by remember { mutableStateOf(0) }
    var restRemainingMs by remember { mutableStateOf(0L) }
    var showExitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // 完成一个动作的祝贺文案（在组合里取好，LaunchedEffect 里不能用 stringResource）
    val completedToast = stringResource(R.string.workout_exercise_done)

    // 写入失败必须让用户看到，否则"结束了但没存上"这件事根本无从察觉
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onConsumeError()
    }

    // 已用时间与倒计时每秒刷新。倒计时的"真理"仍在数据库里，
    // 这里只是它的一个视图 —— 即使本协程被杀，重新进入也立即正确。
    LaunchedEffect(state.workout?.isPaused) {
        elapsedSeconds = state.workout?.let { it.totalCompletedSets * 0 + estimateElapsed(it) } ?: 0
        while (true) {
            delay(1_000)
            val workout = state.workout ?: continue
            elapsedSeconds = estimateElapsed(workout)
            restRemainingMs = when (val rest = workout.rest) {
                is RestState.Running -> rest.remainingMs(System.currentTimeMillis())
                else -> 0L
            }
        }
    }

    val workout = state.workout
    // 屏幕常亮：跟随设置开关（此前开关存在但不生效）。离开页面自动恢复系统行为
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(state.settings.keepScreenOnDuringWorkout) {
        view.keepScreenOn = state.settings.keepScreenOnDuringWorkout
        onDispose { view.keepScreenOn = false }
    }
    // 系统返回键与 X 按钮同一语义：先确认，不直接丢进度
    BackHandler { showExitDialog = true }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        workout?.planName ?: stringResource(R.string.workout_free_training),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    // 直接退出会让用户误触就丢进度：先询问"保存并退出 / 不保存"
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(DesignSystemR.string.common_close))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onPauseToggle) {
                        Icon(
                            if (workout?.isPaused == true) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = stringResource(
                                if (workout?.isPaused == true) {
                                    DesignSystemR.string.common_confirm
                                } else {
                                    DesignSystemR.string.common_cancel
                                },
                            ),
                        )
                    }
                    TextButton(
                        onClick = { viewModel.onFinish(onFinished) },
                        enabled = !state.isFinishing,
                    ) {
                        Text(stringResource(DesignSystemR.string.common_done))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (workout == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        // 折叠状态：默认只展开"当前该练的动作"，用户手动点过的记在这里。
        // 刻意不把当前动作重排到最前 —— 那会打乱处方顺序、也让动作详情与历史对不上。
        val expandedIds = remember { mutableStateMapOf<Long, Boolean>() }
        val currentExerciseId = workout.exercises.getOrNull(workout.currentExerciseIndex)?.id
        val rows = sectionedRows(workout.exercises)
        val listState = rememberLazyListState()

        // 进入训练后自动滚到当前动作：等效于"置顶"，但不改变顺序，滚回去也还在原位。
        // 当前动作切换 = 上一个动作完成：收起它、祝贺一句，再把下一个滚到眼前。
        var lastCurrentId by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(currentExerciseId) {
            val previous = lastCurrentId
            if (previous != null && previous != currentExerciseId) {
                expandedIds[previous] = false
                snackbarHostState.showSnackbar(completedToast)
            }
            lastCurrentId = currentExerciseId

            val target = rows.indexOfFirst {
                it is WorkoutRow.Exercise && it.exercise.id == currentExerciseId
            }
            if (target > 0) listState.animateScrollToItem(target)
        }

        // 训练页强调色 = 橙（与计划页一致：训练是计划的执行态）
        val pageAccent = FitnessTheme.layout.accents.firstOrNull() ?: MaterialTheme.colorScheme.primary
        CompositionLocalProvider(LocalFitnessAccent provides pageAccent) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 左右留白写全：动作卡是独立卡片，贴边会显得像列表分隔线
                contentPadding = PaddingValues(
                    start = FitnessTheme.spacing.m,
                    end = FitnessTheme.spacing.m,
                    top = FitnessTheme.spacing.m,
                    bottom = FitnessTheme.spacing.l,
                ),
                verticalArrangement = Arrangement.spacedBy(FitnessTheme.layout.cardGap),
            ) {
                item(key = "header") {
                    WorkoutHeader(
                        elapsedSeconds = elapsedSeconds,
                        workout = workout,
                        isPaused = workout.isPaused,
                    )
                }

                if (workout.exercises.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(FitnessTheme.spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.workout_no_exercises),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.s))
                            Text(
                                stringResource(R.string.workout_no_exercises_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.m))
                            OutlinedButton(
                                onClick = {
                                    viewModel.onBeginAddAdhoc()
                                    onOpenExercisePicker()
                                },
                            ) {
                                Text(stringResource(R.string.workout_add_exercise))
                            }
                        }
                    }
                }

                // 按部位分区：一个计划同时练肩+背时，动作列表被分成"肩部 / 背部"两块，
                // 用户能清楚看到"这部分练完了，接下来换部位"。只有一个部位时不插标题，避免噪音。
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is WorkoutRow.Section -> MuscleSectionHeader(row.muscle)
                        is WorkoutRow.Exercise -> {
                            val exercise = row.exercise
                            val isCurrent = exercise.id == currentExerciseId
                            val isExpanded = expandedIds[exercise.id] ?: isCurrent
                            ExerciseWorkoutCard(
                                exercise = exercise,
                                settings = state.settings,
                                suggestion = state.loadSuggestions[exercise.id],
                                isCurrent = isCurrent,
                                expanded = isExpanded,
                                onToggleExpand = { expandedIds[exercise.id] = !isExpanded },
                                onPlayMedia = onPlayMedia,
                                onCompleteSet = { set, w, r, d, dist, l, rr, rpe ->
                                    viewModel.onCompleteSet(exercise, set, w, r, d, dist, l, rr, rpe)
                                },
                                onSkipSet = viewModel::onSkipSet,
                                onDuplicateSet = { viewModel.onDuplicateSet(it.id) },
                                onSkipExercise = { viewModel.onSkipExercise(exercise.id) },
                                onAddSet = { viewModel.onAddSet(exercise.id) },
                                onDeleteLastSet = { viewModel.onDeleteLastSet(exercise.id) },
                                onReplaceExercise = {
                                    viewModel.onBeginReplace(exercise.id)
                                    onOpenExercisePicker()
                                },
                            )
                        }
                    }
                }

                item(key = "addExercise") {
                    OutlinedButton(
                        onClick = {
                            viewModel.onBeginAddAdhoc()
                            onOpenExercisePicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.workout_add_exercise))
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(120.dp)) }
            }

            // 休息计时悬浮胶囊：只在计时进行中显示
            val rest = workout.rest
            if (rest is RestState.Running) {
                RestTimerPill(
                    remainingMs = restRemainingMs,
                    plannedSeconds = rest.plannedSeconds,
                    isPaused = rest.isPaused,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = FitnessTheme.spacing.l),
                    onExtend = { viewModel.onExtendRest(30) },
                    onTogglePause = viewModel::onToggleRestPause,
                    onSkip = viewModel::onSkipRest,
                )
            }

            // 休息已结束（例如用户息屏后回来）：显式说一次"该下一组了"，
            // 而不是让倒计时胶囊静默消失 —— 静默消失会被理解成"计时器坏了"。
            // 点一下就收起，不做强制弹窗打断用户。
            if (rest is RestState.Finished) {
                Surface(
                    onClick = viewModel::onSkipRest,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = FitnessTheme.spacing.l),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.rest_finished_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(
                            horizontal = FitnessTheme.spacing.m,
                            vertical = FitnessTheme.spacing.s,
                        ),
                    )
                }
            }

            // 破纪录庆祝浮层：顶部浮出，不打断节奏
            PrCelebrationOverlay(
                celebration = state.celebration,
                onFinished = viewModel::onConsumeCelebration,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = FitnessTheme.spacing.m),
            )
        }
        }
    }

    if (showExitDialog) {
        WorkoutExitDialog(
            onCancel = { showExitDialog = false },
            onSaveAndExit = {
                showExitDialog = false
                // 进度已实时落库，保留进行中的会话，首页会有"继续训练"卡
                onNavigateBack()
            },
            onDiscard = {
                showExitDialog = false
                viewModel.onDiscard { onNavigateBack() }
            },
        )
    }
}

/**
 * 退出训练确认：X 不能直接退，否则误触就丢进度。
 * 三个常用选项：取消 / 保存并退出 / 不保存（丢弃本次会话）。
 */
@Composable
private fun WorkoutExitDialog(
    onCancel: () -> Unit,
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.workout_exit_title)) },
        text = { Text(stringResource(R.string.workout_exit_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(R.string.workout_exit_save))
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        stringResource(R.string.workout_exit_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.workout_exit_cancel))
            }
        },
    )
}

/**
 * 破纪录庆祝浮层。
 *
 * 设计取舍：
 *  - **不阻塞操作**：只做视觉与触感反馈，不抢焦点、不拦截点击。
 *    训练中最好的庆祝是不打断节奏 —— 用户可以立刻继续记录下一组。
 *  - **自动消失**：到时间后消费事件（状态在 ViewModel，Composable 被回收也不会留下脏状态）。
 *  - 触感用 [HapticFeedbackType.LongPress]：最轻量的"注意一下"，
 *    不依赖 VIBRATE 权限，也尊重用户的系统触感设置。
 */
@Composable
private fun PrCelebrationOverlay(
    celebration: PrCelebration?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // 渲染的是"最后一次收到的庆祝"：否则事件被消费后，退场动画会渲染一个空浮层
    var displayed by remember { mutableStateOf<PrCelebration?>(null) }

    LaunchedEffect(celebration?.token) {
        val hit = celebration ?: return@LaunchedEffect
        displayed = hit
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(PR_CELEBRATION_MS)
        onFinished()
    }

    AnimatedVisibility(
        visible = celebration != null,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.85f),
        exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 0.95f),
        modifier = modifier,
    ) {
        val hit = displayed ?: return@AnimatedVisibility
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = FitnessTheme.spacing.l,
                    vertical = FitnessTheme.spacing.m,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrBadge()
                Spacer(Modifier.width(FitnessTheme.spacing.m))
                Column {
                    Text(
                        stringResource(R.string.workout_pr_celebration_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.workout_pr_celebration_detail,
                            hit.exerciseName,
                            when (hit.prType) {
                                PrType.MAX_REPS ->
                                    stringResource(R.string.summary_pr_value_reps, hit.value.toInt())
                                else -> formatWeightCompact(hit.value)
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val PR_CELEBRATION_MS = 2_600L

private fun estimateElapsed(workout: ActiveWorkout): Int {
    val now = System.currentTimeMillis()
    val base = ((now - workout.startedAt) / 1000).toInt().coerceAtLeast(0)
    return if (workout.isPaused) base - workout.pausedTotalSeconds else base
}

@Composable
private fun WorkoutHeader(elapsedSeconds: Int, workout: ActiveWorkout, isPaused: Boolean) {
    // 同动作卡：概览区必须不透明，否则半透明底 + 壁纸会让数字对比度崩掉
    SoftCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 1f)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricColumn(
                    label = stringResource(R.string.workout_elapsed),
                    value = formatDuration(elapsedSeconds),
                )
                MetricColumn(
                    label = stringResource(R.string.workout_volume),
                    value = formatVolume(workout.totalVolumeKg),
                )
                MetricColumn(
                    label = stringResource(R.string.workout_title),
                    value = stringResource(
                        R.string.workout_progress,
                        workout.totalCompletedSets,
                        workout.totalSets,
                    ),
                )
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            LinearProgressIndicator(
                progress = {
                    if (workout.totalSets == 0) 0f
                    else workout.totalCompletedSets.toFloat() / workout.totalSets
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isPaused) {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(R.string.workout_paused_banner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = numericTextStyle(NumericSize.Medium))
    }
}

@Composable
private fun ExerciseWorkoutCard(
    exercise: ActiveExercise,
    settings: UserSettings,
    suggestion: TrainingAdvisor.LoadSuggestion?,
    /** 是否是"当前该练的那个动作"：默认展开它，其余动作折叠成一行 */
    isCurrent: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCompleteSet: (ActiveSet, String, String, String, String, String, String, String) -> Unit,
    onSkipSet: (ActiveSet) -> Unit,
    onDuplicateSet: (ActiveSet) -> Unit,
    onSkipExercise: () -> Unit,
    onAddSet: () -> Unit,
    onDeleteLastSet: () -> Unit,
    onReplaceExercise: () -> Unit,
    onPlayMedia: (MediaRef) -> Unit = {},
) {
    var showMoreActions by remember { mutableStateOf(false) }
    val nextSet = exercise.sets.firstOrNull { it.status == SetStatus.PENDING }
    val unit = settings.weightUnit
    // 最后有成绩的一组："再来一组同样的"从这里取数值，比让用户回忆更可靠。
    // 跳过组不算 —— 它没有数值可复制。
    val lastSettledSet = exercise.sets.lastOrNull {
        it.status == SetStatus.COMPLETED || it.status == SetStatus.FAILED
    }

    // 渐进超负荷建议：只作为文案提示，绝不自动改写下面的输入框（PRD FR-14）
    val suggestionText: String? = suggestion
        ?.takeIf { it.reason != TrainingAdvisor.SuggestionReason.NO_DATA }
        ?.let { hint ->
            stringResource(
                when (hint.reason) {
                    TrainingAdvisor.SuggestionReason.INCREASE_WEIGHT -> R.string.workout_suggestion_increase
                    TrainingAdvisor.SuggestionReason.DELOAD_HINT -> R.string.workout_suggestion_deload
                    else -> R.string.workout_suggestion_keep
                },
                formatWeightCompact(UnitConversion.fromKg(hint.weightKg, unit)),
            )
        }

    // 输入框初始值回退链：本组目标（计划/上次成绩预填）→ 本动作上一完成组（第一次设置后，
    // 后续组自动沿用，除非用户自己改）→ 上次训练该动作的最后一组 → 空
    var weightInput by remember(nextSet?.id) {
        mutableStateOf(
            nextSet?.targetWeightKg?.let { formatWeightCompact(UnitConversion.fromKg(it, unit)) }
                ?: lastSettledSet?.weightKg?.let { formatWeightCompact(UnitConversion.fromKg(it, unit)) }
                ?: exercise.lastSessionSets.lastOrNull()?.weightKg
                    ?.let { formatWeightCompact(UnitConversion.fromKg(it, unit)) }
                ?: ""
        )
    }
    var repsInput by remember(nextSet?.id) {
        mutableStateOf(
            nextSet?.targetReps?.takeWhile { it.isDigit() }
                ?: lastSettledSet?.reps?.toString()
                ?: exercise.lastSessionSets.lastOrNull()?.reps?.toString()
                ?: ""
        )
    }
    var durationInput by remember(nextSet?.id) { mutableStateOf("") }
    var distanceInput by remember(nextSet?.id) { mutableStateOf("") }
    var leftInput by remember(nextSet?.id) { mutableStateOf("") }
    var rightInput by remember(nextSet?.id) { mutableStateOf("") }
    var rpeInput by remember(nextSet?.id) { mutableStateOf("") }

    // 底色强制不透明：壁纸玻璃模式下 surfaceVariant 是 80% 半透明的，
    // 训练页内容密、色块多，透出壁纸后整张卡会"暗沉"、边界也糊掉。
    // 训练中读数字优先于氛围，这里与首页白卡保持同一观感。
    SoftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)) {
        Column {
            // 头部整行可点：点一下展开/收起。当前动作默认展开，其余折叠成一行，
            // 一屏就能看清"现在练哪个、后面还有什么"，不用在长列表里翻。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 训练中看演示最有用：组间休息时确认动作要领
                ExerciseMedia(
                    media = exercise.media,
                    size = MEDIA_SIZE_SMALL,
                    onClick = { exercise.media?.let(onPlayMedia) },
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    if (expanded) {
                        val lastText = if (exercise.lastSessionSets.isEmpty()) {
                            stringResource(R.string.workout_no_history)
                        } else {
                            stringResource(
                                R.string.workout_last_time,
                                exercise.lastSessionSets.joinToString(", ") { record ->
                                    val w = record.weightKg
                                    val r = record.reps
                                    when {
                                        w != null && r != null ->
                                            "${formatWeightCompact(UnitConversion.fromKg(w, unit))}×$r"
                                        r != null -> "${r}次"
                                        else -> "-"
                                    }
                                },
                            )
                        }
                        Text(
                            lastText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // 折叠态只留进度，信息足够判断"这个动作还要不要做"
                        Text(
                            stringResource(
                                R.string.workout_progress,
                                exercise.sets.count { it.status == SetStatus.COMPLETED },
                                exercise.sets.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LabelChip(
                    stringResource(
                        R.string.workout_rest_seconds,
                        exercise.restSeconds,
                    ),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.workout_collapse else R.string.workout_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = FitnessTheme.spacing.xs),
                )
            }

            if (expanded && suggestionText != null) {
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                Text(
                    suggestionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = FitnessTheme.colors.prHighlight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ↓↓↓ 折叠区：set 列表、输入区与操作按钮只在展开时渲染
            if (expanded) {
            Spacer(Modifier.height(FitnessTheme.spacing.s))

            exercise.sets.forEach { set ->
                SetRow(set = set, unit = unit)
            }

            Spacer(Modifier.height(FitnessTheme.spacing.s))

            if (nextSet != null) {
                val type = exercise.exercise.type
                Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                    if (type.usesWeight) {
                        NumberField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = stringResource(R.string.workout_weight_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (type.usesReps) {
                        NumberField(
                            value = repsInput,
                            onValueChange = { repsInput = it },
                            label = stringResource(R.string.workout_reps_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (type.usesDuration) {
                        NumberField(
                            value = durationInput,
                            onValueChange = { durationInput = it },
                            label = stringResource(R.string.workout_duration_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (type.usesDistance) {
                        NumberField(
                            value = distanceInput,
                            onValueChange = { distanceInput = it },
                            label = stringResource(R.string.workout_distance_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (exercise.exercise.isUnilateral) {
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                        NumberField(
                            value = leftInput,
                            onValueChange = { leftInput = it },
                            label = stringResource(R.string.workout_left_label),
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = rightInput,
                            onValueChange = { rightInput = it },
                            label = stringResource(R.string.workout_right_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (settings.showRpe) {
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    NumberField(
                        value = rpeInput,
                        onValueChange = { rpeInput = it },
                        label = stringResource(R.string.workout_rpe_label),
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }

                Spacer(Modifier.height(FitnessTheme.spacing.m))
                Button(
                    onClick = {
                        onCompleteSet(
                            nextSet, weightInput, repsInput, durationInput,
                            distanceInput, leftInput, rightInput, rpeInput,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(FitnessTheme.spacing.xs))
                    Text(stringResource(R.string.workout_complete_set))
                }
            }

            // 次要操作收进"更多"：原来 5 个按钮平铺成两行，一屏里按钮比数据还抢眼
            Box {
                TextButton(onClick = { showMoreActions = true }) {
                    Text(stringResource(R.string.workout_more_actions))
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMoreActions,
                    onDismissRequest = { showMoreActions = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_add_set)) },
                        onClick = {
                            showMoreActions = false
                            onAddSet()
                        },
                    )
                    // 复制一组：把刚才那组的实际数值带进来，训练中最高频的操作之一
                    lastSettledSet?.let { settled ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workout_duplicate_set)) },
                            onClick = {
                                showMoreActions = false
                                onDuplicateSet(settled)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_delete_set)) },
                        onClick = {
                            showMoreActions = false
                            onDeleteLastSet()
                        },
                    )
                    nextSet?.let { set ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workout_skip_set)) },
                            onClick = {
                                showMoreActions = false
                                onSkipSet(set)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_replace_exercise)) },
                        onClick = {
                            showMoreActions = false
                            onReplaceExercise()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_skip_exercise)) },
                        onClick = {
                            showMoreActions = false
                            onSkipExercise()
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SetRow(set: ActiveSet, unit: com.fitnesspro.core.model.WeightUnit) {
    val completed = set.status == SetStatus.COMPLETED || set.status == SetStatus.FAILED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetStatusDot(completed = completed, isPersonalRecord = set.isPersonalRecord)
        Spacer(Modifier.width(FitnessTheme.spacing.s))
        Text(
            stringResource(R.string.workout_set_label, set.setIndex + 1),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(64.dp),
        )
        if (set.setType == SetType.WARMUP) {
            LabelChip(stringResource(DesignSystemR.string.set_type_warmup))
            Spacer(Modifier.width(FitnessTheme.spacing.xs))
        }
        Spacer(Modifier.weight(1f))
        Text(
            buildString {
                set.weightKg?.let { append(formatWeightCompact(UnitConversion.fromKg(it, unit))) }
                set.reps?.let { append(" × $it") }
                set.durationSeconds?.let { append(" ${it}s") }
                if (isEmpty()) append("—")
            },
            style = numericTextStyle(NumericSize.Small),
        )
        if (set.isPersonalRecord) {
            Spacer(Modifier.width(FitnessTheme.spacing.xs))
            PrBadge(compact = true)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        textStyle = numericTextStyle(NumericSize.Small),
    )
}

/**
 * 休息计时悬浮胶囊。
 *
 * 显示的是由 endsAtEpochMs 推算出来的剩余时间，因此它永远与数据库一致 ——
 * 即使通知被清掉、闹钟被延迟、进程刚被杀过。
 */
@Composable
private fun RestTimerPill(
    remainingMs: Long,
    plannedSeconds: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    onExtend: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
) {
    val totalMs = (plannedSeconds.coerceAtLeast(1) * 1000L).toFloat()
    val progress = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .fillMaxWidth(0.92f),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration((remainingMs / 1000).toInt()),
                    style = numericTextStyle(NumericSize.Large),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onExtend) {
                    Text(stringResource(R.string.rest_add_30s))
                }
                TextButton(onClick = onTogglePause) {
                    Text(
                        stringResource(
                            if (isPaused) R.string.rest_resume else R.string.rest_pause,
                        ),
                    )
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.rest_skip))
                }
            }
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FitnessTheme.colors.restTimerIndicator),
                )
            }
        }
    }
}

/** 供 summary 复用：把 REST 状态映射为文案 */
internal fun RestState.displayText(): String = when (this) {
    RestState.Idle -> ""
    RestState.Finished -> "休息已结束"
    is RestState.Running -> formatDuration((plannedSeconds).coerceAtLeast(0))
}

// ---------------------------------------------------------------------------
// 按部位分区
// ---------------------------------------------------------------------------

/** 训练页列表的一行：要么是部位分区标题，要么是一个动作卡 */
private sealed interface WorkoutRow {
    val key: String

    data class Section(val muscle: MuscleGroup?) : WorkoutRow {
        override val key: String = "section-${muscle?.name ?: "none"}"
    }

    data class Exercise(val exercise: ActiveExercise) : WorkoutRow {
        override val key: String = "exercise-${exercise.id}"
    }
}

/**
 * 把动作按主要肌群切成"区块标题 + 动作"的平铺行。
 *
 * 只有一个部位（或都取不到部位）时直接平铺、不插标题 —— 给单部位计划加标题纯属噪音。
 * 一个计划同时练肩+背时，这里就会分出"肩 / 背"两块，训练节奏一目了然。
 */
private fun sectionedRows(exercises: List<ActiveExercise>): List<WorkoutRow> {
    val groups = exercises.groupBy { it.exercise.primaryMuscle }
    if (groups.size <= 1) return exercises.map { WorkoutRow.Exercise(it) }
    return buildList {
        groups.forEach { (muscle, list) ->
            add(WorkoutRow.Section(muscle))
            list.forEach { add(WorkoutRow.Exercise(it)) }
        }
    }
}

/** 部位分区标题：一道强调色短竖条 + 部位名 */
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
