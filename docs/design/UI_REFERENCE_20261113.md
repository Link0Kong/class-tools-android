# UI_REFERENCE_20261113.md — 参考 App 拆解与课表 v5 改造清单

> 任务：task-42（Android 课表页 v5：参考 HFUT-Schedule / xiaoying 的布局、配色、动效，重做课表页）。
> 参考对象（**仅设计参考，不复制源码、不复制图片素材、不引入新依赖**）：
> - HFUT-Schedule（聚在工大，Kotlin/Compose）：https://github.com/Chiu-xaH/HFUT-Schedule ；本地参考图 `C:\Temp\ui-refs\ui.jpg`、`hfut-readme.md`、`Arch.md`、`ContainerShared.md`
> - xiaoying（小应生活，Flutter）：https://github.com/plum-tech/xiaoying ；本地参考图 `C:\Temp\ui-refs\xiaoying-timetable.webp`、`xiaoying-me.png`、源码只读 `C:\Temp\ui-refs\xiaoying-src`
> 工程基线：`android-class-tools` HEAD `2dd4a69`（v4.42.0，单测 1098/0），现有课表 `TimetableViewScreen.kt`（1655 行）+ `TimetableDesign.kt`。
> 交付范围：`ui/screens/timetable/**`、`ui/preview/V3TimetablePreviews.kt`、timetable 单测、本文件；**不改** MainNavHost/theme/构建/其他 screens。

---

## 1. 结论先行（借鉴/不借鉴）

### 从 HFUT-Schedule 借鉴
1. **卡片化的信息层级**：大圆角卡片（24–28dp）承载「一组信息」，组内用细分隔行；页面背景克制，靠层级和留白而不是重色块。
2. **课表页的日期条 + 周切换**：顶部日期 chips 行（含今日高亮/周末弱化）+ 左右周切换；课程块只用一种浅色系，弱化噪声。
3. **课程详情 Bottom Sheet**：圆角顶部 + 拖拽把手 + 分组信息 + 主操作按钮（我们保留「写日程」）。
4. **动效「一镜到底」的原则**（不是抄实现）：同一元素在状态切换时保持位置连续性、用 spring/共享边界，而不是整页 fade；我们在页内用「同一课程块 → 详情弹层」「周切换跟手 → 弹性吸附」表达。
5. **设置页风格**：图标 + 标题/副标题 + 尾部动作的列表行，危险操作用确认对话框。

### 从 xiaoying 借鉴
1. **马卡龙课程块**：低饱和 pastel 底 + 深色文字，块内排版固定为「开始时间（加粗）→ 课程名 → 地点」；块间距均匀、圆角大（14–18dp）。
2. **日期头行**：两行（星期 + 日号），今日整列用柔和强调色块高亮，周末文字降透明度。
3. **顶部控制条**：左侧「第 N 周 ⌄」胶囊（点击可跳周）+ 右侧「切换视图」+「设置」入口；周/日视图用分段按钮而不是滑动。
4. **网格表达**：只保留虚线节次分隔 + 左侧极简节次/时间列；课程块自带时间，不重复大表头。
5. **按下/加载反馈**：课程块按下缩放；加载态用 opacity/骨架，而不是整页 spinner。
6. **我的页/设置页**：身份卡 → 数据指标 → 分组列表 → 危险操作用 error 语义。

### 明确不借鉴（避免返工/违反约束）
- HFUT 的**着色器/实时模糊/自定义全局转场体系**：会引入渲染复杂度与跨页面改动，且超出课表页写范围；我们已有 Haze 浮岛（Lead 维护）。
- HFUT 的**多业务聚合首页/查询中心**：信息密度与本产品定位不同。
- xiaoying 的**底部大导航**：本产品已有浮岛底栏（Lead 设计），课表页不重复。
- xiaoying 的**多课程表管理/导入导出文件体系**：Android 已有个人/公共课表与导出，不扩展。
- **任何参考项目的源码片段、图片、字体、图标素材**：只借鉴交互与视觉规律；图标全部使用 Material Icons，配色为自行生成的马卡龙色板（见 §4）。

---

## 2. HFUT-Schedule 拆解（以 `ui.jpg` 与 README 为准）

| 页面/模块 | 设计要点 | 可借鉴 | 不借鉴 |
|---|---|---|---|
| 首页 | 顶部状态区 + 大圆角卡片（电费/水费/天气/课表摘要）；卡片内信息分主次，数字大、标签小 | 卡片内「主数值 + 小标签」层级；同卡多行用 divider | 多入口聚合的查询中心密度 |
| 课表页（第二行第 2、3 图） | 日期 chips（选中日高亮）；课程块为一类深绿/浅绿描边卡片；顶部「‹ 第 6 周 ›」；底部导航 | 日期 chips、周切换位置、课程块层级 | 单一深绿主题（我们已有品牌雾青+马卡龙需求） |
| 课程详情 | Bottom Sheet：拖拽把手、课程名/时间/教室、操作按钮 | 弹层结构、把手、主操作按钮 | — |
| 查询中心 | 网格：图标 + 标题 + 副标 + 角标 | 分组网格的「图标+两行文字」结构（用于设置页） | 多业务信息密度 |
| 选项/设置 | 个人信息卡、常驻/应用设置分组；行内 icon + title + 状态色；深色外观开关 | 分组标题 + 行式列表 + 状态 pill | 过于细碎的多级设置 |
| 动效 | 自研全局转场「一镜到底」；着色器/实时模糊；加载时底栏由暗变亮 | 「保持元素连续性」原则；按下/加载反馈 | 全局转场体系（超范围）、着色器模糊 |

## 3. xiaoying 拆解（以 `xiaoying-timetable.webp` 与源码结构为准）

| 模块 | 设计要点 | 可借鉴 | 不借鉴 |
|---|---|---|---|
| 课表顶栏 | 左「第 2 周 ⌄」胶囊；右侧「切换视图」文字 + 粉色「设置」按钮 | 周次胶囊 + 设置入口 + 切换视图文案 | 粉色单一强调（我们使用品牌色+马卡龙） |
| 日期头 | 「一 9 / 二 10 / 三 11…」两行；今天（周四 12）整列浅粉高亮；周末文字更淡 | 两行日期头、今日整列高亮、周末弱化 | 无（结构适用） |
| 时间轴 | 左侧仅显示部分时间（9:00/10:35/13:30/15:05），虚线分隔；块内也显示开始时间 | 虚线网格 + 块内时间 | 左侧时间稀疏到难以定位（我们保留每节次号+开始时间，保证可定位） |
| 课程块 | pastel 底（粉/桃/黄/绿/蓝/紫轮换）、圆角大、块内「时间→课程名→楼栋房间」 | 马卡龙色板、块内排版、圆角 | 文字全部深色且固定（我们需浅/深两套对比达标色） |
| 日/周切换 | 分段按钮（切换视图） | 分段按钮放控制条 | 无 |
| 我的页 | 身份卡 + 指标 + 云同步/数据隐私/帮助分组 + 退出；行式列表 | 已用于 v5 我的页（task-39） | 无 |
| 设置页 | SliverAppBar large + ListTile（清缓存/擦除/关于）+ 危险操作确认 | 分组列表、危险确认 | Flutter 特有组件结构 |
| 动效/交互 | loading opacity 0.5 + 阻断；左右滑操作；长文本 marquee；进度条 | 课程块错峰入场、按压缩放、跟手滑动 | 文字 marquee（中文课名不适合横向滚动）；底部导航 |

## 4. v5 课表页改造清单（映射到代码）

### 4.1 视觉
| 项 | 设计 | 落地 |
|---|---|---|
| 课程配色 | **马卡龙 16 色**（浅色 pastel 底 + 深字；深色深底 + 浅字），同课程名稳定同色，对比度 ≥4.5:1 | `TimetablePalette.LIGHT/DARK` 重写（保持 API/16 色/散列索引不变）；单测对比度继续校验 |
| 日期头 | 两行：星期（小）+ 日号（大）；今日整列 `primaryContainer` 柔和块 + 主色文字；周末文字降 alpha；左上角月份标签 | `WeekGrid` 表头重做；`TimetableLayout` 保留自适应表头高（字号随 fontScale） |
| 周次控制 | 左「第 N 周 ⌄」胶囊（点击弹周选择）；两侧 ‹ ›；右侧导出/设置图标；第二行「切换视图」+ 周/日分段 + 「本周」 | 新 `WeekControlBar` + `WeekPickerSheet` + `TimetableSettingsSheet`；导出/登录/清除个人课表/刷新进设置弹层 |
| 课程块 | 大圆角（14–16dp）、块内「开始时间（粗）→ 课名 → 地点」；span≥2 才显示地点；深色用深底浅字 | `DayColumn` 重做；使用 metrics 字号/行数，大字体不截断关键信息 |
| 现在指示 | 虚线「现在 HH:mm」横线（仅本周且今天在可视列）；进行中课程块显示剩余进度条 + 描边 | 纯逻辑 `TimetableNowLogic`（可单测）+ 网格 overlay |
| 下一节摘要 | macaron 淡底条：时间 + 课程名 + 地点 + 倒计时/「上课中·剩余 X 分钟」 | 重做 `OngoingOrNextCard` → `NextSessionBar` |
| 详情弹层 | Bottom Sheet（把手 + spring 进入）：色块头 + 课程名 + 时间/地点 + 信息行 + 「写日程」 | 重做 `TimetableCourseDetailContent`（签名不变） |
| 状态页 | 加载：骨架块错峰呼吸；空：日历插画 + 刷新/登录；错误：四类文案 + 重试 | 重做 `LoadingState/EmptyState/ErrorState`（回调不变） |

### 4.2 动效
| 场景 | 效果 | 实现 |
|---|---|---|
| 周切换跟手 | 拖动 1:1、边缘阻尼 0.28、提交阈值 56px；松手**弹性吸附**；提交后 150ms 飞出 + 230ms 飞入 | 保留 `timetableSwipe`；非提交回弹从 tween 改为 `CtMotion.spatial` spring |
| 课程块按下 | 0.96 缩放（可中断） | `interactionSource.collectIsPressedAsState` + `animateFloatAsState(CtMotion.spatialFast)` + `graphicsLayer` |
| 首次/切周入场 | 课程块按位置错峰淡入 + 上移 6dp（24ms/块） | `Animatable` + `LaunchedEffect(week)`，key = 课程块；`TimetableLayout.entranceDelayMs(index)`（纯函数可测） |
| 详情弹层 | spring 进入 + 拖拽把手 | `CtBottomSheet`（ModalBottomSheet 内置 MotionScheme/把手） |
| 加载 | 骨架块 0.55↔1.0 呼吸 | `rememberInfiniteTransition`（仅加载态，页面不可见即无） |
| 选中/高亮 | 今日列/胶囊用颜色过渡 | `animateColorAsState(CtMotion.effects)`（少量） |

性能约束：课程块动画只改 `graphicsLayer`（不参与 composition 读取）；网格不滚动（7 列 fit 宽度），无嵌套滚动冲突；动效全部可中断。

### 4.3 布局自适应
- 手机：单列，顶部控制条 → 下一节条 → 周/日网格 → 全部课程（折叠）。
- 平板：**≥800dp 两栏**（本地判断 `spec.twoPane || widthDp >= 800`，不改 theme/adaptive 文件）；左 68% 周表/日视图，右 32% 下一节 + 全部课程，**导出卡固定在右栏底部**（保留既有能力）。
- 大字体 1.3x：`TimetableLayout.metricsFor(..., fontScale)` 放大行高/表头/标签列；课程名允许 2–3 行；日期头日号/周次胶囊允许 2 行；关键信息（时间/地点至少一行可见）。
- 空/错/加载：所有宽度下同构。

### 4.4 保留的既有功能（不得回归）
- 左右滑动：周视图切上一周/下一周，日视图切相邻一天（含边缘阻尼/提交/点击抑制）、上下周按钮、「本周」。
- 周/日视图切换（仅按钮）、日视图日期选择。
- 导出课表图片 `onExportTimetable(ExportCardSpec)`（顶部图标 + 平板固定导出卡 + 设置弹层）。
- 登录教务 `loginRepository/sessionSid`、个人课表提示与清除、公共/个人/缓存来源标识。
- 平板右栏固定导出卡；周表周末空列隐藏；今日高亮；全部课程折叠卡。
- 空/加载/错误四态；课程详情「写日程」入口与开学日未知降级文案；大字体 1.3x。

### 4.5 测试与预览
- 纯逻辑新增：马卡龙色板对比度（保留）、`TimetableNowLogic.indicator/progress`、`TimetableLayout.entranceDelayMs`、`twoPaneFor(800)`；既有 16 例保留。
- 预览 ≥12 张（更新 `V3TimetablePreviews.kt`）：浅/深周视图、日视图浅/深、课程详情、平板双栏、大字体 1.3x、滑动中（下周切换中）、周末 7 列、空态、网络错误、16 色马卡龙板、小屏 360、进行中/现在指示。
- 编译：`gradle :app:compileDebugKotlin --no-build-cache` 通过；改动纯逻辑后跑 `:app:testDebugUnitTest --tests "...ui.screens.timetable.*"`。

---

## 5. 与现有设计体系的映射

| 参考设计元素 | 本项目 token/组件 |
|---|---|
| 大圆角卡片/弹层 | `CtShapeTokens.hero`(32)/`toolCard`(16)/`sheet`(30)；课程块圆角 14–16dp（`TimetableMetrics.blockCornerDp`） |
| 马卡龙课程色 | `TimetablePalette.LIGHT/DARK`（16 色，按课程名 FNV-1a 稳定索引；浅色对比 5.3–7.5，深色 6.4–9.1） |
| 品牌/强调 | `MaterialTheme.colorScheme.primary/secondary/tertiary`、`CtBrandColors`（不改 theme） |
| 分段/胶囊 | `CtSegmentedButtons`、`CtPill`/Surface 胶囊 |
| 动效 | `CtMotion.spatial/spatialFast/effects`（spring），不新增动效依赖 |
| 弹层 | `CtBottomSheet`（ModalBottomSheet + drag handle） |
| 状态 | `ExpressiveBackground` + Surface 卡片 |
| 底部导航 | 已由 Lead 的浮岛底栏承载，课表页不新增 |

## 6. 参考来源与合规说明
- 本文仅记录对两个开源项目**公开截图/README/可读源码结构**的设计分析；不复制其源代码、图片、图标、字体或资源文件。
- 课表页实现全部为本项目自有代码：色板数值由本任务生成并用 WCAG 对比度脚本校验；图标使用 Material Icons；动效使用 Compose 自带 spring/`CtMotion`；无新增第三方依赖。
- HFUT-Schedule 采用 Kotlin/Compose + Haze 等；我们仅借鉴其 Material3 层级与转场「连续性」原则，未引入其着色器/导航体系。
- xiaoying 采用 Flutter；我们仅借鉴马卡龙配色、日期头与课程块排版，未使用其 Flutter 组件或 logo 素材。
