package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.UnitConversion
import com.fitnesspro.core.model.WeightUnit

/**
 * 训练辅助建议：热身组生成、渐进超负荷建议。
 *
 * 重要约束（PRD FR-14）：这里的建议**只作为 UI 文案提示**，绝不自动改写用户输入。
 * 自动改用户输入会让用户失去对数据的信任感 —— 你写 60kg，App 偷偷改成 62.5kg，
 * 那记录就不再是"我做了什么"了。
 */
object TrainingAdvisor {

    // ------------------------------------------------------------------
    // 热身组生成器
    // ------------------------------------------------------------------

    data class WarmupStep(
        val percentOfWorkingWeight: Double,
        val reps: Int,
        val weightKg: Double,
    )

    /** 常用热身序列：40%×8、60%×5、80%×3 */
    val DEFAULT_WARMUP_TEMPLATE: List<Pair<Double, Int>> = listOf(
        0.40 to 8,
        0.60 to 5,
        0.80 to 3,
    )

    /**
     * 依据正式组重量生成热身组。
     * @param stepKg 杠铃最小配重步进（kg 为 2.5，lb 换算后为 5 时对应 2.5kg 的近似）
     */
    fun generateWarmupSets(
        workingWeightKg: Double,
        stepKg: Double = 2.5,
        template: List<Pair<Double, Int>> = DEFAULT_WARMUP_TEMPLATE,
        barWeightKg: Double = 20.0,
    ): List<WarmupStep> {
        if (workingWeightKg <= barWeightKg) return emptyList()
        return template.map { (percent, reps) ->
            val raw = workingWeightKg * percent
            val rounded = UnitConversion.roundToStep(raw, stepKg).coerceAtLeast(barWeightKg)
            WarmupStep(percent, reps, UnitConversion.round2(rounded))
        }
    }

    // ------------------------------------------------------------------
    // 渐进超负荷建议（双进阶 / Double Progression）
    // ------------------------------------------------------------------

    enum class SuggestionReason {
        /** 上次所有正式组都达到次数区间上限 → 加重 */
        INCREASE_WEIGHT,

        /** 达标但未到上限（或区间内） → 维持重量继续加次数 */
        KEEP_WEIGHT,

        /** 连续多次未达区间下限 → 减重 */
        DELOAD_HINT,

        /** 没有历史数据 → 无法建议 */
        NO_DATA,
    }

    data class LoadSuggestion(
        val weightKg: Double,
        val reason: SuggestionReason,
    )

    /**
     * 双进阶算法：
     *  1. 上次所有正式组都达到次数区间**上限** → 建议 +1 个步进，次数回到区间下限
     *  2. 所有组都达到**下限** → 维持重量，目标是把次数推到上限
     *  3. 存在低于下限的组 → 维持重量
     *  4. 连续 [DELOAD_STREAK_THRESHOLD] 次低于下限 → 建议减重 10%（Deload 提示）
     *
     * @param consecutiveMissedSessions 从最近一次往前数，连续未达标的场次数
     */
    fun suggestNextLoad(
        lastSessionSets: List<SetRecord>,
        repRange: RepsRange,
        currentWeightKg: Double,
        weightUnit: WeightUnit = WeightUnit.KG,
        consecutiveMissedSessions: Int = 0,
    ): LoadSuggestion {
        val working = lastSessionSets.filter {
            it.setType == SetType.NORMAL && it.status == SetStatus.COMPLETED
        }
        if (working.isEmpty() || repRange.isEmpty) {
            return LoadSuggestion(currentWeightKg, SuggestionReason.NO_DATA)
        }

        val low = repRange.low
        val high = repRange.high

        if (consecutiveMissedSessions >= DELOAD_STREAK_THRESHOLD) {
            val deloaded = UnitConversion.roundToStep(
                currentWeightKg * 0.90,
                UnitConversion.defaultWeightStep(weightUnit),
            )
            return LoadSuggestion(UnitConversion.round2(deloaded), SuggestionReason.DELOAD_HINT)
        }

        val missedLow = low != null && working.any { (it.reps ?: 0) < low }
        if (missedLow) return LoadSuggestion(currentWeightKg, SuggestionReason.KEEP_WEIGHT)

        val allReachedTop = high != null && working.all { (it.reps ?: 0) >= high }
        if (allReachedTop) {
            val step = UnitConversion.defaultWeightStep(weightUnit)
            val incremented = UnitConversion.roundToStep(currentWeightKg + step, step)
            return LoadSuggestion(UnitConversion.round2(incremented), SuggestionReason.INCREASE_WEIGHT)
        }

        return LoadSuggestion(currentWeightKg, SuggestionReason.KEEP_WEIGHT)
    }

    private const val DELOAD_STREAK_THRESHOLD = 3

    /** 建议增量：kg 用 2.5，lb 用 5 */
    fun incrementFor(unit: WeightUnit): Double = UnitConversion.defaultWeightStep(unit)
}
