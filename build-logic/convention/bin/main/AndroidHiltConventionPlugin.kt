import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            "implementation"(libs.findLibrary("hilt-android").get())
            "ksp"(libs.findLibrary("hilt-compiler").get())
            // WorkManager 的 @HiltWorker 支持（没有 Worker 的模块也没有副作用）
            "implementation"(libs.findLibrary("androidx-hilt-work").get())
            "ksp"(libs.findLibrary("androidx-hilt-compiler").get())
        }
    }
}
