plugins {
    id("fitnesspro.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.feature.exercises"
}

dependencies {
    implementation(libs.timber)
    // api 而非 implementation：ExerciseDetailViewModel 的构造参数里出现 core:media 的
    // MediaAccess，KSP 处理 @HiltViewModel 时要求这些类型在 api 元素里可见
    api(projects.core.data)
    api(projects.core.media)
    // 动作演示：关联面板 + 演示图渲染组件（与计划编辑页共用）
    implementation(projects.core.mediaui)
    implementation(libs.androidx.activity.compose)
}
