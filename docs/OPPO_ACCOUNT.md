# OPPO 账号绑定学号 + OPPO 快捷登录（Android 客户端）

> 用户 2026-11-17 要求：**一个学号最多绑定 3 个 OPPO 账号**；允许 **OPPO 账号快捷登录**。
> 服务端验证（OPPO token/openid 换取、绑定表、上限校验）由 Lead 在 ECS 新接口
> `http://121.196.47.2/account` 实现；**客户端只把 OPPO SDK 返回的 authCode 交给服务端，不自行验证 OPPO 身份**。

## 1. 改动清单

| 位置 | 内容 |
| --- | --- |
| `gradle/libs.versions.toml` | 新增 `oppoAccountOauthWeb = "1.1.3"` 与 `oppo-account-oauth-web`（Maven Central 官方依赖） |
| `app/build.gradle.kts` | 读取 `oppo.accountAppId` / `oppo.accountAppKey` / `oppo.accountScope` → `BuildConfig.OPPO_ACCOUNT_APP_ID/APP_KEY/SCOPE`；`BuildConfig.ACCOUNT_BASE_URL`（默认 `http://121.196.47.2/account`，gradle 属性 `ct.accountBaseUrl` 可覆盖）；版本 **1.0.0-beta2 / vc85** |
| `app/oppo.properties.example` | 新增账号服务配置项模板（**真实值放 `app/oppo.properties`，已在 .gitignore，不入库**） |
| `app/proguard-rules.pro` | keep `com.platform.account.**` / `com.platform.usercenter.**`（SDK 反射/IPC/Manifest 组件） |
| `AndroidManifest.xml` | **无需手写**：SDK AAR 自动合并 `OAuthWebActivity`、`AcOAuthAccountOperateReceiver` 与 OPPO 账号 App `<queries>`（见 §3） |
| `data/account/OppoAccountConfig.kt` | 配置/设备支持/state/authCode 校验/展示文案（纯逻辑） |
| `data/account/OppoAccountModels.kt` | 绑定记录、登录/绑定/列表/解绑结果、UI 状态 |
| `data/account/OppoAccountApi.kt` | ECS 账号接口客户端（URL/headers/body/解析/错误降级；`EcsOppoAccountApi`） |
| `data/account/OppoOAuthSdk.kt` | 官方 SDK 适配（`AcOAuthSdkManager`）+ 接口隔离 + 回调解析 |
| `data/account/OppoAccountManager.kt` | 编排：快捷登录 → Session；绑定/列表/解绑 + `bindingsState` |
| `AppContainer.kt` | `oppoAccountManager`（懒加载；BuildConfig 配置 + 设备探测；切号/退出清绑定缓存） |
| `ui/screens/identity/IdentityScreen.kt` | 身份页「OPPO 账号快捷登录」按钮（仅配置+OPPO 系设备+已勾选协议）；未绑定/失败简短弹窗 |
| `ui/ClassToolsRoot.kt` | 快捷登录成功 → 与学号+姓名完全一致的会话流程（updateActiveSession → 落盘 → 预热名单） |
| `ui/screens/profile/ProfileScreen.kt` | 「我的 → 账号」行「OPPO 账号 · 已绑定 N/3」 |
| `ui/screens/profile/OppoAccountSheet.kt` | 绑定管理底部弹层：列表（昵称+绑定日期+解绑）/绑定按钮/上限提示/错误重试 |
| `ui/navigation/MainNavHost.kt` | 进入「我的」/打开弹层刷新列表；绑定/解绑走 manager + 简短 Toast |
| `data/account/*Test.kt` | 新增 72 项纯逻辑单测（见 §7） |

## 2. 配置（本机，不入库）

复制 `app/oppo.properties.example` → `app/oppo.properties`，填写：

```properties
# 账号服务凭证（开放平台 → 账号服务 → 应用详情；与推送 AppKey/AppSecret 是两套）
oppo.accountAppId=<AppID>
oppo.accountAppKey=<AppKey>
# 可选：授权范围，默认 openid（静默授权最小权限）；要到昵称/unionid 需先在开放平台申请权限
# oppo.accountScope=openid
```

- `BuildConfig.ACCOUNT_BASE_URL` 默认 `http://121.196.47.2/account`；可在本机 `gradle.properties` 用
  `ct.accountBaseUrl=http://...` 覆盖（备案 HTTPS 后切域名用）。
- **未配置行为**（AppID 或 AppKey 为空）：身份页不显示「OPPO 账号快捷登录」，「我的 → 账号」不显示入口；
  其它功能不受影响；不会请求 OPPO SDK / ECS。
- **仅 ColorOS 手机显示**（用户 2026-11-17 晚反馈）：严格按 ColorOS/OPPO ROM 系统属性判断
  （`ro.build.version.oplusrom` / `ro.build.version.opporom` / `ro.build.version.oplusos` 任一非空），
  **不再仅凭厂商/品牌名**（OPPO 品牌但非 ColorOS、一加/realme 非 ColorOS 版本、模拟器一律隐藏入口）；
  非 ColorOS 完全不显示（无空占位）。
- 构建期通过 `buildConfigString()` 转义，密钥含特殊字符不会破坏 Gradle 脚本；任何日志/Toast/报告中**不打印** AppID/AppKey/authCode。

## 3. 官方 SDK 接入事实（1.1.3 AAR 核对）

- 依赖：`com.heytap.accountsdk:account-oauth-web-sdk:1.1.3`（Maven Central）；
  传递依赖 `account-oauth-sdk:1.0.6` / `account-env:1.0.13` / `account-service-ipc:1.1.7` +
  `androidx.appcompat:1.3.0`(runtime) / `androidx.databinding:viewbinding:7.1.2` / `kotlin-stdlib-jdk8` 由 Gradle 解析。
  本仓库 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 已含 `mavenCentral()`
  （官方要求的 root `build.gradle` repositories 配置已满足；`FAIL_ON_PROJECT_REPOS` 下不要往模块里加仓库）。
- **Manifest 不需要宿主配置 APPID meta-data**：官方文档「在 application 节点下配置 APPID」在 1.1.3 的实现中
  对应的是 `AcOauthRequest.Builder().setAppId(...)/setAppKey(...)` 运行时参数（字节码核实：Builder 对
  appId/appKey/state/scope 四项均做非空校验）；AAR 只读取 **OPPO 账号 App** 的 metaData（`AcOauthAppUtil`）。
  因此本项目通过 BuildConfig → `AcOauthRequest` 传参，**没有**在 Manifest 里落任何真实 APPID。
- AAR 自动合并的组件（无需手写）：
  - `com.platform.account.oauth.web.ui.OAuthWebActivity`（H5 授权兜底，`exported=false`）；
  - `com.platform.usercenter.oauth.asm.AcOAuthAccountOperateReceiver`（`com.usercenter.action.receiver.SUPPORT_LOGIN`）；
  - `<queries>`：`com.oplus.account` / `com.heytap.usercenter` / `com.heytap.vip` / `com.oppo.usercenter` / `com.oneplus.account` / `com.oplus.vip`。
- 明文网络：`res/xml/network_security_config.xml` 已放行 `121.196.47.2`（此前校园地图 H5 同时使用），无需改动。

## 4. 客户端流程

### 4.1 OPPO 快捷登录（身份页）
1. **入口独立于学号+姓名表单**（位于身份卡标题下方、表单上方；用户 2026-11-17 晚反馈：未填学号/姓名也应能用）：
   按钮只依赖「协议勾选 + 未在授权中」——已勾选协议时可直接点击，与学号/姓名/表单校验无关；
   未勾选协议时点击在按钮下方显示「请先勾选同意《用户协议》《隐私政策》」，不静默禁用；
   授权中按钮转圈并禁用防重复拉起。
2. `OppoAccountManager.login(activity)`：
   - 生成 32 位十六进制 `state`（SecureRandom）→ `AcOauthRequest`（appId/appKey/scope/state）
     → `AcOAuthSdkManager.getInstance().auth(activity, request, callback)`；
3. SDK 回调 `AcOauthApiResponse<AcOauthResult>` → 取 `data.authCode`（空/含空白/超长视为无效）；
4. `POST {ACCOUNT_BASE_URL}/oppo/login` body `{"code": authCode}`（不带 X-Class-Token）；
5. 成功：服务端返回 `{ok, token, sid, name[, nickname]}` → 构造既有 `Session`（sid/name/token，issuedAt=now，
   expiresAt=now+30 天）→ 与学号+姓名核验完全一致的流程：`updateActiveSession` → `SessionStore.save` → 预热名单；
6. `ok:false, code:"unbound"` → 简短弹窗「该 OPPO 账号未绑定学号，请先用学号+姓名登录，再到「我的 → 账号」绑定」；
7. 授权/SDK/服务端失败 → 简短弹窗，**保留可诊断错误码**（见 §6），可重试；不崩溃。

### 4.2 绑定 / 解绑（「我的 → 账号」）
- 行标题「OPPO 账号」，副标题 `已绑定 N/3`（读取中显示「读取中…」，失败「读取失败」；服务端 max 以其返回为准）。
- 点开底部弹层：
  - 列表：`GET /oppo/bindings` → 每条显示 **OPPO 昵称 + 绑定日期（yyyy-MM-dd）** + 「解绑」；
  - 「绑定 OPPO 账号」：`OppoAccountManager.bind` → OPPO 授权 → `POST /oppo/bind`（带 `X-Class-Token`）→ 刷新列表；
  - 解绑前二次确认 → `POST /oppo/unbind` body `{"id": <bindingId>}` → 刷新列表；
  - 已达上限（count ≥ max）时绑定按钮禁用并显示「已达上限 3/3」；
  - 错误（limit / 已绑其他学号 / 网络）→ 简短 Toast，不崩溃。
- 进入「我的」页或打开弹层时自动刷新一次（轻量请求；失败静默，可在弹层内「重试」）。
- 退出登录 / 切换账号：清空内存绑定列表（`OppoAccountManager.clearBindings`），不落盘任何绑定/OPPO 标识。

## 5. ECS 服务端契约（客户端消费方，Lead 实现）

Base：`ACCOUNT_BASE_URL`（默认 `http://121.196.47.2/account`），JSON；身份接口带 `X-Class-Token`。

| 接口 | 请求 | 成功 | 失败 |
| --- | --- | --- | --- |
| `POST /oppo/login` | `{"code":"<authCode>"}` | `{"ok":true,"token":"...","sid":"...","name":"...","nickname":"..."}` | `{"ok":false,"code":"unbound","message":"..."}` / 其它 `{ok:false,code,message}` |
| `POST /oppo/bind` | header token；`{"code":"<authCode>"}` | `{"ok":true,"binding":{"id":1,"nickname":"...","createdAt":"..."},"count":1}` | `code:"limit"`（最多 3 个）/ `code:"openid_bound_else"`（已绑其他学号）/ 其它 |
| `GET /oppo/bindings` | header token | `{"ok":true,"max":3,"count":N,"items":[{"id","nickname","createdAt"}]}` | `{ok:false,code,message}` |
| `POST /oppo/unbind` | header token；`{"id":1}` | `{"ok":true,"count":N}` | `{ok:false,code,message}` |
| `GET /__health` | — | `{"ok":true,"configured":bool,"version":"1.0"}` | 网络失败安全降级 |

客户端容错：非 2xx / 401/403 / 坏 JSON / 超时（15s）/ 网络异常 → 对应简短中文提示；`ok` 缺失但带 token 视为成功（兼容灰度）。

## 6. 运行时错误码 / 提示

| code | 含义 | 客户端提示（简短，可诊断） |
| --- | --- | --- |
| `unbound` | OPPO 账号未绑定学号 | 先用学号+姓名登录，再到「我的 → 账号」绑定 |
| `limit` | 一个学号已绑定 3 个 | 一个学号最多绑定 3 个 OPPO 账号 |
| `openid_bound_else` | 该 OPPO 账号已绑定其他学号 | 该 OPPO 账号已绑定其他学号 |
| `oppo_exchange_failed` | 服务端用 authCode 换 openid 失败（常见 message=`invalid_client`：账号服务未开通 / AppID·AppKey·包名·签名不匹配） | OPPO 账号服务未开通或凭证不匹配（`<message>`），请联系管理员 |
| `unauthorized` / HTTP 401/403 | 班级 token 失效 | 登录已失效，请重新验证 |
| `network` / `timeout` | 网络异常 / 超时 | 网络不可用 / 网络超时，请稍后重试 |
| `parse` | 响应结构异常 | 账号服务返回异常，请稍后重试 |
| OAuth `-2015` | 系统未登录 OPPO 账号（仅 openid 静默授权失败） | 请先在系统设置登录 OPPO 账号（code=-2015） |
| OAuth `-2011` | state 校验失败 | 授权状态校验失败，请重试（code=-2011） |
| OAuth `-2012/-2013/-2014` | H5 授权数据/操作/无响应失败 | OPPO 授权未完成，请重试（code=-201x） |
| OAuth `1117001` | 环境不安全（包名/签名与 AppID 不匹配） | OPPO 账号授权环境校验失败（code=1117001） |
| OAuth `-210/-2002/-2003/-2009` | 跳转/参数/App 信息/OAuth URL 失败 | OPPO 账号服务不可用，请重试（code=...） |
| OAuth 其它未知码 | — | OPPO 授权失败（code=xxx） |

> 上线前需在 OPPO 开放平台把 **包名 `com.furina.classtools` + release 签名（SHA-1）** 与账号服务 AppID 绑定，
> 否则会报 `1117001`/`2020003`。

## 7. 测试

新增纯逻辑单测 **78 项**（`app/src/test/kotlin/com/furina/classtools/data/account/`，全部假 SDK/假 ECS，不发真实请求）：

- `OppoAccountConfigTest`（24）：配置读取/空值判定/baseUrl 归一；**严格 ColorOS 设备支持**（有 ROM 属性才 true，OPPO 品牌但无 ColorOS 属性=false）；快捷登录按钮纯规则（未填学号/姓名也可点、未勾选协议提示）与 UI 静态断言（入口在学号表单之前、不依赖表单状态）；
  state 随机性与校验；authCode 规范化（空白/控制字符/长度边界）；`AcOauthRequest` 参数构造与必填校验；
  SDK 回调解析（成功/空 authCode/错误码文案）；绑定文案与上限 clamp；createdAt 多格式解析。
- `OppoAccountApiTest`（32）：登录/绑定/列表/解绑/健康检查的 **方法+URL+headers+body** 断言；
  成功/未绑定/limit/已绑其他学号/非 2xx/401/坏 JSON/网络异常/**15s 超时**降级；无 token 时不发请求；请求不含 CloudBase Authorization。
- `OppoAccountManagerTest`（22）：未配置/非 ColorOS 设备安全跳过（不拉 SDK/不发请求）；快捷登录成功（Session 字段/签发/过期/state/scope）、
  未绑定/服务端失败/空 token；绑定成功刷新列表、limit/已绑其他学号/授权失败/空 token；列表 Loading→Ready→Error；
  解绑成功刷新/失败不刷新；切号清缓存。

全量 `:app:testDebugUnitTest` 见构建报告（本机已全绿）；不涉及真机 OPPO 授权（凭证/真机联调待后续，见 §10）。

## 8. 构建与 APK

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:assembleRelease
```
产物：`app/build/outputs/apk/release/app-release.apk`（版本/大小/sha256 见本次交付报告与 `docs/CHANGELOG.md`）。

## 9. 回滚

1. **客户端**：隐藏入口只需清空/删除 `app/oppo.properties` 中 `oppo.accountAppId`/`oppo.accountAppKey` 后重构建
   （未配置 = 入口隐藏，其余功能不变）；完整回退 = `git checkout -- app/build.gradle.kts gradle/libs.versions.toml`
   并删除 `data/account/`、`ui/screens/profile/OppoAccountSheet.kt`、Identity/Profile/MainNavHost/ClassToolsRoot/AppContainer 的本轮改动和测试；
2. **服务端**：ECS `/account` 接口下线后，客户端登录/绑定返回网络/404 错误提示（只影响 OPPO 入口，不影响学号+姓名登录）；
3. 版本回退到上一版 `1.0.0-beta2 / vc85` 构建即可（vc86 不会被旧版本覆盖）。

## 10. 未验证项（如实）

- **真实 OPPO 账号 OAuth 联调仍未完成**：本机 `app/oppo.properties` 已填账号服务 AppID/AppKey（不入库、不打印），
  但服务端用 authCode 换 openid 仍返回 `oppo_exchange_failed`（常见 `invalid_client`：账号服务未开通或凭证/包名/签名不匹配），
  **当前真实授权链路被服务端凭证问题阻塞**；客户端已把该错误以可诊断文案展示（见 §6）。ColorOS 真机授权页/静默授权待服务端修复后复测；
  2026-11-20 按官方《OPPO 账号OAuth授权SDK服务端API》(id=10155) 核对：国内生产固定
  `https://api.uc.oppomobile.com/oauth2/token/token-code`，只接受 `appKey/appSecret/code`；
  用户提供的 `appServerSecret` 实为 **Push MasterSecret**（推送服务端鉴权已通过），不是账号服务参数。
  用给定 appKey/appSecret + 无效 code 实测返回 `2020003 invalid_client` → **应用在账号服务侧未激活/未申请 openid 权限**，
  或 appSecret 不属于账号服务应用；需按文档完成「产品→账号服务→申请接入→激活账号→申请 openid/公开信息/unionid」，
  详见 `PUSH_ACCOUNT_RECHECK_20261120.md`；
- **ECS `/account` 接口由 Lead 实现**：本仓库只按契约消费；真实 `unbound/limit/openid_bound_else` 行为与响应字段以实际服务端为准
  （客户端解析已按契约 + 容错实现）；
- **授权 scope 需在开放平台申请**：默认 `openid`（静默授权，服务端用 authCode 换 openid）；若要展示 OPPO 昵称，需账号服务开通
  对应权限并在 `oppo.accountScope` 增加 scope（服务端返回 nickname 后客户端自动显示）；`profile`/`unionid` 的实际取值格式未经真机验证；
- 非 ColorOS 设备（含 OPPO 品牌但无 ColorOS ROM 属性、一加/realme 非 ColorOS 版本、模拟器）按用户要求**不展示**
  快捷登录/绑定入口；H5 兜底授权理论可用但未启用（严格 ColorOS 口径由 ColorOS ROM 属性判断）。
- 绑定列表的 `createdAt` 格式以服务端实际返回为准（客户端兼容 ISO 偏移/Z/空格/纯日期 4 种）。
