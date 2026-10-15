package com.fitnesspro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 个人纪录。每种 PR 类型对每个动作只有一行（唯一索引），破纪录时 UPDATE 覆盖。
 */
@Entity(
    tableName = "personal_record",
    indices = [
        Index("exerciseDefinitionId"),
        Index("achievedAt"),
        Index(value = ["exerciseDefinitionId", "prType"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseDefinitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseDefinitionId: Long,
    val prType: String,
    val value: Double,
    /** "kg" / "reps" / "sec" */
    val unit: String,
    /** 达成时的次数（MAX_WEIGHT 场景） */
    val reps: Int? = null,
    val weightKg: Double? = null,
    val achievedAt: Long,
    val sessionId: Long? = null,
    val setId: Long? = null,
)

/**
 * 身体数据。epochDay 为日期语义（跨时区/夏令时安全）；recordedAt 为精确时刻。
 */
@Entity(
    tableName = "body_measurement",
    indices = [
        Index("epochDay"),
        Index("source"),
    ],
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val recordedAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val heightCm: Double? = null,
    val visceralFatLevel: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val calfCm: Double? = null,
    val bmr: Double? = null,
    val source: String = "MANUAL",
    val notes: String? = null,
)

/**
 * 媒体资产登记表。
 * 存在的价值有两个：① 孤立文件扫描（GC）时的比对基准；
 * ② 媒体占用空间统计。文件本身存放在 filesDir/media/，不在此表中。
 */
@Entity(
    tableName = "media_asset",
    indices = [
        Index(value = ["fileName"], unique = true),
        Index("createdAt"),
    ],
)
data class MediaAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val thumbnailName: String? = null,
    val mediaType: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long,
    /** 最近一次被引用时间，GC 判定用 */
    val lastReferencedAt: Long? = null,
    /**
     * 登记状态：PENDING = 已导入但还没写进引用方（用户点了保存才转 ACTIVE）。
     * GC 对 PENDING 用 24h 短宽限期，对 ACTIVE 用 30 天 ——
     * 这样"导入后没保存"产生的孤儿能被尽快回收，而被引用过的文件不会被误删。
     */
    val state: String = "ACTIVE",
)

/** 种子数据导入标记（单行 KV） */
@Entity(tableName = "app_flag")
data class AppFlagEntity(
    @PrimaryKey val key: String,
    val value: String,
)
