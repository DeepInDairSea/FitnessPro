package com.fitnesspro.core.domain.repository

import com.fitnesspro.core.model.CustomThemeSlot
import com.fitnesspro.core.model.OneRmFormula
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.ThemeMode
import com.fitnesspro.core.model.ThemePreset
import com.fitnesspro.core.model.UserSettings
import com.fitnesspro.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * 用户偏好读写。
 *
 * 为什么接口在 :core:domain 而不是 :core:datastore：
 *  - 偏好是**领域概念**（单位、默认休息时长、是否自动填充），不是 DataStore 的实现细节
 *  - :core:data 与各 feature 都需要读它；若把它留在 :core:datastore，
 *    就得为了暴露一个接口而把 DataStore 的依赖泄露给全工程
 *  - 实现（SettingsDataStore）仍在 :core:datastore，依赖方向为 datastore → domain，无环
 *
 * 读取统一通过 [settings] Flow 或 [current] 快照；写方法均幂等且 suspend。
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    /** 一次性读取当前值（用于命令式场景，例如记录一组时判断是否自动开始休息） */
    suspend fun current(): UserSettings

    // 单位
    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setDistanceUnit(unit: com.fitnesspro.core.model.DistanceUnit)

    // 训练
    suspend fun setDefaultRestSeconds(seconds: Int)
    suspend fun setAutoStartRestTimer(enabled: Boolean)
    suspend fun setRestSoundEnabled(enabled: Boolean)
    suspend fun setRestVibrationEnabled(enabled: Boolean)
    suspend fun setRestTtsEnabled(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setAutoFillFromLastSession(enabled: Boolean)
    suspend fun setWeightStepKg(step: Double)
    suspend fun setShowRpe(enabled: Boolean)
    suspend fun setIncludeWarmupInVolume(enabled: Boolean)
    suspend fun setOneRmFormula(formula: OneRmFormula)

    // 显示
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemePreset(preset: ThemePreset)

    /**
     * 自定义主题色：argb 为 ARGB 颜色值，null = 清除该槽位（恢复跟随预设）。
     */
    suspend fun setCustomThemeColor(slot: CustomThemeSlot, argb: Long?)
    suspend fun setFirstDayOfWeek(day: Int)
    suspend fun setPlanSortOrder(order: PlanSortOrder)

    // 壁纸：uri 是复制到 filesDir 后的文件名；null = 清除壁纸
    suspend fun setWallpaper(uri: String?)
    suspend fun setWallpaperOpacity(alpha: Float)

    // 个人资料：用于分享卡片的昵称与头像（头像为复制到 filesDir 的文件名）
    suspend fun setNickname(nickname: String)
    suspend fun setAvatarUri(fileName: String?)
    /** 首次分享时"仅保存到本地"提示的确认标记 */
    suspend fun setShareDisclaimerAcknowledged(acknowledged: Boolean)

    // 代谢档案（BMR/TDEE）：身高/体重来自身体记录的最新值，其余在此维护
    suspend fun setAge(age: Int?)
    suspend fun setGender(gender: String?)
    suspend fun setActivityLevel(level: String)

    // 通知
    suspend fun setWorkoutReminder(enabled: Boolean, days: Set<Int>, hour: Int, minute: Int)
    suspend fun setBodyWeightReminderEnabled(enabled: Boolean)
    suspend fun setNotificationPermissionAsked(asked: Boolean)
    suspend fun setNotificationPermissionDeclined(declined: Boolean)

    // 引导与合规
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun acceptPrivacy(epochMs: Long)

    suspend fun setCrashReportingEnabled(enabled: Boolean)

    /** 清空全部数据时调用 */
    suspend fun resetToDefaults()
}
