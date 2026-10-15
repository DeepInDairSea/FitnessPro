package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.OneRmFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1RM 估算测试，对应 T5 / T6 */
class OneRmCalculatorTest {

    // T5: Epley 100kg × 5 = 100 × (1 + 5/30) = 116.67
    @Test
    fun `epley formula`() {
        val result = OneRmCalculator.estimate(100.0, 5, OneRmFormula.EPLEY)
        assertEquals(116.67, result!!, 0.01)
    }

    @Test
    fun `brzycki formula`() {
        // 100 × 36 / (37 - 5) = 112.5
        val result = OneRmCalculator.estimate(100.0, 5, OneRmFormula.BRZYCKI)
        assertEquals(112.5, result!!, 0.01)
    }

    @Test
    fun `auto uses brzycki for low reps and epley for high reps`() {
        val low = OneRmCalculator.estimate(100.0, 5, OneRmFormula.AUTO)!!
        val high = OneRmCalculator.estimate(100.0, 10, OneRmFormula.AUTO)!!
        assertEquals(112.5, low, 0.01)
        assertEquals(133.33, high, 0.01)
    }

    // T6: 边界
    @Test
    fun `single rep returns the weight itself`() {
        assertEquals(100.0, OneRmCalculator.estimate(100.0, 1)!!, 0.001)
    }

    @Test
    fun `zero and negative reps return null`() {
        assertNull(OneRmCalculator.estimate(100.0, 0))
        assertNull(OneRmCalculator.estimate(100.0, -3))
    }

    @Test
    fun `reps above reliable limit return null`() {
        assertNull(OneRmCalculator.estimate(100.0, 13))
        assertEquals(12, OneRmCalculator.MAX_RELIABLE_REPS)
    }

    @Test
    fun `zero weight returns null`() {
        assertNull(OneRmCalculator.estimate(0.0, 5))
    }

    @Test
    fun `weight for percent rounds to step`() {
        assertEquals(70.0, OneRmCalculator.weightForPercent(100.0, 0.70, 2.5), 0.001)
        // 100 × 0.72 = 72 → 最近 2.5 的倍数 = 72.5
        assertEquals(72.5, OneRmCalculator.weightForPercent(100.0, 0.72, 2.5), 0.001)
    }
}
