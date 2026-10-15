# 发布核查清单（RELEASE CHECKLIST）

> 每次 release 构建前逐项打勾。M6 的"设备矩阵回归"执行本清单第 2 节。

## 1. 构建

- [ ] `gradlew assembleRelease testDebugUnitTest` 全绿（单测含数据层 Robolectric 与设计系统截图基线）
- [ ] `gradlew :app:generateBaselineProfile` 在本轮 UI 变更后重新执行过（产物随代码提交）
- [ ] versionCode / versionName 已递增
- [ ] 备份格式版本（BackupFormat.CURRENT_FORMAT_VERSION）未意外变更；若变更，旧版本导入兼容性已验证

## 2. 设备矩阵回归（最小集合）

| # | 设备 | OS | 必测项 |
|---|---|---|---|
| 1 | 纯 AOSP（Pixel 模拟器可代） | 最新 Android | 全功能冒烟：引导 → 计划 → 训练（记录/休息/破纪录）→ 总结 → 历史/统计/身体 → 备份导出导入 |
| 2 | 小米/Redmi（MIUI/澎湃） | 最新 | **进程死亡恢复**（HOME → 设置里强制停止 → 重开，游标/成绩/倒计时恢复）；通知到达；后台不被杀时休息到点提醒 |
| 3 | 华为/荣耀（无 GMS） | 最新 | Health Connect 入口隐藏（availability=Unsupported 不显示）；其余同 #1 |
| 4 | 三星 | One UI | Doze 穿透：拔电 → force-idle → 休息到点仍提醒 |
| 5 | 低端机（≤4GB RAM，Android 10-12 级别） | — | 媒体导入（大 GIF/视频）不 OOM；备份导出几十 MB 级 zip |

说明：
- #2/#3/#4 的"后台存活"结论**不能**来自模拟器（模拟器没有内存压力与厂商省电策略）
- 每项结论配截图/录屏（adb shell screencap / screenrecord，注意 PowerShell 二进制重定向坑）

## 3. 关键不变量（任何设备上破坏即阻塞发布）

1. 进程死亡后：动作/组游标、已记录成绩、休息倒计时（按 endsAtEpochMs 续算）全部恢复
2. 已记录的成绩不因任何操作（换动作/跳过/删除计划）被静默删除
3. 备份导出：目标位置要么是完整 zip、要么没有文件；导入失败不留半套数据
4. 休息计时正确性不依赖任何后台组件：剩余时间始终由 endsAtEpochMs 推算
5. 备份导入在媒体部分缺失时必须如实告知（"N 个媒体文件恢复失败"）

## 4. 发布开关检查

- [ ] 崩溃报告开关（设置 → 隐私）在发布包可用：开启后触发崩溃，files/crash/last_crash.txt 生成
- [ ] Onboarding 仅在首次启动出现；完成后清除应用数据 → 重装不重复（onboarding_completed 标记随备份走）
- [ ] 训练提醒：设置的星期/时刻改动后立即生效（无需重启应用）
- [ ] 分享功能已移除：总结页无分享按钮，file_paths.xml 仅暴露 capture/

## 5. 法务与商店

- [ ] Data Safety 表单 = docs/STORE_LISTING.md §4
- [ ] Health Connect 审核材料（READ_WEIGHT 用途声明）
- [ ] 隐私政策 URL 可访问，内容与"本地优先"承诺一致