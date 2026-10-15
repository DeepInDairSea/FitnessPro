plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.settings"
}

dependencies {
    // 设置页需要 MaintenanceRepository（存储维护）与 BackupExporter/Importer。
    // 这里用 api 而非 implementation：KSP 在处理 @HiltViewModel 的构造参数类型时，
    // 需要这些类型出现在 compileClasspath 的 api 元素里，仅 implementation 会导致
    // "xxx could not be resolved"。feature 是叶子模块，api 只向上影响到 :app，风险可控。
    api(projects.core.data)
    implementation(projects.core.media)
    implementation(libs.androidx.activity.compose)
}
