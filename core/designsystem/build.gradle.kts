plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.compose")
}

android {
    namespace = "com.fitnesspro.core.designsystem"

    testOptions {
        unitTests {
            all { test ->
                // Roborazzi 通过该系统属性判断是否处于 NATIVE 图形模式（@GraphicsMode
                // 注解在 AndroidJUnit4 包装下不总会落到系统属性上），缺了会静默跳过抓图
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                // captureRoboImage 只在 record/verify/compare 任务类型下才真正写文件
                // （官方插件任务会设置 roborazzi.test.record；这里常驻 record，
                //   验证交给测试内的像素比对而不是 Roborazzi 的 verify 流程）
                test.systemProperty("roborazzi.test.record", "true")
                // 基准重生成开关：-P 传递（-D 系统属性到不了 fork 出的测试 JVM）
                test.systemProperty(
                    "screenshot.record",
                    providers.gradleProperty("screenshot.record").orElse("false").get(),
                )
            }
        }
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    implementation(libs.androidx.lifecycle.runtime.compose)
    // 头像解码需要 EXIF 方向修正（Photo Picker 的图常带旋转标记），FileProvider 分享需要 core-ktx
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)

    // 截图测试基线：Robolectric NATIVE 图形模式下真实渲染 Compose 组件并抓位图，
    // 与 src/test/snapshots/ 下的基准图逐像素比对。不需要设备，PR 上即可回归视觉。
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    // Roborazzi：在 Robolectric NATIVE 图形下抓取渲染位图。
    // 原生 captureToImage 在 Robolectric 上依赖 PixelCopy + 硬件渲染配置，帧同步不稳；
    // Roborazzi 封装了整套渲染同步，是该场景的事实标准。
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
