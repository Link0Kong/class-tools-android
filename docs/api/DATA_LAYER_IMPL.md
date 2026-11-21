# `docs/api/DATA_LAYER_IMPL.md` — Android 数据层实现与接线说明

> 任务：task-4「Android 数据层实现：ApiClient / class_verify / 名单 / userdata v6 / marks（含单元测试）」
> 契约依据：`docs/BACKEND_CONTRACT.md`、`docs/api/ENDPOINTS.md`、`docs/api/USERDATA_V6.md`、`docs/api/KOTLIN_MODELS.md`
> 状态：**已实现并单测通过**（`gradle :app:testDebugUnitTest` → 79 tests / 0 failures；`compileDebugKotlin` 通过）。

---

## 0. 重要前置说明（与任务书的两点差异）

1. **包名以工程实际为准**：任务书写的是 `fun.furina.classtools`，但工程在 2026-11-07 23:12 前后已整体改名为
   **`com.furina.classtools`**（`namespace`/`applicationId`/`package` 全量替换；`fun` 是 Kotlin 硬关键字，
   原包名无法编译）。数据层按实际工程放在 `app/src/main/kotlin/com/furina/classtools/data/**`，
   测试在 `app/src/test/kotlin/com/furina/classtools/**`。旧的 `fun/furina` 目录已清空删除。
2. **测试夹具来源**：`mp-class-tools-tests/_sync_fixtures/`（只读，未修改）→ 拷贝到
   `app/src/test/resources/sync_fixtures/`（golden copies，避免测试依赖工程外路径）。源目录变更时两者需同步。
3. 未改 `AppContainer`/`Screens`/`build.gradle.kts`/`libs.versions.toml`；`data/auth/AuthRepository.kt` 保持原接口
   （`VerifyResult.Success/Failure` 语义未变，仅新增 `ApiAuthRepository` 实现）。

---

## 1. 文件与类清单

### `data/network/`（网络基座）

| 文件 | 类型 | 说明 |
|---|---|---|
| `ApiResult.kt` | `ApiResult<T>` sealed / `ApiError` sealed / `PostgrestErrorBody` / `map`·`valueOrNull`·`isTokenInvalid` 扩展 | 统一返回：`Success(value)` / `Failure(ApiError)`；错误分类 `Network / TokenInvalid / Http(status,postgrestCode,serverMessage) / Parse / Config`。**所有仓库方法不抛异常**。 |
| `HttpTransport.kt` | `HttpMethod` / `ApiRequest` / `ApiResponse` / `HttpTransport` / `OkHttpTransport` / `NetJson` / `parseJsonOrNull` 等 | 传输层抽象（便于单测注入）；OkHttp 实现：连接/读 10s、call 20s、`retryOnConnectionFailure(false)`、`Dispatchers.IO`。 |
| `ApiClient.kt` | `ApiClient` | 头注入（`Authorization: Bearer key`、`X-Class-Token`、`Cache-Control: no-store`、`Content-Type`、`Accept`、`Prefer`）；GET 网络失败重试 1 次（350ms），写不重试；401/403 → `TokenInvalid` + `onTokenInvalid()`；PostgREST 错误体解析；`get/post/patch` 原始 + `getJson/postJson/patchJson`。 |

关键签名：

```kotlin
class ApiClient(
    baseUrl: String,                                  // 由装配层传 BuildConfig.REST_BASE_URL
    publishableKey: String,                           // BuildConfig.PUBLISHABLE_KEY（公开 anon key）
    tokenProvider: () -> String? = { null },          // 有身份时返回班级 token，否则 null
    transport: HttpTransport = OkHttpTransport(),
    json: Json = NetJson,
    retryDelayMs: Long = 350L,
    onTokenInvalid: () -> Unit = {},                  // 统一 401 处理：清 Session/回身份页
)

suspend fun get(pathAndQuery: String, authenticated: Boolean = true, extraHeaders: Map<String,String> = emptyMap()): ApiResult<String>
suspend fun post(path: String, body: String, prefer: String? = null, authenticated: Boolean = true, extraHeaders: Map<String,String> = emptyMap()): ApiResult<String>
suspend fun patch(path: String, body: String, prefer: String? = null, authenticated: Boolean = true, extraHeaders: Map<String,String> = emptyMap()): ApiResult<String>
suspend fun getJson(...): ApiResult<JsonElement>   // 同上，自动解析
```

### `data/auth/`

| 文件 | 类型 | 说明 |
|---|---|---|
| `ApiAuthRepository.kt` | `ApiAuthRepository : AuthRepository`、`VerifyErrorCode` | `verify(sid,name)`：前置校验 10 位学号/非空姓名（不发请求）→ `POST rpc/class_verify`（**不带 X-Class-Token**）→ 兼容对象/单元素数组响应 → `ok:false` 用云端 reason → 成功构造 `Session(issuedAt=now, expiresAt=now+30d)`。4xx→`invalid_credentials`，5xx→`server_error`，网络→`network`。 |

```kotlin
class ApiAuthRepository(api: ApiClient, now: () -> Long = System::currentTimeMillis, json: Json = NetJson, sessionTtlMs: Long = 30d)
// AuthRepository.verify(studentId, name): VerifyResult
object VerifyErrorCode { INVALID_INPUT, INVALID_CREDENTIALS, TOKEN_MISSING, NETWORK, SERVER_ERROR, PARSE_ERROR, NOT_CONFIGURED }
```

### `data/member/`

| 文件 | 类型 | 说明 |
|---|---|---|
| `ClassMember.kt` | `@Serializable ClassMember`、`ClassMembersResult`（Success(members, fromCache)/Failure）、`ClassMembersRepository` 接口 | `seat/sid/name/role/badge/isAdmin` + `isCommittee`；接口 `load(force)` / `cached()` / `clear()`。 |
| `ClassMemberCache.kt` | `FileClassMemberCache`、`ClassMemberCacheFile` | 按 sid 隔离落盘 `<dir>/members_<sid>.json`（`{savedAt, members}`）；目录建议 `File(context.cacheDir,"class_members")`（名单可重建，缓存可丢）。 |
| `ClassMembersRepository.kt` | `DefaultClassMembersRepository` | `POST rpc/class_members {}`；内存 L1 + 文件缓存；**TTL 6h**；空数组/网络失败 → 返回旧缓存（`fromCache=true`），不覆盖缓存；解析兼容数组与 `{data:[...]}`，过滤空姓名/坏行、按座号排序。 |

### `data/sync/`（userdata v6）

| 文件 | 类型 | 说明 |
|---|---|---|
| `UserdataV6.kt` | `object UserdataV6` | 11 个 `SECTION_KEYS`、14 个 `TOP_LEVEL_KEYS`、`defaultSection()`、`empty()/emptyBlob()`、`decode/encode`、`sectionsOf`、`atValue/numMs`。 |
| `UserdataMerge.kt` | `object UserdataMerge` | 纯函数：`merge(remote, local)`（逐 section LWW、相等云端赢、输出固定键序、`homeToolsAt` 镜像、v5 归一）、`normalizeV5`、`localView(base,current,remote,forceKeys,now,ownedSections)`（脏 section `at=max(now,base+1,remote+1)`、不拥有 section 从基线带、`power.at` 镜像）、`dirtySections`、`changedKeysVsDefaults`。 |
| `WaterNormalizer.kt` | `object WaterNormalizer` | `water` section 归一：room 只留对象；`p∈{4,6}`；月份 `YYYY-MM`；读数 ≥0、2 位、整数输出为整数形态（避免脏检测抖动）；按月唯一升序、≤120 条。 |
| `UserDataLocalStore.kt` | `UserDataLocalStore` 接口、`FileUserDataLocalStore`、`InMemoryUserDataLocalStore`、`UserDataStoreFile` | 按 sid 持久化：cache（合并基线）/ current（本机 section 值）/ pend+pendingKeys / lastPullAt / holdUntil / syncEnabled。**文件目录建议 `filesDir/userdata`**（不要 cacheDir）。 |
| `UserDataRepository.kt` | `UserDataRepository` 接口、`DefaultUserDataRepository`、`UserdataSyncConfig`、`UserdataPullResult`、`UserdataPushResult` | 双向同步编排（见 §3）。 |

```kotlin
class DefaultUserDataRepository(
    api: ApiClient,
    local: UserDataLocalStore,
    currentSid: () -> String?,
    scope: CoroutineScope,                       // App 级 scope（Debounce 用；测试可传 TestScope）
    clock: () -> Long = System::currentTimeMillis,
    config: UserdataSyncConfig = UserdataSyncConfig(ttlMs=60_000, debounceMs=1_200, holdMs=30d),
    json: Json = NetJson,
    ownedSections: Set<String> = UserdataV6.SECTION_KEYS.toSet(),  // Android 全量拥有
) : UserDataRepository {
    val currentSections: StateFlow<JsonObject>
    suspend fun pull(force: Boolean = false): UserdataPullResult
    suspend fun push(force: Boolean = false, forceKeys: Set<String> = emptySet()): UserdataPushResult
    suspend fun updateCurrent(sections: JsonObject)   // 业务层写完本机后调用（落盘 + Flow + 防抖推送）
    fun touch()                                      // 非挂起：仅通知「有本机变更」
    suspend fun flush(): UserdataPushResult          // 页面隐藏/退出前调用
    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun syncEnabled(): Boolean
    suspend fun cachedBlob(): JsonObject?
}
```

### `data/marks/`

| 文件 | 类型 | 说明 |
|---|---|---|
| `Mark.kt` | `MarkKind`(hw/nt/ntdone)、`@Serializable MarkRow`(@SerialName("val"))、`MarksSnapshot` | `idOf()`；快照 `isDone(kind,ref)`/`with()`；只保留 true 项。 |
| `MarksRepository.kt` | `MarksLocalStore`、`InMemoryMarksLocalStore`、`MarksUpdateResult`、`MarksRepository`、`DefaultMarksRepository` | GET `marks?select=*&seat=eq.<sid>`（TTL 2min）；POST upsert（`Prefer: return=representation,resolution=merge-duplicates`）；取消写 `val:false`；**没有任何 DELETE**；失败入 pending、`flushPending()` 补发；`marks: StateFlow<MarksSnapshot>` 乐观更新。 |

---

## 2. 线程 / 协程约定

- 所有公开方法都是 `suspend` 且**主线程安全**：
  - `OkHttpTransport` 内部 `withContext(Dispatchers.IO)`；
  - 文件存储（成员缓存 / userdata）内部 `Dispatchers.IO + Mutex`；
  - 纯逻辑（merge/解析）无 IO，可直接在测试或主线程调用。
- `UserDataRepository.touch()` 是**唯一非挂起**入口（业务层 save 后调用），内部 `scope.launch` 做脏检测与 1200ms 防抖推送；`scope` 由 AppContainer 注入 App 级 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`。
- 不要在主线程 `runBlocking` 调数据层；UI 层用 `lifecycleScope`/`viewModelScope` 调 `pull/push`。
- 所有跨线程可变状态都用 `Mutex` 保护；`push` 用 `pushMutex` 串行（单飞行）。

## 3. userdata v6 同步语义（与契约逐条对应）

| 契约要求 | 实现位置 |
|---|---|
| 逐 section LWW，`remote.at[k] >= local.at[k]` 云端赢 | `UserdataMerge.merge()`；单测对拍 `expected_merge_*` |
| v5→v6 迁移并立即写回 | `merge()` 先 `normalizeV5`；`pull()` 检测 `wasV5` → `push(force=true)`；`push()` 远端 v5 也会写 v6 |
| 14 顶层键固定顺序 | `merge/localView` 输出 `v, at, <11 sections>, homeToolsAt`，单测断言键序 + 与 fixture 字节一致 |
| `homeToolsAt` 镜像 `at.homeTools` | merge/localView 输出时统一写 |
| `power.at` 值内镜像 | `localView()` |
| 脏时间戳 `max(now, base+1, remote+1)` | `localView()` |
| 无基线不盲推 → 先 pull；用户先改动则 markPending | `push()` 返回 `Skipped`；`onLocalChanged()` 无基线时 `markPending(changedKeysVsDefaults)` + `pull(true)` |
| 防抖 1200ms / TTL 60s | `schedulePush()` / `pull()` |
| 失败保留待补发 | `push()` 失败 → `markPending(...)+setPend(true)`；下次 pull/flush 补推 |
| 关闭同步 | `setSyncEnabled(false)` → pull/push 返回 `Disabled`；恢复时补推或拉取 |
| 抑制期（清除云端后） | `local.holdUntil()` 检查 → `Held`（清除云端流程本身未在本增量实现，接口已就绪） |

## 4. AppContainer 接线建议（Lead 负责改 AppContainer）

```kotlin
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(appContext)

    // 建议由 SessionRepository/AppContainer 维护当前会话的内存快照，供 ApiClient 同步读取 token。
    @Volatile private var activeSession: Session? = null
    fun updateActiveSession(session: Session?) { activeSession = session }

    val apiClient = ApiClient(
        baseUrl = BuildConfig.REST_BASE_URL,
        publishableKey = BuildConfig.PUBLISHABLE_KEY,
        tokenProvider = { activeSession?.token },
        onTokenInvalid = {
            // 统一 401：清会话并让 UI 回身份页（不要在这里打日志带 token）
            scope.launch { sessionStore.clear() }
        },
    )

    val authRepository: AuthRepository = ApiAuthRepository(apiClient)
    val classMembersRepository = DefaultClassMembersRepository(
        api = apiClient,
        cache = FileClassMemberCache(File(appContext.cacheDir, "class_members")),
        currentSid = { activeSession?.sid },
    )
    val userDataRepository = DefaultUserDataRepository(
        api = apiClient,
        local = FileUserDataLocalStore(File(appContext.filesDir, "userdata"), { activeSession?.sid }),
        currentSid = { activeSession?.sid },
        scope = scope,
    )
    val marksRepository = DefaultMarksRepository(
        api = apiClient,
        currentSid = { activeSession?.sid },
    )
}
```

> `activeSession` 的刷新点建议：启动时 `sessionStore.state.first()` 恢复；`identity` 页验证成功后「`authRepository.verify` → `sessionStore.save` → `updateActiveSession` → `classMembersRepository.load()` → 进入主页」；退出身份时 `updateActiveSession(null)` + `sessionStore.clear()` + `classMembersRepository.clear()`（+ userdata/marks 的缓存清理按隐私页语义）。

### 典型调用时序

```text
身份页:
  verify = authRepository.verify(sid, name)
  if Success: sessionStore.save(session); container.updateActiveSession(session)
              classMembersRepository.load(force = true)

主界面 onStart:
  marksRepository.refresh()
  userDataRepository.pull()            // 内部 TTL 60s
  classMembersRepository.load()        // 内部 TTL 6h

用户修改任意同步内容（抽签参数/分组/名单/关注宿舍/水量…）:
  本机业务状态写入后 → userDataRepository.updateCurrent(当前 sections JsonObject)
  （或若业务层自己维护 current，则改完调用 userDataRepository.touch()）

标记作业完成 / 通知已处理:
  marksRepository.setMark(MarkKind.HW/NT_DONE, ref, true/false)   // 失败自动 pending，refresh/flush 补发

回前台 / 页面 onShow:
  userDataRepository.pull(force = false); marksRepository.refresh(force = true).takeIf { 需要强刷 }? 
  // 与网页一致：回前台 marks 强制拉一次（5s 防抖由调用方节流即可）

退出/切后台:
  userDataRepository.flush(); marksRepository.flushPending()
```

## 5. 测试

命令（本机验证通过）：

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd <项目根>
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

结果（2026-11-07 23:2x，最新一次全绿）：

| 测试类 | tests | failures |
|---|---|---|
| `data.auth.ApiAuthRepositoryTest` | 12 | 0 |
| `data.marks.MarksRepositoryTest` | 9 | 0 |
| `data.member.ClassMembersRepositoryTest` | 10 | 0 |
| `data.network.ApiClientTest` | 13 | 0 |
| `data.sync.UserdataMergeTest` | 16 | 0 |
| `data.sync.UserDataRepositoryTest` | 15 | 0 |
| `data.sync.WaterNormalizerTest` | 4 | 0 |
| **合计** | **79** | **0** |

报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。
另：`gradle :app:compileDebugKotlin` 通过（仅 Lead 文件 `ToolCatalog.kt` 有 1 条 deprecation warning）。

覆盖要点：
- v6 merge 三向对拍：`merge(mp,web)==expected_merge_web_mp`、`merge(web,mp)==expected_merge_mp_web`、v5 迁移 == `expected_v5_migrate`（语义 + 顶层键序 + 序列化字节一致）；
- TTL/防抖（TestScope 虚拟时间）、无基线不盲推、失败 pending→重试、v5 写回、关闭同步、抑制期；
- `class_verify` 对象/数组/ok:false/4xx/5xx/网络/坏 JSON/学号格式；
- marks 行结构（`val` 字段）、Prefer 头、取消=false、pending 覆盖、TTL、无 DELETE；
- 名单解析/空数组保留缓存/TTL/force/sid 隔离/落盘命中；
- water 归一（人数、月份、坏房间、120 条上限）。

**测试不发真实网络**：无 MockWebServer 依赖，统一走 `FakeTransport`；夹具里是假 token（`fake-token-*`），没有任何真实密钥/学号。

## 6. 待确认 / 后续增量

| # | 项 | 说明 |
|---|---|---|
| Q1 | token 过期/无 token 时云端返回形态 | **已实测（Lead 2026-11-07，真实云端）**：`rpc/class_members` 无 token 返回 **HTTP 200 + `[]`**（不是 401）；`rpc/class_verify` 假身份返回 `200 + {"ok":false,"reason":"姓名或学号不在班级名单中"}`。⇒ 正确性不能只靠 401 拦截：**（a）本地有会话才允许发数据请求；（b）空数组/空数据一律视为失败并保留本机缓存（现有实现已满足：ClassMembersRepository 空数组保留旧缓存、DefaultUserDataRepository 空行视为无云端数据不覆盖本机）**。若未来服务端改为 401 也兼容（ApiClient 已映射 TokenInvalid）。 |
| Q2 | `class_verify` 4xx 的语义 | 已按契约映射为 `invalid_credentials`（姓名/学号不匹配）；若服务端 4xx 还承担限流/风控，需要区分（目前未观察到）。 |
| — | `notices/homework/fitrecs/power/power_history/site_assets/visits/feedback/tt` 仓库 | 本增量只做网络基座 + 身份 + 名单 + userdata + marks；其余按同一 `ApiClient` 模式在后续 task 增量实现（契约与 DTO 已在 `docs/api/`） |
| — | userdata `current` 的真实来源 | 目前由 `UserDataLocalStore.current` 承载；业务层（抽签/分组/名单/体测/课表/电费/水量页面状态）落地后，统一调用 `updateCurrent` 即可，无需改数据层 |
| — | 清除云端数据流程 | `UserDataLocalStore.holdUntil/pend/clear` 接口已就绪；`clearCloud` 编排（空 blob + marks 全 false + fitrecs arch）待后续增量 |
| — | token 加密存储 | 当前 `SessionStore` 是 DataStore 私有目录（Manifest allowBackup=false）；后续可换 EncryptedSharedPreferences/Keystore |
| — | 测试夹具同步 | `sync_fixtures` 是 `mp-class-tools-tests/_sync_fixtures` 的只读拷贝；上游合并规则变更时要同步（可加 CI 校验 sha256） |
