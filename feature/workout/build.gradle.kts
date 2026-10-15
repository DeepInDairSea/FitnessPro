plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.workout"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.media)
    // 训练执行页展示动作演示图
    implementation(projects.core.mediaui)

    implementation(libs.androidx.activity.compose)
    // FileProvider：分享训练卡片时把 cache/share/ 的 PNG 暴露给接收方
    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
