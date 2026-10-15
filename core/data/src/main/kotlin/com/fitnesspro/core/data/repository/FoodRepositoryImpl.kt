package com.fitnesspro.core.data.repository

import com.fitnesspro.core.database.dao.FoodDao
import com.fitnesspro.core.database.entity.DietEntryEntity
import com.fitnesspro.core.database.entity.FoodDefinitionEntity
import com.fitnesspro.core.database.seed.FoodSeedImporter
import com.fitnesspro.core.domain.repository.Food
import com.fitnesspro.core.domain.repository.FoodCategory
import com.fitnesspro.core.domain.repository.FoodRepository
import com.fitnesspro.core.domain.repository.DietEntry
import com.fitnesspro.core.domain.repository.DietTotals
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FoodRepositoryImpl @Inject constructor(
    private val foodDao: FoodDao,
    private val seedImporter: FoodSeedImporter,
    private val time: TimeProvider,
) : FoodRepository {

    override fun observeFoods(query: String): Flow<List<Food>> =
        foodDao.observeFoods(query).map { list -> list.map { it.toDomain() } }

    override suspend fun importSeedIfNeeded(): Outcome<Int> =
        runCatchingOutcomeSuspend { seedImporter.importIfNeeded() }

    override suspend fun createCustomFood(
        name: String,
        category: FoodCategory,
        proteinPer50g: Double,
        fatPer50g: Double,
        carbPer50g: Double,
        kcalPer50g: Double,
    ): Outcome<Long> = runCatchingOutcomeSuspend {
        foodDao.insert(
            FoodDefinitionEntity(
                name = name.trim(),
                category = category.name,
                proteinPer50g = proteinPer50g,
                fatPer50g = fatPer50g,
                carbPer50g = carbPer50g,
                kcalPer50g = kcalPer50g,
                isCustom = true,
            ),
        )
    }

    override suspend fun recordDiet(epochDay: Long, foodId: Long, grams: Int): Outcome<Unit> =
        runCatchingOutcomeSuspend {
            val food = foodDao.getFood(foodId) ?: error("食物不存在：$foodId")
            val ratio = grams / 50.0
            foodDao.insertEntry(
                DietEntryEntity(
                    epochDay = epochDay,
                    foodId = foodId,
                    grams = grams,
                    protein = food.proteinPer50g * ratio,
                    fat = food.fatPer50g * ratio,
                    carb = food.carbPer50g * ratio,
                    kcal = food.kcalPer50g * ratio,
                    foodName = food.name,
                    createdAt = time.nowEpochMs(),
                ),
            )
        }

    override fun observeDietDay(epochDay: Long): Flow<List<DietEntry>> =
        foodDao.observeDay(epochDay).map { list -> list.map { it.toDomain() } }

    override fun observeDietTotals(epochDay: Long): Flow<DietTotals> =
        foodDao.observeDayTotals(epochDay).map { row ->
            DietTotals(row.protein, row.fat, row.carb, row.kcal)
        }

    override suspend fun deleteDietEntry(id: Long): Outcome<Unit> =
        runCatchingOutcomeSuspend { foodDao.deleteEntry(id) }
}

private fun FoodDefinitionEntity.toDomain() = Food(
    id = id,
    name = name,
    category = runCatching { FoodCategory.valueOf(category) }.getOrDefault(FoodCategory.MEAT),
    proteinPer50g = proteinPer50g,
    fatPer50g = fatPer50g,
    carbPer50g = carbPer50g,
    kcalPer50g = kcalPer50g,
    isCustom = isCustom,
    scoopGrams = scoopGrams,
    proteinPerScoop = proteinPerScoop,
)

private fun DietEntryEntity.toDomain() = DietEntry(
    id = id,
    epochDay = epochDay,
    foodId = foodId,
    foodName = foodName,
    grams = grams,
    protein = protein,
    fat = fat,
    carb = carb,
    kcal = kcal,
)
