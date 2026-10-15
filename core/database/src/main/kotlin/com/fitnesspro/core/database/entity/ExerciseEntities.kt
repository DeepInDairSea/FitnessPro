package com.fitnesspro.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 动作库条目。
 *
 * name / aliases / type / primaryMuscle / equipment 等字段使用 TEXT 存枚举名，
 * 由 TypeConverter 负责转换 —— 这样 schema 对枚举顺序变化不敏感（新增枚举值不需要迁移）。
 */
@Entity(
    tableName = "exercise_definition",
    indices = [
        Index("name"),
        Index("isCustom"),
        Index("isArchived"),
        Index("usageCount"),
        Index("primaryMuscle"),
        Index("majorGroup"),
        Index("equipment"),
    ],
)
data class ExerciseDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 逗号分隔的别名，用于搜索（"卧推,bench press,平板卧推"） */
    val aliases: String? = null,
    val type: String,
    val primaryMuscle: String,
    val equipment: String,
    /** 大肌群/类别（v2 分类体系，MajorGroup 枚举名）；历史数据由迁移按 primaryMuscle 回填 */
    val majorGroup: String? = null,
    /** 细分肌群（v2 分类体系，中文名即 ID，须与 majorGroup 配对合法）；历史数据为 null */
    val subGroup: String? = null,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = 90,
    val defaultSets: Int = 3,
    val defaultReps: String? = "10",
    val instructions: String? = null,

    val mediaType: String = "NONE",
    /** 本地文件名（filesDir/media 下）或外链 URL —— 绝不存绝对路径 */
    val mediaPath: String? = null,
    val thumbnailPath: String? = null,

    /** 自定义身份色（hex，如 "#F0653C"）；null = 跟随主肌群色系 */
    val colorKey: String? = null,

    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    /** 归档：不再出现在选择器，但历史记录仍能正确解析显示 */
    val isArchived: Boolean = false,
    /** 回收站：非 null 表示已删除（30 天后由清理 Worker 彻底删除） */
    val deletedAt: Long? = null,

    /** 使用频次与最近使用时间，用于"最近使用/常用"排序 */
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,

    val createdAt: Long,
    val updatedAt: Long,
)

/** 次要肌群（多对多） */
@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exerciseId", "muscle"],
    indices = [Index("exerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseMuscleEntity(
    val exerciseId: Long,
    val muscle: String,
)

/**
 * FTS4 外部内容表，用于动作名与别名的全文检索。
 * contentEntity 指向 exercise_definition，SQLite 触发器由 Room 自动创建以保持同步。
 * 查询方式：SELECT * FROM exercise_definition WHERE rowid IN (SELECT rowid FROM exercise_fts WHERE exercise_fts MATCH :q)
 */
@Fts4(contentEntity = ExerciseDefinitionEntity::class)
@Entity(tableName = "exercise_fts")
data class ExerciseFtsEntity(
    val name: String,
    val aliases: String?,
)
