package com.fitnesspro.core.domain.calculator

/**
 * 基础代谢（BMR）与每日总消耗（TDEE）。
 *
 * BMR 用 Mifflin-St Jeor 公式 —— 对现代人群比 Harris-Benedict 更准，且只需要
 * 体重 / 身高 / 年龄 / 性别四个易得输入：
 *   男：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 + 5
 *   女：BMR = 10×体重kg + 6.25×身高cm − 5×年龄 − 161
 *
 * TDEE = BMR × 活动系数。饮食热量与 TDEE 的差即"盈余/缺口"。
 */
object MetabolicCalculator {

    enum class Gender { MALE, FEMALE }

    enum class ActivityLevel(val factor: Double) {
        SEDENTARY(1.2),      // 久坐：办公室为主，几乎不运动
        LIGHT(1.375),        // 轻度：每周运动 1-3 次
        MODERATE(1.55),      // 中度：每周运动 3-5 次
        ACTIVE(1.725),       // 高度：每周运动 6-7 次
        VERY_ACTIVE(1.9),    // 极高：体力劳动或每天两练
    }

    /** 基础代谢（kcal/天）。输入缺失或非法时返回 null（UI 显示引导而非编一个数） */
    fun bmr(
        weightKg: Double?,
        heightCm: Double?,
        age: Int?,
        gender: Gender?,
    ): Double? {
        val w = weightKg?.takeIf { it > 0 } ?: return null
        val h = heightCm?.takeIf { it > 0 } ?: return null
        val a = age?.takeIf { it in 10..100 } ?: return null
        val base = 10 * w + 6.25 * h - 5 * a
        return when (gender) {
            Gender.MALE -> base + 5
            Gender.FEMALE -> base - 161
            null -> null
        }
    }

    /** 每日总消耗 = BMR × 活动系数 */
    fun tdee(bmr: Double?, level: ActivityLevel?): Double? {
        if (bmr == null || level == null) return null
        return bmr * level.factor
    }

    /**
     * 热量差 = 摄入 − TDEE。正值 = 盈余（增肌方向），负值 = 缺口（减脂方向）。
     */
    fun surplusOrDeficit(intakeKcal: Double, tdee: Double?): Double? {
        if (tdee == null) return null
        return intakeKcal - tdee
    }
}
