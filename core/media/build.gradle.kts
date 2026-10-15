plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.hilt")
}

android {
    namespace = "com.fitnesspro.core.media"
}

dependencies {
    // 媒体层只依赖模型与通用工具。刻意不依赖 :core:domain 与 :core:database：
    // 它应当是"纯粹的 Android 文件/编解码能力"，与业务规则、持久化解耦，
    // 这样 GC 的引用判定逻辑才会被逼到 :core:data 里（那里才有 DAO），避免职责错位。
    api(projects.core.model)
    api(projects.core.common)

    implementation(libs.androidx.core.ktx)
    // ActivityResultContracts.PickVisualMedia（系统 Photo Picker）
    implementation(libs.androidx.activity.compose)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
