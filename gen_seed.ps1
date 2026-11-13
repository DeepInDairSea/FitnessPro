# 将 action/*.csv（v2 分类体系源数据）转换为 core/database/src/main/assets/exercise_seed.json
# 输出格式与 SeedExercise(kotlinx.serialization) 一致。
# v3：按 action/fit-carculate.md + action/kcal-develp.md 为每个动作标定能耗维度
#     （基础 MET、自重系数、器械效率、复合度、节奏、强度区、动作模式、代谢类型等）。
$ErrorActionPreference = "Stop"

$srcDir = "f:/JAVAReference/JAVA/Fitness Pro/action"
$outPath = "f:/JAVAReference/JAVA/Fitness Pro/core/database/src/main/assets/exercise_seed.json"

# majorGroup 中文 → 枚举名
$majorMap = @{
  "胸部" = "CHEST"; "肩部" = "SHOULDERS"; "背部" = "BACK"; "腿部" = "LEGS"
  "臀部" = "GLUTES"; "手臂" = "ARMS"; "核心" = "CORE"; "颈部" = "NECK"
  "壶铃" = "KETTLEBELL"; "TRX" = "TRX"; "药球与战绳" = "MED_BALL_AND_ROPE"
  "功能性" = "FUNCTIONAL"; "自重补充" = "CALISTHENICS"; "弹力带" = "RESISTANCE_BAND"
  "普拉提" = "PILATES"; "瑜伽" = "YOGA"; "热身" = "WARMUP"; "拉伸" = "STRETCH"
}

# subGroup（细分肌群）→ primaryMuscle（旧细分枚举，用于配色与训练页分区）
$subToMuscle = @{
  "胸大肌上束"="UPPER_CHEST"; "胸大肌中束"="CHEST"; "胸大肌下束"="LOWER_CHEST"; "胸大肌内侧"="INNER_CHEST"; "前锯肌"="CHEST"; "胸大肌"="CHEST"
  "三角肌前束"="FRONT_DELT"; "三角肌中束"="SIDE_DELT"; "三角肌后束"="REAR_DELT"; "肩袖肌群"="SHOULDERS"; "肩胛稳定肌群"="SHOULDERS"
  "背阔肌宽度"="LATS"; "背阔肌厚度"="LATS"; "背阔肌"="LATS"; "竖脊肌"="LOWER_BACK"; "斜方肌上束"="TRAPS"; "斜方肌中束"="TRAPS"; "斜方肌中下束"="TRAPS"; "斜方肌下束"="TRAPS"; "腰方肌"="LOWER_BACK"
  "股四头肌"="QUADS"; "内收肌群"="QUADS"; "腘绳肌"="HAMSTRINGS"; "腓肠肌"="CALVES"; "比目鱼肌"="CALVES"; "胫骨前肌"="CALVES"; "足部内在肌"="CALVES"; "足部稳定肌"="CALVES"; "外展肌群"="GLUTES"; "髂腰肌"="OTHER"
  "臀大肌"="GLUTES"; "臀中肌"="GLUTES"; "梨状肌"="GLUTES"; "闭孔肌群"="GLUTES"
  "肱二头肌长头"="BICEPS"; "肱二头肌短头"="BICEPS"; "肱二头肌"="BICEPS"; "肱肌"="BICEPS"; "肱桡肌"="BICEPS"; "旋后肌"="BICEPS"
  "肱三头肌外侧头"="TRICEPS"; "肱三头肌内侧头"="TRICEPS"; "肱三头肌长头"="TRICEPS"; "肱三头肌"="TRICEPS"
  "前臂屈肌群"="FOREARMS"; "前臂伸肌群"="FOREARMS"; "旋前肌"="FOREARMS"
  "腹直肌上部"="CORE"; "腹直肌"="CORE"; "腹直肌下部"="CORE"; "腹斜肌"="CORE"; "腹横肌"="CORE"
  "胸锁乳突肌"="NECK"; "头夹肌"="NECK"; "斜角肌"="NECK"; "颈半棘肌"="NECK"; "颈部"="NECK"
  "全身"="FULL_BODY"; "三角肌"="SHOULDERS"; "肩部稳定"="SHOULDERS"; "握力"="FOREARMS"; "肩部"="SHOULDERS"
  "背部"="BACK"; "胸部"="CHEST"; "肩关节"="SHOULDERS"; "肩袖"="SHOULDERS"
  "脊柱"="CORE"; "核心"="CORE"; "髋屈肌"="OTHER"; "髋部"="OTHER"; "髋关节"="OTHER"; "关节"="OTHER"
}

# equipmentType/equipment → Equipment 枚举
function Get-Equipment($equipType, $equipStr) {
  $first = ($equipStr -split ";")[0]
  switch ($equipType) {
    "固定器械" { if ($equipStr -match "史密斯") { return "SMITH" } else { return "MACHINE" } }
    "绳索" { return "CABLE" }
    "自重" { return "BODYWEIGHT" }
    "自重+负重" { return "BODYWEIGHT" }
    "弹力带" { return "BAND" }
    "壶铃" { return "KETTLEBELL" }
    "TRX" { return "TRX" }
    "药球" { return "MEDICINE_BALL" }
    "战绳" { return "BATTLE_ROPE" }
    "小器械" { return "OTHER" }
    "自由重量" {
      if ($first -match "哑铃") { return "DUMBBELL" }
      if ($first -match "曲杆") { return "EZ_BAR" }
      return "BARBELL"
    }
    default { return "OTHER" }
  }
}

# ---------------------------------------------------------------
# 能耗维度标定（kcal-develp.md §3.3/§3.4 + fit-carculate.md 维度设计）
# ---------------------------------------------------------------

# 基础 MET 关键词映射（Compendium of Physical Actions 力量训练常见区间）
function Get-MetValue($name, $major) {
  if ($major -in @("WARMUP", "STRETCH")) { return 2.0 }
  if ($major -in @("YOGA", "PILATES")) { return 2.5 }
  if ($name -match "波比|壶铃摇摆|跳箱|战绳|药球砸地|雪橇|登机|开合跳|高抬腿") { return 8.0 }
  if ($name -match "深蹲|硬拉|卧推|引体|推举|高翻|抓举|双力臂|挺举") { return 6.0 }
  if ($name -match "划船|下拉|箭步蹲|分腿蹲|腿举|保加利亚|农夫") { return 5.0 }
  if ($name -match "弯举|侧平举|飞鸟|腿屈伸|腿弯举|面拉|耸肩|提踵|臂屈伸") { return 3.5 }
  if ($name -match "卷腹|平板|俄罗斯转体|悬垂举腿|死虫|鸟狗|腹肌") { return 2.5 }
  return 3.5
}

# 自重负荷系数（kcal-develp.md §3.3 系数表）
function Get-SelfWeightCoefficient($name) {
  if ($name -match "下斜.*俯卧撑") { return 0.75 }
  if ($name -match "上斜.*俯卧撑") { return 0.45 }
  if ($name -match "俯卧撑") { return 0.64 }
  if ($name -match "引体|高位") { return 1.00 }
  if ($name -match "双杠|臂屈伸.*支撑") { return 0.95 }
  if ($name -match "深蹲|蹲") { return 0.75 }
  if ($name -match "臀桥|臀推") { return 0.45 }
  if ($name -match "平板|支撑") { return 0.65 }
  if ($name -match "箭步蹲|弓步|分腿蹲") { return 0.70 }
  if ($name -match "波比") { return 0.80 }
  if ($name -match "倒立") { return 1.00 }
  return 0.50
}

# 器械效率系数：固定轨迹器械有摩擦与配重片损耗，有效负荷低于标称配重
function Get-EquipmentEfficiency($equipType, $name) {
  if ($name -match "史密斯") { return 0.80 }
  switch ($equipType) {
    "固定器械" { return 0.85 }
    "绳索" { return 0.80 }
    "弹力带" { return 0.75 }
    default { return 1.0 }
  }
}

# 动作模式（fit-carculate.md 维度 1）
function Get-MovementPattern($name) {
  if ($name -match "高翻|抓举|跳箱|波比|跳跃|甩|砸") { return "EXPLOSIVE" }
  if ($name -match "平板|静蹲|靠墙|等长|静态") { return "ISOMETRIC" }
  if ($name -match "转体|旋转") { return "ROTATION" }
  if ($name -match "帕罗夫|抗旋") { return "ANTI_ROTATION" }
  if ($name -match "箭步蹲|弓步|分腿蹲|单腿|单臂") { return "LUNGE" }
  if ($name -match "硬拉|罗马尼亚|髋|壶铃摇摆|臀桥|臀推") { return "HINGE" }
  if ($name -match "深蹲|腿举|哈克|蹲") { return "SQUAT" }
  if ($name -match "引体|下拉|高位") { return "VERTICAL_PULL" }
  if ($name -match "推举|肩推| overhead|实力举|倒立") { return "VERTICAL_PUSH" }
  if ($name -match "划船|反向飞鸟|俯身|俯卧") { return "HORIZONTAL_PULL" }
  if ($name -match "卧推|推胸|俯卧撑|卧") { return "HORIZONTAL_PUSH" }
  if ($name -match "弯举|屈伸|侧平举|前平举|飞鸟|耸肩|提踵|卷腹|弯腿|屈腿") { return "ISOLATION" }
  return "OTHER"
}

# 强度区：爆发 → POWER；恢复类 → ENDURANCE；默认增肌区
function Get-IntensityZone($pattern, $major) {
  if ($pattern -eq "EXPLOSIVE") { return "POWER" }
  if ($major -in @("WARMUP", "STRETCH", "YOGA", "PILATES")) { return "ENDURANCE" }
  return "HYPERTROPHY"
}

# 代谢类型：高强度自重全身类偏有氧，爆发类偏无氧，其余混合
function Get-MetabolicType($pattern, $met, $major) {
  if ($major -in @("WARMUP", "STRETCH", "YOGA", "PILATES")) { return "AEROBIC" }
  if ($pattern -eq "EXPLOSIVE") { return "ANAEROBIC" }
  if ($met -ge 8.0) { return "AEROBIC" }
  return "MIXED"
}

# 肌群体积 → 能耗基准层级：大肌群动作单位时间能耗更高
$largeMuscles = @("CHEST","UPPER_CHEST","LOWER_CHEST","INNER_CHEST","BACK","LATS","UPPER_BACK","LOWER_BACK","QUADS","HAMSTRINGS","GLUTES","FULL_BODY")
$smallMuscles = @("CORE","FOREARMS","CALVES","NECK")
function Get-MuscleVolume($muscle) {
  if ($muscle -in $largeMuscles) { return "LARGE" }
  if ($muscle -in $smallMuscles) { return "SMALL" }
  return "MEDIUM"
}

$rows = Get-ChildItem $srcDir -Filter *.csv | ForEach-Object { Import-Csv $_.FullName -Encoding UTF8 }

# 按名称去重（跨文件重复只保留第一条）
$seen = @{}
$exercises = @()
$dups = 0
$unknownPairs = @{}
foreach ($r in $rows) {
  if ($seen.ContainsKey($r.name)) { $dups++; continue }
  $seen[$r.name] = $true

  $major = $majorMap[$r.majorGroup]
  if (-not $major) { throw "未知 majorGroup: $($r.majorGroup)（动作 $($r.name)）" }
  $muscle = $subToMuscle[$r.subGroup]
  if (-not $muscle) { $unknownPairs["$($r.majorGroup)|$($r.subGroup)"] = $true; continue }

  $isRecovery = ($r.majorGroup -in @("热身", "拉伸", "瑜伽", "普拉提"))
  $type = if ($isRecovery) { "TIME" } else { "WEIGHT_REPS" }
  $rest = if ($isRecovery) { 30 } else { 90 }
  $reps = if ($isRecovery) { $null } else { "10" }
  $instructions = $r.tips
  if ($r.variations) { $instructions = "$($r.tips)`n变式：$($r.variations)" }

  # ---- 能耗维度标定 ----
  $equipType = $r.equipmentType
  $equip = Get-Equipment $equipType $r.equipment
  $isBodyweight = ($equipType -match "自重" -or $equipType -eq "TRX" -or $equip -eq "BODYWEIGHT")
  $pattern = Get-MovementPattern $r.name
  $met = Get-MetValue $r.name $major
  # 孤立（单关节）动作负荷与能耗低于复合动作
  $isCompound = ($pattern -ne "ISOLATION")
  # 节奏因子：爆发类动作单位时间张力更高；等长保持类次之
  $tempo = 1.0
  if ($pattern -eq "EXPLOSIVE") { $tempo = 1.25 }
  elseif ($pattern -eq "ISOMETRIC") { $tempo = 1.2 }
  # 等长动作一次"次数"即一次保持，按 30 秒计；其余按 3 秒
  $repDuration = if ($pattern -eq "ISOMETRIC") { 30 } else { 3 }
  # 爆发类与引体类对新手不友好
  $beginner = ($pattern -ne "EXPLOSIVE" -and $r.name -notmatch "双力臂|倒立")
  # 自重+负重 的外部配重不改变自重部分：器械效率只作用于外部配重
  $efficiency = Get-EquipmentEfficiency $equipType $r.name

  $exercises += [ordered]@{
    name = $r.name
    aliases = @()
    type = $type
    primaryMuscle = $muscle
    secondaryMuscles = @()
    equipment = $equip
    isUnilateral = $false
    defaultRestSeconds = $rest
    defaultSets = 3
    defaultReps = $reps
    instructions = $instructions
    majorGroup = $major
    subGroup = $r.subGroup
    # 能耗维度（v3 标定）
    metValue = $met
    isBodyweight = $isBodyweight
    selfWeightCoefficient = (Get-SelfWeightCoefficient $r.name)
    equipmentEfficiency = $efficiency
    isCompound = $isCompound
    tempoFactor = $tempo
    intensityZone = (Get-IntensityZone $pattern $major)
    movementPattern = $pattern
    metabolicType = (Get-MetabolicType $pattern $met $major)
    averageRepDurationSeconds = $repDuration
    primaryMuscleVolume = (Get-MuscleVolume $muscle)
    isBeginnerFriendly = $beginner
    isWarmupSuitable = ($major -in @("WARMUP", "STRETCH"))
  }
}

if ($unknownPairs.Count -gt 0) { throw "未覆盖的 (majorGroup|subGroup) 组合：`n" + (($unknownPairs.Keys | Sort-Object) -join "`n") }

$seed = [ordered]@{ version = 3; exercises = $exercises }
$json = ConvertTo-Json $seed -Depth 5
[System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "写入 $outPath"
Write-Host "动作总数: $($exercises.Count)（去重丢弃 $dups 条）"
