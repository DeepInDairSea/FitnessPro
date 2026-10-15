package com.fitnesspro.feature.workout.di

import com.fitnesspro.core.domain.reminder.WorkoutReminderScheduler
import com.fitnesspro.core.domain.timer.RestTimerGateway
import com.fitnesspro.feature.workout.timer.RestTimerGatewayImpl
import com.fitnesspro.feature.workout.timer.reminder.WorkoutReminderManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 训练相关端口的绑定。
 *
 * 接口定义在 :core:domain，实现在 :feature:workout（因为通知文案资源在这里）。
 * :core:data 的 WorkoutSessionManager 只依赖接口，因此不会反向依赖 feature。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkoutTimerModule {

    @Binds
    @Singleton
    abstract fun bindRestTimerGateway(impl: RestTimerGatewayImpl): RestTimerGateway

    @Binds
    @Singleton
    abstract fun bindWorkoutReminderScheduler(impl: WorkoutReminderManager): WorkoutReminderScheduler
}
