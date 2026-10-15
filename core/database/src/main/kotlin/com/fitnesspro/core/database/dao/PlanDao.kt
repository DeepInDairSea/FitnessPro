package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fitnesspro.core.database.entity.PlanCardRow
import com.fitnesspro.core.database.entity.PlanExerciseEntity
import com.fitnesspro.core.database.entity.PlanExerciseWithSets
import com.fitnesspro.core.database.entity.PlanFolderEntity
import com.fitnesspro.core.database.entity.PlanSetEntity
import com.fitnesspro.core.database.entity.PlanWithExercises
import com.fitnesspro.core.database.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    // ------------------------------------------------------------------
    // 列表（轻量投影，绝不 SELECT * 加载动作明细）
    // ------------------------------------------------------------------

    /**
     * 计划列表。排序由 sortKey 参数决定，避免为每种排序写一条查询。
     * 只取渲染卡片所需字段，动作数与组数通过子查询聚合，不加载明细行。
     */
    @Query(
        """
        SELECT
            p.id AS id,
            p.name AS name,
            p.folderId AS folderId,
            p.colorHex AS colorHex,
            p.isFavorite AS isFavorite,
            p.estimatedDurationSeconds AS estimatedDurationSeconds,
            p.lastPerformedAt AS lastPerformedAt,
            p.updatedAt AS updatedAt,
            p.scheduledDays AS scheduledDays,
            p.focus AS focus,
            p.deletedAt AS deletedAt,
            (SELECT COUNT(*) FROM plan_exercise pe WHERE pe.planId = p.id) AS exerciseCount,
            (SELECT IFNULL(SUM(pe.targetSets), 0) FROM plan_exercise pe WHERE pe.planId = p.id) AS totalSets
        FROM training_plan p
        WHERE p.isArchived = 0 AND p.deletedAt IS NULL
        ORDER BY
            CASE WHEN :sortKey = 'NAME' THEN p.name END ASC,
            CASE WHEN :sortKey = 'MANUAL' THEN p.sortOrder END ASC,
            CASE WHEN :sortKey = 'RECENTLY_PERFORMED' THEN IFNULL(p.lastPerformedAt, 0) END DESC,
            CASE WHEN :sortKey = 'RECENTLY_EDITED' THEN p.updatedAt END DESC
        """
    )
    fun observePlanCards(sortKey: String): Flow<List<PlanCardRow>>

    /** 搜索：同时匹配计划名与动作名（后者用于"我记不住计划叫什么，但记得练了什么"的场景） */
    @Query(
        """
        SELECT
            p.id AS id,
            p.name AS name,
            p.folderId AS folderId,
            p.colorHex AS colorHex,
            p.isFavorite AS isFavorite,
            p.estimatedDurationSeconds AS estimatedDurationSeconds,
            p.lastPerformedAt AS lastPerformedAt,
            p.updatedAt AS updatedAt,
            p.scheduledDays AS scheduledDays,
            p.focus AS focus,
            p.deletedAt AS deletedAt,
            (SELECT COUNT(*) FROM plan_exercise pe WHERE pe.planId = p.id) AS exerciseCount,
            (SELECT IFNULL(SUM(pe.targetSets), 0) FROM plan_exercise pe WHERE pe.planId = p.id) AS totalSets
        FROM training_plan p
        WHERE p.isArchived = 0 AND p.deletedAt IS NULL
          AND (
            p.name LIKE '%' || :raw || '%'
            OR EXISTS (
                SELECT 1 FROM plan_exercise pe2
                WHERE pe2.planId = p.id AND pe2.nameSnapshot LIKE '%' || :raw || '%'
            )
          )
        ORDER BY p.updatedAt DESC
        """
    )
    fun searchPlanCards(raw: String): Flow<List<PlanCardRow>>

    /** 回收站 */
    @Query(
        """
        SELECT
            p.id AS id,
            p.name AS name,
            p.folderId AS folderId,
            p.colorHex AS colorHex,
            p.isFavorite AS isFavorite,
            p.estimatedDurationSeconds AS estimatedDurationSeconds,
            p.lastPerformedAt AS lastPerformedAt,
            p.updatedAt AS updatedAt,
            p.scheduledDays AS scheduledDays,
            p.focus AS focus,
            p.deletedAt AS deletedAt,
            (SELECT COUNT(*) FROM plan_exercise pe WHERE pe.planId = p.id) AS exerciseCount,
            (SELECT IFNULL(SUM(pe.targetSets), 0) FROM plan_exercise pe WHERE pe.planId = p.id) AS totalSets
        FROM training_plan p
        WHERE p.deletedAt IS NOT NULL
        ORDER BY p.deletedAt DESC
        """
    )
    fun observeTrash(): Flow<List<PlanCardRow>>

    // ------------------------------------------------------------------
    // 详情
    // ------------------------------------------------------------------

    @Query("SELECT * FROM training_plan WHERE id = :id")
    fun observePlan(id: Long): Flow<TrainingPlanEntity?>

    @Query("SELECT * FROM training_plan WHERE id = :id")
    suspend fun getPlan(id: Long): TrainingPlanEntity?

    @Query("SELECT * FROM plan_exercise WHERE planId = :planId ORDER BY orderIndex ASC")
    fun observePlanExercises(planId: Long): Flow<List<PlanExerciseEntity>>

    @Query("SELECT * FROM plan_exercise WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getPlanExercises(planId: Long): List<PlanExerciseEntity>

    @Query("SELECT * FROM plan_exercise WHERE id = :id")
    suspend fun findPlanExercise(id: Long): PlanExerciseEntity?

    @Query("SELECT planId FROM plan_exercise WHERE id = :id")
    suspend fun planIdOf(id: Long): Long?

    @Query("SELECT * FROM plan_set WHERE planExerciseId IN (:planExerciseIds) ORDER BY setIndex ASC")
    suspend fun getPlanSets(planExerciseIds: List<Long>): List<PlanSetEntity>

    @androidx.room.Transaction
    @Query("SELECT * FROM training_plan WHERE id = :id")
    suspend fun getPlanWithExercises(id: Long): PlanWithExercises?

    @androidx.room.Transaction
    @Query("SELECT * FROM plan_exercise WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getExercisesWithSets(planId: Long): List<PlanExerciseWithSets>

    /** 已排期到某一星期的计划（scheduledDays 形如 "1,3,5"） */
    @Query(
        """
        SELECT * FROM training_plan
        WHERE deletedAt IS NULL AND isArchived = 0 AND scheduledDays IS NOT NULL AND scheduledDays != ''
        ORDER BY sortOrder ASC
        """
    )
    fun observeScheduledPlans(): Flow<List<TrainingPlanEntity>>

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Insert
    suspend fun insertPlan(plan: TrainingPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: TrainingPlanEntity)

    @Insert
    suspend fun insertExercise(exercise: PlanExerciseEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<PlanExerciseEntity>): List<Long>

    @Update
    suspend fun updateExercise(exercise: PlanExerciseEntity)

    @Query("DELETE FROM plan_exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("DELETE FROM plan_exercise WHERE planId = :planId")
    suspend fun deleteExercisesOfPlan(planId: Long)

    @Insert
    suspend fun insertSets(sets: List<PlanSetEntity>)

    @Query("DELETE FROM plan_set WHERE planExerciseId IN (:planExerciseIds)")
    suspend fun deleteSetsOfExercises(planExerciseIds: List<Long>)

    @Query("DELETE FROM plan_set WHERE planExerciseId = :planExerciseId")
    suspend fun deleteSetsOfExercise(planExerciseId: Long)

    /** 排序用。注意 (planId, orderIndex) 上有唯一索引，重排时需先写入负数再写入目标值。 */
    @Query("UPDATE plan_exercise SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateExerciseOrder(id: Long, orderIndex: Double)

    /** 组数变化时补齐缺省组（保持既有组的自定义设置） */
    @Query("SELECT IFNULL(MAX(setIndex), -1) FROM plan_set WHERE planExerciseId = :planExerciseId")
    suspend fun maxSetIndex(planExerciseId: Long): Int

    @Query("UPDATE training_plan SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: Long, now: Long)

    @Query("UPDATE training_plan SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, now: Long)

    @Query("DELETE FROM training_plan WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT * FROM training_plan WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getTrashOlderThan(threshold: Long): List<TrainingPlanEntity>

    @Query("SELECT COUNT(*) FROM workout_session WHERE planId = :planId")
    suspend fun sessionCountForPlan(planId: Long): Int

    @Query("UPDATE training_plan SET lastPerformedAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun touchLastPerformed(id: Long, at: Long, now: Long)

    @Query("UPDATE training_plan SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE training_plan SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Double, now: Long)

    @Query("UPDATE training_plan SET estimatedDurationSeconds = :seconds WHERE id = :id")
    suspend fun updateEstimatedDuration(id: Long, seconds: Int?)

    @Query("UPDATE training_plan SET folderId = :folderId, updatedAt = :now WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?, now: Long)

    @Query("UPDATE training_plan SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    // ------------------------------------------------------------------
    // 分组
    // ------------------------------------------------------------------

    @Query("SELECT * FROM plan_folder ORDER BY sortOrder ASC, name ASC")
    fun observeFolders(): Flow<List<PlanFolderEntity>>

    @Insert
    suspend fun insertFolder(folder: PlanFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: PlanFolderEntity)

    @Query("DELETE FROM plan_folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}
