package com.fitnesspro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 日历排程：某一天（epochDay）绑定某个训练计划。
 *
 * 与 `training_plan.scheduledDays` 的区别：
 *  - `scheduledDays` 是"每周几"的**循环排程**（每周一练腿），不针对具体日期
 *  - 本表是**具体日期**的绑定，一天可以绑多个计划（上午推、晚上拉），
 *    也支持临时加练/调整，是"日历上订阅计划"的数据源
 *
 * 主键用 (epochDay, planId) 让"同一天重复绑定同一计划"天然幂等，
 * 不需要在业务层做去重判断。
 */
@Entity(
    tableName = "plan_schedule",
    primaryKeys = ["epochDay", "planId"],
    indices = [Index("epochDay"), Index("planId")],
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanScheduleEntity(
    val epochDay: Long,
    val planId: Long,
    /**
     * 当天的开始时间（0:00 起算的分钟数）。null = 未指定时间（全天）。
     * 仅用于写入系统日历时生成具体日程，应用内排程不依赖它。
     */
    val startMinuteOfDay: Int? = null,
    val createdAt: Long,
)

/** 日历查询行：排程 + 计划名（日历上要显示"这天练什么"） */
data class PlanScheduleRow(
    val epochDay: Long,
    val planId: Long,
    val planName: String,
    val colorHex: String?,
    val startMinuteOfDay: Int?,
)
