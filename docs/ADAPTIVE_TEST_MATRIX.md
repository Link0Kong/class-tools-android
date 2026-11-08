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
