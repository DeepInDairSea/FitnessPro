plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.hilt")
}

android {
    namespace = "com.fitnesspro.core.datastore"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    // SettingsRepository 接口定义在领域层（实现 datastore → domain 的依赖方向，无环）
    api(projects.core.domain)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
