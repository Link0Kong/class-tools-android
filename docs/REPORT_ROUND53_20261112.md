# Round 53 报告 · v4.42.0（2026-11-12）

> 用户需求：重新设计 UI（以微信小程序 UI 为基准）；优化通知界面；修复同一通知重复多条；删除测试通知；减少重复功能（工具中的设置直接进入「我的」）；重做「我的」界面；保持风格一致简洁；对接安卓流体云/灵动岛。
> 版本 **v4.42.0**（versionCode 58）。本轮聚焦：我的页/通知页/设置整合/使用说明卡片化 + 通知数据层去重 + 测试通知移除 + 实时活动复验。

## 1. 本轮改动

### 1.1 「我的」页重做（以小程序 me 页为基准）
- 新信息架构：账号与身份（姓名 + 身份已验证/职务/徽章/管理员胶囊，**移除头像与学号**）→ 数据与同步（我的数据 2×2 + 云同步 + 数据与隐私 + 清除本机缓存二次确认）→ 通知与权限（权限管理/提醒设置）→ 外观（壁纸取色/深浅色）→ 关于与帮助（反馈/使用说明/协议/隐私/版本）→ 退出登录。
- 去冗余：旧 7 张独立卡 + 6 条说明性冗余行收缩为 5 组；平板 55/45 双栏；行高 ≥56dp 整行可点；底部 120dp 避让浮岛。

### 1.2 通知界面重做（以小程序 nt 页为基准）
- 标题区（未处理/共 N、已隐藏）+ 待处理简报（待处理/24h 截止/7 天内/已过期）+ 筛选 chips（全部/未处理/置顶，带计数）+ 标题/摘要搜索。
- 通知卡片：分类色块、状态色点、标题、时间、摘要 2 行、来源/发送者/附件数/多源/更新/隐藏标记、≥48dp 已处理/置顶胶囊；「已过期」折叠段；骨架/空/错误/离线缓存态；深色/大字体/平板两列限宽。
- 详情改为底部弹层（`NoticeDetailSheet`）：AI 摘要、字段分段、图片网格+全屏、文件/链接/合并消息、人工正文附件、加入日程/提醒我/标记已处理/置顶。

### 1.3 同一通知重复多条 — 数据层修复
- 根因：`NoticesRepository.buildData()` 把每条非 archived 行 1:1 映射为卡片（只有行 id、无语义身份）；云端保留同一逻辑通知多行（历史未归档旧 gid 行、重新采集 gid 变化、补充未命中 target、人工+自动形态不同）→ 重复。
- 修复：新增 `data/notices/NoticeDedupe.kt` 保守去重——同 group id 直接合并；跨 id 需「规范化标题+分类一致+时间差 ≤2h+强证据（同来源/条目指纹重叠/mergedSources 交集/同发送人）」；items 指纹并集、mergedSources 并集、hidden 过滤先于合并、override 先应用再匹配；人工通知同标题+正文 10min 去重；auto/manual 不互合。UI 无本地去重逻辑，仅消费结果。
- 测试：`NoticeDedupeTest` 18 + `NoticesDedupeIntegrationTest` 5 = 23 个用例。

### 1.4 删除测试通知
- 删除 `ReminderTestNotificationResult`、`ReminderEngine.testNotification()`、`ReminderActions.testNotification()`；ReminderSettings 区块改名「检查与诊断」，仅保留「立即检查 + 诊断」；主源码 `testNotification` 引用 = 0；相关 E2E 断言删除。

### 1.5 减少重复功能
- 全部工具中的「设置」卡片保持跳转「我的」Tab（`MainNavHost` `"settings" -> selectTab(2)`，无独立设置占位页）；「我的」页内部合并重复设置卡与说明行。

### 1.6 使用说明卡片化（同批工作树一并纳入）
- 使用说明页重写为卡片轮播（20 卡 / 5 分类：入门·学习·生活·协作·更多，左右滑动 + 总览弹层 + 分类 chips），`GuideContent.kt`/`GuideScenes.kt` + `UserGuideContentTest` 13 用例（与网页「使用说明卡片化」契约一致，并行会话产出，随本版一并构建验证）。

### 1.7 安卓流体云/灵动岛（实时活动）复验
- v4.38 起已接入 Android 16 Live Updates：PPT 上传进度、限时投票倒计时；本轮回归验证 Debug「实时活动测试」→ `dumpsys notification`：`flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`，channel `ct_live_updates`（实时活动），标题/倒计时正确。ColorOS 16 完整兼容该 API，可直接映射流体云。

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1098 / 0 / 0 / 0**（88 suites；基线 1065 + 去重 23 + 使用说明 13 - 删测试通知 3） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1075.3s / 17.9min）**；导航 10/10；logcat 0 FATAL/ANR；含新 UI 全部步骤（我的/通知 chips/设置跳转/使用说明轮播/深色组） |
| 实时活动 | `flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`（dumpsys 证据 130KB） |
| P0 构建缓存问题 | Gradle build cache 生成的 debug dex 缺 `GuideScenesKt` → 使用说明崩溃；`--no-build-cache` 重建后 0 FATAL；**Release 采用 clean + no-build-cache** 构建 |
| Release v4.42.0 | **3,075,869 B** / SHA-256 `541A0CAF5E669B8795605DD129CFB17E8FE18A358A0CC698546010DB48360761`（vc58）；pm clear 后首两次 1.81s/1.06s，随后稳定 **0.88/0.78/0.79s**；logcat 0 FATAL/ANR |
| 签名 | apksigner V2 通过（同历史证书 `974854df…4a1a`） |

## 3. 已知边界 / 下一步

- 本轮 UI 重做覆盖「我的/通知/设置整合/使用说明」；工作台/全部工具/工具页仅复用既有风格，未整体重排（可下轮继续以小程序为基准统一）。
- 通知详情弹层在测试身份下无通知数据，设备未实测展开；由 15 张预览 + 单测覆盖。
- 使用说明页由并行工作树产出，已随本版构建并 E2E 验证；如需独立验收可单独补。
- 实时活动最终入流体云观感需 ColorOS 16 真机确认；旧系统退化为普通常驻通知。
- **构建 SOP 更新**：发布构建必须 `--no-build-cache`（或清理 Gradle build cache），否则可能复用坏 dex。
