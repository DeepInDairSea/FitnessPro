package com.fitnesspro.feature.workout.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fitnesspro.core.model.RestState
import com.fitnesspro.feature.workout.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 休息计时的通知。
 *
 * 核心技巧：`setUsesChronometer(true)` + `setChronometerCountDown(true)` + `setWhen(endsAt)`
 * 让 **SystemUI 自己每秒重绘倒计时**，应用不需要常驻进程、不需要每秒刷新通知。
 * 这是"零前台服务方案"能成立的关键（ADR-06）。
 */
@Singleton
class RestTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actions: RestTimerActions,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.channel_rest_running_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_rest_running_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )

        // 结束渠道升级为 v2（静音）：提示音与振动由 RestAlarmReceiver 显式负责 ——
        // 渠道声音只在首次创建时注册，之后设置开关改不动它，且部分厂商 ROM 会静默渠道提示音。
        // IMPORTANCE_HIGH 保留：横幅弹出（heads-up）由重要级决定，与是否有声音无关。
        // 旧渠道直接删除，避免用户在系统设置里看到两个"组间休息结束"。
        nm.deleteNotificationChannel(LEGACY_CHANNEL_FINISHED)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINISHED,
                context.getString(R.string.channel_rest_finished_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_rest_finished_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.channel_reminder_name),
                // HIGH：训练提醒需要横幅弹出（heads-up）—— 吉祥物被压成一条静默通知就没有存在感了
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
                enableVibration(true)
            }
        )

        // 以下四个渠道补齐 PRD §2.10 的七渠道清单（PR / 久未训练 / 体重记录提醒 / 媒体清理建议）。
        // 渠道在此统一注册，保证用户在系统设置里能看到并单独控制通知开关。
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PR,
                context.getString(R.string.channel_pr_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_pr_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INACTIVITY,
                context.getString(R.string.channel_inactivity_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_inactivity_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BODY_REMINDER,
                context.getString(R.string.channel_body_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_body_reminder_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDIA_CLEANUP,
                context.getString(R.string.channel_media_cleanup_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_media_cleanup_desc)
                setShowBadge(false)
            }
        )
    }

    fun showRunning(state: RestState.Running) {
        val notification = buildRunning(state)
        notifySafely(NOTIFICATION_ID_RUNNING, notification)
    }

    fun showFinished(exerciseName: String, nextSetHint: String) {
        cancelRunning()
        val text = if (nextSetHint.isNotBlank()) {
            context.getString(R.string.rest_finished_next_set_hint, nextSetHint)
        } else {
            exerciseName
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.rest_finished_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(actions.openWorkoutIntent())
            .addAction(0, context.getString(R.string.rest_add_30s), actions.extendIntent())
            .build()
        notifySafely(NOTIFICATION_ID_FINISHED, notification)
    }

    fun cancelRunning() = runCatching { manager.cancel(NOTIFICATION_ID_RUNNING) }

    fun cancelAll() {
        runCatching {
            manager.cancel(NOTIFICATION_ID_RUNNING)
            manager.cancel(NOTIFICATION_ID_FINISHED)
        }
    }

    private fun buildRunning(state: RestState.Running): Notification {
        val isRunning = !state.isPaused
        val remainingSeconds = state.plannedSeconds
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(context.getString(R.string.rest_timer_title))
            .setContentText(
                if (isRunning) {
                    context.getString(
                        R.string.rest_timer_remaining_format,
                        remainingSeconds / 60,
                        remainingSeconds % 60,
                    )
                } else {
                    context.getString(R.string.rest_timer_paused)
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setContentIntent(actions.openWorkoutIntent())
            .addAction(0, context.getString(R.string.rest_skip), actions.skipIntent())
            .addAction(0, context.getString(R.string.rest_add_30s), actions.extendIntent())

        if (isRunning) {
            // 系统自绘倒计时：应用无需后台刷新
            builder.setWhen(state.endsAtEpochMs)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.addAction(
                0,
                context.getString(R.string.rest_pause),
                actions.pauseIntent(),
            )
        } else {
            builder.setShowWhen(false)
            builder.addAction(
                0,
                context.getString(R.string.rest_resume),
                actions.pauseIntent(),
            )
        }

        return builder.build()
    }

    /**
     * 通知权限可能被拒绝。这里必须吞掉 SecurityException：
     * 没有通知权限时，应用内倒计时与提示音仍然照常工作（PRD FR-12 的降级要求）。
     */
    private fun notifySafely(id: Int, notification: Notification) {
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_RUNNING = "rest_running"

        /** v2：改为静音渠道前的旧 ID，启动时删除 */
        const val LEGACY_CHANNEL_FINISHED = "rest_finished"
        const val CHANNEL_FINISHED = "rest_finished_v2"

        /** 训练提醒（每日计划提醒） */
        const val CHANNEL_REMINDER = "workout_reminder"
        const val CHANNEL_PR = "pr_achievement"
        const val CHANNEL_INACTIVITY = "inactivity_reminder"
        const val CHANNEL_BODY_REMINDER = "body_reminder"
        const val CHANNEL_MEDIA_CLEANUP = "media_cleanup"
        const val NOTIFICATION_ID_RUNNING = 1001
        const val NOTIFICATION_ID_FINISHED = 1002
    }
}

/**
 * 通知上的三个按钮对应的 PendingIntent。
 *
 * 全部指向 RestAlarmReceiver（普通广播接收器）而不是前台服务 —— 这正是 ADR-06
 * "零前台服务"的落地方式：跳过/延长/暂停都不需要启动任何 Service。
 */
@Singleton
class RestTimerActions @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun skipIntent() = broadcast(ACTION_SKIP)
    fun extendIntent() = broadcast(ACTION_EXTEND)
    fun pauseIntent() = broadcast(ACTION_PAUSE)

    fun openWorkoutIntent(): PendingIntent {
        val intent = Intent().apply {
            setClassName(context.packageName, LAUNCHER_ACTIVITY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(action: String): PendingIntent {
        val intent = Intent(context, RestAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_SKIP = "com.fitnesspro.action.REST_SKIP"
        const val ACTION_EXTEND = "com.fitnesspro.action.REST_EXTEND"
        const val ACTION_PAUSE = "com.fitnesspro.action.REST_PAUSE"
        const val ACTION_FINISHED = "com.fitnesspro.action.REST_FINISHED"
        const val EXTRA_ENDS_AT = "extra_ends_at"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_NEXT_SET_HINT = "extra_next_set_hint"

        const val REQUEST_REST_ALARM = 2001
        private const val REQUEST_OPEN = 2002

        /** 主 Activity 的全限定名。用字符串避免 feature 反向依赖 app 模块。 */
        private const val LAUNCHER_ACTIVITY = "com.fitnesspro.MainActivity"
    }
}
