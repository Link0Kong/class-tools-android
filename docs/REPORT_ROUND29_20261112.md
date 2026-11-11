# Round 29 报告 · v4.18.0（2026-11-12）

> v4 深度优化 P2：课表周末隐藏提示 + Baseline Profile 现状审计。版本 **v4.18.0**（versionCode 34）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| T-06 | 周视图在周末两天都无课、周末列隐藏时，课表卡片顶部显示「周末无课，已隐藏周末列」，避免用户误以为缺列；预览渲染核验 |
| 性能资料审计 | 检查 Release APK：已含 `assets/dexopt/baseline.prof`（8.3KB）+ `META-INF/androidx.profileinstaller_profileinstaller.version` → 库级 baseline profile 已合并并由 ProfileInstaller 安装；**应用级 profile（macrobenchmark 生成）尚未接入**，如实记录为待办 |
| E2E 稳定性 | 首次运行在「诊断」步骤出现一次导航抖动（back 落在工作台导致后续查找失败），重跑 43/0 通过；属测试脚本导航时序问题，非应用缺陷 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 预览核验 | `TimetableWeekLightPreview` 顶部显示「周末无课，已隐藏周末列」（`artifacts/previews_t06/`） |
| 快速 E2E（手机 ct-api37，重跑） | **43 PASS / 0 WARN / 0 FAIL（978.8s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.18.0 | **2,856,126 B** / SHA-256 `C7D5A9094CCD01CB9F956134CA1F06ECDBBEE35F1DC359CACFAD365C8F213AD0`（vc34）；两次冷启动 OK（末次模拟器负载高：1.11s/0.77s）；logcat 0 FATAL/ANR |

## 3. 待办

- 应用级 Baseline Profile：接入 macrobenchmark 模块或手工 profile 生成流程（需评估 AGP 9.5 兼容性）
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- TalkBack 真机全量人工走查；P2 剩余细节
