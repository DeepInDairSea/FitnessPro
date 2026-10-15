package com.fitnesspro.core.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 次数区间解析测试，对应 T9 */
class RepsRangeTest {

    @Test
    fun `single value`() {
        val range = RepsRange.parse("12")
        assertEquals(12, range.low)
        assertEquals(12, range.high)
        assertEquals(12.0, range.midpoint!!, 0.001)
        assertEquals("12", range.display())
    }

    @Test
    fun `range value midpoint`() {
        val range = RepsRange.parse("8-12")
        assertEquals(8, range.low)
        assertEquals(12, range.high)
        assertEquals(10.0, range.midpoint!!, 0.001)
        assertEquals("8-12", range.display())
    }

    @Test
    fun `tilde separator and reversed bounds`() {
        assertEquals(10.0, RepsRange.parse("8~12").midpoint!!, 0.001)
        // 用户手误写成 12-8，应当被规范化
        assertEquals(8, RepsRange.parse("12-8").low)
        assertEquals(12, RepsRange.parse("12-8").high)
    }

    @Test
    fun `amrap has no midpoint`() {
        val range = RepsRange.parse("AMRAP")
        assertTrue(range.isAmrap)
        assertNull(range.midpoint)
        assertEquals("AMRAP", range.display())
    }

    @Test
    fun `chinese amrap alias`() {
        assertTrue(RepsRange.parse("力竭").isAmrap)
    }

    @Test
    fun `invalid input returns empty instead of throwing`() {
        assertTrue(RepsRange.parse("").isEmpty)
        assertTrue(RepsRange.parse(null).isEmpty)
        assertTrue(RepsRange.parse("abc").isEmpty)
        assertTrue(RepsRange.parse("8-abc").isEmpty)
    }

    @Test
    fun `reached top and low`() {
        val range = RepsRange.parse("8-12")
        assertTrue(range.reachedTop(12))
        assertTrue(range.reachedTop(13))
        assertTrue(!range.reachedTop(11))
        assertTrue(range.reachedLow(8))
        assertTrue(!range.reachedLow(7))
    }
}
