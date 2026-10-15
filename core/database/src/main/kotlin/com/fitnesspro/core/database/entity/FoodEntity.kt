package com.fitnesspro.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 食物定义（每 50g 的营养标尺）。
 *
 * 统一"每 50g"而不是"每 100g/每份"：份量直觉好折算（一个鸡蛋≈50g、一勺油≈10g），
 * 记录时输入克数即可线性换算。热量 kcal 按蛋白/碳水 4、脂肪 9 折算并由种子数据校准。
 *
 * 补剂（[FoodCategory.SUPPLEMENT]）还会带"scoop"二字段（每勺多少克、每勺含多少蛋白），
 * 贴合"一勺"直觉、UI 的 15g/30g/45g 阶梯也直接基于这俩字段。非补剂食物这两字段为 null。
 */
@Entity(
    tableName = "food_definition",
    indices = [Index("name"), Index("category")],
)
data class FoodDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 分类枚举名（FoodCategory） */
    val category: String,
    /** 每 50g 蛋白质（克） */
    val proteinPer50g: Double,
    /** 每 50g 脂肪（克） */
    val fatPer50g: Double,
    /** 每 50g 碳水（克） */
    val carbPer50g: Double,
    /** 每 50g 热量（kcal） */
    val kcalPer50g: Double,
    /** 用户自建食物（种子数据为 false，升级种子时不会覆盖用户数据） */
    val isCustom: Boolean = false,
    /** 回收站（软删除） */
    val deletedAt: Long? = null,
    /**
     * 每勺克数（**仅补剂有意义**；非补剂为 null）。
     *  典型值 30（一满勺），少数小勺装产品是 15 或 25。
     */
    val scoopGrams: Int? = null,
    /**
     * 每勺含蛋白质克数（**仅补剂有意义**；非补剂为 null）。
     *  典型值 25（~80% 浓缩/酪蛋白一勺）、24（~90% 分离乳清一勺）、23（~95% 水解一勺）。
     */
    val proteinPerScoop: Double? = null,
)

/** 每日饮食记录：营养按记录时的克数折算后快照（食物定义后续可改，快照保证历史不变） */
@Entity(
    tableName = "diet_entry",
    indices = [Index("epochDay"), Index("foodId")],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = FoodDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
)
data class DietEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 记录归属日（LocalDate.toEpochDay） */
    val epochDay: Long,
    val foodId: Long,
    /** 实际摄入克数 */
    val grams: Int,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val kcal: Double,
    /** 记录时的食物名快照（食物被删后历史仍可读） */
    val foodName: String,
    val createdAt: Long,
)
