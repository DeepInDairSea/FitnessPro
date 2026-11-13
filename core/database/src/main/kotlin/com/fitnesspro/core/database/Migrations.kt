package com.fitnesspro.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 全部数据库迁移集中于此，**禁止**在别处定义 Migration。
 *
 * 每次 schema 变更的固定流程（写进 PR 模板，DEV 文档 §4.6）：
 *  1. 修改 Entity → 提升 FitnessDatabase.version → 在本文件新增 MIGRATION_X_Y
 *  2. 运行 `./gradlew :core:database:kspDebugKotlin` 生成 schemas/X.json 并提交
 *  3. 在 core/database/src/androidTest 新增 MigrationXToYTest（MigrationTestHelper 加载旧 schema）
 *  4. 若涉及表结构重建，额外补一条链式迁移测试（1→2→3…）
 *  5. 严禁使用 fallbackToDestructiveMigration —— 那等于静默删除用户全部训练数据
 */
/** v2：统一回收站 —— 两个表各加 deletedAt 列，可空列 ALTER TABLE 即可，无需表重建 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE workout_session ADD COLUMN deletedAt INTEGER DEFAULT NULL")
    }
}

/** v3：身体数据新增 身高 / 内脏脂肪等级 两列（可空，ALTER TABLE 即可） */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE body_measurement ADD COLUMN heightCm REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE body_measurement ADD COLUMN visceralFatLevel REAL DEFAULT NULL")
    }
}

/**
 * v4：media_asset 增加 state 列。
 * 存量数据一律视为 ACTIVE（它们都是"当时已被引用的文件"），
 * 避免升级后旧文件被当成待引用孤儿而缩短宽限期。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_asset ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
    }
}

/**
 * v5：新增 plan_schedule 表（日历上把计划绑到具体日期）。
 *
 * 新表不影响任何存量数据；外键指向 training_plan 并 CASCADE，
 * 计划被彻底删除时排程自动清理。列/索引写法必须与实体定义严格一致，
 * 否则 Room 打开数据库时的 schema 校验会直接崩溃。
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plan_schedule (
                epochDay INTEGER NOT NULL,
                planId INTEGER NOT NULL,
                startMinuteOfDay INTEGER,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(epochDay, planId),
                FOREIGN KEY(planId) REFERENCES training_plan(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_plan_schedule_epochDay ON plan_schedule(epochDay)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_plan_schedule_planId ON plan_schedule(planId)",
        )
    }
}

/** v6：动作定义新增 colorKey 列（自定义身份色，可空，ALTER TABLE 即可） */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN colorKey TEXT DEFAULT NULL")
    }
}

/**
 * v7：新增 食物定义 / 每日饮食记录 两张表（饮食模块）。
 *
 * 新表不影响存量数据；diet_entry 外键指向 food_definition 并 CASCADE，
 * 食物被彻底删除时其记录自动清理（日常走软删除，不触发）。
 * 列/索引写法必须与实体定义严格一致，否则 Room 打库时的 schema 校验会崩溃。
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_definition (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                proteinPer50g REAL NOT NULL,
                fatPer50g REAL NOT NULL,
                carbPer50g REAL NOT NULL,
                kcalPer50g REAL NOT NULL,
                isCustom INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER DEFAULT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_food_definition_name ON food_definition(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_food_definition_category ON food_definition(category)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `diet_entry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `foodId` INTEGER NOT NULL,
                `grams` INTEGER NOT NULL,
                `protein` REAL NOT NULL,
                `fat` REAL NOT NULL,
                `carb` REAL NOT NULL,
                `kcal` REAL NOT NULL,
                `foodName` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`foodId`) REFERENCES `food_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diet_entry_epochDay ON diet_entry(epochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diet_entry_foodId ON diet_entry(foodId)")
    }
}

/**
 * v8：food_definition 补充"每勺"两列（蛋白补剂专用）。
 *
 * 普通食物不需要 → 默认 null；蛋白补剂种子升级会回写。
 * 可空 ALTER TABLE 即可，无需表重建。
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_definition ADD COLUMN scoopGrams INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE food_definition ADD COLUMN proteinPerScoop REAL DEFAULT NULL")
    }
}

/**
 * v9：身体记录补两列 —— calfCm（新增身高体型维度）/ bmr（基础代谢自动估算并落库）。
 *
 * 两列都是可空 ALTER 即可，不动其它字段：
 *  - 旧记录的 calfCm 留 null（用户没填过）
 *  - 旧记录的 bmr 留 null（升级后下次写入会重新估算）
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE body_measurement ADD COLUMN calfCm REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE body_measurement ADD COLUMN bmr REAL DEFAULT NULL")
    }
}

/**
 * v10：动作库 v2 分类体系 + 计划"当日重点部位"。
 *
 *  - exercise_definition 增加 majorGroup / subGroup 两列（可空 TEXT），
 *    并按旧 primaryMuscle 回填 majorGroup（细分 subGroup 只有种子数据知道，留给种子导入 v2 回写）
 *  - training_plan 增加 focus 列（逗号分隔的 MajorGroup 枚举名）
 * 全部为可空 ALTER TABLE，无需表重建。
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN majorGroup TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN subGroup TEXT DEFAULT NULL")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_definition_majorGroup` " +
                "ON exercise_definition (majorGroup)",
        )
        // 旧细分肌群 → 大肌群 回填（与新代码 ExerciseTaxonomy.legacyMajorOf 一致）
        db.execSQL(
            """
            UPDATE exercise_definition SET majorGroup = CASE primaryMuscle
                WHEN 'CHEST' THEN 'CHEST'
                WHEN 'UPPER_CHEST' THEN 'CHEST'
                WHEN 'INNER_CHEST' THEN 'CHEST'
                WHEN 'LOWER_CHEST' THEN 'CHEST'
                WHEN 'SHOULDERS' THEN 'SHOULDERS'
                WHEN 'FRONT_DELT' THEN 'SHOULDERS'
                WHEN 'SIDE_DELT' THEN 'SHOULDERS'
                WHEN 'REAR_DELT' THEN 'SHOULDERS'
                WHEN 'BACK' THEN 'BACK'
                WHEN 'UPPER_BACK' THEN 'BACK'
                WHEN 'LOWER_BACK' THEN 'BACK'
                WHEN 'LATS' THEN 'BACK'
                WHEN 'TRAPS' THEN 'BACK'
                WHEN 'QUADS' THEN 'LEGS'
                WHEN 'HAMSTRINGS' THEN 'LEGS'
                WHEN 'CALVES' THEN 'LEGS'
                WHEN 'GLUTES' THEN 'GLUTES'
                WHEN 'BICEPS' THEN 'ARMS'
                WHEN 'TRICEPS' THEN 'ARMS'
                WHEN 'FOREARMS' THEN 'ARMS'
                WHEN 'NECK' THEN 'NECK'
                WHEN 'CORE' THEN 'CORE'
                ELSE 'FUNCTIONAL'
            END
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE training_plan ADD COLUMN focus TEXT DEFAULT NULL")
    }
}

/**
 * v10 → v11：训练会话增加估算消耗列。
 *
 * `estimatedCalories REAL DEFAULT NULL`（kcal，MET 法实时估算）。
 * 可空列 + DEFAULT NULL：老数据没有消耗概念，置空即可，无需回填。
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_session ADD COLUMN estimatedCalories REAL DEFAULT NULL")
    }
}

/**
 * v11 → v12：动作能耗维度字段（fit-carculate.md / kcal-develp.md）。
 *
 * exercise_definition 新增多列，全部带默认值：老数据按默认值参与估算
 * （MET 3.5 / 非自重 / 效率 1.0 / 复合 / 标准节奏），种子数据升级后会回填真实标定值。
 * workout_session 记录本次估算所用层级，总结页展示精度提示。
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN metValue REAL NOT NULL DEFAULT 3.5")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN isBodyweight INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN selfWeightCoefficient REAL NOT NULL DEFAULT 0.5")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN equipmentEfficiency REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN isCompound INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN tempoFactor REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN intensityZone TEXT NOT NULL DEFAULT 'HYPERTROPHY'")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN movementPattern TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN metabolicType TEXT NOT NULL DEFAULT 'MIXED'")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN averageRepDurationSeconds INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN primaryMuscleVolume TEXT NOT NULL DEFAULT 'LARGE'")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN isBeginnerFriendly INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE exercise_definition ADD COLUMN isWarmupSuitable INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_session ADD COLUMN calorieEstimationLevel TEXT DEFAULT NULL")
    }
}

/**
 * v12 → v13：分组计算方案的实时跳字基准。
 * `calorieElapsedBaseSeconds`：累计消耗对应的有效训练秒数，
 * UI 实时消耗 = 累计值 + (当前有效秒数 − 基准) 的 MET 估算。
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_session ADD COLUMN calorieElapsedBaseSeconds INTEGER DEFAULT NULL")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    // 迁移定义必须在 ALL_MIGRATIONS 之前（顶层属性按文件顺序初始化）
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
)
