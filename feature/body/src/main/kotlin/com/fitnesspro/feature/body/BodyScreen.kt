package com.fitnesspro.feature.body

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.component.EmptyState
import com.fitnesspro.core.designsystem.component.ConfirmDialog
import com.fitnesspro.core.designsystem.component.LoadingState
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.component.SwipeRevealBox
import com.fitnesspro.core.designsystem.component.deleteRevealAction
import com.fitnesspro.core.designsystem.component.TrendLineChart
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.theme.NumericSize
import com.fitnesspro.core.designsystem.theme.numericTextStyle
import com.fitnesspro.core.data.health.HealthConnectManager
import com.fitnesspro.core.domain.calculator.MetabolicCalculator
import com.fitnesspro.core.domain.repository.BodyRepository
import com.fitnesspro.core.domain.repository.DietEntry
import com.fitnesspro.core.domain.repository.DietTotals
import com.fitnesspro.core.domain.repository.FoodRepository
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.BodyMeasurement
import com.fitnesspro.core.model.DataSource
import com.fitnesspro.core.model.WeightUnit
import com.fitnesspro.core.model.time.TimeProvider
import com.fitnesspro.core.model.UnitConversion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

import com.fitnesspro.feature.body.R as BodyR

/** 趋势图可切换的维度：横轴时间，纵轴为该指标的单位（kg / % / cm / 级 / kcal） */
enum class BodyMetric {
    WEIGHT, BODY_FAT, MUSCLE_MASS, HEIGHT, VISCERAL_FAT,
    WAIST, HIP, ARM, THIGH, CALF, BMR,
}

data class BodyUiState(
    val isLoading: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    val records: List<BodyMeasurement> = emptyList(),
    /** 体重趋势（按时间正序） */
    val weightSeries: List<Float> = emptyList(),
    /** 当前选中趋势维度对应的数据序列（按时间正序） */
    val trendSeries: List<Float> = emptyList(),
    val trendMetric: BodyMetric = BodyMetric.WEIGHT,
    val todayEpochDay: Long = 0L,
    val lastMessage: String? = null,
    /** Health Connect 同步进行中：按钮防连点 */
    val isSyncing: Boolean = false,
    /** 今日饮食合计（未记录任何食物时为全 0，仍显示卡片引导） */
    val dietTotals: DietTotals = DietTotals(0.0, 0.0, 0.0, 0.0),
    /** 最新一次基础代谢 BMR（kcal/天），直接从最近一条身体记录里取。 */
    val bmr: Double? = null,
    /** 每日总消耗 TDEE = BMR × 活动系数 */
    val tdee: Double? = null,
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val settings: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
    private val foodRepository: FoodRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val trendMetric = MutableStateFlow(BodyMetric.WEIGHT)

    /** 饮食流（今日合计）。combine 重载最多 5 流，打包成第 4 位 */
    private val dietSummary: Flow<Pair<DietTotals, List<DietEntry>>> = combine(
        foodRepository.observeDietTotals(time.todayEpochDay()),
        foodRepository.observeDietDay(time.todayEpochDay()),
    ) { totals, entries -> totals to entries }

    /** 瞬时 UI 状态打包（消息/同步中/趋势维度） */
    private val transient = combine(
        message,
        syncing,
        trendMetric,
    ) { msg, sync, metric -> Triple(msg, sync, metric) }

    val uiState: StateFlow<BodyUiState> = combine(
        bodyRepository.observeAll(),
        settings.settings,
        transient,
        dietSummary,
    ) { records, userSettings, (msg, isSyncing, metric), (dietTotals, _) ->
        // observeAll 是按日期倒序的；趋势图需要正序，否则线会从右往左画
        val ascending = records.sortedBy { it.epochDay }
        val unit = userSettings.weightUnit
        // 趋势序列按当前维度取值；体重/肌肉量换算成用户单位，纵轴单位随之变化
        val series = when (metric) {
            BodyMetric.WEIGHT -> ascending.mapNotNull { m ->
                m.weightKg?.let { UnitConversion.fromKg(it, unit).toFloat() }
            }
            BodyMetric.BODY_FAT -> ascending.mapNotNull { it.bodyFatPercent?.toFloat() }
            BodyMetric.MUSCLE_MASS -> ascending.mapNotNull { m ->
                m.muscleMassKg?.let { UnitConversion.fromKg(it, unit).toFloat() }
            }
            BodyMetric.HEIGHT -> ascending.mapNotNull { it.heightCm?.toFloat() }
            BodyMetric.VISCERAL_FAT -> ascending.mapNotNull { it.visceralFatLevel?.toFloat() }
            BodyMetric.WAIST -> ascending.mapNotNull { it.waistCm?.toFloat() }
            BodyMetric.HIP -> ascending.mapNotNull { it.hipCm?.toFloat() }
            BodyMetric.ARM -> ascending.mapNotNull { it.armCm?.toFloat() }
            BodyMetric.THIGH -> ascending.mapNotNull { it.thighCm?.toFloat() }
            BodyMetric.CALF -> ascending.mapNotNull { it.calfCm?.toFloat() }
            // BMR 在写入时已估算并入库 —— 这里直接读出来即可，
            // 因此历史 BMR 走势与体重的变动是真实对应关系，而不是"假设今天的体重 = 那时的体重"
            BodyMetric.BMR -> ascending.mapNotNull { it.bmr?.toFloat() }
        }

        // 顶部的"今日 BMR/TDEE" 卡片显示最新一条记录里存的 BMR——
        // 每次保存身体数据体重/身高都会自动重新估算并入库；
        // 用户不必手动维护"今天的基础代谢"这一项
        val latestRecord = records.firstOrNull()
        val bmrFromRecord = latestRecord?.bmr
        val weightNow = latestRecord?.weightKg
        val heightLatest = records.firstNotNullOfOrNull { it.heightCm }
        val gender = when (userSettings.gender) {
            "MALE" -> MetabolicCalculator.Gender.MALE
            "FEMALE" -> MetabolicCalculator.Gender.FEMALE
            else -> null
        }
        val activity = runCatching {
            MetabolicCalculator.ActivityLevel.valueOf(userSettings.activityLevel)
        }.getOrDefault(MetabolicCalculator.ActivityLevel.MODERATE)
        // 优先用落库值；缺失时（升级前无 bmr 列的空数据）现场算一次兜底显示
        val currentBmr = bmrFromRecord ?: MetabolicCalculator.bmr(weightNow, heightLatest, userSettings.age, gender)

        BodyUiState(
            isLoading = false,
            unit = unit,
            records = records,
            weightSeries = series.takeIf { metric == BodyMetric.WEIGHT }
                ?: ascending.mapNotNull { m ->
                    m.weightKg?.let { UnitConversion.fromKg(it, unit).toFloat() }
                },
            trendSeries = series,
            trendMetric = metric,
            todayEpochDay = time.todayEpochDay(),
            lastMessage = msg,
            isSyncing = isSyncing,
            dietTotals = dietTotals,
            bmr = currentBmr,
            tdee = MetabolicCalculator.tdee(currentBmr, activity),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    fun setTrendMetric(metric: BodyMetric) {
        trendMetric.value = metric
    }

    /**
     * 保存今日记录。
     *
     * 输入按用户当前单位解析，但**内部统一存 kg** —— 否则切换单位后历史数据含义会变，
     * 而趋势图上的数字会突然"跳"一次，用户会以为数据错了。
     */
    fun save(
        weightInput: String,
        bodyFatInput: String,
        muscleMassInput: String,
        heightInput: String,
        visceralInput: String,
        chestInput: String,
        waistInput: String,
        hipInput: String,
        armInput: String,
        thighInput: String,
        calfInput: String,
    ) {
        viewModelScope.launch {
            val unit = settings.current().weightUnit
            val weightDisplay = weightInput.trim().toDoubleOrNull()
            if (weightDisplay == null || weightDisplay <= 0.0) {
                message.value = "请输入体重"
                return@launch
            }
            val weightKg = UnitConversion.toKg(weightDisplay, unit)
            if (weightKg < 20 || weightKg > 400) {
                message.value = "请输入 20–400 之间的体重"
                return@launch
            }

            val outcome = bodyRepository.upsertByDay(
                BodyMeasurement(
                    id = 0,
                    epochDay = time.todayEpochDay(),
                    recordedAt = time.nowEpochMs(),
                    weightKg = weightKg,
                    bodyFatPercent = bodyFatInput.trim().toDoubleOrNull(),
                    muscleMassKg = muscleMassInput.trim().toDoubleOrNull(),
                    heightCm = heightInput.trim().toDoubleOrNull(),
                    visceralFatLevel = visceralInput.trim().toDoubleOrNull(),
                    chestCm = chestInput.trim().toDoubleOrNull(),
                    waistCm = waistInput.trim().toDoubleOrNull(),
                    hipCm = hipInput.trim().toDoubleOrNull(),
                    armCm = armInput.trim().toDoubleOrNull(),
                    thighCm = thighInput.trim().toDoubleOrNull(),
                    calfCm = calfInput.trim().toDoubleOrNull(),
                    source = DataSource.MANUAL,
                )
            )
            message.value = if (outcome.isSuccess) "已保存" else "保存失败"
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            bodyRepository.delete(id)
            message.value = "已删除"
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    // ---- Health Connect 同步 ----

    /**
     * 同步入口。可用性 → 权限 → 同步 三级推进；
     * 权限请求走 ActivityResultContract（由 UI 层持有 launcher），这里只发起。
     */
    fun onSyncClicked(launchPermissions: (Set<String>) -> Unit) {
        viewModelScope.launch {
            when (healthConnectManager.availability()) {
                HealthConnectManager.Availability.Available ->
                    if (healthConnectManager.hasReadWeightPermission()) {
                        syncFromHealth()
                    } else {
                        launchPermissions(HealthConnectManager.PERMISSIONS)
                    }
                HealthConnectManager.Availability.UpdateRequired ->
                    message.value = "需要更新 Health Connect 后才能同步"
                HealthConnectManager.Availability.NotInstalled ->
                    message.value = "未安装 Health Connect"
                HealthConnectManager.Availability.Unsupported ->
                    message.value = "此设备不支持 Health Connect"
            }
        }
    }

    fun onHealthPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
                syncFromHealth()
            } else {
                message.value = "未授予体重读取权限"
            }
        }
    }

    /**
     * 导入最近 [SYNC_DAYS] 天的体重。
     *
     * 去重与合并规则：
     *  - 同一天多条（智能秤早上晚上各称一次）取**当天最后一次**
     *  - 当天已有体重记录（手动录入）则跳过 —— 手动数据是用户主动录入的，
     *     不该被后台同步悄悄覆盖；当天记录存在但没称重量的（只有围度）则合并体重进去
     */
    private suspend fun syncFromHealth() {
        syncing.value = true
        try {
            val existingByDay = bodyRepository.observeAll().first().associateBy { it.epochDay }
            val sinceMs = time.nowEpochMs() - SYNC_DAYS * 24L * 60 * 60 * 1000

            val byDay = healthConnectManager.readWeights(sinceMs)
                .groupBy { weight ->
                    java.time.Instant.ofEpochMilli(weight.timeEpochMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toEpochDay()
                }
                .mapValues { (_, sameDay) -> sameDay.last() }

            var imported = 0
            byDay.forEach { (day, weight) ->
                val existing = existingByDay[day]
                if (existing?.weightKg != null) return@forEach
                bodyRepository.upsertByDay(
                    BodyMeasurement(
                        id = 0,
                        epochDay = day,
                        recordedAt = weight.timeEpochMs,
                        weightKg = weight.weightKg,
                        source = DataSource.HEALTH_CONNECT,
                    ),
                )
                imported++
            }

            message.value = if (imported == 0) "没有可导入的新体重" else "已从健康数据导入 $imported 天体重"
        } finally {
            syncing.value = false
        }
    }

    private companion object {
        const val SYNC_DAYS = 180L
    }
}

/** 今日饮食 vs 代谢：摄入、BMR/TDEE、盈余或缺口 */
@Composable
private fun DietSummaryCard(
    totals: DietTotals,
    bmr: Double?,
    tdee: Double?,
    onOpenDiet: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            // 代谢基准：BMR / TDEE。缺资料时给引导而不是编数字
            if (bmr == null || tdee == null) {
                Text(
                    stringResource(BodyR.string.body_diet_need_profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.l)) {
                    Text(
                        "基础代谢 " + formatKcal(bmr),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "每日消耗 " + formatKcal(tdee),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        formatKcal(totals.kcal),
                        style = numericTextStyle(NumericSize.Large),
                    )
                    Text(
                        stringResource(BodyR.string.body_diet_intake),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        surplusLabel(totals.kcal, tdee),
                        style = MaterialTheme.typography.titleMedium,
                        color = surplusColor(totals.kcal, tdee),
                    )
                    Text(
                        stringResource(BodyR.string.body_diet_vs_tdee),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.l)) {
                MacroLabel(stringResource(BodyR.string.body_diet_protein), totals.protein)
                MacroLabel(stringResource(BodyR.string.body_diet_fat), totals.fat)
                MacroLabel(stringResource(BodyR.string.body_diet_carb), totals.carb)
            }
            Spacer(Modifier.height(FitnessTheme.spacing.s))
            Button(onClick = onOpenDiet, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(BodyR.string.body_diet_record))
            }
        }
    }
}

@Composable
private fun MacroLabel(label: String, grams: Double) {
    Column {
        Text(
            String.format("%.1f g", grams),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatKcal(kcal: Double): String = "${kcal.roundToInt()} kcal"

/** 盈余/缺口文案与颜色：缺口=蓝（减脂方向）、盈余=橙（增肌方向）、持平=中性 */
@Composable
private fun surplusLabel(intake: Double, tdee: Double?): String {
    val diff = MetabolicCalculator.surplusOrDeficit(intake, tdee) ?: return "—"
    return when {
        diff > 50 -> "+${diff.roundToInt()} kcal 盈余"
        diff < -50 -> "${diff.roundToInt()} kcal 缺口"
        else -> "持平"
    }
}

@Composable
private fun surplusColor(intake: Double, tdee: Double?): Color {
    val diff = MetabolicCalculator.surplusOrDeficit(intake, tdee) ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return when {
        diff > 50 -> FitnessTheme.colors.prHighlight
        diff < -50 -> Color(0xFF2B6BE0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    onOpenSettings: () -> Unit = {},
    onOpenDiet: () -> Unit = {},
    viewModel: BodyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Health Connect 的权限请求必须由 ActivityResultContract 发起，因此 launcher 在 UI 层
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onHealthPermissionsResult(granted) }

    var weight by rememberSaveable { mutableStateOf("") }
    var bodyFat by rememberSaveable { mutableStateOf("") }
    var muscleMass by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var visceral by rememberSaveable { mutableStateOf("") }
    var chest by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var hip by rememberSaveable { mutableStateOf("") }
    var arm by rememberSaveable { mutableStateOf("") }
    var thigh by rememberSaveable { mutableStateOf("") }
    var calf by rememberSaveable { mutableStateOf("") }
    var showMore by rememberSaveable { mutableStateOf(false) }
    // 身体记录是硬删除（没有回收站），必须二次确认
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.lastMessage) {
        val msg = state.lastMessage ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(msg, actionLabel = null)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(BodyR.string.body_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(
                                com.fitnesspro.core.designsystem.R.string.common_settings,
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
            item(key = "form") {
                SoftCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column {
                        Text(
                            stringResource(BodyR.string.body_today),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(FitnessTheme.spacing.s))

                        MetricField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = stringResource(
                                if (state.unit == WeightUnit.KG) {
                                    BodyR.string.body_weight
                                } else {
                                    BodyR.string.body_weight
                                },
                            ),
                        )

                        Spacer(Modifier.height(FitnessTheme.spacing.xs))
                        // 体重对自重动作的容量计算是必需的，所以这一条提示要放在表单里
                        Text(
                            stringResource(BodyR.string.body_weight_used_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        TextButton(onClick = { showMore = !showMore }) {
                            Text(
                                stringResource(
                                    if (showMore) {
                                        BodyR.string.body_less_metrics
                                    } else {
                                        BodyR.string.body_more_metrics
                                    },
                                ),
                            )
                        }

                        if (showMore) {
                            MetricField(bodyFat, { bodyFat = it }, stringResource(BodyR.string.body_fat))
                            MetricField(muscleMass, { muscleMass = it }, stringResource(BodyR.string.body_muscle_mass))
                            MetricField(height, { height = it }, stringResource(BodyR.string.body_height))
                            MetricField(visceral, { visceral = it }, stringResource(BodyR.string.body_visceral_fat))
                            MetricField(chest, { chest = it }, stringResource(BodyR.string.body_chest))
                            MetricField(waist, { waist = it }, stringResource(BodyR.string.body_waist))
                            MetricField(hip, { hip = it }, stringResource(BodyR.string.body_hip))
                            MetricField(arm, { arm = it }, stringResource(BodyR.string.body_arm))
                            MetricField(thigh, { thigh = it }, stringResource(BodyR.string.body_thigh))
                            MetricField(calf, { calf = it }, stringResource(BodyR.string.body_calf))
                        }

                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        Button(
                            onClick = {
                                viewModel.save(
                                    weight, bodyFat, muscleMass, height, visceral,
                                    chest, waist, hip, arm, thigh, calf,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(BodyR.string.body_save))
                        }
                        Text(
                            stringResource(BodyR.string.body_overwrite_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        OutlinedButton(
                            onClick = { viewModel.onSyncClicked(healthPermissionLauncher::launch) },
                            enabled = !state.isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(FitnessTheme.spacing.s))
                            }
                            Text(stringResource(BodyR.string.body_sync_from_health))
                        }
                    }
                }
            }

            item(key = "trend") {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(BodyR.string.body_trend),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                // 维度切换：横轴时间不变，纵轴单位随指标变化（kg/%/cm/级）
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                ) {
                    BodyMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = state.trendMetric == metric,
                            onClick = { viewModel.setTrendMetric(metric) },
                            label = { Text(metricLabel(metric), maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(FitnessTheme.spacing.xs))
                TrendLineChart(
                    points = state.trendSeries,
                    emptyText = stringResource(BodyR.string.body_trend_empty),
                    valueFormatter = { metricValueFormatter(state.trendMetric, state.unit, it) },
                    contentDescription = "趋势图，共 ${state.trendSeries.size} 个数据点",
                )
            }

            // ---------------- 饮食与代谢 ----------------
            item(key = "diet-title") {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(BodyR.string.body_diet_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item(key = "diet-summary") {
                DietSummaryCard(
                    totals = state.dietTotals,
                    bmr = state.bmr,
                    tdee = state.tdee,
                    onOpenDiet = onOpenDiet,
                )
            }

            item(key = "history-title") {
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    stringResource(BodyR.string.body_history),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.records.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(BodyR.string.body_history_empty),
                        description = stringResource(BodyR.string.body_history_empty_desc),
                    )
                }
            } else {
                items(state.records, key = { it.id }) { record ->
                    BodyRecordRow(
                        record = record,
                        unit = state.unit,
                        previous = state.records.getOrNull(state.records.indexOf(record) + 1),
                        onDelete = { pendingDeleteId = record.id },
                    )
                }
            }

            item(key = "bottom") {
                Spacer(Modifier.height(FitnessTheme.spacing.xl + FitnessTheme.layout.bottomBarInset))
            }
        }
    }

    // 身体记录没有回收站，删除前必须再确认一次
    pendingDeleteId?.let { id ->
        ConfirmDialog(
            title = stringResource(com.fitnesspro.core.designsystem.R.string.common_delete_confirm_title),
            message = stringResource(BodyR.string.body_delete_confirm_message),
            confirmLabel = stringResource(com.fitnesspro.core.designsystem.R.string.common_delete),
            destructive = true,
            onConfirm = {
                pendingDeleteId = null
                viewModel.delete(id)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun metricLabel(metric: BodyMetric): String = stringResource(
    when (metric) {
        BodyMetric.WEIGHT -> BodyR.string.body_metric_weight
        BodyMetric.BODY_FAT -> BodyR.string.body_metric_body_fat
        BodyMetric.MUSCLE_MASS -> BodyR.string.body_metric_muscle
        BodyMetric.HEIGHT -> BodyR.string.body_metric_height
        BodyMetric.VISCERAL_FAT -> BodyR.string.body_metric_visceral
        BodyMetric.WAIST -> BodyR.string.body_waist
        BodyMetric.HIP -> BodyR.string.body_hip
        BodyMetric.ARM -> BodyR.string.body_arm
        BodyMetric.THIGH -> BodyR.string.body_thigh
        BodyMetric.CALF -> BodyR.string.body_calf
        BodyMetric.BMR -> BodyR.string.body_metric_bmr
    },
)

/**
 * 纵轴数值格式：体重/肌肉量带重量单位，体脂 %，围度 cm，内脏脂肪"级"，BMR "kcal"。
 * 注意：valueFormatter 是普通 lambda，不能调用 stringResource，这里用纯字符串拼接。
 */
private fun metricValueFormatter(metric: BodyMetric, unit: WeightUnit, value: Float): String {
    val suffix = when (metric) {
        BodyMetric.WEIGHT, BodyMetric.MUSCLE_MASS -> if (unit == WeightUnit.KG) "kg" else "lb"
        BodyMetric.BODY_FAT -> "%"
        BodyMetric.HEIGHT, BodyMetric.WAIST, BodyMetric.HIP, BodyMetric.ARM,
        BodyMetric.THIGH, BodyMetric.CALF -> "cm"
        BodyMetric.VISCERAL_FAT -> "级"
        BodyMetric.BMR -> "kcal"
    }
    return "%.1f %s".format(value, suffix)
}

@Composable
private fun MetricField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FitnessTheme.spacing.xs),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
        textStyle = numericTextStyle(NumericSize.Small),
    )
}

@Composable
private fun BodyRecordRow(
    record: BodyMeasurement,
    unit: WeightUnit,
    previous: BodyMeasurement?,
    onDelete: () -> Unit,
) {
    val date = java.time.LocalDate.ofEpochDay(record.epochDay)
    val dateText = "%d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    // 赋给局部变量：跨模块的 public 属性无法智能转换（smart cast）
    val currentWeight = record.weightKg
    val previousWeight = previous?.weightKg
    val delta = if (currentWeight != null && previousWeight != null) {
        UnitConversion.fromKg(currentWeight, unit) - UnitConversion.fromKg(previousWeight, unit)
    } else {
        null
    }

    SwipeRevealBox(
        modifier = Modifier.fillMaxWidth(),
        actions = listOf(deleteRevealAction(onDelete = onDelete)),
    ) {
        SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(FitnessTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dateText, style = MaterialTheme.typography.bodyMedium)
            val details = buildList {
                record.bodyFatPercent?.let { add("体脂 ${"%.1f".format(it)}%") }
                record.muscleMassKg?.let { add("肌肉 ${"%.1f".format(it)}kg") }
                record.heightCm?.let { add("身高 ${"%.1f".format(it)}cm") }
                record.visceralFatLevel?.let { add("内脏脂肪 ${it.toInt()}级") }
                record.waistCm?.let { add("腰 ${"%.1f".format(it)}cm") }
                record.source.let { if (it == DataSource.HEALTH_CONNECT) add("来自健康数据") }
            }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            record.weightKg?.let {
                Text(
                    "%.1f %s".format(
                        UnitConversion.fromKg(it, unit),
                        if (unit == WeightUnit.KG) "kg" else "lb",
                    ),
                    style = numericTextStyle(NumericSize.Small),
                )
            }
            delta?.let {
                Text(
                    when {
                        kotlin.math.abs(it) < 0.05 -> "与上次持平"
                        it > 0 -> "+%.1f".format(it)
                        else -> "%.1f".format(it)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
            }
        }
    }
}
