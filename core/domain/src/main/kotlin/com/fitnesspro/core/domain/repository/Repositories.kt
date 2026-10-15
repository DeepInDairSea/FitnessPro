package com.fitnesspro.core.domain.repository

import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.BodyMeasurement
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseFilter
import com.fitnesspro.core.model.ExerciseTrendPoint
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.LastPerformance
import com.fitnesspro.core.model.MajorGroup
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.PlanCard
import com.fitnesspro.core.model.PlanDetail
import com.fitnesspro.core.model.PlanFolder
import com.fitnesspro.core.model.PlanSortOrder
import com.fitnesspro.core.model.SessionSummary
import kotlinx.coroutines.flow.Flow

/**
 * 仓库接口定义在领域层，实现放在 :core:data。
 *
 * 目的：让 UseCase 依赖抽象而非 Room/DataStore/文件系统，
 * 从而可以在纯 JVM 单元测试里用 Fake 实现验证领域逻辑
 * （DEV 文档 §2.2 依赖规则 R3：feature 与 domain 都不应看到数据库类型）。
 *
 * 约定：所有写方法返回 Outcome，不抛业务异常。
 * 用户偏好的读写接口见 :core:datastore 的 SettingsRepository（它是数据层概念，不在此重复声明）。
 */

interface PlanRepository {
    fun observePlanCards(sortOrder: PlanSortOrder, query: String): Flow<List<PlanCard>>
    fun observeFolders(): Flow<List<PlanFolder>>
    fun observePlanDetail(planId: Long): Flow<PlanDetail?>
    fun observeTrash(): Flow<List<PlanCard>>

    suspend fun getPlanDetail(planId: Long): PlanDetail?
    suspend fun createPlan(
        name: String,
        description: String?,
        scheduledDays: Set<Int>,
        defaultRestSeconds: Int?,
        folderId: Long?,
        copyFromPlanId: Long? = null,
        /** 当日主要训练部位（大肌群自由组合）；仅作标记，不限制动作选择 */
        focus: List<MajorGroup> = emptyList(),
    ): Outcome<Long>

    suspend fun savePlanDetail(detail: PlanDetail): Outcome<Unit>
    suspend fun renamePlan(planId: Long, name: String): Outcome<Unit>
    suspend fun setFavorite(planId: Long, favorite: Boolean): Outcome<Unit>
    suspend fun duplicatePlan(planId: Long): Outcome<Long>

    suspend fun moveToTrash(planId: Long): Outcome<Unit>
    suspend fun restoreFromTrash(planId: Long): Outcome<Unit>
    suspend fun permanentlyDelete(planId: Long): Outcome<Unit>

    suspend fun addExerciseToPlan(
        planId: Long,
        exerciseDefinitionId: Long,
        exerciseName: String,
        exerciseType: ExerciseType,
        isUnilateral: Boolean,
        defaultSets: Int,
        defaultReps: String?,
        defaultRestSeconds: Int,
    ): Outcome<Long>

    suspend fun removeExercise(planExerciseId: Long): Outcome<Unit>
    suspend fun reorderExercises(planId: Long, orderedIds: List<Long>): Outcome<Unit>
    suspend fun setExerciseRestOverride(planExerciseId: Long, seconds: Int?): Outcome<Unit>
    suspend fun setSupersetTag(planExerciseIds: List<Long>, tag: Int?): Outcome<Unit>
    suspend fun updateExerciseTarget(
        planExerciseId: Long,
        targetSets: Int,
        targetReps: String?,
        targetWeightKg: Double?,
    ): Outcome<Unit>

    suspend fun setPlanExerciseMedia(planExerciseId: Long, media: MediaRef?): Outcome<Unit>

    suspend fun createFolder(name: String): Outcome<Long>
    suspend fun deleteFolder(folderId: Long): Outcome<Unit>
    suspend fun assignFolder(planId: Long, folderId: Long?): Outcome<Unit>

    /** 回收站保留期清理（30 天），由 WorkManager 调用 */
    suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int>
}

interface ExerciseRepository {
    fun observeExercise(id: Long): Flow<Exercise?>
    fun observeRecentlyUsed(limit: Int = 20): Flow<List<Exercise>>
    fun observeFiltered(filter: ExerciseFilter): Flow<List<Exercise>>

    suspend fun getExercise(id: Long): Exercise?
    suspend fun createCustomExercise(
        name: String,
        type: ExerciseType,
        primaryMuscle: MuscleGroup,
        equipment: Equipment,
        isUnilateral: Boolean,
        defaultSets: Int,
        defaultReps: String?,
        defaultRestSeconds: Int,
        instructions: String?,
        colorKey: String? = null,
        /** 大肌群/类别（v2 分类体系）；缺省时按 primaryMuscle 推导 */
        majorGroup: MajorGroup? = null,
        /** 细分肌群（中文名 ID，须属于 majorGroup） */
        subGroup: String? = null,
    ): Outcome<Long>

    suspend fun updateExercise(exercise: Exercise): Outcome<Unit>
    suspend fun setFavorite(id: Long, favorite: Boolean): Outcome<Unit>
    suspend fun archive(id: Long): Outcome<Unit>
    suspend fun markUsed(id: Long): Outcome<Unit>
    suspend fun setExerciseMedia(id: Long, media: MediaRef?): Outcome<Unit>
    suspend fun importSeedIfNeeded(force: Boolean = false): Outcome<Int>

    // 回收站：删除（软）→ 恢复 / 彻底删除（仅自定义动作）
    fun observeTrash(): Flow<List<Exercise>>
    suspend fun moveToTrash(id: Long): Outcome<Unit>
    suspend fun restoreFromTrash(id: Long): Outcome<Unit>
    suspend fun deleteForever(id: Long): Outcome<Unit>

    /** 回收站保留期清理（30 天），由 WorkManager 调用；返回清理的条数 */
    suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int>
}

/** 日历上已绑定的计划（含计划名与颜色：日历格与日期详情共用同一份数据） */
data class ScheduledPlan(
    val epochDay: Long,
    val planId: Long,
    val planName: String,
    val colorHex: String? = null,
    /** 当天的开始时间（0:00 起算的分钟数），null = 未指定时间（全天） */
    val startMinuteOfDay: Int? = null,
)

/**
 * 日历排程：把训练计划绑定到**具体日期**（一天可绑多个计划）。
 *
 * 与 `PlanCard.scheduledDays`（每周几的循环排程）互补：
 *  - 循环排程回答"每周一练腿"
 *  - 本仓库回答"10 月 20 日上午推、晚上拉"
 * 两者并存，日历页展示的是两者的合集视图。
 */
interface ScheduleRepository {
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<ScheduledPlan>>
    fun observeDay(epochDay: Long): Flow<List<ScheduledPlan>>

    suspend fun bind(epochDay: Long, planId: Long, startMinuteOfDay: Int? = null): Outcome<Unit>
    suspend fun unbind(epochDay: Long, planId: Long): Outcome<Unit>
}

/** 首页"继续训练"列表条目 */
data class ActiveSessionInfo(
    val id: Long,
    val planName: String,
    val startedAt: Long,
    val completedSets: Int,
)

interface WorkoutRepository {
    fun observeActiveSessionId(): Flow<Long?>

    /** 全部进行中的会话：支持同时存在多个未完成训练，互不影响 */
    fun observeActiveSessions(): Flow<List<ActiveSessionInfo>>
    fun observeSessionSummary(sessionId: Long): Flow<SessionSummary?>
    fun observeHistory(query: String, limit: Int = 200): Flow<List<SessionSummary>>
    fun observeHistoryInRange(fromEpochMs: Long, toEpochMs: Long): Flow<List<SessionSummary>>

    // 回收站：删除（软）→ 恢复 / 彻底删除
    fun observeTrash(): Flow<List<SessionSummary>>
    suspend fun restoreSessionFromTrash(sessionId: Long): Outcome<Unit>
    suspend fun deleteSessionForever(sessionId: Long): Outcome<Unit>

    /** 回收站保留期清理（30 天），由 WorkManager 调用；返回清理的条数 */
    suspend fun purgeExpiredTrash(retentionDays: Int): Outcome<Int>
    fun observeCompletedStartEpochDays(): Flow<List<Long>>

    suspend fun getLastPerformance(exerciseDefinitionId: Long): LastPerformance
    suspend fun previousSessionSets(exerciseDefinitionId: Long): List<com.fitnesspro.core.model.SetRecord>

    /**
     * 同一计划上一次已完成训练的容量，用于总结页的"与上次对比"。
     * 没有可比的上一次（首次执行该计划 / 自由训练）时返回 null。
     */
    suspend fun previousSessionVolumeKg(planId: Long, excludeSessionId: Long): Double?

    suspend fun updateSessionNotes(
        sessionId: Long,
        notes: String?,
        rpe: Int?,
        mood: String?,
    ): Outcome<Unit>

    suspend fun deleteSession(sessionId: Long): Outcome<Unit>
    suspend fun completedCountForPlan(planId: Long): Int

    /**
     * 某个本地日（epochDay）已完成训练按计划的聚合（容量/组数/时长求和）。
     * 用于今日页/首页的"完成训练"状态块；同一天同一计划多次训练会合并。
     */
    fun observeCompletedByPlanOnDay(epochDay: Long): Flow<List<CompletedSessionSummary>>

    /**
     * [fromEpochDay, toEpochDay] 区间内已完成训练：planId → 完成日的 epochDay 集合。
     * 用于周视图"已完成"绿色标识与训练计划小结的"已完成"标注。
     */
    fun observeCompletedPlanDaysInRange(fromEpochDay: Long, toEpochDay: Long): Flow<Map<Long, Set<Long>>>

    /** 当天自由训练（无计划）的完成聚合；当天没有自由训练时为 null（planId 恒为 -1 哨兵） */
    fun observeFreestyleCompletedOnDay(epochDay: Long): Flow<CompletedSessionSummary?>

    /** 区间内自由训练（无计划）的完成开始时间（epochMs）；训练日历据此展示"自由训练"条目 */
    fun observeFreestyleStartsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>
}

/** 当天某计划的已完成训练聚合数据 */
data class CompletedSessionSummary(
    val planId: Long,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val totalDurationSeconds: Int,
)

interface StatsRepository {
    suspend fun dailyVolume(sinceEpochMs: Long): List<VolumePoint>
    suspend fun muscleDistribution(sinceEpochMs: Long): List<MuscleVolume>
    suspend fun topExercises(sinceEpochMs: Long, limit: Int = 10): List<ExerciseFrequency>
    suspend fun exerciseTrend(exerciseDefinitionId: Long): List<ExerciseTrendPoint>
    suspend fun calendarHeatmap(): List<DayCount>
    suspend fun streakDays(firstDayOfWeek: Int): Int
    suspend fun totalCompletedSessions(): Int
}

data class VolumePoint(
    val epochDay: Long,
    val volumeKg: Double,
    val sessionCount: Int,
    val totalSets: Int,
)

data class MuscleVolume(
    val muscle: MuscleGroup,
    val volumeKg: Double,
    val setCount: Int,
)

data class ExerciseFrequency(
    val exerciseDefinitionId: Long,
    val name: String,
    val sessionCount: Int,
    val setCount: Int,
    val volumeKg: Double,
)

data class DayCount(val epochDay: Long, val count: Int)

interface BodyRepository {
    fun observeAll(): Flow<List<BodyMeasurement>>
    suspend fun latestWeightKg(): Double?

    /**
     * 按 [BodyMeasurement.epochDay] upsert（0 表示"今天"）。
     * 同一天已存在的记录做字段级合并，而不是覆盖 —— 手动录入的围度不会
     * 被 Health Connect 同步进来的体重抹掉。
     */
    suspend fun upsertByDay(measurement: BodyMeasurement): Outcome<Long>
    suspend fun delete(id: Long): Outcome<Unit>
}

interface PersonalRecordRepository {
    fun observeAll(): Flow<List<PersonalRecord>>
    fun observeForExercise(exerciseDefinitionId: Long): Flow<List<PersonalRecord>>
    suspend fun clearForExercise(exerciseDefinitionId: Long): Outcome<Unit>
}

/** 媒体文件引用统计，供设置页展示占用空间与 GC 使用 */
interface MediaStatisticsRepository {
    val totalSizeBytes: Flow<Long>
    val fileCount: Flow<Int>
    suspend fun findOrphanBytes(): Long

    /**
     * 清理孤立媒体。**默认不删除**（返回 0）：真正的安全清理走
     * MaintenanceRepository.purgeOrphans（先复查引用，再删文件 + 缩略图 + 登记行）。
     */
    suspend fun purgeOrphans(): Long = 0L
}
