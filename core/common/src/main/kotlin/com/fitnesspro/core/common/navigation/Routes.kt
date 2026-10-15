package com.fitnesspro.core.common.navigation

import kotlinx.serialization.Serializable

/**
 * 全局导航路由（类型安全）。
 *
 * 放在 core:common 而不是各 feature 内，是为了让 feature 之间可以互相跳转，
 * 而不需要产生 feature → feature 的编译依赖（DEV 文档 §2.2 规则 R1）。
 * 每个 feature 只负责提供 `NavGraphBuilder.xxxScreen()` 扩展函数。
 */

@Serializable
data object HomeRoute

@Serializable
data object PlanListRoute

@Serializable
data class PlanDetailRoute(val planId: Long)

@Serializable
data class PlanEditRoute(val planId: Long? = null)

@Serializable
data object RecycleBinRoute

@Serializable
data object ExerciseLibraryRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: Long)

/**
 * 自定义动作的新建/编辑。
 * @param exerciseId null/-1 表示新建；>0 表示编辑既有动作
 */
@Serializable
data class ExerciseEditRoute(val exerciseId: Long? = null)

/**
 * 动作选择器。
 * @param targetPlanId 计划编辑页发起的添加动作；为 null 表示从其他入口打开
 * @param replaceWorkoutExerciseId 训练中"替换动作"，传入被替换的 workoutExerciseId
 */
@Serializable
data class ExercisePickerRoute(
    val targetPlanId: Long? = null,
    val replaceWorkoutExerciseId: Long? = null,
)

@Serializable
data class WorkoutRoute(val sessionId: Long)

@Serializable
data class WorkoutSummaryRoute(val sessionId: Long)

/**
 * 全屏媒体播放。fileName 为空表示播放外链。
 * @param kind MediaType 名称：VIDEO_LOCAL 走 Media3 播放器，GIF/IMAGE 走图片查看器
 */
@Serializable
data class MediaPlayerRoute(
    val fileName: String? = null,
    val url: String? = null,
    val kind: String = "VIDEO_LOCAL",
    val title: String = "",
)

@Serializable
data object HistoryRoute

@Serializable
data class SessionDetailRoute(val sessionId: Long)

@Serializable
data object StatsRoute

@Serializable
data object BodyRoute

@Serializable
data object SettingsRoute

/**
 * 跨模块的导航结果 key。
 *
 * 放在 core:common 而不是各 feature 内：动作选择器要给"计划编辑"与"训练中替换动作"
 * 两个不同来源回传结果，把 key 集中在一处可以避免两边各写一份字符串导致对不上。
 */
object NavResults {
    /** Long：被选中的动作定义 id */
    const val PICKED_EXERCISE_ID = "nav_result_picked_exercise_id"

    /** Long：新创建的训练会话 id */
    const val STARTED_SESSION_ID = "nav_result_started_session_id"

    /** Long：需要从编辑页带出的计划 id（新建计划成功后） */
    const val CREATED_PLAN_ID = "nav_result_created_plan_id"
}
