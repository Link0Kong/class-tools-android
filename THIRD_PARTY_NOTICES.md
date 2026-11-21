# 第三方组件与许可声明

本仓库包含或依赖以下第三方组件。**版权归各厂商/作者所有**；使用、分发或再发布时，需同时遵守各自的开发者协议与开源许可证。

> 本文件用于满足公开仓库的告知义务，不构成法律意见；具体条款以各组件的官方许可证/开发者协议原文为准。
> 仓库不包含任何厂商密钥；`app/*.properties.example` 仅为配置模板。

## 1. 随仓库分发的厂商 AAR（`app/libs/`）

| 文件 | 组件 | 提供方 | 用途 |
| --- | --- | --- | --- |
| `com.heytap.msp_V4.0.0.aar` | OPPO PUSH SDK V4.0.0 | OPPO / 广东欧加通信 | OPPO 设备通知推送 |
| `honorid-opensdk-8.0.1.351.aar` | Honor ID Open SDK 8.0.1.351 | 荣耀（HONOR） | 荣耀账号快捷登录与绑定 |
| `vpush_clientSDK_v4.1.7.0_517.aar` | vivo Push Client SDK 4.1.7.0_517 | vivo | vivo 设备通知推送 |

上述 SDK 的版权、商标及相关权利归各厂商所有。本项目仅按官方 SDK 的接入方式集成，
**使用需遵守各厂商开放平台的开发者协议、推送服务条款与隐私政策**；AppSecret / MasterSecret 等凭证只在服务端或本机配置，不入库。

## 2. 主要开源依赖

| 组件 | 用途 | 许可证（上游） |
| --- | --- | --- |
| AndroidX Core / Activity / Lifecycle / Navigation / DataStore / Window / WorkManager | Android 基础能力、页面导航、本地存储、后台任务 | Apache-2.0 |
| Jetpack Compose（UI / Foundation / Animation / Material 3 / Tooling） | 声明式 UI 与设计系统 | Apache-2.0 |
| Kotlin / Kotlin Coroutines / kotlinx.serialization | 语言、协程、JSON 序列化 | Apache-2.0 |
| OkHttp | HTTP 客户端 | Apache-2.0 |
| Haze（`dev.chrisbanes.haze`） | 毛玻璃/模糊效果 | Apache-2.0 |
| OPPO Account OAuth Web SDK（`com.heytap.accountsdk:account-oauth-web-sdk`） | OPPO 账号 OAuth 授权 | 厂商条款 |
| SharedView Kit（`gsai.sdk:sharedview-kit`） | 一镜到底/共享元素转场 | 厂商条款 |
| JUnit 4、AndroidX Test、Espresso、UI Automator、Benchmark Macro | 单元测试与仪器测试 | 各自开源许可证（Eclipse Public License / Apache-2.0 等） |

依赖版本以 [gradle/libs.versions.toml](gradle/libs.versions.toml) 为准；完整许可证文本可从各组件官方仓库或 Maven 构件的 `LICENSE`/`NOTICE` 获取。

## 3. 其他说明

- 本项目代码采用 [MIT License](LICENSE)；MIT 许可不覆盖上述第三方组件。
- OPPO、荣耀、vivo 等名称与标识是其各自权利人的商标，仅用于说明兼容性。
- 若权利人对本仓库的集成方式有异议，请通过 [SECURITY.md](SECURITY.md) 中的联系方式提出，我们会及时处理。
