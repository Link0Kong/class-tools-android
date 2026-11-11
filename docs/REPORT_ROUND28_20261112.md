# Round 28 报告 · v4.17.0（2026-11-12）

> v4 深度优化 性能：启动懒加载 + 提醒调度异步化。版本 **v4.17.0**（versionCode 33）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| AppContainer 懒加载 | 投票/团员名单/PPT（仓库、图片加载器、下载器、本地偏好、网络质量）/体测/反馈/隐私/日历写入等 13 个字段改为 `by lazy`，首帧不再创建这些对象（尤其 `OkHttpPptImageLoader` 的 OkHttp 客户端） |
| 提醒调度异步化 | `WorkManagerReminderScheduler` 改懒加载；`ensureScheduled()` 从 `AppContainer.init` 主线程移到 `appScope.launch` 后台执行，避免 WorkManager 初始化阻塞首帧 |

## 2. 启动测量（同一模拟器 ct-api37，force-stop 后 `am start -W` 各 5 次）

| 版本 | 各次 TotalTime (ms) | 中位数 |
|---|---|---|
| v4.16.0（改动前） | 700, 625, 416, 357, 370 | **416ms** |
| v4.17.0（改动后） | 357, 341, 355, 357（另 1 次采集异常记 0，已剔除） | **≈356ms（-15%）** |

- 后续标准 Release 复验：两次冷启动 0.53s / 0.39s；logcat 0 FATAL/ANR。
- 说明：模拟器受系统缓存/负载影响波动较大，数据仅作同环境相对比较，不代表真机绝对值。

## 3. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（899.8s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.17.0 | **2,856,126 B** / SHA-256 `3A8C092DCA142F4E3D571191DB3E8EF22C0E15DC7A7921A20A2DED68CB8EBEAD`（vc33）；两次冷启动 0.53s/0.39s |

## 4. 待办

- Baseline Profile 生成评估（macrobenchmark 模块）
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- TalkBack 真机全量人工走查；P2 剩余文案/细节
