package com.fitnesspro.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.model.ThemeMode
import com.fitnesspro.core.model.ThemePreset
import com.fitnesspro.core.model.UserSettings

/**
 * 设计系统 Token。
 *
 * 页面禁止硬编码颜色与尺寸：颜色只能从 MaterialTheme.colorScheme 或 FitnessTheme.colors 取，
 * 间距只能从 FitnessTheme.spacing 取。这样深色模式与后续换肤都不需要改页面代码。
 */
@Immutable
data class FitnessColors(
    /** PR 徽标高亮色 —— 必须与图标配合使用，不能只靠颜色传达状态（无障碍要求） */
    val prHighlight: Color,
    val setCompleted: Color,
    val setPending: Color,
    val setSkipped: Color,
    val setFailed: Color,
    val warmupAccent: Color,
    val supersetAccent: Color,
    val chartPrimary: Color,
    val chartSecondary: Color,
    val restTimerTrack: Color,
    val restTimerIndicator: Color,
    val restTimerFinished: Color,
    /** 品牌顶栏：绿底标题区（M3 无独立顶栏色彩角色，由 FitnessTopAppBar 使用） */
    val appBar: Color,
    val onAppBar: Color,
    /** 顶栏随内容滚动的加深色 */
    val appBarScrolled: Color,
)

@Immutable
data class FitnessSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    /** 无障碍强制：所有可点击元素不得小于该尺寸 */
    val touchTargetMin: Dp = 48.dp,
)

/*
 * 视觉基调：绿顶栏 + 白色内容区 + 鲜绿强调。
 *
 *  - 顶栏：M3 没有独立的顶栏色彩角色，由 FitnessTopAppBar 统一取
 *    FitnessColors.appBar（绿底白字），16 个页面通过该组件获得品牌绿标题区
 *  - 内容区：background/surface 全部不透明白，surfaceContainer* 分层用
 *    极浅的绿灰做卡片与底层的层次（不再用透明玻璃）
 *  - 强调：primaryContainer 给最鲜艳的绿（FAB / 主操作），比 primary 的
 *    按钮绿更亮一档；secondaryContainer（底部栏选中胶囊）同样提亮
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    // 强调色：比按钮绿更鲜艳的绿，用于 FAB 与主要操作容器
    primaryContainer = Color(0xFF00C853),
    onPrimaryContainer = Color(0xFF00330F),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    // secondaryContainer 不指定会落到 M3 基线的淡紫（底部栏选中胶囊会变紫），必须显式给绿。
    // 底部栏选中胶囊：多邻国图标深绿 + 半透明 = 深绿毛玻璃胶囊
    secondaryContainer = Color(0xCC58A700),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4D7C0F),
    tertiaryContainer = Color(0xFFE4F4C9),
    onTertiaryContainer = Color(0xFF2E4A08),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF18211B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18211B),
    surfaceVariant = Color(0xFFE7F3EB),
    onSurfaceVariant = Color(0xFF3F4943),
    // 分层：卡片与底层用极浅绿灰区分层次；surfaceContainer 半透明白
    // —— 底部导航栏用它当底色，内容与背景渐变透出形成毛玻璃
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FBF8),
    surfaceContainer = Color(0xCCFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF5F1),
    surfaceContainerHighest = Color(0xFFEAF2EC),
    outline = Color(0xFF6F7D74),
    outlineVariant = Color(0xFFC9D7CD),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF00D06A),
    onPrimaryContainer = Color(0xFF00330F),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xCC4E8F00),
    onSecondaryContainer = Color(0xFFEFFFE0),
    tertiary = Color(0xFFA3E635),
    tertiaryContainer = Color(0xFF3D5C0B),
    onTertiaryContainer = Color(0xFFD9F39B),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141B17),
    onBackground = Color(0xFFE0E6E1),
    surface = Color(0xFF1B231F),
    onSurface = Color(0xFFE0E6E1),
    surfaceVariant = Color(0xFF2F3A33),
    onSurfaceVariant = Color(0xFFBCC9C0),
    surfaceContainerLowest = Color(0xFF161D19),
    surfaceContainerLow = Color(0xFF202924),
    surfaceContainer = Color(0xFF252E29),
    surfaceContainerHigh = Color(0xFF2B342F),
    surfaceContainerHighest = Color(0xFF313A35),
    outline = Color(0xFF89938B),
    outlineVariant = Color(0xFF3F4943),
    error = Color(0xFFFFB4AB),
)

private val LightExtended = FitnessColors(
    prHighlight = Color(0xFFD97706),
    setCompleted = Color(0xFF059669),
    setPending = Color(0xFF9CA3AF),
    setSkipped = Color(0xFFA1887F),
    setFailed = Color(0xFFDC2626),
    warmupAccent = Color(0xFF8B5CF6),
    supersetAccent = Color(0xFF0EA5E9),
    chartPrimary = Color(0xFF059669),
    chartSecondary = Color(0xFF0EA5E9),
    restTimerTrack = Color(0xFFD1E7DA),
    restTimerIndicator = Color(0xFF00B45F),
    restTimerFinished = Color(0xFFD97706),
    // 顶栏：多邻国图标的浅绿高光部；浅绿上用深绿字（白字对比度不足）
    appBar = Color(0xFF89E219),
    onAppBar = Color(0xFF24450A),
    appBarScrolled = Color(0xFF7CD40F),
)

private val DarkExtended = FitnessColors(
    prHighlight = Color(0xFFFBBF24),
    setCompleted = Color(0xFF34D399),
    setPending = Color(0xFF757575),
    setSkipped = Color(0xFFA1887F),
    setFailed = Color(0xFFEF9A9A),
    warmupAccent = Color(0xFFB39DDB),
    supersetAccent = Color(0xFF4FC3F7),
    chartPrimary = Color(0xFF34D399),
    chartSecondary = Color(0xFF4FC3F7),
    restTimerTrack = Color(0xFF3F4943),
    restTimerIndicator = Color(0xFF34D399),
    restTimerFinished = Color(0xFFFBBF24),
    // 深色模式顶栏：多邻国图标深绿主体 + 白字
    appBar = Color(0xFF46A302),
    onAppBar = Color(0xFFFFFFFF),
    appBarScrolled = Color(0xFF3B8B02),
)

/**
 * 全局形状：整体调圆一档。Material 组件全部从 MaterialTheme.shapes 取圆角，改这里全局生效。
 * medium 18dp = 普通卡片；large 24dp = 对话框；extraLarge 32dp = BottomSheet 圆肩；
 * FAB/Chip 等由组件自身的全圆逻辑接管。
 */
private val FitnessShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 便当盒 Soft UI 的形状：圆角整体再调大两档。
 * 大圆角 + 柔和阴影是「软质感」的两个必要条件，缺一个都会退回普通卡片。
 */
private val BentoShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * 布局令牌：形状、阴影、网格与强调色随主题预设切换。
 * 页面只读令牌，不写死数值 —— 否则「换主题只换颜色」就无从避免。
 */
@Immutable
data class FitnessLayout(
    /** 首页启用便当盒网格（大小卡错落），而非一列等宽卡片 */
    val bentoGrid: Boolean = false,
    /** 卡片圆角 */
    val cardRadius: Dp = 18.dp,
    /** 卡片柔和悬浮阴影：Soft UI 的核心，0 表示平面卡片 */
    val cardElevation: Dp = 0.dp,
    /** 卡片之间的沟槽宽度 */
    val cardGap: Dp = 16.dp,
    /**
     * 悬浮菜单压在内容之上时，页面需要预留的底部留白。
     *
     * 经典主题下底部栏不透明、由 Scaffold 提供内边距，页面无需自己留白（0）；
     * 便当盒主题下菜单透明且内容从它后面穿过，页面必须自己补足这段高度，
     * 否则最后一屏内容会被菜单永久遮住、滚不出来。
     */
    val bottomBarInset: Dp = 0.dp,
    /** 便当盒里循环使用的饱满色块（参考图的橙/紫/蓝/黄） */
    val accents: List<Color> = emptyList(),
)

private val DefaultLayout = FitnessLayout()

private fun layoutFor(preset: ThemePreset, dark: Boolean): FitnessLayout =
    if (preset != ThemePreset.BENTO_SOFT) {
        DefaultLayout
    } else {
        FitnessLayout(
            bentoGrid = true,
            cardRadius = 26.dp,
            // Soft UI 的阴影要"看得见"：低于 10dp 在浅色 M3 里几乎不可感知
            cardElevation = 10.dp,
            cardGap = 12.dp,
            // 菜单高约 100dp（含系统手势条内边距），再留一点呼吸空间
            bottomBarInset = 116.dp,
            accents = if (dark) {
                listOf(
                    Color(0xFFFF7A50), // 珊瑚橙
                    Color(0xFFA76BFF), // 电紫
                    Color(0xFF4D6BFF), // 宝蓝
                    Color(0xFFFFC94D), // 亮黄
                )
            } else {
                listOf(
                    Color(0xFFF0653C), // 珊瑚橙
                    Color(0xFF7B2FE8), // 电紫
                    Color(0xFF2B3FE0), // 宝蓝
                    Color(0xFFF0B429), // 亮黄
                )
            },
        )
    }

/** 应用级背景：白色为主的极浅渐变，只提供一点层次感，不与白色内容区打架。 */
private fun appBackground(darkTheme: Boolean, preset: ThemePreset): Brush {
    // 便当盒：中性浅灰画布，白卡才能"浮"起来；绿色渐变会让橙/紫强调色发脏
    if (preset == ThemePreset.BENTO_SOFT) {
        return if (darkTheme) {
            Brush.verticalGradient(
                listOf(Color(0xFF191820), Color(0xFF17161A), Color(0xFF131217)),
            )
        } else {
            Brush.verticalGradient(
                listOf(Color(0xFFF6F5F8), Color(0xFFF1F0F4), Color(0xFFEAE9EF)),
            )
        }
    }
    return if (darkTheme) {
        Brush.verticalGradient(
            listOf(Color(0xFF121A15), Color(0xFF161F1A), Color(0xFF101713)),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFF6FBF7), Color(0xFFEFF8F2)),
        )
    }
}

val LocalFitnessColors = staticCompositionLocalOf { LightExtended }
val LocalFitnessSpacing = staticCompositionLocalOf { FitnessSpacing() }
val LocalFitnessLayout = staticCompositionLocalOf { DefaultLayout }
/** 页面级强调色：未提供时回退到主题 primary */
val LocalFitnessAccent = staticCompositionLocalOf { Color.Unspecified }

object FitnessTheme {
    val colors: FitnessColors
        @Composable @ReadOnlyComposable get() = LocalFitnessColors.current

    val spacing: FitnessSpacing
        @Composable @ReadOnlyComposable get() = LocalFitnessSpacing.current

    /** 形状 / 阴影 / 网格令牌：页面据此决定"卡片长什么样" */
    val layout: FitnessLayout
        @Composable @ReadOnlyComposable get() = LocalFitnessLayout.current

    /**
     * 当前页面的强调色。
     *
     * 便当盒主题下每个一级页面分配一个不同的强调色（橙/紫/蓝/黄），
     * 用于该页的分区条、图表、选中态 —— 页面之间一眼可辨，但用同一套色板，风格仍统一。
     * 其它主题未提供时回退到主题 primary，行为与从前一致。
     */
    val accent: Color
        @Composable get() {
            val provided = LocalFitnessAccent.current
            return if (provided.isSpecified) provided else MaterialTheme.colorScheme.primary
        }
}

// ---------------------------------------------------------------------------
// 主题预设与自定义主题
// ---------------------------------------------------------------------------

/**
 * 主题色板：只定义用户可感知的四个角色（标题栏 / 强调色 / 内容区 / 其他），
 * 其余 scheme 角色由 [buildColorScheme] 从这些颜色推导。
 */
data class ThemePalette(
    val appBar: Color,
    val onAppBar: Color,
    val appBarScrolled: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
)

/** 自定义主题：非 null 的槽位覆盖预设色板的对应角色 */
data class CustomThemeColors(
    val appBar: Color? = null,
    val accent: Color? = null,
    val content: Color? = null,
    val other: Color? = null,
    val font: Color? = null,
) {
    val hasAny: Boolean get() = this != CustomThemeColors()
}

/** 从 UserSettings 的 ARGB 值构建自定义主题；全部为 null 时返回 null（跟随预设） */
fun customThemeFrom(settings: UserSettings): CustomThemeColors? {
    val custom = CustomThemeColors(
        appBar = settings.customAppBarColor?.let(::Color),
        accent = settings.customAccentColor?.let(::Color),
        content = settings.customContentColor?.let(::Color),
        other = settings.customOtherColor?.let(::Color),
        font = settings.customFontColor?.let(::Color),
    )
    return if (custom.hasAny) custom else null
}

/** 预设在设置页选择器里的预览色：顶栏色 + 强调色 */
fun themePresetPreviewColors(preset: ThemePreset): Pair<Color, Color> =
    paletteFor(preset, dark = false).let { it.appBar to it.primary }

private fun paletteFor(preset: ThemePreset, dark: Boolean): ThemePalette = when (preset) {
    ThemePreset.VIVID_GREEN -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF46A302), onAppBar = Color.White, appBarScrolled = Color(0xFF3B8B02),
            primary = Color(0xFF34D399), onPrimary = Color(0xFF003822),
            primaryContainer = Color(0xFF00D06A), onPrimaryContainer = Color(0xFF00330F),
            secondaryContainer = Color(0xCC4E8F00), onSecondaryContainer = Color(0xFFEFFFE0),
            background = Color(0xFF141B17), onBackground = Color(0xFFE0E6E1),
            surfaceVariant = Color(0xFF2F3A33), onSurfaceVariant = Color(0xFFBCC9C0),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFF89E219), onAppBar = Color(0xFF24450A), appBarScrolled = Color(0xFF7CD40F),
            primary = Color(0xFF059669), onPrimary = Color.White,
            primaryContainer = Color(0xFF00C853), onPrimaryContainer = Color(0xFF00330F),
            secondaryContainer = Color(0xCC58A700), onSecondaryContainer = Color.White,
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF18211B),
            surfaceVariant = Color(0xFFE7F3EB), onSurfaceVariant = Color(0xFF3F4943),
        )
    }

    ThemePreset.FRESH_GREEN -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF15803D), onAppBar = Color.White, appBarScrolled = Color(0xFF136F35),
            primary = Color(0xFF4ADE80), onPrimary = Color(0xFF052E16),
            primaryContainer = Color(0xFF16A34A), onPrimaryContainer = Color(0xFF052E16),
            secondaryContainer = Color(0xCC166534), onSecondaryContainer = Color(0xFFECFDF5),
            background = Color(0xFF111915), onBackground = Color(0xFFE0E6E1),
            surfaceVariant = Color(0xFF26382C), onSurfaceVariant = Color(0xFFBCC9C0),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFF4ADE80), onAppBar = Color(0xFF052E16), appBarScrolled = Color(0xFF34D399),
            primary = Color(0xFF16A34A), onPrimary = Color.White,
            primaryContainer = Color(0xFF86EFAC), onPrimaryContainer = Color(0xFF052E16),
            secondaryContainer = Color(0xCC15803D), onSecondaryContainer = Color.White,
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF14211A),
            surfaceVariant = Color(0xFFE3F6EA), onSurfaceVariant = Color(0xFF3E4A42),
        )
    }

    ThemePreset.PURE_WHITE -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF292524), onAppBar = Color(0xFFFAFAF9), appBarScrolled = Color(0xFF221F1E),
            primary = Color(0xFFD6D3D1), onPrimary = Color(0xFF1C1917),
            primaryContainer = Color(0xFF57534E), onPrimaryContainer = Color(0xFFFAFAF9),
            secondaryContainer = Color(0xCC57534E), onSecondaryContainer = Color(0xFFFAFAF9),
            background = Color(0xFF141312), onBackground = Color(0xFFE7E5E4),
            surfaceVariant = Color(0xFF2C2A28), onSurfaceVariant = Color(0xFFBDB9B4),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFFF7F6F3), onAppBar = Color(0xFF1C1917), appBarScrolled = Color(0xFFEDEAE4),
            primary = Color(0xFF57534E), onPrimary = Color.White,
            primaryContainer = Color(0xFFD6D3D1), onPrimaryContainer = Color(0xFF1C1917),
            secondaryContainer = Color(0xE6D6D3D1), onSecondaryContainer = Color(0xFF1C1917),
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF1C1917),
            surfaceVariant = Color(0xFFF1EFEC), onSurfaceVariant = Color(0xFF57534E),
        )
    }

    ThemePreset.OCEAN_BLUE -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF1E3A8A), onAppBar = Color(0xFFEFF6FF), appBarScrolled = Color(0xFF1B337A),
            primary = Color(0xFF60A5FA), onPrimary = Color(0xFF0A2540),
            primaryContainer = Color(0xFF1D4ED8), onPrimaryContainer = Color(0xFFEFF6FF),
            secondaryContainer = Color(0xCC1E40AF), onSecondaryContainer = Color(0xFFEFF6FF),
            background = Color(0xFF10161E), onBackground = Color(0xFFDDE5F0),
            surfaceVariant = Color(0xFF253243), onSurfaceVariant = Color(0xFFBAC4D6),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFF93C5FD), onAppBar = Color(0xFF0A2540), appBarScrolled = Color(0xFF7DB3F7),
            primary = Color(0xFF1D4ED8), onPrimary = Color.White,
            primaryContainer = Color(0xFF93C5FD), onPrimaryContainer = Color(0xFF0A2540),
            secondaryContainer = Color(0xCC1E40AF), onSecondaryContainer = Color.White,
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF101828),
            surfaceVariant = Color(0xFFE7EEFB), onSurfaceVariant = Color(0xFF3A4559),
        )
    }

    ThemePreset.SUNSET_ORANGE -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF9A3412), onAppBar = Color(0xFFFFF7ED), appBarScrolled = Color(0xFF862D0F),
            primary = Color(0xFFFB923C), onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFFC2410C), onPrimaryContainer = Color(0xFFFFF7ED),
            secondaryContainer = Color(0xCC9A3412), onSecondaryContainer = Color(0xFFFFF7ED),
            background = Color(0xFF1B1310), onBackground = Color(0xFFEFE2D9),
            surfaceVariant = Color(0xFF33261F), onSurfaceVariant = Color(0xFFCBBFB5),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFFFDBA74), onAppBar = Color(0xFF431407), appBarScrolled = Color(0xFFF59E0B),
            primary = Color(0xFFEA580C), onPrimary = Color.White,
            primaryContainer = Color(0xFFFDBA74), onPrimaryContainer = Color(0xFF431407),
            secondaryContainer = Color(0xCC9A3412), onSecondaryContainer = Color.White,
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF231407),
            surfaceVariant = Color(0xFFFBF0E7), onSurfaceVariant = Color(0xFF52453B),
        )
    }

    // Bento 软UI：浅灰底 + 白色软质感卡片，珊瑚橙主色，电紫次级容器，宝蓝/亮黄作为图表点缀
    ThemePreset.BENTO_SOFT -> if (dark) {
        ThemePalette(
            appBar = Color(0xFF211F26), onAppBar = Color(0xFFF4F3F6), appBarScrolled = Color(0xFF2A2830),
            primary = Color(0xFFFF7A50), onPrimary = Color(0xFF2B1206),
            primaryContainer = Color(0xFFB84527), onPrimaryContainer = Color(0xFFFFE8DE),
            secondaryContainer = Color(0x997B2FE8), onSecondaryContainer = Color(0xFFEFE4FF),
            background = Color(0xFF17161A), onBackground = Color(0xFFECEBF0),
            surfaceVariant = Color(0xFF26242B), onSurfaceVariant = Color(0xFFC4C2CC),
        )
    } else {
        ThemePalette(
            appBar = Color(0xFFECEAEF), onAppBar = Color(0xFF1B1A1F), appBarScrolled = Color(0xFFE2E0E7),
            primary = Color(0xFFF0653C), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9CB), onPrimaryContainer = Color(0xFF521B07),
            secondaryContainer = Color(0xFFEBDDFF), onSecondaryContainer = Color(0xFF4A1590),
            background = Color(0xFFF2F1F4), onBackground = Color(0xFF1B1A1F),
            surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF4C4A54),
        )
    }
}

/** 背景上可读的前景文字色：亮底深字、暗底白字 */
private fun contrastOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White

/** 用自定义颜色覆盖预设色板的对应角色 */
private fun ThemePalette.withCustom(custom: CustomThemeColors?, dark: Boolean): ThemePalette {
    if (custom == null || !custom.hasAny) return this
    var p = this

    custom.appBar?.let { c ->
        p = p.copy(
            appBar = c,
            onAppBar = contrastOn(c),
            appBarScrolled = lerp(c, Color.Black, 0.12f),
        )
    }
    custom.accent?.let { c ->
        val container = lerp(c, if (dark) Color.Black else Color.White, 0.30f)
        p = p.copy(
            primary = c,
            onPrimary = contrastOn(c),
            primaryContainer = container,
            onPrimaryContainer = contrastOn(container),
        )
    }
    custom.content?.let { c ->
        p = p.copy(background = c, onBackground = contrastOn(c))
    }
    custom.other?.let { c ->
        p = p.copy(
            surfaceVariant = c,
            onSurfaceVariant = contrastOn(c),
            secondaryContainer = c.copy(alpha = 0.80f),
            onSecondaryContainer = contrastOn(c),
        )
    }
    // 字体颜色最后处理：同时刷新内容区文字与次级文字
    custom.font?.let { f ->
        p = p.copy(
            onBackground = f,
            onSurfaceVariant = lerp(f, Color.Gray, 0.35f),
        )
    }
    return p
}

/** 从色板生成完整 ColorScheme：未定义的分层色由 内容区/其他 两色插值得到 */
private fun buildColorScheme(base: ColorScheme, p: ThemePalette): ColorScheme {
    fun mix(fraction: Float): Color = lerp(p.background, p.surfaceVariant, fraction)
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.background,
        onSurface = p.onBackground,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.onSurfaceVariant,
        outlineVariant = lerp(p.surfaceVariant, p.onSurfaceVariant, 0.45f),
        surfaceContainerLowest = mix(0.15f),
        surfaceContainerLow = mix(0.35f),
        surfaceContainer = mix(0.55f),
        surfaceContainerHigh = mix(0.75f),
        surfaceContainerHighest = mix(0.90f),
    )
}

/** 顶栏色/图表主色等品牌角色跟随色板，其余保留基线的语义色 */
private fun buildExtended(base: FitnessColors, p: ThemePalette): FitnessColors = base.copy(
    appBar = p.appBar,
    onAppBar = p.onAppBar,
    appBarScrolled = p.appBarScrolled,
    chartPrimary = p.primary,
    restTimerIndicator = p.primary,
    restTimerTrack = lerp(p.surfaceVariant, p.background, 0.4f),
)

/** 壁纸玻璃模式：背景透出壁纸，容器变半透明 */
private fun ColorScheme.asGlass(dark: Boolean): ColorScheme = copy(
    background = Color.Transparent,
    surface = if (dark) Color(0xCC1B231F) else Color(0xCCFFFFFF),
    surfaceVariant = surfaceVariant.copy(alpha = 0.80f),
    surfaceContainerLowest = surfaceContainerLowest.copy(alpha = 0.96f),
    surfaceContainerLow = surfaceContainerLow.copy(alpha = 0.78f),
    surfaceContainer = surfaceContainer.copy(alpha = 0.72f),
    surfaceContainerHigh = surfaceContainerHigh.copy(alpha = 0.80f),
)

@Composable
fun FitnessProTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 有壁纸时启用玻璃分层：背景透出壁纸，容器变半透明 */
    hasWallpaper: Boolean = false,
    /** 主题配色预设 */
    themePreset: ThemePreset = ThemePreset.BENTO_SOFT,
    /** 自定义主题色，非空槽位覆盖预设对应角色 */
    customTheme: CustomThemeColors? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val palette = paletteFor(themePreset, darkTheme).withCustom(customTheme, darkTheme)
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val colorScheme = buildColorScheme(baseScheme, palette)
        .let { if (hasWallpaper) it.asGlass(darkTheme) else it }

    val layout = layoutFor(themePreset, darkTheme)
    // 便当盒主题用更大的圆角；其余主题保持原有形状，切主题只影响配色
    val shapes = if (layout.bentoGrid) BentoShapes else FitnessShapes

    CompositionLocalProvider(
        LocalFitnessColors provides buildExtended(
            if (darkTheme) DarkExtended else LightExtended,
            palette,
        ),
        LocalFitnessSpacing provides FitnessSpacing(),
        LocalFitnessLayout provides layout,
    ) {
        // 有壁纸时由 MainActivity 在本 Box 底下铺壁纸图，这里不再画渐变
        Box(
            modifier = if (hasWallpaper) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .background(appBackground(darkTheme, themePreset))
            },
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = FitnessTypography,
                shapes = shapes,
                content = content,
            )
        }
    }
}
