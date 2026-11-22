# 班级工具站 · Android 客户端

[![version](https://img.shields.io/badge/version-1.0.0--beta22-blue)](https://github.com/Link0Kong/class-tools-android/releases)
[![license](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF)](https://kotlinlang.org)

「班级工具站」的 Android 原生客户端：Kotlin + Jetpack Compose，与网页端、微信小程序、鸿蒙端、Windows 端共用同一套云端数据契约。

## 简介

- **真原生**：不是 WebView 套壳；抽签、分组、课表、通知、电费、水量等均为 Compose 页面 + Kotlin 业务层。
- **多端同源**：身份核验、`userdata` v6 同步、marks、通知、投票、电费/水量等数据结构与网页端、小程序端、鸿蒙端、Windows 端一致。
- **离线可用**：先读本机缓存再后台刷新；断网时可继续查看与操作，恢复后自动补发。
- **隐私优先**：身份核验走云端名单；App 内不完整展示学号，导出图片不含学号/手机号/宿舍号。

## 功能

- **身份核验**：学号 + 姓名与班级云端名单比对，换取约 30 天有效的班级访问令牌。
- **工作台**：今日课程、待办作业、最新通知、电费/水量卡片聚合。
- **抽签**：独立工具；范围、人数、排除序号、参与名单、可重复/不重复、剔除重抽、排序、导出分享；成功结果本机持久化。
- **分组**：独立工具，不依赖先抽签；成员来源「全班 / 最近抽签 / 手动选择」，按组数/每组人数、随机打乱、复制与导出分享。
- **课表**：周/日视图、周次与单双周、个人导入、教务快照、下一节课。
- **作业**：列表、截止提醒、完成勾选、跨端同步。
- **通知**：自动通知 + 人工通知、分类/状态机、已处理标记、附件预览、提醒设置与测试通知。
- **体测**：国家学生体质健康标准评分、历史记录、补填合并、导出分享。
- **名单 / 点名**：班级名单、座号选择、点名状态、随机点名。
- **电费**：当前电量、24h/7d/30d 趋势、异常扣减识别、可用天数估算、上次充值。
- **水量**：按宿舍抄表、人数与免费额度、水费与折合扣电。
- **投票**：单选/多选、截止时间、结果查看、分享（不泄露票数）。
- **演示（PPT）**：列表、下载、分片上传、原样视频播放、下载中心。
- **文件传输**：临时中转、上传/下载、过期清理提示。
- **校园地图**：校内地点与路线（本地资源，离线可用）。
- **统计**：访问统计（管理员）、个人数据概览。
- **诊断**：设备信息、接口连通性、脱敏日志、异常上报。

> 桌面小组件当前版本未实现，故不在此列出。

## 下载

- 测试版 APK 见 [GitHub Releases](https://github.com/Link0Kong/class-tools-android/releases)。
- 当前为 **1.0.0-beta** 测试版本，功能和稳定性可能变化；正式上架前请以 Release 说明为准。
- 安装前需允许「安装未知应用」；App 内更新会校验版本与 SHA-256。

## 开发与构建

### 前置条件

- JDK **17**（Gradle 构建用）
- Android SDK **37**（platforms;android-37.0、build-tools 37.0.0、platform-tools）
- Windows / macOS / Linux 均可；仓库自带 Gradle Wrapper，无需单独安装 Gradle

```powershell
$env:JAVA_HOME = '<JDK 17 路径>'
$env:ANDROID_HOME = '<Android SDK 路径>'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

### 常用命令

```powershell
.\gradlew.bat assembleDebug        # Debug APK: app\build\outputs\apk\debug\
.\gradlew.bat assembleRelease      # Release APK: app\build\outputs\apk\release\（需自备 keystore）
.\gradlew.bat testDebugUnitTest    # JVM 单元测试
```

- Release 签名：把 keystore 放在本机，按 [docs/RELEASE_BUILD.md](docs/RELEASE_BUILD.md) 配置 `keystore.properties`（已被 `.gitignore` 排除，绝不入库）。
- 依赖与 SDK 版本矩阵见 [docs/TOOLCHAIN.md](docs/TOOLCHAIN.md)。
- 无设备 UI 验收（Compose `@Preview` 渲染 PNG）：`.\scripts\render_previews.ps1`。
- 厂商推送/账号配置模板：`app/*.properties.example`；真实密钥只放本机或服务端，不入库。

## 项目结构

```text
app/                       应用模块
  libs/                    厂商 AAR（OPPO / 荣耀 / vivo）
  src/main/kotlin/com/furina/classtools/
    data/                  网络、身份、同步、名单、作业、通知、电费、水量、投票等数据层
    features/              纯 Kotlin 业务逻辑（抽签、分组、体测、导出、分享等）
    ui/                    Compose UI（theme / components / screens / navigation / preview）
  src/test/                单元测试与同步契约夹具
design/                    设计规格、组件清单与 design tokens
docs/                      架构、后端契约、构建、工具链、路线图
scripts/                   工具链安装、preview 渲染、E2E 脚本
gradle/                    版本目录与 Gradle Wrapper
```

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.4（协程、Serialization） |
| UI | Jetpack Compose + Material 3（自建 Expressive 组件层） |
| 架构 | 分层 + 单向数据流；`data` / `features` / `ui` 三层 |
| 网络 | OkHttp + 手写 PostgREST/RPC 客户端（不接 CloudBase SDK） |
| 本地存储 | DataStore Preferences、SharedPreferences、应用私有文件 |
| 后台任务 | WorkManager（提醒调度、离线补发、低频同步） |
| 图片/绘制 | Compose Canvas、自绘导出卡与分享卡 |
| 构建 | AGP 9.5、Gradle Wrapper、Version Catalog |
| 最低版本 | minSdk 26 / targetSdk 37 / compileSdk 37 |

## 相关仓库

| 端 | 仓库 |
| --- | --- |
| 网页 / 部署 | <https://github.com/Link0Kong/class-tools-site> |
| Android（本仓库） | <https://github.com/Link0Kong/class-tools-android> |
| 微信小程序 | <https://github.com/Link0Kong/class-tools-mp> |
| 鸿蒙 | <https://github.com/Link0Kong/class-tools-harmony> |
| Windows | <https://github.com/Link0Kong/class-tools-windows> |

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 分层、同步、网络与验证策略
- [docs/BACKEND_CONTRACT.md](docs/BACKEND_CONTRACT.md) — 后端与数据层契约
- [docs/api/ENDPOINTS.md](docs/api/ENDPOINTS.md) — 端点参考（PostgREST / RPC）
- [docs/api/USERDATA_V6.md](docs/api/USERDATA_V6.md) — `userdata` v6 同步规格
- [docs/RELEASE_BUILD.md](docs/RELEASE_BUILD.md) — 签名与发版
- [docs/TOOLCHAIN.md](docs/TOOLCHAIN.md) — 工具链与版本矩阵
- [docs/ROADMAP.md](docs/ROADMAP.md) — 里程碑与进度
- [docs/CHANGELOG.md](docs/CHANGELOG.md) — 版本记录
- [design/README.md](design/README.md) — 设计规格入口

## 隐私与数据

- **身份核验**：学号 + 姓名只用于与班级云端名单比对，换取班级访问令牌；不做公开注册。
- **最小展示**：App 内学号以脱敏形式展示（如 `20***01`），不展示手机号；导出图片不含学号、手机号、宿舍号。
- **本机优先**：身份令牌与缓存保存在应用私有目录；`allowBackup=false`，不进入系统云备份。
- **权限克制**：不申请定位、通讯录、短信、通话记录、相册全量、相机、麦克风权限。
- **诊断脱敏**：上报前移除 token、学号、姓名、宿舍号与本机路径。
- **测试身份**：自动化测试与联调只使用测试身份，不登录真实同学或管理员账号。
- 完整口径见 [docs/PRIVACY_COMPLIANCE.md](docs/PRIVACY_COMPLIANCE.md) 与 App 内隐私政策/用户协议。

## 免责声明

- 本项目是**班级自用的非官方工具**，不代表学校、学院或任何官方机构。
- OPPO / 荣耀 / vivo 推送与账号 SDK 的版权归各厂商所有，使用需遵守其开发者协议；详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 第三方云服务、教务系统、推送通道的可用性由其提供方决定；因第三方原因导致的功能受限，本项目不承担其责任。
- 本项目按「现状」提供，不附带任何明示或暗示担保。

## 许可证

本项目采用 [MIT License](LICENSE)。第三方组件与厂商 SDK 的授权见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
