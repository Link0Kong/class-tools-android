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

---

## 8. v1.0.1 最终回归（Round 4，2026-11-08 01:40–01:57）

> 版本：**versionName 1.0.1 / versionCode 2**；主树 `assembleDebug` BUILD SUCCESSFUL、全量单测 **466/0**（Lead 验证）。
> 脚本在 v1.0.0 的 28 项基础上**新增 3 项**（原有步骤全部保留），共 **31 项**（完整模式）。

### 8.1 新增覆盖（v1.0.1 功能点）

| 步骤 | 动作 / 断言 | 结果 |
|---|---|---|
| `appearance_about` | 我的 →「外观」卡片：断言「壁纸取色（Material You）」存在 → 点 Switch 一次（0→1）并验证状态确实变化 → 截图 `23_appearance.png` → 滚到「关于」断言版本含 `1.0.1` | PASS（switch=True；version=1.0.1） |
| `appearance_persist` | `force-stop` + 重启 App → 恢复 Debug 会话 → 我的 → 外观 → 断言 Switch 状态与切换后一致（本地 DataStore 持久化） | PASS（persisted=True） |
| `settings_redirect` | 全部 →（滚动到系统分类）点「设置」卡片 → 断言落到「我的」页（出现「壁纸取色」「关于」，且**不出现**「原生版开发中」占位）→ 截图 `24_settings_redirect.png` → BACK 回全部 | PASS（settings card → profile page） |

说明：Debug 构建「关于」版本显示 `v1.0.1-debug`，断言按「包含 1.0.1」；Release 包为 `v1.0.1`（见 `RELEASE_BUILD.md` §7）。

### 8.2 运行结果

| 项目 | 结果 |
|---|---|
| 完整模式 `-Build -Install` | **31 PASS / 0 WARN / 0 FAIL，462.0 s，退出码 0** |
| 快速模式（无参） | **29 PASS / 0 WARN / 0 FAIL，452.1 s，退出码 0** |
| 导航 10 连击（`nav_probe_10x`） | ✅ **10/10**（两轮均 10/10，持续回归通过） |
| 抽签→分组→BACK STEP3 / 真实鉴权 / 隐私二次确认 | ✅ 全部保持 PASS（v1.0.0 基线未回归） |
| FATAL/ANR | ✅ 0（完整/快速两轮均 0） |
| 导出进相册 | ✅ 完整 20→21（`抽签结果_20261107-1742_…png` 115,118 B）；快速 21→22（`…-1750_…png` 115,914 B） |

### 8.3 v1.0.1 截图（新增 2 张 + 全量 24 张 + release）

| 文件 | 字节 | 说明 |
|---|---|---|
| `23_appearance.png` | 321,413 | 我的→外观（壁纸取色开关已切换为开） |
| `24_settings_redirect.png` | 295,543 | 全部→设置 落到「我的」页（外观/关于/v1.0.1） |
| 其余 22 张 | — | 与 v1.0.0 基线同名同步骤（01–22；`20_auth_error`/`21_privacy`/`22_draw_to_group` 等均重新生成） |
| `release_launch_v101.png` | 531,180 | Release v1.0.1 启动截图（身份页，无开发预览） |

### 8.4 结论

v1.0.1 在保持 v1.0.0 全部基线功能不回归的前提下，新增「外观（壁纸取色，本地持久化）」「设置卡片跳转我的页」「版本号 1.0.1」三项均验证通过；两轮 E2E、导航探针、logcat 全绿，Release 签名/安装/启动复验通过，具备交付条件（真实账号业务链路仍需用户测试身份另测，见 `RELEASE_BUILD.md` §7）。

**回归口径**：以**完整模式 31 项（462.0 s）**为准；快速模式 29 项（452.1 s，跳过 build/install）为同轮补充，两轮均 0 FAIL。
**交付包**（Lead 构建/交付，已核验与构建产物一致）：`android-class-tools\artifacts\classtools-1.0.1-release-signed.apk`，**2,274,622 B，SHA-256 `72135018511DE666CB1AF6222988FD7FE39FF527F21C20D70C1B80D98D144DD2`**，versionName 1.0.1 / versionCode 2。

---

## 9. v2.1.0 最终回归（浮动底栏 / Pager 滑动 / 权限 / 提醒）

> 版本：**versionName `2.1.0` / versionCode `4`**（主树 603/0 单测，Lead 数据）
> 脚本：`scripts/e2e_smoke.ps1`（v2 升级版，纯 ASCII，AST 0 错误）
> 设备：`emulator-5554`（ct-api37，1080×2400 @420），Debug 包 23,199,118 B
> 时间：2026-11-08 08:28–09:19（含 v2 断言调试轮）

### 9.1 脚本 v2 升级内容（在 v1.0.1 的 31 项基线上扩展为 34 项）

| 变更 | 说明 |
|---|---|
| 底栏文案 | 底部导航从「全部」改为 **`工作台 / 工具 / 我的`**（`AllTab` 标签更新；Go-AllTools / nav probe 同步适配） |
| **Pager 滑动**（新增 `pager_swipe`） | 工作台 ←左滑→ 工具页（`全部工具`）；再左滑 → 我的页（`我的数据`/`云同步`/`权限与提醒`）；右滑×2 回工作台；两端断言页面标题后截图 |
| 工作台新结构（`workbench`） | 断言 `常用工具` + **`作业待交`** + **`通知待处理`** 卡片，以及 `课表未同步`/`今天没课`/`课表` 其一 |
| **权限管理**（新增 `permission_management`） | 我的 → 权限与提醒 →「管理」→ 权限页（`权限与提醒`/`通知`/`日历`）→ 点 `开启权限` → 应用内说明弹层点 `去开启` → **系统通知权限弹窗** → 点 **`Don’t allow` 拒绝** → 断言页面状态文案（`未开启`/`需系统设置`/`已开启`）且页面仍在、无 crash |
| **提醒设置**（新增 `reminder_settings`） | 权限页「提醒设置」→ 断言 `提醒总开关`/`作业提醒`/`上课提醒` → **作业提醒开关切换两次并验证状态变化** → 截图 → BACK 两级回「我的」 |
| 设置卡片跳转（`settings_redirect` 适配） | v2 根页是同一个 Activity 内的 Pager，「设置」=切到「我的」Tab（非 push），**不再用系统 BACK**（根页 BACK 会退出应用），改为点底栏「工具」返回 |
| 关于版本 | 断言改为包含 `2.`（当前 `v2.1.0-debug`） |
| 导航探针 | 「工具 → 工作台」底栏点击 10 连击（每轮重启 app），保持 WARN 语义 |
| 其余 28 项 | 身份门禁、真实鉴权失败路径、抽签三步/结果单/分组联动/BACK 状态恢复、8 个工具页、隐私页、外观页、首页编辑、导出相册、logcat 0 FATAL 全部保留 |

> v2.1.0 小屏身份页两行布局、工具网格 2/3/4 列拖拽几何、「我的」平板双栏等自适应改动对 E2E 文本路径无影响（见 `ADAPTIVE_TEST_MATRIX.md` §8）。

### 9.2 结果

| 轮次 | 结果 | 耗时 | 退出码 | 备注 |
|---|---|---|---|---|
| **完整模式** `-Build -Install` | **34 PASS / 0 WARN / 0 FAIL** | **525.1 s** | 0 | build 8 s + install；全量断言 |
| **快速模式**（无参数） | **32 PASS / 0 WARN / 0 FAIL** | **518.2 s** | 0 | 跳过 build/install，其余断言相同 |
| 导航探针 | **10/10**（两轮） | — | — | 底栏「工具 → 工作台」连续 10 次全部命中 |
| logcat | **0 FATAL / 0 ANR**（两轮） | — | — | 结束时全量检查 |
| 导出相册 | 27→28、28→29（各 +1 PNG） | — | — | `抽签结果_20261108-*_*.png` |

完整模式 34 项：`build`、`install`、`identity_gate`、`auth_failure_path`、`workbench`、`pager_swipe`、`all_tools`、`draw_step1/2/3`、`export_image`、`draw_result_actions`、`back_to_all_tools`、`tool_hw/nt/tt/power/water/group/fit/roster`、`tool_pages`(8)、`privacy_page`、`privacy_back_home`、`appearance_about`(version 2.x)、`appearance_persist`、`permission_management`（system dialog denied）、`reminder_settings`、`settings_redirect`、`back_to_workbench`、`nav_probe_10x`、`home_quick_edit`、`logcat_no_fatal`、`gallery_export_file`。

### 9.3 截图（E2E 轮 29 张；`artifacts/e2e/` 目录另含 3 张 Release v2.1.0 复验截图）

v2 新增/关键：`25_pager_tools.png`（滑动到工具页）、`26_pager_profile.png`（滑动到我的页）、`27_permissions.png`（权限管理页）、`28_permission_denied.png`（拒绝系统弹窗后状态）、`29_reminder.png`（提醒设置，作业提醒开关切换后）；其余 24 张与 v1.0.1 基线同名（`01_identity`、`02_workbench`、`03_all_tools`、`04–07` 抽签流程、`11–20` 工具页、`21_privacy`、`23_appearance`、`24_settings_redirect`、`08_back_all_tools`、`09_home_after_back`、`18_home_edit`、`20_auth_error` 等）。

### 9.4 已知行为与边界

- **根页系统 BACK 退出应用**：v2 根页是 3-Tab Pager（单 Activity），在任何顶层 Tab 按 BACK 会退出应用（符合 Android 惯例）；脚本所有根页导航改用底栏点击，不再依赖 BACK。
- **写日程入口在空态不展示**：课表 `添加到系统日历（本周）` 需要已有课程数据、通知 `加入日程` 需要「自动/活动」且有时间，Debug 预览空态下不出现（页面本身断言正常，见 `tool_tt`/`tool_nt`）。
- 反馈建议 / 使用说明 / 我的数据指标卡为 v2.1.0 新增（task-24 接线），本轮 34 项脚本未覆盖（`我的数据` 卡仅作为 Pager 断言 marker）；建议下一轮加入 E2E（见 ADAPTIVE 矩阵 v2 小节的待验证项）。
- **Release v2.1.0 当前被 P0 阻断**：R8 裁剪 `androidx.work.impl.WorkDatabase_Impl` 导致冷启动崩溃，详见 `RELEASE_BUILD.md` §8；Debug 包无此问题，不影响本 E2E 结论。

### 9.5 回归口径

以**完整模式 34 项（525.1 s）**为准；快速模式 32 项（518.2 s，跳过 build/install）为同轮补充，两轮均 0 FAIL。日志：`C:\AndroidToolchain\work\e2e_v21_full.log`、`e2e_v21_fast.log`。

---

## 10. v2.1.1 最终回归（反馈建议 / 使用说明 / 通知提醒）

> 版本：**versionName `2.1.1` / versionCode `5`**（主树 639/0 单测，Lead 数据）
> 设备：`emulator-5554`（ct-api37，1080×2400 @420）；Debug 包 23,231,886 B
> 时间：2026-11-08 09:50–10:10

### 10.1 脚本增量（36 项；v2.1.0 的 34 项全保留）

| 新增/变更 | 说明 |
|---|---|
| **`user_guide`**（新增） | 我的 → 关于 → **使用说明**（入口 actionText「查看」）→ 断言标题 `使用说明` + **≥3 个分节标题**（`身份核验`/`工作台`/`抽签`，滚动查找）→ 再滚动一屏 → 截图 `31_guide.png` → BACK |
| **`feedback_page`**（新增） | 我的 → 关于 → **反馈建议**（actionText「反馈」）→ 断言页面专属内容/状态（`反馈内容`/`提交反馈`/`读取反馈失败`/`还没有反馈`/`加载反馈` 任一）→ 截图 `30_feedback.png` → BACK；**只打开不提交**，不触发云端写 |
| `reminder_settings`（增强） | 在原有「提醒总开关/作业提醒/上课提醒 + 作业开关切换」基础上，新增断言 **`通知提醒`** 行（v2.1.1 ntremind） |
| 版本断言 | 「关于」版本包含 `2.`（当前 `v2.1.1-debug`） |
| 其它 | 34 项基线（身份门禁/鉴权失败/工作台新结构/Pager 滑动/权限拒绝/设置跳转/抽签三步/工具页/隐私/外观/首页编辑/导航 10 连击/导出/日志）全部保留 |

### 10.2 结果

| 轮次 | 结果 | 耗时 | 退出码 |
|---|---|---|---|
| **完整模式** `-Build -Install` | **36 PASS / 0 WARN / 0 FAIL** | **601.6 s** | 0 |
| **快速模式** | **34 PASS / 0 WARN / 0 FAIL** | **575.1 s** | 0 |
| 导航探针 | **10/10**（两轮） | — | — |
| logcat | **0 FATAL / 0 ANR**（两轮） | — | — |
| 导出相册 | 29→30、30→31（各 +1 PNG） | — | — |

新增步骤实测：
- `user_guide`：21.9 s，`title + 3 sections` PASS；截图滚动后可见分节 4–9（分组/名册·点名/作业/班级通知/课表/体测…）
- `feedback_page`：11.3 s，`opened; not submitted` PASS；页面为 READY 空态（`反馈内容` 输入框 + 置灰 `提交反馈` + `还没有反馈`），**未点击提交**
- `reminder_settings`：30.1 s，`通知提醒` 行断言通过（作业提醒开关切换两次还原）

### 10.3 截图（31 张，`artifacts/e2e/`）

新增：`30_feedback.png`（反馈建议页，空态表单，未提交）、`31_guide.png`（使用说明页，滚动到分节 4–9）；其余 29 张与 v2.1.0 基线同名（`25_pager_tools`/`26_pager_profile`/`27_permissions`/`28_permission_denied`/`29_reminder` 等）。目录另含 Release 复验截图（`release_*`）。

### 10.4 边界

- 反馈页**未做真实提交**（避免云端写；提交链路留待真实账号测试身份验证）。
- 写日程入口仍为「课表有课程数据/通知为活动条目」才出现，Debug 空态不展示（页面本身断言正常）。
- 通知提醒（ntremind）本机调度需要真实作业/通知数据+权限，本轮仅验证设置页开关与既有提醒设置项，未做真实通知到达断言。
- Release v2.1.1 复验见 `RELEASE_BUILD.md` §9；多设备小屏/旋转/折叠见 `ADAPTIVE_TEST_MATRIX.md` §9。

### 10.5 回归口径

以**完整模式 36 项（601.6 s）**为准；快速模式 34 项（575.1 s，跳过 build/install）为同轮补充，两轮均 0 FAIL。日志：`C:\AndroidToolchain\work\e2e_v211_full.log`、`e2e_v211_fast.log`。

---

## 11. v3.0.0 最终回归（深色全链路 / 悬浮玻璃底栏 / 通知诊断 / 课表新布局）

> 版本：**versionName `3.0.0` / versionCode `6`**（主树 779/0 单测，Lead 数据）
> 设备：`emulator-5554`（ct-api37，1080×2400 @420）；Debug 包 24,072,553 B
> 时间：2026-11-08 22:20–23:02

### 11.1 脚本增量（40 项；v2.1.1 的 36 项全保留）

| 新增/变更 | 说明 |
|---|---|
| **`dock_overlay`**（新增） | 悬浮玻璃底栏 overlay 校验：3 个 Tab 语义项（`content-desc` 含「第 N 个 Tab」）存在、**恰有 1 个「已选中」pill**、每个 Tab 的**文字与项中心水平对齐**（实测最大偏差 **0 px**）；工作台滚动到底后**内容底边不越过 dock 顶边**（实测 contentBottom < dockTop，120dp 底部留白生效）→ 截图 `4x_dock_workbench/tools/profile.png` |
| **`timetable_v3`**（新增） | 打开课表 → 断言页面正常（空态/加载/错误任一）→ 检测 **`登录教务`** 入口（实测 login=True）与导出按钮 → 截图 `4x_timetable_v3.png` → BACK；不填真实账号 |
| **`diag_page`**（新增） | 全部工具 → **诊断** 卡 → 断言副标题（服务健康）+ **5 个分节**（本机代理/设备诊断/AI 分析/修复任务/服务健康）→ 截图 `4x_diag.png` → BACK；**未点任何上报/修复按钮** |
| **`dark_mode_group`**（新增） | `cmd uimode night yes` → 冷启动 → 深色 **身份/工作台/工具/我的/权限/课表** 6 张（`4x_dark_*.png`）→ `night no` 恢复并截 `4x_light_restore.png`（共 7 张） |
| `reminder_settings`（增强） | 断言 **`通知测试与诊断`** 区块 + **`发送测试通知`**、**`立即检查 + 诊断`** 按钮存在（**未点击**，避免制造系统通知/云端写） |
| 版本断言 | 「关于」版本包含 `3.`（当前 `v3.0.0-debug`） |
| 其它 | 工具网格新增「诊断」卡后，抽签/工具页/权限/反馈/使用说明等 36 项基线全部保持 |

### 11.2 结果

| 轮次 | 结果 | 耗时 | 退出码 |
|---|---|---|---|
| **完整模式** `-Build -Install` | **40 PASS / 0 WARN / 0 FAIL** | **840.1 s** | 0 |
| **快速模式** | **38 PASS / 0 WARN / 0 FAIL** | **820.7 s** | 0 |
| 导航探针 | **10/10**（两轮） | — | — |
| logcat | **0 FATAL / 0 ANR**（两轮） | — | — |
| 导出相册 | 32→33、33→34（各 +1 PNG） | — | — |

v3 新增项实测：
- `dock_overlay`：`pill centered, max label delta 0px, content above dock`；工作台滚动到底内容不遮挡
- `timetable_v3`：`login=True export=False`（导出按钮在空态不展示，正常）
- `diag_page`：`5 sections; no report/fix pressed`
- `dark_mode_group`：67.3 s，6 张深色 + 1 张浅色恢复均成功

### 11.3 截图（本轮 40 张，`artifacts/e2e/`）

v3 新增：`4x_dock_workbench/tools/profile.png`、`4x_timetable_v3.png`、`4x_diag.png`、`4x_dark_identity/workbench/tools/profile/permissions/timetable.png`、`4x_light_restore.png`（共 14 张）；其余 26 张与 v2.1.1 基线同名。目录另含 Release v3.0.0 复验截图（`release_fix_v300_*`、`release_launch_v300.png`）。

### 11.4 深色/底栏人工审图结论

- 深色：状态栏/导航栏图标随主题反色；身份页、工作台、工具、我的、权限、课表均为深色底 + 浅色文字，无「白底黑字」错乱；浮岛底栏深色下对比度正常（详见 `ADAPTIVE_TEST_MATRIX.md` §10 截图）。
- 底栏：选中 pill 与图标/文字垂直居中（同一 Column 内绘制）；内容区 120dp 底部留白实测生效（滚动到底无遮挡）；Tab 语义播报正确（`工作台/工具/我的，第 N 个 Tab，共 3 个，已选中`）。

### 11.5 边界（未触发云端/系统副作用）

- **未点**「发送测试通知」「立即检查 + 诊断」（避免制造系统通知与诊断上报）；仅断言按钮存在。
- 诊断页**未点**上报/自动修复/重新检测按钮。
- 课表**未提交**教务账号（未登录态验证入口存在）；通知「提醒我」需真实通知条目，未覆盖。
- 反馈建议页未提交（沿用 v2.1.1 唯一读打开策略）。
- 真实账号业务链路、真实通知到达、教务登录完整流程仍需测试身份/账号另测。

### 11.6 回归口径

以**完整模式 40 项（840.1 s）**为准；快速模式 38 项（820.7 s，跳过 build/install）为同轮补充，两轮均 0 FAIL。日志：`C:\AndroidToolchain\work\e2e_v300_full.log`、`e2e_v300_fast.log`。

---

## 12. v3.1.0 快速回归（视觉精修 P1）

> 版本：**versionName `3.1.0` / versionCode `7`**（功能与 v3.0.0 相同，仅视觉）
> 设备：`emulator-5554`（ct-api37，1080×2400 @420）；Debug 包 **23,609,800 B**（v3.1.0-debug，从当前主树重编）
> 时间：2026-11-09 00:04–00:06（快速模式单轮）

### 12.1 前置说明（重要）

动作开始前发现 `app/build/outputs/apk/debug/app-debug.apk` 仍是**旧的 v3.0.0（vc6）**构建物，已先 `:app:assembleDebug` 重编为 v3.1.0（vc7）并 `adb install -r` 后再跑回归；第一次对旧包的试跑作废，不计入结果。

### 12.2 结果

| 项目 | 结果 |
|---|---|
| 快速模式（38 项，跳过 build/install） | **38 PASS / 0 WARN / 0 FAIL，870.0 s，退出码 0** |
| 导航探针（工具→工作台 10 连击） | **10/10** |
| logcat | **0 FATAL / 0 ANR** |
| 导出相册 | 35→36（+1 PNG） |
| 重点断言 | **dock_overlay PASS**（pill 唯一、Tab 文字水平偏差 **0 px**、滚动到底内容底边 < dock 顶边/120dp 留白）；工作台/Pager/权限/提醒/诊断/课表/反馈/使用说明等全部文字断言在新玻璃页头下仍可正常定位 |

### 12.3 视觉变化确认（人工审图）

| 项 | 结论 |
|---|---|
| 工作台/我的/全部工具**页头玻璃卡** | ✅ 半透明玻璃卡 + 描边（无阴影伪影）正常渲染，「全部工具」标题与副标题在玻璃卡内 |
| **品牌三圆点**入页头 | ✅ 「常用工具」「全部工具」标题前均显示三色圆点 |
| **背景光晕加强** | ✅ 顶部/卡片后方光晕可见，未影响文字对比 |
| **底栏选中 pill 高光描边 + 悬浮阴影** | ✅ 选中 pill 有明显高光描边与悬浮阴影，深色下同样可见；图标/文字未偏移 |
| 文字定位/截断 | ✅ 玻璃卡不改变 uiautomator 语义，文字断言全部命中；无截断/错位 |

### 12.4 截图（`artifacts/e2e/`）

- 专用 4 张（1080×2400）：`5x_v31_workbench.png`（487,202 B）、`5x_v31_tools.png`（404,494 B）、`5x_v31_profile.png`（403,324 B）、`5x_v31_dark.png`（511,476 B，深色工作台）
- 快速 E2E 本轮另生成 `4x_dock_*`（3 张）、`4x_dark_*`（6 张 + 浅色恢复）及各页基线截图；`artifacts/e2e/` 共 50 张 PNG（含 Release 证据）

### 12.5 边界

- 仅跑**快速模式**（38 项，未跑 `-Build -Install` 完整 40 项）；功能与 v3.0.0 相同，完整回归已在 `§11` 覆盖。
- 深色覆盖沿用 E2E dark 组（6 张）+ 本次 `5x_v31_dark.png`，未重复全矩阵。
- 未触发云端写（诊断/反馈/通知测试均未提交）；真实账号/教务登录未测。
- 主源码相对 release APK 无更新（未发现外部改动），未 git commit/tag。

### 12.6 回归口径

v3.1.0 快速回归以本轮 **38 项 0 FAIL（870.0 s）** 为准；日志：`C:\AndroidToolchain\work\e2e_v310_fast.log`。

---

## 13. v3.2.0 快速回归（全局顶栏玻璃 + 作业单条提醒）

> 版本：**versionName `3.2.0` / versionCode `8`**（功能与 v3.1.0 相同 + 作业单条提醒入口/视觉玻璃化）
> 设备：`emulator-5554`（ct-api37，1080×2400 @420）；Debug 包从当前主树**重新 `assembleDebug`** 并安装（dumpsys 确认 `versionName=3.2.0-debug / versionCode=8`）
> 时间：2026-11-09 00:18–00:33（快速模式单轮）

### 13.1 结果

| 项目 | 结果 |
|---|---|
| 快速模式（38 项，跳过 build/install） | **38 PASS / 0 WARN / 0 FAIL，818.1 s，退出码 0** |
| 导航探针（工具→工作台 10 连击） | **10/10** |
| logcat | **0 FATAL / 0 ANR** |
| 导出相册 | 36→37（+1 PNG） |

重点检查（本轮变化对既有断言的影响）：
- **作业页**：`tool_hw` PASS —— 页面正常打开（空态：`全部 0 / 未完成 0 / 已完成 0` 筛选 + `暂无作业` + `老师发布后会出现在这里`）；新增行内「提醒我」铃铛仅在有作业行时渲染，空态不破坏布局。
- **权限页 / 提醒设置页**：`permission_management`、`reminder_settings` PASS —— 玻璃化后文字断言仍命中，页面状态正常。
- **底栏**：`dock_overlay` PASS —— pill 唯一、Tab 文字水平偏差 **0 px**、滚动到底内容不越过 dock（120dp 留白）。
- 全局顶栏玻璃化不改变 uiautomator 语义，工作台/Pager/抽签/工具页/诊断/课表/反馈/使用说明等 36 项基线断言全部保持。

### 13.2 截图（`artifacts/e2e/`，均 1080×2400）

| 截图 | 大小 | 内容 |
|---|---|---|
| `6x_v32_homework.png` | 518,976 B | 作业页（空态 + 筛选 chips + 全局玻璃顶栏） |
| `6x_v32_permissions.png` | 462,458 B | 权限与提醒页（玻璃卡片） |
| `6x_v32_reminder.png` | 433,190 B | 提醒设置页（玻璃卡片） |
| `6x_v32_dark_permission.png` | 565,333 B | 深色权限页（可选） |

> 快速 E2E 本轮另生成 `4x_dock_*`、`4x_dark_*` 及各页基线截图；`artifacts/e2e/` 共 50 张 PNG（含 Release 证据）。

### 13.3 边界

- 仅跑**快速模式**（38 项）；完整 40 项/矩阵见 v3.0.0 `§11`（v3.2.0 功能未变）。
- 作业「提醒我」铃铛与详情提醒（截止前 1 天/1 小时/截止时刻）需要**真实作业数据**才能点到；本轮空态仅验证页面正常与入口不破坏布局，排程行为未触发。
- 未触发云端写（诊断/反馈/通知测试均未提交）；教务账号未登录。
- 主源码相对 release APK 无更新（未发现外部改动），未 git commit/tag。

### 13.4 回归口径

v3.2.0 快速回归以本轮 **38 项 0 FAIL（818.1 s）** 为准；日志：`C:\AndroidToolchain\work\e2e_v320_fast.log`。
