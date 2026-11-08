# 多比例 / 平板 / 折叠屏模拟器矩阵与 v1.0.1 基线适配审计

> 任务：task-22（v2.0.0 Round 1）
> 时间：2026-11-08 07:25–08:25
> 对象：**v1.0.1 稳定基线**（v2.0.0 正在大改，故不从主树构建）
> 工具链：`C:\AndroidToolchain`（emulator 37.2.13、system-images;android-37.0;google_apis;x86_64、platform-tools 37.0.2）
> 执行：WHPX 一次只启动一台；每台截图后关闭。

---

## 0. 结论速览

- **3 台设备全部完成采集**（平板 2560×1600、小屏 720×1280、超长屏 1440×3200），每台 5 张截图，共 **15 张**（`artifacts/adaptive/<设备>/`，9.4 MB）；另补 1 张 **小屏身份页首帧证据** `05_identity_firstframe.png`（共 16 张）。
- **无崩溃、无 P0 阻断**（所有设备可正常进入工作台/全部工具/我的）。
- **P1 适配问题 2 类**：
  1. **平板（1280×800dp）内容无最大宽度/多栏**：工具卡被拉至约 700dp 宽、大面积留白，工作台未做双栏排布（v2 必须处理）。
  2. **小屏（360×640dp）身份页纵向溢出 + 隐私链接逐字换行**：需要滚动才能看到「开发预览」；「《隐私政策》」被挤成竖排（v2 需改行内链接布局）。
- **P2 优化项 3 类**（信息密度、细粒度留白、首次冷启动等待），见 §5。
- **AVD 已保留**（`ct-a37-tablet` / `ct-a37-small` / `ct-a37-tall` / `ct-a37-fold` 可选），供 v2 回归直接复用。

---

## 1. 基线包（重要前提）

| 包 | 用途 | 证据 |
|---|---|---|
| `artifacts/classtools-1.0.1-release-signed.apk`（2,274,622 B，SHA-256 `72135018…144DD2`） | 仅用于「Release 身份页」截图（Release 无「开发预览」入口，无法进入工作台） | 各设备 `00_release_identity.png` |
| `artifacts/adaptive/classtools-1.0.1-debug.apk`（22,890,617 B，SHA-256 `6D4812C8…FF096`，versionName `1.0.1-debug` / versionCode 2） | 用「开发预览」进入工作台 → 采集 4 页基线截图 | 从 v1.0.1 E2E 轮的模拟器安装包 `base.apk` 拉取，保证是 v1.0.1 代码基线，不受 v2 主树影响 |

> 主树当前是 v2.0.0 改动中，**未**用它 `assembleDebug`；后续 v2 矩阵请改用 v2 Debug APK（或 v2 Release + 测试身份）。

---

## 2. 设备矩阵

| 设备目录 | AVD 名 | 分辨率 (px) | density | 宽度 dp | 形态 | 启动参数 |
|---|---|---|---|---|---|---|
| `tablet-2560x1600/` | `ct-a37-tablet` | 2560×1600 | 320 | **1280×800dp**（landscape） | 10.1" 类平板 | `emulator -avd ct-a37-tablet -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -port 5554` |
| `small-720x1280/` | `ct-a37-small` | 720×1280 | 320 | **360×640dp**（portrait） | 小屏手机 | 同上（`-avd ct-a37-small`） |
| `tall-1440x3200/` | `ct-a37-tall` | 1440×3200 | 560 | **411×914dp**（portrait，21:9） | 超长屏手机 | 同上（`-avd ct-a37-tall`） |
| （预留，未跑） | `ct-a37-fold` | 2208×1840 | 420 | 841×701dp（landscape） | 折叠屏展开态 | 时间原因本轮未采集，AVD 已保留 |
| （对照参考） | `ct-api37` | 1080×2400 | 420 | 411×914dp | 常规手机（v1.0.1 E2E 基线） | 已有 E2E 截图（`artifacts/e2e/`） |

所有 AVD 共用同一 system image：`system-images;android-37.0;google_apis;x86_64`，`hw.ramSize=2G`（emulator 实际自动提升到 4096MB）；实测启动耗时 **16–33 s**。

### 2.1 每台截图（5 张，均成功）

| 截图 | small-720x1280 | tablet-2560x1600 | tall-1440x3200 |
|---|---|---|---|
| `00_release_identity.png` | 233,877 B | 1,411,567 B | 817,812 B |
| `01_identity.png`（Debug 开发预览入口） | 172,482 B | 1,427,216 B | 860,756 B |
| `02_workbench.png` | 262,181 B | 1,136,981 B | 849,355 B |
| `03_all_tools.png` | 227,184 B | 655,814 B | 638,654 B |
| `04_profile.png` | 168,411 B | 515,823 B | 483,468 B |

每台目录另有：`device-info.txt`（wm size/density/android 版本/包路径/截图清单）、`run.log`（时间线）、`emulator.log`。
小屏目录额外保留 **`05_identity_firstframe.png`（233,469 B）**：清空 App 数据后首帧身份页，用于证明「开发预览/进入工具站需滚动」与「隐私政策逐字竖排」两个 P1 问题（见 §4）。

---

## 3. 适配检查结论（v1.0.1 基线）

| 检查项 | 平板 1280×800dp | 小屏 360×640dp | 超长屏 411×914dp |
|---|---|---|---|
| 系统栏/手势条 insets | ✅ 状态栏内容不遮挡；底部手势条下方无内容 | ✅ 底栏在 gesture bar 上方，未被遮挡 | ✅ 同上 |
| 底部导航 | ✅ ≥600dp 走 **NavigationRail**（左侧竖排 工作台/全部/我的） | ✅ 底部 3 项，触控高度足够 | ✅ 底部 3 项 |
| 平板限宽/多栏 | ❌ **P1**：工具网格仍 2 列并拉伸满宽（单卡约 700dp），无 `maxWidth`、无左右双栏；工作台「常用工具」「今日」上下堆叠、大量留白 | —（不适用） | —（不适用） |
| 文字截断 | ✅ 无截断（宽度充足） | ⚠️ **P1**：身份页「我已阅读并同意 《用户协议》 《隐私政策》」一行放不下 → **《隐私政策》逐字竖排换行**；且整页高度超过 640dp，**「开发预览」需滚动才可见** | ✅ 无截断（高屏空间充足） |
| 触控目标 | ✅ rail 项、卡片可点；卡片≥142dp 高 | ✅ 卡片/按钮可点；底部 tab 命中正常 | ✅ 同上 |
| 视觉密度 | ⚠️ **P2**：1280dp 下卡片/间距按手机比例放大，信息密度偏低 | ✅ 正常 | ✅ 正常 |
| 深色/横竖屏 | 本轮仅浅色横屏；深色与旋转（平板竖屏）未覆盖（v2 补） | 仅浅色竖屏 | 仅浅色竖屏 |

---

## 4. 问题清单

### P0（阻断）
- **无**。3 台设备 v1.0.1 均无崩溃、无白屏、导航可正常使用。

### P1（v2 必须处理）
1. **平板/大屏缺少内容限宽与多栏布局**
   - 证据：`artifacts/adaptive/tablet-2560x1600/02_workbench.png`、`03_all_tools.png`
   - 现象：1280×800dp 下工具卡被拉伸至 ~700dp 宽、仅 2 列；工作台左侧工具 + 右侧「今日」未分栏，右侧大片空白；顶部问候区与卡片距离过大。
   - 建议（v2）：内容容器 `widthIn(max = 840–1120dp)` + 大屏 Grid 列数自适应（600–840dp: 2–3 列；≥840dp: 3–4 列）+ 工作台 `Row{ 工具区 weight(2); 今日/课表区 weight(1) }`；详见 `docs/ADAPTIVE_…` 建议与 v2 设计稿。
2. **小屏身份页纵向溢出 + 隐私链接逐字换行**
   - 证据：`artifacts/adaptive/small-720x1280/05_identity_firstframe.png`（首帧：点击区/协议行被裁切、「《隐私政策》」竖排换行、开发预览不可见）；`01_identity.png`（向下滚动后可见「开发预览」）
   - 现象：360×640dp 时身份页高度 > 视口，「我已阅读并同意」行超出宽度使《隐私政策》逐字换行；「进入工具站/开发预览」在首屏外。
   - 建议（v2）：同意行改为 `FlowRow`/两行布局（勾选框 + 文案一行，协议链接第二行），或链接改用 `ClickableText`；身份页外层 `verticalScroll` 保持；确保首屏即可见「进入工具站」与门禁关键信息。

### P2（优化项）
1. 平板大屏信息密度偏低（品牌条/问候/卡片间距按手机尺寸放大）——建议 v2 断点级 spacing token。
2. 平板横屏顶部左右留白过大（品牌条靠左、编辑按钮靠右，中间空）——可考虑内容限宽后自然改善。
3. 首次运行 Debug APK 在小屏 AVD 上安装+首次启动较慢（约 90 s，含 dexopt）——仅影响自动化首跑，建议重试/延长等待（脚本已内置两次启动+滚动兜底）。

---

## 5. 环境阻塞与解决记录（本轮踩坑）

1. **首次小屏 AVD boot 420 s 失败（非 AVD 配置问题）**
   - 现象：`emulator.log` 已打印 `Boot completed in ~16 s`，但宿主机 `adb devices` 始终为空（state=none）。
   - 根因：Lead 同时在跑另一台 headless 模拟器（emulator-5556）抢 WHPX/内存；且本机 adb server 在多次 emulator kill 后处于半挂状态。
   - 解决：**一次只启动一台** + 显式 `-port 5554` + 启动前 `adb kill-server && adb start-server`；之后三台均 16–33 s 启动成功。
   - 文档化：后续矩阵一律「单实例 + 固定端口 + 重启 adb server」。
2. **PowerShell 函数参数命名坑（自动化脚本自身）**：临时脚本中 `function Adb([string[]]$args)` 与 PowerShell 自动变量 `$args` 冲突，导致所有 adb 调用实际未带参数（只输出 help）→ 误判 adb state=none。改名为 `$AdbArgs` 后一切正常。已记录，脚本以 `artifacts/adaptive/shoot_device.ps1` 形式保留。
3. 小屏 Debug APK 首启慢 → 脚本增加「启动重试 + 向下滚动找『开发预览』」兜底，之后 15 s 内完成截图。

### 复现命令

```powershell
# 1) AVD（已创建，保留；重建示例）
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'
avdmanager.bat create avd -n ct-a37-tablet -k "system-images;android-37.0;google_apis;x86_64" -d medium_tablet --force
# 然后 patch ~/.android/avd/ct-a37-tablet.avd/config.ini:
#   hw.lcd.width=2560 / hw.lcd.height=1600 / hw.lcd.density=320 / hw.initialOrientation=landscape / hw.ramSize=2G

# 2) 启动（一次一台）
C:\AndroidToolchain\sdk\emulator\emulator.exe -avd ct-a37-tablet -no-window -no-audio -no-boot-anim `
  -gpu swiftshader_indirect -no-snapshot -port 5554

# 3) 安装 + 截图（自动化，可复用）
pwsh -NoProfile -File artifacts\adaptive\shoot_device.ps1 `
  -Avd ct-a37-tablet -Label tablet-2560x1600 -OutDir artifacts\adaptive\tablet-2560x1600
# 脚本：install release → 身份页截图 → install v1.0.1 debug → 开发预览 → 工作台/全部工具/我的 → 关闭模拟器
```

---

## 6. 给 v2.0.0 的验收建议（多设备回归清单）

每台设备（tablet / small / tall / 可选 fold / 常规手机）至少执行：

| 项目 | 断言要点 |
|---|---|
| 身份页 | 首屏可见「进入工具站」+ 同意行不换行溢出；小屏可纵向滚动；隐私/协议弹层可打开 |
| 新底栏（浮岛/「工具」文案） | 浮岛与手势条/状态栏 insets 不遮挡；点击命中达标；3 项与 ≥600dp NavigationRail 切换正常 |
| 工作台 | 平板限宽/双栏生效（无 700dp 超宽卡）；小屏无文字截断；卡片拖动排序可用 |
| 滑动切换（Pager） | 左右滑动切工作台/工具/我的；与横向可滚动区（课表表体）不冲突；边界不切页 |
| 工具页（抽签/课表等） | 页面 push/pop、返回态保持（STEP3）在三种宽度下一致 |
| 我的/权限页 | 通知/日历权限入口可达；拒绝后降级文案正确（v2 权限引擎） |
| 暗色模式 + 旋转 | 平板竖屏（800×1280dp）与横屏都测；深色对比度 |
| 触控目标 | 所有可点区 ≥48dp；底栏/FAB 距手势条 ≥ 8dp |
| 回归基线 | `docs/E2E_SMOKE.md` 的文本断言在 411dp 宽仍成立；本矩阵截图作为视觉对照 |

> 后续 v2 矩阵建议直接把 `artifacts/adaptive/shoot_device.ps1` 的 APK 路径换成 v2 Debug APK，并把截图目录改成 `artifacts/adaptive/v2/<设备>/`，即可复用同一流程。

---

## 7. 附：AVD 保留清单

| AVD | 用途 | 状态 |
|---|---|---|
| `ct-a37-tablet` (2560×1600@320) | 平板/≥600dp（Rail/双栏/限宽） | ✅ 保留 |
| `ct-a37-small` (720×1280@320) | 小屏 360dp（文字截断/溢出） | ✅ 保留 |
| `ct-a37-tall` (1440×3200@560) | 超长屏 21:9（高度/滚动/insets） | ✅ 保留 |
| `ct-a37-fold` (2208×1840@420) | 折叠屏展开态（可选，本轮未跑） | ✅ 保留 |
| `ct-api37` (1080×2400@420) | 常规手机参考 / E2E 主用 | ✅ 保留 |

> 截图与设备信息：`android-class-tools/artifacts/adaptive/<设备>/`；驱动脚本：`artifacts/adaptive/shoot_device.ps1`；v1.0.1 debug APK：`artifacts/adaptive/classtools-1.0.1-debug.apk`。

---

## 8. v2.1.0 自适应矩阵复验（浮岛底栏 / 限宽双栏 / 小屏修复 / 深色）

> 版本：**v2.1.0 / versionCode 4**（2026-11-08 09:30–09:40）
> 包：Release `artifacts/classtools-2.1.0-release-signed.apk`（2,478,833 B / SHA-256 `DCBB1EAE…4B93`，用于身份页）；Debug `app-debug.apk`（23,199,118 B，用于「开发预览」其余页面）。
> 流程：一次一台 AVD → 启动 → 安装 release（截身份页）→ 安装 debug → 开发预览 → 工作台/工具/我的/权限页 → 平板额外深色两屏 → 关闭。脚本：`artifacts/adaptive/v2/shoot_device_v2.ps1`。
> 结果：**4/4 设备采集成功，共 26 张截图**（`artifacts/adaptive/v2/<设备>/`）。

### 8.1 设备矩阵与截图

| 设备 | AVD | 分辨率 @density | 宽度 dp | 截图数 | 截图清单 |
|---|---|---|---|---|---|
| 平板 | `ct-a37-tablet` | 2560×1600 @320 | **1280×800dp**（landscape） | 8 | `00_release_identity`、`01_identity`、`02_workbench`、`03_tools`、`04_profile`、`05_permissions`、`06_dark_workbench`、`07_dark_tools` |
| 小屏 | `ct-a37-small` | 720×1280 @320 | **360×640dp** | 6 | 00–05（同上，无深色） |
| 超长屏 | `ct-a37-tall` | 1440×3200 @560 | **411×914dp**（21:9） | 6 | 00–05 |
| 折叠屏 | `ct-a37-fold` | 2208×1840 @420 | **841×701dp**（展开态 landscape） | 6 | 00–05 |

> 每目录另有 `device-info.txt`、`run.log`、`emulator.log`；总字节约 18 MB。

### 8.2 重点修复验证结论（对照 §4 的 v1.0.1 P1）

| 原问题 | v2.1.0 结论 | 证据 |
|---|---|---|
| **P1-1 平板无限宽/无多栏** | ✅ **已修复**：平板工作台为**左右双栏**（左：今日课程+电费，右：作业待交/通知待处理+常用工具），内容居中限宽；「全部工具」为 **4 列网格**，卡片不再拉伸至 700dp；品牌条/顶栏左右留白收紧 | `tablet-2560x1600/02_workbench.png`、`03_tools.png` |
| **P1-2 小屏身份页溢出 + 隐私链接竖排** | ✅ **主要修复**：同意区改为**两行布局**（勾选框+「我已阅读并同意」一行；《用户协议》《隐私政策》第二行），不再逐字竖排，链接可正常阅读 | `small-720x1280/01_identity.png` |
| 超长屏底栏遮挡 | ✅ 浮岛底栏与手势条无遮挡，页面内容不被底栏裁切 | `tall-1440x3200/02_workbench.png`、`03_tools.png` |
| 折叠屏展开态 | ✅ 841×701dp 双栏/多列布局正常，浮岛底栏居中、无重叠 | `fold-2208x1840/02_workbench.png`、`03_tools.png` |
| 深色模式（平板） | ✅ 工作台/工具页深色配色正常，浮岛底栏/卡片/文字对比达标 | `tablet-2560x1600/06_dark_workbench.png`、`07_dark_tools.png` |
| 权限页（新） | ✅ 四台均可从「我的 → 权限与提醒 → 管理」进入权限页；通知/日历卡片与开关状态正常 | 各设备 `05_permissions.png` |
| 我的页「我的数据」卡（新增） | ✅ 各设备 `04_profile.png` 可见指标卡（待交作业/已完成作业/抽签记录/体测记录/提醒设置入口） | 各设备 `04_profile.png` |

### 8.3 残余问题（P2，不阻断）

1. **小屏（360×640dp）身份页首屏仍需 1 次轻扫**才能看到「开发预览/进入工具站」：隐私链接两行修复后总高度仍略超视口（约 1 屏半）；建议 v2.2 进一步压缩顶部 logo/间距，或允许提交按钮 sticky 底部。
2. 平板工作台左列（课表/电费）在无数据时留白较多（设计可接受）；如需更紧凑可折叠空态卡。
3. 小屏隐私链接行紧贴手势条（约 20dp），建议底部再留 8–12dp inset（当前未遮挡点击）。
4. 折叠屏/超长屏暂未覆盖横竖旋转切换下的布局（本轮仅固定方向截图）。

### 8.4 复现命令

```powershell
# AVD（已保留）：ct-a37-tablet / ct-a37-small / ct-a37-tall / ct-a37-fold
# 一次一台，固定端口 + 重启 adb server（避免 adb 半挂）
pwsh -NoProfile -File artifacts\adaptive\v2\shoot_device_v2.ps1 `
  -Avd ct-a37-tablet -Label tablet-2560x1600 -OutDir artifacts\adaptive\v2\tablet-2560x1600 -DarkMode
# 脚本步骤：install release(v2.1.0) → 身份页截图 → install debug(v2.1.0) → 开发预览 →
#           工作台/工具/我的/权限页截图 →（平板）深色两屏 → 关闭模拟器
```

### 8.5 给下一轮的验收建议

- 保留每设备 ≥6 张（身份/工作台/工具/我的/权限 + 深色或旋转各 1）。
- 新增页（反馈建议/使用说明/我的数据指标卡）纳入矩阵逐设备验证。
- 平板补「竖屏 800×1280dp」与折叠「折叠态」截图；旋转前后各 1 张。
- 浮岛底栏重点断言：手势条 insets、选中态药丸、横滑切 Tab 不误触发。

---

## 9. v2.1.1 小屏首屏修复 + 旋转/折叠态复测

> 版本：**v2.1.1 / versionCode 5**（2026-11-08 10:11–10:18）
> 包：Release `artifacts/classtools-2.1.1-release-signed.apk`（2,495,213 B / SHA-256 `62E5C4C9…CF3F`，身份页）；Debug `app-debug.apk`（23,231,886 B，开发预览页）。
> 脚本：`artifacts/adaptive/v2/shoot_device_v211.ps1` + `run_v211_matrix.ps1`（一次一台、固定端口、启动前重启 adb server）。
> 结果：**2/2 设备成功；small 8 张（含横屏）、fold 6 张**；v2.1.0 已测的 tablet/tall 结论继续有效。

### 9.1 设备与截图

| 设备 | 分辨率@dpi | 宽度 dp | 截图 | 说明 |
|---|---|---|---|---|
| 小屏 `ct-a37-small` | 720×1280 @320 | 360×640 | **8 张** | `00_release_identity`、`01_identity`（首屏验证）、`02_workbench`、`03_tools`、`04_profile`、`05_permissions`、**`06_landscape_workbench`（横屏）**、**`07_landscape_profile`（横屏）** |
| 折叠 `ct-a37-fold` | 2208×1840 @420 | 841×701 | **6 张** | 00–05（展开态，含工作台/工具/我的/权限页） |

> `small-720x1280/device-info.txt` 记录 `rotate=True; identity_first_frame_login=True`；`fold-2208x1840` 记录 `rotate=False`。

### 9.2 修复验证结论（对照 §8 残余 P2）

| 项 | v2.1.1 结论 | 证据 |
|---|---|---|
| **小屏身份页首屏**（原 P2：需滚动才能看到「进入工具站」） | ✅ **已修复**：顶部 logo/间距压缩后，**首屏直接可见「进入工具站」**（禁用态）+ 身份说明 + 版本文案；无需滚动。脚本断言 `identity_first_frame_login=True` | `small-720x1280/01_identity.png` |
| 小屏协议链接两行 | ✅ 保持 v2.1.0 修复：`《用户协议》  《隐私政策》` 第二行并排，不竖排、不裁切 | 同上 |
| **小屏横屏**（新增） | ✅ 1280×720（640×360dp）正常：浮岛底栏可点、无手势条遮挡；工作台/我的页布局正常；横屏高度小，工作台首屏仅「今天」卡（可滚动，预期） | `06_landscape_workbench.png`、`07_landscape_profile.png` |
| **折叠屏展开态** | ✅ 841×701dp 正常（工作台/工具/我的/权限页），底栏居中 | `fold-2208x1840/*.png` |
| 权限页/我的数据卡 | ✅ 小屏/折叠均正常 | `05_permissions.png`、`04_profile.png` |

### 9.3 残余问题（不阻断）

1. 横屏短屏（360dp 高）工作台首屏信息量有限（仅课表卡），需纵向滚动查看更多卡片——移动端横屏属低频场景，可接受；v2.2 可考虑横屏紧凑布局。
2. Debug 构建的「开发预览」入口仍在首屏下方（需一次滚动）；Release 无此入口，不影响用户。
3. 本轮未重跑 tablet/tall（v2.1.0 已通过），亦未截权限页横屏；下轮可补。
4. 折叠屏「折叠态」（半折 1800×840 类）仍未覆盖，建议 v2.2 增加。

### 9.4 复现命令

```powershell
# 小屏：首屏断言 + 横屏（脚本会自动：清数据→identity 首帧→开发预览→工作台/工具/我的/权限→横屏 2 张→恢复）
pwsh -NoProfile -File artifacts\adaptive\v2\shoot_device_v211.ps1 `
  -Avd ct-a37-small -Label small-720x1280 -OutDir artifacts\adaptive\v2\small-720x1280 -Rotate

# 折叠屏展开态
pwsh -NoProfile -File artifacts\adaptive\v2\shoot_device_v211.ps1 `
  -Avd ct-a37-fold -Label fold-2208x1840 -OutDir artifacts\adaptive\v2\fold-2208x1840
```

### 9.5 下轮建议

- 旋转覆盖：小屏竖/横 + 平板竖/横；折叠展开/折叠两态。
- 横屏适配专项（工作台紧凑双列、底栏横屏位置）。
- 小屏身份页：可再验证软键盘弹出时「进入工具站」不被遮挡。
