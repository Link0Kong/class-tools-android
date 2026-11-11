# v4 数据层：投票 / 名单三分类 / 电费同期比较（task-33）

> 状态：**已交付**（纯数据/逻辑层 + 单测 + 文档；无 UI、不改 MainNavHost/AppContainer/构建）。
> 基线 v3.6.0（HEAD 72805f9，单测 994/0）；本任务新增 **71 个单测**（要求 ≥45），全量回归数字见交付消息。
> 权威契约（只读）：`web/vote/CONTRACT.md`（含 2026-11-11 18:08 可见人范围增量）、
> `web/deploy/cloudbase/2026-11-11_stage_rpc.sql` 的 `class_league()`、`web/AGENTS.md §34`（电费同期比较 + 13/16 隐藏 24h）。

---

## 1. 交付文件

| 文件 | 内容 |
|---|---|
| `data/vote/VoteModels.kt` | `PollMode`/`PollResultVisibility`/`PollAudience`、`PollSettings`（归一/标签）、`PollOption`、`PollVoter`、`Poll`（详情）、`PollSummary`（列表）、`PollList`、`PollDetail`、`PollOptionDraft`/`PollDraft`、`PollManageAction`/`PollManageResult`、`VoteError`（sealed 中文）、`VoteResult<T>`、`VoteValidationError`/`VoteField`、`VoteLimits`。**所有模型无 sid/学号** |
| `data/vote/VoteLogic.kt` | 纯逻辑：倒计时（不限时/已截止/剩 hh:mm:ss）、截止标签、快捷截止、结果可见性三档、能否投票/改票/管理、选项校验、草稿校验（标题/说明/选项/配图/规则/截止/范围）、草稿归一、`prepareDraft`（幂等 id）、列表排序、范围/结果标签、百分比、ID 生成 |
| `data/vote/VoteRepository.kt` | `VoteRepository` 接口 + `DefaultVoteRepository`（list/detail/create/submit/manage；`VoteRpcBody` 请求体构造；`VoteParser` 响应解析；`VoteErrors` reason/ApiError 映射；列表/详情内存缓存 TTL 15s + 失败降级） |
| `data/member/ClassLeagueRepository.kt` | `LeagueMember(seat,name)`（无 sid）、`ClassLeagueResult`、`ClassLeagueRepository` + `DefaultClassLeagueRepository`（`rpc/class_league`，TTL 6h，失败降级缓存；`parseLeague` 纯函数容错） |
| `data/member/MemberCategories.kt` | `MemberCategory`（班委/团员/全班）、`MemberEntry(seat,name,badge)`（**无 sid**）、`MemberCategoryCounts`、`MemberCategoryBuilder`（判定/过滤/计数/搜索） |
| `features/power/PowerCompareLogic.kt` | 自然周期窗口、真实采样覆盖（≥35%）、窗口内用电（真实下降和、不插值）、比较结果（百分比/文案/不可比原因）、`showRange`/`normRange`（13/16 隐藏 24h） |
| `test/.../data/vote/VoteLogicTest.kt` | 24 用例 |
| `test/.../data/vote/VoteRepositoryTest.kt` | 19 用例（假 `HttpTransport`，不真请求） |
| `test/.../data/member/ClassLeagueRepositoryTest.kt` + `MemberCategoryBuilderTest` | 15 用例 |
| `test/.../features/power/PowerCompareLogicTest.kt` | 13 用例 |

> 说明：本任务只新增文件；未修改 `ClassMember`/`ClassMembersRepository`（现有班委数据继续复用）。

---

## 2. 投票：公开 API

```kotlin
interface VoteRepository {
    suspend fun list(force: Boolean = false): VoteResult<PollList>
    suspend fun detail(pollId: String, force: Boolean = false): VoteResult<PollDetail>
    suspend fun create(draft: PollDraft): VoteResult<PollDetail>
    suspend fun submit(pollId: String, choices: List<String>): VoteResult<PollDetail>
    suspend fun manage(pollId: String, action: PollManageAction): VoteResult<PollManageResult>
}

class DefaultVoteRepository(
    api: ApiClient,
    currentSid: () -> String?,          // 为空 → 直接 VoteError.IdentityInvalid（不发无 token 请求）
    clock: () -> Long = System::currentTimeMillis,
    json: Json = NetJson,
    listTtlMs: Long = 15_000L,          // 列表/详情缓存 TTL
) : VoteRepository
```

### 关键模型

```kotlin
enum class PollAudience(wire, label) { ALL, COMMITTEE, LEAGUE, CUSTOM }
enum class PollResultVisibility(wire, label) { ALWAYS, AFTER_VOTE, AFTER_CLOSE }
data class PollSettings(multi=false, maxChoices=1, anonymous=true, allowChange=true,
                        resultVisibility=AFTER_VOTE) {
    fun normalized(optionCount: Int): PollSettings
    fun tags(hasVoted: Boolean = false, closed: Boolean = false): List<String>
}
data class PollSummary(..., closed, total, counts: Map<String,Int>, hasVoted, myChoices,
                       resultsHidden, mine, canManage, audience, audienceCount)
data class PollDetail(poll: Poll, serverNow, closed, total, counts, hasVoted, myChoices,
                      resultsHidden, mine, canManage, voters: List<PollVoter>)
data class PollDraft(id="", title, description, mode, options: List<PollOptionDraft>,
                     settings, deadline=0, audience=ALL, audienceSeats: List<Int> = emptyList())
sealed interface PollManageAction { Close; Extend(deadline); Delete; Edit(draft, updateAudience=true) }
sealed interface PollManageResult { Updated(detail); Deleted(pollId) }
```

### 纯逻辑（`VoteLogic`）

- `countdown(closed, deadline, now)`: `Unlimited` / `Closed` / `Remaining(millis,text)`；`formatRemaining` 输出 `剩 hh:mm:ss` / `剩 mm:ss`；
- `deadlineLabel` / `quickDeadline`；
- `resultsVisible(settings, closed, hasVoted)`：always / 投票后（或已截止）/ 截止后；
- `voteBlockReason(closed, settings, hasVoted, inAudience)` → null/「不在可见范围内」/「投票已结束」/「已投过票，不可改票」；
- `canChangeVote` / `canManage`；
- `validateChoices(settings, choices, optionIds)` → `VoteError?`；
- `validateDraft(draft, now)` → `List<VoteValidationError>`（标题 1–80、说明 ≤500、选项 2–20、选项文字 1–80、说明 ≤200、配图 data:image/、单张 ≤150000 字符、总量 ≤1.6MB、单选/多选 max、截止、范围 custom 座号 1–70 去重）；
- `normalizeDraft` / `prepareDraft`（首次发起生成稳定 `p...`/`o...` id；**网络失败重试复用同一草稿 → 服务端按 p_id 幂等**）；
- 列表排序：进行中按截止升序（不限时最后），已结束按创建倒序；
- `audienceTag`（all→null；班委可见/团员可见/指定 N 人）、`resultHiddenTag`、百分比；
- `newPollId`/`newOptionId`（`[a-z0-9]`，符合服务端 CHECK）。

---

## 3. RPC 契约映射

| 方法 | path | body（具名参数） | 成功解析 |
|---|---|---|---|
| `list` | `rpc/poll_list` | `{}` | `{now, polls[]}` → `PollList` |
| `detail` | `rpc/poll_detail` | `{p_id}` | `{poll, now, closed, total, counts, has_voted, my_choices, results_hidden, voters, mine, can_manage}` → `PollDetail` |
| `create` | `rpc/poll_create` | `{p_id,p_title,p_description,p_mode,p_options,p_settings,p_deadline,p_audience,p_audience_seats}` | 同 detail（服务端幂等返回） |
| `submit` | `rpc/poll_submit` | `{p_id,p_choices:[...]}` | 同 detail（含最新 counts） |
| `manage` | `rpc/poll_manage` | `{p_id,p_action,...}`：close/delete 仅两项；extend 加 `p_deadline`；edit 加 title/description/options/settings/deadline（可选 audience/audience_seats） | `{ok:true,deleted:true}` → `Deleted`；其它 → detail → `Updated` |

- 请求一律 `POST`、带班级 token（`X-Class-Token`）、`Cache-Control:no-store`（ApiClient 统一）；
- 响应 `{ok:false,reason}`（HTTP 200）→ `VoteErrors.fromReason`；
- 列表最多 100 条由服务端限制；自定义范围座号仅 `can_manage` 返回。

### 错误映射（`VoteErrors`）

| 服务端 reason | VoteError | message |
|---|---|---|
| `投票不存在` | `NotFound` | 投票不存在 |
| `身份已失效，请重新进入班级` / `未登录` / `身份无效` | `IdentityInvalid` | 身份已失效，请重新进入班级 |
| `不在可见范围内` | `OutOfAudience` | 不在可见范围内 |
| `投票已结束` | `Closed` | 投票已结束 |
| `已投过票，不可改票` | `AlreadyVoted` | 已投过票，不可改票 |
| `无权管理该投票` | `NoPermission` | 无权管理该投票 |
| `选项不合法` | `InvalidOptions` | 选项不合法 |
| `重复的选项` | `DuplicateChoices` | 重复的选项 |
| `只能选 1 项` | `SingleChoiceOnly` | 只能选 1 项 |
| `最多选 N 项` | `LimitExceeded(N)` | 最多选 N 项 |
| `已有投票，不能修改内容` | `EditLocked` | 已有投票，不能修改内容 |
| 其它业务 reason（标题/说明/截止/范围…） | `Validation(reason)` | reason 原文 |
| 网络不可用 / 5xx | `Network` | 网络不可用，请稍后重试 |
| 401/403 / `ApiError.TokenInvalid` | `IdentityInvalid` | 身份已失效，请重新进入班级 |
| 坏 JSON | `Validation("投票数据格式异常")` | 投票数据格式异常 |

---

## 4. 名单三分类

```kotlin
enum class MemberCategory(label) { COMMITTEE("班委"), LEAGUE("团员"), ALL("全班") }
data class MemberEntry(seat: Int, name: String, badge: String = "")   // 无 sid
data class MemberCategoryCounts(committee, league, all) { fun of(category) }

object MemberCategoryBuilder {
    fun isCommittee(member: ClassMember): Boolean          // role 非空 || badge 含「班委」（与服务端 auth_is_committee 同口径）
    fun committee(members): List<MemberEntry>              // 座号升序；badge = role ?: badge ?: "班委"
    fun league(members: List<LeagueMember>): List<MemberEntry>
    fun all(members): List<MemberEntry>                    // badge = role ?: badge
    fun entries(category, classMembers, leagueMembers): List<MemberEntry>
    fun counts(classMembers, leagueMembers): MemberCategoryCounts
    fun search(entries, query): List<MemberEntry>          // 姓名搜索（忽略大小写/空格）
    fun visibleEntries(category, classMembers, leagueMembers, query): List<MemberEntry>
}

interface ClassLeagueRepository {
    suspend fun load(force: Boolean = false): ClassLeagueResult
    suspend fun cached(): List<LeagueMember>
    suspend fun clear()
}
class DefaultClassLeagueRepository(api, currentSid = { null }, clock, json, ttlMs = 6h)
```

- `rpc/class_league` → `{ok:true,count,seats:[{seat,name}]}`；**无 sid**；
- 缓存 TTL 6 小时（与班级名单一致）；网络失败 → 缓存 + `fromCache=true` + `warning`；无缓存 → `Failure`；
- 空数组视为有效空名单（团员可能 0 人）；坏行（seat 非 1..70 / name 空）跳过、按座号去重升序；
- `MemberEntry`/`LeagueMember` 模型无学号字段（单测用反射校验）。

---

## 5. 电费自然周期同期比较（`features/power/PowerCompareLogic.kt`）

```kotlin
data class PowerCompareWindow(label, start, end)              // [start,end)
data class PowerCompareResult(range, available, label, percent, currentUse, previousUse,
                              currentWindow, previousWindow, currentCoverage, previousCoverage,
                              reason, title) { val badgeText: String? }

object PowerCompareLogic {
    const val MIN_COVERAGE = 0.35
    const val MIN_DELTA = 0.005
    fun windows(range, nowMillis, zone): Pair<PowerCompareWindow, PowerCompareWindow>
    fun coverage(points, window): Double
    fun usageIn(points, window): Double?                       // 真实下降和（忽略上升/噪声），不插值
    fun pointsIn(points, window): List<PowerPoint>
    fun compare(range, history, nowMillis, zone): PowerCompareResult
    fun compareAll(history, nowMillis, zone): Map<PowerRange, PowerCompareResult>
    fun formatPercent(value): String                            // +12.3% / -4.5% / 0%
    fun windowTitle(current, previous, zone): String
    // 13/16 隐藏 24h
    fun isSlowBuilding(building): Boolean
    fun showRange(range, building): Boolean
    fun showRange(ranges = PowerRange.entries, building): List<PowerRange>
    fun normRange(range, building): PowerRange                  // 24h 被隐藏 → D7
}
```

### 口径
- **窗口**：24h = 今日 00:00–现在 vs 昨日 00:00–昨日同一时刻；7d = 本周一 00:00–现在 vs 上周一 00:00–上周同一时刻；30d = 本月 1 日 00:00–现在 vs 上月 1 日 00:00–上月同一日同时刻；
- **覆盖**：窗口内真实采样跨度 / 窗口时长（样本 <2 → 0）；**两侧都 ≥35% 才可比较**；
- **用电**：窗口内相邻真实采样下降之和（`<-0.005` 才计；上升=充值/异常跳过）；首样本晚于窗口起点时**不插值补算**缺失段；
- **不可比较**（`available=false`，UI 隐藏徽标）：当前覆盖不足→「数据积累中」；上一周期覆盖不足→「上一周期数据不足」；上一周期无有效下降→「上一周期无有效用电数据」；
- **13/16 号楼**（`PowerStats.SLOW_BUILDINGS` 同口径）：隐藏 24h 档；`normRange` 把非法 24h 归到 7 天；切换宿舍时由 UI 调用（不覆盖用户显式选择）；
- 现网 power_history 约 6 天，故上周/上月比较通常不可用，数据积累后自动出现（不硬凑）。

---

## 6. 接线点（Lead / design）

### 6.1 AppContainer（新增仓库，1–2 行）

```kotlin
val voteRepository: VoteRepository = DefaultVoteRepository(
    api = apiClient,
    currentSid = { activeSession?.sid },
)
val classLeagueRepository: ClassLeagueRepository = DefaultClassLeagueRepository(
    api = apiClient,
    currentSid = { activeSession?.sid },
)
```

### 6.2 投票 UI（design 层）
- 列表：`voteRepository.list()` → `PollList`；`VoteLogic.sortOpen/sortClosed`；倒计时用 `VoteLogic.countdown(closed, deadline, now)`，`now` 建议用 `VoteLogic.bestNow(serverNow)` + 每秒本地 tick；
- 详情/结果：`detail(pollId, force = true)` 轮询（契约 5s/10s；缓存 TTL 15s，**刷新必须 force=true**）；结果条用 `percent/counts`；`resultsHidden` 显示标签；实名 `voters`；
- 发起/编辑：`PollDraft` + `VoteLogic.validateDraft`；**打开发起弹层时 `VoteLogic.prepareDraft` 一次并保存**，失败重试复用（服务端按 p_id 幂等）；
- 管理：`PollManageAction`（编辑仅 `total==0`；删除返回 `Deleted` 后回列表并清缓存）；
- 可见范围：`PollAudience` 四段；custom 成员选择用 `classMembers` 座号（chips 只显示姓名）；`audienceTag` 生成标签；
- 匿名：`settings.anonymous` 时永远不显示 `voters`；
- 无 sid：所有 UI 直接渲染模型即可，不要自行拼学号；`PollSummary`/`Poll` 无 sid 字段。

### 6.3 名单 UI（与现有 RosterScreen 融合建议）
- 三个 tab：`MemberCategory.COMMITTEE/LEAGUE/ALL`，徽标用 `MemberCategoryBuilder.counts(...)`；
- 班委/全班来自现有 `classMembersRepository`；团员来自 `classLeagueRepository.load()`；
- `MemberCategoryBuilder.visibleEntries(category, classMembers, leagueMembers, query)` 一次生成过滤+搜索后的 `List<MemberEntry>`（无学号）；
- `LeagueMember`/`MemberEntry` 可直接用于点名/抽签座号（`seat`），不需要 sid。

### 6.4 电费 UI
- 比较徽标：`PowerCompareLogic.compare(range, history, now)`（或 `compareAll`）；`available=false` 时**隐藏徽标**（不显示「—」占位，避免误导）；
- 13/16 号楼：区间按钮用 `PowerCompareLogic.showRange(building = PowerRooms.buildingOf(sel))` 过滤；选中慢楼栋且当前档为 24h 时 `normRange` 归到 7 天（仅在切换宿舍时归位）；
- 比较所需更长窗口（7d→14d、30d→70d）按需补拉由 UI/数据层负责（本文件只做纯计算；沿用现网缓存策略）。

---

## 7. 测试

```text
新增 71/0：
  VoteLogicTest            24
  VoteRepositoryTest       19
  ClassLeagueRepositoryTest 9
  MemberCategoryBuilderTest 6
  PowerCompareLogicTest    13
全量回归（2026-11-11 21:57）：**CLASSES=85 / TOTAL=1065 / FAIL=0 / ERR=0**（基线 994 + 本任务 71）。
```

覆盖：倒计时/可见性/改票/范围/草稿校验/归一/幂等 id/排序/标签；RPC 请求体/解析/缓存 TTL/失败降级/错误映射/管理动作；league 解析/坏行/缓存/降级；三分类/计数/搜索/无学号；电费三档窗口/覆盖/不插值/百分比/不可比原因/13·16 规则。

---

## 8. 契约风险 / 取舍 / 遗留（如实）

1. **重试幂等**：服务端仅按 `p_id` 幂等。本仓库每次 `create` 若 `draft.id` 为空会生成新 id → **UI 必须在发起弹层打开时调用 `VoteLogic.prepareDraft` 并保存**，失败重试复用；否则可能重复创建。
2. **详情缓存 TTL 15s**：与契约「详情 5s/10s 轮询」冲突时，UI 刷新需 `detail(force=true)`（已提供参数）。
3. **30d 窗口月末**：`ZonedDateTime.minusMonths(1)` 对 3-31/5-31 等会钳到上月最后一天；网页 JS 的「上月同一日」行为可能不同（滚到下月）。后端数据仅 6 天，短期无影响，后续若需严格对齐需与 web 确认。
4. **范围外隐藏**：服务端对范围外返回「投票不存在」（不暴露存在性）；客户端列表不会收到该投票，无需特殊处理。
5. **owner 始终可投**：`PollDetail.mine=true` 时 UI 传 `inAudience=true` 调用 `voteBlockReason`（避免 custom 范围下 owner 被误判不可投）。
6. **管理员越权可见不可投**：服务端 submit 返回「不在可见范围内」；客户端无法仅凭 detail 判断自己是否在范围（无 sid/seat 信息），以服务端错误为准。
7. **class_league 缓存仅内存**：进程重启首次重新拉取（6h TTL）；如需离线持久化可后续加文件缓存（与本任务范围无关）。
8. **投票数据不进 userdata/marks**：无新同步 section；不写云端（本任务只做客户端代码 + 假传输单测，不真实写云端）。
9. **名单三分类的团员数据来自独立 RPC，与 class_members 无 sid 对齐**：如需判断「某团员是否班委」可再用 seat 匹配。
