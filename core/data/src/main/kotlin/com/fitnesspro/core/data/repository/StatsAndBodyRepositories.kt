package com.fitnesspro.core.data.repository

import androidx.room.withTransaction
import com.fitnesspro.core.domain.calculator.MetabolicCalculator
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.UserSettings
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.data.mapper.toDomain
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.database.dao.BodyMeasurementDao
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.MediaDao
import com.fitnesspro.core.database.dao.PersonalRecordDao
import com.fitnesspro.core.database.dao.StatsDao
import com.fitnesspro.core.database.entity.BodyMeasurementEntity
import com.fitnesspro.core.domain.repository.BodyRepository
import com.fitnesspro.core.domain.repository.DayCount
import com.fitnesspro.core.domain.repository.ExerciseFrequency
import com.fitnesspro.core.domain.repository.MediaStatisticsRepository
import com.fitnesspro.core.domain.repository.MuscleVolume
import com.fitnesspro.core.domain.repository.PersonalRecordRepository
import com.fitnesspro.core.domain.repository.StatsRepository
import com.fitnesspro.core.domain.repository.VolumePoint
import com.fitnesspro.core.model.BodyMeasurement
import com.fitnesspro.core.model.ExerciseTrendPoint
import com.fitnesspro.core.model.PersonalRecord
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val time: TimeProvider,
) : StatsRepository {

    override suspend fun dailyVolume(sinceEpochMs: Long): List<VolumePoint> =
        statsDao.dailyVolume(sinceEpochMs).map { it.toDomain() }

    override suspend fun muscleDistribution(sinceEpochMs: Long): List<MuscleVolume> =
        // 热身组是否计入容量跟随用户设置；此处默认排除（与容量口径保持一致）
        statsDao.muscleDistribution(sinceEpochMs, includeWarmup = 0).map { it.toDomain() }

    override suspend fun topExercises(sinceEpochMs: Long, limit: Int): List<ExerciseFrequency> =
        statsDao.topExercises(sinceEpochMs, limit).map { it.toDomain() }

    override suspend fun exerciseTrend(exerciseDefinitionId: Long): List<ExerciseTrendPoint> =
        statsDao.exerciseTrend(exerciseDefinitionId).map { it.toDomain() }

    override suspend fun calendarHeatmap(): List<DayCount> =
        statsDao.calendarHeatmap().map { it.toDomain() }

    /**
     * 连续打卡周数：从本周往前逐周检查是否有训练记录。
     * 按"周"而不是"天"计算 —— 力量训练的自然节奏是每周 3-5 次，
     * 用"连续天数"会让几乎所有人在休息日清零，毫无意义。
     */
    override suspend fun streakDays(firstDayOfWeek: Int): Int {
        val days = statsDao.completedDays().toSet()
        if (days.isEmpty()) return 0

        val today = time.todayEpochDay()
        var weekStart = time.weekStartEpochDay(today, firstDayOfWeek)
        var streak = 0

        // 最多回溯 5 年，避免脏数据导致死循环
        repeat(260) {
            val weekEnd = weekStart + 6
            val hasTraining = (weekStart..weekEnd).any { it in days }
            if (!hasTraining) {
                // 本周还没练不算断签，允许从本周继续往前算
                if (streak == 0 && weekStart == time.weekStartEpochDay(today, firstDayOfWeek)) {
                    // 继续查看上一周
                } else {
                    return streak
                }
            } else {
                streak++
            }
            weekStart -= 7
        }
        return streak
    }

    override suspend fun totalCompletedSessions(): Int = statsDao.totalCompletedSessions()
}

@Singleton
class BodyRepositoryImpl @Inject constructor(
    private val bodyDao: BodyMeasurementDao,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : BodyRepository {

    override fun observeAll(): Flow<List<BodyMeasurement>> =
        bodyDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun latestWeightKg(): Double? =
        runCatching { bodyDao.latest()?.weightKg }.getOrNull()

    /**
     * 用体重 + 身高 + 设置里的年龄性别估算 BMR；缺一项返回 null（绝不补一个骗人的数）。
     * 身高按写入优先取本次，再退回身体表里同一天已有记录的身高，
     * 再退回 body_measurement 上最近一次录到的身高。
     */
    private suspend fun computeBmrForRecord(
        weightKg: Double?,
        epochDay: Long,
        fallbackHeightCm: Double? = null,
    ): Double? {
        if (weightKg == null || weightKg <= 0) return null
        val userSettings: UserSettings = runCatching { settings.settings.first() }.getOrNull()
            ?: return null
        val height = fallbackHeightCm
            ?: runCatching { bodyDao.getByDay(epochDay)?.heightCm }.getOrNull()
            ?: runCatching { bodyDao.latestWithHeight()?.heightCm }.getOrNull()
        return MetabolicCalculator.bmr(
            weightKg = weightKg,
            heightCm = height,
            age = userSettings.age,
            gender = when (userSettings.gender) {
                "MALE" -> MetabolicCalculator.Gender.MALE
                "FEMALE" -> MetabolicCalculator.Gender.FEMALE
                else -> null
            },
        )
    }

    override suspend fun upsertByDay(measurement: BodyMeasurement): Outcome<Long> =
        runCatchingOutcomeSuspend {
            val now = time.nowEpochMs()
            val epochDay = measurement.epochDay.takeIf { it > 0 } ?: time.todayEpochDay()
            val existing = bodyDao.getByDay(epochDay)

            if (existing == null) {
                bodyDao.insert(
                    BodyMeasurementEntity(
                        epochDay = epochDay,
                        recordedAt = measurement.recordedAt.takeIf { it > 0 } ?: now,
                        weightKg = measurement.weightKg,
                        bodyFatPercent = measurement.bodyFatPercent,
                        muscleMassKg = measurement.muscleMassKg,
                        heightCm = measurement.heightCm,
                        visceralFatLevel = measurement.visceralFatLevel,
                        chestCm = measurement.chestCm,
                        waistCm = measurement.waistCm,
                        hipCm = measurement.hipCm,
                        armCm = measurement.armCm,
                        thighCm = measurement.thighCm,
                        calfCm = measurement.calfCm,
                        // 写库前自动估算 BMR：体重 + 本次/记录身高 + 设置里的年龄性别
                        // 缺任一项时存 null（UI 显示引导，不编一个数骗用户）
                        bmr = measurement.bmr ?: computeBmrForRecord(measurement.weightKg, epochDay),
                        source = measurement.source.name,
                        notes = measurement.notes,
                    )
                )
            } else {
                // 同一天重复录入视为修正，覆盖而不是新增 —— 避免趋势图上出现同一天两个点
                bodyDao.update(
                    existing.copy(
                        recordedAt = now,
                        weightKg = measurement.weightKg ?: existing.weightKg,
                        bodyFatPercent = measurement.bodyFatPercent ?: existing.bodyFatPercent,
                        muscleMassKg = measurement.muscleMassKg ?: existing.muscleMassKg,
                        heightCm = measurement.heightCm ?: existing.heightCm,
                        visceralFatLevel = measurement.visceralFatLevel ?: existing.visceralFatLevel,
                        chestCm = measurement.chestCm ?: existing.chestCm,
                        waistCm = measurement.waistCm ?: existing.waistCm,
                        hipCm = measurement.hipCm ?: existing.hipCm,
                        armCm = measurement.armCm ?: existing.armCm,
                        thighCm = measurement.thighCm ?: existing.thighCm,
                        calfCm = measurement.calfCm ?: existing.calfCm,
                        // 体重/身高变化后再估算；height 优先取本次实测、再退回写入日的记录身高
                        bmr = measurement.bmr ?: computeBmrForRecord(
                            measurement.weightKg ?: existing.weightKg,
                            epochDay,
                            fallbackHeightCm = measurement.heightCm ?: existing.heightCm,
                        ),
                        notes = measurement.notes ?: existing.notes,
                    )
                )
                existing.id
            }
        }

    override suspend fun delete(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { bodyDao.delete(id) }
}

@Singleton
class PersonalRecordRepositoryImpl @Inject constructor(
    private val prDao: PersonalRecordDao,
    private val exerciseDao: ExerciseDao,
) : PersonalRecordRepository {

    // PR 表只存 exerciseDefinitionId，动作名要回查动作库；
    // 少这一查，列表里所有记录都会显示成空白（历史上就是这样）
    private suspend fun nameOf(exerciseDefinitionId: Long): String =
        runCatching { exerciseDao.getById(exerciseDefinitionId)?.name }.getOrNull().orEmpty()

    override fun observeAll(): Flow<List<PersonalRecord>> =
        prDao.observeAll().map { rows ->
            rows.map { it.toDomain(nameOf(it.exerciseDefinitionId)) }
        }

    override fun observeForExercise(exerciseDefinitionId: Long): Flow<List<PersonalRecord>> =
        prDao.observeForExercise(exerciseDefinitionId).map { rows ->
            rows.map { it.toDomain(nameOf(it.exerciseDefinitionId)) }
        }

    override suspend fun clearForExercise(exerciseDefinitionId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { prDao.clearForExercise(exerciseDefinitionId) }
}

/**
 * 媒体空间统计与孤立文件清理。
 *
 * 重要：孤立判定必须**包含回收站中的计划**（deletedAt 非空）。
 * 如果只统计未删除的计划，那么"删除计划 → 媒体被判为孤儿 → 用户从回收站恢复计划"
 * 就会导致媒体已经被删掉、动作演示丢失。这是必须写进测试的安全约束（DEV 文档 §6.4）。
 */
@Singleton
class MediaStatisticsRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val appFlagDao: AppFlagDao,
    private val database: FitnessDatabase,
    private val time: TimeProvider,
) : MediaStatisticsRepository {

    override val totalSizeBytes: Flow<Long> = mediaDao.observeTotalSize()

    override val fileCount: Flow<Int> = mediaDao.observeCount()

    override suspend fun findOrphanBytes(): Long =
        runCatching { mediaDao.findOrphanCandidates().sumOf { it.sizeBytes } }.getOrDefault(0L)

    /**
     * 这里**刻意不实现删除**：清理必须走 [MaintenanceRepository.purgeOrphans]，
     * 那条路径会先复查引用再删文件 + 缩略图 + 登记行。
     * 只删登记行不删文件的实现会让磁盘文件变成"未登记孤儿"，反而更难收拾。
     */
    override suspend fun purgeOrphans(): Long = 0L
}
