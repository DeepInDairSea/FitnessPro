# 动作细分维度与字段设计

动作细分不能只靠“大肌群 + 细分肌群”两级，要支撑**卡路里估算、筛选推荐、训练编程、执行记录**四类场景，需要引入多个正交维度。下面按维度说明，并给出完整字段设计。

---

## 一、为什么需要细分

同样叫“卧推”，下面几种情况的能耗和编程差异巨大：

| 变式 | 差异 |
|------|------|
| 平板杠铃卧推 5×5×100kg | 大重量、低次数、复合动作 |
| 上斜哑铃卧推 3×12×20kg | 中等重量、高次数 |
| 器械推胸 3×15×60kg | 固定轨迹、孤立 |
| 俯卧撑 3×20 | 自重、无配重 |
| 暂停卧推 3×3×110kg | 节奏特殊、力量导向 |

如果只用 MET 值区分，精度不够。需要引入**动作模式、复合度、负荷类型、节奏、强度**等维度。

---

## 二、动作细分的六个维度

### 维度 1：动作模式（Movement Pattern）

这是训练编程的核心分类，决定动作的力学结构。

| 模式 | 说明 | 示例 |
|------|------|------|
| 水平推 | 向前推 | 卧推、俯卧撑、器械推胸 |
| 水平拉 | 向后拉 | 划船、反向飞鸟 |
| 垂直推 | 向上推 | 推举、倒立俯卧撑 |
| 垂直拉 | 向下拉 | 引体、下拉 |
| 蹲 | 屈膝下蹲 | 深蹲、箭步蹲、腿举 |
| 髋铰链 | 以髋为轴 | 硬拉、罗马尼亚硬拉、壶铃摇摆 |
| 弓步/单腿 | 单腿主导 | 保加利亚分腿蹲、单腿硬拉 |
| 旋转 | 躯干旋转 | 俄罗斯转体、绳索斜拉 |
| 抗旋转 | 抵抗旋转 | 帕罗夫压、单侧农夫行走 |
| 等长 | 静态保持 | 平板支撑、靠墙静蹲 |
| 爆发 | 快速发力 | 高翻、跳箱、波比跳 |
| 孤立 | 单关节 | 弯举、腿屈伸、侧平举 |

### 维度 2：复合度（Compound vs Isolation）

| 类型 | 说明 | 能耗影响 |
|------|------|---------|
| 复合动作 | 多关节参与 | 能耗高，TV 权重 ×1.2 |
| 孤立动作 | 单关节参与 | 能耗低，TV 权重 ×0.8 |

### 维度 3：负荷类型（Load Type）

| 类型 | 说明 | TV 计算方式 |
|------|------|------------|
| 自由重量 | 杠铃、哑铃、壶铃 | 直接使用实际配重 |
| 固定器械 | 史密斯、悍马机、腿举 | 实际配重 × 器械效率系数（0.8-0.95） |
| 自重 | 俯卧撑、引体 | 体重 × 自重系数 |
| 自重+负重 | 负重引体、负重俯卧撑 | 体重 × 自重系数 + 配重 |
| 弹力带 | 弹力带动作 | 估算平均阻力 × 动作幅度 |
| 绳索 | 龙门架动作 | 实际配重 × 绳索效率系数（0.7-0.9） |

### 维度 4：动作节奏（Tempo）

节奏影响肌肉张力时间（TUT），进而影响能耗。

| 节奏类型 | 说明 | 能耗因子 |
|---------|------|---------|
| 标准 | 2-0-2（离心-停顿-向心） | 1.0 |
| 慢速离心 | 4-0-2 | 1.15 |
| 暂停 | 2-2-2 | 1.20 |
| 爆发 | 1-0-X | 1.25 |
| 恒定节奏 | 3-0-3 | 1.10 |

### 维度 5：单侧 vs 双侧（Unilateral vs Bilateral）

| 类型 | 说明 | 能耗影响 |
|------|------|---------|
| 双侧 | 双腿/双臂同时 | 基准 |
| 单侧 | 单腿/单臂交替 | 同重量下能耗略高（稳定性需求） |

### 维度 6：强度等级（Intensity Zone）

| 强度 | %1RM | 次数范围 | 能耗特征 |
|------|------|---------|---------|
| 力量 | 85-100% | 1-5 | 单位时间能耗高，TV 低 |
| 增肌 | 67-85% | 6-12 | TV 中等，能耗中等 |
| 耐力 | <67% | 15+ | TV 高，单位时间能耗低 |
| 爆发 | 30-60%（快速） | 1-5 | 能耗高，EPOC 高 |

---

## 三、字段设计

### 3.1 分类字段（已有，保留）

```kotlin
val id: String
val name: String
val majorGroup: String
val subGroup: String
val equipmentType: String
val equipment: List<String>
val difficulty: String
val scene: List<String>
val goal: List<String>
val tips: List<String>
val variations: String
val media: Media
val dataVersion: Int
val isCustom: Boolean
```

### 3.2 能耗字段（新增，核心）

```kotlin
// 基础能耗
val metValue: Double = 3.5,                    // MET 值
val isBodyweight: Boolean = false,             // 是否自重动作
val selfWeightCoefficient: Double = 0.5,       // 自重负荷系数
val equipmentEfficiency: Double = 1.0,         // 器械效率系数（自由重量 1.0，器械 0.85，绳索 0.8）
val isCompound: Boolean = true,                // 复合/孤立
val tempoFactor: Double = 1.0,                 // 节奏因子
val isUnilateral: Boolean = false,             // 单侧/双侧
val intensityZone: IntensityZone = IntensityZone.HYPERTROPHY,  // 强度区
val movementPattern: MovementPattern = MovementPattern.OTHER,  // 动作模式
val metabolicType: MetabolicType = MetabolicType.MIXED,        // 代谢类型
```

### 3.3 训练编程字段（新增，推荐用）

```kotlin
val recommendedSets: IntRange? = null,          // 推荐组数，如 3..5
val recommendedReps: IntRange? = null,          // 推荐次数，如 8..12
val recommendedRestSeconds: IntRange? = null,   // 推荐休息，如 60..120
val recommendedIntensityPercent1RM: IntRange? = null,  // 推荐强度
val isBeginnerFriendly: Boolean = true,         // 是否适合新手
val isRehabFriendly: Boolean = false,           // 是否适合康复
val isWarmupSuitable: Boolean = false,          // 是否适合热身
val primaryMuscleVolume: MuscleVolume = MuscleVolume.LARGE,  // 主要肌群体积
```

### 3.4 执行字段（新增，用于训练记录）

```kotlin
val averageRepDurationSeconds: Int = 3,         // 单次动作平均时长
val breathingPattern: String? = null,           // 呼吸模式
val requiresSpotter: Boolean = false,           // 是否需要保护者
val canAddWeight: Boolean = true,               // 是否可负重
```

### 3.5 枚举定义

```kotlin
enum class MovementPattern {
    HORIZONTAL_PUSH,    // 水平推
    HORIZONTAL_PULL,    // 水平拉
    VERTICAL_PUSH,      // 垂直推
    VERTICAL_PULL,      // 垂直拉
    SQUAT,              // 蹲
    HINGE,              // 髋铰链
    LUNGE,              // 弓步/单腿
    ROTATION,           // 旋转
    ANTI_ROTATION,      // 抗旋转
    ISOMETRIC,          // 等长
    EXPLOSIVE,          // 爆发
    ISOLATION,          // 孤立
    OTHER
}

enum class IntensityZone {
    STRENGTH,       // 力量 85-100% 1RM
    HYPERTROPHY,    // 增肌 67-85%
    ENDURANCE,      // 耐力 <67%
    POWER           // 爆发
}

enum class MetabolicType {
    AEROBIC,        // 有氧
    ANAEROBIC,      // 无氧
    MIXED           // 混合
}

enum class MuscleVolume {
    LARGE,          // 大肌群（胸、背、腿、臀）
    MEDIUM,         // 中肌群（肩、手臂）
    SMALL           // 小肌群（前臂、小腿、核心）
}
```

---

## 四、细分维度如何影响卡路里估算

在 `AdvancedCalorieEstimator` 中，这些字段参与 TV 校正和 MET 修正：

```kotlin
fun calculateEffectiveLoad(exercise: Exercise, profile: UserProfile, actualWeightKg: Double): Double {
    val baseLoad = if (exercise.isBodyweight) {
        profile.weightKg * exercise.selfWeightCoefficient + actualWeightKg
    } else {
        actualWeightKg * exercise.equipmentEfficiency
    }
    return baseLoad
}

fun calculateTV(exercise: Exercise, sets: Int, reps: Int, effectiveLoad: Double): Double {
    var tv = sets * reps * effectiveLoad
    if (exercise.isCompound) tv *= 1.2
    if (exercise.isUnilateral) tv *= 1.1
    tv *= exercise.tempoFactor
    return tv
}

fun adjustMETByIntensity(baseMET: Double, zone: IntensityZone): Double {
    return when (zone) {
        IntensityZone.STRENGTH -> baseMET * 1.3
        IntensityZone.HYPERTROPHY -> baseMET * 1.0
        IntensityZone.ENDURANCE -> baseMET * 0.85
        IntensityZone.POWER -> baseMET * 1.4
    }
}
```

---

## 五、细分维度如何影响筛选推荐

用户可以说“我想练胸，居家，无器械，新手友好”，App 按字段筛选：

```kotlin
val results = exerciseDao.filter(
    majorGroup = "胸部",
    scene = listOf("居家"),
    equipmentType = listOf("自重"),
    isBeginnerFriendly = true
)
```

推荐算法可以基于：

- **动作模式**：避免同一天重复同一模式
- **肌群平衡**：推拉比例 1:1
- **强度区**：根据用户目标（力量/增肌/耐力）推荐
- **疲劳管理**：复合动作优先，孤立动作收尾

---

## 六、给 CodeBuddy 的实现指令

```
请在 Fitness Pro 项目中扩展 Exercise 实体，新增以下字段并实现相应逻辑：

1. 新增能耗字段：
   - metValue: Double
   - isBodyweight: Boolean
   - selfWeightCoefficient: Double
   - equipmentEfficiency: Double
   - isCompound: Boolean
   - tempoFactor: Double
   - isUnilateral: Boolean
   - intensityZone: IntensityZone
   - movementPattern: MovementPattern
   - metabolicType: MetabolicType

2. 新增训练编程字段：
   - recommendedSets: IntRange?
   - recommendedReps: IntRange?
   - recommendedRestSeconds: IntRange?
   - recommendedIntensityPercent1RM: IntRange?
   - isBeginnerFriendly: Boolean
   - isRehabFriendly: Boolean
   - isWarmupSuitable: Boolean
   - primaryMuscleVolume: MuscleVolume

3. 新增执行字段：
   - averageRepDurationSeconds: Int
   - breathingPattern: String?
   - requiresSpotter: Boolean
   - canAddWeight: Boolean

4. 定义枚举：
   - MovementPattern（12 种动作模式）
   - IntensityZone（4 种强度区）
   - MetabolicType（3 种代谢类型）
   - MuscleVolume（3 种肌群体积）

5. 编写 Room Migration

6. 在 AdvancedCalorieEstimator 中集成这些字段：
   - 有效负荷计算考虑 equipmentEfficiency
   - TV 计算考虑 isCompound、isUnilateral、tempoFactor
   - MET 校正考虑 intensityZone

7. 提供按字段筛选动作的 DAO 方法

8. 单元测试验证：
   - 复合动作 TV 比孤立动作高 20%
   - 器械动作有效负荷比自由重量低 15%
   - 单侧动作 TV 比双侧高 10%
```

---

## 七、总结

| 维度 | 字段 | 影响 |
|------|------|------|
| 动作模式 | movementPattern | 训练编程、肌群平衡 |
| 复合度 | isCompound | TV 校正 ×1.2 / ×0.8 |
| 负荷类型 | isBodyweight、equipmentEfficiency | 有效负荷计算 |
| 节奏 | tempoFactor | TV 校正 ×1.0~1.25 |
| 单双侧 | isUnilateral | TV 校正 ×1.1 |
| 强度区 | intensityZone | MET 校正 ×0.85~1.4 |
| 代谢类型 | metabolicType | 有氧/无氧占比 |
| 肌群体积 | primaryMuscleVolume | 能耗基准 |

**核心改进**：用这些字段把「一个 MET 值走天下」升级为「多维度加权估算」，同时支撑筛选、推荐、编程、记录四类场景。