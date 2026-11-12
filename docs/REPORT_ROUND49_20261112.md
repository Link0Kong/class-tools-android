# Round 49 报告 · v4.38.0（2026-11-12）

> 用户要求：尝试接入系统流体云/灵动岛接口。版本 **v4.38.0**（versionCode 54）。

## 1. 调研结论

| 结论 | 来源 |
|---|---|
| **ColorOS 16 流体云完整接入原生 Android 16 Live Updates API**：应用遵循 Google 实时活动规范即可直接适配流体云（OPPO 双兼容方案） | IT之家 2025-10-17（[1](https://www.ithome.com/0/890/345.htm)、[2](https://www.163.com/dy/article/KC3IMBH30511B8LM.html)） |
| Google 官方要求：样式 Standard/BigText/Progress/Call/Metric、声明 POST_PROMOTED_NOTIFICATIONS、setRequestPromotedOngoing(true)、ongoing、有 contentTitle、无 customContentView/群摘要/colorized、渠道非 MIN | Android Developers「创建实时更新通知」（[docs](https://developer.android.com/develop/ui/views/notifications/live-update?hl=zh-cn)） |
| OPPO 另有意图框架/服务卡片路径（需合作申报），适合系统级分发；独立小工具采用公开 API 更合适 | OPPO 开放平台（[docs](https://open.oppomobile.com/documentation/page/info?id=13258)） |

→ 采用公开 **Android 16 Live Updates**：同时覆盖 ColorOS 16 流体云与其它 OEM 灵动岛类能力，无需厂商白名单。

## 2. 实现

- `data/notify/LiveUpdateNotifier.kt`：渠道 `ct_live_updates`（IMPORTANCE_LOW、无声、无角标）；`ProgressStyle` 上传进度（`setStyledByProgress`、`setShortCriticalText("上传 N%")`）；限时投票 `BigTextStyle + chronometerCountDown` 原生倒计时；`cancel*()` 撤销；`canNotify()` 校验 POST_NOTIFICATIONS。
- `AndroidManifest.xml`：新增 `POST_PROMOTED_NOTIFICATIONS`（非运行时）。
- 接线：`AppContainer` PPT 仓库懒加载时订阅 `uploadProgress` 自动发布/更新/撤销；`VoteDetailScreen` 打开限时投票时发布倒计时，离开即撤销。
- Debug：「我的 → 关于 → 实时活动测试 / 关闭实时活动」用于机型验证（Release 不显示）。

## 3. 验证（模拟器 API 37）

| 项 | 结果 |
|---|---|
| dumpsys notification | `flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`、`android.requestPromotedOngoing=true`、`android.shortCriticalText=测试 60 秒` → **系统已提升为实时活动** |
| 通知栏截图 | 顶部实时活动卡片「实时活动测试 · 00:55」(`artifacts/v4380-live-notification-shade.png`) |
| 单测 | **1065/0** |
| 快速 E2E（手机） | **43 PASS / 0 WARN / 0 FAIL（975.2s）**；logcat 0 FATAL/ANR |
| Release | **3,026,717 B** / SHA-256 `FBA2907B0D5EDFD6992FC28C8CA6E68877BC37690B5BACB6DBD2E2ECA84EA27C`（vc54）；冷启动 **0.31s / 0.29s**；logcat 0 FATAL/ANR |

## 4. 差异与限制（如实）

- 是否上岛/进流体云由系统策略决定，用户可降级/关闭；ColorOS 16+ 才具备该 API 映射；ColorOS 15 及以下退化为普通常驻通知（不崩溃）。
- OPPO 意图框架旧接口需合作资质，本轮未接入（文档 `docs/LIVE_UPDATES_20261112.md` 已注明后续可立项）。
- 投票倒计时实时活动仅存在于打开详情期间；后台常驻需评估前台服务策略。
