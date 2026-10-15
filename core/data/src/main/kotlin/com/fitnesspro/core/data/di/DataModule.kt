package com.fitnesspro.core.data.di

import com.fitnesspro.core.data.repository.BodyRepositoryImpl
import com.fitnesspro.core.data.repository.ExerciseRepositoryImpl
import com.fitnesspro.core.data.repository.FoodRepositoryImpl
import com.fitnesspro.core.data.repository.MediaStatisticsRepositoryImpl
import com.fitnesspro.core.data.repository.PersonalRecordRepositoryImpl
import com.fitnesspro.core.data.repository.PlanRepositoryImpl
import com.fitnesspro.core.data.repository.ScheduleRepositoryImpl
import com.fitnesspro.core.data.repository.StatsRepositoryImpl
import com.fitnesspro.core.data.repository.WorkoutRepositoryImpl
import com.fitnesspro.core.domain.repository.BodyRepository
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.domain.repository.FoodRepository
import com.fitnesspro.core.domain.repository.MediaStatisticsRepository
import com.fitnesspro.core.domain.repository.PersonalRecordRepository
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.ScheduleRepository
import com.fitnesspro.core.domain.repository.StatsRepository
import com.fitnesspro.core.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindBodyRepository(impl: BodyRepositoryImpl): BodyRepository

    @Binds
    @Singleton
    abstract fun bindPersonalRecordRepository(impl: PersonalRecordRepositoryImpl): PersonalRecordRepository

    @Binds
    @Singleton
    abstract fun bindMediaStatisticsRepository(impl: MediaStatisticsRepositoryImpl): MediaStatisticsRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindFoodRepository(impl: FoodRepositoryImpl): FoodRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CalculatorModule {

    /**
     * PersonalRecordDetector 是无状态纯函数类。
     *
     * 刻意不给它加 @Inject constructor：:core:domain 是纯 Kotlin JVM 模块，
     * 不应为了 DI 而引入 javax.inject 依赖（那会让领域层认识"框架"）。
     * 由数据层负责把它接进对象图。
     */
    @Provides
    @Singleton
    fun providePersonalRecordDetector(): com.fitnesspro.core.domain.calculator.PersonalRecordDetector =
        com.fitnesspro.core.domain.calculator.PersonalRecordDetector()
}

@Module
@InstallIn(SingletonComponent::class)
object ConcurrencyModule {

    /**
     * 串行化"完成一组"等写操作。
     *
     * 为什么需要：用户可能在上一组还在写库时快速点击下一组，
     * 或者在休息计时归零的瞬间点击"完成"。这些并发写入会导致
     * 光标推进错位、PR 重复判定。用一把 Mutex 把写入串行化，
     * 代价可忽略（每次写入是毫秒级），换来的是确定性的状态。
     */
    @Provides
    @Singleton
    @Named("workoutWriteMutex")
    fun provideWorkoutWriteMutex(): Mutex = Mutex()
}
