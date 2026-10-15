package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.OneRmFormula
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.UnitConversion

/**
 * 个人纪录判定。
 *
 * 三条硬规则（都在单元测试里锁住）：
 *  1. 只有**正式组**参与判定 —— 热身组重量往往很低不会误报，但递减组会误报，因此一并排除
 *  2. 只有**已完成**的组参与 —— 跳过/失败的组不算
 *  3. 1RM 估算在次数 > [OneRmCalculator.MAX_RELIABLE_REPS] 时返回 null，不参与判定
 */
class PersonalRecordDetector {

    sealed interface Result {
        data class Hit(
            val prType: PrType,
            val value: Double,
            val previousValue: Double?,
            val reps: Int? = null,
        ) : Result
    }

    /**
     * @param set           本次完成的组
     * @param bodyWeightKg  自身体重，用于自重动作的单组容量
     * @param existing      该动作当前已存在的 PR（key = PrType，value = 数值）
     * @param oneRmFormula  估算公式（跟随用户设置）
     * @param includeDropAndFailure 递减组/力竭组是否参与判定，默认不参与
     */
    fun detect(
        set: SetRecord,
        bodyWeightKg: Double?,
        existing: Map<PrType, Double>,
        oneRmFormula: OneRmFormula = OneRmFormula.AUTO,
        includeDropAndFailure: Boolean = false,
    ): List<Result> {
        if (!isEligible(set, includeDropAndFailure)) return emptyList()

        val results = mutableListOf<Result>()

        // 1) 单组最大重量（必须有有效次数，否则"空杆放上去"也算纪录了）
        val weight = set.weightKg
        val totalReps = set.totalReps
        if (weight != null && weight > 0.0 && totalReps >= 1) {
            val previous = existing[PrType.MAX_WEIGHT]
            if (previous == null || weight > previous) {
                results += Result.Hit(PrType.MAX_WEIGHT, weight, previous, totalReps)
            }
        }

        // 2) 单组最大容量
        val volume = VolumeCalculator.setVolume(set, bodyWeightKg, includeWarmup = false)
        if (volume > 0.0) {
            val previous = existing[PrType.MAX_VOLUME_SET]
            if (previous == null || volume > previous) {
                results += Result.Hit(PrType.MAX_VOLUME_SET, UnitConversion.round2(volume), previous, totalReps)
            }
        }

        // 3) 最大估算 1RM
        // 注意：单侧动作用单侧次数，不能用左右之和（会算高一倍）
        val estimationReps = set.repsForEstimation
        if (weight != null && estimationReps != null) {
            val estimated = OneRmCalculator.estimate(weight, estimationReps, oneRmFormula)
            if (estimated != null) {
                val previous = existing[PrType.MAX_E1RM]
                if (previous == null || estimated > previous) {
                    results += Result.Hit(PrType.MAX_E1RM, estimated, previous, estimationReps)
                }
            }
        }

        return results
    }

    private fun isEligible(set: SetRecord, includeDropAndFailure: Boolean): Boolean {
        if (!set.isWorkingAndCompleted) return false
        if (includeDropAndFailure) return true
        return set.setType == SetType.NORMAL || set.setType == SetType.AMRAP
    }

    /**
     * 只返回"最重要的那一条"，避免同一次完成弹三个 PR 提示。
     * 优先级：最大重量 > 估算 1RM > 单组容量。
     */
    fun primaryHit(results: List<Result>): Result.Hit? =
        results.filterIsInstance<Result.Hit>().maxByOrNull { importance(it.prType) }

    private fun importance(type: PrType): Int = when (type) {
        PrType.MAX_WEIGHT -> 4
        PrType.MAX_E1RM -> 3
        PrType.MAX_VOLUME_SET -> 2
        PrType.MAX_REPS -> 1
    }
}
