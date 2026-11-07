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
