package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitnesspro.core.database.entity.PlanScheduleEntity
import com.fitnesspro.core.database.entity.PlanScheduleRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanScheduleDao {

    /** 某一天绑定的计划（含计划名，日历详情列表用） */
    @Query(
        """
        SELECT s.epochDay AS epochDay, s.planId AS planId, p.name AS planName,
               p.colorHex AS colorHex, s.startMinuteOfDay AS startMinuteOfDay
        FROM plan_schedule s
        JOIN training_plan p ON p.id = s.planId
        WHERE s.epochDay = :epochDay AND p.deletedAt IS NULL
        ORDER BY s.startMinuteOfDay IS NULL, s.startMinuteOfDay, p.id
        """
    )
    fun observeByDay(epochDay: Long): Flow<List<PlanScheduleRow>>

    /** 一段日期区间内的全部排程（月视图用，含计划名与颜色） */
    @Query(
        """
        SELECT s.epochDay AS epochDay, s.planId AS planId, p.name AS planName,
               p.colorHex AS colorHex, s.startMinuteOfDay AS startMinuteOfDay
        FROM plan_schedule s
        JOIN training_plan p ON p.id = s.planId
        WHERE s.epochDay BETWEEN :fromEpochDay AND :toEpochDay AND p.deletedAt IS NULL
        ORDER BY s.epochDay, s.startMinuteOfDay IS NULL, s.startMinuteOfDay
        """
    )
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<PlanScheduleRow>>

    /** 绑定幂等：同一天重复绑定同一计划不会报错 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun bind(schedule: PlanScheduleEntity)

    @Query("DELETE FROM plan_schedule WHERE epochDay = :epochDay AND planId = :planId")
    suspend fun unbind(epochDay: Long, planId: Long)

    /** 计划被彻底删除时的兜底清理（外键 CASCADE 已覆盖，这里用于批量/显式清理） */
    @Query("DELETE FROM plan_schedule WHERE planId = :planId")
    suspend fun clearForPlan(planId: Long)
}
