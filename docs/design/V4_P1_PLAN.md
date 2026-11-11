# v4 P1 深度优化实施计划（Lead，2026-11-12）

> 依据 `docs/design/V4_UX_AUDIT.md`（112 条：P0 14 / P1 52 / P2 46）与 `V4_RESEARCH_AND_DEMOS.md`。
> P0 已在 v4.0.x 处理（见 REPORT_ROUND11）；本计划针对 P1，按「每批可编译/可验证/可回滚」拆批。

## 批次 A（下一步，本/下轮）：交互可靠性

| # | 问题 | 方案 | 涉及文件 | 验收 |
|---|---|---|---|---|
| A1 | 全局撤销缺失（G03） | 新增 `ui/components/CtUndoHost`（Snackbar 5s + undo 回调）；本地危险操作接入：体测删除、水量删除、名单清空/重置、作业勾选（可选） | `ui/components/CtUndoHost.kt`、对应 screen | 单测（undo 状态机）+ 预览 + 手测 |
| A2 | 下拉刷新覆盖不足（H-01/G01） | 给 诊断/演示/名单(已)/电费(已)/体测/课表 加 `PullToRefreshBox` 或页面内刷新按钮统一化；无网络时可用缓存 + 顶部提示 | 各 screen | E2E 逐页断言可刷新；截图 |
| A3 | 通知权限横幅仅在部分页 | 提取 `NotificationPermissionBannerState` 到 App 级单例（CompositionLocal），工作台/我的/提醒/权限页一致；权限授予后自动消失 | `ui/components/NotificationPermissionBanner.kt`、MainNavHost | 手测（关闭/开启权限） |
| A4 | 返回/预测性返回一致 | 检查所有二级页/弹层：弹层先关、二级页 pop、预测性返回不退出；`enableOnBackInvokedCallback` 已开 | 各 screen 弹层 | E2E 返回探针 + 手测 |

## 批次 B：动效/光场（对应调研）

| # | 内容 | 方案 |
|---|---|---|
| B1 | `CtMotion` 对齐 M3 Expressive motionScheme | `MaterialTheme.motionScheme.defaultSpatialSpec()` 等派生 `CtMotion` 的 default/fast/slow；保留旧名兼容；页面转场 default、控件 fast、大面板 slow |
| B2 | 光场 specular 跟随触摸 | 底栏/hero 卡在按下时按触摸位置偏移高光（`drawWithCache`/`pointerInput`）；列表禁用 |
| B3 | 水滴击湖弹层动效 | `ModalBottomSheet` 默认 spring + scale/fade；投票详情/发起、PPT 上传等弹层统一；避免 tween 硬切 |
| B4 | 列表项进入动效 | 首屏 staggered fade/slide（≤300ms，可关闭；TalkBack/降低动效时禁用） |

## 批次 C：无障碍/自适应

| # | 内容 | 方案 |
|---|---|---|
| C1 | 触控目标 ≥48dp 审查 | 用 `Modifier.minimumInteractiveComponentSize` / 显式 size；E2E 断言 bounds |
| C2 | 大字体 1.3 全页审查 | 课表/PPT/投票/作业/通知 已部分处理；用 `fontScale=1.3` 预览矩阵复查 |
| C3 | 平板/横屏 | 投票/纪要/诊断/PPT 详情增加限宽/双栏；底栏在 ≥840dp 评估 rail（暂不默认） |
| C4 | contentDescription/TalkBack | 图标按钮补齐；列表语义合并（`semantics(mergeDescendants=true)`） |

## 批次 D：性能

| # | 内容 | 方案 |
|---|---|---|
| D1 | 动画 phase deferral | 审计 16 处 `animate*AsState`：scale/alpha/offset 移到 `graphicsLayer`/`offset` lambda；文本计数用 `Text` 局部化 |
| D2 | Lazy 列表稳定性 | 检查 10 处 LazyColumn/Grid 的 key/stability；图片加载采样/内存缓存（PPT 已有） |
| D3 | 启动耗时 | Baseline Profile 生成（compose 页面启动路径）+ Macrobenchmark（可选） |

## 执行顺序与交付

1. 批次 A（交互可靠性）→ v4.1.0
2. 批次 B（动效/光场）→ v4.2.0
3. 批次 C/D（无障碍/性能）→ v4.3.0
每批：单测全绿 + 目标 E2E + Release 冷启动/签名 + 桌面交付 + REPORT/CHANGELOG 更新；不改 web/mp。
