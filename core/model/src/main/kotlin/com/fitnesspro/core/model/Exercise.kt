package com.fitnesspro.core.model

import kotlinx.serialization.Serializable

/** 媒体引用：本地文件只存文件名，外链存完整 URL。绝不存绝对路径。 */
@Serializable
sealed interface MediaRef {
    @Serializable
    data class Local(
        val fileName: String,
        val thumbnailName: String? = null,
        val type: MediaType,
    ) : MediaRef

    @Serializable
    data class Remote(val url: String) : MediaRef

    companion object {
        fun localOrNull(
            type: MediaType,
            fileName: String?,
            thumbnailName: String?,
        ): MediaRef? = when {
            fileName.isNullOrBlank() -> null
            type == MediaType.VIDEO_LINK -> Remote(fileName)
            type.isLocalFile -> Local(fileName, thumbnailName, type)
            else -> null
        }
    }
}

/**
 * 动作库条目（领域模型）。
 * 与 Room 实体分离：实体负责持久化结构，本模型负责业务语义，转换由 :core:data 的 Mapper 完成。
 */
@Serializable
data class Exercise(
    val id: Long,
    val name: String,
    val aliases: List<String> = emptyList(),
    val type: ExerciseType,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    /** 大肌群/类别（v2 分类体系顶层）；历史数据由 primaryMuscle 回填推导 */
    val majorGroup: MajorGroup = MajorGroup.FUNCTIONAL,
    /** 细分肌群（v2 分类体系第二层，中文名即 ID，须属于 majorGroup）；历史数据可能为 null */
    val subGroup: String? = null,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = 90,
    val defaultSets: Int = 3,
    val defaultReps: String? = "10",
    val instructions: String? = null,
    // ===== 能耗维度（v12，卡路里多因素估算的输入）=====
    /** 基础 MET 值 */
    val metValue: Double = 3.5,
    /** 是否自重动作：有效负荷 = 体重 × 自重系数 + 外部配重 */
    val isBodyweight: Boolean = false,
    /** 自重负荷系数（标准俯卧撑 0.64、引体 1.00、自重深蹲 0.75……） */
    val selfWeightCoefficient: Double = 0.5,
    /** 器械效率系数：自由重量 1.0、固定器械 0.85、绳索 0.8 */
    val equipmentEfficiency: Double = 1.0,
    /** 复合动作（多关节）能耗高于孤立动作 */
    val isCompound: Boolean = true,
    /** 节奏因子（慢速离心/暂停/爆发） */
    val tempoFactor: Double = 1.0,
    /** 强度区：MET 校正 ×0.85~1.4 */
    val intensityZone: IntensityZone = IntensityZone.HYPERTROPHY,
    /** 动作模式 */
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    /** 代谢类型 */
    val metabolicType: MetabolicType = MetabolicType.MIXED,
    /** 单次动作平均时长（秒） */
    val averageRepDurationSeconds: Int = 3,
    /** 肌群体积分层（能耗基准层级） */
    val primaryMuscleVolume: MuscleVolumeTier = MuscleVolumeTier.LARGE,
    /** 是否适合新手 */
    val isBeginnerFriendly: Boolean = true,
    /** 是否适合热身 */
    val isWarmupSuitable: Boolean = false,
    val media: MediaRef? = null,
    /** 自定义身份色（hex，如 "#F0653C"）；null 表示跟随主肌群的色系 */
    val colorKey: String? = null,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    /** 回收站：非 null 表示已删除（仅回收站列表使用） */
    val deletedAt: Long? = null,
) {
    /** 命中搜索关键词（用于 FTS 回退路径与内存过滤） */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            aliases.any { it.lowercase().contains(q) }
    }
}

/**
 * 计划内动作的"动作快照"。
 * 冗余保存动作名与类型，保证动作库条目被改名/归档后，历史与计划仍显示当时的名称。
 */
@Serializable
data class ExerciseSnapshot(
    val definitionId: Long?,
    val name: String,
    val type: ExerciseType,
    val isUnilateral: Boolean = false,
    /** 是否自重动作：训练页输入框按"附加负重"语义展示 */
    val isBodyweight: Boolean = false,
    /** 自重负荷系数：有效负荷 = 体重 × 系数 + 附加负重 */
    val selfWeightCoefficient: Double = 0.5,
    /**
     * 主要肌群。不是快照语义（不受"改动作名"影响），而是给计划/训练页做**按部位分区**
     * 用的显示信息：一个计划同时练肩+背时，训练页据此把动作分成"肩部/背部"两块。
     * 动作被删除或自定义时可能为 null —— 此时不分区，退化成平铺列表。
     */
    val primaryMuscle: MuscleGroup? = null,
)

/** 动作库筛选条件 */
@Serializable
data class ExerciseFilter(
    val query: String = "",
    /** 旧版单值细分筛选（保留兼容；新版 UI 使用 majorGroup + subGroup 两级筛选） */
    val muscle: MuscleGroup? = null,
    /** 大肌群/类别筛选（v2） */
    val majorGroup: MajorGroup? = null,
    /** 细分肌群筛选（v2，须与 majorGroup 配对使用） */
    val subGroup: String? = null,
    val equipment: Equipment? = null,
    val type: ExerciseType? = null,
    val favoritesOnly: Boolean = false,
    val customOnly: Boolean = false,
)
