package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.UnitConversion

/**
 * 杠铃片计算器。
 *
 * 力量训练中非常实用的工具：算出目标重量后，用户需要知道"每侧挂哪些片"。
 * 采用贪心算法（先大后小）。因为杠铃片重量通常是相互整除的一组数
 * （25/20/15/10/5/2.5/1.25），贪心能得到精确解；当目标重量不是最小片的整数倍时，
 * 给出"最接近但不超过"的方案，并通过 deltaKg 告知差值，由 UI 提示用户。
 */
class PlateCalculator(
    /** 每侧可用的杠铃片（kg），默认覆盖常见商用片 */
    private val availablePlatesKg: List<Double> = DEFAULT_PLATES_KG,
    /** 杠铃杆重量（kg）。奥杆 20kg，女杆 15kg，EZ 杆约 10kg */
    private val barWeightKg: Double = 20.0,
) {

    data class Solution(
        /** 每侧配片，已按从重到轻排序 */
        val perSide: List<Double>,
        /** 实际可达总重量 */
        val achievedTotalKg: Double,
        /** 与目标的差值（正数表示超出） */
        val deltaKg: Double,
    ) {
        val isExact: Boolean get() = kotlin.math.abs(deltaKg) < 0.001
    }

    /**
     * @return 目标重量小于杆重时返回 null（无解）
     */
    fun solve(targetKg: Double): Solution? {
        if (targetKg < barWeightKg) return null

        var remainingPerSide = (targetKg - barWeightKg) / 2.0
        val perSide = mutableListOf<Double>()

        for (plate in availablePlatesKg.sortedDescending()) {
            while (remainingPerSide + EPSILON >= plate) {
                perSide += plate
                remainingPerSide -= plate
            }
        }

        val achieved = barWeightKg + perSide.sum() * 2.0
        return Solution(
            perSide = perSide,
            achievedTotalKg = UnitConversion.round2(achieved),
            deltaKg = UnitConversion.round2(achieved - targetKg),
        )
    }

    /** 针对磅制杠铃：2.5 / 5 / 10 / 25 / 35 / 45 lb 片，杆重 45 lb */
    fun solveForPounds(targetLb: Double): SolutionPounds? {
        val lbPlates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
        val barLb = 45.0
        if (targetLb < barLb) return null

        var remaining = (targetLb - barLb) / 2.0
        val perSide = mutableListOf<Double>()
        for (plate in lbPlates) {
            while (remaining + EPSILON >= plate) {
                perSide += plate
                remaining -= plate
            }
        }
        val achieved = barLb + perSide.sum() * 2.0
        return SolutionPounds(perSide, achieved, achieved - targetLb)
    }

    data class SolutionPounds(
        val perSide: List<Double>,
        val achievedTotalLb: Double,
        val deltaLb: Double,
    )

    companion object {
        private const val EPSILON = 1e-9

        val DEFAULT_PLATES_KG: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

        /** 常见杠铃杆配置 */
        val BAR_PRESETS_KG: List<Pair<String, Double>> = listOf(
            "奥杆" to 20.0,
            "女杆" to 15.0,
            "EZ 杆" to 10.0,
            "短杆" to 10.0,
        )
    }
}
