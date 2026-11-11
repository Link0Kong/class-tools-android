# `docs/api/NOTICES.md` — 通知数据模型、状态机与 Android 原生渲染方案

> 数据来源：`public.notices`（行模型见主文档 §3/§6）；自动通知组 JSON 在 `body = "[[CLASS_NOTICE_V1]]" + JSON`。
> 状态机/时间口径唯一真源：`mp-class-tools/utils/notice.js`（589 行，与网页通知板同口径）、`MP/pages/nt/nt.js:154-333`。
> 标记：`[已核实]` / `[推断]`。

---

## 1. 行模型（复述关键点）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | text PK | 自动通知 = `auto_<gid>`；人工通知 = 任意 id |
| `title` | text not null | 人工通知标题；自动通知 body 里也有 title |
| `body` | text | 人工通知正文 / 自动通知 `[[CLASS_NOTICE_V1]]<JSON>` |
| `pin` / `imp` | bool | 前端忽略 pin（置顶走 marks）；`imp` 显示「重要」 |
| `author` / `owner` | text | 发布者昵称 / 学号（删除权限判断，历史兼容） |
| `attachments` | text | JSON 字符串 `[{n,t,s,d}]`（人工通知附件） |
| `archived` | bool | 软删；查询恒带 `archived=eq.false` |
| `ts` | bigint | 排序/增量 |
| `audience` | text | `all`（默认）/`committee`/`league`/`sid:<学号>`/`admin`，**服务端 RLS 过滤** |
| `override_json` | text | 管理员编辑覆盖（JSON 字符串，白名单见 §4） |
| `hidden` | bool | 管理员软删（列表隐藏，可恢复） |
| `edited_at` / `edited_by` | bigint / text | 最后编辑时间/人 |

`[已核实 schema.sql:47-72; DDL 2026-11-05_notice_admin_override.sql; CB:367-464]`

**权限**：`notices` 的 `nt_insert/nt_update` 已收紧为仅管理员；普通已认证用户只读 `[已核实 CB:401]`。Android v1 不提供发布/编辑通知入口（§11 Q7）。

---

## 2. 拉取策略（两阶段，避免 2 MB）

```text
每次进入通知页 / TTL(10min) 到期 / 回前台：
  1) GET notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false   // ~2KB
  2) sig = rows.join("|")，每行 "id:ts:hidden:edited_at"
  3) sig == 本地缓存 sig → 直接用缓存（0 次大请求）
     否则 GET notices?select=*&order=ts.asc,id.asc&archived=eq.false → 重建缓存 + 解析
```

`[已核实 WEB:1856-1865, 2599-2650; MP/cloud.js:534-687]`
- 管理员编辑/隐藏**不改 ts**，所以签名必须含 `hidden` 与 `edited_at`（否则永远命中旧缓存）。
- 首次无缓存 → 直接整表；缓存持久化（网页 IndexedDB 按 sid 分桶 + 世代号；MP `utils/cache.js` 分块）`[已核实 WEB:3190-3264; MP/utils/cache.js]`。
- 解析：`body.startsWith("[[CLASS_NOTICE_V1]]")` → 去掉前缀 JSON.parse 成 group；否则归「人工通知」列表。
- group 附加内部字段（不落库）：`_hidden`、`_override`（解析后的对象）、`_editedAt`、`_editedBy`。

---

## 3. 状态机 `statusOf(group)`（必须移植）

来源：`MP/utils/notice.js:20-162`（网页 `anStatus` 是简化版，Android 按 MP 完整版）。

### 3.1 时间字段优先级

```text
signupStart/signupEnd（报名窗口）> deadline（真实截止，extensions 最后一次延期优先）
> eventAt（活动/事件时间）> 无时间
```

- `expireAt` **只用于「活动类自动归档」**（`event` 的结束 = `expireAt || eventAt`），**绝不当作截止时间展示**。
- `recurrence`（长期安排）存在时**永不按 deadline 判过期**。
- `extensions[]` 取最后一个的 `deadline` 作为真实截止；`deadlineAction` / `eventAction` 是动作文案（如「报名截止」）。

### 3.2 主显示时间 `whenInfo(g)`（chip/排序用）

```text
若 signup 存在:
    now < signupStart → {ts:signupStart, action:"报名开始"}
    否则若有 signupEnd → {ts:signupEnd, action:"报名截止"}
否则若真实截止 → {ts:deadline, action: 精简(deadlineAction || "截止")}
否则若 eventAt → {ts:eventAt, action: 精简(eventAction || "开始")}
否则 null
```

### 3.3 `statusOf` 决策树

```text
if recurrence: return {key:"recurring", label:"长期安排 · <label>", cls:"recurring", expired:false}

real = deadlineOf(g); archiveAt = expireAt; eventAt = eventAt
if real != null && now > real && g.alert: return {key:"alert", label:"已截止 · 有新名单", cls:"alert", expired:false}

// 报名优先
if signup 存在（且不是「只有 start 且已开始」）:
    未开始 → bucket(signupStart, "报名开始")
    窗口内 → bucket(signupEnd, "报名截止")
    已结束 → {key:"expired", label:"<fmtWhen(end)> 报名截止", expired:true}

if real != null:
    now > real → {key: ext?"extended_past":"expired", label:"<fmtWhen(real)> <action>", cls:"expired", expired:true}
    否则 → bucket(real, action)；有 ext 时 key="extended", cls="extended"

if eventAt != null:
    end = archiveAt || eventAt
    now > end → {key:"expired", label:"<fmtWhen(eventAt)> <action>", expired:true}
    否则 → {key:"active", label:..., expired:false}

return {key:"none", label:"无时间", cls:"none", expired:false}
```

`bucket(ts, action)`（紧迫度分桶，同时生成 label）：

```text
label = fmtWhen(ts) + " " + action
left = ts - now
left < 1h  → {key:"urgent", cls:"urgent"}
left < 24h → {key:"today",  cls:"urgent"}
left < 72h → {key:"soon",   cls:"active"}
else       → {key:"active", cls:"active"}
```

`fmtWhen(ts)`：已过 → `MM-DD HH:MM`；<1h → `N 分钟后`；<24h → `N 小时后`；<7d → `N 天后`；≥7d → `MM-DD HH:MM`（**禁止「今天/明天 + 钟点」混排**）。

`conciseAction(a)`：≤6 字；长词映射（`报送姓名截止→报送截止`、`集合点名→点名`、`活动开始→开始`、`报名已截止→报名截止` 等）。

### 3.4 简报统计与最紧急一条

```text
statOf(all, done):
  expired 计数：statusOf(g).expired
  跳过 recurrence；跳过 actionable===false；跳过 done[g.id]
  pending++；whenOf(g) 在未来：left<=24h → urgent++；left<=7d → week++

mostUrgent(all, done):
  过滤 !expired && actionable && !done && !recurrence
  排序：alert 优先，然后 whenOf 升序（无时间排后）
```

`[已核实 MP/utils/notice.js:302-348]`

### 3.5 长期安排 `recurrence`

```jsonc
{"freq":"weekly|monthly_weeks|monthly_day|daily",
 "weekday":0..6,                 // 0=周日（JS getDay 口径）
 "weeks":[1,2,3], "day":1..28, "time":"HH:MM",
 "startAt":"ISO", "label":"每月前三周 周三 17:30"}
```

- 「下次」时间**本地按 now 计算**（不依赖云端下发）：daily 60 天窗口；weekly 按 weekday；monthly_day 只支持 1–28；monthly_weeks 取每月第 n 次出现的 weekday（`nthWeekdayOfMonth`）。`nextOf`/`nextTextOf` 见 `MP/utils/notice.js:214-281`。
- 展示：`长期安排 · <label> · 下次 MM-DD 周X HH:MM`；`actionable=false` 不计待处理。

---

## 4. 管理员编辑覆盖（override）

- 白名单：`title, cat, summary, aiSummary, deadline, eventAt, expireAt, timeLabel` `[已核实 MP/cloud.js:724-727; MP/utils/notice.js:283]`。
- 应用规则：先 `applyOverride(rawG)`（值 `null`/`""` 表示删除该字段 → 回落云端原值；否则覆盖），再做状态/渲染计算。`[已核实 MP/utils/notice.js:283-294]`
- Android 流程：本地解析 group → 套用 `_override` → 计算卡片 → 列表展示；管理员「编辑」保存 = PATCH `notices?id=eq.auto_<gid>`（见 `api/ENDPOINTS.md` E9），成功后本地热补丁 `_override/_editedAt/_editedBy` 并清通知 TTL（下一次签名校验会重建）。
- `hidden=true`：非管理员一律过滤；管理员在「已隐藏」筛选里可恢复。

---

## 5. Android 原生渲染映射（MP 已验证）

```
卡片模型：
  title      = absRelDates(group.title, anchor)      // 相对日期替换（可选移植）
  cat        = group.cat ?: "通知"                    // 分类 chip（筛选）
  status     = statusOf(applyOverride(group))         // label / cls / expired
  source     = group.source                           // 来源群/来源
  sender     = group.senderName/senderRole/senderBadge
  summary    = group.aiSummary
  points     = group.aiPoints[0..2]
  segs       = compactSegs(group.aiSegments)          // 10 类归并为 ≤6 条 {type,text}
  quotes     = items 中 kind=text/follow → {label, fromName, fromRole, time, text}（最多 2 段；有 aiSummary 时不展示）
  items      = 按 kind 分派：
                 file   → {name, extKind, size, hasUrl, pending, archiveInfo}
                 form   → {name, statusText(报名中/已截止), closeText, note, hasUrl}
                 link/mini/article → {name, title, account, host, hasUrl}
                 merged → {title, count, messages[0..7]}
  imgs       = items 中 kind=img → {caption, ocr}（本体 src 在 items 里，按需解码）
  recurring  → recurLabel + nextText
  updated    = group.updatedAt || row._editedAt       // 显示「已更新」
  hidden     = row._hidden
  canDone    = !expired && !recurring
```

`[已核实 MP/pages/nt/nt.js:157-333]`

**附件处理**（2026-11-10 起为「data URI ↔ 签名 URL」双形态，契约唯一真源 `web/attachments/CONTRACT.md`）：
- `items[].src`/`url` 旧数据是 `data:<mime>;base64,...`；外置迁移后是签名 URL
  `https://<host>/att/<id>?exp=<unix秒>&sig=<hex64>`（备案期调试可能 `http://<ip>/att/...`）。
  **只判断 `^https?://`，不写死域名**；GET 直出字节、无鉴权头、内容不可变（`Cache-Control: immutable`）。
- 图片（`kind=img`）：data URI 走 `ApiNoticeRawAttachmentLoader` 按需拉原始行 + `BitmapFactory` 采样解码；
  URL 走 `container.pptImageLoader`（`data/ppt` 的 OkHttp + BitmapFactory + 内存 LRU），失败占位不崩。
- 文件（`kind=file`）：URL 用 OkHttp 下载到 `cacheDir/notice_attachments/<sha256前16>/<展示名>`
  （上限 20MB；同 key 命中磁盘缓存不重下）→ FileProvider content Uri + `ACTION_VIEW`（无处理器退回 `ACTION_SEND`）；
  data URI 旧数据仍只展示元信息（行为不变）；`file` 无 url 且无 archive 识别 → 显示「待获取」。
- 大小：URL 形态下不能从 base64 估算体积 → 优先 `items[].size`（人类可读，如 `82 KB`），否则显示「附件」，
  **禁止显示 0 B**。
- `items[].archive = {status,count,summary,files:[{name,kind,size,textLen,url?}]}` 是压缩包内容识别结果
  （只展示元数据 + 摘要 ≤120 字）；`files[].url` 外置后是签名 URL，可逐项「下载 → 系统打开」，无 URL 只展示元信息。
- 内联 data URI 体积上限 10 MB base64（`NoticeAttachmentParser.decodeBase64Payload`），超限拒绝；外置 URL 下载上限 20MB。
- 实现：`data/notices/NoticeAttachmentSource`（形态判定/文件名/MIME）、`data/notices/NoticeAttachmentFiles`
  （下载/解码编排，纯 JVM 可测）、`data/notices/NoticeAttachmentOpen`（cacheDir sink + FileProvider）、
  `ui/screens/notices/NoticeAttachmentPreview`（缩略图/大图/文件打开行）。旧数据 data URI 全程向后兼容。

**`absRelDates` 相对日期替换（建议移植，纯显示层）** `[已核实 MP/utils/notice.js:350-547]`：
- 锚点 = 同一卡片**第一条带 time 的 item** 的发送时间（`anchorOf`）；无锚点则原样返回。
- 规则：今天/明天/后天/今早/明晚 → `MM-DD`（保留早晚）；本周X/下周X → 具体日期；裸「周X」→ 最近未来该星期；裸「N号/N日」→ 仅在有上午/下午/比赛/点名等语境时替换（避免「5号楼」「42号同学」误伤）；裸时段（上午/中午/下午/晚上/凌晨）→ 前缀锚点日期；绝对日期括号内相对星期去重。
- 解析失败/无锚点 → 原文返回，不抛错。

---

## 6. WebView 兜底建议（不做主路径）

仅以下场景考虑 WebView（加载本地 HTML 字符串，不请求外网）：
1. `items[].kind` 出现 App 未实现的未知类型；
2. 未来通知 body 引入富 HTML（当前没有）；
3. 需要展示 PDF 等系统 WebView 更好支持的文件（也可用系统 PDF 组件替代）。

不要用 WebView 加载 `tool.furina.fun` 整页：网页通知页是 20,000 px 高 iframe + 模板 b64 嵌入，移动端性能与离线都差，且需要网页端身份注入 `[已核实 WEB:2470-2506; AGENTS §4b-2]`。

---

## 7. 与其他状态的耦合

- 「已处理」= `marks kind='ntdone'`（§主文档 §5）；拉取 marks 后：`ntDone[gid]=val`，卡片状态显示「已处理」，pending 统计排除 `[已核实 WEB:2675-2703; MP/cloud.js:365-369]`。
- 「置顶」`marks kind='nt'` 只对人工通知有意义（自动通知列表按时间/紧急度排序）`[已核实 SYNC-C:24-25]`。
- 通知页刷新保位：Android 不需要（原生 ScrollState 自带），但**切换筛选/分类时不要重置滚动到顶部**（网页/MP 均保留滚动语义）`[推断-体验一致性]`。
