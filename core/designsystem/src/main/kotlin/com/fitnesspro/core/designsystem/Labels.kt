package com.fitnesspro.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitnesspro.core.common.text.UiText
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.model.DataSource
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.WeightUnit
import com.fitnesspro.core.model.DistanceUnit
import com.fitnesspro.core.model.time.TimeProvider

/**
 * 枚举 → 字符串资源 的集中映射。
 *
 * 为什么不把 label 放进枚举：core:model 是纯 Kotlin JVM 模块，没有 Android 资源；
 * 而且文案属于表现层，混进领域模型会让"改文案"变成"改领域模型"。
 */
@StringRes
fun ExerciseType.labelRes(): Int = when (this) {
    ExerciseType.WEIGHT_REPS -> R.string.exercise_type_weight_reps
    ExerciseType.REPS_ONLY -> R.string.exercise_type_reps_only
    ExerciseType.WEIGHT_TIME -> R.string.exercise_type_weight_time
    ExerciseType.TIME -> R.string.exercise_type_time
    ExerciseType.DISTANCE_TIME -> R.string.exercise_type_distance_time
    ExerciseType.WEIGHT_DISTANCE_TIME -> R.string.exercise_type_weight_distance_time
}

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.muscle_chest
    MuscleGroup.UPPER_CHEST -> R.string.muscle_upper_chest
    MuscleGroup.INNER_CHEST -> R.string.muscle_inner_chest
    MuscleGroup.LOWER_CHEST -> R.string.muscle_lower_chest
    MuscleGroup.BACK -> R.string.muscle_back
    MuscleGroup.UPPER_BACK -> R.string.muscle_upper_back
    MuscleGroup.LOWER_BACK -> R.string.muscle_lower_back
    MuscleGroup.LATS -> R.string.muscle_lats
    MuscleGroup.TRAPS -> R.string.muscle_traps
    MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
    MuscleGroup.FRONT_DELT -> R.string.muscle_front_delt
    MuscleGroup.SIDE_DELT -> R.string.muscle_side_delt
    MuscleGroup.REAR_DELT -> R.string.muscle_rear_delt
    MuscleGroup.BICEPS -> R.string.muscle_biceps
    MuscleGroup.TRICEPS -> R.string.muscle_triceps
    MuscleGroup.FOREARMS -> R.string.muscle_forearms
    MuscleGroup.QUADS -> R.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
    MuscleGroup.GLUTES -> R.string.muscle_glutes
    MuscleGroup.CALVES -> R.string.muscle_calves
    MuscleGroup.CORE -> R.string.muscle_core
    MuscleGroup.FULL_BODY -> R.string.muscle_full_body
    MuscleGroup.CARDIO -> R.string.muscle_cardio
    MuscleGroup.NECK -> R.string.muscle_neck
    MuscleGroup.OTHER -> R.string.muscle_other
}

/** 大肌群/类别标签：直接取枚举 displayName（中文名即显示名） */
fun MajorGroup.displayNameOf(): String = displayName

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.SMITH -> R.string.equipment_smith
    Equipment.EZ_BAR -> R.string.equipment_ez_bar
    Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.BAND -> R.string.equipment_band
    // 动作库 v2 新增器械
    Equipment.TRX -> R.string.equipment_trx
    Equipment.MEDICINE_BALL -> R.string.equipment_medicine_ball
    Equipment.BATTLE_ROPE -> R.string.equipment_battle_rope
    Equipment.OTHER -> R.string.equipment_other
}

@StringRes
fun SetType.labelRes(): Int = when (this) {
    SetType.NORMAL -> R.string.set_type_normal
    SetType.WARMUP -> R.string.set_type_warmup
    SetType.DROP -> R.string.set_type_drop
    SetType.FAILURE -> R.string.set_type_failure
    SetType.AMRAP -> R.string.set_type_amrap
}

@StringRes
fun PrType.labelRes(): Int = when (this) {
    PrType.MAX_WEIGHT -> R.string.pr_max_weight
    PrType.MAX_REPS -> R.string.pr_max_reps
    PrType.MAX_VOLUME_SET -> R.string.pr_max_volume_set
    PrType.MAX_E1RM -> R.string.pr_max_e1rm
}

@StringRes
fun WeightUnit.labelRes(): Int =
    if (this == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb

@StringRes
fun DistanceUnit.labelRes(): Int =
    if (this == DistanceUnit.KM) R.string.unit_km else R.string.unit_mi

/** 把 UiText 解析成实际字符串 —— 只允许在 Compose 层调用 */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> if (args.isEmpty()) {
        stringResource(resId)
    } else {
        stringResource(resId, *args.toTypedArray())
    }
    is UiText.Plain -> value
}

@Composable
fun UiText?.asStringOrNull(): String? = this?.asString()

// ---------------------------------------------------------------------------
// 数值格式化
// ---------------------------------------------------------------------------

/**
 * 重量显示：整数不带小数点（60 而不是 60.0）。
 * 训练场景下"60.0 kg"显得啰嗦，用户心里想的就是 60。
 */
fun formatWeight(value: Double, unit: WeightUnit): String {
    val number = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    return "$number ${if (unit == WeightUnit.KG) "kg" else "lb"}"
}

fun formatWeightCompact(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** 时长：mm:ss，超过一小时显示 h:mm:ss */
fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

/**
 * 字节数 → 人类可读（B / KB / MB / GB）。
 * 与 formatVolume 分开：容量是"训练量"，字节是"存储占用"，两者混用会让文案读起来很怪
 * （"3.2 MB kg" 这种拼接错误就是这么来的）。
 */
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes <= 0L -> "0 B"
        bytes < kb -> "$bytes B"
        bytes < mb -> "%.1f KB".format(bytes / kb)
        bytes < gb -> "%.1f MB".format(bytes / mb)
        else -> "%.2f GB".format(bytes / gb)
    }
}

/** 容量：大数值用 k 简写，避免列表里出现 12345.67 kg 这种噪音 */
fun formatVolume(volumeKg: Double): String = when {
    volumeKg >= 100_000 -> "%.0fk kg".format(volumeKg / 1000)
    volumeKg >= 10_000 -> "%.1fk kg".format(volumeKg / 1000)
    volumeKg % 1.0 == 0.0 -> "${volumeKg.toInt()} kg"
    else -> "%.1f kg".format(volumeKg)
}

/**
 * 相对时间："今天 / 昨天 / 3 天前 / 2026-08-01"。
 * 训练记录用相对时间更好理解 —— 用户关心的是"上次练是几天前"。
 */
fun formatRelativeDay(
    epochDay: Long,
    todayEpochDay: Long,
): String = when (val diff = todayEpochDay - epochDay) {
    0L -> "今天"
    1L -> "昨天"
    2L -> "前天"
    in 3L..30L -> "$diff 天前"
    else -> "更早"
}

fun formatDaysAgo(epochDay: Long, todayEpochDay: Long, time: TimeProvider): String =
    formatRelativeDay(epochDay, todayEpochDay).ifEmpty { "" }

/** 数据来源标签 */
fun DataSource.label(): String = when (this) {
    DataSource.MANUAL -> "手动录入"
    DataSource.HEALTH_CONNECT -> "健康数据同步"
}
