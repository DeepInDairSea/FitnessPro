package com.fitnesspro.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.CustomThemeSlot
import com.fitnesspro.core.model.DistanceUnit
import com.fitnesspro.core.model.OneRmFormula
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.ThemeMode
import com.fitnesspro.core.model.ThemePreset
import com.fitnesspro.core.model.UserSettings
import com.fitnesspro.core.model.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    override val settings: Flow<UserSettings> = store.data.map { it.toUserSettings() }

    override suspend fun current(): UserSettings = settings.first()

    /**
     * 统一的写入入口。
     *
     * DataStore 的 `edit` 在磁盘 IO 失败（磁盘满、IO 错误）时会抛 IOException。
     * 所有设置都是"即时生效"，调用方在 viewModelScope 里裸 `launch`，
     * 异常会走默认 handler 直接**崩溃** —— 用户改一个开关就能把 App 干崩，不可接受。
     *
     * 设置不是关键数据：写失败收敛为记日志、不上抛。代价是这次改动没存上，
     * 但用户下次进设置页看到的是旧值，改一下即可；比崩溃好得多。
     */
    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { store.edit(block) }
            .onFailure { timber.log.Timber.e(it, "写入设置失败") }
    }

    override suspend fun setWeightUnit(unit: WeightUnit) =
        edit { it[Keys.WEIGHT_UNIT] = unit.name }

    override suspend fun setDistanceUnit(unit: DistanceUnit) =
        edit { it[Keys.DISTANCE_UNIT] = unit.name }

    override suspend fun setDefaultRestSeconds(seconds: Int) =
        edit { it[Keys.DEFAULT_REST_SECONDS] = seconds.coerceIn(0, 900) }

    override suspend fun setAutoStartRestTimer(enabled: Boolean) =
        edit { it[Keys.AUTO_START_REST_TIMER] = enabled }

    override suspend fun setRestSoundEnabled(enabled: Boolean) =
        edit { it[Keys.REST_SOUND] = enabled }

    override suspend fun setRestVibrationEnabled(enabled: Boolean) =
        edit { it[Keys.REST_VIBRATION] = enabled }

    override suspend fun setRestTtsEnabled(enabled: Boolean) =
        edit { it[Keys.REST_TTS] = enabled }

    override suspend fun setKeepScreenOn(enabled: Boolean) =
        edit { it[Keys.KEEP_SCREEN_ON] = enabled }

    override suspend fun setAutoFillFromLastSession(enabled: Boolean) =
        edit { it[Keys.AUTO_FILL_LAST] = enabled }

    override suspend fun setWeightStepKg(step: Double) =
        edit { it[Keys.WEIGHT_STEP_KG] = if (step > 0) step else 2.5 }

    override suspend fun setShowRpe(enabled: Boolean) = edit { it[Keys.SHOW_RPE] = enabled }

    override suspend fun setIncludeWarmupInVolume(enabled: Boolean) =
        edit { it[Keys.INCLUDE_WARMUP_VOLUME] = enabled }

    override suspend fun setOneRmFormula(formula: OneRmFormula) =
        edit { it[Keys.ONE_RM_FORMULA] = formula.name }

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    override suspend fun setThemePreset(preset: ThemePreset) =
        edit { it[Keys.THEME_PRESET] = preset.name }

    override suspend fun setCustomThemeColor(slot: CustomThemeSlot, argb: Long?) = edit { prefs ->
        val key = Keys.customThemeKey(slot)
        if (argb == null) prefs.remove(key) else prefs[key] = argb
    }

    override suspend fun setFirstDayOfWeek(day: Int) =
        edit { it[Keys.FIRST_DAY_OF_WEEK] = day.coerceIn(1, 7) }

    override suspend fun setPlanSortOrder(order: PlanSortOrder) =
        edit { it[Keys.PLAN_SORT_ORDER] = order.name }

    override suspend fun setWallpaper(uri: String?) =
        edit { prefs ->
            if (uri == null) prefs.remove(Keys.WALLPAPER_URI) else prefs[Keys.WALLPAPER_URI] = uri
        }

    override suspend fun setWallpaperOpacity(alpha: Float) =
        edit { it[Keys.WALLPAPER_OPACITY] = alpha.coerceIn(0.05f, 0.95f) }

    override suspend fun setNickname(nickname: String) =
        edit { it[Keys.NICKNAME] = nickname.trim() }

    override suspend fun setAvatarUri(fileName: String?) =
        edit { prefs ->
            if (fileName == null) prefs.remove(Keys.AVATAR_URI) else prefs[Keys.AVATAR_URI] = fileName
        }

    override suspend fun setShareDisclaimerAcknowledged(acknowledged: Boolean) =
        edit { it[Keys.SHARE_DISCLAIMER] = acknowledged }

    override suspend fun markShareCardSaved(sessionId: Long) = edit { prefs ->
        val current = prefs[Keys.SHARE_SAVED_SESSIONS].orEmpty()
        prefs[Keys.SHARE_SAVED_SESSIONS] = (current + sessionId.toString()).toSet()
    }

    override suspend fun setWorkoutReminder(enabled: Boolean, days: Set<Int>, hour: Int, minute: Int) = edit {
        it[Keys.REMINDER_ENABLED] = enabled
        it[Keys.REMINDER_DAYS] = days.joinToString(",")
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }

    override suspend fun setBodyWeightReminderEnabled(enabled: Boolean) =
        edit { it[Keys.BODY_REMINDER] = enabled }

    override suspend fun setNotificationPermissionAsked(asked: Boolean) =
        edit { it[Keys.NOTIF_ASKED] = asked }

    override suspend fun setNotificationPermissionDeclined(declined: Boolean) =
        edit { it[Keys.NOTIF_DECLINED] = declined }

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.ONBOARDING_DONE] = completed }

    override suspend fun acceptPrivacy(epochMs: Long) =
        edit { it[Keys.PRIVACY_ACCEPTED_AT] = epochMs }

    override suspend fun setCrashReportingEnabled(enabled: Boolean) =
        edit { it[Keys.CRASH_REPORTING] = enabled }

    override suspend fun setAge(age: Int?) =
        edit { prefs ->
            if (age == null) prefs.remove(Keys.AGE) else prefs[Keys.AGE] = age
        }

    override suspend fun setGender(gender: String?) =
        edit { prefs ->
            if (gender == null) prefs.remove(Keys.GENDER) else prefs[Keys.GENDER] = gender
        }

    override suspend fun setActivityLevel(level: String) =
        edit { it[Keys.ACTIVITY_LEVEL] = level }

    override suspend fun resetToDefaults() {
        store.edit { it.clear() }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            weightUnit = WeightUnit.fromKey(this[Keys.WEIGHT_UNIT]),
            distanceUnit = DistanceUnit.fromKey(this[Keys.DISTANCE_UNIT]),
            defaultRestSeconds = this[Keys.DEFAULT_REST_SECONDS] ?: d.defaultRestSeconds,
            autoStartRestTimer = this[Keys.AUTO_START_REST_TIMER] ?: d.autoStartRestTimer,
            restSoundEnabled = this[Keys.REST_SOUND] ?: d.restSoundEnabled,
            restVibrationEnabled = this[Keys.REST_VIBRATION] ?: d.restVibrationEnabled,
            restTtsEnabled = this[Keys.REST_TTS] ?: d.restTtsEnabled,
            keepScreenOnDuringWorkout = this[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOnDuringWorkout,
            autoFillFromLastSession = this[Keys.AUTO_FILL_LAST] ?: d.autoFillFromLastSession,
            weightStepKg = this[Keys.WEIGHT_STEP_KG] ?: d.weightStepKg,
            showRpe = this[Keys.SHOW_RPE] ?: d.showRpe,
            includeWarmupInVolume = this[Keys.INCLUDE_WARMUP_VOLUME] ?: d.includeWarmupInVolume,
            oneRmFormula = this[Keys.ONE_RM_FORMULA]
                ?.let { key -> OneRmFormula.entries.firstOrNull { it.name == key } }
                ?: d.oneRmFormula,
            themeMode = ThemeMode.fromKey(this[Keys.THEME_MODE]),
            themePreset = ThemePreset.fromKey(this[Keys.THEME_PRESET]),
            customAppBarColor = this[Keys.CUSTOM_APP_BAR],
            customAccentColor = this[Keys.CUSTOM_ACCENT],
            customContentColor = this[Keys.CUSTOM_CONTENT],
            customOtherColor = this[Keys.CUSTOM_OTHER],
            customFontColor = this[Keys.CUSTOM_FONT],
            firstDayOfWeek = this[Keys.FIRST_DAY_OF_WEEK] ?: d.firstDayOfWeek,
            planSortOrder = this[Keys.PLAN_SORT_ORDER]
                ?.let { key -> PlanSortOrder.entries.firstOrNull { it.name == key } }
                ?: d.planSortOrder,
            wallpaperUri = this[Keys.WALLPAPER_URI],
            wallpaperOpacity = this[Keys.WALLPAPER_OPACITY] ?: d.wallpaperOpacity,
            nickname = this[Keys.NICKNAME] ?: d.nickname,
            avatarUri = this[Keys.AVATAR_URI],
            shareDisclaimerAcknowledged = this[Keys.SHARE_DISCLAIMER] ?: false,
            shareSavedSessionIds = this[Keys.SHARE_SAVED_SESSIONS]
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            age = this[Keys.AGE],
            gender = this[Keys.GENDER],
            activityLevel = this[Keys.ACTIVITY_LEVEL] ?: "MODERATE",
            workoutReminderEnabled = this[Keys.REMINDER_ENABLED] ?: false,
            workoutReminderDays = this[Keys.REMINDER_DAYS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet(),
            workoutReminderHour = this[Keys.REMINDER_HOUR] ?: 19,
            workoutReminderMinute = this[Keys.REMINDER_MINUTE] ?: 0,
            bodyWeightReminderEnabled = this[Keys.BODY_REMINDER] ?: false,
            notificationPermissionAsked = this[Keys.NOTIF_ASKED] ?: false,
            notificationPermissionDeclined = this[Keys.NOTIF_DECLINED] ?: false,
            onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
            privacyAcceptedAt = this[Keys.PRIVACY_ACCEPTED_AT],
            crashReportingEnabled = this[Keys.CRASH_REPORTING] ?: false,
        )
    }

    private object Keys {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val AUTO_START_REST_TIMER = booleanPreferencesKey("auto_start_rest_timer")
        val REST_SOUND = booleanPreferencesKey("rest_sound")
        val REST_VIBRATION = booleanPreferencesKey("rest_vibration")
        val REST_TTS = booleanPreferencesKey("rest_tts")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_FILL_LAST = booleanPreferencesKey("auto_fill_last")
        val WEIGHT_STEP_KG = doublePreferencesKey("weight_step_kg")
        val SHOW_RPE = booleanPreferencesKey("show_rpe")
        val INCLUDE_WARMUP_VOLUME = booleanPreferencesKey("include_warmup_volume")
        val ONE_RM_FORMULA = stringPreferencesKey("one_rm_formula")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val CUSTOM_APP_BAR = longPreferencesKey("custom_app_bar")
        val CUSTOM_ACCENT = longPreferencesKey("custom_accent")
        val CUSTOM_CONTENT = longPreferencesKey("custom_content")
        val CUSTOM_OTHER = longPreferencesKey("custom_other")
        val CUSTOM_FONT = longPreferencesKey("custom_font")

        fun customThemeKey(slot: CustomThemeSlot) = when (slot) {
            CustomThemeSlot.APP_BAR -> CUSTOM_APP_BAR
            CustomThemeSlot.ACCENT -> CUSTOM_ACCENT
            CustomThemeSlot.CONTENT -> CUSTOM_CONTENT
            CustomThemeSlot.OTHER -> CUSTOM_OTHER
            CustomThemeSlot.FONT -> CUSTOM_FONT
        }
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        val PLAN_SORT_ORDER = stringPreferencesKey("plan_sort_order")
        val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val SHARE_DISCLAIMER = booleanPreferencesKey("share_disclaimer_ack")
        val SHARE_SAVED_SESSIONS = stringSetPreferencesKey("share_saved_sessions")
        val AGE = intPreferencesKey("metabolic_age")
        val GENDER = stringPreferencesKey("metabolic_gender")
        val ACTIVITY_LEVEL = stringPreferencesKey("metabolic_activity_level")
        val WALLPAPER_OPACITY = floatPreferencesKey("wallpaper_opacity")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_DAYS = stringPreferencesKey("reminder_days")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val BODY_REMINDER = booleanPreferencesKey("body_reminder")
        val NOTIF_ASKED = booleanPreferencesKey("notif_asked")
        val NOTIF_DECLINED = booleanPreferencesKey("notif_declined")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PRIVACY_ACCEPTED_AT = longPreferencesKey("privacy_accepted_at")
        val CRASH_REPORTING = booleanPreferencesKey("crash_reporting")
    }
}
