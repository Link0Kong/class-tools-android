# 班级工具站 · 后端与数据层实现契约（Android 原生 App 版）

> **文档定位**：把现有「班级工具站」网页端（`web/tools/index.html`，646,956 B / 8,897 行）与微信小程序端（`mp-class-tools/`，`utils/cloud.js` 1,721 行）的**数据层 + 同步层 + 导出层**逆向成 Android 原生 App 可直接照做的实现契约。
>
> **证据基准（2026-11-07 快照）**
> - 网页端：`C:\Users\Ko_Li\.openclaw\workspace\web\tools\index.html`（下文简称 **WEB**，行号以当前文件为准）
> - 小程序端：`C:\Users\Ko_Li\.openclaw\workspace\mp-class-tools\`（下文简称 **MP**；`utils/cloud.js` 简称 **CLOUD**）
> - 契约文档：`web/CLOUDBASE.md`（**CB**）、`web/HANDOFF.md`（**HANDOFF**）、`web/tools/tests/_water_contract.md`（**WATER-C**）、`web/tools/tests/export_card_v2_spec.md`（**CARD**）、`web/tools/tests/dns_pause_tt_health_spec.md`（**DNSSPEC**）
> - 同步 v6 契约原文（归档）：`archive/_sync_trash_20261105/_sync_task/CONTRACT.md`（**SYNC-C**）
> - 云端 DDL 迁移：`web/deploy/cloudbase/*.sql`
>
> **标记法**：`[已核实]` = 本文件直接从代码/DDL/实测文档读到；`[推断]` = 代码行为推导、未见显式文档；`[待确认]` = 现有代码中找不到权威依据，**不要臆造**，按节末清单找 Lead/服务端确认。
>
> **机密红线**：本文只写字段名/流程，**不复制任何密钥、token、私钥内容**。CloudBase 的 Publishable Key（JWT，`role=anon`，设计上公开）在 `WEB:1844-1845`（`CB`/`CBK`）与 `MP/utils/cloud-config.js:23,26` 中内联；Android 端从部署方索取同一公开 key，禁止把任何服务端高权限 key 打进 APK。

---

## 0. 给 Android 的结论摘要（先读这段）

1. **后端只有一种形态**：腾讯云 CloudBase（envId `tool-d4gosudok46e0e081`，地域 `ap-shanghai`）的 **PostgreSQL + PostgREST 风格 REST 网关**，单根地址 `https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/` `[已核实 CB:107-126, WEB:1844, MP/cloud-config.js:23]`。**没有自建业务 API、没有 GraphQL、没有云函数承担业务读写**（云函数只做定时同步/健康巡检，与 App 无关）`[已核实 CB:120, CB:673-690]`。
2. **鉴权两层**：① 每个请求带 `Authorization: Bearer <PUBLISHABLE_KEY>`（公开、可进客户端）；② 班级身份用 `POST rpc/class_verify` 换 **30 天有效期 token**，之后所有数据请求带 `X-Class-Token: <token>`（小程序）或 `X-Device-Id: <token>`（网页）——服务端 `auth_hdr()` **两者都认**。Android 建议统一用 `X-Class-Token`。`[已核实 CB:16-22, CB:26-52; WEB:3165; MP/cloud.js:127-137]`
3. **个人数据全部是「按学号 sid 的单行/单键」**：`marks.id = "<sid>|<kind>|<ref>"`；`userdata.id = sid`；`fitrecs.acc = sid`；`visits.sid = sid`。RLS 只允许本人读写 `[已核实 CB:62-76, SYNC-C:42-60]`。
4. **同步核心是 `userdata` 单行 blob v6**：11 个 section、**14 个顶层键**（`v,at,homeTools,fit,draw,group,roster,hide,tt,prefs,nick,power,water,homeToolsAt`），逐 section LWW（`remote.at[k] >= local.at[k]` 时云端赢）、推送防抖 1200 ms、拉取 TTL 60 s、离线待补发、可关闭云同步 `[已核实 WATER-C:54, SYNC-C:62-149, MP/utils/privacy.js:15]`。**注意**：任务描述里的「13 键」是加水前的旧口径，2026-11-06 增 `water` 后实际 14 键（证据 WATER-C:54）。
5. **通知是「行 + JSON body」结构，不是 HTML 站点抓取内容**：自动通知 `notices.id = auto_<gid>`、`body = [[CLASS_NOTICE_V1]] + <JSON>`；网页用 iframe 包了一份 HTML 模板渲染（**只是网页实现手段**），小程序已证明**可以用结构化 JSON 完全原生渲染**。结论：**Android 原生渲染可行，WebView 仅作附件预览兜底** `[已核实 WEB:2470-2506, WEB:2619-2650; MP/pages/nt/nt.js:157-333; 结论为推断-高置信]`。
6. **Android 端真正需要实现的核心端点约 15 个**（不含管理员专用与查询变体；详见 §3 与 `api/ENDPOINTS.md`）：`rpc/class_verify`、`rpc/class_members`、`rpc/class_set_role`（管理员）、`homework`(GET/POST/PATCH)、`notices`(GET lite/full、管理员 PATCH)、`marks`(GET/upsert)、`userdata`(GET/upsert)、`fitrecs`(GET/upsert/PATCH arch)、`visits`(POST/管理员 GET)、`feedback`(POST/GET/PATCH)、`power`(GET)、`power_history`(GET)、`site_assets`(GET)、`service_health`(GET 管理员)、`tt.json`(静态 GET 旧兜底)。
7. **所有表 anon 角色基本「无 DELETE」**：删除一律软删（`homework/notices.archived`、`fitrecs.arch`、`marks.val=false`、`notices.hidden`、`userdata` 覆盖空 blob）。仅 `power/power_history` 对管理员 token 放开 DELETE `[已核实 WEB:1864-1872, MP/cloud.js:190-208; CB:152-155, CB:224-234, DDL:2026-11-05_power.sql:54-72]`。
8. **风险提示**：CloudBase 环境 **2026-12-10 到期**（需续费/迁移），且 `class_verify`/`private.class_sessions` 的服务端实现**不在仓库内**（无法从代码核实 token 生成算法、TTL 常量、限流），见 §11 `[已核实 CB:690, _compliance/audit_current_state.md:329]`。

---

## 1. 服务端拓扑与网络基座

### 1.1 拓扑 `[已核实 CB §0/§2，HANDOFF §36/§39]`

| 项 | 值 |
|---|---|
| 云厂商 | 腾讯云 CloudBase（云开发）个人版 |
| envId | `tool-d4gosudok46e0e081` |
| 地域 | `ap-shanghai` |
| 数据库 | **PostgreSQL**（该环境没有文档型数据库，禁止 `db.collection()` 思路） |
| API 形态 | PostgREST 风格：`{REST}/v1/rdb/rest/<table>?<query>` |
| REST 根 | `https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/`（结尾保留 `/`） |
| 静态资源 | 网页在 `https://tool.furina.fun/tools/`（2026-11-07 起 DNS 暂停、转 CloudBase 快照）；课表旧兜底 `https://tool.furina.fun/tt.json` |
| CORS | 网关只白名单了网页来源 + 自定义头 `X-Device-Id`；小程序走 `wx.request` 无 CORS 问题；**Android 原生 HTTP 无 CORS 限制**，但仍需带全请求头 `[已核实 CB:19, CB:119; 推断-Android 无浏览器预检]` |
| 域名要求 | 仅 CloudBase 网关域名；`tool.furina.fun` 只是课表旧兜底，可不配 |

### 1.2 全局请求头 `[已核实 WEB:1846-1848, MP/cloud.js:127-142]`

| 头 | 值 | 适用 |
|---|---|---|
| `Authorization` | `Bearer <PUBLISHABLE_KEY>` | **所有** CloudBase 请求（含 `class_verify`） |
| `Content-Type` | `application/json` | 所有带 body 的请求 |
| `X-Class-Token` / `X-Device-Id` | 班级 token（二选一，服务端都认） | 除 `class_verify` 外的所有数据请求 |
| `Prefer` | `return=representation` 或 `return=representation,resolution=merge-duplicates` | 需要返回行 / upsert 时 |
| `Accept` | `application/json`（可选） | — |

- **绝不要发空 token 头**：两端实现都是「本机没有身份就不发数据请求」`[已核实 MP/cloud.js:144-148; WEB:3165, WEB:8880-8885]`。
- 需要 `return=representation` 的 upsert：`marks`、`fitrecs`、`userdata`；`homework` 发布用 `return=representation`。**写 `visits`/`feedback` 不要 representation**（网页历史 401 教训：PostgREST 的 returning 要求新行同时可 SELECT，非管理员读不到自己刚插入的行会被 RLS 拒绝；2026-11-04 已放宽读策略，但两端代码仍按「不请求 representation」实现）`[已核实 WEB:1868-1870; CB:481-521]`。

### 1.3 超时 / 重试 / 缓存控制 `[已核实 MP/cloud.js:53-63, 144-177; MP/cloud-config.js:42; WEB:1850-1872]`

- 小程序实际 `timeout: 8000 ms`（cloud-config 覆盖 cloud.js 默认 15000）；网页用浏览器默认。
  **Android 建议**：连接/读超时 10 s；GET 失败重试一次（间隔 ~350 ms），**写请求不重试**（避免重复写入）——与 MP 一致。
- GET 一律带 `Cache-Control: no-store` 语义（网页 `cache:"no-store"`；Android 用 OkHttp `cacheControl(CacheControl.FORCE_NETWORK)` 或禁用缓存）。
- HTTP 失败约定：非 2xx 即失败；PostgREST 错误体形如 `{code, message, requestId}`；RLS 拒绝典型 `401` + `DATABASE_42501`；网关未知路径 `404` `[已核实 CB:177; DDL 注释:42, 155]`。

---

## 2. 鉴权：`class_verify` 与 token 生命周期

### 2.1 身份校验 RPC（唯一免身份接口）`[已核实 CB:24-52, WEB:3308-3311, MP/cloud.js:214-247]`

```http
POST {REST}rpc/class_verify
Authorization: Bearer <PUBLISHABLE_KEY>
Content-Type: application/json

{"p_sid":"<10 位学号>","p_name":"<真实姓名（与云端名单逐字一致）>"}
```

成功响应（HTTP 200，对象或单元素数组——**两种都见过，客户端要兼容**）：

```json
{"ok":true,"token":"<30 天有效的身份 token>","sid":"<学号>","name":"<姓名>",
 "role":"<职务，可为空>","badge":"<徽章，可为空>","is_admin":false}
```

失败响应（HTTP 200 + `ok:false`，或 HTTP 4xx）：

```json
{"ok":false,"reason":"姓名或学号不在班级名单中"}
```

实现要点：
- 学号为 **10 位数字**（网页/小程序都在前端先校验格式）；姓名去首尾空白。
- 成功即写入本机身份 `{token,sid,name,role,badge,is_admin,ts}`，`ts=Date.now()`。
- 失败原因直接展示云端 `reason`；网络异常/云端不可达给通用文案 `[已核实 MP/cloud.js:241-246]`。
- 网页还会把最近一次 `{sid,name}` 存 `zq_last_identity` 用于回填输入框 `[已核实 WEB:8843, 8868-8871]`。
- **管理员**：`is_admin=true` 即陈子麒（学号 3260707024）；管理员专属能力 = `class_set_role`、通知编辑/隐藏、`service_health` 读、`site_assets` 写（服务端脚本）、`visits` 全表读、`feedback` 列表 `[已核实 CB:74, CB:390-401; WEB:3591-3593]`。

### 2.2 测试身份（is_test）`[已核实 CB:634-647, DDL:2026-11-05_test_account.sql]`

- `private.class_ids` 增列 `is_test boolean not null default false`；测试行（座位 999）**正常走 `class_verify` 登录**，无演示模式。
- `public.class_members()` 过滤 `is_test`，所以 App 拉到的 70 人名单 / 名单选择器 / 统计里**不会出现**测试身份。
- 测试身份可读写自己的数据（与普通身份同权）。
- 具体账号密码对（sid + 姓名）写在 CloudBase 运维文档中，**不在本契约复述**；Android 联调用测试身份请向 Lead 索取。
- 审核结束回收会删除 `class_sessions` 行 + `class_ids` 行（token 立即失效）。

### 2.3 token 生命周期 `[已核实 CB:16-22, WEB:8880-8885, CB:690; 部分待确认]`

- 服务端签发 **30 天**有效 token，会话存 `private.class_sessions`（sid/name/expires_at），**不对外暴露** `[已核实 CB:18, CB:66-67]`。
- 客户端没有 refresh token 机制：
  - 网页启动时若 `localStorage.zq_class_identity` 的 `ts` 距今 **< 29 天**则直接进入班级（不重新 verify）；否则回到身份门禁 `[已核实 WEB:8880-8885]`。
  - 小程序不校验 ts，依赖服务端 401 → 数据为空/报错，用户手动退出重登 `[已核实 MP/utils/identity.js（无过期处理）；MP/cloud.js:355-383 失败静默]`。
- **Android 建议（推断/推荐）**：本地存 `token + issuedAt`；启动时 `now-issuedAt < 29d` 直接进入并后台静默拉一次数据；若任意数据请求连续返回 401，则清除 token 回身份页（现有两端没有统一 401 拦截，这是 Android 要补的健壮性）。
- **无令牌吊销入口**（CloudBase 控制台删会话除外）`[已核实 _compliance/audit_current_state.md:78]`。

### 2.4 本机身份存储与退出 `[已核实 WEB:3161-3163; MP/utils/identity.js:13,41-67]`

| 平台 | 存储键 | 内容 |
|---|---|---|
| 网页 | `localStorage.zq_class_identity` | `{token,sid,name,role,badge,is_admin,ts}` |
| 小程序 | `wx storage.zq_class_identity` | 同上（JSON 字符串） |
| Android 建议 | EncryptedSharedPreferences / DataStore(加密) | 同上；token 属等价口令，必须加密存储 |

退出身份 = 清身份 + 清按 sid 隔离的本机缓存/通知缓存 + 回身份页（数据表本身不删）`[已核实 WEB:8872-8879 cacheKill; MP/identity.js:63-67,119-127]`。

---

## 3. 端点与数据表总表

> 详细请求/响应示例见 `api/ENDPOINTS.md`；Kotlin DTO 见 `api/KOTLIN_MODELS.md`。

### 3.1 表清单与 RLS 前置条件

| 表 | 主键/身份键 | 关键列 | 读 RLS | 写 RLS | Android 用途 | 证据 |
|---|---|---|---|---|---|---|
| `private.class_ids` | `sid` | seat,name,role,badge,is_admin,is_test | 无 API（security definer 用） | — | 不直接访问 | CB:66, CB:343-349 |
| `private.class_sessions` | token/sid | sid,name,expires_at | 无 API | — | 不直接访问 | CB:67 |
| `public.homework` | `id` text | subject,content,due,date,time,author,owner,attachments,done,archived,ts | 有效 token | 有效 token（insert/update；**无 delete**） | 作业列表/发布/软删 | DDL schema.sql:6-42; WEB:1988-2016 |
| `public.notices` | `id` text | title,body,pin,imp,author,owner,attachments,archived,ts,**audience,override_json,hidden,edited_at,edited_by** | 有效 token + audience 过滤 | **仅管理员**（insert/update 快照 2026-11-04 收紧） | 通知板/已处理 | DDL schema.sql:47-72; CB:371-401, 366-415; DDL 2026-11-05_notice_admin_override.sql |
| `public.marks` | `id = sid\|kind\|ref` | seat,kind('hw'\|'nt'\|'ntdone'),ref,val,ts | 本人 sid（token 解析；函数名见 Q1） | 同左 | 作业完成/通知置顶/通知已处理（**跨端唯一真源**） | DDL schema.sql:79-97; SYNC-C:42-60 |
| `public.userdata` | `id = sid` | blob(text JSON), ts | 本人 sid（token 解析） | 同左 | userdata v6 全量同步 | DDL schema.sql:191-199; SYNC-C:62-149 |
| `public.fitrecs` | `id` text | acc,data(JSON 字符串),arch,ts | 本人 sid（token 解析） | 同左 | 体测历史（最近 2 条） | DDL schema.sql:127-145; WEB:7678-7727 |
| `public.visits` | `id` text | slot('YYYY-MM-DD-HH'),sid,name,did,ts | 管理员全量；普通身份仅自己行 | 已认证可 insert（本人 sid） | 访问埋点/统计页 | CB:481-521; WEB:7884-7976; MP/cloud.js:1425-1494 |
| `public.feedback` | `id` text | content,contact,acc,done,ts | 管理员全量；本人可读自己 | 已认证可 insert | 反馈提交/管理员处理 | DDL schema.sql:182-189; CB:481-521 |
| `public.power` | `id = roomverify` | label,building,room,roomverify,quantity,unit,low,updated_at | 有效 token | 仅管理员（同步脚本） | 电费当前电量 | DDL 2026-11-05_power.sql:42-72 |
| `public.power_history` | `id bigserial` | roomverify,quantity,unit,ts | 有效 token | 仅管理员（insert/delete） | 电费趋势采样 | DDL 2026-11-06_power_history.sql:29-55 |
| `public.site_assets` | `id='tt'` | body(jsonb {c,t,meta}),meta,updated_at | 有效 token | 仅管理员 | 课表快照（首选） | DDL 2026-11-07_site_assets.sql:33-56; CB:649-690 |
| `public.site_config` | `id='web'` | value(jsonb {paused,note,paused_at}) | 仅管理员 | 仅管理员 | App 不需要 | DDL 2026-11-07_site_assets.sql:61-77; CB:668-671 |
| `public.service_health` | `id` text | name,kind,status,detail,meta,checked_at,updated_at | 仅管理员 | 仅管理员（云函数） | 诊断页（可选） | CB:576-632 |
| `public.roster` / `public.users` / `public.identity_verify` | — | — | — | — | **已下线/弃用**，Android 不要接 | CB:70-76; CB:340-352 |

### 3.2 端点一览（方法 + 路径 + 关键 query）

| # | 方法 | 路径 | 关键 query / body | 说明 |
|---|---|---|---|---|
| E1 | POST | `rpc/class_verify` | body `{p_sid,p_name}` | 免身份；换 token |
| E2 | POST | `rpc/class_members` | body `{}` | 班级名单 70 人 `[{seat,sid,name,role,badge,is_admin}]` |
| E3 | POST | `rpc/class_set_role` | body `{p_sid,p_role,p_badge}` | 管理员改班委职务 |
| E4 | GET | `homework` | `select=*&order=ts.asc&archived=eq.false` | 作业列表 |
| E5 | POST | `homework` | body `[row]`，`Prefer: return=representation` | 发布作业（已认证者均可） |
| E6 | PATCH | `homework?id=eq.<id>` | body `{archived:true}` 或 `{done:true}` | 软删（done 列前端忽略） |
| E7 | GET | `notices` | `select=*&order=ts.asc,id.asc&archived=eq.false` | 通知整表（含 auto 行） |
| E8 | GET | `notices` | `select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false` | 轻量签名（~2 KB，先比签名再拉整表） |
| E9 | PATCH | `notices?id=eq.auto_<gid>` | `{override_json,edited_at,edited_by}` / `{hidden,...}` | 管理员编辑/软删自动通知 |
| E10 | GET | `marks` | `select=*&seat=eq.<sid>` | 本人所有勾选 |
| E11 | POST | `marks` | body `[row]`，`Prefer: return=representation,resolution=merge-duplicates` | upsert（取消写 val:false） |
| E12 | GET | `userdata` | `select=*&id=eq.<sid>` | 取本人 blob |
| E13 | POST | `userdata` | body `[{id,blob,ts}]`，`Prefer: return=representation,resolution=merge-duplicates` | 覆盖式 upsert（先 GET 合并） |
| E14 | GET | `fitrecs` | `select=*&acc=eq.<sid>&arch=eq.false&order=ts.desc` | 本人体测历史 |
| E15 | POST | `fitrecs` | body `[row]` + merge-duplicates | 体测保存 |
| E16 | PATCH | `fitrecs?id=eq.<id>` | `{arch:true}` | 软删体测记录 |
| E17 | POST | `visits` | body `[row]`（**不要 representation**） | 访问埋点 |
| E18 | GET | `visits` | 管理员：`select=slot,sid,name&slot=gte.<from>&order=slot.desc&limit=2000` | 统计页 |
| E19 | POST | `feedback` | body `[row]`（不要 representation） | 提交反馈 |
| E20 | GET | `feedback` | `select=*&order=ts.desc`（管理员） | 反馈列表 |
| E21 | PATCH | `feedback?id=eq.<id>` | `{done:true}`（管理员） | 标记已处理 |
| E22 | GET | `power` | `select=id,label,building,room,quantity,unit,low,updated_at&id=eq.<rv>` | 电费当前值 |
| E23 | GET | `power_history` | `select=ts,quantity&roomverify=eq.<rv>&ts=gte.<ISO>&order=ts.asc&limit=2000` | 趋势采样 |
| E24 | GET | `site_assets` | `select=body,meta,updated_at&id=eq.tt` | 课表快照（首选） |
| E25 | GET | `service_health` | `select=id,name,kind,status,detail,meta,checked_at&order=id`（管理员） | 诊断（可选） |
| E26 | GET | `https://tool.furina.fun/tt.json` | 无（不带身份头） | 课表旧兜底（DNS 已暂停，可能不可达） |

---

## 4. `userdata` v6 同步契约（核心）

> 完整规格 + Kotlin 参考实现见 `api/USERDATA_V6.md`；数据模型见 `api/KOTLIN_MODELS.md`。本节给实现要点。

### 4.1 blob 结构（14 顶层键，顺序固定）`[已核实 MP/utils/privacy.js:10-31; WATER-C:48-58; SYNC-C:66-104]`

```jsonc
{
  "v": 6,
  "at": { "homeTools":0, "fit":0, "draw":0, "group":0, "roster":0, "hide":0,
          "tt":0, "prefs":0, "nick":0, "power":0, "water":0 },     // 11 个 section 时间戳（ms，0=无改动）
  "homeTools": ["draw","hw","nt","tt"],                             // 工具 key 数组，≤4（用户勾选，按工具顺序）
  "fit": { "sex":"m","grade":"12","h":"","w":"","vc":"","r50":"","sit":"","jump":"","pull":"","durM":"","durS":"" },
  "draw": { "start":1,"end":70,"count":10,"dup":false,"excl":"","sort":"draw" },
  "group": { "source":"prev","mode":"groups","n":4,"shuffle":false,"sel":[] },
  "roster": { "rosterSel":[], "rollSel":[], "rollSt":{}, "rollNames":"" },
  "hide": { "hwHide":{}, "ntHide":{} },
  "tt": { "ttView":"", "ttUseMine":false, "ttLocal":null, "termStart":"" },
  "prefs": { "hwRemind":{}, "ntSub":false, "ntSubAt":0, "toolOrder":["..."] },  // toolOrder 仅当用户拖过排序才写
  "nick": "",
  "power": { "sel":"<roomverify>", "at":0, "low":30 },              // at 值内镜像 = at.power
  "water": { "rooms": { "<roomverify>": { "p":4, "recs":[{"m":"2026-11","r":130.10,"ts":1699100000000}] } } },
  "homeToolsAt": 0                                                   // = at.homeTools 的旧版镜像
}
```

- **11 个 section**：`homeTools, fit, draw, group, roster, hide, tt, prefs, nick, power, water`（`SECTION_KEYS`）；顶层再加 `v` + `at` + `homeToolsAt` = **14 键**。
- `power` 的**值内**有 `at`（镜像 `at.power`，网页 `localView` 写、MP `udMirrorPower` 对齐），其它 section 值内无 `at`；`water` 值内**不镜像** `at` `[已核实 WATER-C:55; WEB:2866, 2936-2940; MP/cloud.js:983-987, 1094-1100]`。
- `prefs.toolOrder` 是「全部工具」自定义顺序（页面顺序，网关于 `prefs` section），默认不写，只有 `toolOrderSetAt>0` 才写，避免污染旧夹具 `[已核实 MP/cloud.js:957-965; AGENTS §4b 第 10 条]`。

### 4.2 合并算法（两端逐字节等价，tie-break = 云端赢）`[已核实 SYNC-C:109-132; WEB:2774-2795; MP/cloud.js:888-904]`

```text
merge(remote, local):
  if remote.v == 5: remote = { v:6, at:{homeTools: remote.homeToolsAt||0, fit:0},
                               homeTools: remote.homeTools, fit: remote.fit }
  out = { v:6, at:{} }
  for k in SECTION_KEYS:
      ra = num(remote?.at?.[k]) or 0
      la = num(local.at?.[k]) or 0
      hasR = remote?.[k] != null;  hasL = local[k] != null
      if hasR and (not hasL or ra >= la): out[k] = clone(remote[k]); out.at[k] = ra   // 云端赢（含相等）
      elif hasL:                          out[k] = clone(local[k]);  out.at[k] = la
      else:                               out[k] = DEFAULT[k];       out.at[k] = 0
  out.homeToolsAt = out.at.homeTools
```

- **默认值** `DEFAULTS`：`homeTools:[], fit:{}, draw:{}, group:{}, roster:{rosterSel:[],rollSel:[],rollSt:{},rollNames:""}, hide:{hwHide:{},ntHide:{}}, tt:{ttView:"",ttUseMine:false,ttLocal:null,termStart:""}, prefs:{hwRemind:{},ntSub:false,ntSubAt:0}, nick:"", power:{sel:"",at:0,low:30}, water:{rooms:{}}` `[已核实 MP/utils/privacy.js:18-31; WEB:2752-2759]`。
- **坏 JSON / 空行** = 没有云端数据（不要覆盖本机，只把本机合并结果推上去）`[已核实 SYNC-C:129; MP/cloud.js:857-865]`。
- **v5 → v6 迁移**：拉到 v5 也要正常 merge，并**立即写回 v6**（幂等）`[已核实 SYNC-C:130-132; WEB:2776-2781, 3083, 3105-3107; MP/cloud.js:1179-1196]`。
- **未拥有 section 原样保留**：网页不写 `prefs`（从最近合并基线带）、小程序不写 `nick` 与 `tt` 除 `termStart` 外字段——push 时不得用默认值覆盖云端 `[已核实 SYNC-C:106-107; WEB:2857; MP/cloud.js:915-980]`。

### 4.3 推 / 拉 时机与防抖 `[已核实 SYNC-C:134-149; WEB:2990-3149; MP/cloud.js:1165-1315]`

| 动作 | 规则 |
|---|---|
| 拉取 | 启动后 idle 拉一次；回前台 / 30 s 轮询时 TTL **60 s** 门控；手动「立即同步」force |
| 推送触发 | 本机数据 `D.save()`/`store.save()` 后做脏检测（与「最近合并基线」比较），有脏 → 防抖 **1200 ms** 后 push |
| push 流程 | ① GET 本人行 → ② `localView`（脏 section `at = max(now, base.at+1, remote.at+1)` 抢时间戳）→ ③ merge → ④ POST upsert `{id,blob:JSON.stringify(merged),ts}` → ⑤ 成功后更新基线、清待补发 |
| pull 流程 | GET → merge → 应用比本机新的 section → 更新基线；若本机仍有脏 section 或云端是 v5 → 立刻补一次 push |
| 离线/失败 | 保留脏标记 `pend`（网页 `zq_udpend_<sid>`；MP `store.udPend`），下轮 pull/push 自动补发 |
| 关闭同步 | MP `cloudOff=true`：不拉不推、marks 写入入队；恢复后补推 + 拉一次。**网页没有这个开关**（`UD.off()` 恒 false）`[已核实 WEB:2769]` |
| 清除云端 | 覆盖空 v6 blob + 本人 marks 全 `val=false` + fitrecs 逐条 `arch=true`；随后进入抑制期（默认不自动回推，直到用户再次显式改动；安全上限 30 天）`[已核实 MP/cloud.js:1323-1384, 1118-1127; SYNC-C:148]` |

### 4.4 Android 必须踩准的 5 个细节 `[已核实]`

1. **`at` 只增不减**：脏 section 抢时间戳时用 `max(now, base.at[k]+1, remote.at[k]+1)`（网页 `localView`）；MP 用 `now` 但保证 `udOwnedDirty` 才置。**统一按网页版取 max 更稳**。
2. **`homeToolsAt` 每次输出都等于 `at.homeTools`**，否则旧客户端读不到。
3. **`power.at` 双向镜像**：写 blob 前把 `power.at = at.power`。
4. **`water` 必须归一**（`p∈{4,6}`、月份 `YYYY-MM` 合法、`r` 保留 2 位、按月升序、≤120 条、坏房间丢弃），推流前归一，避免把脏数据写回云端 `[已核实 WATER-C:175-177; WEB:5264-5276]`。
5. **首次无基线**：不能盲目 push（会用默认值覆盖云端）；先 pull 建基线；若用户先产生改动（如刚选关注宿舍），用 forceKeys/`pendWrite` 保证本地赢 `[已核实 WEB:3013-3062, 2896-2908; MP/cloud.js:993-997, 1165-1205]`。

---

## 5. `marks` 契约（跨端唯一真源）

```text
id   = "<sid>|<kind>|<ref>"
seat = "<sid>"                      // 服务端 RLS 按 seat 校验本人
kind = "hw" | "nt" | "ntdone"       // 作业已完成 / 通知置顶 / 自动通知已处理
ref  = homework.id / notices.id / 通知组 id（g_*，即 auto 行去掉前缀）
val  = true | false                 // 取消只写 false，绝不 DELETE
ts   = Date.now()
```

- 写入：`POST marks` + `Prefer: return=representation,resolution=merge-duplicates`（upsert）`[已核实 WEB:2670-2673; MP/cloud.js:389-403]`。
- 读取：`GET marks?select=*&seat=eq.<sid>`；只认 `val===true`；拉回后「本机待补发的改动」要最后覆盖云端旧值，防回滚 `[已核实 SYNC-C:42-60; WEB:2684-2694; MP/cloud.js:370-377]`。
- 网页 `marks` TTL 2 min；回前台/切页 `marksPull(true)` 强制拉（5 s 防抖），并在拉回后立刻 `resyncPending()` 补发失败写入 `[已核实 WEB:2704-2722, 2719-2721, 3640-3645, 8798-8804]`。
- 迁移：网页旧 `localStorage.zq_auto_nt_done` 首次同步时转成 `ntdone` marks（按 sid 标记一次，迁后清本地）`[已核实 WEB:2070, 8785; CB:539]`。
- `marks` 表 `kind` 为 text 无 CHECK 约束，`ntdone` 无需 DDL `[已核实 SYNC-C:253-254]`。

---

## 6. `notices` 契约（含自动通知 JSON）

> 完整字段表、`statusOf` 状态机、`recurrence` 与原生渲染方案见 `api/NOTICES.md`。

### 6.1 行模型 `[已核实 DDL schema.sql:47-72; DDL 2026-11-05_notice_admin_override.sql; CB:358-464]`

`public.notices`：`id,title,body,pin,imp,author,owner,attachments,archived,ts`（人工通知，`pin/done` 前端忽略，置顶走 marks）＋ `audience`（`all|committee|league|sid:<学号>|admin`）＋ 管理员覆盖 4 列 `override_json(txt), hidden(bool), edited_at(bigint), edited_by(text)`。

- **自动通知行**：`id = "auto_" + gid`，`body = "[[CLASS_NOTICE_V1]]" + JSON.stringify(group)`；采集脚本写这些行，**不得**写 override 4 列 `[已核实 CB:358-363, 411-415; DDL override:14-16]`。
- 可见性：RLS 服务端按 `audience` 过滤（班委/团员/指定学号/管理员）；`hidden=true` 且非管理员在客户端也会被过滤 `[已核实 CB:388-411; MP/cloud.js:564-567]`。
- 轻量签名（避免每次拉 ~2 MB 整表）：

```text
sig = 行按 order=ts.asc,id.asc 后 join("|") 每行 "id:ts:hidden:edited_at"
查询：GET notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false
签名一致 → 不拉整表；不一致 → GET notices?select=*&order=ts.asc,id.asc&archived=eq.false
```

`[已核实 WEB:1856-1865, 2600-2607; MP/cloud.js:544-553, 638-665]`。管理员编辑/隐藏**不改 ts**，所以签名必须含 `hidden`/`edited_at`——Android 照抄。

- 管理员编辑：`PATCH notices?id=eq.auto_<gid>`，body `{override_json: JSON.stringify(fields), edited_at: now, edited_by: sid}`（`fields` 白名单 `title,cat,summary,aiSummary,deadline,eventAt,expireAt,timeLabel`；值为 null 表示清空覆盖）；软删/恢复：`{hidden:true|false, edited_at, edited_by}` `[已核实 MP/cloud.js:723-763; DDL override:12-13]`。
- 附件：`attachments` 为 JSON 字符串数组 `{n:文件名,t:MIME,s:字节数,d:base64}`，最多 2 个（图片长边 1600/JPEG 压缩后 ≤ ~1.9 MB base64；文件 ≤ 2 MB）`[已核实 CB:219-222; WEB:1880-1956]`。

### 6.2 自动通知组 JSON（Android 原生渲染的数据源）`[已核实 MP/pages/nt/nt.js:157-333; WEB:7343-7373, 7490-7504]`

核心字段（全部可选，缺省有兜底）：

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | string | 组 id（`g_*`/`auto_*` 体系，前端只用作 key） |
| `title` | string | 标题 |
| `cat` | string | 分类（通知/活动/比赛…，前端只做筛选 chip） |
| `source` | string | 来源群名/来源描述 |
| `senderName/senderRole/senderBadge` | string | 发送人展示信息 |
| `aiSummary` | string | AI 摘要 |
| `aiPoints` | string[] | 要点（≤3 展示） |
| `aiSegments` | `[{type,text}]` | 结构化段落（时间/地点/对象/内容/报名/截止/要求/联系方式/补充） |
| `items` | array | 附件/原文条目，见下 |
| `deadline` / `deadlineAction` | ISO 时间 / string | 真实截止；`extensions[]` 最后一次延期优先 |
| `eventAt` / `eventAction` | ISO / string | 活动/集合时间 |
| `expireAt` | ISO | 自动归档时间（优先于 deadline 判断「不再进行中」） |
| `timeLabel` | string | 卡片顶部展示文案（如「报名 11-05 12:00」） |
| `signupStart` / `signupEnd` | ISO | 报名窗口（有则报名优先展示） |
| `recurrence` | object | 长期安排（`freq: daily/weekly/monthly_day/monthly_weeks`, `weekday,weeks,day,time,startAt,label`） |
| `actionable` | bool | false 不计入待处理 |
| `alert` | bool | 已截止但「有新名单」特殊状态 |
| `ocr` | bool | 含 OCR 图片内容 |
| `updatedAt` | ISO | 采集端更新时间（显示「已更新」） |
| `mergedSources` / `crossSource` | array / bool | 跨源合并标记（微信/QQ 同通知合并） |
| `items[].kind` | `text\|follow\|img\|file\|form\|link\|mini\|article\|merged` | 条目类型 |
| `items[].time` | string | 发送时间（用于相对日期替换锚点） |
| `items[].text/fromName/fromRole/label` | string | 文本条目 |
| `items[].src` | data URI | 图片（`kind=img`，可能带 `caption`,`ocr`） |
| `items[].name,url,size,note` | string | 文件；可能带 `archive{status,count,summary,files[]}` 压缩包识别结果 |
| `items[].realTitle,realUrl,close,note` | string | 收集表（腾讯文档类） |
| `items[].messages[]` | `[{from,fromName,fromRole,time,text}]` | 合并转发的聊天记录（≤8 条展示） |

---

## 7. 业务算法（客户端本地计算，云端不参与）

### 7.1 抽签 STEP 1 `[已核实 WEB:6303-6345, 6397-6409; MP/pages/draw]`

```text
输入：start,end,count,dup(允许重复),excl(排除序号，逗号分隔),rosterSel(参与名单座号数组，可空)
lo=min(start,end), hi=max(start,end)
pool = [lo..hi] - excl                         // 序号字符串解析，支持 "1,3,5-7" 之类（网页 parseExcl）
if rosterSel 非空: pool = pool ∩ rosterSel     // 抽签池必须同时在范围与名单中
if !dup && count > pool.length: 拒绝（提示可用序号不足）
if dup: 结果 = count 次独立随机（可重复）
else:   Fisher-Yates 洗牌 pool 后取前 count 个（不重复）
排序选项：按抽取顺序 / 从小到大 / 从大到小 / 随机（结果后处理，不改变抽取集合）
剔除重抽：从结果删一条 → 若勾选「自动补抽」再从 pool-已用 中随机补一个；记录 log[{r,add[]}]
```

### 7.2 分组 STEP 2 `[已核实 WEB:6478-6504, 6522-6535]`

```text
成员来源：prev = STEP1 结果（带姓名） / names = rosterSel 名单（≥2 人）
模式：groups = 按组数 n（clamp 2..成员数）；pergroup = 按每组人数 n（组数 = ceil(成员数/n)）
可选打乱：Fisher-Yates（未打乱则按原顺序轮流分配）
分配：list.forEach((p,i)=> out[i % groups].push(p))    // 轮流分配
```

### 7.3 体测（《国家学生体质健康标准 2014》大学组）`[已核实 WEB:7517-7611, 7523-7556, 7566-7579]`

- 输入：`sex∈{m,f}`、`grade∈{12,34}`（大一/大二、大三/大四）、`h,w,vc(肺活量),r50(50m),sit(坐位体前屈),jump(立定跳远),pull(引体向上/仰卧起坐),durM,durS(长跑分秒)`。
- 权重：BMI 15%、肺活量 15%、50 m 20%、坐位体前屈 10%、立定跳远 10%、引体/仰卧 10%、长跑 20%；总权重 100，另有附加分 ≤20（引体/仰卧、1000/800 m 为高/低优指标加分表）。
- 算法：每项按 `FTB` 阈值表查「等级分」`FTS=[100,95,90,85,80,78,76,74,72,70,68,66,64,62,60,50,40,30,20,10]`（`hi:true` 指标用 `v>=阈值` 命中，跑步用 `v<=阈值`）；BMI 分档 低体重 80 / 正常 100 / 超重 80 / 肥胖 60（`FTBMI m:[17.9,23.9], f:[17.2,23.9]`）。
- 总分 = `round10(Σ 单项分×权重 + 附加分)`；等级 ≥90 优秀 / ≥80 良好 / ≥60 及格 / <60 不及格；未填项按 0 计并标注 `miss`。
- 阈值表体积较大（`FTB` 7 张表 ×4 列 ×20 值 + 加分表 `FTP`），**Android 端直接从 WEB:7523-7564 移植成常量表**（纯数据，无版权问题；页面称数据源为国家标准公开表）。
- 历史记录（`fitrecs`）：本机/云端**最多保留最近 2 条**，超出的旧记录 `arch=true` 软删；记录 `data` JSON = `{id,ts,n(名称),sex,gr,fields{...},total,grade}`；「补填项目」时与同 sex/gr 且旧记录字段是其子集的记录合并 `[已核实 WEB:7683-7727, 7804-7827; MP/cloud.js:765-809]`。

### 7.4 课表（周/日视图）`[已核实 WEB:6591-6601, 6616-6628; MP/utils/ttplan.js]`

- 数据 `{c:[course], t:"YYYY-MM-DD"(开学日), meta:{source,fetchedAt,count,termStart,syncedAt}}`；
  `course = {name, teacher, room, day:0..6(0=周一), start:1..14, end, weeks:"1-16,18" 或 "2-4,6-9", parity:"all|odd|even"}`。
- 节次时间 `PERIODS`（12 节，固定）：`08:10,08:55,09:50,10:40,11:25,14:30,15:15,16:10,16:55,19:00,19:45,20:30` 起，各节结束时间见 WEB:6591-6593（Android 常量照抄）。
- 周次 `nextWeek = floor((today - termStart)/7) + 1`（同日为第 1 周；`termStart` 为空/无效则 null = 不按周过滤）；上课判定 `active(c,wk)`：`weeks` 含 wk（空 weeks = 全学期）且 parity 匹配 `[已核实 WEB:6595-6601]`。
- 数据源优先级（网页）：发布链接 > 教务快照(`site_assets`/`tt.json`) > 个人导入 > 本机缓存；**非管理员也看教务快照**，只是个人导入优先 `[已核实 WEB:6615-6628]`。
- 拉取：网页 `tt.json` 同源优先，失败回退 `GET site_assets?id=eq.tt&select=body,meta,updated_at`；MP 反过来（site_assets 优先，失败回退旧 `tt.json`，且旧域名不带身份头）。TTL 6 h `[已核实 WEB:7035-7067; MP/cloud.js:1596-1655; CB:673-687]`。
- 「下一节课」：从今天起 14 天内按真实时间找下一节（今天已开始的跳过已结束节次），见 `MP/utils/ttplan.js:84-99`。

### 7.5 宿舍电费（当前值 + 趋势 + 估算）`[已核实 WEB:4273-4700, 4470-4600; MP/utils/power.js]`

**数据**：`PW_ROOMS` **17 间**宿舍目录（硬编码常量，顺序即 UI 顺序；`rv` = roomverify 形如 `4-23A---321`，`label/building/room`）`[已核实 WEB:4286-4304; MP/power.js:86-105]`。
`power` 行：`{id(=rv),label,building,room,quantity,unit,low,updated_at}`；`power_history` 采样：`{roomverify,quantity,ts}` 按 `ts.asc`，区间 `24h|7d|30d`。

**状态**：`low` 阈值用户可调（档位 `[10,20,30,40,50,60,80,100]`，默认 30，随 `userdata.power.low` 同步）；`q<low` 低电 / `low≤q<low*2` 偏低 / 其余充足 `[已核实 WEB:4283, 4366-4374; MP/power.js:19-24]`。

**算法 1 — 异常扣减识别（水费从电费账户扣）** `[已核实 WATER-C:119-143; WEB:4405-4430]`

```text
对相邻采样 dq = q[i-1] - q[i]（>0 用电，<0 充值）
U = 所有 dq>0.05 的中位数（空=0）
候选异常：dq > max(5, 4*U)
水费匹配：取该宿舍水量记录（月份=采样月或上一月）算 fee=max(0,(r-prev_r)-2*p)*3.03，deg=fee/0.58
  若 dq≥2 且 |dq-deg| ≤ max(2, 0.10*deg) → 判 water:true（即使未达候选阈值也忽略）
判定段不计入区间用电/区间电费（时间照算，用电记 0）；充值段整段跳过
```

**算法 2 — 区间统计 / 估算可用天数** `[已核实 WEB:4431-4622]`

- `deriveRange`：起点/当前/累计下降（跳过充值上升与异常扣减段）、窗口小时、`perDay=drop/hours*24`、`days=当前电量/perDay`（上限 999；有效条件 n≥2 且 hours≥6）。
- `rateFrom(24h/7d)`：正常段用 `used+=dq, hours+=dt`；异常扣减段 `hours+=dt`（用电 0）；充值段不计；持平计时长。`rate=used/hours*24`。
- 非慢频房估算：`v24`/`v7` 各自有效（`span≥6h 且 used>0.05`）→ `rate = 0.65*r24 + 0.35*r7`（只有一档用该档），`rate=max(rate,0.05)`，`days=q/rate`。
- 慢频房（楼栋 `13号楼`/`16号楼`，24 h 变化 <6 次）改用 30 d 真实更新点统计 `slowStats`：相邻变化点分段（dq>0.05 且间隔≥6 h 才算用电段），中位数阈值 `max(5,4*median)` 剔除异常大跌；1 段时按 24 h 折算并标「参考」；≥2 段按时间加权；不插值。
- 慢更新提示：24 h 窗口相邻采样 `|Δq|>0.005` 的变化次数 ≤2 → 显示「数据更新较慢 · 上次变化 MM-DD HH:MM」（0 次「数据未更新」）`[已核实 HANDOFF §8/AGENTS §4b 第 8 条; WEB:4457-4468]`。
- 「上次充值」tile（Android）：取 **30 d 全量历史**（云端历史保留上限 30 天）最后一次电量上升 `lastRecharge`，值显示 `+Δq` 度（**充值量**，不是充值后余额），切 24h/7d/30d 不变；慢频房用 `slowStats.lastRecharge`（真实变化点，避免日更采样噪声）；无记录「—/暂无记录」。
- 趋势交互（Android 参考）：24h/7d/30d 全采样按真实时间比例铺满（**禁止横向滚动**）；点击/拖动按 x 命中最近**真实采样点**显示竖线+气泡 `HH:MM / MM-DD HH:MM · 电量 xx.xx 度` `[已核实 AGENTS §4b 第 11 条; WEB:4623-4701]`。
- 请求节奏：**未选择宿舍时 0 请求**；当前值 2 min TTL；每档历史 5 min 缓存；空结果/失败**不写缓存**（防「数据积累中」毒化）`[已核实 MP/cloud.js:1496-1576; WEB:4388-4402]`。

### 7.6 宿舍水量（抄表 + 计费）`[已核实 WATER-C:7-58, 104-143; WEB:5207-5669; MP/utils/water.js]`

- 常量 `RULES`：电 `0.58 元/度`、水 `3.03 元/吨`、免费 `2 吨水 + 5 度电 /人/月`、`personsDef=4`、可选 `[4,6]`、每学年 10 个月、无阶梯。
- 数据 `water.rooms[rv] = {p:4|6, recs:[{m:"YYYY-MM", r:读数吨, ts}]}`；`recs` 按月唯一、升序、≤120 条、`r` 2 位小数。
- 保存校验：读数有限且 ≥0；同月覆盖；不得小于上一条、不得大于下一条（存在更晚记录时）；保存后 `D.save()` 触发 UD 同步（1200 ms 防抖）。
- 公式：`本期用量 = 本次 − 上一条（同房间月升序）`；`水费 = max(0, 用量 − 2×人数) × 3.03`（2 位）；`折合扣电 = 水费 ÷ 0.58`（1 位）；`1 吨 ≈ 5.2241 度`。
- 电费页联动：采样时刻所在月（或上一月）若恰有水量记录，按上式折算 `deg`，供 §7.5 的异常扣减匹配。
- 同步：`water` section 整段 LWW（不按宿舍/记录合并；双端并发改不同宿舍时后写整段覆盖 = 已知限制）`[已核实 WATER-C:168-171]`。

### 7.7 名册 / 点名 `[已核实 WEB:3338-3428, 7832-7882, 7860-7881; MP/utils/roster.js]`

- 名单只能来自 `rpc/class_members`（座号 1–70）；三处选择器（抽签/分组/点名）都存**座号数组**（去重升序，`normSeats`：只保留 1–70 整数），派生文本 `roster/group.names/rollNames` 只用于显示/导出。
- 点名状态 `rollSt: {"<索引>": "in"|"ab"}`（索引对应当前 `rollSel` 排序后的位置）；重建时按**姓名**保留状态 `writeRollFromSel`（名单座号变化不会把状态错位到别人）。
- 旧文本名单迁移是一次性幂等操作（按姓名匹配回座号；迁移标记 `rosterMigrated`）。

---

## 8. 导出卡片 v2（网页/小程序同一版式）

> 完整参数、逐像素版式、Android Canvas 数据模型见 `api/EXPORT_CARD_V2.md`；唯一真源 `web/tools/tests/export_card_v2_spec.md`（134 行）。

要点 `[已核实 CARD:1-134]`：
- 逻辑宽度 `W=760`、`DPR=2`（成图 1520 px 宽、高度自适应）；主卡内缩 22、圆角 22、白底 + 柔和投影；画布垂直渐变底。
- 结构固定：**头部（品牌行/标题/副标题/分隔线）→ 参数配置面板 → blocks 内容 → 页脚（生成时间 + 唯一编号 + 品牌）→ 斜向平铺水印**。
- `blocks` 三种：`{label,lines:[string|runs],mono?,atomic?}`、`{label,chips:[{n,nm}]}`、`{label,groups:[[{n,nm}]]}`；`opt.qr/qrNote` 仅抽签剔除重抽使用。
- 命名：`<类型>_<YYYYMMDD-HHMM>_<参数>.png`（例：`抽签结果_20261009-2024_范围1-50_抽10个_不重复_按抽取顺序.png`）；文件名经 `safeName` 过滤 `\/:*?"<>|` 与空白。
- 水印：卡内裁剪后 15px/700、旋转 -20°、alpha 0.03、以画布中心按 x 步长 300 / y 步长 140 平铺。
- 唯一编号 `recId(prefix)` = `PREFIX-YYYYMMDD-HHMM-XXXX`（4 位随机大写）`[已核实 WEB:5746-5749]`。
- 硬红线：图内**不得出现学号/手机号/宿舍号**；水印只在卡内。
- MP 实现签名（Android 可对齐命名）：`drawCard(g, canvas, opt)` / `exportCard(page, selector, opt)` / `exportAndSave(...)` `[已核实 MP/utils/canvas.js:77,495,561]`。

---

## 9. 通知渲染建议（Android）

**结论：可以 100% 原生渲染，不需要 WebView 作为主路径。** 依据与方案：

1. **数据是结构化 JSON**（§6.2），不是 HTML；网页 iframe 只是把同一份 `AUTO_NOTICES` 注入 HTML 模板：`payload = JSON.stringify(AUTO_NOTICES)` → 替换模板占位符 `__DATA__` → `iframe.doc.write()` `[已核实 WEB:2482-2500]`。小程序已用 `mapGroup(rawG)` 把它映射成原生 WXML 卡片并上线 `[已核实 MP/pages/nt/nt.js:157-333]`。
2. **原生渲染所需字段**（MP 已验证的卡片结构）：`title/cat/statusLabel+statusCls(expired)/source/sender/pix 徽标/aiSummary/aiPoints/segs/items(按 kind 分派)/quotes(原始消息)/imgs/compose 统计/长期安排 label+下次时间/updated(管理员编辑过)/hidden(管理员软删)`。
3. **状态机**（必须移植，`api/NOTICES.md` 附伪代码）：报名窗口优先 → 真实截止（`extensions` 最后一次延期）→ `eventAt`（结束= `expireAt||eventAt`）→ 无时间「无时间」；长期安排 `recurrence` 永不判过期、`actionable=false` 不计待处理 `[已核实 MP/utils/notice.js:36-162, 164-300]`。
4. **相对日期替换**：标题/摘要/要点/段落/原文/合并消息里的「今天/明天/本周三/N 号/下午」按**同卡第一条 item 的发送时间**做锚点替换为 `MM-DD`（`absRelDates`，~150 行规则，纯前端显示层）`[已核实 MP/utils/notice.js:350-547]`。Android 可直接移植或先用原文（不影响功能，只影响可读性）。
5. **附件**：`items[].src/url` 多为 `data:` URI（图片/PDF/文件，base64）；Android 用 `Base64.decode` 后写 `cacheDir` 再交给图片库/`ACTION_VIEW`（MP 同思路：data URI → 临时文件）。超 10 MB base64 拒收 `[已核实 MP/pages/nt/nt.js:335-349, 1220-1360]`。
6. **WebView 兜底场景（可选，不作为主路径）**：① 将来出现未知 `items[].kind`；② 需要渲染富文本/复杂 HTML 邮件式通知时。兜底 WebView 只加载本地拼的 HTML，**不要**加载外部 URL（隐私 & 离线）。
7. **「已处理」写回**：`marks kind='ntdone', ref=gid`（§5）；MP 在卡片按钮里 `setMark` 后本地立刻更新 `ntDone`，云端失败进队列 `[已核实 MP/pages/nt/nt.js:732-760; MP/cloud.js:389-403]`。
8. **管理员编辑/软删**：仅 `is_admin` 显示入口；写 E9；成功后本地热补丁（`_override/_hidden/_editedAt/_editedBy`）+ 清通知同步 TTL，下次签名校验自动重建 `[已核实 MP/cloud.js:730-757; WEB:2556-2570]`。

---

## 10. Android 端实现建议（推荐，非现有代码约束）

1. **网络层**：OkHttp + kotlinx.serialization；统一 `ApiClient`：
   - `baseUrl = BuildConfig.REST_BASE`（默认同上，可配置）；
   - 拦截器注入 `Authorization: Bearer <publishableKey>`、`X-Class-Token`（有身份时才加）、`Content-Type`、`Accept`；
   - `cacheControl(FORCE_NETWORK)`；`retryOnConnectionFailure` 只对 GET 手动重试 1 次（350 ms 退避），写请求不重试；
   - `timeout 10 s`；错误统一映射 `ApiError.Http(status, code, message)` / `ApiError.Network`。
2. **身份**：`IdentityStore`（EncryptedSharedPreferences）存 `{token,sid,name,role,badge,isAdmin,issuedAt}`；启动 29 天判断；401 统一拦截 → 清 token → 回登录页；不做 refresh（无接口）。
3. **本地缓存**：Room（表：`homework/notices/marks/fitrecs/userdata_cache/outbox`）。**所有读先给缓存再后台刷新**（网页/MP 都是这个策略，断网必须可用）。大字段（通知 2 MB、附件 base64）不要每次读进内存全量重解析——参考 MP 的「内存热缓存 + 按需临时文件」；Android 可用 Room + 分页/懒加载。
4. **同步引擎**（对齐 v6）：
   - 写路径：业务写本地 → 标脏 → 1200 ms 防抖协程 → `GET userdata` → merge → `POST`；
   - 拉取：启动/回前台/TTL 60 s；30 s 前台轮询只刷 marks/通知签名/作业等轻量项（不要每 30 s 重拉 2 MB 通知）；
   - 待补发 outbox：marks / homework / notices 发布 / fitrecs / userdata 脏标记（与两端 `markPend/_pending/udPend` 语义一致）；
   - `WorkManager` 只做低频补发（如 15 min 一次，网络可用时），不做实时同步。
5. **安全/隐私**：不打印 token/名单内文；日志脱敏；`android:allowBackup=false` 或排除敏感偏好；导出图片不含学号/宿舍号；宿舍号仅在电费/水量页本人可见。
6. **时区/时间**：云端 `ts` 都是 **epoch 毫秒**（bigint），`power_history.ts`/`site_assets.updated_at` 是 **timestamptz(ISO 字符串)**；`visits.slot` 是**本地时间** `YYYY-MM-DD-HH`。Android 一律用 `ZoneId.systemDefault()` 格式化，与现有网页/MP 一致。
7. **可配置性**：把 REST base / publishable key / tt.json legacy URL 放 `BuildConfig`，避免重编译（CloudBase 2026-12-10 到期可能迁移，见 §11）。
8. **不建议**：接 CloudBase SDK（现有两端都是裸 REST，RLS 与头契约已固定）；也不建议自己做 WebView 壳套网页（课表/通知/导出都能原生做，且网页有 646 KB 单文件 + iframe 性能债）。

---

## 11. 待确认清单（代码中找不到权威依据，禁止臆造）

| # | 待确认项 | 现状/线索 | 建议确认方式 |
|---|---|---|---|
| Q1 | `class_verify` / `class_members` / `auth_ok` / `auth_sid` 的**服务端实现**（token 生成算法、随机源、TTL 常量、过期清理任务、失败限流） | 仓库无 `create function public.class_verify`；`private.class_sessions` DDL 也不在库；只有文档描述「30 天」`[CB:18, CB:67; _compliance/audit_current_state.md:329]` | CloudBase 控制台 `select proname,pg_get_functiondef(oid) from pg_proc where proname like 'class_%' or proname in ('auth_ok','auth_sid','auth_admin')`（只读，管理员执行） |
| Q2 | token 过期时各端点的**具体状态码/响应体**（401？403？还是 200+空数组） | 文档说「token 失效后页面回身份门禁」，但网页代码没有 401 处理（只是请求失败静默）；MP 失败静默 `[CB:22; WEB:2704-2722 无 401 分支]` | 用一个过期/伪造 token 实测（只读 GET），记录状态码 + body |
| Q3 | 通知 `audience` 的完整取值与 RLS 是否最终版（`all/committee/league/sid:<学号>/admin`） | CB 记录 2026-11-04 两次迁移，均已应用；仓库无对应 SQL | 管理员身份只读 `select distinct audience from public.notices`（或查 pg_policies） |
| Q4 | `service_health` 18 行 id 清单与 App 是否需要 | CB 列 18 行分组、13 项采集；诊断页纯管理员可选功能 `[CB:576-632; WEB:7990-8008]` | 产品决定：Android v1 可不做诊断页 |
| Q5 | `site_config` 标准字段 | `{paused,note,paused_at}`，仅管理员读写；App 不需要 `[CB:668-671]` | 跳过 |
| Q6 | CloudBase 环境到期（2026-12-10）后的迁移方案/新 base URL | CB 明确「到期需续费/迁移」`[CB:690]` | Lead/产品确认，App 端把 base URL 做成可配置 |
| Q7 | 是否需要 Android 支持「发布通知」 | 网页手动通知发布入口已移除（只有采集管线 + 管理员编辑）；`notices` insert/update RLS 已收紧为仅管理员 `[CB:401; WEB:7515]` | 产品确认：普通用户只读通知 |
| Q8 | `homework` 的 `done` 列是否仍写 | 前端恒写 false/读取忽略，完成走 marks `[CB:272; WEB:1989, 2675-2703]` | 保持现状即可 |
| Q9 | 通知卡片「原始消息/附件预览」的富文本边界 | 数据是纯文本 + data URI，无 HTML 正文；网页 iframe 的富排版是模板生成 `[WEB:2482-2500]` | 原生渲染即可；专项需求再评估 WebView |

---

## 12. 证据索引（文件 + 行号）

| 主题 | 证据 |
|---|---|
| REST 根 / 公开 key 位置 | `WEB:1844-1845`；`MP/utils/cloud-config.js:23,26` |
| 请求头/PostgREST 基座 | `WEB:1846-1872`；`MP/utils/cloud.js:127-208` |
| 身份 RPC / 名单 RPC / 改职务 | `WEB:3308-3331, 3591-3593`；`MP/utils/cloud.js:214-325`；`CB:24-60` |
| token 生命周期 / 自动登录 | `CB:16-22`；`WEB:8880-8885`；`MP/utils/identity.js:41-67` |
| 表结构（早期） | `web/deploy/cloudbase/schema.sql` |
| 通知覆盖 4 列 | `web/deploy/cloudbase/2026-11-05_notice_admin_override.sql` |
| 电费/电量历史/site_assets/service_health | `web/deploy/cloudbase/2026-11-05_power.sql`、`2026-11-06_power_history.sql`、`2026-11-07_site_assets.sql`、`2026-11-05_service_health.sql` |
| marks / v6 同步 | `SYNC-C:42-149`；`CB:523-573`；`WEB:2670-3149`；`MP/utils/cloud.js:327-449,811-1384` |
| v6 权威键序 / water | `WATER-C:48-58,168-199`；`MP/utils/privacy.js:10-31` |
| 通知可见性/audience | `CB:367-464` |
| 通知渲染（网页 iframe） | `WEB:2019`（模板 b64）、`WEB:2470-2506`、`WEB:2619-2650` |
| 通知渲染（MP 原生） | `MP/pages/nt/nt.js:154-333`；`MP/utils/notice.js:1-589` |
| 抽签/分组 | `WEB:6303-6358,6397-6433,6478-6535` |
| 体测 | `WEB:7517-7611,7523-7564,7567-7579,7678-7727` |
| 课表 | `WEB:6591-6628,6958-7067`；`MP/utils/ttplan.js`；`CB:649-690`；`DNSSPEC:35-70` |
| 电费算法 | `WEB:4273-4700,4405-4622,5076-5124`；`MP/utils/power.js:1-765`；`WATER-C:119-143`；`web/tools/tests/power_cadence_spec.md`（慢频/估算 §1.1-§1.5） |
| 同步 golden 测试/夹具 | `web/tools/tests/sync_check.py`；`mp-class-tools-tests/sync_contract_test.js`；`mp-class-tools-tests/_sync_fixtures/`（`merge_ref.js` + 6 个 JSON，见 `api/USERDATA_V6.md` §6） |
| 水量 | `WEB:5207-5669`；`MP/utils/water.js`；`WATER-C:0-200` |
| 导卡 v2 | `CARD:0-134`；`WEB:5746-5749,5853-6290,6441-6457,6522-6535`；`MP/utils/canvas.js` |
| visits / feedback | `WEB:4256-4271,7884-7976`；`MP/utils/cloud.js:1386-1494`；`CB:481-521` |
| 状态/缓存/轮询 | `WEB:3172-3189,8797-8839`；`MP/utils/cloud.js:46-99` |

---

## 附录：与任务描述的两点口径修正

1. **「userdata v6 13 键」→ 实际 14 键**：任务描述沿用 2026-11-05 的 13 键口径（`v,at,homeTools,fit,draw,group,roster,hide,tt,prefs,nick,power,homeToolsAt`）；2026-11-06 水量上线后 `UD_KEYS` 末尾追加 `water`，`WATER-C:54` 明确定稿为 **14 键**，两端代码（`WEB:2746-2758`、`MP/utils/privacy.js:15`）一致。本契约按 **14 键**实现。
2. **「通知能否原生渲染」→ 能**：网页 iframe 只是渲染手段；数据层是自动通知 JSON + 行级覆盖字段。小程序已原生实现并上线（`mp-class-tools/pages/nt`），Android 可以照做。
