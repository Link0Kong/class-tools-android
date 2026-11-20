# 2026-11-20 推送 / 账号服务复核（OPPO + 荣耀 + vivo）

> 用户要求：接入荣耀账号服务、接入 vivo 推送，并「再次检查 OPPO 账号服务和推送服务」。

## 1. OPPO 推送（复核结果：鉴权正常；调度缺失已修；当前 0 台注册设备）

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| AppKey/MasterSecret | **有效** | `oppo_push.py --auth` → `OPPO 鉴权成功`（真实 authToken） |
| 服务端发送脚本 | 存在且可运行 | `briefdesk/scripts/oppo_push.py`；`--dry-run` 正常 |
| 定时调度 | **原先缺失，已修** | 唯一推送任务 `ClasstoolsPushWatch` 之前只跑鸿蒙 `push_tick.py`，OPPO/vivo 脚本从未被调用；现已改为 `push_tick_all.py`（harmony → oppo → vivo），实跑 exit=0 |
| 云端注册设备 | **0 台 OPPO** | 管理员查询 `push_tokens`：仅 1 条 `provider=harmony`（KEE-AL00） |
| 客户端注册链路 | 代码完整；未在真机验证 | `OppoPushManager.onAppCreated` → `HeytapPushManager.init/register`；需 ColorOS 手机 + 正式 AppKey/AppSecret + 包名/签名登记 |
| 服务端字段 | `channel_id=class_notice`、`clickActionType=4`、`actionParameters={"noticeId":…}` | 与客户端 `PushChannels`/`OppPushClick` 一致 |

**结论**：OPPO 推送「收不到」不是密钥问题，而是（a）没有调度器调用 `oppo_push.py`（已修）；（b）云端没有 OPPO 设备 token（当前没有 ColorOS 真机登录过 beta 包）。装 beta18 到 ColorOS 手机并登录后，可在 `push_tokens` 看到 `provider=oppo`，再用
`python oppo_push.py --test <regId>` 验证到达与点击。

## 2. OPPO 账号服务（复核结果：服务已部署，但账号服务凭证被 OPPO 判为 invalid_client）

| 检查项 | 结果 |
| --- | --- |
| ECS 服务 | `http://121.196.47.2/account/__health` → `{ok:true, configured:true, providers:{oppo:true,honor:true}, version:"1.1"}` |
| 服务端 OPPO 凭证 | 已加载（`oppo-account.json`：app_id=38092999、app_key=11ea…、app_secret=…） |
| 凭证有效性（2026-11-20 实测） | `POST https://api.uc.oppomobile.com/oauth2/token/token-code` 带真实 appKey/appSecret + 无效 code → `{"success":false,"error":{"code":"2020003","message":"invalid_client"}}` |
| 仅 appId+appSecret+code | `2020005 invalid_request`（说明接口确实要 appKey/appSecret，而非 appId） |
| 对照 | 同一套 AppKey/AppSecret 调 OPPO **推送**鉴权成功 → 推送凭证没问题 |

**根因判断**：`oppo-account.json` 里的 `app_secret` 是**推送 AppSecret**（`app/oppo.properties` 的 `oppo.appSecret`），
但 OPPO **账号服务**的 AppKey/AppSecret 是另一套（账号服务未开通或未在开放平台「账号服务」里生成）。
`invalid_client` 在 OPPO 文档中的含义是「未知的应用，检查 appid 和环境是否正确」，与授权码无关。

**2026-11-20 22:5x 补充（用户提供 appServerSecret + OMS SDK 后复测）**：
- 用户提供的 `appserversecret` 经比对等于现有 `oppo-push.json.master_secret`，即 **OPPO Push 的 MasterSecret**（推送服务端鉴权已用它并通过）；它不是账号服务凭证。
- 账号服务端兑换已扩展为 **两套域名 × 两套密钥**：
  `client-uc.heytapmobi.com`（当前 1.1.3 SDK 的 account-env 实际环境，XOR 解出）与旧 `api.uc.oppomobile.com`；
  密钥分别用 `app_secret` 与 `app_server_secret` 尝试。
  用无效 code 实测 **4 种组合全部返回 `2020003 invalid_client`**（而非 `invalid code`），说明账号服务端仍未认可该 AppID/AppKey。
- 用户提供的 RAR（`OMSBase.aar` + `OMSOAuth.aar`，2018 年旧 OMS 账号 SDK）已核对：
  它是**旧版客户端授权 SDK**（AIDL 绑定 `com.oppo.service.account`，不提供 server 兑换接口），
  与仓库现有官方 `account-oauth-web-sdk:1.1.3` 重复；不建议降级接入（会新增 READ_PHONE_STATE/旧 IPC，且不解决 invalid_client）。
- 结论不变：**OPPO 账号服务未对该应用开通，或账号服务的 AppID/AppKey/AppSecret 与推送不是同一套**。

**需要的修复材料（本机无法自行生成）**：
1. 登录 OPPO 开放平台 → **账号服务** → 创建/开通应用（不是推送服务页面），确认应用状态可用；
2. 取该服务的 **AppID / AppKey / AppSecret**，写入
   `G:\ClassTools\briefdesk\secrets\oppo-account.json`：
   ```json
   {"app_id":"<账号服务 AppID>","app_key":"<账号服务 AppKey>","app_secret":"<账号服务 AppSecret>",
    "app_server_secret":"<可选，账号服务若单独给 server secret 再填>"}
   ```
3. 在账号服务里登记包名 `com.furina.classtools` + release 签名 SHA-1/SHA-256；
4. 重新 `cd web && python deploy\ecs_account.py deploy`，再在 ColorOS 真机点「OPPO 账号快捷登录」。
   若仍返回 `invalid_client`，把该接口返回的 code/message 发给 OPPO 开放平台工单确认应用开通状态。

客户端已将 `invalid_client` 转成可诊断文案（`OppoAccountText.exchangeFailedMessage`），不会崩溃。

## 3. 荣耀账号（新增）

- ECS `/honor/*` 已部署；凭证用无效 code 实测返回 `1101/20152 invalid code`（说明 client_id/client_secret/redirect_uri 被接受）。
- Android 客户端已接 SDK/入口/绑定/登录；真机授权未验证（无荣耀设备），详见 `HONOR_ACCOUNT.md`。

## 4. vivo 推送（新增）

- 服务端凭证 `--auth` 成功；发送器 `vivo_push.py` 已接入统一巡检。
- Android 客户端已接 SDK/Manifest/注册/上传/deeplink；真机未验证（无 vivo 设备），详见 `VIVO_PUSH.md`。

## 5. 数据库

`2026-11-20_push_vivo_account_honor.sql` 已应用：
- `push_tokens_provider_check` → `('oppo','harmony','vivo')`
- `account_bindings_provider_check` → `('oppo','honor')`


