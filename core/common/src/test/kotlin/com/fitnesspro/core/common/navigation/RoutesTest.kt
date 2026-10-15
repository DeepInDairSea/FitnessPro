package com.fitnesspro.core.common.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D17：导航 Route 对象的参数解析单测。
 * 项目采用类型安全导航（kotlinx.serialization + Navigation Compose），路由对象自身携带参数，
 * ViewModel 端经 SavedStateHandle 按 key 取参（如 "sessionId"）。这里验证 data class 的字段取值与序列化往返。
 */
class RoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun workoutRoute_holdsSessionId() {
        assertEquals(123L, WorkoutRoute(sessionId = 123L).sessionId)
    }

    @Test
    fun planDetailRoute_roundTripsViaJson() {
        val original = PlanDetailRoute(planId = 7L)
        val decoded = json.decodeFromString<PlanDetailRoute>(json.encodeToString(original))
        assertEquals(7L, decoded.planId)
    }

    @Test
    fun exercisePickerRoute_defaultsAreNull() {
        val route = ExercisePickerRoute()
        assertEquals(null, route.targetPlanId)
        assertEquals(null, route.replaceWorkoutExerciseId)
    }

    @Test
    fun sessionDetailRoute_holdsSessionId() {
        assertEquals(55L, SessionDetailRoute(sessionId = 55L).sessionId)
    }
}
