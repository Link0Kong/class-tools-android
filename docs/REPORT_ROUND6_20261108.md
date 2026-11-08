# Round 6 增量验收报告 · v2.1.0（2026-11-08）

> 接续 Round 5（v2.0.0 大版本）。本轮完成：**小程序对齐 P1**（反馈建议 / 使用说明 / 我的数据指标卡）、**小屏身份页修复**、**工具网格 2/3/4 列拖拽参数化**、**「我的」页平板双栏**、**大屏密度/深色收尾**；最终版本 **v2.1.0**。
> 目标（旅行者 v2 需求）仍在进行：v2 E2E 完整回归 + 多设备矩阵 + Release 复验由 toolchain 本轮收尾（见 `docs/E2E_SMOKE.md`/`ADAPTIVE_TEST_MATRIX.md` 的 v2 小节）。

## 1. 本轮交付

| 模块 | 内容 | 来源 |
|---|---|---|
| 反馈建议 | `features/feedback`（E19–E21 契约：GET 30s TTL、POST、管理员 PATCH done + 失败 upsert）+ `FeedbackScreen`（500 字/四态/普通用户仅自己/管理员筛选标记）；Profile「关于 → 反馈建议」入口 | contract + Lead 接线 |
| 使用说明 | `UserGuideScreen`：18 分节、零网络零外链、宽屏限宽 | contract |
| 我的数据 | `MeStatsSection`（mp 4 指标：待交/已完成作业、抽签、体测 + 可选班级人数/未处理通知/电量 + 提醒入口三态）；嵌入 Profile 身份卡下方；Lead 从本机缓存聚合（无新增网络） | contract + Lead |
| 小屏身份页 | 同意行改两行布局（勾选说明 / 协议链接），360×640dp 不再溢出/逐字竖排（对应 ADAPTIVE P1-2） | Lead |
| 工具网格 | 2/3/4 列随宽度自适应，拖拽命中几何参数化（修复平板 P1-1 拉伸/留白）；TalkBack 排序保持 | design |
| 我的页平板 | ≥840dp 双栏（左：账号+同步+权限；右：数据隐私+外观+关于+退出），保留全部入口；密度收紧 | design |
| 大屏/深色 | 工作台双栏间距、CtTopBar 平板留白收紧；v2 新组件深色对比度全部 ≥AA（核对表 `V2_ADAPTIVE_SPEC.md §6.4`） | design + Lead |
| 版本 | `versionName = 2.1.0` / versionCode 4；CHANGELOG 更新 | Lead |

## 2. 验证证据

| 项 | 结果 |
|---|---|
| 全量单测 | **603 tests / 0 failures**（48 类；反馈 23 + 指标卡 13 + 自适应 15 + 其余既有） |
| @Preview | 本轮新增 19 张（反馈/使用说明/指标卡 8 + 自适应 11），全量渲染见 `artifacts/previews/` |
| 构建 | `assembleDebug` / `assembleRelease` SUCCESSFUL |
| Release APK | **2,429,681 B**；SHA-256 `1BF4CE6D4036A88D7EF9A098F64254B41AD334A407EB54318489EF60888F3EB3`；apksigner V2 通过（证书 `974854df…4a1a`） |
| 手动验证 | 1080×2400 模拟器：Profile 新增「我的数据」卡与反馈/使用说明入口、提醒状态行（历史截图 `artifacts/v2-*.png`） |
| 待补 | toolchain 的 v2 E2E 完整回归 + 多设备矩阵（本轮进行中，完成后追加本报告/文档） |

## 3. 与小程序对齐结论（审计见 `docs/MP_PARITY_AUDIT.md`）

- mp 21 页：**12 已对齐 + 5 部分对齐 → 本轮补齐 fb/ug/me 指标后基本对齐用户面功能**；剩余 stats/diag 仅管理员、部分管理员编辑（P2）不在本轮。
- 反馈/使用说明/指标卡与 mp 的差异（TTL、作者名映射、18 节 vs 16 节、提醒入口为 Android 增强）已在 `docs/api/PARITY_P1_IMPL.md` 记录。

## 4. 已知问题 / 下一轮

| # | 项 | 说明 |
|---|---|---|
| 1 | v2 E2E + 多设备矩阵 | toolchain 本轮收尾（ADAPTIVE P1-1/P1-2 修复验证、权限弹窗拒绝降级、Pager 滑动断言、Release 复验） |
| 2 | 启动图标 launcher 渲染 | API 37 Pixel Launcher 图标外圈浅色环；真机复核后如存在改用 legacy PNG 或调 monochrome |
| 3 | 反馈页下拉刷新 / 离线历史 | contract 遗留，后续补 |
| 4 | stats/diag/管理员编辑 | P2，仅管理员场景，按需再做 |
| 5 | 真实账号链路 | 仍需旅行者本人/测试身份实测（同前） |

## 5. 文档

- `docs/CHANGELOG.md`（v2.1.0）
- `docs/api/PARITY_P1_IMPL.md`
- `docs/ui/V2_ADAPTIVE_SPEC.md`（§6 列数/双栏/深色/预览）
- `docs/MP_PARITY_AUDIT.md`、`docs/ADAPTIVE_TEST_MATRIX.md`（待 v2 小节）
