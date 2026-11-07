# Round 5 增量验收报告 · v2.0.0 大版本（2026-11-08）

> 目标（本轮新增，来自旅行者新需求）：应用图标与网页/小程序一致；功能与微信小程序看齐（不做简化，含工作台）；权限管理；安卓原生通知提醒；课表/通知写日程；UI/动画优化、底部工具栏对齐 ColorOS 17；多比例/平板适配；各页面滑动切换与动画。
> 本轮（goal round 5）交付 **v2.0.0**：品牌图标统一、ColorOS 17 浮岛底栏、三 Tab 滑动切换、工作台对齐小程序、自适应框架、权限管理、原生提醒、写日程。**功能对齐审计已完成**（详见 `docs/MP_PARITY_AUDIT.md`），剩余 P1 页面（反馈/使用说明/我的数据指标卡）进入下一轮。

## 1. 本轮交付

| 模块 | 内容 |
|---|---|
| 品牌图标 | 启动图标（自适应前景/单色层/暖白底）与应用内 `BrandMark` 改为与网页 favicon、微信小程序 `app-icon-1024.png` 完全一致的三圆点；512/1024 商店图标生成（`artifacts/icon/`） |
| 浮岛底栏 | `CtFloatingDock`：半透明胶囊悬浮离底（导航 insets + 10dp）、弹性药丸跟手（拉伸 8%/压扁 2%，spring 吸附）、按压柔性反馈、TalkBack 语义；平板限宽 480dp |
| Tab 滑动 | `CtTabPager` 三 Tab HorizontalPager（8% 位移 + 淡出 + 0.98 缩放跟手）；工具页 push 转场（slide+fade+scale spring）；预测返回 |
| 工作台 | 对齐 `mp-class-tools/pages/index/index.wxml`：问候（时段+姓名+日期/周次/角色）→ 今日课程时间线（多节/上课中/已结束/未同步引导）→ 电费卡（未关注 0 请求）→ 作业待交（数量+最新一条 5s 轮换）→ 通知待处理（同步中/离线/失败四态）→ 常用工具（≤4/空态引导/编辑/「全部 ›」）；≥600dp 双栏 |
| 自适应 | `ui/adaptive`：compact/medium/expanded 断点、内容限宽居中（1200dp）、双栏 helper、工具网格 2/3/4 列；工作台/工具/我的页接入 |
| 权限管理 | 「我的 → 权限与提醒 → 权限管理」：通知/日历四态、理由说明、永久拒绝跳系统设置；`AndroidPermissionController`；Manifest 增 `POST_NOTIFICATIONS`/`READ_CALENDAR`/`WRITE_CALENDAR`/`RECEIVE_BOOT_COMPLETED` |
| 原生提醒 | 作业截止（每日 20:00 汇总 + 截止前 0/1/3 天）与上课提前（5/10/15 分钟）；WorkManager 15min 周期 + BootReceiver 重排 + 安静时段 + 去重；点击通知直达作业/课表（MainActivity 消费 extras → MainNavHost 跳转）；提醒设置页（本机偏好，不同步） |
| 写日程 | 课表课程详情「添加到系统日历（本周）」、通知详情「加入日程」；有权限直接写 CalendarContract（含 10 分钟提醒），无权限/无账号退回系统日历插入界面 |
| 版本 | versionName **2.0.0** / versionCode 3；隐私政策新增通知/日历权限说明 |

## 2. 验证证据（本轮最终）

| 项 | 结果 |
|---|---|
| 全量单测 | **552 tests / 0 failures**（43 个测试类；本轮新增 86：dock/adaptive/workbench 27 + reminder/calendar/permission 59） |
| @Preview | 全量 **97 张** 渲染通过（v2 新增 20：浮岛底栏 6 / 工作台 9 / 自适应 5） |
| 模拟器手测 | 1080×2400 真机尺寸（第二台 headless）已验证：浮岛底栏渲染/选中药丸；左右滑动切换 工作台↔工具↔我的；工作台新结构（时段问候/今日课表空态/电费卡/作业/通知/常用工具）；权限管理页（通知/日历四态）；提醒设置页（总开关/作业/上课/提前档位/安静时段）；启动图标三圆点 |
| 构建 | `assembleDebug` / `assembleRelease` SUCCESSFUL |
| Release | APK **2,413,297 B**；SHA-256 `9E6759686402C56B5CA43FE6E59A84863F78E05104951A0AAB00DA5CAB53D3A3`；apksigner V2 通过（证书 `974854df…4a1a`） |
| 广告/权限合规 | 仍零第三方 SDK/广告/统计；通知/日历权限均有理由说明与降级路径 |

## 3. 关键实现说明

1. **导航架构重构**：`MainNavHost` 由「NavHost 三个顶层 destination」改为「`root` 根页 Pager + push 目的地」，`pagerState` hoist 在 NavHost 外层，进入工具页返回后保持当前 Tab；`navigateTopLevel` 旧缺陷（吞点击）随之消失（底栏直接操作 pager）。
2. **浮岛底栏性能取舍**：半透明色层 + 1dp 高光描边 + 阴影（未使用 RenderEffect 实时模糊，避免低端机掉帧）；`translucent=false` 可关闭半透明。
3. **提醒数据源**：Worker 只读本机缓存（作业 TTL 10min / 课表 6h / marks 2min），登录后 `warmReminderCaches()` 预热，不联网；通知权限缺失时静默跳过。
4. **写日程降级链**：权限 GRANTED → CalendarContract 直接插入；NeedsPermission/NoCalendarAccount → `ACTION_INSERT` 打开系统日历由用户确认；保证任何机型可用。
5. **品牌图标几何**：64 视口 → 自适应 108 画布安全区（72/64 缩放居中），主圆 r=10.125，副圆 r=5.625，位置与 web SVG 完全一致。

## 4. 已知问题 / 下一轮

| # | 项 | 说明 |
|---|---|---|
| 1 | v2 E2E 未跑 | 旧 E2E 脚本基于旧底栏文案（「全部」），v2 改为「工具」+ Pager，需 toolchain 更新脚本（底栏点击/滑动断言、权限页/提醒页/写日程入口）后跑完整回归；本轮用手测+单测+预览兜底 |
| 2 | 多设备适配矩阵 | 首轮因 Lead 并发启动第二台模拟器抢 WHPX，toolchain 的 AVD 未能 boot；已协调下一轮单独重跑，结果补入 `docs/ADAPTIVE_TEST_MATRIX.md` |
| 3 | 启动图标 launcher 渲染 | Pixel Launcher（API 37 模拟器）在图标外圈显示一圈随背景色变化的浅色环；三圆点本体正确。需真机复核（若存在则改用 legacy PNG 图标或调整 monochrome 层） |
| 4 | MP 功能对齐剩余 P1 | 反馈建议(fb)、使用说明(ug)、我的数据指标卡、单条通知提醒、结果单复制/点名导出等（见 `docs/MP_PARITY_AUDIT.md`） |
| 5 | 工具网格 3/4 列 | 自适应 helper 已提供，AllToolsScreen 拖拽几何仍固定 2 列，需参数化（`V2_ADAPTIVE_SPEC.md §5`） |
| 6 | 真实账号链路 | 仍需旅行者本人/测试身份实测（同 v1.0.1 边界） |

## 5. 文档

- 新增 `docs/ui/V2_MOTION_SPEC.md`、`V2_ADAPTIVE_SPEC.md`、`V2_WORKBENCH_SPEC.md`
- 新增 `docs/api/REMINDER_CALENDAR_IMPL.md`、`docs/MP_PARITY_AUDIT.md`
- 更新 `docs/CHANGELOG.md`（v2.0.0）、`README.md`、`docs/ARCHITECTURE.md`、`docs/PRIVACY_COMPLIANCE.md`
