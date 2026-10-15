# 将 action/*.csv（v2 分类体系源数据）转换为 core/database/assets/exercise_seed.json
# 输出格式与 SeedExercise(kotlinx.serialization) 一致；种子版本号升为 2。
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

  $exercises += [ordered]@{
    name = $r.name
    aliases = @()
    type = $type
    primaryMuscle = $muscle
    secondaryMuscles = @()
    equipment = Get-Equipment $r.equipmentType $r.equipment
    isUnilateral = $false
    defaultRestSeconds = $rest
    defaultSets = 3
    defaultReps = $reps
    instructions = $instructions
    majorGroup = $major
    subGroup = $r.subGroup
  }
}

if ($unknownPairs.Count -gt 0) { throw "未覆盖的 (majorGroup|subGroup) 组合：`n" + (($unknownPairs.Keys | Sort-Object) -join "`n") }

$seed = [ordered]@{ version = 2; exercises = $exercises }
$json = ConvertTo-Json $seed -Depth 5
[System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "写入 $outPath"
Write-Host "动作总数: $($exercises.Count)（去重丢弃 $dups 条）"
