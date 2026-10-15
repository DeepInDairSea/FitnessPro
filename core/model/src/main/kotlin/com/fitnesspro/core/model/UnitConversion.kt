package com.fitnesspro.core.model

/**
 * 单位换算。
 *
 * 设计约定（ADR-03）：数据库内部**一律以 kg 存储**，保留 2 位小数；
 * 用户输入的原始值另存 displayWeight / displayUnit，避免单位切换后原值丢失。
 */
object UnitConversion {

    private const val KG_PER_LB = 0.45359237
    private const val KM_PER_MILE = 1.609344

    fun lbToKg(lb: Double): Double = round2(lb * KG_PER_LB)

    fun kgToLb(kg: Double): Double = round2(kg / KG_PER_LB)

    /** 用户输入 → 内部 kg */
    fun toKg(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) round2(value) else lbToKg(value)

    /** 内部 kg → 展示值 */
    fun fromKg(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) round2(kg) else kgToLb(kg)

    /** 按单位取默认步进：kg 用 2.5，lb 用 5 */
    fun defaultWeightStep(unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) 2.5 else 5.0

    fun metersToDisplay(meters: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.KM) round2(meters / 1000.0) else round2(meters / KM_PER_MILE / 1000.0)

    fun displayToMeters(value: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.KM) round2(value * 1000.0)
        else round2(value * KM_PER_MILE * 1000.0)

    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** 四舍五入到指定步进（用于百分比换算与建议重量） */
    fun roundToStep(value: Double, step: Double): Double =
        if (step <= 0.0) value else Math.round(value / step) * step
}
