# Round 9 报告 · v3.1.0（2026-11-08 深夜）

> 在 v3.0.0（用户 5 点反馈第一轮）基础上的**全局视觉 P1 精修**：工作台/我的/全部工具玻璃化 + 品牌三圆点 + 背景光晕 + 底栏 pill 高光。版本 **v3.1.0**（versionCode 7），功能不变。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 玻璃卡体系 | 新增 `ui/components/glass/CtGlassCard.kt`（Lead 接线层）：单层半透明 surfaceContainerLow（浅 96% / 深 86%）+ 1dp outlineVariant 描边 + 可选点击；**不使用阴影**（模拟器 GPU 对「半透明 Surface + shadowElevation」渲染出白块伪影，已定位并规避）。全部工具页头用 28dp 圆角玻璃卡。 |
| 工作台 | 今日课程 / 宿舍电费 / 作业待交 / 通知待处理 / 空态卡全部换玻璃卡；保留原信息密度与对比度。 |
| 我的 | 账号卡、同步/权限与提醒/数据与隐私/外观/关于 卡组换玻璃卡；平板双栏保持。 |
| 全部工具 | 页头（标题+提示）玻璃卡化；工具卡保持平面 + 品牌 accent 图标（按规范 P1）。 |
| 品牌识别 | 新增 `CtBrandDots`（灰蓝/雾青/暖沙三圆点），加入「常用工具」「全部工具」页头；`ExpressiveBackground` 品牌光晕加强（primary 0.22 / tertiary 0.16 / secondary 0.12）。 |
| 底栏 | 选中 pill 增加 1dp surfaceBright 高光描边 + 6dp 悬浮阴影，配合弹性药丸/跟手动画。 |
| 深色 | 玻璃卡自动切深色 token（fill 86%、描边 35%），实测深色工作台不发灰、文字对比度正常。 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 单测 | **779 tests / 0 failures** |
| Debug 构建 | BUILD SUCCESSFUL；真机/模拟器浅色工作台/工具/我的、深色工作台截图（`artifacts/v31*.png`） |
| Release | APK **2,577,485 B** / SHA-256 `AF97EC07DA78BE0AC87AFB4D86843F7B1EAA5B14BCA2BE61D263CEBAECCD4A8C` / v3.1.0 vc7；apksigner V2 通过；冷启动 1.16s、logcat 0 FATAL/ANR |
| 待补 | toolchain task-31：快速 E2E 38 项回归 + Release 复验 + 文档 §12/§11 |

## 3. 设计取舍 / 遗留

- **真实背景模糊**：Haze 2.x/1.7 在 HorizontalPager 图层取样失败并 fallback 发灰（v3.0.0 已试）；本轮玻璃卡采用「半透明 + 描边」的稳定方案，视觉接近 ColorOS 毛玻璃但无实时模糊；真实模糊留待后续用单图层取样/RenderEffect 方案继续攻。
- **阴影**：模拟器上 translucency + shadowElevation 出现白块伪影，故玻璃卡不用阴影，改用描边定义边界；真机需复核观感（若真机正常可加回轻阴影）。
- 剩余 P2：通知/权限/提醒页浅玻璃、抽签/分组/体测/水量/名册页头玻璃化、动效 token 统一；作业单条提醒入口。
- 用户第 6 点仍为空，待补。

## 4. 文档

- 代码：`ui/components/glass/CtGlassCard.kt`、`CtBrandDots`（BrandMark.kt）、Home/Profile/AllTools 接线。
- toolchain 追加：`docs/E2E_SMOKE.md §12`、`docs/RELEASE_BUILD.md §11`。
