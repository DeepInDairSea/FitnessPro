plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.compose")
    id("fitnesspro.android.hilt")
}

android {
    namespace = "com.fitnesspro.core.mediaui"
}

dependencies {
    // 媒体 UI 是"装配层组件"：既要用媒体能力（导入/压缩/解析），
    // 又要用设计系统的间距与配色，因此同时依赖两侧。
    // 它被 feature 依赖；core:media 与 core:designsystem 互不依赖，无环。
    api(projects.core.model)
    api(projects.core.designsystem)
    api(projects.core.media)
    implementation(projects.core.common)
    implementation(projects.core.data)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // 视频播放：Media3（与 feature:workout 同一套依赖，版本由 version catalog 统一）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
