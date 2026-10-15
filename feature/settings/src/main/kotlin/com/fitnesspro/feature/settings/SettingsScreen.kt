package com.fitnesspro.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import com.fitnesspro.core.designsystem.component.UserAvatar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.media.MaintenanceRepository
import com.fitnesspro.core.designsystem.asString
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.formatBytes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.themePresetPreviewColors
import com.fitnesspro.core.domain.reminder.WorkoutReminderScheduler
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.CustomThemeSlot
import com.fitnesspro.core.model.DistanceUnit
import com.fitnesspro.core.model.OneRmFormula
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.ThemeMode
import com.fitnesspro.core.model.ThemePreset
import com.fitnesspro.core.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnesspro.core.designsystem.R as DesignSystemR
import com.fitnesspro.feature.settings.R

data class SettingsUiState(
    val nickname: String = "",
    val avatarUri: String? = null,
    val isLoading: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val defaultRestSeconds: Int = 90,
    val autoStartRestTimer: Boolean = true,
    val restSoundEnabled: Boolean = true,
    val restVibrationEnabled: Boolean = true,
    val restTtsEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val autoFillFromLastSession: Boolean = true,
    val weightStepKg: Double = 2.5,
    val showRpe: Boolean = false,
    val includeWarmupInVolume: Boolean = false,
    val oneRmFormula: OneRmFormula = OneRmFormula.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePreset: ThemePreset = ThemePreset.BENTO_SOFT,
    val customAppBarColor: Long? = null,
    val customAccentColor: Long? = null,
    val customContentColor: Long? = null,
    val customOtherColor: Long? = null,
    val customFontColor: Long? = null,
    val firstDayOfWeek: Int = 1,
    val planSortOrder: PlanSortOrder = PlanSortOrder.RECENTLY_EDITED,
val wallpaperUri: String? = null,
val wallpaperOpacity: Float = 0.35f,
    val workoutReminderEnabled: Boolean = false,
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    /** 代谢档案（BMR/TDEE）；身高体重取身体记录最新值 */
    val age: Int? = null,
    val gender: String? = null,
    val activityLevel: String = "MODERATE",
    val crashReportingEnabled: Boolean = false,
    val mediaFileCount: Int = 0,
    val mediaTotalBytes: Long = 0L,
    val mediaOrphanCount: Int = 0,
    val mediaOrphanBytes: Long = 0L,
    val mediaScanning: Boolean = false,
    val lastScannedAt: Long? = null,
    val message: String? = null,
)

/** 壁纸图片固定复制为该文件名（filesDir 下） */
private const val WALLPAPER_FILE = "wallpaper.jpg"

/** 头像图片固定复制为该文件名（filesDir 下），分享卡片从本地读取 */
private const val AVATAR_FILE = "avatar.jpg"
/** 256×256 正方形缩略图：设置头像时同步生成，分享卡片直接引用 */
private const val AVATAR_THUMB_FILE = "avatar_thumb.jpg"
private const val AVATAR_THUMB_SIZE = 256

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val maintenance: MaintenanceRepository,
    private val reminderScheduler: WorkoutReminderScheduler,
) : ViewModel() {

    private val localUi = MutableStateFlow(LocalUi())
    private val mediaStats = maintenance.stats

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        localUi,
        mediaStats,
    ) { user, local, media ->
        SettingsUiState(
            isLoading = false,
            weightUnit = user.weightUnit,
            distanceUnit = user.distanceUnit,
            defaultRestSeconds = user.defaultRestSeconds,
            autoStartRestTimer = user.autoStartRestTimer,
            restSoundEnabled = user.restSoundEnabled,
            restVibrationEnabled = user.restVibrationEnabled,
            restTtsEnabled = user.restTtsEnabled,
            keepScreenOn = user.keepScreenOnDuringWorkout,
            autoFillFromLastSession = user.autoFillFromLastSession,
            weightStepKg = user.weightStepKg,
            showRpe = user.showRpe,
            includeWarmupInVolume = user.includeWarmupInVolume,
            oneRmFormula = user.oneRmFormula,
            themeMode = user.themeMode,
            themePreset = user.themePreset,
            customAppBarColor = user.customAppBarColor,
            customAccentColor = user.customAccentColor,
            customContentColor = user.customContentColor,
            customOtherColor = user.customOtherColor,
            customFontColor = user.customFontColor,
            firstDayOfWeek = user.firstDayOfWeek,
            planSortOrder = user.planSortOrder,
            wallpaperUri = user.wallpaperUri,
            wallpaperOpacity = user.wallpaperOpacity,
            nickname = user.nickname,
            avatarUri = user.avatarUri,
            workoutReminderEnabled = user.workoutReminderEnabled,
            reminderDays = user.workoutReminderDays,
            reminderHour = user.workoutReminderHour,
            reminderMinute = user.workoutReminderMinute,
            age = user.age,
            gender = user.gender,
            activityLevel = user.activityLevel,
            crashReportingEnabled = user.crashReportingEnabled,
            mediaFileCount = media.fileCount,
            mediaTotalBytes = media.totalBytes,
            mediaOrphanCount = media.orphanCount,
            mediaOrphanBytes = media.orphanBytes,
            mediaScanning = media.isScanning,
            lastScannedAt = media.lastScannedAt,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { maintenance.refreshCached() }
    }

    // ---- 单位 ----
    fun setWeightUnit(unit: WeightUnit) = launch { settings.setWeightUnit(unit) }
    fun setDistanceUnit(unit: DistanceUnit) = launch { settings.setDistanceUnit(unit) }

    // ---- 训练 ----
    fun setDefaultRest(seconds: Int) = launch {
        settings.setDefaultRestSeconds(seconds.coerceIn(0, 900))
    }

    fun setAutoStartRest(enabled: Boolean) = launch { settings.setAutoStartRestTimer(enabled) }
    fun setRestSound(enabled: Boolean) = launch { settings.setRestSoundEnabled(enabled) }
    fun setRestVibration(enabled: Boolean) = launch { settings.setRestVibrationEnabled(enabled) }
    fun setRestTts(enabled: Boolean) = launch { settings.setRestTtsEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = launch { settings.setKeepScreenOn(enabled) }
    fun setAutoFill(enabled: Boolean) = launch { settings.setAutoFillFromLastSession(enabled) }
    fun setWeightStep(step: Double) = launch { settings.setWeightStepKg(step) }
    fun setShowRpe(enabled: Boolean) = launch { settings.setShowRpe(enabled) }
    fun setIncludeWarmup(enabled: Boolean) = launch { settings.setIncludeWarmupInVolume(enabled) }
    fun setOneRmFormula(formula: OneRmFormula) = launch { settings.setOneRmFormula(formula) }

    // ---- 显示 ----
    fun setTheme(mode: ThemeMode) = launch { settings.setThemeMode(mode) }
    fun setThemePreset(preset: ThemePreset) = launch { settings.setThemePreset(preset) }

    fun setCustomColor(slot: CustomThemeSlot, argb: Long?) =
        launch { settings.setCustomThemeColor(slot, argb) }

    fun clearCustomColors() = launch {
        CustomThemeSlot.entries.forEach { settings.setCustomThemeColor(it, null) }
    }

    fun setFirstDayOfWeek(day: Int) = launch { settings.setFirstDayOfWeek(day) }
    fun setPlanSort(order: PlanSortOrder) = launch { settings.setPlanSortOrder(order) }

    fun setWallpaper(fileName: String?) = launch { settings.setWallpaper(fileName) }
    fun setWallpaperOpacity(alpha: Float) = launch { settings.setWallpaperOpacity(alpha) }

    // ---- 个人资料（分享卡片用） ----
    fun setNickname(value: String) = launch { settings.setNickname(value) }

    fun setAge(value: Int?) = launch { settings.setAge(value?.takeIf { it in 10..100 }) }
    fun setGender(value: String?) = launch { settings.setGender(value) }
    fun setActivityLevel(value: String) = launch { settings.setActivityLevel(value) }
    fun setAvatar(fileName: String?) = launch { settings.setAvatarUri(fileName) }

    // ---- 通知 ----
    fun setReminderEnabled(enabled: Boolean) = launch {
        settings.setWorkoutReminder(
            enabled = enabled,
            days = uiState.value.reminderDays,
            hour = uiState.value.reminderHour,
            minute = uiState.value.reminderMinute,
        )
        reminderScheduler.reschedule()
    }

    fun toggleReminderDay(day: Int) = launch {
        val current = uiState.value.reminderDays.toMutableSet()
        if (!current.add(day)) current.remove(day)
        settings.setWorkoutReminder(
            enabled = uiState.value.workoutReminderEnabled,
            days = current,
            hour = uiState.value.reminderHour,
            minute = uiState.value.reminderMinute,
        )
        reminderScheduler.reschedule()
    }

    fun setReminderTime(hour: Int, minute: Int) = launch {
        settings.setWorkoutReminder(
            enabled = uiState.value.workoutReminderEnabled,
            days = uiState.value.reminderDays,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
        )
        reminderScheduler.reschedule()
    }

    // ---- 隐私 ----
    fun setCrashReporting(enabled: Boolean) = launch { settings.setCrashReportingEnabled(enabled) }

    // ---- 媒体维护 ----
    fun rescan() = launch {
        maintenance.scanNow()
        publish("扫描完成")
    }

    fun purgeOrphans() = launch {
        val freed = maintenance.purgeOrphans()
        publish(
            if (freed > 0) "已释放 ${freed / 1024 / 1024} MB" else "没有可清理的文件"
        )
    }

    fun consumeMessage() {
        localUi.value = localUi.value.copy(message = null)
    }

    /**
     * 清空全部数据。
     *
     * 顺序很重要：先清数据库，再删媒体文件。
     * 反过来的话，如果删完媒体、清库时崩溃，用户会看到一堆"文件已丢失"的演示引用；
     * 而按这个顺序最坏情况只是留下一些孤立文件，GC 会处理。
     */
    fun clearAllData() = launch {
        maintenance.clearAllData()
        publish("已清空所有数据")
    }

    private fun publish(text: String) {
        localUi.value = localUi.value.copy(message = text)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class LocalUi(val message: String? = null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName
    var batteryOptimized by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(packageName)) }
    var showBatteryTip by remember { mutableStateOf(false) }

    // 壁纸：选图复制到私有目录（filesDir/wallpaper.jpg），主界面在主题之下渲染它
    val wallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val target = java.io.File(context.filesDir, WALLPAPER_FILE)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess {
                viewModel.setWallpaper(WALLPAPER_FILE)
            }
        }
    }

    // 头像：与壁纸同一套"复制到私有目录"的做法。
    // 直接存原图 avatar.jpg（读取端 AvatarDecoder 自带 EXIF 修正 + 降采样），
    // 缩略图仅从本地原图生成、作兼容保留；不再从 content:// 临时 URI 二次解码，
    // 避免临时 URI 解码失败/白图导致分享卡片头像空白
    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val original = java.io.File(context.filesDir, AVATAR_FILE)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    original.outputStream().use { output -> input.copyTo(output) }
                }
                // 缩略图从本地原图生成（不走 content URI），保证与最终显示同源
                val decoded = com.fitnesspro.core.designsystem.component.AvatarDecoder
                    .decode(context, AVATAR_FILE, AVATAR_THUMB_SIZE)
                if (decoded != null) {
                    val thumb = android.media.ThumbnailUtils.extractThumbnail(
                        decoded,
                        AVATAR_THUMB_SIZE,
                        AVATAR_THUMB_SIZE,
                    )
                    java.io.File(context.filesDir, AVATAR_THUMB_FILE).outputStream().use { output ->
                        thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
                    }
                }
                // 无论缩略图生成与否，主显示路径始终是原图 avatar.jpg
                viewModel.setAvatar(AVATAR_FILE)
            }
        }
    }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }

    var showClearAll by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var clearAllInput by remember { mutableStateOf("") }
    /** 正在编辑的自定义主题颜色槽位；null = 无弹窗 */
    var editingSlot by remember { mutableStateOf<CustomThemeSlot?>(null) }

    // 通知权限：被拒后不再反复弹系统框，只提供跳设置入口
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setReminderEnabled(true)
        else viewModel.setReminderEnabled(false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 设置页经任意 tab 右上角齿轮进入，属二级页面：放返回键
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = FitnessTheme.spacing.s),
        ) {
            // ---------------- 个人资料 ----------------
            item(key = "profile-header") { GroupHeader(stringResource(R.string.settings_group_profile)) }
            item(key = "avatar") {
                // 头像行内直接预览：选完立刻能看到效果，避免"到底有没有保存成功"的疑惑
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            avatarLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        }
                        .padding(
                            horizontal = FitnessTheme.spacing.m,
                            vertical = FitnessTheme.spacing.m,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        nickname = state.nickname.ifBlank { "健身者" },
                        avatarUri = state.avatarUri,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(FitnessTheme.spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_avatar),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                if (state.avatarUri != null) R.string.settings_avatar_set
                                else R.string.settings_avatar_unset,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 已设置头像时提供移除入口：清空存储并删除本地文件，回到首字母圆标
                if (state.avatarUri != null) {
                    TextButton(
                        onClick = {
                            java.io.File(context.filesDir, AVATAR_FILE).delete()
                            java.io.File(context.filesDir, AVATAR_THUMB_FILE).delete()
                            viewModel.setAvatar(null)
                        },
                        modifier = Modifier.padding(start = FitnessTheme.spacing.xl),
                    ) {
                        Text(
                            stringResource(R.string.settings_avatar_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item(key = "nickname") {
                ClickableRow(
                    title = stringResource(R.string.settings_nickname),
                    subtitle = state.nickname.ifBlank {
                        stringResource(R.string.settings_nickname_unset)
                    },
                    onClick = {
                        nicknameInput = state.nickname
                        showNicknameDialog = true
                    },
                )
            }
            item(key = "metabolic-age") {
                var showAgeDialog by remember { mutableStateOf(false) }
                var ageInput by remember { mutableStateOf("") }
                ClickableRow(
                    title = stringResource(R.string.settings_age),
                    subtitle = state.age?.let { stringResource(R.string.settings_age_value, it) }
                        ?: stringResource(R.string.settings_unset),
                    onClick = {
                        ageInput = state.age?.toString().orEmpty()
                        showAgeDialog = true
                    },
                )
                if (showAgeDialog) {
                    AlertDialog(
                        onDismissRequest = { showAgeDialog = false },
                        title = { Text(stringResource(R.string.settings_age)) },
                        text = {
                            OutlinedTextField(
                                value = ageInput,
                                onValueChange = { ageInput = it.filter { c -> c.isDigit() }.take(3) },
                                placeholder = { Text(stringResource(R.string.settings_age_hint)) },
                                singleLine = true,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.setAge(ageInput.toIntOrNull())
                                    showAgeDialog = false
                                },
                            ) { Text(stringResource(android.R.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAgeDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                    )
                }
            }
            item(key = "metabolic-gender") {
                Column(Modifier.padding(horizontal = FitnessTheme.spacing.m)) {
                    Text(
                        stringResource(R.string.settings_gender),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs)) {
                        listOf(null to stringResource(R.string.settings_unset), "MALE" to stringResource(R.string.settings_gender_male), "FEMALE" to stringResource(R.string.settings_gender_female))
                            .forEach { (value, label) ->
                                FilterChip(
                                    selected = state.gender == value,
                                    onClick = { viewModel.setGender(value) },
                                    label = { Text(label) },
                                )
                            }
                    }
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                }
            }
            item(key = "metabolic-activity") {
                Column(Modifier.padding(horizontal = FitnessTheme.spacing.m)) {
                    Text(
                        stringResource(R.string.settings_activity),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    // 5 档放不下一行：横向滚动
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                    ) {
                        listOf(
                            "SEDENTARY" to stringResource(R.string.settings_activity_sedentary),
                            "LIGHT" to stringResource(R.string.settings_activity_light),
                            "MODERATE" to stringResource(R.string.settings_activity_moderate),
                            "ACTIVE" to stringResource(R.string.settings_activity_active),
                            "VERY_ACTIVE" to stringResource(R.string.settings_activity_very_active),
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = state.activityLevel == value,
                                onClick = { viewModel.setActivityLevel(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                }
            }

            // ---------------- 单位 ----------------
            item(key = "units-header") { GroupHeader(stringResource(R.string.settings_group_units)) }
            item(key = "weight-unit") {
                EnumRow(
                    title = stringResource(R.string.settings_weight_unit),
                    subtitle = stringResource(R.string.settings_weight_unit_desc),
                    options = WeightUnit.entries.toList(),
                    selected = state.weightUnit,
                    labelOf = { if (it == WeightUnit.KG) "kg" else "lb" },
                    onSelect = viewModel::setWeightUnit,
                )
            }
            item(key = "distance-unit") {
                EnumRow(
                    title = stringResource(R.string.settings_distance_unit),
                    subtitle = null,
                    options = DistanceUnit.entries.toList(),
                    selected = state.distanceUnit,
                    labelOf = { if (it == DistanceUnit.KM) "km" else "mi" },
                    onSelect = viewModel::setDistanceUnit,
                )
            }

            // ---------------- 训练 ----------------
            item(key = "training-header") { GroupHeader(stringResource(R.string.settings_group_training)) }

            item(key = "default-rest") {
                SliderRow(
                    title = stringResource(R.string.settings_default_rest),
                    valueText = stringResource(R.string.settings_default_rest_value, state.defaultRestSeconds),
                    value = state.defaultRestSeconds.toFloat(),
                    valueRange = 0f..300f,
                    steps = 59,
                    onValueChange = { viewModel.setDefaultRest(it.toInt()) },
                )
            }
            item(key = "weight-step") {
                SliderRow(
                    title = stringResource(R.string.settings_weight_step),
                    valueText = stringResource(
                        R.string.settings_weight_step_value,
                        if (state.weightUnit == WeightUnit.KG) {
                            (state.weightStepKg).toString()
                        } else {
                            (state.weightStepKg / 0.45359237).let { "%.1f".format(it) }
                        },
                        if (state.weightUnit == WeightUnit.KG) "kg" else "lb",
                    ),
                    value = state.weightStepKg.toFloat(),
                    valueRange = 0.5f..10f,
                    steps = 18,
                    onValueChange = { viewModel.setWeightStep((it * 2).toInt() / 2.0) },
                )
            }
            item(key = "auto-start-rest") {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_start_rest),
                    checked = state.autoStartRestTimer,
                    onCheckedChange = viewModel::setAutoStartRest,
                )
            }
            item(key = "rest-sound") {
                SwitchRow(
                    title = stringResource(R.string.settings_rest_sound),
                    checked = state.restSoundEnabled,
                    onCheckedChange = viewModel::setRestSound,
                )
            }
            item(key = "rest-vibration") {
                SwitchRow(
                    title = stringResource(R.string.settings_rest_vibration),
                    checked = state.restVibrationEnabled,
                    onCheckedChange = viewModel::setRestVibration,
                )
            }
            item(key = "rest-tts") {
                SwitchRow(
                    title = stringResource(R.string.settings_rest_tts),
                    checked = state.restTtsEnabled,
                    onCheckedChange = viewModel::setRestTts,
                )
            }
            item(key = "keep-screen-on") {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    checked = state.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }
            item(key = "auto-fill") {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_fill),
                    subtitle = stringResource(R.string.settings_auto_fill_desc),
                    checked = state.autoFillFromLastSession,
                    onCheckedChange = viewModel::setAutoFill,
                )
            }
            item(key = "show-rpe") {
                SwitchRow(
                    title = stringResource(R.string.settings_show_rpe),
                    checked = state.showRpe,
                    onCheckedChange = viewModel::setShowRpe,
                )
            }
            item(key = "include-warmup") {
                SwitchRow(
                    title = stringResource(R.string.settings_include_warmup),
                    subtitle = stringResource(R.string.settings_include_warmup_desc),
                    checked = state.includeWarmupInVolume,
                    onCheckedChange = viewModel::setIncludeWarmup,
                )
            }
            item(key = "one-rm") {
                EnumRow(
                    title = stringResource(R.string.settings_one_rm_formula),
                    subtitle = null,
                    options = OneRmFormula.entries.toList(),
                    selected = state.oneRmFormula,
                    labelOf = {
                        when (it) {
                            OneRmFormula.AUTO -> stringResource(R.string.settings_formula_auto)
                            OneRmFormula.EPLEY -> stringResource(R.string.settings_formula_epley)
                            OneRmFormula.BRZYCKI -> stringResource(R.string.settings_formula_brzycki)
                            OneRmFormula.LOMBARDI -> stringResource(R.string.settings_formula_lombardi)
                        }
                    },
                    onSelect = viewModel::setOneRmFormula,
                )
            }

            // ---------------- 显示 ----------------
            item(key = "display-header") { GroupHeader(stringResource(R.string.settings_group_display)) }
            item(key = "theme") {
                EnumRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = null,
                    options = ThemeMode.entries.toList(),
                    selected = state.themeMode,
                    labelOf = {
                        when (it) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                    },
                    onSelect = viewModel::setTheme,
                )
            }

            // 主题配色预设：chip 前的色点展示该预设的顶栏色
            item(key = "theme-preset") {
                val hasCustom = state.customAppBarColor != null ||
                    state.customAccentColor != null ||
                    state.customContentColor != null ||
                    state.customOtherColor != null ||
                    state.customFontColor != null
                Column(
                    Modifier.padding(
                        horizontal = FitnessTheme.spacing.m,
                        vertical = FitnessTheme.spacing.s,
                    ),
                ) {
                    Text(stringResource(R.string.settings_theme_preset), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                    ) {
                        ThemePreset.entries.forEach { preset ->
                            val (barColor, _) = themePresetPreviewColors(preset)
                            FilterChip(
                                selected = preset == state.themePreset && !hasCustom,
                                onClick = { viewModel.setThemePreset(preset) },
                                label = { Text(presetLabel(preset), maxLines = 1) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(barColor),
                                    )
                                },
                            )
                        }
                        // 自定义 chip：选中表示存在自定义颜色，点按清除全部自定义恢复预设
                        FilterChip(
                            selected = hasCustom,
                            onClick = { viewModel.clearCustomColors() },
                            label = { Text(stringResource(R.string.settings_theme_preset_custom), maxLines = 1) },
                        )
                    }
                }
            }

            // 自定义主题：五个颜色槽位，点按进入滑条调色
            item(key = "custom-theme") {
                Column(
                    Modifier.padding(
                        horizontal = FitnessTheme.spacing.m,
                        vertical = FitnessTheme.spacing.s,
                    ),
                ) {
                    Text(
                        stringResource(R.string.settings_custom_theme_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_custom_theme_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    CustomColorRow(
                        label = stringResource(R.string.settings_custom_app_bar),
                        argb = state.customAppBarColor,
                        onClick = { editingSlot = CustomThemeSlot.APP_BAR },
                    )
                    CustomColorRow(
                        label = stringResource(R.string.settings_custom_accent),
                        argb = state.customAccentColor,
                        onClick = { editingSlot = CustomThemeSlot.ACCENT },
                    )
                    CustomColorRow(
                        label = stringResource(R.string.settings_custom_content),
                        argb = state.customContentColor,
                        onClick = { editingSlot = CustomThemeSlot.CONTENT },
                    )
                    CustomColorRow(
                        label = stringResource(R.string.settings_custom_other),
                        argb = state.customOtherColor,
                        onClick = { editingSlot = CustomThemeSlot.OTHER },
                    )
                    CustomColorRow(
                        label = stringResource(R.string.settings_custom_font),
                        argb = state.customFontColor,
                        onClick = { editingSlot = CustomThemeSlot.FONT },
                    )
                }
            }

            item(key = "plan-sort") {
                EnumRow(
                    title = stringResource(R.string.settings_plan_sort),
                    subtitle = null,
                    options = PlanSortOrder.entries.toList(),
                    selected = state.planSortOrder,
                    labelOf = {
                        when (it) {
                            PlanSortOrder.RECENTLY_EDITED -> stringResource(R.string.settings_sort_recent_edited)
                            PlanSortOrder.RECENTLY_PERFORMED -> stringResource(R.string.settings_sort_recent_performed)
                            PlanSortOrder.NAME -> stringResource(R.string.settings_sort_name)
                            PlanSortOrder.MANUAL -> stringResource(R.string.settings_sort_custom)
                        }
                    },
                    onSelect = viewModel::setPlanSort,
                )
            }

            // 壁纸：选图复制到私有目录，透明度实时可调
            item(key = "wallpaper") {
                ClickableRow(
                    title = if (state.wallpaperUri != null) {
                        stringResource(R.string.wallpaper_change)
                    } else {
                        stringResource(R.string.wallpaper_pick)
                    },
                    subtitle = if (state.wallpaperUri != null) null else stringResource(R.string.wallpaper_none),
                    onClick = {
                        wallpaperLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
            }
            if (state.wallpaperUri != null) {
                item(key = "wallpaper-opacity") {
                    SliderRow(
                        title = stringResource(R.string.wallpaper_opacity),
                        valueText = (state.wallpaperOpacity * 100).toInt().toString() + "%",
                        value = state.wallpaperOpacity,
                        valueRange = 0.05f..0.95f,
                        steps = 17,
                        onValueChange = viewModel::setWallpaperOpacity,
                    )
                }
            }
            // 清除壁纸常驻：未设置时点了也无副作用（文件不存在），保持入口可见更好发现
            item(key = "wallpaper-clear") {
                ClickableRow(
                    title = stringResource(R.string.wallpaper_clear),
                    subtitle = if (state.wallpaperUri == null) {
                        stringResource(R.string.wallpaper_clear_hint_none)
                    } else {
                        null
                    },
                    onClick = {
                        java.io.File(context.filesDir, WALLPAPER_FILE).delete()
                        viewModel.setWallpaper(null)
                    },
                )
            }

            // ---------------- 通知 ----------------
            item(key = "notif-header") {
                GroupHeader(stringResource(R.string.settings_group_notifications))
            }
            item(key = "reminder") {
                SwitchRow(
                    title = stringResource(R.string.settings_workout_reminder),
                    subtitle = stringResource(R.string.settings_workout_reminder_desc),
                    checked = state.workoutReminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setReminderEnabled(enabled)
                        }
                    },
                )
            }
            if (state.workoutReminderEnabled) {
                item(key = "reminder-time") {
                    var showTimePicker by remember { mutableStateOf(false) }
                    ClickableRow(
                        title = stringResource(R.string.settings_reminder_time),
                        subtitle = String.format(
                            "%02d:%02d",
                            state.reminderHour,
                            state.reminderMinute,
                        ),
                        onClick = { showTimePicker = true },
                    )
                    if (showTimePicker) {
                        val timeState = rememberTimePickerState(
                            initialHour = state.reminderHour,
                            initialMinute = state.reminderMinute,
                            is24Hour = true,
                        )
                        AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            title = { Text(stringResource(R.string.settings_reminder_time)) },
                            text = { TimePicker(state = timeState) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.setReminderTime(timeState.hour, timeState.minute)
                                        showTimePicker = false
                                    },
                                ) { Text(stringResource(android.R.string.ok)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePicker = false }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            },
                        )
                    }
                }
                item(key = "reminder-days") {
                    Column(Modifier.padding(horizontal = FitnessTheme.spacing.m)) {
                        Text(
                            stringResource(R.string.settings_reminder_days),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(FitnessTheme.spacing.xs))
                        // 7 个胶囊窄屏放不下：横向滚动而不是挤压溢出
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                        ) {
                            listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
                                .forEach { (value, label) ->
                                    FilterChip(
                                        selected = value in state.reminderDays,
                                        onClick = { viewModel.toggleReminderDay(value) },
                                        label = { Text(label) },
                                    )
                                }
                        }
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                    }
                }
            }

            // ---------------- 数据 ----------------
            item(key = "data-header") { GroupHeader(stringResource(R.string.settings_group_data)) }
            item(key = "backup") {
                ClickableRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    onClick = onOpenBackup,
                )
            }
            item(key = "media") {
                Column(Modifier.padding(FitnessTheme.spacing.m)) {
                    Text(
                        stringResource(R.string.settings_media_stats),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(
                            R.string.settings_media_stats_value,
                            state.mediaFileCount,
                            formatBytes(state.mediaTotalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.lastScannedAt?.let {
                        Text(
                            stringResource(
                                R.string.settings_last_scanned,
                                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(it)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                        TextButton(onClick = viewModel::rescan, enabled = !state.mediaScanning) {
                            Text(
                                stringResource(
                                    if (state.mediaScanning) {
                                        R.string.settings_media_scanning
                                    } else {
                                        R.string.settings_media_scan
                                    },
                                ),
                            )
                        }
                        if (state.mediaOrphanCount > 0) {
                            TextButton(onClick = { showPurgeConfirm = true }) {
                                Text(
                                    stringResource(
                                        R.string.settings_media_cleanable,
                                        "${state.mediaOrphanBytes / 1024 / 1024} MB",
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "clear-all") {
                ClickableRow(
                    title = stringResource(R.string.settings_clear_all),
                    subtitle = stringResource(R.string.settings_clear_all_desc),
                    destructive = true,
                    onClick = {
                        clearAllInput = ""
                        showClearAll = true
                    },
                )
            }

            // ---------------- 隐私 ----------------
            item(key = "privacy-header") { GroupHeader(stringResource(R.string.settings_group_privacy)) }
            item(key = "privacy-note") {
                Text(
                    stringResource(R.string.settings_local_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = FitnessTheme.spacing.m,
                        vertical = FitnessTheme.spacing.s,
                    ),
                )
            }
            item(key = "privacy-policy") {
                ClickableRow(
                    title = stringResource(R.string.settings_privacy_policy),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    onClick = onNavigateToPrivacy,
                )
            }
            item(key = "disclaimer") {
                ClickableRow(
                    title = stringResource(R.string.settings_disclaimer),
                    subtitle = stringResource(R.string.settings_disclaimer_desc),
                    onClick = onNavigateToPrivacy,
                )
            }
            item(key = "open-source") {
                ClickableRow(
                    title = stringResource(R.string.settings_open_source),
                    subtitle = stringResource(R.string.settings_open_source_desc),
                    onClick = onNavigateToPrivacy,
                )
            }
            item(key = "crash") {
                SwitchRow(
                    title = stringResource(R.string.settings_crash_reporting),
                    subtitle = stringResource(R.string.settings_crash_reporting_desc),
                    checked = state.crashReportingEnabled,
                    onCheckedChange = viewModel::setCrashReporting,
                )
            }

            // ---------------- 关于 ----------------
            item(key = "about-header") { GroupHeader(stringResource(R.string.settings_group_about)) }
            item(key = "brand-card") {
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "1.0"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(DesignSystemR.drawable.ic_launcher_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Fitness Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_version, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "© 2026 KerryZeng · MIT License",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "battery") {
                ClickableRow(
                    title = "电池优化",
                    subtitle = if (batteryOptimized) "已加入白名单" else "建议加入白名单以保证提醒准时",
                    onClick = { showBatteryTip = true },
                )
            }
            item(key = "bottom-space") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
        }
    }

    if (showPurgeConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_media_clean_confirm_title),
            message = stringResource(
                R.string.settings_media_clean_confirm_message,
                "${state.mediaOrphanBytes / 1024 / 1024} MB",
            ),
            confirmLabel = stringResource(R.string.settings_media_clean),
            destructive = true,
            onConfirm = {
                showPurgeConfirm = false
                viewModel.purgeOrphans()
            },
            onDismiss = { showPurgeConfirm = false },
        )
    }

    if (showBatteryTip) {
        AlertDialog(
            onDismissRequest = { showBatteryTip = false },
            title = { Text("电池优化") },
            text = {
                Text(
                    "为保证训练提醒与组间休息计时能准时送达，建议将本应用加入系统电池优化白名单。" +
                        "是否前往系统设置？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryTip = false
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:$packageName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryTip = false }) { Text("稍后") }
            },
        )
    }

    // 昵称编辑：分享卡片上的署名
    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text(stringResource(R.string.settings_nickname)) },
            text = {
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_nickname_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setNickname(nicknameInput)
                        showNicknameDialog = false
                    },
                ) { Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text(stringResource(com.fitnesspro.core.designsystem.R.string.common_cancel))
                }
            },
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text(stringResource(R.string.settings_clear_all_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_clear_all_confirm_message))
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    OutlinedTextField(
                        value = clearAllInput,
                        onValueChange = { clearAllInput = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_clear_all_confirm_word)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAll = false
                        viewModel.clearAllData()
                    },
                    enabled = clearAllInput == "DELETE",
                ) {
                    Text(
                        stringResource(DesignSystemR.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) {
                    Text(stringResource(DesignSystemR.string.common_cancel))
                }
            },
        )
    }

    editingSlot?.let { slot ->
        val initial = when (slot) {
            CustomThemeSlot.APP_BAR -> state.customAppBarColor
            CustomThemeSlot.ACCENT -> state.customAccentColor
            CustomThemeSlot.CONTENT -> state.customContentColor
            CustomThemeSlot.OTHER -> state.customOtherColor
            CustomThemeSlot.FONT -> state.customFontColor
        }?.let { Color(it) }
        CustomColorPickerDialog(
            title = when (slot) {
                CustomThemeSlot.APP_BAR -> stringResource(R.string.settings_custom_app_bar)
                CustomThemeSlot.ACCENT -> stringResource(R.string.settings_custom_accent)
                CustomThemeSlot.CONTENT -> stringResource(R.string.settings_custom_content)
                CustomThemeSlot.OTHER -> stringResource(R.string.settings_custom_other)
                CustomThemeSlot.FONT -> stringResource(R.string.settings_custom_font)
            },
            initial = initial,
            onConfirm = { color ->
                viewModel.setCustomColor(slot, color.toArgb().toLong() and 0xFFFFFFFFL)
                editingSlot = null
            },
            onClear = {
                viewModel.setCustomColor(slot, null)
                editingSlot = null
            },
            onDismiss = { editingSlot = null },
        )
    }
}

// ---------------------------------------------------------------------------
// HSL 颜色换算（滑条调色用，不依赖扩展库）
// ---------------------------------------------------------------------------

/** HSL → Compose Color */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = hue / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

/** Compose Color → (色相 0-360, 饱和度 0-1, 明度 0-1) */
private fun colorToHsl(color: Color): Triple<Float, Float, Float> {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = if (d == 0f) {
        0f
    } else {
        when (max) {
            color.red -> 60f * (((color.green - color.blue) / d) % 6f)
            color.green -> 60f * ((color.blue - color.red) / d + 2f)
            else -> 60f * ((color.red - color.green) / d + 4f)
        }
    }
    return Triple(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

@Composable
private fun presetLabel(preset: ThemePreset): String = stringResource(
    when (preset) {
        ThemePreset.VIVID_GREEN -> R.string.settings_theme_preset_vivid_green
        ThemePreset.FRESH_GREEN -> R.string.settings_theme_preset_fresh_green
        ThemePreset.PURE_WHITE -> R.string.settings_theme_preset_pure_white
        ThemePreset.OCEAN_BLUE -> R.string.settings_theme_preset_ocean_blue
        ThemePreset.SUNSET_ORANGE -> R.string.settings_theme_preset_sunset_orange
        ThemePreset.BENTO_SOFT -> R.string.settings_theme_preset_bento_soft
    },
)

@Composable
private fun CustomColorRow(label: String, argb: Long?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FitnessTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(argb?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * 滑条调色：色相 / 饱和度 / 明度三个滑条实时预览。
 * initial = null 表示该槽位当前跟随预设，起始值取一个中性绿色。
 */
@Composable
private fun CustomColorPickerDialog(
    title: String,
    initial: Color?,
    onConfirm: (Color) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (startH, startS, startL) = colorToHsl(initial ?: hslToColor(150f, 0.60f, 0.40f))
    var hue by remember { mutableFloatStateOf(startH) }
    var saturation by remember { mutableFloatStateOf(startS) }
    var lightness by remember { mutableFloatStateOf(startL) }
    val current = hslToColor(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(current),
                )
                Spacer(Modifier.height(FitnessTheme.spacing.m))
                Text(stringResource(R.string.settings_color_hue), style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.settings_color_saturation), style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text(stringResource(R.string.settings_color_lightness), style = MaterialTheme.typography.labelMedium)
                Slider(value = lightness, onValueChange = { lightness = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.settings_color_done))
            }
        },
        dismissButton = {
            Row {
                // 只有已设置过自定义颜色时才提供"清除"（恢复跟随预设）
                if (initial != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.settings_color_follow_preset))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(DesignSystemR.string.common_cancel))
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// 通用行组件
// ---------------------------------------------------------------------------

@Composable
private fun GroupHeader(title: String) {
    Column {
        Spacer(Modifier.height(FitnessTheme.spacing.m))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = FitnessTheme.spacing.m),
        )
        HorizontalDivider(Modifier.padding(top = FitnessTheme.spacing.xs))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Switch 自身不接收点击，整行负责切换：保证触控目标 ≥ 48dp 且行为一致
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun ClickableRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.m),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> EnumRow(
    title: String,
    subtitle: String?,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.s)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        // 选项多时（如 4 种 1RM 公式）窄屏可横向滚动
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = {
                        // 单行显示：配合横向滚动，长文案（如 Brzycki）不会被压成竖排
                        Text(labelOf(option), maxLines = 1)
                    },
                )
            }
        }
    }
}
