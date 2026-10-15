package com.fitnesspro.core.data.repository

import com.fitnesspro.core.data.mapper.toDomain
import com.fitnesspro.core.database.dao.PlanScheduleDao
import com.fitnesspro.core.database.entity.PlanScheduleEntity
import com.fitnesspro.core.domain.repository.ScheduleRepository
import com.fitnesspro.core.domain.repository.ScheduledPlan
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 日历排程实现。
 *
 * 绑定用 (epochDay, planId) 作主键 + INSERT IGNORE，因此重复点击同一天同一计划
 * 是幂等的，不需要在 UI 层先查再写（那会有并发下的重复插入风险）。
 */
@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: PlanScheduleDao,
    private val time: TimeProvider,
) : ScheduleRepository {

    override fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<ScheduledPlan>> =
        scheduleDao.observeRange(fromEpochDay, toEpochDay)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDay(epochDay: Long): Flow<List<ScheduledPlan>> =
        scheduleDao.observeByDay(epochDay).map { rows -> rows.map { it.toDomain() } }

    override suspend fun bind(
        epochDay: Long,
        planId: Long,
        startMinuteOfDay: Int?,
    ): Outcome<Unit> = runCatchingOutcomeSuspend {
        scheduleDao.bind(
            PlanScheduleEntity(
                epochDay = epochDay,
                planId = planId,
                startMinuteOfDay = startMinuteOfDay,
                createdAt = time.nowEpochMs(),
            ),
        )
    }

    override suspend fun unbind(epochDay: Long, planId: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { scheduleDao.unbind(epochDay, planId) }
}
