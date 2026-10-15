# 健身记录 App 开发技术文档（Technical Design Document）

**版本**：v1.0
**对应需求**：`docs/PRD_v2.0_健身记录App.md`
**目标读者**：Android 开发、测试、技术负责人
**状态**：可据此排期与开工

---

## 0. 文档说明

### 0.1 本文档与 PRD 的关系

| 文档 | 回答的问题 | 变更频率 |
|------|-----------|---------|
| PRD v2.0 | **做什么**（功能、规则、验收标准） | 低（需求评审后冻结） |
| 本文档 | **怎么做**（架构、模块、接口、算法、任务拆解） | 中（每个迭代更新） |
| ADR（§18） | **为什么这么做**（关键决策与取舍记录） | 只增不改 |

需求冲突时以 PRD 为准；实现细节冲突时以本文档 + ADR 为准。

### 0.2 交付物清单

| # | 交付物 | 说明 |
|---|--------|------|
| D1 | 可编译运行的多模块工程 | §2 |
| D2 | 数据层（Room 实体/DAO/迁移/种子） | §4 |
| D3 | 领域层（模型/计算器/UseCase） | §5 |
| D4 | 媒体层（导入/缩略图/转码/GC） | §6 |
| D5 | UI 层（导航/页面/设计系统） | §7 |
| D6 | 训练执行引擎（会话/休息计时/断点恢复） | §8 |
| D7 | 后台任务、通知、权限 | §9 / §10 |
| D8 | 备份导出导入 | §12 |
| D9 | 测试工程与 CI | §14 / §16 |
| D10 | WBS 与里程碑 | §17 |

### 0.3 约定

- **时间**：所有持久化时间戳为 UTC epoch millis（`Long`）；日期语义用 `epochDay`（`Long`）。
- **重量**：数据库内部统一 **kg**（`Double`），保留 2 位小数；用户输入原值另存 `displayWeight`/`displayUnit`。
- **命名**：数据库表 `snake_case` 复数；Kotlin 类 `PascalCase`；Compose 组件 `XxxScreen` / `XxxSection` / `XxxItem`。
- **禁用清单**（CI 静态检查拦截）：`fallbackToDestructiveMigration()`、`GlobalScope`、`runBlocking`（除 `main()` 与测试）、`!!`（除已校验的 `findViewById` 场景，本项目 Compose 无此场景）、硬编码用户可见文案、Android 依赖出现在 `core:model`。

---

## 1. 技术栈基线

### 1.1 版本锁定原则

PRD 已明确不预填未经验证的版本号。本文档给出**参考基线**，实际以启动日核实的最新稳定版为准，并**一次性写入 `gradle/libs.versions.toml`**，全工程禁止散落版本号。

**启动日核实清单（Checklist）**

1. 打开 <https://developer.android.com/jetpack/androidx/versions> 与各库官方 Release 页
2. 确认 AGP ↔ Gradle ↔ Kotlin ↔ KSP 四者兼容矩阵
3. 确认 Compose Compiler 由 Kotlin 插件提供（Kotlin 2.0+ 后为 `org.jetbrains.kotlin.plugin.compose`），**不需要单独指定 compiler 版本**
4. 确认 `targetSdk` 满足商店上架要求（2026 年应 ≥ 36）
5. 把最终版本号写入本文档 §1.2 表格并记录核实日期

### 1.2 技术栈表（参考基线）

| 类别 | 组件 | 用途 | 备注 |
|------|------|------|------|
| 构建 | Gradle + AGP | 构建 | Kotlin DSL，启用 configuration cache |
| 构建 | Version Catalog | 依赖集中管理 | `gradle/libs.versions.toml` |
| 构建 | Convention Plugins | 模块构建逻辑复用 | `build-logic/` |
| 语言 | Kotlin | — | 启用 `-Xjvm-default=all`、显式 API 可选 |
| 编译期 | KSP | Room / Hilt 注解处理 | **禁用 kapt** |
| UI | Jetpack Compose + Material 3 | 声明式 UI | 版本由 BOM 统一 |
| 导航 | Navigation Compose | 类型安全路由 | `@Serializable` 路由对象 |
| 架构 | MVVM + UseCase + Repository | 分层 | UI 只依赖 ViewModel |
| DI | Hilt | 依赖注入 | `@HiltViewModel`、`hilt-navigation-compose` |
| 异步 | Coroutines + Flow | 并发 | `StateFlow` 驱动 UI |
| 数据库 | Room | 本地存储 | `exportSchema = true` |
| 全文检索 | Room FTS4 | 动作/计划搜索 | `@Fts4` |
| 偏好 | DataStore (Preferences) | 设置项 | 单位、主题、计时默认值 |
| 后台 | WorkManager | 清理/提醒/导入导出 | 唯一后台调度入口（除精确提醒） |
| 媒体加载 | Coil 3 + `coil-gif` | GIF/WebP/静态图 | 只加载预生成缩略图 |
| 媒体播放/转码 | Media3（ExoPlayer / Transformer / Session） | 播放、逐帧、倍速、压缩 | 音频焦点由 Media3 管理 |
| 序列化 | kotlinx.serialization | 备份 JSON | 显式 `schemaVersion` |
| 图表 | Vico 或 Compose Canvas 自绘 | 趋势/饼图/热力图 | 优先自绘以减小体积 |
| 日期时间 | `java.time`（API 26+） | — | 无需 desugar |
| 日志 | Timber | 分级日志 | Release 仅 W/E |
| 静态检查 | Detekt + Ktlint + Android Lint | CI 门禁 | `warningsAsErrors` |
| 测试 | JUnit4 + Turbine + MockK + Robolectric + Compose UI Test + Roborazzi + Macrobenchmark | — | 见 §14 |

### 1.3 关键依赖取舍（ADR 摘要，详见 §18）

| 决策 | 选择 | 理由 |
|------|------|------|
| Room 2.x vs 3.x | **2.x 最新稳定版** | 3.x 主要面向 KMP；本项目纯 Android，无跨平台诉求，稳定优先 |
| 图表库 vs 自绘 | **自绘（Canvas）为主** | 减少 APK 体积与依赖风险；图表类型有限（折线/饼/条形/热力） |
| 数据库加密 | **v1.2 可选 SQLCipher** | v1.0 不引入，避免拖慢迭代；身体数据敏感度在本地沙箱内可接受 |
| 精确提醒 | **AlarmManager + SCHEDULE_EXACT_ALARM** | 训练提醒需要准点；被拒则降级 WorkManager 非精确 |
| 休息计时承载 | **普通通知（系统 Chronometer 倒计时）+ `AlarmManager.setAndAllowWhileIdle`** | 零权限、零后台耗电、无 Play 审核风险、进程被杀仍能到点提醒；详见 ADR-06 与 §8.3 |

---

## 2. 工程结构与构建

### 2.1 目录结构

```
FitnessPro/
├── build-logic/
│   ├── convention/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/
│   │       ├── AndroidApplicationConventionPlugin.kt
│   │       ├── AndroidLibraryConventionPlugin.kt
│   │       ├── AndroidFeatureConventionPlugin.kt
│   │       ├── AndroidComposeConventionPlugin.kt
│   │       ├── AndroidHiltConventionPlugin.kt
│   │       └── AndroidRoomConventionPlugin.kt
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── core/
│   ├── common/            # 工具、Result、Dispatcher 限定符、时间提供者
│   ├── model/             # 纯 Kotlin 领域模型 + 枚举（零 Android 依赖）
│   ├── designsystem/      # 主题、色板、排版、通用组件、图标
│   ├── database/          # Room 实体/DAO/Database/迁移/种子
│   ├── datastore/         # 偏好定义与读写
│   ├── domain/            # UseCase + 计算器（纯 Kotlin）
│   ├── media/             # 媒体导入/缩略图/转码/GC
│   ├── data/              # Repository 实现（桥接 database/datastore/media）
│   └── testing/           # 测试工具、Fake、数据构造器
├── feature/
│   ├── onboarding/
│   ├── home/              # 首页「今日」
│   ├── plans/
│   ├── exercises/
│   ├── workout/           # 训练执行 + 休息计时 UI
│   ├── history/
│   ├── stats/
│   ├── body/
│   └── settings/
├── app/                   # Application、MainActivity、NavHost、DI 聚合
├── benchmark/             # Macrobenchmark + BaselineProfile
├── docs/                  # 本 PRD 与技术文档
└── AGENTS.md              # AI 协作约定（见 PRD §12.1）
```

### 2.2 模块依赖图

```
                    ┌───────────────┐
                    │     :app      │
                    └───────┬───────┘
        ┌───────────┬───────┼───────┬───────────┐
        ▼           ▼       ▼       ▼           ▼
  :feature:*   :feature:*  ...  :feature:*  :feature:*      （禁止互相依赖）
        │           │       │       │           │
        └───────────┴───────┴───────┴───────────┘
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
   :core:data        :core:designsystem   :core:common
        │                   │                    │
   ┌────┼────┬────┐          │                    │
   ▼    ▼    ▼    ▼          ▼                    ▼
:core: :core: :core: :core: (仅依赖 common)   (无依赖)
domain database datastore media
   │       │      │      │
   └───────┴──────┴──────┘
              │
        :core:model  ← 零依赖，被所有模块依赖
```

**依赖规则（CI 用 `module-dependency-check` 或自写 Gradle 校验任务强制）**

| 规则 | 说明 |
|------|------|
| R1 | `feature:*` 之间**禁止**互相依赖；跨特性跳转只能通过导航路由（`@Serializable` 路由对象定义在 `:core:common` 的 `navigation` 包） |
| R2 | `:core:model`、`:core:domain` 必须是纯 Kotlin（`java-library` 或 `kotlin-jvm` 或 android-library 但无 android 依赖），保证可纯 JVM 单测 |
| R3 | UI 模块**禁止**直接依赖 `:core:database`，只能依赖 `:core:data` |
| R4 | `:app` 只做装配，禁止写业务逻辑 |
| R5 | 任何模块依赖 `:core:model` 是允许的；依赖 `:core:testing` 仅限 `testImplementation`/`androidTestImplementation` |

### 2.3 Version Catalog（节选）

```toml
# gradle/libs.versions.toml
[versions]
agp = "<核实后锁定>"
kotlin = "<核实后锁定>"
ksp = "<与 kotlin 对齐>"
composeBom = "<核实后锁定>"
room = "<核实后锁定>"
hilt = "<核实后锁定>"
media3 = "<核实后锁定>"
coil = "<核实后锁定>"
work = "<核实后锁定>"
datastore = "<核实后锁定>"
navCompose = "<核实后锁定>"
serialization = "<核实后锁定>"
coroutines = "<核实后锁定>"
timber = "<核实后锁定>"
turbine = "<核实后锁定>"
mockk = "<核实后锁定>"
roborazzi = "<核实后锁定>"
detekt = "<核实后锁定>"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-datastore-prefs = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navCompose" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui-compose = { group = "androidx.media3", name = "media3-ui-compose", version.ref = "media3" }
media3-transformer = { group = "androidx.media3", name = "media3-transformer", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-gif = { group = "io.coil-kt.coil3", name = "coil-gif", version.ref = "coil" }
coil-video = { group = "io.coil-kt.coil3", name = "coil-video", version.ref = "coil" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 2.4 Convention Plugin 示例

```kotlin
// build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("org.jetbrains.kotlin.android")
        }
        extensions.configure<LibraryExtension> {
            compileSdk = AppConfig.COMPILE_SDK
            defaultConfig { minSdk = AppConfig.MIN_SDK }
            buildFeatures { buildConfig = false }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            // Room schema 输出目录统一
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        dependencies {
            add("implementation", libs.findLibrary("timber").get())
            add("testImplementation", libs.findLibrary("turbine").get())
        }
    }
}
```

### 2.5 关键 Gradle 配置

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
android.defaults.buildfeatures.buildconfig=false
ksp.incremental=true
```

```kotlin
// app/build.gradle.kts（要点）
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // R8 full mode（AGP 8+ 默认）
        }
    }
    // 依赖方向校验：开启 room schema 目录
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

---

## 3. 架构设计

### 3.1 分层与数据流

```
┌──────────────────────── UI 层（feature:*） ─────────────────────────┐
│  XxxScreen(uiState, onAction)        无状态、可预览                  │
│        ▲                    │                                        │
│  StateFlow<XxxUiState>   XxxAction (sealed interface)                │
│        │                    ▼                                        │
│  XxxViewModel  ──── 事件转 UseCase 调用 ────►                        │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
┌──────────────── 领域层（:core:domain）──────────────────────────────┐
│  UseCase（单一职责，operator fun invoke = 可读性）                    │
│  Calculator（纯函数：Volume / Pr / OneRm / Duration / Unit）          │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
┌──────────────── 数据层（:core:data）────────────────────────────────┐
│  Repository 接口 + 实现（唯一数据入口）                               │
│      ├── :core:database  DAO（Flow 查询 + suspend 写入）              │
│      ├── :core:datastore 偏好（Flow）                                 │
│      └── :core:media     文件系统                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 UiState 模式（全工程统一）

```kotlin
// 所有 feature 的 UiState 必须满足
@Immutable
data class PlansUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val plans: ImmutableList<PlanCardUiModel> = persistentListOf(),
    val folders: ImmutableList<FolderUiModel> = persistentListOf(),
    val error: UiError? = null,          // 一次性错误，消费后置 null
)

sealed interface PlansAction {
    data class OnQueryChange(val query: String) : PlansAction
    data class OnPlanClick(val id: Long) : PlansAction
    data object OnRetryClick : PlansAction
}

// 副作用（导航/Toast）用 Channel，不进 UiState
sealed interface PlansEffect {
    data class NavigateToDetail(val id: Long) : PlansEffect
    data class ShowMessage(val resId: Int) : PlansEffect
}
```

**强制规则**
- UiState 必须 `@Immutable`，集合用 `kotlinx.collections.immutable` 的 `ImmutableList`
- 一次性事件（导航、Snackbar）用 `Channel<Effect>` + `receiveAsFlow()`，**不放在 UiState 里**（避免旋转重复触发）
- ViewModel 中禁止出现 `Context`（需要资源用 `Application` 持有 + 通过 `UiText` 包装）

### 3.3 错误处理模式

```kotlin
// :core:common
sealed interface UiError {
    val code: String                 // 用于日志与埋点，稳定不变
    data class Message(val resId: Int, val args: List<Any> = emptyList()) : UiError { override val code = "MSG" }
    data class Unknown(val throwable: Throwable) : UiError { override val code = "UNKNOWN" }
}

// Repository 层统一返回
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

sealed interface DomainError {
    object DatabaseFailure : DomainError
    object MediaFileMissing : DomainError
    object InsufficientStorage : DomainError
    object PermissionDenied : DomainError
    data class ImportFailure(val code: String) : DomainError
    data class Unknown(val cause: Throwable) : DomainError
}
```

- Repository **不抛异常**（除编程错误），统一返回 `Outcome`
- ViewModel 把 `DomainError` 映射为 `UiError`（资源 id 在这里绑定，保证可测试性）
- 所有 `DomainError` → 用户文案的映射集中在 `:core:designsystem` 的 `errorMessages.xml`，保证文案一致

### 3.4 依赖注入模块划分

```kotlin
// :core:database
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FitnessDatabase =
        Room.databaseBuilder(ctx, FitnessDatabase::class.java, "fitness.db")
            .addMigrations(*ALL_MIGRATIONS)     // 见 §4.6
            .addCallback(SeedCallback())        // 首次启动导入动作库
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun providePlanDao(db: FitnessDatabase) = db.planDao()
    // ...
}

// :core:common
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

@Module @InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
}

// :core:common —— 时间抽象（让时间相关逻辑可测试）
interface TimeProvider {
    fun nowEpochMs(): Long
    fun elapsedRealtimeMs(): Long
    fun todayEpochDay(): Long
}
```

> `TimeProvider` 是本项目**必须**的抽象：休息计时器、PR 判定、训练时长、连续打卡全部依赖时间，不抽象则无法写确定性测试。

---

## 4. 数据层设计

### 4.1 数据库总览

```kotlin
@Database(
    entities = [
        TrainingPlan::class, PlanExercise::class, PlanSet::class, PlanFolder::class,
        ExerciseDefinition::class, ExerciseMuscle::class, ExerciseFts::class,
        WorkoutSession::class, WorkoutExercise::class, WorkoutSet::class, WorkoutSessionState::class,
        PersonalRecord::class, BodyMeasurement::class, MediaAsset::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun statsDao(): StatsDao
    abstract fun prDao(): PersonalRecordDao
    abstract fun bodyDao(): BodyMeasurementDao
    abstract fun mediaDao(): MediaAssetDao
}
```

**全局约定**
- `@ForeignKey` 必须显式声明 `onDelete`（Room 默认 `NO_ACTION`，会导致删除失败）
- 所有外键列必须有索引（Room 编译告警）
- 所有 `@Query` 返回 `Flow<T>`（列表/详情）或 `suspend fun`（单次读/写）
- 写入方法返回 `Long`/`Int` 便于断言

### 4.2 建表 DDL（用于迁移与人工核查）

```sql
CREATE TABLE training_plan (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL, description TEXT, folder_id INTEGER, color_hex TEXT,
  default_rest_seconds INTEGER, estimated_duration_seconds INTEGER,
  is_favorite INTEGER NOT NULL DEFAULT 0, is_archived INTEGER NOT NULL DEFAULT 0,
  sort_order REAL NOT NULL DEFAULT 0.0, scheduled_days TEXT,
  last_performed_at INTEGER, deleted_at INTEGER,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  FOREIGN KEY(folder_id) REFERENCES plan_folder(id) ON DELETE SET NULL
);
CREATE INDEX idx_training_plan_folder_id ON training_plan(folder_id);
CREATE INDEX idx_training_plan_deleted_at ON training_plan(deleted_at);
CREATE INDEX idx_training_plan_updated_at ON training_plan(updated_at);

CREATE TABLE plan_exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  plan_id INTEGER NOT NULL,
  exercise_definition_id INTEGER,
  name_snapshot TEXT NOT NULL, type_snapshot TEXT NOT NULL,
  order_index REAL NOT NULL, superset_tag INTEGER,
  target_sets INTEGER NOT NULL DEFAULT 3, target_reps TEXT,
  target_weight REAL, target_duration_seconds INTEGER, target_distance_meters REAL,
  target_rpe REAL, rest_seconds_override INTEGER, tempo TEXT, notes TEXT,
  media_type TEXT, media_path TEXT, thumbnail_path TEXT,
  FOREIGN KEY(plan_id) REFERENCES training_plan(id) ON DELETE CASCADE,
  FOREIGN KEY(exercise_definition_id) REFERENCES exercise_definition(id) ON DELETE RESTRICT
);
CREATE INDEX idx_plan_exercise_plan_id ON plan_exercise(plan_id);
CREATE UNIQUE INDEX idx_plan_exercise_order ON plan_exercise(plan_id, order_index);

CREATE TABLE plan_set (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  plan_exercise_id INTEGER NOT NULL, set_index INTEGER NOT NULL,
  set_type TEXT NOT NULL DEFAULT 'NORMAL',
  target_reps TEXT, target_weight REAL, target_duration_seconds INTEGER,
  target_rpe REAL, is_unilateral_split INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(plan_exercise_id) REFERENCES plan_exercise(id) ON DELETE CASCADE
);
CREATE INDEX idx_plan_set_pe ON plan_set(plan_exercise_id);
CREATE UNIQUE INDEX idx_plan_set_order ON plan_set(plan_exercise_id, set_index);

CREATE TABLE workout_session (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  plan_id INTEGER, plan_name_snapshot TEXT NOT NULL, status TEXT NOT NULL,
  started_at INTEGER NOT NULL, completed_at INTEGER,
  paused_total_seconds INTEGER NOT NULL DEFAULT 0, total_duration_seconds INTEGER NOT NULL DEFAULT 0,
  total_volume_kg REAL NOT NULL DEFAULT 0, total_sets INTEGER NOT NULL DEFAULT 0,
  total_reps INTEGER NOT NULL DEFAULT 0, body_weight_kg_snapshot REAL,
  perceived_effort INTEGER, mood TEXT, notes TEXT,
  is_deleted INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  FOREIGN KEY(plan_id) REFERENCES training_plan(id) ON DELETE SET NULL
);
CREATE INDEX idx_ws_plan ON workout_session(plan_id);
CREATE INDEX idx_ws_started ON workout_session(started_at);
CREATE INDEX idx_ws_status ON workout_session(status);

CREATE TABLE workout_exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  session_id INTEGER NOT NULL, exercise_definition_id INTEGER,
  name_snapshot TEXT NOT NULL, type_snapshot TEXT NOT NULL,
  order_index REAL NOT NULL, superset_tag INTEGER,
  rest_seconds INTEGER NOT NULL DEFAULT 90, notes TEXT, status TEXT NOT NULL,
  media_type TEXT NOT NULL DEFAULT 'NONE', media_path TEXT,
  is_adhoc INTEGER NOT NULL DEFAULT 0, skipped_at INTEGER,
  FOREIGN KEY(session_id) REFERENCES workout_session(id) ON DELETE CASCADE,
  FOREIGN KEY(exercise_definition_id) REFERENCES exercise_definition(id) ON DELETE SET NULL
);
CREATE INDEX idx_we_session ON workout_exercise(session_id);
CREATE UNIQUE INDEX idx_we_order ON workout_exercise(session_id, order_index);

CREATE TABLE workout_set (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  workout_exercise_id INTEGER NOT NULL, set_index INTEGER NOT NULL,
  set_type TEXT NOT NULL DEFAULT 'NORMAL', status TEXT NOT NULL DEFAULT 'PENDING',
  weight_kg REAL, display_weight REAL, display_unit TEXT,
  reps INTEGER, duration_seconds INTEGER, distance_meters REAL,
  left_reps INTEGER, right_reps INTEGER, rpe REAL, rest_taken_seconds INTEGER,
  is_personal_record INTEGER NOT NULL DEFAULT 0, pr_type TEXT,
  plan_set_id INTEGER, target_weight_kg REAL, target_reps TEXT,
  completed_at INTEGER, notes TEXT,
  FOREIGN KEY(workout_exercise_id) REFERENCES workout_exercise(id) ON DELETE CASCADE
);
CREATE INDEX idx_wset_we ON workout_set(workout_exercise_id);
CREATE UNIQUE INDEX idx_wset_order ON workout_set(workout_exercise_id, set_index);

CREATE TABLE personal_record (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  exercise_definition_id INTEGER NOT NULL, pr_type TEXT NOT NULL,
  value REAL NOT NULL, unit TEXT NOT NULL, reps INTEGER, weight_kg REAL,
  achieved_at INTEGER NOT NULL, session_id INTEGER, set_id INTEGER,
  FOREIGN KEY(exercise_definition_id) REFERENCES exercise_definition(id) ON DELETE CASCADE
);
CREATE INDEX idx_pr_ex ON personal_record(exercise_definition_id);
CREATE UNIQUE INDEX idx_pr_ex_type ON personal_record(exercise_definition_id, pr_type);

-- exercise_fts（FTS4，外部内容表）
CREATE VIRTUAL TABLE IF NOT EXISTS exercise_fts USING FTS4(
  name, aliases, content=`exercise_definition`
);
```

### 4.3 DAO 关键接口

```kotlin
@Dao
interface ExerciseDao {

    @Query("""
        SELECT * FROM exercise_definition
        WHERE is_archived = 0
        ORDER BY usage_count DESC, name ASC
        LIMIT :limit
    """)
    fun observeRecentlyUsed(limit: Int = 20): Flow<List<ExerciseDefinition>>

    @Query("""
        SELECT e.* FROM exercise_definition e
        WHERE e.is_archived = 0
          AND e.rowid IN (SELECT rowid FROM exercise_fts WHERE exercise_fts MATCH :ftsQuery)
        ORDER BY e.usage_count DESC
        LIMIT :limit
    """)
    fun search(ftsQuery: String, limit: Int = 50): Flow<List<ExerciseDefinition>>

    @Query("""
        SELECT e.* FROM exercise_definition e
        WHERE e.is_archived = 0
          AND (:muscle IS NULL OR e.primary_muscle = :muscle)
          AND (:equipment IS NULL OR e.equipment = :equipment)
        ORDER BY e.name ASC
    """)
    fun filterBy(muscle: String?, equipment: String?): Flow<List<ExerciseDefinition>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseDefinition>): List<Long>

    @Query("UPDATE exercise_definition SET usage_count = usage_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun bumpUsage(id: Long, now: Long)
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("""
        SELECT * FROM workout_session
        WHERE status = 'IN_PROGRESS' AND is_deleted = 0
        ORDER BY started_at DESC LIMIT 1
    """)
    fun observeActiveSession(): Flow<WorkoutSessionWithExercises?>

    @Transaction
    suspend fun createSessionWithExercises(
        session: WorkoutSession,
        exercises: List<WorkoutExercise>,
        sets: List<WorkoutSet>,
    ): Long { /* 默认实现组合 @Insert */ }

    @Query("""
        SELECT ws.* FROM workout_set ws
        JOIN workout_exercise we ON ws.workout_exercise_id = we.id
        WHERE we.exercise_definition_id = :exerciseId AND ws.status = 'COMPLETED'
        ORDER BY ws.completed_at DESC LIMIT 1
    """)
    suspend fun latestCompletedSet(exerciseId: Long): WorkoutSet?

    @Query("""
        SELECT we.id AS exerciseId, ws.reps, ws.weight_kg AS weightKg, ws.set_index AS setIndex
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workout_exercise_id = we.id
        JOIN workout_session s ON we.session_id = s.id
        WHERE we.exercise_definition_id = :exerciseId
          AND s.id = (
            SELECT s2.id FROM workout_session s2
            JOIN workout_exercise we2 ON we2.session_id = s2.id
            WHERE we2.exercise_definition_id = :exerciseId AND s2.status = 'COMPLETED'
            ORDER BY s2.started_at DESC LIMIT 1
          )
        ORDER BY ws.set_index ASC
    """)
    suspend fun previousSessionSets(exerciseId: Long): List<PreviousSetRow>

    @Query("""
        UPDATE workout_session
        SET status = :status, completed_at = :completedAt,
            total_duration_seconds = :duration, total_volume_kg = :volume,
            total_sets = :sets, total_reps = :reps, updated_at = :now
        WHERE id = :sessionId
    """)
    suspend fun finalizeSession(
        sessionId: Long, status: String, completedAt: Long,
        duration: Int, volume: Double, sets: Int, reps: Int, now: Long,
    )
}

@Dao
interface StatsDao {
    // 容量趋势：按周聚合
    @Query("""
        SELECT (started_at / 604800000) AS bucket,
               SUM(total_volume_kg) AS volume,
               COUNT(*) AS sessionCount
        FROM workout_session
        WHERE status = 'COMPLETED' AND is_deleted = 0 AND started_at >= :sinceEpochMs
        GROUP BY bucket ORDER BY bucket ASC
    """)
    fun weeklyVolume(sinceEpochMs: Long): Flow<List<VolumeBucket>>

    // 肌群分布：按主要肌群聚合容量
    @Query("""
        SELECT e.primary_muscle AS muscle,
               SUM(CASE WHEN ws.set_type = 'WARMUP' THEN 0
                        ELSE COALESCE(ws.weight_kg, 0) * COALESCE(ws.reps, 0) END) AS volume,
               COUNT(DISTINCT ws.id) AS setCount
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workout_exercise_id = we.id
        JOIN exercise_definition e ON we.exercise_definition_id = e.id
        JOIN workout_session s ON we.session_id = s.id
        WHERE s.status = 'COMPLETED' AND s.is_deleted = 0
          AND ws.status = 'COMPLETED' AND s.started_at >= :sinceEpochMs
        GROUP BY e.primary_muscle
        ORDER BY volume DESC
    """)
    fun muscleDistribution(sinceEpochMs: Long): Flow<List<MuscleVolume>>

    // 训练日历热力图数据
    @Query("""
        SELECT (started_at / 86400000) AS epochDay, COUNT(*) AS count
        FROM workout_session
        WHERE status = 'COMPLETED' AND is_deleted = 0
        GROUP BY epochDay ORDER BY epochDay ASC
    """)
    fun calendarHeatmap(): Flow<List<DayCount>>
}
```

> **注意**：SQL 中 `604800000`（7天）与 `86400000`（1天）的 bucket 计算使用 UTC 边界，日/周边界与用户时区可能不一致。生产实现应在**应用层**用 `TimeProvider.todayEpochDay()` 与 `ZoneId` 计算边界后传入 `:startEpochDay` / `:endEpochDay` 参数，避免夏令时与跨时区错误（见 §5.4）。

### 4.4 FTS 实体与同步

```kotlin
@Fts4(contentEntity = ExerciseDefinition::class)
@Entity(tableName = "exercise_fts")
data class ExerciseFts(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "aliases") val aliases: String?,
)
```

- FTS 表由 SQLite 触发器自动同步（Room 为 `contentEntity` 自动创建触发器）
- 用户输入搜索词需转义为 FTS 安全查询串（见 §5.6 `FtsQueryBuilder`）

### 4.5 种子数据导入

```kotlin
// :core:database —— 首次创建数据库时导入
class SeedCallback @Inject constructor(
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // onCreate 在主线程且事务内，禁止在此直接做重活
        // 方案：写入一个标记，由 Application 启动后通过 WorkManager 导入
        db.execSQL("INSERT INTO seed_flag(key, done) VALUES('exercise_seed', 0)")
    }
}
```

**采用方案**：Application 启动时检查 `seed_flag` 与 `exercise_definition` 是否为空 → 为空则在 `WorkManager` 一次性任务中导入 `assets/exercise_seed.json`（单事务批量插入，≥300 条）。

```kotlin
@Serializable
data class SeedExercise(
    val name: String,
    val aliases: List<String> = emptyList(),
    val type: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = 90,
    val defaultSets: Int = 3,
    val defaultReps: String? = "10",
    val instructions: String? = null,
)
```

> 种子文件放 `:core:database/src/main/assets/`。若体积过大（>2MB），改用 **Play Asset Delivery** 或首次启动按需下载（此时需网络，与"离线优先"冲突，判定为不必要）。

### 4.6 迁移策略

```kotlin
// :core:database —— 所有迁移集中注册，禁止散落
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_set ADD COLUMN notes TEXT")
    }
}
```

**强制流程（写进 PR 模板）**
1. 修改 Entity → 提升 `version` → 编写 `MIGRATION_X_Y`
2. 运行 `./gradlew :core:database:kspDebugKotlin` 生成 `schemas/X.json`，**将 schema JSON 提交到版本控制**
3. 编写 `MigrationXToYTest`（`MigrationTestHelper` 加载 `X.json` 建库 → 插数据 → migrate → 断言）
4. 若涉及表结构重建，额外编写链式迁移测试（1→2→3）
5. 迁移前在 `Debug` 构建下自动把数据库文件快照到 `cacheDir/db_snapshot/`（仅保留最近 1 份，用于线上问题取证）

### 4.7 排序字段（orderIndex: Double）的实现

```kotlin
// :core:data
class OrderIndexCalculator(private val reindexThreshold: Double = 1e-6) {
    /** 在 prev 与 next 之间插入 */
    fun between(prev: Double?, next: Double?): Double {
        val p = prev ?: return (next ?: 1.0) - 1.0
        val n = next ?: return p + 1.0
        val mid = (p + n) / 2.0
        require(n - p > reindexThreshold) { "NEED_REINDEX" }
        return mid
    }
}
```

- 捕获 `NEED_REINDEX` → 在单事务中对整表重排（`0.0, 1.0, 2.0, ...`）后重试
- 单测覆盖：连续插入 200 次触发 reindex；首/尾插入；并发重排

---

## 5. 领域层设计

### 5.1 领域模型（`:core:model`，零 Android 依赖）

领域模型与 Room 实体**分离**：实体是持久化结构，领域模型是业务结构。转换由 `:core:data` 的 Mapper 负责，避免 Entity 泄漏到 UI。

```kotlin
// :core:model
data class Exercise(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val type: ExerciseType,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: Equipment,
    val isUnilateral: Boolean,
    val defaultRestSeconds: Int,
    val defaultSets: Int,
    val defaultReps: String?,
    val instructions: String?,
    val media: MediaRef?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
)

sealed interface MediaRef {
    data class Local(val fileName: String, val thumbnailName: String?, val type: MediaType) : MediaRef
    data class Remote(val url: String, val type: MediaType) : MediaRef
}

data class PlanDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val folderId: Long?,
    val defaultRestSeconds: Int?,
    val estimatedDurationSeconds: Int?,
    val scheduledDays: Set<DayOfWeek>,
    val isFavorite: Boolean,
    val exercises: List<PlanExerciseItem>,
)

data class PlanExerciseItem(
    val id: Long,
    val exercise: ExerciseSnapshot,
    val orderIndex: Double,
    val supersetTag: Int?,
    val targetSets: Int,
    val sets: List<PlannedSet>,
    val restSecondsOverride: Int?,
    val notes: String?,
    val mediaOverride: MediaRef?,
)

data class ExerciseSnapshot(
    val definitionId: Long?,
    val name: String,
    val type: ExerciseType,
    val isUnilateral: Boolean,
)

data class PlannedSet(
    val id: Long,
    val setIndex: Int,
    val setType: SetType,
    val targetReps: String?,
    val targetWeightKg: Double?,
    val targetDurationSeconds: Int?,
    val targetRpe: Double?,
)

data class ActiveWorkout(
    val sessionId: Long,
    val planName: String,
    val startedAt: Long,
    val status: SessionStatus,
    val pausedTotalSeconds: Int,
    val exercises: List<ActiveExercise>,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val rest: RestState,
)

sealed interface RestState {
    data object Idle : RestState
    data class Running(val endsAtEpochMs: Long, val plannedSeconds: Int, val pausedRemainingMs: Long? = null) : RestState
}
```

### 5.2 计算器（纯函数，100% 单测覆盖）

```kotlin
// :core:domain/calculator/VolumeCalculator.kt
object VolumeCalculator {

    /**
     * 计算单组容量（kg·次 或 kg·秒/分）。
     * @param bodyWeightKg REPS_ONLY 类（引体/俯卧撑）需要体重参与容量
     * @return 容量 kg；TIME / DISTANCE_TIME 类返回 0（不参与重量容量）
     */
    fun setVolume(set: SetRecord, bodyWeightKg: Double?): Double {
        if (set.setType == SetType.WARMUP && !set.includeWarmup) return 0.0
        val w = set.weightKg ?: 0.0
        return when (set.type) {
            ExerciseType.WEIGHT_REPS        -> w * (set.reps ?: 0)
            ExerciseType.REPS_ONLY          -> ((bodyWeightKg ?: 0.0) + w) * (set.reps ?: 0)
            ExerciseType.WEIGHT_TIME        -> w * ((set.durationSeconds ?: 0) / 60.0)
            ExerciseType.WEIGHT_DISTANCE_TIME -> w * (set.distanceMeters ?: 0.0)
            ExerciseType.TIME,
            ExerciseType.DISTANCE_TIME      -> 0.0
        }
    }

    fun sessionVolume(sets: List<SetRecord>, bodyWeightKg: Double?): Double =
        sets.sumOf { setVolume(it, bodyWeightKg) }
}
```

```kotlin
// :core:domain/calculator/OneRmCalculator.kt
object OneRmCalculator {

    const val MAX_RELIABLE_REPS = 12   // 超过 12 次的估算不可靠

    /** 估算 1RM。reps == 1 时直接返回重量；reps > 12 返回 null（不参与 PR 判定） */
    fun estimate(weightKg: Double, reps: Int, formula: OneRmFormula = OneRmFormula.AUTO): Double? {
        if (weightKg <= 0 || reps <= 0) return null
        if (reps == 1) return weightKg
        if (reps > MAX_RELIABLE_REPS) return null
        return when (formula) {
            OneRmFormula.AUTO -> if (reps <= 6) brzycki(weightKg, reps) else epley(weightKg, reps)
            OneRmFormula.EPLEY -> epley(weightKg, reps)
            OneRmFormula.BRZYCKI -> brzycki(weightKg, reps)
            OneRmFormula.LOMBARDI -> lombardi(weightKg, reps)
        }
    }

    /** Epley: 1RM = w * (1 + r/30) */
    fun epley(w: Double, r: Int): Double = w * (1 + r / 30.0)

    /** Brzycki: 1RM = w * 36 / (37 - r)，r >= 37 时无意义 */
    fun brzycki(w: Double, r: Int): Double =
        if (r >= 37) epley(w, r) else w * 36.0 / (37 - r)

    /** Lombardi: 1RM = w * r^0.10 */
    fun lombardi(w: Double, r: Int): Double = w * Math.pow(r.toDouble(), 0.10)

    /** 由 1RM 反推指定百分比对应的训练重量（四舍五入到最小配重步进） */
    fun weightForPercent(oneRm: Double, percent: Double, stepKg: Double = 2.5): Double =
        roundToStep(oneRm * percent, stepKg)
}

private fun roundToStep(value: Double, step: Double): Double =
    if (step <= 0) value else Math.round(value / step) * step
```

```kotlin
// :core:domain/calculator/PersonalRecordDetector.kt
class PersonalRecordDetector(private val time: TimeProvider) {

    sealed interface Result {
        data class Hit(val prType: PrType, val value: Double, val previousValue: Double?) : Result
        data object None : Result
    }

    /**
     * 判定一组是否刷新 PR。仅正式组参与，热身组/递减组按配置排除。
     * @param candidates 该动作当前已存在的 PR（来自 PersonalRecordDao）
     */
    fun detect(
        set: SetRecord,
        existing: Map<PrType, Double>,
        bodyWeightKg: Double?,
    ): List<Result> {
        if (set.setType == SetType.WARMUP) return emptyList()
        val results = mutableListOf<Result>()

        // MAX_WEIGHT：仅 reps >= 1 的有效完成组
        set.weightKg?.takeIf { it > 0 && (set.reps ?: 0) >= 1 }?.let { w ->
            val prev = existing[PrType.MAX_WEIGHT]
            if (prev == null || w > prev) results += Result.Hit(PrType.MAX_WEIGHT, w, prev)
        }

        // MAX_VOLUME_SET
        val vol = VolumeCalculator.setVolume(set, bodyWeightKg)
        if (vol > 0) {
            val prev = existing[PrType.MAX_VOLUME_SET]
            if (prev == null || vol > prev) results += Result.Hit(PrType.MAX_VOLUME_SET, vol, prev)
        }

        // MAX_E1RM
        set.weightKg?.let { w ->
            set.reps?.let { r ->
                OneRmCalculator.estimate(w, r)?.let { e ->
                    val prev = existing[PrType.MAX_E1RM]
                    if (prev == null || e > prev) results += Result.Hit(PrType.MAX_E1RM, e, prev)
                }
            }
        }
        return results
    }
}
```

```kotlin
// :core:domain/calculator/PlanDurationCalculator.kt
object PlanDurationCalculator {
    const val DEFAULT_SECONDS_PER_REP = 3
    const val EXERCISE_SWITCH_OVERHEAD_SECONDS = 5

    fun estimate(plan: PlanDetail, globalDefaultRestSeconds: Int): DurationRange {
        val total = plan.exercises.sumOf { ex -> exerciseSeconds(ex, plan, globalDefaultRestSeconds) }
        val overhead = plan.exercises.size * EXERCISE_SWITCH_OVERHEAD_SECONDS
        val seconds = total + overhead
        // 给区间：-10% ~ +15%（热身、找器械、聊天不可预测）
        return DurationRange(
            lowSeconds = (seconds * 0.9).toInt(),
            highSeconds = (seconds * 1.15).toInt(),
        )
    }

    private fun exerciseSeconds(ex: PlanExerciseItem, plan: PlanDetail, globalRest: Int): Int {
        val workSeconds = ex.sets.sumOf { set ->
            set.targetDurationSeconds
                ?: parseRepsMidpoint(set.targetReps)?.times(DEFAULT_SECONDS_PER_REP)?.toInt()
                ?: 0
        }
        val rest = ex.restSecondsOverride ?: plan.defaultRestSeconds ?: globalRest
        // 超级组内相邻动作之间无休息：仅统计每个 superset 分组的「最后一项」之后的一次休息
        val restCount = if (ex.supersetTag == null) maxOf(0, ex.sets.size - 1) else 0
        return workSeconds + restCount * rest
    }

    /** "8-12" → 10.0；"12" → 12.0；"AMRAP" / null → null */
    fun parseRepsMidpoint(reps: String?): Double? {
        if (reps.isNullOrBlank()) return null
        val t = reps.trim()
        if (t.equals("AMRAP", ignoreCase = true)) return null
        return t.split('-', '~').mapNotNull { it.trim().toDoubleOrNull() }
            .takeIf { it.isNotEmpty() }?.average()
    }

    /** 超级组额外休息：每个 superset 分组的所有动作完成后计一次 */
    fun supersetRestSeconds(items: List<PlanExerciseItem>, plan: PlanDetail, globalRest: Int): Int =
        items.groupBy { it.supersetTag }.filterKeys { it != null }.values.sumOf { group ->
            val last = group.maxByOrNull { it.orderIndex } ?: return@sumOf 0
            val sets = last.sets.size
            maxOf(0, sets - 1) * (last.restSecondsOverride ?: plan.defaultRestSeconds ?: globalRest)
        }
}
```

```kotlin
// :core:domain/calculator/UnitConverter.kt
object UnitConverter {
    private const val KG_PER_LB = 0.45359237

    fun lbToKg(lb: Double): Double = round2(lb * KG_PER_LB)
    fun kgToLb(kg: Double): Double = round2(kg / KG_PER_LB)

    /** 按单位解析用户输入 -> 内部 kg */
    fun toKg(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) round2(value) else lbToKg(value)

    /** 内部 kg -> 展示值 */
    fun fromKg(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) round2(kg) else kgToLb(kg)

    /** 按单位取默认步进 */
    fun defaultStep(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 2.5 else 5.0

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
```

```kotlin
// :core:domain/calculator/PlateCalculator.kt
class PlateCalculator(
    private val availablePlatesKg: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    private val barWeightKg: Double = 20.0,
) {
    data class Solution(val perSide: List<Double>, val achievedTotalKg: Double, val deltaKg: Double)

    /** 贪心求每侧配片；无法精确达成时返回最接近且不超过/超过的方案 */
    fun solve(targetKg: Double): Solution? {
        if (targetKg < barWeightKg) return null
        var remainingPerSide = (targetKg - barWeightKg) / 2.0
        val perSide = mutableListOf<Double>()
        for (plate in availablePlatesKg.sortedDescending()) {
            while (remainingPerSide + 1e-9 >= plate) {
                perSide += plate
                remainingPerSide -= plate
            }
        }
        val achieved = barWeightKg + perSide.sum() * 2
        return Solution(perSide, achieved, achieved - targetKg)
    }
}
```

### 5.3 UseCase 清单

| UseCase | 输入 | 输出 | 说明 |
|---------|------|------|------|
| `ObservePlansUseCase` | query: String, filter | `Flow<List<PlanCard>>` | 列表 + 搜索 |
| `GetPlanDetailUseCase` | planId | `Flow<PlanDetail>` | 含动作与组级处方 |
| `CreatePlanUseCase` | name, desc, schedule, template | `Long` | 支持模板/复制 |
| `DuplicatePlanUseCase` | planId | `Long` | 深拷贝动作与 PlanSet |
| `UpdatePlanUseCase` | PlanDetail | `Outcome<Unit>` | 单事务全量保存（diff 后增量写） |
| `SoftDeletePlanUseCase` | planId | `Unit` | 置 `deletedAt`，媒体不删 |
| `RestorePlanUseCase` | planId | `Unit` | 清 `deletedAt` |
| `PermanentlyDeletePlanUseCase` | planId, deleteMedia | `Unit` | 级联删除 + 可选删媒体 |
| `SearchExercisesUseCase` | query, filters | `Flow<List<Exercise>>` | FTS + 筛选 |
| `CreateCustomExerciseUseCase` | 字段 | `Long` | 名称查重 |
| `StartWorkoutUseCase` | planId?, mode | `Long` (sessionId) | 三种填充模式 |
| `LogSetUseCase` | setRecord | `Outcome<SetLogResult>` | 写组 + PR 判定 + 更新汇总 |
| `SkipSetUseCase` / `SkipExerciseUseCase` | id | `Unit` | 运行时状态 |
| `AddAdhocExerciseUseCase` | sessionId, exerciseId | `Long` | 不写回计划 |
| `ReplaceExerciseUseCase` | workoutExerciseId, newId | `Unit` | 保留/清空数据可选 |
| `FinishWorkoutUseCase` | sessionId, 总结数据 | `WorkoutSummary` | 汇总 + PR 列表 + 更新 plan.lastPerformedAt |
| `ResumeWorkoutUseCase` | — | `ActiveWorkout?` | 断点恢复 |
| `AbandonWorkoutUseCase` | sessionId | `Unit` | 标记 ABANDONED |
| `ObserveHistoryUseCase` | 日期范围, 筛选 | `Flow<List<SessionSummary>>` | — |
| `ObserveStatsUseCase` | range | `Flow<StatsData>` | 聚合 |
| `RecordBodyMeasurementUseCase` | 数据 | `Long` | 手动/健康同步 |
| `ExportBackupUseCase` | uri | `Flow<ExportProgress>` | zip |
| `ImportBackupUseCase` | uri | `Flow<ImportProgress>` | 预校验 + 事务 |
| `ImportCsvUseCase` | uri, source | `Flow<ImportProgress>` | Strong/Hevy 适配器 |

### 5.4 时间与时区处理

```kotlin
// :core:common
class SystemTimeProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : TimeProvider {
    override fun nowEpochMs() = clock.millis()
    override fun elapsedRealtimeMs() = SystemClock.elapsedRealtime()
    override fun todayEpochDay() = LocalDate.now(clock).toEpochDay()
}
```

**规则**
- 训练时长：`(elapsedRealtimeMs_end - elapsedRealtimeMs_start) / 1000 - pausedTotalSeconds`，**不使用**墙钟差值
- 训练归属日期：`Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().toEpochDay()`，跨天训练归属开始日
- 统计聚合的日/周边界：在应用层用 `LocalDate` / `WeekFields.of(firstDayOfWeek, 1)` 计算 `epochDay` 起止后传入 SQL，**不在 SQL 里做除法**（避免时区/夏令时错误，修正 §4.3 的临时写法）
- 测试使用 `MutableClock`（自定义 `Clock` 实现，可手动推进时间），**禁止**在业务代码中直接调用 `System.currentTimeMillis()`

### 5.5 渐进超负荷建议算法

```kotlin
// :core:domain/usecase/SuggestNextLoadUseCase.kt
class SuggestNextLoadUseCase(
    private val weightUnit: WeightUnit,
) {
    data class Suggestion(
        val weightKg: Double,
        val reason: Reason,
    )
    enum class Reason { INCREASE_WEIGHT, KEEP_WEIGHT, DELOAD_HINT, NO_DATA }

    /**
     * 双进阶（Double Progression）：
     * 1. 上次所有正式组都达到次数区间上限 -> 加重一个步进（重量重置为区间下限的次数）
     * 2. 上次存在未达区间下限的组 -> 维持重量
     * 3. 连续 3 次未达下限 -> 建议减重 10%（Deload 提示）
     */
    fun suggest(
        lastSessionSets: List<SetRecord>,
        repRangeLow: Int,
        repRangeHigh: Int,
        currentWeightKg: Double,
    ): Suggestion {
        val working = lastSessionSets.filter { it.setType == SetType.NORMAL && it.status == SetStatus.COMPLETED }
        if (working.isEmpty()) return Suggestion(currentWeightKg, Reason.NO_DATA)

        val allAtTop = working.all { (it.reps ?: 0) >= repRangeHigh }
        val allAtOrAboveLow = working.all { (it.reps ?: 0) >= repRangeLow }

        return when {
            allAtTop -> Suggestion(currentWeightKg + UnitConverter.defaultStep(weightUnit), Reason.INCREASE_WEIGHT)
            allAtOrAboveLow -> Suggestion(currentWeightKg, Reason.KEEP_WEIGHT)
            else -> Suggestion(currentWeightKg, Reason.KEEP_WEIGHT)
        }
    }
}
```

> 该建议**只作为 UI 文案提示**，绝不自动改写用户输入（PRD FR-14）。

### 5.6 FTS 查询构造

```kotlin
object FtsQueryBuilder {
    /**
     * 用户输入转 FTS4 安全查询。
     * - 移除 SQL 特殊字符
     * - 支持前缀匹配 "bench*"
     * - 多词用 AND 连接
     * - 中文按字切分（FTS4 无内置中文分词，采用逐字前缀 + 子串回退）
     */
    fun build(raw: String): String? {
        val tokens = raw.trim()
            .replace(Regex("""["'*():\-^]"""), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token ->
            if (token.any { it.code > 0x2E80 }) {
                // CJK：逐字加前缀，保证「卧推」能匹配「平板卧推」
                token.map { "$it*" }.joinToString(" ")
            } else {
                "$token*"
            }
        }
    }
}
```

> **降级策略**：FTS 查询返回空且原始输入长度 ≥ 2 时，回退到 `LIKE '%token%'` 查询（覆盖中日韩子串匹配），结果合并去重。这是中文搜索可用性的必要兜底。

---

## 6. 媒体层设计

### 6.1 流水线

```
                    ┌────────────────────────────────────────────┐
Picker/URI ────────►│ 1. 元数据探测 (size/mime/duration/wh)      │
                    │ 2. 校验: 格式白名单 + 大小上限 + 磁盘空间   │
                    │ 3. [可选] 转码 (Media3 Transformer)        │
                    │ 4. 流式复制 -> cacheDir/tmp/{uuid}.part    │
                    │ 5. 原子 rename -> filesDir/media/{uuid}.ext│
                    │ 6. 生成缩略图 -> filesDir/thumbs/{uuid}.webp│
                    │ 7. Room 单事务写入 MediaAsset + 关联字段   │
                    └────────────────────────────────────────────┘
                            失败：清理 tmp / 已落盘文件，回滚 DB
```

### 6.2 核心接口

```kotlin
// :core:media
data class MediaCandidate(
    val uri: Uri,
    val mimeType: String?,
    val sizeBytes: Long?,
    val displayName: String?,
)

sealed interface ImportResult {
    data class Success(val asset: ImportedMedia) : ImportResult
    data class Rejected(val reason: RejectReason, val detail: String) : ImportResult
    data class Failed(val error: DomainError) : ImportResult
}

sealed interface RejectReason {
    data object UnsupportedFormat : RejectReason
    data object TooLarge : RejectReason
    data object InsufficientStorage : RejectReason
    data object MetadataUnreadable : RejectReason
    data object UserCancelled : RejectReason
}

data class ImportedMedia(
    val fileName: String,
    val thumbnailName: String?,
    val type: MediaType,
    val sizeBytes: Long,
    val durationMs: Long?,
)

interface MediaImporter {
    /** 单文件导入，带进度（0f..1f），支持取消（协程取消） */
    fun import(
        candidate: MediaCandidate,
        options: ImportOptions = ImportOptions(),
    ): Flow<ImportProgress>
}

sealed interface ImportProgress {
    data class Reading(val fraction: Float) : ImportProgress
    data class Transcoding(val fraction: Float) : ImportProgress
    data class Copying(val fraction: Float) : ImportProgress
    data class Thumbnailing(val fraction: Float) : ImportProgress
    data class Done(val result: ImportResult) : ImportProgress
}

data class ImportOptions(
    val videoLimitBytes: Long = 200L * 1024 * 1024,
    val imageLimitBytes: Long = 20L * 1024 * 1024,
    val requiredFreeSpaceBufferBytes: Long = 200L * 1024 * 1024,
    val transcodeIfLargerThanBytes: Long? = null,   // 非空则超限时触发转码而非拒绝
    val transcodeTargetHeight: Int = 720,
    val thumbnailMaxEdgePx: Int = 512,
)
```

### 6.3 关键实现要点

| 要点 | 实现 |
|------|------|
| 格式白名单 | `image/gif`, `image/webp`（动态）, `image/png/jpeg`, `video/mp4`, `video/webm`, `video/quicktime`；通过 `ContentResolver.getType()` + 魔数校验双保险 |
| 大文件复制 | `contentResolver.openInputStream(uri)` → `FileOutputStream`，8KB buffer，逐块写入并上报进度；**禁止** `readBytes()` |
| 原子写入 | 先写 `cacheDir/tmp/{uuid}.part`，`File.renameTo` 到目标目录（同分区保证原子） |
| 缩略图 | API 29+ 用 `ContentResolver.loadThumbnail(uri, Size(512,512), null)`；低版本 `MediaMetadataRetriever.getFrameAtTime` + `inSampleSize` 下采样；输出 WebP 质量 80 |
| 动画 WebP/GIF 缩略图 | 缩略图取首帧静态 WebP，避免列表页解码动画（性能） |
| 转码 | `Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)`，输出分辨率按 `transcodeTargetHeight` 等比缩放 |
| 空间预检 | `StatFs(filesDir).availableBytes` ≥ 文件大小 × 2 + buffer，否则 `InsufficientStorage` |
| 取消清理 | 在 `finally` 与 `invokeOnCancellation` 中删除 `.part` 与已落盘文件（若 DB 未写） |
| 存储位置 | `context.filesDir/media/`（私有）；**绝不写外部存储** |
| 备份排除 | `dataExtractionRules` 与 `fullBackupContent` 均排除 `media/`、`thumbs/`、`cache/` |
| 启动清理 | Application 启动时清空 `cacheDir/tmp/`（上次异常退出的残留） |

### 6.4 媒体垃圾回收

```kotlin
// :core:media
class MediaGarbageCollector(
    private val mediaDir: File,
    private val thumbDir: File,
    private val mediaDao: MediaAssetDao,
    private val referenceChecker: MediaReferenceChecker,
) {
    data class ScanReport(
        val totalFiles: Int,
        val totalBytes: Long,
        val orphanFiles: List<OrphanFile>,
        val orphanBytes: Long,
    )

    /** 只读扫描，不删文件 */
    suspend fun scan(orphanThresholdDays: Int = 30): ScanReport

    /** 用户确认后执行删除。返回释放的字节数 */
    suspend fun purge(orphans: List<OrphanFile>): Long
}
```

**引用判定必须覆盖全部引用方**（否则会误删用户数据）：

| 引用位置 | 字段 |
|---------|------|
| `exercise_definition` | `media_path`, `thumbnail_path` |
| `plan_exercise` | `media_path`, `thumbnail_path` |
| `workout_exercise` | `media_path` |
| **回收站内计划**（`deleted_at IS NOT NULL`） | 通过 `plan_exercise` 间接引用，**必须计入**，否则恢复计划后媒体丢失 |

因此 GC 的 SQL 需**不筛选** `deleted_at`，这是与常规查询的关键差异，需在代码注释与测试中明确。

---

## 7. UI 层设计

### 7.1 导航图

```kotlin
// :core:common/navigation/Routes.kt
@Serializable data object HomeRoute
@Serializable data object OnboardingRoute

@Serializable data object PlanListRoute
@Serializable data class PlanDetailRoute(val planId: Long)
@Serializable data class PlanEditRoute(val planId: Long? = null)   // null = 新建
@Serializable data object RecycleBinRoute

@Serializable data object ExerciseLibraryRoute
@Serializable data class ExerciseDetailRoute(val exerciseId: Long)
@Serializable data class ExercisePickerRoute(val planId: Long?, val replacePlanExerciseId: Long? = null)

@Serializable data class WorkoutRoute(val sessionId: Long)
@Serializable data class WorkoutSummaryRoute(val sessionId: Long)
@Serializable data class MediaPlayerRoute(val mediaRefId: String)

@Serializable data object HistoryRoute
@Serializable data class SessionDetailRoute(val sessionId: Long)
@Serializable data object StatsRoute
@Serializable data object BodyRoute
@Serializable data object SettingsRoute
@Serializable data object BackupRoute
```

**导航实现规则**
- 单一 `NavHost` 在 `:app`，各 feature 通过 `NavGraphBuilder.xxxScreen()` 扩展函数注册，避免 `:app` 了解 feature 内部
- 训练页使用 **`launchSingleTop = true`** + `popUpTo(PlanDetail){ inclusive = false }`，防止重复入栈
- **预测性返回（Android 15+）**：`PlanEditRoute` 与 `WorkoutRoute` 使用 `PredictiveBackHandler`，拦截时弹确认对话框并给出返回动画预览

### 7.2 页面清单

| 页面 | 模块 | 关键交互 |
|------|------|---------|
| Onboarding | feature:onboarding | 3 步 ViewPager + 隐私同意 |
| Home（今日） | feature:home | 今日计划卡片、快速开始、上次训练、本周统计摘要 |
| PlanList | feature:plans | 搜索、分组、排序切换、长按菜单、侧滑 |
| PlanDetail | feature:plans | 头部时长/组数、动作列表、超级组可视化、开始训练 FAB |
| PlanEdit | feature:plans | 名称/描述/排期、动作增删改排序、组明细展开、自动保存指示 |
| RecycleBin | feature:plans | 剩余天数、恢复、永久删除 |
| ExerciseLibrary | feature:exercises | 搜索、肌群/器械筛选、Tab 分组 |
| ExerciseDetail | feature:exercises | 演示、要点、PR 卡片、历史列表、趋势图 |
| ExercisePicker | feature:exercises | 底部弹层、多选、快速创建自定义 |
| Workout | feature:workout | 核心页，见 §8.4 |
| WorkoutSummary | feature:workout | 汇总对比、PR、分享卡片 |
| MediaPlayer | feature:workout | 全屏播放、倍速、逐帧、横屏 |
| History | feature:history | 日历/列表切换、筛选 |
| SessionDetail | feature:history | 逐组复盘、目标 vs 实际、编辑 |
| Stats | feature:stats | 概览卡、容量趋势、肌群分布、PR 列表、热力图 |
| Body | feature:body | 体重/围度录入与趋势 |
| Settings | feature:settings | 分组设置项 |
| Backup | feature:settings | 导出/导入、进度、媒体空间统计 |

### 7.3 设计系统（`:core:designsystem`）

```kotlin
// 只暴露语义化 Token，页面禁止硬编码颜色/尺寸
@Immutable
data class FitnessColors(
    val prHighlight: Color,
    val setCompleted: Color,
    val setPending: Color,
    val setSkipped: Color,
    val setFailed: Color,
    val supersetAccent: Color,
    val chartPrimary: Color,
    val chartSecondary: Color,
    val restTimerTrack: Color,
    val restTimerIndicator: Color,
    // ... 其余走 Material3 ColorScheme
)

@Immutable
data class FitnessSpacing(
    val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 16.dp,
    val l: Dp = 24.dp, val xl: Dp = 32.dp,
    val touchTargetMin: Dp = 48.dp,   // 无障碍强制
)

@Immutable
data class FitnessTypography(/* 基于 Material3 Typography，训练页数字用等宽字体 */)
```

**必备通用组件**

| 组件 | 说明 |
|------|------|
| `NumberStepperField` | 重量/次数输入，带 ± 步进、长按连续加减、单位后缀 |
| `RestTimerPill` | 悬浮胶囊，环形进度，可展开 |
| `SetRow` | 组记录行：序号、重量、次数、勾选、对比箭头、长按菜单 |
| `SupersetGroupCard` | 超级组容器（左侧竖线 + 分组标识） |
| `ExerciseThumbnail` | 统一缩略图（含占位图、加载失败态） |
| `PrBadge` | PR 徽标（图标 + 文字，不单靠颜色） |
| `TrendLineChart` | 折线图（自绘 Canvas，支持空态/单点态） |
| `MuscleDistributionChart` | 饼图/条形 |
| `CalendarHeatmap` | 年历热力图 |
| `EmptyState` / `ErrorState` / `SkeletonList` | 三态统一 |
| `ConfirmDialog` | 统一二次确认（含破坏性操作红色按钮） |
| `SnackbarHostWithUndo` | 带 Undo 的 Snackbar |

**UI 强制规范**
- 所有可点击元素 ≥ 48dp（`touchTargetMin`）
- 图标必须有 `contentDescription`；装饰性元素显式传 `null`
- 训练页数字使用等宽字体（`FontFamily.Monospace`）避免倒计时跳动
- 训练中屏幕常亮：`WindowCompat` + `FLAG_KEEP_SCREEN_ON`（跟随设置）
- Edge-to-edge：`enableEdgeToEdge()` + 各页面 `WindowInsets` 处理，**禁止**硬编码状态栏高度

---

## 8. 训练执行引擎

这是全项目风险最高的模块（PRD R3），需要最严格的设计。

### 8.1 状态承载与持久化分工

| 状态 | 存放 | 持久化时机 | 恢复来源 |
|------|------|-----------|---------|
| 会话/动作/组的**事实数据** | Room（`workout_session` / `workout_exercise` / `workout_set`） | **每完成一组立即写入** | Room |
| 当前光标位置、暂停标记 | Room（`workout_session_state`，单行） | 每次切换动作/组、暂停/恢复 | Room |
| 休息计时终点 | Room（`workout_session_state.rest_timer_ends_at`） | 启动/暂停/跳过/调整时 | Room + 墙钟兜底 |
| UI 瞬时输入（未提交的重量框内容） | Compose `rememberSaveable` | 屏幕旋转/进程重建 | SavedState |

**核心原则：完成一组 = 一次数据库写入。** 不存在"训练结束才批量保存"，因此任何时刻杀进程最多丢失"正在输入但未提交"的那一组。

### 8.2 WorkoutSessionManager

```kotlin
// :core:data/workout
@Singleton
class WorkoutSessionManager @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val stateDao: WorkoutSessionStateDao,
    private val detector: PersonalRecordDetector,
    private val prDao: PersonalRecordDao,
    private val volumeCalc: VolumeCalculator,       // object，仅为可注入性包装
    private val restTimer: RestTimerController,
    private val settingsRepository: SettingsRepository,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val mutex: Mutex = Mutex(),             // 串行化组写入，避免并发竞态
) {
    /** 当前活跃会话的完整状态流 */
    fun observeActive(): Flow<ActiveWorkout?>

    /**
     * 记录一组。原子性要求：
     * 1) 写 workout_set
     * 2) 更新 workout_exercise.status
     * 3) PR 判定与写入
     * 4) 更新 workout_session 汇总
     * 5) 推进 state 光标
     * 6) 启动休息计时
     * 步骤 1-5 在同一 Room 事务内；步骤 6 在事务外（涉及系统服务）
     */
    suspend fun logSet(input: LogSetInput): Outcome<SetLogResult> = mutex.withLock {
        withContext(io) {
            runCatching {
                var prHits: List<PersonalRecordDetector.Result> = emptyList()
                val setId = workoutDao.runInTransaction {
                    // ... 写入与推进
                    0L
                }
                Outcome.Success(SetLogResult(setId, prHits))
            }.getOrElse { Outcome.Failure(DomainError.DatabaseFailure) }
        }
    }

    suspend fun skipSet(workoutSetId: Long): Outcome<Unit>
    suspend fun skipExercise(workoutExerciseId: Long): Outcome<Unit>
    suspend fun addSetToExercise(workoutExerciseId: Long): Outcome<Long>
    suspend fun deleteLastSet(workoutExerciseId: Long): Outcome<Unit>
    suspend fun addAdhocExercise(sessionId: Long, exerciseId: Long): Outcome<Long>
    suspend fun replaceExercise(workoutExerciseId: Long, newExerciseId: Long, keepData: Boolean): Outcome<Unit>
    suspend fun pause(): Outcome<Unit>
    suspend fun resume(): Outcome<Unit>
    suspend fun finish(sessionId: Long, summaryInput: SummaryInput): Outcome<WorkoutSummary>
    suspend fun abandon(sessionId: Long): Outcome<Unit>
}

data class LogSetInput(
    val workoutExerciseId: Long,
    val setIndex: Int,
    val setType: SetType,
    val weightInput: Double?,
    val unit: WeightUnit,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val leftReps: Int? = null,
    val rightReps: Int? = null,
    val rpe: Double? = null,
    val notes: String? = null,
)

data class SetLogResult(
    val setId: Long,
    val prHits: List<PersonalRecordDetector.Result>,
    val restSeconds: Int,
)
```

### 8.3 休息计时器

#### 8.3.1 设计结论：**不使用前台服务**

原方案（`specialUse` 前台服务）存在审核不确定性，且对 60-180 秒的短任务而言是过度设计。改用**「普通通知 + 系统闹钟」零后台方案**，同时获得更好的体验与更低的审核风险。

| 维度 | A. `specialUse` 前台服务 | B. `shortService`（≤3min） | **C. 普通通知 + AlarmManager（采用）** |
|------|:---:|:---:|:---:|
| Play 审核风险 | **高**（需说明用途，可能被要求整改） | 低 | **无** |
| 额外权限 | `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | `FOREGROUND_SERVICE` | **无** |
| 常驻通知 | 需要 | 需要 | 需要（同样是优点：可见倒计时） |
| >3 分钟休息 | 支持 | **不支持**（硬超时） | **支持** |
| 后台耗电 | 有（常驻服务 + wakelock） | 有 | **零**（无服务、无 wakelock、无每秒刷新） |
| 进程被杀后到点提示 | 服务会被杀，提示丢失 | 同左 | **不丢失**（通知与闹钟由系统托管） |
| 抗国产 ROM 清理 | 弱（服务易被杀） | 弱 | **强** |
| 倒计时精度 | 应用自算 | 应用自算 | **系统自算 + 闹钟到点** |

**为什么普通通知就能显示倒计时**：`NotificationCompat.setUsesChronometer(true)` + `setChronometerCountDown(true)` + `setWhen(endsAt)` 会让 **SystemUI 自己每秒重绘倒计时**，应用不需要常驻进程、不需要每秒更新通知。这是系统原生能力，被绝大多数"计时类"应用忽略。

#### 8.3.2 三层机制与职责

```
RestTimerController（单例，领域服务）
  ├── 唯一真理：endsAtEpochMs（持久化到 workout_session_state）
  ├── 计算：剩余时间 / 暂停 / 延长 / 跳过
  ├── 机制 1：普通通知（系统自绘倒计时 + 3 个 Action）→ 负责"显示"
  ├── 机制 2：AlarmManager 一次性闹钟 → RestAlarmReceiver → 负责"到点提醒"
  └── 机制 3：应用内 UI 协程 ticker → 负责"训练页内的高亮反馈"
```

| 场景 | 机制 1（通知） | 机制 2（闹钟） | 机制 3（UI） | 用户感知 |
|------|:---:|:---:|:---:|---------|
| 应用在前台 | 显示 | 已注册 | 运行 | 悬浮胶囊 + 通知双倒计时 |
| 应用切后台（进程存活） | 显示 | 已注册 | 停止 | 通知栏可见倒计时 |
| **进程被杀 / 用户划掉** | 仍在（系统持有） | **仍会触发** | — | **功能完全正常** |
| 设备休眠（Doze） | 显示 | 可能延迟数十秒 | — | 到点提醒可能稍晚（见 8.3.3 说明） |
| 设备重启 | 清除 | 清除 | — | 状态过期，直接清理 |
| 用户返回 App | 同步 | 同步 | 用 `endsAtEpochMs` 重算 | **倒计时永远正确** |

**核心设计原则**：*正确性永不依赖任何后台机制*。剩余时间永远由 `endsAtEpochMs - now` 计算，通知、闹钟、UI 都只是它的"视图"。因此即使通知被滑掉、闹钟被系统延迟、进程被杀，用户回到训练页看到的倒计时依然是准确的。这把"系统杀进程"从一个**功能错误**降级为一个**少了一次提醒**。

#### 8.3.3 实现

```kotlin
// :feature:workout/notify/RestTimerNotifier.kt
fun buildRunningNotification(state: RestState.Running, actions: RestActions): Notification {
    val countDown = state.pausedRemainingMs == null
    return NotificationCompat.Builder(context, NotificationChannels.REST_RUNNING)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle(context.getString(R.string.rest_timer_title))
        .setContentText(context.getString(R.string.rest_timer_remaining_format, state.plannedSeconds / 60, state.plannedSeconds % 60))
        .setWhen(state.endsAtEpochMs)
        .setUsesChronometer(countDown)
        .setChronometerCountDown(countDown)        // 系统自绘倒计时，零刷新
        .setOngoing(true)                          // 不可滑动清除
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(actions.openWorkout)     // 点通知回到训练页
        .addAction(0, context.getString(R.string.rest_skip), actions.skip)
        .addAction(0, context.getString(R.string.rest_add_30s), actions.extend)
        .addAction(0, context.getString(R.string.rest_pause), actions.pause)
        // 注意：不调用 setForegroundServiceBehavior —— 本通知不是前台服务通知
        .build()
}

/** 到点后的提醒通知（由闹钟触发，高优先级 + 震动） */
fun buildFinishedNotification(actions: RestActions): Notification =
    NotificationCompat.Builder(context, NotificationChannels.REST_FINISHED)
        .setSmallIcon(R.drawable.ic_timer_done)
        .setContentTitle(context.getString(R.string.rest_finished_title))
        .setContentText(context.getString(R.string.rest_finished_next_set_hint))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(actions.openWorkout)
        .addAction(0, context.getString(R.string.rest_add_30s), actions.extend)
        .build()
```

```kotlin
// :feature:workout/timer/RestAlarmScheduler.kt
class RestAlarmScheduler(private val context: Context) {

    /**
     * 注册休息结束闹钟。
     *
     * 使用 setAndAllowWhileIdle()：这是「无需任何权限」即可调用的精确度最高 API。
     * - setExact() / setExactAndAllowWhileIdle() 在 API 31+ 需要 SCHEDULE_EXACT_ALARM，
     *   该权限用户可拒绝、Play 也有使用场景限制，故不使用。
     * - 训练过程中设备处于用户活跃状态，不会进入 Doze，实际触发误差通常在 2 秒内。
     * - 即便出现延迟，也仅影响「提醒音」，不影响倒计时正确性（见 8.3.2 核心原则）。
     */
    fun schedule(endsAtEpochMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endsAtEpochMs,
            restPendingIntent(),
        )
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java).cancel(restPendingIntent())
    }

    private fun restPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REST_ALARM,
            Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_REST_FINISHED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
```

```kotlin
// :feature:workout/receiver/RestAlarmReceiver.kt
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return
        // 幂等：若该次休息已被跳过/延长/暂停/训练已结束，则不再提醒
        // 通过一次快速的 DataStore 读取判断 restFiredToken 是否匹配本次 endsAt
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = stateDao.get()?.restTimerEndsAtEpochMs
                if (state != null && state == intent.getLongExtra(EXTRA_ENDS_AT, -1L)) {
                    notifier.showFinished()
                    haptics.restFinished()      // 震动
                    if (settings.restSoundEnabled) sound.playRestFinished()   // USAGE_ASSISTANCE_SONIFICATION
                    if (settings.ttsEnabled) tts.speak(nextSetHint())
                    stateDao.clearRestTimer()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
```

```xml
<!-- AndroidManifest.xml —— 注意：无任何前台服务声明 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />

<receiver
    android:name=".receiver.RestAlarmReceiver"
    android:exported="false" />
```

> 与上一版对比：**删除了 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`WAKE_LOCK` 三个权限**，删除了 `<service>` 声明，审核风险与实现复杂度同时下降。
>
> `POST_NOTIFICATIONS` 仍是必需的（要发通知）。若用户拒绝该权限：仅失去通知栏倒计时与到点提醒，**应用内训练页的倒计时与提示音照常工作**（提示音由应用内播放，不需要通知权限）。这是可接受的降级。

**关于提示音不打断音乐**：`sound.playRestFinished()` 使用 `AudioAttributes(USAGE_ASSISTANCE_SONIFICATION, CONTENT_TYPE_SONIFICATION)`，它**不会**申请音频焦点，因此不会暂停用户正在播放的音乐。若用户设置了"语音播报"（TTS），则由 TTS 引擎自行处理音频焦点（会短暂压低音乐音量，属期望行为）。

**关于训练提醒（FR-44 的"训练提醒"）**：训练提醒需要准点，且可能发生在设备休眠时。此处仍使用 `AlarmManager`，但优先尝试 `setExactAndAllowWhileIdle()`；调用前用 `canScheduleExactAlarms()` 判断，被拒则降级 `setAndAllowWhileIdle()` 并在设置页提示"提醒可能有几分钟延迟"。这是唯一需要精确闹钟能力的功能，与休息计时器解耦。

#### 8.3.4 恢复与兜底

```kotlin
suspend fun restore(): RestState {
    val persisted = stateDao.get()?.restTimerEndsAtEpochMs ?: return RestState.Idle
    val remaining = persisted - time.nowEpochMs()
    return when {
        remaining <= 0L -> {
            stateDao.clearRestTimer()
            alarmScheduler.cancel()
            notifier.cancelRunning()
            RestState.Finished          // UI 提示「休息已结束」，而非静默回到 Idle
        }
        remaining > MAX_REST_MS -> {    // 墙钟被大幅调后（> 15min），判定异常
            stateDao.clearRestTimer()
            alarmScheduler.cancel()
            notifier.cancelRunning()
            RestState.Idle
        }
        else -> RestState.Running(persisted, plannedSeconds = ((remaining + 999) / 1000).toInt())
            .also { notifier.showRunning(it, actions()) }     // 重建通知（进程重启后通知可能已丢失）
    }
}
```

- **进程重启后必须重建通知**：新进程不会自动继承旧通知，`restore()` 里调用 `notifier.showRunning()` 重建；闹钟可能仍有效（系统持有），但重建时用同一 `PendingIntent` 覆盖是幂等的
- **设备重启**：`BOOT_COMPLETED` 收到后**只做清理**（闹钟已被系统清除，休息早已过期），不做恢复
- **训练已结束**：`finish()` / `abandon()` 必须调用 `restTimer.cancel()` → 同时 `alarmScheduler.cancel()` + `notifier.cancelAll()` + 清理状态行
- **所有入口幂等**：跳过、延长、暂停、结束都走同一个 `RestTimerController` 方法，内部统一做「更新 DB → 更新通知 → 重排/取消闹钟」，避免状态发散
- **时钟跳变**：剩余 > 15 分钟视为异常直接清理；这是防止用户改系统时间导致计时器永久卡住的必要兜底

### 8.4 训练页 UI 结构

```kotlin
@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    onAction: (WorkoutAction) -> Unit,
    onNavigate: (WorkoutEffect) -> Unit,
) {
    // LazyColumn 结构
    // item(0): StickyHeader —— 已用时间 / 总容量 / 组进度 / 结束按钮
    // items(exercises, key = { it.id }): ExerciseCard
    //    卡片内：演示折叠区 + 上次成绩 + 组列表 + 输入区
    // item(last): AddExerciseButton
    // Box 覆盖层：RestTimerPill（当前动作有休息时显示）
}
```

| 要点 | 实现 |
|------|------|
| 列表 key | 所有 `items` 使用稳定 id（`workoutExercise.id`），**禁止用 index** |
| 输入框状态 | 每个输入框局部 `rememberSaveable`，避免整页重组 |
| 焦点自动跳转 | 完成一组后 `FocusRequester` 请求下一组输入框焦点 |
| 数字键盘 | `KeyboardType.Decimal`（重量）/ `KeyboardType.Number`（次数） |
| 自动带入 | 输入框初始值 = 上一组值 ?: 上次训练同序号值 ?: 计划目标值 |
| 已用时间 | `LaunchedEffect` 每秒 tick，只在屏幕可见时运行（`repeatOnLifecycle(STARTED)`） |
| 完成一组 | 乐观更新 + 异步落库；失败回滚并 `Snackbar` 提示重试 |
| 屏幕常亮 | `DisposableEffect` 设置/清除 `FLAG_KEEP_SCREEN_ON` |
| 快速淡出 | 已完成动作卡片自动折叠，当前动作展开 |

### 8.5 训练中"上次成绩"与自动填充

```kotlin
// :core:domain/usecase/BuildWorkoutPrefillUseCase.kt
class BuildWorkoutPrefillUseCase(
    private val workoutDao: WorkoutDao,
    private val settings: SettingsRepository,
) {
    /**
     * 为训练会话生成每个动作的预填数据。
     * 策略：preferLastSession 为真时用上次成绩覆盖计划目标（次数取上次值，重量取上次值）。
     */
    suspend fun build(
        exercises: List<WorkoutExercise>,
        preferLastSession: Boolean,
    ): Map<Long, ExercisePrefill> = coroutineScope {
        exercises.map { ex ->
            async {
                val definitionId = ex.exerciseDefinitionId
                val last = definitionId?.let { workoutDao.previousSessionSets(it) }.orEmpty()
                ex.id to ExercisePrefill(
                    lastSessionSets = last,
                    lastSessionSummary = last.toSummaryText(),   // "60kg × 8, 8, 7"
                )
            }
        }.awaitAll().toMap()
    }
}
```

### 8.6 结束训练与汇总

```kotlin
@Transaction
suspend fun finishInternal(sessionId: Long, input: SummaryInput, now: Long): WorkoutSummary {
    val sets = workoutDao.completedSetsOf(sessionId)
    val session = workoutDao.get(sessionId)
    val volume = VolumeCalculator.sessionVolume(sets.map { it.toRecord() }, session.bodyWeightKgSnapshot)
    val reps = sets.sumOf { it.reps ?: 0 }
    val duration = ((workoutDao.currentDurationMs(sessionId) - session.pausedTotalSeconds * 1000L) / 1000).toInt()

    workoutDao.finalizeSession(
        sessionId = sessionId,
        status = if (input.completedFully) "COMPLETED" else "ABANDONED",
        completedAt = now, duration = duration, volume = volume,
        sets = sets.size, reps = reps, now = now,
    )
    session.planId?.let { workoutDao.touchPlanLastPerformed(it, now) }
    stateDao.deleteFor(sessionId)
    return WorkoutSummary(volume, duration, sets.size, reps, prHitsFrom(sessionId))
}
```

- 「继续训练」可从 `WorkoutSummary` 返回训练页（`sessionId` 不变）
- 分享卡片使用 Compose 绘制 View → `Bitmap` → `FileProvider` 分享（不依赖第三方库）

---

## 9. 后台任务（WorkManager）

Worker 全部集中在 `:core:data/worker`，统一在 `app` 的 `WorkManagerInitializer`（或默认 `Configuration.Provider`）注册。

| Worker | 触发 | 约束 | 唯一性 | 说明 |
|--------|------|------|--------|------|
| `SeedImportWorker` | 首次启动（`seed_flag = 0`） | 无 | `KEEP` | 导入 `exercise_seed.json`（单事务） |
| `RecycleBinCleanupWorker` | 每日 | 无 | `KEEP` | 清理 `deleted_at < now - 30d` 的计划与软删除会话 |
| `MediaGcScanWorker` | 每周 | `requiresCharging` + `requiresDeviceIdle` | `KEEP` | 扫描孤立媒体 → 写"待清理"记录 → 通知用户确认（**不静默删除**） |
| `HealthConnectSyncWorker` | 每日（若已授权） | `requiresBatteryNotLow` | `KEEP` | 拉取体重记录、推送训练记录 |
| `WidgetRefreshWorker` | 计划/会话变更时（OneTime） | 无 | `REPLACE` | 刷新 Glance 小组件 |
| `StatsCacheWarmupWorker` | 应用启动后延迟 30s | `requiresBatteryNotLow` | `KEEP` | 预热重统计查询（可选） |

```kotlin
// 注册示例
object Workers {
    const val RECYCLE_CLEANUP = "recycle_bin_cleanup"
    const val MEDIA_GC = "media_gc_scan"

    fun scheduleAll(workManager: WorkManager) {
        workManager.enqueueUniquePeriodicWork(
            RECYCLE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            MEDIA_GC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MediaGcScanWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true)
                    .build())
                .build(),
        )
    }
}
```

- 「回收站保留 30 天」的**唯一实现**就是 `RecycleBinCleanupWorker`；UI 显示的剩余天数由同一常量 `RETENTION_DAYS` 计算，保证一致
- Worker 中严禁持有 `Context` 长引用；使用 `HiltWorker` + `@AssistedInject`

---

## 10. 通知、权限与系统适配

### 10.1 通知渠道

```kotlin
object NotificationChannels {
    const val REST_RUNNING = "rest_running"      // IMPORTANCE_LOW，静默，常驻
    const val REST_FINISHED = "rest_finished"    // IMPORTANCE_HIGH，震动+声音
    const val WORKOUT_REMINDER = "workout_reminder"  // IMPORTANCE_HIGH
    const val PR_HIT = "pr_hit"                  // IMPORTANCE_DEFAULT
    const val BODY_REMINDER = "body_reminder"    // IMPORTANCE_DEFAULT
    const val MAINTENANCE = "maintenance"        // IMPORTANCE_LOW，清理建议
    const val INACTIVITY = "inactivity"          // IMPORTANCE_DEFAULT
}
```

- 渠道在 `Application.onCreate()` 创建；用户关闭后**不重复引导**
- 每个渠道的关闭状态需在设置页反映（`NotificationManagerCompat.getNotificationChannel().importance`）

### 10.2 权限申请时序（关键设计）

统一封装，避免散落：

```kotlin
// :core:designsystem/permission
@Composable
fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit,
): PermissionRequest

@Composable
fun rememberCameraOrPickerRequest(kind: MediaPickerKind): PermissionRequest
```

**通知权限策略（提升授予率）**
1. 应用启动时**不申请**
2. 用户首次开启任一通知功能（训练提醒/体重提醒等）时：
   - 先弹**自研解释弹窗**：「开启后可接收训练提醒与休息结束提示，数据仍只存本地」
   - 用户点「同意开启」→ 调用系统权限请求
   - 用户点「暂不」→ 不再打扰（记录 `hasDeclinedNotifications`）
3. 系统拒绝后 → 该功能入口显示「通知已关闭，点击前往设置」
4. `shouldShowRequestPermissionRationale == false` 且未授予 → 判定为永久拒绝，只提供跳转设置的入口，**不再弹系统弹窗**（避免死循环，PRD NFR-S）

### 10.3 媒体选择权限路径（无 GMS 兼容）

```kotlin
// :core:media/picker
sealed interface MediaPickerSource {
    data object PhotoPicker : MediaPickerSource          // 优先
    data object SafDocument : MediaPickerSource          // 降级
}

class MediaPickerResolver(context: Context) {
    /**
     * Photo Picker 可用性判定：
     * 1) ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)（API 33+ 或 GMS 回填）
     * 2) 否则降级 SAF：ACTION_OPEN_DOCUMENT + EXTRA_MIME_TYPES + takePersistableUriPermission
     */
    fun resolve(): MediaPickerSource =
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            MediaPickerSource.PhotoPicker
        } else {
            MediaPickerSource.SafDocument
        }
}
```

- **主路径零存储权限**：Photo Picker / SAF 均不需要 `READ_MEDIA_*`
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_VISUAL_USER_SELECTED` **仅在本项目自实现相册选择器时**才需要——本项目不实现自实现选择器，因此这些权限**可以不声明**，从而彻底绕开 Android 14 部分媒体访问的复杂度。若后续要加自定义相册网格，再按 PRD §3.3 补齐
- **相机路径已定：直接调用系统相机**（`ActivityResultContracts.CaptureVideo(uri)`，uri 由 `FileProvider` 提供）。因此 Manifest 中**不声明 `CAMERA`**，也不引入 CameraX。与拍摄相关的水印、网格线、倒计时等自定义拍摄功能**明确不做**。

**系统相机的实现注意点（必须处理，否则部分机型不可用）**

| 问题 | 处理 |
|------|------|
| 部分 ROM 相机忽略 `EXTRA_OUTPUT`，返回 `resultCode = OK` 但 uri 为空 | 校验 uri 与文件大小；为空则提示"未获取到视频，请重试或从相册导入" |
| 部分 ROM 相机取消时仍返回 OK | 双重校验：uri 非空 **且** 文件大小 > 0 **且** 可被 `MediaMetadataRetriever` 解析 |
| 系统相机无法控制分辨率/码率，可能产出 4K 大文件（远超 200MB） | 拍摄后走 §6.2 的 `transcodeIfLargerThanBytes`：超限时**不直接拒绝**，而是提示"视频较大（320MB），是否压缩到 720p 后导入？"（这是拍摄路径与相册路径的关键差异——拍摄的文件是用户刚拍的，不能简单拒绝） |
| 拍摄中的视频默认落在 `cacheDir` | 拍摄目标写 `cacheDir/capture/{uuid}.mp4`，导入成功后清理；启动时清理残留 |
| `FileProvider` 路径暴露 | `<paths>` 只暴露 `cache/capture/` 一个子目录，禁止暴露整个 `cacheDir` |
| 长时间录制中断（来电/内存） | 校验文件完整性（能被 `MediaMetadataRetriever` 打开），损坏则提示重新拍摄 |

> 这一取舍会显著简化 PRD §3.3 中描述的权限矩阵，是需求侧已确认的**收敛性优化**（见 ADR-05）。最终权限清单为：`POST_NOTIFICATIONS`、`VIBRATE`、`INTERNET`、`ACCESS_NETWORK_STATE`、`RECEIVE_BOOT_COMPLETED`、Health Connect 相关（可选）。**其余全部不需要。**

### 10.4 Android 版本适配清单

| 版本 | 适配项 | 实现位置 |
|------|-------|---------|
| API 26 | `java.time`（已内置）、通知渠道 | — |
| API 29 | Scoped Storage、`loadThumbnail` | `:core:media` |
| API 30 | 包可见性（本应用无需 `QUERY_ALL_PACKAGES`） | Manifest |
| API 31 | `SCHEDULE_EXACT_ALARM` 运行时判定 `canScheduleExactAlarms()`、`PendingIntent` 必须声明 `FLAG_IMMUTABLE` | 提醒模块 |
| API 33 | `POST_NOTIFICATIONS` 运行时权限 | §10.2 |
| API 34 | **本方案不涉及前台服务，故无需声明 FGS 类型**（见 §8.3.1）；若将来引入，类型必填且漏声明会崩溃 | §8.3.1 |
| API 35 | **强制 edge-to-edge**；预测性返回默认开启 | 全 UI |
| API 36 | 16KB 页大小（纯 Kotlin 无 so，自动满足）；行为变更复核 | 上架前核查 |

**国产 ROM 适配**
- 首次进入训练页时检测厂商（`Build.MANUFACTURER`）→ 若为小米/华为/OPPO/vivo 且应用不在电池优化白名单，展示一次性引导卡片
- 提供"如何设置后台运行"图文说明页（不同厂商不同路径）
- 休息计时器**不依赖服务存活做正确性判断**：剩余时间始终由 `endsAtEpochMs` 与当前时间计算，服务被杀只会导致"没有通知和提示音"，不会导致"计时错误"。这是关键的健壮性设计

---

## 11. Health Connect 集成

### 11.1 能力边界

| 方向 | 数据类型 | 用途 |
|------|---------|------|
| 写入 | `ExerciseSessionRecord` | 训练时长、类型、标题 |
| 写入 | `TotalCaloriesBurnedRecord` | 卡路里估算（可选，需明确标注估算） |
| 读取 | `WeightRecord` | 同步体重到身体数据 |
| 读取 | `StepsRecord` | 可选：展示当日步数（v1.1 之后） |

### 11.2 权限与清单

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />
<uses-permission android:name="android.permission.health.WRITE_TOTAL_CALORIES_BURNED" />

<!-- Android 14+ 需声明权限说明 Activity -->
<activity android:name=".health.HealthConnectRationaleActivity" android:exported="true">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity>

<!-- Android 13 及以下通过 Play 商店版本连接 -->
<activity-alias
    android:name="ViewPermissionUsageActivity"
    android:exported="true"
    android:targetActivity=".health.HealthConnectRationaleActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```

### 11.3 可用性探测与降级

```kotlin
sealed interface HealthConnectAvailability {
    data object Available : HealthConnectAvailability
    data object NotInstalled : HealthConnectAvailability      // 提示安装或隐藏入口
    data object UpdateRequired : HealthConnectAvailability
    data object Unsupported : HealthConnectAvailability       // 无 GMS：永久隐藏入口
}

suspend fun checkAvailability(): HealthConnectAvailability
```

- `Unsupported` 时设置页**不显示** Health Connect 分组（不显示灰置不可点，避免困惑）
- 写入训练：在 `finish()` 成功后触发；写入失败**不影响**本地数据保存（后台重试由 `HealthConnectSyncWorker` 负责，最多重试 3 次）
- 去重：本地保存 `healthRecordId`（新增可选列或存到 DataStore），避免重复写入同一次训练

---

## 12. 备份、导出与导入

### 12.1 完整备份包结构

```
fitnesspro_backup_YYYYMMDD_HHmmss.zip
├── manifest.json           # { schemaVersion, appVersion, exportedAt, deviceId(匿名), checksum }
├── data/
│   ├── plans.json
│   ├── plan_exercises.json
│   ├── plan_sets.json
│   ├── plan_folders.json
│   ├── exercises.json          # 仅 isCustom = true 的动作（内置库靠种子重建）
│   ├── exercise_muscles.json
│   ├── sessions.json
│   ├── workout_exercises.json
│   ├── workout_sets.json
│   ├── personal_records.json
│   ├── body_measurements.json
│   └── media_assets.json
└── media/
    ├── {uuid}.gif|webp|mp4|webm|mov
    └── thumbs/{uuid}.webp
```

### 12.2 导出实现

```kotlin
class ExportBackupUseCase @Inject constructor(
    private val daoBundle: DaoBundle,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    sealed interface Progress {
        data class WritingData(val step: Int, val total: Int) : Progress
        data class CopyingMedia(val done: Int, val total: Int) : Progress
        data object Finalizing : Progress
        data class Done(val bytes: Long) : Progress
    }

    /** 流式导出到 SAF 目标 URI，避免大文件进内存 */
    operator fun invoke(target: Uri): Flow<Progress> = flow {
        // ZipOutputStream(BufferedOutputStream(contentResolver.openOutputStream(target)))
        // 逐表写入 JSON（流式序列化，逐条 emit，避免整体构建大 List）
        // 逐个复制媒体文件（8KB buffer）
    }.flowOn(io)
}
```

### 12.3 导入实现（含安全校验）

导入必须按顺序通过 5 道校验，任一步失败即中止且**不产生任何数据**：

| 步骤 | 校验 | 失败处理 |
|------|------|---------|
| 1 | zip 完整性（`ZipInputStream` 可读、`manifest.json` 存在） | `ImportFailure("CORRUPT_ZIP")` |
| 2 | `schemaVersion` ≤ 当前支持版本 | `ImportFailure("VERSION_TOO_NEW")` |
| 3 | **Zip Slip 防护**：每个 entry 规范化后的路径必须在目标目录内 | `ImportFailure("ILLEGAL_PATH")` |
| 4 | **Zip 炸弹防护**：entry 数量 ≤ 20000、解压后总大小 ≤ 5GB、单文件 ≤ 500MB | `ImportFailure("TOO_LARGE")` |
| 5 | 数据引用完整性：`planId` / `exerciseDefinitionId` 等外键都能解析（缺失的外键降级为 null 或自动创建） | 报告可修复项，用户确认 |

```kotlin
private fun isSafeEntry(entry: ZipEntry, targetDir: File): Boolean {
    val canonical = File(targetDir, entry.name).canonicalFile
    return canonical.path.startsWith(targetDir.canonicalPath + File.separator)
}
```

**导入模式**
- **合并模式**（默认）：保留现有数据，新增导入内容；重名计划加后缀
- **覆盖模式**：清空后导入（需输入 `DELETE` 确认）

**导入执行**
- 媒体先全部解压到 `cacheDir/import_tmp/`，全部成功后再批量移动到 `filesDir/media/`（保证原子性）
- 数据库写入在**单个 Room 事务**内完成；失败则回滚并清理临时文件
- 导入完成后重建 FTS 索引

### 12.4 CSV 导入适配器

| 来源 | 列映射 | 特点 |
|------|--------|------|
| Strong | `Date, Workout Name, Exercise Name, Set Order, Weight, Reps, Distance, Seconds, Notes, Workout Notes, RPE` | 重量单位在表头（`Weight (kg)`） |
| Hevy | `title, start_time, end_time, exercise_title, set_index, weight_kg, reps, rpe, notes` | 时间格式 ISO8601 |
| FitNotes | `Date, Exercise, Category, Weight (kg), Weight (lb), Reps, Distance, Time, Comment` | 重量单位按行区分 |

```kotlin
interface CsvImportAdapter {
    val source: CsvSource
    fun detect(header: List<String>): Boolean
    fun parse(rows: Sequence<List<String>>): ImportPlan
}
```

- 通过表头自动识别来源；识别失败让用户手动选择
- 导入前展示预览：将新增 N 个计划、M 次训练、K 条记录、J 个需新建的自定义动作
- 本功能的验收门槛是"用真实导出文件测试"（取一份真实 Strong/Hevy 导出做固定 fixture）

---

## 13. 性能实现要点

### 13.1 达成 PRD NFR-P 的具体手段

| 指标 | 实现手段 |
|------|---------|
| 冷启动 ≤ 1.5s | ① `App Startup` 库拆分初始化；② Room 数据库在 `Application` 中**懒加载**（首次访问 DAO 时才建）；③ 种子导入与健康检查放 `WorkManager`；④ **Baseline Profile** 覆盖启动路径；⑤ R8 全量优化 + 资源压缩 |
| 计划列表 ≤ 300ms | ① 列表查询只取渲染所需字段（`PlanCardRow` 投影，不取 `SELECT *`）；② 动作数/组数/时长用 SQL `COUNT`/`SUM` 聚合，不加载明细；③ `estimatedDurationSeconds` 缓存字段；④ 超过 200 条启用 Paging 3 |
| 滚动 Jank < 1% | ① `LazyColumn` + 稳定 key；② 数据类 `@Immutable` + `ImmutableList`；③ Lambda 用方法引用或 `remember` 包裹，避免捕获不稳定状态；④ 缩略图固定尺寸避免重测量；⑤ 用 **Deferred read**（`Modifier.offset { }` / 自定义 `State` 读取推迟到绘制） |
| 搜索 ≤ 100ms | ① FTS4 索引；② 输入 debounce 200ms；③ 查询在 `Dispatchers.IO`，结果用 `stateIn` 缓存 |
| 缩略图 ≤ 200ms | ① **预生成**缩略图（不实时解码视频）；② Coil 内存 + 磁盘双层缓存；③ 固定 `Size`，禁止 `Size.Original` |
| 训练页操作 ≤ 100ms | ① **乐观更新**：先改 UI 状态，再异步落库；② 落库使用单事务批量；③ PR 判定异步，超时则跳过（不阻塞 UI） |
| 内存 ≤ 200MB | ① 媒体流式处理，`Bitmap` 按需采样；② 缩略图 `inSampleSize`；③ 避免在 `ViewModel` 持有大 List 的多个副本 |
| APK ≤ 15MB | ① R8 + `shrinkResources`；② 种子动作库压缩/按需（若 >2MB）；③ 图表自绘不引库；④ `resConfigs("zh", "en")` 限制语言资源 |

### 13.2 Baseline Profile

```kotlin
// benchmark/src/main/kotlin/BaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = "com.example.fitnesspro") {
        startActivityAndWait()
        device.findObject(By.res("home_start_workout")).click()   // 覆盖关键路径
        device.waitForIdle()
    }
}
```

- 生成物放 `app/src/main/baseline-prof.txt`（或 `baseline-prof/`），随 AAB 交付
- 每次 Release 前重新生成并回归对比

### 13.3 Macrobenchmark 门禁

```kotlin
@Test fun coldStartup() = benchmarkRule.measureRepeated(
    packageName = TARGET,
    metrics = listOf(StartupTimingMetric()),
    iterations = 10,
    startupMode = StartupMode.COLD,
) { pressHome(); startActivityAndWait() }

@Test fun planListScroll() = benchmarkRule.measureRepeated(
    packageName = TARGET,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
) {
    startActivityAndWait()
    device.findObject(By.res("tab_plans")).click()
    device.findObject(By.res("plan_list")).fling(Direction.DOWN)
}
```

**CI 门禁阈值**（相对基线退化超过阈值即失败）
- 冷启动：P90 不得比主干基线差 > 15%
- 滚动：`frameDurationCpuMs` P95 ≤ 16ms，jank 帧占比 < 1%

---

## 14. 测试工程

### 14.1 测试目录规范

```
core/domain/src/test/kotlin/            # 纯 JVM 单测（计算器、UseCase）
core/database/src/androidTest/kotlin/   # DAO 测试 + Migration 测试
core/media/src/test/kotlin/             # 媒体逻辑（用 Robolectric 或抽象 FileSystem）
feature/plans/src/test/kotlin/          # ViewModel 测试（Turbine）
feature/plans/src/androidTest/kotlin/   # Compose UI 测试
benchmark/src/main/kotlin/              # Macrobenchmark
```

### 14.2 必须的测试基地

```kotlin
// :core:testing —— 统一时间与数据构造
class MutableClock(private var currentMs: Long = 1_700_000_000_000) : Clock() {
    fun advanceBy(duration: Duration) { currentMs += duration.toMillis() }
    override fun instant() = Instant.ofEpochMilli(currentMs)
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: ZoneId) = this
}

object TestData {
    fun plan(name: String = "推日", exerciseCount: Int = 3): TrainingPlanWithExercises
    fun session(planId: Long, setCount: Int = 9): WorkoutSessionWithExercises
    fun set(weightKg: Double? = 60.0, reps: Int? = 8, type: SetType = SetType.NORMAL): WorkoutSet
}
```

### 14.3 覆盖率要求

| 模块 | 行覆盖 | 分支覆盖 |
|------|:---:|:---:|
| `:core:domain`（计算器 + UseCase） | ≥ 90% | ≥ 85% |
| `:core:data`（Repository） | ≥ 80% | ≥ 70% |
| `:core:database`（DAO） | 每个 public 方法至少 1 个测试 | — |
| Migration | 100% 迁移路径 | — |
| `feature:*`（ViewModel） | ≥ 70% | — |
| UI 层（Composable） | 不做覆盖率要求，以关键路径 UI 测试替代 | — |

### 14.4 必测用例清单（与 PRD §7.2 对应，这里给出**具体断言**）

| # | 用例 | 断言 |
|---|------|------|
| T1 | `VolumeCalculator` - WEIGHT_REPS | 60kg × 8 = 480.0 |
| T2 | `VolumeCalculator` - REPS_ONLY 引体 | 体重 75kg + 附加 10kg，8 次 = 680.0 |
| T3 | `VolumeCalculator` - WARMUP 排除 | 热身组容量 = 0.0（默认配置） |
| T4 | `VolumeCalculator` - TIME | 平板支撑 120s 容量 = 0.0，但计入总时长 |
| T5 | `OneRmCalculator` Epley | 100kg × 5 = 116.67（±0.01） |
| T6 | `OneRmCalculator` 边界 | reps = 0 → null；reps = 13 → null；reps = 1 → weight |
| T7 | `UnitConverter` 往返 | kg→lb→kg 误差 < 0.01 |
| T8 | `PlanDurationCalculator` 超级组 | 超级组内动作间休息不计入 |
| T9 | `PlanDurationCalculator` 区间解析 | "8-12" → 10.0；"AMRAP" → null |
| T10 | `PersonalRecordDetector` 首次 | 无历史 → 命中 MAX_WEIGHT 且 previousValue = null |
| T11 | `PersonalRecordDetector` 热身组 | setType = WARMUP → 返回空列表 |
| T12 | `PlateCalculator` | 目标 100kg，杆 20kg → 每侧 40kg；delta = 0 |
| T13 | `OrderIndexCalculator` | 连续插入 200 次触发 NEED_REINDEX |
| T14 | `FtsQueryBuilder` 中文 | "卧推" → "卧* 推*"，且能匹配"平板卧推" |
| T15 | DAO 级联删除 | 删除 plan → plan_exercise / plan_set 全部消失 |
| T16 | DAO RESTRICT | 删除被引用的 exercise_definition 抛异常或阻止 |
| T17 | Migration 1→2 | 旧数据行数一致，新列默认值正确 |
| T18 | 回收站 GC 安全性 | 回收站内计划引用的媒体**不被** `MediaGarbageCollector` 标记为孤儿 |
| T19 | 断点恢复 | 写入 session_state 后重建 Manager → 光标位置一致 |
| T20 | 休息计时恢复 | `endsAt` 在过去 → 返回 Idle 且清理状态行 |
| T21 | 休息计时异常时钟 | `endsAt` 超过 now + 15min → 清理并 Idle |
| T22 | Zip Slip | 含 `../../evil.txt` 的 zip → `ImportFailure("ILLEGAL_PATH")` |
| T23 | Zip 炸弹 | 20001 个 entry → `ImportFailure("TOO_LARGE")` |
| T24 | 导入事务回滚 | 中途失败 → 数据库行数与导入前一致 |
| T25 | ViewModel 乐观更新回滚 | 落库失败 → UiState 中该组回到 PENDING 且 error 非空 |
| T26 | 跨天训练归属 | 23:50 开始 00:30 结束 → epochDay 为开始日 |
| T27 | 时区无关性 | 同上用例在 UTC+8 / UTC-5 下结果一致 |

### 14.5 UI 测试关键路径

```
1. 首次启动 → Onboarding → 同意隐私 → 进入首页
2. 建计划 → 添加 3 个动作（搜索 + 自定义）→ 编辑组明细 → 保存
3. 开始训练 → 记录 3 组（含自动带入上次值）→ 触发 PR → 结束训练 → 校验总结数字
4. 休息计时 → 切后台 → 返回 → 计时仍正确
5. 删除计划 → Undo → 恢复成功
6. 导出备份 → 清空数据 → 导入 → 数据与媒体完整
```

### 14.6 截图测试

- 目标页面：PlanList、PlanDetail、Workout（含 PR 态与休息态）、Stats、Settings
- 变体矩阵：`浅色 / 深色` × `默认字体 / 200% 字体` × `360dp / 411dp / 600dp 宽`
- 基线图提交到版本控制；CI 上像素差异 > 0.1% 即失败

---

## 15. 编码规范

### 15.1 Kotlin 规范（Detekt + Ktlint 强制）

| 规则 | 值 |
|------|-----|
| 最大文件行数 | 400（Compose 文件放宽至 600） |
| 最大函数行数 | 60（Composable 放宽至 120） |
| 最大复杂度 | 15 |
| 最大函数参数 | 6（超过则用参数对象） |
| 禁止 | `GlobalScope`、`runBlocking`、`lateinit var`（除 DI 注入字段）、可变全局状态 |
| 命名 | 布尔值以 `is`/`has`/`should` 开头；UseCase 以动词开头 |
| 注释 | 只解释"为什么"，不解释"是什么"；复杂算法必须标注公式来源 |

### 15.2 Compose 规范

```kotlin
// ✅ 正确：状态提升 + 无状态组件 + 稳定参数
@Composable
fun SetRow(
    model: SetRowUiModel,          // @Immutable
    onToggleComplete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) { /* ... */ }

// ❌ 禁止：组件内部直接调用 ViewModel
@Composable
fun BadSetRow(vm: WorkoutViewModel) { val s by vm.state.collectAsStateWithLifecycle() }

// ❌ 禁止：在 Composable 中做重计算
@Composable fun Bad() { val sorted = hugeList.sortedBy { it.name } }   // 移到 ViewModel

// ✅ 正确：只在需要的地方读取 State，减少重组范围
Box(modifier = Modifier.offset { IntOffset(0, scrollState.value) })
```

**强制项**
- 所有 Composable 必须有 `modifier: Modifier = Modifier` 作为首个可选参数
- 列表项必须 `key = { it.id }`
- 所有 `TextField` 在 `LazyColumn` 内必须提升状态或用 `rememberSaveable`
- 禁止在 Composable 中创建 `SimpleDateFormat` / `DateTimeFormatter`（放顶层常量或用 `remember`）
- 预览：每个页面组件提供 `@Preview`，并额外提供 `@PreviewFontScale(2f)` 与 `@Preview(uiMode = UI_MODE_NIGHT_YES)`

### 15.3 资源规范

- **禁止硬编码用户可见文案**（Detekt 自定义规则 + Lint 拦截）
- 字符串命名：`screen_element_state`，如 `plan_edit_title_save_failed`
- 复数用 `<plurals>`：`set_count`
- 所有图标用 Vector Drawable，按 24dp 网格设计
- 颜色只从 `MaterialTheme.colorScheme` 或 `FitnessTheme.colors` 取

### 15.4 Git 工作流

| 项 | 约定 |
|---|------|
| 分支 | `main`（保护）← `develop` ← `feature/FP-123-xxx` |
| 提交 | Conventional Commits：`feat(workout): 支持超级组分组` |
| PR | 必须：① 关联任务号；② 通过 CI；③ 新逻辑有测试；④ schema 变更附迁移与测试；⑤ 至少 1 人 review |
| 保护 | `main` 禁止直接 push；禁止 force push |
| 版本 | `versionName` 语义化 `1.0.0`；`versionCode` 单调递增 |

---

## 16. CI / CD

### 16.1 CI 流水线（每次 PR）

```yaml
stages:
  - validate:   [ ktlintCheck, detekt, lintDebug, moduleDependencyCheck ]
  - test:       [ testDebugUnitTest, :core:database:connectedDebugAndroidTest ]   # 模拟器
  - build:      [ assembleDebug, assembleRelease(未签名仅验证) ]
  - report:     [ jacoco 覆盖率, 测试报告, detekt 报告 ]
```

**失败即阻断的规则**
- `lint` 有 error
- Detekt 有 error
- 单元测试失败或覆盖率低于 §14.3 阈值
- `moduleDependencyCheck` 发现违反 §2.2 的依赖规则
- 数据库 schema JSON 有变更但未提交新迁移

### 16.2 主干流水线（合并到 develop / 打 tag）

```yaml
stages:
  - fullTest:      [ 全量单测 + 全量 instrumented 测试 ]
  - benchmark:     [ macrobenchmark 对比基线，退化 > 阈值即失败 ]
  - baselineGen:   [ 重新生成 baseline profile ]
  - bundle:        [ bundleRelease (AAB) ]
  - internalTrack: [ 上传 Play 内部测试轨道 / 或输出 APK 到内测分发 ]
```

### 16.3 质量门禁（发布前）

| 门禁 | 阈值 |
|------|------|
| Crash-free sessions（内测 2 周） | ≥ 99.5% |
| ANR 率 | < 0.1% |
| Macrobenchmark 冷启动 | P90 ≤ 1.5s |
| 滚动 jank | < 1% |
| PRD AC-1 ~ AC-15 | 全部通过 |
| 无障碍走查（TalkBack 全流程） | 通过 |
| 真实第三方 CSV 导入 | 通过 |
| 国产 ROM 后台存活验证 | 通过（至少小米 + 华为真机） |

---

## 17. WBS 与里程碑

### 17.1 任务拆解

估时单位为**人日**（1 人日 = 6 小时有效编码），假设 1 名 Android 开发 + 0.3 名设计 + 0.2 名测试。

#### M0 骨架（目标：可编译、CI 绿）

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M0-1 | 核实并锁定依赖版本，建立 Version Catalog | — | 0.5 | `libs.versions.toml` |
| M0-2 | 建立 `build-logic` 与 6 个 Convention Plugin | M0-1 | 1.5 | 可复用构建逻辑 |
| M0-3 | 创建全部模块骨架与依赖声明 | M0-2 | 1 | 空模块可编译 |
| M0-4 | 依赖规则校验 Gradle 任务 | M0-3 | 1 | `moduleDependencyCheck` |
| M0-5 | Detekt + Ktlint + Lint 配置与基线 | M0-2 | 1 | 静态检查可跑 |
| M0-6 | 设计系统（色板/排版/间距/深色模式） | M0-3 | 2 | `FitnessTheme` |
| M0-7 | 导航骨架 + 空页面占位 | M0-6 | 1 | 可点击跳转 |
| M0-8 | CI 流水线（PR + 主干） | M0-5 | 1.5 | CI 全绿 |
| | **小计** | | **9.5** | |

#### M1 数据内核（目标：DAO 与迁移测试全绿）

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M1-1 | Room 实体 14 张表 + 索引 + 外键 | M0-3 | 2 | Entity 代码 |
| M1-2 | DAO：Plan/Exercise/Workout/Stats/PR/Body/Media | M1-1 | 3 | DAO 接口 |
| M1-3 | FTS4 实体与查询构造器 | M1-2 | 1 | 中文搜索可用 |
| M1-4 | 数据库类 + DI + `exportSchema` | M1-2 | 0.5 | `FitnessDatabase` |
| M1-5 | DAO 测试（含级联/RESTRICT/FTS） | M1-4 | 3 | 测试覆盖 |
| M1-6 | 迁移框架 + 首个迁移 + `MigrationTestHelper` | M1-4 | 1.5 | 迁移测试模板 |
| M1-7 | 种子数据：300 条动作 JSON + 导入 Worker | M1-4 | 3 | 动作库可用 |
| M1-8 | DataStore 偏好层（单位/主题/计时默认值） | M0-3 | 1 | `SettingsRepository` |
| M1-9 | 领域模型 + Entity↔Domain Mapper | M1-1 | 1.5 | `:core:model` |
| M1-10 | 计算器 6 件套 + 27 项单测 | M1-9 | 3 | `:core:domain` |
| M1-11 | Repository 层（Plan/Exercise/Workout/Stats） | M1-2 M1-8 | 3 | `:core:data` |
| | **小计** | | **22.5** | |

#### M2 计划与动作库（目标：PRD AC-1 通过）

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M2-1 | PlanList 页面（三态 + 搜索 + 分组 + 排序） | M1-11 M0-6 | 3 | 页面 |
| M2-2 | PlanEdit 页面（名称/描述/排期） | M1-11 | 1.5 | 页面 |
| M2-3 | 动作选择器（FTS + 筛选 + 最近使用 + 自定义） | M1-3 | 3 | BottomSheet |
| M2-4 | 动作条目编辑 + 组明细展开 | M1-11 | 3 | 组件 |
| M2-5 | 拖拽排序 + 无障碍上下移 | M2-4 | 2 | 组件 |
| M2-6 | 超级组编组 UI | M2-4 | 2 | 组件 |
| M2-7 | 自动保存（debounce 300ms）+ 退出确认 + 草稿恢复 | M2-2 | 2 | — |
| M2-8 | 删除/回收站/Undo | M1-11 | 2 | 页面 |
| M2-9 | 计划时长计算与展示 | M1-10 | 0.5 | — |
| M2-10 | ExerciseLibrary + ExerciseDetail（不含统计区） | M1-11 | 2.5 | 页面 |
| | **小计** | | **21.5** | |

#### M3 媒体（目标：PRD AC-3、AC-8 通过）

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M3-1 | Photo Picker + SAF 双路径选择器 | M0-3 | 1.5 | `MediaPickerResolver` |
| M3-2 | MediaImporter（校验/流式复制/原子写入/进度/取消） | M3-1 | 3 | `:core:media` |
| M3-3 | 缩略图生成（新老 API + WebP + 下采样） | M3-2 | 2 | — |
| M3-4 | 视频转码（Media3 Transformer + 进度） | M3-2 | 2 | — |
| M3-5 | 视频播放页（倍速/循环/逐帧/横屏/音频焦点） | M0-7 | 3 | 页面 |
| M3-6 | 动图加载（Coil GIF/WebP）+ 缩略图列表 | M3-3 | 1 | — |
| M3-7 | 外链资源（校验/WebView/浏览器降级） | M3-5 | 1.5 | — |
| M3-8 | 媒体 GC 扫描 + 清理确认页 + Worker | M3-2 | 2.5 | — |
| M3-9 | Backend 媒体空间统计 | M3-8 | 1 | — |
| | **小计** | | **17.5** | |

#### M4 训练执行（目标：PRD AC-2、AC-4、AC-5 通过；风险最高）

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M4-1 | StartWorkout 三种填充模式 + 空训练 | M1-11 | 2 | `StartWorkoutUseCase` |
| M4-2 | `WorkoutSessionManager` + 事务化 `logSet` | M1-11 | 4 | 引擎 |
| M4-3 | Workout 页面（LazyColumn 结构 + 动态表单 + 乐观更新） | M4-2 | 5 | 页面 |
| M4-4 | 上次成绩回显 + 自动带入（跨会话查询） | M4-2 | 2 | — |
| M4-5 | 组操作：跳过/加组/删组/复制/替换/临时动作 | M4-2 | 3 | — |
| M4-6 | 休息计时器：Controller + 倒计时通知（系统 Chronometer）+ 三 Action | M4-2 | 2.5 | — |
| M4-7 | 闹钟到点提醒（Receiver + 幂等去重 + 震动/音效/TTS） | M4-6 | 2 | — |
| M4-7b | 计时器持久化 + 进程重启恢复 + 开机清理 + 时钟异常兜底 | M4-6 M4-7 | 2 | — |
| M4-8 | 提示音/震动/TTS（不打断音乐） | M4-6 | 1.5 | — |
| M4-9 | 训练暂停/恢复 + 屏幕常亮 | M4-2 | 1 | — |
| M4-10 | 结束训练 + 汇总页 + 与上次对比 | M4-2 | 3 | — |
| M4-11 | PR 判定与庆祝动效 | M4-2 | 2 | — |
| M4-12 | 训练分享卡片生成 | M4-10 | 1.5 | — |
| M4-13 | 辅助工具：1RM 计算器 / 杠片计算器 / RPE | M1-10 | 2 | — |
| M4-14 | 渐进超负荷建议 | M1-10 | 1.5 | — |
| M4-15 | 进程死亡恢复专项测试 | M4-7 | 2 | 测试用例 |
| | **小计** | | **37.5** | |

#### M5 历史、统计、身体数据、设置、备份

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M5-1 | History 页面（日历 + 列表 + 筛选） | M1-11 | 3 | 页面 |
| M5-2 | SessionDetail（逐组复盘 + 编辑 + 删除） | M5-1 | 2.5 | 页面 |
| M5-3 | Stats 概览 + 容量趋势图（自绘） | M1-11 | 3 | 页面 |
| M5-4 | 肌群分布图 + 动作频率榜 | M5-3 | 2 | — |
| M5-5 | PR 列表页 | M1-11 | 1.5 | 页面 |
| M5-6 | 训练热力图 | M5-3 | 2 | — |
| M5-7 | 动作详情统计区（PR 卡 + 历史 + 趋势） | M5-3 | 2 | — |
| M5-8 | Body 页面（录入 + 趋势） | M1-11 | 2.5 | 页面 |
| M5-9 | Settings 页面（全部设置项） | M1-8 | 2.5 | 页面 |
| M5-10 | 完整备份导出 zip | M1-11 | 3 | — |
| M5-11 | 备份导入（5 道校验 + 事务 + 回滚） | M5-10 | 3.5 | — |
| M5-12 | 单计划导出/分享/接收 | M5-10 | 2 | — |
| M5-13 | CSV 导出 + 三源导入适配器 | M5-11 | 3 | — |
| M5-14 | Health Connect 集成（探测/授权/读写/降级） | M5-8 | 3 | — |
| M5-15 | 通知体系（7 渠道 + 权限时序） | M0-3 | 3 | — |
| M5-16 | 训练提醒（AlarmManager + 精确权限降级） | M5-15 | 2 | — |
| M5-17 | Onboarding 引导 | M0-6 | 2 | 页面 |
| M5-18 | Home 首页（今日计划 + 摘要） | M5-3 | 2 | 页面 |
| | **小计** | | **42.5** | |

#### M6 打磨与发布

| ID | 任务 | 依赖 | 估时 | 交付物 |
|----|------|------|:---:|--------|
| M6-1 | 无障碍专项（TalkBack + 对比度 + 大字体） | M2-M5 | 3 | — |
| M6-2 | 截图测试基线建立 | M2-M5 | 2 | — |
| M6-3 | Baseline Profile 生成与回归 | M4-2 | 1.5 | — |
| M6-4 | Macrobenchmark 门禁接入 CI | M6-3 | 1.5 | — |
| M6-5 | 国产 ROM 后台适配与引导页 | M4-6 | 2 | — |
| M6-6 | 设备矩阵回归（7 个 API × 5 厂商） | 全部 | 4 | 测试报告 |
| M6-7 | 隐私政策页 + 数据安全表单填写 | M5-9 | 1 | — |
| M6-8 | 应用图标/启动图/商店素材 | M0-6 | 2 | — |
| M6-9 | 内测分发 + 崩溃监控接入 + 2 周观察 | 全部 | 2 | — |
| | **小计** | | **19.5** | |

### 17.2 里程碑汇总

| 里程碑 | 内容 | 累计人日 | 建议周期（1.5 人并行） |
|--------|------|:---:|:---:|
| M0 骨架 | 工程 + CI + 设计系统 | 9.5 | 1.5 周 |
| M1 数据内核 | Room + 领域 + 计算器 + 种子 | 32 | 4 周 |
| M2 计划与动作库 | 计划 CRUD + 编排 + 搜索 | 53.5 | 6 周 |
| M3 媒体 | 导入 + 缩略图 + 播放 + GC | 71 | 8 周 |
| M4 训练执行 | **核心引擎 + 休息计时** | 108.5 | 12 周 |
| M5 记录与集成 | 历史/统计/身体/设置/备份/健康 | 151 | 16 周 |
| M6 打磨与发布 | 无障碍/性能/回归/上架 | 170.5 | 18 周 |

> **与 PRD §9 路线图的对应**：M0-M4 + M5 的 M5-1/2/9/10/11 构成 **v1.0 MVP**（约 115 人日，单人 5-6 个月，1.5 人并行约 4 个月）。M5 其余 + M6 构成 **v1.1**。这与 PRD 中"5-7 周 MVP"的估计差异较大——**PRD 的工期估算严重偏低**，因为原 PRD 未包含组级模型、休息计时器、断点恢复、动作库种子这三项高成本工作。建议以本文档的估时为准安排资源（已在 §18 ADR-09 记录）。

### 17.3 关键路径

```
M0-2 → M0-3 → M1-1 → M1-2 → M1-11 → M4-2 → M4-3 → M4-6 → M4-10 → M5-10 → M6-6
                                       ↑
                                    风险最高
```

- **M4-2（WorkoutSessionManager）是全局瓶颈**，任何延期直接顺延发布
- M1-7（300 条动作种子数据）是**可并行的人工密集任务**，建议最早期启动（可外包/众包）
- M3（媒体）与 M4（训练）可并行，但 M4-3 训练页需要 M3-6 的缩略图组件，存在弱依赖

---

## 18. 架构决策记录（ADR）

| ID | 决策 | 状态 | 理由与取舍 |
|----|------|:---:|-----------|
| ADR-01 | 采用组级记录模型（Session→Exercise→Set） | 已定 | 单条汇总记录无法支撑渐进超负荷与统计，是产品成立前提。代价：模型复杂度与 UI 复杂度显著上升，MVP 工期 +30% |
| ADR-02 | 动作库独立成表，动作名快照冗余 | 已定 | 支撑统计与检索；快照避免改名影响历史。代价：改名时历史显示旧名（**这是期望行为**，保持历史真实性） |
| ADR-03 | 重量内部统一存 kg（Double） | 已定 | 避免单位切换导致精度漂移与数据含义歧义。代价：每次显示需换算（可忽略） |
| ADR-04 | Room 2.x 而非 3.x | 已定 | 无 KMP 诉求，稳定优先。风险：未来若做 KMP 需迁移 |
| ADR-05 | **需求侧已确认**：主路径零存储权限（Photo Picker + SAF），相机直接调系统相机（`CaptureVideo`），不声明 `CAMERA`，不做自定义拍摄与水印 | 已定 | 权限矩阵收敛到 6 项以内，绕开 Android 14 部分媒体访问复杂度，降低用户心理门槛与商店合规成本。代价：① 无法提供自定义相册网格；② 系统相机不可控分辨率码率，拍摄大文件时依赖 §6.2 的转码路径兜底 |
| ADR-06 | 休息计时器**不使用前台服务**，改用「普通通知（系统自绘倒计时）+ `AlarmManager.setAndAllowWhileIdle` 到点提醒」 | 已定 | ① 免去 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `WAKE_LOCK` 三个权限与 `<service>` 声明，Play 审核零风险；② 不受 `shortService` 的 3 分钟硬超时限制，支持最长 900s 休息；③ 零后台耗电（无常驻服务、无 wakelock、无每秒刷新）；④ 通知与闹钟由系统托管，进程被杀后到点提醒不丢失，抗国产 ROM 清理能力强。代价：`setAndAllowWhileIdle` 在 Doze 下可能延迟数十秒——但训练中设备不休眠，且正确性由 `endsAtEpochMs` 保证，延迟仅影响提示音 |
| ADR-07 | 训练时长用 `elapsedRealtime` 而非墙钟 | 已定 | 用户改系统时间不影响时长。代价：跨进程/重启无法延续（故休息计时用墙钟 + 异常兜底） |
| ADR-08 | 媒体 GC 与回收站清理只提示、不静默删除用户文件 | 已定 | 用户数据不可静默丢失，宁可不自动清理。代价：需用户偶尔手动确认 |
| ADR-09 | 工期以本文档 §17 为准，PRD 的工期估算作废 | 已定 | 原估时遗漏组级模型、休息计时、断点恢复、种子库。建议向干系人同步风险 |
| ADR-10 | 统计聚合下沉 SQL，日/周边界在应用层计算 | 已定 | SQL 除法无法正确处理时区与夏令时；应用层用 `java.time` 更安全 |
| ADR-11 | 导入导出用 zip（含媒体）而非 v1.1 的"JSON + 手动重连" | 已定 | 手动重连是把工程债转嫁用户，且实际上无人会做。代价：实现量增加约 6 人日 |
| ADR-12 | 图表自绘 Canvas 而非引入图表库 | 已定 | 图表类型有限（折线/饼/条形/热力），自绘可减小 APK 且完全可控。代价：开发时间增加 |
| ADR-13 | 崩溃报告与统计默认关闭（opt-in） | 已定 | 与 PRD 隐私承诺一致。代价：线上问题发现能力下降，需靠内测与用户反馈补偿 |
| ADR-14 | 不实现自实现相册选择器 | 已定 | 见 ADR-05。若产品坚持，则需补 `READ_MEDIA_*` 全量权限与 Android 14 reselect 流程（+3 人日） |

---

## 19. 完成定义（Definition of Done）

一个任务只有同时满足以下全部条件才算完成：

- [ ] 代码通过 Ktlint / Detekt / Lint（无 error，无新增 warning）
- [ ] 新增或修改的**业务逻辑**有对应单元测试，覆盖率满足 §14.3
- [ ] 新增 DAO 方法有 DAO 测试
- [ ] 若涉及数据库 schema 变更：迁移已编写 + 迁移测试通过 + schema JSON 已提交
- [ ] ViewModel 有测试（状态流转 + 错误路径）
- [ ] UI 新增页面有 `@Preview`（含深色模式与 200% 字体）与关键路径 UI 测试
- [ ] 无硬编码用户可见文案；无未处理的权限拒绝路径
- [ ] 所有新图标有 `contentDescription`
- [ ] 在 API 26 与 API 36 模拟器上手动验证主路径
- [ ] 无内存泄漏（`LeakCanary` 无新增报告）
- [ ] 已更新本技术文档的相关章节（如接口/表结构变化）
- [ ] PR 已 review 并 squash 合并

---

## 20. 附录

### 20.1 常用命令

```bash
# 生成 Room schema
./gradlew :core:database:kspDebugKotlin

# 只跑单元测试
./gradlew testDebugUnitTest

# DAO / 迁移测试（需模拟器或真机）
./gradlew :core:database:connectedDebugAndroidTest

# 静态检查
./gradlew ktlintCheck detekt lintDebug

# 依赖规则校验
./gradlew moduleDependencyCheck

# 生成 baseline profile
./gradlew :app:generateBaselineProfile

# Macrobenchmark
./gradlew :benchmark:connectedBenchmarkAndroidTest

# 截图测试（Roborazzi）
./gradlew :feature:plans:recordRoborazziDebug      # 生成/更新基线
./gradlew :feature:plans:verifyRoborazziDebug      # 校验

# Release 包
./gradlew bundleRelease
```

### 20.2 开工前检查清单

- [ ] 依赖版本已核实并写入 `libs.versions.toml`，本文档 §1.2 已更新核实日期
- [ ] `targetSdk` 满足当前商店上架要求
- [ ] 关键依赖方向规则已由 Gradle 任务强制
- [ ] `dataExtractionRules` 已排除 `media/`、`thumbs/`、`cache/`
- [ ] 已确认**无任何前台服务声明**（休息计时器走 §8.3 的零后台方案，ADR-06）；若后续引入 FGS，必须先确认类型与审核要求
- [ ] 相机路径确认为系统相机（`CaptureVideo`），Manifest 中**不声明 `CAMERA`**（ADR-05）
- [ ] 300 条动作种子数据已启动采集（并行任务，最早启动）
- [ ] 至少 2 台国产 ROM 真机 + 1 台无 GMS 真机可用于测试
- [ ] 干系人已知晓 §17.2 的工期调整（ADR-09）

### 20.3 术语缩写

| 缩写 | 含义 |
|------|------|
| PRD | Product Requirements Document |
| ADR | Architecture Decision Record |
| TDD | Technical Design Document（本文档） |
| FTS | Full-Text Search |
| FGS | Foreground Service |
| SAF | Storage Access Framework |
| WBS | Work Breakdown Structure |
| e1RM | estimated One Rep Max |
| PR | Personal Record |
| RPE / RIR | Rate of Perceived Exertion / Reps In Reserve |

---

## 文档修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | 2026-10-07 | 首版。基于 PRD v2.0 输出工程结构、架构、数据层（14 张表 DDL + DAO）、领域层（6 个计算器 + 27 项测试用例）、媒体流水线、UI 与导航、训练执行引擎（含休息计时器与断点恢复）、后台任务、通知与权限（含权限收敛优化 ADR-05）、Health Connect、备份导出导入、性能实现、测试工程、编码规范、CI/CD、WBS（170.5 人日 / 6 个里程碑）、14 条 ADR、DoD |





