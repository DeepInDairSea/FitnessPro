package com.fitnesspro.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.component.softPressedScale
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.fitnesspro.core.mediaui.MediaPlayerScreen
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitnesspro.core.common.navigation.ExercisePickerRoute
import com.fitnesspro.core.common.navigation.PlanDetailRoute
import com.fitnesspro.core.common.navigation.PlanEditRoute
import com.fitnesspro.core.common.navigation.PlanListRoute
import com.fitnesspro.core.common.navigation.WorkoutRoute
import com.fitnesspro.feature.exercises.ExerciseDetailScreen
import com.fitnesspro.feature.exercises.ExerciseEditScreen
import com.fitnesspro.feature.exercises.ExercisePickerScreen
import com.fitnesspro.feature.history.SessionDetailScreen
import com.fitnesspro.feature.home.HomeScreen
import com.fitnesspro.feature.body.BodyScreen
import com.fitnesspro.core.designsystem.R as DesignR
import com.fitnesspro.feature.plans.PlanDetailScreen
import com.fitnesspro.feature.plans.PlanEditScreen
import com.fitnesspro.feature.plans.ScheduleScreen
import com.fitnesspro.feature.settings.BackupScreen
import com.fitnesspro.feature.settings.PrivacyPolicyScreen
import com.fitnesspro.feature.settings.SettingsScreen
import com.fitnesspro.feature.workout.WorkoutScreen
import com.fitnesspro.feature.workout.WorkoutSummaryScreen
import com.fitnesspro.onboarding.OnboardingScreen
import com.fitnesspro.ui.DataScreen
import com.fitnesspro.ui.DataSegment
import com.fitnesspro.ui.LibrarySegment
import com.fitnesspro.feature.body.DietScreen
import com.fitnesspro.ui.TodayScreen
import com.fitnesspro.ui.TrainingLibraryScreen
import com.fitnesspro.ui.TrashType
import com.fitnesspro.ui.UnifiedRecycleBinScreen

/**
 * 应用导航图。
 *
 * 放在 :app 而不是各 feature：feature 之间禁止互相依赖（DEV 文档 §2.2 规则 R1），
 * 因此"谁能跳到谁"只能由装配层决定。feature 只提供无状态的 Composable。
 *
 * 这里使用字符串路由 + 显式参数（而不是 type-safe 路由对象），是为了让
 * ViewModel 能通过 SavedStateHandle 直接读取参数 —— 两条路径的 key 都由
 * core:common 的 Route 定义集中管理，避免"导航写一个名、读取写另一个名"。
 */
@Composable
fun FitnessProNavHost(
    /** 首次启动（未完成引导）时为 true，由 MainActivity 根据设置判定 */
    showOnboarding: Boolean = false,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 底部导航只在顶层目的地显示：
    // 编辑页/训练页/详情页是"有明确退出意图"的深层页面，给它们加底部栏会诱导用户在
    // 没保存的情况下跳走
    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    // 便当盒主题的菜单是透明的，内容要能从它后面穿过：
    // 因此不消费 Scaffold 的底部内边距，改由各页面用 bottomBarInset 自己留白
    val floatingBottomBar = FitnessTheme.layout.bentoGrid

    // 首次启动进引导页。设置加载完成前 showOnboarding=false，不导航；
    // 加载后为 true 才推一次 —— onboardingCompleted 写成 true 后不会再触发。
    // handled 标记防止旋转/重组合时重复入栈
    var onboardingHandled by remember { mutableStateOf(false) }
    LaunchedEffect(showOnboarding) {
        if (showOnboarding && !onboardingHandled) {
            onboardingHandled = true
            navController.navigate(ROUTE_ONBOARDING)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val onSelect: (BottomBarItem) -> Unit = { item ->
                    if (currentRoute != item.route) {
                        navController.navigate(item.navRoute) {
                            // 顶层切换不堆栈：否则连点会累积十几层返回栈
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                if (FitnessTheme.layout.bentoGrid) {
                    // 便当盒主题：漂浮胶囊导航条，选中项是强调色实心胶囊
                    BentoNavigationBar(
                        currentRoute = currentRoute,
                        onSelect = onSelect,
                    )
                } else {
                    NavigationBar {
                        BOTTOM_BAR_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { onSelect(item) },
                                // 图标文案与文字标签一致：TalkBack 读出的是同一个意思
                                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = if (floatingBottomBar) {
                Modifier.padding(top = scaffoldPadding.calculateTopPadding())
            } else {
                Modifier.padding(scaffoldPadding)
            },
        ) {
        // ---------------- 首页 ----------------
        composable(ROUTE_HOME) {
            HomeScreen(
                onOpenActiveWorkout = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        launchSingleTop = true
                    }
                },
                onOpenPlan = { planId ->
                    navController.navigate("$ROUTE_PLAN_DETAIL_PREFIX/$planId")
                },
                onWorkoutStarted = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        launchSingleTop = true
                    }
                },
                onOpenPlans = {
                    // restoreState 必须关：开启时会把上次保存的库页栈（含旧 segment 参数）整树
                    // 恢复，新参数被忽略 —— 点"动作库"却落在"计划"分支就是这个原因
                    navController.navigate("$ROUTE_LIBRARY?segment=plans") {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onOpenHistory = {
                    // 注意：ROUTE_DATA 是带 {segment} 占位符的模板，这里用裸字符串拼接参数
                    navController.navigate("data?segment=${DataSegment.HISTORY.routeValue}") {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onOpenExerciseLibrary = {
                    navController.navigate("$ROUTE_LIBRARY?segment=exercises") {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onOpenSchedule = { navController.navigate(ROUTE_SCHEDULE) },
                onOpenStats = {
                    navController.navigate("data?segment=${DataSegment.STATS.routeValue}") {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }

        // ---------------- 首次启动引导 ----------------
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(onDone = { navController.popBackStack() })
        }

        // ---------------- 今日训练（训练 tab：直接开炼） ----------------
        composable(ROUTE_TODAY) {
            TodayScreen(
                onWorkoutStarted = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        launchSingleTop = true
                    }
                },
                onOpenActiveWorkout = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        launchSingleTop = true
                    }
                },
                onOpenPlan = { planId ->
                    navController.navigate("$ROUTE_PLAN_DETAIL_PREFIX/$planId")
                },
                onOpenLibrary = {
                    // 普通压栈（不 popUpTo）：返回时回到今日页 —— "从哪里来回哪里"
                    // 用裸字符串拼参数（ROUTE_LIBRARY 是含占位符的模板，直接拼接会把占位符带进去）
                    navController.navigate("library?segment=plans") {
                        launchSingleTop = true
                    }
                },
                onOpenSchedule = { navController.navigate(ROUTE_SCHEDULE) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }

        // ---------------- 训练库（计划 + 动作，顶部分段切换） ----------------
        composable(
            route = ROUTE_LIBRARY,
            arguments = listOf(
                navArgument("segment") {
                    type = NavType.StringType
                    defaultValue = LibrarySegment.PLANS.routeValue
                },
            ),
        ) { entry ->
            val segment = when (entry.arguments?.getString("segment")) {
                LibrarySegment.EXERCISES.routeValue -> LibrarySegment.EXERCISES
                else -> LibrarySegment.PLANS
            }
            TrainingLibraryScreen(
                segment = segment,
                onSegmentChange = { seg ->
                    // 同目的地换参数：launchSingleTop 会复用旧 entry 导致参数不更新，
                    // 必须用 inclusive popUpTo 把旧分段 entry 顶掉。
                    // 注意：这里用裸字符串 —— ROUTE_LIBRARY 是带 {segment} 占位符的
                    // 模板，直接拼接会把占位符一起塞进参数值
                    navController.navigate("library?segment=${seg.routeValue}") {
                        popUpTo(ROUTE_LIBRARY) { inclusive = true }
                    }
                },
                onPlanClick = { planId ->
                    navController.navigate("$ROUTE_PLAN_DETAIL_PREFIX/$planId")
                },
                onCreatePlan = {
                    navController.navigate("$ROUTE_PLAN_EDIT_PREFIX/-1")
                },
                onExerciseClick = { id -> navController.navigate("$ROUTE_EXERCISE_DETAIL/$id") },
                onCreateExercise = { navController.navigate("$ROUTE_EXERCISE_EDIT/-1") },
                onWorkoutStarted = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenTrash = { seg ->
                    val type = when (seg) {
                        LibrarySegment.EXERCISES -> TrashType.EXERCISE
                        LibrarySegment.PLANS -> TrashType.PLAN
                    }
                    navController.navigate("$ROUTE_RECYCLE_BIN/${type.routeValue}")
                },
                // 从哪里来回哪里（今日页/首页均可进入）
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------- 数据（统计 + 历史，顶部分段切换，同训练库） ----------------
        composable(
            route = ROUTE_DATA,
            arguments = listOf(
                navArgument("segment") {
                    type = NavType.StringType
                    defaultValue = DataSegment.STATS.routeValue
                },
            ),
        ) { entry ->
            val segment = when (entry.arguments?.getString("segment")) {
                DataSegment.HISTORY.routeValue -> DataSegment.HISTORY
                else -> DataSegment.STATS
            }
            DataScreen(
                segment = segment,
                onSegmentChange = { seg ->
                    // 同目的地换参数：与训练库分段切换同一套写法（见 library 分支注释）
                    navController.navigate("data?segment=${seg.routeValue}") {
                        popUpTo(ROUTE_DATA) { inclusive = true }
                    }
                },
                onSessionClick = { id -> navController.navigate("$ROUTE_SESSION_DETAIL/$id") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenTrash = { navController.navigate("$ROUTE_RECYCLE_BIN/${TrashType.HISTORY.routeValue}") },
            )
        }

        composable(
            route = "$ROUTE_EXERCISE_DETAIL/{exerciseId}",
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType }),
        ) {
            ExerciseDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlayMedia = { media ->
                    mediaPlayerRoute(media)?.let { navController.navigate(it) }
                },
                // 演示文件丢失 → 跳编辑页重新绑定（编辑页就是绑定入口）
                onRelinkMedia = { exerciseId ->
                    navController.navigate("$ROUTE_EXERCISE_EDIT/$exerciseId")
                },
            )
        }

        // ---------------- 全屏演示播放（视频 / 动图 / 图片） ----------------
        composable(
            route = ROUTE_MEDIA_PLAYER,
            arguments = listOf(
                navArgument("fileName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("kind") {
                    type = NavType.StringType
                    defaultValue = MediaType.VIDEO_LOCAL.name
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            MediaPlayerScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---------------- 动作编辑 ----------------
        composable(
            route = "$ROUTE_EXERCISE_EDIT/{exerciseId}",
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType }),
        ) {
            ExerciseEditScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ---------------- 统一回收站（计划/动作/历史） ----------------
        composable(
            route = "$ROUTE_RECYCLE_BIN/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            val type = when (entry.arguments?.getString("type")) {
                TrashType.EXERCISE.routeValue -> TrashType.EXERCISE
                TrashType.HISTORY.routeValue -> TrashType.HISTORY
                else -> TrashType.PLAN
            }
            UnifiedRecycleBinScreen(
                type = type,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ---------------- 设置 ----------------
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenBackup = { navController.navigate(ROUTE_BACKUP) },
                onNavigateToPrivacy = { navController.navigate(ROUTE_PRIVACY) },
            )
        }

        // ---------------- 隐私政策 ----------------
        composable(ROUTE_PRIVACY) {
            PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---------------- 训练日历 ----------------
        composable(ROUTE_SCHEDULE) {
            ScheduleScreen(
                onNavigateBack = { navController.popBackStack() },
                onWorkoutStarted = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId")
                },
            )
        }

        // ---------------- 备份与恢复 ----------------
        composable(ROUTE_BACKUP) {
            BackupScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---------------- 计划详情 ----------------
        composable(
            route = "$ROUTE_PLAN_DETAIL_PREFIX/{planId}",
            arguments = listOf(navArgument("planId") { type = NavType.LongType }),
        ) {
            PlanDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onEdit = { planId ->
                    navController.navigate("$ROUTE_PLAN_EDIT_PREFIX/$planId")
                },
                onWorkoutStarted = { sessionId ->
                    navController.navigate("$ROUTE_WORKOUT_PREFIX/$sessionId") {
                        // 只保留一个训练页实例：避免连续点两次"开始训练"入栈两次
                        launchSingleTop = true
                    }
                },
            )
        }

        // ---------------- 计划编辑 ----------------
        composable(
            route = "$ROUTE_PLAN_EDIT_PREFIX/{planId}",
            arguments = listOf(navArgument("planId") { type = NavType.LongType }),
        ) {
            PlanEditScreen(
                onNavigateBack = { navController.popBackStack() },
                // 选择器不再需要"目标计划 id"：选中的动作先进入编辑页的内存草稿，
                // 用户点保存时才真正落库。这样新建计划在保存前不会在列表里留下半成品。
                onAddExercise = {
                    navController.navigate("$ROUTE_EXERCISE_PICKER/$NO_PLAN_ID")
                },
            )
        }

        // ---------------- 动作选择器 ----------------
        composable(
            route = "$ROUTE_EXERCISE_PICKER/{targetPlanId}",
            arguments = listOf(navArgument("targetPlanId") { type = NavType.LongType }),
        ) {
            ExercisePickerScreen(
                onNavigateBack = { navController.popBackStack() },
                onExerciseSelected = { _ ->
                    // 选择结果由选择器自己的 ViewModel 通过 NavResultBus 投递给上一页，
                    // 这里只负责出栈。
                    //
                    // 以前这里写 previousBackStackEntry.savedStateHandle ——
                    // 实测它与上一页 ViewModel 拿到的 SavedStateHandle 不是同一实例，
                    // 值写进去了但 ViewModel 永远收不到，且完全静默。
                    // 详见 core:common 的 NavResultBus 注释。
                    navController.popBackStack()
                },
            )
        }

        // ---------------- 训练执行 ----------------
        composable(
            route = "$ROUTE_WORKOUT_PREFIX/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            WorkoutScreen(
                onNavigateBack = { navController.popBackStack() },
                onFinished = { sessionId ->
                    // 进总结页，并把训练页从返回栈移除：
                    // 否则用户按返回键会回到已经结束的会话，看到一堆已完成/跳过的死数据
                    navController.navigate("$ROUTE_WORKOUT_SUMMARY/$sessionId") {
                        popUpTo("$ROUTE_WORKOUT_PREFIX/{sessionId}") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                // 训练中的"临时动作 / 替换动作"复用同一个动作选择器：
                // 有意不给选择器传"意图"，由训练页自己记住为什么打开它 ——
                // 这样选择器只负责"选一个动作"这一件事。
                onOpenExercisePicker = {
                    navController.navigate("$ROUTE_EXERCISE_PICKER/$NO_PLAN_ID")
                },
                onPlayMedia = { media ->
                    mediaPlayerRoute(media)?.let { navController.navigate(it) }
                },
            )
        }

        // ---------------- 训练总结 ----------------
        composable(
            route = "$ROUTE_WORKOUT_SUMMARY/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            WorkoutSummaryScreen(
                onDone = {
                    // 训练完成回到首页（而不是计划列表）：首页是新的"根"，概览数据刚更新
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "$ROUTE_SESSION_DETAIL/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            SessionDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---------------- 身体数据 ----------------
        composable(ROUTE_BODY) {
            BodyScreen(
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenDiet = { navController.navigate(ROUTE_DIET) },
            )
        }

        // ---------------- 饮食记录（食物库 + 记一笔） ----------------
        composable(ROUTE_DIET) {
            DietScreen(onNavigateBack = { navController.popBackStack() })
        }
        }
    }
}

private data class BottomBarItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    /** 带默认参数的实际导航路由（route 是含占位符的模板，用于选中态比对） */
    val navRoute: String = route,
)

private val BOTTOM_BAR_ITEMS = listOf(
    BottomBarItem(ROUTE_HOME, DesignR.string.tab_home, Icons.Filled.Home),
    // 训练 tab = 今日训练（直接开炼）；训练库收进今日页右上角入口
    BottomBarItem(ROUTE_TODAY, DesignR.string.tab_training, Icons.Filled.PlayArrow),
    BottomBarItem(
        ROUTE_DATA,
        DesignR.string.tab_data,
        Icons.Filled.Insights,
        navRoute = "data?segment=${DataSegment.STATS.routeValue}",
    ),
    BottomBarItem(ROUTE_BODY, DesignR.string.tab_body, Icons.Filled.MonitorWeight),
)

private val BOTTOM_BAR_ROUTES = BOTTOM_BAR_ITEMS.map { it.route }.toSet()

/**
 * 便当盒主题的底部菜单：没有整条底板，只有几块"状态块"浮在内容之上。
 *
 * 设计立场（与 M3 NavigationBar 的关键区别）：
 *  - 底栏容器完全透明 —— 页面内容从菜单后面穿过去，滚动时能看见卡片经过，
 *    不再有一条白底横条把内容"切"在边界上
 *  - 未选中项只留图标（半透明块保证可读），选中项展开成实心强调色胶囊 + 文字
 *  - 因为不再由 Scaffold 提供底部内边距，页面需自行留出 `bottomBarInset`
 */
@Composable
private fun BentoNavigationBar(
    currentRoute: String?,
    onSelect: (BottomBarItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BOTTOM_BAR_ITEMS.forEach { item ->
            BentoNavItem(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BentoNavItem(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    // 菜单块不能用 surfaceVariant：那正是卡片色，菜单会和卡片糊成同一块白。
    // 用"画布色 + 7% 前景色"混出一层中性灰，菜单才算独立的一层。
    val navBlockColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.onBackground,
        0.07f,
    ).copy(alpha = 0.94f)

    val indicatorColor by animateColorAsState(
        // 未选中项用半透明块：既保证图标在任意内容上都读得清，又不会挡住内容
        targetValue = if (selected) primary else navBlockColor,
        animationSpec = tween(220),
        label = "bentoNavIndicator",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) onPrimary else onSurfaceVariant,
        animationSpec = tween(220),
        label = "bentoNavContent",
    )
    val label = stringResource(item.labelRes)

    Box(
        modifier = modifier
            .softPressedScale(interactionSource)
            .clip(RoundedCornerShape(22.dp))
            .background(indicatorColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // 图标始终带无障碍文案：未选中时它就是这一项的语义
            Icon(
                item.icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(visible = selected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// 路由常量。与 core:common 的路由对象保持一一对应，集中在此便于核对。
private const val ROUTE_HOME = "home"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_TODAY = "today"
private const val ROUTE_LIBRARY = "library?segment={segment}"
private const val ROUTE_PLAN_DETAIL_PREFIX = "plan_detail"
private const val ROUTE_PLAN_EDIT_PREFIX = "plan_edit"
private const val ROUTE_EXERCISE_PICKER = "exercise_picker"
private const val ROUTE_WORKOUT_PREFIX = "workout"
private const val ROUTE_WORKOUT_SUMMARY = "workout_summary"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PRIVACY = "privacy"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_EXERCISE_DETAIL = "exercise_detail"
private const val ROUTE_EXERCISE_EDIT = "exercise_edit"
private const val ROUTE_EXERCISE_PICKER_PREFIX = "exercise_picker_new"
private const val ROUTE_RECYCLE_BIN = "recycle_bin"
private const val ROUTE_DATA = "data?segment={segment}"
private const val ROUTE_SESSION_DETAIL = "session_detail"
private const val ROUTE_MEDIA_PLAYER =
    "media_player?fileName={fileName}&url={url}&kind={kind}&title={title}"
private const val ROUTE_BODY = "body"
private const val ROUTE_DIET = "diet"
private const val ROUTE_BACKUP = "backup"

/**
 * 演示资源 → 播放页路由。
 * 参数必须 URL 编码：外链里带 : / ? & 会破坏 route 解析。
 * kind 决定播放页用 Media3 还是图片查看器（GIF/动图不需要播放器）。
 */
private fun mediaPlayerRoute(media: MediaRef?): String? = when (media) {
    is MediaRef.Local ->
        "media_player?fileName=${Uri.encode(media.fileName)}&kind=${media.type.name}"
    is MediaRef.Remote ->
        "media_player?url=${Uri.encode(media.url)}&kind=${MediaType.VIDEO_LINK.name}"
    else -> null
}

/**
 * 动作选择器的"不针对某个计划"占位值。
 * 训练中临时添加动作、替换动作都走 `exercise_picker/-1`，选择结果同样通过
 * [com.fitnesspro.core.common.navigation.NavResults.PICKED_EXERCISE_ID] 回传给训练页。
 */
private const val NO_PLAN_ID = -1L

// 保留类型安全路由对象的引用，确保它们与实际使用的字符串保持同步（编译期即可发现遗漏）
@Suppress("unused")
private val routeReferences = listOf(
    PlanListRoute,
    PlanDetailRoute::class,
    PlanEditRoute::class,
    ExercisePickerRoute::class,
    WorkoutRoute::class,
)
