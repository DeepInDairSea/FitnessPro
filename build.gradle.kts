plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.detekt) apply false
}

/**
 * 模块依赖规则校验（DEV 文档 §2.2 规则 R1）：
 *  - core:* 不得依赖 feature:*
 *  - feature:* 不得互相依赖
 * 由 CI 在每次构建时强制执行，本地也可手动运行 ./gradlew moduleDependencyCheck
 */
tasks.register("moduleDependencyCheck") {
    group = "verification"
    description = "校验模块依赖规则：core 不依赖 feature，feature 间不互相依赖"
    doLast {
        val coreRoot = file("core")
        val featureRoot = file("feature")
        val featureDep = Regex("""projects\.feature\.([A-Za-z0-9_]+)""")

        coreRoot.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            val f = File(dir, "build.gradle.kts")
            if (f.exists() && f.readText().contains("projects.feature.")) {
                throw GradleException("core:${dir.name} 依赖了 feature 模块，违反规则 R1")
            }
        }

        featureRoot.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            val f = File(dir, "build.gradle.kts")
            if (!f.exists()) return@forEach
            featureDep.findAll(f.readText()).forEach { m ->
                val target = m.groupValues[1]
                if (target != dir.name) {
                    throw GradleException("feature:${dir.name} 依赖了 feature:$target，feature 间禁止互相依赖")
                }
            }
        }
        println("moduleDependencyCheck: 依赖规则通过")
    }
}

// 注：Detekt 等静态分析在 CI 中按需启用，本地构建不强制，以免拖慢日常开发。
// 如需在 CI 启用，可在流水线中对各模块 apply io.gitlab.arturbosch.detekt 插件并指定 config/detekt/detekt.yml。
