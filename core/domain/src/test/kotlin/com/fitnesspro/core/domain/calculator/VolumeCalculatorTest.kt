package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.SetRecord
import com.fitnesspro.core.model.SetStatus
import com.fitnesspro.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 容量计算单元测试。
 * 对应 DEV 文档 §14.4 的 T1-T4。这些断言是统计与 PR 的地基，必须锁死。
 */
class VolumeCalculatorTest {

    // T1: WEIGHT_REPS —— 60kg × 8 = 480
    @Test
    fun `weight reps volume`() {
        val set = SetRecord(
            exerciseType = ExerciseType.WEIGHT_REPS,
            weightKg = 60.0,
            reps = 8,
        )
        assertEquals(480.0, VolumeCalculator.setVolume(set, bodyWeightKg = 75.0), 0.001)
    }

    // T2: REPS_ONLY（引体向上）—— (体重 75 + 附加 10) × 8 = 680
    @Test
    fun `bodyweight exercise includes body weight`() {
        val set = SetRecord(
            exerciseType = ExerciseType.REPS_ONLY,
            weightKg = 10.0,
            reps = 8,
        )
        assertEquals(680.0, VolumeCalculator.setVolume(set, bodyWeightKg = 75.0), 0.001)
    }

    @Test
    fun `bodyweight exercise without added weight`() {
        val set = SetRecord(exerciseType = ExerciseType.REPS_ONLY, weightKg = null, reps = 10)
        assertEquals(750.0, VolumeCalculator.setVolume(set, bodyWeightKg = 75.0), 0.001)
    }

    // T3: 热身组默认不计入容量
    @Test
    fun `warmup set excluded by default`() {
        val set = SetRecord(
            exerciseType = ExerciseType.WEIGHT_REPS,
            setType = SetType.WARMUP,
            weightKg = 40.0,
            reps = 10,
        )
        assertEquals(0.0, VolumeCalculator.setVolume(set, bodyWeightKg = null), 0.001)
        assertEquals(
            400.0,
            VolumeCalculator.setVolume(set, bodyWeightKg = null, includeWarmup = true),
            0.001,
        )
    }

    // T4: TIME 类不参与重量容量
    @Test
    fun `time based exercise contributes no weight volume`() {
        val set = SetRecord(
            exerciseType = ExerciseType.TIME,
            durationSeconds = 120,
        )
        assertEquals(0.0, VolumeCalculator.setVolume(set, bodyWeightKg = 75.0), 0.001)
        assertEquals(120, VolumeCalculator.totalDurationSeconds(listOf(set)))
    }

    @Test
    fun `weight time volume is per minute`() {
        val set = SetRecord(
            exerciseType = ExerciseType.WEIGHT_TIME,
            weightKg = 20.0,
            durationSeconds = 90,
        )
        // 20kg × 1.5min = 30
        assertEquals(30.0, VolumeCalculator.setVolume(set, null), 0.001)
    }

    @Test
    fun `unfinished set contributes nothing`() {
        val set = SetRecord(
            exerciseType = ExerciseType.WEIGHT_REPS,
            status = SetStatus.SKIPPED,
            weightKg = 100.0,
            reps = 10,
        )
        assertEquals(0.0, VolumeCalculator.setVolume(set, null), 0.001)
    }

    @Test
    fun `unilateral set uses both sides reps`() {
        val set = SetRecord(
            exerciseType = ExerciseType.REPS_ONLY,
            leftReps = 8,
            rightReps = 7,
        )
        assertEquals(15, VolumeCalculator.effectiveReps(set))
        assertEquals(1125.0, VolumeCalculator.setVolume(set, 75.0), 0.001)
    }
}
