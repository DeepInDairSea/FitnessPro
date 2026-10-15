plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.stats"
}

dependencies {
    // api：@HiltViewModel 的构造参数里出现 core:data 的类型（MaintenanceRepository 等），
    // KSP 处理 Hilt 注解时要求它们在 api 元素里可见
    api(projects.core.data)
    implementation(projects.core.media)
    implementation(libs.androidx.activity.compose)
}
