package com.fitnesspro.core.domain.calculator

import com.fitnesspro.core.model.ExerciseSnapshot
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.PlanExerciseItem
import com.fitnesspro.core.model.PlannedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计划时长估算测试，对应 T8（超级组不计重复休息） */
class PlanDurationCalculatorTest {

    private fun item(
        id: Long,
        orderIndex: Double,
        sets: Int,
        reps: String,
        supersetTag: Int? = null,
        restOverride: Int? = null,
    ) = PlanExerciseItem(
        id = id,
        exercise = ExerciseSnapshot(
            definitionId = id,
            name = "动作$id",
            type = ExerciseType.WEIGHT_REPS,
        ),
        orderIndex = orderIndex,
        supersetTag = supersetTag,
        targetSets = sets,
        sets = (0 until sets).map { index ->
            PlannedSet(setIndex = index, targetReps = reps)
        },
        restSecondsOverride = restOverride,
    )

    @Test
    fun `single exercise duration`() {
        // 3 组 × 10 次 × 3 秒 = 90s 做功；组间休息 2 × 60 = 120s；切换开销 5s → 215s
        val range = PlanDurationCalculator.estimate(
            exercises = listOf(item(1, 0.0, sets = 3, reps = "10")),
            planDefaultRestSeconds = 60,
        )
        assertEquals(193, range.lowSeconds) // 215 × 0.9 = 193.5 → 193
        assertEquals(247, range.highSeconds) // 215 × 1.15 = 247.25 → 247
    }

    // T8: 超级组内部两动作之间不应重复计休息
    @Test
    fun `superset does not double count rest between its members`() {
        val a = item(1, 0.0, sets = 3, reps = "10", supersetTag = 1)
        val b = item(2, 1.0, sets = 3, reps = "10", supersetTag = 1)

        val superset = PlanDurationCalculator.estimate(listOf(a, b), planDefaultRestSeconds = 60)
        // 做功 90 + 90 = 180；超级组尾部休息 (3-1)×60 = 120；开销 2×5 = 10 → 310
        assertEquals(279, superset.lowSeconds)
        assertEquals(356, superset.highSeconds)

        // 同样两个动作若不编组，休息要算两次：每个动作 2×60 = 120 → 总 180+240+10 = 430
        val separate = PlanDurationCalculator.estimate(
            listOf(a.copy(supersetTag = null), b.copy(supersetTag = null)),
            planDefaultRestSeconds = 60,
        )
        assertTrue(
            "编组后总时长必须显著短于不编组",
            superset.highSeconds < separate.highSeconds,
        )
        assertEquals(387, separate.lowSeconds) // 430 × 0.9
    }

    @Test
    fun `exercise level rest override wins over plan default`() {
        val withOverride = PlanDurationCalculator.estimate(
            listOf(item(1, 0.0, sets = 3, reps = "10", restOverride = 120)),
            planDefaultRestSeconds = 60,
        )
        val withoutOverride = PlanDurationCalculator.estimate(
            listOf(item(1, 0.0, sets = 3, reps = "10")),
            planDefaultRestSeconds = 60,
        )
        assertTrue(withOverride.highSeconds > withoutOverride.highSeconds)
    }

    @Test
    fun `reps range uses midpoint`() {
        val range = PlanDurationCalculator.estimate(
            listOf(item(1, 0.0, sets = 2, reps = "8-12")),
            planDefaultRestSeconds = 60,
        )
        // 做功 2 × 10 × 3 = 60；休息 1 × 60 = 60；开销 5 → 125
        assertEquals(112, range.lowSeconds) // 125 × 0.9 = 112.5 → 112
    }

    @Test
    fun `empty plan yields zero`() {
        val range = PlanDurationCalculator.estimate(emptyList(), planDefaultRestSeconds = null)
        assertEquals(0, range.lowSeconds)
        assertEquals(0, range.highSeconds)
    }

    @Test
    fun `global default rest used when plan default is null`() {
        val range = PlanDurationCalculator.estimate(
            listOf(item(1, 0.0, sets = 2, reps = "10")),
            planDefaultRestSeconds = null,
            globalDefaultRestSeconds = 90,
        )
        // 做功 60 + 休息 90 + 开销 5 = 155
        assertEquals(139, range.lowSeconds) // 155 × 0.9 = 139.5 → 139
    }
}
