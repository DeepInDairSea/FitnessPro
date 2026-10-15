import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * 所有 :core:* 与 :feature:* 库模块的基础插件。
 * 只负责：应用 Android Library + Kotlin Android 插件，统一 SDK / Java / Kotlin 编译选项。
 * 依赖声明仍由各模块的 build.gradle.kts 负责，避免 convention plugin 与 Version Catalog 耦合。
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = AppConfig.COMPILE_SDK

            defaultConfig {
                minSdk = AppConfig.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }

            buildFeatures {
                buildConfig = false
            }

            packaging {
                resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/LICENSE*",
                )
            }

            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }

            lint {
                warningsAsErrors = false
                abortOnError = false
                checkDependencies = true
            }
        }

        configureKotlinAndroid()

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.add("-Xjvm-default=all")
            }
        }
    }
}
