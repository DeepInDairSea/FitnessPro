plugins {
    id("fitnesspro.android.feature")
}

android {
    namespace = "com.fitnesspro.feature.home"
}

dependencies {
    // api：@HiltViewModel 的构造参数里出现 core:data 类型时，KSP 需要它在 api 元素里可见
    api(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
