package com.fitnesspro.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.entity.ExerciseDefinitionEntity
import com.fitnesspro.core.model.Equipment
import com.fitnesspro.core.model.ExerciseType
import com.fitnesspro.core.model.MuscleGroup
import com.fitnesspro.core.model.PlannedSet
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * 计划保存链路的真实落库验证。
 *
 * ## 为什么需要这个测试
 *
 * `PlanRepository` 的写入方法全部返回 [Outcome]，而 UI 层此前**丢弃了返回值**：
 * `addExerciseToPlan(...)` / `savePlanDetail(...)` 的失败会被 `runCatchingOutcomeSuspend`
 * 收敛成 `Outcome.Failure` 然后被直接忽略，界面照样显示"已保存"。
 * 结果是"点了保存但没存上，且没有任何提示"。
 *
 * 这类缺陷用模拟器手点极难定界（看不到异常），必须让数据层在真实 SQLite 上跑一遍。
 * Robolectric + 内存 Room 提供了真实的表结构、唯一索引与外键约束，
 * 因此 `(planId, orderIndex)` 唯一索引冲突、外键失败这类问题都会被这里抓住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanSaveFlowTest {

    private lateinit var database: FitnessDatabase
    private lateinit var planRepository: PlanRepositoryImpl
    private val time = FakeTimeProvider()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java)
            // 测试本身就在主线程（Robolectric 的测试线程即主线程），必须放行
            .allowMainThreadQueries()
            .build()
        planRepository = PlanRepositoryImpl(database, database.planDao(), time)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------
    // 主链路
    // ------------------------------------------------------------------

    @Test
    fun `新建计划_添加动作_保存_应完整落库`() = runTest {
        val definitionId = insertDefinition(name = "杠铃卧推", defaultSets = 4, defaultReps = "8-12")

        val planId = createPlanOrFail(name = "推日")
        addExerciseOrFail(planId, definitionId, sets = 4, reps = "8-12")

        val loaded = planRepository.getPlanDetail(planId)
        assertNotNull("刚保存的计划应能读回", loaded)
        assertEquals(1, loaded!!.exercises.size)
        assertEquals("动作的组级处方应被创建", 4, loaded.exercises.first().sets.size)

        // 与 UI 自动保存完全相同的调用
        val saved = planRepository.savePlanDetail(
            loaded.copy(
                name = "推日 A",
                exercises = loaded.exercises.map { item ->
                    item.copy(
                        targetSets = 5,
                        sets = (0 until 5).map { index ->
                            PlannedSet(setIndex = index, targetReps = "8-12")
                        },
                    )
                },
            ),
        )

        assertTrue("保存计划失败：${saved.failureDetail()}", saved.isSuccess)

        val reloaded = planRepository.getPlanDetail(planId)!!
        assertEquals("推日 A", reloaded.name)
        assertEquals(5, reloaded.exercises.first().sets.size)
    }

    @Test
    fun `多个动作连续添加_序号不应冲突`() = runTest {
        val planId = createPlanOrFail(name = "腿日")
        val ids = listOf("深蹲", "腿举", "腿弯举").map { insertDefinition(name = it) }

        ids.forEach { addExerciseOrFail(planId, it, sets = 3, reps = "10") }

        val loaded = planRepository.getPlanDetail(planId)!!
        assertEquals(3, loaded.exercises.size)
        // (planId, orderIndex) 上有唯一索引：序号必须严格互不相同
        assertEquals(
            "动作序号出现重复，唯一索引会被破坏",
            loaded.exercises.size,
            loaded.exercises.map { it.orderIndex }.distinct().size,
        )
    }

    @Test
    fun `重排后保存_应与数据库中的序号保持一致`() = runTest {
        val planId = createPlanOrFail(name = "拉力日")
        val ids = listOf("引体向上", "划船", "硬拉").map { insertDefinition(name = it) }
        ids.forEach { addExerciseOrFail(planId, it, sets = 3, reps = "10") }

        var loaded = planRepository.getPlanDetail(planId)!!
        val reversedIds = loaded.exercises.map { it.id }.reversed()

        val reordered = planRepository.reorderExercises(planId, reversedIds)
        assertTrue("重排失败：${reordered.failureDetail()}", reordered.isSuccess)

        loaded = planRepository.getPlanDetail(planId)!!
        assertEquals(reversedIds, loaded.exercises.map { it.id })

        // 重排之后紧接着一次自动保存：这一组合最容易撞唯一索引
        val saved = planRepository.savePlanDetail(loaded.copy(name = "拉力日 B"))
        assertTrue("重排后保存失败：${saved.failureDetail()}", saved.isSuccess)
        assertEquals("拉力日 B", planRepository.getPlanDetail(planId)!!.name)
    }

    @Test
    fun `动作全部移除后保存_应只剩空计划`() = runTest {
        val planId = createPlanOrFail(name = "临时计划")
        addExerciseOrFail(planId, insertDefinition(name = "波比跳"), sets = 3, reps = "15")

        val loaded = planRepository.getPlanDetail(planId)!!
        val saved = planRepository.savePlanDetail(loaded.copy(exercises = emptyList()))
        assertTrue("移除动作后保存失败：${saved.failureDetail()}", saved.isSuccess)

        assertEquals(0, planRepository.getPlanDetail(planId)!!.exercises.size)
    }

    @Test
    fun `按 UI 允许的极端处方保存_不应抛异常`() = runTest {
        val planId = createPlanOrFail(name = "极端处方")
        addExerciseOrFail(
            planId = planId,
            definitionId = insertDefinition(name = "平板支撑", defaultSets = 3, defaultReps = null),
            sets = 3,
            reps = null,
        )

        val loaded = planRepository.getPlanDetail(planId)!!
        // AMRAP 与空次数是处方里真实存在的取值：PlanDurationCalculator 必须能处理
        val saved = planRepository.savePlanDetail(
            loaded.copy(
                exercises = loaded.exercises.map { item ->
                    item.copy(
                        targetSets = 1,
                        sets = listOf(PlannedSet(setIndex = 0, targetReps = "AMRAP")),
                    )
                },
            ),
        )
        assertTrue("AMRAP 处方保存失败：${saved.failureDetail()}", saved.isSuccess)
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private suspend fun createPlanOrFail(name: String): Long {
        val outcome = planRepository.createPlan(
            name = name,
            description = null,
            scheduledDays = emptySet(),
            defaultRestSeconds = 120,
            folderId = null,
        )
        assertTrue("创建计划失败：${outcome.failureDetail()}", outcome.isSuccess)
        return outcome.getOrNull()!!
    }

    private suspend fun addExerciseOrFail(
        planId: Long,
        definitionId: Long,
        sets: Int = 3,
        reps: String? = "10",
    ) {
        val outcome = planRepository.addExerciseToPlan(
            planId = planId,
            exerciseDefinitionId = definitionId,
            exerciseName = "测试动作",
            exerciseType = ExerciseType.WEIGHT_REPS,
            isUnilateral = false,
            defaultSets = sets,
            defaultReps = reps,
            defaultRestSeconds = 90,
        )
        assertTrue("添加动作失败：${outcome.failureDetail()}", outcome.isSuccess)
    }

    private suspend fun insertDefinition(
        name: String,
        defaultSets: Int = 3,
        defaultReps: String? = "10",
    ): Long = database.exerciseDao().insert(
        ExerciseDefinitionEntity(
            name = name,
            type = ExerciseType.WEIGHT_REPS.name,
            primaryMuscle = MuscleGroup.CHEST.name,
            equipment = Equipment.BARBELL.name,
            defaultSets = defaultSets,
            defaultReps = defaultReps,
            createdAt = time.nowEpochMs(),
            updatedAt = time.nowEpochMs(),
        )
    )

    /** 失败时把被吞掉的原始异常打出来 —— 否则只能看到"断言失败"，看不到根因 */
    private fun Outcome<*>.failureDetail(): String {
        val error = errorOrNull() ?: return "（无错误）"
        val cause = (error as? DomainError.Unknown)?.cause
        return buildString {
            append(error.code)
            if (cause != null) {
                append(": ")
                append(cause::class.java.simpleName)
                append(": ")
                append(cause.message)
            }
        }
    }

    private class FakeTimeProvider(private val now: Long = 1_700_000_000_000L) : TimeProvider {
        override fun nowEpochMs(): Long = now
        override fun elapsedRealtimeMs(): Long = now
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }
}
