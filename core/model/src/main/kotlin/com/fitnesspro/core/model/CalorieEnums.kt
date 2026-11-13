package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/**
 * 动作模式（fit-carculate.md 维度 1）：训练编程与能耗修正的核心分类。
 * 同名动作因模式不同，力学结构与能耗差异巨大。
 */
@Serializable
enum class MovementPattern {
    /** 水平推：卧推、俯卧撑、器械推胸 */
    HORIZONTAL_PUSH,

    /** 水平拉：划船、反向飞鸟 */
    HORIZONTAL_PULL,

    /** 垂直推：推举、倒立俯卧撑 */
    VERTICAL_PUSH,

    /** 垂直拉：引体、下拉 */
    VERTICAL_PULL,

    /** 蹲：深蹲、箭步蹲、腿举 */
    SQUAT,

    /** 髋铰链：硬拉、罗马尼亚硬拉、壶铃摇摆 */
    HINGE,

    /** 弓步/单腿：保加利亚分腿蹲、单腿硬拉 */
    LUNGE,

    /** 旋转：俄罗斯转体、绳索斜拉 */
    ROTATION,

    /** 抗旋转：帕罗夫压、单侧农夫行走 */
    ANTI_ROTATION,

    /** 等长：平板支撑、靠墙静蹲 */
    ISOMETRIC,

    /** 爆发：高翻、跳箱、波比跳 */
    EXPLOSIVE,

    /** 孤立：弯举、腿屈伸、侧平举 */
    ISOLATION,

    OTHER,
    ;

    companion object {
        fun fromNameOrNull(name: String?): MovementPattern? =
            entries.firstOrNull { it.name == name }
    }
}

/** 强度区（fit-carculate.md 维度 6）：影响 MET 校正与 EPOC */
@Serializable
enum class IntensityZone {
    /** 力量 85-100% 1RM：单位时间能耗高 */
    STRENGTH,

    /** 增肌 67-85% 1RM */
    HYPERTROPHY,

    /** 耐力 <67% 1RM：单位时间能耗低 */
    ENDURANCE,

    /** 爆发 30-60% 快速发力：能耗高、EPOC 高 */
    POWER,
    ;

    companion object {
        fun fromNameOrNull(name: String?): IntensityZone? =
            entries.firstOrNull { it.name == name }
    }
}

/** 代谢类型：有氧/无氧/混合，决定供能特征与 EPOC 倾向 */
@Serializable
enum class MetabolicType {
    AEROBIC,
    ANAEROBIC,
    MIXED,
    ;

    companion object {
        fun fromNameOrNull(name: String?): MetabolicType? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * 肌群体积分层：决定动作的能耗基准层级。
 * 命名说明：方案文档中为 MuscleVolume，但项目里已有同名的"肌群容量趋势"数据类，
 * 为避免混淆改名为 MuscleVolumeTier。
 */
@Serializable
enum class MuscleVolumeTier {
    /** 大肌群（胸、背、腿、臀） */
    LARGE,

    /** 中肌群（肩、手臂） */
    MEDIUM,

    /** 小肌群（前臂、小腿、核心） */
    SMALL,
    ;

    companion object {
        fun fromNameOrNull(name: String?): MuscleVolumeTier? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * 卡路里估算层级（kcal-develp.md 四级降级）。
 * 数据越全层级越高、精度越高；数据缺失自动降级，绝不崩溃。
 */
enum class EstimationLevel {
    /** L1 多因素回归：身高+年龄+体成分+训练容量（精度最高） */
    L1_MULTI_FACTOR,

    /** L2 简化多因素：身高+年龄+体重+训练容量 */
    L2_SIMPLIFIED,

    /** L3 TV 校正 MET：体重+训练容量 */
    L3_TV_ADJUSTED,

    /** L4 标准 MET：仅体重（兜底） */
    L4_WEIGHT_ONLY,
    ;

    /** 面向用户的精度星级（1-5），展示用 */
    val accuracyStars: Int
        get() = when (this) {
            L1_MULTI_FACTOR -> 5
            L2_SIMPLIFIED -> 4
            L3_TV_ADJUSTED -> 3
            L4_WEIGHT_ONLY -> 2
        }
}
