package com.fitnesspro.feature.workout.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 休息到点闹钟。
 *
 * 使用 `setAndAllowWhileIdle()` 而不是 `setExactAndAllowWhileIdle()`：
 *  - 后者在 API 31+ 需要 `SCHEDULE_EXACT_ALARM` 权限，用户可拒绝，且 Play 有使用场景限制
 *  - 前者**不需要任何权限**
 *  - 训练过程中设备处于用户活跃状态，不会进入 Doze，实际触发误差通常在秒级
 *  - 即便出现延迟，也只影响"提示音"，不影响倒计时正确性（正确性由 endsAtEpochMs 保证）
 */
@Singleton
class RestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(endsAtEpochMs: Long, exerciseName: String, nextSetHint: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                endsAtEpochMs,
                buildPendingIntent(endsAtEpochMs, exerciseName, nextSetHint),
            )
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
