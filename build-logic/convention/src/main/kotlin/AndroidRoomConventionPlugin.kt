import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room 模块：应用 KSP、注入 Room 依赖、开启 schema 导出。
 * schema JSON 会输出到 <module>/schemas 并纳入版本控制（迁移测试依赖它）。
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        extensions.configure<KspExtension> {
            arg("room.generateKotlin", "true")
        }

        // 让 MigrationTestHelper 能在 androidTest 中读到历史 schema
        extensions.configure<LibraryExtension> {
            sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
        }

        dependencies {
            "implementation"(libs.findLibrary("androidx-room-runtime").get())
            "implementation"(libs.findLibrary("androidx-room-ktx").get())
            "ksp"(libs.findLibrary("androidx-room-compiler").get())
            "androidTestImplementation"(libs.findLibrary("androidx-room-testing").get())
        }
    }
}
