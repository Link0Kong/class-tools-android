# 与微信小程序功能对齐审计（v2.0.0 Round 1）

> 审计时间：2026-11-08；只读 `mp-class-tools`（v2.0.0 / v25，21 页）与 `web/`（未改动）。
> 对比对象：Android v1.0.1（46 个原生页面/组件 + 数据层；基线 466/0 测试）。
> 审计方法：读取 `mp-class-tools/app.json`（页面/自定义 tabBar）、`pages/*/` 页面 JS 头部职责注释、`utils/*` 关键模块（notify/share/cloud/roster/water 等），与 Android `ui/screens/*`、`data/*`、`features/*` 对照；不运行小程序、不改 mp/web。
> 状态定义：**已对齐**（功能可用且交互等价）/ **部分对齐**（核心可用，缺子功能或交互差异）/ **缺失**（Android 无）/ **仅管理员**（mp 也仅管理员，Android 未做）/ **不适用**（平台差异，Android 以系统能力替代）。

---

## 1. 总览

| 状态 | 页数 | 说明 |
|---|---|---|
| 已对齐 | 12 | identity / index / all / tt / ft / fttable / hw / nt / group / draw / roll / water / power（12） |
| 部分对齐 | 5 | me / data / sum / committee /（nt 管理员子功能） |
| 缺失 | 3 | fb（反馈）/ ug（使用说明）/ stats（访问统计） |
| 仅管理员缺失 | 2 | diag（诊断）/ stats（访问统计） |
| 不适用/平台替代 | 2 | 订阅消息→原生通知（数据层已建）；分享卡片→系统分享 |

**结论**：v1.0.1 的 8 个工具与身份/工作台/工具目录已基本对齐小程序；v2.0.0 用户新需求中的 **原生通知提醒**（数据层本轮完成）、**日历写入**（本轮完成）、**页面滑动切换/图标一致/多比例平板**（v2 UI/design 任务）是主要差距；**反馈/使用说明/我的数据统计/诊断/访问统计** 是发布前建议补齐的页面级差距。

---

## 2. 逐页对齐矩阵

| # | mp 页面 | mp 功能（按页面注释） | Android 现状 | 状态 | 优先级 | 实现建议 / 工作量 |
|---|---|---|---|---|---|---|
| 1 | `identity` | 姓名+学号 → `rpc/class_verify` → token；协议勾选；30 天身份 | `IdentityScreen`（学号+姓名、协议弹层、同意勾选、脱敏展示、29 天本地有效期；Debug 才有开发预览） | **已对齐** | — | 维持；v2 增加通知/日历权限引导入口（本轮数据层已备文案） |
| 2 | `index` 工作台 | 今日课程时间线 + 待办（作业/通知）+ 4 常用工具 + 编辑首页；下拉刷新 | `HomeScreen`（今日/下一节、待办、常用工具编辑≤4、全部入口、快照缓存加载） | **已对齐** | — | 缺下拉刷新与页面滑动切换（v2 P1 统一做） |
| 3 | `all` 全部工具 | 全部工具目录（`tools.catalog()`）+ 长按拖动排序 + 编辑首页按钮 | `AllToolsScreen`（目录、长按拖动、插入位置、prefs.toolOrder 云同步、TalkBack 上移/下移） | **已对齐** | — | 图标体系 v2 统一（design） |
| 4 | `me` 我的 | 身份卡、我的数据（待交作业/已完成/抽签记录/体测记录）、云同步开关、数据与隐私、帮助、退出 | `ProfileScreen`（身份卡/学号脱敏、同步、数据与隐私、关于、动态取色、深浅色、退出） | **部分对齐** | **P1** | 缺「我的数据」4 指标卡；缺「提醒设置」入口（本轮数据层已备）；退出无二次确认（Release 审计 P1-3）；建议 Lead/design 补 `我的数据`（作业/通知 marks 计数 + fitrecs 计数 + 抽签记录本机）与提醒设置页；约 0.5–1 人日 |
| 5 | `data` 数据与隐私 | 身份摘要、云同步开关、同步范围清单、本机数据概览（8 计数）、云端数据说明、导出我的数据、清除云端/本机、访问统计（管理员） | `DataPrivacyScreen`（概览 3 指标、导出 JSON、清除云端/本机、隐私说明、隐私政策/用户协议）；同步范围清单未做；访问统计未做 | **部分对齐** | **P1**（同步范围/统计口径说明）/ P2（访问统计入口） | 补「同步范围清单」与「本机数据 8 计数」（marks/通知/体测/抽签/分组/点名等）；访问统计管理员入口 P2；约 0.5 人日 |
| 6 | `stats` 访问统计 | 管理员：按账号聚合访问次数/最近时间，今天/近 7 天 | Android 无（仅管理员；E18 visits 契约已记录） | **缺失（仅管理员）** | P2 | 管理员页（可合入「数据与隐私」管理员区）；复用 E18 `visits?select=slot,sid,name&slot=gte.…`；约 1 人日 |
| 7 | `sum` 结果单 | 抽签+分组汇总、可复制/导图 | Android：`DrawSheetFactory`/`GroupExportFactory` 直接导出结果单图片；无独立「结果单页」 | **部分对齐** | P2 | Android 用导出/分享替代「复制」；若需复制文本可加「复制结果」按钮；约 0.5 人日 |
| 8 | `fb` 反馈建议 | 提交反馈（content/contact）→ feedback 表；我的反馈；管理员列表 | Android 无反馈入口 | **缺失** | **P1**（发布后用户反馈渠道/商店合规建议） | 新增「反馈」页：文本输入 + 可选联系方式 → POST rpc/feedback；「我的反馈」本机/云端列表；约 1 人日（数据层契约需补 E20/E21 细节） |
| 9 | `ug` 使用说明 | 身份系统说明、各工具用法、隐私/安全提示 | Android 无使用说明页（隐私政策/用户协议有；无工具用法） | **缺失** | **P1**（新用户引导） | 新增「使用说明」页（静态分区文本，零网络）；约 0.5 人日 |
| 10 | `committee` 班委名单 | role 非空成员、角色/徽章；所有验证同学可看 | Android `RosterScreen` 名册含角色/徽章，但无独立「班委」筛选页 | **部分对齐** | P2 | 名册筛选加「班委」chip（已有 `RosterFilter.COMMITTEE`，只需入口/默认筛选）；约 1–2 小时 |
| 11 | `tt` 课表 | 周/日视图、本周/上下周、下一节、只读、缓存优先、site_assets 去域名化、静默刷新 | `TimetableViewScreen`（周/日、上一周/下一周、下一节、缓存优先、site_assets 优先 + 失败回退、空态三态文案、≥600dp 双栏） | **已对齐** | — | v2 加「写日程」（本轮 `CourseCalendarMapper`/`CalendarWriter` 已备）；页面滑动切换（v2 UI） |
| 12 | `ft` 体测 | 国标评分、历史（fitrecs 云端最多 2 条）、导出 | `FitScreen`（评分/历史/导出/fitrecs 云端读写/改名/删除 arch） | **已对齐** | — | 维持；权限/隐私提示 |
| 13 | `fttable` 国标表 | 男女/年级评分表 | `FitScreen` 内置国标表弹层（`FitTables`） | **已对齐** | — | — |
| 14 | `hw` 作业 | 列表/完成（marks）/截止倒计时/导出？ | `HomeworkScreen`（列表、完成 marks、筛选、倒计时/分组、导出？Android 无作业导出） | **已对齐**（提醒本轮补齐数据层） | P2（作业导出） | 若需导出作业清单可复用 `ExportCardSpec`；提醒设置 UI P0（本轮数据层） |
| 15 | `nt` 通知 | 简报/筛选/已处理/置顶/附件图片/管理员编辑隐藏 | `NoticesScreen`（列表、已处理、置顶、筛选、附件图片预览；管理员编辑/隐藏未做；单条通知提醒未做） | **部分对齐** | P1（通知提醒）/ P2（管理员编辑） | 管理员编辑需 E9 PATCH（仅管理员，P2）；「通知截止提醒」可复用 Planner/Calendar 的 targetMillis（P1） |
| 16 | `group` 分组 | 第 2 步：来源（上一步/名单）/组数/每组人数/打乱/结果/导出 | `GroupScreen`（来源、组数/每组、打乱、结果、导出、上一步抽签联动、Saver 状态恢复） | **已对齐** | — | v2 加「写日程」不适用 |
| 17 | `draw` 抽签 | 第 1 步：范围/排除/名单过滤/洗牌不放回/允许重复/STEP3 结果/剔除重抽/导出 | `DrawScreen`（三步、排除、名单、去重/重复、排序、剔除重抽、结果单/抽签图导出、用此结果分组、Saver 状态恢复） | **已对齐** | — | — |
| 18 | `roll` 点名 | 名单选择（班级名单座号）/到·假/重置/统计/沿用抽签名单 | `RosterScreen` 点名页签（名单、到/假、重置、统计、沿用抽签名单、沉底、状态同步） | **已对齐** | — | 点名结果导出（mp 有？）暂 P2；若需要可加导出卡片 |
| 19 | `power` 电费 | 关注宿舍、24h/7d/30d 趋势、慢频日更、点击采样点、低电提示/阈值 | `PowerScreen`（关注宿舍、区间、趋势点击/气泡、慢频日更/参考、低电阈值、估算、无障碍） | **已对齐** | P2（低电本地通知） | 低电提醒可用本任务 Planner 思路扩展（kind LOW_POWER）；约 0.5–1 人日 |
| 20 | `water` 水量 | 关注宿舍（沿用 power.sel）、4/6 人间、月份选择、抄表历史、水费/扣电、同步 | `WaterScreen`（关注宿舍、4/6、月份 12 宫格、录入校验/覆盖确认、统计、历史删除、规则卡） | **已对齐** | — | 写日程不适用 |
| 21 | `diag` 诊断 | 管理员：18 行/5 组服务健康（web 同源） | Android 无 | **缺失（仅管理员）** | P2 | E25 service_health 管理员页；可合入「数据与隐私」管理员区；约 1 人日 |
| 22 | （全局）`utils/notify.js` | 微信订阅消息：作业/通知订阅、云端发送 | Android：**原生通知**（本轮数据层：作业汇总/截止前、课表下一节、WorkManager+渠道+去重+开机重排）；通知单条提醒未做 | **部分对齐（已超越 mp 的本地能力）** | P0（UI 设置入口/权限）/ P1（ntremind） | Lead 接线路由/设置页 + Manifest receiver；通知提醒可扩展 Planner；mp 的云端订阅消息不迁移（隐私更优） |
| 23 | （全局）`utils/share.js` | 分享卡片/图片（小程序卡片） | Android：导出图片 + 系统分享（`ACTION_SEND`，FileProvider）；无小程序卡片分享 | **不适用/部分** | P2 | 系统分享已满足；v2 可加「分享我的数据 JSON」入口（隐私页已有） |
| 24 | （全局）`darkmode`/`theme.json` | 深色模式 | Android：`ClassToolsTheme` 深色 + 动态取色（DataStore）；审计发现体测等级胶囊深色对比度问题（Release 审计 P1-5） | **部分对齐** | P1（修复对比度） | 修 `FitScreen` 等级胶囊 token；课表网格线深色（P2） |
| 25 | （全局）图标/品牌一致 | 小程序自定义 tabBar + 工具图标 | Android：Material Icons（部分非品牌统一）；v2 用户要求「图标一致」 | **部分对齐** | **P1**（v2 UI/design） | 统一 `ToolCatalog.icon/accent` 与 drawable 单色图标集；通知小图标；Lead/design 任务 |
| 26 | （全局）页面滑动切换 | 小程序顶部工具条滑动/切页 | Android：v1 工具页无左右滑动切页（抽签 STEP 用按钮） | **缺失**（v2 需求） | **P1**（v2 UI） | 导航层统一手势（NavHost + Pager 或自定义 swipe），注意与课表横向滚动/图表手势冲突；design/Lead |
| 27 | （全局）多比例/平板 | 小程序自适应（rpx） | Android：v1 已有 ≥600dp NavigationRail/双栏；缺平板/折叠屏系统矩阵 | **部分对齐** | P1（任务-22 自适应矩阵） | 按 `docs/ADAPTIVE_TEST_MATRIX.md`（并行任务）修复 |
| 28 | （全局）日历 | 小程序无 | Android：**新增能力**（本轮 `data/calendar`：课程/通知写日程 + ACTION_INSERT 兜底） | **超越 mp** | P1（UI 入口） | Lead 在课表/通知页加「写日程」按钮与权限/兜底流程 |

---

## 3. 优先级汇总（v2.0.0 建议顺序）

### P0（本轮/下一轮必须，用户新需求直接相关）
1. **提醒设置入口 + 通知权限流程**（数据层已完成；UI 需 design/Lead）：我的 → 提醒页、权限引导、开关/提前档位、Worker 首次调度（`ReminderRuntime.reschedule`）。
2. **日历写入入口**（数据层已完成）：课表「导入本周日程」、通知「加入日历」；无权限时 ACTION_INSERT 兜底；权限文案/引导。
3. **Manifest receiver**：`ReminderBootReceiver` 声明（Lead，片段见 `REMINDER_CALENDAR_IMPL.md` §6.1），否则开机后提醒不再重排（WorkManager 会持久化，但强制停止/厂商清理后需要）。
4. **通知点击跳转**：MainActivity 消费 `EXTRA_TOOL_ID`（hw/tt），未登录时登录后补跳。

### P1（发布前/上线后短期）
5. 「我的数据」4 指标卡 + 提醒设置入口（`me` 页）。
6. 反馈建议页（`fb`）与使用说明页（`ug`）——商店合规/新用户。
7. 单条通知提醒（`ntremind`，可复用 Planner/Calendar targetMillis）。
8. 深色模式对比度修复（体测等级胶囊）+ 课表深色网格线。
9. 图标统一 + 页面滑动切换（v2 UI/design）。
10. 多比例/平板矩阵回归（任务-22）。

### P2（后续增强）
11. 访问统计（管理员，stats）/诊断（管理员，diag）。
12. 管理员编辑/隐藏通知（E9）。
13. 结果单复制/独立页、点名结果导出、作业清单导出。
14. 低电提醒、日历去重/更新删除、Wear/桌面小组件等。

---

## 4. 对齐说明（哪些「不适用」）

- **微信订阅消息**：小程序依赖 `wx.requestSubscribeMessage` + 云函数定时发送；Android 用系统通知 + WorkManager，不向云端发送任何订阅/提醒数据，隐私更优，**不迁移云端订阅**。
- **小程序分享卡片**：Android 用系统分享（FileProvider）替代；无小程序卡片。
- **微信登录/授权**：Android 仍走班级学号+姓名门禁（与 web/mp 同源），无微信 OAuth，属于平台不适用。
- **tabBar 自定义图标**：Android 用 Material Icons/主题色，v2 统一品牌图标（见 P1）。

---

## 5. 审计方法与证据

- mp：`mp-class-tools/app.json`（21 页 + tabBar；v2.0.0 自定义 tabBar）、`pages/*/*.js` 头部职责注释（逐页读取）、`utils/notify.js`（订阅消息契约）、`utils/share.js`、`README.md`（v2.0.0 变更记录）。
- Android：`ui/screens/*`（identity/home/all/profile/data/draw/group/fit/hw/nt/tt/power/water/roster/tools）、`ui/navigation/MainNavHost.kt`、`data/*`、`features/*`；对照 `docs/RELEASE_AUDIT_20261108.md`（当前质量基线）与 `docs/MP_PARITY_AUDIT.md`（历史口径：`web/AGENTS.md`）。
- 测试/质量基线：Android 466/0（v1.0.1）→ 552/0（v2.0.0 Round 1 本任务后）；mp `README` 报告 unit 234/0、v3 45/0、sync 31/0（仅参考，不运行）。
- 只读约束：未改 mp/web/Android 既有页面；本轮仅新增 `data/{reminder,calendar,permission}` 与测试/文档。

---

## 6. 结论

- **功能面**：小程序 21 页中 12 页已对齐、5 页部分对齐、3 页缺失（fb/ug/stats）+ 2 项仅管理员（stats/diag）；核心 8 工具 + 身份/工作台/目录已对齐，Android 在体测/抽签/分组/课表/电费/水量/名册/通知的交互深度已达到或超过小程序（导出卡、图片预览、状态恢复、无障碍等）。
- **v2.0.0 重点**：通知提醒（数据层已交付）、日历写入（数据层已交付）、图标一致/页面滑动/多比例（design/Lead/任务-22）、我的数据统计/反馈/使用说明（建议下一轮补页面）。
- **隐私**：Android 不迁移微信订阅消息，不上传提醒/日历数据，符合 web 红线；通知内容不含学号/手机号/宿舍号。
