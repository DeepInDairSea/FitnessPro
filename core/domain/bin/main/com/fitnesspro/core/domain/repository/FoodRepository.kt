package com.fitnesspro.core.domain.repository

import com.fitnesspro.core.model.result.Outcome
import kotlinx.coroutines.flow.Flow

/** 食物分类（与种子数据 FoodCategories 同名，TEXT 存储） */
enum class FoodCategory {
    MEAT,        // 肉禽鱼虾
    EGG_DAIRY,   // 蛋奶豆
    STAPLE,      // 主食
    VEG_FRUIT,   // 蔬菜水果
    NUT_OIL,     // 坚果油脂
    SUPPLEMENT,  // 蛋白补剂（乳清、酪蛋白、酵母、鸡蛋蛋白 等）
}

/** 食物：统一"每 50g"营养标尺，记录时按克数线性换算 */
data class Food(
    val id: Long,
    val name: String,
    val category: FoodCategory,
    val proteinPer50g: Double,
    val fatPer50g: Double,
    val carbPer50g: Double,
    val kcalPer50g: Double,
    val isCustom: Boolean = false,
    /** 每勺克数（**补剂专用**；非补剂为 null） */
    val scoopGrams: Int? = null,
    /** 每勺含蛋白克数（**补剂专用**；非补剂为 null） */
    val proteinPerScoop: Double? = null,
) {
    /** 按克数换算蛋白质（克） */
    fun proteinFor(grams: Int): Double = proteinPer50g * grams / 50.0
    fun fatFor(grams: Int): Double = fatPer50g * grams / 50.0
    fun carbFor(grams: Int): Double = carbPer50g * grams / 50.0
    fun kcalFor(grams: Int): Double = kcalPer50g * grams / 50.0
    /** 每 100g 蛋白质克数（UI 上常看到的"X g / 100g"标签） */
    val proteinPer100g: Double get() = proteinPer50g * 2.0
    /** 是否为蛋白补剂（决定 UI 是否启用"15/30/45g 一勺"阶梯） */
    val isSupplement: Boolean get() = category == FoodCategory.SUPPLEMENT &&
        scoopGrams != null && proteinPerScoop != null
}

/** 单日饮食合计 */
data class DietTotals(
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val kcal: Double,
)

/** 每日饮食记录条目（含食物名快照） */
data class DietEntry(
    val id: Long,
    val epochDay: Long,
    val foodId: Long,
    val foodName: String,
    val grams: Int,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val kcal: Double,
)

interface FoodRepository {

    /** 食物库（种子 + 自定义），query 为空返回全部 */
    fun observeFoods(query: String): Flow<List<Food>>

    /** 幂等导入常用食物种子（首次进入饮食相关页面时调用） */
    suspend fun importSeedIfNeeded(): Outcome<Int>

    suspend fun createCustomFood(
        name: String,
        category: FoodCategory,
        proteinPer50g: Double,
        fatPer50g: Double,
        carbPer50g: Double,
        kcalPer50g: Double,
    ): Outcome<Long>

    /** 记一笔饮食：克数换算后的营养在落库时快照 */
    suspend fun recordDiet(epochDay: Long, foodId: Long, grams: Int): Outcome<Unit>

    fun observeDietDay(epochDay: Long): Flow<List<DietEntry>>
    fun observeDietTotals(epochDay: Long): Flow<DietTotals>
    suspend fun deleteDietEntry(id: Long): Outcome<Unit>
}
