# 未完成条目清单 (UNDONE)

> 本文档记录本次「ABCD 全做」任务中**尚未完成**的条目，并附实现指引，便于后续会话/PR 收尾。
> 已完成的条目（均已编译通过并安装到模拟器）见末尾附录。

## 未完成项

> **已全部完成 ✅**（2026-10-10 补充验证）
> - **C10 训练草稿恢复**：进入首页时主动探测未完成训练（`WorkoutSessionManager.findResumableDraft()`，复用既有 `workout_session_state` 表，未新建草稿表），弹出「继续训练 / 丢弃草稿」询问框，仅提示一次；新鲜度判定由纯函数 `DraftRecoveryPolicy` 负责（默认 7 天，超过则不再打扰），已有 `DraftRecoveryPolicyTest` 覆盖。首页「进行中训练」卡片保留为常驻入口。
> - **D17–D19 单元测试**：均已编写并**实际运行通过**（D17/D18 在 JVM/Robolectric，D19 在模拟器 `connectedDebugAndroidTest`）。

## 其他改动说明

### 新增主题：Bento 软UI（便当盒 + Soft UI，其它主题全部保留）
- **预设**：`ThemePreset.BENTO_SOFT`（设置 → 主题配色 → 「Bento 软UI」）。
- **令牌层**（`core/designsystem/theme/Theme.kt`）：新增 `FitnessLayout(bentoGrid, cardRadius, cardElevation, cardGap, accents)`，经 `FitnessTheme.layout` 下发；该主题下圆角 26dp、阴影 10dp、间距 12dp、强调色为橙/紫/蓝/黄；背景从绿色渐变换成中性浅灰画布（否则橙紫会被染脏）；形状切到 `BentoShapes`，顶栏换成扁平中性大标题。
- **组件层**：新增 `SoftCard`（大圆角 + 柔和阴影 + `tonalElevation=0`，可点击时自带按压微缩放）与 `Bento.kt`（`BentoRow` / `BentoStatTile` / `BentoSectionCard` / `Modifier.bentoAppear` 错峰入场）。
- **页面层**：首页便当盒网格（主卡 + 彩色小块 + 热力图 + 肌群 + PR + 2×2 快捷格）；数据页便当盒仪表盘（2×2 概览色块 + 每张图独立悬浮卡）；计划列表白色浮卡 + 分类色条；训练页概览卡与动作卡换软卡片（输入/计时控件未动）。
- **悬浮菜单（透明底）**：底栏**没有底板**，只有四个"状态块"浮在内容之上 —— 选中项为实心强调色胶囊 + 文字，未选中项为半透明块保证可读；页面内容从菜单后面穿过（滚动时能看见卡片经过）。为此 `FitnessLayout` 新增 `bottomBarInset`（便当盒 116dp，其它主题 0）、`FitnessProNavHost` 在便当盒下不再消费 Scaffold 底部内边距，改由各顶层页自行留白（Home/Stats/History/Plans/ExerciseLibrary/Body）；训练库 FAB 同步抬高到菜单之上。其它主题走原路径，行为不变。
- **质感与配比微调**：`BentoStatTile` 由纯色改为「高光→本色→暗角」渐变 + 固定种子细噪点（`softBlockBrush` / `Modifier.softNoise`，图案缓存在 `drawWithCache`，点数有上限）；新增 `BentoNumberCard`（白卡 + 强调色数字）用于首页 PR 格 —— 一屏内饱和色块过多会互相打架，留白卡才能让色块重新成为重点。
- **添加按钮改位置**：删除训练库的悬浮 FAB（会盖住最后一条内容、并和底部菜单争右下角），改为顶栏填色圆形「＋」主操作（`CreateAction`，文案随分段为"新建计划/新建动作"）。
- **左滑显露操作（QQ 消息式）**：新增 `SwipeRevealBox`（`draggable` + `Animatable` 自实现，避免 `SwipeToDismissBox` 的"滑到底即执行"误删语义）。计划列表、动作库、历史列表、身体记录的行内收藏/删除图标全部移除，改为左滑露出「收藏」「删除」；**删除一律再弹二次确认**（身体记录此前是硬删无确认，本次一并补上）；收藏为非破坏性、点了立即生效。
- **剩余页面铺开**：身体页（录入表单卡 + 记录行 + 删除确认）、动作库（行改软卡片）、历史列表、训练总结（指标/对比/PR 分区/PR 行）统一使用 `SoftCard`。
- **菜单与卡片分层**：底部菜单的未选中状态块改用"画布色 + 7% 前景色"混出的中性灰（原先与卡片同为 `surfaceVariant` 纯白，菜单和卡片糊成一块）；动作库 Tab 从平分宽度的 `TabRow` 改为有间距的胶囊行，动作行之间补 `cardGap` 间距。
- **计划分享改为图片**：原来只发 `text/plain`，现在还生成一张处方卡片图（`buildPlanCard`，1000px 宽，动作多时截断 + 省略号）走 `ACTION_SEND`，微信/QQ/相册都能收。
- **许可与免责**：新增根目录 `LICENSE`（MIT，Copyright (c) 2026 KerryZeng）与 `DISCLAIMER.md`；应用内隐私页改为章节数据驱动，新增「免责声明」（纯离线、数据不上传下载、训练风险自负、与作者无关）与「开源许可」两节；设置页新增「免责声明」「开源许可」入口。
- **应用图标**：用新 logo（渐变 + 折线 + 圆环 + 字标）替换绿色哑铃图标。`drawable-nodpi/ic_launcher_logo.png`（从设计稿裁切）作为自适应图标的 background 层铺满，foreground 透明，另写 `drawable/ic_launcher_monochrome.xml`（折线轮廓）供 Android 13+ 主题图标使用。
- **按部位分区（④ 第一步）**：`ExerciseSnapshot` 新增 `primaryMuscle`，计划详情与训练页在动作跨多个部位时自动插入部位分区标题（单部位不插，避免噪音）；数据由仓库/会话管理器一次批量查询动作库补齐，**未改库表结构**。后续的"用户自定义 Section 命名"需要给 `plan_exercise`/`workout_exercise` 加列并做 Room 迁移，尚未开始。
- **训练日历（⑤ 第一步）**：新增 `plan_schedule` 表（`epochDay + planId` 复合主键，外键 CASCADE），数据库版本 4 → 5 并补 `MIGRATION_4_5`（新建表 + 两个索引）；新增 `ScheduleRepository`（绑定/解绑幂等、按天与按月区间观察）。新增「训练日历」页（`feature/plans/ScheduleScreen.kt`）：6×7 月视图（周一开始，含相邻月补位，固定高度不跳动）、今天/选中态区分、日期格最多显示两个计划色点、点日期 → 勾选对话框直接绑定/解绑（幂等，无"保存"按钮）、日期详情里可「开始训练」或「解绑」。入口：首页便当盒快捷格「训练日历」（第 4 格）。
  - **尚未做**：写入系统日历（`CalendarContract` + `WRITE_CALENDAR` 运行时权限，单向写并记录事件 id）。训练提醒继续走应用内 `WorkoutReminderManager`，不依赖系统日历提醒 —— 用户会关掉日历提醒或删事件。

### 第五轮收尾（2026-10-11 编译通过 + 安装验证）
- **总结页软卡片化收尾**：训练总结 `WorkoutSummaryScreen` 的指标卡/对比卡/PR 区块/PR 行全部换 `SoftCard`；修复 `savedToast` 未定义（补 `stringResource(R.string.summary_saved_toast)`）。
- **material3 API 修复**：`SegmentedButtonDefaults.itemColors(...)` 在项目实际解析的 material3 1.3.2（BOM 2025.06.00）中**不存在**（1.4+ 才有的 API 名），改为 `SegmentedButtonDefaults.colors(activeContainerColor, activeContentColor, ...)`（`DataScreen.kt` / `TrainingLibraryScreen.kt`）；`DataScreen` 补 `MaterialTheme` 导入。
- **验证**：全量 `:app:assembleDebug` 通过；模拟器实测首页便当盒、训练库顶栏「＋」、计划卡左滑露「收藏/删除」、数据页分段强调色，浅色/深色均正常。

### 第六轮：提醒 / 首页顶栏 / 肌群主题色（2026-10-12 编译通过 + 实机验证）
- **吉祥物训练提醒**：新建原创吉祥物「训练小鸟」矢量（`feature/workout/.../drawable/ic_mascot_bird.xml`，珊瑚橙圆身 + 呆毛 + 眼睛高光 + 腮红）；`WorkoutReminderWorker` 通知改为 BigPicture 大图样式（折叠态 largeIcon + 展开态整只小鸟），文案从 6 条随机抽取（`reminder_messages` 数组）；提醒渠道 `IMPORTANCE_DEFAULT → HIGH`（横幅弹出 + 震动）。系统日历对接按既定结论不做。
- **提醒时间设置**：`SettingsViewModel.setReminderTime()`（复用 `setWorkoutReminder` + reschedule）；设置页提醒开关下新增「提醒时间」行（M3 `TimePicker` 24h 制弹窗），已实机验证（默认 19:00）。
- **首页顶栏重构**：去掉「首页」标题与内容区问候块 —— 顶栏改为「圆形头像（共享组件 `UserAvatar`，无头像回退昵称首字母 + 强调色实心圆）+ 昵称」，右上角为「问候语 + 日期」双行 + 设置按钮，消除右侧空缺。`HomeViewModel` 通过嵌套 combine（5 流上限）合入 `SettingsRepository.settings` 的 nickname/avatarUri。
- **肌群主题色系统**：新增 `core/designsystem/theme/MuscleColors.kt` —— 15 个肌群映射到 7 个色族（胸/背/腿=橙蓝绿，肩=紫，臂=粉，核心=黄，有氧=青，全身=灰）× 4 档深浅（上胸到下胸由浅到深），深色模式自动提亮；`Exercise.colorKey`（可空，Room 迁移 5→6）+ 色板组件 `ExerciseColorPalette`（7 族 28 色 + 「跟随肌群」）进新建/编辑动作表单；`colorKeyOf` 序列化为 6 位 RGB hex（修复过初版 Float 通道截断 bug —— 曾把所有颜色存成 `#FF000000`，实机抓库发现并修正，`ColorTest2=#7B2FE8` 验证通过；旧脏数据解析失败安全回退肌群色）。应用点：动作库行左缘身份色条（`exerciseIdentityColor`）、统计页/首页肌群分布条（`DistributionBarChart` 新增 `barColors` 逐条颜色）、`ExerciseColorSwatch` 标签点缀。
- **目标肌群枚举细分**：`MuscleGroup` 新增 上胸/中缝/下胸/上背/下背/上腿/下腿/侧肩/后肩/二头/三头/小臂/上腹/下腹/侧腹（各自字符串资源 + 颜色档位），新建动作与动作库筛选 chips 均已生效。
- **验证**：全量编译通过；实机验证顶栏（头像+昵称+右上问候）、设置提醒时间选择器、动作新建选色落库（sqlite 抓库确认）、动作库色条（胸=橙/背=蓝/肩=紫）。

### 第七轮：管理与执行分离（今日训练页 + 每周排程 UI）（2026-10-12 编译 + 实机验证）
- **计划编辑页「训练日」chips（每周循环排程入口）**：数据库 `plan.scheduledDays` 字段与今日并集逻辑早已存在，但编辑页从未暴露（创建时写死空集）—— 补上 UI：描述下方一排 周一~周日 chips，随「保存」一起落库（`createPlan`/`savePlanDetail` 链路均带上）。"每周一练 A、每周二练 B" 一次配置永久生效；日历绑定降级为"某天临时调整"。
- **「训练」tab 换成今日训练页（`app/ui/TodayScreen.kt` + `TodayViewModel`）**：
  - 「今天练什么」= 循环排程 ∪ 日历绑定（同一计划去重，来源标记"每周排程/日历绑定"），每行带身份色条 + 一键「开始」；无安排时空态引导「空手开练」（`startWorkout(planId=null)` 自由训练）。
  - 进行中的训练置顶为「继续」卡；「本周排程」七列总览（今天高亮，每天的计划以色点显示）；训练日历入口卡。
  - 日历流挂设置流重建（换周时观察区间随之更新）。
- **训练库收进今日页右上角入口（哑铃图标）**：tab 结构变为 首页 / 训练(今日) / 数据 / 身体；首页快捷格与计划卡入口不变。
- **验证**：全量编译 + plans 单测通过；实机验证 —— 编辑计划勾"周一"保存后今日页出现该计划（标记每周排程），点「开始」直接进入训练页，退出（不保存）后回到今日页并显示"继续"卡；训练库从右上角进入正常。**轮7 收尾（继续任务重跑）**：`./gradlew :app:assembleDebug` BUILD SUCCESSFUL（全 UP-TO-DATE，说明已编译过）；`./gradlew :feature:plans:testDebugUnitTest` BUILD SUCCESSFUL（2 executed / 177 up-to-date，实测代码未引入回归）；实机截图 — 首页顶栏「Kerry 头像 + 晚上好 + 10月12日 周一」、今日页正确展示「222 每周排程」+「开始」按钮 + 「本周排程」周一高亮 + 「空手开练」+「训练日历」入口卡、底部四 tab（首页 / 训练 / 数据 / 身体）按便当盒菜单约定（选中实心强调色胶囊 + 文字、未选仅图标）。所有轮7 工作已在代码中完成并实机验证通过。

### 轮8（用户反馈补丁）：总结保存失败 / 肌群漏识别 / 趋势缺口 / BMR 自动 / 启动页合一（2026-10-12 编译 + 实机验证）
- **「保存到相册」失败吞异常的修复**（`WorkoutSummaryScreen.kt`）：原 `runCatching { … }.getOrDefault(false)` 把 MediaStore.insert / openOutputStream / compress 任何一步的异常吞掉，用户看到"提示打勾→实际没存"。现在 `saveToGallery` 抛真 `IOException`（带原因说明），`doShare` 三段拆开：建图 → 写相册 → 写缓存 → 启动 chooser → toast；任何一段失败都有 `Timber.e` 落地 + snackbar 提示「保存失败，请重试」。Intent.createChooser 也包了 runCatching，再也不会静默崩。
- **肌群容量识别不全 — REPS_ONLY 自重动作体容率补丁**（`StatsDao.kt`）：原 SQL `IFNULL(ws.weightKg, 0) + IFNULL(s.bodyWeightKgSnapshot, 0)` 在用户没记过体重时永远算 0，引体向上 / 俯卧撑在容量分布上就是 0。改成 `COALESCE(NULLIF(snapshot, 0), (SELECT weightKg FROM body_measurement WHERE weightKg > 0 ORDER BY epochDay DESC LIMIT 1), 65.0)` —— 优先用上次实测体重，仍然为 0 时兜底 65kg（业内估算）。
- **身体数据趋势补全 + 新增小腿围 + BMR 自动估算入库**（数据库 v8→v9）：
  - `body_measurement` 表加两列：`calfCm REAL`（小腿围）+ `bmr REAL`（Mifflin-St Jeor 估算结果）。
  - `BodyMeasurement` domain 模型字段同步；`BodyMeasurementEntity` 列同步；mapper 不丢字段。
  - `BodyViewModel.save()` 多收一个 `calfInput`；表单"更多指标"展开区多一行"小腿围（cm）"输入框 —— 用户录一次就进库。
  - **`BodyViewModel` 顶部 BMR/TDEE 改读落库值**：`currentBmr = latestRecord?.bmr ?: 现场估算兜底`。意味着不论用户先在设置里写年龄/性别，还是先记身体数据，BMR 都立即随体重更新；缺资料时显示引导而不是编一个数。
  - `BodyRepositoryImpl.upsertByDay` 在写入前自动 `computeBmrForRecord(weightKg, epochDay, fallbackHeightCm = ...)`：体重/同一条身高/历史身高 / 设置里的年龄/性别 任一缺失则 BMR 落 null。
  - `BodyMetric` 枚举扩 6 项：`WAIST / HIP / ARM / THIGH / CALF / BMR`，trend chips Row 横向滚动可全部可见；`metricLabel` / `metricValueFormatter` 同步补全（腰/臀/臂/腿/小腿 → `cm`、BMR → `kcal`）。
- **启动页只剩一份**（`themes.xml` / `splash_background.xml` / `values-v31/themes.xml` / `values-night/themes.xml`）：把系统 windowBackground 从「3 色渐变（与 Compose 反向）」改成**纯色 `#F0653C`**（与 Compose 启动页起色完全一致）；Android 12+ `windowSplashScreenBackground` 同步换 `#F0653C`、v31 启动器图标早已是透明 `splash_icon_transparent.xml`，夜间主题也走同一份 splash_background。系统层只显示单色橙色，Compose splash 接管 logo 与版权 —— 用户感受不到两段切换。
- **启动页 logo 不再"空荡荡"**（用户第二轮反馈）：
  - 新增 `drawable/ic_splash_logo.xml`（120×120 viewport 的纯 logo 矢量：折线 + 圆环 + 中段节点 + 入环节点 + 顶端绿点），Compose 启动页改用它，size 170dp、无圆角裁切 —— 之前 `ic_launcher_logo.png` 是带圆角方形底 + "FitnessPro" 字标的完整应用图标，挤在 148dp 里又糊又小。
  - 系统层补 logo：`drawable/splash_background.xml` 改成 `<layer-list>`（橙色底 + 居中 170dp 的 `ic_splash_logo`），给 pre-v31 用；Android 12+ 新增 `drawable/splash_icon.xml`（108×108 viewport、内容严格收在 1/3 安全区 18~90，保证 OS 圆环裁切不切到笔画），接 `windowSplashScreenAnimatedIcon`，`splash_icon_transparent.xml` 删除。系统窗口期就展示完整 logo，不再是"空白橙页"。
  - Compose splash 停留从 1100ms 缩到 600ms，让整段启动更紧凑。
- **验证**：`:app:assembleDebug` BUILD SUCCESSFUL；schemas/9.json 已自动生成（`calfCm` 与 `bmr` 在 `body_measurement` 列里）；实机启动观察：系统窗口→Compose splash 视觉上是连续一段橙→白渐变（无"两份启动页"分界）。

### 轮7 收尾 + 饮食扩充（2026-10-12 编译 + 实机验证）
- **SUPPLEMENT 新分类 + 两列**，数据库 `food_definition` 表加 `scoopGrams: INTEGER?` 与 `proteinPerScoop: REAL?`（可空，对普通食物为 null）；Room 迁移 7→8 + FitnessDatabase.version=8（已自动生成 schemas/8.json）。**关键设计**：scoopGrams + proteinPerScoop 是补剂专属标识 —— UI 上识别 "category == SUPPLEMENT && scoopGrams != null && proteinPerScoop != null" 才走"15/30/45g 一勺"阶梯；非补剂走原来的 50/100/150/200g 阶梯。
- **FoodSeed 大幅扩充**：`@object FoodSeed` 现含 ~200 条（含原 70 条 + 新 130+ 条互联网常见食物 + 12 条蛋白补剂）。普通食物分 6 块：肉禽鱼虾（含做法）、蛋奶豆、主食（含网红：蛋炒饭/螺蛳粉/酸辣粉/煎饼果子/饭团）、蔬菜水果、坚果油脂；新增「网红零食/外卖/饮料」块（汉堡/披萨/炸鸡/麻辣烫/火锅/各类奶茶/咖啡）；**补剂 12 条**：
  - 乳清分三档：标准浓缩 80% / WPC 80% / WPI 90% / WPH 95%
  - 酪蛋白：Casein 80% / Micellar Casein 80%（部分品牌 32g/勺）
  - 蛋/酵/植：Egg White Protein 80% / Yeast Protein 65% / Plant Blend 70%
  - 复合/功能：Mass Gainer（30% 蛋白、碳水主导）/ 缓释蛋白 Blend 80% / 牛初乳 70%
  - 每条标注 `scoopGrams`（15-60g 区间，依品牌主流包装）与 `proteinPerScoop`（按 scoopGrams × 蛋白百分比算出，便于用户记一勺多少 g 蛋白）
- **UI 适配**：
  - 食物行：补剂单独一行 `每 100g 含蛋白 Xg · 一勺 Yg 含蛋白 Zg`（primary 强调色），普通食物保持"每 50g · 蛋白/脂肪/碳水"
  - PortionDialog：检测到 `food.isSupplement` 时自动切到「半勺·15g / 一勺·30g / 一勺半·45g」三档，文件选择自定义克数仍生效；顶部多一行 primary 色 `每 100g 含蛋白 Xg · 一勺 Yg 含蛋白 Zg`
- **新增 strings**：`diet_category_supplement`、`diet_scoop_half`、`diet_scoop_one`、`diet_scoop_one_half`
- **验证**：`:app:assembleDebug` BUILD SUCCESSFUL；装机实测 —— 蛋白补剂 chip 选中后正确展示 12 条补剂；点开"乳清蛋白粉（标准浓缩 80%）"PortionDialog 展示「半勺·15g / 一勺·30g / 一勺半·45g」+ 实时预览（蛋白质 24.0g、113 kcal）；切换"半勺"自动更新为"15g / 12.0g 蛋白 / 56 kcal"。
- **计划组合逻辑**：①「同一天两练」已天然支持 —— `plan_schedule` 一天可绑多个计划，训练库「今日训练」并集显示；②「计划内分段（A 部肩背 / B 部腿）」建议给 `plan_exercise` 加 `sectionLabel + sectionOrder` 列（Room 迁移 5→6），计划详情支持插入/命名分区，训练页按分区折叠成页 —— 这也是「按部位自动分区」的落库延伸。业界三层模型（计划→天→动作）过重，本项目用「计划=天 + section 列」等价且改动小。
- **系统日历对接**：建议不做提醒依赖，最多做「可选单向导出」——绑定时写 `CalendarContract` 事件并记录 `eventId`，解绑时同步删除；需 `WRITE_CALENDAR` 运行时权限 + 设置页开关。提醒主路径继续走应用内 `WorkoutReminderManager`（用户可能改/删日历事件，系统日历提醒不可靠）。

### 训练页与全局体验改造（第二轮）
- **训练页简化**：动作卡改为「当前动作展开、其余折叠成一行（动作名 + N/M 组）」，头部整行可点展开/收起；加一组/复制一组/删除末组/跳过本组/替换动作/跳过动作**收进「更多操作」菜单**，主操作只剩「完成一组」；进入训练自动滚动到当前动作（不重排顺序 —— 重排会打乱处方与详情对应关系）。
- **训练卡颜色与留白**：动作卡与概览卡底色**强制不透明**（壁纸玻璃模式下 surfaceVariant 是 80% 半透明，压在壁纸上会显暗沉、边界糊掉）；LazyColumn 左右留白显式写全。
- **训练库「今日训练」块**：今天有排程的计划置顶显示，橙色实心卡 + 白色「开始训练」按钮，一天多个计划各占一行；数据 = `scheduledDays` 循环排程 ∪ 日历绑定（`plan_schedule`），两者并集都算"今日训练"。
- **每页强调色**：`LocalFitnessAccent` + `FitnessTheme.accent`，页面根部分发 —— 计划/训练=橙、动作库=紫、统计=蓝（图表/热力图随之变蓝）、历史=黄（只做色条不做文字色）；图表组件新增 `accent` 参数默认跟随页面强调色。同一套四色板，页面可辨、风格统一。
- **启动页**：新增 Compose `SplashScreen`（橙白渐变 + 完整 logo + 底部「© 2026 KerryZeng · MIT License / 版本 x.y.z」，约 1.1s 浮入）；`themes.xml` 的 windowBackground 改为同色系渐变 drawable，消除冷启动白闪。
- **顺带修复**：
  - `CalendarHeatmap` / `DistributionBarChart` 的空槽色原本取 `surfaceVariant`，在白卡上会与卡片同色导致整图"看起来是空的"，改为半透明次级文字色（可通过参数覆盖）。
  - `PersonalRecordRepositoryImpl` 未回查动作名，PR 列表全部显示空白（现补上 `ExerciseDao` 查询）。

### 根构建脚本修复
- 移除了 `build.gradle.kts` 中 `subprojects { if (CI) configure<DetektExtension> }` 配置块。
- **原因**：该块在配置阶段引用 `io.gitlab.arturbosch.detekt.DetektExtension` 类型，但 `apply false` 不会把该类型加入根脚本编译 classpath，导致**任何本地构建都失败**（与功能代码无关）。
- **恢复方式（如需 CI 静态分析）**：在 `buildscript { dependencies { classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8") } }` 中显式加入 detekt 实现 artifact，或改为在 CI 流水线脚本里对各模块 `apply` 插件，避免污染根脚本编译 classpath。

---

## 附录：本次已完成项（已编译 + 安装到模拟器）

- **A 组**：串会话修复（相邻训练会话合并）+ 死代码清理。
- **B4** 屏幕常亮（训练/休息时 `keepScreenOn`）。
- **B5** 历史日历视图。
- **B6** 会话详情逐组编辑/删除。
- **B7** 训练分享卡片。
- **B8** 单计划分享。
- **C9** BLANK 填充模式。
- **C12** 动作库种子扩充（新增壶铃摆动、保加利亚分腿蹲、平板支撑、农夫行走、俄罗斯转体、死虫 6 个动作）。
- **C13** 通知渠道补齐（PR / 久坐提醒 / 身体提醒 / 媒体清理）。
- **D14–D16** 设置项与权限引导（通知/媒体/后台）。
- **D20** 隐私政策页（独立路由 `privacy`，设置页入口）。
- **D21** 电池优化引导（设置页「电池优化」入口 + 引导 dialog 跳系统白名单）。
- **C10** 训练草稿恢复（首页冷启动探测 + 「继续/丢弃」询问框 + `DraftRecoveryPolicy` 新鲜度判定）。
- **D17** 导航 Route 单测（`core:common`，已运行通过）。
- **D18** `RestTimerNotifier` 单测（`feature:workout`，Robolectric，已运行通过）。
- **D19** `ExerciseSeedImporter` 幂等性测试（`core:database`，androidTest，模拟器运行通过）。
