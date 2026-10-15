package com.fitnesspro.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.database.dao.BodyMeasurementDao
import com.fitnesspro.core.database.dao.ExerciseDao
import com.fitnesspro.core.database.dao.FoodDao
import com.fitnesspro.core.database.dao.MediaDao
import com.fitnesspro.core.database.dao.PersonalRecordDao
import com.fitnesspro.core.database.dao.PlanDao
import com.fitnesspro.core.database.dao.PlanScheduleDao
import com.fitnesspro.core.database.dao.StatsDao
import com.fitnesspro.core.database.dao.WorkoutDao
import com.fitnesspro.core.database.entity.AppFlagEntity
import com.fitnesspro.core.database.entity.BodyMeasurementEntity
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.database.entity.ExerciseFtsEntity
import com.fitnesspro.core.database.entity.ExerciseMuscleEntity
import com.fitnesspro.core.database.entity.DietEntryEntity
import com.fitnesspro.core.database.entity.FoodDefinitionEntity
import com.fitnesspro.core.database.entity.MediaAssetEntity
import com.fitnesspro.core.database.entity.PersonalRecordEntity
import com.fitnesspro.core.database.entity.PlanExerciseEntity
import com.fitnesspro.core.database.entity.PlanScheduleEntity
import com.fitnesspro.core.database.entity.PlanFolderEntity
import com.fitnesspro.core.database.entity.PlanSetEntity
import com.fitnesspro.core.database.entity.TrainingPlanEntity
import com.fitnesspro.core.database.entity.WorkoutExerciseEntity
import com.fitnesspro.core.database.entity.WorkoutSessionEntity
import com.fitnesspro.core.database.entity.WorkoutSessionStateEntity
import com.fitnesspro.core.database.entity.WorkoutSetEntity

/**
 * 应用数据库。
 *
 * 约定：
 *  - exportSchema = true，schema JSON 输出到 core/database/schemas 并纳入版本控制（迁移测试依赖它）
 *  - **禁止** fallbackToDestructiveMigration（见 DatabaseModule）
 *  - 列名采用属性名（camelCase），因此索引与 SQL 均使用 camelCase
 *  - 枚举一律以 TEXT 存名称，避免枚举顺序变化引发无谓迁移
 */
@Database(
    entities = [
        // 动作库
        ExerciseDefinitionEntity::class,
        ExerciseMuscleEntity::class,
        ExerciseFtsEntity::class,
        // 计划
        PlanFolderEntity::class,
        TrainingPlanEntity::class,
        PlanExerciseEntity::class,
        PlanSetEntity::class,
        // 日历排程（计划绑定到具体日期）
        PlanScheduleEntity::class,
        // 训练
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        WorkoutSessionStateEntity::class,
        // 成就与数据
        PersonalRecordEntity::class,
        BodyMeasurementEntity::class,
        MediaAssetEntity::class,
        AppFlagEntity::class,
        // 饮食
        FoodDefinitionEntity::class,
        DietEntryEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun statsDao(): StatsDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun planScheduleDao(): PlanScheduleDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun mediaDao(): MediaDao
    abstract fun appFlagDao(): AppFlagDao
    abstract fun foodDao(): FoodDao

    companion object {
        const val NAME = "fitness_pro.db"
    }
}
