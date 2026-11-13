package com.fitnesspro.feature.workout.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 休息到点闹钟。
 *
 * 优先 `setExactAndAllowWhileIdle()`：训练提示对准时性很敏感 —— 用户反馈
 * "倒计时结束了很久才响"，非精确闹钟在系统闹钟批处理/厂商省电下可以延迟数分钟，
 * 这对组间休息完全不可接受。API 31+ 需要精确闹钟权限，拿不到时降级回
 * `setAndAllowWhileIdle()`（无需权限，正确性仍由 endsAtEpochMs 保证，只是提示可能晚）。
 */
@Singleton
class RestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(endsAtEpochMs: Long, exerciseName: String, nextSetHint: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(endsAtEpochMs, exerciseName, nextSetHint)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endsAtEpochMs,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endsAtEpochMs,
                    pendingIntent,
                )
            }
        }.onFailure { Timber.w(it, "注册休息闹钟失败") }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(buildPendingIntent(0L, "", "")) }
    }

    private fun buildPendingIntent(
        endsAtEpochMs: Long,
        exerciseName: String,
        nextSetHint: String,
    ): PendingIntent {
        val intent = Intent(context, RestAlarmReceiver::class.java).apply {
            action = RestTimerActions.ACTION_FINISHED
            putExtra(RestTimerActions.EXTRA_ENDS_AT, endsAtEpochMs)
            putExtra(RestTimerActions.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(RestTimerActions.EXTRA_NEXT_SET_HINT, nextSetHint)
        }
        return PendingIntent.getBroadcast(
            context,
            RestTimerActions.REQUEST_REST_ALARM,
            intent,
            // FLAG_UPDATE_CURRENT 保证多次注册是覆盖而不是堆积；FLAG_IMMUTABLE 是 API 31+ 的强制要求
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
