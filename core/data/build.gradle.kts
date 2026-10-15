plugins {
    id("fitnesspro.android.library")
    id("fitnesspro.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnesspro.core.data"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(projects.core.domain)

    // 以下三个用 implementation：避免数据库实体 / DataStore / 文件系统细节泄漏到 feature 层
    // （对应 DEV 文档 §2.2 依赖规则 R3）
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.media)

    // Room 的 withTransaction 需要 room-ktx。用 implementation 而非 api：
    // core:data 内部需要它来组合跨表事务，但 feature 层不应看到 Room API。
    implementation(libs.androidx.room.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.work.runtime.ktx)
    // HiltWorker：让后台任务也能注入 Repository（否则 Worker 只能自己 new 依赖）
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric + 内存 Room：让"新建计划→添加动作→保存"这条链路能在 JVM 上真实跑一遍。
    // 这些是纯数据层行为，用模拟器手点成本太高、且无法回归。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // api 而不是 implementation：feature:body 需要 PermissionController 的
    // ActivityResultContract 类型来发起权限请求
    api(libs.androidx.health.connect.client)
}
