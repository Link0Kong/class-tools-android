# 班级工具站 · Android 原生 App

> 目标：用 Android 17（API 37）的设计语言与 Compose-first 技术栈，把班级工具站重做成**真原生** Android App；数据与网页/小程序共用同一套 CloudBase 契约。

- 包名：`com.furina.classtools`（早期草稿的 `fun.furina.classtools` 因 `fun` 是 Kotlin 硬关键字无法编译，已全量改名）
- 版本：3.6.0（versionCode 12），Debug 后缀 `.debug`
- minSdk 26 / targetSdk 37 / compileSdk 37
- 语言/UI：Kotlin + Jetpack Compose + Material 3（Expressive 自建层）+ 自绘组件
- 状态：**v4.40.0**——v4 功能同步 + P0 + P1 主体 + P2/性能 + 系统实时活动（Live Updates/流体云，含结果通知与权限提示）（Baseline Profile 最新代码、底栏真实模糊 Haze、12 屏无障碍审计、移除动画适配、自适应、大图缩放、电费切档、体测删除撤销、房间搜索）；快速 E2E **43/0**、单测 **1065/0**，签名 APK 已交付桌面。详见 [REPORT_ROUND51](docs/REPORT_ROUND51_20261112.md)。

## 目录

```
app/                     Android 应用模块
  src/main/kotlin/com/furina/classtools/
    data/                数据层：network(OkHttp/PostgREST)、auth(class_verify)、member(名单)、ppt(演示/分片上传)、
                         sync(userdata v6 合并/防抖/离线补发)、marks、session、tool 目录
    features/            业务领域层（draw 抽签、group、fit、ppt 演示…纯 Kotlin，可单测）
    ui/                  Compose UI
      theme/             设计令牌（颜色/字体/形状/动效/主题/Tokens）
      components/        通用组件 + expressive 自建组件
      screens/           页面（identity/home/all/profile/tools/draw…ppt 列表/播放器）
      navigation/        自适应导航（底部 NavigationBar / NavigationRail）
      preview/           全部 @Preview（android preview render 无设备截图验收）
design/                  Android 17 设计规格（事实核验 + token + 45 组件对照 + 16 页面方案）
docs/                    后端契约、数据层实现、工具链、架构、路线图、验收
scripts/                 工具链安装 / preview 渲染 / 构建脚本
artifacts/               APK、预览 PNG 等构建产物（不提交）
```

## 构建

本机工具链（已安装）：

| 项 | 路径/版本 |
|---|---|
| JDK | `C:\AndroidToolchain\jdk17`（OpenJDK 17.0.2，Gradle 构建用）、`C:\AndroidToolchain\jdk`（21.0.2） |
| Android SDK | `C:\AndroidToolchain\sdk`（platforms/android-37.0、build-tools/37.0.0 + 36.0.0、platform-tools 37.0.2） |
| Gradle | `C:\AndroidToolchain\gradle\gradle-9.9.0`（项目内 `gradlew.bat` 已生成，distributionUrl 指向腾讯镜像） |

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat assembleDebug --console=plain     # Debug APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease --console=plain   # Release 签名 APK -> app\build\outputs\apk\release\app-release.apk
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

无设备 UI 验收（Compose @Preview → PNG，Android CLI 33+）：

```powershell
.\scripts\render_previews.ps1 -OutDir artifacts\previews
```

> Release 签名说明见 [docs/RELEASE_BUILD.md](docs/RELEASE_BUILD.md)（keystore 与密码只存本机，不入库；已产 1.75MB 签名 APK）。

> 注意：`android preview` 在 Windows 上会把项目根 `build/` 目录误判为 Bazel 的 `BUILD` 标记，
> 因此脚本在 `C:\Temp\ct-preview` 的干净副本里渲染（robocopy 源码 → 删 build 目录 → `preview render app --fqn ...`）。

## 文档

- `docs/ARCHITECTURE.md` — 分层、同步、网络、验证策略
- `docs/ROADMAP.md` — 里程碑与当前进度
- `docs/BACKEND_CONTRACT.md` + `docs/api/` — 后端契约（ENDPOINTS / USERDATA_V6 / KOTLIN_MODELS / NOTICES / EXPORT_CARD_V2 / DATA_LAYER_IMPL）
- `docs/TOOLCHAIN.md` — 工具链与版本矩阵（进行中）
- `docs/PPT_20261109.md` — 「演示」v1.1 分步播放器实现/契约对应/已知限制/构建与测试证据
- `docs/PPT_VIDEO_20261110.md` — 「演示」v1.2 原样播放视频模式/章节/倍速/续播/降级链/OSS 域名与下载限制/构建证据
- `docs/PPT_DOWNLOAD_20261110.md` — 「演示」v2.0 下载中心 + v2.3 视频/音频单独下载：兼容性处理/详情页下载/FileProvider 打开分享/旧 deck/构建证据
- `design/ANDROID17_DESIGN.md`、`design/tokens.json`、`design/COMPONENTS.md`、`design/SCREENS.md`

## 红线（继承自 `web/AGENTS.md`）

1. 不读取/不复制 `web/deploy/creds.env`、`web/tools/cert/` 等机密；APK 只内嵌公开 Publishable Key（role=anon，与网页/小程序一致），不内嵌任何服务端高权限 key。
2. 不修改 `web/`、`mp-class-tools/` 现有代码；两端继续独立可用。
3. 班级数据必须先过「学号+姓名」云端核验（token）才加载；不导出手机号/学号/宿舍号等隐私（电费页仅显示本人关注宿舍的楼栋房间+电量，不导出）。
4. App 不申请定位/通讯录/存储读取权限；分享/下载走系统 FileProvider。
