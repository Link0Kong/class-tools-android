# v1.0.1 发布前只读审计（2026-11-08）

> 审计性质：**只读**。未修改任何 app 源码/构建文件；本文件是唯一写入。
> 审计对象：`<项目根>`（包 `com.furina.classtools`，v1.0.1 / versionCode 2）。
> 审计人：contract（Team）；审计时间：2026-11-08 01:33–01:42；代码快照：工作区 01:38 编译绿树。
> 说明：该工作区 git 仓库（`<工作区>`）**尚无任何 commit**（`git rev-list HEAD` 失败），因此无法以 commit hash 固定快照；本报告对应“2026-11-08 01:38 编译通过 + 01:40 成功 assembleRelease 的工作树”。

---

## 0. 结论摘要

**结论：有条件通过，建议修复 P1 后再发布 v1.0.1。** 未发现 P0 级（必然崩溃/必然数据丢失/隐私明文外泄）问题；发现 **10 项 P1**（发布配置安全、R8 规则、退出确认、备案占位、深色对比度、附件/导出内存边界、marks 离线持久化、发布可追溯性、合规文本）与约 15 项 P2（性能/文案/清理项）。

**基线证据（本轮实测）**

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **PASS：37 classes / 466 tests / 0 failures**（2026-11-08 01:38；结果目录 `app/build/test-results/testDebugUnitTest/`） |
| `:app:assembleRelease` | **BUILD SUCCESSFUL**（01:40，R8 + 资源压缩 + lintVitalRelease）；APK `app/build/outputs/apk/release/app-release.apk` = **2,274,622 B** |
| `apksigner verify --print-certs` | ✅ v2 签名；证书 `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；SHA-256 `974854df…bec4a1a`（与 RELEASE_BUILD.md 一致） |
| E2E（Lead 最新一轮） | **PASS 26/26 steps，0 warn / 0 fail，22 张截图**（`artifacts/e2e/summary.json`，01:19–01:26） |
| @Preview | 78 张（`ui/preview/`，脚本渲染 + 手工可见） |
| 审计期异常 | 01:35 前后主树因**其他任务并发 WIP**（`ui/components/A11yFormat.kt`、`UserDataRepository.hydrateFromLocal()` 测试未同步）短暂编译红；01:38 恢复全绿。发布前必须冻结主树并复跑（见 P1-9）。 |

---

## 1. P0（0 项）

本轮**未发现 P0**：无必然崩溃路径（主流程均有 runCatching/空态/错误态兜底）、无必然数据覆盖（userdata 合并有基线保护，见 §4）、无明文 token/学号进日志/导出/崩溃栈的证据（见 §3）。所有“可能”问题按影响降级为 P1/P2 并给出证据。

---

## 2. P1（发布前应修，10 项）

| # | 位置（文件:行） | 问题 | 影响 | 最小修法建议 |
|---|---|---|---|---|
| P1-1 | `app/build.gradle.kts:58` | release 签名配置缺失时**静默回退 debug 签名**：`signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")` | 在未放 keystore.properties 的机器/CI 上 `assembleRelease` 仍成功，但产出 debug 证书签名包 → 商店拒审/无法覆盖升级既有 release 安装；且易被误当正式包分发 | 移除 `?: debug` 兜底；`keystore.properties` 缺失时 `error("release keystore 缺失")` 或仅在显式 `-PallowDebugSigning=true` 时降级并打警告；发布检查 `apksigner verify` 证书指纹 |
| P1-2 | `app/proguard-rules.pro:8,11,14` | R8 keep 规则包名写错：`fun.furina.classtools.**`（实际包 `com.furina.classtools`） | 规则完全不生效；当前解析都走显式 `serializer()`（已核对全仓无 reified `decodeFromString<T>`），所以 release 暂未崩；但任何后续反射式序列化/插件生成路径变化会变成 **release-only 崩溃**（debug 不复现） | 3 处包名改为 `com.furina.classtools.**`；补 `-keep,includedescriptorclasses class com.furina.classtools.**$$serializer { *; }`；发布前在真机/模拟器装 release 包实际打开**通知/作业/课表**（走 JSON 解析）验证 |
| P1-3 | `ui/screens/profile/ProfileScreen.kt:161-171` + `ui/ClassToolsRoot.kt:83-86` | 「退出登录并清除本机会话」**无二次确认**，点击立即 `logout()+signOut()`（清 token、回身份门禁） | 误触即被登出（需重新学号+姓名核验）；不符合 `design/SCREENS.md` §14.1「退出身份二次确认」 | 增加 AlertDialog 确认（文案说明“清除本机会话，需重新验证；云端数据不受影响”）；确认后才调用 onSignOut |
| P1-4 | `ui/screens/profile/ProfileScreen.kt:152` | 「备案信息：上线前填写」占位仍展示 | 面向公开分发时的合规信息缺失（App 备案号/主体需可查）；商店/监管审核风险；用户可见未完成文案 | 填入真实备案号（可点击链接工信部备案系统）或**先移除该行**；同步更新 `docs/RELEASE_BUILD.md`/隐私政策 |
| P1-5 | `ui/screens/fit/FitScreen.kt:528-530` + `:592-598` | 体测等级胶囊：`containerColor = gradeContainer(...)` 用**固定浅色 pastel**（`0xFFD8EEE3` 等），`contentColor = MaterialTheme.colorScheme.onSurface`；深色主题下 onSurface 为浅色 → 浅底浅字 | 深色模式等级文字几乎不可读（对比度 ≈1.2:1）；用户看不到等级 | 改用主题 token 容器（`primaryContainer/tertiaryContainer/errorContainer`）或 `gradeContainer` 返回 (container, content) 成对颜色；也可仅用 `Surface` + 语义色；顺手复查 `TimetableViewScreen.kt:1017-1019` 固定课程色板深色表现（P2-4） |
| P1-6 | `ui/screens/notices/NoticeAttachmentPreview.kt:176-184,209-224`（loader `:92-108`） | 附件 data URI **无大小上限**：`decodeBase64Payload` 直接对整段 base64 解码为 ByteArray，`decodeSampled` 再保留 bytes 到解码完；契约/MP 侧是「超 10MB base64 拒收」`[BACKEND_CONTRACT §9-5]` | 大附件（QQ 群文件/聊天图片可能数 MB~数十 MB）打开预览时瞬时内存 = base64 String + ByteArray + Bitmap，低内存设备可能 OOM/卡顿；单条通知详情内多图更明显 | 解析前检查 payload 长度（如 base64 字符 ≤ ~13.7M ≈ 10MB 字节）与 `substring` 前先算 `data.length`，超限直接降级「附件过大，暂不支持预览」；缩略图已 480/1800 采样 + RGB_565（这部分是好的）；详情多图建议串行/限制同时解码数 |
| P1-7 | `features/export/ExportCardRenderer.kt:102-107`（+`drawChipsBlock:373`、`drawGroupsBlock:419`；`features/draw/DrawModels.kt:67-69` MAX_COUNT=999） | 导出位图高度**无上限**：高度 = 内容高度自适应；抽签最多 999 号码 chips / 分组最多 70 组时逻辑高可达数千 → 1520px 宽 ARGB_8888 位图约 40MB+ | 极限参数导出有 OOM/系统内存压力风险；虽有 `runCatching` 兜底成「导出失败：生成图片失败」，但用户体验差、极端情况可能被系统杀 | 直接复用已有 `ExportCardRenderer.logicalHeight(spec)`（`:608`）先估算；超过阈值（如逻辑高 > 6000 或估算内存 > 32MB）时：降级 `scale`、分页导出或提示「结果过多，请分批导出」；同时给 chips/groups 数量设导出上限 |
| P1-8 | `AppContainer.kt:97-99` + `data/marks/MarksRepository.kt:71` | marks 仓库使用**默认内存** `InMemoryMarksLocalStore`（AppContainer 未注入文件实现）：marks 缓存与失败 pending 仅内存 | 进程被杀/系统回收后：离线勾选的作业完成/通知已处理可能**静默丢失**（pending 未补发）；冷启动必定重拉 marks（多一次网络）；断网冷启动看不到上次勾选 | 提供 `FileMarksLocalStore`（与 `FileUserDataLocalStore` 同思路，按 sid 隔离写 cache/pending），AppContainer 注入；启动时 `refresh` 后 `flushPending`；这是小改动、单测可覆盖（已有 MarksRepository 测试基建） |
| P1-9 | 仓库状态（workspace git，无 commit/tag）+ 审计期实测 | **发布不可追溯**：工作区 git 仓库无任何 commit（`git rev-list HEAD` 失败，所有源码 untracked）；审计期间并发任务 WIP 使主树在 01:35 短暂编译失败（`A11yFormat.kt` 未完成、`UserDataRepository.hydrateFromLocal` 接口新增后 `DrawConfigStoreTest` fake 未同步），01:38 恢复 | 无法从某个 commit 复现 release 包；若发布前有人并发改代码，可能产出“构建通过但内容非预期”的包；出问题无法回滚/比对 | v1.0.1 发布窗口冻结主树：`git add -A && git commit -m "release: v1.0.1"` + `git tag v1.0.1`；在 **tag/干净工作树**上依次跑 `:app:compileDebugKotlin` → `:app:testDebugUnitTest` → `:app:assembleRelease`；把 commit hash + 测试数字写入 release 说明；产物从该构建复制 |
| P1-10 | `ui/screens/privacy/PrivacyTexts.kt`（版本 2026-11-08）+ `docs/PRIVACY_COMPLIANCE.md` §2 待办 #4/#5 | 隐私政策/用户协议仍为“内嵌草稿”，账号删除/数据删除仅“联系班级管理员人工处理” | 公开上架（尤其国内商店）前需法务/管理员确认正式文本与生效日期；删除请求渠道不明确可能被审核问询 | 发布前由管理员确认文本并把版本/生效日期写进 `PrivacyTexts.VERSION`；「关于」补充删除请求渠道（邮箱/群公告/管理员联系方式）；如仅班内分发，可接受现状但需 Lead 明确口径 |

---

## 3. P2（建议后续修，按主题）

### 3.1 性能 / 主线程

| # | 位置 | 问题 | 最小修法 |
|---|---|---|---|
| P2-1 | `data/notices/NoticesRepository.kt:64-80`（`parseRows`/`buildData`/`NoticeBodyParser`） | 通知整表可能 ~2MB；`api.get` 在 IO 完成后，**JSON 解析 + body/items 二次解析在调用方（主线程）**，首次打开通知/刷新时有可感卡顿 | 在 repo 的解析/构建外面包 `withContext(Dispatchers.Default)`（或 IO）；大 body 的 `parseGroup` 也放进去；单测不受影响 |
| P2-2 | `data/timetable/TimetableRepository.kt:83,156` | 课表快照解析同为主线程（snapshot 一般较小，但通知/课表页首次进入叠加） | 同 P2-1，`parse()` 放 `Dispatchers.Default` |
| P2-3 | `ui/screens/home/HomeScreen.kt:97-115` | 今日课程/待办加载失败被 `runCatching` 静默忽略 → 页面展示“暂无课程/暂无作业”，无失败提示/重试 | 增加小型错误行/提示（可复用 `SyncStatusChip` 或小号 error pill）；至少区分“空数据”和“加载失败” |

### 3.2 暗色 / 大字体 / 视觉

| # | 位置 | 问题 | 最小修法 |
|---|---|---|---|
| P2-4 | `ui/screens/timetable/TimetableViewScreen.kt:518,1017-1019` | 网格线固定 `Color(0x14000000)` 深色主题几乎不可见；课程色板第 4-6 组为固定浅色 pastel + 固定深色字（深色下与主题不协调，但可读） | 网格线改 `MaterialTheme.colorScheme.onSurface.copy(alpha=0.10f)`；课程色板深色分支用固定深色容器 + 浅色字，或全部用 token |
| P2-5 | `ui/screens/profile/ProfileScreen.kt`（`SettingRow` 系列）+ `ui/screens/fit/FitScreen.kt`（固定 32–40dp 列宽） | fontScale 1.3 时：Profile 左右两列文本无 weight/maxLines，可能挤压重叠；Fit 分数行固定列宽可能截断（task-17 正在做关键页大字体核查，需以其结论为准） | SettingRow 的 label 加 `weight(1f)` + value `maxLines=1`/`softWrap`；Fit 固定列宽改 `widthIn(min=…)` 或允许换行 |
| P2-6 | 全局（a11y 任务-17 收尾中） | icon-only 控件 contentDescription 已补大半（通知/水/名册/抽奖等），但仍需以 task-17 最终 diff 为准；AllTools 拖动已加「上移/下移」语义 | 等 task-17 完成并复核 `ui/components/Accessibility.kt`、`A11yFormat.kt`；发布前用 TalkBack 抽测首页/通知/名册 |

### 3.3 导航 / 状态 / 交互

| # | 位置 | 问题 | 最小修法 |
|---|---|---|---|
| P2-7 | `ui/navigation/MainNavHost.kt:11/16,83,99,274` | ① 重复 `import getValue`（lint 噪音）；② `drawGroupMembers` 为普通 `remember`，进程重建后分组页来源丢失（BACK 恢复已由 task-16 修复，杀进程仍丢）；③ `openTool`/分组跳转未 `launchSingleTop`，快速双击卡片可能重复入栈，需多次 BACK | 删重复 import；`drawGroupMembers` 用 `rememberSaveable`（`GroupMember` 简单，可 listSaver）；工具导航加 `launchSingleTop=true`（保留 `popUpTo` 语义） |
| P2-8 | `ui/screens/profile/ProfileScreen.kt:131` + `AppContainer.kt:155-157` | 「清除本机缓存」无二次确认，且只清名单（DataPrivacy 页还能清 userdata 基线等）→ 两处能力/文案不一致 | Profile 行改为跳转 DataPrivacy 页（或加确认）；或删该行并提示“在数据与隐私中管理” |
| P2-9 | `data/session/SessionStore.kt:43,69` | 班级 token 以**明文**存 DataStore（应用私有目录 + `allowBackup=false` 已缓解，但 root/调试备份可读） | 后续用 `EncryptedSharedPreferences`（androidx.security）或 Keystore 派生密钥加密 token 字段；至少保持不在日志/导出中出现（已满足） |
| P2-10 | `data/network/ApiClient.kt:187-188` + `AppContainer.kt:72` | 任意 401/403 都触发 `onTokenInvalid` 清会话；管理员/RLS 场景的 403 会误登出（当前工具多为本人数据，影响有限；清云端数据若 403 会直接被踢回身份页） | 仅对“带 X-Class-Token 且 401”清会话；403 保留会话并提示“无权限/请重试”；错误信息区分 |
| P2-11 | `ui/ClassToolsRoot.kt:48-49` | Debug「开发预览」会话会**持久关闭云同步**（写入 DataStore `syncEnabled=false`）；同机之后真实登录时云同步仍关闭（需手动打开） | debug 预览会话不写 syncEnabled，或退出预览时恢复；或在 Profile 云同步关闭时显示醒目提示（已有开关，但用户未必注意） |
| P2-12 | `features/export/ExportCardFiles.kt:67-84` + `ui/screens/data/DataPrivacyScreen.kt:633-636` | 导出/分享写 `cacheDir/exports/` 后从不清理；JSON 导出每次生成新文件，长期占缓存 | 分享/保存成功后删除临时文件（分享需在 chooser 结束后延迟删）；或启动时清理 `exports/` 中 N 天前文件 |
| P2-13 | `data/privacy/PrivacyRepository.kt:258-260` + `data/sync/UserDataRepository.kt`（push/防抖） | 清除云端成功后会 `resetToDefaults()`（updateCurrent→防抖 push），若清除前已有 in-flight/后续防抖 push，短窗口内可能把旧本机数据再写回；且 marks 本机快照不会立即清空（页面概览可能短暂显示旧数字） | 清除前 `flush()`/取消防抖并等待 push 完成；成功后 `marksRepository.refresh(force=true)`；清除流程中禁用 debounce 或置“clearing”标记 |
| P2-14 | `ui/screens/tools/ToolPlaceholderScreen.kt:91` + `ui/navigation/MainNavHost.kt:385-391` | 占位页（“原生版开发中”）与 `ToolCatalog.features`/`allEntry` 已无真实入口（所有 10 个工具 id 均已映射，settings 走 Profile）；死代码/未完成文案可能被误发现 | 删除占位页与 `features` 字段，或仅在 `BuildConfig.DEBUG` 作为 future-tool 兜底 |
| P2-15 | `data/fitrecs/FitRecsRepository.kt`（pendingSaves/pendingArchives 内存 Map） | 体测写失败 pending 仅内存，杀进程即丢（与 P1-8 marks 同类，频次低） | 可选：pending 落盘或至少 UI 保留“本机未同步”标识（当前 toast 已提示“联网后补发”，但重启后无法补发） |
| P2-16 | `ui/screens/notices/NoticeAttachmentPreview.kt:92` | 附件预览每次 `GET notices?...` 拉整行 body（2MB 级），同一图重复打开重复拉取；无缓存 | 单次内存 LRU 缓存（rowId+ordinal → data URI，注意内存上限 <16MB）或详情打开时一次性按需拉全行并按序释放 |
| P2-17 | `features/export/ExportCardRenderer.kt:373-410,446-460` | 单个 chip/组员姓名超长时：chip 宽度不封顶，可能越出内容区；组卡成员单行超宽不折行（真实姓名短，风险低） | chip 名称/组员姓名套用已有 `ellipsize`/`wrapText` 工具，并给 chip 最大宽度（如 240 逻辑px） |

### 3.4 文档 / 版本口径

| # | 位置 | 问题 | 最小修法 |
|---|---|---|---|
| P2-18 | `README.md:6,9`；`docs/RELEASE_BUILD.md:39,97`；`docs/PRIVACY_COMPLIANCE.md` | 文档仍写 v1.0.0/versionCode 1/APK 2.06MB/72 张预览；实际 v1.0.1/versionCode 2/APK 2.27MB/78 张预览；RELEASE_BUILD 第 6 节为 v1.0.0 复验记录 | 发布时将 README/RELEASE_BUILD 更新到 v1.0.1（版本、体积、E2E/测试数字、release 复验）；旧记录保留为“历史”小节 |
| P2-19 | `data/auth/AuthRepository.kt:14,24` | 注释仍称“占位实现…契约确认后替换”，实际已用 `ApiAuthRepository`（`AppContainer.kt:77`）；易误导维护 | 更新注释（`StubAuthRepository` 仅测试/降级保留） |

---

## 4. 已核对无问题（正向清单，供发布说明引用）

### 4.1 隐私与安全（对照 `web/AGENTS.md` §0 / `docs/PRIVACY_COMPLIANCE.md`）

- ✅ 权限最小：`AndroidManifest.xml:4-5` 仅 `INTERNET` + `ACCESS_NETWORK_STATE`；无存储/定位/通讯录/相册读取权限。
- ✅ `allowBackup="false"`（`:9`）；仅 `MainActivity` exported（`:19`）；FileProvider `exported=false`（`:31-32`），无其它导出组件；默认禁 cleartext（targetSdk 37 未配置 `usesCleartextTraffic`）。
- ✅ **身份门禁**：Release 无“开发预览”入口（`IdentityScreen.kt:267` 在 `BuildConfig.DEBUG` 内）；核对同意勾选后才可提交；29 天本地过期与 401 自动清会话（`Session.kt`/`AppContainer.kt:72`）。
- ✅ **token/学号不外泄**：全仓仅 1 条日志（`AppContainer.kt:72`，不含 token/学号/姓名）；token 仅进 `X-Class-Token` 请求头与私有 DataStore；导出 JSON 用 `PrivacyExportBuilder` 脱敏（`20***01`）、无 token、**power 电费不导出**、`roster.rollNames`（名单原文）清空、`tt` 不导出；`privacy` 仓库 16 单测覆盖（含“导出文本不包含 sid/token”断言）。
- ✅ **导出卡隐私**：抽签/分组/体测导出内容与文件名不含学号/手机号/宿舍号（分组仅姓名+座号，页面本就展示）；电费/水量不提供导出入口；导出图仅写相册/分享。
- ✅ **清除云端**：userdata 空 v6 blob（upsert）、marks 逐条 `val=false`、fitrecs 逐条 `PATCH arch=true`，**全部无 DELETE**（契约）；部分失败逐项报告可读错误。
- ✅ **零外部请求**：主源码无第三方 URL；网络仅 `BuildConfig.REST_BASE_URL`（CloudBase 网关）；无 WebView / CDN / 统计 SDK；README/依赖表无第三方采集库。
- ✅ **本机数据隔离**：`FileClassMemberCache`/`FileUserDataLocalStore` 按 sid 分目录；退出登录清会话+名单缓存（业务数据按账号隔离）。
- ✅ 暴露的 anon publishable key 属设计公开（`gradle.properties` 的 `ct.publishableKey`，只进 BuildConfig，与 web 前端一致）；`key/release.jks`、`keystore.properties`、`local.properties` 均被 `.gitignore` 排除且 git 未跟踪（`git check-ignore` 实测）。

### 4.2 稳定性 / 网络 / 数据一致性

- ✅ 网络：OkHttp 连接/读/写 10s + callTimeout 20s、`retryOnConnectionFailure=false`；写请求不重试、GET 网络失败仅重试 1 次（`ApiClient.kt`）；401/403 统一映射（按 P2-10 修订更佳）；错误体解析 PostgREST。
- ✅ IO 未上主线程：OkHttp 在 `Dispatchers.IO`（`HttpTransport.kt:60`）；名单/用户数据文件读写 `withContext(Dispatchers.IO)`（`ClassMemberCache`、`UserDataLocalStore`）；附件图片 bounds 采样解码在 IO（`NoticeAttachmentPreview.kt:257,387`）；导出渲染 `Dispatchers.Default` + 落盘 IO（`ExportPresenter`）。
- ✅ userdata 合并安全（`UserdataMerge.kt:40-41,110`）：无基线不盲写（`UserDataRepository.kt:267` push skip）；合并 tie-break 云端优先（`ra >= la`）；`power` 特例、pending 落盘（`FileUserDataLocalStore`）+ 补发。
- ✅ 附件大 base64 不再常驻列表：`NoticesRepository.buildData` 清空 `row.body`/`group.items`，列表只留元信息；详情按需拉取（边界见 P1-6）。
- ✅ 状态恢复：抽签/分组 workflow `rememberSaveable + Saver`（task-16；手工验收 BACK 回 STEP3 通过）；身份输入用 `rememberSaveable`。
- ✅ 无 `runBlocking`/`GlobalScope`/主线程文件 IO；`!!` 仅集中在渲染器（均有 `drawing` 守卫）与既有强约束路径。
- ✅ 崩溃兜底：页面四态齐全（加载/错误/空/正常；逐屏核对 `ui/screens/*` 的 `EmptyState/CtLoading/Error` 使用）；导出/同步失败均有 Toast/错误条。

### 4.3 构建 / 发布配置

- ✅ `versionCode=2`、`versionName="1.0.1"`（`app/build.gradle.kts:25-26`）；minSdk 26 / targetSdk 37 / compileSdk 37。
- ✅ Release：`isMinifyEnabled=true` + `isShrinkResources=true` + proguard-android-optimize + 自有规则；`buildConfig` 仅 REST base + anon publishable key（无服务端密钥）；`signingConfig` 只从被忽略的 `keystore.properties` 读取密码（密码不写入仓库）。
- ✅ 本次审计实测 `assembleRelease` 成功、APK 2,274,622B、v2 签名证书与既有记录一致；`lintVitalRelease` 通过。
- ✅ 依赖面小（Compose/OkHttp/serialization/DataStore/navigation），无广告/统计/热更新 SDK。

### 4.4 用户可感知功能完整性

- ✅ 8 个工具 + 设置/数据隐私/身份门禁均为原生页面；toolbar/底部导航/宽屏 NavigationRail 自适应；`hw`/`nt`/`tt`/`power`/`water`/`roster`/`draw`/`group`/`fit` 路由均已接线，无占位页入口（P2-14 仅死代码）。
- ✅ 四态与主要错误提示已覆盖（通知/作业/课表/电费/水量/名册/体测/抽签/分组/隐私页均有 loading/empty/error 文案与重试）。
- ✅ E2E 26/26 PASS（含真实鉴权失败路径、导航/状态回归、Release 复验）；78 张 @Preview 覆盖浅/深色关键页。

---

## 5. 建议修复顺序（v1.0.1 发布 gate）

1. **先冻结 + 全绿基线**：停止并发改动 → `git commit` + `git tag v1.0.1` → 在 tag 上跑 `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleRelease`（P1-9）。
2. **发布配置/签名安全**：P1-1（禁 debug 签名兜底）、P1-2（R8 keep 包名）+ release 包实机冒烟（通知/作业/课表 JSON 解析）。
3. **合规/文案**：P1-4（备案号）、P1-3（退出确认）、P1-10（隐私政策/删除渠道确认）。
4. **深色/可读性**：P1-5（体测等级胶囊）+ 复查 P2-4（课表深色）。
5. **内存/稳定性边界**：P1-6（附件 10MB 上限）、P1-7（导出位图高度/内存阈值）。
6. **离线数据一致性**：P1-8（marks 落盘 pending）。
7. 其余 P2 按“性能 → 文档 → 清理”批量处理（P2-1/2、P2-18/19 成本低优先）。

---

## 6. 下一版发布检查清单（建议直接勾选执行）

- [ ] 主树冻结，`git status` 干净（无未提交改动），有 `v1.0.1` tag 与 commit hash 记录。
- [ ] `:app:testDebugUnitTest` = 0 failures（本轮 466/0 基线），`:app:assembleRelease` 成功。
- [ ] `apksigner verify --print-certs` 证书 SHA-256 = `974854df…bec4a1a`（与发布证书一致，非 debug 签名）。
- [ ] Release 包安装到模拟器：身份页无“开发预览”；用测试身份进入后抽查 通知/作业/课表/电费/水量/名册/抽签/分组/体测 各 1 次（JSON/导出/写接口）。
- [ ] 隐私抽查：导出我的数据 JSON 无 token/学号明文/电费；导出图无学号/手机号/宿舍号；清除云端二次确认+逐项结果正确；清除本机保留身份。
- [ ] 备案号/隐私政策/用户协议正式文本已确认并在「我的 → 关于」可查。
- [ ] E2E 最新轮全 PASS（26 steps 基线），截图/摘要归档到 `artifacts/e2e/`。
- [ ] README/RELEASE_BUILD 更新到 v1.0.1（版本、体积、测试/E2E 数字）；将 `artifacts/classtools-1.0.1-release-signed.apk` 交付。

---

## 7. 附录：审计方法与证据

- 方法：只读通读主源码（133 个 kt 文件）+ 关键路径 grep（Log/URL/Bitmap/IO/!!/runBlocking/TODO）+ 构建与签名实测；**未修改任何源码/构建文件**。
- 测试：`:app:testDebugUnitTest` @ 2026-11-08 01:38 → 37 classes / 466 tests / 0 failures（XML：`app/build/test-results/testDebugUnitTest/`；HTML 报告：`app/build/reports/tests/testDebugUnitTest/index.html`）。
- Release：`:app:assembleRelease` @ 01:40 BUILD SUCCESSFUL；APK `app/build/outputs/apk/release/app-release.apk` 2,274,622B；`apksigner verify --print-certs` 通过（v2）。
- E2E：`artifacts/e2e/summary.json`（result=PASS，26 steps，22 shots，01:19–01:26）。
- 预览：`ui/preview/` 78 张 @Preview；渲染脚本 `scripts/render_previews.ps1`（本次未重跑，沿用已渲染产物）。
- 审计期并发观察（保留证据）：01:35 release 构建因其他任务未完成改动失败（`ui/components/A11yFormat.kt:33 unresolved reference`），随后 test compile 因 `UserDataRepository.hydrateFromLocal()` 接口新增而 `DrawConfigStoreTest.FakeUserDataRepository` 未实现失败；01:38 主树恢复全绿。**这正说明发布窗口必须冻结主树**（P1-9）。

---

## 修复状态（2026-11-08 Round 4 收尾）

| P1 | 状态 | 处理 |
|---|---|---|
| 1 release 签名静默回退 debug | ✅ 已修 | pp/build.gradle.kts：无 keystore 时不再回退 debug 签名（保持未签名，由发布脚本校验） |
| 2 R8 keep 包名写错 | ✅ 已修 | proguard-rules.pro 全部改为 com.furina.classtools.** |
| 3 退出登录无二次确认 | ✅ 已修 | Profile 增加「退出登录？」确认弹层 |
| 4 备案信息占位 | ⏳ 外部依赖 | 需真实备案号（旅行者提供），文档保留待办 |
| 5 体测等级胶囊深色不可读 | ✅ 已修 | 浅色 pastel 底固定深色文字（0xFF17342D），深色主题可读 |
| 6 通知附件 base64 无上限 | ✅ 已修 | NoticeAttachmentParser.decodeBase64Payload 10MB 上限（解码前字号预判 + 解码后校验） |
| 7 导出位图高度无上限 | ✅ 已修 | ExportCardRenderer 预检：高度 >8192px 或面积 >12M px 时友好报错「导出内容过大…」 |
| 8 marks 缓存/pending 仅内存 | ⏳ 下版本 | 需本机持久化 marks 文件（跨进程杀恢复 + 补发），本轮未做 |
| 9 无 git commit/tag | ✅ 已修 | 工作区已 git init + commit + tag 1.0.1（key/keystore/artifacts 均忽略，不入库） |
| 10 隐私文本草稿/删除渠道 | ⏳ 外部依赖 | 需管理员确认正式文本与删除渠道；隐私政策页已含「联系班委/管理员」说明 |

验证：修复后 :app:testDebugUnitTest = **466/0**；ssembleRelease 成功（2,274,622 B / SHA-256 721350…44DD2）；Release 模拟器安装启动 0 FATAL；E2E 完整模式 **31 PASS / 0 FAIL**。