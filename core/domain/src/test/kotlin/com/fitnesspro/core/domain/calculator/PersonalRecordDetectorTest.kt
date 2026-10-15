package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.PrType
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PR 判定测试，对应 T10 / T11 */
class PersonalRecordDetectorTest {

    private val detector = PersonalRecordDetector()

    private fun workingSet(weight: Double?, reps: Int?, type: SetType = SetType.NORMAL) = SetRecord(
        exerciseType = ExerciseType.WEIGHT_REPS,
        setType = type,
        status = SetStatus.COMPLETED,
        weightKg = weight,
        reps = reps,
    )

    // T10: 首次记录 —— 没有历史时应命中，且 previousValue 为 null
    @Test
    fun `first ever set hits all applicable records`() {
        val hits = detector.detect(workingSet(60.0, 8), bodyWeightKg = 75.0, existing = emptyMap())
        val types = hits.filterIsInstance<PersonalRecordDetector.Result.Hit>().map { it.prType }
        assertTrue(types.contains(PrType.MAX_WEIGHT))
        assertTrue(types.contains(PrType.MAX_VOLUME_SET))
        assertTrue(types.contains(PrType.MAX_E1RM))
        val weightHit = hits.filterIsInstance<PersonalRecordDetector.Result.Hit>()
            .first { it.prType == PrType.MAX_WEIGHT }
        assertEquals(null, weightHit.previousValue)
    }

    // T11: 热身组不参与判定
    @Test
    fun `warmup set never produces a record`() {
        val hits = detector.detect(
            workingSet(100.0, 10, SetType.WARMUP),
            bodyWeightKg = 75.0,
            existing = emptyMap(),
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `drop and failure sets are excluded by default`() {
        assertTrue(detector.detect(workingSet(200.0, 5, SetType.DROP), 75.0, emptyMap()).isEmpty())
        assertTrue(detector.detect(workingSet(200.0, 5, SetType.FAILURE), 75.0, emptyMap()).isEmpty())
        // 显式开启后应参与
        assertFalse(
            detector.detect(
                workingSet(200.0, 5, SetType.DROP), 75.0, emptyMap(),
                includeDropAndFailure = true,
            ).isEmpty()
        )
    }

    @Test
    fun `skipped set never produces a record`() {
        val set = SetRecord(
            exerciseType = ExerciseType.WEIGHT_REPS,
            status = SetStatus.SKIPPED,
            weightKg = 200.0,
            reps = 5,
        )
        assertTrue(detector.detect(set, 75.0, emptyMap()).isEmpty())
    }

    @Test
    fun `no record when below existing`() {
        val existing = mapOf(
            PrType.MAX_WEIGHT to 100.0,
            PrType.MAX_E1RM to 120.0,
            PrType.MAX_VOLUME_SET to 900.0,
        )
        val hits = detector.detect(workingSet(80.0, 8), 75.0, existing)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `beats previous weight but not estimated one rep max`() {
        // 上次 100kg × 1（e1RM 100）；本次 90kg × 12 → e1RM 126，两者都破
        val existing = mapOf(PrType.MAX_WEIGHT to 100.0, PrType.MAX_E1RM to 100.0)
        val hits = detector.detect(workingSet(90.0, 12), 75.0, existing)
            .filterIsInstance<PersonalRecordDetector.Result.Hit>()
            .map { it.prType }
        assertFalse(hits.contains(PrType.MAX_WEIGHT))
        assertTrue(hits.contains(PrType.MAX_E1RM))
    }

    @Test
    fun `high reps produce no one rep max record`() {
        val hits = detector.detect(workingSet(40.0, 20), 75.0, emptyMap())
            .filterIsInstance<PersonalRecordDetector.Result.Hit>()
            .map { it.prType }
        assertFalse(hits.contains(PrType.MAX_E1RM))
        // 但重量与容量仍然可以破纪录
        assertTrue(hits.contains(PrType.MAX_WEIGHT))
    }

    @Test
    fun `zero weight produces no weight record`() {
        val hits = detector.detect(workingSet(0.0, 10), 75.0, emptyMap())
            .filterIsInstance<PersonalRecordDetector.Result.Hit>()
            .map { it.prType }
        assertFalse(hits.contains(PrType.MAX_WEIGHT))
    }

    @Test
    fun `primary hit prefers max weight`() {
        val hits = detector.detect(workingSet(60.0, 8), 75.0, emptyMap())
        val primary = detector.primaryHit(hits)!!
        assertEquals(PrType.MAX_WEIGHT, primary.prType)
    }
}
