package com.fitnesspro.feature.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.data.media.MediaRegistrar
import com.fitnesspro.core.designsystem.theme.ExerciseColorPalette
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.colorKeyOf
import com.fitnesspro.core.designsystem.theme.muscleColor
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.mediaui.ExerciseMedia
import com.fitnesspro.core.mediaui.MediaAttachSheet
import com.fitnesspro.core.mediaui.MEDIA_SIZE_LARGE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// 本模块自己的 R（namespace = com.fitnesspro.feature.exercises）
import com.fitnesspro.feature.exercises.R
// 设计系统字符串（关闭/保存）显式别名，避免误用本模块的 R
import com.fitnesspro.core.designsystem.R as DesignSystemR

/**
 * 自定义动作的新建/编辑。
 *
 * ## 为什么必须有这个页面
 *
 * 动作库的"新建动作"此前跳到的是**选择器**，而选择器只能从既有动作里挑，
 * [ExerciseRepository.createCustomExercise] 一直没有调用方 ——
 * 也就是"新建自定义动作"这个功能实际上不存在，只有入口。
 *
 * ## 保存语义
 *
 * 与计划编辑页同一条原则：**什么时候写库由用户决定**。
 * 右上角"保存"是唯一的写库入口；有未保存的修改时退出会询问。
 * 编辑既有动作时以加载到的动作为底，只覆盖本页暴露的字段 ——
 * 媒体引用、收藏状态、别名不在表单里，被覆盖掉就是数据损坏。
 */
data class ExerciseEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = false,
    val name: String = "",
    val nameError: String? = null,
    val type: ExerciseType = ExerciseType.WEIGHT_REPS,
    val primaryMuscle: MuscleGroup = MuscleGroup.CHEST,
    /** 大肌群/类别（v2 分类体系第一层） */
    val majorGroup: MajorGroup = MajorGroup.CHEST,
    /** 细分肌群（第二层，须属于 majorGroup） */
    val subGroup: String? = null,
    val equipment: Equipment = Equipment.BARBELL,
    val isUnilateral: Boolean = false,
    val defaultSets: String = "3",
    val defaultReps: String = "10",
    val defaultRestSeconds: String = "90",
    val instructions: String = "",
    /** 动作演示资源。与计划侧同一语义：只在"保存"时写库 */
    val media: MediaRef? = null,
    /** 自定义身份色（hex）；null = 跟随主肌群色系 */
    val colorKey: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /** 保存成功次数。自增表示"可以安全离开"，由 UI 消费后出栈 */
    val savedToken: Long = 0L,
) {
    /** 记录一次本地改动并清掉上一次的错误。所有修改入口都必须走这里 */
    fun markDirty(): ExerciseEditUiState = copy(
        isDirty = true,
        errorMessage = null,
    )
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val mediaRegistrar: MediaRegistrar,
) : ViewModel() {

    /** -1 表示新建；>0 表示编辑既有动作 */
    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: -1L

    /** 编辑模式下用于保留界面上没有暴露的字段（媒体、收藏、别名等） */
    private var loaded: Exercise? = null

    private val _uiState = MutableStateFlow(
        ExerciseEditUiState(isLoading = exerciseId > 0L, isNew = exerciseId <= 0L),
    )
    val uiState: StateFlow<ExerciseEditUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId > 0L) {
            viewModelScope.launch {
                val exercise = exerciseRepository.getExercise(exerciseId)
                if (exercise == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ERROR_NOT_FOUND) }
                    return@launch
                }
                loaded = exercise
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = exercise.name,
                        type = exercise.type,
                        primaryMuscle = exercise.primaryMuscle,
                        majorGroup = exercise.majorGroup,
                        subGroup = exercise.subGroup,
                        equipment = exercise.equipment,
                        isUnilateral = exercise.isUnilateral,
                        defaultSets = exercise.defaultSets.toString(),
                        defaultReps = exercise.defaultReps.orEmpty(),
                        defaultRestSeconds = exercise.defaultRestSeconds.toString(),
                        instructions = exercise.instructions.orEmpty(),
                        media = exercise.media,
                        colorKey = exercise.colorKey,
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        val trimmed = value.take(MAX_NAME_LENGTH)
        _uiState.update { it.copy(name = trimmed, nameError = null).markDirty() }
    }

    fun onTypeChange(value: ExerciseType) {
        _uiState.update { it.copy(type = value).markDirty() }
    }

    fun onPrimaryMuscleChange(value: MuscleGroup) {
        _uiState.update { it.copy(primaryMuscle = value).markDirty() }
    }

    /** 选择大肌群：细分默认取该大肌群第一项，primaryMuscle 随细分自动推导（驱动配色/分区） */
    fun onMajorGroupChange(value: MajorGroup) {
        if (_uiState.value.majorGroup == value) return
        val sub = ExerciseTaxonomy.subGroupsOf(value).firstOrNull()
        _uiState.update {
            it.copy(
                majorGroup = value,
                subGroup = sub,
                primaryMuscle = sub?.let { ExerciseTaxonomy.primaryMuscleOf(value, it) } ?: value.colorOf,
            ).markDirty()
        }
    }

    fun onSubGroupChange(value: String?) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                subGroup = value,
                primaryMuscle = value?.let { ExerciseTaxonomy.primaryMuscleOf(state.majorGroup, it) }
                    ?: state.primaryMuscle,
            ).markDirty()
        }
    }

    fun onEquipmentChange(value: Equipment) {
        _uiState.update { it.copy(equipment = value).markDirty() }
    }

    fun onUnilateralChange(value: Boolean) {
        _uiState.update { it.copy(isUnilateral = value).markDirty() }
    }

    fun onSetsChange(value: String) {
        _uiState.update {
            it.copy(defaultSets = value.filter { c -> c.isDigit() }.take(2)).markDirty()
        }
    }

    fun onRepsChange(value: String) {
        _uiState.update { it.copy(defaultReps = value.take(MAX_REPS_LENGTH)).markDirty() }
    }

    fun onRestChange(value: String) {
        _uiState.update {
            it.copy(defaultRestSeconds = value.filter { c -> c.isDigit() }.take(3)).markDirty()
        }
    }

    fun onInstructionsChange(value: String) {
        _uiState.update { it.copy(instructions = value.take(MAX_INSTRUCTIONS_LENGTH)).markDirty() }
    }

    /** 关联/移除演示资源：与计划侧一致，只改草稿，保存时才写库 */
    fun setMedia(media: MediaRef?) {
        _uiState.update { it.copy(media = media).markDirty() }
    }

    /** 自定义身份色：null 表示"跟随肌群" */
    fun onColorKeyChange(value: String?) {
        _uiState.update { it.copy(colorKey = value).markDirty() }
    }

    fun onSaveClick() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch { save() }
    }

    fun onConsumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 保存。**返回值是"是否真的写进了数据库"** —— 失败必须留在页面并说明原因。
     */
    suspend fun save(): Boolean {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(nameError = NAME_ERROR_EMPTY, errorMessage = NAME_ERROR_EMPTY) }
            return false
        }
        if (state.isSaving) return false

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        val outcome = if (exerciseId > 0L) {
            val base = loaded
            if (base == null) {
                Timber.e("编辑动作失败：原始动作不存在 exerciseId=%d", exerciseId)
                _uiState.update { it.copy(isSaving = false, errorMessage = ERROR_SAVE_FAILED) }
                return false
            }
            // 以加载到的动作为底，只覆盖本页暴露的字段
            exerciseRepository.updateExercise(
                base.copy(
                    name = name,
                    type = state.type,
                    primaryMuscle = state.primaryMuscle,
                    majorGroup = state.majorGroup,
                    subGroup = state.subGroup,
                    equipment = state.equipment,
                    isUnilateral = state.isUnilateral,
                    defaultSets = state.defaultSets.toIntOrNull()?.coerceIn(1, 30) ?: DEFAULT_SETS,
                    defaultReps = state.defaultReps.takeIf { it.isNotBlank() },
                    defaultRestSeconds = state.defaultRestSeconds.toIntOrNull()
                        ?.coerceIn(0, 900) ?: DEFAULT_REST,
                    instructions = state.instructions.takeIf { it.isNotBlank() },
                    media = state.media,
                    colorKey = state.colorKey,
                ),
            )
        } else {
            val created = exerciseRepository.createCustomExercise(
                name = name,
                type = state.type,
                primaryMuscle = state.primaryMuscle,
                equipment = state.equipment,
                isUnilateral = state.isUnilateral,
                defaultSets = state.defaultSets.toIntOrNull()?.coerceIn(1, 30) ?: DEFAULT_SETS,
                defaultReps = state.defaultReps.takeIf { it.isNotBlank() },
                defaultRestSeconds = state.defaultRestSeconds.toIntOrNull()
                    ?.coerceIn(0, 900) ?: DEFAULT_REST,
                instructions = state.instructions.takeIf { it.isNotBlank() },
                colorKey = state.colorKey,
                majorGroup = state.majorGroup,
                subGroup = state.subGroup,
            )
            // 新建接口没有 media 入参，创建成功后再补一次关联（省得改接口签名）
            val newId = created.getOrNull()
            if (newId != null && state.media != null) {
                exerciseRepository.setExerciseMedia(newId, state.media)
            }
            created
        }

        return if (outcome.isSuccess) {
            // 引用已写入：把登记行从"待引用"翻成"已引用"，GC 才不会误收
            (state.media as? MediaRef.Local)?.let { local ->
                runCatching { mediaRegistrar.markActive(local.fileName) }
                    .onFailure { Timber.w(it, "标记媒体已引用失败：%s", local.fileName) }
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isDirty = false,
                    nameError = null,
                    savedToken = it.savedToken + 1,
                )
            }
            true
        } else {
            val error = outcome.errorOrNull()
            Timber.e("保存动作失败：%s", error)
            val message = when (error) {
                is DomainError.DuplicateName -> ERROR_NAME_DUPLICATE
                else -> ERROR_SAVE_FAILED
            }
            _uiState.update { it.copy(isSaving = false, errorMessage = message) }
            false
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_INSTRUCTIONS_LENGTH = 500
        const val MAX_REPS_LENGTH = 12
        const val DEFAULT_SETS = 3
        const val DEFAULT_REST = 90
        const val NAME_ERROR_EMPTY = "请输入动作名称"
        const val ERROR_NAME_DUPLICATE = "已有同名动作，请换一个名字"
        const val ERROR_SAVE_FAILED = "保存失败，请重试（改动尚未写入）"
        const val ERROR_NOT_FOUND = "这个动作已不存在"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功 → 出栈。用 token 自增而不是布尔标记：布尔标记在出栈后仍是 true，
    // 同一目的地再次进入被复用时会导致误出栈
    LaunchedEffect(state.savedToken) {
        if (state.savedToken > 0L) onNavigateBack()
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onConsumeError()
    }

    /** 退出统一走这里：有未保存的修改才询问，没有就直接走 */
    fun requestExit() {
        if (state.isDirty) showExitDialog = true else onNavigateBack()
    }

    BackHandler(enabled = state.isDirty && !showExitDialog) { showExitDialog = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.exercise_edit_title_new
                            } else {
                                R.string.exercise_edit_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(DesignSystemR.string.common_close),
                        )
                    }
                },
                actions = {
                    // 显式保存：没有改动时不可点，一眼就知道"现在没什么要存的"
                    TextButton(
                        onClick = viewModel::onSaveClick,
                        enabled = state.isDirty && !state.isSaving,
                    ) {
                        Text(
                            stringResource(
                                if (state.isSaving) {
                                    R.string.exercise_edit_saving
                                } else {
                                    R.string.exercise_edit_save
                                },
                            ),
                        )
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FitnessTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            // 演示资源：此前动作侧完全没有绑定入口（ExercisesRepository.setExerciseMedia
            // 一直没有被调用），动作演示只能来自种子数据或备份导入。
            item(key = "media") {
                Column {
                    Text(
                        stringResource(R.string.exercise_edit_media),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.m),
                    ) {
                        ExerciseMedia(
                            media = state.media,
                            size = MEDIA_SIZE_LARGE,
                            onRelink = { showMediaSheet = true },
                            onClick = { showMediaSheet = true },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (state.media == null) {
                                        R.string.exercise_edit_media_empty
                                    } else {
                                        R.string.exercise_edit_media_bound
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(FitnessTheme.spacing.xs))
                            OutlinedButton(onClick = { showMediaSheet = true }) {
                                Text(stringResource(R.string.exercise_edit_media_action))
                            }
                        }
                    }
                }
            }

            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.exercise_edit_name)) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                )
            }

            item(key = "type") {
                ChipRow(
                    label = stringResource(R.string.exercise_edit_type),
                    options = ExerciseType.entries,
                    selected = state.type,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = viewModel::onTypeChange,
                )
            }

            item(key = "majorGroup") {
                ChipRow(
                    label = stringResource(R.string.exercise_edit_major_group),
                    options = MajorGroup.entries,
                    selected = state.majorGroup,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::onMajorGroupChange,
                )
            }

            item(key = "subGroup") {
                ChipRow(
                    label = stringResource(R.string.exercise_edit_sub_group),
                    options = ExerciseTaxonomy.subGroupsOf(state.majorGroup),
                    selected = state.subGroup,
                    optionLabel = { it },
                    onSelect = viewModel::onSubGroupChange,
                )
            }

            item(key = "equipment") {
                ChipRow(
                    label = stringResource(R.string.exercise_edit_equipment),
                    options = Equipment.entries,
                    selected = state.equipment,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = viewModel::onEquipmentChange,
                )
            }

            item(key = "color") {
                ColorPaletteRow(
                    selectedKey = state.colorKey,
                    fallbackColor = muscleColor(state.primaryMuscle),
                    onSelect = viewModel::onColorKeyChange,
                )
            }

            item(key = "unilateral") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.exercise_edit_unilateral),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.exercise_edit_unilateral_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(onCheckedChange = viewModel::onUnilateralChange, checked = state.isUnilateral)
                }
            }

            item(key = "numbers") {
                Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                    OutlinedTextField(
                        value = state.defaultSets,
                        onValueChange = viewModel::onSetsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.exercise_edit_sets)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.defaultReps,
                        onValueChange = viewModel::onRepsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.exercise_edit_reps)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.defaultRestSeconds,
                        onValueChange = viewModel::onRestChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.exercise_edit_rest)) },
                        singleLine = true,
                    )
                }
            }

            item(key = "instructions") {
                OutlinedTextField(
                    value = state.instructions,
                    onValueChange = viewModel::onInstructionsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.exercise_edit_instructions)) },
                    minLines = 3,
                )
            }

            state.errorMessage?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
        }
    }

    if (showExitDialog) {
        ExitExerciseEditDialog(
            onSaveAndExit = {
                showExitDialog = false
                // 保存成功会自增 savedToken，由上面的 LaunchedEffect 负责出栈；
                // 失败则留在页面（原因由 Snackbar 展示）
                viewModel.onSaveClick()
            },
            onDiscard = {
                showExitDialog = false
                onNavigateBack()
            },
            onStay = { showExitDialog = false },
        )
    }

    // 演示资源面板：与计划编辑页同一个组件，保证两侧行为一致
    if (showMediaSheet) {
        MediaAttachSheet(
            title = stringResource(R.string.exercise_edit_media),
            current = state.media,
            onDismiss = { showMediaSheet = false },
            onAttach = { media ->
                viewModel.setMedia(media)
                showMediaSheet = false
            },
            onRemove = {
                viewModel.setMedia(null)
                showMediaSheet = false
            },
        )
    }
}

/**
 * 横向滚动的单选行。肌群有 16 个选项，纵向列表会把表单撑得不可用。
 *
 * [optionLabel] 由调用方提供（返回 string resource id）：泛型 T 上无法解析
 * `labelRes()` 扩展函数，把"怎么取文案"留在调用点是最省事的解法。
 */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
            // 枚举的 toString() == name，跨重组稳定且互不相同，适合做 key
            items(options, key = { it.toString() }) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

/**
 * 主题色选择：28 色按 7 个色相家族分行展示（每行一族、由浅到深），
 * 首个选项"跟随肌群"回退为 [muscleColor]（随主肌群联动）。
 */
@Composable
private fun ColorPaletteRow(
    selectedKey: String?,
    fallbackColor: Color,
    onSelect: (String?) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.exercise_edit_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        // "跟随肌群"选项：直径略大的空心圆，让"未自定义"这个状态可辨识
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            val followSelected = selectedKey == null
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(fallbackColor)
                    .border(
                        width = if (followSelected) 3.dp else 1.dp,
                        color = if (followSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable(onClick = { onSelect(null) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.exercise_edit_color_follow),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                stringResource(R.string.exercise_edit_color_follow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FitnessTheme.spacing.s))
        // 色板按 4 个一组切行：同一行的 4 个色正好是"同色相、由浅到深"
        ExerciseColorPalette.chunked(4).forEach { family ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
                modifier = Modifier.padding(vertical = FitnessTheme.spacing.xs),
            ) {
                family.forEach { color ->
                    val key = colorKeyOf(color)
                    val selected = selectedKey == key
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable(onClick = { onSelect(key) }),
                    )
                }
            }
        }
    }
}

/**
 * 退出前的询问：保存 / 不保存 / 继续编辑。
 * 三个选项都必须给全 —— 只给"放弃/取消"时，想保存的用户得先取消、找按钮、再返回一次。
 */
@Composable
private fun ExitExerciseEditDialog(
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.exercise_edit_exit_title)) },
        text = { Text(stringResource(R.string.exercise_edit_exit_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(R.string.exercise_edit_exit_save))
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        stringResource(R.string.exercise_edit_exit_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onStay) {
                    Text(stringResource(R.string.exercise_edit_exit_stay))
                }
            }
        },
    )
}
