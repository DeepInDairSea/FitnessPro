package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/** 身体数据记录。体重是必填项，其余可选 */
@Serializable
data class BodyMeasurement(
    val id: Long = 0L,
    /** 日期语义，避免跨时区/夏令时错位 */
    val epochDay: Long,
    val recordedAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    /** 身高（cm），可空：只在用户录入时才有 */
    val heightCm: Double? = null,
    /** 内脏脂肪等级（1-59，体成分秤口径），可空 */
    val visceralFatLevel: Double? = null,
    val chestCm: Double? = null,
    /** 腰围（cm）—— 腹部指标，配合胸/臀/臂/腿/小腿围一档 */
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    /** 小腿围（cm）—— 用户新增 */
    val calfCm: Double? = null,
    /**
     * 基础代谢 kcal/天 —— 由体重/身高 + 设置里的年龄/性别 估算后随本次写入。
     * 不让用户手填，避免"自己估计"和"自动估算"两个口径混乱。
     * 任一必填项缺失时为 null（保留空 = 资料未补全）。
     */
    val bmr: Double? = null,
    val source: DataSource = DataSource.MANUAL,
    val notes: String? = null,
)

/** 个人纪录 */
@Serializable
data class PersonalRecord(
    val id: Long = 0L,
    val exerciseDefinitionId: Long,
    val exerciseName: String = "",
    val prType: PrType,
    val value: Double,
    val unit: String,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val achievedAt: Long,
    val sessionId: Long? = null,
)

/** 动作维度的历史趋势点 */
@Serializable
data class ExerciseTrendPoint(
    val epochDay: Long,
    val bestWeightKg: Double? = null,
    val estimatedOneRmKg: Double? = null,
    val volumeKg: Double = 0.0,
    val totalReps: Int = 0,
)
