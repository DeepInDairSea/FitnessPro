package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetType
import com.fitnesspro.core.model.UnitConversion
import com.fitnesspro.core.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T7 单位换算 / T12 杠片 / T13 排序索引 / T14 中文 FTS，以及热身与超负荷建议 */
class MiscCalculatorsTest {

    // ---------------------------------------------------------------- T7

    @Test
    fun `weight unit round trip keeps precision`() {
        val original = 60.0
        val lb = UnitConversion.kgToLb(original)
        val back = UnitConversion.lbToKg(lb)
        assertTrue("往返误差应小于 0.01kg", kotlin.math.abs(back - original) < 0.01)
    }

    @Test
    fun `toKg respects unit`() {
        assertEquals(100.0, UnitConversion.toKg(100.0, WeightUnit.KG), 0.001)
        // 100lb = 45.36kg
        assertEquals(45.36, UnitConversion.toKg(100.0, WeightUnit.LB), 0.01)
    }

    @Test
    fun `default step differs by unit`() {
        assertEquals(2.5, UnitConversion.defaultWeightStep(WeightUnit.KG), 0.001)
        assertEquals(5.0, UnitConversion.defaultWeightStep(WeightUnit.LB), 0.001)
    }

    @Test
    fun `distance conversion`() {
        assertEquals(5.0, UnitConversion.metersToDisplay(5000.0, com.fitnesspro.core.model.DistanceUnit.KM), 0.01)
        // 5000m = 3.11mi
        assertEquals(3.11, UnitConversion.metersToDisplay(5000.0, com.fitnesspro.core.model.DistanceUnit.MI), 0.01)
    }

    // ---------------------------------------------------------------- T12

    @Test
    fun `plate calculator exact solution`() {
        val calc = PlateCalculator(barWeightKg = 20.0)
        val solution = calc.solve(100.0)!!
        // (100 - 20) / 2 = 40 → 25 + 15
        assertEquals(listOf(25.0, 15.0), solution.perSide)
        assertEquals(100.0, solution.achievedTotalKg, 0.001)
        assertTrue(solution.isExact)
    }

    @Test
    fun `plate calculator reports shortfall`() {
        val calc = PlateCalculator(barWeightKg = 20.0)
        val solution = calc.solve(101.0)!!
        // 只能凑到 100，差 -1
        assertEquals(-1.0, solution.deltaKg, 0.001)
        assertTrue(!solution.isExact)
    }

    @Test
    fun `plate calculator returns null below bar weight`() {
        val calc = PlateCalculator(barWeightKg = 20.0)
        assertNull(calc.solve(15.0))
        assertNull(calc.solve(20.0 - 0.1))
    }

    @Test
    fun `plate calculator uses largest plates first`() {
        val calc = PlateCalculator(barWeightKg = 20.0)
        val solution = calc.solve(20.0 + 2 * (25.0 + 25.0 + 10.0))!!
        assertEquals(listOf(25.0, 25.0, 10.0), solution.perSide)
    }

    // ---------------------------------------------------------------- T13

    @Test
    fun `order index between first and last`() {
        assertEquals(0.5, OrderIndexCalculator.between(0.0, 1.0), 1e-9)
        assertEquals(1.0, OrderIndexCalculator.between(null, null), 1e-9)
        assertEquals(0.0, OrderIndexCalculator.between(null, 1.0), 1e-9)
        assertEquals(2.0, OrderIndexCalculator.between(1.0, null), 1e-9)
    }

    @Test
    fun `repeated mid insertion eventually requires reindex`() {
        var low = 0.0
        var high = 1.0
        var iterationsBeforeReindex = 0
        try {
            // 反复往同一个缝隙里插入，Double 精度终将耗尽
            repeat(200) {
                val mid = OrderIndexCalculator.between(low, high)
                high = mid
                iterationsBeforeReindex++
            }
        } catch (e: OrderIndexCalculator.NeedReindexException) {
            // 预期路径
        }
        assertTrue("应在 200 次以内触发 reindex，实际 $iterationsBeforeReindex", iterationsBeforeReindex < 200)
    }

    @Test
    fun `reindex produces strictly increasing values`() {
        val values = OrderIndexCalculator.reindexed(5)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), values)
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
    }

    // ---------------------------------------------------------------- T14

    @Test
    fun `fts query for chinese uses per character prefix`() {
        val query = FtsQueryBuilder.build("卧推")!!
        assertEquals("卧* 推*", query)
    }

    @Test
    fun `fts query for english uses prefix`() {
        assertEquals("bench*", FtsQueryBuilder.build("bench")!!)
        assertEquals("bench* AND press*", FtsQueryBuilder.build("bench press")!!)
    }

    @Test
    fun `fts query strips special characters to avoid syntax errors`() {
        assertEquals("卧* 推*", FtsQueryBuilder.build("  \"卧推\"  ")!!)
        assertNull(FtsQueryBuilder.build("   "))
        assertNull(FtsQueryBuilder.build("\"*()^:"))
    }

    @Test
    fun `chinese input needs like fallback`() {
        assertTrue(FtsQueryBuilder.needsLikeFallback("卧推"))
        assertTrue(!FtsQueryBuilder.needsLikeFallback("bench"))
        assertTrue(!FtsQueryBuilder.needsLikeFallback("卧"))
    }

    // ---------------------------------------------------------------- 热身组

    @Test
    fun `warmup generator scales with working weight`() {
        val steps = TrainingAdvisor.generateWarmupSets(workingWeightKg = 100.0, stepKg = 2.5)
        assertEquals(3, steps.size)
        assertEquals(40.0, steps[0].weightKg, 0.001)
        assertEquals(60.0, steps[1].weightKg, 0.001)
        assertEquals(80.0, steps[2].weightKg, 0.001)
        assertEquals(8, steps[0].reps)
    }

    @Test
    fun `warmup generator returns empty for bar weight only`() {
        assertTrue(TrainingAdvisor.generateWarmupSets(workingWeightKg = 20.0).isEmpty())
        assertTrue(TrainingAdvisor.generateWarmupSets(workingWeightKg = 15.0).isEmpty())
    }

    @Test
    fun `warmup generator never goes below bar weight`() {
        val steps = TrainingAdvisor.generateWarmupSets(workingWeightKg = 22.5, stepKg = 2.5)
        assertTrue(steps.all { it.weightKg >= 20.0 })
    }

    // ---------------------------------------------------------------- 渐进超负荷

    private fun completedSet(reps: Int, type: SetType = SetType.NORMAL) = SetRecord(
        exerciseType = ExerciseType.WEIGHT_REPS,
        setType = type,
        weightKg = 60.0,
        reps = reps,
    )

    @Test
    fun `suggest increase when all sets reach the top of range`() {
        val suggestion = TrainingAdvisor.suggestNextLoad(
            lastSessionSets = listOf(completedSet(12), completedSet(12), completedSet(12)),
            repRange = RepsRange.parse("8-12"),
            currentWeightKg = 60.0,
            weightUnit = WeightUnit.KG,
        )
        assertEquals(62.5, suggestion.weightKg, 0.001)
        assertEquals(TrainingAdvisor.SuggestionReason.INCREASE_WEIGHT, suggestion.reason)
    }

    @Test
    fun `suggest keep weight when some sets below the low bound`() {
        val suggestion = TrainingAdvisor.suggestNextLoad(
            lastSessionSets = listOf(completedSet(12), completedSet(9), completedSet(7)),
            repRange = RepsRange.parse("8-12"),
            currentWeightKg = 60.0,
        )
        assertEquals(60.0, suggestion.weightKg, 0.001)
        assertEquals(TrainingAdvisor.SuggestionReason.KEEP_WEIGHT, suggestion.reason)
    }

    @Test
    fun `suggest deload after repeated misses`() {
        val suggestion = TrainingAdvisor.suggestNextLoad(
            lastSessionSets = listOf(completedSet(5), completedSet(6)),
            repRange = RepsRange.parse("8-12"),
            currentWeightKg = 60.0,
            consecutiveMissedSessions = 3,
        )
        // 60 × 0.9 = 54 → 取 2.5 步进 = 55
        assertEquals(55.0, suggestion.weightKg, 0.001)
        assertEquals(TrainingAdvisor.SuggestionReason.DELOAD_HINT, suggestion.reason)
    }

    @Test
    fun `suggest nothing without history`() {
        val suggestion = TrainingAdvisor.suggestNextLoad(
            lastSessionSets = emptyList(),
            repRange = RepsRange.parse("8-12"),
            currentWeightKg = 60.0,
        )
        assertEquals(TrainingAdvisor.SuggestionReason.NO_DATA, suggestion.reason)
        assertEquals(60.0, suggestion.weightKg, 0.001)
    }

    @Test
    fun `warmup sets do not affect suggestion`() {
        val suggestion = TrainingAdvisor.suggestNextLoad(
            lastSessionSets = listOf(
                completedSet(12),
                completedSet(12),
                completedSet(12),
                completedSet(3, SetType.WARMUP),
            ),
            repRange = RepsRange.parse("8-12"),
            currentWeightKg = 60.0,
        )
        assertEquals(TrainingAdvisor.SuggestionReason.INCREASE_WEIGHT, suggestion.reason)
        assertNotNull(suggestion.weightKg)
    }
}
