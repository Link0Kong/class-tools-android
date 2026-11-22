# M3 模拟器逐页审计（2026-11-22）

测试环境：Pixel 1080×2400 @2.62x，Android 模拟器；测试账号 **3260799999 / 测试账号**；
浅色 + 深色，fontScale 1.0 / 2.0，手机 412dp 与平板 800dp 两档宽度。

## 已修复（beta37）

### 共享控件

- `CtToolSegmented`：鸿蒙白药丸 → M3 `SingleChoiceSegmentedButtonRow` + `SegmentedButton`（40dp、secondaryContainer、无缩放）。
- `CtToolField` / `CtTextField`：14–16dp 无边框 → M3 filled TextField（4dp 顶圆角、surfaceContainerHighest、底部指示线）；
  移除 Material3 TextField 默认 280dp 最小宽，修复两列表单溢出。
- `CtToolChip` / `CtChipGroup`：白药丸/缩放 → M3 FilterChip（8dp、无 scale）。
- `CtToolPrimaryButton`：48dp bodyMedium → 40dp labelLarge（保留 48dp 触控目标）。
- `EmptyState`：40dp 图标 + 14sp 标题 → 48dp + titleMedium/bodyMedium。
- 工具占位页：88dp 彩色大方块 → 56dp 圆形容器 + 28dp 图标；计划卡 12dp。
- 课表接入 FAB：pill/primary → M3 extended FAB（16dp、primaryContainer、24dp 图标）。

### 页面

- 首页 Hero：三色渐变条/阴影/边框/纸面 → M3 filled card（16dp、surfaceContainerHigh、headlineSmall）。
- 首页快捷工具：8 族强调色 → secondaryContainer/onSecondaryContainer，labelMedium。
- 全部工具：卡片图标统一 secondaryContainer；标题 titleMedium、副标题 bodyMedium；
  网格高度随 fontScale 增长（200% 不再裁字）。
- 我的：SectionTitle → labelLarge；通知权限横幅改为单行 icon + 文本 + 文字按钮 + 关闭；
  200% 字号下自动换行不重叠。
- 身份核验：OutlinedTextField 无边框变体 → M3 filled TextField；按钮 40dp/labelLarge；卡片 12dp。
- 体测：两列输入框不再溢出；分段按钮 M3。
- 抽签/分组/体测/电费/名单/点名：分段/筛选控件随共享组件升级。

### 自适应

- ≥600dp 使用 **M3 NavigationRail**（左侧 80dp + 全高 surfaceContainer），隐藏底部 NavigationBar；
- 平板 800dp 实测：全部工具 3 列、Rail 可切换工作台/工具/我的；
- 200% 字号：底部栏高度随文案增长、工具卡高度增长、权限横幅不重叠；
- 动态色开启/关闭均能正常渲染。

## beta39 追加

- 作业「全部/未完成/已完成」、名册「班委/团员/全班」：白药丸 → M3 PrimaryTabRow + 下划线指示器。
- Profile 行标题/副标题 → bodyLarge/bodyMedium，最小行高 56dp。
- PPT 空态卡片改为占满内容宽。

## beta40 追加

- 主按钮改用 `heightIn(min=40.dp)`，200% 字号下按钮随文案长高，不再裁字。
- SegmentedButton 显式指定选中/未选中文字色，深色下对比稳定。

## beta41 追加

- 工具页顶栏副标题（缓存周次/地图来源等）改用 onSurfaceVariant，深色下不再黑字。
- 抽签「上一步」、身份页快捷登录按钮改 heightIn(min=40dp)，大字号随文案长高。

## beta42 追加

- 通知页 200%：隐藏总数胶囊、标题降为 titleMedium，避免「班级…」截断；统计卡/筛选仍完整。
- 工具页副标题与身份/抽签按钮的大字号修复归入 beta41。

## beta43 追加

- `surfaceContainerLow` 改不透明（此前 0xE0FFFFFF 会让 Sheet/Dialog 透底，形成白色闪层）。
- 移除「我的」ActionRow 的自定义白色容器遮罩；页面跳转回归 NavHost 自身转场，不再闪白。
- `CtStickyHeaderBar` 用 surface 覆盖状态栏 + 64dp 标题栏，滚动后顶部完整通栏。

## beta44 课表逻辑与网页端对齐

- 最大周次：`max(课程周, 当前周, 18)`，上限 30（原漏当前周）。
- 开学日：课程 `t` 优先，`meta.termStart` 仅兜底（原 meta 优先）。
- 周次解析支持 `1~16周` / `1—16` / `1-16周`。
- 周视图列：周一~周五固定，周六/周日仅当该周有课才显示（同 `ttVisibleDays`）。
- 节次数：至少 10、最多 12（同网页 `maxP`，TtPeriods 上限）。
- 200% 字号：日期头高度放大到 2×，节次+时间合并一行，避免裁字。

## beta45 工具/首页/状态栏

- 首页常用工具：可直接长按左右拖动排序，松手写 userdata；编辑保存先本地乐观更新，避免「点完成没反应」。
- 全部工具：普通态长按工具卡自动进入编辑态，再拖动排序，无需先点右上角配置；副标题同步说明。
- 工具图标：按网页 Monet 色板（mint/blue/sand/rose/violet/neutral）取色，深色用同色相压暗版。
- 状态栏：窗口重新获得焦点时补隐藏 + 1.2s 低频巡检，修复 WebView/播放器/弹窗退出后状态栏复现。

## 仍需继续

- 平板「我的」仍是单列 720dp，未做 M3 双栏；
- 名册/作业/通知的分类条仍是 FilterChip 形态，是否改 PrimaryTabRow 待定；
- PPT 空态卡片宽度偏窄；地图页底部浮层控件未 M3 化；
- 部分工具页仍有自定义图表/地图控件，不属于 Material 组件。







