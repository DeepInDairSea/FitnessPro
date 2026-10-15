package com.fitnesspro.feature.workout.timer.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fitnesspro.core.domain.reminder.WorkoutReminderScheduler
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 训练提醒的调度实现。
 *
 * 为什么用 **WorkManager 一次性任务**而不是 AlarmManager 定周期：
 *  - 提醒是"天级"精度，不需要精确到秒 —— `setAndAllowWhileIdle` 的精确性在这里毫无意义，
 *    反而要处理 SCHEDULE_EXACT_ALARM 权限与 ROM 差异
 *  - WorkManager 任务**自动跨重启存活**，不用像休息闹钟那样在 BootReceiver 里重建
 *  - Worker 到点后自行排下一次，设置变更时 REPLACE 掉旧任务即可，状态永远只有一份
 */
@Singleton
class WorkoutReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : WorkoutReminderScheduler {

    override suspend fun reschedule() {
        val s = settings.current()
        if (!s.workoutReminderEnabled || s.workoutReminderDays.isEmpty()) {
            cancel()
            return
        }

        val delayMs = nextTriggerMillis(
            now = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(time.nowEpochMs()),
                java.time.ZoneId.systemDefault(),
            ),
            days = s.workoutReminderDays,
            hour = s.workoutReminderHour,
            minute = s.workoutReminderMinute,
        )

        val request = OneTimeWorkRequestBuilder<WorkoutReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                // 提醒不需要网络/电量条件；只要求进程能被拉起即可
                Constraints.NONE,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    internal companion object {
        const val UNIQUE_WORK_NAME = "workout_reminder"

        /**
         * 下一次触发时刻（毫秒）。星期语义为 ISO：1 = 周一 … 7 = 周日，
         * 与设置页的"一二三四五六日"chip 及计划的 scheduledDays 保持一致。
         *
         * 若今天就是提醒日且时刻未过，返回今天；否则找未来 7 天内最近的命中日。
         */
        fun nextTriggerMillis(
            now: LocalDateTime,
            days: Set<Int>,
            hour: Int,
            minute: Int,
        ): Long {
            require(days.isNotEmpty()) { "提醒日不能为空" }
            val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            val today = now.toLocalDate()

            (0..7L).forEach { offset ->
                val date = today.plusDays(offset)
                if (date.dayOfWeek.isoNumber() !in days) return@forEach
                val trigger = LocalDateTime.of(date, targetTime)
                if (trigger.isAfter(now)) {
                    return ChronoUnit.MILLIS.between(now, trigger).coerceAtLeast(0L)
                }
            }
            // 理论不可达：7 天内必有一次命中。防御性返回明天的同一时刻
            return TimeUnit.DAYS.toMillis(1)
        }

        private fun DayOfWeek.isoNumber(): Int = value
    }
}
