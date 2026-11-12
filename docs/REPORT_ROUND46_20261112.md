# Round 46 报告 · v4.35.0（2026-11-12）

> v4 深度优化 无障碍/动效：系统「移除动画」适配（G22）。版本 **v4.35.0**（versionCode 51）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| G22 | 新增 `rememberReduceMotion()`（读 `Settings.Global.ANIMATOR_DURATION_SCALE` 与 `ValueAnimator.areAnimatorsEnabled()`）；命中时：① 工作台轮换卡停止自动轮换；② `CtAnimatedCounter`/`CtAnimatedDecimalCounter` 用 `snap()` 直接显示目标值；③ PPT 播放器热点脉冲固定为 1f 不再无限动画 |
| 恢复 | 系统动画恢复后重新进入页面即恢复动效；加载/进度指示器保留（reduced-motion 通常允许进度指示） |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 设备冒烟（reduced-motion） | 三个 animation scale 全 0 时冷启动/渲染正常、无崩溃（`artifacts/v4350-reduced-motion.png`） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1130.7s）**；导航 10/10；深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.35.0 | **3,026,681 B** / SHA-256 `8D15454DB0341CBDD4A160C9EBD3FFB658BA8CADEA8C0E43D80C33417DEC2361`（vc51）；冷启动 0.77s / 0.59s；logcat 0 FATAL/ANR |

## 3. 待办

- TalkBack 真机逐项走查（静态审计已 0 未标注，但播报顺序/语义需人工确认）
- 页面级玻璃化（性能取舍）
- 系统动画设置在运行中变化需重进页面才生效（可后续用 ContentObserver 实时监听）
