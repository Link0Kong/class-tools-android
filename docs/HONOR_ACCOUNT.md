# 荣耀账号服务（Honor ID OAuth 2.0）接入说明

> 2026-11-20 · 用户要求「根据 honorid-opensdk-8.0.1.351.aar 荣耀账号服务 SDK OAuth 2.0 客户端 ID 接入荣耀账号服务」。
> 客户端只拿 SDK 的 `authorizationCode`；**Client Secret 只在 ECS 账号服务**，不进 APK/仓库。

## 1. 交付内容

| 位置 | 内容 |
| --- | --- |
| `app/libs/honorid-opensdk-8.0.1.351.aar` | 官方 SDK（194,793 B；sha256 `B1C2162B3476982F8F5CAC941482A9F516BA3E2B4B48B7A86FFBC0B367463EA9`），本地 AAR 依赖 |
| `app/honor.properties.example` | 客户端配置模板（入库）；真实 `app/honor.properties` 已在 `.gitignore` |
| `app/build.gradle.kts` | 读取 `honor.clientId` / `honor.scope` → `BuildConfig.HONOR_ACCOUNT_CLIENT_ID/SCOPE`；AAR 依赖 |
| `data/account/HonorAccountConfig.kt` | 配置、scope 归一（强制含 `openid`）、荣耀设备判定、错误文案 |
| `data/account/HonorAuthSdk.kt` | SDK 适配：`SignInOptionBuilder` + `getSignInIntent()` + `parseAuthResultFromIntent()` |
| `data/account/HonorAccountManager.kt` | 编排：登录（authCode → `/honor/login` → Session）、绑定/列表/解绑 |
| `data/account/OppoAccountApi.kt` | 泛化为 `pathPrefix`/`providerLabel`，OPPO `/oppo/*` 与荣耀 `/honor/*` 共用 |
| 身份页 / 我的页 / 弹层 | 「荣耀账号快捷登录」按钮 + 「我的 → 账号 → 荣耀账号」绑定管理（复用 OPPO 弹层，标题/文案参数化） |
| ECS `deploy/account_app.py` | 新增 `POST /honor/{login,bind,unbind}`、`GET /honor/bindings`、`/__health.providers.honor` |
| ECS `deploy/ecs_account.py` | 上传 `honor-account.json` + systemd `ACCOUNT_HONOR_FILE` |
| DB `deploy/cloudbase/2026-11-20_push_vivo_account_honor.sql` | `account_bindings.provider` 放开 `honor`（每学号每厂商最多 3 个） |

## 2. 客户端授权流程

1. 身份页点击「荣耀账号快捷登录」（仅 `HONOR_ACCOUNT_CLIENT_ID` 非空 + 荣耀设备展示；未勾选协议时提示，不静默禁用）。
2. `HonorIdAuthSdk.signInIntent()`：
   ```kotlin
   SignInOptionBuilder()
       .setClientId(clientId)
       .setScopeList(listOf(Scope("openid"), Scope("profile")))
       .setReqiureAuthCode(true)   // SDK 拼写如此；要求返回 authorizationCode
       .createParams()
   HonorIdSignInManager.getService(activity, options).signInIntent
   ```
   SDK 实际发出 `com.hihonor.id.ACTION.WEBAUTH`（package `com.hihonor.id`），要求设备已安装荣耀账号 App（MagicUI 4.0+）。
3. `rememberLauncherForActivityResult` 拉起 → 结果交 `parseAuthResultFromIntent(requestCode, data)` → `SignInAccountInfo.getAuthorizationCode()`。
4. `POST {ACCOUNT_BASE_URL}/honor/login {"code": "<authCode>"}` → 服务端返回班级 token/sid/name → 与学号+姓名登录完全一致的会话流程。
5. 绑定：同一授权流程 → `POST /honor/bind`（`X-Class-Token`）；列表/解绑同 OPPO 契约。

**设备判定**：`Build.MANUFACTURER == HONOR` 或 `HonorIdSignInManager.isHonorMachine()`。非荣耀设备隐藏入口（SDK 只在荣耀设备可用）。

## 3. ECS 服务端兑换（已部署）

`deploy/account_app.py`：

```
POST https://hnoauth-login.cloud.honor.com/oauth2/v3/token
  form: grant_type=authorization_code&code=...&client_id=...&client_secret=...&redirect_uri=honorid://redirect_url
→ {access_token, expires_in, id_token, ...}
POST https://account-drcn.platform.hihonorcloud.com/rest.php?nsp_svc=GOpen.User.getInfo
  form: access_token=...&getNickName=1
→ {openID, unionID, displayName, headPictureURL}
```

- 荣耀 token 响应**不含 openid**；openid 必须从 `GOpen.User.getInfo` 取（要求 scope 含 `profile`；若未授予则回退 `hihonor.oauth2.user.getTokenInfo`）。
- `redirect_uri` 默认 `honorid://redirect_url`；若返回 1101 且非 invalid code，会再尝试省略该参数（兼容未登记回调的应用）。
- 错误体映射：`{error: 1101, sub_error: 20152, error_description: "invalid code"}` → `honor_exchange_failed` + `1101/20152 invalid code`。
- 凭证文件（本机，不入库）：`G:\ClassTools\briefdesk\secrets\honor-account.json`
  ```json
  {"client_id":"<Client ID>","client_secret":"<Client Secret>","redirect_uri":"honorid://redirect_url"}
  ```
- 部署：`cd web && python deploy\ecs_account.py deploy`；健康检查 `http://121.196.47.2/account/__health` 应返回 `providers.honor=true`。

## 4. 已验证 / 未验证（如实）

**已验证**
- 荣耀 OAuth 凭证有效：用无效 code 调 token 端点返回 `1101/20152 invalid code`（说明 client_id/client_secret/redirect_uri 均被接受，而非 `invalid_client`）。
- ECS 已部署 v1.1，公网 `/account/__health` = `{ok:true,providers:{oppo:true,honor:true}}`。
- 公网 `POST /account/honor/login {"code":"invalid"}` 返回 `{ok:false,code:"honor_exchange_failed",message:"1101/20152 invalid code"}`。
- Android 单测覆盖配置/设备判定/scope/错误文案/管理器状态机；全量 `:app:testDebugUnitTest` **1794/0（2 skipped）**。
- Debug APK 内已打包 `com.hihonor.cloudservice.support.account.HonorIdSignInManager`。

**未验证（阻塞项）**
- **荣耀真机授权未联调**（本机无荣耀设备）：`getSignInIntent()` 是否成功拉起、`authorizationCode` 真实值、`GOpen.User.getInfo` 的真实返回字段。
- 需要在荣耀开发者平台把 **包名 `com.furina.classtools` + release 签名 SHA-256** 绑定到该 Client ID，否则真机授权可能被拒。
- 若平台给的是「服务器应用」的 Client ID/Secret 而非 Android 应用，SDK 授权会失败；届时需在控制台创建/关联 Android 应用并使用同一 Client ID 或对应 AppID。
- 绑定上限：`account_bindings` 触发器按 `provider` 分别计数；荣耀 3 个 + OPPO 3 个互不影响。

## 5. 回滚

1. 客户端：清空 `app/honor.properties` 的 `honor.clientId` → 荣耀入口隐藏；完整回滚删除 `Honor*` 文件与 UI 接线、移除 AAR 依赖/keep 规则。
2. 服务端：删除 `/opt/class-tools/account/honor-account.json`（或置空）→ `providers.honor=false`；`/honor/*` 返回 `not_configured`。
3. DB：`ALTER TABLE public.account_bindings DROP CONSTRAINT account_bindings_provider_check` 后按需重建为仅 `oppo`（先删 `provider='honor'` 行）。
