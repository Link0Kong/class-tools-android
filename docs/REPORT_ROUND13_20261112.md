# Round 13 报告 · v4.2.0（2026-11-12）

> v4 深度优化 P1 批次 B：光场/动效 + 工作台下拉刷新。版本 **v4.2.0**（versionCode 18）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 光场高光跟随触摸 | `CtFloatingDock` 增加触摸位置状态（Initial pass 只读不消费），`drawBehind` 绘制径向镜面高光，按下/滑动时高光随手指移动，松手淡出；切页/点击行为不变 |
| 工作台下拉刷新 | `HomeScreen` 用 `PullToRefreshBox` 包裹内容，下拉强制刷新课表/作业/通知/电费（`refreshToken` + marks refresh），1s 后收起指示 |
| 动效 token 审计 | 复核 17 处 `animate*AsState`：均在 `graphicsLayer`/`offset`/`drawBehind` 等绘制阶段读取，无组合阶段动画状态读取；CtMotion 与 M3 1.4.0 internal MotionScheme 关系说明保留（1.5 公开后可桥接） |
| 工程细节 | dock 增加 `setValue` import / `specularColor` 提前捕获避免在 draw 阶段调用 Composable |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（v4.2.0 Debug） | **42 PASS / 0 WARN / 0 FAIL（940.7s）**；工作台/底栏/翻页/名单三分类+点名/投票/电费/通知权限横幅/清缓存确认/深色组/导航 10/10/logcat 0 FATAL/ANR |
| Release v4.2.0 | **2,856,126 B** / SHA-256 `C43A921BF4061A598E640DE3ED9E685C5D1153133E1CEBA033370AC4F663947A`（vc18）；两次冷启动 0.49s / 0.35s；logcat 0 FATAL/ANR |
| 证据 | `artifacts/e2e/e2e_v420_fast.log`、`summary.json`、`v420-release-identity.png` |

## 3. 待办

- 批次 C：无障碍（TalkBack、触控 ≥48dp、大字体 1.3 全页）、平板/横屏复核。
- 批次 D：Baseline Profile、进一步动画 phase deferral（CtAnimatedCounter 文本计数可改 draw 阶段）、列表稳定性复核。
- 真实背景模糊：受 Compose/模拟器限制，维持毛玻璃；后续可评估 PixelCopy/合成层方案。
