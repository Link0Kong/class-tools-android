# 应用级 Baseline Profile（Android v4.22.0 起）

> 目标：把启动与首屏热点方法随 APK 一起下发，让 ART 在安装后的后台编译阶段提前 AOT 编译，
> 减少冷启动解释执行。库级 profile（AndroidX/Compose 自带）此前已合并；本文件记录**应用级** profile 的生成链路。

## 1. 工程结构（v4.22.0）

```
android-class-tools/
├── app/
│   └── src/release/generated/baselineProfiles/
│       ├── baseline-prof.txt   # 生成产物（约 1.86 MB 文本，18,166 条规则，其中 908 条 app 自身）
│       └── startup-prof.txt    # 启动专用 profile（同一批规则，includeInStartupProfile=true）
├── baseline-profile/           # com.android.test 模块（BaselineProfileGenerator）
│   └── src/main/java/com/furina/classtools/baselineprofile/BaselineProfileGenerator.kt
├── app/build.gradle.kts        # androidx.baselineprofile 插件 + baselineProfile { saveInSrc=true; automaticGenerationDuringBuild=false }
├── build.gradle.kts            # 插件声明 apply false
├── settings.gradle.kts         # include(":baseline-profile")
└── gradle/libs.versions.toml   # baselineprofile=1.5.0 / benchmarkMacro=1.5.0 / test deps
```

`app/src/release/generated/baselineProfiles/*.txt` 是**签入源码树的生成产物**（与官方模板一致，变更时按需重生成并提交）。

## 2. 重新生成

前置：连接/启动模拟器（API 33+，本项目用 ct-api37），debug 可安装。

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd <项目根>
.\gradlew.bat :app:generateBaselineProfile --console=plain
```

完成后自动写入 `app/src/release/generated/baselineProfiles/`；改动了启动路径/主题/首屏结构后建议重跑（约 3–5 分钟，含 nonMinifiedRelease 构建 + 模拟器采集）。

生成规则（`BaselineProfileGenerator.kt`）：
- `rule.collect(packageName = "com.furina.classtools", includeInStartupProfile = true) { pressHome(); startActivityAndWait(); wait(pkg 根节点); Thread.sleep(1000) }`
- 只覆盖冷启动+首屏；后续要覆盖列表滚动/投票详情等，可增加 `@Test` 采集片段（会增加 profile 大小）。

## 3. 构建产物与验证

- v4.22.0 Release：**2,987,256 B**（比 v4.21.0 的 2,856,126 B 增加约 131 KB，主要是二进制 profile）；APK 内 `assets/dexopt/baseline.prof` + `baseline.profm` + ProfileInstaller 均在。
- 单测：**1065/0**；快速 E2E：**43/0**（见报告）。
- ProfileInstaller 运行确认：logcat 出现 `ProfileInstaller: Installing profile for com.furina.classtools`。

### 冷启动测量（模拟器 ct-api37，force-stop 后 `am start -W` 各 5 次）

| 场景 | 各次 (ms) | 中位数 |
|---|---|---|
| v4.21.0（无应用级 profile，默认 verify 编译） | 716, 780, 800, 768, 784 | **780** |
| v4.22.0（应用级 profile，安装后默认 verify 编译） | 914, 854, 784, 743, 766 | **784** |
| v4.22.0 + 强制 `cmd package compile -m speed-profile -f`（模拟后台 AOT 完成） | 795, 709, 766, 750, 707 | **750** |
| v4.21.0 + 强制 `cmd package compile -m verify -f`（对照组） | 843, 801, 755, 860, 879 | **843** |

结论（如实）：
- **ProfileInstaller 安装后，系统后台 dexopt 通常仍是 `verify` 过滤**（模拟器不会立即后台 AOT），所以「刚安装完」的冷启动差异不明显。
- 强制按 profile 做 `speed-profile` 编译后，冷启动中位数 **750ms vs 843ms（约 -11%）**，证明 profile 在真机后台编译完成后有效；真机效果依赖系统 idle 编译时机，通常首日夜间完成。
- 测量受模拟器负载影响，绝对数值不代表真机。

## 4. 已知限制 / 注意事项

- `app/src/release/generated/` 为生成目录，勿手改；重生成会整体替换。
- profile 只覆盖启动首屏；更多路径需扩展 generator 的采集块。
- 构建耗时增加：`assembleRelease` 不触发采集，但首次构建会多编译 `nonMinifiedRelease` 变体（用于采集），约 +1–2 分钟；日常 `assembleRelease` 无需接入采集步骤。
- 若 CI 无设备，保留已签入的 profile 即可，不要设置 `automaticGenerationDuringBuild=true`。
- 版本升级 AGP/插件时，若 baselineprofile 插件不兼容会先报配置错误；回退方案：移除插件与 `:baseline-profile` 模块、删除 generated 目录，库级 profile 仍随 AAR 合并。
