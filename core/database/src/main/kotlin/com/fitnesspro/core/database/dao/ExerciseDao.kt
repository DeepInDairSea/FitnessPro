package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.ExerciseMuscleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    @Query("SELECT * FROM exercise_definition WHERE id = :id")
    fun observeById(id: Long): Flow<ExerciseDefinitionEntity?>

    @Query("SELECT * FROM exercise_definition WHERE id = :id")
    suspend fun getById(id: Long): ExerciseDefinitionEntity?

    @Query("SELECT * FROM exercise_definition WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ExerciseDefinitionEntity>

    @Query(
        """
        SELECT * FROM exercise_definition
        WHERE isArchived = 0 AND deletedAt IS NULL
        ORDER BY usageCount DESC, name ASC
        LIMIT :limit
        """
    )
    fun observeRecentlyUsed(limit: Int = 20): Flow<List<ExerciseDefinitionEntity>>

    @Query(
        """
        SELECT * FROM exercise_definition
        WHERE isArchived = 0 AND deletedAt IS NULL AND isFavorite = 1
        ORDER BY name ASC
        """
    )
    fun observeFavorites(): Flow<List<ExerciseDefinitionEntity>>

    /**
     * 全文检索。exercise_fts 是 contentEntity = exercise_definition 的 FTS4 表，
     * 其 rowid 与 exercise_definition.id 一一对应。
     */
    @Query(
        """
        SELECT * FROM exercise_definition
        WHERE isArchived = 0 AND deletedAt IS NULL
          AND id IN (SELECT rowid FROM exercise_fts WHERE exercise_fts MATCH :ftsQuery)
        ORDER BY usageCount DESC, name ASC
        LIMIT :limit
        """
    )
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<ExerciseDefinitionEntity>>

    /**
     * 中文回退路径：FTS4 无中文分词，"卧推" 可能匹配不到 "平板卧推" 之外的场景，
     * 因此在使用 FTS 之前先用 LIKE 做子串匹配并与 FTS 结果合并。
     */
    @Query(
        """
        SELECT * FROM exercise_definition
        WHERE isArchived = 0 AND deletedAt IS NULL
          AND (name LIKE '%' || :raw || '%' OR IFNULL(aliases, '') LIKE '%' || :raw || '%')
        ORDER BY usageCount DESC, name ASC
        LIMIT :limit
        """
    )
    fun searchLike(raw: String, limit: Int = 50): Flow<List<ExerciseDefinitionEntity>>

    @Query(
        """
        SELECT * FROM exercise_definition
        WHERE isArchived = 0 AND deletedAt IS NULL
          AND (:muscle IS NULL OR primaryMuscle = :muscle)
          AND (:majorGroup IS NULL OR majorGroup = :majorGroup)
          AND (:subGroup IS NULL OR subGroup = :subGroup)
          AND (:equipment IS NULL OR equipment = :equipment)
          AND (:type IS NULL OR type = :type)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:customOnly = 0 OR isCustom = 1)
        ORDER BY usageCount DESC, name ASC
        """
    )
    fun filter(
        muscle: String?,
        majorGroup: String?,
        subGroup: String?,
        equipment: String?,
        type: String?,
        favoritesOnly: Int,
        customOnly: Int,
    ): Flow<List<ExerciseDefinitionEntity>>

    @Query("SELECT COUNT(*) FROM exercise_definition")
    suspend fun count(): Int

    /**
     * 精确匹配动作名。名字是用户检索动作的主要方式，
     * 重名会产生两条无法区分的记录，因此新建/改名前必须查重。
     */
    @Query("SELECT * FROM exercise_definition WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ExerciseDefinitionEntity?

    @Query("SELECT * FROM exercise_muscle WHERE exerciseId IN (:exerciseIds)")
    suspend fun getMusclesFor(exerciseIds: List<Long>): List<ExerciseMuscleEntity>

    /** 全部内置（非自定义）动作 —— 种子导入 v2 做按名更新时使用 */
    @Query("SELECT * FROM exercise_definition WHERE isCustom = 0")
    suspend fun getAllNonCustom(): List<ExerciseDefinitionEntity>

    /**
     * 清理新版种子中已不存在的内置动作。
     * 只删除未被任何计划/训练/个人纪录引用的行 —— 被引用的保留为"遗留条目"，
     * 避免 FK RESTRICT 失败或级联误删用户数据。
     */
    @Query(
        """
        DELETE FROM exercise_definition
        WHERE isCustom = 0
          AND name NOT IN (:keepNames)
          AND id NOT IN (SELECT exerciseDefinitionId FROM plan_exercise WHERE exerciseDefinitionId IS NOT NULL)
          AND id NOT IN (SELECT exerciseDefinitionId FROM workout_exercise WHERE exerciseDefinitionId IS NOT NULL)
          AND id NOT IN (SELECT exerciseDefinitionId FROM personal_record)
        """
    )
    suspend fun deleteObsoleteNonCustom(keepNames: List<String>): Int

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ExerciseDefinitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseDefinitionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscles(items: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscle WHERE exerciseId = :exerciseId")
    suspend fun deleteMusclesOf(exerciseId: Long)

    @Update
    suspend fun update(item: ExerciseDefinitionEntity)

    /** 使用频次自增 —— 用于"最近使用/常用"排序 */
    @Query("UPDATE exercise_definition SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun bumpUsage(id: Long, now: Long)

    @Query("UPDATE exercise_definition SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Query("UPDATE exercise_definition SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    /** 名称查重（自定义动作创建时使用） */
    @Query("SELECT COUNT(*) FROM exercise_definition WHERE name = :name AND isArchived = 0 AND deletedAt IS NULL")
    suspend fun countByName(name: String): Int

    // ------------------------------------------------------------------
    // 回收站（deletedAt 非 null = 在回收站）
    // ------------------------------------------------------------------

    @Query("SELECT * FROM exercise_definition WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ExerciseDefinitionEntity>>

    @Query("UPDATE exercise_definition SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: Long, now: Long)

    @Query("UPDATE exercise_definition SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, now: Long)

    @Query("SELECT * FROM exercise_definition WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getTrashOlderThan(threshold: Long): List<ExerciseDefinitionEntity>

    /** 仅删除自定义动作；内置动作只能归档 */
    @Query("DELETE FROM exercise_definition WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: Long): Int
}
