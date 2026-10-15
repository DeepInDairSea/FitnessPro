package com.fitnesspro.feature.workout.timer.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.feature.workout.R
import com.fitnesspro.feature.workout.timer.RestTimerNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 训练提醒到点执行。
 *
 * 设置以 **到点那一刻** 的值为准（而不是排程时的快照）：
 * 用户可能在等待期间改了设置或关闭提醒，快照会发一条用户已经取消的提醒。
 * 判定不通过就静默成功，再由 [WorkoutReminderManager.reschedule] 语义保证
 * 下一次排程与最新设置一致。
 */
@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val reminderManager: WorkoutReminderManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()

        if (s.workoutReminderEnabled && s.workoutReminderDays.isNotEmpty()) {
            val today = LocalDate.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis()),
                ZoneId.systemDefault(),
            )
            // 星期语义与设置页/计划一致：1 = 周一（ISO）
            if (today.dayOfWeek.value in s.workoutReminderDays) {
                showNotification()
            }
            reminderManager.reschedule()
        }

        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext

        // API 33+ 没有通知权限时发通知会抛 SecurityException；
        // 静默跳过而不是崩溃 —— 提醒是可选能力
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent().apply {
                setClassName(context.packageName, LAUNCHER_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, RestTimerNotifier.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(pickReminderMessage(context))
            // 吉祥物大图：折叠态右侧是小鸟头像，展开后是整只小鸟 —— 有"人"在催你，而不是一条系统广播
            .setLargeIcon(renderMascot(context))
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(renderMascot(context))
                    .bigLargeIcon(null as Bitmap?),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 到点随机抽一条吉祥物文案；数组缺失时回退默认文案 */
    private fun pickReminderMessage(context: Context): String {
        val messages = context.resources.getStringArray(R.array.reminder_messages)
        return if (messages.isEmpty()) {
            context.getString(R.string.reminder_text)
        } else {
            messages[Random.nextInt(messages.size)]
        }
    }

    /** 矢量吉祥物渲染成位图（通知大图只接受 Bitmap） */
    private fun renderMascot(context: Context, sizePx: Int = 512): Bitmap {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_mascot_bird)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, sizePx, sizePx)
        drawable?.draw(canvas)
        return bitmap
    }

    private companion object {
        const val NOTIFICATION_ID = 2001
        const val REQUEST_CODE = 2101
        private const val LAUNCHER_ACTIVITY = "com.fitnesspro.MainActivity"
    }
}
