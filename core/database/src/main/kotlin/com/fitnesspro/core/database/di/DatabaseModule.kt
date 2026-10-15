package com.fitnesspro.core.database.di

import android.content.Context
import androidx.room.Room
import com.fitnesspro.core.database.ALL_MIGRATIONS
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.database.dao.BodyMeasurementDao
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.FoodDao
import com.fitnesspro.core.database.dao.MediaDao
import com.fitnesspro.core.database.dao.PersonalRecordDao
import com.fitnesspro.core.database.dao.PlanScheduleDao
import com.fitnesspro.core.database.dao.PlanDao
import com.fitnesspro.core.database.dao.StatsDao
import com.fitnesspro.core.database.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitnessDatabase =
        Room.databaseBuilder(context, FitnessDatabase::class.java, FitnessDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            // 注意：这里刻意不调用 fallbackToDestructiveMigration()。
            // schema 变更必须提供显式迁移，否则用户数据会被静默清空。
            .build()

    @Provides
    fun providePlanDao(db: FitnessDatabase): PlanDao = db.planDao()

    @Provides
    fun provideExerciseDao(db: FitnessDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutDao(db: FitnessDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideStatsDao(db: FitnessDatabase): StatsDao = db.statsDao()

    @Provides
    fun providePersonalRecordDao(db: FitnessDatabase): PersonalRecordDao = db.personalRecordDao()

    @Provides
    fun provideBodyMeasurementDao(db: FitnessDatabase): BodyMeasurementDao = db.bodyMeasurementDao()

    @Provides
    fun providePlanScheduleDao(db: FitnessDatabase): PlanScheduleDao = db.planScheduleDao()

    @Provides
    fun provideMediaDao(db: FitnessDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideAppFlagDao(db: FitnessDatabase): AppFlagDao = db.appFlagDao()

    @Provides
    fun provideFoodDao(db: FitnessDatabase): FoodDao = db.foodDao()
}
