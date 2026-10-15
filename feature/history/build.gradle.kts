plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.history"
}

dependencies {
    // api：@HiltViewModel 的构造参数里出现 core:data 的 WorkoutSessionManager，
    // KSP 处理 Hilt 注解时要求该类型在 api 元素里可见（implementation 会报
    // "xxx could not be resolved"）
    api(projects.core.data)
    implementation(projects.core.media)
    implementation(libs.androidx.activity.compose)
    implementation(libs.timber)
}
