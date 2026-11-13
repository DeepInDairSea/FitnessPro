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

    // ===== 能耗字段（v12，fit-carculate.md 多维度卡路里估算）=====
    /** 基础 MET 值（动作字典标定，kcal-develp.md MET 映射表） */
    val metValue: Double = 3.5,
    /** 是否自重动作：有效负荷 = 体重 × 自重系数 + 外部配重 */
    val isBodyweight: Boolean = false,
    /** 自重负荷系数（如标准俯卧撑 0.64、引体 1.00、自重深蹲 0.75） */
    val selfWeightCoefficient: Double = 0.5,
    /** 器械效率系数：自由重量 1.0、固定器械 0.85、绳索 0.8 */
    val equipmentEfficiency: Double = 1.0,
    /** 复合动作 TV ×1.2，孤立动作 ×0.8 */
    val isCompound: Boolean = true,
    /** 节奏因子：慢速离心 1.15、暂停 1.20、爆发 1.25 */
    val tempoFactor: Double = 1.0,
    /** 强度区（IntensityZone 枚举名）：MET 校正 ×0.85~1.4 */
    val intensityZone: String = "HYPERTROPHY",
    /** 动作模式（MovementPattern 枚举名） */
    val movementPattern: String = "OTHER",
    /** 代谢类型（MetabolicType 枚举名） */
    val metabolicType: String = "MIXED",
    /** 单次动作平均时长（秒），时长类动作的能耗分摊基准 */
    val averageRepDurationSeconds: Int = 3,
    /** 肌群体积（MuscleVolume 枚举名）：能耗基准层级 */
    val primaryMuscleVolume: String = "LARGE",
    /** 是否适合新手（筛选/推荐用） */
    val isBeginnerFriendly: Boolean = true,
    /** 是否适合热身（热身组能耗减半） */
    val isWarmupSuitable: Boolean = false,

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
