package com.fitnesspro.feature.workout.timer

import com.fitnesspro.core.domain.timer.RestTimerGateway
import com.fitnesspro.core.model.RestState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 休息计时平台端口的实现。
 *
 * 职责分工（ADR-06）：
 *  - **显示**由普通通知承担，倒计时由 SystemUI 自绘，应用零后台开销
 *  - **到点提醒**由 AlarmManager 一次性闹钟承担，进程被杀也不丢
 *  - **正确性**由 :core:data 里持久化的 endsAtEpochMs 承担，与本类无关
 *
 * 因此本类中的所有操作都是"尽力而为"的副作用：即使全部失败，
 * 用户回到训练页看到的倒计时依然准确。
 */
@Singleton
class RestTimerGatewayImpl @Inject constructor(
    private val notifier: RestTimerNotifier,
    private val alarmScheduler: RestAlarmScheduler,
) : RestTimerGateway {

    override suspend fun start(state: RestState.Running) {
        notifier.showRunning(state)
        if (state.pausedRemainingMs == null) {
            alarmScheduler.schedule(
                endsAtEpochMs = state.endsAtEpochMs,
                exerciseName = state.exerciseName,
                nextSetHint = state.nextSetHint,
            )
        } else {
            // 暂停状态下不应有活跃闹钟
            alarmScheduler.cancel()
        }
    }

    override suspend fun update(state: RestState.Running) {
        notifier.showRunning(state)
        if (state.pausedRemainingMs == null) {
            alarmScheduler.schedule(
                endsAtEpochMs = state.endsAtEpochMs,
                exerciseName = state.exerciseName,
                nextSetHint = state.nextSetHint,
            )
        } else {
            alarmScheduler.cancel()
        }
    }

    override suspend fun cancel() {
        alarmScheduler.cancel()
        notifier.cancelRunning()
    }

    override fun notifyFinished(token: Long, exerciseName: String, nextSetHint: String) {
        notifier.showFinished(exerciseName, nextSetHint)
    }
}
