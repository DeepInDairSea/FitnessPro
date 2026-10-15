import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** 全工程统一的 SDK 与 JVM 目标。修改此处即全局生效。 */
object AppConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
}

internal val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17

/**
 * 让 convention plugin 的 Kotlin 源码能够读取 Version Catalog。
 *
 * 之所以不用 Gradle 生成的 `LibrariesForLibs` 访问器：该访问器只对 .gradle.kts 脚本可用，
 * 在 build-logic 的 src/main/kotlin 里无法解析。VersionCatalogsExtension 是 Gradle 公共 API，
 * 对同一次构建中的所有工程都可用，因此更稳。
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * 所有 Android 模块共用的 Kotlin 编译选项。
 */
internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.addAll(
                "kotlin.RequiresOptIn",
                "kotlin.ExperimentalStdlibApi",
            )
        }
    }
}
