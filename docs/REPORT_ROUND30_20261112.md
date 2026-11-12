# Round 30 报告 · v4.19.0（2026-11-12）

> v4 深度优化 P2：工作台电费卡一键关注（H-04）。版本 **v4.19.0**（versionCode 35）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| H-04 | 工作台「宿舍电费」卡未关注宿舍时，点击 → 路由 `tool/power?pick=true` → `PowerScreen(initialPickerOpen=true)` 首帧直接弹出「选择关注宿舍」选择器；已关注时仍进入电费仪表盘。工具页/其他入口的 power 路由不携带 pick 参数，不会误弹 |
| 实现细节 | `Routes.TOOL` 增加可选 query 参数 `pick`（默认 false）；`RootTabs`/`HomeScreen` 增加 `onOpenPowerPicker` 回调；`PowerScreen` 新增 `initialPickerOpen`，仅在 `IDLE && sel.isEmpty()` 时弹选择器 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 设备实测 | Debug 工作台点击「关注宿舍后显示剩余电量」卡：直接出现选择器（楼栋 13/16/23A/23B + 房间列表），截图 `artifacts/v4190-power-auto-picker.png` |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（980.8s）**；power 工具页步骤不受影响；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.19.0 | **2,856,126 B** / SHA-256 `74A23B5DC079FB415AAD6C2F0C87E063EB6D45F0E7612E0DD79B98CA85DCCEDE`（vc35）；两次冷启动 OK（模拟器负载高：0.85s/0.71s）；logcat 0 FATAL/ANR |

## 3. 待办

- 应用级 Baseline Profile（macrobenchmark 模块）
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- TalkBack 真机全量走查；P2 剩余细节（G13 数据新鲜度、A-04 角色徽标等）
