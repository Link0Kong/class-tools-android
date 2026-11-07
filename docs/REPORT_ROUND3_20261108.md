# Round 3 增量验收报告 · Android 原生 App（2026-11-08）

> 目标不变：「对班级工具站进行底层重构，以安卓17的设计语言和思路为基础，完成班级工具站安卓app的开发」。
> 本轮（goal round 3）完成 **水量/名册两张工具页**、**fitrecs 云端读写**、**抽签结果单 + 抽签→分组联动**、**通知附件图片预览**、**全部工具拖动排序**、**课表三态文案**、**隐私同意门禁 + 数据与隐私页**，并修复 E2E 发现的 **底部导航吞点击** 与 **抽签/分组返回状态丢失** 两个真实缺陷。
> 结论：**8 个工具全部原生可运行**（抽签/分组/体测/作业/通知/课表/电费/水量/名册；名册与点名同页），E2E **28 PASS / 0 WARN / 0 FAIL**，全量单测 **451/0**，@Preview **72 张**。整体目标继续（真实账号端到端、发布包/备案、剩余打磨未完成）。

## 1. 本轮新增/交付

| 类别 | 内容 | 来源 |
|---|---|---|
| 水量 | `features/water/`（编解码/月份状态机/计费/校验/写入）+ `WaterScreen`（关注宿舍/4·6 人间/月份选择器+12 月网格/录入/统计/历史删除/空态） | contract |
| 名册 | `features/roster/`（名单归一/搜索筛选/点名到·假/按姓名迁移状态/沉底）+ `RosterScreen`（名册·点名页签、筛选、统计、沿用抽签名单） | contract |
| fitrecs | `data/fitrecs/`（GET 2min TTL、POST upsert、最多 2 条、超限 arch 软删、pending 补发）+ FitScreen 历史接线 | contract |
| 抽签结果单 | `DrawResultSheetPayload`（chips + 可选分组 + 剔除重抽文本 → 统一 ExportCardSpec）+ STEP3「导出结果单」「用此结果分组」 | contract |
| 抽签→分组 | NavHost 级共享状态把结果（座号+姓名）带入分组页 `prevMembers` | contract |
| 状态恢复 | `DrawWorkflowCodec`/`GroupWorkflowCodec` + Saver → `rememberSaveable`；BACK 后回到 STEP3 结果（含剔除记录）；旋转/分屏/进程重建可恢复 | contract |
| 通知附件 | `NoticeAttachmentPreview`：data URI 图片按需拉取（列表不常驻 base64）、采样解码（缩略 480 / 大图 1800）、全屏预览；未知/远程降级占位 | design |
| 全部工具排序 | `ToolOrderLogic`（2 列网格命中/迁移/仅用户修改才写）+ 长按 350ms 拖动、ghost、边缘自动滚动、松手写 `prefs.toolOrder` | design |
| 课表三态 | `TtFailureKind{NETWORK,NO_TOKEN,EMPTY,FORMAT}` → 「网络异常/暂无课表数据/数据格式异常」；周末空列隐藏 | design |
| 隐私合规 | 身份页隐私同意门禁 + 《用户协议》《隐私政策》内嵌弹层；「我的 → 数据与隐私」页：导出我的数据 JSON（脱敏/白名单/电费不导出）、清除云端（userdata 空 blob + marks val=false + fitrecs arch，无 DELETE）、清除本机、协议回看 | contract + Lead |
| 缺陷修复 | ①底部导航 `navigateTopLevel` 使用组合期捕获 currentRoute → 在全部页点工作台被静默吞掉（E2E nav_probe 0/10）；改为点击时实时读当前路由（liveRoute）；②抽签/分组 `remember` 状态在导航后丢失 → rememberSaveable+Saver | Lead + contract |

## 2. 验证证据（本轮最终）

| 项 | 结果 |
|---|---|
| Debug 构建 | `assembleDebug` SUCCESSFUL；APK **21.8 MB**（`artifacts/classtools-1.0.0-debug.apk`） |
| 全量单测 | **451 tests / 0 failures**（35 个测试类；新增 Saver/隐私/水量名册等） |
| @Preview | **72 张**（全部页面浅/深色 + 抽签结果单/分组联动/通知附件/拖动中/隐私页/水量/名册等） |
| 模拟器 E2E | **28 PASS / 0 WARN / 0 FAIL / 391.3s**（完整模式）；快速模式 26 PASS/0 FAIL；导航 10 连击 10/10；导出相册每次 +1 |
| E2E 覆盖 | 身份门禁→真实 class_verify 失败路径→工作台→全部→抽签 STEP1/2/3→导出相册→结果页「用此结果分组」→分组页 BACK 回 STEP3（状态恢复）→8 个工具页（作业/通知/课表/电费/水量/分组/体测/名册点名）→隐私页（进入+清除云端二次确认弹层取消）→返回→导航 10/10 探测→首页编辑→logcat 0 FATAL→相册新文件 |
| Release | `assembleRelease` + `apksigner verify` + 模拟器安装启动（toolchain 复验，见 docs/RELEASE_BUILD.md 复验小节） |

E2E 截图（`artifacts/e2e/`，21 张）：`01_identity`、`20_auth_error`（真实鉴权失败）、`02_workbench`、`03_all_tools`、`04~06_draw_step1-3`、`07_export`、`22_draw_to_group`、`08_back_all_tools`、`11_hw`、`12_nt`、`13_tt`、`14_power`、`15_water`、`16_group`、`17_fit`、`19_roster`、`20_roster_roll`、`21_privacy`、`18_home_edit`。
状态恢复手工证据：`artifacts/e2e_state_fix/05_draw_after_back.png`（BACK 后 STEP3 结果页，含剔除重抽记录）。

## 3. 关键实现说明

1. **导航缺陷根因**：`navigateTopLevel` 判断「是否当前页」使用组合期捕获的 `currentRoute`；Scaffold bottomBar 槽可能持有旧 lambda，在 All 页点 Workbench 时被误判为当前页直接 return。修复：点击时实时读 `navController.currentBackStackEntry?.destination?.route`。E2E nav_probe 由 0/10 → 10/10。
2. **状态恢复**：Navigation Compose 为每个 back stack entry 保存 SaveableStateHolder；Draw/Group 的 workflow（config/candidates/result/step/log）通过 JSON Saver 持久化，push 分组页→BACK 恢复；坏 JSON 降级回初始。
3. **fitrecs 写链路**：保存后最多保留 2 条，被挤出记录逐条 PATCH `arch=true`（无 DELETE）；离线 pending + flush；UI 按 `synced` 提示「已保存并同步 / 已保存本机，联网后补发」。
4. **隐私**：身份页必须同意协议才能提交；隐私页导出 JSON 脱敏（学号 `32***24`）、白名单 section、电费不导出、roster 名单原文不导出；清除云端编排不发 DELETE；文件走 FileProvider + 系统分享。
5. **通知附件**：列表阶段仍只存元信息（防大 base64 常驻），详情按需拉单行原始数据并采样解码，避免 OOM。

## 4. 已知问题 / 下一轮（Round 4）

| # | 项 | 说明 |
|---|---|---|
| 1 | 真实身份全链路未验证 | E2E 已用真实 `class_verify` 失败路径验证网络可达与错误展示；**通过核验后的数据链路需要旅行者本人或测试身份**（学号+姓名）实测，是下一步最关键的验收 |
| 2 | 发布交付 | 签名 APK/备案号页/版本检查/桌面交付未做；隐私政策文本需管理员确认 |
| 3 | 抽签结果单中分组块 | 当前导出结果单默认无分组块（分组页有自己的导出）；若要合并需分组结果回传抽签页（建议 activity-scoped ViewModel） |
| 4 | 无障碍/性能 | 拖动排序 TalkBack 不可用（可加「上移/下移」动作）；低端机大列表/趋势图性能待测 |
| 5 | 通知编辑/发布 | 通知管理员编辑、作业发布仍为只读（v1 设计如此，按需再加） |
| 6 | 隐私待办 | 备案号、隐私政策正式文本、账号删除人工渠道文案 |
| 7 | E2E 环境 | headless 模拟器时区 UTC、uiautomator dump 偶发慢；脚本已多次重试兜底 |

## 5. 文档索引（本轮新增/更新）

- `docs/api/WATER_ROSTER_IMPL.md`、`FITRECS_DRAW_FLOW_IMPL.md`、`NOTICE_DRAG_TT_IMPL.md`、`DRAW_GROUP_STATE_IMPL.md`、`PRIVACY_DATA_IMPL.md`
- `docs/PRIVACY_COMPLIANCE.md`（实现清单与发布前待办）
- `docs/E2E_SMOKE.md`（28 PASS 记录 + 导航/状态回归断言）
- `docs/RELEASE_BUILD.md`（Release 复验）
- `docs/ROADMAP.md`、`README.md`、`docs/ARCHITECTURE.md`（本轮同步）

## 6. 桌面交付（本轮检查点）

- 已交付到桌面（用户可安装实测真实账号链路）：
  - C:\Users\Ko_Li\OneDrive\桌面\班级工具站_v1.0.0_安卓.apk（2,258,234 B，SHA-256 716063E4F8B446B31806E7850B5A04E621C2A612E8C906039969B1BB1D2DC056）
  - C:\Users\Ko_Li\OneDrive\桌面\班级工具站_v1.0.0_安装说明.txt
- 说明：这是 v1.0.0 检查点签名包，供旅行者用本人学号+姓名验证真实云端数据链路并反馈；后续迭代仍用同一 keystore 签名升级（keystore 务必备份，见 RELEASE_BUILD.md）。