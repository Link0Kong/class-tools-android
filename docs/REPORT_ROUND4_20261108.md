# Round 4 增量验收报告 · v1.0.1 发布候选（2026-11-08）

> 目标不变：「对班级工具站进行底层重构，以安卓17的设计语言和思路为基础，完成班级工具站安卓app的开发」。
> 本轮（goal round 4）把 v1.0.0 收尾为 **v1.0.1 发布候选**：新增外观（壁纸取色）、离线本机状态恢复、无障碍排序、对比度修复，按发布审计修复 6 项 P1，建立 git 版本基线（tag `v1.0.1`），E2E 扩到 **31 项全绿**，签名 APK 已交付桌面。
> 结论：**功能开发完成**（8 个工具 + 身份/同步/导出/隐私全部原生实现，Release 签名包可安装启动）。剩余为**外部依赖与可选增强**：真实账号业务链路实测（需旅行者本人/测试身份）、备案号/隐私政策正式文本（需管理员确认）、marks 离线持久化（下版本）、可选提醒/小组件。

## 1. 本轮新增/交付

| 类别 | 内容 |
|---|---|
| 外观 | 「我的 → 外观 → 壁纸取色（Material You）」开关：DataStore 本机持久化、重启保持、实时生效；深浅色跟随系统 |
| 设置入口 | 「全部工具 → 设置」不再打开占位页，直接落到「我的」页签 |
| 首页快捷 | 保存后 Toast「首页快捷已保存」 |
| 离线恢复 | `UserDataRepository.hydrateFromLocal()`：登录/启动时先恢复本机持久化的 userdata sections（homeTools/toolOrder/water/roster/draw 等）；离线/关闭云同步启动不再回落默认（已实测：改快捷 → 重启 → 保持） |
| 无障碍 | 全部工具 TalkBack「上移/下移」自定义操作（与拖动同一 `prefs.toolOrder` 持久化）；icon-only contentDescription 补齐；1.3x 字体下工作台/我的页排版修复；`docs/ACCESSIBILITY.md` |
| 对比度 | 电费趋势轴（α0.45→0.8）、课表节次时间（α0.7→0.85）；关键正文组合全部达标（清单见 ACCESSIBILITY.md） |
| 发布审计 | `docs/RELEASE_AUDIT_20261108.md`：0 P0 / 10 P1 / 19 P2 + 正向核对 + 发布检查清单 |
| P1 修复 | ①release 无 keystore 不再回退 debug 签名；②R8 keep 包名 `fun.`→`com.furina`；③退出登录二次确认；④体测等级胶囊深色可读；⑤通知附件 10MB 上限（防 OOM）；⑥导出超大卡片预检友好报错 |
| 版本基线 | 工作区 `git init` + commit + tag `v1.0.1`（key/keystore/artifacts 均 gitignore）；`docs/CHANGELOG.md` |
| E2E 扩展 | 新增「我的→外观」断言+持久化、`appearance_about`（版本 1.0.1）、`settings_redirect`、`23_appearance.png`、`24_settings_redirect.png` 等 |
| 交付 | v1.0.1 签名 APK + 安装说明 → 桌面（见 §4） |

## 2. 验证证据（本轮最终）

| 项 | 结果 |
|---|---|
| 全量单测 | **466 tests / 0 failures**（37 个测试类；新增 a11y 13 + hydrate 2 等） |
| @Preview | **78 张**（含无障碍大字体 6 张；`artifacts/previews/`） |
| 模拟器 E2E | 完整模式 **31 PASS / 0 WARN / 0 FAIL / 462.0s**；覆盖：身份门禁/真实鉴权失败路径/工作台/8 工具页/抽签三步+结果单+分组联动+状态恢复/隐私页/外观/版本 1.0.1/设置跳转/导航 10 连击/导出相册/logcat 0 FATAL |
| Release 复验 | `assembleRelease` SUCCESSFUL（含 P1 修复）；APK **2,274,622 B**；SHA-256 `72135018511DE666CB1AF6222988FD7FE39FF527F21C20D70C1B80D98D144DD2`；`apksigner verify` V2 通过（证书 SHA-256 `974854df…4a1a`）；`adb install -r` Success；启动首屏身份页、logcat 0 FATAL |
| 发布审计 | 0 P0；P1 已修 6 项 + 2 项外部依赖 + 1 项下版本（marks 持久化） |
| 版本可追溯 | git commit `2d8c595` + tag `v1.0.1`（239 个源码/文档文件；无密钥入库） |

E2E 截图（`artifacts/e2e/`，24 张）新增：`23_appearance.png`（外观页）、`24_settings_redirect.png`（设置→我的）、`release_launch_v101.png`（Release 首屏）。

## 3. 关键实现说明

1. **离线本机镜像恢复**：`currentSections` 原为纯内存 StateFlow，重启后 UI 回落默认值（实测首页快捷丢失）。新增 `hydrateFromLocal()` 在 `ClassToolsRoot` 登录分支、`setSyncEnabled` 之前调用，只读本机 current 文件、不发网络；已有镜像时不覆盖（避免启动竞态）。单测覆盖持久化恢复/空本机/已有镜像不覆盖。
2. **发布安全**：`keystore.properties` 缺失时 release **保持未签名**而不是静默用 debug 签名；ProGuard 规则包名对齐 `com.furina.classtools`，消除 release-only 序列化风险。
3. **导出 OOM 防护**：渲染前按 `W*scale × logicalHeight*scale` 预检（高度 >8192px 或面积 >12M px 拒绝并提示「导出内容过大…」），极限参数（999 号码/70 组）不再可能创建 40MB+ 位图。
4. **通知附件**：列表仍只存元信息；详情按需拉取原始行并采样解码；本次加 10MB 原始字节上限（解码前 base64 长度预判 + 解码后校验），与小程序/契约一致。
5. **无障碍排序**：`Modifier.reorderableSemantics` + `CtA11y` 把「上移/下移」并入工具卡同一读屏焦点，调用与拖动完全相同的 `persistOrder()`；TalkBack 用户可不用手势完成排序并云同步。

## 4. 桌面交付

- `C:\Users\Ko_Li\OneDrive\桌面\班级工具站_v1.0.1_安卓.apk`（2,274,622 B，SHA-256 `721350…44DD2`）
- `C:\Users\Ko_Li\OneDrive\桌面\班级工具站_v1.0.1_安装说明.txt`（安装/新增/边界/签名信息）
- v1.0.0 旧包已移至 `workspace\archive\android-v1.0.0\`（可恢复，不留桌面）
- ⚠️ keystore：`android-class-tools/key/release.jks` + `keystore.properties`（gitignore，未入库）——**必须离线备份**，丢失将无法覆盖升级。

## 5. 剩余事项（Round 5+ / 外部依赖）

| # | 项 | 说明 |
|---|---|---|
| 1 | 真实账号业务链路实测 | 需旅行者本人或云端测试身份登录后验证各工具真实数据/同步/导出；自动化只能覆盖到鉴权失败路径与空态 |
| 2 | 备案号 / 隐私政策正式文本 / 账号删除渠道 | 需管理员提供；「关于」页已预留位置，隐私政策为可替换常量 |
| 3 | marks 本机持久化 | 杀进程后离线勾选会丢失（P1-#8），需 marks 文件缓存 + pending 补发 |
| 4 | 可选增强 | 作业截止/课表本地提醒（WorkManager）、课表小组件（Glance）、通知/作业管理员编辑、抽签结果单合并分组块 |
| 5 | 平板/横屏逐页视觉 | 已有 ≥600dp 双栏，实机未逐页复核 |

## 6. 文档索引

- `docs/CHANGELOG.md`（v1.0.0 / v1.0.1）
- `docs/RELEASE_AUDIT_20261108.md`（审计 + 修复状态）
- `docs/ACCESSIBILITY.md`（无障碍/大字体/对比度）
- `docs/E2E_SMOKE.md`、`docs/RELEASE_BUILD.md`（v1.0.1 复验）
- `docs/PRIVACY_COMPLIANCE.md`、`docs/ROADMAP.md`、`README.md`
