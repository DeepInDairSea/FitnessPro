# FitnessPro 健身记录

一款 **纯离线** 的 Android 力量训练记录应用。训练计划、动作库、训练日志、身体数据全部保存在本机，不上传、不联网同步，数据完全由你掌控。

<p align="center">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-green">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-blue">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-orange">
</p>

## 简介

FitnessPro 面向力量训练爱好者，从"练什么、怎么练、练了多少、练得如何"四个环节提供完整闭环：

- **计划**：内置与自定义训练计划，进训练房照着练即可；
- **动作库**：内置 500+ 动作（含演示媒体），支持自建动作；
- **训练**：训练中逐组记录重量/次数/RPE，自动组间休息倒计时、PR 破纪录庆祝、未完成会话可恢复续练；
- **统计**：容量、次数、部位分布、个人纪录与历史趋势；
- **身体**：体重、体成分等长期记录，并反哺卡路里估算精度。

## 主要特性

- **多因素卡路里估算引擎（L1–L4 精度分级）**
  - 基于动作 MET 标定值 + 训练容量（容量主导，弱化时间敏感度，快慢节奏结果一致）；
  - 逐组累积：每组完成即结算该组消耗，实时界面 = 已完成组消耗 + 当前组 MET 估算；
  - 自重动作自动折算有效负荷：`体重 × 自重系数 + 附加负重`（引体向上、双杠臂屈伸等）；
  - 训练结束追加 EPOC（运动后过量氧耗）估算，总结页展示精度星级与提升建议；
  - 身体数据（身高/年龄/体成分）越完善，估算精度星级越高。
- **纯离线**：无账号、无云同步，不申请网络权限上传任何数据；分享卡片也只存本机相册。
- **贴心细节**：训练屏幕常亮、久未训练/体重记录/存储清理提醒、回收站找回误删数据、崩溃报告可选且匿名。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose（BOM 2025.06）、Material 3 |
| 架构 | 多模块 + MVVM，Hilt 依赖注入 |
| 数据 | Room 2.7、DataStore、kotlinx.serialization |
| 异步 | Kotlin Coroutines / Flow |
| 媒体 | Media3（动作演示视频）、Coil 3（GIF/WebP/视频封面） |
| 性能 | Baseline Profile（冷启动优化） |
| 构建 | AGP 8.13、Version Catalog、Convention Plugins、Detekt |

## 工程结构

```
app/                 应用壳：导航、入口
build-logic/         Gradle convention 插件（统一 SDK、编译配置）
core/
  common/            通用工具
  model/             纯领域模型
  domain/            用例与仓库接口
  data/              仓库实现
  database/          Room 实体/DAO/迁移/动作库种子数据
  datastore/         设置持久化
  designsystem/      主题与公共组件
  media/ mediaui/    演示媒体管理与播放
feature/
  home/ plans/ exercises/ workout/
  history/ stats/ body/ settings/
baselineprofile/     启动性能基准采集模块
```

依赖规则（CI 强制）：`core:*` 不依赖 `feature:*`，`feature:*` 之间不互相依赖，可用 `./gradlew moduleDependencyCheck` 本地校验。

## 构建与运行

要求：JDK 17、Android Studio（AGP 8.13+）。

```bash
# Debug 构建
./gradlew :app:assembleDebug

# 安装到设备并做 AOT 编译加速冷启动
./gradlew :app:installFastDebug

# Release 签名构建（需先配置 keystore.properties）
./gradlew :app:assembleRelease

# 模块依赖规则校验
./gradlew moduleDependencyCheck
```

### Release 签名（可选）

签名信息外置于根目录 `keystore.properties`（不进版本库）：

```properties
storeFile=your-keystore.jks
storePassword=xxx
keyAlias=xxx
keyPassword=xxx
```

文件存在时 release 自动挂签名产出可直接安装的包；缺失时降级为未签名产物，保证 clone 后开箱可构建。

## 文档

- [PRD 产品文档](docs/PRD_v2.0_健身记录App.md)
- [开发技术文档](docs/DEV_开发技术文档_v1.0.md)
- [发布检查清单](docs/RELEASE_CHECKLIST.md)
- [商店上架文案](docs/STORE_LISTING.md)
- [免责声明](DISCLAIMER.md)

## 免责声明

本应用为纯离线软件，不提供医疗建议，不代替专业教练或医生指导；训练内容与强度完全由用户自行决定，请在充分了解自身健康状况的前提下量力而行。详见 [DISCLAIMER.md](DISCLAIMER.md)。

## 许可

[MIT](LICENSE) © 2026 KerryZeng
