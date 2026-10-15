package com.fitnesspro.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.component.LabelChip
import com.fitnesspro.core.designsystem.component.NumberStepperField
import com.fitnesspro.core.designsystem.component.PrBadge
import com.fitnesspro.core.designsystem.component.SetStatusDot
import com.fitnesspro.core.designsystem.component.SkeletonList
import com.fitnesspro.core.designsystem.theme.FitnessProTheme
import com.fitnesspro.core.model.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * 设计系统截图测试基线。
 *
 * 机制：Robolectric 的 NATIVE 图形模式（Robolectric 4.10+）会在 JVM 上真实执行
 * Skia 渲染管线，因此 `captureToImage()` 拿到的是真实渲染的位图，不是布局快照。
 * 测试把渲染结果与 `src/test/snapshots/<name>.png` 基准图逐像素比对：
 *
 *  - 更新基准：`gradlew :core:designsystem:testDebugUnitTest -Pscreenshot.record=true`
 *  - 默认运行：与基准比对，平均像素差超过容差即失败
 *
 * 基线只覆盖**纯静态组件**；带动画或不确定性的组件（转圈进度条等）不进基线。
 * 像素比对对字体渲染敏感 —— 基准图必须由本套测试自身生成，不允许手工修改。
 */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-160dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recordMode: Boolean
        get() = System.getProperty("screenshot.record") == "true"

    private val snapshotDir = File("src/test/snapshots")

    @Test
    fun labelChip() = snapshot("label_chip") {
        LabelChip("休息 90 秒")
    }

    @Test
    fun prBadge() = snapshot("pr_badge") {
        PrBadge()
    }

    @Test
    fun setStatusDots() = snapshot("set_status_dots") {
        Row {
            SetStatusDot(completed = true)
            Spacer(Modifier.width(16.dp))
            SetStatusDot(completed = false)
            Spacer(Modifier.width(16.dp))
            SetStatusDot(completed = true, isPersonalRecord = true)
        }
    }

    @Test
    fun numberStepper() = snapshot("number_stepper") {
        NumberStepperField(
            value = "60",
            onValueChange = {},
            onStepUp = {},
            onStepDown = {},
            suffix = "kg",
            contentDescription = "重量",
        )
    }

    @Test
    fun skeletonList() = snapshot("skeleton_list") {
        SkeletonList(itemCount = 3)
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            FitnessProTheme(themeMode = ThemeMode.LIGHT) {
                // 对内容节点抓图而不是 onRoot()：根节点边界取决于主题内部布局
                // （如全屏渐变背景 Box），会让基准尺寸漂移；内容节点才是被测对象
                Box(Modifier.padding(16.dp)) { content() }
            }
        }
        composeRule.waitForIdle()
        snapshotDir.mkdirs()
        val golden = File(snapshotDir, "$name.png")

        // 统一抓全窗口：Roborazzi 的节点级裁剪依赖语义树布局细节（脆弱），
        // 全窗口尺寸固定（360x640，由 @Config qualifiers 决定），基准更稳定
        val target = composeRule.onRoot()
        if (!golden.exists()) {
            // 首次运行落基准，但必须显式提醒提交进版本库
            target.captureRoboImage(golden.path)
            assertTrue(
                "基准图 $name.png 未能生成（检查 roborazzi.test.record 配置），请提交到版本库",
                golden.exists(),
            )
            return
        }
        if (recordMode) {
            target.captureRoboImage(golden.path)
            return
        }

        // 比对：抓到临时文件再解码，避免覆盖基准
        val captured = File(snapshotDir, "$name.captured.png")
        target.captureRoboImage(captured.path)
        // 用 BitmapFactory（Robolectric NATIVE 支持真实解码）而不是 javax.imageio：
        // 后者属于 java.desktop，根本不在 android.jar 的类面上
        val actual = BitmapFactory.decodeFile(captured.path) ?: error("抓图失败：$name")
        val expected = BitmapFactory.decodeFile(golden.path)
            ?: error("基准图损坏：${golden.path}")
        val meanDiff = meanAbsDiff(actual, expected)
        captured.delete()
        assertTrue(
            "与基准不一致（平均像素差 $meanDiff > $TOLERANCE）：$name。" +
                "若是预期的视觉变更，用 -Pscreenshot.record=true 重新生成基准并提交",
            meanDiff <= TOLERANCE,
        )
    }

    private fun meanAbsDiff(actual: Bitmap, expected: Bitmap): Double {
        assertTrue(
            "尺寸不一致：基准 ${expected.width}x${expected.height}，实际 ${actual.width}x${actual.height}。" +
                "如布局有意变更，请用 -Pscreenshot.record=true 重新生成基准",
            expected.width == actual.width && expected.height == actual.height,
        )
        val actualPixels = IntArray(actual.width * actual.height).also {
            actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height)
        }
        val expectedPixels = IntArray(expected.width * expected.height).also {
            expected.getPixels(it, 0, expected.width, 0, 0, expected.width, expected.height)
        }
        var total = 0.0
        var count = 0L
        for (i in actualPixels.indices) {
            val a = actualPixels[i]
            val b = expectedPixels[i]
            val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
            val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            total += abs(dr) + abs(dg) + abs(db)
            count += 3
        }
        return if (count == 0L) 0.0 else total / count
    }

    private companion object {
        /**
         * 平均像素差容差（每通道 0-255）。NATIVE 模式下同机渲染应当逐位一致，
         * 少量容差只为吸收 PNG 往返的极小波动；设大会放过真实的视觉回归。
         */
        const val TOLERANCE = 2.0

        const val SNAPSHOT_TAG = "snapshot_target"
    }
}
