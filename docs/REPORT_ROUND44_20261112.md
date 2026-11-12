# Round 44 报告 · v4.33.0（2026-11-12）

> v4 深度优化 视觉：底栏真实背景模糊（Haze 磨砂玻璃），对齐研究文档「ColorOS 光场/液态玻璃」目标。版本 **v4.33.0**（versionCode 49）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 真实背景模糊 | 底部悬浮岛（CtFloatingDock）接入 **Haze 1.7.3**：`RootTabs` 创建 `HazeState`，`CtTabPager` 加 `Modifier.hazeSource(state)`，dock 的 `glassModifier = Modifier.hazeEffect(state, HazeDefaults.style(backgroundColor = surface 55%, blurRadius = 24.dp, noiseFactor = 0.02f))`；dock Surface 背景透明，由模糊层显示真实背景采样 |
| 兼容/降级 | Haze 对不支持 RenderEffect 的旧设备回退为半透明着色（不崩溃）；原有高光描边、阴影、镜面高光、弹性药丸全部保留 |
| 依赖 | 启用已在版本目录中的 `dev.chrisbanes.haze` 1.7.3（app 新增 `implementation(libs.haze)`）；APK +39KB |

## 2. 历史背景（本轮闭环）

v3.3 曾尝试 Haze 1.7.3 失败（「运行时 blur fallback 发灰、底栏变暗」），当时问题在于未按 1.x API 正确配置取样源与 style；本轮按 `hazeSource`（pager）+ `hazeEffect`（dock，带 24dp/55% 表面色/噪点 style）重新接线后，**模拟器实测 dock 区域可采样到下方卡片色（左绿右沙）并模糊，文字不再透出**，视觉为磨砂玻璃。

## 3. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 设备实测 | 截图 `artifacts/haze-style-dock.png`：dock 后方内容模糊透出（左 体测-绿 / 右 作业-沙），标签可读 |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1071.7s）**；dock_overlay 步骤通过、导航 10/10、深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.33.0 | **3,026,681 B** / SHA-256 `9DB65C96033DF718B0154ECB0E4D408549055A32E05A8CEF1B61403CB96F32A4`（vc49）；冷启动 0.92s / 0.63s；logcat 0 FATAL/ANR |

## 4. 已知边界

- 模糊仅作用于底部 dock；页面卡片仍用既有半透明+渐变（全页面玻璃化涉及性能，后续按需评估）。
- 模拟器为 swiftshader 软件渲染，真机 RenderEffect 模糊通常更明显；旧设备回退半透明。
- TalkBack 真机走查仍待做。
