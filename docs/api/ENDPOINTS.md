# `docs/api/ENDPOINTS.md` — 端点参考（PostgREST / RPC）

> 配套主文档 `docs/BACKEND_CONTRACT.md`。所有 URL 以 `{REST} = https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/` 为前缀。
> 公共请求头（下文简写为 `H`）：

```http
Authorization: Bearer <PUBLISHABLE_KEY>
Content-Type: application/json
X-Class-Token: <班级 token>        # 或 X-Device-Id；class_verify 时不要带
```

> 证据：`WEB:1844-1872`、`MP/utils/cloud.js:127-208`。
> 标记：`[已核实]` 代码/DDL/文档直接读到；`[推断]` 由行为推导；`[待确认]` 需服务端确认。

---

## 1. RPC

### E1 `POST rpc/class_verify` — 学号+姓名换 token `[已核实 CB:24-52; WEB:3308-3311; MP/cloud.js:214-247]`

- 头：`Authorization` + `Content-Type`；**不要**带 `X-Class-Token`/`X-Device-Id`。
- 请求：

```json
{"p_sid":"2024000001","p_name":"陈同学"}
```

- 成功（`ok:true`）：

```json
{"ok":true,"token":"<token>","sid":"2024000001","name":"陈同学",
 "role":"副团支部书记","badge":"班委","is_admin":true}
```

- 失败：HTTP 200 且 `{"ok":false,"reason":"姓名或学号不在班级名单中"}` **或** HTTP 4xx（两端都兼容；MP 把 `http4*` 映射为该提示）。
- 响应体可能是**对象或单元素数组**（MP 有 `Array.isArray(raw) ? raw[0] : raw` 兼容）——Android 两种都要接。
- 客户端前置校验：sid 必须 10 位数字、name 非空（`WEB:8855-8856`；`MP/cloud.js:217-219`）。
- token 有效期 30 天 `[已核实 CB:18]`；服务端实现不在仓库 `[待确认 Q1]`。

### E2 `POST rpc/class_members` — 班级名单 `[已核实 CB:42-52; WEB:3319-3331; MP/cloud.js:278-292]`

- 头：`H`（必须带身份 token）。
- 请求：`{}`。
- 响应（数组，约 70 人；`is_test` 已被服务端过滤）：

```json
[{"seat":1,"sid":"2024000002","name":"张三","role":"","badge":"","is_admin":false}, ...]
```

- `role` 非空 = 班委；`is_admin=true` = 管理员。客户端按 `seat` 归一（整数 1–70）；空数组视为失败并**保留旧缓存** `[已核实 MP/cloud.js:283-291]`。
- 名单 TTL：网页 6 h、MP 6 h `[已核实 WEB:3173; MP/cloud.js:58]`。

### E3 `POST rpc/class_set_role` — 管理员改班委职务 `[已核实 CB:54-60; WEB:3591-3593]`

```json
{"p_sid":"<目标学号>","p_role":"副团支书","p_badge":"班委"}
```

- 仅管理员可用（服务端 security definer 校验）；`p_badge` 空 = 取消班委。
- 错误 `{ok:false,reason}` 或非 2xx；成功后重新拉 E2 刷新。

---

## 2. 读接口（GET）

通用约定：`select` 显式列名优先（省流量）；`order` 必须确定（同 ts 时要加次级排序）；`limit` 必给（防全表外溢）。`[已核实 WEB:1850-1872; MP/cloud.js:190-201]`

### E4 作业列表

```http
GET {REST}homework?select=*&order=ts.asc&archived=eq.false
```

```json
[{"id":"m9x2k1ab","subject":"高等数学","content":"P42 习题 1-5",
  "due":"10-12","date":"2026-10-12","time":"23:59","author":"李四","owner":"2024000003",
  "attachments":"[{\"n\":\"p42.jpg\",\"t\":\"image/jpeg\",\"s\":12345,\"d\":\"<base64>\"}]",
  "done":false,"archived":false,"ts":1760000000000}]
```

- `done` 列**前端忽略**（完成状态在 marks）；`archived=true` 不返回。
- 过期清理：截止后 3 天的作业从本机移除并 PATCH `archived=true` `[已核实 WEB:7228-7238; MP/cloud.js:519-532]`。

### E7/E8 通知

```http
# 轻量签名（先比签名，省 2MB）
GET {REST}notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false

# 整表（签名变化或首次）
GET {REST}notices?select=*&order=ts.asc,id.asc&archived=eq.false
```

整表行示例：

```json
[{"id":"auto_g_20261105_ai_01","title":"通知","body":"[[CLASS_NOTICE_V1]]{\"id\":\"g_...\",\"title\":\"...\"}",
  "pin":false,"imp":false,"author":"","owner":"","attachments":"","archived":false,"ts":1760000000000,
  "audience":"all","override_json":"","hidden":false,"edited_at":0,"edited_by":""}]
```

- 服务端按 `audience` + 身份过滤（班委/团员/指定学号/管理员；`hidden` 行是否下发取决于当前 RLS 策略，客户端对非管理员也要过滤 hidden）`[已核实 CB:388-411; MP/cloud.js:564-567]`。
- 手动通知行（`id` 非 `auto_`）与自动行混在同一数组，客户端按 `body.startsWith("[[CLASS_NOTICE_V1]]")` 分流 `[已核实 WEB:2619-2641]`。

### E10 marks

```http
GET {REST}marks?select=*&seat=eq.<sid>
```

```json
[{"id":"2024000001|hw|m9x2k1ab","seat":"2024000001","kind":"hw","ref":"m9x2k1ab","val":true,"ts":1760000001000}]
```

- 只认 `val===true`；`kind ∈ {hw,nt,ntdone}`。
- TTL 2 min；回前台/切页强拉（5 s 防抖）`[已核实 WEB:2704-2722]`。

### E12 userdata

```http
GET {REST}userdata?select=*&id=eq.<sid>
```

```json
[{"id":"2024000001","blob":"{\"v\":6,\"at\":{...},\"homeTools\":[...], ...}","ts":1760000002000}]
```

- 0 行或 `blob` 为空/坏 JSON = 无云端数据，**不要覆盖本机**，只把本机合并结果推上去。
- 明细见 `api/USERDATA_V6.md`。

### E14 fitrecs

```http
GET {REST}fitrecs?select=*&acc=eq.<sid>&arch=eq.false&order=ts.desc
```

```json
[{"id":"f_abc123","acc":"2024000001",
  "data":"{\"id\":\"f_abc123\",\"ts\":1760000000000,\"n\":\"体测 11-05 20:00\",\"sex\":\"m\",\"gr\":\"12\",\"fields\":{\"h\":\"175\",...},\"total\":78.5,\"grade\":\"及格\"}",
  "arch":false,"ts":1760000000000}]
```

- `data` 是 JSON **字符串**，需二次 parse；最多保留 2 条（多余的旧行由客户端 PATCH `arch=true`）`[已核实 WEB:7698-7727; MP/cloud.js:765-809]`。

### E18 visits（管理员统计）

```http
GET {REST}visits?select=slot,sid,name&slot=gte.<YYYY-MM-DD-00>&order=slot.desc&limit=2000
```

- `slot` 是**本地时间小时槽**字符串，可直接字符串比较/切片做按天聚合 `[已核实 WEB:7887, 7908-7916]`。

### E20 feedback（管理员）

```http
GET {REST}feedback?select=*&order=ts.desc
```

### E21 宿舍目录（电费/水量选择器）

```http
GET {REST}power?select=id,label,building,room&order=id.asc&limit=200
```

```json
[{"id":"4-23B---423","label":"23号楼B座423","building":"23号楼B座","room":"423"},
 {"id":"1-13---324","label":"13号楼324","building":"13号楼","room":"324"}]
```

- `id` 即 roomverify；客户端**不硬编码房号**，目录由本接口读取后按楼栋自然序 + 房间号数字序排序。
- `label/building/room` 以 roomverify 派生为准（`1-13---324` → `13号楼324`、`4-23B---423` → `23号楼B座423`），云端字段仅作解析失败时的回退。
- 5 min 内存缓存；失败回退旧缓存；空目录不写缓存（保留上次成功目录）。
- 目录只用于「换宿舍」：已关注宿舍的展示名由 roomverify 派生，目录失败不影响看电量。

### E22 电费当前值

```http
GET {REST}power?select=id,label,building,room,quantity,unit,low,updated_at&id=eq.<roomverify>
```

```json
[{"id":"4-23A---305","label":"23号楼A座305","building":"23号楼A座","room":"305",
  "quantity":35.62,"unit":"度","low":false,"updated_at":"2026-11-07T03:01:00.123+00:00"}]
```

- `id` 即 roomverify（如 `4-23A---305`），目录 17 间见主文档 §7.5；未选择宿舍时**不要发请求**。

### E23 电费历史采样（趋势）

```http
GET {REST}power_history?select=ts,quantity&roomverify=eq.<rv>&ts=gte.<ISO 时间>&order=ts.asc&limit=2000
```

```json
[{"ts":"2026-11-06T03:00:00+00:00","quantity":40.12}, ...]
```

- `ts` 是 timestamptz ISO；`quantity` numeric（客户端 `Double.parseDouble` 容错）。
- 区间：`24h/7d/30d` → `cut = now - ms`，转 ISO 传入；每档 5 min 缓存，空结果不缓存 `[已核实 MP/cloud.js:1541-1561]`。

### E24 课表快照

```http
GET {REST}site_assets?select=body,meta,updated_at&id=eq.tt
```

```json
[{"body":{"c":[{"name":"高等数学","teacher":"王老师","room":"自强楼(教室01104)",
   "day":0,"start":1,"end":2,"weeks":"2-4","parity":"all"}],
  "t":"2026-08-31",
  "meta":{"source":"jwxt","fetchedAt":1760000000000,"count":29,"termStart":"2026-08-31","syncedAt":"2026-11-07T..."}},
 "meta":{"source":"jwxt","fetchedAt":1760000000000,"count":29,"termStart":"2026-08-31"},
 "updated_at":"2026-11-07T..."}]
```

- `body` 可能是 **jsonb 对象或 JSON 字符串**（CloudBase jsonb 返回对象；MP 兼容字符串）；必须含 `c` 数组 `[已核实 WEB:7047-7057; MP/cloud.js:1606-1618]`。
- 空行/格式错 → 回退 `GET https://tool.furina.fun/tt.json`（**不带身份头**；2026-11-07 起该域名已暂停，可只保留本机缓存）。

### E25 service_health（管理员，可选）

```http
GET {REST}service_health?select=id,name,kind,status,detail,meta,checked_at&order=id
```

- 非管理员**不要请求**（RLS 返回空；MP 也加了 `isAdmin` 双保险）`[已核实 MP/cloud.js:1578-1594]`。

---

## 3. 写接口

通用：POST body **必须是数组**（PostgREST 批量语义，即使一条）；URL 中 `id=eq.<value>` 的 value 要 `encodeURIComponent`。

### E5 发布作业 `[已核实 WEB:2010-2013; MP/cloud.js:505-513]`

```http
POST {REST}homework
Prefer: return=representation

[{"id":"<cbId: base36 时间戳+随机>","subject":"数学","content":"P42","due":"","date":"2026-11-08",
  "time":"23:59","author":"<昵称>","owner":"<sid>","attachments":"","done":false,"archived":false,"ts":1760000000000}]
```

- 返回 201 + 插入行数组；失败（离线）→ 行标 `_pending`，联网后 `resyncPending` 补发。
- id 生成规则与两端一致：`Date.now().toString(36) + Math.random().toString(36).slice(2,8)` `[已核实 WEB:1849; MP/cloud.js:209]`。

### E6 作业软删 / 标记（PATCH）

```http
PATCH {REST}homework?id=eq.<id>
Prefer: return=representation

{"archived":true}
```

- `anon` **无 DELETE 权限**，删除一律软删 `[已核实 CB:152-155]`。

### E9 管理员编辑/隐藏自动通知 `[已核实 MP/cloud.js:723-763; DDL override]`

```http
PATCH {REST}notices?id=eq.auto_<gid>
Content-Type: application/json

{"override_json":"{\"title\":\"新标题\",\"deadline\":null}","edited_at":1760000000000,"edited_by":"2024000001"}
```

```http
PATCH {REST}notices?id=eq.auto_<gid>
{"hidden":true,"edited_at":1760000000000,"edited_by":"2024000001"}
```

- `override_json` 白名单 `title,cat,summary,aiSummary,deadline,eventAt,expireAt,timeLabel`；`null` 表示清空覆盖。
- **不要** `return=representation`（MP 只判成功）；成功后在本地热补丁 + 清通知 TTL。

### E11 marks upsert `[已核实 WEB:2670-2673; MP/cloud.js:389-403]`

```http
POST {REST}marks
Prefer: return=representation,resolution=merge-duplicates

[{"id":"<sid>|ntdone|<gid>","seat":"<sid>","kind":"ntdone","ref":"<gid>","val":true,"ts":1760000000000}]
```

- 取消：同一行 `val:false`（不 DELETE）；RLS 校验 `seat = 本人`。

### E13 userdata upsert（先 GET 合并，不要盲写）`[已核实 WEB:3114-3140; MP/cloud.js:1211-1267]`

```http
POST {REST}userdata
Prefer: return=representation,resolution=merge-duplicates

[{"id":"<sid>","blob":"<merge 后的 v6 JSON 字符串>","ts":1760000000000}]
```

- 流程必须是 GET → merge → POST；**无基线时不要 push**（会用默认值覆盖云端）。

### E15/E16 fitrecs `[已核实 WEB:7698-7727; MP/cloud.js:766-808]`

```http
POST {REST}fitrecs
Prefer: return=representation,resolution=merge-duplicates

[{"id":"f_abc","acc":"<sid>","data":"{\"id\":\"f_abc\",...}","arch":false,"ts":1760000000000}]
```

```http
PATCH {REST}fitrecs?id=eq.<id>
{"arch":true}
```

### E17 visits 埋点 `[已核实 WEB:7898-7906]`

```http
POST {REST}visits

[{"id":"<sid>|<slot>|<rand>","slot":"2026-11-07-23","sid":"<sid>","name":"<姓名>","did":"<8~64 hex 设备码>","ts":1760000000000}]
```

- **不要 `Prefer: return=representation`**（历史 401 原因；写入后不回读）。
- 去重：同一账号同一小时槽只写一次（本机 `localStorage zq_visit_slot_<sid>` 记录 slot）`[已核实 WEB:7900-7903]`。

### E19 feedback `[已核实 WEB:4256-4261; MP/cloud.js:1388-1404]`

```http
POST {REST}feedback

[{"id":"<cbId>","content":"反馈内容","contact":"","acc":"<sid>","done":false,"ts":1760000000000}]
```

- `contact` 列保留但两端新提交均为空串（2026-11-05 起停收联系方式）`[已核实 MP/cloud.js:1387]`。

### E21 反馈标记已处理（管理员）

```http
PATCH {REST}feedback?id=eq.<id>
{"done":true}
```

---

## 4. 错误与边界

| 情形 | 表现 | 处理 |
|---|---|---|
| 无/过期 token | RLS 拒绝 → `401` + `{"code":"DATABASE_42501",...}`，或读返回 `[]`（策略决定）`[待确认 Q2]` | 401 → 清 token 回身份页；`[]` 时保留本机缓存 |
| 表不存在/路径错 | `404` | 视为接口不可用，降级本机缓存 |
| 写入被 RLS 拒 | `401`/`403` | 不重试；进入 outbox 稍后补发 |
| DELETE 任意表 | `401 DATABASE_42501` | 不用 DELETE |
| 负载过大（通知整表 ~2 MB / 附件） | 响应慢 | 通知只走「签名 + 整表」两级；附件按需解码 |
| 网络中断 | exception | 全部读写降级本机；启动/回前台/TTL 到点自动重试 |

---

## 5. 调用时序建议（Android）

```text
启动
 ├─ 有本地身份且 <29 天 → 进入主界面（先读 Room 缓存渲染）
 │    └─ 后台并发：E2 名单(6h TTL) / E10 marks / E4 作业 / E12 userdata / E24 课表(6h)
 ├─ 无身份 → 身份页 → E1 → 存 token → E2 → 进入主界面
 ├─ 回前台 / 30s 轮询（仅轻量）：E10 marks、E8 通知签名、按 TTL 刷 E4/E12
 └─ 用户操作 → 本地写入 + 标脏 → 防抖 1200ms → 对应写接口（失败进 outbox）
```

> 具体 TTL 表：`WEB:3173`（nt/hw 10 min，members 6 h，fit 5 min，marks 2 min，visits 10 min，tt 6 h，power 2 min）；userdata 60 s（`MP/cloud.js:60`，`SYNC-C:138-139`）。
