package com.fitnesspro.core.domain.reminder

/**
 * 训练提醒的调度端口。
 *
 * 接口在 :core:domain，实现在 :feature:workout（通知文案与渠道资源在那里），
 * 与 [com.fitnesspro.core.domain.timer.RestTimerGateway] 同一模式：
 * 设置页（:feature:settings）只依赖本接口，不会反向依赖 feature。
 *
 * 实现要求：
 *  - [reschedule] 必须读当前设置自行决定"排程还是取消"——调用方不该重复设置逻辑
 *  - 调度必须跨进程死亡存活（WorkManager 持久化）
 *  - 到点后由实现自行排下一次，不需要调用方再触发
 */
interface WorkoutReminderScheduler {

    /** 按当前设置重新调度（启用且选了星期才排程，否则取消） */
    suspend fun reschedule()

    /** 立即取消已有提醒（重置设置 / 清空数据时调用） */
    fun cancel()
}
