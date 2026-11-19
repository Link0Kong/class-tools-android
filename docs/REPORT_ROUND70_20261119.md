# ROUND 70 · 2026-11-19 · 通知列表 P0 收口（light meta 优先）+ release 4 个 ECS 子系统可用性修复

> 承接 Lead 2026-11-19 网络审计结论：通知页慢/错误率高的主因是 Android 仍在 `notices?select=*` 分页拉整表
> （每页 ~0.55MB、后台自动翻完 ~28 页 / 14MB、单页超时 40s + 重试）；另外 release 的账号/云空间/更新/地图
> 仍指向 NXDOMAIN 的 `tool.furina.fun`。
> **未提交 git、未改版本号（1.0.0-beta7 / vc90）、未加依赖、未碰 key/keystore。**

---

## 1. 通知列表：`rpc/class_notices_meta` 优先（P0）

### 1.1 契约（与网页/小程序同一口径）

服务端 `deploy/cloudbase/2026-11-17_notice_meta.sql`（网页仓，只读参考）：

- `notice_lite_body(body)`：把 `[[CLASS_NOTICE_V1]]` JSON 里 **`items[].url/src`** 与
  **`archive.files[].url/src`** 的内联 `data:` 置空，并在对象上写 `lazy=true`（标题/时间/摘要/文件名/大小/
  压缩包清单原样保留）——整表 14MB → **~0.3MB**；
- `class_notices_meta()`：返回**全部** `archived=false` 行（`ts asc,id asc`），与 `notices?select=*` 同构，
  `SECURITY DEFINER` + `auth_ok()`（**需要有效班级 token**）；
- 网页请求形态：`POST {CB}rpc/class_notices_meta`，body `{}`；打不开时回退旧整表；
  `GRANT EXECUTE ... TO anon`（但函数内部仍要求 `auth_ok()`）。

### 1.2 Android 实现（`data/notices/NoticesRepository.kt`）

```text
load(force):
  1) GET notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc   // 签名 ~2KB（保留原逻辑）
  2) 签名一致 → 用缓存
  3) POST rpc/class_notices_meta  body={}   authenticated=true, notifyTokenInvalid=false
       ├─ 200 且是数组 → resetPages + appendPage(全部行) → hasMore=false（一次拿全量，不再分页）
       └─ 400/401/403/404/405/501 → metaAvailable=false → 回退旧分页链路（首屏一页 + 用户按需加载）
          └─ 其它失败（网络/超时/坏 JSON）→ 有缓存用缓存，无缓存返回 Failure（不偷偷去拉 14MB）
```

- **回退后不会每次重复探测**：`metaAvailable=false` 后同一进程一直走旧链路；`force`（下拉刷新/重试）
  允许再探测一次，云端部署完 RPC 后**无需升级 App** 即可自动切回 light meta。
- **401/403 不清会话**：`ApiClient` 新增 `notifyTokenInvalid`（默认 true，保持既有语义）；light meta 探测
  传 `false`，避免「接口不存在/权限口径不同」把用户踢回身份页；主链路（签名/分页正文）语义不变。
- **取消「后台自动翻完所有页」**：删除 `NoticesAutoLoader`（含 6 项单测）与其在 `NoticesScreen` 的循环，
  只剩用户下拉刷新或底部 **「加载更早的通知（还有 N 条）」** 按需加载一页；light meta 链路下
  `hasMore=false`，这一行不出现。OPPO 推送定位失败时也**只加载一页**再放弃。
- **超时 40s → 25s**，超时最多重试 1 次（`timeoutRetries=1`，网络失败仍由 ApiClient 重试 1 次，均可预期、
  不无限重试）。light meta ~0.3MB 在 25s 内足够。

### 1.3 懒加载：点开才拉单行完整 body

- `NoticeItemMeta.Image/File`、`NoticeArchiveFile` 新增 `lazy` 字段（`NoticeBodyParser` 从 item 的
  `lazy` 读取）；`NoticeEntry.hasLazyContent` 汇总「有被服务端置空的内容」。
- 新增仓库方法：
  - `loadRow(rowId)`：`GET notices?select=*&id=eq.<rowId>` → `NoticeRow?`（单行完整 body）；
  - `hydrateRow(rowId)`：拉单行 → 重新解析 → **替换缓存里同 rowId 的条目** → 返回新 `NoticesData`；
    行不在列表里（已删除/被过滤）时返回当前缓存，不打断阅读。
- UI（`NoticesScreen`）在**打开详情时**触发一次 `hydrateRow`（`selectedKey` 变化 → `entry.hasLazyContent`），
  同一 rowId 只尝试一次；成功后图片有内联/外置内容、文件有签名 URL（可点开），**重开不再请求**；
  失败只 Snackbar 提示一次，图片仍有逐张按需加载兜底（既有 `ApiNoticeRawAttachmentLoader`），
  文件继续显示「待获取」。**启动时不预取任何附件**。
- 人工通知的 `attachments` 列没有被 light 化（RPC 只重写 `body`），故手动通知行为完全不变。

---

## 2. release 4 个 ECS 子系统：NXDOMAIN → 可用明文 IP（`ecsProfile` 档位）

### 2.1 问题

`tool.furina.fun` 备案暂停（DNS NXDOMAIN），而 release 之前把这 5 个地址写成 `https://tool.furina.fun/...`：

| BuildConfig | 之前（release） | 现在（默认 `ecsProfile=ecsIp`） | 影响 |
|---|---|---|---|
| `ACCOUNT_BASE_URL` | `https://tool.furina.fun/account` ❌ | **`http://121.196.47.2/account`** ✅ | OPPO 快捷登录/绑定 |
| `CLOUDSPACE_BASE_URL` | `https://tool.furina.fun/drop` ❌ | **`http://121.196.47.2/drop`** ✅ | 文件传输/云空间 |
| `UPDATE_BASE_URL` | `https://tool.furina.fun/android/latest.json` ❌ | **`http://121.196.47.2/android/latest.json`** ✅ | 更新检查 |
| `MAP_BASE_URL` | `https://tool.furina.fun/map/campus_final_25d.html` ❌ | **`http://121.196.47.2/map/campus_final_25d.html`** ✅ | 在线地图（内置离线地图兜底不变） |
| `WEB_BASE_URL` | `https://tool.furina.fun` ❌ | **`http://121.196.47.2`** ✅ | PPT 媒体缓存 + 法律全文（ROUND69） |
| `REST_BASE_URL` | CloudBase 网关（HTTPS）✅ | **不变** | 登录/通知/业务数据 |

### 2.2 改法（低风险、可一键回滚）

1. `app/build.gradle.kts`：新增 **`ct.ecsProfile`**（`ecsIp`（默认）| `selfhosted`），5 个 ECS 地址由它推导；
   单地址仍可用 `ct.accountBaseUrlRelease` / `ct.cloudspaceBaseUrlRelease` / `ct.updateBaseUrlRelease` /
   `ct.mapBaseUrlRelease` / `ct.webBaseUrlRelease` 覆盖（新增，之前只有 Debug 版）。
2. `app/src/main/res/xml/network_security_config.xml`：release 之前是**全面禁明文**，即使改了地址也会被系统拦。
   现**只放行 `121.196.47.2` 这一个固定 IP**（其余域名仍禁明文，教务 `210.34.74.77` 维持原样）；
   debug 有自己的同名资源（本来已放行该 IP），不受影响。
3. `:app:verifyReleaseEndpoints`（发布闸门）按档位校验：
   - **REST 永远要求 https 且不含明文 IP**（CloudBase 或备案后的域名）；
   - `ecsProfile=ecsIp` → 5 个 ECS 地址必须落在 `http://121.196.47.2[/...]`，并打印 WARN；
   - `ecsProfile=selfhosted` → 必须 https 且不含明文 IP（备案恢复后的严格口径）。
4. `gradle.properties` 增加档位说明与默认值（留空 = `ecsIp`）。

### 2.3 备案 + HTTPS 恢复后怎么回滚

```cmd
.\gradlew.bat :app:verifyReleaseEndpoints -Pct.ecsProfile=selfhosted   :: 先验证闸门通过
.\gradlew.bat :app:assembleRelease -Pct.ecsProfile=selfhosted          :: 或者把 build.gradle.kts 默认值改回 selfhosted
```
恢复后建议同时删除 `network_security_config.xml` 里那条 IP 明文豁免（注释已写明）。

> 这是**有意的、范围最小的让步**：只为一个由同一运营者控制的固定 IP 开明文，REST 仍走 HTTPS，
> 其余域名维持「禁明文」。若 Lead 认为 release 不允许任何明文，可把默认改成 `selfhosted`
> ——代价是这 5 个功能在备案前不可用（现状即如此）。

---

## 3. 401/403 分类（按要求：改动小则保留，写建议）

- **主链路语义保留**：`ApiClient.interpretResponse` 对 401/403 仍返回 `ApiError.TokenInvalid` 并回调
  `onTokenInvalid`（清会话回身份页）——这是既有契约，未改。
- 仅给**探测类**调用加了 `notifyTokenInvalid`（默认 true）。当前只有 light meta 探测用 false。
- **建议（未做）**：`ApiError.TokenInvalid` 现在无法区分「token 过期」与「该接口权限不足」，若后续还有
  类似可选接口，建议把 403 细分为 `ApiError.Forbidden`（不清会话、只禁用该功能），需要同时改
  `AppContainer`/数据层错误映射与相关测试，属协议级调整。

---

## 4. 测试与构建

| 步骤 | 命令（`JAVA_HOME=C:\AndroidToolchain\jdk17`） | 结果 |
|---|---|---|
| 基线（本轮开始前） | `:app:testDebugUnitTest` | **1652 / 0 失败 / 2 skipped（134 suites）** |
| 改后 | 同上 | **1654 / 0 失败 / 0 error / 2 skipped（134 suites）** |
| Debug 打包 | `:app:assembleDebug` | BUILD SUCCESSFUL；`app-debug.apk` **29,522,153 B / SHA-256 `CFAA873884A6C9554F327D20D3DBFE72C9643F214FA387876408683A935A79D2`** |
| Release 编译 | `:app:compileReleaseKotlin` | BUILD SUCCESSFUL（未签名、未打 release 包） |
| 发布闸门 | `:app:verifyReleaseEndpoints` | **OK**：`REST_PROFILE=cloudbase ECS_PROFILE=ecsIp`，REST=CloudBase HTTPS，5 个 ECS 地址=`http://121.196.47.2/...`（打印 WARN） |
| Release BuildConfig 核对 | `:app:generateReleaseBuildConfig` 后读生成文件 | `ACCOUNT/CLOUDSPACE/UPDATE/MAP/WEB_BASE_URL` 均为 `http://121.196.47.2...`，`REST_BASE_URL` 仍为 CloudBase HTTPS |

测试变化明细（净 +2）：

| 测试类 | 变化 |
|---|---|
| `NoticesMetaTest`（新，10） | meta 一次拿全量且不再发分页正文请求、meta 可用时 `loadMore` 不发请求、签名未变只发 lite、meta 404 回退旧分页可继续加载、meta 403 不清会话、meta 坏 JSON 无缓存返回 Failure、lazy 标记、`hydrateRow` 只拉单行并合并回缓存、`hydrateRow` 行不在列表返回缓存、`loadRow` 正常/不存在 |
| `NoticesRepositoryTest`（11，改） | 显式 `metaEnabled=false`，继续覆盖旧两级链路的签名/整表/hidden/override/容错 |
| `NoticesPaginationTest`（14→12，改） | 显式 `metaEnabled=false`；删除 2 项「自动全部加载」用例（loader 已移除），保留分页/超时/失败/去重用例 |
| `NoticesAutoLoaderTest`（-6） | 删除（`NoticesAutoLoader` 及其后台翻页能力整体移除） |
| `NoticesDedupeIntegrationTest`（5，未改） | 现在恰好走 light meta 路径，跨行去重仍全绿（回归保护） |

---

## 5. 未验证 / 风险（如实）

1. **没有真机/模拟器端到端验证通知页**：本机 `emulator-5562` 无外网（`ping 121.196.47.2` 100% 丢包），
   无法连 CloudBase/ECS；本轮的结论来自：真实服务端 RPC 源码（网页仓只读）+ 10 项单测 + 编译/打包。
2. **debug 构建的 REST 是 staging 影子网关**（`/v1/rdb/rest-next/`），该网关**可能没有** `class_notices_meta`
   → debug 下大概率走回退分页链路（符合设计，但意味着 light meta 只在 release/CloudBase 生效）。
   若要在 debug 验证 light meta：`-Pct.restProfile=cloudbase` 构建，或用测试账号在正式网关上验证。
3. **单行 `select=*` 仍可能很大**：个别通知的附件是 MB 级内联 data URI，点开会下载整行（与网页 `NT.loadBody`
   一致，只影响用户主动打开的那一条）；未做「按图片序号只取一图」的字节级裁剪。
4. **light meta 首次探测多 1 个请求**（约 0.3MB）；之后同 TTL 内 0 请求、签名未变 0 正文请求。
5. **meta 的 5xx/超时不会自动改走 14MB 旧整表**：直接降级到缓存（无缓存则报错），由用户下拉刷新重试；
   这是有意的——弱网下偷偷拉 14MB 正是本次要消除的行为。只有 400/401/403/404/405/501 才切旧分页链路。
5. **release 明文 IP**：见 §2.3，属有意让步；备案恢复后按说明切回 `selfhosted`。
6. `NoticesScreen` 的「加载更早的通知」行在 light meta 链路下永远不会出现（`hasMore=false`），旧链路才有。
7. 模拟器上装的 debug 包已还原到「release beta7 + debug 两包共存」状态；本轮未重新安装 APK（无网络也验证不到通知）。

---

## 6. 回滚

- 通知：`NoticesRepository` 构造参数 `metaEnabled=false`（或把 `loadLocked` 里的 meta 分支去掉）→ 立即回到
  旧分页链路；`NoticesAutoLoader` 已删除，若要恢复后台翻页需从 git 历史取回（不建议）。
- release ECS：`-Pct.ecsProfile=selfhosted`（或改 build.gradle.kts 默认值）→ 地址回到 `https://tool.furina.fun`；
  同时可删 `network_security_config.xml` 里的 IP 明文豁免。
- 本轮未改 DB、未改协议、未改版本号；`git checkout -- <文件>` 即可整体回退。
