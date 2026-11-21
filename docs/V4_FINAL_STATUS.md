# 班级工具站 Android v4 · 最终状态与验收对照（2026-11-12）

> 覆盖 v4.0 → **v4.41.0**（versionCode 57），共 42 个签名版本，全部交付到桌面并做设备/模拟器验证。
> 本文档把用户 v4 目标逐条映射到实现、证据与遗留边界，作为阶段验收与后续维护入口。

## 0. 交付与验证基线（最新版本 v4.41.0）

| 项 | 结果 |
|---|---|
| APK | `<下载目录>\班级工具站_v4.41.0_安卓.apk`，**3,026,713 B** |
| SHA-256 | `E663D882F9EE20794169ED5C6ACF94FB8E854F1C65F2B53944E2A5DB7F2CA25E` |
| 版本 | versionCode 57 / versionName 4.41.0 / minSdk 26 / targetSdk 37 |
| 签名 | 自签名 V2（CN=ClassTools），与历史版本同 keystore |
| 单测 | **1065 / 0 失败**（`:app:testDebugUnitTest`） |
| 快速 E2E | **43 PASS / 0 WARN / 0 FAIL（1,144.4s）**（ct-api37；详见 `artifacts/e2e/summary.json`） |
| logcat | 0 FATAL / 0 ANR；Release 两次冷启动正常 |
| 预览渲染 | 180+ Compose @Preview PNG（`artifacts/previews*`） |
| 无障碍审计 | 12 个界面「可点击但无文字/描述」节点 = **0**（`artifacts/a11y-audit/`） |
| 启动性能 | 启动懒加载后中位 416→356ms（同环境 5 次）；Baseline Profile 强制 speed-profile 后冷启动中位 **282ms** |

## 1. 目标①：基于调研/演示的深层优化

| 子项 | 落地情况 | 证据/文档 |
|---|---|---|
| 真实背景模糊/光场层次 | **底栏悬浮岛接入 Haze 1.7 真实背景模糊**（24dp、55% 表面色、噪点），旧设备自动降级半透明；卡片保留既有半透明+渐变 | `artifacts/haze-style-dock.png`；`docs/design/V4_RESEARCH_AND_DEMOS.md` 附录；`docs/REPORT_ROUND44` |
| 动效 token | `CtMotion`（spring/tween/spatial）+ 页面转场 `CtPageTransitions`；新增**系统「移除动画」适配**（ContentObserver 实时监听：轮换/计数器/PPT 热点降级） | `ui/theme/CtMotion.kt`、`CtMotionPrefs.kt`；REPORT_ROUND46/47 |
| 交互与状态 | 全局撤销（体测删除/通知/名单/水量）、通知卡行内重试、身份页禁用原因、下拉刷新（工作台/PPT）、编辑返回确认、参数已保存提示、退出登录提示、抽签分组徽标 | 各轮报告 26–51 |
| 无障碍 | 7+12 屏静态审计 0 未标注；开关读屏标签；课表大字体行高自适应；dock/Tab/卡片语义与 heading；TalkBack 文案 | `artifacts/a11y-audit/`；REPORT_ROUND45/48 |
| 自适应 | 手机 360dp / 平板 800–1000dp 双栏 / 大字体 1.3x：工作台、课表、电费、投票、我的均验证；平板课表导出卡固定底部 | `artifacts/adaptive-*`、`previews_t01/t07/t08` |
| 性能 | 启动懒加载 + WorkManager 异步化（-15% 启动中位）；应用级 **Baseline Profile**（18,166→17,649 规则，v4.39 刷新）；计数组件重组优化 | `docs/BASELINE_PROFILE.md`；REPORT_ROUND28/50 |
| 系统集成（额外） | **Android 16 Live Updates / ColorOS 16 流体云**：PPT 上传进度实时活动 + 限时投票倒计时；系统实测 `PROMOTED_ONGOING` | `docs/LIVE_UPDATES_20261112.md`；REPORT_ROUND49/51 |

## 2. 目标②：同步班级工具站近期网页/小程序功能

| 功能 | 落地情况 | 证据 |
|---|---|---|
| 投票（含可见人范围） | 列表/详情/发起/改票/管理；单选多选、匿名、允许改票、结果可见性；**可见范围：全班/班委/团员/指定成员（座号）**；倒计时、15s 静默刷新；限时投票接入系统实时活动 | `ui/screens/vote/*`；E2E `tool_vote`；REPORT_ROUND11 |
| 名单三分类 | 「班委 / 团员 / 全班」分段常显（READY/ERROR 均有）；团员走 `class_league`；名单/点名/清空撤销 | E2E `roster_categories`；REPORT_ROUND13+ |
| 电费自然周期同期比较 | 24h=较昨日同期、7d=较上周同期、30d=较上月同期；样本覆盖不足不硬凑 | `features/power/PowerCompareLogic.kt` + 单测 |
| 13/16 号楼隐藏 24h | 慢楼栋隐藏 24h 档并自动落 7d；数据仍拉取用于日更统计 | 同上 |
| 慢更新「日更」/参考 | 慢房日更徽标、上次变化、估算标参考、无更新提示 | `PowerStats` 单测 + 预览 |
| PPT 对齐现状 | 列表/上传（分片）/下载详情（兼容版/PDF/原文件/媒体单独下载）/播放器（原样视频+分步回退）/续播/软删 | E2E `tool_ppt`；ROUND13–24 |
| 诊断对齐现状 | 数据/服务健康/设备检查/AI 分析/修复任务/本机代理状态/简洁模式/自动刷新暂停/详情折叠 | `DiagScreen` + 预览；E2E `diag_page` |
| 其他同步 | 作业完成、通知已处理/置顶、水表抄录、课表登录/个人课表、提醒设置、反馈、使用说明 | 各轮报告与测试 |

## 3. 目标③：系统排查与修复

- 初始 112 项审计（`docs/design/V4_UX_AUDIT.md`）：**P0 全部修复**，**P1 主体修复**，**P2 绝大多数修复**（撤销、触感、读屏语义、诊断简版、写日历权限、宿舍搜索、水量校验、通知搜索、诊断暂停、审计文案、计数器、大字预览、课表去重、演示下拉刷新、平板限宽、dock 光场、通知大图缩放分享、隐私分组、数据明细、电费切档过渡、房间搜索、大字体课表、平板导出卡、实时活动等）。
- 每轮均：单测 → 预览/设备验证 → 快速 E2E → Release 冷启动/logcat → 桌面交付 → 文档/CHANGELOG → git commit+tag。

## 4. 遗留边界（无法在本环境闭环或属产品取舍）

| 项 | 说明 |
|---|---|
| TalkBack 真机逐项走查 | 静态审计 0 未标注，但真实播报顺序/语气需真机人工确认；本环境仅有模拟器与 uiautomator |
| ColorOS 16 真机流体云呈现 | 已按 Android 16 Live Updates 规范实现且模拟器验证 `PROMOTED_ONGOING`；最终「入云」观感需 ColorOS 16 真机确认 |
| 页面级玻璃化 | 目前仅底栏真实模糊；全页面模糊涉及每帧采样与性能，保留为可选优化 |
| OPPO 旧版流体云接口 | 需走 OPPO 意图框架合作申报，本轮未接入（文档已注明可立项） |
| A-04 角色徽标 | 当前 Android 端无「管理员专属工具」入口（诊断全员可见），审计项判定不适用 |
| H-07 首屏统一骨架 | 各卡已有独立 loading，统一 shimmer 属锦上添花，未做 |

## 5. 后续建议

1. 有 ColorOS 16 真机时：安装 v4.41.0 → 打开「我的 → 权限与提醒 → 实时活动通知」确认渠道 → 触发限时投票/PPT 上传，确认流体云呈现。
2. TalkBack 真机走查：按 `docs/design/V4_UX_AUDIT.md` 无障碍条目抽查工作台/抽签/投票/电费。
3. 若需要旧版流体云/小布建议：以 OPPO 意图框架另立小项目（资质+模板）。
4. 页面级玻璃化若要做：建议先用 Baseline Profile + 真机帧率测量评估成本再决定。
