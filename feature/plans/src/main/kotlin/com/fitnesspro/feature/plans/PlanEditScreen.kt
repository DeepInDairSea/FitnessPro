package com.fitnesspro.feature.plans

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.common.navigation.NavResultBus
import com.fitnesspro.core.common.navigation.NavResults
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.calculator.RepsRange
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.model.ExerciseSnapshot
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.data.media.MediaRegistrar
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.mediaui.ExerciseMedia
import com.fitnesspro.core.mediaui.MediaAttachSheet
import com.fitnesspro.core.mediaui.MEDIA_SIZE_SMALL
import com.fitnesspro.core.model.PlanDetail
import com.fitnesspro.core.model.PlanExerciseItem
import com.fitnesspro.core.model.PlannedSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

/**
 * 保存过程的瞬时状态。
 *
 * 这个页面**没有自动保存**：什么时候写库完全由用户点"保存"决定。
 * 因此这里不再需要"已保存/未保存"这类常驻指示灯 ——
 * 它和"退出时询问是否保存"是互相矛盾的两种模型，同时存在只会让人怀疑数据到底存没存。
 */
enum class SaveState {
    IDLE,

    SAVING,

    /** 保存失败：留在页面并说明原因，绝不静默关闭 */
    FAILED,
}

data class PlanEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = false,
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val defaultRestSeconds: String = "",
    /** 每周几循环排程（ISO：1=周一），随"保存"一起落库 */
    val scheduledDays: Set<Int> = emptySet(),
    /** 当日主要训练部位（大肌群自由组合，如 胸+腿）；仅作标记，不限制动作选择 */
    val focus: List<MajorGroup> = emptyList(),
    val exercises: List<PlanExerciseItem> = emptyList(),
    /**
     * 是否存在尚未保存的修改。
     *
     * 这是"保存按钮是否可点"与"退出时是否要询问"的**唯一依据**：
     * 没有它就只能靠猜，而猜错的两侧代价都很高（该问不问 = 丢数据；不该问却问 = 骚扰）。
     */
    val isDirty: Boolean = false,
    val saveState: SaveState = SaveState.IDLE,
    /** 保存失败的原因。非空即表示改动没写进去 */
    val errorMessage: String? = null,
    /** 保存成功次数。自增表示"可以安全离开"，由 UI 消费后出栈 */
    val savedToken: Long = 0L,
) {
    /**
     * 记录一次本地改动：标记为待保存，并清掉上一次的错误/失败态。
     *
     * 所有修改入口都必须走这里 —— 漏掉一处的后果是"改了东西但退出时不问是否保存"，
     * 那是直接丢用户的输入。
     */
    fun markDirty(): PlanEditUiState = copy(
        isDirty = true,
        saveState = SaveState.IDLE,
        errorMessage = null,
    )
}

@HiltViewModel
class PlanEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val navResultBus: NavResultBus,
    private val mediaRegistrar: MediaRegistrar,
) : ViewModel() {

    private val initialPlanId: Long? = savedStateHandle.get<Long>("planId")?.takeIf { it > 0L }

    private val _uiState = MutableStateFlow(PlanEditUiState(isNew = initialPlanId == null))
    val uiState: StateFlow<PlanEditUiState> = _uiState.asStateFlow()

    private var planId: Long? = initialPlanId
    private var loadedDetail: PlanDetail? = null

    /**
     * 串行化写库。
     *
     * 现在只有"保存"一个写入入口，正常不会再并发；保留它是因为用户可能连点保存，
     * 而"创建计划"必须恰好发生一次。
     */
    private val writeMutex = Mutex()

    /**
     * 未保存动作的临时 id 来源（负数）。
     *
     * 动作选择器只把动作加进**内存草稿**，不碰数据库：
     *  - 用户点"不保存"就不该在计划列表里留下一条半成品；
     *  - 组级处方的编辑（改组数、排序、挂演示资源）全部在保存时一并落库，
     *    让"保存"是一次完整、原子的写入，而不是十几次零散写入。
     *
     * 负 id 让 [PlanRepository.savePlanDetail] 自动走插入分支（它以 `id > 0` 区分更新与插入）。
     * 起始值刻意远离 0：Room 的 `autoGenerate` **只对 id == 0 生效**，负值会被当作真实主键写入，
     * 所以这里用一个不可能与任何历史数据撞车的区间，并且保存成功后立刻把草稿换成真实主键
     * （见 [save]）——临时 id 只存在于"首次保存之前"。
     */
    private var nextTempId = -1_000_000L

    init {
        viewModelScope.launch {
            if (initialPlanId != null) {
                val detail = planRepository.getPlanDetail(initialPlanId)
                loadedDetail = detail
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            description = detail.description.orEmpty(),
                            defaultRestSeconds = detail.defaultRestSeconds?.toString().orEmpty(),
                            scheduledDays = detail.scheduledDays,
                            focus = detail.focus,
                            exercises = detail.exercises,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // 动作选择器的结果：订阅方是 ViewModel，生命周期覆盖整个编辑过程
        viewModelScope.launch {
            navResultBus.observe(NavResults.PICKED_EXERCISE_ID)
                .filterIsInstance<Long>()
                .collect { definitionId -> addExerciseToDraft(definitionId) }
        }
    }

    fun onNameChange(value: String) {
        val trimmed = value.take(MAX_NAME_LENGTH)
        _uiState.update {
            it.copy(
                name = trimmed,
                nameError = if (trimmed.isBlank()) NAME_ERROR_EMPTY else null,
            ).markDirty()
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(MAX_DESCRIPTION_LENGTH)).markDirty() }
    }

    fun onRestChange(value: String) {
        _uiState.update {
            it.copy(defaultRestSeconds = value.filter { c -> c.isDigit() }.take(3)).markDirty()
        }
    }

    /** 切换"每周几"：1=周一 … 7=周日，与提醒、日历的排程语义一致 */
    fun onToggleScheduleDay(day: Int) {
        _uiState.update {
            val next = it.scheduledDays.toMutableSet()
            if (!next.add(day)) next.remove(day)
            it.copy(scheduledDays = next).markDirty()
        }
    }

    /** 切换"当日主要训练部位"：自由组合（如 胸+腿），不限制动作选择 */
    fun onToggleFocus(major: MajorGroup) {
        _uiState.update {
            val next = it.focus.toMutableSet()
            if (!next.add(major)) next.remove(major)
            it.copy(focus = next.toList()).markDirty()
        }
    }

    /** 把选中的动作加进内存草稿。**不写数据库** —— 写库只发生在用户点"保存"时。 */
    private suspend fun addExerciseToDraft(definitionId: Long) {
        val exercise = exerciseRepository.getExercise(definitionId)
        if (exercise == null) {
            Timber.w("选择的动作不存在：definitionId=%d", definitionId)
            _uiState.update { it.copy(errorMessage = ERROR_EXERCISE_NOT_FOUND) }
            return
        }
        val sets = exercise.defaultSets.coerceIn(1, 30)
        val item = PlanExerciseItem(
            id = nextTempId--,
            exercise = ExerciseSnapshot(
                definitionId = exercise.id,
                name = exercise.name,
                type = exercise.type,
                isUnilateral = exercise.isUnilateral,
            ),
            // 顺序在保存时按列表位置统一重排，这里给什么都不影响结果
            orderIndex = 0.0,
            supersetTag = null,
            targetSets = sets,
            sets = (0 until sets).map { index ->
                PlannedSet(setIndex = index, targetReps = exercise.defaultReps)
            },
            restSecondsOverride = null,
            notes = null,
            mediaOverride = null,
        )
        _uiState.update {
            it.copy(exercises = it.exercises + item, errorMessage = null).markDirty()
        }
    }

    /** 移除动作：只改草稿。真正的删除发生在保存时（savePlanDetail 会删掉不在列表里的动作） */
    fun onRemoveExercise(planExerciseId: Long) {
        _uiState.update { state ->
            state.copy(exercises = state.exercises.filterNot { it.id == planExerciseId }).markDirty()
        }
    }

    /**
     * 上移/下移。
     *
     * 同时提供按钮与拖拽两种方式：TalkBack 用户无法长按拖拽，
     * 只做拖拽等于把无障碍用户排除在外（PRD NFR-A2）。
     */
    fun onMove(index: Int, delta: Int) {
        val list = _uiState.value.exercises.toMutableList()
        val target = index + delta
        if (target !in list.indices) return

        val moved = list.removeAt(index)
        list.add(target, moved)
        _uiState.update { it.copy(exercises = list).markDirty() }
    }

    fun onUpdateTarget(planExerciseId: Long, sets: Int, reps: String) {
        val normalizedReps = RepsRange.normalize(reps).ifBlank { DEFAULT_REPS }
        val count = sets.coerceIn(1, 30)
        _uiState.update { state ->
            state.copy(
                exercises = state.exercises.map { item ->
                    if (item.id != planExerciseId) {
                        item
                    } else {
                        item.copy(
                            targetSets = count,
                            sets = (0 until count).map { index ->
                                item.sets.getOrNull(index)?.copy(targetReps = normalizedReps)
                                    ?: PlannedSet(setIndex = index, targetReps = normalizedReps)
                            },
                        )
                    }
                },
            ).markDirty()
        }
    }

    /** 关联/移除演示资源：同样只改草稿，保存时随动作一起写库 */
    fun setMedia(planExerciseId: Long, media: MediaRef?) {
        _uiState.update { state ->
            state.copy(
                exercises = state.exercises.map { item ->
                    if (item.id == planExerciseId) item.copy(mediaOverride = media) else item
                },
            ).markDirty()
        }
    }

    /** 用户点了"保存"。成功后自增 [PlanEditUiState.savedToken]，由 UI 出栈。 */
    fun onSaveClick() {
        if (_uiState.value.saveState == SaveState.SAVING) return
        viewModelScope.launch { save() }
    }

    /**
     * 保存草稿。**这是全页唯一的写库入口。**
     *
     * 返回值是"是否真的写进数据库"。失败时留在页面（[SaveState.FAILED] + 错误文案），
     * 绝不静默关闭 —— 保存失败却把页面关掉，是这类界面最严重的失误。
     */
    suspend fun save(): Boolean {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = NAME_ERROR_EMPTY, saveState = SaveState.FAILED) }
            return false
        }
        if (state.saveState == SaveState.SAVING) return false

        _uiState.update { it.copy(saveState = SaveState.SAVING, errorMessage = null) }

        val ok = writeMutex.withLock { writeDraft(state) }

        // 引用已落库：把登记行从"待引用"翻成"已引用"，GC 才不会误收
        if (ok) {
            state.exercises.forEach { item ->
                (item.mediaOverride as? MediaRef.Local)?.let { local ->
                    runCatching { mediaRegistrar.markActive(local.fileName) }
                        .onFailure { Timber.w(it, "标记媒体已引用失败：%s", local.fileName) }
                }
            }
        }

        _uiState.update {
            if (ok) {
                it.copy(
                    saveState = SaveState.IDLE,
                    isDirty = false,
                    errorMessage = null,
                    // 把草稿换成数据库的真实状态。新动作的临时负 id 必须在这里换成真实主键，
                    // 否则下一次保存时 savePlanDetail 会按"id <= 0 即新行"把它们**再插一遍**。
                    exercises = loadedDetail?.exercises ?: it.exercises,
                    savedToken = it.savedToken + 1,
                )
            } else {
                it.copy(saveState = SaveState.FAILED, errorMessage = ERROR_SAVE_FAILED)
            }
        }
        return ok
    }

    private suspend fun writeDraft(state: PlanEditUiState): Boolean {
        val newId = planId ?: planRepository.createPlan(
            name = state.name,
            description = state.description.takeIf { it.isNotBlank() },
            scheduledDays = state.scheduledDays,
            defaultRestSeconds = state.defaultRestSeconds.toIntOrNull(),
            folderId = null,
            focus = state.focus,
        ).getOrNull()?.also {
            planId = it
            _uiState.update { current -> current.copy(isNew = false) }
        }

        if (newId == null) {
            Timber.e("创建计划失败")
            return false
        }

        // 基准取数据库当前值：避免"本地拼装"漏掉我们没在界面上暴露的字段
        // （收藏状态、创建时间、文件夹等）
        val base = loadedDetail?.takeIf { it.id == newId } ?: planRepository.getPlanDetail(newId)
        if (base == null) {
            Timber.e("读取计划详情失败，放弃保存：planId=%d", newId)
            return false
        }

        val saved = planRepository.savePlanDetail(
            base.copy(
                name = state.name,
                description = state.description.takeIf { it.isNotBlank() },
                defaultRestSeconds = state.defaultRestSeconds.toIntOrNull(),
                scheduledDays = state.scheduledDays,
                focus = state.focus,
                // 顺序按列表位置统一重排：orderIndex 上有唯一索引，
                // 沿用旧值在删除/移动后会撞索引
                exercises = state.exercises.mapIndexed { index, item ->
                    item.copy(
                        // 占位用的负 id 必须归零后再落库。Room 的 autoGenerate
                        // **只对 id == 0 生成主键**，传负数会被当成真实主键原样写进去，
                        // 留下负主键的脏数据。
                        id = if (item.id > 0L) item.id else 0L,
                        orderIndex = index.toDouble(),
                    )
                },
            ),
        )
        if (saved.isFailure) {
            Timber.e("保存计划失败：%s", saved.errorOrNull())
            return false
        }

        loadedDetail = planRepository.getPlanDetail(newId) ?: base
        return true
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 500
        const val DEFAULT_REPS = "10"
        const val NAME_ERROR_EMPTY = "计划名称不能为空"
        const val ERROR_SAVE_FAILED = "保存失败，请重试（改动尚未写入）"
        const val ERROR_EXERCISE_NOT_FOUND = "这个动作已不存在，请重新选择"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(
    onNavigateBack: () -> Unit,
    onAddExercise: () -> Unit,
    viewModel: PlanEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    // 当前为哪个动作打开演示资源面板。null 表示面板关闭。
    var mediaSheetFor by remember { mutableStateOf<PlanExerciseItem?>(null) }
    // 演示文件丢失时的"重新关联"面板（与上面同一个组件，入口不同）
    var relinkSheetFor by remember { mutableStateOf<PlanExerciseItem?>(null) }

    val backLabel = stringResource(R.string.common_close)

    // 保存成功 → 出栈。用 token 自增而不是布尔标记：
    // 布尔标记在出栈后还会停留在 true，页面被复用时会导致误出栈。
    LaunchedEffect(state.savedToken) {
        if (state.savedToken > 0L) onNavigateBack()
    }

    /** 退出统一走这里：有未保存的修改才询问，没有就直接走 */
    fun requestExit() {
        if (state.isDirty) showExitDialog = true else onNavigateBack()
    }

    // 系统返回键与左上角返回箭头行为必须一致，否则两种返回方式会产生两种结果
    BackHandler(enabled = state.isDirty && !showExitDialog) { showExitDialog = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                com.fitnesspro.feature.plans.R.string.plan_edit_title_new
                            } else {
                                com.fitnesspro.feature.plans.R.string.plan_edit_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    // 显式保存：不再有任何自动保存。
                    // 没有改动时按钮不可点，用户一眼就知道"现在没什么要存的"。
                    TextButton(
                        onClick = viewModel::onSaveClick,
                        enabled = state.isDirty && state.saveState != SaveState.SAVING,
                    ) {
                        Text(
                            stringResource(
                                if (state.saveState == SaveState.SAVING) {
                                    com.fitnesspro.feature.plans.R.string.plan_edit_saving
                                } else {
                                    com.fitnesspro.feature.plans.R.string.plan_edit_save
                                },
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FitnessTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_name)) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                )
            }

            item(key = "description") {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_description)) },
                    placeholder = {
                        Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_description_hint))
                    },
                    minLines = 2,
                )
            }

            // 每周循环排程："每周一练腿、每周二练背"在这里一次性配置，
            // 首页/今日训练按 weekday 自动出现；日历绑定留给"某天临时调整"。
            item(key = "schedule") {
                Column {
                    Text(
                        stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_schedule),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                    ) {
                        listOf(
                            1 to "一", 2 to "二", 3 to "三", 4 to "四",
                            5 to "五", 6 to "六", 7 to "日",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = value in state.scheduledDays,
                                onClick = { viewModel.onToggleScheduleDay(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item(key = "focus") {
                Column {
                    Text(
                        stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_focus),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_focus_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                    ) {
                        MajorGroup.entries.forEach { major ->
                            FilterChip(
                                selected = major in state.focus,
                                onClick = { viewModel.onToggleFocus(major) },
                                label = { Text(major.displayName) },
                            )
                        }
                    }
                }
            }

            item(key = "rest") {
                OutlinedTextField(
                    value = state.defaultRestSeconds,
                    onValueChange = viewModel::onRestChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_default_rest)) },
                    singleLine = true,
                )
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = onAddExercise,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_detail_add_exercise))
                }
            }

            // 写入失败必须可见：否则用户会以为改动已经存上了
            state.errorMessage?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (state.exercises.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_no_exercises_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.exercises, key = { it.id }) { item ->
                val index = state.exercises.indexOf(item)
                EditableExerciseCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.exercises.lastIndex,
                    onMoveUp = { viewModel.onMove(index, -1) },
                    onMoveDown = { viewModel.onMove(index, 1) },
                    onRemove = { viewModel.onRemoveExercise(item.id) },
                    onUpdateTarget = { sets, reps -> viewModel.onUpdateTarget(item.id, sets, reps) },
                    onOpenMedia = { mediaSheetFor = item },
                    onRelinkMedia = { relinkSheetFor = item },
                )
            }

            item(key = "bottom-space") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
        }
    }

    if (showExitDialog) {
        ExitWithoutSavingDialog(
            onSaveAndExit = {
                showExitDialog = false
                // 保存成功会自增 savedToken，由上面的 LaunchedEffect 负责出栈；
                // 失败则留在页面并显示原因
                viewModel.onSaveClick()
            },
            onDiscard = {
                showExitDialog = false
                onNavigateBack()
            },
            onStay = { showExitDialog = false },
        )
    }

    // 演示资源面板。挂在编辑页而不是每条动作里：ModalBottomSheet 需要稳定的宿主，
    // 放在 LazyColumn 的 item 内会随滚动被回收，导致面板在滑出屏幕时消失。
    mediaSheetFor?.let { item ->
        MediaAttachSheet(
            title = item.exercise.name,
            current = item.mediaOverride,
            onDismiss = { mediaSheetFor = null },
            onAttach = { media -> viewModel.setMedia(item.id, media) },
            onRemove = { viewModel.setMedia(item.id, null) },
        )
    }
    relinkSheetFor?.let { item ->
        MediaAttachSheet(
            title = item.exercise.name,
            current = item.mediaOverride,
            onDismiss = { relinkSheetFor = null },
            onAttach = { media -> viewModel.setMedia(item.id, media) },
            onRemove = { viewModel.setMedia(item.id, null) },
        )
    }
}

/**
 * 退出前的询问：保存 / 不保存 / 继续编辑。
 *
 * 三个选项都必须给全。只有"放弃/取消"两个选项时，用户想保存就只能先取消、
 * 找到保存按钮、再返回一次 —— 把一条正常路径拆成三步。
 */
@Composable
private fun ExitWithoutSavingDialog(
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_exit_title)) },
        text = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_exit_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_exit_save))
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_exit_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onStay) {
                    Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_continue))
                }
            }
        },
    )
}

@Composable
private fun EditableExerciseCard(
    item: PlanExerciseItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onUpdateTarget: (Int, String) -> Unit,
    onOpenMedia: () -> Unit,
    onRelinkMedia: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 计划里挂的演示资源此前无处可见，用户会以为"存了没生效"
                ExerciseMedia(
                    media = item.mediaOverride,
                    size = MEDIA_SIZE_SMALL,
                    onRelink = onRelinkMedia,
                    onClick = onOpenMedia,
                )
                Spacer(Modifier.width(FitnessTheme.spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    LabelChip(stringResource(item.exercise.type.labelRes()))
                }

                // 无障碍友好的排序方式：拖拽之外必须提供按钮
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_move_down),
                    )
                }
                IconButton(onClick = onOpenMedia) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = stringResource(
                            com.fitnesspro.feature.plans.R.string.cd_media_attach,
                        ),
                        tint = if (item.mediaOverride != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_remove_exercise),
                    )
                }
            }

            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                // 组数：本地草稿态。之前的实现每次重组都从 state 回填，
                // "删空"会在一帧内被旧值覆盖，导致 3 删不掉、只能输 3 或 30
                var setsDraft by remember(item.id) { mutableStateOf(item.targetSets.toString()) }
                OutlinedTextField(
                    value = setsDraft,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }.take(2)
                        setsDraft = filtered
                        // 空串/无效输入不回写（保留用户"删空重输"的中间态），
                        // 保存时以 state 的最近有效值为准
                        filtered.toIntOrNull()?.let { v ->
                            onUpdateTarget(
                                v.coerceIn(1, 30),
                                RepsRange.parse(item.sets.firstOrNull()?.targetReps).display(),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_sets_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = RepsRange.parse(item.sets.firstOrNull()?.targetReps).display(),
                    onValueChange = { raw ->
                        onUpdateTarget(item.targetSets, RepsRange.normalize(raw))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_reps_label)) },
                    placeholder = { Text(stringResource(com.fitnesspro.feature.plans.R.string.plan_edit_reps_hint)) },
                    singleLine = true,
                )
            }
        }
    }
}


