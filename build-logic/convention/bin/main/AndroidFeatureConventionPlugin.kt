import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * feature 模块统一依赖。
 * 目的：强制 feature 之间的边界 —— 只允许依赖 :core:*，不允许依赖其他 :feature:*。
 * （跨特性跳转通过 :core:common 中的导航路由对象完成，见 DEV 文档 §2.2 规则 R1）
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("fitnesspro.android.library")
        pluginManager.apply("fitnesspro.android.compose")
        pluginManager.apply("fitnesspro.android.hilt")

        dependencies {
            "implementation"(project(":core:common"))
            "implementation"(project(":core:model"))
            "implementation"(project(":core:designsystem"))
            "implementation"(project(":core:domain"))

            "implementation"(libs.findLibrary("androidx-navigation-compose").get())
            "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())
            "implementation"(libs.findLibrary("kotlinx-collections-immutable").get())

            "testImplementation"(libs.findLibrary("robolectric").get())
        }
    }
}
