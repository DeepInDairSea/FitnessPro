plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.core.common"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.core.ktx)
    implementation(libs.timber)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
