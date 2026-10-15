plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.body"
}

dependencies {
    // api：@HiltViewModel 的构造参数里出现 core:data 类型时，KSP 需要它在 api 元素里可见
    api(projects.core.data)
    implementation(projects.core.media)
    implementation(libs.androidx.activity.compose)
}
