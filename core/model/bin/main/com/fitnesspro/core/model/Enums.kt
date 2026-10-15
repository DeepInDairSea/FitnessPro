package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/**
 * 动作类型 —— 决定记录表单渲染哪些字段，以及容量如何计算。
 * 禁止在 UI 层硬编码"重量 + 次数"之类的字段组合，一律由本枚举驱动。
 */
@Serializable
enum class ExerciseType {
    /** 重量 + 次数，如卧推、深蹲 */
    WEIGHT_REPS,

    /** 仅次数（自重动作），如引体向上、俯卧撑；容量 = (体重 + 附加重量) × 次数 */
    REPS_ONLY,

    /** 重量 + 时长，如负重平板支撑 */
    WEIGHT_TIME,

    /** 仅时长，如平板支撑、跳绳计时 */
    TIME,

    /** 距离 + 时长，如跑步机、划船机 */
    DISTANCE_TIME,

    /** 重量 + 距离 + 时长，如农夫行走 */
    WEIGHT_DISTANCE_TIME,
    ;

    /** 是否需要输入重量 */
    val usesWeight: Boolean
        get() = this == WEIGHT_REPS || this == WEIGHT_TIME ||
            this == WEIGHT_DISTANCE_TIME || this == REPS_ONLY

    /** 是否需要输入次数 */
    val usesReps: Boolean
        get() = this == WEIGHT_REPS || this == REPS_ONLY

    /** 是否需要输入时长 */
    val usesDuration: Boolean
        get() = this == WEIGHT_TIME || this == TIME ||
            this == DISTANCE_TIME || this == WEIGHT_DISTANCE_TIME

    /** 是否需要输入距离 */
    val usesDistance: Boolean
        get() = this == DISTANCE_TIME || this == WEIGHT_DISTANCE_TIME

    /** 是否参与"重量容量"统计（kg·次 / kg·分）。TIME 与 DISTANCE_TIME 不参与 */
    val contributesToWeightVolume: Boolean
        get() = this == WEIGHT_REPS || this == REPS_ONLY ||
            this == WEIGHT_TIME || this == WEIGHT_DISTANCE_TIME
}

@Serializable
enum class MuscleGroup {
    // 胸部系（同橙色系不同深浅）
    CHEST, UPPER_CHEST, INNER_CHEST, LOWER_CHEST,
    // 背部系（同宝蓝色系不同深浅）
    BACK, UPPER_BACK, LOWER_BACK, LATS, TRAPS,
    // 肩部系（同电紫色系不同深浅）
    SHOULDERS, FRONT_DELT, SIDE_DELT, REAR_DELT,
    // 手臂
    BICEPS, TRICEPS, FOREARMS,
    // 腿部
    QUADS, HAMSTRINGS, GLUTES, CALVES,
    // 颈部（动作库 v2 新增细分）
    NECK,
    // 其它
    CORE, FULL_BODY, CARDIO, OTHER,
}

/**
 * 大肌群 / 类别 —— 动作库的顶层分类（v2 分类体系）。
 *
 * 层级：大肌群（MajorGroup）→ 细分肌群（SubGroup，见 ExerciseTaxonomy）→ 动作。
 * 与 [MuscleGroup]（细分维度，驱动配色与训练页分区）是两个正交概念：
 * 动作同时携带 majorGroup（顶层归属）与 subGroup（细分归属）。
 * TEXT 存储枚举名，新增枚举值无需数据库迁移。
 */
@Serializable
enum class MajorGroup(val displayName: String, /** 复用细分肌群的身份色系 */ val colorOf: MuscleGroup) {
    /** 胸大肌、前锯肌 */
    CHEST("胸部", MuscleGroup.CHEST),

    /** 三角肌、肩袖、肩胛稳定肌群 */
    SHOULDERS("肩部", MuscleGroup.SHOULDERS),

    /** 背阔肌、斜方肌、竖脊肌、腰方肌 */
    BACK("背部", MuscleGroup.BACK),

    /** 股四头肌、腘绳肌、内收肌、外展肌、小腿、髂腰肌 */
    LEGS("腿部", MuscleGroup.QUADS),

    /** 臀大肌、臀中肌、梨状肌 */
    GLUTES("臀部", MuscleGroup.GLUTES),

    /** 肱二头肌、肱三头肌、前臂、旋前/旋后肌 */
    ARMS("手臂", MuscleGroup.BICEPS),

    /** 腹直肌、腹斜肌、腹横肌 */
    CORE("核心", MuscleGroup.CORE),

    /** 胸锁乳突肌、斜角肌、头夹肌 */
    NECK("颈部", MuscleGroup.NECK),

    /** 壶铃专项动作 */
    KETTLEBELL("壶铃", MuscleGroup.FULL_BODY),

    /** 悬吊训练动作 */
    TRX("TRX", MuscleGroup.LATS),

    /** 药球、战绳专项 */
    MED_BALL_AND_ROPE("药球与战绳", MuscleGroup.CARDIO),

    /** 爆发力、全身协调 */
    FUNCTIONAL("功能性", MuscleGroup.FULL_BODY),

    /** 街头健身、体操动作 */
    CALISTHENICS("自重补充", MuscleGroup.OTHER),

    /** 弹力带全身训练 */
    RESISTANCE_BAND("弹力带", MuscleGroup.FOREARMS),

    /** 普拉提核心动作 */
    PILATES("普拉提", MuscleGroup.CORE),

    /** 瑜伽体式 */
    YOGA("瑜伽", MuscleGroup.CORE),

    /** 动态热身 */
    WARMUP("热身", MuscleGroup.CARDIO),

    /** 静态拉伸 */
    STRETCH("拉伸", MuscleGroup.OTHER),
    ;

    companion object {
        fun fromKey(key: String?): MajorGroup? =
            key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}

@Serializable
enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, SMITH, EZ_BAR,
    BODYWEIGHT, KETTLEBELL, BAND, OTHER,
    // 动作库 v2 新增器械类型
    TRX, MEDICINE_BALL, BATTLE_ROPE,
}

@Serializable
enum class MediaType {
    NONE, GIF, IMAGE, VIDEO_LOCAL, VIDEO_LINK,
    ;

    val isLocalFile: Boolean
        get() = this == GIF || this == IMAGE || this == VIDEO_LOCAL
}

@Serializable
enum class SetType {
    /** 正式组 —— 参与 PR 与容量统计 */
    NORMAL,

    /** 热身组 —— 默认不参与 PR 与容量统计 */
    WARMUP,

    /** 递减组 */
    DROP,

    /** 力竭组 */
    FAILURE,

    /** 极限次数组 */
    AMRAP,
}

@Serializable
enum class SetStatus {
    PENDING, COMPLETED, FAILED, SKIPPED,
}

@Serializable
enum class ExerciseStatus {
    PENDING, IN_PROGRESS, COMPLETED, SKIPPED, REPLACED,
}

@Serializable
enum class SessionStatus {
    IN_PROGRESS, COMPLETED, ABANDONED,
}

@Serializable
enum class PrType {
    /** 单组最大重量（需要有效次数 ≥ 1） */
    MAX_WEIGHT,

    /** 同一重量下的最大次数（由 MAX_E1RM 派生，v1.1 启用） */
    MAX_REPS,

    /** 单组最大容量 */
    MAX_VOLUME_SET,

    /** 最大估算 1RM */
    MAX_E1RM,
}

@Serializable
enum class OneRmFormula {
    /** 自动：次数 ≤ 6 用 Brzycki，否则用 Epley。Brzycki 在低次数更准，Epley 在高次数更稳 */
    AUTO,
    EPLEY,
    BRZYCKI,
    LOMBARDI,
}

@Serializable
enum class DataSource {
    MANUAL, HEALTH_CONNECT,
}

@Serializable
enum class WeightUnit {
    KG, LB,
    ;

    companion object {
        fun fromKey(key: String?): WeightUnit = if (key == LB.name) LB else KG
    }
}

@Serializable
enum class DistanceUnit {
    KM, MI,
    ;

    companion object {
        fun fromKey(key: String?): DistanceUnit = if (key == MI.name) MI else KM
    }
}

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK,
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * 主题配色预设。每个预设定义 标题栏 / 强调色 / 内容区 / 其他色 四个角色，
 * 深/浅两套完整色板由 designsystem 根据 These 角色生成。
 */
@Serializable
enum class ThemePreset {
    /** 生动绿 */
    VIVID_GREEN,

    /** 活力绿 */
    FRESH_GREEN,

    /** 极简白 */
    PURE_WHITE,

    /** 深海蓝 */
    OCEAN_BLUE,

    /** 活力橙 */
    SUNSET_ORANGE,

    /** Bento 软UI（默认；便当盒布局 + 软质感悬浮卡片，灰白底/珊瑚橙/电紫/宝蓝点缀） */
    BENTO_SOFT,
    ;

    companion object {
        fun fromKey(key: String?): ThemePreset =
            entries.firstOrNull { it.name == key } ?: BENTO_SOFT
    }
}

/**
 * 媒体登记状态。
 * PENDING = 已导入但尚未写入引用方（或引用方已被删除）；ACTIVE = 正被引用。
 */
@Serializable
enum class MediaAssetState {
    PENDING, ACTIVE,
    ;

    companion object {
        fun fromKey(key: String?): MediaAssetState =
            entries.firstOrNull { it.name == key } ?: ACTIVE
    }
}

/** 自定义主题的可调颜色槽位（与 设置页 -- 预设名 一一对应） */
enum class CustomThemeSlot {
    /** 顶栏（标题区）颜色 */
    APP_BAR,

    /** 强调色（按钮/选中态） */
    ACCENT,

    /** 内容展示区背景 */
    CONTENT,

    /** 其他颜色（卡片分层/次级容器） */
    OTHER,

    /** 字体颜色 */
    FONT,
}

/** 列表排序方式 */
@Serializable
enum class PlanSortOrder {
    /** 最近编辑 */
    RECENTLY_EDITED,

    /** 最近训练 */
    RECENTLY_PERFORMED,

    /** 自定义拖拽顺序 */
    MANUAL,

    /** 名称 */
    NAME,
}
