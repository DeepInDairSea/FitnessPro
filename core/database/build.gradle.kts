plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.hilt")
    id("fitnesspro.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.core.database"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.timber)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
