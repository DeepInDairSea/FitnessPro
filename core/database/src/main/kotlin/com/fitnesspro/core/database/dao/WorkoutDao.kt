package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.fitnesspro.core.database.entity.PreviousSetRow
import com.fitnesspro.core.database.entity.SessionSummaryRow
import com.fitnesspro.core.database.entity.WorkoutExerciseEntity
import com.fitnesspro.core.database.entity.WorkoutSessionEntity
import com.fitnesspro.core.database.entity.WorkoutSessionStateEntity
import com.fitnesspro.core.database.entity.WorkoutSessionWithExercises
import com.fitnesspro.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

/** 首页"继续训练"列表条目：completedSets 由组表实时统计 */
data class ActiveSessionRow(
    val id: Long,
    val planName: String,
    val startedAt: Long,
    val completedSets: Int,
)

/** 当天已完成训练按计划的聚合：用于今日页/首页的"完成训练"状态块 */
data class CompletedSessionByPlanRow(
    val planId: Long,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val totalDurationSeconds: Int,
)

/** 区间内已完成训练的 (计划, 开始时间) 行：用于周视图"已完成"标识 */
data class CompletedSessionDayRow(
    val planId: Long,
    val startedAt: Long,
)

@Dao
interface WorkoutDao {

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------

    /**
     * 全部进行中的会话。
     * 允许同时存在多个未完成训练：旧的"开新训练即放弃旧会话"逻辑
     * 会把用户没练完的训练从首页挤掉，恢复回收站记录也可能被误伤。
     */
    @Query(
        """
        SELECT s.id, s.planNameSnapshot AS planName, s.startedAt,
            (SELECT COUNT(*) FROM workout_set ws
             JOIN workout_exercise we ON ws.workoutExerciseId = we.id
             WHERE we.sessionId = s.id AND ws.status = 'COMPLETED') AS completedSets
        FROM workout_session s
        WHERE s.status = 'IN_PROGRESS' AND s.isDeleted = 0
        ORDER BY s.startedAt DESC
        """
    )
    fun observeActiveSessionRows(): Flow<List<ActiveSessionRow>>

    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'IN_PROGRESS' AND isDeleted = 0
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'IN_PROGRESS' AND isDeleted = 0
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    suspend fun getActiveSession(): WorkoutSessionEntity?

    /** 全部进行中的会话（开机清理残留休息计时等场景需要遍历，而非只看最新一条） */
    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'IN_PROGRESS' AND isDeleted = 0
        ORDER BY startedAt DESC
        """
    )
    suspend fun getActiveSessions(): List<WorkoutSessionEntity>

    /**
     * 当前正在休息（或休息被暂停）的会话。
     * 休息计时全局只有一份（单例闹钟 + 单条通知），多会话并存时由它定位归属。
     */
    @Query(
        """
        SELECT st.sessionId FROM workout_session_state st
        JOIN workout_session s ON s.id = st.sessionId
        WHERE s.status = 'IN_PROGRESS' AND s.isDeleted = 0
          AND (st.restTimerEndsAtEpochMs IS NOT NULL OR st.restTimerPausedRemainingMs IS NOT NULL)
        LIMIT 1
        """
    )
    suspend fun getSessionIdWithActiveRest(): Long?

    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<WorkoutSessionEntity?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    fun observeSessionWithExercises(sessionId: Long): Flow<WorkoutSessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    suspend fun getSessionWithExercises(sessionId: Long): WorkoutSessionWithExercises?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query(
        """
        UPDATE workout_session
        SET status = :status, completedAt = :completedAt,
            totalDurationSeconds = :duration, totalVolumeKg = :volume,
            totalSets = :sets, totalReps = :reps,
            activeSegmentStartedAt = NULL, updatedAt = :now
        WHERE id = :sessionId
        """
    )
    suspend fun finalizeSession(
        sessionId: Long,
        status: String,
        completedAt: Long,
        duration: Int,
        volume: Double,
        sets: Int,
        reps: Int,
        now: Long,
    )

    @Query(
        """
        UPDATE workout_session
        SET totalDurationSeconds = :accumulated, activeSegmentStartedAt = :segmentStart,
            pausedTotalSeconds = :pausedTotal, updatedAt = :now
        WHERE id = :sessionId
        """
    )
    suspend fun updateTiming(
        sessionId: Long,
        accumulated: Int,
        segmentStart: Long?,
        pausedTotal: Int,
        now: Long,
    )

    @Query("UPDATE workout_session SET isDeleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSession(id: Long, now: Long)

    // ------------------------------------------------------------------
    // 回收站（isDeleted = 1 且 deletedAt 非 null；abandonAllInProgress 只置
    // isDeleted 不写 deletedAt —— 主动放弃的半截训练对用户不可见，也不进回收站）
    // ------------------------------------------------------------------

    @Query("SELECT * FROM workout_session WHERE isDeleted = 1 AND deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<WorkoutSessionEntity>>

    @Query("UPDATE workout_session SET isDeleted = 0, deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, now: Long)

    /** workout_exercise / workout_set / workout_session_state 均为 CASCADE，一次 DELETE 即可 */
    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun purgeSession(id: Long)

    /**
     * 训练中"不保存"：会话移入回收站（30 天后由清理 Worker 物理删除）。
     * **保留 IN_PROGRESS 状态**：从回收站恢复后它直接回到首页"进行中训练"列表，
     * 可以接着练 —— 丢弃只是"暂存"，不是终结。
     * 进行中会话不会出现在历史里（历史查询排除 IN_PROGRESS），互不干扰。
     */
    @Query(
        """
        UPDATE workout_session
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now
        WHERE id = :id AND status = 'IN_PROGRESS'
        """
    )
    suspend fun discardSession(id: Long, now: Long)

    @Query("SELECT * FROM workout_session WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getTrashOlderThan(threshold: Long): List<WorkoutSessionEntity>

    @Query("UPDATE workout_session SET notes = :notes, perceivedEffort = :rpe, mood = :mood, updatedAt = :now WHERE id = :id")
    suspend fun updateSessionSummaryInput(id: Long, notes: String?, rpe: Int?, mood: String?, now: Long)

    // ------------------------------------------------------------------
    // 会话内的动作
    // ------------------------------------------------------------------

    @Insert
    suspend fun insertExercise(exercise: WorkoutExerciseEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>): List<Long>

    @Update
    suspend fun updateExercise(exercise: WorkoutExerciseEntity)

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun getExercise(id: Long): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    fun observeExercise(id: Long): Flow<WorkoutExerciseEntity?>

    @Query("SELECT * FROM workout_exercise WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getExercises(sessionId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeExercises(sessionId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("UPDATE workout_exercise SET status = :status WHERE id = :id")
    suspend fun setExerciseStatus(id: Long, status: String)

    @Query("UPDATE workout_exercise SET status = 'SKIPPED', skippedAt = :now WHERE id = :id")
    suspend fun skipExercise(id: Long, now: Long)

    @Query("UPDATE workout_exercise SET notes = :notes WHERE id = :id")
    suspend fun updateExerciseNotes(id: Long, notes: String?)

    @Query("UPDATE workout_exercise SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateExerciseOrder(id: Long, orderIndex: Double)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT IFNULL(MAX(orderIndex), -1.0) FROM workout_exercise WHERE sessionId = :sessionId")
    suspend fun maxExerciseOrderIndex(sessionId: Long): Double

    // ------------------------------------------------------------------
    // 组
    // ------------------------------------------------------------------

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>): List<Long>

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    /** 历史复盘：删除任意状态的组（不只是 PENDING） */
    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setIndex ASC")
    suspend fun getSets(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setIndex ASC")
    fun observeSets(workoutExerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE id = :id AND status = 'PENDING'")
    suspend fun getPendingSet(id: Long): WorkoutSetEntity?

    @Query("DELETE FROM workout_set WHERE id = :id AND status = 'PENDING'")
    suspend fun deletePendingSet(id: Long): Int

    @Query("SELECT IFNULL(MAX(setIndex), -1) FROM workout_set WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun maxSetIndex(workoutExerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_set WHERE workoutExerciseId = :workoutExerciseId AND status = 'PENDING'")
    suspend fun pendingSetCount(workoutExerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_set WHERE workoutExerciseId = :workoutExerciseId AND status != 'PENDING'")
    suspend fun settledSetCount(workoutExerciseId: Long): Int

    /** 某次会话中所有已完成的组，用于结束训练时汇总 */
    @Query(
        """
        SELECT ws.* FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        WHERE we.sessionId = :sessionId AND ws.status = 'COMPLETED'
        ORDER BY we.orderIndex ASC, ws.setIndex ASC
        """
    )
    suspend fun completedSets(sessionId: Long): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        WHERE we.sessionId = :sessionId
        ORDER BY we.orderIndex ASC, ws.setIndex ASC
        """
    )
    suspend fun allSets(sessionId: Long): List<WorkoutSetEntity>

    @Query(
        """
        UPDATE workout_set SET status = 'SKIPPED' WHERE workoutExerciseId = :workoutExerciseId AND status = 'PENDING'
        """
    )
    suspend fun skipPendingSets(workoutExerciseId: Long): Int

    // ------------------------------------------------------------------
    // 上次成绩（渐进超负荷的核心数据来源）
    // ------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM workout_set
        WHERE workoutExerciseId = :workoutExerciseId AND status = 'COMPLETED'
        ORDER BY completedAt DESC LIMIT 1
        """
    )
    suspend fun latestCompletedSet(workoutExerciseId: Long): WorkoutSetEntity?

    /**
     * 上一次完成该动作的整场训练中，该动作的全部组。
     * 用于训练页显示「上次：60kg × 8, 8, 7」以及自动带入。
     */
    @Query(
        """
        SELECT ws.setIndex AS setIndex, ws.reps AS reps, ws.weightKg AS weightKg,
               ws.durationSeconds AS durationSeconds, ws.distanceMeters AS distanceMeters,
               ws.setType AS setType
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout_session s ON we.sessionId = s.id
        WHERE we.exerciseDefinitionId = :exerciseDefinitionId
          AND s.status = 'COMPLETED' AND s.isDeleted = 0
          AND s.id = (
              SELECT s2.id FROM workout_session s2
              JOIN workout_exercise we2 ON we2.sessionId = s2.id
              WHERE we2.exerciseDefinitionId = :exerciseDefinitionId
                AND s2.status = 'COMPLETED' AND s2.isDeleted = 0
              ORDER BY s2.startedAt DESC LIMIT 1
          )
        ORDER BY ws.setIndex ASC
        """
    )
    suspend fun previousSessionSets(exerciseDefinitionId: Long): List<PreviousSetRow>

    /**
     * 同一计划**上一次已完成训练**的总容量（排除当前会话）。
     *
     * 只比同一个计划：不同计划的容量没有可比性，"今天比昨天多了 3000kg"
     * 如果昨天练的是另一个计划，这个数字就是误导。
     */
    @Query(
        """
        SELECT totalVolumeKg FROM workout_session
        WHERE planId = :planId AND isDeleted = 0 AND status = 'COMPLETED'
          AND id != :excludeSessionId
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    suspend fun previousCompletedVolumeKg(planId: Long, excludeSessionId: Long): Double?

    // ------------------------------------------------------------------
    // 历史
    // ------------------------------------------------------------------

    @Query(
        """
        SELECT id, planId, planNameSnapshot AS planName, startedAt, completedAt, status,
               totalDurationSeconds AS durationSeconds, totalVolumeKg, totalSets, totalReps
        FROM workout_session
        WHERE isDeleted = 0 AND status != 'IN_PROGRESS' AND startedAt >= :sinceEpochMs
        ORDER BY startedAt DESC
        LIMIT :limit
        """
    )
    fun observeHistory(sinceEpochMs: Long, limit: Int = 500): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT id, planId, planNameSnapshot AS planName, startedAt, completedAt, status,
               totalDurationSeconds AS durationSeconds, totalVolumeKg, totalSets, totalReps
        FROM workout_session
        WHERE id = :sessionId
        """
    )
    fun observeSessionSummaryRow(sessionId: Long): Flow<SessionSummaryRow?>

    @Query(
        """
        SELECT id, planId, planNameSnapshot AS planName, startedAt, completedAt, status,
               totalDurationSeconds AS durationSeconds, totalVolumeKg, totalSets, totalReps
        FROM workout_session
        WHERE isDeleted = 0 AND status != 'IN_PROGRESS'
          AND (planNameSnapshot LIKE '%' || :raw || '%'
               OR EXISTS (
                   SELECT 1 FROM workout_exercise we
                   WHERE we.sessionId = workout_session.id AND we.nameSnapshot LIKE '%' || :raw || '%'
               ))
        ORDER BY startedAt DESC
        LIMIT :limit
        """
    )
    fun searchHistory(raw: String, limit: Int = 200): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT id, planId, planNameSnapshot AS planName, startedAt, completedAt, status,
               totalDurationSeconds AS durationSeconds, totalVolumeKg, totalSets, totalReps
        FROM workout_session
        WHERE isDeleted = 0 AND status != 'IN_PROGRESS'
          AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs
        ORDER BY startedAt DESC
        """
    )
    fun observeHistoryInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<SessionSummaryRow>>

    @Query("SELECT COUNT(*) FROM workout_session WHERE planId = :planId AND status = 'COMPLETED' AND isDeleted = 0")
    suspend fun completedCountForPlan(planId: Long): Int

    /** 训练日历：每天的训练次数 */
    @Query(
        """
        SELECT startedAt FROM workout_session
        WHERE isDeleted = 0 AND status = 'COMPLETED'
        ORDER BY startedAt DESC
        """
    )
    fun observeCompletedStartTimes(): Flow<List<Long>>

    /**
     * 当天（startedAt 落在 [fromEpochMs, toEpochMs) 内）已完成训练按计划的聚合。
     * 用于今日页/首页的"完成训练"状态块；同一个计划当天练多次会合并求和。
     */
    @Query(
        """
        SELECT planId AS planId,
            IFNULL(SUM(totalSets), 0) AS totalSets,
            IFNULL(SUM(totalVolumeKg), 0.0) AS totalVolumeKg,
            IFNULL(SUM(totalDurationSeconds), 0) AS totalDurationSeconds
        FROM workout_session
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND planId IS NOT NULL
          AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs
        GROUP BY planId
        """
    )
    fun observeCompletedByPlanBetween(fromEpochMs: Long, toEpochMs: Long): Flow<List<CompletedSessionByPlanRow>>

    /** 区间内已完成训练的 (planId, startedAt) 明细：应用层换算成本地日后按计划聚合 */
    @Query(
        """
        SELECT planId AS planId, startedAt AS startedAt
        FROM workout_session
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND planId IS NOT NULL
          AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs
        """
    )
    fun observeCompletedPlanDaysBetween(fromEpochMs: Long, toEpochMs: Long): Flow<List<CompletedSessionDayRow>>

    /**
     * 当天自由训练（无计划）的完成聚合：今日页/首页与计划完成块同权展示。
     * 注意：无 GROUP BY 的聚合对空结果集也返回一行，必须用 HAVING 滤掉，
     * 否则会出现"0 组 0kg"的假完成块。
     */
    @Query(
        """
        SELECT planId AS planId,
            IFNULL(SUM(totalSets), 0) AS totalSets,
            IFNULL(SUM(totalVolumeKg), 0.0) AS totalVolumeKg,
            IFNULL(SUM(totalDurationSeconds), 0) AS totalDurationSeconds
        FROM workout_session
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND planId IS NULL
          AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs
        GROUP BY planId
        """
    )
    fun observeFreestyleCompletedBetween(fromEpochMs: Long, toEpochMs: Long): Flow<CompletedSessionByPlanRow?>

    /** 区间内自由训练（无计划）的完成开始时间：训练日历据此展示自由训练条目 */
    @Query(
        """
        SELECT startedAt FROM workout_session
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND planId IS NULL
          AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs
        ORDER BY startedAt ASC
        """
    )
    fun observeFreestyleStartsBetween(fromEpochMs: Long, toEpochMs: Long): Flow<List<Long>>

    // ------------------------------------------------------------------
    // 中断恢复状态
    // ------------------------------------------------------------------

    /** 会话内已完成组数：草稿恢复提示需要用它告知用户"练到哪了" */
    @Query(
        """
        SELECT COUNT(*) FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        WHERE we.sessionId = :sessionId AND ws.status = 'COMPLETED'
        """
    )
    suspend fun countCompletedSets(sessionId: Long): Int

    @Query("SELECT * FROM workout_session_state WHERE sessionId = :sessionId")
    suspend fun getState(sessionId: Long): WorkoutSessionStateEntity?

    @Query("SELECT * FROM workout_session_state WHERE sessionId = :sessionId")
    fun observeState(sessionId: Long): Flow<WorkoutSessionStateEntity?>

    @Upsert
    suspend fun upsertState(state: WorkoutSessionStateEntity)

    @Query("DELETE FROM workout_session_state WHERE sessionId = :sessionId")
    suspend fun deleteState(sessionId: Long)

    @Query("UPDATE workout_session_state SET restTimerEndsAtEpochMs = NULL, restTimerPausedRemainingMs = NULL, updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun clearRestTimer(sessionId: Long, now: Long)

    @Query("UPDATE workout_session_state SET isPaused = :paused, updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun setPaused(sessionId: Long, paused: Boolean, now: Long)

}
