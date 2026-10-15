package com.fitnesspro.core.domain.timer

import com.fitnesspro.core.model.RestState

/**
 * 休息计时器的"外部世界"端口。
 *
 * 为什么需要这个接口：休息计时需要做三件平台相关的事 —— 发通知、注册系统闹钟、震动/提示音。
 * 这些属于 Android 层，不能在 :core:data 里实现（否则 domain/data 被平台细节污染），
 * 而实现放在 :feature:workout（因为通知文案资源在那里）。
 *
 * 关键设计（ADR-06）：**不使用前台服务**。
 *  - 倒计时由普通通知承载，用 setUsesChronometer + setChronometerCountDown 让 SystemUI 自绘
 *  - 到点提醒由 AlarmManager.setAndAllowWhileIdle 触发一个 BroadcastReceiver
 * 结果：零权限、零后台耗电、进程被杀后到点提醒不丢失，且没有 Play 审核风险。
 */
interface RestTimerGateway {

    /** 启动休息：发出带倒计时的常驻通知，并注册到点闹钟 */
    suspend fun start(state: RestState.Running)

    /** 更新（延长/缩短/暂停/恢复）：重写通知与闹钟，必须幂等 */
    suspend fun update(state: RestState.Running)

    /** 取消：清除通知与闹钟 */
    suspend fun cancel()

    /**
     * 到点提示（由 AlarmManager 触发的接收器调用）。
     * 需要做幂等判定：如果用户已经跳过/延长/结束训练，则不应响铃。
     * @param token 本次休息的唯一标识（用 endsAtEpochMs），用于与当前状态比对
     */
    fun notifyFinished(token: Long, exerciseName: String, nextSetHint: String)
}
