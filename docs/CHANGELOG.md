# 版本记录（Android 客户端）

## 2026-11-27 · 1.0.0-beta93 / vc178

- 修复四类桌面小组件滑动时越出圆角边框的问题。工作台显示分类状态与数量，宽版同行等高，标题详情和分类列表分别可点。

## 2026-11-27 · 1.0.0-beta88 / vc173

- 课表、待办、通知和工作台桌面组件按大小和系统字号切换摘要或可滚动列表。「全部」入口放在卡片上方，任务完成、创建和通知刷新继续按可用空间显示。
- 工作台保留四类入口及顺序，宽版支持双列。课表和待办的宽版分组保留完整条目，通知宽版增加摘要。

## 2026-11-27 · 1.0.0-beta87 / vc172

- 校园地图更新 52 栋建筑复核数据，加入自强楼和厚德楼平面图、五行楼目录，以及室外多起点路线。
- 课表已收录教室直接打开楼层并选中房间。修复从班级课件返回文件传输入口的流程。

## 2026-11-27 · 个人任务并发与旧缓存恢复（源码）

- 个人任务按上一次确认的版本同步。两台设备并发编辑、同时导入作业和重复任务时会重读合并，删除后的旧编辑不会自动恢复任务。
- 识别旧任务包装并保留未知字段。旧草稿或写入回执不明确时可以先导出，再选择合并或采用云端内容。附件文件保存在本机。

## 2026-11-26 · 1.0.0-beta86 / vc169

- 默认外观采用网页端的奶油底、暖白卡面和雾青强调色，深色使用绿灰底面。M3 按钮、输入框、选中态和桌面默认配色沿用同一角色，外观设置可选系统壁纸色。
- 待办及工作台组件可直接勾选个人任务，点标题查看详情。通知组件提供独立刷新按钮。操作按当前登录账号校验，完成和重复任务沿用任务列表的规则。
- 个人任务支持 AI 整理文字。可选取草稿回填截止时间、步骤及完成建议，编辑后逐项保存。
- 平板和宽窗口支持任务列表与详情、周课表与日程并排显示，工作台卡片适配多栏。

## 2026-11-26 · 1.0.0-beta84 / vc167

- 「课表接入」移到周／日视图切换旁，给课表网格和课程列表留出完整空间。课程数据包含教师时，日视图显示教师，周视图在课程块有足够高度时显示教师。

## 2026-11-26 · 1.0.0-beta83 / vc166

- 通知、班级作业和个人任务按手动选定的时刻排系统闹钟，可在通知设置授予「闹钟和提醒」。后台补发会核对当前任务、账号和发送记录。提醒可加入系统日历，直接写入的任务日程会随改期、推迟和完成更新。
- 活动详情可选择在报名开始前 1、5、15 或 30 分钟显示流体云。实时通知显示活动名、开抢时间及已提供的地点、截止和来源，支持多轮报名和自定开抢时间。
- 点开实时通知可看精细倒计时，并将小窗悬浮在报名页面上。最后 10 秒按显示帧刷新三位毫秒，系统胶囊使用原生秒级计时器。

## 2026-11-26 · 1.0.0-beta82 / vc165

- Android 12 及以上默认使用莫奈壁纸配色，工具和桌面默认卡片随壁纸、应用深浅模式变化。外观设置支持切回青瓷，桌面组件保留单实例选色。
- 待办卡增加数量、分色清单和新建入口，宽大尺寸按星标与截止日期展示四象限。课表支持双日日程；工作台提供四类入口；通知突出最新标题及发布时间。
- 四种组件同步更新选择器预览与旧系统预览图。详见[卡片验收表](ui/CARD_JOURNEYS.md)。

## 2026-11-26 · 1.0.0-beta79 / vc162

- 工作台主卡显示当前或下一节课。课程行定位到对应日期和课次，个人任务、班级作业和通知行分别打开详情。
- 卡面保留列表入口。详情优先显示时间、状态和允许的操作，通知刷新失败时保留可用缓存并支持重试。电费页把当前读数、更新时间和低电阈值放在趋势图之前。
- 四种桌面小组件按尺寸显示摘要和可点条目，跟随应用外观并保留单个组件选色。更新系统选择器预览。
- 验收路径和覆盖场景见 [卡片入口与验收](ui/CARD_JOURNEYS.md)。

## 2026-11-26 · 1.0.0-beta77 / vc160

- 诊断页查询排除已归档的分析，近期异常使用完整的 20 条查询额度。
- 合入上课流体云的保持与恢复、课间提醒，以及工作台逐小节上下课倒计时。

## 2026-11-23 · ICP 备案通过：release 切回 `https://tool.furina.fun`（移除明文 IP；本次不发版）

> 网站 ICP 备案已通过：**闽ICP备2026042939号-2**（工信部入口 <https://beian.miit.gov.cn/>），
> 正式域名 `https://tool.furina.fun`（阿里云杭州 ECS + Caddy 自动 HTTPS）恢复。release 的账号 / 云空间 /
> 更新清单 / 校园地图 / PPT 媒体 / 法律全文从临时明文 IP `http://121.196.47.2/...` 切回 https 正式域名。

- **`app/build.gradle.kts`**：`ct.ecsProfile` 默认改为 `selfhosted`，且只接受该值（`ecsIp` 分支移除，
  其它值在配置期直接构建失败）；release 的 5 个 ECS 地址固定 `https://tool.furina.fun/...`，
  debug 保留 `http://121.196.47.2` 本机调试入口。REST 默认值**未改**（仍 CloudBase，P4 数据迁移后再切）。
- **`:app:verifyReleaseEndpoints`**：删除 `ecsIp` 校验与 WARN，改为断言 5 个 ECS 地址全部 https、
  以 `https://tool.furina.fun` 为前缀、不含 `121.196.47.2`。
- **网络安全配置**：main 删除 `121.196.47.2` 的 cleartext 放行（只保留教务 `210.34.74.77`）；
  debug 配置保留 staging IP 供本机调试，注释同步更新。
- **产品代码**：`OppoAccountConfig` / `HonorAccountConfig` 的默认账号根地址改为读取
  `BuildConfig.ACCOUNT_BASE_URL`（debug = 调试 IP、release = https 正式域名），main 中不再有明文 IP 常量；
  更新弹窗预览数据改用 https。全仓确认主源码无「备案信息 / 上线前填写」占位（APP 端按用户决定不展示备案信息行）。
- **文档**：CHANGELOG / PRIVACY_COMPLIANCE / OPPO_ACCOUNT / ROADMAP / RELEASE_BUILD / api/NOTICES 等
  的「备案期 / 备案暂停 / 上线前填写」临时口径改为正式域名与已通过说明（历史验收报告保留原样）。
- **验证（真实执行）**：
  - `:app:verifyReleaseEndpoints` OK：`REST_PROFILE=cloudbase ECS_PROFILE=selfhosted`，
    ACCOUNT / CLOUDSPACE / UPDATE / MAP / WEB 均为 `https://tool.furina.fun...`；debug 仍 staging IP。
  - `:app:testDebugUnitTest`：**1895 用例 / 0 失败 / 0 错误 / 2 skipped**（154 suites）。
  - `:app:assembleRelease --project-prop "ct.skipUpdateChannel=true"`：APK **6,107,746 B /
    SHA-256 `1E0CB767C4EA99D6391F0CC823C49461517059EFC080BB65689ED1C35367F80E`**，v2 签名通过
    （证书 SHA-256 `974854df…bec4a1a`）；release `BuildConfig` 实测 5 个 ECS 地址全为 https 正式域名。
  - 说明：本机 checkout 目录的 Windows ACL 拒绝子进程写入，`computeOppoSign` 写 `build/opposign` 失败
    （OPPOSign 仅 OPPO 商店需要），故 release 编译验证附加 `-Pct.skipOppoSign=true`；
    **未上传更新渠道**（发版由 Lead 在域名切换完成后统一执行）。
- **未做**：未发版（versionCode/versionName 未动）、未真机验证、未把 REST 切到 selfhosted（P4 数据迁移）。

## 2026-11-22 · 1.0.0-beta53 / vc136：抽签与分组独立 + 最近抽签持久化 + 分组页优化

> 对齐 `web/group/CONTRACT.md`：抽签与分组拆成两个独立工具；抽签页不再有「去分组 / 下一步」，
> 分组页的「最近抽签」改为读本机持久化结果（跨进程重启仍可用），并支持「全班」快捷。

- **抽签页**：删除结果卡「用此结果分组」按钮与 `onGroup` 回调链（`DrawScreen` / DrawSections / `MainNavHost` / 预览），
  不再把抽签结果直接送进分组页；保留三步（配置 / 候选 / 结果）、剔除重抽、排序、导出抽签图。
- **结果单下线**：删除「导出结果单」按钮、`DrawResultSheetPayload` / `DrawSheetFactory` / `ShareKind.SUM` 与对应单测
  （契约 §1.3）；抽签图导出、分组图导出不受影响。
- **最近抽签持久化**：新增 `features/draw/DrawLastResultStore.kt`（纯 Kotlin 编解码 + 键值接口）与
  `data/draw/SharedPreferencesDrawLastResultStore.kt`（`ct_draw_last` 私有偏好，不进 userdata v6、不跨端同步）。
  抽签成功 / 重新抽签 / 剔除补抽 / 排序都会写入序号数组 + 参数快照（range/count/dup/sort）+ 时间戳；重置保留上一次成功结果。
- **分组页**：新增 `GroupMemberResolver`，`PREV` 来源改从本机存储解析成员（内存 `prevMembers` 仅预览/兜底）；
  来源分段改为「全班 N / 最近抽签 N / 手动选择 N」，无抽签结果时置灰并提示「还没有抽签结果」；
  手动名单支持「全选 / 清空」，底部名单弹层保留「全选」；结果摘要统一为「共 N 人 · M 组 · 每组 x–y 人」；
  结果操作保留重新分组 / 复制 / 导出；移除「1 配置 / 2 分组结果」步骤条，配置与结果同页。
- **兼容**：`userdata.group.source` 仍只有 `prev/names`（「全班」= `names` + 全选，不新增枚举值）；
  旧数据 `source=prev` 且本机无最近抽签时给「还没有抽签结果，先抽签或换成员来源」提示，不崩溃；未引入新依赖。
- **验证**：`testDebugUnitTest` **1890 用例 / 0 失败 / 2 skipped**（新增 `DrawLastResultStoreTest`、`GroupSourcesTest`、
  `DrawGroupSeparationTest`；删除结果单测试；更新分组快照文案与使用说明期望）。
- **发布**：beta53 / vc136，APK **6,124,166 B / SHA-256
  `30486a1a7aa325ecc80e3364c544ca6a957fe0cddc7a2fd09d98267f4ea65c0c`**；ECS 与 OSS 的 `latest.json` 均回读
  `versionCode=136 / 1.0.0-beta53`，APK 均 HTTP 200。
- **并发说明**：本版包含并行会话已提交的 `d749589`（glass / beta52 vc135）；为避开发版撞号，本版顺延为 beta53 / vc136。
  首次 `assembleRelease` 时 OSS `latest.json` 被并行会话写成 beta52 导致末尾校验失败（APK 已上传），
  重跑 `:app:publishUpdateChannel` 后 ECS/OSS 双通道一致。

## 2026-11-22 · 1.0.0-beta48 / vc131：工具顺序 / 首页勾选跨端同步修复（体测、名单换端不再丢）

> 用户反馈：「自定义顺序和自定义首页的云同步似乎失效，我在安卓端的排序无法同步到小程序」；
> 小程序侧同一时间出现「部分工具无法勾选到首页」。

- **根因（跨端 id 不一致，主因）**：userdata v6 的 `homeTools` / `prefs.toolOrder` 是五端共用的同一份数组，
  但安卓本机 id 是体测 `fit`、名单 `roster`（另有独立 `group` 分组），网页是 `ft` / `committee`，小程序是 `ft` / `bz`。
  安卓读写 blob 时把 `ft` / `committee` 当作未知 id 过滤 → 从网页/小程序同步来的**体测、名单会从「首页常用工具」
  与排序里消失**（或被补到末尾），本机再保存时还会把过滤后的数组回推云端，形成「排序同步不过去 / 勾选被改掉」的观感。
- **`data/tool/ToolCatalog.kt`**：新增 `syncIdOf` / `localIdOf` / `bySyncId` / `toSyncIds` / `fromSyncIds`
  （本机 `fit→ft`、`roster→committee`；读入时 `ft/fit→fit`、`committee/roster/bz→roster`，`group` 保持原样）。
- **`features/home/HomeToolsEditor.kt`**：`apply` 写 canonical id（≤4 截断在映射后），`read` 映射回本机 id。
- **`ui/screens/all/ToolOrderLogic.kt`**：`ToolOrderPrefs.read/apply` 同样走映射，顺序数组不再丢体测/名单。
- **`ui/navigation/MainNavHost.kt`**：`homeTools` section 先 `bySyncId` 再渲染首页常用工具，兼容网页/小程序写入。
- **测试**：新增 `ToolCatalogSyncIdTest`（4 条）、`HomeToolsEditorTest`（4 条），`ToolOrderLogicTest` 补 2 条跨端映射用例；
  `testDebugUnitTest` **1883 用例 / 0 失败 / 2 skipped**。
- **发布**：beta48 / vc131（`assembleRelease` 自动推送更新渠道）。
- **未改**：userdata v6 顶层键 / 11 section / 合并算法；鸿蒙端仍按本机 id 读写同一数组，后续可用同一映射对齐。

## 2026-11-22 · 1.0.0-beta32 / vc115：OPPO 推送 AppSecret 大小写修复（错误码 16）

> 用户提供 OPPO 开放平台凭证后核对发现：**客户端内置 AppSecret 与开放平台原文大小写不一致**。

- **根因**：`app/oppo.properties` 的 `oppo.appSecret` 为 `BCF077F313184549a21b09c3b3f36052`（前 8 位大写），
  而开放平台 AppSecret 为 `bcf077f313184549a21b09c3b3f36052`（全小写）。OPPO SDK 在 `register(appKey, appSecret)`
  后用**原样字符串**参与签名计算，服务端按注册时的 secret 校验 → `INVALID_SIGNATURE_SIGN`（错误码 16）。
  账号服务（ECS `/account`）与 `briefdesk/secrets/oppo-push.json` 的 app_secret 已同步为小写。
- **发布**：beta32（vc115）已上线 ECS+OSS；APK **6,091,398 B / SHA-256
  `d934dce41be005e64c4cc776bfbc5b5e7027359bf384b3f0b8c432d2fd538751`**，`latest.json` 回读 vc115 一致；
  全量单测 **1870 用例 / 0 失败 / 2 skipped**。
- **账号绑定（同步排查）**：ECS 账号服务健康、`/opt/class-tools/account/oppo-account.json` 的
  app_id/app_key/app_secret 与开放平台一致；日志只有 `GET /oppo/bindings` 轮询，**没有 `/oppo/exchange` 请求**，
  说明绑定在手机端 OPPO 账号 SDK 授权阶段就失败了（未到服务端）。直接调用 OPPO 账号
  `/oauth2/token/token-code`（appKey/appSecret + dummy code）仍返回 `2020003 invalid_client`
  （大小写两种都是），需在开放平台「产品 → 账号服务」申请接入/激活并申请 openid 等能力；同时账号 SDK
  也校验应用签名，签名登记修好后需一并复测。

## 2026-11-21 · 1.0.0-beta29 / vc112：OPPO 推送 MCS 接收权限 + 推送状态自查

> 用户反馈：「手机为 ColorOS（OnePlus PYB110 / Android 17），收不到通知」；真机安装修复包后
> 「通知设置 → 系统推送」显示 **OPPO 推送：注册失败（错误码 16）**。

- **根因 1（已修）**：`AndroidManifest.xml` 只声明了 OPPO 回调 Service，漏掉官方接入要求的两个 MCS 接收权限
  `com.coloros.mcs.permission.RECIEVE_MCS_MESSAGE` / `com.heytap.mcs.permission.RECIEVE_MCS_MESSAGE`。
  ColorOS/一加/realme 的系统推送服务通过它们投递注册回调与通知；缺失时 `register` 拿不到 RegistrationID
  （云端 `push_tokens` 长期 0 条 `provider=oppo`）。已在 Manifest 补齐（`aapt2 dump permissions` 可见）。
- **根因 2（控制台，需用户在 OPPO 开放平台改）**：补权限后真机进入签名校验并返回 **16 = Invalid Signature**。
  OPPO「应用签名」需要填**证书 MD5**：本仓库 release 证书为 `fd87bb879e78fc7c8c39dc9bdf8b2ef5`
  （此前文档误按「SHA-1 前 32 位」核对，口径错误）。控制台填对后重开 App 即可注册成功。
- **新增自查能力**（`data/push/PushStatusDiag.kt`）：
  - 「我的 → 通知设置」新增「系统推送（应用关闭后）」卡片，展示 OPPO / vivo 的注册状态与错误码
    （未配置 / 本机不支持 / 注册中 / 已注册（…末 4 位）/ 注册失败（错误码 N））；
  - 登录后自动向 `client_diag` 上报一条 `check=push_status`（只含状态与错误码，不含 token/学号），
    可远程排查，不必截图或连 adb；
  - OPPO 错误码文案修正：16 = 签名无效（APK 签名与开放平台登记不一致），不是 AppSecret 问题。
- **验证**：修复包（beta26 基础上）真机已从「无 regId」推进到「签名校验 16」，证明权限链路生效；
  本版在 beta28（vc111）基础上顺延为 **beta29（vc112）**，避免被更新渠道覆盖。
- **真机复核（远程诊断）**：手机在 beta26 修复包上自动上报 `client_diag`：`oppo=FAILED:16 vivo=UNKNOWN:0`
  （`check=push_status`，2026-11-21 22:05/22:06 两条），与界面一致 → 权限链路已生效，只剩控制台签名。
- **发布**：beta29（vc112）已上线 ECS+OSS；APK **6,075,014 B / SHA-256
  `024d43a549aa9a99dcc040bb411bffe2cf9fce3a9101a15221a245de940d6dc6`**，`latest.json` 回读 vc112 一致，
  ECS/OSS 的 `latest.json` 与 APK 均 HTTP 200。全量单测 **1869 用例 / 0 失败 / 2 skipped**。
- 文档：`docs/OPPO_PUSH.md` 补充 §11 修复记录与 §11.1 错误码 16 处置。

## 2026-11-21 · 1.0.0-beta27 / vc110：宿舍目录改为云端读取（修复「部分宿舍无法显示」）

> 用户 2026-11-21 反馈：「修复安卓端无法显示部分宿舍的问题」。
> 本版与并行会话的 beta26（M3 组件替换）基于同一 master（`1a1ca91`），版本号顺延为 beta27/vc110，避免覆盖 beta26 的渠道清单。

### 一、根因

- 电费/水量页的宿舍目录原本是**客户端硬编码常量**。2026-11-21 的「公开仓库脱敏」提交（`3bc0b99`，混在 docs 提交里）
  把 17 间真实宿舍整段替换成虚构房号（13号楼301/302、16号楼303/304、23A 305–313、23B 314–317）。
- 云端 `public.power` 仍然是从完美校园同步的真实 17 间（13号楼308/324、16号楼305/306、23A 321/323–330、23B 422–425）。
  `PowerRooms.normalizeSel()` 只认硬编码目录 → 真实 roomverify 一律被判非法：
  电费页已关注宿舍显示「未选择」、水量页显示「还没有关注宿舍」，选择器里也找不到真实宿舍。

### 二、修复

- **目录改为云端读取**（E21：`GET power?select=id,label,building,room&order=id.asc&limit=200`）：
  新增 `data/power/PowerDirectoryRepository.kt`（`DefaultPowerDirectoryRepository`：5 min 内存缓存、失败回退旧缓存、
  空目录不写缓存）。**客户端不再内置任何真实房号**，班级增删宿舍只改云端，无需发版，也符合「公开仓库不含宿舍号」约束。
- **展示名由 roomverify 派生**（`PowerRooms`）：`1-13---324` → `13号楼324`、`4-23B---423` → `23号楼B座423`。
  因此已关注宿舍**不依赖目录**也能显示，目录加载失败只影响「换宿舍」。
- **选择器三态**：电费 BottomSheet / 水量对话框增加「加载中 / 加载失败（可重试）/ 空目录」；
  水量选择器 = 目录 ∪ 本机已有抄表记录的宿舍（目录失败时仍能切换已记录宿舍）。
- **历史脏数据**：云端 `power.sel` 若是旧版本写入的虚构房号，目录加载后回到「未选择」并提示
  「原关注宿舍已失效，请重新选择」，**不回写云端空值**（避免误清其他设备的真实选择）。
- **排序**：楼栋自然序（13号楼 → 16号楼 → 23号楼A座 → 23号楼B座）+ 房间号数字序，与网页/小程序展示顺序一致。
- 接线：`AppContainer.powerDirectoryRepository` → `MainNavHost` → 电费 / 水量页。

### 三、测试与文档

- 新增 `PowerRoomsTest`（11 个：roomverify 解析 / 非法值 / 规范名派生 / 目录解析去重排序 / 搜索 / contains / merge）
  与 `PowerDirectoryRepositoryTest`（7 个：请求路径 / 排序 / 5 min 缓存 / force / 失败回退 / 空目录不缓存 / clearCache）。
- `:app:testDebugUnitTest` **1864 用例 / 0 失败 / 2 skipped**（148 suites；其中电费目录相关 `PowerRoomsTest` 12、`PowerDirectoryRepositoryTest` 7）。
- 文档：`docs/BACKEND_CONTRACT.md §7.5`、`docs/api/ENDPOINTS.md`（新增 E21）、`docs/api/TT_POWER_IMPL.md`
  同步为「目录云端读取 + roomverify 派生」。

### 四、发布

- `assembleRelease` 在**独立 worktree**（提交 `eeecf38`）干净构建，避开并行会话未提交改动：
  APK **6,074,882 B / SHA-256 `406eedb8b8345ff44a0f856618975bd4fce3998e8f0e1cd0218e47b8863f04ee`**（beta27 / vc110）。
- 渠道清单回读 `versionCode=110 / versionName=1.0.0-beta27`、sha256 与 APK 一致；ECS
  `http://121.196.47.2/android/latest.json`、OSS `android/latest.json` 与 APK / APK.bin 均 HTTP 200
  （脚本日志 `ECS_ANDROID_OK`）。
- 真实目录接口核验（测试身份 `3260799999`，只读）：`GET power?select=id,label,building,room&order=id.asc&limit=200`
  返回 17 间宿舍（13号楼308/324、16号楼305/306、23A 321/323–330、23B 422–425），排序与网页/小程序一致。
- 双远端：GitHub `origin` 与 Gitee `gitee` 均指向 `eeecf38`（GitHub 直连失败时按 `docs/GitHub推送兜底.md` 临时代理推送）。
- 说明：beta26/vc109 由并行 M3 会话发布（本版已顺延版本号，未覆盖其清单）；本版 APK 从同一 `master` 的干净提交构建。


## 2026-11-21 · 1.0.0-beta22 / vc105：通知模块化（对齐鸿蒙）+ 统一分享弹层（对齐小程序）

> 用户要求：「参考鸿蒙端增加安卓端的可进行系统通知的工具以及内容，参考微信小程序端对安卓端的分享功能进行增加和优化」。

### 一、通知（对齐鸿蒙 `features/notify` + 「通知设置」面板）

- **设置模型 v4**：`ReminderSettings` 从 3 个总开关（作业/课表/通知）扩展为**总开关 + 10 个模块**：
  新作业 / 作业即将截止 / 上课 / 新通知 / 活动截止 / 活动开抢 / 新投票 / 投票结果 / 水费扣除 / 每日汇总（安卓保留）；
  截止类统一提前 `leadMinutes`（10/30/60/120/1440 分钟，默认 60），上课提前 `classLeadMinutes`（5/10/15/30，默认 10）。
  旧版本键自动迁移（作业/课表/通知总开关 + 提前天数/分钟），升级不丢偏好；设置仍只存本机。
- **新增类通知**：新增 `ReminderSnapshot` + `ReminderSnapshotStore`（SharedPreferences JSON）。首轮只建基线**不推送**，
  之后对比新增作业/通知/投票才提醒；权限缺失时也建基线（授权后不会把历史消息当新增）；只存 id 与一个水费指纹，最多 300 条。
- **新增规划内容**（对齐鸿蒙文案）：`新作业`（含截止时间）、`新通知`（发布人 · 标题）、
  `活动开抢提醒`（报名前 5 分钟）、`新投票`、`投票结果可查看`（结束后 / 截止后 1 分钟）、
  `水费扣除提醒`（只在本机按水量记录算用量/水费/折合扣电）；上课提醒改为鸿蒙文案「上课提醒 · 课程」+「还有 N 分钟」。
- **通知渠道/分组**：新增 `vote_alert`（投票）、`water_alert`（水费）、`ct_test`（测试）渠道与对应通知组；
  点击跳转白名单新增 `vote` / `water`（直达投票页 / 水量页）。
- **测试通知工具**：`ReminderEngine.testNotification()` / `ReminderActions.testNotification(context)`，
  固定内容「班级工具站 · 测试通知」，三态结果（已发送 / 权限缺失 / 发送失败）。
- **设置页重做**（我的 → 通知设置，结构对齐鸿蒙 `notifyPanel`）：允许通知总开关（含系统权限状态与「去开启权限」）/
  作业提醒（3 项）/ 上课提醒 + 提前胶囊 / 通知提醒（3 项）/ 投票与水费（3 项）/ 提醒时间胶囊 /
  **发送测试通知** / 立即检查 + 诊断 / 说明；「我的」页入口文案改为「已开启 N 项 · 截止提前 X」。

### 二、分享（对齐小程序 `share.js` / `sharecard.js` / `shareflow.js`）

- **统一分享弹层** `ShareSheet`：5:4 卡片预览 + **分享图片**（系统分享面板，`image/png` + 标题链接）+
  **保存图片**（Android 10+ 相册）+ **分享链接**（纯文本）+ **复制文案**（无分享应用兜底）；
  分享图写 `cache/share/`（FileProvider 新增 `share/` 路径），不联网、不上传。
- **5:4 分享卡**：`ShareCardPainter`（逻辑 500×400，布局与绘制分离、可单测）+ `ShareCardRenderer`（×2 → 1000×800 PNG），
  结构对齐小程序：品牌三点 + 徽标 / 标题（≤2 行）/ 副标题 / 状态胶囊（≤2）/ 统计块（≤3）/ 关键字段（≤2）/
  信息行（≤6）/ 备注 / 页脚。
- **接入入口**：抽签 / 分组 / 体测 / 课表 的「导出」改为**分享弹层**（内容由既有导出卡自动派生，导出实现不变）；
  通知详情底栏新增「分享」（标题/分类/状态/摘要/至多 3 条要点/时间·地点字段/来源/发布者/附件数）；
  投票详情顶栏新增「分享」（**固定不展示票数与百分比**，提示「结果请打开投票页查看」）；
  我的页新增「分享给同学」（班级工具站介绍卡）。
- **分享链接**：`<WEB_BASE_URL>/tools/index.html#tab=<toolId>`（与网页 `gotoTab` 的 tab id 一致），
  分享文案 = 标题 + 一句话 + 链接。
- **隐私口径**：投票分享输入结构上不含票数/百分比；名单/体测/课表只含班级内已展示信息（无学号/手机号/宿舍号）。

### 三、文档与测试

- 新增 `docs/api/NOTIFICATION_V4_IMPL.md`、`docs/SHARE_IMPL.md`；文档记录模块表、内容文案、迁移与隐私口径。
- 单测：`ShareSpecsTest` 15 个（链接/导出卡派生/通知字段优先级/投票不出现结果/绘制层截断/文件名）；
  通知侧 `ReminderPlannerTest` / `NoticeReminderPlannerTest` / `ReminderEngineTest` / `ReminderOneShotTest` / `MeStatsSectionTest` 同步更新；
  `:app:testDebugUnitTest` **1844 用例 / 0 失败 / 2 skipped**。

### 四、发布

- 双远端已推送（GitHub `origin` + Gitee `gitee`，HEAD 一致）；`assembleRelease` 自动发布 beta 渠道。
- **beta22（vc105）已上线 ECS+OSS**：APK **6,097,875 B / SHA-256 `658552aa97c8fc7d9afb903e7dba97f1465761f5ad4bef15413845d079ed24be`**，
  `latest.json` 回读 versionCode=105、sha256 一致，ECS/OSS 两处 `latest.json` 与 APK 均 HTTP 200。


## 2026-11-21 · 1.0.0-beta21 / vc104：电费「上次充值」跨区间查找并显示充值量

> 用户要求：「修复电费上次充值只会在选择区间内查找的问题，同时优化安卓端的电费曲线，上次充值应该改为显示上次的充值量」。

- **根因**：电费页第 4 个指标 tile「上次充值」在实时房取 `deriveRange(所选区间).lastUp`，只扫描当前 24h/7d/30d 档；
  充值发生在所选区间之外时显示「—/暂无记录」。同时值显示 `lastUp.reset`（充值后余额），不是本次充值量。
- **数据**：`power_history` 云端只保留 30 天（`sync_power_cloud.py` `HISTORY_KEEP_DAYS=30`），因此电费页改为**始终加载 30d**
  （=全部可用历史；实时房由 2 个历史请求变 3 个，5 分钟缓存与失败回退不变）。
- **算法**：新增 `PowerStats.lastRecharge(points)`：按时间升序扫描最后一次 `Δq>0.05`，返回 `{t, reset, delta}`；
  `slowStats` / `slowUpdate` 的 `lastRecharge` 同步补 `delta`（慢频房继续用 30d 真实变化点，避免日更采样噪声）。
- **展示**：tile4 值改为 `+<delta>` 度（充值量），副标保持 `MM-dd HH:mm`；切 24h/7d/30d 不再改变；无记录「—/暂无记录」；
  30d 未加载/失败或预览场景回退旧区间口径，不出现空白。
- **真实数据核对（测试身份只读）**：17 间宿舍 30d 各 684 条采样（无重复时间戳），7 间存在上升；
  如 23号楼A座310 +187.19（11-17 14:46）、13号楼301 +68.16（11-11 10:01）、16号楼303 +174.27（11-11 10:01）均在 24h 之外，
  旧口径在 24h 档只能显示「—」。
- **验收**：`PowerStatsTest` 31 用例（新增 lastRecharge 跨区间、slowStats.delta、tilesOf 充值量/回退三组断言）；
  `:app:testDebugUnitTest` **1803 用例 / 0 失败 / 2 skipped**；`assembleRelease` 成功。
- **发布**：beta21（vc104）已上线 ECS+OSS；APK **6,065,079 B / SHA-256 `ab0f13b482d1e8d364dcb6cc91b4be9ff23564fc45f779f51a10b687bf3086b6`**，`latest.json` 回读一致（ECS+OSS 均 HTTP 200）。


## 2026-11-21 · 1.0.0-beta20 / vc103：诊断页间距按鸿蒙「间隔参数」补齐

> 用户 2026-11-21 反馈「诊断页的间距还是有问题」。诊断页上一轮未纳入控件间距收敛，本版逐项补齐。

- **摘要卡与列表**：`SummaryStrip` 之前与下方第一张卡片**零间距**（两张卡圆角互相压住），补 12vp（`CtSpace.sm`）；
  摘要卡内「最后更新 …」与上一条标题之间补 4vp（`CtSpace.xxs`）。
- **卡片间距**：诊断页卡片列表由 `10.dp` 改为 `CtSpace.sm`（12vp）——原值不在 4/8 栅格上，
  也与其它工具页（8/12）不一致。
- **卡片标题与正文**：`DiagCard` 标题行与内容列之间由 0 改为 8vp（`CtSpace.controlGap`），
  内容行间距 6→8vp；标题 / 状态胶囊 / 右上角操作（如「注意 + 重新检测」）之间也统一 8vp。
- **检查行按钮**：`DeviceCheckBlock` 的「上报」文字按钮原与状态胶囊贴死（0vp），补 8vp。
- **顶栏动作**：代理胶囊 / 简洁·详细 / 暂停 / 刷新 四个动作由 4vp（`CtTopBar` 默认）改为 8vp，
  仅在诊断页生效（外层包一层 `Row`），不影响其它页面顶栏。
- **验收**：AOSP API 37 模拟器（411dp 宽）实拍 `docs/design/shots/diag-before.png` →
  `diag-after.png`（摘要卡/卡片间/标题-正文/上报按钮四处肉眼可见拉开，排版无溢出错行）；
  `:app:testDebugUnitTest` **1802 用例 / 0 失败 / 2 skipped**；`:app:assembleDebug` / `:app:assembleRelease` 均成功。
- **发布**：beta20（vc103）已上线 ECS+OSS；APK **6,065,079 B / SHA-256
  `851FDCE44E71C92830BA5C21EA4F163D101B0045A885E87C80D80874D9D6BE11`**，
  `latest.json` 回读 `versionCode=103 / versionName=1.0.0-beta20` 且 sha256 与本地包一致。

## 2026-11-20 · 1.0.0-beta19 / vc102：金标联盟 SharedView Kit 一镜到底（工具卡 → 工具页）

> 用户 2026-11-20 要求：按 `https://gitee.com/itgsa_sharedviewkit/SharedViewDemo` 配置金标联盟手机的相关动效。

- **依赖**：接入官方 Nexus `gsai.sdk:sharedview-kit:1.0.0.5`（`settings.gradle.kts` 仅对 `gsai.sdk`
  放行内测期 HTTP 仓库；AAR 自带 R8 consumer 规则）。运行时只按
  `ViewAnimationManager.isViewAnimationSupported()` 判断，不按品牌/系统版本硬编码。
- **路径**：新增不导出的 `ToolActivity`，首屏直接 `tool/{id}`；点击工具卡时用 **Bitmap 源 + ActivityOptions**
  启动，返回前 `overrideViewAnimationTransition` 回原卡片 bounds。目标 Activity 与 MainActivity 共用
  `ClassToolsThemeHost`（主题/背景/系统栏），Session 由显式 Intent 传入，不落盘、不写日志。
- **参数**：半径 dp×density 转 px（24dp 全部工具卡 / 14dp 首页快捷卡）；不传 `color`（动态壁纸无稳定底色）；
  bounds 坐标系按 `getViewAnimationExtras()` 的 `WINDOW`/`SCREEN` 动态选择；半遮卡片、无锚点、截图失败、
  Kit 返回 null 全部回退原 Compose 容器变换；源 Activity 销毁/旋屏时不 override，避免落点错位。
- **导航**：ToolActivity 内「课表 → 地图」等 push 使用统一页间转场，返回逐层 pop，最后一层退出 Activity；
  新增 `standaloneTool` 顶层 BackHandler 兜底；独立宿主不重复拉取根页数据，减少首帧阻塞。
- **测试**：`:app:testDebugUnitTest` **1802 用例 / 0 失败 / 2 skipped**（新增 `SharedViewBoundsTest` 7 例）；
  `:app:assembleDebug` 成功。AOSP API 37 模拟器 `protocol=0`，已验证降级 Compose 转场与独立
  ToolActivity 的打开/嵌套/返回；**真实金标联盟机型的系统动画需真机验收**（模拟器不支持 Kit）。
- **文档**：`docs/SHAREDVIEW_MOTION.md`（接入原理、参数口径、验收步骤、已知限制）。
- **同树并行改动一并纳入**：工具页相邻可点控件间距统一到 `CtSpace.controlGap`（≥8vp），覆盖体测/点名/
  通知/名单/课表/演示/文件传输/身份页；`CtSpacingTokensTest` 新增 8/12/16 三档断言。详见 `docs/DESIGN_SYSTEM.md` §13.4。
- **发布**：beta19（vc102）已上线 ECS+OSS；APK **6,065,079 B / SHA-256
  `1FE2FBAC29E848AF8B0F8390F698C1CDF12F10A643EB218A035A4282E781A6AF`**，`latest.json` 回读一致；
  release 包在 AOSP API 37 模拟器安装/启动成功（版本行 v1.0.0-beta19）。

## 2026-11-20 · 1.0.0-beta18 / vc101：荣耀账号 + vivo 推送 + OPPO 推送/账号复核（含 beta15 统计、beta16 沉浸光感、beta18 顶部悬浮重做）

> 用户 2026-11-20 要求：按 `honorid-opensdk-8.0.1.351.aar` 接入荣耀账号 OAuth；按 AppID/AppKey/AppSecret 接入 vivo 推送；再次检查 OPPO 账号服务与推送服务。
> 本版同时包含 beta15 访问统计可视化、beta16 沉浸光感修复与 beta18 顶部悬浮重做（并行会话已把版本号推到 beta18/vc101；本版在同一棵树上叠加荣耀/vivo 并统一发布）。

### 荣耀账号（新增）
- 客户端：本地 AAR `honorid-opensdk-8.0.1.351.aar`；`HonorAccountConfig/HonorAuthSdk/HonorAccountManager`；
  `SignInOptionBuilder(clientId/scope/openid/profile, requireAuthCode)` → `getSignInIntent` → `parseAuthResultFromIntent`
  → authCode → ECS `/honor/login|bind|bindings|unbind`；身份页「荣耀账号快捷登录」+「我的 → 账号 → 荣耀账号」弹层；
  仅荣耀设备（`MANUFACTURER=HONOR` 或 `isHonorMachine`）展示；Client Secret 只在服务端。
- 服务端：`account_app.py` v1.1 增加 `/honor/*`（token 端点 `hnoauth-login.cloud.honor.com/oauth2/v3/token` + `GOpen.User.getInfo` 取 openID）；
  `ecs_account.py` 上传 `honor-account.json`；已部署，`/account/__health` providers.honor=true。
- DB：`account_bindings.provider` 放开 `honor`（每学号每厂商最多 3 个）。
- 详见 `docs/HONOR_ACCOUNT.md`。

### vivo 推送（新增）
- 客户端：本地 AAR `vpush_clientSDK_v4.1.7.0_517.aar`；`VivoPushConfig/Sdk/Store/Receiver/Manager`；
  Manifest `app_id/api_key` + `VivoPushReceiver` + `classtools://com.furina.classtools/notice` deeplink；
  登录后（已同意隐私）才 `initialize(agreePrivacyStatement=true)` → `turnOnPush` → `getRegId` → 上传 `push_tokens(provider='vivo')`；AppSecret 只在服务端。
- 服务端：`scripts/vivo_push.py`（auth + `/message/send`，skipType=4 intent URI 点击直达通知详情）；
  统一推送巡检 `push_tick_all.py`（harmony→oppo→vivo），`ClasstoolsPushWatch` 已改指向它。
- DB：`push_tokens.provider` 放开 `vivo`。
- 详见 `docs/VIVO_PUSH.md`。

### OPPO 复核
- **推送**：AppKey/MasterSecret 鉴权成功；发现唯一推送任务此前只跑鸿蒙、`oppo_push.py` **从未被调度** → 已并入统一巡检；
  云端当前 0 台 OPPO token（无 ColorOS 真机登录），需真机验证到达/点击。
- **账号**：ECS 服务 configured=true，但账号服务 token 端点用现有 appKey/appSecret + 无效 code 返回 `2020003 invalid_client`
  （同一套密钥在推送侧有效）→ 账号服务 AppKey/AppSecret 是另一套或服务未开通；需在 OPPO 开放平台补齐并登记包名+签名。详见 `docs/PUSH_ACCOUNT_RECHECK_20261120.md`。

### beta15 访问统计可视化（本次一并发布）
- **契约**：`web/统计多端_详细统计_契约_20261120.md`（新增 `visits.ver/dev` 两列）。
- **数据**：`VisitRow` 增 `ver/dev/did`；`VisitReporter` 写版本与 `品牌 机型 · Android 版本`；`VisitsRepository` 查询/解析对应列。
- **统计页**：摘要 4 格（账号/次数/今日活跃/设备）、24 小时/近 7 天趋势、登录端堆叠条+图例、版本/设备分布、账号明细可展开；不显示学号/did；非管理员不发请求。
- **测试**：全量 `:app:testDebugUnitTest` **1794 用例 / 0 失败（2 skipped）**（含访问统计 51 + 荣耀 20 + vivo 8 新增）。
- **发布**：更新渠道 beta18（vc101）已上线：APK **6,064,999 B / SHA-256 `BFCD0EC034C5ABDB2467D8EEEEE6820CAF507479FF40BC3A0E2BEB5F897A3AC3`**（ECS+OSS 回读一致；apkanalyzer 确认包含 vivo PushClient / HonorIdSignInManager / VivoPushReceiver 与 vivo AppID/API Key）。

## 2026-11-19 18:0x · 1.0.0-beta10 / vc93：宿舍人数 1–6 自选

- **需求**：用户要求「优化宿舍人数为 6 人以下自选」。原 `PERSONS_OPTIONS=[4,6]`、`WaterNormalizer` 与 `PowerStats.billOf` 把非 4/6 一律回落 4，23A/23B 固定四人间。
- **实现**：
  - `WaterRules.PERSONS_OPTIONS` 改为 `1..6`；`WaterCodec.normalizePersons` 只接受 1–6，非法值回落 4。
  - `WaterNormalizer` 远端 `p` 接受 1–6；`PowerStats.billOf` 免费额度按 1–6 计算，不再强制 4/6。
  - 水量页人数 chips 分两行展示（1–6），窄屏不横向溢出；标题/范围文案与工具卡副标题同步。
- **下线约束**：23A/23B 固定四人间取消，所有宿舍均可 1–6 自选；云端 `water` / `water_room` 的 persons CHECK 已迁移为 1–6（`deploy/cloudbase/2026-11-19_water_persons_1_6.sql`）。
- **验证**：`:app:testDebugUnitTest` 通过（WaterLogicTest 增加 5 人合法 / 9 人回落）；`:app:compileDebugKotlin` 成功；beta10 APK 5,820,541 B / `31B09D78…`；ECS+OSS `latest.json` vc93。
- **发布**：`assembleRelease` 自动上传 beta10（vc93）完成。

## 2026-11-19 14:0x · 1.0.0-beta9 / vc92：鸿蒙间隔参数落地（16/24/32 + 12/16/8/2）

## 2026-11-19 14:0x · 1.0.0-beta9 / vc92：鸿蒙间隔参数落地（16/24/32 + 12/16/8/2）

- **屏幕边距**：手机 16dp / 折叠 24dp / 平板 32dp（`HarmonyTokens.PagePadding*`、`CtSpacing`）。
- **卡片**：卡片间 12dp；卡片内边距 16dp（`HarmonyTokens.CardPadding`、`CtTokens.Spacing.cardPadding` 同步 14→16）。
- **元素**：大间隔 16dp / 普通 8dp；主次文本纵 2dp / 横 8dp（新增 `ElementGapLarge/ElementGap/TextGapHorizontal` 与 `CtTokens` 对应字段）。
- **页面收敛**：电费卡片/图表/文本、首页 hero/工具卡、我的页分组标题按上述值替换；删除 18/14/10 等不在语义档位的关键间距。
- **验证**：`HarmonyTokensTest` 增补 token 断言；`:app:compileDebugKotlin` 成功；`:app:testDebugUnitTest 1654/0（2 skipped）`。
- **发布**：`assembleRelease` 自动上传 beta9（vc92）到 ECS+OSS；APK 5,820,537 B / `23D0F4AF…`；ECS/OSS `latest.json` 均为 vc92。

## 2026-11-19 13:4x · 1.0.0-beta8 / vc91：所有版本发布自动上传更新渠道

## 2026-11-19 13:4x · 1.0.0-beta8 / vc91：所有版本发布自动上传更新渠道

> 用户原话：「安卓所有版本发布都要自动上传更新渠道」。本条把发布上传固化为构建流程，并发布当前版本。

- **自动上传**：`app/build.gradle.kts` 注册 `publishUpdateChannel`，`assembleRelease` 完成后由 `finalizedBy` 自动执行；默认开启，调用 `web/deploy/android_release.py`：
  - 计算 APK SHA-256 / size，生成 `latest.json`；
  - 上传 OSS `android/classtools-<version>.apk.bin` + `android/latest.json`；
  - 通过 ECS 云助手从 OSS 拉取 APK 到 `/var/www/tool/android/<version>.apk`，写 ECS `latest.json`；
  - 回读 ECS/OSS JSON、APK 校验 200 与 sha256；同版本同 sha 幂等跳过（`--force` 可重传）。
- **可选参数**：`-Pct.updateNotes="..."` 发布说明、`-Pct.updateChannel=beta`、`-Pct.skipUpdateChannel=true` 仅编译不发版、`-Pct.python=...`。
- **当前发布**：`1.0.0-beta8 / vc91`，release APK **5,820,537 B / SHA-256 `4E01872E79BA786AFBC32254B4CFA937B400EBFD7CAE186F6FD212660E549D58`**；ECS/OSS latest.json 与 APK 均已 HTTP 200 且 sha 一致；`apkUrl` 指向 OSS HTTPS `.bin`，ECS 同步落 `.apk` 供手动下载。
- **验证**：`:app:assembleRelease` 成功并自动上传；`:app:verifyReleaseEndpoints` OK；`:app:testDebugUnitTest` 仍 **1654/0（2 skipped）**。
- **回滚**：把 `latest.json` 改回上一版本（beta7 / vc90 / `67926229…`）并重传；APK 文件保留不删即可。

## 2026-11-19 · 通知列表 P0 收口（light meta 优先 + 懒加载附件 + 取消后台翻页）+ release 4 个 ECS 子系统可用性修复（源码；未发版，版本仍 beta7/vc90）

> 承接 Lead 网络审计：通知页慢/错误率高的主因是 `notices?select=*` 分页拉整表（每页 ~0.55MB、后台自动翻完 ~28 页 / 14MB、单页 40s 超时 + 重试）；release 的账号/云空间/更新/地图仍指向 NXDOMAIN `tool.furina.fun`。详见 `docs/REPORT_ROUND70_20261119.md`。**未改版本号、未加依赖、未碰 keystore、未提交 git。**

- **通知列表改走 `POST rpc/class_notices_meta`**（与网页/小程序同契约）：服务端 `notice_lite_body()` 把 `items[].url/src`、`archive.files[].url/src` 的内联 data URI 置空并写 `lazy=true`，整表 14MB → **~0.3MB**；一次拿全量 → 不再分页。**接口不存在/无权限（400/401/403/404/405/501）自动回退旧分页链路**（保留原分页/渲染/已读逻辑），回退后不再重复探测（`force` 刷新允许再探一次，云端部署后可自动切回）。
- **取消「后台自动翻完所有页」**：删除 `NoticesAutoLoader`（含 6 项单测与 UI 循环）。只有下拉刷新或底部**「加载更早的通知（还有 N 条）」**才按需加载一页；OPPO 推送定位未命中时也只加载一页。
- **详情懒加载**：新增 `NoticesRepository.loadRow/hydrateRow`（`notices?select=*&id=eq.<rowId>` 拉单行完整 body，合并回缓存，重开不再请求）；打开详情时触发一次，失败仅提示且图片仍有逐张按需加载兜底。`NoticeItemMeta.Image/File`、`NoticeArchiveFile` 新增 `lazy` 标记。**启动不预取任何附件**。
- **超时 40s → 25s**，超时最多重试 1 次（不无限重试）。`ApiClient` 新增 `notifyTokenInvalid`（默认 true，主链路语义不变）：light meta 探测遇 401/403 **不清会话**。
- **release ECS 地址修复**：新增 `ct.ecsProfile`（`ecsIp`（默认，备案暂停期）| `selfhosted`）；release 的 `ACCOUNT_BASE_URL` / `CLOUDSPACE_BASE_URL` / `UPDATE_BASE_URL` / `MAP_BASE_URL` / `WEB_BASE_URL` 由 `https://tool.furina.fun`（NXDOMAIN）改为 `http://121.196.47.2/...`；`network_security_config.xml` **只放行这一个固定 IP**（其余域名仍禁明文）；`:app:verifyReleaseEndpoints` 改为按档位校验（REST 永远 https + 无明文 IP；`ecsIp` 要求 5 个 ECS 地址落在该 IP 并打印 WARN；`selfhosted` 保持严格 https）。备案恢复后 `-Pct.ecsProfile=selfhosted` 一键回滚。
- **401/403 分类**：主链路保留现状（401/403 → TokenInvalid + 清会话）；建议（未做）把 403 细分为 `ApiError.Forbidden`（不清会话，仅禁用该功能），属协议级调整。
- **测试**：基线 **1652/0（2 skipped，134 suites）** → 改后 **1654/0（2 skipped，134 suites）**。新增 `NoticesMetaTest` 10 项（meta 一次拿全量 / 不再发分页请求 / `loadMore` 不发请求 / 404 回退 / 403 不清会话 / 坏 JSON 降级 / lazy 标记 / `hydrateRow` 单行合并 / 行不在列表返回缓存 / `loadRow`）；`NoticesRepositoryTest`、`NoticesPaginationTest` 显式 `metaEnabled=false` 继续守旧链路；`NoticesAutoLoaderTest` 删除。
- **构建**：`:app:assembleDebug` 成功（`app-debug.apk` 29,522,153 B / `CFAA8738…`）；`:app:compileReleaseKotlin` 成功（未签名）；`:app:verifyReleaseEndpoints` OK（`REST_PROFILE=cloudbase ECS_PROFILE=ecsIp`）；release `BuildConfig` 实测 5 个 ECS 地址均为 `http://121.196.47.2...`、REST 仍 CloudBase HTTPS。
- **未验证**：本机 `emulator-5562` 无外网，未做通知页真机端到端；debug 的 REST 是 staging 影子网关（可能没有该 RPC → 走回退链路）。

## 2026-11-19 · 界面动效/UI 向鸿蒙对齐 + 隐私协议/用户协议全文入口 + 网络层审计（源码；未发版，版本仍 beta7/vc90）

> 用户 2026-11-19 三项要求：① 按鸿蒙 HomeScreen/AppShell/主题/卡片/列表/页面切换对齐动效与 UI；② 接入已制作好的法律全文（不破坏现有摘要）；③ 网络层低风险审计。详见 `docs/REPORT_ROUND69_20261119.md`。**未改版本号、未加依赖、未提交 git**。

- **令牌校正（鸿蒙源码逐项复核）**：`HarmonyTokens.PagePadding 20→16`（= `Tokens.PAGE_PADDING`，并把折叠屏 24 / 平板 32 落成令牌）、`ContentMaxWidthWide 960→1080`（= `Breakpoints.CONTENT_MAX_WIDE`）、`PageHeaderHeight 60→56`（= `PageHeader.ets`）、新增 `ToolMaxWide/FloatBarSpace/SideBarSpace/SideNavWidth/BreathTopWide/TileGlyph 24/TileTextGap 12/TextGap 2/BadgeBox 30/BadgeGlyph 17/HairlineWidth 0.5`、骨架三色、hero 阴影 `#0F4E9082`；`CtSpacing.screenHorizontal 20→16`。
- **动效契约**：新增 `HarmonyMotion`（FAST 160 / NORMAL 260 / SLOW 380、`cubic-bezier(.4,0,.2,1)`、pageEnter 28vp+0.985、pageExit 24vp+0.99、listItem 10、popIn 0.9、按压 0.97/0.92、骨架 900ms 1↔0.55）；`CtPageTransitions` 新增 `harmonyPushEnter/harmonyPushExit/harmonyPopEnter/harmonyPopExit`，`MainNavHost` 11 个页面路由 + 工具页路由切到鸿蒙口径（工具页 pop 仍由快照收回覆盖层负责）；旧 Material 口径保留。
- **组件/页面**：`AllToolCard` 字形 20→24、图标-文字 10→12、主次文本 3→2、按压加 opacity 0.92；工作台 hero padding 18→16 / 边框 1→0.5 / 下边距 20→12 / 阴影改鸿蒙 `#0F4E9082 y6`；工作台信息卡头部 40dp+15sp → 30dp+17 字形+13sp Medium 次级色+8 间距；`CtTopBar` 标题改 Bold（高度随令牌 56）；底栏图标 22→20、文案 11→10sp；我的页分组标题 13 Regular → 12 Medium 次级色、下间距 6→8。
- **加载骨架**（新 `ui/components/HarmonySkeleton.kt`）：首次加载由「转圈 + 文案」改为两行灰块 + 900ms 呼吸（文案转读屏说明），「已有内容刷新中」仍保留转圈提示。
- **法律全文入口**（新 `ui/screens/privacy/LegalLinks.kt`）：`PrivacyTexts` 摘要保留并升级到与全文一致的 **V1.0 · 生效 2026-11-17**，补齐处理者/邮箱/15 个工作日/第三方与跨境（OSS 中国香港）/保存期限/权利/重大变更重新同意/「以全文为准」；弹层新增**「查看完整版」**（`ACTION_VIEW` 系统浏览器打开 `WEB_BASE_URL + legal/android/{privacy,terms}.html`，不硬编码明文 IP、不新增权限/依赖），失败兜底 = 复制链接 + Toast + 弹层内红字提示；入口在我的页两行副标题、摘要弹层、身份页协议勾选处三处。
- **网络层审计**：超时分级 / GET 单次重试 + 抖动 / 写不重试 / 共享连接池与 Dispatcher / 在途 GET 合并 / TTL 缓存 / 错误映射均判定正常；**修为 2 处**——协程取消不再被 `catch(Throwable)` 吞成 `ApiError.Network`（`execute`/`getTimed`）、在途合并 leader 取消时等待者改为拿 `Failure` 而不是同一个取消异常。另给 4 条「大改只建议」：超时也会重试（最坏 2× 读超时）、仓库 Mutex 跨网络持锁、附件通道 120s、不加 HTTP 缓存。
- **测试**：基线 `:app:testDebugUnitTest` **1632/0（2 skipped，130 suites）** → 改后 **1652/0（2 skipped，134 suites）**，新增 `HarmonyTokensTest` 8、`CtPageTransitionsHarmonyTest` 3、`LegalLinksTest` 7、`ApiClientCancellationTest` 2；`:app:assembleDebug` 成功（`app-debug.apk` 29,522,153 B / `E674B4D2…`）；`:app:verifyReleaseEndpoints` 通过。
- **模拟器实测**（`emulator-5562`，装 debug 包 `…debug`，不动机器上的 release 包）：截图确认工作台 hero/信息卡、全部工具卡、我的页分组标题与协议行副标题、摘要弹层「查看完整版」按钮均按预期渲染；点「查看完整版」成功交给系统浏览器（`mCurrentFocus=com.android.chrome`）。**真机截图暴露并修掉文案 bug**：我的页显示成 `完整版 VV1.0`（`VERSION_LABEL` 内重复 V 前缀），已修 + 单测锁「不得出现 VV1.0」。截图见 `artifacts/round69/`（gitignored）。
- **未验证**：未逐屏回归通知/课表/投票/PPT；转场观感未录屏；release 的全文域名为备案暂停中的 `tool.furina.fun`（debug 的 `121.196.47.2` 实测 200），备案恢复前 release 点「查看完整版」会走失败兜底。

## 2026-11-19 · v1.0.0-beta7 / vc90：修复安卓通知工具收不到通知（release REST 指向备案暂停域名）

> 用户反馈「安卓端通知工具收不到通知」。根因不是通知页本身：beta6 release APK 的 `BuildConfig.REST_BASE_URL` 实测为 `https://tool.furina.fun/v1/rdb/rest/`，而该域名备案暂停、DNS **NXDOMAIN**；release 的登录/通知/数据请求全部失败。此前的 2026-11-18 自建后端切流把 release 默认设成 selfhosted，但备案/HTTPS 尚未恢复。

- **release REST 默认改回 CloudBase 网关**（`https://tool-d4...api.tcloudbasegateway.com/v1/rdb/rest/`），`REST_PROFILE=cloudbase`；备案 + HTTPS 恢复后用 `-Pct.restProfile=selfhosted` 或改回默认。`verifyReleaseEndpoints` 允许 cloudbase profile（仍要求 HTTPS、禁止明文 IP）。
- **通知首屏自适应分页**：`DefaultNoticesRepository.load()` 首屏从固定 20 条改为 20→10→5→3→1 逐级降级（仅在超时时），避免最新页含大附件时弱网 40s 超时只显示缓存；`fetchPage` 成功时清理 `lastPageError`。
- 版本 **vc90 / 1.0.0-beta7 / 测试版本 7**；`UpdateLogicTest` 版本断言同步 7。
- **测试**：`:app:testDebugUnitTest` **1630/0（2 skipped）**；新增「首屏大页超时自动缩小 limit 后成功」用例；`:app:verifyReleaseEndpoints` 通过（REST=CloudBase，其余 ECS 服务仍为 tool.furina.fun）。
- **产物**：`artifacts/classtools-1.0.0-beta7-vc90-release-signed.apk`（**5,804,121 B / SHA-256 `679262294C2A18007E36AC046B9214AABAC4B0A02726CA11E291EC9CCA979958`**）；桌面 `班级工具站_v1.0.0-beta7_安卓.apk` 同字节；模拟器 `adb install -r` 成功、启动 `v1.0.0-beta7` 无 FATAL/ANR。
- **发布**：OSS `android/classtools-1.0.0-beta7.apk.bin`（Content-Type `application/octet-stream`，否则 OSS 以 `ApkDownloadForbidden` 拒绝公开分发）+ OSS/ECS `android/latest.json` → beta7；ECS `/var/www/tool/android/classtools-1.0.0-beta7.apk`。OSS latest 的 `apkUrl` 用 HTTPS OSS（release 不再依赖备案域名）；beta6 的更新检查可经 OSS 兜底发现 beta7。
- **已知限制**：本次只修 REST；release 的账号服务/云空间/地图/PPT 媒体仍指向 `tool.furina.fun`，备案前这些功能仍不可用；更新主 URL 仍为该域名，靠 OSS 兜底。通知工具/登录/作业/通知数据已恢复。

## 2026-11-18 · v1.0.0-beta6 / vc89：统计排序改为「访问次数优先」

> 用户要求：「把排序设成访问次数排序优先，访问次数相同时按访问时间排序」。与网页/小程序/Windows 同口径。

- `VisitStats.build` 排序：`count` 降序 → `lastTs` 降序 → 姓名；统计页说明文案同步「按访问次数排序（次数相同按最近访问）」。
- `ToolCatalog.stats` 副标题/功能点同步；`VisitsRepository` 注释同步。
- 测试：`:app:testDebugUnitTest` **1629/0（2 skipped）**（新增/调整次数优先、同次数按最近访问用例）；`assembleRelease` 成功。
- **产物**：`artifacts/classtools-1.0.0-beta6-vc89-release-signed.apk`（**5,804,121 B / SHA-256 `DD47A30183CDD8B82DC22FB86137042D1DCAF270EB39D818B678720F988B2959`**）；桌面 `班级工具站_v1.0.0-beta6_安卓.apk` 同字节。安装到模拟器启动无 FATAL/ANR。
- 更新通道 latest.json 未改（当前仍 beta4；如需推送更新另发）。

## 2026-11-18 · v1.0.0-beta5 / vc88：新增「统计」页（管理员）

> 用户要求：「安卓也加统计页」。口径与网页/小程序/Windows 一致：默认今天、分钟时间、显示登录端、忽略测试账号；**排序初始为最近登录，beta6 起改为访问次数优先**（契约 `web/统计多端_最新登录_契约_20261118.md`）。

- **入口**：`ToolCatalog` 新增 `stats`（`adminOnly=true`，图标 Insights，分类「系统」）；`AllToolsScreen` 增加 `isAdmin` 过滤，普通成员看不到；首页快捷编辑也过滤 adminOnly。非管理员即使直达路由也只显示「仅管理员可见」，不发请求。
- **数据层**：新增 `data/visits/VisitModels.kt`、`VisitStats.kt`（纯逻辑：测试账号/匿名过滤、排序（beta6 起访问次数优先/同次数按最近访问）、`MM/dd HH:mm`、`client` 标签、旧行回退 slot、今天/近 7 天）、`VisitsRepository.kt`（`select=slot,sid,name,ts,client&slot=gte.&order=ts.desc&limit=5000`）。
- **页面** `ui/screens/stats/StatsScreen.kt`：顶栏返回/刷新，今天/近 7 天 chips，摘要三格（访问账号/次数/今日活跃），账号行（名次/姓名/次数/最近时间+端），加载/失败重试/空态；不显示学号。
- **接线**：`MainNavHost` 的 `"stats"` 路由 → `StatsScreen`；`AppContainer.visitsRepository`。
- **版本**：`versionCode 88` / `versionName 1.0.0-beta5`（测试版本 5）。
- **测试**：`:app:testDebugUnitTest` **1627/0（2 skipped）**；新增 `VisitStatsTest` 5、`VisitsRepositoryTest` 3、`ToolCatalogStatsTest` 2；`compileDebugKotlin` 通过；Release APK 安装到模拟器启动无 FATAL/ANR。
- **产物**：`artifacts/classtools-1.0.0-beta5-vc88-release-signed.apk`（**5,804,121 B / SHA-256 `6EE6E7AB078FB27A0E96A2B017153C3150057A1DF8294CB83AE794047E57EFB1`**）；桌面 `班级工具站_v1.0.0-beta5_安卓.apk` 同字节。更新通道 latest.json 未改（当前仍 beta4；如需推送更新另发）。

## 2026-11-18 · 访问统计上报记录登录端 android（源码；未发版）

> 用户要求：「将所有端的统计工具统一更新为按最近登录顺序排序、默认今天、分钟时间、记录具体登录端、忽略测试账号」。唯一契约 `web/统计多端_最新登录_契约_20261118.md`。

- 新增 `data/visits/VisitReporter.kt`：`POST visits` 一行 `{id:sid|slot|android, slot, sid, name, did, client:"android", ts}`，本地小时槽、`Prefer: resolution=ignore-duplicates,return=minimal`、失败静默；did 为私有 SharedPreferences 持久化的 16 位随机 hex（不读 ANDROID_ID/IMEI）。
- `AppContainer.updateActiveSession` 会话恢复/登录成功后上报；`MainActivity.onResume` 回前台补报（跨整点仍能刷新最近登录，同一小时 reporter 内部去重）。
- 测试：`VisitReporterTest` **12/12**；全量 `:app:testDebugUnitTest` **1617/0（2 skipped）**；未发版、版本号未动、未新增统计页。
- 提交：`72ac3cf`（上报）+ `a1185ca`（回前台补报），已双推 origin/gitee。

## v1.0.0-beta3 / vc86（2026-11-17 晚）· 用户 4 项反馈修复（ColorOS-only / 快捷登录免填 / 授权错误诊断 / 更新弹窗手动选择）

1. **OPPO 账号只在 ColorOS 手机显示**：`OppoAccountDeviceSupport` 改为**严格 ColorOS ROM 判定**（`ro.build.version.oplusrom` / `opporom` / `oplusos` 任一非空），不再仅凭 manufacturer/brand 含 oppo/oneplus/realme/oplus/heytap；非 ColorOS（含 OPPO 品牌但非 ColorOS、模拟器）身份页快捷登录与「我的 → 账号」入口**完全不显示**（无空占位）。
2. **未填学号/姓名也能 OPPO 快捷登录**：入口从表单下方移到身份卡标题下方、学号/姓名输入框之前，独立于表单；按钮只依赖「协议勾选 + 未授权中」（纯规则 `OppoQuickLoginRule`，与学号/姓名/表单校验无关）；未勾选协议点击在按钮下方提示「请先勾选同意《用户协议》《隐私政策》」，不再静默禁用；新增规则单测 + UI 静态断言（入口位于学号表单之前、不依赖表单状态）。
3. **OPPO 授权失败可诊断**：SDK 回调错误提示保留中文原因并显示 `code=xxx`（如 `请先在系统设置登录 OPPO 账号（code=-2015）`、未知码 `OPPO 授权失败（code=xxx）`）；ECS `oppo_exchange_failed`（如 message=`invalid_client`）提示「OPPO 账号服务未开通或凭证不匹配（invalid_client），请联系管理员」；登录/绑定/非 2xx 三条路径均映射，新增错误映射单测。
4. **更新改为弹窗手动选择 + 卡片优化**：自动检查/手动检查发现新版本**只弹窗，不自动下载**；下载完成**不自动拉起安装器**，等用户点「立即安装」；弹窗标题「发现新版本」、副标题「当前 vX → 新版本 vY」、更新说明滚动区、包大小（`latest.json` 可选 `sizeBytes`，缺省不显示）、渠道；按钮未下载「立即更新/稍后」、下载中进度+「取消下载」、完成「立即安装/稍后」、失败「重试」、force 无「稍后/取消下载」；`UpdateManager` 移除 `autoDownloadOnAutomaticCheck`、新增可注入安装器；新增 `UpdateManagerTest`（13 项：自动/手动检查均不下载、点击后才 enqueue、Ready 不自动安装、用户点装才调用、force/取消下载/失败/稍后、sizeBytes）。
- **测试**：全量 `:app:testDebugUnitTest` **1566/0**（117 suites；账号 78、更新 49）；新增 `UpdateManagerTest` 13 项。
- **构建**：`assembleRelease` 成功 → `app/build/outputs/apk/release/app-release.apk` **5,787,701 B / SHA-256 `11C121C8ACC6E94B561A7784E3EAAB98F05613EC20A536F537A3A584A52BBD93`**（vc86 / 1.0.0-beta3，v2 签名；aapt2 复核版本；release dex 已核对 ColorOS 属性/新错误文案/「立即安装」等新逻辑字符串）。本机 `oppo.properties` 已填账号服务凭证（不入库、不打印），故 ColorOS 真机会显示入口；**真实 OAuth 仍受服务端 `invalid_client`（账号服务未开通/凭证不匹配）阻塞**，待服务端修复后复测。
- 文档 `docs/OPPO_ACCOUNT.md` 同步更新（严格 ColorOS/快捷登录独立/错误码可诊断）。**不 commit**（工作区改动，供 Lead 发布 vc86 更新通道与桌面规范包）。
## v1.0.0-beta2 / vc85（2026-11-17）· OPPO 账号绑定学号（≤3）+ OPPO 账号快捷登录

用户 2026-11-17 要求：一个学号最多绑定 3 个 OPPO 账号 + 允许 OPPO 账号快捷登录。服务端验证由 Lead 在 ECS 新接口实现（客户端只传 authCode，不自行验证 OPPO token/openid）。

- **依赖/配置**：接入 OPPO 账号服务 OAuth SDK `com.heytap.accountsdk:account-oauth-web-sdk:1.1.3`（Maven Central，版本走 `gradle/libs.versions.toml`；传递依赖 appcompat/viewbinding 等由 Gradle 解析，`settings.gradle.kts` 已有 mavenCentral）。`app/oppo.properties` 新增 `oppo.accountAppId` / `oppo.accountAppKey` / `oppo.accountScope`（模板 `.example` 已更新并注明账号服务凭据与推送凭据是两套；真实值不入库）；BuildConfig 新增 `OPPO_ACCOUNT_APP_ID/APP_KEY/SCOPE` 与 `ACCOUNT_BASE_URL`（默认 `http://121.196.47.2/account`，gradle 属性 `ct.accountBaseUrl` 可覆盖）。**未配置/非 OPPO 系设备 → 快捷登录与绑定入口隐藏，其余功能无影响**。
- **客户端流程**：身份页新增「OPPO 账号快捷登录」（需先勾选协议）→ `AcOAuthSdkManager` 授权拿 authCode → `POST /oppo/login` → 服务端返回班级 token → 与学号+姓名核验完全一致的会话流程（Session/落盘/预热名单）；未绑定（`unbound`）弹简短提示引导先学号+姓名登录再绑定。「我的 → 账号」新增「OPPO 账号 · 已绑定 N/3」，底部弹层管理：已绑定列表（昵称+绑定日期+解绑）/「绑定 OPPO 账号」/已达上限 3/3 禁用/错误重试；绑定 `POST /oppo/bind`、列表 `GET /oppo/bindings`、解绑 `POST /oppo/unbind`（均带 `X-Class-Token`），解绑二次确认；退出/切号清空内存绑定列表。
- **SDK 事实核对**：1.1.3 AAR 自动合并 `OAuthWebActivity`/`AcOAuthAccountOperateReceiver`/OPPO 账号 App `<queries>`；**无需宿主 Manifest APPID meta-data**（appId/appKey/state/scope 由 `AcOauthRequest.Builder` 运行时传入，字节码核实必需四项非空）；明文网络 `121.196.47.2` 已在既有 network_security_config 放行，无需改。
- **测试**：新增纯逻辑单测 **72 项**（配置/设备支持/state/authCode；登录/绑定/列表/解绑/健康检查的 URL+headers+body+解析；15s 超时/网络/非 2xx/坏 JSON 降级；manager 编排/Session 构造/未配置安全跳过/绑定上限与错误分支），全量 `:app:testDebugUnitTest` **1545/0**（116 suites）；授权 SDK 全以假实现隔离，不依赖真机与真实密钥。
- **构建**：`assembleRelease` 成功 → `app-release.apk` **5,787,701 B / SHA-256 `CE861934E606E5B408505C133BDAB8ABFA2D39DD5C705D313D89D0DB78055C78`**（vc85 / 1.0.0-beta2，v2 签名；aapt2 复核版本与合并组件、dex 含账号 SDK 类）。**当前 APK 未配置账号服务凭证**（BuildConfig 为空串）→ 入口隐藏；填 `oppo.accountAppId/accountAppKey` 后重构建即启用。
- **文档** `docs/OPPO_ACCOUNT.md`（配置/流程/ECS 契约/错误码/SDK 事实/测试/构建/回滚/未验证项）。
- **未验证（如实）**：真实 OPPO 账号 OAuth 授权/静默授权未联调（无账号服务凭据、未上 ColorOS 真机）；ECS `/account` 接口由 Lead 实现，真实 unbound/limit/openid_bound_else 行为以服务端为准（客户端已按契约+容错实现）；昵称展示依赖开放平台 scope 申请。**未提交 commit（工作区改动）**。
## v1.0.0-beta1 热修 vc83（2026-11-16 晚）
- **删除工具**：活动 / 今日校园 / 学信网（Android 20→17、使用说明 27→24；相关 screen/data/features/route/测试 33 文件删除，非隐藏）。小程序端本就没有这三项；鸿蒙端删除 chsi/today（活动从未移植），工具 18→16、版本 1.0.7；网页端删除 chsi（活动/今日校园本来无入口）并已部署 OSS+ECS。
- **更新自动弹窗**：UpdateHost 移到根级，冷启动即调用 `checkOnStart()`，发现新版本直接弹窗并自动下载，不再依赖「我的→检查更新」。临时服务端 vc84 实测：启动 10s 内自动弹窗/自动下载（artifacts/auto-update-popup.png），随后恢复 latest vc83。
- 单测 1473/0；Release 4,336,487 B / SHA-256 `91814E1A5BC6E715E2A9645F1F8E2F313A86DB7B22CC61DAEF45DC79017AC69A`（vc83）；debug 27,002,917 B。
- 自动更新验证后 latest.json 已恢复 vc83（versionName 1.0.0-beta1）。

## v1.0.0-beta1 热修 vc82（2026-11-16 晚）
- 通知：按用户要求恢复列表上方的 5 张数据卡（待处理/24h 截止/7 天内/长期通知/最新发布）；放在滚动内容顶部，固定头仍为单行 56dp，兼顾“能看到数据卡”和“固定头不占屏”。
- 其余沿用 vc81（返回快照遮底、地图离线 assets+静态兜底、活动登录事实修正、UI 统一、课表接入）。
- 单测 1633/0；Release 4,418,407 B / SHA-256 `F4032E59ED2638C196EE70135B5B69F34C90E75630028A754E9ED1EACF4707FE`（vc82）。
- 明确拒绝伪造微信 openId（违法/违反学校与微信规则）；活动 App 内登录需学校官方 OAuth 凭据或免微信绑定接口。

## v1.0.0-beta1 热修 · vc81（2026-11-16 晚）
用户二轮反馈“必须彻底修复”，逐项复核：
- **退出工具动画**：根因是 NavHost 根页 pop 淡入 + 覆盖层只画卡片，快照收回时底层工具页仍可见。修复：根页 enter/exit/popEnter/popExit 全 None；覆盖层退出全程不淡出，并在快照下方铺一层不透明根页底色；pop 同时发生，收回到卡片后才撤层。录屏抽帧（artifacts/eh3-*.png）确认动画中/结束后不再出现工具页内部。
- **地图仍加载不出来**：发现 APK 内 `assets/map/campus_final_25d.html` 是**旧版 H5**（219,034B，缺 `#scene fixed/100vh` 与 innerHeight resize 修复），已同步为当前修复版（224,646B）；另加健康检查失败后的**内置静态地图兜底**（`map_campus_fallback.png`），保证任何 WebView 环境至少显示地图；模拟器离线截图验证。
- **活动登录**：实测学校账号登录页头像上传依赖微信 JSSDK（DOM 无 file input），绑定接口返回 `openId不能为空`；H5 的微信 OAuth 需学校 appid + 微信客户端授权 + 学校后端换 openId，App 无开放平台签名/回调域名，**不能合法取得 openId**。已改为诚实双主路径：「在微信内打开」（主）与「学校账号登录（App 内受限）」并提示限制，删除误导的 App 内微信登录按钮；不伪造 openId。若要 App 内登录，需学校提供免微信绑定接口或开放平台 App SDK/回调域名。
- **通知固定 UI**：定时头压到单行 56dp（返回/标题+总数/搜索/筛选），统计卡与分类 chips 移入筛选弹层/滚动内容，不再长期占据屏幕。
- **整体视觉**：统一 8dp 间距、16/20/24 圆角、22sp 页面标题、卡片 padding/描边；全部工具页去掉整块渐变头；首页通知权限横幅改浅色玻璃卡；浮岛描边减淡。
- 版本仍为 **1.0.0-beta1**（测试版本），versionCode 81；更新 JSON 指向 vc81。
- 测试：单测 **1633/0**；E2E 见下；Release **4,418,407 B / SHA-256 `E9DA002F085666918BB760D2E55A26DE9C4FF5C993845D6A65AC2041F5918CBF`**。

## v1.0.0-beta1 · 2026-11-16（测试版本；11 项反馈集中修复）
- 版本号改为 **1.0.0-beta1 / vc80**；界面显示「v1.0.0-beta1（测试版本）」，更新弹窗统称「测试版本」，渠道 `beta`；正式使用前所有版本均标测试。
- 工具返回：改为**真实界面快照缩回卡片**（返回时截当前界面 Bitmap → 立即 pop → 快照全屏缩到卡片 bounds），不再在动画中/结束后露出工具内部页面；快照失败降级图标卡。
- 下一节课：新增 `NextSessionCountdown` 格式化（<1h=N 分钟；≥1h=x 小时 y 分；明天/周X HH:mm；更远=x 天 y 小时），不再出现几千分钟。
- 顶栏瘦身：`CtTopBar` 默认紧凑（56dp、单行 22sp、隐藏副标题），所有工具页统一减少顶部占位，功能按钮保留。
- 通知：修正「最新发布」排序（max(行 ts, 组 updatedAt)，不再用只有 HH:mm 的 item time 按当天解析）；卡片/顶部 5 卡/chips 收紧、未读点、附件 N。
- 移除 OPPO 推送入口与 Debug 实时活动测试等无用/误导按钮；保留通知权限/提醒设置。
- 绿色 Hero 卡改版：单行 meta + 22sp 问候 + 小头像 + 紧凑课程块，高度约 190→137dp，删装饰胶囊/暗角，深色/平板不变。
- 学信网：参考 HFUT-Schedule 的 WebView+Cookie 模式：标准 Chrome UA（无 wv）、创建前 Cookie 开启、第三方 Cookie/DOM storage、onPageFinished flush；新增「清除 Cookie 重登」（只清 chsi.com.cn 域，不误伤其它工具）；人工确认/官方入口兜底保留。
- 活动：新增微信登录引导（检测微信三态；复制链接 / 在微信中打开；明示 openId 只能微信内获取、Cookie 不共享、不伪造登录）；头像上传文件选择器保留。
- 地图：H5 打包 `assets/map/campus_final_25d.html` 离线优先加载（file:///android_asset），多帧 resize 强制重算 canvas，健康检查失败才走 ECS/浏览器；**飞行模式截图与在线完全相同**，零网络依赖。
- 课表接入（新）：课表页右下「课表接入」——① 写入系统日历（CalendarContract 事件+提前 15/30 分钟提醒，可写日历选择、幂等清理/清除）；② 导出 .ics（RRULE 周次/单双周、VALARM、Asia/Shanghai）；③ WakeUp 兼容 `.wakeup_schedule` 备份文件（5 行 JSON，按 WakeUp 开源格式）；可分享/打开/下载。
- 测试：单测 **1624/0**（120+ suites，含 NextSessionCountdown 11、通知排序 7、学信网 WebView 13、课表接入 59 等）；E2E **44 步 0 FAIL（1055.5s，导航 10/10，logcat 0 FATAL/ANR）**；Release **3,562,103 B / SHA-256 `E200C0B24BDA35B48DCE842FD30CBDC16B3054671EEE5E90F55A2AF26290ECB3`**（vc80 / 1.0.0-beta1）；更新端点 `http://121.196.47.2/android/latest.json` + beta1 APK 已部署。
- 参考：HFUT-Schedule（CAS/WebView+Cookie/AIDL/日历）、WakeUp 开源（https://github.com/gitleon/WakeupSchedule_Kotlin）、WakeUp2XiaoAi、RFC5545 CalendarContract。

## v4.57.0-test1 · 2026-11-16（测试版本 1：更新系统 / 活动头像 / 今日校园 / 学信网）
### 新增：检测更新 + 自动更新
- 「我的 → 关于」新增「检查更新」行；登录后每天最多静默检查一次；发现新版弹「发现新版本 · 测试版本 1」。
- 自动检查默认自动下载（进度/非强制可取消），下载完成拉起系统安装器；支持 DownloadManager + OkHttp 回退、FileProvider、sha256 校验、未知来源授权跳转、force 强制更新。
- 端点：`http://121.196.47.2/android/latest.json` → OSS fallback；服务端已部署 APK + latest.json（versionCode 79 / 4.57.0-test1）。
### 活动登录上传头像点击无反应
- 根因：登录 WebView 未实现 `WebChromeClient.onShowFileChooser`，`<input type=file>` 静默无响应。已补齐文件选择器（图片/文件、多选、取消回调、生命周期清理），DEBUG 顶栏新增「上传自测」；模拟器实测点击「选择图片」能拉起系统照片选择器。
### 今日校园打不开
- 参考 HFUT-Schedule：OkHttp 完成 CAS HTTP 登录 → Cookie 注入 WebView → 加载门户 index.html；实现学校定制 CAS 的 AES/CBC 密码加密（salt 解析、encrypt.js 同款）、MemoryCookieJar、302 跟随、CookieManager 注入。
- 登录成功自动加载门户首页；失败回退 WebView JS 填表/手动登录；保留空白检测、清缓存重载、融合门户/浏览器打开/重试。
### 学信网仍不行
- 登录页与卡片页始终显示「我已登录，进入卡片页」；人工确认后即使提取为空也保留已登录展示、10 个官方入口与「打开完整学信档案」，不再卡登录页/显示未登录；延迟重试 + Cookie 辅助保留。
### 版本与测试
- versionCode 79 / versionName `4.57.0-test1`；界面显示「测试版本 1」。
- 单测 **1534/0**（119 suites）；E2E **44 步 0 FAIL（1001.7s，导航 10/10，logcat 0 FATAL/ANR）**；Release **3,432,477 B / SHA-256 `0C3424D406F059AD6D063DF3900D8FBD3E0FF9BE5A05DB33BD0BCDC51E02BA9F`**；冷启动 902/476ms，稳定 474/425/523ms。
- 手工设备验证：版本行显示「v4.57.0-test1（测试版本 1）」；检查更新显示「已是最新版本」；活动「上传自测」拉起系统选择器；今日校园打开门户登录页；学信网按钮显示「已登录·打开官方页面查看」。

## v4.56.4 · 2026-11-16（2.5D 地图白屏修复）
- 根因（WebView DevTools 实测）：地图 H5 的 html/body/#app 在 Android WebView 中计算高度为 0，`canvas.clientHeight=0`（画布内容其实已绘制但 CSS 高 0 不可见）。
- 修复 H5（已更新 OSS+ECS）：`#scene` 改 `position:fixed; width:100vw; height:100vh`；resize 用 innerWidth/innerHeight 兜底并显式写 canvas style 宽高；初始布局未完成时 40/120/300/700ms 多帧重试 resize，监听 resize；移动端 DPR 上限 1.25。
- Android：MAP_VERSION→202611160800 强制刷新缓存；保留软件层渲染规避部分 WebView canvas 合成问题。
- 验证：DevTools 检查 canvas 514×988 且像素非空；模拟器截图 2.5D 地图完整渲染（artifacts/map-fixed-css.png）。单测 **1478/0**（110 suites）；Release **3,399,577 B / SHA-256 `EAC29685F282EB39B7C7BB128395F35D5A512B58879A040B4285E7D27E8EB9ED`**（vc78 / 4.56.4），冷启动 367/302ms、稳定 305/289/306ms；logcat 0 FATAL/ANR。

## v4.56.3 · 2026-11-16（退出动画时序最终修正）
- 反向转场改为等收回动画即将结束（272ms）才 pop 工具路由，且覆盖层不淡出、结束后延迟 32ms 撤层；工具页移除与覆盖层撤除几乎同时，修复动画结束后工具页残留一瞬。onDone 仍保留强制 pop 兜底。
- 其余同 v4.56.2（学信网登录态/今日校园白屏/教务服务器繁忙修复）。单测 **1478/0**（110 suites）；快速 E2E 45 步 0 FAIL（v4.56.2 代码，1041.4s，导航 10/10）；Release **3,399,581 B / SHA-256 `FDA7D88ADCBD53BA7B4D8050AEE6C8334CC9D690939A09510AF95CDE44300855`**（vc77 / 4.56.3），冷启动 433/367ms、稳定 393/331/335ms。


## v4.56.2 · 2026-11-16（退出动画残留 / 学信网登录态 / 今日校园白屏 / 教务繁忙）
- 退出动画：反向覆盖层全程不淡出、结束后延迟 32ms 再撤层，并在 onDone 兜底强制 pop，修复动画结束后工具页残留一瞬。
- 学信网：登录后档案页延迟渲染/无强信号导致误判未登录；改为 2–3 次延迟提取（1.2s）+ 数据信号 + my.chsi Cookie 辅助；登录页与卡片页均提供「已登录，进入卡片页」兜底，失败不再强制跳回登录页。
- 今日校园：/login2 是 CAS 跳板、index.html 是 Vue SPA 外壳；登录成功后显式加载门户首页，开 mixed content/缓存，加白屏检测（500ms+800ms 复核）、清缓存重载一次（不清 Cookie）、仍空白显示「融合门户/浏览器打开/重试」。
- 教务「服务器繁忙」：改用微信 UA、第三方 Cookie/DOM storage/mixed content，自动填充延迟 700ms+提交前 300ms、自动仅 1 次；识别繁忙页即停止重试并提示手动/浏览器打开；保留直接打开/WebVPN 手动入口。
- 单测 __TESTS__；E2E **44 步 0 FAIL（1055.5s，导航 10/10，logcat 0 FATAL/ANR）**；Release __REL__（vc76 / 4.56.2）。


## v4.56.1 · 2026-11-16（课表课程块重叠修复）
- 课表周视图课程块缺少按开始节次的纵向偏移，导致同一列所有课程都叠在顶部/错位；已按 `rowHeight * (start-1)` 偏移到对应节次（v4.56 修复），`CourseBlockV5` 位置/高度与节次行一致。
- 单测 **1459/0**（109 suites）；快速 E2E **45 步 0 FAIL（1051.4s，导航 10/10）**；Release **3,383,197 B / SHA-256 `73230CF34C683571D6AFF2C9473C53A90D14E5F9974D6A80EA351ADD0D364827`**（vc75 / 4.56.1），冷启动 600/470ms、稳定 415/422/393ms。
- 多端移植：鸿蒙 1.0.6（地图/学信网/今日校园/文件传输 + 18 工具 + 平板断点，HAP 3,808,456 B，415 单测全绿）已产出；网页/小程序移植性审计与可移植项已实现（未部署/未发布）；详见各端 `docs/PORTING_MATRIX_20261115.md`。


## v4.56.0 · 2026-11-16（反馈第二轮 8 项 + 快递/今日校园 + 地图网页嵌入）

1. **浅色底栏发黑**：底栏黑色 elevation 阴影在浅色下改为 0dp（深色克制 6dp），容器改近白 tonal 底 + 1dp 细边框；药丸阴影同步。
2. **学信网登录后停在账号页**：LOGIN 模式回档案域且提取成功、无营销标记即自动回卡片；另提供「已登录，进入卡片页」兜底。
3. **工具退出两重动画**：工具路由 NavHost pop 动画改为 None，仅保留「页面收回卡片」覆盖层。
4. **活动「请在微信小程序打开」**：活动 WebView 设微信 UA + 最小只读微信环境桩/会话种子（不伪造身份），拦截微信 OAuth 跳转；验证停在 #/fjtcm/login 学校登录页。
5. **WebVPN 登录后未自动进教务**：新增门户探针（表单/二次验证/门户/错误），登录成功自动扫描并点击「本科教务」（多选择器/最多 3 次），失败给「打开 WebVPN 门户」手动入口。
6. **教务卡片 WebView 只显示顶部一条**：WebView 常驻组合树 + 每次 attach update 重设 MATCH_PARENT/requestLayout；DEBUG 顶栏「布局自测」验证。
7. **顶部贴边**：全局顶部呼吸区 = max(状态栏高度, 刘海) + 10dp（状态栏隐藏时生效），身份页/工作台/工具页/WebView 全部覆盖。
8. **复刻 HFUT 今日校园**：新增「今日校园」工具，12 个学工/门户服务入口 + 本地搜索 + 融合门户 WebView + CAS 自动登录（复用本机教务密码，验证码/失败回退手动）+ 打开今日校园 App（未装回退门户）。
- **快递工具**：复刻 HFUT 快递窗口——拼多多/淘宝身份码、菜鸟/京东入口一键打开；未装 App 自动官方网页；不保存任何取件信息。
- **地图改网页嵌入**：直接 WebView 加载 ECS H5，去原生 canvas 依赖；顶栏常驻浏览器入口、失败重试/嵌入备用/浏览器三级降级。
- 使用说明 27 卡、工具 20 项；单测 **__TESTS__**；快速 E2E **__E2E__**；Release **__REL__**（vc74 / 4.56.0）。
- 多端移植进行中：网页/小程序/鸿蒙按「可完整移植才移植」原则并行审计与实现，结果见各端文档。

## v4.55.0 · 2026-11-16（用户 12 项反馈修复 + 云空间同步）

1. **活动报错「未找到对应编号的学校信息」**：根因是登录 WebView 用了无 hash 路由入口，非微信环境把 schoolNo 写成 undefined；改为 #/fjtcm/login 入口 + schoolNo 兜底 + 多结构 lgInfo 解析，错误提示带服务器原文可诊断。
2. **工具拖动异常**：修复重排后拖动被取消（缺 key）、滚动后坐标重复计算、长按阈值 500→350ms、拖动静止不自滚、点击抑制不足、重启顺序丢失等 7 项；卡片副标改为 2 行、卡高 96dp，描述不再被折叠。
3. **状态栏隐藏**：新增沉浸式状态栏（默认隐藏，滑动临时显示），可在「我的 → 外观」关闭；处理刘海/安全区避免顶栏遮挡。
4. **地图打不开**：H5 正常但 WebView canvas 白屏；显式 viewport/硬件层 + canvas 健康检查/强制重绘 + 自动软件层重试 + 错误条，并补 Android 定位桥。
5. **外观模式**：新增「跟随系统 / 浅色 / 深色」本机设置（实时生效、冷启动保持），保留壁纸取色。
6. **教务非校园网**：TCP 探测不可直连时自动打开学校 WebVPN（https://webvpn.fjtcm.edu.cn:4433/login），用本机 Keystore 密码自动填充 CAS，短信/TOTP 或失败回退手动；提供「直接打开 / 走 WebVPN」入口。
7. **学信网**：点卡片默认直接进入工具卡片页（10 个官方入口始终可见），后台自动恢复登录并提取数据；未登录显示「去登录」，登录成功自动回卡片刷新。
8. **电费对齐网页**：同期比较移到「区间用电」tile 内独立一行完整显示（自然周期同期、14d/70d 补拉、35% 覆盖门槛与网页同口径）；同步「充值后趋势可读」：充值跳变折算曲线 + 充值竖虚线 + 「已折算充值」标识 + y 轴折算范围 + 图例充值度数。
9. 工具描述折叠：见第 2 条（副标 2 行）。
10. **云空间同步**：新增「文件传输 · 临时云空间」（同班级身份跨设备、任意大小 8MiB 分片上传、列表/下载/删除/清空、文件保留 72 小时），对齐小程序/网页 ECS drop v2.2；工具目录 18 项、使用说明 25 卡。审计其余未同步功能：小程序「统计（管理员）」「大屏互联」仍为管理员/网页专用，未在 App 内提供，其余工具已对齐。
11. **深色模式绿色卡片观感**：工作台/我的/使用说明深色下的大面积绿色渐变 hero 改为中性 tonal 深色，品牌绿只保留小面积点缀；浅色不变。
12. **通知实时全部加载**：首屏成功后自动后台连续加载全部剩余页（无需点按钮），显示「正在加载全部…（剩余 N 条）」，失败自动重试一次并保留已加载；不再需要点「加载更多/加载全部」。
- 单测 **1412 / 0 failures / 0 errors**（109 suites）；快速 E2E **45 步 PASS / 0 WARN / 0 FAIL（1057.9s，导航 10/10，logcat 0 FATAL/ANR）**；Release **3,366,813 B / SHA-256 `7A4A6A382D69E45C3680FA92E655C1AE17F16673D70F4E60D69374AF41AD9B9E`**（vc73 / 4.55.0，v2 签名），冷启动 477/420ms、稳定 419/463/369ms。
- 说明：活动学校账号、学信网真实账号、WebVPN 真实登录均需用户本人账号验证（红线段）；电费 14d/70d 同期需云端历史积累后自动出现；云端云空间未用真实 token 做端到端上传（测试身份可手工冒烟）。

## v4.54.0 · 2026-11-15（底部导航黑条修复 + 并行功能整合交付）

- **底部导航栏黑条修复**：nableEdgeToEdge 显式使用全透明系统栏 + isNavigationBarContrastEnforced=false；底部工具栏下方增加与页面底色一致的导航栏安全区背景。3 键导航（ColorOS 等）不再出现系统对比度黑色横条。
- **整合并行会话的「活动」工具**（第二课堂）：活动列表/详情/各角色剩余名额/报名与取消；学校账号在 App 内 WebView 手动登录，token 只存本机 DataStore，不上云/不打日志；无登录时引导登录，不显示假数据。
- **整合并行会话的「地图」工具**：内置 H5 2.5D 校园地图（约 231KB，ECS 121.196.47.2/map/campus_final_25d.html），楼栋/楼层/道路导航与定位权限；课表页可跳地图；网络安全配置仅对该 IP 放行 cleartext。
- **整合「学信网」工具**（v4.53 并行会话）：数据卡 + 10 个官方入口；修复未登录误判——学信档案域未登录时返回的是营销首页（不是登录页 URL），新增页面内容强信号判定（「注册学信网账号/立即登录」= 未登录，「退出登录/个人中心」= 已登录），未登录自动跳官方账号登录页，不再展示「已登录」空数据卡；新增 3 项单测。
- **整合 OPPO PUSH**（v4.51 并行会话）：官方 SDK AAR + Manifest 回调服务 + push_tokens upsert（未配置密钥时完全跳过）；设置页「我的 → 通知与权限」显示推送状态。
- 使用说明新增「活动报名」「校园地图」2 张卡（22 → 24 张，VERSION 2026-11-15.2）；工具目录 17 项。
- 单测 **1279 / 0 failures / 0 errors**（100 suites）；快速 E2E **44 步 PASS / 0 WARN / 0 FAIL（1048.1s，导航 10/10，logcat 0 FATAL/ANR）**；Release **3,301,273 B / SHA-256 `B02FDDCE3EE71EDA901059519014A42F5A7C865877A1D39581EF351C4BCCD858`**（vc72 / 4.54.0，v2 签名），冷启动 599/404ms、稳定 395/417/416ms。并行功能均为源码整合 + 构建/单测/冒烟验证；活动学校账号、学信网真实账号、OPPO 真实推送因红线/无密钥未做真实账号验证。

## v4.53.0 · 2026-11-15（新增「学信网」卡片页：数据卡 + 10 个官方工具入口）

- 新增「学信网」工具（`Icons.Rounded.Verified`、BrandSlateBlue、学习分类）：点卡片进入 App 内卡片页。
- 学信网有短信/验证码/实人核验，**无法自动登录**：进入即加载 `https://my.chsi.com.cn/archive/wap/index.jsp`，未登录时全屏显示**官方登录页由本人手动登录**（App 不预填/不保存/不上传学信网账号密码）；登录成功后从官方页面**只读提取**学籍/学历/学位/在线验证报告文本摘要。
- 卡片页：状态条（已登录 · 数据更新 HH:mm / 未登录或会话过期，去登录/刷新）+『数据』4 卡（提取不到显示「已登录 · 打开官方页面查看」空态）+『官方工具』10 卡（学期学籍/学历查询/学位查询/在线验证报告/报告在线验证/学历认证/照片校对/研招网/学职平台/帮助中心，内置 WebView 打开）+「打开完整学信档案」+「退出学信网登录」（清 Cookie/WebStorage/缓存）；WebView 顶栏「桌面版/手机版」UA 切换。
- 手机适配：LazyColumn；窄屏 2 列 / ≥600dp 3 列；状态栏/导航栏安全区；点按 ≥48dp；文字最多 3 行省略；深浅色 token。
- 安全：提取数据仅内存展示，不落盘/不上传/不打日志；展示前掩码 18 位身份证（保留前 6 后 4）与 11 位手机号（前 3 后 4）；WebView 禁 file/content access、仅 http(s)；不调用任何写接口。
- 单测 **1276/0**（100 suites；新增 ChsiExtractLogicTest 26 项，全合成夹具；UserGuideContentTest 22 卡断言更新）；Release 3,284,845 B / SHA-256 `DA22AC20…9E14BC`（vc71，v2 签名）；**未用真实学信网账号验证、未做真机验证**（如实）。

## v4.51.0 · 2026-11-14（OPPO PUSH 官方 SDK V4.0.0 接入：注册/上传 token/通知点击跳转）

- 接入 OPPO PUSH 官方 SDK V4.0.0（本地 AAR `app/libs/com.heytap.msp_V4.0.0.aar`，164,111 B / sha256 `8A542A78…1C41`；SDK 自包含，无新增 maven 依赖/gson）；Manifest 按官方快速接入声明 `DataMessageCallbackService`（heytap permission/action）+ `CompatibleDataMessageCallbackService`（coloros action）；proguard keep `com.heytap.msp.** / com.heytap.mcssdk.** / com.mcs.aidl.**`（release R8 后 dex 已核对 SDK 类保留）。
- 密钥配置：`app/oppo.properties`（**已加入 .gitignore，不入库**）+ 入库模板 `app/oppo.properties.example`；Gradle 读取生成 `BuildConfig.OPPO_PUSH_APP_KEY/OPPO_PUSH_APP_SECRET`（缺失/空 → 空串）。**当前无真实密钥**：未配置时客户端不 init / 不 register / 不上传，设置页「OPPO 推送」显示「未配置」，其余功能无影响（真实注册/到达/点击待密钥后真机验证）。
- 推送逻辑：`ClassToolsApp.onCreate` → 创建「班级通知」渠道 `class_notice`（IMPORTANCE_HIGH，服务端 channel_id 对齐）→ 配置非空才 `HeytapPushManager.init(context, DEBUG)` → `isSupportPush` → `register(appKey, appSecret, callback)`；RegistrationID/状态缓存本机（SharedPreferences）；设置页「我的 → 通知与权限」新增「OPPO 推送」行（未配置/不支持/注册中/已注册掩码/失败 + 点击重试）。
- token 上传（云端 `public.push_tokens`，契约由 Lead 定义）：`POST push_tokens?on_conflict=provider,token` + `Prefer: resolution=merge-duplicates,return=minimal`，body `{provider:"oppo",token,device,app_version}`；未登录跳过、登录/切换账号后自动补传；同一会话+regId+版本幂等只传一次，失败可重试，失败只日志不阻塞。
- 通知点击：服务端 `clickActionType=4` + `clickActionActivity=MainActivity` + `actionParameters={"noticeId":"<gid>"}` + `channel_id=class_notice`；MainActivity 冷启动/onNewIntent 双路径解析（intent extra `noticeId` 或 actionParameters JSON）→ 进通知工具页并展开对应通知详情（最多多加载一页旧通知，找不到则停留通知页）。
- 安全：不打印/不提交真实 AppKey/AppSecret/完整 RegistrationID（设置页仅掩码末 4 位；日志只输出错误码/状态）；`_oppo_sdk_tmp/`（官方 demo 解压参考）已 gitignore、不入交付。
- 单测 **1246/0**（98 suites；新增 OPPO 推送 33 项：配置空跳过/状态文案与掩码/回调状态映射/渠道常量/点击参数解析与路由/payload 与 URL·headers 构造/幂等门/编排器状态机与登录补传，全部假 SDK/假上传器，不依赖真机与真实密钥）；`assembleRelease` 成功：APK **3,252,045 B / SHA-256 `5BD44F09…EDA1A5`**（vc69 / 4.51.0，v2 签名，aapt2+apksigner 复核；Manifest 两个回调服务与 AAR 的 DataMessageReceivingService 均已合并）。
- 文档 `docs/OPPO_PUSH.md`（改动清单/填密钥/构建/真机验证步骤/错误码排查/运行时各状态/回滚/未验证项）。**未验证**：真实 AppKey 注册、服务端真实推送到达、点击跳转真实链路、ColorOS 真机行为（无密钥/未开推送服务）。

## v4.50.0 · 2026-11-14（通知页：长期通知按期次 + 顶部 5 张可点数据卡 + 资料栏 + 时间显示归一）

- 三端同构改造（契约 `web/通知三端_长期通知与时间显示_契约_20261114.md` §1/§2/§3）：长期安排（`recurrence`）新增 `until` 有效期末，到期/下一次超出 until → 状态「长期安排已结束」归已过期区且不再产生期次；非法 recurrence（无 freq/时间）按普通通知兜底。
- 期次算法（§1.3）：`next` = 严格 > now 的下一次发生；窗口 `[next-24h, next)` 内产生期次卡；期次 key `<gid>@occ:<yyyyMMdd>`，「本次已处理」写既有 `marks(kind=ntdone, ref=期次 key)`；本期处理后该期消失、下一期不受影响；长期定义卡无已处理。
- 顶部 5 张可点数据卡（待处理 / 24h 截止 / 7 天内 / 长期通知 / 最新发布）：点卡筛选、再点取消、激活态；与 chips（全部 / 未处理 / 置顶）互斥；手机 3+2 两行无横向溢出，点按区 ≥64dp。
- 分类筛选：新增分类 chips 行（全部 + 实际分类，FlowRow 自动换行不横向溢出），与顶部卡/主筛选 chips 独立组合，对全部视图（含长期清单、最新发布、搜索）生效（契约 §2.2）。
- 列表分区（§2.2）：主列表（普通时间明确通知 + 期次卡，紧急度排序，期次按 next 参与）→「资料 / 无时间」栏（默认展开、可折叠）→「已过期」区（默认折叠）；「长期通知」筛选=长期安排清单（周期 / 下次 / 进行中，无 done）；「最新发布」=按发布时间倒序（含已过期，过期样式不变）。
- 统计口径（§2.4，含工作台同步）：待处理要求有时间锚点（资料/无时间不计）、排除长期定义、期次已处理不计；长期=进行中条数；最新发布相对文案（`今天 12:49` / `M月D日（相对周） HH:mm`）。
- 时间显示（§3）：统一 `M月D日（相对周）`（今天/明天/本周X/上周X/下周X/周X，跨年 `YYYY年`，已是括号不重复；`11-13` 周六时显示「本周五」；倒计时保持相对）；覆盖卡片标题/摘要/状态绝对时间/详情摘要要点分段原文/合并消息/发布时间。
- 单测 **1213/0**（95 suites；新增 NoticeOccurrenceTest / NoticesBoardModelTest；NoticesPaginationTest 全绿）；**快速 E2E 44 步 0 FAIL（957.0s，导航 10/10，logcat 0 FATAL/ANR）**；Release 3,174,605 B / SHA-256 `BE22690A797E8D5C39B08FB93D3EDB74723E4335E2950BCD2CF38E3DF7C03845`（vc68 / 4.50.0，v2 签名），稳定冷启动 396/393/346ms。
- 通知「加载全部」修复（用户反馈无法全部加载）：底部「加载更多」改为「加载全部」（一次连续加载到全部完成，逐页出现新内容，加载中显示剩余条数）；旧通知页正文差异极大（单页 3–4MB），仓库自适应页大小 5/3/1（超时自动降级）；HTTP read 30s / call 60s、通知单请求协程超时 40s + 超时重试 1 次；失败保留已加载内容可继续/重试。

## v4.49.0 · 2026-11-14（点名拆为独立工具 + 名单三类人群滑动）

- 「点名」从名单工具拆出为独立卡片/页面：保留点名名单、到/假、已处理沉底、全选/沿用抽签名单、清空、重置本轮、搜索/筛选、空/加载/错误态；状态仍写 userdata.roster（姓名迁移/同步不变，不显示学号、不导出）。
- 名单页只保留「名册」：删除名册↔点名外层切换；班委/团员/全班三类人群改为 HorizontalPager 左右滑动（跟手 + spring、指示条跟手、点击分段 animateScrollToPage；reduceMotion 退化），搜索跨分类共享，团员按需懒加载。
- 新增点名工具卡（Icons.Rounded.HowToReg，班级事务分类），全部工具/首页快捷编辑自动包含；E2E 增加 tool_roll 步骤并适配新标题/入口。
- 单测 **1177/0**；快速 E2E **45 步 0 FAIL**（1028.2s，导航 10/10，logcat 0 FATAL/ANR）；Release 3,158,217 B / SHA-256 `54AB432F…C8A7`（vc67），稳定冷启动 288/270/291ms。


## v4.48.0 · 2026-11-14（教务工具页改「卡片速览 + 官方接口数据 + 官方工具入口」）

- 点「教务」卡 = App 内卡片页：4 张官方接口数据卡（成绩/考试/选课/公告，官方 JSON 接口只读 GET）+ 8 张官方工具入口卡（学期理论课表/课程成绩查询/学生选课中心/考试安排查询/培养方案明细/已收公告/缓考申请/社会考试报名）+「打开完整教务官网」。
- 登录复用自动登录（sso.jsp → authserver 注入 → 回 210 视为成功）；登录成功从 CookieManager 取同域 cookie，原生 HTTP GET 4 个官方接口（pageNum/pageSize + Cookie/UA/Referer/X-Requested-With/Accept），单卡失败隔离；返回 HTML/登录页判会话失效并自动重登 1 次。
- 点任意卡片 = App 内置 WebView 全屏打开对应官方页面，返回回卡片页；**App 不调用任何写接口**（选课/报名/缓考等写操作只在官方页面内由用户完成）。
- 安全：密码仍只本机 Keystore 加密保存；cookie 只在请求瞬间使用，不落盘/不打日志/不上传；卡片数据只内存展示；「清除登录」清密码 + Cookie + WebStorage + 缓存。
- 实测口径（2026-11-14 本人账号一次性勘察）：4 个接口均可用但当前无数据（count 全 0），**空态为正常路径**；有数据行渲染按官方字段 + 合成夹具实现，**未用真实有数据账号验证**。
- 单测 **1177/0**（93 suites；新增 JwApiClientTest 20 / JwCardsRepositoryTest 8 / JwCardModelsTest 14）；Release 3,141,837 B / SHA-256 `7543DAE4…49B9`（vc66，v2 签名）；未做真机验证（如实）。

## v4.47.1 · 2026-11-14（全部工具卡片尺寸对齐微信小程序）

- 「全部工具」卡片改为小程序 pages/all 同款：横向布局（40dp 图标 chip + 名称 + 副标题两行）、卡高 76dp（mp 132rpx≈72dp）、两列 48.6% 宽、圆角 16dp；删除分类标签/渐变装饰/页脚说明，一屏可见全部工具。
- 长按拖动排序、落位几何、转场锚点、读屏上移/下移、点击打开全部保持；拖动幽灵同步缩为紧凑卡。
- 单测 **1135/0**；快速 E2E **44 步 0 FAIL**（导航 10/10，logcat 0 FATAL/ANR）；Release 3,141,833 B / SHA-256 `626670C0…90DAA`（vc65），稳定冷启动 328/303/321ms。


## v4.47.0 · 2026-11-13（退出动画 + 工具简洁化/滑动 + 通知分页修复 + 删除设置工具）

- 退出动画：CtPageTransitions 重做（进入 10%/0.985、退出 12%/0.965、退场临界阻尼不晃）；工具页返回新增「页面收回工具卡」一镜到底反向覆盖层（返回键/顶栏返回均生效，reduceMotion 退化）。
- 工具简洁化：抽签/分组/体测/作业/电费/水量/通知/名单/投票/演示/诊断向小程序紧凑密度看齐（卡片 padding 16、titleSmall、去冗余分组/统计块），删除解释性段落文案。
- 左右滑动：作业筛选、电费区间、水量月份、通知筛选、名单 名册↔点名 + 分类、投票进行中/已结束 支持跟手滑动切换与 spring/fade 动画；reduceMotion 直接切。
- 通知联网修复：根因 
otices?select=* 全表 13.9MB 超 10s 超时；改为最新 20 条分页首屏 + 「加载更多」、每请求 30s 超时保护与重试，失败保留缓存；新增 11 项分页单测。
- 删除「设置」工具卡（设置入口保留在「我的」）；全部工具/工作台/身份页解释文字精简。
- 同时包含并行会话的「教务」WebView 自动登录改动（v4.46.1）。
- 单测 **1135/0**；快速 E2E **43 步 0 FAIL**（导航 10/10，logcat 0 FATAL/ANR）；Release 3,141,833 B / SHA-256 `9E5BA5FB…52B1A`（vc64），稳定冷启动 662/668/638ms。


## v4.46.1 · 2026-11-13（教务入口改「内置 WebView 直进 + 本机加密自动登录」）

- 点「教务」卡 = 直接进入内置 WebView（`http://210.34.74.77/sso.jsp`）；跳到统一身份认证登录页后自动填充账号（当前身份学号）+ 密码并提交（复用页面自身 `encryptPassword`），去掉原「入口页 + 打开按钮」一步（替代 v4.45.0 旧入口页实现）。
- 密码仅本机保存：Android Keystore AES-256/GCM + SharedPreferences（sid + 密文 + IV），不打日志/不上报/不参与同步；顶栏「清除登录」清密码 + Cookie + 缓存；身份 sid 变化或解密失败要求重输；无凭据时一次性密码输入（账号默认当前 sid、掩码展示，可「仅打开不保存」）。
- 新增 `features/jw`（URL/跳转 allowlist、JS 注入生成与转义、密码校验/掩码/结果解析）+ `data/jw`（Keystore 凭据存储）+ WebView 页；`res/xml/network_security_config.xml` 仅对 `210.34.74.77` 放行 cleartext（manifest 引用，不全局开）；非校内/非 http(s) 跳转交给系统浏览器或阻止。
- 注入：authserver `/authserver/login` 加载完成 → 300ms 填 `#username`/`#password`（input+change）→ 点 `#login_submit`；800ms 复核 `#showErrorTip`/`#captchaDiv` → 回退人工（提示「自动登录失败，可能需验证码，请手动完成」，最多尝试 2 次不死循环）；回到 `210.34.74.77` 视为成功。
- 单测 **1135/0**（90 suites；新增 JwLoginLogicTest 20 项，UserGuideContentTest 21 卡/VERSION 断言更新）；Release 3,141,833 B / SHA-256 `9074038E…7ACCF`（vc63，v2 签名）；按任务口径**未做真机/真实教务账号验证**（红线；验证码登录/WebView 兼容性为已知限制）。

## v4.46.0 · 2026-11-13（工具内部界面 v6 + 背景修复）

- 背景 v6：去除三团高 alpha 径向色块/色带；改为极轻纵向渐变+低 alpha 品牌柔光，深色近黑不发灰；移除噪点 shader 性能回退。
- 顶栏 v6：透明大标题 + 44dp 圆形玻璃返回/动作；新增工具页设计系统 CtToolCard/SectionHeader/PrimaryButton/Segmented/Chip/Field/InfoRow/Stat。
- 迁移 12 个工具内部界面（抽签/分组/体测/作业/电费/水量/通知/名单/投票/演示 PPT/诊断/课表背景统一），去掉重复页内大标题；保留全部业务逻辑/文案关键字/导出/同步。
- 修复电费空态双重 verticalScroll 崩溃、大字体胶囊换行、投票标签重复；移除背景噪点 shader 恢复导航性能。
- 单测 **1104/0**；快速 E2E **44 步 0 FAIL**（1084.5s，导航 10/10，logcat 0 FATAL/ANR）；Release 3,108,633 B / SHA-256 `442D5C7E…50F2`（vc62），稳定冷启动 385/394/394ms。
- 说明：本轮只做 UI/背景；未包含并行会话的「教务」工具入口改动。


## v4.45.0 · 2026-11-13（新增「教务」工具卡与入口页）

- 全部工具新增「教务」（`Icons.Rounded.School`、BrandSlateBlue、学习分类）：卡片标题「教务管理系统」、短描述「成绩 · 选课 · 考试」；可拖入首页快捷。
- 新增 `ui/screens/jw/JwEntryScreen.kt`：主按钮「打开教务系统」= `Intent(Intent.ACTION_VIEW, Uri.parse("http://210.34.74.77/sso.jsp"))`（`runCatching` 失败 toast「没有可打开浏览器的应用」）；「复制链接」= 剪贴板 + toast「链接已复制，请在手机浏览器打开」；URL 小字 + 提示行「统一身份认证登录 · 打不开时先连学校 VPN」；只做顶层导航，不内嵌、不采集账号密码。
- `MainNavHost` 增加 `"jw"` 路由分支；使用说明新增「教务入口」卡（21 张，VERSION 2026-11-13.1）。
- 单测 **1112/0**（89 suites；新增 JwEntryTest 8 项，UserGuideContentTest 21 卡断言更新）；Release 3,108,633 B / SHA-256 `39458E83…5CFA41`（vc61，v2 签名）；本轮按任务口径未跑 E2E/模拟器。

## v4.44.0 · 2026-11-13（工具卡 → 工具页「一镜到底」容器转场）

- 新增 `ToolTransitionOverlay`：卡片 bounds 长成全屏圆角容器（340ms、最后 30% 淡出）+ 目标页 push，形成卡片展开成页面的连续感；覆盖 Home 快捷工具与全部工具卡片；reduceMotion 时直接导航。
- 单测 1104/0；快速 E2E **44/0**（1292.1s，导航 10/10，logcat 0 FATAL/ANR）；Release 3,108,633 B / SHA-256 `0F37DA73…CB18`（vc60），稳定冷启动 804/826/806ms。
## v4.43.0 · 2026-11-13（参考 HFUT-Schedule / xiaoying 重做课表/工作台/全部工具/通知动效/我的 hero）

- 课表 v5：16 色马卡龙课程块、两行日期头（今日高亮/周末弱化）、周次胶囊+周选择、切换视图分段、现在指示线/进行中进度、下一节摘要条、spring/错峰入场、详情 Bottom Sheet、平板两栏；公开参数不变。
- 工作台 v5：今日 hero 五态+进度、信息卡统一外壳、常用工具四图标快捷行/编辑网格、错峰入场；全部工具 v5：渐变页头、2/3/4 列卡片、按压缩放，拖动排序/持久化原样保留。
- 通知页动效：animateItem/错峰/按压延伸感/chips 弹性/计数动画/搜索展开/状态过渡，reduceMotion 全量降级；「我的」hero 渐变身份卡。
- 单测 **1104/0**；快速 E2E **43/0**（1115.4s，导航 10/10，logcat 0 FATAL/ANR）；Release 3,108,633 B / SHA-256 `296A36B4…46A4`（vc59），稳定冷启动 620/674/635ms。
- 只借鉴参考 App 设计原则，未复制源码/图片/字体；「一镜到底」跨页转场留待下一轮。
## v4.42.0 · 2026-11-12（以小程序为基准 UI 重构：我的/通知/设置/使用说明 + 通知去重 + 删除测试通知）

- 「我的」页重做（账号身份/数据与同步/通知与权限/外观/关于与帮助，去学号与冗余行，平板双栏）；「通知」页重做（简报/筛选 chips/搜索/卡片/已过期折叠/详情底部弹层）；全部工具「设置」仍直达「我的」Tab；使用说明卡片化（20 卡轮播）。
- 修复同一通知重复多条：数据层 `NoticeDedupe`（同 gid + 语义强证据保守合并，23 用例）；删除「发送测试通知」能力与入口，保留「立即检查 + 诊断」。
- 实时活动（Android 16 Live Updates / ColorOS 16 流体云）复验：dumpsys `flags=…PROMOTED_ONGOING`。
- P0：Gradle build cache 产生缺 `GuideScenesKt` 的 dex 致使用说明崩溃；`--no-build-cache` 重建解决，Release 采用 clean + no-build-cache。
- 单测 **1098/0**；快速 E2E **43/0**（1075.3s，导航 10/10，logcat 0 FATAL/ANR）；Release 3,075,869 B / SHA-256 `541A0CAF…60761`（vc58），稳定冷启动 0.88/0.78/0.79s。
## v4.41.0 · 2026-11-12（实时活动通知设置入口 · v4 阶段收官）

- 权限与提醒新增「实时活动通知」入口（跳系统通知类别设置）；新增 docs/V4_FINAL_STATUS.md 阶段验收文档。
- 单测 1065/0；快速 E2E **43/0**（1144.4s）；Release 3,026,713 B / SHA-256 `E663D882…A25E`（vc57）。
## v4.40.0 · 2026-11-12（实时活动完善：结果通知 + 通知关闭提示）

- PPT 上传完成/失败补短暂结果通知；通知未开启时上传前明确提示；实时活动复测仍 PROMOTED_ONGOING。
- 单测 1065/0；快速 E2E **43/0**（1036.4s）；Release SHA-256 `52CBB195…4852`（vc56）。
## v4.39.0 · 2026-11-12（性能维护：Baseline Profile 刷新）

- 重新采集应用级 Baseline Profile：18,166→17,649 规则（清理失效/覆盖新代码），binary profile 12,327B。
- speed-profile 后冷启动中位 282ms；常规冷启动 0.30s/0.28s；单测 1065/0；E2E **43/0**（974.1s）；SHA-256 `8855DA3C…0738`（vc55）。
## v4.38.0 · 2026-11-12（系统实时活动：Live Updates / ColorOS 流体云）

- 接入 Android 16 Live Updates（ColorOS 16 流体云兼容）：PPT 上传进度实时活动 + 限时投票原生倒计时；POST_PROMOTED_NOTIFICATIONS。
- 设备实测系统提升 `PROMOTED_ONGOING`（通知栏顶部倒计时卡片）；debug 提供测试入口。
- 单测 1065/0；快速 E2E **43/0**（975.2s）；Release SHA-256 `FBA2907B…EA27C`（vc54），冷启动 0.31s/0.29s。
## v4.37.0 · 2026-11-12（交互：首页快捷编辑返回确认 + 审计扩面）

- 编辑态返回先确认放弃修改（H-03）；新增工具/设置页无障碍审计脚本，12 界面 unlabeled=0。
- 单测 1065/0；快速 E2E **43/0**（1013.2s）；Release SHA-256 `31F41D6D…93C3`（vc53），冷启动 0.30s/0.28s。
## v4.36.0 · 2026-11-12（动效/自适应：移除动画实时生效 + 平板复核）

- ContentObserver 监听动画缩放，「移除动画」运行中切换立即生效；平板 800dp 三屏实测截图刷新。
- 单测 1065/0；快速 E2E **43/0**（1074.9s）；Release SHA-256 `E336A981…1D45`（vc52），冷启动 0.32s/0.27s。
## v4.35.0 · 2026-11-12（无障碍/动效：系统移除动画适配）

- 系统「移除动画」时：轮换卡停止、计数器直接显示目标值、PPT 热点不脉冲；恢复动画后自动恢复。
- 单测 1065/0；reduced-motion 设备冒烟；快速 E2E **43/0**（1130.7s）；Release SHA-256 `8D15454D…2361`（vc51）。
## v4.34.0 · 2026-11-12（无障碍：开关读屏标签 + 自动化审计）

- 6 处裸 Switch 补齐 contentDescription（云同步/允许重复/自动重抽/打乱/提醒/投票选项）；新增 7 屏无障碍审计脚本，修复后 unlabeled=0。
- 单测 1065/0；快速 E2E **43/0**（1081.6s）；Release 3,026,681 B / SHA-256 `2EA1362E…C9AA`（vc50）。
## v4.33.0 · 2026-11-12（视觉：底栏真实背景模糊）

- 底部悬浮岛接入 Haze 1.7.3 真实背景模糊（24dp、55% 表面色、噪点），旧设备自动降级半透明；APK 3,026,681 B（+39KB）。
- 单测 1065/0；设备截图核验模糊取样；快速 E2E **43/0**（1071.7s）；Release SHA-256 `9DB65C96…32A4`（vc49）。
## v4.32.0 · 2026-11-12（自适应：平板课表右栏导出卡固定底部）

- 平板双栏右栏：下一节/全部课程可滚动，导出课表卡固定底部，与周表底部对齐。
- 单测 1065/0；平板预览核验；快速 E2E **43/0**（1131.4s）；Release 2,987,256 B / SHA-256 `E7A885EA…E7A7`（vc48）。
## v4.31.0 · 2026-11-12（无障碍：课表大字体行高自适应）

- 课表网格按系统 fontScale 放大行高/表头/时间列，大字体下课程名不再被截断。
- 单测 1065/0；1.3x 预览核验；快速 E2E **43/0**（1117.2s）；Release 2,987,256 B / SHA-256 `81444CE3…A27B`（vc47）。
## v4.30.0 · 2026-11-12（P2：关注宿舍选择器搜索）

- 电费关注宿舍选择器支持按房间号/楼栋搜索，跨楼栋结果、已关注标记、限高滚动。
- 单测 1065/0；预览核验；快速 E2E **43/0**（1114.5s）；Release 2,987,256 B / SHA-256 `9F025F46…A1B2`（vc46）。
## v4.29.0 · 2026-11-12（P2：体测历史删除可撤销）

- 体测历史删除后 5 秒内可撤销（恢复本地并按原记录重新保存）；删除确认文案去开发术语。
- 单测 1065/0；快速 E2E **43/0**（1192.7s）；Release 2,987,256 B / SHA-256 `EEA14801…2FBA`（vc45）。
## v4.28.0 · 2026-11-12（P2：参数已保存提示 + 退出登录提示）

- 抽签参数首次自动保存后轻提示「参数已保存」；退出登录 Toast「已退出登录」。
- 单测 1065/0；快速 E2E **43/0**（1118.9s）；Release 2,987,256 B / SHA-256 `CED61C04…E11E`（vc44）。
## v4.27.0 · 2026-11-12（P2：抽签分组徽标 + 体测改名计数）

- 抽签结果「已用于分组」徽标（重新抽签自动重置）；体测改名弹窗显示 N/24 计数。
- 单测 1065/0；快速 E2E **43/0**（1143.1s）；Release 2,987,256 B / SHA-256 `362D5D33…E6D1`（vc43）。
## v4.26.0 · 2026-11-12（P2：电费切档过渡 + 清除数据明细）

- 电费趋势切档/刷新淡入淡出（180ms）；清除云端数据弹窗显示数量明细并修正文案。
- 单测 1065/0；快速 E2E **43/0**（重跑 1111.4s）；Release 2,987,256 B / SHA-256 `BA2D3824…FFD5`（vc42）。
## v4.25.0 · 2026-11-12（P2：手机课表全部课程折叠卡）

- 手机周视图下方「全部课程（N）」折叠卡（默认收起，可展开），内容为课程名+教师+节数。
- 单测 1065/0；预览核验；快速 E2E **43/0**（996.9s）；Release 2,987,256 B / SHA-256 `7FA20DF2…B144`（vc41），冷启动 0.31s/0.27s。
## v4.24.0 · 2026-11-12（P2：隐私说明分组 + E2E 稳定性）

- 隐私说明按「收集与展示 / 存储与同步 / 你的控制」分组；修正文案与按钮重名歧义。
- E2E 脚本：屏幕外/被 dock 遮挡节点先滚动再点、诊断与清除云端对话框重试；修复后 43/0。
- 单测 1065/0；Release 2,987,256 B / SHA-256 `B3886C62…16E1`（vc40）。
## v4.23.0 · 2026-11-12（P2：通知大图缩放/分享）

- 全屏通知图片支持双指缩放、拖动、双击复位；外置 URL 图片可分享链接。
- 单测 1065/0；快速 E2E **43/0**（1017.2s）；Release 2,987,256 B / SHA-256 `0E60E4A9…7A9`（vc39）。
## v4.22.0 · 2026-11-12（性能：应用级 Baseline Profile）

- 接入 androidx.baselineprofile 1.5.0：18,166 条规则（908 条 app 自身）+ 启动 profile 随 APK 下发。
- 模拟器：profile 编译后冷启动中位 750ms vs 对照 843ms（约 -11%）；安装后默认 verify 阶段差异不明显。
- 单测 1065/0；快速 E2E **43/0**（959.2s）；Release 2,987,256 B / SHA-256 `BCEF2431…07A1`（vc38）。
## v4.21.0 · 2026-11-12（P2：通知卡数据新鲜度）

- 工作台通知卡就绪时显示「更新 HH:mm」。
- 单测 1065/0；快速 E2E **43/0**（1008.8s）；Release 2,856,126 B / SHA-256 `24A2875A…8126`（vc37）。
## v4.20.0 · 2026-11-12（P2：身份页可用性 + 权限页重新检测）

- 身份页按钮禁用时显示具体缺少项；权限页新增「重新检测权限状态」。
- 单测 1065/0；快速 E2E **43/0**（1019.0s）；Release 2,856,126 B / SHA-256 `6C4C7F14…DD32`（vc36）。
## v4.19.0 · 2026-11-12（P2：工作台电费卡一键关注）

- 工作台电费卡未关注时点击直接弹出选择关注宿舍；工具页入口保持原行为。
- 单测 1065/0；设备实测选择器直达；快速 E2E **43/0**（980.8s）；Release 2,856,126 B / SHA-256 `74A23B5D…CEDE`（vc35）。
## v4.18.0 · 2026-11-12（P2：课表周末隐藏提示）

- 周视图周末无课时顶部提示「周末无课，已隐藏周末列」；确认库级 baseline profile 已合并（应用级待接入）。
- 单测 1065/0；快速 E2E **43/0**（重跑 978.8s）；Release 2,856,126 B / SHA-256 `C7D5A909…3AD0`（vc34）。
## v4.17.0 · 2026-11-12（性能：启动懒加载）

- AppContainer 13 个重对象改懒加载，WorkManager 提醒调度移出主线程；冷启动中位数 416ms → ≈356ms（同环境 5 次）。
- 单测 1065/0；快速 E2E **43/0**（899.8s）；Release 2,856,126 B / SHA-256 `3A8C092D…BEAD`（vc33）。
## v4.16.0 · 2026-11-12（P2：今日课程「现在」时间线）

- 今日课程卡在已结束/未开始之间插入「现在」分割线（预览核验）。
- 单测 1065/0；快速 E2E **43/0**（931.3s）；Release 2,856,126 B / SHA-256 `CAACE9A1…244B`（vc32）。
## v4.15.0 · 2026-11-12（P1：通知卡行内重试）

- 工作台通知卡失败/离线态新增行内「重试」，无需进入通知页即可恢复。
- 单测 1065/0；快速 E2E **43/0**（940.3s）；Release 2,856,126 B / SHA-256 `BE3911C6…8054`（vc31），两次冷启动 0.33s/0.33s。
## v4.14.0 · 2026-11-12（P1：触控审计 + 触感反馈）

- 可点热区扫描：应用内无 <44dp 违例；名单/点名/作业/投票关键操作加入轻触反馈；身份页移除互通承诺 pill。
- 单测 1065/0；快速 E2E **43/0**（907.3s）；Release 2,856,126 B / SHA-256 `2A0412D2…066A`（vc30），两次冷启动 0.48s/0.33s。
## v4.13.0 · 2026-11-12（P1：读屏语义补齐）

- 电费环形图/趋势图增加读屏描述；顶栏与诊断卡片标题加 heading 语义（TalkBack 按标题导航）。
- 单测 1065/0；快速 E2E **43/0**（960.4s）；Release 2,856,126 B / SHA-256 `DD46FC8F…CBE`（vc29），两次冷启动 0.74s/0.37s。
## v4.12.0 · 2026-11-12（P1：诊断简版 + 课表来源）

- 诊断页默认简版（摘要/设备/服务异常），右上角「详细/简洁」切换完整内容；课表副标显示「公共课表」来源。
- 手势实测：工作台下拉刷新、dock 光场按压截图留档。
- 单测 1065/0；快速 E2E **43/0**（932.4s，含简版→详细断言）；Release 2,856,126 B / SHA-256 `27A81204…4000`（vc28），两次冷启动 0.63s/0.40s。
## v4.11.0 · 2026-11-12（P1：写日历权限体验）

- 写日程先申请日历读写权限，授权后直接写入；拒绝/无账号回退系统新建日程界面并提示。
- 单测 1065/0；快速 E2E **43/0**（948.3s）；Release 2,856,126 B / SHA-256 `D101F634…686F`（vc27），两次冷启动 0.66s/0.39s。
## v4.10.0 · 2026-11-12（P1：宿舍选择搜索 + 水量录入校验）

- 选择关注宿舍弹层新增房间号/楼栋搜索；水量录入与上次读数差 >30% 二次确认。
- 单测 1065/0；快速 E2E **43/0**（931.4s）；Release 2,856,122 B / SHA-256 `CF2FB36C…B499`（vc26），两次冷启动 0.46s/0.39s。
## v4.9.0 · 2026-11-12（P1：通知搜索 + 诊断刷新控制）

- 通知页新增标题/内容搜索与清除；筛选空态提供「清除搜索与筛选」。
- 诊断页新增暂停/恢复自动刷新按钮与刷新中进度条。
- 单测 1065/0；快速 E2E **43/0**（958.1s）；Release 2,856,126 B / SHA-256 `D9967782…6763`（vc25），两次冷启动 0.64s/0.39s。
## v4.8.0 · 2026-11-12（审计文案清理 + 计数器性能优化）

- 作业详情去掉开发版语气文案；通知未知附件统一「附件 · 暂不支持预览」；电费慢更/参考文字提升可读性。
- 数字计数器改用 derivedStateOf，按显示值变化重组（减少逐帧重组）。
- 单测 1065/0；快速 E2E **43/0**（952.1s）；Release 2,856,126 B / SHA-256 `E930E898…55BB`（vc24），两次冷启动 0.63s/0.44s。
## v4.7.0 · 2026-11-12（大字覆盖扩展 + 课表头部去重）

- 新增作业/课表/演示/电费 1.3× 大字体预览并核验；课表周标题已含日期范围时不再重复显示副标。
- 单测 1065/0；快速 E2E **43/0**（932.7s）；Release 2,856,130 B / SHA-256 `3BE63A53…8C37`（vc23），两次冷启动 0.54s/0.36s。
## v4.6.0 · 2026-11-12（预览渲染恢复 + 大字投票修复 + 演示下拉刷新）

- 恢复 Compose 预览批量渲染（182 张）；预览走查修复大字 1.3 下「截止时间」三段标签截断（限时/指定/不限）。
- 演示（PPT）列表支持下拉刷新。
- 单测 1065/0；快速 E2E **43/0**（929.0s）；Release 2,856,126 B / SHA-256 `61AAD1C2…7A7A`（vc22），两次冷启动 0.48s/0.35s。
## v4.5.0 · 2026-11-12（P1 批次 C/D：读屏语义 + 大字投票实测 + 性能审计）

- 电费同期徽标读屏语义（不再只靠颜色表达增减）；大字 1.3 投票页实测正常；新增投票详情/发起大字体预览声明。
- 性能复核：17 处动画均在绘制阶段读取；预览渲染工具卡住已记录待排。
- 单测 1065/0；快速 E2E **43/0**（929.3s）；Release 2,856,130 B / SHA-256 `8E6D5074…07DC`（vc21），两次冷启动 0.42s/0.32s。
## v4.4.0 · 2026-11-12（P1 批次 C：小屏/大字 + 无障碍语义）

- 360dp 小屏与字体 1.3× 实测通过（工作台/工具/投票/名单无截断溢出，截图留档）。
- 投票选项 selectable（单选/多选角色 + 选中状态）；名单成员行合并语义，TalkBack 一次读出信息。
- 单测 1065/0；快速 E2E **43/0**（967.9s）；Release 2,856,130 B / SHA-256 `0E7FC2BE…EC3C`（vc20），两次冷启动 0.81s/0.38s。
## v4.3.0 · 2026-11-12（P1 批次 C：自适应第一批）

- 平板名单页限宽 880dp 居中（段选/三分类/搜索/列表），修复工作台通知权限横幅重复显示。
- 平板 2560×1600 抽查工作台/工具/投票/名单/电费正常；手机快速 E2E **43/0**（941.7s）。
- 单测 1065/0；Release 2,856,126 B / SHA-256 `E98946F4…3310`（vc19），两次冷启动 0.37s/0.40s。
## v4.2.0 · 2026-11-12（P1 批次 B：光场/动效 + 工作台下拉刷新）

- dock 高光跟随触摸（光场层次；不消费事件）；工作台支持下拉刷新（课表/作业/通知/电费）。
- 动效 phase 审计：17 处 animate*AsState 均在绘制阶段读取；CtMotion ↔ M3 MotionScheme 说明保留。
- 单测 1065/0；快速 E2E **42/0**（940.7s）；Release 2,856,126 B / SHA-256 `C43A921B…947A`（vc18），两次冷启动 0.49s/0.35s。
## v4.1.0 · 2026-11-12（P1 批次 A：交互可靠性 / 全局撤销）

- 名单页「清空点名名单 / 重置本轮」、作业完成/取消完成、水量删除记录 → Snackbar 5s 内可「撤销」。
- 名单页在空名单/加载失败时保留三分类/搜索/刷新；文案统一「名单」。
- 单测 1065/0；快速 E2E **42/0**（938s）；Release 2,856,130 B / SHA-256 `8491DD50…9E43`（vc17），两次冷启动 0.51s/0.33s。
## v4.0.3 · 2026-11-12（名单状态收尾 + 完整 E2E 通过）

- 名单页标题/错误文案统一「名单」；名册 tab 在空名单/加载失败时仍保留三分类/搜索/刷新；点名失败显示「名单加载失败 + 重试」。
- **完整 E2E 44 PASS / 0 FAIL（929s）**：新增名单三分类+点名、投票页、电费页、通知权限横幅、清缓存确认断言；深色组 + 导航 10/10 + logcat 0 FATAL。
- 单测 1065/0；Release 2,839,746 B / SHA-256 `F53EC747…68DF`（vc16）；两次冷启动 0.77s/0.41s。
## v4.0.2 · 2026-11-11（名单命名对齐 + v4.0.x 收尾）

- 工具「名册」更名为「名单」（副标题「班委/团员/全班 · 点名」），与网页/小程序对齐。
- 名单三分类在班级名单为空时保留（v4.0.1 修复延续）；加载失败仍显示错误/重试。
- 单测 1065/0；Release 2,839,746 B / SHA-256 `17239BFF…210E`（vc15），冷启动 0.42s、logcat 0 FATAL/ANR。
## v4.0.1 · 2026-11-11（v4.0.0 修复版）

- 修复「名单」页在班级名单为空（测试身份/同步失败）时，名册 tab 的三分类（班委/团员/全班）与搜索区被空态整体替换的问题；名册 tab 始终保留分类/搜索，空态在列表区展示；点名 tab 空名单仍提示同步。
- 其余同 v4.0.0。
## v4.0.0 · 2026-11-11（Round 11：深度调研/审计 + 投票/名单三分类/电费同期同步 + 全局 P0）

- **调研/审计**：ColorOS 光场/液态玻璃、M3 Expressive Motion、Compose 性能、同类班级/投票产品调研；112 条 UX 审计（P0 14）；新功能 UI 规格与光场动效文档。
- **投票（新增）**：列表/详情/发起/管理/可见范围（全班/班委/团员/指定成员，无学号）；限时/指定截止/不限时，单选/多选/匿名/改票/结果可见性，实时结果；数据层 5 RPC + 幂等草稿 + 中文错误。
- **名单三分类**：班委/团员/全班 + 计数/搜索/下拉刷新；团员来自 class_league，失败降级缓存提示。
- **电费**：13/16 号楼隐藏 24h（自动落 7d、切回恢复）；自然周期同期比较徽标（较昨日/上周/上月同期，数据不足隐藏）。
- **全局 P0**：通知权限横幅（可关闭，工作台/我的/提醒设置）、清除本机缓存二次确认、体测评分语义色、课表/PPT 大字体适配。
- 单测 **1065/0**；Release 2,839,742 B / SHA-256 `0A788D35…5783`，冷启动 0.66s、logcat 0 FATAL/ANR。
## v3.6.0 · 2026-11-11（「演示」v2.3：视频/音频单独下载）

- **下载详情页新增分组「视频单独下载」**（契约 §12.8）：读取 `manifest.media_files`，在兼容版/PDF/原始文件三行之后展示
  每个内嵌音视频：`视频1 · 第 7 页` / `音频1 · 第 3 页` + 时长 `mm:ss` + 分辨率 `1024×576` + 大小；
  分组标题旁显示计数（`2 个视频 · 1 个音频`）；无 `media_files` 的旧 deck 不显示（向后兼容）。
- **下载/打开/分享**：复用 `PptFileDownloader`（DownloadManager，系统下载通知 + 进度百分比）；
  完成后 `ACTION_VIEW` 用 `video/mp4`、`audio/mp4` 等 MIME 交给系统播放器/WPS 打开，可分享（`ACTION_SEND`）；
  失败 toast、按钮点按 ≥48dp、图标带无障碍 `contentDescription`。
- **数据层**：`PptMediaFile`（src/name/size/sha256/kind/dur/w/h/slides/sourceName）+ `PptManifest.mediaFiles/mediaUrl/hasMediaFiles`；
  `PptParsers.parseMediaFiles` 容错：缺失/非数组 → 空、坏项跳过、kind 未知按扩展名推断（m4a/mp3 → 音频）、
  slides 只保留合法数字去重升序、条数上限 50/单媒体页数上限 20。
- **纯逻辑**：`PptMediaDownloadLogic`（行模型：序号/页号/时长/分辨率/大小/MIME/文件名清洗兜底/URL/分组计数，全部 JVM 单测）。
- 版本 **v3.6.0 / versionCode 12**；单测 **994/0**（v2.3 新增 12 项：媒体行逻辑 9 + 解析 3；原 982 基线无回退）；无新增依赖。
- 详见 `docs/PPT_DOWNLOAD_20261110.md` §6（v2.3 增量）。

## v3.5.0 · 2026-11-10（「演示」v2.0 功能重定义：下载中心 = 兼容性处理 + 多端下载）

- **定位变更**：不再以在线播放为主 → 上传 PPT 后由转换机自动做**不降质**兼容性处理（`.ppt→.pptx`、包修复、字体嵌入、
  PDF 副本、外链媒体如实告警），三端**下载处理后的文件**，用 PowerPoint/WPS/PDF 查看器打开；旧在线播放器降级为旧 deck 的「在线预览」入口（代码完整保留）。
- **列表**：卡片点击 = 打开**下载详情**（不再直接进播放器）；有产物的卡片显示小标签「兼容版」「PDF」；
  标签数据用 PostgREST jsonb 子路径 `manifest->files` 轻量获取（不拉完整 manifest）；上传/轮询/删除/重试保留；就绪卡片去掉封面「▶」。
- **下载详情页**（新路由 `pptDetail/{deckId}`）：三行下载 `兼容版 PPTX (size)` / `PDF (pages 页, size)` / `原始文件 (size)`；
  点下载 → `DownloadManager`（系统下载通知）→ 轮询进度 → 完成自动 `ACTION_VIEW`（FileProvider 打开 PowerPoint/WPS/PDF），
  可分享（`ACTION_SEND`）；兼容提示 `manifest.compat.warnings` 橙色逐条；「在线预览」仅旧版内容可见；
  旧 deck：「兼容版未生成」+「在线预览」+「重新处理」+ 原始文件下载；非 ready 只显示状态/进度/失败重试。
- **数据层**：`PptManifest` 增 `files`（compat/pdf/source，含 size/sha256/ext/name/pages）与 `compat`（warnings/fonts_embedded/converted_from）容错解析；
  `PptRepository.loadDownloadDetail`；列表行解析 files 标签；`AndroidPptFileDownloader`（DownloadManager + FileProvider + 分享）。
- **纯逻辑**（JVM 单测）：`PptDownloadLogic`（三行下载/大小/文件名清洗补扩展名去重/MIME/旧 deck 判定/兼容提示/列表标签/状态文案）。
- 版本 **v3.5.0 / versionCode 11**；单测 **982/0**（v2.0 新增 28 项，原有 954 基线无回退）；
  无新增依赖；旧 v1.1/v1.2 播放器代码全部保留（在线预览入口）。
- 已知限制：下载保存到**应用外部下载目录**（不申请存储权限红线；系统下载通知可见，FileProvider 打开/分享，不写公共 Downloads）；
  需设备装有对应查看器。详见 `docs/PPT_DOWNLOAD_20261110.md`。

## v3.4.0 · 2026-11-10（「演示」v1.2：原样播放高保真视频模式 + 便捷操作）

- **原样播放模式**（契约 `web/ppt/CONTRACT.md` §11）：`manifest.video`（转换机用 PowerPoint 导出的高保真视频）存在且网络良好时
  **默认播放视频**（含入场/强调/路径动画、页切换、内嵌视频声音）；网络差/无视频/加载失败自动回退分步浏览（不白屏）。
- **默认模式**：`ConnectivityManager`（WiFi/以太网 GOOD；Data Saver/计量移动网 <1500kbps SLOW；无网 OFFLINE）+ 用户设备偏好
  （播放器内「原样/分步」胶囊切换，手动切换后 SharedPreferences 记住）。
- **原样播放控制条**：播放/暂停、`mm:ss / mm:ss`、章节上一页/下一页（按 chapters seek，非 ±10s）、章节刻度进度条（点击/拖动 seek）、
  倍速 1×/1.25×/1.5×/2×（`PlaybackParams`）、全屏（横竖屏切换）、下载原样视频（DownloadManager）、关闭、模式切换；
  点击舞台显隐控制条、左右滑切章节、键盘 Space/←/→/F/Esc/M；3s 自动隐藏（暂停时常驻）。
- **降级状态机**：视频 error/卡顿 4s×2 → 重试 1 次 → 自动切分步 + Toast；章节跳页 `seek(t+0.05)` 保持播放/暂停状态。
- **续播**：SharedPreferences 按 deckId 存 `{mode,page,step,t,at}`（7 天），打开自动续播 + Toast「续播 12:34」，
  控制条/更多菜单「从头播放」；列表卡片「继续」小徽标（7 天内 + 未看完）。
- **列表**：整卡点击 = 默认模式直接播放（封面大「▶」）；删除/重试/上传/原件下载保持 v1.1 行为。
- **分步增强**：热点 64dp（音频 48dp）+ 轻微脉冲 + 「视频 m:ss」角标；热点状态驱动渲染（无图片 onload 竞态）。
- 新增纯 Kotlin 层 `PptVideoLogic/PptPlayMode/PptResume/PptVideoFallback` + `PptLocalStore/PptNetworkQuality`；
  `PptVideoPlayer.kt`（MediaPlayer+SurfaceView，自建控制条，**无新增依赖**）。
- 版本 **v3.4.0 / versionCode 10**；单测 **954/0**（v1.2 新增 20 项，原有 934 项基线无回退；
  其中 934 = v1.1 的 904 + 下条「通知附件外置」新增 30 项，一并在本版本发布）。
- 详见 `docs/PPT_VIDEO_20261110.md`（实现/契约对应/OSS 域名与下载限制/已知风险/构建证据）。

## 未发布 · 2026-11-10（通知附件外置：data URI / 签名 URL 双形态，迁移第 2 步 task-5）

- **背景**：云端通知 `body` 的附件将从 base64 `data:` URI 改为签名 URL（`web/attachments/CONTRACT.md` v1）；
  安卓必须同时兼容两种形态，旧数据继续可读。**versionName 仍 3.3.0 / versionCode 9（未 bump、未构建分发 APK）**。
- **数据层**：`NoticeItemMeta.Image/File`、`NoticeManualAttachment` 增加 `url`（只保留 http(s)，不写死域名）；
  新增 `NoticeArchiveFile`（`archive.files[]` 的 name/kind/size/url）；`NoticeBodyParser` 统一用
  `NoticeAttachmentSource` 判定 `data:` / `http(s)` / 无内容，data URI 行为不变。
- **大小显示**：URL 形态下不显示 0 B —— 优先 `items[].size`，否则「附件」（人工附件同理）。
- **图片**：URL 图片走 `container.pptImageLoader`（复用 `data/ppt` 的 OkHttp+BitmapFactory+LRU，无新依赖）；
  缩略图/大图加载失败降级占位，不崩；data URI 图片仍走原有按需加载 + 采样解码。
- **文件**：URL 文件 OkHttp 下载到 `cacheDir/notice_attachments/<sha256前16>/<展示名>`（上限 20MB、命中磁盘缓存不重下、
  7 天自动清理）→ FileProvider + `ACTION_VIEW`（无处理器退回 `ACTION_SEND`）；`archive.files[].url` 逐项可打开。
- **测试**：新增 30 个单测（URL/data URI 双形态解析、archive、大小显示、下载编排假 fetcher/sink、文件名/MIME/cacheKey）；
  相关 `*Notice*` 单测 105/0，全量 `testDebugUnitTest` 全绿（数字见交付报告）。

## v3.3.0 · 2026-11-09（新增「演示」工具：PPT 上传自动转换 + 沉浸式分步播放）

- **新工具「演示」**（工具目录 CLASS 类，不新增底栏项）：班级成员上传 `.ppt/.pptx` → 本机转换机渲染成分步图片 + 抽离视频 →
  任意设备流畅播放（契约唯一真源 `web/ppt/CONTRACT.md` v1）。
- **数据层 `data/ppt/`**：PostgREST 列表/播放清单/建行/分片上传（512KiB→base64、≤4 并行、失败重试 2 次 400/1200ms）/提交转换/软删/重试 +
  转换机心跳（`site_assets id=ppt_agent`，180s 判离线）；列表失败回退本机缓存；SAF Uri 分片读取不整文件进内存；64MiB 上限、标题≤80 字。
- **纯 Kotlin 领域层 `features/ppt/`**：manifest URL 解析、页/步/全篇 step 导航（后退粒度=step）、状态短文案、分片计划、列表过滤排序、播放器手势/转场降级。
- **列表页**：卡片流（封面/标题/页数·上传者/状态胶囊，ready 不显示胶囊）、上传按钮（OpenDocument 过滤 ppt/pptx、可选改标题、就地进度、取消软删）、
  本人删除二次确认、失败重试（上传中断=重选文件 / 转换失败=重新排队）、空态、可见 10s 轮询（LIFECYCLE 感知 + onResume 强拉）、转换机离线横幅。
- **播放器**：沉浸式全屏黑舞台（隐藏系统栏 + KEEP_SCREEN_ON）、letterbox contain、顶部 2px step 进度条、控制条 3s 自动隐藏、
  单击左右 1/3、左右滑动、键盘 →/←/Space/PageUp/Down/Esc/B/T/S、音量键±翻页、页内步点（step≥2）、缩略图横向抽屉跳页、备注抽屉、
  黑屏、视频/音频热点（点击才播、媒体层不随 step 卸载）、tr 转场（低端机降级 fade 200ms）、下载原件（Downloads/浏览器兜底）、退出二次确认。
  项目无 Coil/ExoPlayer → 自建 OkHttp+BitmapFactory LRU 图片加载器 + VideoView，**未新增任何依赖**。
- 版本 **v3.3.0 / versionCode 9**（按既有发布规则递增）；单测 **904/0**（新增 PPT 8 类 87 项，无回退）。
- 详见 `docs/PPT_20261109.md`（实现/契约/已知限制/构建与测试证据）。

## v3.2.0 · 2026-11-09（Round 10：P2 顶栏/权限/提醒页玻璃 + 作业单条提醒）

- **全局顶栏玻璃化**：`CtTopBar` 半透明玻璃条，覆盖所有工具页/设置页，深浅色自动适配。
- **权限与提醒页 / 提醒设置页**：全部卡片换 `CtGlassCard`（半透明+亮描边）。
- **作业单条提醒**：作业列表每行铃铛「提醒我」+ 作业详情「提醒我」→ 选择截止前 1 天/1 小时/截止时刻，走本机 WorkManager 单条排程（权限/过期/超限明确提示）。
- 功能其余不变；单测 779/0；Release 2,577,485 B / SHA-256 `1307A06D…9456`，冷启动 1.06s、logcat 0 FATAL/ANR。
- 遗留：其余 P2 页面玻璃化、真实背景模糊、动效 token 统一、用户第 6 点待补。
## v3.1.0 · 2026-11-08（Round 9：全局视觉 P1 玻璃精修）

- **玻璃卡体系**：新增 `CtGlassCard`（半透明 surfaceContainerLow + 1dp 描边，无阴影规避 GPU 伪影），
  工作台（今日课程/电费/作业/通知/空态）、我的（账号+5 组设置卡）、全部工具页头全部玻璃化。
- **品牌识别**：`CtBrandDots` 三圆点加入「常用工具」「全部工具」页头；背景品牌光晕加强。
- **底栏**：选中 pill 加高光描边 + 悬浮阴影。
- **深色**：玻璃卡自动深色 token，实测不发灰、对比度正常。
- 功能不变；单测 779/0；Release 2,577,485 B / SHA-256 `AF97EC07…4A8C`，冷启动 1.16s、logcat 0 FATAL/ANR。
- 遗留：真实背景模糊（Haze pager 取样问题）、P2 页面玻璃化、作业单条提醒入口、用户第 6 点待补。
## v3.0.0 · 2026-11-08（Round 8：深色模式 + 玻璃底栏 + 通知 v3 + 课表重构 + 全局视觉规范）

- **深色模式全链路**：values-night 主题/窗口底色 + 按主题切换状态栏/导航栏图标（修黑字压暗底/白闪）。
- **底栏玻璃与对齐**：选中 pill 垂直居中（修 8dp 偏移）；改为悬浮 overlay（内容可从玻璃下滚过，页面底部留白）；半透明玻璃 + 镜面高光 + 亮描边 + 悬浮阴影。
- **通知 v3**：设置页「发送测试通知/立即检查+诊断」、专用通知小图标、Doze 错过补发、分组防刷屏、单条提醒（截止前1天/1小时/截止时刻/活动前1小时）、通知详情「提醒我」。
- **课表重构**：比例自适应（手机 5–7 列/平板双栏）、左右滑动切周、16 色稳定配色（浅/深）、课程详情/写日程、下一周/「本周」、下一节/上课中、日视图、导出课表图片；新增教务登录直接拉取课表（密码不保存）。
- **全局视觉规划**：`docs/design/V3_GLOBAL_STYLE.md` + Glass 组件（CtGlassSurface/折射高光/底栏 spec）。
- 并行新增「诊断」工具（服务健康/设备/AI 修复/上报）与客户端诊断能力。
- 单测 **779/0**；Release 2,577,485 B / SHA-256 `615351BF…5012`，两次冷启动 ok、logcat 0 FATAL/ANR。
- 用户第 6 点待补；全局页面 P1 玻璃化、底栏真实背景模糊、作业单条提醒入口留待下一轮。
## v2.1.1 · 2026-11-08（Round 7：通知提醒 + 数据隐私增强 + 小屏收尾）

- **通知提醒（ntremind）**：提醒设置新增「通知提醒」开关；每日汇总「明天/后天」有截止/活动的未处理通知 + 临近 24h 提醒（长期安排/已处理/已过期/人工通知不提醒；点击通知直达通知页）。
- **数据与隐私增强**：「同步范围清单」（userdata v6 11 section + marks 3 类 + 仅本机项）+「本机数据 8 计数」（已完成作业/已处理通知/置顶通知/体测记录/抽签名单/分组名单/点名名单/待补发，与小程序同口径）。
- **小屏身份页修复**：360×640dp 压缩顶部品牌区/卡片间距，首屏直接可见「进入工具站」；协议链接两行不竖排（实测通过）。
- 单测：**639/0**（新增 36：通知提醒 20 + 本机数据 9 + 同步范围 7）。
- 版本：versionName 2.1.1 / versionCode 5；Release 冷启动自检通过（toolchain 最终 E2E/矩阵/Release 复验见文档 §9/§10）。
## v2.1.0 · 2026-11-08（Round 6：小程序对齐 P1 + 小屏/平板收尾）

- **反馈建议**：我的 → 关于 → 反馈建议；500 字表单、四态、普通用户只看自己的反馈、管理员可筛选并标记已处理（反馈表 E19–E21，联系方式停收）。
- **使用说明**：我的 → 关于 → 使用说明；18 个分节（身份/工作台/抽签/分组/名册/作业/通知/课表/体测/电费/水量/提醒/写日程/权限/排序/反馈/隐私/FAQ），零网络零外链。
- **我的数据**：「我的」页新增 4 指标卡（待交作业/已完成作业/抽签记录/体测记录，mp 同口径）+ 班级人数等可选扩展 + 提醒入口状态（未开启/通知权限未开启/已开启范围）。
- **小屏修复**：身份页同意行改为两行布局（勾选说明 / 协议链接），360×640dp 不再溢出或逐字竖排。
- **自适应收尾**：工具网格 2/3/4 列随宽度变化，拖动排序命中几何同步参数化（P1-1 平板修复）；「我的」页 ≥840dp 双栏；工作台双栏间距、CtTopBar 平板留白收紧；深色对比度复核全部 ≥AA。
- **Release P0 修复**：R8 裁剪 WorkManager/Room 生成类导致 release 冷启动崩溃；`proguard-rules.pro` 增加 Room/WorkManager keep 规则后复验通过（详见 RELEASE_BUILD §8）。
- 验证：全量单测 **603/0**（新增 36：反馈 23 + 指标卡 13，另含自适应 15）；预览新增 19 张；Debug E2E **34/0**；Release 两次冷启动通过；4 设备矩阵 P1 修复确认。
## v2.0.0 · 2026-11-08（Round 5：v2 大版本）

- **品牌图标统一**：启动图标/应用内 BrandMark 改为与网页 favicon、微信小程序 `app-icon-1024.png` 完全一致的三圆点（暖白底 + 雾青/灰蓝/暖沙），含单色自适应图标。
- **ColorOS 17 浮岛式底栏**：半透明胶囊悬浮离底、弹性药丸跟手（滑动中拉伸/压缩）、按压柔性反馈、TalkBack 语义；替换原 NavigationBar。
- **三 Tab 滑动切换**：工作台/工具/我的横向 Pager，页面 8% 位移 + 淡出 + 0.98 缩放跟手；工具页 push 转场（slide+fade+scale spring）；预测返回兼容。
- **工作台对齐微信小程序**：问候（时段+姓名+日期/周次/角色）→ 今日课程时间线（多节/上课中/已结束/未开始/未同步）→ 宿舍电费卡 → 作业待交（最新一条 5s 轮换）→ 通知待处理（同步中/离线/失败四态）→ 常用工具（≤4/空态引导/「全部 ›」）；≥600dp 双栏。
- **多比例与平板自适应**：compact/medium/expanded 断点、内容限宽居中、2/3/4 列工具网格、双栏 helper；工作台/工具/我的页适配。
- **权限管理**：新增「我的 → 权限与提醒 → 权限管理」（通知/日历四态、理由说明、永久拒绝跳系统设置）；Manifest 增加通知/日历权限。
- **原生通知提醒**：作业截止（每日 20:00 汇总 + 截止前 0/1/3 天）与上课提前（5/10/15 分钟）本机通知；WorkManager 15min 周期、BootReceiver 重排、安静时段、点击通知直达作业/课表；提醒设置页（本机偏好，不同步）。
- **写日程**：课表课程详情「添加到系统日历（本周）」、通知详情「加入日程」；有权限直接写 CalendarContract（含 10 分钟提醒），无权限退回系统日历插入界面。
- 预览 20+ 张新增；单测 552/0（新增 86：dock/adaptive/workbench 27 + reminder/calendar/permission 59）。
## v1.0.1 · 2026-11-08（Round 4）

- **外观**：新增「我的 → 外观 → 壁纸取色（Material You）」开关（DataStore 本机持久化，重启保持）；深浅色跟随系统。
- **设置入口**：「全部工具 → 设置」不再打开占位页，直接落到「我的」页签。
- **首页快捷**：编辑保存后 Toast「首页快捷已保存」。
- **离线/同步关闭恢复**：新增 `UserDataRepository.hydrateFromLocal()`，启动时先恢复本机持久化的 userdata sections（homeTools/toolOrder/water/roster/draw 等），离线或关闭云同步时不再回落默认值。
- **无障碍**：全部工具 TalkBack 支持「上移/下移」自定义操作（与拖动同一持久化路径）；icon-only 补充 contentDescription；1.3x 字体下工作台/我的页排版修复；新增 `docs/ACCESSIBILITY.md`。
- **对比度**：电费趋势轴标签（α0.45→0.8）与课表节次时间（α0.7→0.85）修复；其余关键正文组合达标。
- **发布审计**：`docs/RELEASE_AUDIT_20261108.md`（只读审计清单）。
- 单测：466/0（37 类）；E2E 基线 28/0；Release 签名复验（见 RELEASE_BUILD §7）。

## v1.0.0 · 2026-11-08（Round 1–3）

- 8 个工具原生页面：抽签（三步/剔除重抽/结果单/一键分组）、分组、体测（国标评分/历史云同步）、作业、通知（附件图片预览）、课表（周/日视图）、电费（趋势/慢更新）、水量、名册（点名）。
- 身份门禁（学号+姓名 → class_verify，30 天 token）、userdata v6 双向同步、marks 同步、导出卡片 v2（相册/分享）。
- 首页今日（下一节课/待完成作业）、常用工具编辑、全部工具拖动排序（prefs.toolOrder 云同步）。
- 隐私合规：同意门禁 + 用户协议/隐私政策 + 数据与隐私页（导出我的数据/清除云端/清除本机）。
- 修复：底部导航吞点击、抽签/分组返回状态丢失、课表空态文案三态、通知附件按需加载。
- 交付：签名 Release APK（2.26 MB）+ 安装说明（桌面）。






