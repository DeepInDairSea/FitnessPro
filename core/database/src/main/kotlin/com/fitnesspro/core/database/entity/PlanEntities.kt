package com.fitnesspro.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "plan_folder")
data class PlanFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null,
    val sortOrder: Double = 0.0,
)

@Entity(
    tableName = "training_plan",
    indices = [
        Index("folderId"),
        Index("deletedAt"),
        Index("updatedAt"),
        Index("lastPerformedAt"),
        Index("isArchived"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlanFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            // 删除分组只把计划移出分组，绝不级联删除计划
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TrainingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val folderId: Long? = null,
    val colorHex: String? = null,
    /** 计划级默认休息秒数；null 表示继承全局设置 */
    val defaultRestSeconds: Int? = null,
    /** 缓存字段：在 updatedAt 变更时重算，避免每次列表渲染重算 */
    val estimatedDurationSeconds: Int? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    /** 列表自定义排序：使用 Double 中值插入，避免频繁重排导致全表重写 */
    val sortOrder: Double = 0.0,
    /** "1,3,5" 表示周一/三/五，空表示不排期 */
    val scheduledDays: String? = null,
    /** 当日主要训练部位：逗号分隔的 MajorGroup 枚举名（如 "CHEST,LEGS"），空表示未设置 */
    val focus: String? = null,
    val lastPerformedAt: Long? = null,
    /** 回收站标记；非空表示在回收站中 */
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "plan_exercise",
    indices = [
        Index("planId"),
        Index(value = ["planId", "orderIndex"], unique = true),
        Index("exerciseDefinitionId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseDefinitionId"],
            // RESTRICT：不允许删除仍被计划引用的动作（改为归档）
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseDefinitionId: Long?,
    /** 冗余快照，防止动作改名影响既有计划与历史 */
    val nameSnapshot: String,
    val typeSnapshot: String,
    val orderIndex: Double,
    /** 同一非空 tag 的动作构成一个超级组 */
    val supersetTag: Int? = null,
    val targetSets: Int = 3,
    val targetReps: String? = "10",
    /** 统一 kg */
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetRpe: Double? = null,
    val restSecondsOverride: Int? = null,
    val tempo: String? = null,
    val notes: String? = null,

    val mediaType: String? = null,
    val mediaPath: String? = null,
    val thumbnailPath: String? = null,
)

/** 组级处方 —— v2.0 相对 v1.1 最关键的模型补齐，用于表达热身组/递减组/金字塔组 */
@Entity(
    tableName = "plan_set",
    indices = [
        Index("planExerciseId"),
        Index(value = ["planExerciseId", "setIndex"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlanExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["planExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planExerciseId: Long,
    val setIndex: Int,
    val setType: String = "NORMAL",
    val targetReps: String? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRpe: Double? = null,
    val isUnilateralSplit: Boolean = false,
)

// ---------------------------------------------------------------------------
// 关系查询 POJO —— 只用于一次性加载明细，列表查询一律走投影以保持轻量
// ---------------------------------------------------------------------------

data class PlanWithExercises(
    @Embedded val plan: TrainingPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val exercises: List<PlanExerciseEntity>,
)

data class PlanExerciseWithSets(
    @Embedded val exercise: PlanExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "planExerciseId")
    val sets: List<PlanSetEntity>,
)

/** 计划列表投影：只取渲染卡片所需字段，避免加载动作明细 */
data class PlanCardRow(
    val id: Long,
    val name: String,
    val folderId: Long?,
    val colorHex: String?,
    val isFavorite: Boolean,
    val estimatedDurationSeconds: Int?,
    val lastPerformedAt: Long?,
    val updatedAt: Long,
    val scheduledDays: String?,
    val focus: String?,
    val deletedAt: Long?,
    val exerciseCount: Int,
    val totalSets: Int,
)
