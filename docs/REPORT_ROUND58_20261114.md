# Round 58 报告 · v4.47.1（全部工具卡片尺寸对齐微信小程序）

> 用户要求：「工具卡片大小和微信小程序端一样即可」。
> 本轮把「全部工具」两列卡片从 v5 的大卡（148dp、图标气泡+分类标签+两行文字）改成与小程序 `pages/all` 的 `.tool-cell__in` 同尺寸/同结构。

## 尺寸对照（小程序 → Android）

| 项 | 小程序 `pages/all` | Android v4.47.1 |
|---|---|---|
| 卡片结构 | 横向：图标 chip + 名称/副标题两行 | 一致（Row：40dp chip + 两行文本） |
| 卡高 | `min-height: 132rpx` ≈ **72dp** | `heightIn(min = 72.dp)`，网格固定 **76dp** |
| 图标 chip | `72rpx` ≈ **39.4dp**，圆角圆底 | **40dp**，圆角 12dp，accent 17% 底 |
| 名称 | `28rpx` ≈ 15.3sp / Medium | **15sp / Medium** |
| 副标题 | `24rpx` ≈ 13.1sp，单行省略 | **12sp**，单行省略 |
| 卡片宽度 | `48.6%`，两列 | 两列均分（411dp 屏 ≈ 184dp/列） |
| 卡间距 | `margin-bottom: 16rpx` ≈ 8.8dp | **10dp** |
| 圆角/内边距 | `--md-shape-lg` / `20rpx`≈11dp | 16dp / 12dp·10dp |

## 实现

- `ui/screens/all/AllToolCard.kt` 重写为紧凑横向卡：删除分类标签、图标渐变装饰、竖排大标题；保留按压 spring 缩放（0.96→0.96/0.96）、`ctToolTransitionSource` 转场锚点、`contentSemantics` 拖动/读屏语义。
- `ui/screens/all/AllToolsScreen.kt`：网格 `itemHeightDp 148→76`、`spacingDp 12→10`；删除页脚「全部工具均为原生页面 · 数据随账号同步」说明；拖动落位几何、自动滚动、长按 350ms、点击抑制、TalkBack 上移/下移全部沿用同一 itemHeight/spacing 常量，行为不变；拖动幽灵同步变为紧凑卡。
- 一屏即可看到全部 13–14 个工具（411dp 手机），无需先滚动。

## 验证

- `:app:compileDebugKotlin --no-build-cache` 通过；全量单测 **1135 / 0 failures / 0 errors**。
- 设备截图：`artifacts/alltools-compact-light.png`、`artifacts/alltools-compact-dark.png`（浅/深色，2 列紧凑卡）。
- 快速 E2E：**44 步 PASS / 0 WARN / 0 FAIL（1045.5s）**，导航探针 **10/10**，logcat 0 FATAL/ANR，深色组/浅色恢复通过。
- Release v4.47.1：**3,141,833 B** / SHA-256 `626670C070706E1922B9EF59FE85B817671600772B37ED079DE4881F8EB90DAA`（vc65）；
  `pm clear` 后 651ms / 306ms，稳定 **328 / 303 / 321ms**；logcat 0 FATAL/ANR；versionName=4.47.1。

## 边界

- 全部工具页头仍保留品牌渐变卡（小程序为普通标题行）；用户本轮只要求卡尺寸，页头未动。
- 长按拖动排序功能与落位算法不变；E2E 不含拖动用例，已按同一几何常量复核。
