# design/ —— Android 17 设计语言与页面方案（索引）

| 文件 | 内容 | 面向 |
|---|---|---|
| [`M3_STANDARD_2026-11-21.md`](M3_STANDARD_2026-11-21.md) | beta28：M3 标准 app bar、课表一屏周视图、通知缓存修复、沉浸光感移除、远程配置热更 | 设计/评审 |
| [`M3_REPLACEMENT_2026-11-21.md`](M3_REPLACEMENT_2026-11-21.md) | M3 组件替换：顶栏/卡片/底栏/对话框/弹层/Snackbar 的角色与实机核验 | 设计/评审 |
| [`MD3_PAGE_AUDIT_2026-11-21.md`](MD3_PAGE_AUDIT_2026-11-21.md) | 18 个工具入口与可达子页的逐页截图核验：结果表、统一修复项、仍存在的产品级问题 | 设计/评审 |
| [`MD3_ALIGNMENT_2026-11-21.md`](MD3_ALIGNMENT_2026-11-21.md) | 按 MD3 官方知识库的对齐报告：字阶 / 形状 / 间距 / 底栏的前后对照、保留的品牌决策、未完成待办 | 设计/评审 |
| [`ANDROID17_DESIGN.md`](ANDROID17_DESIGN.md) | 设计原则、色彩体系（明/暗全量 token + 动态取色三层策略 + 对比度审计）、Roboto Flex/中文回退与 30 个类型样式、形状 scale 与变形规则、spring 动效数值与场景映射、25+ 组件规范、Android 17 适配（target 37 大屏/方向、edge-to-edge、预测返回、窗口尺寸类、模糊性能）、无障碍、QA 清单、来源与核实状态 | 设计/开发/评审 |
| [`tokens.json`](tokens.json) | 机器可读 token：`color.light/dark`（字段名 = Compose `ColorScheme` 参数）、`color.brand/glass`、`color.tonalPalettes`（HCT 全色阶）、`typography`（15 baseline + 15 emphasized，字段名 = `Typography` 参数）、`shape`、`spacing`、`motion`（spring/时长/缓动/场景）、elevation、windowSizeClasses、accessibility | 开发（可直接生成主题） |
| [`COMPONENTS.md`](COMPONENTS.md) | 组件 → Compose API / material3 版本对应表（45 行）、1.4.0 stable 与 1.5.0-alpha29 双轨依赖方案、实验 API 清单与降级方案、BOM 约束、版本坑与升级规则 | 开发/技术选型 |
| [`SCREENS.md`](SCREENS.md) | 信息架构与自适应策略、16 个页面/流程的原生方案（布局 ASCII、状态、动效、横屏/平板、空态/加载/错误）、原生 vs WebView 兜底建议、导出卡 v2 Android 实现、Mermaid 流程图、页面验收清单 | 开发/评审 |

---

## 一句话结论

**用「品牌三色 HCT tonal palette + M3 标准 role→tone 映射」构建明暗两套 ColorScheme（主色 `#24695C` 承载白字，品牌 `#4E9082` 仅用于大字号/图形），配合 material3 1.4.0 stable 的 `MotionScheme`（自建 expressive spring 0.8/380 等）+ 15 个 emphasized 字型还原 M3 Expressive 观感；Expressive 独有组件（FloatingToolbar/ButtonGroup/SplitButton/LoadingIndicator/FABMenu/MaterialShapes）在 1.5.0-alpha29 才可用，建议「stable 轨为主 + expressive 层隔离」双轨推进。**

---

## 需要 Lead / 产品确认的 5 件事

1. **material3 版本事实修正（影响 task-1 工具链）**：
   - 最新 **stable = 1.4.0（2025-09-24）**；BOM **2026.09.00 → material3 1.4.0**（已核实）。
   - **1.4.0 不包含 M3 Expressive 公共组件**：`1.4.0-beta01` 起所有 `ExperimentalMaterial3ExpressiveApi` 公共 API 被移除、转到 1.5.0-alpha 线（ButtonGroup/SplitButton/FloatingToolbar/LoadingIndicator/FABMenu/ToggleButton/WavyProgress/MaterialShapes/Flexible app bar 均不在 1.4.0）。
   - 最新 **1.5.0-alpha29（2026-09-23）**，**不存在 1.5.0-rc01**（截至 2026-09 发布页）。`material3-adaptive-navigation-suite:1.5.0-beta01` 搭配 1.4.0 使用。
   - 1.4.0 的 `MotionScheme.Companion.expressive()` 是 `internal`，App 内需自建 spring 实现（`COMPONENTS.md §1.2` 已给代码）。
   - ⇒ 建议工具链冒烟工程先用 **BOM 2026.09.00 + material3 1.4.0**；若需要 Expressive 原生组件，另加一个 `-Pexpressive` 变体测 1.5.0-alpha29。
2. **导航形态**：web 红线「没有底部导航栏」只约束网页/小程序。Android 端建议 M3 自适应导航（compact 底部短导航 / medium 侧栏 / expanded 宽侧栏，3 目的地：工作台/工具/我的，对齐小程序 IA）。若产品要求 1:1 复刻 web 顶部工具条，`SCREENS.md §2.3 方案 B` 为备选。
3. **动态取色**：默认走品牌固定方案（跨端一致、QA 可复现）；「跟随壁纸」作为设置项（Android 12+），默认关闭。若产品要「Material You 动态色」做默认，需接受品牌识别弱化。
4. **通知详情**：列表/摘要/状态原生；富文本详情「原生优先、WebView 兜底」（禁 JS、禁外链、失败回退纯文本）。若后端契约能保证结构化的分点/附件 JSON，可完全原生。
5. **最低版本 / 分发**：minSdk 26 可覆盖老设备（模糊仅 API 31+ 生效，自动降级）；若上架 Play 建议只走 stable 轨（1.4.0）。

---

## 与已核实事实的差异说明（重要）
| 任务描述中的说法 | 核实结果 | 依据 |
|---|---|---|
| material3 1.4.0 中 M3 Expressive API 多为 `ExperimentalMaterial3ExpressiveApi` | ❌ 1.4.0 **没有**这些公共 API（beta01 起移除）；实验 Expressive 组件在 1.5.0-alpha 线，且 alpha29 中多数已去实验注解（LoadingIndicator/MaterialShapes 仍实验） | Compose Material3 发布页 + 1.4.0/1.5.0-alpha29 sources jar |
| 1.5.0-rc01 可用 | ❌ 最新为 1.5.0-alpha29（2026-09-23）；adaptive-navigation-suite 最新 1.5.0-beta01 | 同上 |
| 1.4.0 是否含 `MotionScheme.expressive()` | 接口公开但 `expressive()` 为 internal，需自建 | 1.4.0 sources |
| Android 17 / API 37 / target 37 大屏忽略方向与尺寸限制 | ✅ 与官方博客一致（2026-02-13；自 Android 16 起无新增变更，只禁止退出） | developer.android.google.cn blog |

（其余「已核实」事实与来源逐条列在 `ANDROID17_DESIGN.md §10`。）



