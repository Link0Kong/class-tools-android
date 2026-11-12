# `MP_UI_ALIGN.md` — 小程序「我的」→ Android「我的」界面对齐规范（v5）

> 任务：task-39（Android「我的」重做 + 设置整合 + 风格规范，以微信小程序 me 页为基准）。
> 基线：`android-class-tools` HEAD `6268510`（v4.41.0），单测 1065/0。
> 参考：`mp-class-tools/pages/me/me.wxml|me.wxss|me.js`、`ui/screens/profile/ProfileScreen.kt`（旧 545 行/7 卡片）、`ui/screens/settings/ReminderSettingsScreen.kt`、`ui/screens/data/DataPrivacyScreen.kt`、`ui/theme/**`。
> 产物：`ProfileScreen.kt` 重写、`ProfileRedesignPreviews.kt`（171–179）、本文件；`ReminderSettingsScreen` 删除「发送测试通知」。

---

## 1. 目标与原则

- **对齐小程序 me 页的信息架构**：身份 → 我的数据 → 云同步/数据与隐私 → 帮助 → 退出；页面简洁、行式列表、次要文字克制。
- **不整页换皮**：继续用 `CtGlassCard(hero)`、`CtShapeTokens`、品牌色（`Color.kt`）与既有组件；只调整分组/层级/文案密度。
- **功能不减**：云同步开关、清除本机缓存、数据与隐私、权限管理、提醒设置、壁纸取色、反馈建议、使用说明、退出登录、用户协议/隐私政策、版本号全部保留。
- **隐私红线**：身份卡不显示头像、不显示学号（旧版 `maskedSid` 胶囊移除，对齐 mp「只留姓名 + 角色徽章」）。

---

## 2. 小程序 me 页 → Android 新版 逐块对照

| # | 小程序 me 块（me.wxml） | Android v5 映射 | 处理 | 说明 |
|---|---|---|---|---|
| 1 | 骨架 `state=loading` | 不需要 | 删除 | Android 数据由 Lead 预聚合传入（`meStats`），页面无独立加载态；统计 0 显示 0（不伪造） |
| 2 | 错误态 `state=error` + 重试 | 不需要 | 删除 | 同上，聚合失败由上层降级为 0/未开启态 |
| 3 | 空态 `state=empty`（还没有身份→去验证） | 不适用 | 保留在页面外 | Android 有 MainNavHost 身份门禁，进入「我的」必已登录 |
| 4 | 身份卡：姓名 + 「身份已验证」+ 职务 + 徽章 + 管理员（**无头像/学号**） | `IdentitySection`：姓名 headlineSmall + `FlowRow` 胶囊（已验证 primary / 职务 secondary / 徽章 / 管理员） | **保留，去学号** | 旧版 `CtPill(session.maskedSid)` 删除（对齐 mp 隐私口径） |
| 5 | 标题「我的数据」+ 2×2 指标（待交/已完成/抽签/体测） | `DataSection` 内「我的数据」2×2（`MetricCell`） | **保留** | 旧版 `MeStatsSection` 4 列 + 内嵌提醒入口；新版改 2×2 并移除内嵌提醒入口（移到「通知与权限」组） |
| 6 | 云同步卡：云同步状态 + 同步时间 + 待补发 + 「立即同步」按钮 | `DataSection` 中 `SwitchRow(云同步, 按账号同步…)` | **简化保留** | Android 现无同步按钮/待补发回调；只保留开关（`onSyncEnabledChange`），接口不变 |
| 7 | 数据与隐私卡：入口 → `pages/data`；三条隐私摘要长文 | `DataSection`：`ActionRow(数据与隐私, 查看与设置)` + `ActionRow(清除本机缓存)` | **合并保留** | 隐私长文不再抄本页（mp 也收敛为入口）；隐私正文在 `DataPrivacyScreen`；清缓存沿用二次确认（M-01） |
| 8 | 帮助卡：反馈建议（入口+副标）、使用说明 | 「关于与帮助」卡：反馈建议 / 使用说明 | **合并保留** | 与用户协议/隐私政策/版本合为一张卡，减少卡片数量 |
| 9 | 退出身份（error 描边按钮）+ 一行说明 | `SignOutSection`：error 描边 `OutlinedButton` + 居中说明 | **保留** | 文案对齐：「退出后需重新验证姓名 + 学号才能使用班级数据」；确认弹窗保留 |
| 10 | 页脚版本号 `versionText` | 页脚 `v$version · 班级工具站` | **保留** | 版本唯一来源 `BuildConfig.VERSION_NAME`；「设计语言/备案信息」等占位行删除 |
| 11 | 无「权限/提醒/外观」块（mp 无系统权限体系） | 新增但归入两个组：「通知与权限」（权限管理 / 提醒设置）、「外观」（壁纸取色 / 深浅色） | **保留（Android 独有）** | 旧版分散在「权限与提醒」「外观」两卡；新版合入同一信息层级，减少纵向长度 |
| 12 | 无调试入口 | 「关于与帮助」卡内 DEBUG-only：实时活动测试 / 关闭实时活动 | **保留（仅 Debug）** | 旧版在关于卡内；新版同样仅 `BuildConfig.DEBUG` 显示 |

### 删除/合并清单
- 删除独立卡片标题（旧「同步」「权限与提醒」「数据与隐私」「外观」「关于」）→ 统一「分组标题 + 一张卡多行」。
- 删除冗余行：`用户数据 userdata v6`、`通知已处理/作业完成 marks 同步`、`通知/日历 本地功能·不上传`、`本机缓存 随账号隔离`、`设计语言 Android 17 · M3 Expressive`、`备案信息 上线前填写`（占位）。
- 合并：`MeStatsSection` 内嵌的「提醒设置」入口 → 「通知与权限」组；`反馈/使用说明` 与协议/版本合并为「关于与帮助」。
- 删除：旧版身份卡学号胶囊（隐私对齐 mp）。

---

## 3. 新版信息架构（Android）

```
我的（ProfileScreen）
├─ Header：BrandMark + 「我的」
├─ 账号与身份
│   └─ 身份卡：姓名 + 身份已验证/职务/徽章/管理员
├─ [通知权限提示横幅]（可选，一次性可关）
├─ 数据与同步
│   ├─ 我的数据（2×2：待交作业/已完成作业/抽签记录/体测记录 + 可选扩展行）
│   ├─ 云同步（Switch）
│   ├─ 数据与隐私 ›
│   └─ 清除本机缓存 ›（二次确认）
├─ 通知与权限
│   ├─ 权限管理 ›
│   └─ 提醒设置 ›（副标 = ReminderStatus.label，权限缺失时 error 色）
├─ 外观
│   ├─ 壁纸取色（Switch）
│   └─ 深浅色（跟随系统）
├─ 关于与帮助
│   ├─ 反馈建议 ›
│   ├─ 使用说明 ›
│   ├─ 用户协议 ›
│   ├─ 隐私政策 ›
│   ├─ 版本（值）
│   └─ [DEBUG] 实时活动测试 / 关闭实时活动
└─ 退出登录（error 描边按钮）+ 说明 + 版本页脚
```

平板（≥840dp）双栏：
- 左（55%）：账号与身份 + 横幅 + 数据与同步；
- 右（45%）：通知与权限 + 外观 + 关于与帮助 + 退出；
- 内容限宽 `spec.contentMaxWidth`（1200dp），间距 16dp，底部留白 120dp。

---

## 4. 风格 token 用法

| 元素 | 规格 |
|---|---|
| 分组卡片 | `CtGlassCard(shape = CtShapeTokens.hero)`（32dp 圆角、玻璃层 L2；不新增强调色） |
| 分组标题 | `labelLarge` + `onSurfaceVariant`，左内边距 6dp（对应 mp `sec-title` 的 muted 小标题） |
| 行（ActionRow/SwitchRow/ValueRow） | `heightIn(min = 56.dp)`，水平 18dp、垂直 8–10dp；整行 `clickable`（点按 ≥48dp） |
| 行图标 | 22dp、`primary`；不使用彩色大图标/头像 |
| 行副标题 | `bodySmall` + `onSurfaceVariant`，最多 2 行；提醒权限缺失时 `error` |
| 分隔线 | `HorizontalDivider(outlineVariant)`，`start = 18dp` 缩进（行式列表视觉） |
| 指标 | 数值 `headlineSmall` + `primary` + Bold；标签 `labelSmall` + muted；2×2 均分 |
| 胶囊 | 复用 `CtPill`；已验证 `primaryContainer`，职务 `secondaryContainer`，徽章/管理员中性 |
| 退出按钮 | `OutlinedButton`，error 色文字 + error 55% 描边，`heightIn(min=52.dp)`，pill 形状 |
| 页脚 | `labelSmall` + muted 居中：`v<version> · 班级工具站` |
| 动效 | 不新增自定义动画；点击反馈沿用 `clickable` + 既有 ripple/CtPressableCard 规范 |
| 深色 | 全部走主题 colorScheme（无硬编码颜色）；预览 172/179 覆盖深色 |

---

## 5. `ReminderSettingsScreen` 变更（用户明确要求）

- **删除**「发送测试通知」按钮及其状态/文案/`ReminderTestNotificationResult` 处理；
- 保留并重命名卡片为「检查与诊断」：说明「立即检查本机通知链路，并查看缓存与上次运行摘要。」+ 单一按钮「立即检查 + 诊断」（`ReminderRuntime.runNow` + `ReminderActions.diagnostics`）；
- 保留：通知权限横幅（`NotificationPermissionBanner`）、提醒总开关/作业/上课/通知/安静时段设置、「说明」卡（WorkManager 15 分钟、省电延迟）与「管理权限」入口；
- 不新增/不删除参数（`onOpenPermissions`/`onSettingsChanged` 不变）。

---

## 6. 给 Lead 的接线点

1. **`ProfileScreen` 参数完全兼容旧调用**（MainNavHost 938 行现有参数无需改动）：
   `session, onSignOut, modifier, syncEnabled, onSyncEnabledChange, onClearLocalCache, onOpenDataPrivacy, onOpenPermissions, showNotificationPermissionBanner, onOpenReminderSettings, dynamicColorEnabled, onDynamicColorChange, adaptiveSpec, meStats, reminderStatus, onOpenFeedback, onOpenGuide`。
   新增仅预览参数（默认关闭）：`initialShowSignOutConfirm: Boolean = false`、`initialShowClearCacheConfirm: Boolean = false`。
2. 页面不再依赖 `ProfileCard`/`ProfileLayoutLogic`（保留文件与既有单测 6 条不动）；`MeStatsSection` 保留供其它预览使用，Profile 不再引用。
3. 建议后续（未实现，需数据/回调）：mp 的「立即同步 / 待补发 N 项」如需移植，新增 `onSyncNow: (() -> Unit)?` 与 `syncText/pendingCount` 参数即可，`DataSection` 已预留同步行结构。
4. `ReminderSettingsScreen` 无参数变化，无需 MainNavHost 改动。
5. 真机验收：手机单栏滚动到底部（退出按钮不被浮岛遮挡，底部 120dp 留白）、平板双栏、深色、大字体 1.3、通知权限横幅点击进权限页。

---

## 7. 预览与验证

- 预览文件：`app/src/main/kotlin/com/furina/classtools/ui/preview/ProfileRedesignPreviews.kt`
  - 171 我的·浅色（412×1100）
  - 172 我的·深色
  - 173 我的·大字体 1.3x
  - 174 我的·平板双栏（1000dp）
  - 175 我的·空数据（统计全 0 / 同步关 / 提醒未开启）
  - 176 我的·退出确认（深色）
  - 177 我的·清除缓存确认
  - 178 我的·通知权限横幅
  - 179 我的·平板深色·壁纸取色开启
- 验证：`gradle :app:compileDebugKotlin` 通过（见 task-39 汇报）；既有单测 1065/0 不破（ProfileLayoutTest 6 条仍针对未改动的 `ProfileLayoutLogic`）。

## 8. 遗留 / 未移植
- mp「立即同步」按钮、同步时间/待补发计数：Android 缺回调（见 §6.3）。
- mp 隐私三条长文：Android 收敛到「数据与隐私」入口页（与 mp v19 一致，不在本页重复）。
- mp 的 `onPullDownRefresh` 刷新：Android 数据由上层聚合 + 仓库各自刷新，本页无独立刷新动作。
- 旧 `MeStatsSection` 组件仍被 `ParityP1Previews` 引用（只读），后续可统一为 `ProfileStatsCard`。
