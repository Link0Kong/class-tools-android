# Release 构建与签名说明（Android）

> 本机已生成自签名 release keystore 并成功产出**已签名 release APK**（R8 混淆 + 资源压缩）。
> ⚠️ **keystore 一旦丢失，已发布 App 将无法升级覆盖**。请尽快把 `key/release.jks` 与 `keystore.properties`
> 备份到安全的离线位置（不要提交到任何仓库/云端公开位置，不要贴到对话里）。

## 1. 文件位置（本机）

| 文件 | 路径 | 是否入库 |
|---|---|---|
| 签名 keystore | `android-class-tools/key/release.jks` | ❌ 已在 `.gitignore` 排除 |
| 签名参数 | `android-class-tools/keystore.properties` | ❌ 已在 `.gitignore` 排除 |
| 签名配置代码 | `app/build.gradle.kts`（`signingConfigs.release`） | ✅ 仅读 properties，不含密码 |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | 产物 |
| 交付副本 | `artifacts/classtools-1.0.0-release-signed.apk` | 产物 |

- 证书主题：`CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`
- 证书指纹（SHA-256）：`974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`
- 签名方案：APK Signature Scheme v2（`apksigner verify` 通过）
- 包名：`com.furina.classtools`（release 无 `.debug` 后缀，可与 debug 版共存）

## 2. 构建命令

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools

.\gradlew.bat assembleDebug    --console=plain   # Debug（含「开发预览」入口，仅调试用）
.\gradlew.bat assembleRelease  --console=plain   # Release（R8 + 签名）
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

### 2.1 自动上传更新渠道（2026-11-19 起，硬性）

`assembleRelease` 完成后会由 `publishUpdateChannel` finalizer **自动上传** APK + `latest.json` 到 ECS + OSS，并回读校验 sha256；同版本同 sha 幂等跳过。

- 发布说明：`-Pct.updateNotes="1. 改动一；2. 改动二"`
- 渠道：`-Pct.updateChannel=beta`
- 只编译不发版：`-Pct.skipUpdateChannel=true`
- 上传脚本：`web/deploy/android_release.py`（凭据仍只在 `web/deploy/creds.env` / 环境变量，不入库）
- 发布后核对：`http://121.196.47.2/android/latest.json`、`.../android/classtools-<version>.apk`、OSS `android/latest.json`

## 3. Release 验证（已实测）

| 项 | 结果 |
|---|---|
| `assembleRelease` | BUILD SUCCESSFUL（约 1m48s，含 R8/lintVital） |
| APK 体积 | **2.06 MB**（debug 20.87 MB；R8+资源压缩后，含 7 个工具模块） |
| `apksigner verify --print-certs` | V2 Signer 通过，证书 DN/指纹见上 |
| 安装到 Android 17 模拟器 | `Success`；`MainActivity` 正常启动到身份页 |
| 日志 | 无 FATAL/ANR |

> Release 包**不含** Debug 的「开发预览：跳过核验」入口；要进入主界面必须用真实班级身份（学号+姓名）通过云端核验。
> 因此 release 的「数据/业务全链路」需用户本人账号或测试身份实测（测试身份凭据由用户提供，不入仓库）。

## 4. 更新版本

- 版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`；每次发布递增 `versionCode`。
- APP 备案所需的包名/公钥/签名 MD5 可在 `apksigner verify --print-certs` 输出中取得。
- 发布前检查：`docs/REPORT_M0_20261107.md` §4 的已知问题（备案号、隐私政策页、通知提醒等）。

## 5. 安全红线

1. 不要把 `key/release.jks`、`keystore.properties` 内容粘贴到对话、Issue、公开仓库。
2. 不要把密码写进 `app/build.gradle.kts` 或 `gradle.properties`（当前实现只从本地 `keystore.properties` 读取）。
3. keystore 备份建议：加密压缩包 + 离线 U 盘/密码管理器；至少保留 2 份异地副本。

---

## 6. 复验（2026-11-08 01:28，最终 E2E 验收轮）

> 本节为最终代码树（Round 3 收尾：privacy 页、water/roster 真实页、fitrecs、通知附件预览、全部工具拖动排序、抽签/分组状态修复、导航修复）的 Release 复验记录。
> 复验前主树状态（Lead 已验证）：`assembleDebug` BUILD SUCCESSFUL、全量单测 **451/0**。

### 6.1 复验命令（本机可复制）

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat :app:assembleRelease --console=plain --no-daemon

$apk = 'app\build\outputs\apk\release\app-release.apk'
& 'C:\AndroidToolchain\sdk\build-tools\37.0.0\apksigner.bat' verify --verbose --print-certs $apk

$adb = 'C:\AndroidToolchain\sdk\platform-tools\adb.exe'
& $adb install -r $apk
& $adb logcat -c
& $adb shell am start -n 'com.furina.classtools/com.furina.classtools.MainActivity'
& $adb shell dumpsys activity activities | Select-String topResumedActivity
# 截图（二进制安全）
cmd /c "`"$adb`" exec-out screencap -p > artifacts\e2e\release_launch.png"
& $adb logcat -d | Select-String 'FATAL EXCEPTION|ANR in '
```

### 6.2 复验结果（全部 PASS）

| 项 | 结果 |
|---|---|
| `:app:assembleRelease` | **BUILD SUCCESSFUL in 1m 41s**（52 tasks；含 R8、lintVital、签名） |
| Release APK | `app\build\outputs\apk\release\app-release.apk`，**2,258,234 B**（约 2.15 MiB；debug 版 22,860,755 B） |
| APK SHA-256 | `716063E4F8B446B31806E7850B5A04E621C2A612E8C906039969B1BB1D2DC056` |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true（v1/v3/v3.1/v3.2/v4 = false，与当前签名配置一致）；Number of signers = 1 |
| 签名证书 | v2 Signer：`CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048 |
| 安装 | `adb install -r` → `Performing Streamed Install` → **Success** |
| 包信息 | `com.furina.classtools`，versionName **1.0.0**，versionCode 1，minSdk 26，targetSdk 37 |
| 启动 | `topResumedActivity = com.furina.classtools/.MainActivity` |
| 首屏 | 身份核验页（**无「开发预览」入口**，符合 Release 预期）；`v1.0.0 · Android 17 设计语言` |
| 启动截图 | `artifacts\e2e\release_launch.png`（1080×2400，531,526 B） |
| logcat | `artifacts\e2e\release_logcat.txt`（699 行 / 97,554 B）中 **FATAL/ANR = 0** |

### 6.3 证据文件（artifacts/e2e/）

- `release_build.log`（构建日志）
- `release_apksigner_verify.txt`（验签完整输出）
- `release_launch.png`（启动截图）
- `release_ui.xml`（启动后 UI 层级 dump，可见身份页文案、无开发预览）
- `release_logcat.txt`（启动期间全量 logcat）

### 6.4 说明与边界

1. 本次复验覆盖「签名 → 安装 → 启动 → 无崩溃/无 FATAL」；Release 包**业务全链路**（真实身份登录→数据加载）未在本轮测试，需要用户提供测试身份后另测（对应 `docs/E2E_SMOKE.md` 中 Debug「开发预览」链路已全绿）。
2. 版本号仍为 `1.0.0`（本轮未发布）；正式上传前按 §4 递增 `versionCode`。
3. 安装 release 包不会覆盖 debug 包（包名不同：`com.furina.classtools` vs `com.furina.classtools.debug`），两者可共存。
4. 签名证书有效期至 2054-03-25（`signingReport` 实测），请务必按 §5 完成 keystore 离线备份。

---

## 7. v1.0.1 复验（2026-11-08 01:57，Round 4 收尾）

> 版本：**versionName `1.0.1` / versionCode `2`**；主树 `assembleDebug` + 全量单测 **466/0** 通过。
> 本次为**最终构建**（已包含审计 P1 修复：无 keystore 时保持未签名、R8 keep 包名 `com.furina`、退出登录二次确认、体测等级胶囊深色可读、通知附件 10MB 上限、导出超大卡片预检报错、userdata 本机状态离线恢复）。
> 本节不覆盖 §3/§6 的 v1.0.0 记录，仅追加 v1.0.1 的复验证据。

### 7.1 构建与签名

| 项 | 结果 |
|---|---|
| `:app:assembleRelease` | **BUILD SUCCESSFUL in 13 s**（52 tasks；增量，packageRelease/assembleRelease 复用已构建产物） |
| Release APK | `C:\Users\Ko_Li\.openclaw\workspace\android-class-tools\app\build\outputs\apk\release\app-release.apk` |
| 交付副本 | `android-class-tools\artifacts\classtools-1.0.1-release-signed.apk`（与构建产物逐字节一致，供交付桌面） |
| APK 字节数 | **2,274,622 B**（约 2.17 MiB） |
| APK SHA-256 | `72135018511DE666CB1AF6222988FD7FE39FF527F21C20D70C1B80D98D144DD2`（构建产物与交付副本实测一致） |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（与 v1.0.0 同一 keystore，可覆盖升级） |

### 7.2 安装与启动

| 项 | 结果 |
|---|---|
| 安装 | `adb install -r` → `Performing Streamed Install` → **Success**（v1.0.0 → v1.0.1 覆盖升级成功） |
| 包信息（dumpsys） | `com.furina.classtools`，**versionName=1.0.1，versionCode=2**，minSdk 26，targetSdk 37 |
| 启动 | `topResumedActivity = com.furina.classtools/.MainActivity` |
| 首屏 | 身份核验页（无「开发预览」入口），`v1.0.1 · Android 17 设计语言` |
| 启动截图 | `artifacts\release_launch_v101.png`（Lead 复验截图，78,370 B）；E2E 轮另存 `artifacts\e2e\release_launch_v101.png`（1080×2400，531,180 B） |
| logcat | `artifacts\e2e\release_logcat_v101.txt`（569 行 / 86,799 B）中 **FATAL/ANR = 0** |

### 7.3 证据文件（artifacts/e2e/）

- `release_build_v101.log`（构建日志）
- `release_apksigner_verify_v101.txt`（验签完整输出）
- `release_launch_v101.png`（启动截图）
- `release_ui_v101.xml`（启动后 UI dump：身份页、无开发预览、v1.0.1）
- `release_logcat_v101.txt`（启动期间全量 logcat，0 FATAL/ANR）

### 7.4 v1.0.1 相对 v1.0.0 的主要变化（供交付说明）

privacy 数据页（导出/清除云端/清除本机/协议回看）、water/roster 真实页、fitrecs 云端读写、通知附件预览、全部工具长按拖动排序（prefs.toolOrder 同步）、抽签/分组状态 `rememberSaveable` 持久化、底部导航 liveRoute 修复、外观「壁纸取色（Material You）」本地开关、设置卡片跳转「我的」页、关于版本 v1.0.1。

### 7.5 边界（与 v1.0.0 相同）

Release 包**真实账号业务全链路**（真实身份登录 → 云端数据加载）未在本轮测试，需要用户提供测试身份后另测；Debug「开发预览」链路的完整 E2E 已全绿（见 `docs/E2E_SMOKE.md` §8）。
> 回归口径：以**完整模式 31 项**（`docs/E2E_SMOKE.md` §8）为准；快速模式 29 项为同轮补充（跳过 build/install），两轮均 0 FAIL。审计 P1 修复未触及 E2E 覆盖路径，断言与修复不冲突。

---

## 8. v2.1.0 Release 复验（含 R8/WorkManager P0 修复）

> 版本：**versionName `2.1.0` / versionCode `4`**；主树 603/0 单测（Lead 数据）
> 日期：2026-11-08 09:19–09:35
> 本节为追加，保留 §7 的 v1.0.1 历史记录。

### 8.1 构建与签名

| 项 | 结果 |
|---|---|
| `:app:assembleRelease` | **BUILD SUCCESSFUL**（修复 R8 规则后，11 s；52 tasks） |
| Release APK | `C:\Users\Ko_Li\.openclaw\workspace\android-class-tools\app\build\outputs\apk\release\app-release.apk` |
| 交付副本（Lead） | `android-class-tools\artifacts\classtools-2.1.0-release-signed.apk`（与构建产物 SHA-256 实测一致） |
| APK 字节数 | **2,478,833 B**（约 2.36 MiB） |
| APK SHA-256 | **`DCBB1EAE01D79BF3EFF5A70B02742CDC08FA65922C9C9CA8CD9C3DC7FB490B93`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（与 v1.0.1/v2.0.0 同一 keystore） |

### 8.2 安装与启动复验（两次冷启动）

| 项 | 结果 |
|---|---|
| `adb install -r`（v2.0.0 → v2.1.0 覆盖升级） | **Success** |
| 冷启动 #1 | `Status: ok`，`TotalTime: 802 ms`；身份核验页（身份核验/学号/姓名/我已阅读并同意/进入工具站） |
| 冷启动 #2（force-stop 后重启） | `Status: ok`；界面与 #1 一致（`release_fix_v21_second.png`） |
| 进程 | `com.furina.classtools` 存活；`topResumedActivity=com.furina.classtools/.MainActivity` |
| 包信息（dumpsys） | **versionName=2.1.0，versionCode=4**，minSdk 26，targetSdk 37 |
| 启动截图 | `artifacts/e2e/release_fix_v21_identity.png`、`release_fix_v21_second.png`、`release_launch_v21.png`（均 1080×2400，509,385 B） |
| UI dump | `artifacts/e2e/release_ui_v21_fixed.xml`（身份页，含 `v2.1.0 · Android 17 设计语言`，**无开发预览**） |
| logcat | `artifacts/e2e/release_logcat_v21_fixed.txt`（8,484 行）中 **FATAL/ANR = 0**、无 `NoSuchMethodException` |

### 8.3 P0 事件记录（R8/WorkManager，修复前构建作废）

- **现象**：v2.1.0 首次构建（`1BDDA067F4A5E65C984C31B7ECE700E2828082C29DBF49D31DB5CB0A7AB41760`，2,429,681 B）安装成功但**冷启动必崩**，进程退出：
  `java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider` → `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`
- **根因**：v2.1.0 新增 WorkManager/Reminder（内部 Room），release R8 裁剪/重命名了 Room 生成类的无参构造器；`proguard-rules.pro` 缺少 WorkManager/Room keep 规则（Debug 不混淆，故 E2E 全绿掩盖了该问题）。
- **修复**（Lead，`app/proguard-rules.pro`）：`-keep class androidx.work.impl.WorkDatabase_Impl { *; }`、`-keep class * extends androidx.room.RoomDatabase { *; }`、`-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }`、`-dontwarn androidx.room.** / androidx.work.**`，并保留 `com.furina.classtools.data.reminder.ReminderWorker`。
- **修复后验证**：见 §8.1/§8.2（两次冷启动 + 0 FATAL）。**旧 SHA `1BDDA067…` 与大小 2,429,681 B 作废，不作为交付物。**
- **教训**：v2 起 Release 包必须做「冷启动 + logcat 0 FATAL」门禁（本复验已固化为 §8.2 流程）；Debug E2E 不能替代 release 混淆验证。

### 8.4 边界

Release 包**真实账号业务全链路**（真实身份登录 → 云端数据加载 → 提醒/权限实际授权）仍需用户提供测试身份后另测；Debug「开发预览」链路的 v2.1.0 E2E 全绿（见 `docs/E2E_SMOKE.md` §9），多设备截图为 Debug 包 + Release 身份页（见 `docs/ADAPTIVE_TEST_MATRIX.md` §8）。

---

## 9. v2.1.1 Release 复验

> 版本：**versionName `2.1.1` / versionCode `5`**；主树 639/0 单测（Lead 数据）
> 日期：2026-11-08 10:19–10:23
> 本节为追加，保留 §7（v1.0.1）、§8（v2.1.0）历史记录。

### 9.1 构建与签名

| 项 | 结果 |
|---|---|
| `:app:assembleRelease` | 通过（含 v2.1.1 新增：通知提醒 ntremind、数据隐私同步范围/本机 8 计数、小屏身份页压缩、反馈建议/使用说明接线） |
| 交付 APK | `android-class-tools\artifacts\classtools-2.1.1-release-signed.apk` |
| APK 字节数 | **2,495,213 B**（约 2.38 MiB） |
| APK SHA-256 | **`62E5C4C9C9E374B9CDD0BF357335D87F66792A85F0B1FC37A4152E3A206ECF3F`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（同一 keystore，可覆盖升级） |

### 9.2 安装与两次冷启动

| 项 | 结果 |
|---|---|
| `adb install -r` | **Success**（覆盖升级 v2.1.0 → v2.1.1） |
| 冷启动 #1 | `Status: ok`，`TotalTime: 682 ms`；身份核验页（`v2.1.1 · Android 17 设计语言`） |
| 冷启动 #2（force-stop 后） | `Status: ok`（`WaitTime: 294 ms`）；界面一致 |
| 进程 | `com.furina.classtools` 存活；`topResumedActivity=com.furina.classtools/.MainActivity` |
| 包信息（dumpsys） | **versionName=2.1.1，versionCode=5**，minSdk 26，targetSdk 37 |
| 启动截图 | `artifacts/e2e/release_fix_v211_identity.png`、`release_fix_v211_second.png`、`release_launch_v211.png`（均 1080×2400，508,686 B） |
| UI dump | `artifacts/e2e/release_ui_v211.xml`（身份页，含 `v2.1.1`，无开发预览） |
| logcat | `artifacts/e2e/release_logcat_v211_fixed.txt`（5,931 行）中 **FATAL/ANR = 0**、无 `NoSuchMethodException` |

### 9.3 v2.1.1 相对 v2.1.0 的用户可见变化（交付说明）

- **通知提醒**（ntremind）：设置页「通知提醒」开关；有明确时间/截止的通知并入本机提醒（每日汇总 + 24h 临近），点击通知跳通知页。
- **数据与隐私页**：新增「同步范围清单」与「本机数据 8 计数」。
- **小屏身份页**：压缩顶部品牌/间距，360×640dp 首屏直接可见「进入工具站」；协议链接两行布局。
- 我的页新增「反馈建议」「使用说明」入口（P1 对齐）。

### 9.4 边界

- Release 包**真实账号业务全链路**（真实身份登录/云端数据/提醒实际到达/权限实际授权）仍需用户测试身份另测。
- 反馈建议页 E2E 仅打开未提交（避免云端写），提交链路由真实账号验证。
- 通知提醒的实际通知到达需要真实作业/通知数据 + 通知权限，本轮未做到达断言（设置页开关已 E2E 覆盖）。

---

## 10. v3.0.0 Release 复验

> 版本：**versionName `3.0.0` / versionCode `6`**；主树 779/0 单测（Lead 数据）
> 日期：2026-11-08 23:02–23:04
> 本节为追加，保留 §7（v1.0.1）、§8（v2.1.0）、§9（v2.1.1）历史记录。

### 10.1 构建与签名

| 项 | 结果 |
|---|---|
| `:app:assembleRelease` | 通过（Lead 构建；含 v3 新增：深色全链路、悬浮玻璃底栏、通知测试/诊断、诊断工具、课表新布局+教务登录） |
| 交付 APK | `android-class-tools\artifacts\classtools-3.0.0-release-signed.apk` |
| APK 字节数 | **2,577,485 B**（约 2.46 MiB） |
| APK SHA-256 | **`615351BF40B45A7FD234D9B53E3E3C5747775A51A2E1C10FDB000D8AD4EE5012`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（同一 keystore，可覆盖升级） |

### 10.2 安装与两次冷启动

| 项 | 结果 |
|---|---|
| `adb install -r` | **Success**（覆盖升级 v2.1.1 → v3.0.0） |
| 冷启动 #1 | `Status: ok`，`TotalTime: 2,666 ms`；身份核验页（`v3.0.0 · Android 17 设计语言`） |
| 冷启动 #2（force-stop 后） | `Status: ok`（`WaitTime: 1,538 ms`）；界面一致 |
| 进程 | `com.furina.classtools` 存活；`topResumedActivity=com.furina.classtools/.MainActivity` |
| 包信息（dumpsys） | **versionName=3.0.0，versionCode=6**，minSdk 26，targetSdk 37 |
| 启动截图 | `artifacts/e2e/release_fix_v300_identity.png`、`release_fix_v300_second.png`、`release_launch_v300.png`（均 1080×2400，约 509 KB） |
| UI dump | `artifacts/e2e/release_ui_v300.xml`（身份页，含 `v3.0.0`，无开发预览） |
| logcat | `artifacts/e2e/release_logcat_v300_fixed.txt`（8,801 行）中 **FATAL/ANR = 0**、无 `NoSuchMethodException` |

> 说明：冷启动耗时较 v2.1.1（682 ms）上升，属 v3 首次冷启动（新版本 dex/资源初始化）+ 模拟器负载所致；两次启动均 `Status: ok` 且无崩溃，未达异常阈值。

### 10.3 v3.0.0 相对 v2.1.1 的用户可见变化（交付说明）

- **深色模式全链路**：状态栏/导航栏图标随主题、values-night 窗口背景、全页面深色适配。
- **底栏改为内容 overlay 悬浮玻璃**：不再占 Scaffold bottomBar；选中 pill 垂直居中；页面底部 120dp 内容留白。
- **通知能力**：提醒设置页「发送测试通知」「立即检查 + 诊断」；通知详情「提醒我」。
- **工具页新增「诊断」**：服务健康 · 设备诊断 · AI 分析 · 修复任务。
- **课表新布局**：新比例、左右滑动切周、日视图、导出课表图片、教务登录入口。

### 10.4 边界

- Release 包**真实账号业务全链路**（真实登录/云端数据/提醒真实到达/教务登录完整流程）仍需测试身份或真实账号另测。
- 本复验**未触发**诊断上报/自动修复/发送测试通知/反馈提交（避免云端写与系统通知副作用）。
- 深色可读性为截图人工审图结论（无自动化对比度断言）。

---

## 11. v3.1.0 Release 复验（视觉精修 P1）

> 版本：**versionName `3.1.0` / versionCode `7`**（功能与 v3.0.0 相同；单测 779/0，Lead 数据）
> 日期：2026-11-09 00:08
> 本节为追加，保留 §7–§10 历史记录。

### 11.1 构建与签名

| 项 | 结果 |
|---|---|
| 交付 APK | `android-class-tools\artifacts\classtools-3.1.0-release-signed.apk` |
| 构建产物一致性 | `app\build\outputs\apk\release\app-release.apk` 与交付副本 **SHA-256 完全一致** |
| APK 字节数 | **2,577,485 B** |
| APK SHA-256 | **`AF97EC07DA78BE0AC87AFB4D86843F7B1EAA5B14BCA2BE61D263CEBAECCD4A8C`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（同一 keystore） |

### 11.2 安装与两次冷启动

| 项 | 结果 |
|---|---|
| `adb install -r` | **Success**（覆盖升级 v3.0.0 → v3.1.0） |
| 冷启动 #1 | `Status: ok`，`TotalTime: 1,764 ms`；身份核验页（`v3.1.0 · Android 17 设计语言`） |
| 冷启动 #2（force-stop 后） | `Status: ok`（`WaitTime: 746 ms`）；界面一致 |
| 进程 | `com.furina.classtools` 存活；`topResumedActivity=com.furina.classtools/.MainActivity` |
| 包信息（dumpsys） | **versionName=3.1.0，versionCode=7**，minSdk 26，targetSdk 37 |
| 启动截图 | `artifacts/e2e/release_fix_v310_identity.png`（545,081 B）、`release_fix_v310_second.png`（545,687 B）、`release_launch_v310.png`（545,687 B），均 1080×2400 |
| UI dump | `artifacts/e2e/release_ui_v310.xml`（身份页含 `v3.1.0`，无开发预览） |
| logcat | `artifacts/e2e/release_logcat_v310_fixed.txt`（7,773 行）中 **FATAL/ANR = 0**、无 `NoSuchMethodException` |

### 11.3 v3.1.0 相对 v3.0.0 的变化（交付说明）

- 工作台/我的/全部工具**页头玻璃卡**（半透明 + 描边，无阴影避免 GPU 伪影）；
- **品牌三圆点**加入「常用工具」「全部工具」页头；
- 背景光晕加强；
- 底栏选中 **pill 高光描边 + 悬浮阴影**；
- 功能与 v3.0.0 完全相同（779/0 单测未变）。

### 11.4 边界

- 视觉精修版：本轮只做 Release 启动/签名复验 + 快速 E2E 回归（见 `E2E_SMOKE.md` §12），未重跑 v3.0.0 的完整 40 项/矩阵。
- 真实账号业务链路、提醒实际到达、教务登录完整流程仍需测试账号另测；未触发任何云端写。

---

## 12. v3.2.0 Release 复验（视觉 P2 + 作业单条提醒）

> 版本：**versionName `3.2.0` / versionCode `8`**（单测 779/0，Lead 数据）
> 日期：2026-11-09 00:37
> 本节为追加，保留 §7–§11 历史记录。

### 12.1 构建与签名

| 项 | 结果 |
|---|---|
| 交付 APK | `android-class-tools\artifacts\classtools-3.2.0-release-signed.apk` |
| 构建产物一致性 | `app\build\outputs\apk\release\app-release.apk` 与交付副本 **SHA-256 完全一致**（均 2,577,485 B） |
| APK 字节数 | **2,577,485 B** |
| APK SHA-256 | **`1307A06DD5FB755F14811FEF5A4D9961A0EB64405762702B232CBF8339C29456`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`；RSA 2048（同一 keystore） |

### 12.2 安装与两次冷启动

| 项 | 结果 |
|---|---|
| `adb install -r` | **Success**（覆盖升级 v3.1.0 → v3.2.0） |
| 冷启动 #1 | `Status: ok`，`TotalTime: 2,388 ms`；身份核验页（`v3.2.0 · Android 17 设计语言`） |
| 冷启动 #2（force-stop 后） | `Status: ok`（`WaitTime: 1,344 ms`）；界面一致 |
| 进程 | `com.furina.classtools` 存活；`topResumedActivity=com.furina.classtools/.MainActivity` |
| 包信息（dumpsys） | **versionName=3.2.0，versionCode=8**，minSdk 26，targetSdk 37 |
| 启动截图 | `artifacts/e2e/release_fix_v320_identity.png`（545,509 B）、`release_fix_v320_second.png`（545,466 B）、`release_launch_v320.png`（545,466 B），均 1080×2400 |
| UI dump | `artifacts/e2e/release_ui_v320.xml`（身份页含 `v3.2.0`，无开发预览） |
| logcat | `artifacts/e2e/release_logcat_v320_fixed.txt`（9,574 行）中 **FATAL/ANR = 0**、无 `NoSuchMethodException` |

### 12.3 v3.2.0 相对 v3.1.0 的变化（交付说明）

- **全局顶栏半透明玻璃**（工作台/我的/全部工具等页头统一玻璃卡）；
- **权限与提醒页 / 提醒设置页卡片玻璃化**；
- **作业列表每行新增「提醒我」铃铛** + 作业详情「提醒我」（选截止前 1 天/1 小时/截止时刻，走本机单条提醒排程）；
- 功能其他不变（779/0 单测未变）。

### 12.4 边界

- 本轮只做快速 E2E 回归（38 项）+ Release 启动/签名复验，未重跑 v3.0.0 的完整 40 项/矩阵。
- 作业「提醒我」的排程行为需真实作业数据 + 通知权限后另测；未触发云端写。
- 真实账号业务链路、提醒实际到达、教务登录完整流程仍需测试账号另测。

---

## 13. v3.3.0 Release 构建（新增「演示」工具）

> 版本：**versionName `3.3.0` / versionCode `9`**（新增功能 = minor，符合 §4「每次发布递增 versionCode」）
> 日期：2026-11-09 22:54；本轮为「演示」功能实现的构建/测试证据，安装与冷启动复验由 Lead 交付时执行。

### 13.1 构建与签名

| 项 | 结果 |
|---|---|
| 构建命令 | `$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'; .\gradlew.bat assembleRelease --console=plain` |
| 结果 | **BUILD SUCCESSFUL in 1m 38s**（R8 + 资源压缩 + lintVitalRelease 通过） |
| 交付 APK | `android-class-tools\artifacts\classtools-3.3.0-release-signed.apk` |
| APK 字节数 | **2,692,174 B**（2.57 MiB；较 v3.2.0 +114,689 B = 演示模块/预览无额外依赖，仅代码与资源） |
| APK SHA-256 | **`B4E88732028AA32B21133EA296AC3FD6A5C4176083E32186434CC5FE9459BF2B`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；v2 scheme = true；Number of signers = 1 |
| 签名证书 | 同一 keystore：`CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`；证书 SHA-256 `974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a` |
| 新增依赖 | **无**（图片加载自建 OkHttp+BitmapFactory，视频用系统 VideoView/MediaController） |

### 13.2 测试与预览

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL，904 tests / 0 failures / 0 errors / 0 skipped**（74 suites；新增 PPT 8 类 87 项，无原有用例回退） |
| Compose @Preview 渲图 | `android preview render` **exit 0**，5 张 PNG：`artifacts/ppt-list-states.png`、`ppt-list-empty.png`、`ppt-player-169.png`、`ppt-player-43.png`、`ppt-player-dark.png`（原始输出 `artifacts/previews_ppt/`） |
| 详细实现/契约/已知限制 | `docs/PPT_20261109.md` |

### 13.3 边界

- 本轮未做模拟器安装/冷启动复验（Lead 交付时统一执行）；签名与构建产物已验证。
- 「演示」真实端到端链路（测试身份上传 → 本机转换机转换 → manifest 可达 → 播放器加载真实图片/视频）按契约 §9.3 由 Lead 执行。
- 播放器转场为保守淡入实现（契约允许降级）；续播/重命名列为 P2，详见 `docs/PPT_20261109.md` §5。

---

## 14. v3.4.0 Release 构建（「演示」v1.2：原样播放视频模式）

> 版本：**versionName `3.4.0` / versionCode `10`**；日期：2026-11-10 21:12。安装/冷启动复验由 Lead 统一执行。

### 14.1 构建与签名

| 项 | 结果 |
|---|---|
| 构建命令 | `$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'; .\gradlew.bat assembleRelease --console=plain` |
| 结果 | **BUILD SUCCESSFUL in 3m 34s**（R8 + 资源压缩 + lintVitalRelease 通过） |
| 构建产物 | `app\build\outputs\apk\release\app-release.apk` = **2,724,998 B** |
| 交付副本 | `android-class-tools\artifacts\classtools-3.4.0-release-signed.apk`（SHA-256 与构建产物一致） |
| APK SHA-256 | **`542943B43EE1857EABFE275B6D1C37AF1DFD3FE9DBFF3F06AA7C7947737DCC0D`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；APK Signature Scheme v2 = true；1 signer；证书 `CN=ClassTools, OU=Furina, O=Furina, L=Zhangzhou, ST=Fujian, C=CN`（同一 keystore，SHA-256 `974854df…bec4a1a`） |
| 新增依赖 | **无**（视频用系统 MediaPlayer + SurfaceView；图片沿用自建 OkHttp+BitmapFactory） |

### 14.2 测试与预览

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL，954 tests / 0 failures / 0 errors / 0 skipped（78 suites）**；v1.2 新增 20 项（video 解析 4 + 视频模式 16），原有 934 基线无回退 |
| Compose @Preview 渲图 | `android preview render`：新增「65 原样播放控制条」「66 分步热点（64dp 脉冲）」+ 列表「继续」徽标；PNG 见 `artifacts/ppt-*.png` |
| 详细实现/契约对应/OSS 域名与下载限制 | `docs/PPT_VIDEO_20261110.md` |

### 14.3 边界

- 本轮未做模拟器安装/冷启动复验（Lead 交付时统一执行）；签名与构建产物已验证。
- 「原样播放」真实端到端依赖转换机导出 `manifest.video`（Lead 对样例 deck `dmwalrdlgevz90l` 执行 `agent.py --deck <id> --video-only`）；
  用户 deck 当前 manifest 尚无 `video`，安卓端按 §11.1 自动回退分步，不报错。
- OSS 默认域名的 force-download 头对个别机型在线播放的影响、视频下载限制详见 `docs/PPT_VIDEO_20261110.md` §5。

---

## 15. v3.5.0 Release 构建（「演示」v2.0：下载中心 = 兼容性处理 + 多端下载）

> 版本：**versionName `3.5.0` / versionCode `11`**；日期：2026-11-10 22:20。安装/冷启动复验由 Lead 统一执行。

### 15.1 构建与签名

| 项 | 结果 |
|---|---|
| 构建命令 | `$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'; .\gradlew.bat assembleRelease --console=plain` |
| 结果 | **BUILD SUCCESSFUL in 1m 45s**（R8 + 资源压缩 + lintVitalRelease 通过） |
| 构建产物 | `app\build\outputs\apk\release\app-release.apk` = **2,741,438 B** |
| 交付副本 | `android-class-tools\artifacts\classtools-3.5.0-release-signed.apk`（SHA-256 与构建产物一致） |
| APK SHA-256 | **`C45070ACB6C91745F27DD28D5C28075B64688999A50B1F4DFB5D92406B947647`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；APK Signature Scheme v2 = true；1 signer；证书与 v3.4.0 同一 keystore |
| 新增依赖 | **无**（DownloadManager / FileProvider；旧播放器 MediaPlayer+SurfaceView 保留） |

### 15.2 测试与预览

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL，982 tests / 0 failures / 0 errors / 0 skipped（79 suites）**；v2.0 新增 28 项（下载逻辑 16 + 解析 5 + 仓库 7），原有 954 基线无回退 |
| Compose @Preview 渲图 | 新增「67 下载详情 · 三行+大小」「68 兼容提示」「69 旧 deck」；列表预览加「兼容版/PDF」标签；PNG 见 `artifacts/ppt-*.png` |
| 详细实现/契约对应/已知限制 | `docs/PPT_DOWNLOAD_20261110.md` |

### 15.3 边界

- 本轮未做模拟器安装/冷启动复验（Lead 交付时统一执行）；签名与构建产物已验证。
- **下载位置**：受「不申请存储读取权限」红线 + scoped storage 限制，下载保存到应用外部下载目录（系统下载通知可见，FileProvider 打开/分享），不写公共 Downloads。
- 真实端到端（`dmwalrdlgevz90l` 跑 `--compat-only` 后三端下载/打开/质量 sha256 断言）由 Lead 执行；安卓侧为单测 + 预览 + 构建验证。

---

## 16. v3.6.0 Release 构建（「演示」v2.3：视频/音频单独下载）

> 版本：**versionName `3.6.0` / versionCode `12`**；日期：2026-11-11 19:52。安装/冷启动复验由 Lead 统一执行。

### 16.1 构建与签名

| 项 | 结果 |
|---|---|
| 构建命令 | `$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'; .\gradlew.bat assembleRelease --console=plain` |
| 结果 | **BUILD SUCCESSFUL in 2m 20s**（R8 + 资源压缩 + lintVitalRelease 通过） |
| 构建产物 | `app\build\outputs\apk\release\app-release.apk` = **2,757,826 B** |
| 交付副本 | `android-class-tools\artifacts\classtools-3.6.0-release-signed.apk` |
| APK SHA-256 | **`514527F08641889EF83FCC9CCEED55E2979FF147D50D146C828885A5BF615E1E`** |
| `apksigner verify --verbose --print-certs` | **Verifies**；APK Signature Scheme v2 = true；1 signer；证书与 v3.5.0 同一 keystore |
| 新增依赖 | **无** |

### 16.2 测试与预览

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **BUILD SUCCESSFUL，994 tests / 0 failures / 0 errors / 0 skipped（80 suites）**；v2.3 新增 12 项（媒体行逻辑 9 + media_files 解析 3），原有 982 基线无回退 |
| Compose @Preview 渲图 | 下载详情预览加入 2 视频 + 1 音频（「视频单独下载」分组）；10 张 Ppt 预览全部渲出，PNG 见 `artifacts/ppt-*.png` |
| 详细实现/契约对应 | `docs/PPT_DOWNLOAD_20261110.md` §6 |

### 16.3 边界

- 本轮未做模拟器安装/冷启动复验（Lead 统一执行）；签名与构建产物已验证。
- 真实 `media_files` 需转换机 v2.3 完成抽取后才会出现在 manifest；无该字段的旧 deck 不显示分组（已兼容）。
- 下载落应用外部下载目录（不申请存储权限红线），完成后 `ACTION_VIEW`（video/*|audio/*）交给系统播放器/WPS 或分享；详见 §5 已知限制。
