package com.fitnesspro.feature.workout.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fitnesspro.core.common.di.ApplicationScope
import com.fitnesspro.core.data.workout.WorkoutSessionManager
import com.fitnesspro.core.domain.repository.SettingsRepository
import com.fitnesspro.feature.workout.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 休息计时的全部用户操作入口。
 *
 * 为什么用 BroadcastReceiver 而不是 Service：通知上的"跳过/延长/暂停"都是**瞬时操作**，
 * 用接收器即可完成，不需要启动任何 Service，也就不需要前台服务类型声明（ADR-06）。
 *
 * 所有分支都必须**幂等**：用户可能连点两次，闹钟可能与用户操作同时到达。
 * 幂等手段是 token（本次休息的 endsAtEpochMs）与数据库当前状态的比对。
 */
@AndroidEntryPoint
class RestAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionManager: WorkoutSessionManager
    @Inject lateinit var notifier: RestTimerNotifier
    @Inject lateinit var alarmScheduler: RestAlarmScheduler
    @Inject lateinit var ttsAnnouncer: RestTtsAnnouncer
    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestTimerActions.ACTION_SKIP -> {
                val pending = goAsync()
                applicationScope.launch {
                    try {
                        // 通知是全局单份：多会话并存时由"正在休息的会话"定位归属
                        sessionManager.activeRestSessionId()?.let { sessionManager.skipRest(it) }
                        alarmScheduler.cancel()
                        notifier.cancelRunning()
                    } catch (t: Throwable) {
                        Timber.w(t, "跳过休息失败")
                    } finally {
                        pending.finish()
                    }
                }
            }

            RestTimerActions.ACTION_EXTEND -> {
                val pending = goAsync()
                applicationScope.launch {
                    try {
                        sessionManager.activeRestSessionId()
                            ?.let { sessionManager.extendRest(it, EXTEND_SECONDS) }
                    } catch (t: Throwable) {
                        Timber.w(t, "延长休息失败")
                    } finally {
                        pending.finish()
                    }
                }
            }

            RestTimerActions.ACTION_PAUSE -> {
                val pending = goAsync()
                applicationScope.launch {
                    try {
                        sessionManager.activeRestSessionId()
                            ?.let { sessionManager.toggleRestPause(it) }
                    } catch (t: Throwable) {
                        Timber.w(t, "切换休息暂停失败")
                    } finally {
                        pending.finish()
                    }
                }
            }

            RestTimerActions.ACTION_FINISHED -> {
                val token = intent.getLongExtra(RestTimerActions.EXTRA_ENDS_AT, -1L)
                val exerciseName = intent.getStringExtra(RestTimerActions.EXTRA_EXERCISE_NAME).orEmpty()
                val nextSetHint = intent.getStringExtra(RestTimerActions.EXTRA_NEXT_SET_HINT).orEmpty()
                val pending = goAsync()
                applicationScope.launch {
                    try {
                        onRestFinished(context, token, exerciseName, nextSetHint)
                    } catch (t: Throwable) {
                        Timber.w(t, "处理休息结束失败")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /**
     * 到点处理。
     *
     * 幂等判定：只有当数据库里记录的 endsAt 与闹钟携带的 token 一致时才提示。
     * 这可以避免"用户已经跳过休息，但闹钟仍按原时间响起"的重复提示。
     */
    private suspend fun onRestFinished(
        context: Context,
        token: Long,
        exerciseName: String,
        nextSetHint: String,
    ) {
        // 多会话并存：按"正在休息的会话"取幂等 token，而不是全局最新一条
        val currentToken = sessionManager.activeRestEndsAt()

        if (currentToken != token) {
            Timber.d("闹钟已过期（用户已跳过/延长/暂停），忽略本次提醒")
            return
        }

        notifier.showFinished(exerciseName, nextSetHint)

        val nowSettings = settings.current()
        if (nowSettings.restVibrationEnabled) {
            vibrate(context)
        }
        // 语音提示与震动并列：两个开关都默认关，开了才播
        if (nowSettings.restTtsEnabled) {
            ttsAnnouncer.announce(context.getString(R.string.rest_finished_title))
        }
    }

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return

            val pattern = longArrayOf(0, 200, 120, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }.onFailure { Timber.w(it, "震动失败") }
    }

    private companion object {
        const val EXTEND_SECONDS = 30
    }
}

/**
 * 设备重启后的清理。
 *
 * 闹钟在重启时会被系统清除，而休息最长 15 分钟 —— 重启耗时必然超过它，
 * 因此这里**只做清理，不做恢复**。这与"进程被杀"是两种不同的场景：
 * 后者闹钟仍在系统手里，到点照样提醒。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionManager: WorkoutSessionManager
    @Inject lateinit var alarmScheduler: RestAlarmScheduler
    @Inject lateinit var notifier: RestTimerNotifier
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                sessionManager.clearStaleRestStateOnBoot()
                alarmScheduler.cancel()
                notifier.cancelAll()
            } catch (t: Throwable) {
                Timber.w(t, "开机清理失败")
            } finally {
                pending.finish()
            }
        }
    }
}
