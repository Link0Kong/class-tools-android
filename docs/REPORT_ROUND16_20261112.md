# Round 16 报告 · v4.5.0（2026-11-12）

> v4 深度优化 P1 批次 C/D 收尾：读屏语义 + 大字投票页实测 + 性能审计。版本 **v4.5.0**（versionCode 21）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 电费同期徽标读屏 | `PowerScreen` 徽标加 `semantics`：内容描述「较昨日同期 上升/下降 N%，点击查看说明」，不再只靠颜色区分增减；点击说明保留 |
| 大字投票页实测 | 系统字体 1.30 下投票页（身份失效四态：标题/提示/重试）布局正常，截图 `artifacts/largefont-vote-1.3.png` |
| 大字体预览声明 | 新增 `157 投票详情 · 大字体 1.3`、`158 发起投票 · 大字体 1.3` Compose 预览（`fontScale = 1.3f`），供后续渲染复查 |
| 性能审计 | 复核 17 处 `animate*AsState` 均在 `graphicsLayer`/`offset`/`drawBehind` 读取；启动路径无新增阻塞初始化 |
| 预览渲染工具问题 | `android preview render` 卡在 `preview list` 阶段 12 分钟无输出（android-cli 空闲），已终止并记录为工具链问题，下轮排查（可能 Gradle 锁/CLI 依赖下载） |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（929.3s）**；名单三分类= True、投票/电费/通知横幅/清缓存/深色组/导航 10/10、logcat 0 FATAL/ANR |
| 大字投票页 | 截图核验通过（页面骨架/四态文案不截断） |
| Release v4.5.0 | **2,856,130 B** / SHA-256 `8E6D5074F2B0BC3F12B7935AD3C78089CDE9F96021627CA22413ACF3B38907DC`（vc21）；两次冷启动 0.42s / 0.32s；logcat 0 FATAL/ANR |

## 3. 待办

- 预览渲染工具排查（android-cli preview list 卡住）→ 恢复可渲染后复查 157/158 大字投票详情/发起。
- TalkBack 全量语义走查剩余页面；触控 ≥48dp 逐项核对。
- 批次 D：Baseline Profile 生成评估、CtAnimatedCounter 绘制阶段化。
- 真实背景模糊：维持毛玻璃（Haze/GraphicsLayer+RenderEffect 在模拟器不可见），评估 PixelCopy 合成方案。
