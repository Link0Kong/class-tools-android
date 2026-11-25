# vivo 推送接入说明（vpush 4.1.7.0_517）

> 2026-11-20 · 用户要求接入 vivo 推送服务。
> AppID/AppKey 由 vivo 设计要求编译进 APK（具体值见厂商后台配置）；**AppSecret 只在服务端**，不进客户端/仓库。

## 1. 交付内容

| 位置 | 内容 |
| --- | --- |
| `app/libs/vpush_clientSDK_v4.1.7.0_517.aar` | 官方 SDK（267,290 B；sha256 `7A759AB6A23BF38210EED73377098A5CE6419E3417ED4D9003B4B1D6CA08FB3A`）；内部 `libs/pushOpenClient_…jar` 由 AGP 合并 |
| `app/vivo.properties.example` | 客户端配置模板（入库）；真实 `app/vivo.properties` 已在 `.gitignore` |
| `app/build.gradle.kts` | `BuildConfig.VIVO_PUSH_APP_ID/API_KEY` + `manifestPlaceholders` |
| `AndroidManifest.xml` | `<meta-data app_id / api_key>`、`VivoPushReceiver`、MainActivity 的 `classtools://com.furina.classtools/notice` deeplink |
| `data/push/VivoPushConfig.kt` | 配置/状态机/文案/错误码（纯逻辑，单测覆盖） |
| `data/push/VivoPushSdk.kt` | SDK 最小接口隔离 + `PushClient` 实现（`initialize`/`turnOnPush`/`getRegId`/`isSupport`） |
| `data/push/VivoPushStore.kt` | regId/状态本机缓存 + `VivoPushBus`（Receiver → manager 桥） |
| `data/push/VivoPushReceiver.kt` | `OpenClientPushMessageReceiver`：`onReceiveRegId` 上传；点击兜底 |
| `data/push/VivoPushManager.kt` | 编排：登录后初始化 → 注册 → 缓存 → 上传 `push_tokens(provider='vivo')` |
| `data/push/PushTokenUploader.kt` | `PROVIDER_VIVO` + `of(provider=…)` |
| ECS/本机 `scripts/vivo_push.py` | 服务端发送器（auth + 单推 + 增量水位 + `--test/--notice/--dry-run`） |
| `scripts/push_tick_all.py` | 推送巡检统一调度（鸿蒙 + OPPO + vivo） |

## 2. 客户端流程

- **隐私**：不在 `Application.onCreate` 初始化。用户完成身份核验（已勾选隐私政策）后，
  `AppContainer.updateActiveSession` → `VivoPushManager.onSessionChanged()` 首次
  `PushClient.initialize(PushConfig.Builder().agreePrivacyStatement(true).build())`。
- `turnOnPush` 回调 0/1 → `getRegId()` → `onReceiveRegId` / 查询回调 → 缓存 → 上传：
  ```json
  {"provider":"vivo","token":"<regId>","device":"<厂商 机型>","app_version":"<versionName>"}
  ```
  PostgREST `push_tokens?on_conflict=provider,token`（服务端已放开 provider 约束）。
- beta76 把本机注册与账号绑定分开显示。只有服务端确认后才显示绑定成功。换号、退出和标识轮换会解除本机旧绑定；网络恢复或手动刷新可重试。
- 点击：服务端 `skipType=4` + `skipContent=intent://com.furina.classtools/notice#Intent;scheme=classtools;S.noticeId=<gid>;end`
  → 系统直接拉起 `MainActivity`（`singleTop`）→ 既有 `noticeIdFrom(intent)` 解析 → 进通知页定位。
  `onNotificationMessageClicked` 仅为兼容兜底（SDK 3.0+ 官方已改为系统直接拉起）。

## 3. 服务端

`scripts/vivo_push.py`（与 `oppo_push.py` 同水位模型）：

- 鉴权：`POST https://api-push.vivo.com.cn/message/auth`
  `sign = md5(str(appId)+appKey+timestamp+appSecret)` 小写，13 位毫秒；authToken 缓存 20h。
- 发送：`POST /message/send`（header `authToken`）：
  `notifyType=4`、`title ≤40`、`content ≤100`、`timeToLive=86400`、`skipType=4`、`skipContent=intent URI`、
  `category`（服务端配置，默认 `IM`）、`requestId`、`notifyId`、`pushMode=0`。
- 首轮记录现有通知。后续增量及 `--notice` 补推通过 `notice_push_targets` 查询每条通知的收件设备，受限通知使用通用预览。发送器按通知和设备记录厂商受理结果，中断或部分失败后只重试尚未受理的设备。
- 无效设备：`10302/10311` 计入日志（不自动删云端行）。
- **category 必须在 vivo 开放平台申请并通过**（即时消息 `IM`）；未通过会返回 `10096/10097`。
  运营消息仅 07:00–23:00 可发，`IM` 属系统消息不受限。
- 凭证文件（部署机，不入库）：路径与内容见运维文档；字段为 app_id / app_key / app_secret / category / click 配置，
  **具体值见厂商后台配置**。

## 4. 验证记录

**2026-11-20 接入时的验证**
- vivo 服务端凭证有效：`python vivo_push.py --auth` → `鉴权成功`（真实 authToken）。
- `--dry-run` 正常读取云端 `push_tokens?provider=eq.vivo`（当前 0 台）。
- Android 单测：配置/状态/掩码/错误码/注册编排/上传 provider=vivo 共 8 组；全量 **1794/0（2 skipped）**。
- Debug APK：Manifest 已合并 `app_id`/`api_key`/`VivoPushReceiver`/deeplink；dex 内含 `com.vivo.push.PushClient`。
- 推送巡检定时任务 `ClasstoolsPushWatch` 已改指向 `push_tick_all.py`，实跑 harmony/oppo/vivo 三个脚本 exit=0。

**2026-11-25 复核**

- 新受众接口已在线验证管理员空查询和匿名拒绝。vivo 与 OPPO 发送器的 29 项回归通过，涵盖受众隔离、通用预览、局部失败及重试。
- 现网鉴权使用新的厂商令牌验证通过。当日注册的 vivo 设备为 0 台。

**真机验证**
- **vivo 真机注册未联调**（本机无 vivo 设备）：`getRegId` 真实回调、通知到达、点击拉起。
- vivo 开放平台需把 **包名 `com.furina.classtools`** 与 AppID/AppKey 绑定；签名不参与校验。
- 消息分类 `category=IM` 需在平台申请；未通过前 `send` 会失败（代码已可配置）。
- 云端当前没有 vivo token；需在 vivo 设备安装当前测试版，登录后在推送诊断中确认账号绑定状态。

## 5. 回滚

1. 客户端：清空 `app/vivo.properties`（AppID/APIKey）→ manager 显示「未配置」，不初始化/注册/上传。
2. 服务端：删除/改名 `部署机密钥文件（不入库）` → `vivo_push.py` 安全跳过；
   把 `ClasstoolsPushWatch` 指回 `harmony-class-tools/scripts/push_tick.py` 可只停 vivo/OPPO 调度。
3. DB：`DELETE FROM public.push_tokens WHERE provider='vivo'`，再收紧 CHECK。


