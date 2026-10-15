package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * 统计投影。所有聚合都下沉到 SQL —— 绝不把明细加载到内存再计算
 * （PRD NFR-P6：单查询 ≤ 50ms）。
 *
 * 注意：所有时间范围都由应用层用 java.time 算好后以毫秒/epochDay 传入，
 * 不在 SQL 里做除法取整 —— 那样在跨时区与夏令时下会算错（ADR-10）。
 */

data class VolumeBucketRow(
    val bucketEpochDay: Long,
    val volumeKg: Double,
    val sessionCount: Int,
    val totalSets: Int,
)

data class MuscleVolumeRow(
    val muscle: String,
    val volumeKg: Double,
    val setCount: Int,
)

data class DayCountRow(
    val epochDay: Long,
    val sessionCount: Int,
)

data class ExerciseFrequencyRow(
    val exerciseDefinitionId: Long,
    val exerciseName: String,
    val sessionCount: Int,
    val setCount: Int,
    val volumeKg: Double,
)

data class ExerciseTrendRow(
    val epochDay: Long,
    val bestWeightKg: Double?,
    val volumeKg: Double,
    val totalReps: Int,
    val maxEstimatedOneRmKg: Double?,
)

@Dao
interface StatsDao {

    /** 容量趋势：应用层传入已按周/月对齐的 bucket 起点（epochDay） */
    @Query(
        """
        SELECT
            (s.startedAt / 86400000) AS bucketEpochDay,
            SUM(s.totalVolumeKg) AS volumeKg,
            COUNT(*) AS sessionCount,
            SUM(s.totalSets) AS totalSets
        FROM workout_session s
        WHERE s.status = 'COMPLETED' AND s.isDeleted = 0
          AND s.startedAt >= :sinceEpochMs
        GROUP BY bucketEpochDay
        ORDER BY bucketEpochDay ASC
        """
    )
    suspend fun dailyVolume(sinceEpochMs: Long): List<VolumeBucketRow>

    @Query(
        """
        SELECT
            IFNULL(e.primaryMuscle, 'OTHER') AS muscle,
            SUM(
                CASE
                    WHEN ws.setType = 'WARMUP' AND :includeWarmup = 0 THEN 0
                    WHEN e.type = 'REPS_ONLY' THEN
                        -- REPS_ONLY 用自重当负载：优先用 session 记录时的体重快照，
                        -- 否则取身体表最近一次体重（很可能就是用户最新体重），
                        -- 都没有再兜底 65kg —— 否则引体向上/俯卧撑这类纯自重动作
                        -- 在 volume 分布上永远是 0，胸/腿/背/斜方肌这种大类就识别不到了
                        (IFNULL(ws.weightKg, 0) +
                         COALESCE(
                             NULLIF(s.bodyWeightKgSnapshot, 0),
                             (SELECT weightKg FROM body_measurement
                              WHERE weightKg IS NOT NULL AND weightKg > 0
                              ORDER BY epochDay DESC LIMIT 1),
                             65.0
                         )) * IFNULL(ws.reps, 0)
                    WHEN e.type = 'WEIGHT_TIME' THEN IFNULL(ws.weightKg, 0) * IFNULL(ws.durationSeconds, 0) / 60.0
                    WHEN e.type = 'WEIGHT_DISTANCE_TIME' THEN IFNULL(ws.weightKg, 0) * IFNULL(ws.distanceMeters, 0)
                    WHEN e.type IN ('TIME', 'DISTANCE_TIME') THEN 0
                    ELSE IFNULL(ws.weightKg, 0) * IFNULL(ws.reps, 0)
                END
            ) AS volumeKg,
            COUNT(*) AS setCount
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout_session s ON we.sessionId = s.id
        LEFT JOIN exercise_definition e ON we.exerciseDefinitionId = e.id
        WHERE s.status = 'COMPLETED' AND s.isDeleted = 0
          AND ws.status = 'COMPLETED'
          AND s.startedAt >= :sinceEpochMs
        GROUP BY muscle
        ORDER BY volumeKg DESC
        """
    )
    suspend fun muscleDistribution(sinceEpochMs: Long, includeWarmup: Int): List<MuscleVolumeRow>

    /** 训练热力图：每天的完成次数 */
    @Query(
        """
        SELECT (startedAt / 86400000) AS epochDay, COUNT(*) AS sessionCount
        FROM workout_session
        WHERE status = 'COMPLETED' AND isDeleted = 0
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    suspend fun calendarHeatmap(): List<DayCountRow>

    /** 最常练的动作 Top N */
    @Query(
        """
        SELECT
            we.exerciseDefinitionId AS exerciseDefinitionId,
            we.nameSnapshot AS exerciseName,
            COUNT(DISTINCT s.id) AS sessionCount,
            COUNT(*) AS setCount,
            SUM(CASE WHEN e.type = 'REPS_ONLY'
                     THEN (IFNULL(ws.weightKg, 0) + IFNULL(s.bodyWeightKgSnapshot, 0)) * IFNULL(ws.reps, 0)
                     ELSE IFNULL(ws.weightKg, 0) * IFNULL(ws.reps, 0) END) AS volumeKg
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout_session s ON we.sessionId = s.id
        LEFT JOIN exercise_definition e ON we.exerciseDefinitionId = e.id
        WHERE s.status = 'COMPLETED' AND s.isDeleted = 0
          AND ws.status = 'COMPLETED' AND ws.setType != 'WARMUP'
          AND s.startedAt >= :sinceEpochMs
        GROUP BY we.exerciseDefinitionId, we.nameSnapshot
        ORDER BY sessionCount DESC, setCount DESC
        LIMIT :limit
        """
    )
    suspend fun topExercises(sinceEpochMs: Long, limit: Int = 10): List<ExerciseFrequencyRow>

    /** 单动作趋势：按天聚合，用于动作详情页折线图 */
    @Query(
        """
        SELECT
            (s.startedAt / 86400000) AS epochDay,
            MAX(ws.weightKg) AS bestWeightKg,
            SUM(IFNULL(ws.weightKg, 0) * IFNULL(ws.reps, 0)) AS volumeKg,
            SUM(IFNULL(ws.reps, 0)) AS totalReps,
            MAX(
                CASE
                    WHEN ws.reps IS NULL OR ws.reps <= 0 OR ws.weightKg IS NULL THEN NULL
                    WHEN ws.reps = 1 THEN ws.weightKg
                    WHEN ws.reps <= 12 THEN ws.weightKg * 36.0 / (37 - ws.reps)
                    ELSE ws.weightKg * (1 + ws.reps / 30.0)
                END
            ) AS maxEstimatedOneRmKg
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout_session s ON we.sessionId = s.id
        WHERE we.exerciseDefinitionId = :exerciseDefinitionId
          AND s.status = 'COMPLETED' AND s.isDeleted = 0
          AND ws.status = 'COMPLETED' AND ws.setType != 'WARMUP'
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    suspend fun exerciseTrend(exerciseDefinitionId: Long): List<ExerciseTrendRow>

    /** 概览：区间内的训练次数与容量 */
    @Query(
        """
        SELECT
            (startedAt / 86400000) AS bucketEpochDay,
            SUM(totalVolumeKg) AS volumeKg,
            COUNT(*) AS sessionCount,
            SUM(totalSets) AS totalSets
        FROM workout_session
        WHERE status = 'COMPLETED' AND isDeleted = 0 AND startedAt >= :sinceEpochMs
        """
    )
    suspend fun overview(sinceEpochMs: Long): VolumeBucketRow?

    /** 有训练记录的日期（用于连续打卡 streak 计算） */
    @Query(
        """
        SELECT DISTINCT (startedAt / 86400000) AS epochDay
        FROM workout_session
        WHERE status = 'COMPLETED' AND isDeleted = 0
        ORDER BY epochDay DESC
        """
    )
    suspend fun completedDays(): List<Long>

    /** 累计训练次数 */
    @Query("SELECT COUNT(*) FROM workout_session WHERE status = 'COMPLETED' AND isDeleted = 0")
    suspend fun totalCompletedSessions(): Int
}
