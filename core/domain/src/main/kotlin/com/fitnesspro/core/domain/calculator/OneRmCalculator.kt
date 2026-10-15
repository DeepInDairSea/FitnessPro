package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.OneRmFormula
import com.fitnesspro.core.model.UnitConversion
import kotlin.math.pow

/**
 * 1RM 估算与百分比换算。
 *
 * 公式来源：
 *  - Epley (1985):    1RM = w × (1 + r/30)
 *  - Brzycki (1993):  1RM = w × 36 / (37 − r)
 *  - Lombardi (1989): 1RM = w × r^0.10
 *
 * 选择策略：次数 ≤ 6 时 Brzycki 更准；次数 7-12 时 Epley 更稳；超过 12 次估算已不可靠，返回 null。
 */
object OneRmCalculator {

    /** 超过该次数不再估算（误差过大，且会被用于 PR 判定而失真） */
    const val MAX_RELIABLE_REPS = 12

    fun estimate(
        weightKg: Double,
        reps: Int,
        formula: OneRmFormula = OneRmFormula.AUTO,
    ): Double? {
        if (weightKg <= 0.0 || reps <= 0) return null
        if (reps == 1) return UnitConversion.round2(weightKg)
        if (reps > MAX_RELIABLE_REPS) return null

        val raw = when (formula) {
            OneRmFormula.AUTO -> if (reps <= 6) brzycki(weightKg, reps) else epley(weightKg, reps)
            OneRmFormula.EPLEY -> epley(weightKg, reps)
            OneRmFormula.BRZYCKI -> brzycki(weightKg, reps)
            OneRmFormula.LOMBARDI -> lombardi(weightKg, reps)
        }
        return UnitConversion.round2(raw)
    }

    fun epley(weightKg: Double, reps: Int): Double = weightKg * (1.0 + reps / 30.0)

    fun brzycki(weightKg: Double, reps: Int): Double =
        if (reps >= 37) epley(weightKg, reps) else weightKg * 36.0 / (37.0 - reps)

    fun lombardi(weightKg: Double, reps: Int): Double =
        weightKg * reps.toDouble().pow(0.10)

    /**
     * 由 1RM 反推指定百分比对应的重量，并按最小配重步进取整。
     * 例：oneRm = 100, percent = 0.7, step = 2.5 → 70.0
     */
    fun weightForPercent(
        oneRm: Double,
        percent: Double,
        stepKg: Double = 2.5,
    ): Double = UnitConversion.roundToStep(oneRm * percent, stepKg)

    /** 常用百分比表，用于"训练强度区间"提示 */
    val COMMON_PERCENTAGES: List<Double> =
        listOf(0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.00)

    /** 由 RPE 粗估剩余次数（RIR），用于 RPE 与次数区间互相换算 */
    fun rirFromRpe(rpe: Double): Double = (10.0 - rpe).coerceIn(0.0, 5.0)
}
