import java.util.Properties

plugins {
    id("fitnesspro.android.application")
    id("fitnesspro.android.compose")
    id("fitnesspro.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// release 签名配置外置在 keystore.properties（不进版本库）。
// 文件缺失时自动降级为"未签名产物"，保证 clone 下来仍能正常构建。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.fitnesspro"

    defaultConfig {
        applicationId = "com.fitnesspro"
        versionCode = 1
        versionName = "0.1.0"
    }

    val releaseSigning = if (keystorePropertiesFile.exists()) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        // Baseline Profile 生成在非最小化变体上进行（插件约定）
        create("benchmark") {
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
        // release 由 convention 插件创建：有签名配置时挂上，产出可直接安装的包
        if (releaseSigning != null) {
            getByName("release").signingConfig = releaseSigning
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    // 全屏演示播放页（视频/动图/图片）
    implementation(projects.core.mediaui)
    implementation(projects.core.datastore)
    implementation(projects.core.media)

    implementation(projects.feature.plans)
    implementation(projects.feature.exercises)
    implementation(projects.feature.workout)
    implementation(projects.feature.settings)
    implementation(projects.feature.history)
    implementation(projects.feature.stats)
    implementation(projects.feature.body)
    implementation(projects.feature.home)

    // Baseline Profile 采集模块：gradlew :app:generateBaselineProfile 时使用
    baselineProfile(projects.baselineprofile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    // OnboardingScreen 用 hiltViewModel()：app 模块此前全部页面都来自 feature，不需要它
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)

    // Coil 3：GIF / 动态 WebP / 视频封面帧
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// ---------------------------------------------------------------------------
// 开发提速：debug 构建没有 Baseline Profile，JIT 冷启动比 release 慢 1.5s 以上。
// 本任务 = 安装 debug 包 + 对其做 ART 全量 AOT 编译（cmd package compile），
// 之后冷启动可从 ~2.2s 降到 ~0.9s（实测 420dpi 模拟器）。
// 注意：每次重新安装（installDebug）后 AOT 缓存会失效，需要重跑本任务。
// 用法：gradlew :app:installFastDebug
// ---------------------------------------------------------------------------
tasks.register("installFastDebug") {
    group = "install"
    description = "安装 debug 包并做 ART AOT 编译，消除 debug 构建的 JIT 冷启动开销"
    dependsOn("installDebug")
    doLast {
        val adbExecutable = android.adbExecutable.absolutePath
        providers.exec {
            commandLine(adbExecutable, "shell", "cmd", "package", "compile", "-m", "speed", "-f", "com.fitnesspro.debug")
        }.result.get()
        providers.exec {
            commandLine(adbExecutable, "shell", "am", "start", "-W", "-n", "com.fitnesspro.debug/com.fitnesspro.MainActivity")
        }.result.get()
    }
}
