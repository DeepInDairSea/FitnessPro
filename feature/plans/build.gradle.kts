plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.plans"
}

dependencies {
    // api 而非 implementation：MediaAttachViewModel 的构造参数里出现 core:media 类型，
    // KSP 处理 @HiltViewModel 时要求这些类型在 api 元素里可见（否则报 "could not be resolved"）
    api(projects.core.data)
    api(projects.core.media)
    // 媒体关联面板与演示图组件（与动作库共用同一套实现）
    implementation(projects.core.mediaui)

    implementation(libs.timber)
}
