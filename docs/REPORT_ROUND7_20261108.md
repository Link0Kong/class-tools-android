# Round 7 增量验收报告 · v2.1.1（2026-11-08）

> 接续 v2.1.0。本轮收敛小程序对齐剩余 P1：**通知提醒（ntremind）**、**数据与隐私页增强（同步范围清单 + 本机数据 8 计数）**，并修复小屏身份页首屏 P2。版本 **v2.1.1**（versionCode 5）。

## 1. 本轮交付

| 模块 | 内容 | 来源 |
|---|---|---|
| 通知提醒 | `ReminderSettings.noticeEnabled` + `ReminderPlanner.planNotices`（每日汇总明天/后天截止或活动的未处理通知、24h 临近提醒、长期安排/已处理/已过期/人工通知排除、去重、安静时段）；`ReminderKind.NOTICE_DIGEST/NOTICE_DUE`（toolId=nt，渠道 notice_due）；DataProvider 读 notices 本机缓存 + marks ntdone；设置页新增「通知提醒」开关 | contract + Lead |
| 数据与隐私 | `LocalDataSnapshot`/`LocalDataStats`（8 计数与 mp pages/data 同口径 + 3 汇总）、`SyncScopeCatalog`（userdata v6 11 section + marks + 本机/设备偏好）；`SyncScopeSectionCard` + `LocalDataStatsCard` 已接入数据与隐私页；Lead 从本机缓存聚合（不联网） | contract + Lead |
| 小屏身份页 | 360×640dp 压缩顶部品牌区/间距、隐藏非必要 pill、卡片密度下调；**实测首屏直接可见「进入工具站」**（截图 `artifacts/fixed-small-identity.png`） | Lead |
| 版本 | versionName 2.1.1 / versionCode 5 | Lead |

## 2. 验证证据

| 项 | 结果 |
|---|---|
| 全量单测 | **639 tests / 0 failures**（51 类；新增 36：NoticeReminderPlanner 20 / LocalDataSnapshot 9 / SyncScopeCatalog 7） |
| 小屏实测 | ct-a37-small（720×1280@320 = 360×640dp）：身份页首屏「进入工具站」bounds 可见（无需滚动）、协议两行不竖排 |
| Release 自检 | APK **2,495,213 B**，SHA-256 `62E5C4C9C9E374B9CDD0BF357335D87F66792A85F0B1FC37A4152E3A206ECF3F`；apksigner V2；安装成功、冷启动 `Status: ok`（749ms）、logcat 0 FATAL/0 NoSuchMethodException |
| 待补 | toolchain 的 v2.1.1 E2E（含反馈/使用说明页）、矩阵（小屏首屏/横屏/折叠）、Release/文档终验 |

## 3. 与小程序差异（已文档化）

- ntremind：mp 为微信订阅消息（用户自选时间、云端发送）；Android 为本机 WorkManager（不联网、不上传），固定「每日 20:00 汇总 + 24h 临近」；点击通知直达通知页。
- 数据与隐私 8 计数标签/顺序与 mp 一致；额外 3 汇总（同步配置/常用工具/名单缓存人数）；抽签/分组 section 缺失显示「—」。
- 通知提前量暂固定 24h（后续可配置）；FitRecs 无 cachedCount 访问器（用已加载列表大小）；pending 计数默认 0。

## 4. 已知问题 / 下一轮

| # | 项 | 说明 |
|---|---|---|
| 1 | v2.1.1 E2E/矩阵 | toolchain 进行中（预计完整 36 项 + 反馈/使用说明；小屏首屏断言、横屏、折叠展开） |
| 2 | 通知提前量可配置 | 目前固定 24h，可在提醒设置页加档位 |
| 3 | 反馈页下拉刷新/离线历史 | contract 遗留（不影响验收） |
| 4 | 启动图标真机复核 | 模拟器 launcher 有浅色外环（三圆点本体正确），待真机确认 |
| 5 | 真实账号链路 | 仍需旅行者本人/测试身份实测 |

## 5. 文档

- `docs/api/NOTICE_REMINDER_DATA_PRIVACY.md`
- `docs/CHANGELOG.md`（v2.1.1）
- `docs/E2E_SMOKE.md §10`、`docs/ADAPTIVE_TEST_MATRIX.md §9`、`docs/RELEASE_BUILD.md §9`（toolchain 追加）
