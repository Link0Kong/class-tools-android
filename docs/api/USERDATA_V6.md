# `docs/api/USERDATA_V6.md` — `userdata` v6 blob 同步契约（含 Kotlin 参考实现）

> 唯一真源：`archive/_sync_trash_20261105/_sync_task/CONTRACT.md`（SYNC-C）§3 + `web/tools/tests/_water_contract.md`（WATER-C）§2/§7 + 两端实现（`WEB:2745-3150`、`MP/utils/cloud.js:811-1384`、`MP/utils/privacy.js`）。
> 合并 oracle：`mp-class-tools-tests/_sync_fixtures/merge_ref.js`（逐字节等价基准）+ 同目录 fixtures。
> 标记：`[已核实]` / `[推断]` / `[待确认]`。

---

## 1. 规范 JSON（canonical shape）

### 1.1 顶层 14 键（**顺序固定**，合并输出逐字节依赖它）`[已核实 WATER-C:54; merge_ref.js:39-50]`

```jsonc
{
  "v": 6,
  "at": {                                     // 11 个 section 的最后修改时间（epoch ms；0 = 无改动）
    "homeTools": 0, "fit": 0, "draw": 0, "group": 0, "roster": 0,
    "hide": 0, "tt": 0, "prefs": 0, "nick": 0, "power": 0, "water": 0
  },
  "homeTools": [],
  "fit": {},
  "draw": {},
  "group": {},
  "roster": { "rosterSel": [], "rollSel": [], "rollSt": {}, "rollNames": "" },
  "hide": { "hwHide": {}, "ntHide": {} },
  "tt": { "ttView": "", "ttUseMine": false, "ttLocal": null, "termStart": "" },
  "prefs": { "hwRemind": {}, "ntSub": false, "ntSubAt": 0 },
  "nick": "",
  "power": { "sel": "", "at": 0, "low": 30 },
  "water": { "rooms": {} },
  "homeToolsAt": 0
}
```

- **键序**：`v, at, homeTools, fit, draw, group, roster, hide, tt, prefs, nick, power, water, homeToolsAt`。
  `at` 内部键序同 `SECTION_KEYS`；`homeToolsAt` 永远在**最后**（merge 循环结束后 `out.homeToolsAt = out.at.homeTools`）。
- `SECTION_KEYS = [homeTools, fit, draw, group, roster, hide, tt, prefs, nick, power, water]`（11 个，顺序即合并顺序）。

### 1.2 各 section 字段与默认值 `[已核实 MP/utils/privacy.js:18-31; WEB:2746-2759; merge_ref.js:8-20]`

| section | 默认值 | 字段说明 |
|---|---|---|
| `homeTools` | `[]` | 工具 key 数组，≤4，按「工具顺序」语义（首页工具栏的**用户选择集合**，不再自动补齐）。工具 key 取值见 WEB:3678-3688：`draw, tt, committee, hw, nt, ft, roll, stats(admin), diag(admin), water` + 工具页非 TOOLS 的 `all/fb/ug/power` 不可进 homeTools（由前端过滤）`[已核实 WEB:3745-3747]` |
| `fit` | `{}` | 体测录入当前输入：`sex("m"/"f"), grade("12"/"34"), h(""), w, vc, r50, sit, jump, pull, durM, durS`（值多为字符串；`UD_FIT_KEYS` 固定 11 键）`[已核实 WEB:2747, 2830-2835]` |
| `draw` | `{}` | 抽签参数：`start(1), end(70), count(10), dup(false), excl(""), sort("draw")`。`excl` 文本格式：逗号/中文逗号/空白分隔，支持区间 `5-7`、`5~7`、`5—7` `[已核实 WEB:6288-6297, 1768; MP/cloud.js:926-934]` |
| `group` | `{}` | 分组参数 + 名单选择：`source`（`prev`=上一步抽签 / `names`=选择名单）、`mode`（`groups`=按组数 / `pergroup`=按每组人数）、`n`(4)、`shuffle`(false)、`sel`（座号数组）。**注意两端本机词汇不同，blob 里统一用网页词汇**：MP 存 `source:pick/paste` → blob `names`；`mode:per` → blob `pergroup` `[已核实 MP/cloud.js:936-942, 1060-1068; WEB:2867, 2900-2907]` |
| `roster` | `{"rosterSel":[],"rollSel":[],"rollSt":{},"rollNames":""}` | 抽签参与名单/点名名单/点名到·假：`rosterSel/rollSel`=座号数组（去重升序 1–70），`rollSt`={索引:"in"/"ab"}（网页用中文"到"/"假"还是 in/ab 需按真实数据兼容——网页 `writeRollFromSel` 存的状态来自旧数据，可能是 `in/ab`，导出/渲染按 `in`/`ab` 处理；MP fixtures 见下）, `rollNames`=派生文本 `<seat> <name>` 换行 |
| `hide` | `{"hwHide":{},"ntHide":{}}` | 个人视图隐藏：`{rowId: true}`（作业/手动通知） |
| `tt` | `{"ttView":"","ttUseMine":false,"ttLocal":null,"termStart":""}` | 课表偏好：`ttView`=`week`/`day`；`ttUseMine`=是否用个人导入；`ttLocal`={c:[course],t:termStart} 或 null；`termStart`=`YYYY-MM-DD`。**MP 只写 `termStart`，其余原样保留** `[已核实 SYNC-C:102; MP/cloud.js:950-955]` |
| `prefs` | `{"hwRemind":{},"ntSub":false,"ntSubAt":0}` | 通知提醒偏好（MP 拥有）：`hwRemind`={作业 id: true}；`ntSub`；`ntSubAt`。可选 `toolOrder`（工具页顺序数组，只有用户拖过排序才写）。**网页不写 prefs**（原样保留云端值）`[已核实 SYNC-C:103; MP/cloud.js:956-965]` |
| `nick` | `""` | 网页发布作者昵称。**MP 不写** `[已核实 SYNC-C:104]` |
| `power` | `{"sel":"","at":0,"low":30}` | 关注宿舍：`sel`=roomverify，`at`=选择时间（与 `at.power` 镜像），`low`=低电阈值，合法档位 `[10,20,30,40,50,60,80,100]`，默认 30；非法值归 30 `[已核实 WEB:2750-2751; MP/cloud.js:830-839]; WATER-C:54` |
| `water` | `{"rooms":{}}` | 水量抄表：`rooms[roomverify] = {p:4|6, recs:[{m:"YYYY-MM", r:读数吨, ts:ms}]}`；recs 按月唯一、升序、≤120、r 2 位小数 `[已核实 WATER-C:31-58; MP/utils/water.js:11-14]` |

### 1.3 特例规则（Android 必须实现）

1. `homeToolsAt` 每次输出 = `at.homeTools`（旧客户端兼容镜像）。
2. `power.at`（值内）每次写 blob 前 = `at.power`（网页 `localView` 与 MP `udMirrorPower` 都做）。
3. `water` 值内**没有** at；water 的 LWW 时间只在 `at.water`。
4. `at` 中缺键按 0 处理；`at` 值只有「正数」有效（`num(v)=isFinite(v)&&v>0?v:0`）。
5. `tt.ttLocal` 必须允许显式 `null` 序列化（不要 `explicitNulls=false` 把它吃掉）。

---

## 2. 合并算法（伪代码 + 边界）

```text
fun merge(remoteIn: JsonObject?, localIn: JsonObject?): JsonObject
  remote = remoteIn
  if remote != null && remote["v"] == 5:
      remote = { v:6,
                 at: { homeTools: num(remote["homeToolsAt"]), fit: 0 },
                 homeTools: remote["homeTools"] if Array else absent,
                 fit: remote["fit"] if Object else absent }

  out = { v:6, at:{} }
  for k in SECTION_KEYS (11 个，固定顺序):
      ra = num(remote?.at?[k])            // 正数 ms，否则 0
      la = num(local?.at?[k])
      hasR = remote != null && remote[k] != null
      hasL = local != null && local[k] != null
      if hasR && (!hasL || ra >= la):      // 相等时云端赢 → 幂等收敛
          out[k] = deepClone(remote[k]); out.at[k] = ra
      else if hasL:
          out[k] = deepClone(local[k]);  out.at[k] = la
      else:
          out[k] = deepClone(DEFAULTS[k]); out.at[k] = 0
  out.homeToolsAt = out.at.homeTools
  return out
```

边界（`[已核实 SYNC-C:109-132; WEB:2774-2795; MP/cloud.js:887-904]`）：

| 情形 | 行为 |
|---|---|
| 云端 `v=5` | 先 `normalizeV5` 再合并；合并后 **必须写回 v6**（幂等；写回后 remote 变 v6 不再触发）。网页 `wasV5` 时 force push；MP `udPull` 拉取后立即 `udPush(true)` |
| 云端行不存在 / `blob` 空 / 坏 JSON | 视为 `remote=null`；**不要**用本机默认值覆盖（走 `localView` 的本机值，`at` 只对脏 section 抬高） |
| 字段缺省（某个 section 在 remote 有 `at` 但没值） | `hasR=false` → 用本机值 → 两边都无则默认值 + at 0 |
| 同时间戳 | 云端赢（`ra >= la`），保证合并幂等收敛 |
| 合并顺序无关性 | 同一对输入，无论谁先，最终每 section 赢家一致（逐字节一致由 fixture 保证） |

---

## 3. 本机视图（localView）与「抢时间戳」

「无基线」时的关键设计（Android 必须照做，否则会覆盖云端/丢用户选择）`[已核实 WEB:2851-2869, 2896-2908, 3013-3031, 3074-3113; MP/cloud.js:916-1004, 1165-1206]`：

```text
localView(remote, forceKeys):
    base = 最近一次合并成功的基线（本机缓存 blob）
    cur  = 本机当前各 section 值
    for k in SECTION_KEYS:
        val = (k == prefs) ? base.prefs : cur[k]      // 不拥有的 section 从基线带
        ba  = base?.at?[k] ?? 0
        dirty = base == null ? false : !valueEquals(base[k], val)
        if k in forceKeys: dirty = true
        la = ba
        if dirty: la = max(now, ba + 1, remote?.at?[k] + 1)
        out[k] = val; out.at[k] = la
    if k == power: out.power.at = out.at.power
    out.homeToolsAt = out.at.homeTools
```

- `forceKeys` 用途：v5 迁移后强制 homeTools/fit 落本机；用户刚操作（如选关注宿舍）但尚无基线时保证「本机这次改动赢」。
- **无基线且无用户改动**：不 push（避免默认值污染云端）；先 pull 建基线。
- 网页对 power/water 有专门强同步入口（`UD.requestPowerSync` / `WATER.requestSync`）：选完立刻落云 + 断网重试；Android 建议同样实现「关键偏好立即 push + 失败标记」。

### 3.1 脏检测与防抖

- 脏 = 本机当前值与**最近合并基线**逐 section 深比较不等（键序无关：网页用 `JSON.stringify` 比较——要求本机对象键序稳定；MP 用键排序的 canonical 比较更稳，Android 建议用 `JsonObject` 深度相等，键序无关）。
- 防抖 **1200 ms**；页面/应用隐藏时 `flush` 立即冲刷（取消定时器，有脏才发）。
- 拉取 TTL **60 s**（`lastPull` 持久化）；启动/回前台手动「立即同步」force 跳过 TTL。

---

## 4. 关闭同步 / 清除云端 / 抑制期

`[已核实 MP/cloud.js:1118-1163, 1323-1384; SYNC-C:148]`（网页无 cloudOff 开关）

| 机制 | 行为 |
|---|---|
| `cloudOff=true` | 不拉不推；marks 写入进本机队列；恢复时先 `flushPending` 再 `cloudPull(true)` |
| 清除云端 | ① `POST userdata` 空 v6 blob（`privacy.emptyV6()`：v6 + at 全 0 + 默认 section）② GET 本人 marks → 全部 upsert `val:false` ③ GET 本人 fitrecs（arch=false）→ 逐条 PATCH `arch:true`。成功后设 `udHoldUntil = now + 30d`、清本机 marks/pend → **默认不自动回推**，直到用户下次显式改动同步内容才解除 |
| 本机数据 | 清除云端不动本机数据（用户可另行「清除本机」） |
| `visits/feedback` | 无法删除（无 DELETE 权限），隐私页如实说明 |

---

## 5. Kotlin / kotlinx.serialization 参考实现

### 5.1 依赖与 Json 配置

```kotlin
// build.gradle.kts
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

val BlobJson = Json {
    encodeDefaults = true      // 需要输出 at 全部 11 键、默认 section（与网页/MP 逐字节一致的形状）
    explicitNulls = true       // tt.ttLocal 必须能显式输出 null
    ignoreUnknownKeys = true   // 前向兼容：未来新增 section 不崩
    prettyPrint = false        // blob 是压缩 JSON 字符串
}
```

> 二进制/字节一致性建议：**合并用 `JsonObject` 而不是 data class**（保证键顺序、保留未知键、避免 data class 缺省字段被丢弃），业务读写再用强类型 data class（见 `api/KOTLIN_MODELS.md`）。

### 5.2 合并实现（与 `merge_ref.js` 等价）

```kotlin
import kotlinx.serialization.json.*

object UserdataMerge {
    val SECTION_KEYS = listOf(
        "homeTools","fit","draw","group","roster","hide","tt","prefs","nick","power","water"
    )
    private fun num(v: JsonElement?): Long {
        val n = (v as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return 0
        return if (n.isFinite() && n > 0) n.toLong() else 0
    }
    fun merge(remoteIn: JsonObject?, localIn: JsonObject?): JsonObject {
        var remote = remoteIn
        if (remote != null && num(remote["v"]) == 5L) remote = normalizeV5(remote)
        val out = LinkedHashMap<String, JsonElement>()
        val at = LinkedHashMap<String, JsonElement>()
        out["v"] = JsonPrimitive(6)
        for (k in SECTION_KEYS) {
            val ra = num(remote?.get("at")?.jsonObjectOrNull()?.get(k))
            val la = num(localIn?.get("at")?.jsonObjectOrNull()?.get(k))
            val hasR = remote?.get(k) != null && remote[k] !is JsonNull
            val hasL = localIn?.get(k) != null && localIn[k] !is JsonNull
            when {
                hasR && (!hasL || ra >= la) -> { out[k] = remote!![k]!!; at[k] = JsonPrimitive(ra) }
                hasL -> { out[k] = localIn!![k]!!; at[k] = JsonPrimitive(la) }
                else -> { out[k] = Defaults.of(k); at[k] = JsonPrimitive(0) }
            }
        }
        // 重新按 canonical 顺序组装：v, at, sections..., homeToolsAt
        val canonical = buildJsonObject {
            put("v", JsonPrimitive(6))
            put("at", JsonObject(at))
            for (k in SECTION_KEYS) put(k, out[k]!!)
            put("homeToolsAt", JsonPrimitive(num(at["homeTools"])))
        }
        return canonical
    }
    fun normalizeV5(old: JsonObject): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(6))
        put("at", buildJsonObject {
            put("homeTools", JsonPrimitive(num(old["homeToolsAt"])))
            put("fit", JsonPrimitive(0))
        })
        old["homeTools"]?.let { put("homeTools", it) }
        old["fit"]?.let { put("fit", it) }
    }
    // 注意：JsonObject 的键序由构造顺序决定；kotlinx 的 JsonObject 内部 LinkedHashMap 保序。
}
```

> 若采用 `JsonObject` 组装，`buildJsonObject` 的插入顺序即输出顺序，满足 canonical 键序；`deepClone` 可用 `element.jsonObject` 直接复用（不可变）。

### 5.3 localView + dirty 参考

```kotlin
fun localView(remote: JsonObject?, base: JsonObject?, cur: JsonObject,
              forceKeys: Set<String>, now: Long, owned: Set<String>): JsonObject {
    val at = LinkedHashMap<String, JsonElement>()
    val out = LinkedHashMap<String, JsonElement>()
    for (k in SECTION_KEYS) {
        val valEl: JsonElement = if (k == "prefs")
            base?.get("prefs") ?: Defaults.of("prefs") else cur[k] ?: Defaults.of(k)
        val ba = num(base?.get("at")?.jsonObjectOrNull()?.get(k))
        var dirty = base != null && base[k] != valEl            // 需按深比较；JsonElement 用 == 即可
        if (k in forceKeys) dirty = true
        val la = if (dirty) maxOf(now, ba + 1, num(remote?.get("at")?.jsonObjectOrNull()?.get(k)) + 1) else ba
        out[k] = valEl; at[k] = JsonPrimitive(la)
    }
    val power = out["power"]?.jsonObject?.toMutableMap()
    if (power != null) { power["at"] = JsonPrimitive(num(at["power"])); out["power"] = JsonObject(power) }
    return buildJsonObject {
        put("v", JsonPrimitive(6)); put("at", JsonObject(at))
        for (k in SECTION_KEYS) put(k, out[k]!!)
        put("homeToolsAt", JsonPrimitive(num(at["homeTools"])))
    }
}
```

> 真正落盘时建议同时持久化「最近合并基线」`udCache`（与网页 `zq_udcache_<sid>`、MP `store.udCache` 对应），否则每次冷启动都要 pull 才能判脏。

### 5.4 push / pull 参考流程

```text
suspend fun pull(force: Boolean): PullResult {
    if (!force && now - lastPullAt < 60_000) return Fresh
    val remote = GET userdata?select=*&id=eq.<sid> → blob JSON（0 行/坏 JSON → null）
    val wasV5 = remote?.get("v") == 5
    val base = cacheStore.read()                    // 最近合并基线
    val wasDirty = if (base != null) dirtySections(curStore.snapshot()) else emptyList()
    val forceKeys = if (pendingKeys.isNotEmpty()) pendingKeys else null
    val local = localView(remote, base, curStore.snapshot(), forceKeys ?: emptySet(), now, owned)
    val merged = merge(remote, local)
    applyToLocal(merged)                            // 只改参数/选择，不重置进行中的结果
    cacheStore.write(merged); lastPullAt = now
    if (wasV5 || wasDirty.isNotEmpty() || dirtySections(...).isNotEmpty() || pending) push(force = true)
    return Changed
}

suspend fun push(force: Boolean = false, forceKeys: Set<String>? = null) {
    if (base == null && forceKeys == null) return          // 无基线不盲推
    val remote = GET userdata?select=*&id=eq.<sid>
    val local = localView(remote, cacheStore.read(), curStore.snapshot(), forceKeys ?: emptySet(), now, owned)
    val merged = merge(remote, local)
    POST userdata  body=[{id:<sid>, blob: BlobJson.encodeToString(merged), ts:now}]
         Prefer: return=representation,resolution=merge-duplicates
    cacheStore.write(merged); pending = false
}
```

---

## 6. 测试向量（用仓库 fixtures 对拍）

fixtures 目录：`mp-class-tools-tests/_sync_fixtures/`（Android 单测应把这几个文件拷进 `test/resources` 做 golden test）。

| fixture | 用途 |
|---|---|
| `userdata_v6_web.json` | 网页写的 blob（含全部 section + 顶层镜像） |
| `userdata_v6_mp.json` | 小程序写的 blob（含 `water`、`prefs`、`power.low`） |
| `legacy_v5.json` | 旧 v5 blob：`{v:5, fit:{...}, homeTools:[...], homeToolsAt:1762200000000}` |
| `expected_v5_migrate.json` | v5 迁移 + merge 的期望输出（v6，`at.homeTools=1762200000000`，fit 保留，其余默认） |
| `expected_merge_web_mp.json` / `expected_merge_mp_web.json` | 双向合并期望（键序、tie-break） |
| `marks_ntdone_rows.json` | `ntdone` marks 行样例（含 true/false） |
| `merge_ref.js` | 参考实现（Node，64 行） |

**最低验收**：
1. `merge(legacy_v5, {at:{},fit:{sex:"m"}})` 与 `expected_v5_migrate.json` 深度相等，且 canonical 键序一致；
2. `merge(mp, web)` 与 `expected_merge_web_mp.json` 深度相等；`merge(web, mp)` 与 `expected_merge_mp_web.json` 深度相等；
3. 幂等：`merge(x, x) == x`（对 v6 输入）；
4. 同 ts 冲突：构造两边 at 相同、值不同 → 云端值胜出；
5. 坏 JSON/空行：远端 null 时不覆盖本机、不 panic。

---

## 7. 数据落盘映射（Android 建议）

| 云端 | 网页本机 | MP 本机 | Android 建议 |
|---|---|---|---|
| `userdata.blob` | `localStorage.zq_udcache_<sid>`（基线）+ 各业务字段在 `zq_tools_v2_<sid>` | `store.udCache` + 各字段 | Room `userdata_cache(sid, blob, pulledAt)` + `outbox_ud(sid, dirtyKeys)` |
| `marks` | `zq_marks_cache_<sid>`（Array） | `store.hwDone/ntPin/ntDone/markPend` | Room `marks` + `marks_outbox` |
| 身份 | `zq_class_identity` | `zq_class_identity` | 加密偏好（同键名可迁移网页导出？不需要） |
| 通知签名 | IndexedDB `zq_cache_v1`（按 sid 分桶，`sig`） | `utils/cache.js`（分块） | Room `notices_cache(sid, sig, fetchedAt, payload)` |
