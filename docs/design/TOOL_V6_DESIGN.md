# 工具内部 v6 设计语言 + 背景修复（2026-11-13）

> 用户反馈：「工具内部的界面不满意，背景也有问题」。本轮针对两点做系统性修复：
> 1) **背景**：旧 `ExpressiveBackground` 三团高 alpha 径向色块在浅色页形成明显色斑/脏色，深色发灰；
> 2) **工具内部**：各工具页仍沿用旧卡片/描边输入框/重复页内大标题，与 v5 外壳（工作台/课表/通知/我的）割裂。
>
> 参考基准仍是 HFUT-Schedule（大圆角卡片层级、连续性）与 xiaoying（柔和卡片/胶囊/分段），**只借鉴视觉规律，不复制源码/素材**。

## 1. 背景 v6（全局唯一入口 `ExpressiveBackground`）

旧版：`background + primary(0.22) + tertiary(0.16) + secondary(0.12)` 三团巨大径向色块 → 白卡之间露出明显绿/沙色斑，8bit 渐变有轻微色带；深色模式色相偏灰。

v6：
- **浅色**：`surfaceContainerLowest(0.9)` → `background` 的极轻纵向渐变；顶部仅一团 primary **≤6.5%** 柔光，右下 tertiary **≤3.5%**；
- **深色**：近黑中性底 + primary **≤10%**、tertiary **≤5%** 柔光，不发灰；
- 首版曾叠加 64×64 噪点纹理消色带；实测模拟器每帧 tiled shader 增加渲染负担、导航探针偶发超时，最终移除（渐变 alpha 已压到不产生可见色带），背景仅 3 个静态 rect，零额外开销；
- 不依赖 RenderEffect/模糊 API，低端机稳定；所有页面自动生效（工具页/根页/身份页/设置页）。

## 2. 顶栏 v6（`CtTopBar`）

- 去掉旧版整条 `surface 62%` 半透明底带；
- 返回：44dp 圆形 `surfaceContainerHigh` 玻璃按钮 + 21dp 箭头（contentDescription 仍为「返回」）；
- 标题：`headlineSmall` Bold（大标题），副标题 `bodySmall`；动作按钮由各页传入（通常 44dp 圆形）；
- 平板 ≥840dp 收紧 padding；不改变滚动行为与读屏 heading 语义。

## 3. 工具页通用组件（`ui/components/tool/CtToolComponents.kt`）

| 组件 | 规范 | 用途 |
|---|---|---|
| `CtToolCard` | 24dp 圆角、surfaceContainerLow、outlineVariant 28% 软描边、18dp 内边距；可选 title/subtitle/trailing | 所有工具内容卡 |
| `CtToolSectionHeader` | titleSmall SemiBold + 可选副标题 + 右侧动作，heading 语义 | 分组标题 |
| `CtToolPrimaryButton` | 52dp 胶囊、品牌 primary/onPrimary、titleSmall SemiBold | 主操作（下一步/开始抽签/保存/重试） |
| `CtToolSegmented` | pill 底 + 选中 surface 浮层，44dp 视觉（M3 补足 48dp 触控） | 步骤条/周-日/性别/年级/分类 |
| `CtToolChip` | 40dp pill、选中 primaryContainer | 筛选/候选来源/排序 |
| `CtToolField` | 无描边填充式、surfaceContainerHigh、18dp 圆角、上置 label、聚焦 primary 55% 描边 | 所有输入 |
| `CtToolInfoRow` | label + value 行，min 44dp | 卡片内信息 |
| `CtToolStat` | 大数字 + 标签、surfaceContainerHigh 55% 圆角块 | 摘要统计 |

## 4. 逐工具迁移

| 工具 | 主要改动 |
|---|---|
| 抽签 | 去掉页内「设置抽签参数」大标题；步骤条 CtToolSegmented、参数卡 CtToolCard+字段 CtToolField、候选来源/排序 CtToolChip、结果/候选卡、底部主按钮；校验/状态机/导出/名单/剔除重抽不变 |
| 分组 | 步骤条、配置卡（成员来源/分组方式/组数步进）、主按钮、结果两列组卡；分组算法/导出/名单不变 |
| 体测 | 录入卡（性别/年级分段 + 8 项 CtToolField）、实时评分大数字卡、历史卡、操作行；评分算法/评分口径/历史/导出不变 |
| 作业 | 顶部 3 统计 + 统一筛选 pill + 按截止分组卡片；完成勾选/撤销/提醒/详情/四态不变 |
| 电费 | 关注宿舍卡、余额 hero（环形仪表 + 大数字 + 日更/状态胶囊）、趋势卡（CtToolSegmented 24h/7d/30d + 固定右上气泡）、4 指标 tile + 同期胶囊、计费参考；13/16 隐藏 24h/0 请求/慢更/估算/隐私逻辑不变 |
| 水量 | 房间/月份选择、抄表表单 CtToolField、统计 CtToolStat、历史/计费规则卡、双栏；覆盖确认/差异确认/删除撤销/同步不变 |
| 通知 | 待处理简报卡 + 统计、筛选 CtToolChip、搜索 v6 字段、通知卡 CtToolCard 外壳（保留按压/延伸/错峰/reduceMotion）、已过期分组；置顶/已处理/详情/附件不变 |
| 名单 | 页签+三分类 CtToolSegmented（计数）、搜索字段、成员行卡片、点名统计/气泡；点名/全选/沿用名单/同步不变 |
| 投票 | 列表/详情/发起全部 CtToolCard + CtToolField + CtToolSegmented/Chip；范围/匿名/改票/管理/配图/倒计时不变 |
| 演示 PPT | 列表卡/上传进度/空错误态/下载详情三行文件/媒体分组全部卡统一；上传/下载/打开/分享/软删/重试不变；播放器页不动 |
| 诊断 | 摘要/设备/服务/AI/其它分组 CtToolSectionHeader + CtToolCard；折叠/重试/上报/暂停/简洁模式不变 |
| 课表 | 已是 v5；仅受新背景/新顶栏统一影响，逻辑不变 |

## 5. 验证方式

- `:app:compileDebugKotlin --no-build-cache`、`:app:testDebugUnitTest --no-build-cache`（1104/0）；
- 快速 E2E 44 步 0 FAIL（导航 10/10，logcat 0 FATAL/ANR）；
- 设备截图：`artifacts/tool-v6/`（浅色 9 页 + 补拍）、`artifacts/e2e/`（E2E 全程）；深色/大字体/平板预览由各任务预览文件覆盖（ToolV6Core/Life/Class/MediaPreviews）。
- Release APK 冷启动/logcat/apksigner 见 `docs/REPORT_ROUND56_20261113.md`。

## 6. 已知边界

- 工具页内个别复杂嵌入区（如投票配图、诊断 AI flow）仍保留原交互形态，仅统一外壳；下一轮可按需继续细化。
- 演示/水量/名单/诊断的设备长截图部分依赖测试数据，空态页面为当前实际观感。
- 背景噪点为静态 SVG 式位图叠加，无性能开销；如个别 OLED 设备觉得颗粒可见，可在 `ExpressiveBackground` 调 alpha。
