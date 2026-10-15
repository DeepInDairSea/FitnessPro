package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/** 计划分组（文件夹） */
@Serializable
data class PlanFolder(
    val id: Long,
    val name: String,
    val colorHex: String? = null,
    val sortOrder: Double = 0.0,
)

/** 计划在列表页的轻量视图 —— 只包含渲染所需字段，避免加载动作明细 */
@Serializable
data class PlanCard(
    val id: Long,
    val name: String,
    val folderId: Long? = null,
    val colorHex: String? = null,
    val exerciseCount: Int = 0,
    val totalSets: Int = 0,
    val estimatedDurationSeconds: Int? = null,
    val isFavorite: Boolean = false,
    val lastPerformedAt: Long? = null,
    val updatedAt: Long = 0L,
    val scheduledDays: Set<Int> = emptySet(),
    /** 当日主要训练部位（大肌群自由组合，如 胸+腿）；不影响动作选择的任何限制 */
    val focus: List<MajorGroup> = emptyList(),
    val deletedAt: Long? = null,
)

/** 计划详情（含动作与组级处方） */
@Serializable
data class PlanDetail(
    val id: Long,
    val name: String,
    val description: String? = null,
    val folderId: Long? = null,
    val colorHex: String? = null,
    val defaultRestSeconds: Int? = null,
    val estimatedDurationSeconds: Int? = null,
    val scheduledDays: Set<Int> = emptySet(),
    /** 当日主要训练部位（大肌群自由组合，如 胸+腿）；仅作标记展示，不限制动作选择 */
    val focus: List<MajorGroup> = emptyList(),
    val isFavorite: Boolean = false,
    val exercises: List<PlanExerciseItem> = emptyList(),
    // 编辑页需要
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val totalSets: Int get() = exercises.sumOf { it.targetSets }
}

/** 计划中的一个动作条目 */
@Serializable
data class PlanExerciseItem(
    val id: Long,
    val exercise: ExerciseSnapshot,
    val orderIndex: Double,
    val supersetTag: Int? = null,
    val targetSets: Int,
    val sets: List<PlannedSet> = emptyList(),
    val restSecondsOverride: Int? = null,
    val notes: String? = null,
    val mediaOverride: MediaRef? = null,
)

/** 组级处方 */
@Serializable
data class PlannedSet(
    val id: Long = 0L,
    val setIndex: Int,
    val setType: SetType = SetType.NORMAL,
    val targetReps: String? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRpe: Double? = null,
)

/** 计划预估时长区间 —— 给区间而非精确值，因为热身与找器械时间不可预测 */
@Serializable
data class DurationRange(
    val lowSeconds: Int,
    val highSeconds: Int,
)
