# Android UI 重构 + 通知去重 + 实时活动 — 独立验证记录（task-41）

> 状态：**代码审查 / 构建 / 单测 已完成**；**设备验证 / E2E / Release 复验待 Lead 通知**（当前模拟器由 Lead 占用，避免并发，本文档对应小节留空）。
> 工作树：`C:\Users\Ko_Li\.openclaw\workspace\android-class-tools`，HEAD `6268510`（v4.41.0/vc57 已提交），**当前工作树含未提交改动**，版本已 bump 为 **v4.42.0 / versionCode 58**（`app/build.gradle.kts` 未提交 diff）。
> 执行时间：2026-11-12 21:10–21:20（本机 gradle，未启动模拟器）
> 写范围：本文件 + `artifacts/e2e/`；未 git commit / tag。

---

## 0. 工作树变更面（git status）

修改（10 个，+2254 / -1288）：

| 文件 | 行数 | 关联 |
|---|---|---|
| `app/src/main/kotlin/.../data/notices/NoticesRepository.kt` | +10/-4 | 通知去重接入（task-38） |
| `app/src/main/kotlin/.../data/reminder/ReminderDiagnostics.kt` | +19/-19 | 删除测试通知 API（task-38） |
| `app/src/main/kotlin/.../data/reminder/ReminderScheduler.kt` | +28/-28 | 删除 `ReminderEngine.testNotification`（task-38） |
| `app/src/main/kotlin/.../ui/screens/notices/NoticesScreen.kt` | +1877（重写） | 通知页 UI 重写（task-39/40） |
| `app/src/main/kotlin/.../ui/screens/profile/ProfileScreen.kt` | +518/-283 | 我的页 UI 重写（task-39） |
| `app/src/main/kotlin/.../ui/screens/settings/ReminderSettingsScreen.kt` | +56/-56 | 删除测试通知按钮（task-38/39） |
| `app/src/main/kotlin/.../ui/screens/guide/UserGuideScreen.kt` | +682 | 使用说明页重写（task-40） |
| `app/src/test/kotlin/.../data/reminder/ReminderEngineTest.kt` | -31 | 删除测试通知用例（task-38） |
| `scripts/e2e_smoke.ps1` | +34/-34 | E2E 适配新 UI（见 §1.7） |
| `app/build.gradle.kts` | +4/-4 | 版本 4.41.0/vc57 → 4.42.0/vc58 |

新增（未跟踪，11 项，均为本轮功能/预览/测试/文档）：

- `data/notices/NoticeDedupe.kt`（数据层去重，核心）
- `ui/screens/notices/NoticeDetailSheet.kt`（通知详情底部弹层）
- `ui/screens/guide/GuideContent.kt`、`GuideScenes.kt`（使用说明内容/场景）
- `ui/preview/NoticeRedesignPreviews.kt`、`ProfileRedesignPreviews.kt`
- `test/.../data/notices/NoticeDedupeTest.kt`、`NoticesDedupeIntegrationTest.kt`、`test/.../ui/screens/guide/UserGuideContentTest.kt`
- `docs/api/NOTICES_DEDUPE_IMPL.md`、`docs/design/MP_UI_ALIGN.md`（队友交付文档）

---

## 1. 代码审查结论

### 1.1 通知去重（数据层，task-38）— PASS

**实现**：`data/notices/NoticeDedupe.kt`（新文件）在数据层做两层稳定去重：

1. **同一 group id**：直接合并（`sameLogical` 中 `a.id == b.id` 即命中），保留 supplies/items/mergedSources 并集；
2. **不同 group id 但同一逻辑通知**：规范化标题一致 + 分类一致 + 时间锚点差 ≤ `SAME_EVENT_WINDOW_MS`（2h） + **强证据**（同来源 / 条目指纹重叠 / mergedSources 交集 / 同一发送人）；无结构化时间的通知要求「同来源 + 内容或合并来源重叠」，宁可漏合不误合；auto 与 manual 不互相合并；
3. 合并后条目按内容指纹去重（`fingerprintOf`：text/img/file/form/link/merged），保留 supplements 追加顺序；合并条目上限 `MAX_MERGED_ITEMS = 64`；
4. **人工通知**按「规范化标题 + 正文 + 10 分钟内」保守去重（`MANUAL_WINDOW_MS`）。

**接线**：`DefaultNoticesRepository.buildData()`（`NoticesRepository.kt` diff）在应用 hidden/override 之后执行：

```kotlin
val dedupedAuto = NoticeDedupe.dedupeAuto(auto).entries.sortedByDescending { it.ts }
val dedupedManual = NoticeDedupe.dedupeManual(manual).entries.sortedByDescending { it.ts }
return NoticesData(auto = dedupedAuto, manual = dedupedManual, ...)
```

**UI 无本地重复逻辑**：`NoticesScreen.kt` 明确注释「UI 模型（数据层 → 卡片/详情渲染；不在 UI 侧做任何分组/合并，避免重复展示）」「不做任何本地分组/合并/去重；同一通知是否重复由数据层负责」（约 L132 / L150 / L362）。全文件无 `dedupe|distinct|groupBy` 等本地归并逻辑。

**测试**：`NoticeDedupeTest` **18** 个 `@Test`（314 行：同 id 合并、同逻辑合并、不同类/标题/时间不合并、无结构化时间强证据、条目指纹去重、mergedSources 并集、人工 10 分钟窗口等）；`NoticesDedupeIntegrationTest` **5** 个 `@Test`（167 行：仓库层端到端去重 + 排序）。合计 **23 个去重用例**，与 `docs/api/NOTICES_DEDUPE_IMPL.md` 记载一致。

### 1.2 测试通知已移除（task-38）— PASS（主源码 0 引用）

| 位置 | 变更 |
|---|---|
| `ReminderDiagnostics.kt` | 删除 `ReminderTestNotificationResult` sealed interface；`ReminderActions.testNotification(...)` 删除；KDoc 改为「通知诊断」 |
| `ReminderScheduler.kt` | 删除 `ReminderEngine.testNotification(kind)`（真实渠道 + PendingIntent 的测试通知发送逻辑） |
| `ReminderSettingsScreen.kt` | 删除「发送测试通知」按钮/`testingNotification` 状态/`ReminderTestNotificationResult` 分支；区块改名 **「检查与诊断」**（文案改为「立即检查本机通知链路…」），保留 **「立即检查 + 诊断」** 单一按钮 |
| `ReminderEngineTest.kt` | 删除 3 个测试通知用例（31 行） |
| `scripts/e2e_smoke.ps1` | 删除 `NotifTestTitle`/`NotifTestBtn` 断言，保留 `NotifDiagBtn`（立即检查 + 诊断）断言 |

**验证**：`app/src/main/**/*.kt` 中 `ReminderTestNotificationResult|testNotification|发送测试通知|测试通知` 引用数 = **0**（grep 精确检查）。

**历史残留（非活动代码，建议保留）**：
- `app/src/test/.../ReminderEngineTest.kt:25` KDoc 注释仍提到「测试通知」（注释过时，P3）；
- 测试夹具与文档中的「测试通知」字样（`NoticeStatusMachineTest` 样例标题、`NoticeAttachmentParserTest` fixture、`docs/CHANGELOG.md`/`docs/E2E_SMOKE.md`/`docs/RELEASE_BUILD.md` 历史章节、`docs/REPORT_ROUND8_20261108.md`）——均为历史记录/样例数据；
- `_backup_diag_login_20261108/`（**已 tracked 的历史快照目录**）仍含旧 `testNotification` 实现；不影响 `app/src` 构建（不在 sourceSets 内）。

### 1.3 我的页 UI 重写（task-39）— PASS

`ProfileScreen.kt` +518/-283，新结构（顶部注释与实现一致）：

1. 身份卡（昵称/角色 pill/管理员标记）；
2. 通知权限提示（可选、可关闭，`NotificationPermissionBanner`）；
3. **「数据与同步」**：我的数据 2×2 指标（待交作业/已完成作业/抽签记录/体测记录 + 附加指标）+ 云同步开关 + 数据与隐私 / 清除本机缓存（二次确认）；
4. **「通知与权限」**：权限管理 / 提醒设置（副标题实时显示提醒状态）；
5. **「外观」**：壁纸取色（Material You）开关 / 深浅色跟随系统；
6. **「关于与帮助」**：反馈建议 / 使用说明 / 用户协议 / 隐私政策 / 版本。

平板 ≥840dp 双栏（左：身份+数据与同步；右：通知与权限+外观+关于+退出）。行为变化：原「SettingActionRow + 管理/设置 actionText」改为**整行可点**（E2E 已同步改为 `Tap-UiText -Label <row>`，见 §1.7）。

### 1.4 通知页 UI 重写（task-39/40）— PASS

`NoticesScreen.kt` 重写（+1877），`NoticeDetailSheet.kt` 新增：

- **筛选 chips**：`全部 N / 未处理 N / 置顶 N`（`NoticeFilter.ALL/PENDING/PINNED`），顶栏搜索（标题/摘要）；
- 列表：分类语义色 + 状态色点 + 标题 + 摘要 2 行 + 来源/附件/合并/已更新标记；「已过期」默认折叠段（仅「全部」筛选且无搜索词时）；
- **详情底部弹层**：AI 摘要 / 字段分段 / 图片网格 / 附件 / 链接 / 合并消息 / 人工通知正文（`NoticeDetailSheet`，FlowRow + 滚动）；
- **只消费数据层结果，无本地去重**（见 §1.1）；已处理项视觉弱化用色阶而非整体透明度。

### 1.5 设置工具入口 → 「我的」Tab（task-39）— PASS

- `ui/navigation/MainNavHost.kt` L197-199：

```kotlin
when (tool.id) {
    "settings" -> selectTab(2)   // 我的 Tab（与小程序一致，不设独立占位页）
    else -> navController.navigate(Routes.tool(tool.id))
}
```

- `data/tool/ToolCatalog.kt` L162-165：`id="settings" / name="设置"` 卡片保留在「全部工具」。
- E2E `settings_redirect` 步骤：全部工具点「设置」→ 断言落到「我的」（外观行/关于可见）且**无占位页**（`PlaceholderPill` 不存在）→ 点底栏「工具」返回。
- 结论：设置工具不再打开独立设置页/占位页，直接切「我的」Tab。

### 1.6 重复功能残留检查 — PASS（1 项 P3）

- 权限管理 / 提醒设置 / 数据与隐私：`ProfileScreen` 各出现 **1 次**（L500/L507/L453）；`PermissionSettingsScreen.kt` 内的「提醒设置」是权限页→提醒页的二次导航（正当，非重复入口）。
- 通知去重仅在数据层（`NoticeDedupe`），UI 无第二套合并逻辑（§1.1）。
- **P3**：`ui/screens/me/MeStatsSection.kt` 的 `MeStatsSection` 组件在 Profile 重写后**生产代码已不再使用**（仅 `ui/preview/ParityP1Previews.kt` 引用）；Profile 现内联实现「我的数据」指标。属旧迭代死代码，建议后续删除或让 Profile 复用（不影响功能）。
- 无其他重复实现/旧入口残留。

### 1.7 E2E 脚本适配（scripts/e2e_smoke.ps1，+34/-34）— PASS

- 我的页行动作变更：`Tap-ActionNearLabel(... "管理"/"设置")` → `Tap-UiText -Label <行标签>`（数据与隐私/权限管理/提醒设置/清除本机缓存/使用说明/反馈建议）；
- `Get-ScreenKind` 的 PROFILE 判定由「权限与提醒」卡片标题改为**「通知与权限」区块标题**（`ProfileSection`），pager/dock/dark 组同步替换；
- 删除测试通知相关断言（§1.2），保留「立即检查 + 诊断」；
- `settings_redirect` 步骤保持（§1.5）。
- 未使用残留：`NotifTestTitle`/`NotifTestBtn` 两个 label 定义仍保留但已无引用（P3，可删）。

### 1.8 实时活动（task-38/39/40 代码存在性，设备部分待补）

`data/notify/LiveUpdateNotifier.kt` 存在：渠道「实时活动」、`setRequestPromotedOngoing(true)`（上传进度/限时投票倒计时等）、Debug 构建「实时活动测试」入口（60s 倒计时）。`PptListScreen` 已接入。**`dumpsys` flags=PROMOTED_ONGOING 的实际验证属 task-41 设备部分（待 Lead 通知后执行）。**

---

## 2. 构建与单测（本机执行，未占用模拟器）

```
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
set JAVA_HOME=C:\AndroidToolchain\jdk17
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain --no-daemon
```

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | **BUILD SUCCESSFUL in 1m 17s**（45 tasks，16 executed）；debug APK **24,911,087 B** @ 2026-11-12 21:12:33 |
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL**；**1098 tests / 0 failures / 0 errors / 0 skipped**（88 个测试结果 XML）——与 Lead 预期 **1098/0 一致** |
| 编译告警 | 2 条 deprecated 图标（`ProfileScreen.kt:563 Icons.Rounded.MenuBook`、`:576 Icons.Rounded.Article`，建议换 AutoMirrored，P3）；无其他 error/warning |
| 退出码 | 0 |

> 数字核对方式：`app/build/test-results/testDebugUnitTest/*.xml` 汇总 `tests=1098, failures=0, errors=0, skipped=0`（88 个 suite）。

---

## 3. 审查发现汇总

| 级别 | 项 | 说明 / 建议 |
|---|---|---|
| P0/P1/P2 | 无 | 主源码无测试通知残留、无重复功能实现；构建 + 1098/0 单测通过 |
| P3-1 | E2E 两个未使用 label | `NotifTestTitle`/`NotifTestBtn` 定义无引用，可删 |
| P3-2 | `ReminderEngineTest.kt:25` KDoc 提「测试通知」 | 注释过时，建议更新为「诊断字段/错过补发」 |
| P3-3 | `MeStatsSection.kt` 生产未使用 | 仅预览引用，建议删除或 Profile 复用 |
| P3-4 | `_backup_diag_login_20261108/` tracked 历史快照 + docs 历史章节含旧测试通知 | 历史记录，构建不读取；建议后续归档移出仓库（不影响本轮） |
| P3-5 | scripts/e2e_smoke.ps1 不再纯 ASCII | 9 行含非 ASCII（513 字节：6 行中文注释 + DiagDetail/DiagSimple 注释 + 1 条控制台输出含「详细」）；当前 E2E 用 pwsh 7 执行无问题，若需 PS 5.1 兼容建议注释改 ASCII。AST 0 错误、1647 行、42 个 Add-Step（+install 步 = 快速 43 项） |
| 观察 | 版本号 | 工作树未提交 bump 到 **v4.42.0/vc58**（基线 HEAD 为 v4.41.0/vc57），与 Lead 集成一致 |
| 观察 | Debug 实时活动入口 | ProfileScreen.kt 含「实时活动测试（Debug）」「关闭实时活动（Debug）」两行（L589/L596），阶段 2 用于 dumpsys flags=PROMOTED_ONGOING 验证 |

---

## 4. 待补：设备验证 / E2E / Release（等 Lead 通知，当前模拟器由 Lead 占用）

- [ ] 设备验证（ct-api37，debug 安装）：我的页新布局 / 通知页筛选+详情 / 工具「设置」→「我的」Tab / 无测试通知入口 / 通知列表无重复（测试身份或现有 E2E 路径）→ 截图 `artifacts/e2e/`
- [ ] 快速 E2E 43 项（`-Install -SkipWater`，约 17 min）：43/0、导航 10/10、logcat 0 FATAL/ANR
- [ ] Release（Lead 提供 v4.42.0 APK 后）：安装 / 两次冷启动 / logcat FATAL/ANR / versionName=4.42.0
- [ ] 实时活动：Debug「实时活动测试」→ `dumpsys notification` 确认 `flags=...PROMOTED_ONGOING...`（若 debug 入口保留）
- [ ] 完成后追加本节结果并 send_message 给 Lead

## 5. 约束执行情况

- **未 git commit / tag**（Lead 统一提交）；
- **未启动/占用模拟器**（Lead 当前占用）——仅本机 gradle 构建与单测；
- 写操作仅本文件（+ 后续设备阶段 `artifacts/e2e/`）；未改业务源码/构建文件。


---

## 6. 阶段 2（v4.42.0 debug）设备验证 / E2E 记录（2026-11-12 21:20–22:20）

### 6.1 设备检查（Debug v4.42.0 / vc58，ct-api37 1080×2400@420）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 我的页新版分组 | PASS：`数据与同步` / `通知与权限` / `外观` / `关于与帮助` 区块均渲染 | `artifacts/e2e/8x_my_profile_top.png` |
| 我的页无学号标签 | PASS：dump 中无「学号」字段（仅 `账号 00***00` 脱敏显示） | 同上 |
| 退出登录不被底部浮岛遮挡 | PASS：signOutBottom=1853 ≤ dockTop=2153（未覆盖） | `artifacts/e2e/8x_my_profile_bottom.png` |
| 全部工具「设置」→「我的」Tab（无占位） | PASS：landedProfile=True，placeholder=False | `artifacts/e2e/24_settings_redirect.png（E2E 同时校验落我的页）` |
| 提醒设置页无「发送测试通知」入口 | PASS：有「提醒总开关」，无「发送测试通知」文本 | `artifacts/e2e/29_reminder.png`（E2E 提醒页：无「发送测试通知」） |
| 通知页筛选 chips（全部/未处理/置顶） | PASS：chips=all,pending,pinned（本机缓存空态「暂无通知」） | `artifacts/e2e/8x_notices_filters.png` |
| 通知详情弹层 | 无法实测：debug 测试身份本地缓存无通知数据（页面空态）；详情 UI 由 `NoticeRedesignPreviews` + 单测覆盖，待真实数据/正式包复核 | （无通知数据，未截到详情；已注明） |
| 通知列表无重复 | 数据层去重已由 23 个单测覆盖；设备侧当前无真实通知数据，暂无法做视觉对比 | 单测 1098/0 |

### 6.2 快速 E2E（`-Install -SkipWater`）

- 第 1 轮：713.9s，PASS=31 / FAIL=1，失败于 `reminder_settings`（权限页 row 点击未导航，脚本适配问题）。
- 第 2 轮：797.6s，PASS=33 / FAIL=1，失败于 `clear_cache_confirm`（reminder 步骤双 BACK 退出 App、通知栏残留）。
- 第 3 轮：616.8s，PASS=26 / FAIL=1，失败于 `privacy_page` 断言（旧 `隐私说明` 标记在新页不存在，脚本适配问题）。
- 第 4 轮：789.8s，PASS=34 / FAIL=1，失败于 **`user_guide` 真实崩溃**（见 §6.3 P0）。
- 脚本小修（均属新 UI 适配，不改业务源码）：
  1. `reminder_settings`：从权限页先 BACK 回「我的」再点「提醒设置」行（原先是权限页 row 点击，不导航）；
  2. `notify_banner` / `clear_cache_confirm`：补 `Get-ScreenKind` 守卫 + 先回「我的」页，避免上一轮残留的后退/通知栏导致滑到桌面；
  3. reminder 步骤去掉多余 BACK（原双 BACK 在根页退出 App）；
  4. `privacy_page` 取消后断言改为「对话框消失 + 清除云端数据行可见」（旧 `隐私说明` 标题在新页已移除）。
- 前 34 步全部 PASS（含 install/identity/auth/pager/dock 0px/all_tools/抽签导出/各工具/投票/名单三分类/隐私页/外观/权限/提醒/通知 banner/清缓存确认）。

### 6.3 P0：使用说明页崩溃（真实缺陷，已上报）

- 现象：我的 → 使用说明（或 E2E `user_guide`）→ App 立即崩溃退出到桌面；两次复现（22:11、22:14）。
- 栈：
  ```
  java.lang.NoClassDefFoundError: Failed resolution of:
    Lcom/furina/classtools/ui/screens/guide/GuideScenesKt;
    at UserGuideScreenKt.GuideCardPage$lambda$0(UserGuideScreen.kt:277)
  ```
- 根因（构建产物层面）：clean 重建后的 debug APK 中，`classes14.dex` 只定义 `GuideScenesKt$WhenMappings` / `GuideScene` / `GuideTone` 等，**缺少外层主类 `GuideScenesKt`**；D8 输出不完整 → 运行时解析失败。
- 触发条件：`:app:dexBuilderDebug FROM-CACHE`（Gradle build cache 命中）；`:app:clean` **不会**清理 Gradle 本地 build cache，所以 clean 重建仍复现。
- 处理建议：debug/release 构建均加 `--no-build-cache`（或清除 `%USERPROFILE%\.gradle\caches\build-cache-1`），构建后用 dexdump/apkanalyzer 校验 `GuideScenesKt` 与 `$WhenMappings` 同时存在。
- 证据：`artifacts/e2e/task41_guide_crash_logcat.txt`、`artifacts/e2e/task41_guide_crash_cleanbuild_logcat.txt`、`artifacts/e2e/failure.png`、`failure_ui.xml`。
- 状态：已上报 Lead；**待 Lead 提供 no-build-cache 重建的 debug APK 后重跑 E2E 43 项**。

### 6.4 状态：待续

- [ ] no-build-cache 重建 debug → 重装 → 使用说明页单点验证（截图 `8x_guide_v442.png` 更新为正常页）
- [ ] 快速 E2E 43/0（含 user_guide/feedback/settings_redirect/dark/logcat/导航 10/10）
- [ ] 实时活动：「我的 → 关于与帮助 → 实时活动测试（Debug）」→ `dumpsys notification --noredact` 断言 `flags=...PROMOTED_ONGOING...`（Lead 指示 Release 由 Lead 负责，本轮不安装 release）

---

## 7. 阶段 2 最终结果（2026-11-12 22:2x–22:5x，v4.42.0 / vc58）

### 7.1 P0 修复复验（使用说明崩溃）

- Lead 按 `--no-build-cache` 重建 debug：**24,943,855 B / SHA-256 `69BC85F74BDB04A446FAFD0A819CB93E8363DD78364E6D41880D61C2C4B103B9`**（22:21 构建，无 FROM-CACHE；Lead 已解包校验 `classes14.dex` 同时含 `GuideScenesKt` 与 `UserGuideScreenKt`）。
- 我覆盖安装后单点验证：我的 → 使用说明 → **正常打开**（`使用说明 / 左右滑动切换 · 一键总览 / 总览 / 入门 / 学习 / 生活 / 协作 / 更多 / 1 / 20 / 身份核验 / … / 上一张 / 下一张`），**无 FATAL**，前台仍是 `com.furina.classtools.debug/.MainActivity`。
- 截图：`artifacts/e2e/31_guide.png`（E2E 卡片轮播）、`8x_guide_v442.png`（单点复验）（480,555 B）。
- 结论：P0 已修复；根因为 Gradle build cache 中的不完整 dex（`:app:dexBuilderDebug FROM-CACHE`），`clean` 不清 build cache，故必须 `--no-build-cache`（或清 `%USERPROFILE%\.gradle\caches\build-cache-1`）。该结论已同步 Lead（Release 由 Lead 以 no-build-cache 重建，SHA `541A0CAF…`）。

### 7.2 快速 E2E（最终轮）— **43/0 全绿**

命令：`pwsh -NoProfile -File scripts/e2e_smoke.ps1 -Install -SkipWater`

| 指标 | 结果 |
|---|---|
| 结果 | **PASS=43 WARN=0 FAIL=0**（install + 42 步） |
| 总耗时 | 1,075.3s（约 17.9 min） |
| 导航探针 | **10/10 ok, 0 ignored** |
| logcat | **no FATAL / ANR**（`logcat_no_fatal` PASS） |
| 导出画廊 | 99 → 100（+1 png） |
| 截图 | 47 张（`artifacts/e2e/`，含 `31_guide.png`、`30_feedback.png`、`24_settings_redirect.png`、`7x_*`、`4x_dark_*` 等） |
| 日志 | `C:\AndroidToolchain\work\e2e_v442_fast_final.log`；`artifacts/e2e/summary.json` |

关键步骤明细：install / identity_gate / auth_failure_path / workbench / pager_swipe / dock_overlay(0px) / all_tools / draw 三步+导出 / 8 个工具页 / timetable_v3 / diag_page / tool_vote / roster_categories(segments=True) / power_v4 / privacy_page / privacy_back_home / appearance_about / appearance_persist / permission_management / reminder_settings / notify_banner(banner=True) / clear_cache_confirm / **user_guide（card carousel: first card + next=2）** / feedback_page / settings_redirect / back_to_workbench / nav_probe_10x / home_quick_edit / dark_mode_group / logcat_no_fatal / gallery_export_file。

E2E 脚本本轮适配（均新 UI 适配，不改业务源码）：
1. `reminder_settings`：权限页先 BACK 回「我的」再点「提醒设置」行；
2. reminder 步骤去掉多余 BACK（双 BACK 在根页退出 App）；
3. `notify_banner` / `clear_cache_confirm`：补 PROFILE 守卫 + 先回「我的」；
4. `privacy_page` 取消后断言改为「对话框消失 + 清除云端数据行可见」；
5. `user_guide`：旧「>=3 个 section」改为新卡片轮播断言（首卡「身份核验」+ 左滑后第二卡「工作台」，共 20 卡）；
6. `Invoke-AppLaunch` 注释/环境复位（状态栏收起由外部复位处理）。

### 7.3 实时活动（Debug）— PASS

- 通知权限：`pm grant ... POST_NOTIFICATIONS` = granted
- 路径：我的 → 关于与帮助 → 「实时活动测试（Debug）」→ 60 秒倒计时通知
- `dumpsys notification --noredact` 证据（完整见 `artifacts/e2e/task41_live_update_dumpsys_full.txt`，摘要 `task41_live_update_summary.txt`）：
  - `pkg=com.furina.classtools.debug ... id=7403 ... channel=ct_live_updates`
  - **`flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`**
  - `originalFlags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`
  - `effectiveNotificationChannel=... mId='ct_live_updates', mName=实时活动 ...`
  - `android.title=String (实时活动测试)` / `android.bigText=String (60 秒倒计时 · 用于验证实时活动/流体云/胶囊展示)`
- 截图：`artifacts/e2e/8x_live_update_test.png`（619,657 B）。
- 结论：Android 16 实时活动/流体云升级请求生效（PROMOTED_ONGOING）。

### 7.4 Release（Lead 验证，clean + no-build-cache）

- APK：`artifacts/classtools-4.42.0-release-signed.apk`，**3,075,869 B / SHA-256 `541A0CAF5E669B8795605DD129CFB17E8FE18A358A0CC698546010DB48360761`**；`versionCode=58 / versionName=4.42.0`；apksigner V2 通过（同历史证书 `974854df…4a1a`）。
- 安装：`pm clear` 后 `am start -W` 两次：**1.81s / 1.06s**（首次冷启动含 verify 编译）；随后 3 次稳定冷启动 **0.88s / 0.78s / 0.79s**。
- logcat：**0 FATAL / 0 ANR**；截图 `artifacts/v4420-release-identity.png`。
- APK 干净重建说明：`--no-build-cache` 重建解决 Gradle build cache 导致的 dex 缺类（`GuideScenesKt`）P0；Release 为 R8 全量重建（dex 2 个），R8 mapping 含 GuideScenesKt 方法引用，使用说明页在 Debug 运行验证不崩溃。

### 7.5 环境状态（交接）

- `emulator-5554`（ct-api37）**保持在线**（供 Lead 做 release 冷启动/logcat 验证），debug v4.42.0 已安装且通知权限已授予。
- 已点亮的「实时活动测试」通知 60 秒后自动结束；无需清理。
- 本轮所有产物在 `android-class-tools/artifacts/e2e/`；未 git commit/tag；未修改业务源码（仅 `scripts/e2e_smoke.ps1` 新 UI 适配与 `docs/UI_REDESIGN_VERIFY.md`）。

