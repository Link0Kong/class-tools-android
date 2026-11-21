# M3 组件替换与实机核验（2026-11-21 · beta26）

> 依据：https://m3.material.io/ 组件规范（Top app bar / Cards / Navigation bar / Dialogs / Snackbar / Bottom sheet / Buttons / Text fields / Chips），
> 以及本机知识库 `_refs/Android-MD3-Knowledge`（核验 2026-11-16）。
> 测试身份：官方测试账号 **3260799999 / 测试账号**（真实 `class_verify` 登录，非 Debug 绕过）。

## 1. 本轮替换

| 区域 | 旧实现 | 新实现（M3） |
|---|---|---|
| 工具页顶栏 | 通栏半透明玻璃 + 方形材质层 | 悬浮 56dp `surfaceContainer` 圆角条（28dp），无方角/无透字 |
| 工作台吸顶栏 | 通栏渐变底 + 玻璃药丸 | 实心 `surfaceContainer` 56dp 圆角条，去掉 `matchParentSize` 方形渐变 |
| 内容卡片 | 半透明磨砂玻璃（0.45–0.7） | **filled card**：`surfaceContainerHigh` 实色、12dp 圆角、无模糊 |
| 底部导航 | 玻璃浮岛 + 反光高光 | `surfaceContainer` 实心浮岛 + 64×32 `secondaryContainer` 指示器、24dp 图标、Label Medium |
| 对话框 | 光感半透明容器、22sp 标题 | `surfaceContainerHigh` 不透明、28dp 圆角、Headline Small 标题 |
| 底部弹层 | 0.94 半透明（会透字） | `surfaceContainerLow` 不透明 |
| Snackbar | 各页默认 Host + 玻璃外框 | 统一 `CtSnackbarHost`：inverseSurface / inverseOnSurface / inversePrimary，4dp 圆角 |
| 根页面顶部渐隐 | 固定 96dp，首屏标题永远半透明 | 按滚动量 0→96dp 展开；回到顶部完全不淡 |
| 状态栏 | 系统对比度蒙层导致方形底 | `isStatusBarContrastEnforced=false` + 透明状态栏 |

## 2. 逐项修复对应

1. **“显示状态栏时顶部悬浮条四角方形边框”**：根因有两个——
   - 系统状态栏对比度蒙层（通栏方角）：`ClassToolsWindow` 关闭 status bar contrast；
   - 吸顶栏背后的 `matchParentSize` 矩形渐变：删除，改为实心 Surface 圆角条。
2. **“提示框 / 应用内通知”**：
   - 对话框、底部弹层改为不透明 M3 容器；
   - 新增 `CtSnackbarHost` 并接入 14 个页面，统一反色 Snackbar；
   - 状态条 / 错误条沿用 `surfaceContainer` / `errorContainer` 语义色。
3. **“滑到最顶文字仍渐隐”**：`ctRootPageEdgeFade` 改为 `scrollPx` 参数，0 滚动时顶部遮罩为 0，滚过 96dp 才满强度。
4. **M3 设计语言替换**：顶栏、卡片、底栏、对话框、弹层、Snackbar 六类核心容器全部换成 M3 surface 角色；
   字阶/形状/间距 token 已在 beta23–beta25 对齐。

## 3. 实机核验

- 测试账号真实登录：工作台显示「测试账号」、第 12 周课表、11 条通知、1 条作业、23号楼B座 423 电费 227 度；
- 状态栏显示/隐藏两种形态均检查（1:2x 状态栏图标、悬浮条圆角、无方角）；
- 工作台滚动到顶 / 滚到中部 / 工具页 / 课表页截图核对；
- 深色模式与 200% 字体另测（见 `shots/`）。

## 4. 仍未替换（如实记录）

- 底栏仍是悬浮 64dp 形态（M3 Expressive floating toolbar），不是标准 80dp pinned NavigationBar；
- 部分页面仍有系统 Toast（账号快捷登录失败、首页快捷上限等），未全部换成 Snackbar；
- 工作台 header 的两个图标动作仍是玻璃圆钮，未换成 `FilledTonalIconButton`；
- 名单/点名等少数空态仍是裸 `EmptyState`；详情页数据相关壳层待用测试账号继续逐页跑。
- `WaterScreen` 的本轮 Snackbar 替换暂未提交（与并行开发的电费/水量云端目录改动同文件，避免覆盖）。
