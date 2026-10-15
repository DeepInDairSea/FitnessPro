package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fitnesspro.core.database.entity.AppFlagEntity
import com.fitnesspro.core.database.entity.BodyMeasurementEntity
import com.fitnesspro.core.database.entity.MediaAssetEntity
import com.fitnesspro.core.database.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {

    @Query("SELECT * FROM personal_record WHERE exerciseDefinitionId = :exerciseDefinitionId")
    suspend fun getForExercise(exerciseDefinitionId: Long): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_record WHERE exerciseDefinitionId = :exerciseDefinitionId")
    fun observeForExercise(exerciseDefinitionId: Long): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_record ORDER BY achievedAt DESC")
    fun observeAll(): Flow<List<PersonalRecordEntity>>

    @Query("SELECT COUNT(*) FROM personal_record")
    suspend fun count(): Int

    @Query("SELECT * FROM personal_record WHERE exerciseDefinitionId = :exerciseId AND prType = :prType LIMIT 1")
    suspend fun getByType(exerciseId: Long, prType: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_record WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Long): List<PersonalRecordEntity>

    /**
     * 注意：这里不用 @Upsert。@Upsert 依据主键判定，而本表主键是自增 id（插入时为 0），
     * 无法命中 (exerciseDefinitionId, prType) 的唯一索引，会导致"破纪录变成插入第二行然后崩溃"。
     * 因此由调用方先 getByType 再决定 insert 或 update。
     */
    @Insert
    suspend fun insert(record: PersonalRecordEntity): Long

    @androidx.room.Update
    suspend fun update(record: PersonalRecordEntity)

    @Query("DELETE FROM personal_record WHERE exerciseDefinitionId = :exerciseDefinitionId")
    suspend fun clearForExercise(exerciseDefinitionId: Long)
}

@Dao
interface BodyMeasurementDao {

    @Query("SELECT * FROM body_measurement ORDER BY epochDay DESC, recordedAt DESC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurement WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByDay(epochDay: Long): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurement ORDER BY epochDay DESC, recordedAt DESC LIMIT 1")
    suspend fun latest(): BodyMeasurementEntity?

    /**
     * 最近一次"录过身高"的记录 —— BMR 估算在身高缺失时回退到这里，
     * 不是简单取最后一行，因为有可能用户近期只录了体重没录身高。
     */
    @Query(
        """
        SELECT * FROM body_measurement
        WHERE heightCm IS NOT NULL AND heightCm > 0
        ORDER BY epochDay DESC, recordedAt DESC LIMIT 1
        """,
    )
    suspend fun latestWithHeight(): BodyMeasurementEntity?

    @Insert
    suspend fun insert(item: BodyMeasurementEntity): Long

    @Query("SELECT COUNT(*) FROM body_measurement")
    suspend fun count(): Int

    @androidx.room.Update
    suspend fun update(item: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_asset")
    suspend fun getAll(): List<MediaAssetEntity>

    @Query("SELECT * FROM media_asset WHERE fileName = :fileName")
    suspend fun getByFileName(fileName: String): MediaAssetEntity?

    @Query("SELECT IFNULL(SUM(sizeBytes), 0) FROM media_asset")
    fun observeTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM media_asset")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaAssetEntity): Long

    @Query("DELETE FROM media_asset WHERE fileName = :fileName")
    suspend fun delete(fileName: String)

    @Query("UPDATE media_asset SET lastReferencedAt = :at WHERE fileName = :fileName")
    suspend fun touch(fileName: String, at: Long)

    /** 从"刚导入未引用"翻成"已被引用"。GC 对两种状态用不同宽限期 */
    @Query("UPDATE media_asset SET state = :state, lastReferencedAt = :at WHERE fileName = :fileName")
    suspend fun setState(fileName: String, state: String, at: Long)

    /** 孤立媒体判定：登记表中存在，但没有任何引用方。
     *
     * 注意：刻意**不筛 deletedAt** —— 回收站里的动作/计划也算引用方，
     * 否则"删除 → GC 清理 → 从回收站恢复"会导致演示文件已经没了。
     * workout_exercise 的 mediaPath 与 thumbnailPath 都要比对（漏掉缩略图会被误判）。
     */
    @Query(
        """
        SELECT * FROM media_asset m
        WHERE NOT EXISTS (SELECT 1 FROM exercise_definition e WHERE e.mediaPath = m.fileName OR e.thumbnailPath = m.fileName)
          AND NOT EXISTS (SELECT 1 FROM plan_exercise pe WHERE pe.mediaPath = m.fileName OR pe.thumbnailPath = m.fileName)
          AND NOT EXISTS (SELECT 1 FROM workout_exercise we WHERE we.mediaPath = m.fileName OR we.thumbnailPath = m.fileName)
        """
    )
    suspend fun findOrphanCandidates(): List<MediaAssetEntity>

    /**
     * 备份用：所有"实际被引用"的媒体文件名。
     *
     * 不能只看 media_asset：预置/种子媒体、或登记失败但引用已写入的文件
     * 都不在登记表里，只看表会漏导出 —— 备份恢复后演示图就空了。
     * 这里以引用方表为准做并集，登记表只是补充。
     */
    @Query(
        """
        SELECT mediaPath AS name FROM exercise_definition WHERE mediaPath IS NOT NULL
        UNION
        SELECT thumbnailPath FROM exercise_definition WHERE thumbnailPath IS NOT NULL
        UNION
        SELECT mediaPath FROM plan_exercise WHERE mediaPath IS NOT NULL
        UNION
        SELECT thumbnailPath FROM plan_exercise WHERE thumbnailPath IS NOT NULL
        UNION
        SELECT mediaPath FROM workout_exercise WHERE mediaPath IS NOT NULL
        UNION
        SELECT thumbnailPath FROM workout_exercise WHERE thumbnailPath IS NOT NULL
        UNION
        SELECT fileName FROM media_asset WHERE state = 'ACTIVE'
        """
    )
    suspend fun referencedMediaNames(): List<String>

    /** 同上，但只取缩略图目录里的文件名 */
    @Query(
        """
        SELECT thumbnailName AS name FROM media_asset WHERE state = 'ACTIVE' AND thumbnailName IS NOT NULL
        UNION
        SELECT thumbnailPath FROM exercise_definition WHERE thumbnailPath IS NOT NULL
        UNION
        SELECT thumbnailPath FROM plan_exercise WHERE thumbnailPath IS NOT NULL
        UNION
        SELECT thumbnailPath FROM workout_exercise WHERE thumbnailPath IS NOT NULL
        """
    )
    suspend fun referencedThumbnailNames(): List<String>
}

@Dao
interface AppFlagDao {

    @Query("SELECT value FROM app_flag WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(flag: AppFlagEntity)

    @Query("SELECT COUNT(*) FROM app_flag WHERE `key` = :key")
    suspend fun exists(key: String): Int
}
