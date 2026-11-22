# 「我的」选项 → 详情页 一镜到底转场（2026-11-22）

参考：<https://gitee.com/itgsa_sharedviewkit/SharedViewDemo>

## 1. SharedViewKit 的能力边界

官方 Demo / `gsai.sdk:sharedview-kit` 的公开 API 是 **Activity 级**的：

- `ViewAnimationManager.makeViewAnimationOptions(sourceView, bundle, listener)` 返回 `ActivityOptions`；
- 调用方必须 `startActivity(intent, options.toBundle())`；
- 运行时用 `isViewAnimationSupported()` 判断 ROM 是否声明支持；
- 依赖金标联盟 Nexus（内测 HTTP 仓库）与设备侧实现。

本项目是**单 Activity + Compose Navigation**：个人页的「数据与隐私 / 权限管理 / 通知设置 / 反馈建议 / 使用说明」都是 NavHost 内部路由，没有独立 Activity 可供 `startActivity`，因此无法直接把 Kit 套在这些选项上。

## 2. 本项目采用的等价实现

`CtContainerTransform`（`ui/components/CtContainerTransform.kt`）：

- 点击 `ActionRow` 时记录该行在根布局中的 `bounds`；
- 根级 `CtContainerTransformHost` 画一层从行 bounds 扩展到整屏的 surface：
  - 300ms `FastOutSlowInEasing` 展开；
  - 圆角 12dp → 0；
  - 目标导航在遮罩下完成，再 140ms 淡出；
- 无 ROM 能力要求，所有设备可用；
- `rememberReduceMotion()` 开启时跳过动画，直接导航；
- 只覆盖**会跳页**的 `ActionRow`；开关行（SwitchRow）不播转场。

## 3. 后续可接真 Kit 的路径

如果后续要把 Kit 真正接进来，建议只对**已经是 Activity** 的页面使用：

1. `ToolActivity` 工具页：主页工具卡点击时可用 `makeViewAnimationOptions`；
2. 若把「数据与隐私 / 设置」拆成独立 `Activity`，可在 `startActivity` 时接入；
3. Kit 不支持时按 Demo 的降级策略直接 `startActivity`。

在单 Activity 架构下，继续使用 Compose 容器变换是更稳的方案。
