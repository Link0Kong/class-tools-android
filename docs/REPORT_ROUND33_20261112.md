# Round 33 报告 · v4.22.0（2026-11-12）

> v4 深度优化 性能：**应用级 Baseline Profile** 接入。版本 **v4.22.0**（versionCode 38）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| Baseline Profile 工程 | 新增 `:baseline-profile`（com.android.test）模块 + `androidx.baselineprofile` 1.5.0 插件；app 侧 `baselineProfile { saveInSrc = true; automaticGenerationDuringBuild = false }` + `baselineProfile(project(":baseline-profile"))` |
| 采集规则 | `BaselineProfileRule.collect(packageName="com.furina.classtools", includeInStartupProfile=true)`：pressHome → startActivityAndWait → 首屏等待 1s |
| 生成产物 | `app/src/release/generated/baselineProfiles/{baseline-prof,startup-prof}.txt`：**18,166 条规则（908 条 app 自身）**，各 ~1.86MB 文本，签入源码树；APK 内 `assets/dexopt/baseline.prof` + ProfileInstaller 均存在 |
| 体积/构建 | Release APK 2,987,256 B（+131KB）；单测 1065/0；`docs/BASELINE_PROFILE.md` 记录生成/验证/回退 |

## 2. 冷启动测量（模拟器 ct-api37，force-stop + `am start -W` 各 5 次）

| 场景 | 各次 (ms) | 中位数 |
|---|---|---|
| v4.21（无 app profile，默认 verify） | 716, 780, 800, 768, 784 | 780 |
| v4.22（app profile，安装后默认 verify） | 914, 854, 784, 743, 766 | 784 |
| v4.22 + 强制 `cmd package compile -m speed-profile -f` | 795, 709, 766, 750, 707 | **750** |
| v4.21 + 强制 `cmd package compile -m verify -f`（对照） | 843, 801, 755, 860, 879 | 843 |

结论（如实）：安装后系统后台 dexopt 仍为 verify，故「刚装完」差异不明显；按 profile 完成 speed-profile 编译后中位数 **750ms vs 843ms（-11%）**，profile 有效但依赖真机后台编译时机。

## 3. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机） | **43 PASS / 0 WARN / 0 FAIL（959.2s）**；logcat 0 FATAL/ANR；导航 10/10；深色组+浅色恢复 |
| ProfileInstaller | logcat 确认 `Installing profile for com.furina.classtools` |
| Release v4.22.0 | **2,987,256 B** / SHA-256 `BCEF243117CC1804F0B4304C78C8FB65E394FB63431023DF788A1567CB2407A1`（vc38）；两次冷启动 0.92s/0.69s（模拟器负载高）；logcat 0 FATAL/ANR |

## 4. 待办

- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- TalkBack 真机全量走查
- 可按需扩展 profile 采集路径（列表/投票/PPT），并评估真机首日后台编译后的实际收益
