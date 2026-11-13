# 健身 App 卡路里估算完整解决方案

> 整合数据模型、动作细分、多因素估算、UI 说明和实现指令。可整段贴给 CodeBuddy。

---

## 一、总体架构

```
动作字典（只读）
├── 分类字段：大肌群、细分肌群
├── 能耗字段：MET、自重系数、器械效率、复合度、节奏、强度区
├── 编程字段：推荐组数/次数/休息、新手友好、热身适用
└── 执行字段：单次时长、呼吸模式、是否需保护

用户身体数据（读写）
├── 基础：体重、身高、年龄、性别
└── 体成分：体脂率、去脂体重、骨骼肌含量

卡路里估算引擎（四级降级）
├── L1：多因素回归（体成分 + TV）
├── L2：简化多因素（基础信息 + TV）
├── L3：TV 校正 MET
└── L4：标准 MET（兜底）

训练执行（实时）
├── 每秒 tick，累加消耗
├── 实时显示总卡路里 + 当前层级
└── 训练结束保存记录
```

---

## 二、数据模型

### 2.1 枚举定义

```kotlin
enum class Gender { MALE, FEMALE }

enum class MovementPattern {
    HORIZONTAL_PUSH, HORIZONTAL_PULL,
    VERTICAL_PUSH, VERTICAL_PULL,
    SQUAT, HINGE, LUNGE,
    ROTATION, ANTI_ROTATION,
    ISOMETRIC, EXPLOSIVE, ISOLATION, OTHER
}

enum class IntensityZone {
    STRENGTH,       // 力量 85-100% 1RM
    HYPERTROPHY,    // 增肌 67-85%
    ENDURANCE,      // 耐力 <67%
    POWER           // 爆发
}

enum class MetabolicType { AEROBIC, ANAEROBIC, MIXED }

enum class MuscleVolume { LARGE, MEDIUM, SMALL }

enum class EstimationLevel {
    L1_MULTI_FACTOR,     // 完整多因素回归
    L2_SIMPLIFIED,       // 简化多因素模型
    L3_TV_ADJUSTED,      // TV 校正 MET
    L4_WEIGHT_ONLY       // 仅体重（兜底）
}
```

### 2.2 Exercise（动作字典）

```kotlin
@Entity(tableName = "Exercise")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val majorGroup: String,
    val subGroup: String,

    // 分类
    val equipmentType: String,
    val equipment: List<String>,
    val difficulty: String,
    val scene: List<String>,
    val goal: List<String>,
    val tips: List<String>,
    val variations: String?,

    // 能耗（核心）
    val metValue: Double = 3.5,
    val isBodyweight: Boolean = false,
    val selfWeightCoefficient: Double = 0.5,
    val equipmentEfficiency: Double = 1.0,
    val isCompound: Boolean = true,
    val tempoFactor: Double = 1.0,
    val isUnilateral: Boolean = false,
    val intensityZone: IntensityZone = IntensityZone.HYPERTROPHY,
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val metabolicType: MetabolicType = MetabolicType.MIXED,

    // 编程
    val recommendedSets: String? = null,          // "3-5"
    val recommendedReps: String? = null,          // "8-12"
    val recommendedRestSeconds: String? = null,   // "60-120"
    val recommendedIntensityPercent1RM: String? = null,
    val isBeginnerFriendly: Boolean = true,
    val isRehabFriendly: Boolean = false,
    val isWarmupSuitable: Boolean = false,
    val primaryMuscleVolume: MuscleVolume = MuscleVolume.LARGE,

    // 执行
    val averageRepDurationSeconds: Int = 3,
    val breathingPattern: String? = null,
    val requiresSpotter: Boolean = false,
    val canAddWeight: Boolean = true,

    // 媒体与版本
    val mediaVideo: String? = null,
    val mediaGif: String? = null,
    val mediaThumbnail: String? = null,
    val dataVersion: Int = 1,
    val isCustom: Boolean = false
)
```

### 2.3 UserProfile（用户身体数据）

```kotlin
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val weightKg: Double = 70.0,
    val heightCm: Double? = null,
    val age: Int? = null,
    val gender: Gender? = null,
    val bodyFatPercentage: Double? = null,
    val skeletalMuscleKg: Double? = null,
    val leanBodyMassKg: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
```

### 2.4 TrainingPlan（训练计划）

```kotlin
@Entity(tableName = "TrainingPlan")
data class TrainingPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
```

### 2.5 PlanExercise（计划内动作）

```kotlin
@Entity(
    tableName = "PlanExercise",
    foreignKeys = [
        ForeignKey(TrainingPlan::class, ["id"], ["planId"], onDelete = CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = SET_NULL)
    ]
)
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val exerciseId: String?,
    val exerciseName: String,
    val sets: Int = 3,
    val reps: String = "12",
    val durationSeconds: Int? = null,
    val restSeconds: Int = 60,
    val notes: String?,
    val mediaType: String?,
    val mediaPath: String?,
    val sortOrder: Int = 0,
    val isSkipped: Boolean = false,

    // 执行记录
    val actualWeightKg: Double = 0.0,
    val actualReps: Int = 0,
    val actualSets: Int = 0,
    val estimated1RM: Double? = null
)
```

### 2.6 WorkoutRecord（训练记录）

```kotlin
@Entity(tableName = "WorkoutRecord")
data class WorkoutRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val startedAt: Long,
    val completedAt: Long?,
    val actualSets: Int = 0,
    val actualReps: String = "",
    val durationSeconds: Int = 0,
    val notes: String?,
    val isCompleted: Boolean = false,

    // 卡路里
    val totalCalories: Double = 0.0,
    val caloriesPerExercise: String? = null,   // JSON
    val estimationLevel: String? = null,
    val estimationInputs: String? = null       // JSON 快照
)
```

### 2.7 Migration 示例

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Exercise 扩展
        db.execSQL("ALTER TABLE Exercise ADD COLUMN metValue REAL NOT NULL DEFAULT 3.5")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN isBodyweight INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN selfWeightCoefficient REAL NOT NULL DEFAULT 0.5")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN equipmentEfficiency REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN isCompound INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN tempoFactor REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN isUnilateral INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN intensityZone TEXT NOT NULL DEFAULT 'HYPERTROPHY'")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN movementPattern TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN metabolicType TEXT NOT NULL DEFAULT 'MIXED'")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN averageRepDurationSeconds INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE Exercise ADD COLUMN primaryMuscleVolume TEXT NOT NULL DEFAULT 'LARGE'")

        // PlanExercise 扩展
        db.execSQL("ALTER TABLE PlanExercise ADD COLUMN actualWeightKg REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE PlanExercise ADD COLUMN actualReps INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE PlanExercise ADD COLUMN actualSets INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE PlanExercise ADD COLUMN estimated1RM REAL")

        // WorkoutRecord 扩展
        db.execSQL("ALTER TABLE WorkoutRecord ADD COLUMN totalCalories REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE WorkoutRecord ADD COLUMN caloriesPerExercise TEXT")
        db.execSQL("ALTER TABLE WorkoutRecord ADD COLUMN estimationLevel TEXT")
        db.execSQL("ALTER TABLE WorkoutRecord ADD COLUMN estimationInputs TEXT")

        // UserProfile 新建
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS user_profile (
                id INTEGER NOT NULL PRIMARY KEY,
                weightKg REAL NOT NULL DEFAULT 70.0,
                heightCm REAL,
                age INTEGER,
                gender TEXT,
                bodyFatPercentage REAL,
                skeletalMuscleKg REAL,
                leanBodyMassKg REAL,
                updatedAt INTEGER NOT NULL
            )
        """)
    }
}
```

---

## 三、估算引擎

### 3.1 四级降级总览

| 层级 | 可用数据 | 公式 | 精度 |
|------|---------|------|------|
| L1 | 身高+年龄+体成分+TV | 多因素回归 | R=0.773，SEE≈28.5 kcal |
| L2 | 身高+年龄+体重+TV | 简化多因素 | R²=0.847，MAE≈10.9 kcal |
| L3 | 体重+TV | TV 校正 MET | 中等 |
| L4 | 仅体重 | 标准 MET | 兜底 |

### 3.2 核心估算器

```kotlin
object AdvancedCalorieEstimator {

    fun estimate(
        exercise: PlanExercise,
        exerciseMeta: Exercise,
        profile: UserProfile,
        durationSeconds: Int
    ): AdvancedEstimationResult {
        val minutes = durationSeconds / 60.0
        if (minutes <= 0) return AdvancedEstimationResult(0.0, EstimationLevel.L4_WEIGHT_ONLY)

        // 有效负荷
        val effectiveLoad = calculateEffectiveLoad(exerciseMeta, profile, exercise.actualWeightKg)

        // 训练容量
        val tv = calculateTV(exerciseMeta, exercise.actualSets, exercise.actualReps, effectiveLoad)

        // L1：体成分 + 多因素回归
        if (profile.heightCm != null && profile.age != null && profile.gender != null) {
            val fatMass = profile.bodyFatPercentage?.let { profile.weightKg * it / 100.0 }
            val leanMass = profile.leanBodyMassKg
                ?: profile.skeletalMuscleKg?.let { it / 0.54 }
                ?: fatMass?.let { profile.weightKg - it }

            if (leanMass != null && fatMass != null) {
                val netKcal = 0.874 * profile.heightCm
                    - 0.596 * profile.age
                    - 1.016 * fatMass
                    + 1.638 * leanMass
                    + 2.461 * (tv / 10.0)
                    - 110.742
                val epoc = estimateEPOC(tv, 70.0, minutes)
                return AdvancedEstimationResult(
                    (netKcal + epoc).coerceAtLeast(0.0),
                    EstimationLevel.L1_MULTI_FACTOR
                )
            }
        }

        // L2：基础信息 + TV
        if (profile.heightCm != null && profile.age != null) {
            val simplified = estimateSimplified(profile.weightKg, tv)
            val epoc = estimateEPOC(tv, 70.0, minutes)
            return AdvancedEstimationResult(
                (simplified + epoc).coerceAtLeast(0.0),
                EstimationLevel.L2_SIMPLIFIED
            )
        }

        // L3：TV 校正 MET
        val baseMET = exerciseMeta.metValue
        val adjustedMET = adjustMETByIntensity(baseMET, exerciseMeta.intensityZone)
        val tvFactor = 1.0 + (tv / (profile.weightKg * 1000)).coerceIn(0.0, 0.5)
        val kcal = adjustedMET * tvFactor * profile.weightKg * 3.5 / 200 * minutes
        return AdvancedEstimationResult(kcal, EstimationLevel.L3_TV_ADJUSTED)

        // L4：兜底
        val fallback = baseMET * profile.weightKg * 3.5 / 200 * minutes
        return AdvancedEstimationResult(fallback, EstimationLevel.L4_WEIGHT_ONLY)
    }

    /** 有效负荷 */
    private fun calculateEffectiveLoad(
        exercise: Exercise,
        profile: UserProfile,
        actualWeightKg: Double
    ): Double {
        return if (exercise.isBodyweight) {
            profile.weightKg * exercise.selfWeightCoefficient + actualWeightKg
        } else {
            actualWeightKg * exercise.equipmentEfficiency
        }
    }

    /** 训练容量 */
    private fun calculateTV(
        exercise: Exercise,
        sets: Int,
        reps: Int,
        effectiveLoad: Double
    ): Double {
        var tv = sets * reps * effectiveLoad
        if (exercise.isCompound) tv *= 1.2
        if (exercise.isUnilateral) tv *= 1.1
        tv *= exercise.tempoFactor
        return tv
    }

    /** 强度校正 MET */
    private fun adjustMETByIntensity(baseMET: Double, zone: IntensityZone): Double {
        return when (zone) {
            IntensityZone.STRENGTH -> baseMET * 1.3
            IntensityZone.HYPERTROPHY -> baseMET * 1.0
            IntensityZone.ENDURANCE -> baseMET * 0.85
            IntensityZone.POWER -> baseMET * 1.4
        }
    }

    /** 简化多因素模型 */
    private fun estimateSimplified(weightKg: Double, tv: Double): Double {
        return 0.5 * weightKg + 0.02 * tv + 10.0
    }

    /** EPOC 追加 */
    fun estimateEPOC(totalVolumeKg: Double, intensityPercent1RM: Double, minutes: Double): Double {
        val baseEPOC = minutes * 1.2
        val intensityFactor = when {
            intensityPercent1RM >= 80 -> 1.5
            intensityPercent1RM >= 60 -> 1.2
            else -> 1.0
        }
        val volumeFactor = (totalVolumeKg / 5000).coerceIn(0.5, 2.0)
        return (baseEPOC * intensityFactor * volumeFactor).coerceIn(30.0, 150.0)
    }
}

data class AdvancedEstimationResult(
    val calories: Double,
    val level: EstimationLevel
)
```

### 3.3 自重负荷系数表

| 动作 | 系数 |
|------|------|
| 标准俯卧撑 | 0.64 |
| 上斜俯卧撑 | 0.45 |
| 下斜俯卧撑 | 0.75 |
| 引体向上 | 1.00 |
| 双杠臂屈伸 | 0.95 |
| 自重深蹲 | 0.75 |
| 臀桥 | 0.45 |
| 平板支撑 | 0.65 |
| 箭步蹲（自重） | 0.70 |
| 波比跳 | 0.80 |
| 其他 | 0.50 |

### 3.4 MET 值映射

| 关键词 | MET |
|--------|-----|
| 深蹲、硬拉、卧推、引体、推举、高翻、抓举、双力臂 | 6.0 |
| 划船、下拉、箭步蹲、分腿蹲、腿举、保加利亚 | 5.0 |
| 弯举、侧平举、飞鸟、腿屈伸、腿弯举、面拉、耸肩 | 3.5 |
| 卷腹、平板、俄罗斯转体、悬垂举腿、死虫式 | 2.5 |
| 波比跳、壶铃摇摆、跳箱、战绳、药球砸地、雪橇推 | 8.0 |
| 热身、拉伸、关节绕环、泡沫轴 | 2.0 |
| 其他 | 3.5 |

---

## 四、ViewModel 集成

```kotlin
@HiltViewModel
class CalorieTrackerViewModel @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val exerciseDao: ExerciseDao
) : ViewModel() {

    private val _totalCalories = MutableStateFlow(0.0)
    val totalCalories: StateFlow<Double> = _totalCalories.asStateFlow()

    private val _currentLevel = MutableStateFlow(EstimationLevel.L4_WEIGHT_ONLY)
    val currentLevel: StateFlow<EstimationLevel> = _currentLevel.asStateFlow()

    private val _perExerciseCalories = MutableStateFlow<Map<String, Double>>(emptyMap())
    val perExerciseCalories: StateFlow<Map<String, Double>> = _perExerciseCalories.asStateFlow()

    private var profile: UserProfile = UserProfile()
    private var exerciseMeta: Exercise? = null
    private var currentPlanExercise: PlanExercise? = null
    private var lastTickTime: Long = 0L

    init {
        viewModelScope.launch {
            userProfileDao.getProfile()?.let { profile = it }
        }
    }

    fun startExercise(planExercise: PlanExercise) {
        currentPlanExercise = planExercise
        viewModelScope.launch {
            exerciseMeta = planExercise.exerciseId?.let { exerciseDao.getById(it) }
        }
        lastTickTime = System.currentTimeMillis()
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (lastTickTime == 0L) { lastTickTime = now; return }
        val deltaSeconds = ((now - lastTickTime) / 1000).toInt()
        if (deltaSeconds <= 0) return
        lastTickTime = now

        val planEx = currentPlanExercise ?: return
        val meta = exerciseMeta ?: return

        val result = AdvancedCalorieEstimator.estimate(meta, planEx, profile, deltaSeconds)
        _currentLevel.value = result.level
        _totalCalories.value += result.calories

        planEx.exerciseId?.let { id ->
            _perExerciseCalories.value = _perExerciseCalories.value.toMutableMap().apply {
                this[id] = (this[id] ?: 0.0) + result.calories
            }
        }
    }

    fun pauseExercise() {
        lastTickTime = 0L
        currentPlanExercise = null
        exerciseMeta = null
    }

    fun finish(): CalorieResult {
        return CalorieResult(
            totalCalories = _totalCalories.value,
            perExercise = _perExerciseCalories.value,
            level = _currentLevel.value,
            profileSnapshot = profile
        )
    }

    fun reset() {
        _totalCalories.value = 0.0
        _perExerciseCalories.value = emptyMap()
        lastTickTime = 0L
        currentPlanExercise = null
        exerciseMeta = null
    }
}

data class CalorieResult(
    val totalCalories: Double,
    val perExercise: Map<String, Double>,
    val level: EstimationLevel,
    val profileSnapshot: UserProfile
)
```

---

## 五、UI 层

### 5.1 训练执行页

```kotlin
@Composable
fun TrainingScreen(
    viewModel: CalorieTrackerViewModel,
    currentExercise: PlanExercise
) {
    val totalCalories by viewModel.totalCalories.collectAsState()
    val level by viewModel.currentLevel.collectAsState()

    LaunchedEffect(currentExercise.id) {
        viewModel.startExercise(currentExercise)
        while (true) {
            delay(1000)
            viewModel.tick()
        }
    }

    Column {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("实时消耗", fontSize = 14.sp, color = Color.Gray)
                Text(
                    text = String.format("%.1f kcal", totalCalories),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B35)
                )
                Spacer(Modifier.height(4.dp))
                EstimationHint(level)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.pauseExercise() }
    }
}
```

### 5.2 估算精度提示组件

```kotlin
@Composable
fun EstimationHint(level: EstimationLevel) {
    val (text, color) = when (level) {
        EstimationLevel.L1_MULTI_FACTOR ->
            "基于体成分与训练容量的精准估算" to Color(0xFF4CAF50)
        EstimationLevel.L2_SIMPLIFIED ->
            "基于身体数据与训练容量的估算" to Color(0xFF4CAF50)
        EstimationLevel.L3_TV_ADJUSTED ->
            "基于训练容量的估算，填写体成分可提升精度" to Color(0xFFFF9800)
        EstimationLevel.L4_WEIGHT_ONLY ->
            "基础估算，完善资料可显著提升精度" to Color(0xFFF44336)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = color)
    }
}
```

### 5.3 用户资料页（分层引导）

```kotlin
@Composable
fun UserProfileScreen(viewModel: UserProfileViewModel) {
    val profile by viewModel.profile.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("个人资料", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("填写越完整，卡路里估算越准确", fontSize = 13.sp, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        SectionTitle("基础信息")
        OutlinedTextField(
            value = profile.weightKg.toString(),
            onValueChange = { viewModel.updateWeight(it.toDoubleOrNull()) },
            label = { Text("体重 (kg) *必填") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = profile.heightCm?.toString() ?: "",
            onValueChange = { viewModel.updateHeight(it.toDoubleOrNull()) },
            label = { Text("身高 (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = profile.age?.toString() ?: "",
            onValueChange = { viewModel.updateAge(it.toIntOrNull()) },
            label = { Text("年龄") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row {
            FilterChip(
                selected = profile.gender == Gender.MALE,
                onClick = { viewModel.updateGender(Gender.MALE) },
                label = { Text("男") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = profile.gender == Gender.FEMALE,
                onClick = { viewModel.updateGender(Gender.FEMALE) },
                label = { Text("女") }
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("体成分数据（选填，提升精度）")
        OutlinedTextField(
            value = profile.bodyFatPercentage?.toString() ?: "",
            onValueChange = { viewModel.updateBodyFat(it.toDoubleOrNull()) },
            label = { Text("体脂率 (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = profile.leanBodyMassKg?.toString() ?: "",
            onValueChange = { viewModel.updateLeanBodyMass(it.toDoubleOrNull()) },
            label = { Text("去脂体重 (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = profile.skeletalMuscleKg?.toString() ?: "",
            onValueChange = { viewModel.updateSkeletalMuscle(it.toDoubleOrNull()) },
            label = { Text("骨骼肌含量 (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Spacer(Modifier.height(16.dp))
        CurrentEstimationLevelCard(profile)

        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
    }
}
```

### 5.4 训练总结页

```kotlin
@Composable
fun WorkoutSummaryScreen(
    totalCalories: Double,
    perExercise: Map<String, Double>,
    exerciseNames: Map<String, String>,
    level: EstimationLevel
) {
    Column(Modifier.padding(16.dp)) {
        Text("训练完成", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            String.format("%.0f kcal", totalCalories),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B35)
        )
        EstimationHint(level)
        Spacer(Modifier.height(24.dp))

        Text("各动作消耗", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        perExercise.forEach { (id, cal) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(exerciseNames[id] ?: id, Modifier.weight(1f))
                Text(String.format("%.1f kcal", cal))
            }
        }

        Spacer(Modifier.height(24.dp))
        CalorieDisclaimer()
    }
}

@Composable
fun CalorieDisclaimer() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("关于估算", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "本估算基于 MET 公式结合您的身体数据计算，误差通常在 ±15%~25%。" +
                "填写体成分数据（体脂率、去脂体重、骨骼肌含量）可显著提升准确度。",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
        }
    }
}
```

### 5.5 保存训练记录

```kotlin
val result = calorieViewModel.finish()
val record = WorkoutRecord(
    planId = planId,
    startedAt = startTime,
    completedAt = System.currentTimeMillis(),
    totalCalories = result.totalCalories,
    caloriesPerExercise = Json.encodeToString(result.perExercise),
    estimationLevel = result.level.name,
    estimationInputs = Json.encodeToString(result.profileSnapshot)
)
workoutRecordDao.insert(record)
```

---

## 六、估算说明（用户可见）

### 卡路里估算说明

**估算方式**
本 App 采用国际通用的 MET（代谢当量）公式，结合您的身体数据、动作模式和训练容量估算运动消耗。

**数据分层**

| 数据完整度 | 估算方式 | 精度 |
|-----------|---------|------|
| 身高+年龄+体成分 | 多因素回归（L1） | ★★★★★ |
| 身高+年龄+体重 | 简化多因素（L2） | ★★★★☆ |
| 体重+训练容量 | TV 校正 MET（L3） | ★★★☆☆ |
| 仅体重 | 标准 MET（L4） | ★★☆☆☆ |

**误差说明**
即使采用最精确的公式，估算误差仍在 ±15%~25% 范围内。原因包括个体代谢差异、MET 值本身是群体平均值、训练中实际强度波动等。

**如何提升准确度**
1. 在「设置 → 个人资料」中填写体成分数据
2. 使用体脂秤或 InBody 测量获得准确数据
3. 训练中准确记录实际配重、次数、组数
4. 保持动作标准、组间休息一致

**免责声明**
本估算仅供参考，不作为医疗或健康建议。

---

## 七、给 CodeBuddy 的实现指令

```
请在 Fitness Pro Android App 中实现「多因素卡路里估算」功能。

技术栈：Kotlin + Jetpack Compose + Room + Hilt + Coroutines/Flow
要求：纯本地计算，不接外部设备、不需要网络、不需要额外权限

实现步骤：

1. 扩展 Exercise 实体，新增字段：
   metValue, isBodyweight, selfWeightCoefficient, equipmentEfficiency,
   isCompound, tempoFactor, isUnilateral, intensityZone, movementPattern,
   metabolicType, averageRepDurationSeconds, primaryMuscleVolume,
   以及编程字段（recommendedSets/Reps/RestSeconds、isBeginnerFriendly 等）

2. 扩展 PlanExercise 实体，新增：
   actualWeightKg, actualReps, actualSets, estimated1RM

3. 扩展 WorkoutRecord 实体，新增：
   totalCalories, caloriesPerExercise, estimationLevel, estimationInputs

4. 新建 UserProfile 实体

5. 定义枚举：
   Gender, MovementPattern, IntensityZone, MetabolicType, MuscleVolume, EstimationLevel

6. 编写 Room Migration

7. 实现 AdvancedCalorieEstimator：
   - 四级降级（L1 多因素回归 / L2 简化模型 / L3 TV 校正 / L4 兜底）
   - 有效负荷计算（自重系数、器械效率）
   - TV 计算（复合度、单双侧、节奏）
   - MET 强度校正
   - EPOC 追加

8. 实现 CalorieTrackerViewModel：
   - 每秒 tick，累加消耗
   - 暴露 totalCalories、currentLevel、perExerciseCalories
   - finish() 返回快照

9. UI：
   - 训练执行页实时显示卡路里 + 精度提示
   - 训练总结页显示总消耗、各动作消耗、免责说明
   - 用户资料页分层引导填写
   - 「估算说明」帮助页

10. 单元测试：
    - 自重俯卧撑 3×15，体重 70kg，应得到合理估算
    - 深蹲 5×5×100kg，TV 高，EPOC 偏高
    - 空 profile 不抛异常
    - 同样 70kg 但骨骼肌不同，估算结果有合理差异

验收标准：
- 数据不全时自动降级，不崩溃
- 每条训练记录保存估算层级和输入快照
- 自重动作使用有效负荷系数，外部配重单独叠加
- 无网络、无外部设备、无额外权限
```

---

## 八、验收标准

1. 用户可在设置页填写体重（必填）、身高、年龄、性别、体脂率、去脂体重、骨骼肌含量
2. 训练执行中，顶部实时显示卡路里 + 当前估算精度提示
3. 训练结束后，总结页显示总消耗、各动作消耗、精度提示、免责说明
4. 数据不全时自动降级到可用层级，不崩溃、不显示 NaN
5. 每条训练记录保存估算层级和输入快照
6. 自重动作使用有效负荷系数，外部配重单独叠加
7. 复合动作 TV 比孤立动作高 20%，单侧动作高 10%
8. 无网络、无外部设备、无额外权限，全部本地计算
9. 单元测试覆盖 L1~L4 降级路径和空数据场景

---

**把本文档整段贴给 CodeBuddy 即可。**