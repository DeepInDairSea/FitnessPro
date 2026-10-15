package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/**
 * 用户偏好。存储于 DataStore（非 Room），通过 Flow 暴露。
 * 全部字段都有默认值，保证首次启动可直接可用。
 */
@Serializable
data class UserSettings(
    // 单位
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,

    // 训练
    val defaultRestSeconds: Int = 90,
    val autoStartRestTimer: Boolean = true,
    val restSoundEnabled: Boolean = true,
    val restVibrationEnabled: Boolean = true,
    val restTtsEnabled: Boolean = false,
    val keepScreenOnDuringWorkout: Boolean = true,
    val autoFillFromLastSession: Boolean = true,
    val weightStepKg: Double = 2.5,
    val showRpe: Boolean = false,
    val includeWarmupInVolume: Boolean = false,
    val oneRmFormula: OneRmFormula = OneRmFormula.AUTO,

    // 显示
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 主题配色预设；自定义颜色非空时优先于预设的对应角色 */
    val themePreset: ThemePreset = ThemePreset.BENTO_SOFT,
    /** 自定义主题色（ARGB Long），null = 该角色跟随预设 */
    val customAppBarColor: Long? = null,
    val customAccentColor: Long? = null,
    val customContentColor: Long? = null,
    val customOtherColor: Long? = null,
    val customFontColor: Long? = null,
    val firstDayOfWeek: Int = 1, // 1 = 周一（ISO），7 = 周日
    val planSortOrder: PlanSortOrder = PlanSortOrder.RECENTLY_EDITED,
    /** 壁纸：复制到 filesDir 的文件名；null = 未设置 */
    val wallpaperUri: String? = null,
    /** 壁纸不透明度 0-1：值越小越透，内容区越容易被看见 */
    val wallpaperOpacity: Float = 0.35f,

    /** 分享卡片上的昵称：空 = 显示默认"健身者" */
    val nickname: String = "",
    /** 头像：复制到 filesDir 的文件名；null = 未设置（分享卡片用首字母圆标代替） */
    val avatarUri: String? = null,
    /** 首次分享时的"仅保存到本地"提示是否已确认 */
    val shareDisclaimerAcknowledged: Boolean = false,
    /** 卡片已保存过相册的训练会话 ID：用于"第 2 次只分享不重复保存"的跨会话持久判定 */
    val shareSavedSessionIds: Set<Long> = emptySet(),

    // 代谢档案（BMR/TDEE 计算；身高/体重取身体记录的最新值）
    /** 年龄（岁）；null = 未设置（BMR 显示引导而非编造数字） */
    val age: Int? = null,
    /** 性别：male / female / 未设置 */
    val gender: String? = null,
    /** 活动水平（MetabolicCalculator.ActivityLevel 枚举名），默认中度 */
    val activityLevel: String = "MODERATE",

    // 通知
    val workoutReminderEnabled: Boolean = false,
    val workoutReminderDays: Set<Int> = emptySet(),
    val workoutReminderHour: Int = 19,
    val workoutReminderMinute: Int = 0,
    val bodyWeightReminderEnabled: Boolean = false,
    val notificationPermissionAsked: Boolean = false,
    val notificationPermissionDeclined: Boolean = false,

    // 引导
    val onboardingCompleted: Boolean = false,
    val privacyAcceptedAt: Long? = null,

    // 其他
    val crashReportingEnabled: Boolean = false,
) {
    /** 生效的休息秒数优先级：组级 > 动作级 > 计划级 > 全局默认（本字段） */
    fun effectiveRestSeconds(override: Int?): Int =
        (override ?: defaultRestSeconds).coerceIn(0, 900)
}
