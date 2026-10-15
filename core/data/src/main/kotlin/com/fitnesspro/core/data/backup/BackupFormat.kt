package com.fitnesspro.core.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 备份格式。
 *
 * 为什么用"通用表快照"而不是给每张表手写 DTO：
 *
 * 手写 DTO 的方式，每新增一列就要同步改三处（实体 / DTO / 映射），
 * 漏掉任何一处都会导致**备份静默丢字段** —— 这类 bug 在恢复之前完全不可见，
 * 是最危险的一种。而表快照直接读 SQLite 的列与值，schema 变了它自动跟着变，
 * 只有表本身被删掉时才需要改代码。
 *
 * 代价：产物不是人类友好的领域 JSON。因此额外提供 CSV 导出（见 CsvBackup.kt）
 * 满足"导入 Excel / 迁移到其他 App"的互操作需求。
 */
@Serializable
data class BackupManifest(
    /** 备份格式版本。导入时校验，拒绝比自己更新的备份 */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** 导出时的数据库 schema 版本，仅用于日志与诊断 */
    val databaseVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val exportedAtEpochMs: Long,
    val mediaFileCount: Int,
    val mediaTotalBytes: Long,
    /** 各表行数，用于导入时的一致性校验与预览 */
    val tableRowCounts: Map<String, Int>,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class TableSnapshot(
    /** 列顺序。导入时按列名插入，因此列顺序变化不影响兼容性 */
    val columns: List<String>,
    /**
     * 行数据。每个元素与 columns 一一对应。
     * 类型映射：INTEGER→JsonPrimitive(Long)、REAL→JsonPrimitive(Double)、
     * TEXT→JsonPrimitive(String)、NULL→JsonNull。
     * 本项目 schema 不含 BLOB，若将来引入需要额外的类型标记。
     */
    val rows: List<List<JsonElement>>,
)

@Serializable
data class BackupData(
    /** 表名 → 快照。key 使用真实表名，导入时按固定顺序写入以满足外键约束 */
    val tables: Map<String, TableSnapshot>,
)

/**
 * 导出的表顺序 —— **这个顺序不能随意改**。
 *
 * 导出时顺序无所谓，但导入时必须先插父表再插子表（plan_folder → training_plan →
 * plan_exercise → plan_set），否则外键约束会直接失败。
 * 清空时则必须反向（先删子表）。两边都由这一个列表派生，避免出现两套顺序不一致。
 */
val BACKUP_TABLE_ORDER: List<String> = listOf(
    "plan_folder",
    "exercise_definition",
    "exercise_muscle",
    "training_plan",
    "plan_exercise",
    "plan_set",
    "workout_session",
    "workout_exercise",
    "workout_set",
    "personal_record",
    "body_measurement",
    "media_asset",
    "app_flag",
)

/** 不参与备份的表：FTS 索引表由触发器与内容表自动重建，备份它反而会造成不一致 */
val BACKUP_EXCLUDED_TABLES: Set<String> = setOf(
    "exercise_fts",
    "exercise_fts_content",
    "exercise_fts_segments",
    "exercise_fts_segdir",
    "exercise_fts_docsize",
    "exercise_fts_stat",
    "android_metadata",
    "room_master_table",
    "sqlite_sequence",
)
