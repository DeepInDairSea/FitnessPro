package com.fitnesspro.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MuscleGroup

/**
 * 身份色系（Identity Colors）。
 *
 * 规则：**同一部位共用一个色相，细分肌群只用深浅区分** ——
 * 上胸 / 中缝 / 下胸 都是橙，但一眼能看出"这是胸部家族，且不是一个东西"。
 * 颜色是固定的身份属性，不随主题切换（与 Material 强调色分工不同）。
 */
fun muscleColor(muscle: MuscleGroup): Color = when (muscle) {
    // 胸 = 橙色系（浅 → 深）
    MuscleGroup.INNER_CHEST -> Color(0xFFF5987A)
    MuscleGroup.CHEST -> Color(0xFFF0653C)
    MuscleGroup.UPPER_CHEST -> Color(0xFFD84E26)
    MuscleGroup.LOWER_CHEST -> Color(0xFFB8401E)

    // 背 = 宝蓝色系（浅 → 深）
    MuscleGroup.TRAPS -> Color(0xFF7E90F5)
    MuscleGroup.UPPER_BACK -> Color(0xFF5470EE)
    MuscleGroup.BACK -> Color(0xFF2B3FE0)
    MuscleGroup.LATS -> Color(0xFF2233B8)
    MuscleGroup.LOWER_BACK -> Color(0xFF1A2894)

    // 肩 = 电紫色系（浅 → 深）
    MuscleGroup.FRONT_DELT -> Color(0xFFA875F2)
    MuscleGroup.SHOULDERS -> Color(0xFF7B2FE8)
    MuscleGroup.SIDE_DELT -> Color(0xFF6424C4)
    MuscleGroup.REAR_DELT -> Color(0xFF4E1A9E)

    // 手臂
    MuscleGroup.BICEPS -> Color(0xFFD9437A)
    MuscleGroup.TRICEPS -> Color(0xFFC23A55)
    MuscleGroup.FOREARMS -> Color(0xFFA9714B)

    // 腿
    MuscleGroup.QUADS -> Color(0xFFF0B429)
    MuscleGroup.HAMSTRINGS -> Color(0xFFC98E1B)
    MuscleGroup.GLUTES -> Color(0xFFE86A9A)
    MuscleGroup.CALVES -> Color(0xFF2FA98C)

    // 其它
    MuscleGroup.CORE -> Color(0xFF22A7B8)
    MuscleGroup.CARDIO -> Color(0xFFD64FA0)
    MuscleGroup.FULL_BODY -> Color(0xFF4A5578)
    MuscleGroup.NECK -> Color(0xFF6B9AC4)
    MuscleGroup.OTHER -> Color(0xFF8E8B96)
}

/**
 * 大肌群/类别身份色：复用该大肌群代表的细分肌群色系（见 MajorGroup.colorOf），
 * 保证动作库筛选、计划卡片、启动页等场景的色彩语言一致。
 */
fun majorColor(major: MajorGroup): Color = muscleColor(major.colorOf)

/** 器械身份色：与肌群色系互不冲突，动作列表/筛选处可辨识器械类型 */
fun equipmentColor(equipment: Equipment): Color = when (equipment) {
    Equipment.BARBELL -> Color(0xFF34435E)
    Equipment.DUMBBELL -> Color(0xFF3E6FB0)
    Equipment.MACHINE -> Color(0xFF5B7C99)
    Equipment.CABLE -> Color(0xFF2FA5A0)
    Equipment.SMITH -> Color(0xFF7C8894)
    Equipment.EZ_BAR -> Color(0xFF8FA3BF)
    Equipment.BODYWEIGHT -> Color(0xFF4CAF6E)
    Equipment.KETTLEBELL -> Color(0xFF8A5A33)
    Equipment.BAND -> Color(0xFF9C6BD3)
    // 动作库 v2 新增器械
    Equipment.TRX -> Color(0xFFE0A458)
    Equipment.MEDICINE_BALL -> Color(0xFF5C8D89)
    Equipment.BATTLE_ROPE -> Color(0xFFB3572D)
    Equipment.OTHER -> Color(0xFF8E8B96)
}

/**
 * 新建/编辑动作的色板：7 个色相家族 × 4 档深浅，共 28 个可选身份色。
 * 未选择时动作颜色跟随主肌群（[muscleColor]）。
 */
val ExerciseColorPalette: List<Color> = listOf(
    // 橙
    Color(0xFFF5987A), Color(0xFFF0653C), Color(0xFFD84E26), Color(0xFFB8401E),
    // 黄
    Color(0xFFF6CC5C), Color(0xFFF0B429), Color(0xFFC98E1B), Color(0xFFA5740F),
    // 粉红
    Color(0xFFF08BB0), Color(0xFFE86A9A), Color(0xFFD9437A), Color(0xFFB83262),
    // 宝蓝
    Color(0xFF7E90F5), Color(0xFF2B3FE0), Color(0xFF2233B8), Color(0xFF1A2894),
    // 紫
    Color(0xFFA875F2), Color(0xFF7B2FE8), Color(0xFF6424C4), Color(0xFF4E1A9E),
    // 青
    Color(0xFF6FD0C4), Color(0xFF2FA98C), Color(0xFF1F8873), Color(0xFF156B58),
    // 灰
    Color(0xFFC4C1CC), Color(0xFF8E8B96), Color(0xFF6B6975), Color(0xFF4A4852),
)

/** colorKey 列存 hex 字符串（如 "#F0653C"）；解析失败返回 null（回退肌群色） */
fun colorFromKey(colorKey: String?): Color? {
    val raw = colorKey?.takeIf { it.length == 7 && it.startsWith('#') } ?: return null
    val value = raw.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}

/** colorKey 的标准序列化：6 位 RGB 大写 hex（alpha 恒为不透明，不参与存储） */
fun colorKeyOf(color: Color): String {
    val argb = color.toArgb()
    return "#%06X".format(argb and 0xFFFFFF)
}

/** 动作的身份色：优先自定义 colorKey，否则跟随肌群 */
fun exerciseIdentityColor(colorKey: String?, muscle: MuscleGroup): Color =
    colorFromKey(colorKey) ?: muscleColor(muscle)
