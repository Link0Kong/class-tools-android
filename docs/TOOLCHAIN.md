# Android 原生 App 构建工具链说明（TOOLCHAIN.md）

> 适用工程：`android-class-tools`（班级工具站 Android 原生客户端）
> 机器：Windows 11（10.0.26300），2026-11-07 实测
> 状态：**全链路已验证可用** —— JDK/SDK/Gradle 安装 → `assembleDebug` 成功 → Compose `@Preview` 渲染成功 → Android 17 模拟器启动 + APK 安装运行 + 截图成功。
> 本文件所有版本号、路径、输出均为本机实测结果（不是"推荐/应该"）。

---

## 1. 绝对路径总表

| 用途 | 绝对路径 | 版本 / 备注 |
|---|---|---|
| JDK（正式工程用） | `C:\AndroidToolchain\jdk` | OpenJDK **21.0.2+13-58**（Oracle 构建，来自华为云镜像） |
| JDK 17（备选，正式工程实测用） | `C:\AndroidToolchain\jdk17` | OpenJDK **17.0.2+8-86** |
| Android SDK 根 | `C:\AndroidToolchain\sdk` | ANDROID_HOME / ANDROID_SDK_ROOT |
| Android CLI / cmdline-tools | `C:\AndroidToolchain\sdk\cmdline-tools\latest` | cmdline-tools **23.0.0**；`android.exe` 版本 **1.1.16717390**（launcher 1.0.15498356） |
| sdkmanager | `C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\sdkmanager.bat` | **已 deprecated**，仅仍可用于 `--list_installed`（见 §4） |
| platform-tools | `C:\AndroidToolchain\sdk\platform-tools` | **37.0.2**（`adb version` = 1.0.41 / 37.0.2-16765528） |
| Android 平台 | `C:\AndroidToolchain\sdk\platforms\android-37.0` | **API 37.0**，extension-level 22，`android.jar` 43,044,472 B；`source.properties` 中 `AndroidVersion.ApiLevel=37.0` |
| build-tools | `C:\AndroidToolchain\sdk\build-tools\37.0.0`（及 AGP 自动装的 `36.0.0`） | AGP 9.5.1 默认用 **36.0.0**；显式 `buildToolsVersion="37.0.0"` 亦实测可用 |
| Gradle | `C:\AndroidToolchain\gradle\gradle-9.9.0` | **9.9.0**（内嵌 Kotlin 2.4.21、Groovy 4.0.33、Ant 1.10.17） |
| Gradle 可执行 | `C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat` | 无 wrapper 时可直接用 |
| emulator | `C:\AndroidToolchain\sdk\emulator\emulator.exe` | **37.2.13.0**（build_id 16740729） |
| system image | `C:\AndroidToolchain\sdk\system-images\android-37.0\google_apis\x86_64` | 包版本 **8.0.0**，约 **5.3 GB**（解压后；官方索引标称压缩包 2 GB 级别，实际更大） |
| AVD | `<AVD 目录>\ct-api37.avd` | Android **17.0（"CinnamonBun"）**，google_apis/x86_64，medium_phone 配置 |
| 冒烟工程 | `C:\AndroidToolchain\smoke`、`C:\AndroidToolchain\smoke-k24`、`C:\AndroidToolchain\smoke-kgp` | 三套 Kotlin 接线各一（§5） |
| 安装/构建日志 | `C:\AndroidToolchain\work\*.log` | 见 §11 |
| 仓库产物目录 | `android-class-tools\artifacts\` | APK + Previews + 模拟器截图 |

磁盘占用（实测）：SDK 6.91 GB、JDK21 0.31 GB、JDK17 0.29 GB、Gradle 0.14 GB、downloads 0.63 GB（可删）。

---

## 2. 每次 shell 必须显式设置环境（PowerShell 模板）

`pwsh` 每次调用都是新进程、不继承上次环境变量，因此**每条命令前都要带**：

```powershell
$env:JAVA_HOME      = 'C:\AndroidToolchain\jdk'        # 正式工程用 JDK21；用 JDK17 时改成 C:\AndroidToolchain\jdk17
$env:ANDROID_HOME   = 'C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT = 'C:\AndroidToolchain\sdk'
$env:PATH = "C:\AndroidToolchain\jdk\bin;C:\AndroidToolchain\sdk\cmdline-tools\latest\bin;C:\AndroidToolchain\sdk\platform-tools;C:\AndroidToolchain\sdk\emulator;$env:PATH"
```

可复用脚本（本次新增）：`android-class-tools\scripts\toolchain\toolchain-env.ps1`（纯 ASCII 编写，Windows PowerShell 5.1 与 pwsh 7 均可加载），用法：

```powershell
. <项目根>\scripts\toolchain\toolchain-env.ps1   # 默认 JDK21
. .\scripts\toolchain\toolchain-env.ps1 -Jdk 17                                                # 切 JDK17
```

脚本会设置四个变量并打印 java 版本 / compileSdk 37 平台自检（实测输出 `compileSdk 37 = OK (...)`）。

> ⚠️ 同类脚本务必**纯 ASCII**：Windows PowerShell 5.1 会把无 BOM 的 UTF-8 .ps1 按 ANSI 解析，中文注释会导致语法解析错乱（本机实测踩过，改写为英文注释后两个 PowerShell 版本都通过）。

> `local.properties` 里的 `sdk.dir` 只是给 Gradle 找 SDK 用，**不能替代 JAVA_HOME**；`gradlew` 需要 JAVA_HOME（或 PATH 上的 java）才能起 JVM。

---

## 3. 版本矩阵（全部实测）

### 3.1 推荐组合（与正式工程一致，已交叉验证）

| 组件 | 版本 | 验证方式 |
|---|---|---|
| JDK | **21.0.2**（或 17.0.2） | `gradle --version` 显示 Launcher JVM 21.0.2 / 17.0.2，两者均 `BUILD SUCCESSFUL` |
| Gradle | **9.9.0** | `gradle --version` / `gradlew --version` |
| AGP | **9.5.1** | `com.android.application` 9.5.1（google maven） |
| Kotlin（built-in） | **2.4.21**（由声明的 compose/serialization 插件版本决定） | 依赖树 `kotlin-stdlib:2.4.21`，见 §3.2 |
| Compose 编译器插件 | `org.jetbrains.kotlin.plugin.compose` **2.4.21** | 与 Kotlin 严格同版本 |
| Kotlin 序列化插件 | `org.jetbrains.kotlin.plugin.serialization` **2.4.21** | 冒烟工程含 `@Serializable` 实测 |
| compileSdk | **37**（映射 `platforms/android-37.0`） | 构建 + 预览 + 模拟器均通过 |
| targetSdk / minSdk | 37 / 26（正式工程） | — |
| Compose BOM | **2026.09.00** | 实际解析：`material3=1.4.0`、`ui/foundation/animation/runtime=1.12.1`、`material-icons=1.7.8` |
| activity-compose | **1.13.0** | — |
| build-tools | 36.0.0（AGP 默认，自动安装）/ 37.0.0（显式指定也可） | 冒烟工程两者都构建成功 |
| platform-tools | 37.0.2 | adb / 模拟器安装截图 |

**结论：AGP 9.5.1 + built-in Kotlin（compose/serialization 插件 2.4.21）+ Gradle 9.9.0 + JDK17/21 + compileSdk 37 + Compose BOM 2026.09.00 为确认可用组合。**

### 3.2 built-in Kotlin 的版本规则（关键实测结论）

AGP 9.5.1 的 POM 里带的是 `kotlin-gradle-plugin:2.2.10`（只是默认下限）；实际编译用的 Kotlin 版本 = **你在 `plugins {}` 里声明的 Kotlin 插件版本**：

| 声明 | 实测 kotlin-stdlib 解析版本 | 结果 |
|---|---|---|
| 只加 compose 插件 **2.2.10** | 2.2.10 | ✅ `assembleDebug` 成功（`smoke`） |
| compose + serialization 插件 **2.4.21** | **2.4.21** | ✅ 成功，含 `@Serializable`/`Json.encodeToString` 运行期可用（`smoke-k24`） |

→ 正式工程一定要让 **compose 编译器插件与 serialization 插件版本完全一致**（正式工程 = 2.4.21），否则会报 Compose 编译器与 Kotlin 版本不匹配。

### 3.3 三种 Kotlin 接线方式（第三种仅作回退方案）

| 方式 | gradle.properties | plugins（app 模块） | 实测结果 |
|---|---|---|---|
| A. built-in Kotlin（**推荐**） | 无额外开关 | `com.android.application` + `org.jetbrains.kotlin.plugin.compose` | ✅ 成功 |
| B. built-in Kotlin + 2.4.21 全家桶（**正式工程用**） | 无额外开关 | 上面两个 + `org.jetbrains.kotlin.plugin.serialization` | ✅ 成功 |
| C. 经典 KGP opt-out（**仅临时回退**） | `android.builtInKotlin=false` **且** `android.newDsl=false` | + `org.jetbrains.kotlin.android` 2.4.21 | ✅ 成功（两开关缺一不可） |

> 只设 `android.builtInKotlin=false` 会失败：
> `The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0 new DSL ('android.newDsl=true' is enabled by default).`
> 官方提示：要么迁移 built-in Kotlin（推荐），要么 `android.newDsl=false` 临时绕过。
> 另：`android.builtInKotlin=false` 自 AGP 9.5.1 起已 deprecated（AGP 10 将移除），不建议正式工程采用。

---

## 4. Android SDK 包安装（新 CLI 用法，重要）

### 4.1 事实：sdkmanager 已废弃，改用 `android` CLI

- `cmdline-tools 16111833`（版本 23.0.0）开始，`sdkmanager` 会打印：
  `WARNING: The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead.`
- 替代品是同目录的 **`android.exe`**（`android sdk install/list/update/remove`）。
- **`--licenses` 已不需要**（新 CLI 提示 `--licenses option is no longer needed`），安装时自动接受许可。
- `sdkmanager` 仍可用于查看：`sdkmanager.bat --list_installed --sdk_root=C:\AndroidToolchain\sdk`。

### 4.2 安装命令（包名用「斜杠」，不是分号）

```powershell
$android = 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\android.exe'
$sdk     = 'C:\AndroidToolchain\sdk'

# 列出可安装包（--all 显示全部；--canary 才包含 canary 索引里的新包）
& $android --sdk=$sdk sdk list --all --canary 'platforms*'
& $android --sdk=$sdk sdk list --all --canary 'system-images*'

# 安装（注意包名是 platforms/android-37.0 这种斜杠形式）
& $android --sdk=$sdk sdk install 'platform-tools'
& $android --sdk=$sdk sdk install 'platforms/android-37.0'
& $android --sdk=$sdk sdk install 'build-tools/37.0.0'
& $android --sdk=$sdk sdk install 'emulator'
& $android --sdk=$sdk sdk install 'system-images/android-37.0/google_apis/x86_64'
```

> ⚠️ 用 sdkmanager 时代的分号包名（`platforms;android-37.0`）传给 `sdkmanager.bat` 会被新 CLI 拆开，报
> `Package platforms not found. / Package android-37.0 not found.`。**用 `android sdk install` + 斜杠包名**。
> 也不需要 `--channel=3`：`android sdk install platforms/android-37.0` 直接成功（系统自动查到 canary 索引）。

### 4.3 `sdkmanager --list_installed` 关键行（实测输出）

```
Installed packages:
  build-tools/36.0.0                                    36.0.0                      Android SDK Build-Tools 36
  build-tools/37.0.0                                    37.0.0                      Android SDK Build-Tools 37
  build/templates                                       0.1.3                       Android Project Templates
  cmdline-tools/latest                                  unknown   ->        23.0.0  Android SDK Command-line Tools (latest)
  emulator                                              37.2.13                     Android Emulator
  platform-tools                                 canary 37.0.2    ->        37.0.1  Android SDK Platform-Tools
  platforms/android-37.0                                2.0.0                       Android SDK Platform 37.0
  system-images/android-37.0/google_apis/x86_64         8.0.0                       Google APIs Intel x86_64 Atom System Image
```

> `build-tools/36.0.0` 是第一次 `assembleDebug` 时 AGP **自动下载安装**的（AGP 9.5.1 默认 36.0.0），不是手动装的。
> API 37 平台在 SDK 里的目录名是 **`android-37.0`**（带小版本号），AGP 的 `compileSdk = 37` 能正确匹配到它。

---

## 5. 冒烟工程（三套，验证版本矩阵用）

全部位于 `C:\AndroidToolchain\`（不属于仓库，不污染正式工程）：

| 目录 | Kotlin 接线 | 关键配置 | APK 绝对路径（均存在） |
|---|---|---|---|
| `smoke` | built-in Kotlin + compose 2.2.10 | compileSdk 37, minSdk 24, BOM 2026.09.00 | `C:\AndroidToolchain\smoke\app\build\outputs\apk\debug\app-debug.apk`（11,563,595 B） |
| `smoke-k24` | built-in Kotlin + compose/serialization 2.4.21（**= 正式工程组合**） | 同上 + `@Serializable` + `@Preview` | `C:\AndroidToolchain\smoke-k24\app\build\outputs\apk\debug\app-debug.apk`（11,793,343 B） |
| `smoke-kgp` | 经典 KGP 2.4.21 opt-out | `android.builtInKotlin=false` + `android.newDsl=false`，`buildToolsVersion="37.0.0"` | `C:\AndroidToolchain\smoke-kgp\app\build\outputs\apk\debug\app-debug.apk`（11,645,891 B） |

### 5.1 构建命令

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
cd C:\AndroidToolchain\smoke-k24
# 方式 1：直接用安装的 gradle
& C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat :app:assembleDebug --console=plain --stacktrace
# 方式 2：用项目 wrapper（smoke-k24 已生成，distributionUrl 指向腾讯镜像）
.\gradlew.bat :app:assembleDebug --console=plain
```

### 5.2 成功日志摘要（实测）

- `smoke`（built-in 2.2.10）：`BUILD SUCCESSFUL in 1m 51s` / `36 actionable tasks: 36 executed`（首次含依赖下载）
- `smoke-k24`（built-in 2.4.21）：`BUILD SUCCESSFUL in 33s` / `36 actionable tasks: 36 executed`
- `smoke-k24` 用 **JDK17** 重新 clean 构建：`BUILD SUCCESSFUL in 34s`；`gradlew --version` → `Launcher JVM: 17.0.2 (Oracle Corporation 17.0.2+8-86)`、`Daemon JVM: C:\AndroidToolchain\jdk17`
- `smoke-kgp`（经典 KGP）：`BUILD SUCCESSFUL in 1m 8s`
- 唯一常见 warning（可忽略）：
  `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so.`
  原因：该 .so 无可 strip 符号，AGP 原样打包，不影响运行。

### 5.3 依赖仓库速度（**必读**，否则首次构建极慢）

| 仓库 | 实测速度（本机 2026-11-07） |
|---|---|
| `repo.maven.apache.org` 直连 | **≈130 KB/s**（不可接受） |
| `maven.aliyun.com/repository/google` | **≈11 MB/s** |
| `maven.aliyun.com/repository/public` | **≈10 MB/s** |
| `dl.google.com` 直连 | ≈5–6 MB/s |

正式工程 `settings.gradle.kts` 已把阿里云镜像放在最前（实测有效），保持即可。

---

## 6. 给正式工程的配置建议

### 6.1 `local.properties`（每台机器不同，**不入库**）

```properties
sdk.dir=C\:\\AndroidToolchain\\sdk
```
（也可写正斜杠 `sdk.dir=C:/AndroidToolchain/sdk`，AGP 两种都能解析；`.` 和 `:` 的转义只在反斜杠写法里需要。）

### 6.2 `gradle.properties`（建议值，正式工程当前已有等价配置）

```properties
# JVM：4g 够用（19 依赖 + Compose 的正式工程实测峰值 < 3g）
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official

# 不要设 android.builtInKotlin / android.newDsl，保持 AGP 9 默认（built-in Kotlin + 新 DSL）
```

### 6.3 `settings.gradle.kts` 仓库（与正式工程一致，建议保留）

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral(); gradlePluginPortal()   // 兜底
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral()                          // 兜底
    }
}
```

### 6.4 `gradle/wrapper/gradle-wrapper.properties`（**必须换成国内镜像**）

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.9.0-bin.zip
```
`services.gradle.org` 直连实测 ≈20–40 KB/s，换腾讯镜像后秒级下载。正式工程已采用此地址，`gradlew` 可用（实测 `Gradle 9.9.0`）。
仓库里已有 `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`，新机器无需再执行 `gradle wrapper`。

### 6.5 Kotlin 配置注意

- built-in Kotlin 下 **不要**加 `org.jetbrains.kotlin.android` 插件。
- `compose` 与 `serialization` 插件用**同一版本**（2.4.21）。
- 正式工程 app 模块用了 `kotlin { jvmToolchain(17) }`（built-in Kotlin 下可用，实测构建成功）；`compileOptions` 17 与其一致。
- 若出现 "Compose Compiler ... Kotlin version" 不匹配，先检查两个 Kotlin 插件版本是否一致。

---

## 7. Compose `@Preview` 渲染（无需模拟器/IDE）

用途：CI/无设备环境把 Compose 预览渲染成 PNG，做 UI 走查（本次用于产出 `artifacts/previews`）。

### 7.1 三个必踩的坑（都已解决）

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | `error: Gradle wrapper not found: ...\gradlew.bat` | `android preview` 用项目 wrapper 构建 | 工程根必须有 `gradlew.bat`/`gradlew`/`gradle/wrapper/*`（正式工程已有） |
| 2 | `error: Invalid relative Gradle project path: 'C:\...\android-class-tools'` | reference 只接受**相对 Gradle 路径** | 在工程根目录用 `app`（或 `:app`）；**不要传绝对路径** |
| 3 | `Multiple build systems detected for path ...: gradle, bazel` | **Windows 文件系统大小写不敏感**：根目录存在 Gradle 的 `build/` 输出目录，CLI 把 `build` 误认成 Bazel 的 `BUILD` 标记 | 在**干净副本**里渲染（robocopy 同步源码时排除 `build/.gradle/.kotlin/artifacts/...`，并删掉副本里的 `build` 目录），见 `scripts/render_previews.ps1` |
| 4 | 渲染必须给 `--fqn`：`Error: At least one --fqn must be specified` | 需先 `preview list` 拿 FQN | `android preview list app --format json` → 取 `previews[].methodFqn` → `--fqn` |

### 7.2 标准命令

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'   # 17/21 均可
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$android = 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\android.exe'

# 在工程根目录（clean 副本）里：
& $android preview list app --format json          # 列出 @Preview 的 methodFqn
& $android preview render app --output C:\path\out --fqn com.example.FooKt.MyPreview
# 也可一次渲染全部：把每个 FQN 都用 --fqn 传一遍；--component-tree 额外输出组件树 JSON
```

输出布局：`<out>\<包路径>\<文件Kt>\<PreviewName>_0.png`（同名 `.json` 需 `--component-tree`）。

### 7.3 现成脚本（Lead 已写）

`android-class-tools\scripts\render_previews.ps1`：robocopy 同步到 `C:\Temp\ct-preview`（排除 build/.gradle/.kotlin/artifacts/design/docs/scripts）→ 删副本 build 目录 → `preview list app` 取全部 FQN → `preview render app --output artifacts\previews`。用法：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <项目根>\scripts\render_previews.ps1
# 输出默认到 <repo>\artifacts\previews
```

### 7.4 本次渲染产物（8 张 PNG，实测 1082 宽）

目录：`<项目根>\artifacts\previews\com\furina\classtools\ui\preview\ScreenPreviewsKt\`

| 文件 | 字节 | 像素 |
|---|---|---|
| `AllToolsPreview_0.png` | 497,413 | 1082×2402 |
| `DesignTokensPreview_0.png` | 454,717 | 1082×3938 |
| `HomeDarkPreview_0.png` | 622,336 | 1082×2402 |
| `HomeLightPreview_0.png` | 606,050 | 1082×2402 |
| `IdentityDarkPreview_0.png` | 635,729 | 1082×2402 |
| `IdentityLightPreview_0.png` | 619,275 | 1082×2402 |
| `ProfilePreview_0.png` | 368,263 | 1082×2402 |
| `ToolPreview_0.png` | 634,012 | 1082×2402 |

（预期 7 张，实际 8 张 —— 多了 `ToolPreview`；全部为有效 PNG，UI 中文/图标/毛玻璃色块渲染正常。）

### 7.5 冒烟工程验证的 preview 命令（最小复现）

```powershell
cd C:\AndroidToolchain\smoke-k24
& 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\android.exe' preview list app --format json
# → previews[0].methodFqn = com.example.smoke.MainActivityKt.SmokePreview
& 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\android.exe' preview render app --output C:\AndroidToolchain\work\previews --fqn com.example.smoke.MainActivityKt.SmokePreview
# 实测输出：...\previews\com\example\smoke\MainActivityKt\SmokePreview_0.png（945×1680，29,819 B）
```

---

## 8. 模拟器（API 37 / Android 17，已实测可跑）

### 8.1 硬件加速

```
> emulator.exe -accel-check
accel:
0
WHPX(10.0.26300) is installed and usable.
accel
```
（`accel: 0` = 可用；本机 HypervisorPresent=True，WHPX 正常。）

### 8.2 创建 AVD

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$avdmanager = 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\avdmanager.bat'
'no' | & $avdmanager create avd -n ct-api37 -k 'system-images;android-37.0;google_apis;x86_64' -d medium_phone --force
```

- AVD 路径：`<AVD 目录>\ct-api37.avd`
- 实测 `avdmanager list avd`：`Based on: Android 17.0 ("CinnamonBun") Tag/ABI: google_apis/x86_64`
- `config.ini`：`image.sysdir.1=system-images\android-37.0\google_apis\x86_64\`、`hw.ramSize=2G`
- 注意：`android emulator create medium_phone`（新 CLI）会**自己选默认镜像**（本机实测选成 android-36 google_apis_playstore，2.3 GB），要指定 android-37.0 镜像请用 `avdmanager`。

### 8.3 启动（headless，实测 46–50 s boot 完成）

```powershell
$env:PATH="C:\AndroidToolchain\jdk\bin;C:\AndroidToolchain\sdk\platform-tools;C:\AndroidToolchain\sdk\emulator;$env:PATH"
& 'C:\AndroidToolchain\sdk\emulator\emulator.exe' -avd ct-api37 `
    -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
```

日志关键行：`INFO | Boot completed in 50483 ms`。

### 8.4 等 boot + 安装 APK + 截图（实测命令序列）

```powershell
$adb = 'C:\AndroidToolchain\sdk\platform-tools\adb.exe'
& $adb start-server
# 等 boot：循环直到 (adb shell getprop sys.boot_completed) == '1'
do { Start-Sleep -Seconds 5 } until ((& $adb shell getprop sys.boot_completed).Trim() -eq '1')

& $adb shell getprop ro.build.version.sdk        # 37
& $adb shell getprop ro.build.version.release    # 17
& $adb shell getprop ro.product.cpu.abi          # x86_64

& $adb install -r '<项目根>\artifacts\classtools-1.0.0-debug.apk'
& $adb shell am start -n 'com.furina.classtools.debug/com.furina.classtools.MainActivity'
Start-Sleep -Seconds 8
& adb exec-out screencap -p > <repo>\artifacts\emulator-home.png
```

实测结果：
- `adb devices`：`emulator-5554  device  product:sdk_gphone64_x86_64 ...`
- `adb install`：`Performing Streamed Install` → `Success`
- `dumpsys activity`：`topResumedActivity=...com.furina.classtools.debug/com.furina.classtools.MainActivity`
- 截图：`artifacts\emulator-home.png`，**1080×2400**，569,026 B，显示「班级工具站 / 身份核验（学号+姓名）」页面，中文与 17 状态栏正常。
- 无崩溃：`adb logcat -d | grep -E 'FATAL|AndroidRuntime'` 无输出。

> 说明：模拟器进程目前仍在后台运行（job `pwsh-7d513c7ef01d4c40`，emulator + qemu-system-x86_64-headless）。需要释放内存时可 `adb emu kill`；下次复跑直接用 §8.3 命令。
> `-gpu swiftshader_indirect` 是纯软件渲染，稳定但截图较慢（截图约 1–2 s）；不需要图形时可加 `-no-window` 即可（已加）。

---

## 9. 踩坑记录（按严重度）

1. **Windows 大小写不敏感 → `preview render` 把 Gradle `build/` 误判为 Bazel `BUILD`**
   现象：`Multiple build systems detected for path ...: gradle, bazel`。
   解决：干净副本渲染（见 §7.1 坑 3 / `scripts/render_previews.ps1`）。
2. **sdkmanager 废弃 + 包名分隔符变了**
   旧：`sdkmanager "platforms;android-37.0"` → 新 CLI 报 `Package platforms not found`。
   新：`android sdk install platforms/android-37.0`（斜杠）；无需 `--licenses`、无需 `--channel=3`。
3. **AGP 9 新 DSL 与经典 KGP 不兼容**
   `org.jetbrains.kotlin.android` 直接报 incompatible；必须 built-in Kotlin，或 `android.builtInKotlin=false` + `android.newDsl=false` 双开关。
4. **Maven Central 直连龟速（130 KB/s）** → 阿里云镜像（10 MB/s）放最前；`dl.google.com` 可直连兜底。
5. **Gradle 分发包（wrapper）直连龟速** → `distributionUrl` 换腾讯镜像。
6. **AGP 默认 build-tools 是 36.0.0**（不是 37.0.0），首次构建会自动下载安装；想固定 37 需显式 `buildToolsVersion = "37.0.0"`（实测可用）。
7. **`android preview` 必须项目有 gradlew**；reference 必须是相对 Gradle 路径 `app`（绝对路径报 `Invalid relative Gradle project path`）。
8. **Adoptium 官方源下载 Temurin 极慢**（≈1 KB/s，一度 200MB 预计 55 小时）；JDK 改用华为云镜像 `https://mirrors.huaweicloud.com/openjdk/21.0.2/openjdk-21.0.2_windows-x64_bin.zip`（≈4 MB/s）；cmdline-tools 用 dl.google.com（≈5 MB/s）；Gradle 用 `https://mirrors.cloud.tencent.com/gradle/gradle-9.9.0-bin.zip`。
9. **`android emulator create <profile>` 不能指定系统镜像**（自动选了 android-36 的 2.3GB 包）；要 android-37.0 用 `avdmanager create avd -k 'system-images;android-37.0;google_apis;x86_64'`。
10. **JDK17 别忘 `JAVA_HOME`**：`gradlew --version` 会显示实际 Launcher JVM，用它确认；JDK17 下正式工程组合同样 `BUILD SUCCESSFUL`。

---

## 10. 可复制的最短上手命令（正式工程）

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:PATH="C:\AndroidToolchain\jdk17\bin;C:\AndroidToolchain\sdk\platform-tools;$env:PATH"
cd <项目根>

.\gradlew.bat :app:assembleDebug --console=plain          # 构建 debug APK
# 产物：app\build\outputs\apk\debug\app-debug.apk；正式工程另存一份→ artifacts\classtools-1.0.0-debug.apk

# 单元测试 / Lint（如需）
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
```

---

## 11. 日志与现场（排查用）

- 下载/解压：`C:\AndroidToolchain\work\`（`licenses.log`、`install_sdk.log`、`install_pkgs.log`、`install_emulator.log`）
- 冒烟构建：`C:\AndroidToolchain\work\build_smoke_A.log`、`build_smoke_B.log`、`build_smoke_C_k24.log`、`build_smoke_C_jdk17.log`
- 预览：`C:\AndroidToolchain\work\preview_render.log`、`preview_list_repo.log`
- 模拟器：`C:\AndroidToolchain\work\emulator_ct_api37.log`、`emulator_boot.log`
- 冒烟 preview 产物：`C:\AndroidToolchain\work\previews\com\example\smoke\MainActivityKt\SmokePreview_0.png`

---

## 12. 清理建议（可选，不改版本）

- `C:\AndroidToolchain\downloads`（0.63 GB：JDK/cmdline-tools/Gradle 压缩包）验证后可删；解压后的 jdk/sdk/gradle 不要删。
- `C:\AndroidToolchain\repo-probe`（我探测 bazel 误判时复制的工程副本）可删。
- `C:\Temp\ct-preview`（Lead 的预览渲染副本）可留作快速复渲染，也可删。
- system image 5.3 GB 体积大；若长期只做构建/预览、不做模拟器验证，可 `android sdk remove system-images/android-37.0/google_apis/x86_64` 释放（会丢失 `ct-api37` AVD 能力）。

---

## 13. E2E 冒烟脚本（模拟器回归验收）

> 完整说明见 [`docs/E2E_SMOKE.md`](./E2E_SMOKE.md)；脚本：`scripts/e2e_smoke.ps1`（纯 ASCII PowerShell，文本驱动点击）。

- **用途**：不写业务代码的黑盒验收。构建 → 安装 → 模拟器真机走完「身份页(开发预览)→工作台→全部→抽签 STEP1/2/3→导出图片→返回→工作台」→ 每步截图 → 回归检查（无 FATAL/ANR、导出 PNG 确实进相册）。
- **前置**：`TOOLCHAIN.md` §1 的工具链 + `ct-api37` 模拟器（未运行脚本会自动拉起，见 §8）。
- **命令**：

  ```powershell
  cd <项目根>
  pwsh -NoProfile -File .\scripts\e2e_smoke.ps1 -Build -Install   # 完整验收
  pwsh -NoProfile -File .\scripts\e2e_smoke.ps1                   # 快速回归（复用已装 APK）
  pwsh -NoProfile -File .\scripts\e2e_smoke.ps1 -Screens D:\e2e   # 自定义截图目录
  ```

- **参数**：`-Build`（构建 Debug APK）、`-Install`（强制重装）、`-Screens <dir>`（截图/日志目录，默认 `artifacts\e2e`）、`-KeepData`（保留 App 数据，默认 `pm clear` 保证从身份页开始）、`-BootTimeoutSec`。
- **产物**：`android-class-tools\artifacts\e2e\` —— `01_identity.png … 09_home_after_back.png`、`summary.json`、`run.log`、`build.log`、`logcat_run.txt`、`gallery_listing.txt`；失败时额外 `failure.png`/`failure_ui.xml`/`failure_logcat.txt`。
- **首次实测（2026-11-07）**：**12 项 PASS / 0 FAIL，总耗时 57.3 s**；logcat 5,409 行中 0 条 FATAL/ANR；相册新增 `抽签结果_20261107-1558_范围1-70_抽10个_不重复_按抽取顺序.png`（116,842 B）。
- **坑（已在脚本内消化）**：
  1. 按钮标签必须**精确匹配**——STEP2 说明文字里也含「开始抽签」，包含匹配会点到说明文字；
  2. 脚本内构建要加 `--no-daemon`，否则 Gradle daemon 继承 stdout 管道会让 `| Tee-Object` 调用在构建结束后挂住；
  3. 模拟器时区为 UTC，App/导出文件名时间戳比本机慢 8 h；
  4. `pm clear` 默认执行（清 Debug 会话），保留数据用 `-KeepData`。
