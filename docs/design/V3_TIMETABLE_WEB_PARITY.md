# V3_TIMETABLE_WEB_PARITY.md — 课表页 vs 网页/小程序 功能对照（v3）

> 实现：`ui/screens/timetable/TimetableViewScreen.kt` + `TimetableDesign.kt`
> 参考（只读）：`web/tools/index.html`（`#tGrid`/`#tDayList`/`#tImg`/`ttGoWeek`/滑动切周/swipe 参数）、`mp-class-tools/pages/tt/`（`tt.js`/`tt.wxml`）
> 数据：`data/timetable/TimetableRepository`（site_assets id=tt，6h TTL，失败回退本机缓存）

---

## 1. 逐项对照

| # | 功能 | 网页 | 小程序 | Android（v3） | 差异/说明 |
|---|---|---|---|---|---|
| 1 | 数据源 | 同源 `tt.json` → site_assets 回退 | site_assets 优先 → 旧 tt.json 回退 | site_assets（`site_assets?id=eq.tt`）→ 本机缓存 | 已一致；Android 不请求旧域名 |
| 2 | 周次 | `floor((today-termStart)/7)+1` | 同 | `TtCalendar.weekOf` | 一致 |
| 3 | 上一周/下一周 | 按钮 `ttGoWeek(±1)` | 按钮 | 顶部按钮 + 左右滑动 | 一致 |
| 4 | 左右滑动切周 | 整张课表卡起手，跟手→飞出/飞入（锁定 12px、提交 56px、阻尼 0.28、点击抑制 320ms） | 同参数；手势只包课表内容 | **同参数**（`TimetableSwipe` 常量逐项对齐）；手势只包周表/日视图内容（不含顶部按钮/下一节/全部课程） | 范围与小程序一致（比网页窄，符合「课表内容滑动」规范） |
| 5 | 横向滚动冲突 | 表格可横向滚动（`scroll-view`），2026-11 后列宽自适应不溢出 | 列宽自适应 → 无横向溢出，任何横向手势都切周 | **列宽按可用宽度自适应（5–7 列恒不溢出）→ 无横向滚动，滑动无歧义** | 已消除冲突（与 mp 同款方案） |
| 6 | 本周按钮 | `#tThisW`（非本周显示） | `isThisWeek` 为 false 时显示 | 显示逻辑同 mp；点击回到本周（带动画） | 一致 |
| 7 | 周/日切换 | 分段按钮（只点击，不滑动） | 同 | 同（`CtSegmentedButtons`，只点击） | 一致 |
| 8 | 16 色配色 | `TT_COLORS` 16 组（首次出现顺序分配） | `.tt-c0~15` 16 组（首次出现顺序分配） | `TimetablePalette` 16 浅 + 16 深；**按课程名 FNV-1a 散列固定索引** | 差异：Android 跨会话/跨设备稳定（web/mp 按渲染顺序），同名同色；浅色取自 web 原值 |
| 9 | 深色配色 | CSS 暗色变体 | `.tt-c0~15` 暗色变体 | 独立 16 组深色（对比度 6.2–9.0:1） | 已适配（用户反馈①） |
| 10 | 课程块内容 | 课程名（换行）+ 教室（2 节以上） | 同 | 同；手机 10sp/平板 11sp，最多 2–3 行 | 比例优化 |
| 11 | 今日高亮 | 列头/列底高亮 | 同 | 列头 primaryContainer、列底 10% tint | 一致 |
| 12 | 周末列 | 有课才显示（隐藏空周末） | 同 | 同（5/7 列，`visibleDayCount`） | 一致 |
| 13 | 节次时间列 | 12 节起止时间 | 同 | 手机 38dp 紧凑列（节次号+开始时间）、平板 52dp 显示起止 | 比例优化（手机省宽） |
| 14 | 日视图 | 日期条 + 当天课程列表 | 同 | 同；左侧 4dp 课程色条、时间/名称/时段/教室/状态 pill | 一致 + 配色 |
| 15 | 下一节/上课中 | `nextbox`（下一节） | 同 | 卡片：上课中优先（显示剩余分钟）/下一节（日期·时间·教室） | Android 增加「剩余分钟」 |
| 16 | 课程详情 | 只读弹层（名称/节次/时间/教师/教室/周次/单双周） | 同 | `CtBottomSheet`：色点 + `TimetableDetail.detailRows` + 只读说明 | 一致 |
| 17 | 写日程 | 无（v2 需求新增） | 无 | 「添加到系统日历（本周）」按钮（`onAddToCalendar`，Lead 接 CalendarWriter） | Android 独有入口（保留） |
| 18 | 导出课表图片 | `#tImg`：按天 TextBlock + config（周次/日期/课程数/学期起始）+ `rec(TT)` | `exportImg` | 顶部/侧栏入口 → `TimetableExport.buildSpec` → `onExportTimetable(ExportCardSpec)`（Lead 接 ExportPresenter） | 结构一致；Android 复用统一 ExportCardSpec |
| 19 | 空态 | 课表为空提示 | 同 | 空态（刷新）+ 三态错误（网络/无数据/格式） | 与 v2 一致 |
| 20 | 全部课程 | 可折叠列表 | 同 | 平板右栏常驻（含色点/教师/节数）；手机不占空间 | 取舍：手机隐藏（信息密度） |
| 21 | 平板/限宽 | 桌面主区+侧栏 | 无 | `CtAdaptiveSpec`：内容限宽 1200dp 居中，≥840dp 左右两栏（左 66% 周表/右 34% 下一节+全部课程+导出） | Android 独有 |
| 22 | 显示比例 | `clamp()` 字号/列宽 | rpx 自适应 | 行高 52/56/60、标签列 38/48/52、表头 40/44/46、列宽 = 可用宽/天数 | 已对齐「手机压缩、平板舒展」 |
| 23 | 刷新/缓存 | 有 TTL，本机缓存 | 同 | 顶部刷新按钮、缓存 pill、「刷新失败显示缓存」 | 一致 |
| 24 | 点击抑制 | 滑动后 320ms 吞点击 | 同 | 同（`suppressClickUntil`，课程块点击统一守卫） | 一致 |
| 25 | 边界阻尼 | 第一/最后周橡皮筋 0.28 | 同 | 同 | 一致 |

---

## 2. 需 Lead 接线
1. `onExportTimetable: (ExportCardSpec) -> Unit` → `launchExport(spec)`（现有 ExportPresenter 即可，无需新模型）；
2. `onAddToCalendar: (Course, Int, LocalDate) -> Unit` → CalendarWriter（v2 已存在，保留）；
3. 无需改 MainNavHost 的其他参数（默认值兼容 v2 调用）。

## 3. 已知差异/遗留
- 16 色分配算法：Android 用「名称散列固定」而非 web/mp 的「首次出现顺序」，因此同一课程在两端**颜色可能不同**（但各自稳定）；如需完全同色，可后续把 web 端也改为名称散列（需跨端同步改动，本轮不做）。
- 「全部课程」手机端未展示（网页有折叠列表）；如需要可在周表下方加折叠卡片（P2）。
- 写日程入口为 Android 独有（用户 v2 需求），网页/小程序无。
- 滑动切周动画参数按 mp `tt.js` 实现；网页端最新动画细节（部分缓动）未逐帧对齐，观感一致。
- 周末列隐藏规则同 web/mp（两天都无课 → 5 列）。

## 4. 验证
- 预览：`ui/preview/V3TimetablePreviews.kt`（120–133，13 张：手机周/日/深浅、滑动中、7 列、下一周、平板双栏深浅、空态、错误、小屏、详情、16 色板）。
- 单测：`ui/screens/timetable/TimetableDesignTest.kt`（15 例：配色稳定性+对比度、滑动锁定/提交/阻尼/边界、周次 clamp、比例/列宽、详情文案、导出 payload）。
