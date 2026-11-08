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

## 6. P0 修复记录（Release 冷启动崩溃）

- **现象**：v2.1.0 Release 包（versionCode 4）冷启动即崩：`RuntimeException: Unable to get provider androidx.startup.InitializationProvider` ← `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>`；Debug 与 E2E 不受影响。
- **根因**：v2 新增 WorkManager（内部 Room）后，R8 release 构建裁掉 Room 生成数据库实现类的无参构造（`androidx.startup` 反射实例化）。
- **修复**：`app/proguard-rules.pro` 增加 Room/WorkManager keep 规则（`androidx.work.impl.WorkDatabase_Impl`、所有 `RoomDatabase` 子类构造器、`ReminderWorker`、`-dontwarn androidx.room/**/androidx.work/**`）。
- **修复后验证**（2560×1600 平板模拟器）：`assembleRelease` 成功；APK **2,478,833 B**，SHA-256 `DCBB1EAE01D79BF3EFF5A70B02742CDC08FA65922C9C9CA8CD9C3DC7FB490B93`；`adb install -r` + **两次冷启动** `Status: ok`（314/317ms）、进程存活、logcat FATAL=0；身份页截图 `artifacts/release_fix_v21_identity.png` / `_second.png`。
- 交付桌面 APK 已替换为修复后构建；toolchain 复验 Release + 多设备 Release 身份页 + 文档 §8。

## 7. 最终验证结果（toolchain，2026-11-08）

| 项 | 结果 |
|---|---|
| E2E v2.1.0（Debug） | 完整模式 **34 PASS / 0 WARN / 0 FAIL（525.1s）**；快速模式 32 PASS/0 FAIL；导航 10 连击 10/10；logcat 0 FATAL/ANR |
| v2 新增 E2E 断言 | 底栏点击 + Pager 左右滑动逐页断言、工作台新结构、权限管理（系统弹窗拒绝后状态正常）、提醒设置开关切换、设置卡跳「我的」、版本 2.x；原 28–31 项基线保留 |
| Release v2.1.0（修复后） | APK **2,478,833 B** / SHA-256 `DCBB1EAE…0B93`；apksigner V2 通过；覆盖安装成功；**两次冷启动 OK（802ms）**；logcat 8,484 行 0 FATAL/ANR/NoSuchMethodException |
| 4 设备矩阵 | 平板 1280dp：P1-1 修复（工作台双栏+限宽、工具页 4 列、深色正常）；小屏 360dp：P1-2 主要修复（协议两行、不竖排；余 1 次轻扫可见按钮为 P2）；超长屏 411dp：底栏/手势条无遮挡；折叠屏 841dp：双栏/多列正常；共 26 张截图 |
| 残余 P2 | 小屏首屏仍需轻扫、平板无数据留白、手势条 20dp 间距、旋转/折叠态未覆盖；反馈/使用说明页下一轮纳入 E2E |
| 文档 | `E2E_SMOKE.md §9`、`ADAPTIVE_TEST_MATRIX.md §8`、`RELEASE_BUILD.md §8`（含 P0 事件与旧 SHA 作废） |

**结论：v2.1.0 为可交付版本**（Debug E2E 全绿、Release 冷启动/签名/安装验证通过、多设备适配 P1 修复确认）。