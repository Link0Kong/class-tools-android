# OPPO PUSH（Heytap MCS SDK V4.0.0）接入说明

> 日期：2026-11-14 · 版本：安卓 **v4.51.0 / vc69** · 需求：用户「尝试将通知接入 oppo 官方 sdk」。
>
> 目标：App 未运行 / 被系统清理时，服务端通过 OPPO PUSH 单点推送**通知栏消息**，点击回到 App 的通知页并定位到对应通知。
>
> **当前状态（如实）**：客户端接入、编译、单测与 release APK 已完成；**尚未配置真实 AppKey/AppSecret**（用户 OPPO 开放平台推送服务未开通），因此**真实注册 / 到达 / 点击链路未验证**。配置密钥并完成服务端建表后按本文 §6 真机验证。

---

## 1. 本机改动清单

### 依赖与构建

| 文件 | 改动 |
|---|---|
| `app/libs/com.heytap.msp_V4.0.0.aar` | OPPO 官方 SDK V4.0.0（本地 AAR；164,111 B；sha256 `8A542A78C0CEBD3417763B21E0F9EBAD75000B0964E53C0F231F86445A1B1C41`；来自 OPPO 官方 openfs 2026-11-10 发布）。**该文件随仓库交付**（无 maven 依赖）。 |
| `app/build.gradle.kts` | `implementation(files("libs/com.heytap.msp_V4.0.0.aar"))`（本地 AAR 依赖，自包含无 gson/okhttp 传递依赖）；读取 `app/oppo.properties` 生成 `BuildConfig.OPPO_PUSH_APP_KEY/OPPO_PUSH_APP_SECRET`（缺失/空 → 空串）；`testOptions.unitTests.isReturnDefaultValues = true`（JVM 单测允许 `android.util.Log` 等未 stub 方法返回默认值）；版本 v4.51.0/vc69。 |
| `app/proguard-rules.pro` | `-keep class com.heytap.msp.** / com.heytap.mcssdk.** / com.mcs.aidl.** { *; }` + `-dontwarn com.heytap.** / com.mcs.**`（Manifest 组件按类名拉起 + AIDL/反射，R8 不得裁剪）。 |
| `.gitignore` | 新增 `app/oppo.properties`（密钥本机文件，永不入库）与 `_oppo_sdk_tmp/`（官方 demo 临时解压目录）。 |
| `app/oppo.properties.example` | 入库模板（空值占位 + 获取方式说明）。 |

### Manifest（`app/src/main/AndroidManifest.xml`）

按官方快速接入指南**直连声明 SDK 自带回调服务**（不继承重写，通知栏消息由 SDK 处理）：

```xml
<service android:name="com.heytap.msp.push.service.DataMessageCallbackService"
    android:exported="true" android:permission="com.heytap.mcs.permission.SEND_PUSH_MESSAGE">
    <intent-filter>
        <action android:name="com.heytap.mcs.action.RECEIVE_MCS_MESSAGE" />
        <action android:name="com.heytap.msp.push.RECEIVE_MCS_MESSAGE" />
    </intent-filter>
</service>
<service android:name="com.heytap.msp.push.service.CompatibleDataMessageCallbackService"
    android:exported="true" android:permission="com.coloros.mcs.permission.SEND_MCS_MESSAGE">
    <intent-filter>
        <action android:name="com.coloros.mcs.action.RECEIVE_MCS_MESSAGE" />
    </intent-filter>
</service>
```

- AAR 自身 manifest 已合并 `DataMessageReceivingService`（action `com.heytap.mcs.action.LAUNCH_COMPONENT_VIA_MCS`）与 `sdkVersion` meta-data，无需重复声明；
- 已有 `INTERNET` / `ACCESS_NETWORK_STATE` / `POST_NOTIFICATIONS` 权限保留（Android 13+ 通知权限仍由「权限管理」页申请）。

### 代码（`app/src/main/kotlin/com/furina/classtools/`）

| 文件 | 说明 |
|---|---|
| `data/push/OppoPushConfig.kt` | 密钥配置（空=未配置）+ 状态机 `OppoPushState`/`OppoPushStatus` + 文案/RegistrationID 掩码/官方错误码中文提示 + 回调状态映射（纯函数）。 |
| `data/push/PushChannels.kt` | 通知渠道 `class_notice`（名称「班级通知」，`IMPORTANCE_HIGH`，幂等创建）。**服务端推送的 `channel_id` 必须与之一致**。 |
| `data/push/OppoPushSdk.kt` | SDK 最小接口隔离 `OppoPushSdk` + 真实实现 `HeytapOppoPushSdk`（`HeytapPushManager.init/isSupportPush/register/getRegisterID`）。单测用假实现，不依赖真机。 |
| `data/push/OppoPushStore.kt` | RegistrationID / 状态本机缓存（SharedPreferences `oppo_push`，不参与云端 userdata 同步）。 |
| `data/push/PushTokenUploader.kt` | token 上传 payload/请求构造 + `ApiClient` 实现 + `PushTokenUploadGate`（同一会话+regId+版本只传一次，失败释放重试）。 |
| `data/push/OppoPushManager.kt` | 编排：`onAppCreated`（渠道→init→isSupportPush→register）、回调→缓存→上传、`onSessionChanged`（登录/切号补传）、`retry`（设置页重试）。所有 SDK 调用 `runCatching`，异常不影响启动。 |
| `data/push/OppPushClick.kt` | 点击参数解析：intent extra `noticeId` 优先，其次 `actionParameters` JSON；校验字符白名单（≤128，`[A-Za-z0-9_@:.-]`）；目标 = 通知工具页 `nt`。 |
| `AppContainer.kt` | `oppoPushManager` 装配（配置/SDK/存储/上传器/设备标签/版本）；`updateActiveSession` 在登录/切号/token 恢复时调 `onSessionChanged()`。 |
| `ClassToolsApp.kt` | `onCreate` 调 `container.oppoPushManager.onAppCreated()`（未配置安全跳过）。 |
| `MainActivity.kt` | 冷启动 `onCreate` + `onNewIntent` 解析通知点击参数（`pendingNoticeId`）→ `ClassToolsRoot` → `MainNavHost` 进通知页并定位。 |
| `ui/ClassToolsRoot.kt`、`ui/navigation/MainNavHost.kt` | 传递 `initialNoticeId`；进入 `Routes.tool("nt")` 并带 `pendingFocusNoticeId`。 |
| `ui/screens/notices/NoticesScreen.kt` | 新增 `initialFocusNoticeId`/`onFocusConsumed`：数据就绪后按 `group id / rowId / 期次 key` 定位卡片并展开详情；首屏未命中且还有旧通知时额外加载一页再试，仍找不到则停留通知页（不崩溃）。 |
| `ui/screens/profile/ProfileScreen.kt` | 「通知与权限」新增一行「OPPO 推送」（副标题：未配置/当前设备不支持/注册中…/已注册 · ****末4位/注册失败（码）），点击 = 重新注册。 |

### 云端（服务端契约，由 Lead 维护，客户端只按契约上传）

`POST {REST_BASE_URL}push_tokens?on_conflict=provider,token`

| Header | 值 |
|---|---|
| `Authorization` | `Bearer {BuildConfig.PUBLISHABLE_KEY}`（ApiClient 自动） |
| `X-Class-Token` | 当前班级 token（未登录不请求） |
| `Content-Type` | `application/json` |
| `Prefer` | `resolution=merge-duplicates,return=minimal` |

```json
{"provider":"oppo","token":"<RegistrationID>","device":"<MANUFACTURER> <MODEL>","app_version":"<versionName>"}
```

- 同一 token 重复上传 = upsert 更新 device/app_version（幂等）；
- 未登录/无 RegistrationID 不上传；登录或切换账号后自动补传（`onSessionChanged`）；失败只写日志/状态，不阻塞 UI。

---

## 2. 如何填写密钥（本机，不入库）

1. 在 **OPPO 开放平台 → 推送服务 → 应用配置** 获取 AppKey / AppSecret（需先创建应用并开通推送服务，应用包名 `com.furina.classtools`）。
2. 复制模板并填写：

```powershell
cd <项目根>\app
Copy-Item oppo.properties.example oppo.properties
notepad oppo.properties      # 填 oppo.appKey= / oppo.appSecret=
```

3. 校验（不打印密钥值）：

```powershell
Test-Path app\oppo.properties
```

> 红线：`app/oppo.properties` 已在 `.gitignore`；不要把 AppKey/AppSecret 写入源码、聊天记录、日志或提交。
> OPPO 官方 SDK 要求在客户端 `register(appKey, appSecret)`，因此两个值会进入**本机构建产物**的 `BuildConfig`（属于 OPPO 设计使然；仓库只有空模板）。

---

## 3. 构建

```powershell
cd <项目根>
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:assembleRelease
```

- 产物：`app/build/outputs/apk/release/app-release.apk`（v4.51.0 / vc69，v2 签名）；
- 未配置 `oppo.properties` 也能正常构建：BuildConfig 为空串 → 客户端跳过 init/register/上传，设置页显示「未配置」。
- 验证 SDK 已打包（release，R8 之后）：

```powershell
# Manifest 里应有两个回调服务
& 'C:\AndroidToolchain\sdk\build-tools\37.0.0\aapt2.exe' dump xmltree app\build\outputs\apk\release\app-release.apk --file AndroidManifest.xml | Select-String 'heytap'
# dex 里应有 SDK 类
# （解压 APK 后在 classes*.dex 中检索字符串 com/heytap/msp 或 com.heytap.msp）
```

---

## 4. 运行时行为（按配置分态）

| 状态 | 触发条件 | 客户端行为 | 设置页「OPPO 推送」 |
|---|---|---|---|
| 未配置 | AppKey 或 AppSecret 为空 | **不 init、不 register、不上传**；仍创建通知渠道（无害，服务端推送到达时可直接使用） | 未配置 |
| 不支持 | 已配置，但 `isSupportPush()=false`（非 ColorOS/Heytap 通道设备、模拟器等） | `init` 已执行，不 register | 当前设备不支持 |
| 注册中 | 已发起 `register` | 等待 SDK 回调 | 注册中… |
| 已注册 | 回调 `onRegister(code=0, registerID)` | 缓存 regId → 后台上传 token（登录态） | 已注册 · ****末4位 |
| 注册失败 | 回调错误码 / `onError` / register 抛异常 | 状态失败；点击设置行可重试 | 注册失败（错误码） |

- token 上传仅在**已登录**（非 debug 预览会话）且有 RegistrationID 时执行；
- 登录 / 切换账号 / token 恢复 → 自动补传（同一账号+regId+版本只传一次，失败后可重试）；
- 通知点击：`clickActionType=4` + `actionParameters={"noticeId":"<gid>"}` → MainActivity 解析 → 进入「通知」工具页并展开该通知详情（若该通知不在已加载页，最多再加载一页；仍找不到则停留通知页）。

---

## 5. 服务端推送字段（与客户端约定）

| 服务端字段 | 值 | 说明 |
|---|---|---|
| `channel_id` | `class_notice` | 必须与 `PushChannels.CLASS_NOTICE_ID` 一致（渠道名「班级通知」由客户端创建） |
| `clickActionType` | `4` | 打开应用自定义 Activity |
| `clickActionActivity` | `com.furina.classtools.MainActivity` | 客户端主 Activity（`launchMode=singleTop`，冷启动/onNewIntent 都处理） |
| `actionParameters` | `{"noticeId":"<gid>"}` | 通知组 id（`g_xxx`）或期次 key（`gid@occ:YYYYMMDD`）；客户端也兼容 intent extra `noticeId` 直传 |

服务端 token 来源：`public.push_tokens`（`provider='oppo'`，RLS：本人可读写自己 sid 的行、管理员可读全量；表结构/迁移由 Lead 负责）。

---

## 6. OPPO 真机验证步骤（拿到密钥后）

前置：一台 **ColorOS 手机**（OPPO/一加/realme 国行，系统带「OPPO 推送」服务；非 ColorOS 设备 `isSupportPush=false` 属预期）；OPPO 开放平台已开通推送服务、AppKey/AppSecret 已填入 `app/oppo.properties`；服务端 `push_tokens` 表与发送脚本就绪。

1. **安装与注册**
   - 构建 release（或 debug）安装包并安装到 ColorOS 手机；
   - 打开 App → 「我的 → 通知与权限」，确认「OPPO 推送」显示 **注册中… → 已注册 · ****xxxx**；
   - 关闭 App（后台清理），确认系统「设置 → 通知 → 班级工具站」出现「班级通知」渠道。
2. **token 已上传**
   - 登录班级身份后，在服务端 `push_tokens` 表按 `provider='oppo'` 查看到本次 RegistrationID（末 4 位应与设置页掩码一致）；`device`/`app_version` 正确。
3. **到达**
   - 用服务端发送接口/OPPO 控制台给该 regId 发送单点通知（按 §5 字段，`channel_id=class_notice`）；
   - App 进程不在前台时，通知栏应出现「班级通知」渠道消息。
4. **点击跳转**
   - 点击通知：App 冷启动（或从后台拉起）→ 直接进入「通知」工具页并展开对应通知详情；
   - 再点一条通知（App 已在运行）→ `onNewIntent` 同样跳转；
   - 已登录 / 未登录两种：未登录时先到身份页，登录成功后自动进入通知页并定位。
5. **容错**
   - 断网/注册失败：设置页显示「注册失败（码）」+ 点击可重试；App 其余功能不受影响；
   - 未配置密钥构建：设置页「未配置」，无网络请求、无崩溃。

> 验证记录（真实结果）请追加到本文末尾；**在拿到密钥前不得声称已通过真机验证**。

---

## 7. 错误码排查（官方 `ErrorCode`，客户端设置页显示数字码）

| 码 | 含义 | 排查 |
|---|---|---|
| 0 | 成功 | — |
| -1 | MCS 服务不可用 | 手机是否 ColorOS/带 OPPO 推送服务；系统推送服务是否被禁用 |
| -2 | 未知错误 | 查看 logcat `OppoPush` 标签（不含密钥） |
| 11 | 应用无权限 | OPPO 控制台应用状态/推送服务是否开通 |
| 12 | HTTP 动作不允许 | 服务端/控制台调用方式 |
| 13 | 调用频率受限 | 降低注册/发送频率 |
| 14 | AppKey 无效 | `oppo.properties` 的 appKey 是否属于本应用包名 |
| 15 | 缺少 AppKey | `app/oppo.properties` 未填/未重新构建 |
| 16 | 签名无效 | AppSecret 与 AppKey 不匹配，或包名/签名与开放平台登记不一致 |
| 17/18/19 | 签名/时间戳缺失或无效 | 检查设备时间与时区（自动时间） |
| 40/41 | 缺少/非法参数 | 检查 register 参数与推送字段 |
| 40001 | 请求过于频繁 | 稍后重试 |

> 客户端日志只输出 `OppoPush` 标签 + 错误码/状态，**不打印 AppKey/AppSecret/RegistrationID 全文**。

---

## 8. 回滚

1. **仅停用推送**（保留代码）：清空 `app/oppo.properties`（或改名）→ 重新构建 → 客户端不再 init/register/上传，设置页显示「未配置」。
2. **完全回滚代码**：删除 `app/src/main/kotlin/.../data/push/`、Manifest 两个 service、`app/build.gradle.kts` 的 AAR 依赖与 BuildConfig 字段、proguard keep、`app/oppo.properties*`、ProfileScreen/MainActivity/MainNavHost/ClassToolsRoot/NoticesScreen/AppContainer/ClassToolsApp 的相关接线（可对照本次 diff）。
3. **服务端**：停止对 `provider='oppo'` 的发送即可（表可保留，无副作用）。
4. **版本**：回到 v4.50.0/vc68（`git checkout -- app/build.gradle.kts` 后按需重建）。

---

## 9. 已知限制 / 未验证项（如实）

- **未验证**：真实 AppKey 注册、服务端真实推送到达、点击跳转的真实链路（无密钥/未开推送服务）；ColorOS 真机行为。
- `actionParameters` 在部分 ROM 可能以 JSON 字符串原样放入 intent extra（客户端已同时兼容 `noticeId` 直传与 JSON 解析两种形态）；若实际投放方式不同，需要在 MainActivity 增加对应 extra 解析（纯逻辑改动 + 单测）。
- 非 ColorOS 设备（含多数模拟器）`isSupportPush=false` → 不注册；系统级保活/自启策略仍可能影响到达（国内 ROM 通用限制）。
- 通知点击定位依赖该通知在通知列表已加载页内（最多额外加载 1 页）；极旧通知可能只进入通知页不展开详情（页面有提示语义，不崩溃）。
- 当前未实现「推送开关」（用户要求的是接入 SDK；如需开关可后续加本机偏好并跳过 register）。

## 11. 2026-11-21 修复：缺少 MCS 接收权限 + 推送状态可见化

> 用户反馈「手机为 ColorOS（OnePlus PYB110 / Android 17），但收不到通知」；云端 `push_tokens` 仍为 0 台 OPPO。

**根因（已确认的缺陷）**：Manifest 只声明了 OPPO 回调 Service，**没有声明官方接入要求的两个接收权限**：

```xml
<!-- app/src/main/AndroidManifest.xml -->
<uses-permission android:name="com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE" />
<uses-permission android:name="com.heytap.mcs.permission.RECIEVE_MCS_MESSAGE" />
```

OPPO/一加/realme 的系统推送服务（MCS）通过这两个权限向应用投递注册回调与通知消息；缺失时
`HeytapPushManager.register` 拿不到 RegistrationID（云端自然没有 `provider=oppo` 行），
即使服务端 AppKey/MasterSecret 鉴权成功也无法到达。已补上（release APK 合并后可在
`aapt2 dump permissions` 看到两条）。

**同时新增的自查能力**（`data/push/PushStatusDiag.kt`）：
- 「我的 → 通知设置」新增「系统推送（应用关闭后）」卡片，直接显示 OPPO / vivo 的**上次注册状态与错误码**
  （未配置 / 本机不支持 / 注册中 / 已注册（…末 4 位）/ 注册失败（错误码 N））；
- App 启动并登录后自动向 `client_diag` 上报一条 `check=push_status` 诊断（只含状态与错误码，不含 token/学号），
  便于服务端远程排查，不必让用户截图或连 adb。

**验证顺序**：安装带修复的 APK → 打开 App 登录 → 看「通知设置」里的 OPPO 推送状态 → 若显示
`注册失败（错误码 N）`，按 N 对照 OPPO 开放平台（应用签名/包名/推送服务开通状态）处理。

### 11.1 真机反馈：注册失败（错误码 16）= 应用签名不一致

2026-11-21 晚，装有上述修复包的 ColorOS 真机（OnePlus PYB110 / Android 17）在
「我的 → 通知设置 → 系统推送」显示 **OPPO 推送：注册失败（错误码 16）**。

- 官方状态码表（`ICallBackResultService.onRegister` 等同表）：**16 = Invalid Signature / 签名校验不通过**
  （[OPPO 状态码定义](https://open.oppomobile.com/documentation/page/info?id=11224)）。
- 说明 SDK 已走到「签名校验」这一步（MCS 权限、设备支持、AppKey/AppSecret 均已通过），
  但 APK 签名与 OPPO 开放平台登记的「应用签名」不一致。
- 本仓库 release 证书指纹（与更新渠道 beta25 及本次修复包一致）：
  - **MD5：`fd87bb879e78fc7c8c39dc9bdf8b2ef5`**（OPPO 后台「应用签名」要填这个 32 位 MD5）
  - SHA-1：`aa6d41754e38b9ad9aaaf65a98f189e1b368946b`
  - SHA-256：`974854df1cbed0d2f4191a3eed0cf9465c8752d9e1895d7fd6c0bc748bec4a1a`
- 修复：OPPO 开放平台 → 应用信息 → **应用签名**（正式/调试两个都填，或至少「正式签名」）填上面的 MD5 →
  保存后约 5 分钟生效 → 手机结束 App 进程重开 → 状态应变为「已注册」。
- 注意：此前文档记录的「调试签名与 release 证书 SHA-1 前 32 位一致」是错误口径——
  OPPO 要的是**证书 MD5（32 位）**，不是 SHA-1 前缀。
