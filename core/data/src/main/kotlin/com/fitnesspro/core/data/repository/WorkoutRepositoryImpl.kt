package com.fitnesspro.core.data.repository

import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.data.mapper.toDomain
import com.fitnesspro.core.data.mapper.toEnum
import com.fitnesspro.core.data.mapper.toTrashSummary
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.WorkoutDao
import com.fitnesspro.core.domain.repository.ActiveSessionInfo
import com.fitnesspro.core.domain.repository.CompletedSessionSummary
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.LastPerformance
import com.fitnesspro.core.model.SessionSummary
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val time: TimeProvider,
) : WorkoutRepository {

    override fun observeActiveSessionId(): Flow<Long?> =
        workoutDao.observeActiveSession().map { it?.id }

    override fun observeActiveSessions(): Flow<List<ActiveSessionInfo>> =
        workoutDao.observeActiveSessionRows().map { rows ->
            rows.map { row ->
                ActiveSessionInfo(
                    id = row.id,
                    planName = row.planName,
                    startedAt = row.startedAt,
                    completedSets = row.completedSets,
                )
            }
        }

    override fun observeSessionSummary(sessionId: Long): Flow<SessionSummary?> =
        workoutDao.observeSessionSummaryRow(sessionId).map { it?.toDomain() }

    override fun observeHistory(query: String, limit: Int): Flow<List<SessionSummary>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            workoutDao.observeHistory(sinceEpochMs = 0L, limit = limit)
        } else {
            workoutDao.searchHistory(trimmed, limit)
        }.map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHistoryInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<SessionSummary>> =
        workoutDao.observeHistoryInRange(fromEpochMs, toEpochMs)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeCompletedStartEpochDays(): Flow<List<Long>> =
        workoutDao.observeCompletedStartTimes().map { times ->
            // 时区转换放在应用层：SQL 里的除法无法正确处理时区与夏令时（ADR-10）
            times.map { time.epochMsToEpochDay(it) }
        }

    override fun observeTrainingCaloriesBetween(fromEpochMs: Long, toEpochMs: Long): Flow<Double> =
        workoutDao.observeTrainingCaloriesBetween(fromEpochMs, toEpochMs)

    override suspend fun getLastPerformance(exerciseDefinitionId: Long): LastPerformance {
        val exerciseType = exerciseDao.getById(exerciseDefinitionId)?.type
            .toEnum(ExerciseType.WEIGHT_REPS)
        val rows = runCatching { workoutDao.previousSessionSets(exerciseDefinitionId) }
            .getOrDefault(emptyList())
        return LastPerformance(
            sets = rows.map { row ->
                SetRecord(
                    exerciseType = exerciseType,
                    setType = row.setType.toEnum(SetType.NORMAL),
                    status = SetStatus.COMPLETED,
                    weightKg = row.weightKg,
                    reps = row.reps,
                    durationSeconds = row.durationSeconds,
                    distanceMeters = row.distanceMeters,
                )
            }
        )
    }

    override suspend fun previousSessionSets(exerciseDefinitionId: Long): List<SetRecord> =
        getLastPerformance(exerciseDefinitionId).sets

    override suspend fun previousSessionVolumeKg(planId: Long, excludeSessionId: Long): Double? =
        runCatching { workoutDao.previousCompletedVolumeKg(planId, excludeSessionId) }.getOrNull()

    override suspend fun updateSessionNotes(
        sessionId: Long,
        notes: String?,
        rpe: Int?,
        mood: String?,
    ): Outcome<Unit> = runCatchingOutcomeSuspend {
        workoutDao.updateSessionSummaryInput(
            sessionId,
            notes?.trim()?.takeIf { it.isNotEmpty() },
            rpe,
            mood,
            time.nowEpochMs(),
        )
        Unit
    }

    override suspend fun deleteSession(sessionId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { workoutDao.softDeleteSession(sessionId, time.nowEpochMs()) }

    override fun observeCompletedByPlanOnDay(epochDay: Long): Flow<List<CompletedSessionSummary>> {
        // 与 observeCompletedStartEpochDays 一致：时区换算放应用层，SQL 不做日期运算（ADR-10）
        val zone = java.time.ZoneId.systemDefault()
        val from = java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = java.time.LocalDate.ofEpochDay(epochDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return workoutDao.observeCompletedByPlanBetween(from, to).map { rows ->
            rows.map {
                CompletedSessionSummary(
                    planId = it.planId,
                    totalSets = it.totalSets,
                    totalVolumeKg = it.totalVolumeKg,
                    totalDurationSeconds = it.totalDurationSeconds,
                )
            }
        }
    }

    override fun observeCompletedPlanDaysInRange(fromEpochDay: Long, toEpochDay: Long): Flow<Map<Long, Set<Long>>> {
        val zone = java.time.ZoneId.systemDefault()
        val from = java.time.LocalDate.ofEpochDay(fromEpochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = java.time.LocalDate.ofEpochDay(toEpochDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return workoutDao.observeCompletedPlanDaysBetween(from, to).map { rows ->
            rows.groupBy({ it.planId }) { time.epochMsToEpochDay(it.startedAt) }
                .mapValues { (_, days) -> days.toSet() }
        }
    }

    override fun observeFreestyleCompletedOnDay(epochDay: Long): Flow<CompletedSessionSummary?> {
        val zone = java.time.ZoneId.systemDefault()
        val from = java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = java.time.LocalDate.ofEpochDay(epochDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return workoutDao.observeFreestyleCompletedBetween(from, to).map { row ->
            row?.let {
                // planId 用 -1 哨兵：自由训练不属于任何计划
                CompletedSessionSummary(
                    planId = -1L,
                    totalSets = it.totalSets,
                    totalVolumeKg = it.totalVolumeKg,
                    totalDurationSeconds = it.totalDurationSeconds,
                )
            }
        }
    }

    override fun observeFreestyleStartsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>> {
        val zone = java.time.ZoneId.systemDefault()
        val from = java.time.LocalDate.ofEpochDay(fromEpochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = java.time.LocalDate.ofEpochDay(toEpochDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return workoutDao.observeFreestyleStartsBetween(from, to)
    }

    override fun observeTrash(): Flow<List<SessionSummary>> =
        workoutDao.observeTrash().map { rows -> rows.map { it.toTrashSummary() } }

    override suspend fun restoreSessionFromTrash(sessionId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { workoutDao.restoreFromTrash(sessionId, time.nowEpochMs()) }

    override suspend fun deleteSessionForever(sessionId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { workoutDao.purgeSession(sessionId) }

    override suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int> = runCatchingOutcomeSuspend {
        val threshold = time.nowEpochMs() - retentionDays * 24L * 60 * 60 * 1000
        val rows = workoutDao.getTrashOlderThan(threshold)
        rows.forEach { workoutDao.purgeSession(it.id) }
        rows.size
    }

    override suspend fun completedCountForPlan(planId: Long): Int =
        runCatching { workoutDao.completedCountForPlan(planId) }.getOrDefault(0)
}
