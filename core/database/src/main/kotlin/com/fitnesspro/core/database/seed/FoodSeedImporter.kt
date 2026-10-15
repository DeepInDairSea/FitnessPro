package com.fitnesspro.core.database.seed

import androidx.room.withTransaction
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.database.entity.AppFlagEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 食物种子导入器。幂等模式与 [ExerciseSeedImporter] 一致：
 *  - app_flag 有标记 → 已导入过，跳过
 *  - 库里已有食物 → 只补标记，不重复灌入
 *  - 单事务导入（用户不慍看到半库食物）
 */
@Singleton
class FoodSeedImporter @Inject constructor(
    private val database: FitnessDatabase,
    private val flagDao: AppFlagDao,
) {
    suspend fun importIfNeeded(force: Boolean = false): Int {
        if (!force && flagDao.exists(FLAG_KEY) > 0) return 0
        if (!force && database.foodDao().countAll() > 0) {
            flagDao.put(AppFlagEntity(FLAG_KEY, "skipped_existing"))
            return 0
        }
        return database.withTransaction {
            // 只补种子缺的（按名称判重）：用户可能已自建同名食物，不覆盖
            val existingNames = database.foodDao().getFoodsOnce("").map { it.name }.toSet()
            val missing = FoodSeed.foods.filter { it.name !in existingNames }
            if (missing.isNotEmpty()) {
                database.foodDao().insertAll(missing)
            }
            flagDao.put(AppFlagEntity(FLAG_KEY, "imported_${missing.size}"))
            missing.size
        }
    }

    private companion object {
        const val FLAG_KEY = "food_seed_v1"
    }
}
