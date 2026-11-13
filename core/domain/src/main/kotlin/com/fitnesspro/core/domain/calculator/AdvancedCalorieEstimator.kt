package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.EstimationLevel
import com.fitnesspro.core.model.IntensityZone

/**
 * 多因素卡路里估算引擎（kcal-develp.md，分组计算方案 A）。
 *
 * **核心原则：消耗只在"完成一组"时计算并累加，绝不按整场重算。**
 * 早期实现把整场回归公式在每组落库时重复执行，短时训练里
 * TV 项与回归常数被放大成荒谬数字（3 组 50×10 算出上千 kcal）。
 * 回归类公式（L1/L2）是对**完整训练**标定的，无法按组分解，因此：
 *
 *  - 单组消耗 = 强度校正后的 MET 法（基础 MET × 强度区校正 × 体重 × 时长）
 *  - 训练中实时显示 = 已完成组消耗之和 + 当前进行中组的 MET 跳字
 *  - 训练结束 = 逐组累加值 + EPOC（运动后过量氧耗，一次性追加）
 *
 * 动作差异来自动作字典标定：基础 MET、自重系数、器械效率、复合度、
 * 节奏因子、强度区（fit-carculate.md）。身体差异通过体重进入公式。
 * 全部纯本地计算，数据缺失自动降级，不抛异常、不产生 NaN。
 */
object AdvancedCalorieEstimator {

    /** 完全没有体重记录时的兜底值（成年人均值） */
    const val DEFAULT_WEIGHT_KG = 65.0

    /** 实时跳字使用的默认 MET（当前进行中组的近似抗阻强度） */
    private const val TICKING_MET = 5.0

    /** 单个动作的完成工作量 */
    data class ExerciseWork(
        /** 基础 MET */
        val metValue: Double,
        val isBodyweight: Boolean,
        /** 自重负荷系数 */
        val selfWeightCoefficient: Double,
        /** 器械效率系数 */
        val equipmentEfficiency: Double,
        /** 复合动作 TV ×1.2，孤立 ×0.8 */
        val isCompound: Boolean,
        /** 单侧动作 TV ×1.1（稳定性需求） */
        val isUnilateral: Boolean,
        /** 节奏因子 ×1.0~1.25 */
        val tempoFactor: Double,
        /** 强度区：MET 校正与 EPOC 强度因子 */
        val intensityZone: IntensityZone,
        /** 单次动作平均时长（秒）：标准节奏做功时长的计算基准 */
        val averageRepDurationSeconds: Int,
        /** 已完成组数 */
        val completedSets: Int,
        /** 已完成总次数（左右相加） */
        val totalReps: Int,
        /** 代表性外部配重（kg）：取完成组中的最大重量 */
        val externalWeightKg: Double,
    ) {
        companion object {
            /** 从动作标定构造（缺标定/自定义动作给保守默认值） */
            fun fromCalibration(
                metValue: Double?,
                isBodyweight: Boolean?,
                selfWeightCoefficient: Double?,
                equipmentEfficiency: Double?,
                isCompound: Boolean?,
                isUnilateral: Boolean,
                tempoFactor: Double?,
                intensityZone: IntensityZone?,
                averageRepDurationSeconds: Int?,
                completedSets: Int,
                totalReps: Int,
                externalWeightKg: Double,
            ): ExerciseWork = ExerciseWork(
                metValue = metValue ?: 3.5,
                isBodyweight = isBodyweight ?: false,
                selfWeightCoefficient = selfWeightCoefficient ?: 0.5,
                equipmentEfficiency = equipmentEfficiency ?: 1.0,
                isCompound = isCompound ?: true,
                isUnilateral = isUnilateral,
                tempoFactor = tempoFactor ?: 1.0,
                intensityZone = intensityZone ?: IntensityZone.HYPERTROPHY,
                averageRepDurationSeconds = (averageRepDurationSeconds ?: 3).coerceAtLeast(1),
                completedSets = completedSets,
                totalReps = totalReps,
                externalWeightKg = externalWeightKg,
            )
        }
    }

    /** 用户身体数据快照（会话开始时固化，之后身体数据变化不影响本次估算） */
    data class EstimatorProfile(
        /** 体重（kg）：会话体重快照优先 */
        val weightKg: Double? = null,
        /** 身高（cm）：保留字段，供后续按身体数据分层 */
        val heightCm: Double? = null,
        /** 年龄：保留字段 */
        val age: Int? = null,
        /** 体脂率（%）：保留字段 */
        val bodyFatPercent: Double? = null,
        /** 骨骼肌含量（kg）：保留字段 */
        val skeletalMuscleKg: Double? = null,
        /** 去脂体重（kg）：保留字段 */
        val leanBodyMassKg: Double? = null,
    )

    data class EstimationResult(
        val calories: Double,
        val level: EstimationLevel,
        /** 该次估算覆盖的训练容量（TV，含复合/单侧/节奏校正） */
        val totalVolume: Double,
    )

    /**
     * 单组消耗估算：做功量主导、时长兜底。
     *
     * 设计要点（修正"纯时长法"的两个缺陷）：
     *  1. **做得快慢不该剧烈改变消耗**——同一动作同一次数的做功量相同。
     *     因此做功时长用「次数 × 单次标准时长」计算，实际计时只在其更长
     *     （慢速研磨、组内停顿）时作为 TUT 修正生效，且设上限防止把组间
     *     休息整段计入。
     *  2. **配重强度必须影响消耗**——50kg×10 与 20kg×10 不是一回事。
     *     用有效负荷与体重的比值做强度比修正（自重动作天然接近基准 1.0，
     *     大配重上浮、小配重下调），限幅 [0.7, 1.6] 防止极端值。
     *
     * 平板卧推 50kg×10、体重 70kg（有效负荷比 1.43、标准节奏 30 秒）：
     * ≈ 5.3 kcal/组，3 组 ≈ 16 kcal + EPOC ≈ 4 kcal ≈ 20 kcal。
     * 顺带说明：一分钟内做完 1500kg 容量，物理消耗就是这个量级——
     * 机械功只有约 7.3 千焦，人体不可能在 1 分钟内燃烧几十上百 kcal。
     */
    fun estimateSet(
        work: ExerciseWork,
        profile: EstimatorProfile,
        actualSetDurationSeconds: Int,
    ): EstimationResult {
        val weight = profile.weightKg ?: DEFAULT_WEIGHT_KG
        val totalVolume = trainingVolumeOf(work, weight)
        val load = effectiveLoadOf(work, weight)

        // 标准节奏做功时长；无次数信息（纯时长类）时退回实际计时
        val tempoSeconds = work.totalReps * work.averageRepDurationSeconds
        val effectiveSeconds = if (tempoSeconds <= 0) {
            actualSetDurationSeconds
        } else {
            // 实际计时上限 = 标准节奏的 3 倍 + 90 秒：允许慢速研磨与喘息，
            // 但防止把整段组间休息当成做功时间计费
            val capped = actualSetDurationSeconds.coerceAtMost(tempoSeconds * 3 + 90)
            maxOf(tempoSeconds, capped)
        }

        // 配重强度比：有效负荷相对「体重 × 默认自重系数」的倍数。
        // 无做功量（纯时长类）不做强度修正；自重动作天然落在基准附近。
        val intensityRatio = if (load > 0 && work.totalReps > 0) {
            (load / (weight * 0.5)).coerceIn(0.7, 1.6)
        } else {
            1.0
        }

        val adjustedMet = adjustMetByIntensity(work.metValue, work.intensityZone)
        val calories = adjustedMet * intensityRatio * weight * 3.5 / 200 * (effectiveSeconds / 60.0)
        return EstimationResult(calories.coerceAtLeast(0.0), levelFor(profile), totalVolume)
    }

    /**
     * 训练结束的 EPOC 追加（运动后过量氧耗）。
     * 与文档的差异：不设 30 kcal 下限——那是对"完整训练"的假设，
     * 对短时训练会把估算抬高一个量级；改为按实际训练量线性缩放，上限 150 不变。
     */
    fun estimateEPOC(totalVolumeKg: Double, zone: IntensityZone, minutes: Double): Double {
        val baseEpoc = minutes * 1.2
        val intensityFactor = when (zone) {
            IntensityZone.STRENGTH, IntensityZone.POWER -> 1.5
            IntensityZone.HYPERTROPHY -> 1.2
            IntensityZone.ENDURANCE -> 1.0
        }
        val volumeFactor = (totalVolumeKg / 5000).coerceIn(0.1, 2.0)
        return (baseEpoc * intensityFactor * volumeFactor).coerceIn(0.0, 150.0)
    }

    /** 会话级 EPOC：按会话内最强强度区与总训练容量计算 */
    fun estimateEPOCFor(work: List<ExerciseWork>, weightKg: Double, minutes: Double): Double {
        if (work.isEmpty() || minutes <= 0.0) return 0.0
        val totalTv = work.sumOf { trainingVolumeOf(it, weightKg) }
        if (totalTv <= 0.0) return 0.0
        return estimateEPOC(totalTv, strongestZone(work), minutes)
    }

    /**
     * 实时跳字：当前进行中组的 MET 法估算（不用 TV，保证每秒有数字更新
     * 而不会重复累加已完成组的消耗）。
     *
     * @param metValue 当前动作的基础 MET；未知时用抗阻训练默认值
     * @param elapsedSeconds 距上次"完成一组"以来经过的有效秒数
     */
    fun estimateTicking(metValue: Double?, weightKg: Double?, elapsedSeconds: Int): Double {
        val minutes = elapsedSeconds.coerceIn(0, 7200) / 60.0
        if (minutes <= 0.0) return 0.0
        val met = adjustMetByIntensity(metValue ?: TICKING_MET, IntensityZone.HYPERTROPHY)
        return met * (weightKg ?: DEFAULT_WEIGHT_KG) * 3.5 / 200 * minutes
    }

    /** 单动作有效负荷（kg）：自重 = 体重×系数 + 配重；器械/自由重量 = 配重×效率 */
    fun effectiveLoadOf(work: ExerciseWork, weightKg: Double): Double =
        if (work.isBodyweight) {
            weightKg * work.selfWeightCoefficient + work.externalWeightKg
        } else {
            work.externalWeightKg * work.equipmentEfficiency
        }

    /** 训练容量 TV = 组数 × 次数 × 有效负荷 ×(复合/单侧/节奏校正) */
    fun trainingVolumeOf(work: ExerciseWork, weightKg: Double = DEFAULT_WEIGHT_KG): Double {
        if (work.completedSets <= 0 || work.totalReps <= 0) return 0.0
        val load = effectiveLoadOf(work, weightKg)
        if (load <= 0.0) return 0.0
        var tv = work.completedSets.toDouble() * work.totalReps * load
        tv *= if (work.isCompound) 1.2 else 0.8
        if (work.isUnilateral) tv *= 1.1
        tv *= work.tempoFactor
        return tv
    }

    /** 强度校正 MET：力量/爆发区更高，耐力区更低 */
    fun adjustMetByIntensity(baseMet: Double, zone: IntensityZone): Double = when (zone) {
        IntensityZone.STRENGTH -> baseMet * 1.3
        IntensityZone.HYPERTROPHY -> baseMet * 1.0
        IntensityZone.ENDURANCE -> baseMet * 0.85
        IntensityZone.POWER -> baseMet * 1.4
    }

    /** 会话内最强强度区 */
    private fun strongestZone(work: List<ExerciseWork>): IntensityZone =
        when {
            work.any { it.intensityZone == IntensityZone.POWER } -> IntensityZone.POWER
            work.any { it.intensityZone == IntensityZone.STRENGTH } -> IntensityZone.STRENGTH
            work.any { it.intensityZone == IntensityZone.HYPERTROPHY } -> IntensityZone.HYPERTROPHY
            else -> IntensityZone.ENDURANCE
        }

    /** 层级：有真实体重记录 → L3（TV/MET 校正），否则 L4（默认体重兜底） */
    private fun levelFor(profile: EstimatorProfile): EstimationLevel =
        if (profile.weightKg != null) EstimationLevel.L3_TV_ADJUSTED else EstimationLevel.L4_WEIGHT_ONLY
}
