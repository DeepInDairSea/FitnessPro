package com.fitnesspro.core.model

/**
 * 动作库 v2 分类体系：大肌群（MajorGroup）→ 细分肌群（SubGroup）→ 动作。
 *
 * SubGroup 用中文名作为稳定 ID（与种子数据/数据库 TEXT 列一致，用户可见可读）；
 * 同名细分（如"股四头肌"同时出现在 腿部/壶铃/TRX/弹力带）按所属大肌群区分，
 * 因此 subGroup 始终与 majorGroup 成对存储与校验。
 *
 * 本表是分类的唯一事实来源：种子数据、UI 筛选、自定义动作编辑都从这里取值，
 * 不允许在 UI 层硬编码肌群名。
 */
object ExerciseTaxonomy {

    /** 细分肌群 → 所属大肌群 的全集（顺序即 UI 展示顺序） */
    val subGroupsOf: Map<MajorGroup, List<String>> = mapOf(
        MajorGroup.CHEST to listOf(
            "胸大肌上束", "胸大肌中束", "胸大肌下束", "胸大肌内侧", "前锯肌",
        ),
        MajorGroup.SHOULDERS to listOf(
            "三角肌前束", "三角肌中束", "三角肌后束", "肩袖肌群", "肩胛稳定肌群",
        ),
        MajorGroup.BACK to listOf(
            "背阔肌宽度", "背阔肌厚度", "竖脊肌", "斜方肌上束", "斜方肌中束",
            "斜方肌中下束", "斜方肌下束", "腰方肌",
        ),
        MajorGroup.LEGS to listOf(
            "股四头肌", "内收肌群", "腘绳肌", "腓肠肌", "比目鱼肌",
            "胫骨前肌", "足部内在肌", "足部稳定肌", "外展肌群", "髂腰肌",
        ),
        MajorGroup.GLUTES to listOf(
            "臀大肌", "臀中肌", "梨状肌", "闭孔肌群",
        ),
        MajorGroup.ARMS to listOf(
            "肱二头肌长头", "肱二头肌短头", "肱二头肌", "肱肌", "肱桡肌", "旋后肌",
            "肱三头肌外侧头", "肱三头肌内侧头", "肱三头肌长头", "肱三头肌",
            "前臂屈肌群", "前臂伸肌群", "旋前肌",
        ),
        MajorGroup.CORE to listOf(
            "腹直肌上部", "腹直肌", "腹直肌下部", "腹斜肌", "腹横肌",
        ),
        MajorGroup.NECK to listOf(
            "胸锁乳突肌", "头夹肌", "斜角肌", "颈半棘肌",
        ),
        MajorGroup.KETTLEBELL to listOf(
            "臀大肌", "股四头肌", "全身", "三角肌", "腘绳肌", "握力", "核心", "腹斜肌", "肩部稳定",
        ),
        MajorGroup.TRX to listOf(
            "背阔肌", "胸大肌", "股四头肌", "核心", "腹直肌下部", "腹斜肌",
            "肱二头肌", "肱三头肌", "三角肌后束",
        ),
        MajorGroup.MED_BALL_AND_ROPE to listOf(
            "全身", "胸大肌", "腹斜肌", "腹直肌", "核心", "肩部",
        ),
        MajorGroup.FUNCTIONAL to listOf(
            "全身", "股四头肌", "肩部", "背部", "胸部",
        ),
        MajorGroup.CALISTHENICS to listOf(
            "核心", "背部", "肩部", "胸部", "脊柱",
        ),
        MajorGroup.RESISTANCE_BAND to listOf(
            "股四头肌", "臀大肌", "背阔肌", "三角肌后束", "胸大肌", "肩袖", "三角肌中束",
            "三角肌前束", "肱二头肌", "肱三头肌", "臀中肌", "胫骨前肌", "前臂伸肌", "腹横肌",
        ),
        MajorGroup.PILATES to listOf(
            "腹横肌", "腹直肌", "腹斜肌", "臀中肌", "竖脊肌", "臀大肌", "核心",
        ),
        MajorGroup.YOGA to listOf(
            "肩部", "胸大肌", "核心", "股四头肌", "臀大肌", "臀中肌", "腹斜肌",
            "背部", "脊柱", "梨状肌", "腹直肌",
        ),
        MajorGroup.WARMUP to listOf(
            "全身", "髋屈肌", "腘绳肌", "髋部", "髋关节", "肩关节", "关节",
            "脊柱", "肩袖", "臀中肌", "臀大肌", "腹横肌", "核心",
        ),
        MajorGroup.STRETCH to listOf(
            "胸大肌", "背阔肌", "肱三头肌", "三角肌", "股四头肌", "腘绳肌", "腓肠肌",
            "臀大肌", "髂腰肌", "脊柱", "颈部", "背部",
        ),
    )

    /** 所有合法的 (major, sub) 组合 */
    val validPairs: Set<Pair<MajorGroup, String>> =
        subGroupsOf.flatMap { (major, subs) -> subs.map { major to it } }.toSet()

    /** 某大肌群下的细分肌群列表 */
    fun subGroupsOf(major: MajorGroup): List<String> = subGroupsOf[major].orEmpty()

    /** 校验 (major, sub) 组合是否合法 */
    fun isValid(major: MajorGroup, subGroup: String): Boolean =
        subGroupsOf[major]?.contains(subGroup) == true

    /**
     * 细分肌群 → 旧细分枚举（MuscleGroup）的映射。
     * MuscleGroup 继续承担**配色**与**训练页按部位分区**的职责，从 subGroup 推导；
     * 未列出的组合回退到大肌群的代表色（[MajorGroup.colorOf]）。
     */
    fun primaryMuscleOf(major: MajorGroup, subGroup: String): MuscleGroup {
        val specific = when (subGroup) {
            "胸大肌上束" -> MuscleGroup.UPPER_CHEST
            "胸大肌中束", "胸大肌", "前锯肌" -> MuscleGroup.CHEST
            "胸大肌下束" -> MuscleGroup.LOWER_CHEST
            "胸大肌内侧" -> MuscleGroup.INNER_CHEST
            "三角肌前束" -> MuscleGroup.FRONT_DELT
            "三角肌中束" -> MuscleGroup.SIDE_DELT
            "三角肌", "肩部" -> MuscleGroup.SHOULDERS
            "三角肌后束" -> MuscleGroup.REAR_DELT
            "肩袖肌群", "肩胛稳定肌群", "肩袖" -> MuscleGroup.SHOULDERS
            "背阔肌宽度", "背阔肌厚度", "背阔肌" -> MuscleGroup.LATS
            "竖脊肌" -> MuscleGroup.LOWER_BACK
            "斜方肌上束", "斜方肌中束", "斜方肌中下束", "斜方肌下束" -> MuscleGroup.TRAPS
            "腰方肌" -> MuscleGroup.LOWER_BACK
            "股四头肌" -> MuscleGroup.QUADS
            "内收肌群", "髂腰肌" -> MuscleGroup.OTHER
            "腘绳肌" -> MuscleGroup.HAMSTRINGS
            "腓肠肌", "比目鱼肌", "胫骨前肌", "足部内在肌", "足部稳定肌" -> MuscleGroup.CALVES
            "外展肌群" -> MuscleGroup.GLUTES
            "臀大肌", "臀中肌", "梨状肌", "闭孔肌群" -> MuscleGroup.GLUTES
            "肱二头肌长头", "肱二头肌短头", "肱二头肌", "肱肌", "肱桡肌", "旋后肌" -> MuscleGroup.BICEPS
            "肱三头肌外侧头", "肱三头肌内侧头", "肱三头肌长头", "肱三头肌" -> MuscleGroup.TRICEPS
            "前臂屈肌群", "前臂伸肌群", "旋前肌", "握力" -> MuscleGroup.FOREARMS
            "腹直肌上部", "腹直肌", "腹直肌下部", "腹斜肌", "腹横肌", "脊柱" -> MuscleGroup.CORE
            "胸锁乳突肌", "头夹肌", "斜角肌", "颈半棘肌", "颈部" -> MuscleGroup.NECK
            "全身" -> MuscleGroup.FULL_BODY
            "髋屈肌", "髋部", "髋关节", "关节" -> MuscleGroup.OTHER
            "肩关节" -> MuscleGroup.SHOULDERS
            else -> null
        }
        return specific ?: major.colorOf
    }

    /**
     * 旧版细分（MuscleGroup）→ 新版大肌群 的回填映射。
     * 仅用于数据库迁移时给历史数据补 majorGroup；新数据一律来自分类表。
     */
    fun legacyMajorOf(muscle: MuscleGroup): MajorGroup = when (muscle) {
        MuscleGroup.CHEST, MuscleGroup.UPPER_CHEST, MuscleGroup.INNER_CHEST, MuscleGroup.LOWER_CHEST ->
            MajorGroup.CHEST
        MuscleGroup.SHOULDERS, MuscleGroup.FRONT_DELT, MuscleGroup.SIDE_DELT, MuscleGroup.REAR_DELT ->
            MajorGroup.SHOULDERS
        MuscleGroup.BACK, MuscleGroup.UPPER_BACK, MuscleGroup.LOWER_BACK, MuscleGroup.LATS, MuscleGroup.TRAPS ->
            MajorGroup.BACK
        MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES -> MajorGroup.LEGS
        MuscleGroup.GLUTES -> MajorGroup.GLUTES
        MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS -> MajorGroup.ARMS
        MuscleGroup.NECK -> MajorGroup.NECK
        MuscleGroup.CORE -> MajorGroup.CORE
        MuscleGroup.FULL_BODY, MuscleGroup.CARDIO, MuscleGroup.OTHER -> MajorGroup.FUNCTIONAL
    }
}
