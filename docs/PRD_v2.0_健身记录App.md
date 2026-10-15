# 健身记录工具 Android App 开发需求文档（PRD）

**版本**：v2.0（深度审阅重构版）
**目标平台**：Android（minSdk 26 / targetSdk 36）
**文档状态**：生产级需求规格（可据此拆分开发任务）
**修订日期**：2026-10-07

---

## 0. 审阅结论（v1.1 → v2.0）

### 0.1 一句话结论

v1.1 是一份**「媒体演示工具」的需求文档**，而不是**「训练记录 App」的需求文档**。它对「动作演示资源」的描述精度（格式、大小、权限、降级）远高于对「训练本身就怎么记录」的描述精度，而后者才是这类产品的生命线。按 v1.1 实现出来的 App，用户会在第 3 次训练时放弃，因为它记不住「我上次卧推做了多重」。

### 0.2 致命缺陷（P0，必须修复，否则产品不成立）

| # | 缺陷 | 为什么致命 |
|---|------|-----------|
| P0-1 | **没有「实际成绩」数据模型**。`WorkoutRecord` 只有 `actualSets: Int` + `actualReps: String` | 训练 App 的核心价值是**渐进超负荷**。用户要看到「上周 60kg×8，这周 60kg×9」。当前模型连「每组重量」都存不下，等于无法记录训练 |
| P0-2 | **没有动作库（Exercise Library）实体**。动作只是计划里的一个字符串 `exerciseName` | 无法按肌群/器械检索、无法统计「卧推的历史曲线」、无法计算肌群容量分布。所有统计功能都被这个设计锁死 |
| P0-3 | **处方模型只有「组数 + 单一 reps 字符串」**，没有组级明细 | 无法表达热身组、递减组（drop set）、金字塔组（12/10/8）、超级组、AMRAP、失败组。而这些是真实训练计划的 80% 形态 |
| P0-4 | **训练执行缺休息计时器**。「组间休息」只是一个静态字段 | 组间休息计时是力量训练中**使用频率最高**的功能（每组触发一次）。没有它，用户会开着另一个计时器 App |
| P0-5 | **没有「上次成绩」回显与前次自动填充** | 「上次：60kg × 8」是这类 App 最核心的一屏信息。缺了它，用户每次要翻历史记录 |
| P0-6 | **权限清单缺失 `POST_NOTIFICATIONS`**（Android 13+） | 未申请该权限 → 休息计时器通知、训练提醒全部静默失效。这是 v1.1 权限表的硬伤 |
| P0-7 | **`PlanExercise.isSkipped` 语义错误** | 「跳过」是**运行时事件**，不是计划配置。放在计划表里会导致：跳过一次后，计划永久标记为已跳过 |
| P0-8 | **训练中途杀进程无法恢复** | 一组 90 秒休息 + 用户在微信回消息 = 进程被回收。无 WorksoutSession 持久化 = 整场训练数据丢失 |

### 0.3 严重缺陷（P1，显著影响留存）

| # | 缺陷 | 影响 |
|---|------|------|
| P1-1 | 无**设置页/偏好存储**（kg/lb 单位、默认休息、主题、周起始日） | 不用 DataStore 存偏好，单位切换只能硬编码 |
| P1-2 | 无**身体数据/体重记录** | 自重动作（引体、俯卧撑）无法计入容量；无法看体重趋势 |
| P1-3 | 无**个人纪录（PR）与 1RM 估算** | 缺了最强的正反馈机制和分享素材 |
| P1-4 | 无**统计与图表**（容量、频率、肌群分布、连续打卡） | 记录只有"存档"价值，没有"洞察"价值 |
| P1-5 | 无**通知与训练提醒** | 无召回手段，日活靠运气 |
| P1-6 | **导出/导入要求用户手动重新关联媒体** | 这是把工程债转嫁给用户。正确做法是导出为一个 zip 包 |
| P1-7 | **Android Auto Backup 未配置**，而媒体文件在 `filesDir` | 默认 Auto Backup 会把 200MB 视频塞进 25MB 配额，导致备份直接失败并可能丢失数据库 |
| P1-8 | **无后台清理机制**（回收站 7 天清理、孤立媒体 GC） | 「保留 7 天」写在需求里但没有 WorkManager 定时任务，等于不实现 |
| P1-9 | 无**中国 ROM 后台限制适配** | 小米/华为/OPPO 会杀掉前台服务，休息计时器静默失效 |
| P1-10 | 无**Health Connect 集成** | 2026 年 Android 健身 App 的事实标准；不写入则训练不计入系统健康数据 |
| P1-11 | **GMS 依赖风险未评估** | Photo Picker 回填依赖 Google Play 服务；国内无 GMS 机型会直接不可用，必须有 SAF 降级 |
| P1-12 | 无 **WorkManager / DataStore / Navigation / 序列化库** 等技术栈 | v1.1 技术栈表不完整，无法据此开工 |

### 0.4 一般缺陷（P2）

- `sortOrder` 用 Int 且无重排策略 → 频繁拖拽后需要全表重写，且易并发冲突（建议改 `Double`/LexoRank）。
- FK 列未声明索引 → Room 编译告警 + 查询全表扫描。
- 媒体只存文件名、不存缩略图 → 每次列表滚动都解码视频首帧，必然掉帧（与 NFR「首帧 ≤1s」自相矛盾）。
- `WorkoutRecord.planId` 未声明 `onDelete` 行为 → 删除计划后历史记录悬空或级联丢失。
- 无「空训练/自由训练」入口 → 用户只能按计划练，无法临时加练。
- 无撤销（Undo）。回收站不能替代 Snackbar Undo。
- 无搜索的动作库分词能力（建议 Room FTS4）。
- 无音效/震动/语音播报（休息结束提示）。
- 无「保持屏幕常亮」。
- 无计划分组/文件夹；计划多了以后列表不可用。
- 无上次训练时间、无「今天该练什么」。
- `estimatedDurationSeconds` 公式错误（见 FR-07 修订）。
- 无 i18n/RTL 要求（只有中文界面无法上架海外）。
- 无无障碍标准。
- 无崩溃监控与质量目标（crash-free rate）。
- 无验收标准对应的自动化测试手段（Macrobenchmark / 截图测试 / Migration 测试）。
- 验收标准 1「30 秒创建 3 动作计划」没有定义度量方式。

### 0.5 v2.0 主要增量一览

| 模块 | v1.1 | v2.0 |
|------|------|------|
| 动作库 | 无（字符串） | 独立实体 + 内置种子库 + 自定义 + FTS 搜索 + 肌群/器械分类 |
| 处方模型 | 组数 + reps | 组级明细（PlanSet）：热身/递减/金字塔/AMRAP/超级组 |
| 记录模型 | 1 条汇总 | Session → Exercise → **Set** 三级 + 重量/次数/时长/距离/RPE |
| 休息计时 | 静态字段 | 独立计时器 + 系统自绘倒计时通知 + 到点闹钟提醒 + 持久化恢复（**不用前台服务**） |
| 对比 | 无 | 上次成绩回显、前次自动填充、目标 vs 实际对比 |
| 成就 | 无 | PR 检测（重量/次数/容量/估算 1RM）+ 庆祝动效 |
| 统计 | 无 | 容量趋势、肌群分布、频率/连续打卡、日历热力图 |
| 身体数据 | 无 | 体重/体脂/围度记录 + 趋势图 |
| 设置 | 无 | 单位、默认休息、主题、周起始、提醒、备份 |
| 导出 | JSON（媒体需重连） | **zip 包（含媒体）** + JSON/CSV 单表导出 + 分享单计划 |
| 备份 | 无 | Android Auto Backup（排除媒体）+ 手动本地备份 |
| 通知 | 无 | 训练提醒、休息计时、PR 通知 |
| 集成 | 无 | Health Connect 读取体重/写入训练 |
| 权限 | 4 项（缺 1 关键项） | 完整清单含 `POST_NOTIFICATIONS`、`VIBRATE`、`FOREGROUND_SERVICE_*` 等 |
| 非功能 | 性能 4 条 | 性能/质量/无障碍/i18n/安全/可观测性 6 类 |
| 架构 | 单模块 app/ | 多模块 + KSP + Version Catalog + Navigation + DataStore + WorkManager |

---

## 1. 项目背景与目标

### 1.1 背景

力量训练人群在训练过程中需要记录：练了什么动作、用了多重、做了多少次、休息多久、本次相比上次是否有进步。现有工具的问题不是"不能记"，而是：

1. **计划模板固化**：无法表达真实的训练处方（热身组、递减组、超级组、周期变化）。
2. **记录粒度粗**：只能记"做了 3 组"，记不了"第 3 组 62.5kg 做了 7 次，最后 1 次有辅助"。
3. **多媒体演示流程繁琐**：动作不会做时，需要跳出 App 去搜索。
4. **数据锁死**：用户数据无法导出，迁移成本高，反向降低用户信任。

本项目定位：**以「组级精度的训练记录」为内核，以「自定义计划 + 动作多媒体演示」为差异化**的 Android 原生工具。

### 1.2 产品定位与范围

**In Scope（v1.x）**
- 训练计划的创建/编辑/复用/分组
- 动作库（内置种子 + 用户自定义）与组级处方编排
- 训练执行：休息计时、前次成绩、PR、跳过/替换/临时加组
- 训练历史与统计洞察（容量/频率/肌群/趋势）
- 身体数据（体重、体脂、围度）
- 动作演示（GIF/WebP/本地视频/外链）
- 本地优先存储、完整导出/导入、Health Connect 集成
- 通知与训练提醒

**Out of Scope（明确不做，避免范围膨胀）**
- 饮食与热量记录（另开产品）
- 社交社区、排行榜、关注关系
- 教练端后台与学员管理（v1 只做"导出计划分享"）
- 周期化编程自动生成（mesocycle 自动编排）
- AI 动作识别 / 摄像头姿态纠正
- Wear OS 伴侣应用（v1.x 之后评估）
- 云端账号与多端同步（v1.2 可选，见 §10）

### 1.3 产品目标

| 目标 | 度量 |
|------|------|
| 记录效率 | 完成 1 组记录的平均操作步数 ≤ 2 步（点击"完成"即自动带出上次重量与次数） |
| 数据可信 | 训练中途进程被杀，重进 App 可恢复到中断的那一组，数据零丢失 |
| 渐进超负荷可见 | 打开任意动作详情，可 1 屏内看到最近 10 次的重量/次数趋势 |
| 离线完整 | 飞行模式下 100% 功能可用（除外部链接播放） |
| 数据可携带 | 导出 zip 后可在另一台设备完整恢复（含媒体） |

### 1.4 目标用户与典型场景

| 角色 | 描述 | 核心诉求 | 关键场景 |
|------|------|---------|---------|
| 健身新手 | 训练 < 1 年，跟着计划练 | 动作不会做要有演示；不知道怎么加重量 | 组间看动图学动作；看"下次建议重量" |
| 进阶爱好者 | 训练 1-5 年，自己有体系 | 精确记录组级数据、看趋势、追 PR | 每做完一组点一下；练完看容量对比上周 |
| 力量举/健力爱好者 | 关注 1RM、RPE/RIR | 热身组编排、RPE 记录、1RM 估算 | 用 40/60/80% 热身；记录 RPE 9 |
| 教练 | 给学员制定动作规范 | 计划可复用、可分享、动作要点可标注 | 导出计划文件发给学员 |

### 1.5 竞品基线（功能下限）

v1.0 必须覆盖以下竞品的**并集下限**，否则用户没有迁移理由：

| 能力 | Strong | Hevy | Jefit | Juggernaut AI | 本项目 v1.0 |
|------|:---:|:---:|:---:|:---:|:---:|
| 组级重量/次数记录 | Y | Y | Y | Y | **必须** |
| 休息计时器（后台） | Y | Y | Y | Y | **必须** |
| 上次成绩回显 | Y | Y | Y | Y | **必须** |
| 动作库 + 自定义 | Y | Y | Y | Y | **必须** |
| PR / 1RM 估算 | Y | Y | Y | Y | **必须** |
| 统计图表 | 部分 | Y | Y | Y | **必须（基础版）** |
| 超级组 | Y | Y | Y | Y | **必须** |
| 动作多媒体演示 | Y | Y | Y | Y | **差异化（本地媒体 + 自定义）** |
| 数据导出（含媒体） | Y | Y | 部分 | 部分 | **差异化（zip 完整导出）** |
| 完全离线 | 部分 | 部分 | N | N | **差异化** |
| Health Connect | N | Y | Y | N | v1.1 |
| 社交/云同步 | Y | Y | Y | Y | Out of Scope |

### 1.6 术语表

| 术语 | 定义 |
|------|------|
| 动作库条目（Exercise） | 规范化动作定义，含肌群、器械、动作类型、默认参数 |
| 处方（Plan Exercise） | 计划中某个动作的编排，含目标组数/次数/重量 |
| 组（Set） | 一次连续完成的动作单元，是记录的最小粒度 |
| 热身组（Warm-up） | 不计入正式组统计、不计入 PR 的预热组 |
| 递减组（Drop set） | 同一组内无休息降低重量继续做 |
| 超级组（Superset） | 两个或多个动作连续完成、动作之间不休息 |
| AMRAP | As Many Reps As Possible，力竭次数（预留 X 次则记 RIR） |
| RPE / RIR | 主观疲劳度 1-10 / 剩余可完成次数 |
| 容量（Volume） | Σ(重量 × 次数)，单位 kg，用于衡量训练量 |
| 1RM | 单次最大重量；用 Epley/Brzycki 公式估算（e1RM） |
| PR | Personal Record，个人最好成绩 |
| e1RM | estimated 1RM，估算 1RM |

---

## 2. 功能需求

### 2.0 功能地图

```
[首次启动] 引导 Onboarding → 单位/目标/经验/器械
     │
     ├─ 首页（今日）── 今日计划 / 快速开始 / 上次训练 / 本周统计
     │
     ├─ 计划 ── 列表(分组/搜索/收藏) ── 详情 ── 编辑(动作编排/组级处方/媒体)
     │                                        └─ 开始训练
     │
     ├─ 动作库 ── 搜索(肌群/器械/类型) ── 详情(演示/说明/历史/PR/趋势)
     │
     ├─ 训练中 ── 动作列表 ── 组记录 ── 休息计时器 ── 完成/总结
     │
     ├─ 历史 ── 日历/列表 ── 会话详情(逐组复盘) ── 编辑/删除
     │
     ├─ 统计 ── 容量趋势 / 肌群分布 / 频率 / 连续打卡 / PR 列表
     │
     ├─ 身体数据 ── 体重/体脂/围度 ── 趋势图
     │
     └─ 设置 ── 单位 / 计时默认值 / 通知 / 备份与恢复 / 隐私 / 关于
```

### 2.1 首次启动与引导

#### FR-00 Onboarding（新增）
- 三步以内完成，可跳过：
  1. **单位**：kg / lb（决定全部重量显示与输入）
  2. **训练目标**：增肌 / 减脂 / 力量 / 保持健康
  3. **经验与器械**：训练年限 + 可用器械（健身房/家庭/徒手）
- 引导结果写入 DataStore，用于：
  - 首页统计卡片的默认指标
  - 动作库推荐排序权重
  - 新建计划时的默认动作建议
- 首次启动展示隐私政策与服务条款（必须勾选同意，Android 数据安全表单要求一致）
- 内置动作库种子数据在首次启动时从 `assets/exercise_seed.json` 导入（见 FR-20）

### 2.2 训练计划管理

#### FR-01 计划列表（首页/计划页）
- 展示全部计划，默认按 `updatedAt` 倒序；支持切换「最近编辑 / 最近训练 / 自定义排序 / 名称」
- 每项显示：计划名称、动作数量、目标组数合计、预估总时长、**最近一次训练日期**、**今日是否已排期**
- 搜索：按名称 **和动作名**模糊搜索（Room FTS4）
- 筛选：收藏 / 分组 / 归档
- 计划分组（文件夹）：新建、重命名、拖拽归组、删除分组（组内计划移出而非删除）
- 空状态：引导插画 + 「创建第一个训练计划」+ 「从模板库开始」
- 加载状态：Skeleton 占位（与真实条目布局一致，避免跳动）
- 错误状态：错误文案 + 重试按钮 + 错误码
- 长按条目：复制计划 / 分享 / 归档 / 删除
- 侧滑：收藏、删除（带 Undo）
- **删除/归档均支持 5 秒内 Undo（Snackbar）**，与回收站并存

#### FR-02 新建训练计划
- 必填：计划名称（1-60 字符，去除首尾空格，同名允许但提示）
- 选填：描述（≤ 500 字符）、训练日排期（周一~周日多选 / 不排期）、颜色标签、分组
- 可选：从模板创建（空计划 / 从已有计划复制 / 内置模板「推/拉/腿」「上/下肢」「全身」）
- 新建时可一键带入上一计划的动作清单（"复制结构"）
- 创建后直接进入计划详情编辑页，光标定位到可编辑区域

#### FR-03 编辑训练计划
- 可修改名称、描述、排期、颜色、分组
- 动作编排：添加（从动作库搜索/最近使用/自定义）、删除、复制、排序
- **计划级默认休息时间**与**动作级覆盖**（优先级：动作级 > 计划级 > 全局设置）
- 编辑计划时的动作条目支持 **Superset 编组**（多选动作 → 建组，UI 显示为带右侧竖线包裹的卡片）
- **自动保存**：
  - 采用**字段级 debounce 300ms 持久化**（而非 v1.1 的"每 3 秒整体保存"），避免最后一次输入丢失
  - 顶部状态提示「已保存 / 保存中」
  - 保存失败时立即提示并保留本地内存态，不清空用户输入
- 退出编辑页：若存在未持久化的修改，弹确认弹窗（放弃 / 继续编辑 / 保存并退出）
- **进程死亡恢复**：编辑态通过 `SavedStateHandle` + 草稿行（draft）双重保障

#### FR-04 删除训练计划
- 二次确认弹窗：显示计划名称、动作数量、历史训练次数、关联媒体数量与占用空间
- 删除策略可选：
  - **移入回收站**（默认，保留 30 天，v1.1 为 7 天，见 §0.4 理由）
  - **永久删除**（明确二次确认，需输入计划名确认）
- 若计划含本地媒体，额外提示「将同时释放 XX MB」，并可勾选「保留媒体文件」（保留则转为孤立文件，等待 GC 可恢复）
- 回收站页面：显示剩余保留天数、原计划动作数、恢复 / 永久删除 / 清空回收站
- 回收站自动清理由 WorkManager 每日执行（见 FR-41）

#### FR-05 计划分享与导入
- 单计划导出为 `.fitplan` 文件（zip：`plan.json` + `media/` + 可选缩略图）
- 通过系统分享面板发送（微信/邮件/网盘）
- 支持从 `ACTION_VIEW` / `ACTION_SEND` 接收 `.fitplan` 文件并导入
- 导入时处理冲突：重名计划自动加后缀「(导入)」；动作库缺失动作自动创建为自定义动作

### 2.3 动作库

#### FR-20 动作库数据
- 内置种子库：从 `assets/exercise_seed.json` 首次启动导入（目标 ≥ 300 条常见动作，含中文名与英文别名）
- 每条动作字段见 §5 数据模型（ExerciseDefinition）
- 支持用户自定义动作（`isCustom = true`），可编辑/删除；删除自定义动作时若被计划引用 → 提示"仅归档"而非硬删除
- **动作归档（Archive）**：不再展示于选择器，但历史记录仍可正确解析显示

#### FR-21 动作选择器
- 搜索：名称、别名、肌群拼音首字母（如 "wutui" → 深蹲类）
- 筛选：肌群（胸/背/肩/腿/臀/手臂/核心/全身/有氧）、器械（杠铃/哑铃/器械/绳索/自重/壶铃/弹力带/其他）、动作类型、收藏
- 排序：相关度 / 最近使用 / 使用频次 / 名称
- 分组 Tab：全部 / 收藏 / 最近 / 自定义
- 空结果：提供「将 "XXX" 创建为新动作」快捷入口

#### FR-22 动作详情页
- 头部：动作名、肌群标签、器械标签、动作类型
- 演示资源区（复用 FR-30~FR-34 能力）
- 动作要点 / 说明文本
- **数据区**（v1.0 必须）：
  - PR 卡片（最大重量 / 最多次数 / 最大容量 / 估算 1RM）
  - 最近 10 次训练记录列表（日期、组数、最好一组重量×次数、总容量）
  - 重量 / e1RM / 容量趋势折线图（可切换指标与时间范围 1M / 3M / 6M / 1Y / 全部）
- 操作：加入计划 / 立即开始训练 / 收藏 / 编辑（自定义动作）

#### FR-23 动作类型与记录表单联动

| ExerciseType | 记录字段 | 容量计算 |
|--------------|---------|---------|
| `WEIGHT_REPS` | 重量 + 次数 | 重量 × 次数 |
| `REPS_ONLY`（如引体、俯卧撑） | 次数（+ 可选附加重量） | (体重 + 附加重量) × 次数 |
| `WEIGHT_TIME`（如负重平板支撑） | 重量 + 时长 | 重量 × 时长（秒）/60（按分钟计） |
| `TIME`（如平板支撑） | 时长 | 不参与重量容量，单独计"总时长" |
| `DISTANCE_TIME`（如跑步机、划船机） | 距离 + 时长 | 不计重量容量 |
| `WEIGHT_DISTANCE_TIME`（如农夫行走） | 重量 + 距离 + 时长 | 重量 × 距离 |

> 记录表单必须由 `ExerciseType` 驱动动态渲染，禁止在 UI 层硬编码字段组合。

### 2.4 计划内动作与组级处方

#### FR-06 动作条目结构

**动作级（PlanExercise）**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| 动作 | 引用 ExerciseDefinition | 是 | — | 从动作库选择或现场创建 |
| 展示名快照 | 文本 | 是 | 动作名 | 冗余存储，防止动作改名影响历史 |
| 目标组数 | 整数 | 是 | 3 | 1-30（v1.1 的 20 上限过小，高次数动作常做 20+ 组） |
| 组间休息 | 整数（秒） | 否 | 继承计划/全局 | 0-900 |
| 组级明细 | 列表 | 否 | 由目标值展开 | 见 FR-07 |
| 超级组编组 | 标签 | 否 | — | 同标签动作为一组超级组 |
| 动作要点 | 文本 | 否 | 继承动作库 | ≤ 1000 字符 |
| 演示资源 | 媒体 | 否 | 继承动作库 | 计划内可覆盖动作库默认媒体 |
| 是否单侧 | 布尔 | 否 | 继承动作库 | 单侧动作记录时区分左/右 |

#### FR-07 组级处方（新增，P0）
- 支持对每个动作展开「组明细」编辑，默认由目标组数 × 目标次数自动生成
- 每组的可编辑项：组类型、目标重量、目标次数、目标时长、目标 RPE、是否单侧分别记录
- 批量操作：全选后改次数/重量、快速填充、按百分比计算（如"全部设为 70% 1RM"）
- **组类型（SetType）**：`NORMAL`（正式组）、`WARMUP`（热身组）、`DROP`（递减组）、`FAILURE`（力竭组）、`AMRAP`（极限次数）
- **热身组生成器**：输入正式组重量 → 一键生成常用热身序列（如 40%×8、60%×5、80%×3），可自定义模板
- 热身组规则：不计入 PR、默认不计入总容量（可设置包含）、在训练页折叠显示
- **重量单位换算**：输入 lb 时内部统一换算为 kg 存储（保留 2 位小数），显示时按用户单位还原，避免累积精度损失

#### FR-08 计划总时长计算（公式修订）
v1.1 公式 `Σ(每组次数 × 动作时长) + Σ(组间休息 × (组数 - 1))` 存在三处错误：未计入组内动作时长、未计入超级组（超级组组间无休息）、`组数 - 1` 对单组动作与热身组处理不当。

**v2.0 公式**：

```
T_exercise(i) = Σ_{s ∈ sets(i)} D(s)
             + max(0, setCount(i) - 1) × R_effective(i)
其中：
  D(s) = 目标时长？ 目标时长
       : 目标次数 × T_per_rep        （WEIGHT_REPS / REPS_ONLY，T_per_rep 默认 3s，可配置）
       : 距离 / 速度估算时长          （DISTANCE_TIME 类）
  R_effective(i) = 动作级休息 ?: 计划级休息 ?: 全局默认（默认 90s）
  superset 内相邻动作之间 R = 0，仅在同组最后一个动作后计入一次休息
T_plan = Σ_i T_exercise(i) + 动作切换开销 (n_exercises × 5s)
```

- 展示：计划详情头部「预估 45-60 分钟」（给区间而非精确值，因为热身/找器械时间不可预测）
- 展示「动作数 / 总组数」作为辅助指标
- 该值为**缓存字段**（`estimatedDurationSeconds`），在 `updatedAt` 变更时重算，避免每次列表渲染重算

#### FR-09 动作排序
- 长按拖拽（`LazyColumn` + `detectDragGesturesAfterLongPress`），拖拽时半透明浮层 + 插入指示线 + 触觉反馈
- 同时提供**无障碍友好的"上移/下移"按钮**（TalkBack 用户无法长按拖拽）
- **排序算法**：`orderIndex` 使用 `Double` 并取相邻两项中值，无需全表重写；每 50 次重排或精度不足时执行一次规范化（reindex）
- 排序结果立即持久化，支持 Undo

### 2.5 训练执行（核心）

#### FR-10 开始训练
- 入口：计划详情「开始训练」、首页「今日计划」、计划列表长按「开始」、动作详情「立即开始」
- **支持"空训练/自由训练"**（不选计划，从零添加动作），满足临时加练场景
- 开始前展示"本计划上次练于 X 天前"，并提供：
  - **使用上次数据**（自动填充上次的重量/次数作目标）
  - **使用计划处方**
  - **空白开始**
- 若存在未结束的训练会话，提示"恢复上次未完成的训练 / 放弃并开始新的"

#### FR-11 训练执行页结构
自上而下：
1. **顶部栏**：已用时间（含暂停时间单独显示）、总容量、完成组数 / 总组数、进度条；右端「···」（跳过/替换/添加动作/结束训练）
2. **当前动作卡片**：动作名、演示资源（可折叠）、目标处方、**上次成绩**、本动作已完成组列表
3. **组记录行**（列表）：序号 / 重量 / 次数 / RPE / 完成勾选；已完成行显示"对比上次"箭头（↑↓=）
4. **当前组输入区**：按 `ExerciseType` 动态渲染字段；**自动带入上一组或上次训练的数值**；键盘数字类型；± 步进按钮（重量 ±2.5kg/±5lb，次数 ±1）
5. **底部操作**：完成本组 / 跳过本组 / 添加一组 / 删除末组 / 结束本动作
6. **全局悬浮休息计时器**（见 FR-12）

#### FR-12 休息计时器（新增，P0）
- 完成一组后**自动启动**（可在设置中关闭或设为手动）
- 时长优先级：本组设定 > 动作级 > 计划级 > 全局默认
- UI：悬浮胶囊显示倒计时 + 环形进度；可「+15s / -15s / 跳过休息 / 暂停」
- **后台可靠性**（P0）：
  - **不使用前台服务**。计时由「系统自绘倒计时通知 + 系统闹钟到点提醒」承载，零权限、零后台耗电、无商店审核风险（技术方案见 DEV 文档 ADR-06 / §8.3）
  - 通知栏使用系统 Chronometer 自绘倒计时（应用无需常驻、无需每秒刷新），并提供三个快捷操作（跳过 / +30s / 暂停）；通知不可滑动清除
  - **正确性不依赖任何后台机制**：剩余时间永远由持久化的 `endsAt` 与当前时间计算。因此进程被杀、通知被清除、闹钟被延迟，都**不会**导致倒计时错误，最坏情况只是"少一次提示"
  - 计时状态持久化到数据库，进程被杀后重进 App 自动恢复；设备重启后状态已过期，直接清理
- 结束提示：通知 + 震动 + 可选提示音（使用 `USAGE_ASSISTANCE_SONIFICATION` 音频属性，**不打断用户正在播放的音乐**）+ 可选 TTS 播报（"休息结束，下一组 60 公斤 8 次"）
- 用户拒绝通知权限时：仅失去通知栏倒计时与到点提醒，**应用内倒计时与提示音照常工作**（提示音由应用内播放，不需要通知权限）
- **不再需要**"引导用户关闭电池优化"：由于不存在常驻服务，系统清理应用进程不影响通知（由 SystemUI 持有）与闹钟（由 AlarmManager 持有）

#### FR-13 组操作能力
- 完成组：默认"完成并进入休息"
- **复制上一组**、**复制上次训练的同序号组**
- 修改已完成组（长按进入编辑）
- **添加/插入/删除任意一组**（含正式组与热身组）
- **替换动作**：把当前动作替换为动作库中的其他动作（保留已记录数据或清空，二选一）
- **临时添加动作**（不入计划，仅本次会话）
- **跳过动作 / 跳过某组**（记录为 `SKIPPED`，不影响计划配置）
- 标记为失败组（`FAILED`）、递减组、力竭组
- 单侧动作：分别记录左/右（同一组内左右数据）

#### FR-14 训练中辅助工具
- **前次成绩回显**：每个动作显示"上次：60kg × 8, 8, 7"
- **目标 vs 实际对比**：完成组后显示差值
- **渐进超负荷建议**（v1.0 基础版，可关闭）：
  - 双进阶（Double Progression）：若上次所有正式组均达到次数区间上限 → 本次建议 +最小递增（杠铃 2.5kg / 哑铃 1kg，可配置）
  - 若未达到下限 → 建议维持重量
  - 建议仅作为提示文案，**不自动修改用户输入**
- **1RM 计算器 / 百分比换算**（输入重量与次数 → 输出 e1RM 与 70%~95% 对应重量）
- **杠铃片计算器**：输入目标重量与杠铃杆重 → 输出每侧配片方案（含可选片重配置）
- **RPE / RIR 记录**（可选字段，默认隐藏，设置中开启）
- **训练计时**：暂停/恢复（暂停期间不计入训练时长，单独累计 `autoPausedTotalSeconds`）
- **屏幕常亮**（训练页默认开启，可设置）
- **进程死亡恢复**：所有组数据实时落库（完成即写），无内存缓存丢失风险

#### FR-15 完成训练与总结
- 结束训练（已完成 / 提前结束，两种语义）
- 若有未完成组：提示"还有 N 组未完成，标记为跳过并结束？"
- **训练总结页**：
  - 总时长（含/不含休息）、总容量、总组数、总次数
  - 与上次同计划对比：容量变化 %、时长变化、每组对比明细
  - **PR 命中列表**（高亮 + 庆祝动效）
  - 各动作容量与肌群分布饼图
  - 主观感受（RPE 1-10 / 心情 emoji）
  - 备注（≤ 1000 字符）
  - 分享为图片（生成训练卡片图，可分享到社交平台 —— 这是最强的自传播机制）
- 保存后可编辑历史记录（允许补记/纠正）

### 2.6 训练历史与统计

#### FR-16 历史记录
- 视图切换：**日历视图（月，热力图）/ 列表视图**
- 列表按日期倒序，分组显示（今天/本周/更早）
- 每条显示：计划名、动作数、总容量、时长、日期、PR 标记
- 展开/进入详情：逐动作逐组完整数据（目标 vs 实际）、备注、感受
- 筛选：按计划、按动作、按日期范围
- 操作：编辑、复制为计划、删除（二次确认 + Undo）、手动补录训练

#### FR-17 统计仪表盘（新增）
- **概览卡片**：本周训练次数 / 目标次数、连续训练周数（streak）、本周容量、累计训练次数
- **容量趋势**：按周/月聚合的总容量折线图
- **肌群分布**：按主要肌群聚合的容量占比（饼图/条形），可切换"组数"口径
- **动作频率榜**：最常练的 Top 10 动作
- **个人纪录列表**：按动作分组展示各类 PR 及达成时间
- **训练热力图**：GitHub 风格年历热力图
- 时间范围切换：本周 / 本月 / 3 个月 / 全年 / 全部
- 所有统计基于 SQL 聚合查询，禁止全表加载到内存计算

#### FR-18 个人纪录（PR）（新增）
- PR 类型：`MAX_WEIGHT`（最大重量）、`MAX_REPS`（同重量最多次数）、`MAX_VOLUME_SET`（单组最大容量）、`MAX_E1RM`（最大估算 1RM）
- 检测时机：完成一组时实时判定并落库；仅正式组参与（热身组与递减组默认排除，可配置）
- 展示：训练中即时 Toast/动效（"新纪录！卧推 80kg × 5"）、动作详情 PR 卡片、统计页 PR 列表、通知（可选）
- 支持手动清除某动作 PR（数据修正场景）

#### FR-19 身体数据（新增）
- 记录项：体重（必）、体脂率、骨骼肌量、围度（胸/腰/臀/臂/腿，可选）
- 输入：手动录入 / Health Connect 同步（见 FR-45）
- 每项独立趋势图，支持按周/月聚合
- 体重用途：
  - `REPS_ONLY` 类动作（引体、俯卧撑）的容量计算
  - 体重变化趋势与训练量相关性提示（v1.1）
- 支持设置"体重提醒"（每周固定日提醒记录）

### 2.7 动作演示资源

#### FR-30 资源上传与关联

| 类型 | 格式 | 大小限制 | 说明 |
|------|------|---------|------|
| 动图 | GIF、动态 WebP、APNG | ≤ 20 MB | 循环播放 |
| 视频（本地） | MP4（H.264/H.265）、WebM（VP9/AV1）、MOV | ≤ 200 MB | 用户拍摄或导入 |
| 视频（链接） | YouTube / Vimeo / Bilibili / 直链 MP4 | 无 | 不占本地存储；需按域名白名单处理 |

**上传方式**
1. 从相册/文件选择：**优先 `ActivityResultContracts.PickVisualMedia`（Photo Picker）**
   - 回填依赖 Google Play 服务；**必须提供 SAF 降级**（`ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`），保证无 GMS 机型（国内华为/荣耀等）可用
2. 拍摄新视频：`ActivityResultContracts.CaptureVideo`（输出到 `FileProvider` 的私有目录，无需 `CAMERA` 权限：若使用系统相机 Intent，则**不需要** `CAMERA` 权限；只有自实现 CameraX 预览才需要）
3. 输入外部链接：输入后即时校验 URL 合法性 + 尝试 HEAD 请求探测可达性（可选，弱网失败不阻断保存）
4. **剪贴板粘贴链接**自动识别

**校验与处理流程**
1. 读取元数据（大小、时长、分辨率、编码）
2. 超限 → 拒绝并提示具体数值；**提供"压缩到 720p 后导入"选项**（Media3 Transformer，本地转码，展示进度）
3. 空间检查：剩余空间 < 文件大小 × 2 + 200MB 时警告
4. 复制到 `filesDir/media/{UUID}.{ext}`（流式复制，避免大文件 OOM）
5. 生成缩略图（`ContentResolver.loadThumbnail()` API 29+，低版本用 `MediaMetadataRetriever`）→ 存 `filesDir/thumbs/{UUID}.webp`，尺寸 512px 长边，质量 80
6. 写入数据库（文件名 + 缩略图名 + 元数据），**不存绝对路径**（沙箱路径在升级/迁移后可能变化）
7. 全过程在应用内协程执行 + 进度可视化 + 屏幕常亮，支持一键取消；本地复制的耗时通常在 3 秒内，转码在 60 秒内。若单次转码预估超过 60 秒，提示用户"请保持应用在前台"，避免为一个边缘场景引入前台服务（**不申请 `FOREGROUND_SERVICE_*`**）

**存储策略**
- 本地：`filesDir/media/`（私有，无需权限）
- 命名：`{UUID}.{ext}`；缩略图 `{UUID}.webp`
- 数据库：仅存文件名与相对标识
- **`android:allowBackup` + `dataExtractionRules` 必须排除 `media/` 与 `thumbs/`**（避免 25MB 备份配额被撑爆导致备份整体失败）；数据库与偏好仍需纳入备份

#### FR-31 演示预览与播放
- 条目内嵌缩略图（Coil 3 加载本地缩略图，**不直接解码原视频**）
- 点击进入全屏播放：
  - 动图：Coil `AnimatedImageDecoder` 自动循环
  - 视频：Media3 `ExoPlayer`，支持播放/暂停、进度拖动、倍速 0.25/0.5/0.75/1/1.25/1.5/2x、**循环播放**、**逐帧步进**（-0.1s/+0.1s，教学场景刚需）、音量控制
  - **横屏全屏** + 沉浸式；退出恢复竖屏
  - **音频焦点**：使用 Media3 `setAudioAttributes(handleAudioFocus = true)`；播放时会暂停用户音乐并在结束后恢复（这是期望行为）
- 外部链接：优先内嵌 WebView；若域名不在白名单或加载失败 → "在浏览器中打开"降级
- 播放器状态在配置变更（旋转）时不重置位置

#### FR-32 资源替换与删除
- 替换：新资源导入成功后再删除旧文件（避免中间态丢文件）
- 删除时选择：「仅解除关联」/「同时删除本地文件」
- 删除本地文件前检查是否被其他动作引用（引用计数或全局扫描），有引用则仅解除关联
- 删除动作库条目时同步处理其默认媒体

#### FR-33 孤立媒体清理
- WorkManager 周期任务（每周，仅充电+空闲时执行）：扫描 `filesDir/media/`，与数据库引用集合比对
- 未引用的文件标记为"待清理"，超过 30 天（v1.1 为 7 天）的文件在设置页提示用户可释放的空间，**由用户确认后删除**（不静默删除用户数据）
- 设置页展示媒体占用空间统计：总数、总大小、可清理大小

### 2.8 设置

#### FR-34 设置项（新增，P0）

| 分组 | 设置项 | 默认值 |
|------|--------|--------|
| 单位 | 重量单位 kg/lb、距离单位 km/mi | kg / km |
| 训练 | 默认组间休息（秒）、自动启动休息计时、休息提示音、震动、TTS 播报、屏幕常亮、自动带入上次数据 | 90s / 开 / 开 / 开 / 关 / 开 / 开 |
| 训练 | 重量步进值、是否显示 RPE、是否记录热身组容量 | 2.5kg / 关 / 否 |
| 显示 | 主题（跟随系统/浅色/深色）、语言（中文/English）、周起始日 | 跟随系统 / 中文 / 周一 |
| 通知 | 训练提醒（星期 + 时间）、休息计时通知、PR 通知、体重记录提醒、连续未训练提醒 | 全关 |
| 数据 | 导出备份 / 从备份恢复 / 导出 CSV / 清空所有数据 / 媒体空间统计 | — |
| 隐私 | 隐私政策、Health Connect 授权管理、崩溃报告开关（默认关） | — |
| 关于 | 版本、开源许可、反馈 | — |

- 所有偏好存于 **DataStore (Proto 或 Preferences)**，通过 `Flow` 暴露给 UI
- 「清空所有数据」需二次确认 + 输入"DELETE"确认，并同时清除媒体文件

### 2.9 数据管理

#### FR-40 本地存储
- 计划 / 动作 / 会话 / 组 / 身体数据 → Room
- 媒体文件 → `filesDir/media/`
- 用户偏好 → DataStore
- 无需网络即可完整使用

#### FR-41 备份、导出与导入

| 方式 | 内容 | 说明 |
|------|------|------|
| **完整备份 zip** | 数据库快照（JSON）+ 全部媒体 | 单文件，可存网盘/分享；**导入即完整恢复，无需手动重连媒体**（修复 v1.1 的 P1-6） |
| 单计划导出 `.fitplan` | 单个计划 + 相关媒体 | 用于分享给他人 |
| CSV 导出 | 训练记录平铺表（日期/计划/动作/组号/重量/次数） | 兼容 Excel / 其他 App（Strong/Hevy 的导入格式） |
| Android Auto Backup | 数据库 + 偏好（**排除媒体**） | 换机自动恢复，需配置 `dataExtractionRules` |
| 导入 | 支持本项目 zip / CSV / 第三方 CSV（Strong、Hevy、FitNotes 映射适配器） | 导入前预校验并展示预览（将新增 N 个计划 / M 条记录） |

- 导出文件带版本号与校验和，导入时校验版本兼容性
- 使用 `kotlinx.serialization`；JSON schema 显式版本化（`schemaVersion: 2`），支持向前兼容解析
- 导出/导入均为流式处理（`ZipOutputStream`），避免大文件 OOM
- 导入使用单个 Room 事务，失败整体回滚

#### FR-42 回收站与清理
- 回收站保留 **30 天**（v1.1 的 7 天过短，用户往往一个月后才想起找回）
- WorkManager 每日任务：清理超期回收站条目及其独占媒体
- 会话记录删除采用软删除 + 每日清理

#### FR-43 数据库迁移
- 使用 Room `Migration`，每次 schema 变更提供 `MIGRATION_X_Y`
- 开启 `exportSchema = true`，schema JSON 纳入版本控制
- **禁止 `fallbackToDestructiveMigration()`**（保持 v1.1 要求，正确）
- 每个 Migration 必须有对应测试（`MigrationTestHelper` + 使用旧版本 schema JSON 验证数据）
- 大表迁移（如 v1.1 的 `WorkoutRecord` → v2.0 的三级模型）需编写专门的数据搬迁脚本与回归测试

### 2.10 通知与提醒（新增）

#### FR-44 通知体系

| 通知类型 | 触发 | 渠道 | 交互 |
|---------|------|------|------|
| 训练提醒 | 用户设定的星期/时间 | 默认渠道（High） | 点击进入今日计划 |
| 休息计时结束 | 计时归零 | 独立渠道（High + 震动） | 跳过/延长/暂停 |
| 休息倒计时进行中 | 完成一组后自动发布 | 独立渠道（Low，静默） | 系统自绘倒计时 + 快捷操作（跳过 / +30s / 暂停）；不可滑动清除 |
| PR 达成 | 完成组时判定 | 独立渠道（Default） | 点击进入动作详情 |
| 久未训练 | 超过 N 天未训练（可配置） | 独立渠道（Default） | 点击进入首页 |
| 体重记录提醒 | 每周固定日 | 独立渠道（Default） | 点击进入身体数据 |
| 媒体清理建议 | 孤立文件 > 30 天 | 独立渠道（Low） | 点击进入设置 |

- Android 13+ 需 `POST_NOTIFICATIONS` 运行时权限；首次触发前用**解释性前置弹窗**说明用途，再将系统弹窗延后到用户首次开启提醒功能时请求（提高授予率）
- 训练提醒使用 `AlarmManager`（`SCHEDULE_EXACT_ALARM`，用户可关闭时降级为非精确）或 WorkManager 定期任务
- 通知渠道在 Api 26+ 上创建，用户关闭后不再重复申请

### 2.11 平台集成（新增）

#### FR-45 Health Connect
- 写入：`ExerciseSessionRecord`（训练时长、类型、卡路里估算）
- 读取：`WeightRecord`（体重同步到身体数据）
- 权限：`health.READ_WEIGHT` / `health.WRITE_EXERCISE` 等，需在 Manifest 声明 + 提供隐私说明跳转 Activity（`ACTION_SHOW_PERMISSIONS_RATIONALE`）
- Android 14+ 为系统组件；更低版本通过 Play 商店的 Health Connect APK
- **无 GMS 环境（国内）需优雅降级**：检测不可用时隐藏入口，不报错
- 仅写入明确授权后的数据；提供开关与"停止同步"

#### FR-46 快捷入口（v1.1）
- 桌面小组件（Glance）：今日计划 + 「开始训练」按钮
- 应用快捷方式（Shortcuts）：长按图标 → 开始空训练 / 今日计划 / 历史
- 通知快捷操作（见 FR-44）

### 2.12 计时与业务规则（横切）

#### FR-50 时间与时间戳规则
- 所有时间戳使用 **UTC epoch millis** 存储，展示时按设备时区转换
- "训练日期"是**日期语义**（`LocalDate`），单独存储为 `epochDay`，避免跨时区/夏令时错位
- 训练时长使用**单调时钟**（`SystemClock.elapsedRealtime()`）计算，避免用户改系统时间导致时长异常
- 跨天训练（23:50 开始 00:30 结束）归属开始日

#### FR-51 单位换算
- 内部统一以 **kg 存储**（Double），保留 2 位小数
- 显示/输入按用户单位换算；1 lb = 0.45359237 kg
- 换算后回写不得改变原始输入值（存 `displayWeight` + `displayUnit` 快照）

---

## 3. 非功能需求

### 3.1 性能（NFR-P）

| 编号 | 指标 | 目标 | 手段 |
|------|------|------|------|
| NFR-P1 | 冷启动到首帧可交互 | ≤ 1.5s（中端机）；热启动 ≤ 500ms | Baseline Profile + App Startup + 延迟初始化 |
| NFR-P2 | 计划列表加载（100 条） | ≤ 300ms | Room 异步 + `LazyColumn` + 稳定 key + 分页（>200 条启用 Paging 3） |
| NFR-P3 | 长列表滚动 | Jank 帧占比 < 1%（P95 帧耗时 < 16ms） | Deferred read、`@Immutable` 数据类、避免 Lambda 捕获不稳定状态 |
| NFR-P4 | 动作库搜索（2000 条） | 输入到结果 ≤ 100ms | Room FTS4 + debounce 200ms |
| NFR-P5 | 缩略图加载 | ≤ 200ms（本地缓存命中） | 预生成缩略图 + Coil 磁盘/内存缓存 |
| NFR-P6 | 数据库查询 | 单查询 ≤ 50ms（95 分位） | 索引 + 聚合下沉到 SQL + 避免 N+1 |
| NFR-P7 | 大文件导入 | 200MB 视频复制/转码不阻塞 UI，进度可视 | 流式 IO + 应用内进度 + 屏幕常亮 + 可取消 |
| NFR-P8 | 训练页操作响应 | 点击"完成一组"到 UI 反馈 ≤ 100ms（先更新 UI 再异步落库） | 乐观更新 + 单事务批量写 |
| NFR-P9 | 内存峰值 | ≤ 200MB（训练场景）；无 OOM 崩溃 | 媒体不整块进内存；Bitmap 按需采样 |
| NFR-P10 | APK 下载体积 | ≤ 15MB（不含用户媒体） | R8 + 资源压缩 + 可选 Play Asset Delivery（种子动作库） |

- **必须建立 Macrobenchmark 模块**，将上述指标纳入 CI 趋势监控，而非一次性人工验证

### 3.2 兼容性（NFR-C）

| 编号 | 要求 |
|------|------|
| NFR-C1 | minSdk 26 / targetSdk 36（2026 年上架要求） |
| NFR-C2 | 深色模式（`DayNight` + Compose 主题），跟随系统或手动覆盖 |
| NFR-C3 | **Edge-to-edge 强制**（Android 15+ 默认 edge-to-edge，需正确处理 WindowInsets，禁止被系统栏遮挡） |
| NFR-C4 | 横竖屏：训练页与编辑页支持竖屏；视频播放支持横屏；旋转不丢失状态 |
| NFR-C5 | 折叠屏 / 平板：≥ 600dp 使用双栏布局（列表 + 详情） |
| NFR-C6 | 16 KB 页大小兼容（targetSdk 35+ 上架要求，需验证 native so；本项目纯 Kotlin，无 so 依赖则天然满足） |
| NFR-C7 | 适配 Android 15/16 的**预测性返回**（Predictive Back），Editor 页需拦截并弹确认 |
| NFR-C8 | 中国主流 ROM（小米/华为/OPPO/vivo）后台保活适配与引导 |
| NFR-C9 | 无 GMS 设备的降级路径（Photo Picker → SAF；Health Connect 隐藏） |
| NFR-C10 | 大字体（系统字体缩放 200%）下布局不截断、不重叠 |

### 3.3 权限策略（NFR-S，修订）

| 权限 | 用途 | 申请时机 | 备注 |
|------|------|---------|------|
| `POST_NOTIFICATIONS` | 训练提醒、休息倒计时通知、PR 通知 | 用户首次开启任一通知功能时 | **Android 13+ 必须**；被拒后仅失去通知栏倒计时与到点提醒，应用内计时照常 |
| `VIBRATE` | 休息结束震动、拖拽反馈 | 普通权限，安装即授予 | 无需运行时申请 |
| `INTERNET` | 外链视频播放、崩溃报告（可选） | 普通权限 | 需配置 `networkSecurityConfig` 禁止明文流量 |
| `ACCESS_NETWORK_STATE` | 外链可达性探测 | 普通权限 | 可选 |
| `RECEIVE_BOOT_COMPLETED` | 重启后清理过期的休息计时状态 | 普通权限 | 仅清理，不恢复 |
| Health Connect 权限 | 见 FR-45 | 用户开启健康同步时 | 需隐私说明 Activity；无 GMS 环境隐藏入口 |

**已收敛（明确不申请）的权限及理由**

| 权限 | 为何不需要 |
|------|-----------|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `WAKE_LOCK` | 休息计时器改用「普通通知（系统自绘倒计时）+ `AlarmManager.setAndAllowWhileIdle`」，**不使用前台服务**；既免审核风险，又支持最长 900s 休息，且零后台耗电 |
| `CAMERA` | 拍摄走系统相机 Intent（`CaptureVideo`），不需要该权限；不引入 CameraX |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_VISUAL_USER_SELECTED` / `READ_EXTERNAL_STORAGE` | 相册选择走 Photo Picker（GMS）或 SAF（无 GMS），主路径**零存储权限**，从而完全绕开 Android 14 部分媒体访问（reselect）的复杂度 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 休息计时改用无需权限的系统闹钟 API；仅"训练提醒"可选使用精确闹钟，被拒时自动降级为非精确并提示用户 |

**策略**
- **主路径零存储权限、零前台服务**：Photo Picker（GMS 机型）/ SAF（无 GMS 机型）
- 禁止 `MANAGE_EXTERNAL_STORAGE`（Play 政策直接下架）
- 禁止 `QUERY_ALL_PACKAGES`
- 所有权限被拒后提供解释性弹窗 + 跳转系统设置入口；**被永久拒绝时不得死循环弹窗**
- 权限申请使用 `rememberLauncherForActivityResult` + 明确的 rationale 文案
- 相机返回结果必须**双重校验**（uri 非空 + 文件可被解析），部分 ROM 相机取消时仍返回 `OK`

### 3.4 隐私与合规（NFR-PR）

| 编号 | 要求 |
|------|------|
| NFR-PR1 | 无账号体系；训练数据默认仅存本地，不上传服务端 |
| NFR-PR2 | 首次启动展示隐私政策与服务条款，需明示同意；设置内可随时查看 |
| NFR-PR3 | 若提供崩溃报告/统计分析 → **默认关闭，显式 opt-in**，且不采集训练内容、仅采集匿名堆栈与设备信息 |
| NFR-PR4 | 外链视频播放前提示"将跳转第三方服务，其隐私政策适用" |
| NFR-PR5 | 填写 Google Play **数据安全表单**（Data Safety），与实现保持一致 |
| NFR-PR6 | 健康数据（Health Connect）遵循额外披露要求，提供数据删除入口（"删除所有数据"需覆盖健康同步记录） |
| NFR-PR7 | 导出文件为明文时提示用户注意包含敏感身体数据 |
| NFR-PR8 | 未成年人相关：若面向儿童需符合 Families 政策（本项目面向成人，需在商店标注） |

### 3.5 错误处理与健壮性（NFR-E）

| 场景 | 处理方式 |
|------|---------|
| 数据库操作失败 | 捕获异常 → Snackbar（可重试）+ Timber 记录 + 不影响已完成的内存态 |
| 媒体文件丢失 | 缩略图位置显示占位图 + "文件已丢失，重新关联"按钮；不崩溃 |
| 缩略图生成失败 | 降级为动作类型图标，不阻塞导入 |
| 外部链接失效 | 错误提示 + "在浏览器中打开" + "移除链接" |
| 磁盘空间不足 | 导入前预检（文件大小 × 2 + 200MB 缓冲）；不足时提示需释放的具体空间量 |
| 权限被拒 / 永久拒绝 | 解释弹窗 + 跳转系统设置；提供"使用 SAF 手动选择"备选路径 |
| 进程被杀（训练中） | 恢复会话：从数据库重建全部状态，定位到未完成组 |
| 进程被杀（编辑中） | 从草稿表恢复；提示"检测到上次未保存的编辑，是否恢复？" |
| 设备重启 | 恢复休息计时（按墙钟续算或提示已过期）；不丢失已完成的组数据 |
| 导入文件损坏/版本不兼容 | 预校验 → 明确错误码与提示；事务回滚，不产生半成品数据 |
| 时区/夏令时变更 | 存储 UTC + epochDay，展示层转换；训练时长用单调时钟 |
| 用户修改系统时间 | 训练时长不受影响（单调时钟） |
| 转码失败/取消 | 保留原文件，清理临时文件（`cacheDir` 临时目录启动时清理） |
| 写入中断（磁盘满） | 临时文件写入 + 原子重命名（`File.renameTo`）后再写数据库，避免半文件 |
| 并发写入 | Room 单写事务；媒体操作用 `Mutex` 串行化 |
| 超大计划（100+ 动作） | 训练页使用 `LazyColumn` 懒加载；快速跳转到指定动作 |
| 超过 500 条历史记录 | 分页加载 + 日期分区 |
| 通知被系统屏蔽 | 引导用户到通知设置；计时器在应用内仍可用 |

### 3.6 无障碍与国际化（NFR-A / NFR-I）

| 编号 | 要求 |
|------|------|
| NFR-A1 | 目标 WCAG 2.1 AA；TalkBack 全流程可用 |
| NFR-A2 | 触控目标 ≥ 48dp；拖拽排序必须提供替代操作（上移/下移） |
| NFR-A3 | 所有图标/媒体有 `contentDescription`；纯装饰元素标记为 null |
| NFR-A4 | 文字对比度 ≥ 4.5:1；不仅靠颜色传达状态（如 PR 需同时有图标与文字） |
| NFR-A5 | 支持系统字体缩放至 200%；动态布局不截断 |
| NFR-A6 | 表单错误使用 `errorMessage` + `liveRegion` 播报 |
| NFR-I1 | 支持简体中文 + 英文（`values/` + `values-zh-rCN/`）；文案全部走资源文件，禁止硬编码 |
| NFR-I2 | RTL 布局安全（使用 `start/end` 而非 `left/right`） |
| NFR-I3 | 日期/数字格式使用 `Locale` 感知格式化 |

### 3.7 质量与可观测性（NFR-Q）

| 编号 | 指标 |
|------|------|
| NFR-Q1 | Crash-free sessions ≥ 99.5%；ANR 率 < 0.1% |
| NFR-Q2 | 日志：Timber，Release 仅记录 Warning 以上且无 PII；Debug 全量 |
| NFR-Q3 | 崩溃报告：接入 Play Console / Crashlytics，**默认关闭并 opt-in** |
| NFR-Q4 | 关键路径埋点（本地匿名）：首次建计划成功率、训练完成率、崩溃前操作栈 —— 用于产品迭代，不采集内容 |
| NFR-Q5 | 每个 Release 提供 Macrobenchmark 报告与截图测试基线对比 |

### 3.8 安全（NFR-SEC）

| 编号 | 要求 |
|------|------|
| NFR-SEC1 | `networkSecurityConfig`：禁止明文 HTTP（外链视频若为 http 需显式白名单或转为 https） |
| NFR-SEC2 | `FileProvider` 配置最小化暴露路径，禁止 `file://` URI 外传 |
| NFR-SEC3 | `android:exported` 显式声明；接收 `.fitplan` 的 Activity 需校验来源与文件内容（防 zip 炸弹：限制解压后总大小与文件数） |
| NFR-SEC4 | 导出 zip 解析时校验路径（防 Zip Slip 目录穿越） |
| NFR-SEC5 | 可选：身体数据敏感，提供"数据库加密（SQLCipher）"开关（v1.2） |

---

## 4. 技术架构

### 4.1 技术栈

> 原则：**所有版本在项目启动时以当日最新稳定版为准并锁定**（Version Catalog），不在文档中预先编造未来版本号。下表给出选型与验证要求。

| 层级 | 技术选型 | 备注 |
|------|---------|------|
| 语言 | Kotlin（最新稳定版） | 启用显式 API 模式（`explicitApi`）可选 |
| 构建 | Gradle Kotlin DSL + **Version Catalog**（`libs.versions.toml`） | 依赖集中管理；启用 configuration cache |
| 注解处理 | **KSP**（不用 kapt） | Room / Hilt 均支持，编译速度显著提升 |
| UI | Jetpack Compose + Material 3 | Compose BOM 统一版本；**不要手写单个 Compose 版本号** |
| 导航 | **Navigation Compose（type-safe routes）** | Kotlin Serialization 定义路由 |
| 架构 | MVVM + Repository + UseCase（Clean 精简版） | UI 只依赖 ViewModel 暴露的 `StateFlow<UiState>` |
| 本地数据库 | **Room**（最新稳定版；若需 KMP 再评估 3.x） | `exportSchema = true` + Migration 测试 |
| 全文搜索 | Room **FTS4** | 动作库与计划搜索 |
| 偏好存储 | **DataStore（Preferences/Proto）** | 单位、主题、计时默认值 |
| 后台任务 | **WorkManager** | 回收站清理、媒体 GC、提醒同步 |
| 媒体加载 | **Coil 3**（含 `coil-gif` 支持 GIF/动态 WebP） | Compose `AsyncImage` |
| 视频播放/转码 | **Media3**（ExoPlayer + Transformer + Session） | 播放、逐帧、倍速、压缩、后台播放 |
| 依赖注入 | **Hilt** | `@HiltViewModel` + `hilt-navigation-compose` |
| 异步 | Coroutines + Flow；`StateFlow` 暴露 UI 状态 | 结构化并发，`Dispatchers.IO` 限流 |
| 序列化 | **kotlinx.serialization** | 导出/导入 JSON，显式 `schemaVersion` |
| 图表 | Vico / Compose 自绘 Canvas | 趋势图、饼图、热力图 |
| 日期时间 | `java.time`（minSdk 26 已支持，无需 desugar） | `LocalDate` / `Instant` |
| 图标 | Material Symbols + 自定义矢量图 | — |
| 日志 | Timber | Release 降级 |
| 测试 | JUnit4 + **Turbine** + MockK + Robolectric + Compose UI Test + **Roborazzi/Paparazzi**（截图）+ **Macrobenchmark** | — |
| 静态检查 | Detekt + Ktlint + Android Lint（`warningsAsErrors`） | 纳入 CI |

**明确移除/纠正 v1.1 的问题**
- 删除"Compose BOM 2026.08.00 / Room 2.8.5 / Media3 1.11.1"这类未经验证的硬编码版本号（或改为启动时核实后锁定）
- 补充 v1.1 缺失的：Navigation、DataStore、WorkManager、kotlinx.serialization、Coil 3、图表库、测试框架、静态检查

### 4.2 模块化结构

```
:app                      # 装配层：Application、MainActivity、导航图、DI 聚合
:core:common              # 工具、Result 封装、Dispatcher 提供
:core:designsystem        # 主题、颜色、排版、通用 Compose 组件、图标
:core:database            # Room 实体/DAO/Database/Migration/种子数据
:core:datastore           # DataStore 偏好定义与读写
:core:model               # 领域模型（纯 Kotlin，无 Android 依赖）
:core:media               # 媒体导入/复制/缩略图/转码/GC
:core:testing             # 测试工具、Fake、测试数据构造器
:feature:onboarding       # 引导
:feature:plans            # 计划列表/详情/编辑
:feature:exercises        # 动作库/动作详情
:feature:workout          # 训练执行 + 休息计时器
:feature:history          # 历史记录
:feature:stats            # 统计与 PR
:feature:body             # 身体数据
:feature:settings         # 设置与备份
:benchmark                # Macrobenchmark（独立模块）
```

依赖方向：`feature:* → core:*`；`feature` 之间禁止互相依赖（跨特性跳转通过导航路由 + 共享 `core:model` 数据）；`:app` 负责组装。

> 若团队规模小，v1.0 可先采用单模块 `app/` + 清晰的包结构（`data/domain/ui/di`），但**必须预留上述模块边界**，避免后期拆分成本。数据层、媒体层建议从一开始就独立成模块。

### 4.3 分层与数据流

```
Compose UI (无状态)  ←  StateFlow<UiState>  ←  ViewModel  ←  UseCase  ←  Repository
      │                                                                  │
      └── 事件 (onAction) ──────────────────────────────────────────────┘
                                              ├── Room DAO（Flow 查询）
                                              ├── DataStore（Flow 偏好）
                                              ├── MediaStore 管理器（文件系统）
                                              └── Health Connect / 系统服务
```

- Repository 是唯一数据入口，UI 不直接访问 DAO
- 所有耗时操作通过 `UseCase` 编排，可独立测试
- **训练中的组记录采用"乐观更新 + 事务落库"**：UI 立即反映，DB 异步写入，失败回滚并提示

### 4.4 并发与一致性

- Room 使用单写者模式；批量写入（完成训练）使用 `@Transaction`
- 媒体文件操作用 `Mutex` 串行化，避免并发复制冲突
- 训练会话的"当前状态"（当前动作索引、当前组索引、休息计时）持久化到独立表 `WorkoutSessionState`，实现进程死亡恢复
- 所有 Flow 使用 `flowOn(ioDispatcher)`，`distinctUntilChanged()` 避免无效重组

### 4.5 媒体处理流水线

```
用户选择 → 元数据读取 → 校验(大小/格式/空间) → [可选]转码 → 流式复制到 filesDir/media
        → 生成缩略图(webp) → 事务写入 DB → 完成
异常路径：任一步失败 → 清理临时文件 → 回滚 DB → 提示用户
```

---

## 5. 数据模型（重写）

### 5.1 ER 概览

```
ExerciseDefinition ──1:N──> PlanExercise ──N:1──> TrainingPlan
        │                        │
        │                        └──1:N──> PlanSet
        │
        ├──1:N──> WorkoutExercise ──N:1──> WorkoutSession
        │              └──1:N──> WorkoutSet
        ├──1:N──> PersonalRecord
        └──1:N──> ExerciseMuscle (次要肌群)

PlanFolder ──1:N──> TrainingPlan
WorkoutSession ──1:N──> WorkoutExercise ──1:N──> WorkoutSet
BodyMeasurement（独立）
MediaAsset（媒体引用与 GC 追踪）
WorkoutSessionState（训练中断恢复，单行）
```

### 5.2 实体定义

```kotlin
// ---- 动作库 ----
@Entity(indices = [Index("name"), Index("isCustom"), Index("isArchived")])
data class ExerciseDefinition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                                  // 1-60
    val aliases: String? = null,                       // 逗号分隔，用于搜索
    val type: ExerciseType,                            // 决定记录表单
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val isUnilateral: Boolean = false,
    val defaultRestSeconds: Int = 90,
    val defaultSets: Int = 3,
    val defaultReps: String? = "10",
    val instructions: String? = null,
    val mediaType: MediaType = MediaType.NONE,
    val mediaPath: String? = null,                     // 本地文件名 或 URL
    val thumbnailPath: String? = null,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(primaryKeys = ["exerciseId", "muscle"], indices = [Index("exerciseId")])
data class ExerciseMuscle(val exerciseId: Long, val muscle: MuscleGroup, val isPrimary: Boolean)

// ---- 计划 ----
@Entity(indices = [Index("folderId"), Index("deletedAt"), Index("updatedAt")])
data class TrainingPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                                  // 1-60
    val description: String? = null,
    val folderId: Long? = null,
    val colorHex: String? = null,
    val defaultRestSeconds: Int? = null,               // 计划级默认
    val estimatedDurationSeconds: Int? = null,         // 缓存字段
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Double = 0.0,                       // 列表自定义排序（中值插入）
    val scheduledDays: String? = null,                 // "1,3,5" 表示周一三五
    val lastPerformedAt: Long? = null,
    val deletedAt: Long? = null,                       // 回收站
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(indices = [Index("planId"), Index(value = ["planId", "orderIndex"], unique = true)])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,                                  // FK → TrainingPlan, onDelete CASCADE
    val exerciseDefinitionId: Long?,                   // FK → ExerciseDefinition, onDelete RESTRICT
    val nameSnapshot: String,                          // 冗余，防改名影响历史
    val typeSnapshot: ExerciseType,
    val orderIndex: Double,
    val supersetTag: Int? = null,                      // 同 tag 为超级组
    val targetSets: Int = 3,
    val targetReps: String? = "10",                    // 默认值，用于展开 PlanSet
    val targetWeight: Double? = null,                  // 统一 kg
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetRpe: Double? = null,
    val restSecondsOverride: Int? = null,
    val tempo: String? = null,                         // "4-1-2-0"
    val notes: String? = null,
    val mediaType: MediaType? = null,                  // 覆盖动作库默认媒体
    val mediaPath: String? = null,
    val thumbnailPath: String? = null,
)

@Entity(indices = [Index("planExerciseId"), Index(value = ["planExerciseId", "setIndex"], unique = true)])
data class PlanSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planExerciseId: Long,                          // FK → PlanExercise, onDelete CASCADE
    val setIndex: Int,
    val setType: SetType = SetType.NORMAL,
    val targetReps: String? = null,
    val targetWeight: Double? = null,                  // 统一 kg
    val targetDurationSeconds: Int? = null,
    val targetRpe: Double? = null,
    val isUnilateralSplit: Boolean = false,
)

// ---- 训练会话 ----
@Entity(indices = [Index("planId"), Index("startedAt"), Index("status")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long?,                                 // null = 自由训练
    val planNameSnapshot: String,                      // 冗余
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val startedAt: Long,
    val completedAt: Long? = null,
    val pausedTotalSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val totalVolumeKg: Double = 0.0,                   // 缓存
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val bodyWeightKgSnapshot: Double? = null,          // 用于 REPS_ONLY 容量计算
    val perceivedEffort: Int? = null,                  // 1-10
    val mood: String? = null,
    val notes: String? = null,
    val isDeleted: Boolean = false,                    // 软删除
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(indices = [Index("sessionId"), Index(value = ["sessionId", "orderIndex"], unique = true)])
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,                               // FK → WorkoutSession, onDelete CASCADE
    val exerciseDefinitionId: Long?,                   // FK → ExerciseDefinition, onDelete SET_NULL
    val nameSnapshot: String,
    val typeSnapshot: ExerciseType,
    val orderIndex: Double,
    val supersetTag: Int? = null,
    val restSeconds: Int = 90,
    val notes: String? = null,
    val status: ExerciseStatus = ExerciseStatus.PENDING,
    val mediaType: MediaType = MediaType.NONE,
    val mediaPath: String? = null,
    val isAdhoc: Boolean = false,                      // 训练中临时添加
    val skippedAt: Long? = null,                       // 跳过是运行时事件（修正 v1.1 的 P0-7）
)

@Entity(indices = [Index("workoutExerciseId"), Index(value = ["workoutExerciseId", "setIndex"], unique = true)])
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,                       // FK → WorkoutExercise, onDelete CASCADE
    val setIndex: Int,
    val setType: SetType = SetType.NORMAL,
    val status: SetStatus = SetStatus.PENDING,
    val weightKg: Double? = null,                      // 内部统一 kg
    val displayWeight: Double? = null,                 // 用户输入原值
    val displayUnit: String? = null,                   // "kg" / "lb"
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val leftReps: Int? = null,                         // 单侧动作
    val rightReps: Int? = null,
    val rpe: Double? = null,
    val restTakenSeconds: Int? = null,
    val isPersonalRecord: Boolean = false,
    val prType: PrType? = null,
    val planSetId: Long? = null,                       // 关联的计划处方（用于目标对比）
    val targetWeightKg: Double? = null,
    val targetReps: String? = null,
    val completedAt: Long? = null,
    val notes: String? = null,
)

// ---- 中断恢复 ----
@Entity
data class WorkoutSessionState(
    @PrimaryKey val sessionId: Long,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val restTimerEndsAtEpochMs: Long? = null,
    val restTimerPlannedSeconds: Int? = null,
    val restTimerPausedRemainingMs: Long? = null,
    val isPaused: Boolean = false,
    val updatedAt: Long,
)

// ---- 成就 ----
@Entity(indices = [Index("exerciseDefinitionId"), Index(value = ["exerciseDefinitionId", "prType"], unique = true)])
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseDefinitionId: Long,
    val prType: PrType,
    val value: Double,
    val unit: String,                                  // "kg" / "reps" / "sec"
    val reps: Int? = null,                             // 达成时的次数（MAX_WEIGHT 场景）
    val weightKg: Double? = null,
    val achievedAt: Long,
    val sessionId: Long? = null,
    val setId: Long? = null,
)

// ---- 身体数据 ----
@Entity(indices = [Index("recordedAt"), Index("source")])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,                                // 日期语义
    val recordedAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val source: DataSource = DataSource.MANUAL,        // MANUAL / HEALTH_CONNECT
    val notes: String? = null,
)

// ---- 媒体 ----
@Entity(indices = [Index("fileName", unique = true)])
data class MediaAsset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,                              // filesDir/media 下文件名
    val thumbnailName: String?,
    val mediaType: MediaType,                          // GIF / VIDEO_LOCAL / ...
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long,
    val lastReferencedAt: Long? = null,
)

// ---- 计划分组 ----
@Entity
data class PlanFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null,
    val sortOrder: Double = 0.0,
)
```

### 5.3 枚举

```kotlin
enum class ExerciseType { WEIGHT_REPS, REPS_ONLY, WEIGHT_TIME, TIME, DISTANCE_TIME, WEIGHT_DISTANCE_TIME }
enum class MuscleGroup { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, TRAPS, LATS, FULL_BODY, CARDIO, OTHER }
enum class Equipment { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, KETTLEBELL, BAND, SMITH, EZ_BAR, OTHER }
enum class MediaType { NONE, GIF, IMAGE, VIDEO_LOCAL, VIDEO_LINK }
enum class SetType { NORMAL, WARMUP, DROP, FAILURE, AMRAP }
enum class SetStatus { PENDING, COMPLETED, FAILED, SKIPPED }
enum class ExerciseStatus { PENDING, IN_PROGRESS, COMPLETED, SKIPPED, REPLACED }
enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class PrType { MAX_WEIGHT, MAX_REPS, MAX_VOLUME_SET, MAX_E1RM }
enum class DataSource { MANUAL, HEALTH_CONNECT }
```

### 5.4 与 v1.1 模型的差异说明

| v1.1 | v2.0 | 原因 |
|------|------|------|
| `PlanExercise.sets/reps/restSeconds` 平铺 | `PlanExercise.targetSets` + `PlanSet` 组级明细 | 支持热身/递减/金字塔/超级组（P0-3） |
| `PlanExercise.isSkipped` | 移除，改到 `WorkoutExercise.skippedAt` | 跳过是运行时事件（P0-7） |
| `WorkoutRecord.actualSets: Int` | `WorkoutSession` → `WorkoutExercise` → `WorkoutSet` 三级 | 组级记录是核心价值（P0-1） |
| 动作名为字符串 | `ExerciseDefinition` 实体 + `nameSnapshot` | 支持检索/统计/历史稳定（P0-2） |
| 无 | `PersonalRecord` | PR 是核心正反馈（P1-3） |
| 无 | `BodyMeasurement` | 自重动作容量与趋势（P1-2） |
| 无 | `MediaAsset`、`WorkoutSessionState`、`PlanFolder` | GC、断点恢复、分组 |
| `sortOrder: Int` | `orderIndex: Double` | 避免重排全表重写（P2） |
| `reps: String` 仅计划侧 | 计划侧保留字符串区间，记录侧为 `Int` | 区间是目标，次数是事实 |

---

## 6. 关键状态机与时序

### 6.1 训练会话状态机

```
IDLE
 └─(开始训练)──> IN_PROGRESS ←──────────────┐
                    │  │                    │
      (暂停)────────┘  └──────(恢复)────────┘
                    │
                    ├─(结束且所有动作已处理)──> COMPLETED
                    └─(提前结束/放弃)────────> ABANDONED

组状态：PENDING → COMPLETED | FAILED | SKIPPED
动作状态：PENDING → IN_PROGRESS → COMPLETED | SKIPPED | REPLACED
```

### 6.2 完成一组时序

```
用户点击"完成本组"
  → 校验输入（必填字段、数值范围）
  → 乐观更新 UI（标记完成、刷新统计、显示对比箭头）
  → 异步：Room 事务写入 WorkoutSet + 更新 WorkoutExercise.status
  → PR 判定（仅正式组）：查询历史最大值 → 命中则写入 PersonalRecord + 播放动效
  → 启动休息计时（写 endsAtEpochMs + 发布倒计时通知 + 注册到点闹钟）
  → 自动跳转到下一组，焦点落在输入框
失败分支：
  → 写入失败：回滚 UI，Snackbar 提示并提供重试
  → PR 查询超时：跳过 PR 判定，标记为待复核（不阻塞用户）
```

### 6.3 休息计时器状态机

```
IDLE ──(完成一组)──> RUNNING ──(归零)──> FINISHED ──(通知/震动/音效)──> IDLE
                       │  ↑
                  (暂停)└──┘(恢复)
                       │
                       └──(跳过/结束训练)──> CANCELLED ──> IDLE

持久化：restTimerEndsAtEpochMs 写入 WorkoutSessionState
进程重启：若 endsAt > now → 续算；否则提示"休息已结束"
设备重启：BootReceiver 读取状态 → 同理处理
```

---

## 7. 测试策略

### 7.1 测试金字塔

| 层级 | 范围 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | UseCase、Repository、容量/PR/时长计算、单位换算、JSON 序列化、CSV 解析 | JUnit4 + MockK + Turbine | 核心逻辑 ≥ 80% |
| DAO 测试 | 每个 DAO 的 CRUD、聚合查询、FTS 搜索、级联删除 | Room `inMemoryDatabaseBuilder`（instrumented） | 100% DAO 方法 |
| **Migration 测试** | 每个 `MIGRATION_X_Y` | `MigrationTestHelper` + 历史 schema JSON | 100% 迁移路径 + 全链路链式迁移 |
| ViewModel 测试 | UiState 流转、错误处理、乐观更新与回滚 | Turbine + `StandardTestDispatcher` | 关键流程 |
| Compose UI 测试 | 建计划、加动作、记录一组、休息计时、删除与 Undo | `createComposeRule` + Semantics | 关键用户路径 |
| 截图测试 | 主要页面浅色/深色/大字体/不同屏宽 | Roborazzi / Paparazzi | 每个 feature 主要状态 |
| Macrobenchmark | 冷启动、列表滚动、训练页操作帧率 | Macrobenchmark + Baseline Profile | 每次 Release |
| 手动 QA | 见 7.3 设备矩阵 | — | 每个 Release |

### 7.2 必须覆盖的边界用例（测试清单）

- 单位换算：kg↔lb 往返不丢精度；切换单位后历史数据显示正确
- 容量计算：热身组排除、`REPS_ONLY` 含体重、单侧动作（左右次数和工作量）
- PR 判定：相同重量更多次数、轻重量高次数是否命中 MAX_E1RM
- 时长计算：超级组不重复计入休息；单组动作；时长类动作无次数
- 排序：连续拖拽 100 次后 orderIndex 精度不足触发 reindex
- 回收站：30 天边界清理；恢复后媒体文件仍可访问（未被 GC 误删）
- 媒体 GC：被回收站内的计划引用的媒体**不得**被清理
- 导入导出：zip 路径穿越防护、超大文件、损坏 zip、版本不兼容
- 进程死亡：训练中、编辑中、休息计时中三种场景恢复
- 时区：UTC+8 / UTC-5 / 夏令时切换时的"训练日期"归属
- 权限：拒绝、永久拒绝、Android 13/14/15 各版本路径
- 超大计划：100 个动作 / 500 组
- 空/极值输入：0 组、0 秒休息、60 字符名称、超长备注

### 7.3 设备与系统矩阵

| 维度 | 覆盖 |
|------|------|
| API | 26（最低）、29、31、33、34、35、36 |
| 厂商 ROM | Pixel（原生）、小米 HyperOS、华为 HarmonyOS（无 GMS）、OPPO/vivo、三星 OneUI |
| 屏幕 | 小屏 5.0"、主流 6.5"、折叠屏内屏、平板 |
| 形态 | 竖屏、横屏、分屏、画中画（视频） |
| 语言 | 中文、英文 |
| 环境 | 飞行模式、弱网、存储空间 <200MB、电池省电模式 |

---

## 8. 验收标准（可度量）

| # | 标准 | 度量方式 |
|---|------|---------|
| AC-1 | 用户从计划列表到创建含 3 个动作的计划并保存 ≤ 60 秒，且**无需滚动查找**（动作选择器可直接搜索） | 可用性测试 5 人，≥ 4 人达成 |
| AC-2 | 训练中完成 1 组记录的平均操作 ≤ 2 次点击（上一组数据自动带入） | UI 测试埋点统计 |
| AC-3 | 每个动作可关联 GIF/WebP/本地视频/外链；缩略图首帧显示 ≤ 300ms（缓存命中）/ ≤ 1s（首次） | Macrobenchmark + 人工抽样 |
| AC-4 | 休息计时器误差 ≤ 0.5s（10 分钟计时）；切后台、锁屏、杀进程后重进仍能正确继续或正确提示 | 自动化 + 手动 |
| AC-5 | 训练中途强杀进程，重进后恢复到最后完成的组，数据零丢失 | 手动 + 自动化（`am kill`） |
| AC-6 | 无网络环境下除"外链播放"外全部功能可用 | 飞行模式全流程走查 |
| AC-7 | Android 13 通知权限、Android 14 部分媒体访问、Android 15 edge-to-edge 均正常 | 真机回归 |
| AC-8 | 无 GMS 设备上媒体选择可用（SAF 降级） | 华为真机验证 |
| AC-9 | 导出 zip 在另一台设备导入后，媒体文件、计划结构、历史记录完整一致 | 跨设备验证 |
| AC-10 | 删除计划后媒体释放，回收站超期后自动清理；被回收站引用的媒体不被误删 | 数据库 + 文件系统断言 |
| AC-11 | 数据库从 v1 升级到当前版本数据零丢失（含 v1.1 扁平模型 → v2.0 三级模型的搬迁） | Migration 测试全部通过 |
| AC-12 | 打开任一动作详情，10 次历史重量趋势 1 屏可见 | 人工确认 |
| AC-13 | Crash-free sessions ≥ 99.5%（内测 2 周样本） | Play Console / Crashlytics |
| AC-14 | TalkBack 可完成"创建计划 → 开始训练 → 记录一组 → 结束训练"全流程 | 无障碍走查 |
| AC-15 | 系统字体 200% + 深色模式 + 折叠屏展开态下无布局截断 | 截图测试基线对比 |

---

## 9. 迭代路线（重排）

| 版本 | 范围 | 交付判据 |
|------|------|---------|
| **v0.1 骨架** | 工程搭建（多模块/CI/Version Catalog/静态检查）、设计系统、Room 基础、导航骨架 | 可编译、可运行、CI 绿 |
| **v0.5 数据内核** | 动作库（种子导入 + 检索）、计划 CRUD、组级处方、**组级记录模型与 DAO**、Migration 测试基线 | DAO/迁移测试全绿 |
| **v1.0 MVP（可发布）** | 训练执行全流程 + **休息计时** + 前次成绩回显 + PR + 历史记录 + 媒体演示 + 设置 + 完整备份导出/导入 | AC-1~AC-12 通过 |
| **v1.1 洞察与集成** | 统计仪表盘与图表、身体数据、Health Connect、通知与训练提醒、小组件/快捷方式、CSV 导入、深色模式与无障碍打磨 | AC-13~AC-15 通过 |
| **v1.2 扩展** | 计划分享生态、折叠屏/平板双栏优化、数据库加密开关、云同步（可选，需明确隐私方案）、Wear OS 评估 | — |

> **关于周期**：本版不标注周期。项目采用 AI 辅助开发，编码环节可显著加速；但**真机/ROM 验证、动作库种子数据采集、训练引擎时序 bug 排查、设备矩阵回归**这四类工作无法被 AI 加速，它们才是实际的关键路径。请以 DEV 文档 §17 的**任务清单与依赖关系**（而非人日）作为推进依据。
>
> 与 v1.1 PRD 的路线差异：把「训练历史统计」从 v1.1 提前（历史记录进 MVP，统计进 v1.1），把「媒体演示」放到 MVP 但**不作为首要交付**——先保证记录内核可用，再做演示体验。

---

## 10. 风险登记册

| # | 风险 | 概率 | 影响 | 应对 |
|---|------|:---:|:---:|------|
| R1 | 组级模型复杂度导致 MVP 延期 | 中 | 高 | 组级明细 UI 采用"默认展开区间 + 高级模式折叠"渐进式设计；数据模型先建全，UI 可分期 |
| R2 | 休息计时提示在国产 ROM 失效 | 中 | 低 | 倒计时通知与到点闹钟均由系统托管（不受应用进程清理影响）；正确性由 `endsAtEpochMs` 保证，提示丢失也不会导致计时错误。剩余风险仅为"少一次提示"，已降级 |
| R3 | 训练中途进程被杀丢数据 | 中 | 极高 | 每组即时落库 + WorkoutSessionState 持久化 + 恢复流程；专项测试覆盖 |
| R4 | 媒体文件撑爆备份配额 | 高 | 中 | `dataExtractionRules` 显式排除 media/thumbs；文档与代码双重校验 |
| R5 | 无 GMS 机型 Photo Picker 不可用 | 中 | 高 | SAF 降级路径从一开始就实现，不做事后补丁 |
| R6 | Room 迁移失败丢数据 | 低 | 极高 | 强制 Migration + 迁移测试 + 迁移前自动生成数据库快照到 `cacheDir`（保留 1 个版本） |
| R7 | 大视频（4K/200MB）导入 OOM | 中 | 高 | 流式复制、不进内存；超限时提供 720p 转码；导入在应用内执行并保持屏幕常亮、可取消 |
| R8 | 统计查询在大数据量下变慢 | 中 | 中 | 聚合下沉 SQL + 索引 + 缓存字段（totalVolume 等）+ 必要时预聚合表 |
| R9 | Compose 长列表性能不达标 | 中 | 中 | LazyColumn + 稳定 key + Deferred read + Macrobenchmark 门禁 |
| R10 | 外链视频平台政策变化（无内嵌权限） | 中 | 中 | 默认降级为浏览器打开；仅对允许内嵌的域名启用 WebView |
| R11 | 健康数据合规（Health Connect / 隐私政策） | 中 | 高 | 默认不采集；授权粒度最小；提供删除入口；上架前合规复核 |
| R12 | 范围膨胀（用户要求饮食/社交/AI） | 高 | 中 | 以 §1.2 Out of Scope 为准；新增需求走变更评审 |
| R13 | 动作库种子数据版权/质量 | 中 | 中 | 使用自建/授权文本描述，不使用受版权保护的图片视频；媒体由用户自行导入 |

---

## 11. 待确认问题（Open Questions）

以下问题会影响架构与工作量，**建议在开工前 3 天内确认**：

| # | 问题 | 备选方案 | 影响 |
|---|------|---------|------|
| Q1 | 目标市场？国内应用商店 / Google Play / 双线 | 双线 | 决定 GMS 依赖、Health Connect、支付与合规路径 |
| Q2 | 商业模式？完全免费 / 一次性买断 / 订阅高级功能 | 待定 | 影响是否需要 IAP、付费墙设计、是否需要账号 |
| Q3 | 是否需要云同步与账号？（v1.2 预留） | 暂不做，接口预留 | 影响数据层是否需要远端同步冲突解决（CRDT/时间戳）设计 |
| Q4 | 是否需要动作库种子数据的多语言（中英）？ | 是 | 影响动作库数据结构与翻译工作量 |
| Q5 | 是否需要"训练计划模板市场"或社区分享？ | 不做 | 范围显著不同 |
| Q6 | 是否接受"仅支持手机 + 折叠屏"，不做平板双栏？ | 折叠屏做，平板不做 | 影响布局工作量 |
| Q7 | 中文界面用词：哑铃/杠铃/组/次数 等术语是否需要术语表统一？ | 建议建立 | 影响文案一致性 |
| Q8 | 是否需要卡路里估算？ | 做粗估并明确标注"估算值" | 影响元数据与算法 |
| Q9 | 是否需要深色模式之外的配色主题（如品牌色）？ | 提供 1 套主题 + 强调色选择 | 影响设计系统 |
| Q10 | 崩溃报告与匿名统计是否可接受（opt-in）？ | 建议可接受 | 影响质量保障能力 |
| Q11 | v1 是否支持从 Strong/Hevy 导入历史数据？ | 建议 v1.1 支持 | 降低迁移门槛，显著提升留存 |
| Q12 | 是否已有目标用户可参与可用性测试？ | 建议 5-10 人 | 影响 AC-1 等验收方式 |

---

## 12. 附录：AI 辅助开发工作流（CodeBuddy）

> 说明：CodeBuddy 版本与能力以实际使用版本为准；以下为面向本项目的**可复用工作流**，替代 v1.1 第 5 章的能力罗列式写法。

### 12.1 项目上下文初始化

1. 在项目根目录建立 `AGENTS.md` / `.codebuddy/rules`，固化不可协商的约定：
   - 语言与风格：Kotlin、Compose、禁止 `fallbackToDestructiveMigration`
   - 架构边界：feature 不互相依赖；UI 不直接访问 DAO
   - 数据约定：重量统一存 kg；时间戳 UTC millis；日期用 epochDay
   - 资源约定：文案禁止硬编码，必须走 string resource
   - 测试要求：新增 UseCase/DAO 必须附测试
2. 执行 `/init` 建立代码库知识图谱，让后续对话具备跨文件上下文。

### 12.2 分阶段提示词模板

| 阶段 | 提示词要点（含约束条件） |
|------|------------------------|
| 数据层 | "创建 Room 实体 `ExerciseDefinition`/`PlanExercise`/`PlanSet`/`WorkoutSession`/`WorkoutExercise`/`WorkoutSet`/`PersonalRecord`，外键 `PlanExercise.planId` CASCADE、`WorkoutSet.workoutExerciseId` CASCADE；为所有外键列建索引；`exportSchema = true`；同时给出 `MIGRATION_1_2` 与 `MigrationTestHelper` 测试" |
| 动作库种子 | "编写 `ExerciseSeedImporter`，从 `assets/exercise_seed.json` 用 kotlinx.serialization 解析，在 `RoomDatabase.Callback.onCreate` 中批量插入，使用单个事务，≥300 条" |
| 训练执行 | "用 Compose 实现训练执行页：`LazyColumn` 展示动作，每项内嵌组记录行；`ExerciseType` 驱动动态表单；完成一组后乐观更新并异步落库；顶部显示已用时间、总容量、进度" |
| 休息计时 | "实现休息计时：普通通知使用 `setUsesChronometer(true)` + `setChronometerCountDown(true)` + `setWhen(endsAt)` 实现系统自绘倒计时（不用前台服务）；用 `AlarmManager.setAndAllowWhileIdle(endsAt)` 注册到点闹钟（不用需要权限的 `setExactAndAllowWhileIdle`）；`endsAtEpochMs` 持久化到 `WorkoutSessionState`；所有入口（跳过/延长/暂停/结束）幂等" |
| 容量与 PR | "实现 `VolumeCalculator` 与 `PersonalRecordDetector` 纯 Kotlin 类，覆盖 6 种 ExerciseType、含体重自重动作、排除热身组；为每个分支写单元测试" |
| 媒体导入 | "实现媒体导入流水线：Photo Picker 优先 + SAF 降级；流式复制到 `filesDir/media`；生成 512px webp 缩略图；全部在 WorkManager 中执行并上报进度；失败清理临时文件" |
| 备份恢复 | "实现完整备份 zip 导出/导入：`ZipOutputStream` 流式写入 plan/session JSON + media；导入校验 `schemaVersion`、防 Zip Slip、单事务写入；附单元测试" |
| 权限适配 | "适配 Android 13 通知、14 部分媒体访问（含 reselect）、15 edge-to-edge；无 GMS 环境降级；拒绝后提供解释弹窗与设置跳转，禁止死循环" |
| 迁移 | "为 v1.1 扁平 `WorkoutRecord` 迁移到 v2.0 三级模型编写 `MIGRATION_1_2`，把 `actualSets` 展开为 N 条 `WorkoutSet` 记录（重量/次数置空但保留汇总），并编写迁移测试验证行数与汇总一致" |

### 12.3 迭代与质量闭环

- 编译/运行错误：把完整堆栈（含 Gradle 输出）交给 AI 分析，要求给出**根因 + 最小改动**，而非重写文件
- 代码审查：要求 AI 以"审查者"角色输出问题清单（架构越界、测试缺失、并发问题、无障碍缺失），而不是直接改代码
- 测试生成：对 Repository/ViewModel/Calculator 生成测试，并**要求提供会失败的边界用例**（防止只生成 happy path）
- 性能：用 Macrobenchmark 输出对比，让 AI 定位重组热点与主线程阻塞
- 禁止事项：不允许 AI 引入 `fallbackToDestructiveMigration`、`GlobalScope`、`runBlocking`、硬编码字符串、无 `contentDescription` 的图标、无索引的外键

---

## 文档修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | — | 初始版本 |
| v1.1 | 2026-10-07 | 修正技术栈版本；补充 Android 14 权限适配；更新 CodeBuddy 能力描述；增加错误处理、数据库迁移、媒体清理策略；细化数据模型和验收标准 |
| **v2.0** | 2026-10-07 | **审阅重构**：①重建数据模型（动作库实体、组级处方 PlanSet、三级训练记录 Session/Exercise/Set、PR、身体数据、媒体资产、中断恢复状态）；②补齐训练执行核心（休息计时、前次成绩回显、渐进超负荷建议、组操作、训练总结）；③新增统计仪表盘、通知与提醒、Health Connect、设置页、Onboarding；④修正权限清单（补 `POST_NOTIFICATIONS` 等 8 项）；⑤修正计划时长公式与排序算法；⑥导出改为含媒体的完整 zip；⑦补充 Auto Backup 排除媒体、WorkManager 清理机制、国产 ROM 与无 GMS 降级；⑧补齐 Navigation/DataStore/WorkManager/kotlinx.serialization/Coil3 等技术栈与多模块结构；⑨新增无障碍、i18n、安全、可观测性、质量目标；⑩测试策略与验收标准全面可度量；⑪路线图重排（先内核后演示）；⑫新增风险登记册与 12 项待确认问题 |
| **v2.1** | 2026-10-07 | **决策收敛版**：①FR-12 休息计时改为「系统自绘倒计时通知 + 到点闹钟」，**不使用前台服务**，并移除 R2 中"国产 ROM 杀前台服务"的主要风险；②FR-30 相机路径定案为直接调系统相机，明确不做水印与自定义拍摄，补充 ROM 兼容处理；③§3.3 权限清单由 14 项收敛为 6 项（删除 `FOREGROUND_SERVICE*`、`WAKE_LOCK`、`CAMERA`、`READ_MEDIA_*`、`SCHEDULE_EXACT_ALARM`），并新增"已收敛权限及理由"表；④媒体导入不再使用前台服务，改为应用内进度 + 屏幕常亮 + 可取消；⑤§9 迭代路线取消周期标注，改为以任务清单与依赖关系推进（AI 辅助开发下，验证类工作才是关键路径） |
