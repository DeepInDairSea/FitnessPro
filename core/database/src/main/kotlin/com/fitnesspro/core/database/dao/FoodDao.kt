package com.fitnesspro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitnesspro.core.database.entity.DietEntryEntity
import com.fitnesspro.core.database.entity.FoodDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    /** 食物库（含自定义，按名称排序）；query 为空时返回全部 */
    @Query(
        """
        SELECT * FROM food_definition
        WHERE deletedAt IS NULL
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY isCustom ASC, name ASC
        """,
    )
    fun observeFoods(query: String): Flow<List<FoodDefinitionEntity>>

    @Query("SELECT * FROM food_definition WHERE id = :id")
    suspend fun getFood(id: Long): FoodDefinitionEntity?

    /** 一次性查询（种子导入判重用） */
    @Query(
        """
        SELECT * FROM food_definition
        WHERE deletedAt IS NULL
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY name ASC
        """,
    )
    suspend fun getFoodsOnce(query: String): List<FoodDefinitionEntity>

    @Query("SELECT COUNT(*) FROM food_definition")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodDefinitionEntity): Long

    @Query("UPDATE food_definition SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    // ---- 每日饮食记录 ----

    @Query(
        """
        SELECT * FROM diet_entry WHERE epochDay = :epochDay
        ORDER BY createdAt DESC
        """,
    )
    fun observeDay(epochDay: Long): Flow<List<DietEntryEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(protein), 0) AS protein,
            COALESCE(SUM(fat), 0) AS fat,
            COALESCE(SUM(carb), 0) AS carb,
            COALESCE(SUM(kcal), 0) AS kcal
        FROM diet_entry WHERE epochDay = :epochDay
        """,
    )
    fun observeDayTotals(epochDay: Long): Flow<DietTotalsRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DietEntryEntity): Long

    @Query("DELETE FROM diet_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)
}

/** SUM 聚合的返回行 */
data class DietTotalsRow(
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val kcal: Double,
)
