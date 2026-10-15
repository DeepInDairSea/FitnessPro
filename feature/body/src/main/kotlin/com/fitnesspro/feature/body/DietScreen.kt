package com.fitnesspro.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.designsystem.component.SoftCard
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.repository.Food
import com.fitnesspro.core.domain.repository.FoodCategory
import com.fitnesspro.core.domain.repository.FoodRepository
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class DietUiState(
    val isLoading: Boolean = true,
    val foods: List<Food> = emptyList(),
    val query: String = "",
    val category: FoodCategory? = null,
    val recordedToken: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<FoodCategory?>(null)
    private val recordedToken = MutableStateFlow(0L)

    /** 首次进入时幂等导入食物种子（失败不打扰：列表为空时仍有自定义入口提示） */
    init {
        viewModelScope.launch { foodRepository.importSeedIfNeeded() }
    }

    private val foodFlow = combine(query, category) { q, c -> q to c }

    val uiState: StateFlow<DietUiState> = combine(
        foodFlow.flatMapLatest { (q, c) ->
            // 全部=一次性拉全库在内存里按分类过滤；分类=只取该类。搜索词都走 SQL LIKE
            foodRepository.observeFoods(q)
        },
        query,
        category,
        recordedToken,
    ) { foods, q, c, token ->
        DietUiState(
            isLoading = false,
            foods = if (c == null) foods else foods.filter { it.category == c },
            query = q,
            category = c,
            recordedToken = token,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryChange(value: FoodCategory?) {
        category.value = if (category.value == value) null else value
    }

    /** 记一笔：营养按克数换算快照落库 */
    fun record(food: Food, grams: Int, onRecorded: () -> Unit) {
        val clamped = grams.coerceIn(1, 2000)
        viewModelScope.launch {
            foodRepository.recordDiet(time.todayEpochDay(), food.id, clamped)
            recordedToken.value += 1
            onRecorded()
        }
    }
}

/**
 * 饮食记录页：搜索/分类 → 选食物 → 选克数 → 记录。
 * 一屏只做一件事，连续记录多笔时流程最短。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(
    onNavigateBack: () -> Unit,
    viewModel: DietViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Food?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(R.string.diet_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                com.fitnesspro.core.designsystem.R.string.common_close,
                            ),
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
            contentPadding = PaddingValues(
                start = FitnessTheme.spacing.m,
                end = FitnessTheme.spacing.m,
                top = FitnessTheme.spacing.s,
                bottom = FitnessTheme.spacing.l,
            ),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.diet_search_hint)) },
                    singleLine = true,
                )
            }

            item(key = "categories") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
                ) {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { viewModel.onCategoryChange(null) },
                        label = { Text(stringResource(R.string.diet_all)) },
                    )
                    FoodCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { viewModel.onCategoryChange(category) },
                            label = { Text(categoryLabel(category)) },
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    stringResource(R.string.diet_egg_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(state.foods.size, key = { state.foods[it].id }) { index ->
                val food = state.foods[index]
                FoodRow(food = food, onClick = { selected = food })
            }
        }
    }

    // 份量选择对话框
    selected?.let { food ->
        PortionDialog(
            food = food,
            onDismiss = { selected = null },
            onConfirm = { grams ->
                viewModel.record(food, grams) {
                    selected = null
                    onNavigateBack()
                }
            },
        )
    }
}

/** 克数选择：预设档位 + 自定义输入；选中的克数实时换算营养预览。
 *  补剂走"一勺 → 15/30/45g"阶梯 + 蛋白预估（其它营养按比例） */
@Composable
private fun PortionDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val isSupplement = food.isSupplement
    val defaultGrams = when {
        isSupplement -> food.scoopGrams ?: 30
        else -> 100
    }
    var gramsText by remember { mutableStateOf(defaultGrams.toString()) }
    var gramsPreset by remember { mutableIntStateOf(defaultGrams) }

    fun currentGrams(): Int {
        val typed = gramsText.toIntOrNull() ?: return gramsPreset
        return typed.coerceIn(1, 2000)
    }

    val grams = currentGrams()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column {
                if (isSupplement) {
                    // 补剂主线说明：每 100g 含多少 g 蛋白（比 50g 更适合补剂语境）+ 一勺配置
                    val scoop = food.scoopGrams ?: 30
                    val perScoop = food.proteinPerScoop ?: 0.0
                    Text(
                        text = "每 100g 含蛋白 ${fmt(food.proteinPer100g)}g · 一勺 ${scoop}g 含蛋白 ${fmt(perScoop)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Text(
                        "${food.kcalPer50g.roundToInt()} kcal/50g（蛋白 ${fmt(food.proteinPer50g)}g · 脂肪 ${fmt(food.fatPer50g)}g · 碳水 ${fmt(food.carbPer50g)}g）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.diet_per_50g) +
                            "：蛋白 ${fmt(food.proteinPer50g)}g · 脂肪 ${fmt(food.fatPer50g)}g · " +
                            "碳水 ${fmt(food.carbPer50g)}g · ${food.kcalPer50g.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 补剂走 15/30/45g 一勺阶梯；普通食物走 50/100/150/200g 阶梯
                    val presets: List<Pair<String, Int>> = if (isSupplement) {
                        listOf(
                            stringResource(R.string.diet_scoop_half) to (food.scoopGrams?.div(2) ?: 15),
                            stringResource(R.string.diet_scoop_one) to (food.scoopGrams ?: 30),
                            stringResource(R.string.diet_scoop_one_half) to ((food.scoopGrams ?: 30) + (food.scoopGrams?.div(2) ?: 15)),
                        )
                    } else {
                        listOf(
                            stringResource(R.string.diet_preset_50) to 50,
                            stringResource(R.string.diet_preset_100) to 100,
                            stringResource(R.string.diet_preset_150) to 150,
                            stringResource(R.string.diet_preset_200) to 200,
                        )
                    }
                    presets.forEach { (label, value) ->
                        FilterChip(
                            selected = grams == value,
                            onClick = {
                                gramsPreset = value
                                gramsText = value.toString()
                            },
                            label = { Text("$label·${value}g") },
                        )
                    }
                }
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.diet_grams_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(FitnessTheme.spacing.s))
                Text(
                    "≈ 蛋白 ${fmt(food.proteinFor(grams))}g · 脂肪 ${fmt(food.fatFor(grams))}g · " +
                        "碳水 ${fmt(food.carbFor(grams))}g · ${food.kcalFor(grams).roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(grams) }) {
                Text(stringResource(R.string.diet_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(FitnessTheme.spacing.m)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${food.kcalPer50g.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(FitnessTheme.spacing.xs))
            // 补剂单独描述：每 100g 多少 g 蛋白 + 一勺配置 —— 这是用户决定买不买、写多重的关键
            if (food.isSupplement) {
                val scoop = food.scoopGrams ?: 30
                val perScoop = food.proteinPerScoop ?: 0.0
                Text(
                    "每 100g 含蛋白 ${fmt(food.proteinPer100g)}g · 一勺 ${scoop}g 含蛋白 ${fmt(perScoop)}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    stringResource(R.string.diet_per_50g) +
                        " · 蛋白 ${fmt(food.proteinPer50g)}g · 脂肪 ${fmt(food.fatPer50g)}g · 碳水 ${fmt(food.carbPer50g)}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 食物分类标签：与 FoodCategory 一一对应 */
@Composable
private fun categoryLabel(category: FoodCategory): String = when (category) {
    FoodCategory.MEAT -> "肉禽鱼虾"
    FoodCategory.EGG_DAIRY -> "蛋奶豆"
    FoodCategory.STAPLE -> "主食"
    FoodCategory.VEG_FRUIT -> "蔬菜水果"
    FoodCategory.NUT_OIL -> "坚果油脂"
    FoodCategory.SUPPLEMENT -> "蛋白补剂"
}

private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
