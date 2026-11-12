# 系统实时活动接入（Live Updates / ColorOS 流体云）· 2026-11-12

> Android v4.38.0 起：班级工具站按 **Android 16 Live Updates** 规范发布「实时活动」通知，
> 在支持的系统上以状态栏胶囊 / 通知置顶卡片呈现；ColorOS 16 的流体云完整接入该原生 API，
> 因此同一实现可在 ColorOS 16 上进入流体云，其它 OEM 的「灵动岛」类能力只要遵循同一 API 也可兼容。

## 1. 调研结论（可核实）

| 结论 | 来源 |
|---|---|
| ColorOS 16 流体云**完整接入原生 Android 16 Live Updates API**：应用只要遵循 Google 实时活动规范即可直接打通适配流体云 | IT之家 2025-10-17（[1](https://www.ithome.com/0/890/345.htm)、[2](https://www.163.com/dy/article/KC3IMBH30511B8LM.html)）；OPPO 设计总监公开确认「双兼容方案」 |
| Google 官方实时活动要求：样式为标准/BigText/Call/Progress/Metric；声明 `POST_PROMOTED_NOTIFICATIONS`；`setRequestPromotedOngoing(true)`；必须 ongoing、有 contentTitle、无 customContentView/群摘要/colorized；渠道不得 IMPORTANCE_MIN | Android Developers「创建实时更新通知」（[docs](https://developer.android.com/develop/ui/views/notifications/live-update?hl=zh-cn)） |
| OPPO 另有「意图框架」接入流体云（意图共享/服务卡片），适合系统级分发合作场景，需意向申报与模板；对独立小工具过重 | OPPO 开放平台 流体云/意图框架文档（[docs](https://open.oppomobile.com/documentation/page/info?id=13258)） |

**结论**：走公开的 Android 16 Live Updates 是唯一既能覆盖 ColorOS 16 流体云、又无需厂商白名单/合作协议的路径；
ColorOS 16 以下或未支持该 API 的系统会退化为普通常驻通知（进度条/倒计时），不影响功能。

## 2. 实现（v4.38.0）

- `data/notify/LiveUpdateNotifier.kt`
  - 通知渠道 `ct_live_updates`（IMPORTANCE_LOW、无声、无振动、无角标；满足「不得 IMPORTANCE_MIN」）；
  - 统一 `canNotify()`：Android 13+ 检查 `POST_NOTIFICATIONS` 运行时权限 + 系统通知开关；
  - `postPptUpload()`：`NotificationCompat.ProgressStyle` + `setProgress` + `setStyledByProgress(true)`；
    `setOngoing(true)`、`setRequestPromotedOngoing(true)`、`setShortCriticalText("上传 N%")`、点击打开「演示」工具；
  - `postVoteDeadline()`：`BigTextStyle` + `setUsesChronometer(true)` + `setChronometerCountDown(true)`，
    系统原生倒计时（应用无需逐秒刷新）；点击打开「投票」；
  - `cancel*()`：上传结束/失败、离开投票详情时撤销。
- `AndroidManifest.xml`：声明非运行时权限 `android.permission.POST_PROMOTED_NOTIFICATIONS`。
- 接线：
  - `AppContainer` 的 PPT 仓库懒加载时订阅 `uploadProgress`，自动发布/更新/撤销上传实时活动；
  - `VoteDetailScreen` 打开限时投票时发布倒计时实时活动，离开即撤销（`DisposableEffect`）。
- Debug 构建「我的 → 关于 → 实时活动测试」：发送 60 秒常驻倒计时（含「关闭实时活动」），用于机型/系统验证。

## 3. 验证（模拟器 ct-api37 / API 37）

- `dumpsys notification --noredact` 记录：
  ```
  NotificationRecord(... id=7403 ... flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING ...)
  android.requestPromotedOngoing=Boolean (true)
  android.shortCriticalText=String (测试 60 秒)
  ```
  → 系统已把通知标记为 **PROMOTED_ONGOING**（实时活动），并进入通知抽屉顶部（默认展开、不可折叠）。
- 截图：`artifacts/v4380-live-notification-shade.png`（通知栏顶部「实时活动测试 · 00:55」倒计时卡片）。
- 单元测试 1065/0；快速 E2E 43/0。

## 4. 设备与系统差异（如实）

| 系统 | 表现 |
|---|---|
| Android 16+（AOSP/Pixel/ColorOS 16 等） | 系统可将符合规则的通知升级为实时活动：状态栏胶囊 + 通知置顶卡片；ColorOS 16 由其系统映射进流体云（以真机 ColorOS 16 表现最终为准） |
| ColorOS 13–15 等未接入该 API 的版本 | 退化为普通常驻通知（进度条 / 倒计时）；如后续需要流体云旧接口，需走 OPPO 意图框架/合作申请（本轮未接入） |
| Android <13 或未授予通知权限 | 不发布实时活动（`canNotify()` 返回 false），功能不受影响 |

## 5. 限制

- 实时活动是否「升岛/入云」最终由系统策略决定（用户可在通知设置中降级/关闭），应用只负责按规范发布。
- 本轮未接入 OPPO 意图框架（需合作资质与模板审核）；若后续班级工具站需要更深度的系统分发，可作为独立立项。
- 投票倒计时实时活动仅在「打开限时投票详情」期间存在；若希望后台常驻，需要配合前台服务/后台任务策略再评估。
