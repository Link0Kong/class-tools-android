# E2E 冒烟脚本与模拟器回归验收（E2E_SMOKE.md）

> 适用工程：`android-class-tools`（班级工具站 Android 原生客户端）
> 脚本：`scripts/e2e_smoke.ps1`（纯 ASCII PowerShell，文本驱动点击，约 960 行）
> 环境：Windows 11 + 模拟器 AVD `ct-api37`（Android 17 / API 37，x86_64）+ JDK17 + Gradle 9.9.0
> **最终验收（2026-11-08 01:10–01:28，Round 3 收尾）**：
> - 完整模式 `-Build -Install`：**28 PASS / 0 WARN / 0 FAIL，391.3 s，退出码 0**
> - 快速模式（无参）：**26 PASS / 0 WARN / 0 FAIL，383.3 s，退出码 0**
> - Release 复验：**BUILD SUCCESSFUL + apksigner v2 验签通过 + 模拟器安装启动无 FATAL**（详见 `docs/RELEASE_BUILD.md` §6）
> - 导航稳定性：**「全部→工作台」标签 10/10 成功**（此前的吞点击缺陷已由 Lead 修复）

---

## 1. 定位与用法

**定位**：不写业务代码的黑盒验收 —— 构建 → 安装 → 用 uiautomator 文本驱动真实点击走完主链路 + 全部工具页 + 真实鉴权失败路径 + 数据隐私页 → 每步截图 → 回归检查（无 FATAL/ANR、导出文件确实进相册、导航稳定性）。

### 1.1 依赖

| 依赖 | 说明 |
|---|---|
| 工具链 | JDK17 `C:\AndroidToolchain\jdk17`、SDK `C:\AndroidToolchain\sdk`、Gradle `gradle-9.9.0`（详见 `docs/TOOLCHAIN.md`） |
| 模拟器 | AVD `ct-api37`（Android 17，`TOOLCHAIN.md` §8）；未运行脚本会自动拉起并等 `sys.boot_completed=1` |
| adb | `C:\AndroidToolchain\sdk\platform-tools\adb.exe` |
| 工程 | `<repo>\gradlew.bat`；构建加 `--no-daemon`（避免 daemon 继承 stdout 管道导致外部调用挂起），并发构建导致增量缓存冲突时自动 `clean` 重试一次 |

### 1.2 命令

```powershell
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools

# 完整验收（构建 + 安装 + 全链路 + 工具页 + 鉴权失败 + 隐私页 + 导航探测）
pwsh -NoProfile -File .\scripts\e2e_smoke.ps1 -Build -Install

# 快速回归（复用已装 APK，日常推荐）
pwsh -NoProfile -File .\scripts\e2e_smoke.ps1

# 自定义截图目录 / 保留已登录数据
pwsh -NoProfile -File .\scripts\e2e_smoke.ps1 -Screens D:\e2e
pwsh -NoProfile -File .\scripts\e2e_smoke.ps1 -KeepData
```

### 1.3 参数

| 参数 | 说明 |
|---|---|
| `-Build` | 先执行 `gradle :app:assembleDebug`（APK 缺失时自动构建）。失败即退出并保留 `build.log` |
| `-Install` | 强制 `adb install -r` 新 APK；不带时使用当前已安装版本 |
| `-Screens <dir>` | 截图/日志输出目录，默认 `<repo>\artifacts\e2e` |
| `-KeepData` | 不清 App 数据（默认 `pm clear`，保证每次从身份页开始） |
| `-BootTimeoutSec` | 模拟器未运行时等待启动的上限秒数，默认 180 |

### 1.4 产物（artifacts/e2e/）

| 文件 | 内容 |
|---|---|
| `01_identity.png` … `22_draw_to_group.png` | **22 张分步截图**（均 1080×2400，另含 `release_launch.png`） |
| `summary.json` | 机器可读结果：`result` / `durationMs` / `steps[]` / `screenshots[]` / `gallery` / `logcatProblems` / `navProbe` |
| `run.log` / `build.log` / `logcat_run.txt` / `gallery_listing.txt` | 运行时间线 / 构建日志 / 全量 logcat / 相册清单 |
| `release_build.log` / `release_apksigner_verify.txt` / `release_logcat.txt` / `release_ui.xml` | Release 复验证据 |
| `failure.png`、`failure_ui.xml`、`failure_logcat.txt` | **仅失败时**：失败瞬间截图 / UI 层级 / logcat 尾部关键行 |
| `nav_probe_fail_N.xml` | 导航探测失败时保存的 UI dump（本次 10/10 通过，无残留） |

> 每次运行开始清空旧 `*.png` / `failure_*` / `nav_probe_fail_*` / `summary.json`，目录只反映最新一次运行。

---

## 2. 覆盖范围与最终实测（完整模式 28 项，按耗时）

| # | 步骤 | 驱动动作 / 断言 | 结果 | 耗时 |
|---|---|---|---|---|
| 1 | `build` | `gradlew :app:assembleDebug --no-daemon`，exit 0 | PASS | 7.9 s |
| 2 | `install` | `adb install -r`，输出含 Success | PASS | <1 s |
| 3 | `identity_gate` | `pm clear`→`am start`，出现「开发预览」 | PASS | 6.2 s |
| 4 | `auth_failure_path` | 勾选同意 + 学号 0000000000/姓名 Test → 提交 → 真实 class_verify 返回「姓名或学号不在班级名单中」 | PASS | 19.8 s |
| 5 | `workbench` | 点「开发预览」→「常用工具」+「今日」 | PASS | 10.3 s |
| 6 | `all_tools` | 点底部「全部」→「全部工具」 | PASS | 8.2 s |
| 7 | `draw_step1` | 点「抽签」→「抽签参数」 | PASS | 6.1 s |
| 8 | `draw_step2` | 「下一步：预览候选」→「候选名单」+70 候选 | PASS | 6.1 s |
| 9 | `draw_step3` | 「开始抽签」→「抽签结果」 | PASS | 6.2 s |
| 10 | `export_image` | 「导出抽签图」→ 相册 PNG +1 | PASS | 8.7 s |
| 11 | `draw_result_actions` | 断言「导出结果单」存在 →「用此结果分组」→ 分组页 → **BACK 回到 STEP3 抽签结果页**（task-16 修复回归） | PASS | 14.9 s |
| 12 | `back_to_all_tools` | 系统 BACK 回「全部工具」 | PASS | 3.8 s |
| 13 | `tool_hw` | 点「作业」→ 标题+四态 | PASS | 8.2 s |
| 14 | `tool_nt` | 点「通知」→ 标题「班级通知」+ 四态 | PASS | 8.1 s |
| 15 | `tool_tt` | 点「课表」→ 标题+「暂无课表数据/教务快照为空/网络异常/格式异常」任一 | PASS | 8.0 s |
| 16 | `tool_power` | 点「电费」→「宿舍电费」+「关注一个宿舍」空态 | PASS | 8.1 s |
| 17 | `tool_water` | 点「水量」→「宿舍水量」+「还没有关注宿舍」空态/加载/错误 | PASS | 8.0 s |
| 18 | `tool_group` | 点「分组」→「分组」+「1 配置」 | PASS | 8.0 s |
| 19 | `tool_fit` | 点「体测」→「体测」+「未填项按…」评分卡 | PASS | 8.2 s |
| 20 | `tool_roster` | 点「名册」→「名册 · 点名」+「名册/点名」页签切换（截图 19/20_roster_roll） | PASS | 14.2 s |
| 21 | `tool_pages` | 上述 8 页巡览汇总 | PASS | 97.3 s |
| 22 | `privacy_page` | 我的→「管理」→「数据与隐私」→ 点「清除云端数据」→ 出现「确认清除」二次确认 → **点「取消」**（未真清除） | PASS | 23.6 s |
| 23 | `privacy_back_home` | 隐私页返回 → 我的 → 工作台 | PASS | <0.1 s |
| 24 | `back_to_workbench` | 系统 BACK/导航回工作台（出现「常用工具」） | PASS | 2.9 s |
| 25 | `nav_probe_10x` | 10 轮「全部→点工作台→断言回到工作台」 | **PASS 10/10** | 136.9 s |
| 26 | `home_quick_edit` | 工作台「编辑」→「选择首页快捷」+「已选 4 / 4」→「完成」回工作台 | PASS | 15.4 s |
| 27 | `logcat_no_fatal` | 全量 logcat 无 FATAL/ANR/Force finishing | PASS | — |
| 28 | `gallery_export_file` | 相册 PNG 运行后 +1 | PASS | — |

**完整模式总 391.3 s（28 项）；快速模式 383.3 s（26 项，跳过 build/install）。两次均 0 FAIL / 0 WARN。**

### 2.1 最终截图清单（22 张，均 1080×2400；当前 `artifacts/e2e/` 来自快速模式）

| 文件 | 字节 | 对应步骤 |
|---|---|---|
| `01_identity.png` | 560,583 | 身份门禁 |
| `20_auth_error.png` | 571,110 | 真实鉴权失败（红色错误文案） |
| `02_workbench.png` | 550,857 | 工作台 |
| `03_all_tools.png` | 429,867 | 全部工具 |
| `04_draw_step1.png` | 450,264 | 抽签 STEP1 |
| `05_draw_step2.png` | 470,894 | 抽签 STEP2 |
| `06_draw_step3.png` | 469,836 | 抽签 STEP3 |
| `07_export.png` | 420,705 | 导出后 |
| `22_draw_to_group.png` | 398,787 | 抽签结果→分组页（联动） |
| `08_back_all_tools.png` | 428,837 | 返回全部工具 |
| `11_hw.png` | 519,787 | 作业页 |
| `12_nt.png` | 504,407 | 通知页 |
| `13_tt.png` | 490,388 | 课表页（暂无数据态） |
| `14_power.png` | 566,555 | 电费页（空态） |
| `15_water.png` | 472,635 | 水量真实页（关注宿舍空态） |
| `16_group.png` | 398,516 | 分组页 |
| `17_fit.png` | 301,049 | 体测页 |
| `19_roster.png` | 475,407 | 名册页 |
| `20_roster_roll.png` | 472,562 | 名册→点名页签 |
| `21_privacy.png` | 488,055 | 数据与隐私 + 清除云端二次确认（已取消） |
| `09_home_after_back.png` | 550,757 | 返回工作台 |
| `18_home_edit.png` | 550,613 | 首页常用工具编辑态 |
| `release_launch.png` | 531,526 | Release 包启动（身份页，无开发预览入口） |

### 2.2 导出与 logcat 回归

| 运行 | 相册 | 新增文件 | logcat |
|---|---|---|---|
| 完整模式 | 18 → 19 | `抽签结果_20261107-1711_范围1-70_抽10个_不重复_按抽取顺序.png`（114,710 B） | 0 FATAL/ANR |
| 快速模式（最终产物） | 19 → 20 | `抽签结果_20261107-1721_范围1-70_抽10个_不重复_按抽取顺序.png`（114,779 B） | 0 FATAL/ANR（logcat 5.09 MB） |

导出文件均落在 `/sdcard/Pictures/班级工具站/`，命名符合 `<类型>_<YYYYMMDD-HHMM>_<关键参数>.png`。
⚠️ 文件名时间戳来自**模拟器 UTC 时区**（17:11 vs 本机 01:11），不是 bug。

---

## 3. 文本驱动原理与稳健性设计

1. `adb shell uiautomator dump` → `adb pull` → XML 解析，按文本找节点（精确/包含两种模式）；
2. 取节点 `bounds` 中心 → `adb shell input tap x y`；
3. 长页面用**双向滚动**（`Scroll-Up`/`Scroll-Down`）定位按钮（抽签结果页「用此结果分组」在「导出抽签图」上方）；
4. 轮询间隔 800 ms（dump 每次会在设备端起 Java 进程，过快会让 tap 被吞）；
5. 中文标签以 base64(UTF-8) 常量写在脚本里，保证 `.ps1` **纯 ASCII**（实测 0 非 ASCII 字节，PS 5.1 兼容）；
6. 失败时自动输出 `failure.png` / `failure_ui.xml` / `failure_logcat.txt`（最后 80 行关键行），退出码 1。

---

## 4. 本轮发现与已修复的问题（回归结论）

### 4.1 ✅ 已修复并回归：底部导航吞点击（此前 0/10，现 10/10）

- **此前现象**：从「全部工具」页点底部「工作台」标签无反应（多次/多坐标/重启 app 后仍复现）；从工作台点「全部」在 BACK 之后也可能被吞。E2E 曾用「BACK 回工作台」绕过。
- **修复**：Lead 在 `MainNavHost` 用 `liveRoute` 修复导航状态判断。
- **回归**：`nav_probe_10x` 本次 **10/10 成功**（每轮：工作台→全部→点工作台→断言「常用工具」），此前保存的 `nav_probe_fail_*.xml` 不再产生。
- 结论：**不是自动化环境偶发，是真实 App 缺陷，已修复**；该探测步骤保留为长期回归项。

### 4.2 ✅ 已修复并回归：抽签/分组页导航返回状态丢失（task-16）

- **此前现象**：抽签 STEP3 点「用此结果分组」→ 分组页 → BACK 回到的是抽签 STEP1（`remember` 状态丢失）。
- **修复**：contract 将 `DrawScreen`/`GroupScreen` 状态改为 `rememberSaveable + Saver`（`DrawWorkflow`/Group workflow 经 JSON codec 保存恢复）。
- **回归**：`draw_result_actions` 现在明确断言 BACK 后必须回到「抽签结果」STEP3，本次 **PASS**（截图 `22_draw_to_group.png`）。

### 4.3 ✅ 真实鉴权网络链路已验证（无凭据方式）

`auth_failure_path`：勾选同意 → 学号 `0000000000` / 姓名 `Test` → 提交 → 真实 `class_verify` 网络请求返回 **「姓名或学号不在班级名单中」**（截图 `20_auth_error.png`）。
- 说明服务端鉴权链路、App 网络层、错误态渲染均正常；也顺带验证了「未通过核验不得进入」的门禁逻辑。
- ⚠️ 踩坑（已修复到脚本）：**软键盘未关闭时**，点击「进入工具站」会点到键盘按键（字符被敲进姓名框）。脚本改为输入后检测 `dumpsys input_method` 的 `mInputShown`，必要时发 BACK 关键盘。

### 4.4 ✅ 数据与隐私页（含二次确认）

Debug 预览会话下「我的 → 管理 → 数据与隐私」可正常进入（隐私说明、我的数据概览均渲染），点「清除云端数据」弹出「清除云端数据？」二次确认（「不可恢复」提示），脚本点「取消」未执行任何清除（截图 `21_privacy.png`）。

### 4.5 其它预期内状态（非问题）

- 工作台「今日」：下一节课/待办在无真实数据时显示占位文案（预期）。
- 课表无 token 时显示「暂无课表数据 · 教务快照为空，稍后刷新试试」（task-14 已把「格式异常」文案修正为语义正确的空态，E2E 断言已同步）。
- Release 包无「开发预览」入口，首页只显示身份核验（预期，见 `docs/RELEASE_BUILD.md` §6）。

---

## 5. 已知不稳定点与注意事项

1. **文案包含匹配陷阱**：按钮类标签一律 `-Exact`（如「开始抽签」也出现在 STEP2 说明文字里；「课表」出现在标题与卡片）。
2. **页面标题与工具卡名可能不同**：通知页标题「班级通知」、电费页「宿舍电费」、水量页「宿舍水量」、名册页「名册 · 点名」。
3. **软键盘遮挡**：文本输入后必须关 IME（见 §4.3）。
4. **滚动方向**：长页面按钮需双向滚动；只看一个方向会偶发找不到（已内置 `Wait-UiTextScroll`）。
5. **`pm clear` 默认行为**：清 Debug 会话，保证从身份页开始；保留数据用 `-KeepData`。
6. **并发构建**：队友同时构建可能损坏增量缓存（`FileAnalysisException`/`built_in_kotlinc`），脚本自动 `clean` 重试一次。
7. **模拟器时区 UTC**：导出文件名时间戳比本机慢 8 h。
8. **uiautomator dump 偶发失败**：动画期间可能拿不到层级，脚本内置 4 次重试。
9. **Release 与 Debug 包名不同**：`com.furina.classtools`（release）/ `com.furina.classtools.debug`（debug），可共存；E2E 默认操作 debug 包。

---

## 6. 如何扩展

工具页（统一在 `$pages` 数组加一项，自动获得点击→断言→截图→返回）：

```powershell
# 1) 在 $L 加 base64 标签（中文转 UTF-8 base64）
$L.Settings = U8 '<base64>'
# 2) 数组加一行
@{ step = 'tool_settings'; card = $L.Settings; title = $L.SettingsTitle; shot = '23_settings.png';
   states = @($L.SettingsEmpty, $L.SettingsLoading) }
```

独立流程：`Tap-UiText` / `Tap-UiTextScroll` → `Wait-UiText` / `Wait-UiAny` 断言 → `Save-Shot` → `Add-Step`；导航用 `Go-AllTools` / `Go-Workbench`（带重试与兜底）。文本输入用 `Tap-UiTextInput`，勾选框用 `Tap-ConsentCheckbox`。

---

## 7. 最终验收结论

| 检查项 | 结论 |
|---|---|
| 主链路（身份门禁 → 开发预览 → 工作台 → 全部 → 抽签三步 → 导出 → 分组联动 → 返回） | ✅ 全 PASS |
| 8 个工具页真实打开（作业/通知/课表/电费/水量/分组/体测/名册含页签） | ✅ 8/8 PASS |
| 真实鉴权失败路径（真实 class_verify 网络链路） | ✅ PASS，错误文案正确 |
| 数据与隐私页 + 清除云端二次确认（取消） | ✅ PASS |
| 首页常用工具编辑 | ✅ PASS |
| 导航「全部→工作台」10 连击 | ✅ **10/10**（缺陷已修复） |
| 抽签/分组导航返回状态 | ✅ 回到 STEP3（task-16 已修复） |
| 完整模式 / 快速模式 | ✅ 28 PASS / 391.3 s；✅ 26 PASS / 383.3 s（均退出码 0） |
| FATAL/ANR | ✅ 0（两次运行均 0） |
| 导出进相册 | ✅ 每次 +1（`/sdcard/Pictures/班级工具站/`） |
| 每步截图 | ✅ 22 张 + release_launch.png，均 1080×2400 |
| 脚本质量 | ✅ AST 0 错误、纯 ASCII、约 960 行 |
