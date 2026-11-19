# ROUND 69 · 2026-11-19 · 安卓向鸿蒙对齐（工作台/工具页/我的页/页面切换）+ 法律全文入口 + 网络层审计

> 版本未动：**1.0.0-beta7 / vc90**（本节只是一次 UI/合规/网络收敛改动，未发版、未改版本号、未加依赖）。
> 鸿蒙仓当前有未提交 WIP（`ui/screens/HomeScreen.ets`、`features/home/HomeToolDigest.ets`）——**只读参考，未改动**。
> 未执行 git 提交（Lead 统一 review + commit）。

---

## 1. 需求

| # | 需求 | 交付 |
|---|------|------|
| 1 | 按鸿蒙端 HomeScreen/AppShell/主题/卡片/列表/页面切换，把安卓的**动效与 UI** 收敛式对齐 | 令牌 + 工作台 hero/信息卡 + 工具卡 + 顶栏 + 底栏 + 页面转场 + 加载骨架 + 我的页分组标题（见 §3/§4） |
| 2 | 接入已制作好的隐私政策/用户协议**全文**（不破坏现有摘要） | 摘要保留 + 「查看完整版」入口 ×3（我的页行副标题、摘要弹层、身份页协议勾选处）+ 失败兜底（见 §5） |
| 3 | 网络层审计（只做低风险、可验证优化） | 6 项检查结论 + 2 处低风险修复 + 4 条「大改只给建议」（见 §6） |

---

## 2. 对照的鸿蒙实现（只读）

| 鸿蒙文件 | 关键参数 | 安卓落点 |
|---|---|---|
| `ui/theme/Tokens.ets` | `PAGE_PADDING=16`、`GAP=12`、`CARD_PADDING=14`、`RADIUS_CARD=18 / FLOAT=24 / INPUT=14`、`F_TITLE 26 / F_HEADING 19 / F_BODY 15 / F_LABEL 13 / F_CAPTION 12`、骨架色 `#E6ECEE / #E9EEEF / #EDF1F2` | `ui/theme/Harmony.kt`（新增/修正令牌 + 骨架色） |
| `ui/theme/Motion.ets` | `FAST 160 / NORMAL 260 / SLOW 380`；`pageEnter`=透明度+右移 28+0.985；`pageExit`=透明度+右移 24+0.99；`listItem`=上移 10；`popIn`=0.9 | `HarmonyMotion`（新对象）+ `CtPageTransitions.harmony*` |
| `ui/theme/Breakpoints.ets` | 内容最大宽 `880 / 1080`、`TOOL_MAX_WIDE=960`、`FLOAT_BAR_SPACE=96`、`SIDE_BAR_SPACE=32`、`SIDE_NAV_WIDTH=104`、`BREATH_TOP_WIDE=12` | `HarmonyTokens` 补齐 |
| `ui/components/ToolTile.ets` | 最小高 96、内边距 12、图标块 40（字形 24）、图标-文字 12、主次文本 2、名称 15 Medium、副标题 12×2 行、按压 `scale 0.97 + opacity 0.92`（160ms） | `ui/screens/all/AllToolCard.kt` |
| `ui/components/PageHeader.ets` | 顶栏高 **56**、标题 `F_HEADING` **Bold**、返回 38 视觉热区 | `ui/components/CtTopBar.kt` |
| `ui/components/HomeCardShell.ets` | 3px 顶部品牌条、18 圆角、`shadow radius18/y6`、按压 0.985 | `ui/screens/home/HomeV5Components.kt`（卡片外壳） |
| `ui/screens/HomeScreen.ets` `heroCard` | 底 `#F1F6F2`（页面整体为 `#D9F1F6F2`）、边框 `#DCE7E2`(0.5)、24 圆角、padding 16、三色 3px 条、「今日」奶白胶囊 `#FFFCF6`、问候 24 Bold `#303A35`、副行 12 `#5C6860`、摘要分割线 `#D8E4DE`、阴影 `#0F4E9082` y6 | `HomeHeroCard` |
| `ui/screens/HomeScreen.ets` `iconBadge` / `cardTitleRow` | 图标块 **30**、字形 **17**、标题 `F_LABEL(13)` **Medium / TEXT_SECONDARY**、间距 **8** | `HomeIconBadge` / `HomeInfoCard` |
| `ui/screens/HomeScreen.ets` `skeletonPage` | 三档灰块 + 900ms `opacity 1 ↔ 0.55` 呼吸 | `ui/components/HarmonySkeleton.kt` + `HomeLoadingRow` |
| `ui/shell/AppShell.ets` `tabItem` | 底部悬浮胶囊 3 页签、图标 **20** + 文案 **10** + 间距 2、选中 `MIST_TEAL` + `TINT_TEAL` 药丸、160ms 缓动 | `ui/components/dock/CtFloatingDock.kt` |
| `ui/screens/MeScreen.ets` `notifySection` / 行 | 分组标题 `F_CAPTION` **Medium / TEXT_SECONDARY**、上 16 下 **8**；行高 52；分隔线 0.5 内缩 16 | `ui/screens/profile/ProfileScreen.kt` |

> 说明：鸿蒙 `HomeScreen.ets` 的卡片内容字号（课程行 12/10、列表行 12/10）与安卓现有卡片正文接近，本轮未整体重排卡片正文，避免「为对齐而重写业务卡片」。

---

## 3. 改动文件清单（为什么）

### 3.1 设计令牌 / 动效契约

| 文件 | 改动 | 原因 |
|---|---|---|
| `ui/theme/Harmony.kt` | `PagePadding 20→16` + 折叠屏 24 / 平板 32；`ContentMaxWidthWide 960→1080`；新增 `ToolMaxWide 960`、`FloatBarSpace 96`、`SideBarSpace 32`、`SideNavWidth 104`、`BreathTopWide 12`、`TileGlyph 24`、`TileTextGap 12`、`TextGap 2`、`BadgeBox 30`、`BadgeGlyph 17`、`HairlineWidth 0.5`、骨架三色、hero 阴影；`PageHeaderHeight 60→56`；新增 `HarmonyMotion`（时长/缓动/转场位移与缩放/按压/骨架呼吸）；`HarmonyColors` 增加骨架色（深色独立） | 之前一轮「已对齐」里 `PAGE_PADDING` 误取 20、内容最大宽误取 960、顶栏误取 60；本次按鸿蒙源码逐项校正 |
| `ui/theme/Theme.kt` | `CtSpacing.screenHorizontal 20→16` | 与鸿蒙 `PAGE_PADDING`、与 `CtAdaptiveSpec`（16/24/32）统一口径 |
| `ui/theme/Tokens.kt` | 注释与鸿蒙口径同步 | 避免下一个人再被「20dp」误导 |

### 3.2 组件

| 文件 | 改动 | 原因 |
|---|---|---|
| `ui/components/HarmonySkeleton.kt`（新） | 骨架块/文本块/图标行 + 900ms 1↔0.55 呼吸（`rememberReduceMotion` 时固定 1） | 鸿蒙 `skeletonPage`；原来安卓只有「转圈 + 文案」 |
| `ui/components/CtTopBar.kt` | 标题 `Bold`（高度随令牌 56） | 鸿蒙 `PageHeader` 标题 Bold |
| `ui/components/dock/CtFloatingDock.kt` | 图标 22→20、文案 11→**10sp**（选中 Medium） | 鸿蒙 `tabItem` 图标 20 + 文案 10 + 间距 2 |
| `ui/components/dock/CtTabPager.kt` | 新增 `harmonyPushEnter/PushExit/PopEnter/PopExit` + `HARMONY_*` 常量（380ms / cubic-bezier(.4,0,.2,1) / 7.8%·6.7% 位移 / 0.985·0.99 缩放），旧 `push*` 保留 | 鸿蒙 `Motion.pageEnter/pageExit`；旧口径是 10%/12% + 0.972/0.965（更"Material"、位移更大） |
| `ui/navigation/MainNavHost.kt` | 11 个页面路由 + 工具页路由改用 `harmony*` 转场（根页与工具页 pop 仍 `None`，由快照收回覆盖层负责） | 页面进入/返回与鸿蒙同节奏；工具页退场仍走既有「快照缩回卡片」 |

### 3.3 页面

| 文件 | 改动 | 原因 |
|---|---|---|
| `ui/screens/all/AllToolCard.kt` | 字形 20→**24**、图标-文字 10→**12**、主次文本 3→**2**、按压新增 **opacity 0.92** | 鸿蒙 `ToolTile` 逐项值 |
| `ui/screens/home/HomeV5Components.kt` | hero 内边距 18→**16**、边框 1→**0.5**、下边距 20→**12**、阴影 `0x144E9082 / 6dp`→鸿蒙 `#0F4E9082 y6`；信息卡头部 40dp/20 字形/15sp 主色 → **30dp/17 字形/13sp Medium 次级色**，间距 10→**8** | 鸿蒙 `heroCard` / `iconBadge` / `cardTitleRow` |
| `ui/screens/home/HomeScreen.kt` | 首次加载态由「转圈+文案」改两行骨架（文案转读屏说明）；新增 `HomeRefreshingRow` 用于「已有内容、正在刷新」（避免骨架压住真实内容） | 鸿蒙 `skeletonPage`；同时保留「刷新中」的可读提示 |
| `ui/screens/profile/ProfileScreen.kt` | 分组标题 13 Regular → **12 Medium 次级色**、下间距 6→**8**；协议/隐私两行副标题显示 `摘要 · 完整版 V1.0 · 生效 2026-11-17` | 鸿蒙 `MeScreen.notifySection`；并把全文入口暴露到「我的」 |

### 3.4 法律文本（见 §5）

`ui/screens/privacy/LegalLinks.kt`（新）、`PrivacyDialogs.kt`、`PrivacyTexts.kt`、`ui/screens/identity/IdentityScreen.kt`。

### 3.5 网络层（见 §6）

`data/network/ApiClient.kt`。

---

## 4. UI / 动效对齐点（可视化口径）

| 维度 | 改前 | 改后（= 鸿蒙） |
|---|---|---|
| 页面左右边距 | 20dp | **16dp**（折叠 24 / 平板 32 令牌就位） |
| 大屏内容最大宽 | 960dp | **1080dp** |
| 工具页顶栏 | 60dp / 19 Medium | **56dp / 19 Bold** |
| 工具卡 | 图标 20、间距 10、文本 3、按压 0.97 | **图标 24、间距 12、文本 2、按压 0.97 + 透明度 0.92** |
| 工作台 hero | padding 18、边框 1dp、下边距 20、阴影 8% 雾青 | **padding 16、边框 0.5dp、下边距 12、阴影 6% 雾青 `#0F4E9082`** |
| 工作台信息卡头 | 40dp 图标块 + 15sp 主色标题 + 10 间距 | **30dp + 17 字形 + 13sp Medium 次级色 + 8 间距** |
| 底部页签 | 图标 22、文案 11 | **图标 20、文案 10（选中 Medium）** |
| 我的页分组标题 | 13 Regular | **12 Medium 次级色** |
| 页面进入/返回 | 位移 10%/12%、被覆盖页左移 6%、缩放 0.972/0.965、spring | **位移 7.8%/6.7%（≈28/24vp）、同向右移、缩放 0.985/0.99、380ms cubic-bezier(.4,0,.2,1)** |
| 加载骨架 | 转圈 + 文案 | **两行灰块 + 900ms 呼吸（1↔0.55）**；文案改为读屏说明 |

**刻意保留的偏差（如实）**：
1. 卡片保持**不透明白 + 2dp 柔阴影**，没有采用鸿蒙的半透明白 + 白色发丝边：安卓 `ExpressiveBackground` 是纯色底（鸿蒙有一层环境光渐变），半透明白叠在 `#F7F8F8` 上视觉差 <2%，白描边在浅底几乎不可见，照抄只会增加绘制成本。
2. 底栏选中文字色仍用 `LightPrimary #24695C`（鸿蒙是品牌雾青 `#4E9082`）：10sp 小字用 `#4E9082` 对白底只有 3.7:1，不达 WCAG AA；品牌色只用在图标/图形上（见 `docs/ACCESSIBILITY.md` 的既有口径）。
3. 转场位移用「屏宽比例」而非固定 dp：`NavHost` 的 `transitionSpec` 不是 `@Composable`，拿不到 `LocalDensity`；按 360vp 手机折算（28/360、24/360），大屏位移略小于 28vp，观感更收敛。

---

## 5. 法律全文接入（隐私政策 / 用户协议）

### 5.1 方案与理由

- **入口方式：`ACTION_VIEW` 系统浏览器**（`LegalLinkOpener`）。
  - 不新增依赖、不新增权限、不写 WebView；
  - release 的 `network_security_config` 是**全面禁明文**（只有教务 `210.34.74.77` 放行），ECS 目前只有 `http://121.196.47.2` 明文入口 —— 若在 App 内用 WebView 加载，release 会被自己的网络安全策略拦掉；
  - 交给系统浏览器则请求不由本 App 发起，不受该策略限制，也不占本 App 内存/进程。
- **URL 基址复用 `BuildConfig.WEB_BASE_URL`**：debug = `http://121.196.47.2`、release = `https://tool.furina.fun`；路径 `legal/android/{privacy,terms}.html`。**代码里不硬编码明文 IP**，`:app:verifyReleaseEndpoints`（release 必须 https 且不含 IP）口径不变。
- **不改摘要**：`PrivacyTexts.PRIVACY_POLICY / USER_AGREEMENT` 仍在弹层里离线可读，只是在版本日期与关键条款上**与全文对齐**（见 5.3）。

### 5.2 入口（三处）

| 位置 | 交互 |
|---|---|
| 我的 → 关于与帮助 → 用户协议 / 隐私政策 | 行副标题 `摘要 · 完整版 V1.0 · 生效 2026-11-17`，点开摘要弹层 |
| 摘要弹层（两篇） | 左下 **「查看完整版」**（浏览器打开）；标题下副标题显示 `摘要 · 完整版 V1.0 · 生效 2026-11-17`；正文底部显示全文路径 |
| 身份页协议勾选处 | 两个 `TextButton`（摘要）下方新增一行 `完整版 用户协议 · 隐私政策`（12sp 次级文字，直接打开全文） |

### 5.3 摘要与全文一致性

- `PrivacyTexts.VERSION`：`2026-11-08` → **`2026-11-17`**（= 全文 V1.0 生效日期，见 `legal/android/*.html` 页头「文档版本 V1.0 / 生效日期 2026年11月17日」）。
- 补齐全文里的关键条款（原来摘要缺失）：处理者与联系邮箱 `Ko_Link@outlook.com`、15 个工作日响应、处理清单要点、第三方/委托处理（CloudBase 上海 / 阿里云 ECS / **OSS 中国香港跨境** / DeepSeek / OPPO）、保存期限（附件 180 天、投票 30 天、PPT 90 天、诊断 90 天、反馈 1 年、日志 180 天、备份 180 天）、用户权利与撤回、重大变更「重新取得单独同意」、**「摘要与全文冲突以全文为准」**。
- 用户协议摘要同样补齐：协议主体、测试版本与安装来源提示、使用资格（一个学号 ≤3 个 OPPO 账号）、用户内容与班级数据（不得外传名单/成绩/体测/投票）、第三方、免责、争议解决。

### 5.4 失败兜底

`LegalLinkOpener.openOrCopy(context, url)`：

1. `startActivity(ACTION_VIEW)` 失败（无浏览器 / Intent 被拒 / 任何异常）→ 复制链接到剪贴板 → Toast「未能打开浏览器，链接已复制」；
2. 摘要弹层里额外显示红字「未能打开浏览器，链接已复制：<url>」，摘要正文仍可读、可关闭；
3. 弹层正文底部始终显示 `全文：<host>/legal/android/...`，即使完全没网也能看到地址（可手抄/粘贴）。

---

## 6. 网络层审计（低风险）

### 6.1 检查结论（现状 OK，未改动）

| 项 | 现状 | 结论 |
|---|---|---|
| 超时 | `NetworkClients`：API 连接 15s / 读 30s / 写 30s / 整体 60s；云空间读 120s / 整体 10min；下载整体不设超时；附件整体 120s；登录等按 `ApiRequest.timeoutMs` 分级 | 合理，已分级 |
| 重试 | 只有 **GET** 重试 1 次（350ms + 0~120ms 抖动），另对 502/503/504 重试一次；**写请求永不重试**；`retryOnConnectionFailure(false)`（仅附件只读通道开 true） | 符合「写不重试」契约 |
| 并发 | 共享 `ConnectionPool(8, 5min)` + `Dispatcher(64 / 16 per host)`；工具页/仓库各自 `Mutex`；`PptImageLoader` 图片在途合并 | 不会打爆网络 |
| 重复请求 | `ApiClient` 在途 GET 合并（同 token 指纹 + 同 path）；`AppContainer` 预热与首页首屏并发同一 GET → 仓库 `Mutex` 串行 + TTL 缓存命中，实测只发 1 个请求 | OK |
| 缓存 | 仓库 TTL 缓存（homework 10min、timetable、notices 签名表、power、fitrecs 等）；HTTP 层**不用** OkHttp Cache 且请求带 `Cache-Control: no-store` | 与"数据新鲜度"契约一致，属有意取舍 |
| 错误处理 | 401/403 → `ApiError.TokenInvalid` + `onTokenInvalid` 回调；其余非 2xx → 解析 PostgREST 错误体；坏 JSON → `ApiError.Parse`；网络异常 → `ApiError.Network` | OK |

### 6.2 已修（2 处，低风险、有回归测试）

1. **协程取消被吞成网络失败**（`ApiClient.execute` / `getTimed`）：原来 `catch (t: Throwable)` 会把 `CancellationException` 转成 `ApiResult.Failure(ApiError.Network)`，页面离开/主动取消看起来像"网络错误"，且协程取消无法真正向上传播。现在先 `catch (c: CancellationException) { throw c }` 再兜底。
2. **在途合并把首个调用的取消传染给等待者**（`executeCoalesced`）：原来 `completeExceptionally(t)` 会让其它页面发起的同一 GET 直接抛同一个异常；现在 `complete(ApiResult.Failure(ApiError.Network(cause = t)))`，等待者拿到**可展示、可重试**的失败结果。

回归测试 `ApiClientCancellationTest`（2 项）：取消不产生结果；leader 取消后 follower 拿到 `Failure` 且请求数 ≤1。

### 6.3 只给建议（**未动**，需协议/产品确认）

1. **超时也会被重试**：`SocketTimeoutException` 属 `IOException`，所以"读超时 30s"仍会重试一次 → 最坏约 60s 才失败。建议：`isRetriableNetworkFailure` 对 `SocketTimeoutException` 返回 false（或只在无显式 `timeoutMs` 时重试）。影响面：通知首屏已有 20→10→5→3→1 分页降级兜底，若改需同步回归 `NoticesPaginationTest`。
2. **仓库 `Mutex` 跨网络调用持锁**：慢请求期间其它调用排队（最多一个 call 时长）。改成"共享 in-flight Deferred"体验更好，但会改并发语义与错误传播，本轮不动。
3. **附件通道** `retryOnConnectionFailure(true)` + 整体 120s，弱网大附件可能超时；目前有本地缓存/重试按钮兜底，暂不建议改。
4. **不加 HTTP 缓存**：`Cache-Control: no-store` 是有意为之（避免展示过期班级数据）；若要省流量应做"离线只读快照"，属新功能。

---

## 7. 测试与构建

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线（改动前） | `.\gradlew.bat :app:testDebugUnitTest`（`JAVA_HOME=C:\AndroidToolchain\jdk17`，工程 `jvmToolchain(17)`） | **1632 tests / 0 failures / 2 skipped（130 suites）** |
| 改后 | 同上 | **1652 tests / 0 failures / 0 errors / 2 skipped（134 suites）**，新增 20 项 |
| 编译 | `:app:compileDebugKotlin` | BUILD SUCCESSFUL（仅既有 warning） |
| 打包 | `:app:assembleDebug` | BUILD SUCCESSFUL；`app/build/outputs/apk/debug/app-debug.apk` **29,522,153 B / SHA-256 `E674B4D238063D45A1DAA1E977863F9202630B7944B12799FF9D2EBA802EAB5E`**（本轮（ROUND69）结束时的包；**ROUND70 又改了通知仓库/release 配置，最终包与哈希以 `docs/REPORT_ROUND70_20261119.md` 为准**） |
| release 闸门 | `:app:verifyReleaseEndpoints` | OK（REST=CloudBase，其余 https://tool.furina.fun，无明文 IP） |

### 7.1 模拟器实机验证（`emulator-5562`，1440×3200）

`adb install -r app-debug.apk`（debug 包名 `com.furina.classtools.debug`，**不覆盖**机器上已装的 release `com.furina.classtools`）→ 启动 → 截图：

| 截图 | 结论 |
|---|---|
| `artifacts/round69/home.png` | 工作台：hero 纸面卡三色 3px 条 / 「今日」奶白胶囊 / 24 Bold 问候 / 副行 / 摘要分割线 /「课表 ›」；信息卡 30dp tint 图标块 + 13sp 次级色标题；底栏 3 页签选中「工作台」雾青药丸；页面左右边距 16 |
| `artifacts/round69/all-tools.png` | 全部工具：工具卡 40dp 图标块（24 字形）+ 15sp 名称 + 12sp 两行副标题、18 圆角、12 间距；分组标题 26 Bold；「编辑首页」13 雾青 |
| `artifacts/round69/me.png` | 我的：分组标题 12sp Medium 次级色；身份卡 56dp 首字 + 19 名称；行高 52；协议/隐私两行副标题 `摘要 · 完整版 V1.0 · 生效 2026-11-17` |
| `artifacts/round69/privacy-dialog.png` | 摘要弹层：标题「隐私政策」+ 副标题「摘要 · 完整版 V1.0 · 生效 2026-11-17」+ 可滚动摘要（处理者/邮箱/15 个工作日等新条款）+ 底部 **「查看完整版」/「我知道了」** |
| `artifacts/round69/identity-debug.png` | 身份页：勾选框 + `《用户协议》《隐私政策》` 下方新增 **`完整版  用户协议 · 隐私政策`** 入口（点按直接打开全文） |
| `artifacts/round69/identity-release-beta7.png` | 对照：机器上原 release beta7 的身份页（无「完整版」行），说明改造前后的差异 |

**验证中发现并修掉一个真机才暴露的文案 bug**：我的页行副标题显示成 `完整版 VV1.0`（`VERSION_LABEL` 内部又加了一次 `V` 前缀），已修并把「不得出现 `VV1.0`」写成单测断言（`LegalLinksTest`）。

**「查看完整版」点击实测**：点按后 `mCurrentFocus` 变为 `com.android.chrome/…FirstRunActivity` —— 说明 `ACTION_VIEW` 成功交给系统浏览器（未触发兜底 Toast、未崩溃）；该模拟器 Chrome 处于首次启动引导、且模拟器无外网，因此**页内加载未验证**；URL 可达性已在宿主侧验证（`http://121.196.47.2/legal/android/privacy.html`、`terms.html` 均 **HTTP 200**）。

> 说明：模拟器里跑的是 debug 包，登录态是上一次调试留下的「开发预览」会话（`token` 为空、不请求云端业务数据）；验证「身份页入口」时点了「退出登录」（只清本机会话，不写云端），随后重新装回 release beta7 包（`com.furina.classtools`），模拟器恢复两包共存状态。**全程没有用任何真实账号/真实 token 登录。**

新增测试：

| 测试 | 内容 |
|---|---|
| `ui/theme/HarmonyTokensTest`（8） | 锁定令牌与鸿蒙源码同值（16/12/14/18/24/96/40/56/30/17、字号 26/19/15/13/12、1080/960/96/32/104、Motion 160/260/380 + 28/24 + 0.985/0.99/0.97/0.92、骨架色与 hero 阴影色、明暗两套色） |
| `ui/components/dock/CtPageTransitionsHarmonyTest`（3） | 鸿蒙转场常量、四个转场可构造、旧 Material 口径保留 |
| `ui/screens/privacy/LegalLinksTest`（7） | URL 拼接（带/不带斜杠、空值）、路径固定、展示路径去 scheme、版本口径 = V1.0/2026-11-17、摘要无旧日期且都指向「查看完整版」、摘要补齐处理者/邮箱/15 个工作日/跨境/保存期限/OPPO |
| `data/network/ApiClientCancellationTest`（2） | 取消不被吞成失败结果；在途合并 leader 取消后 follower 拿到 Failure 且只发 1 个请求 |

---

## 8. 未验证 / 风险（如实）

1. **没有逐屏点验全部页面**：模拟器（`emulator-5562`）装了 debug 包并截图确认了工作台 / 全部工具 / 我的 / 隐私弹层 / 浏览器跳转；通知、课表、投票、PPT 等**未逐屏回归**（本轮未改这些页面的布局，但 `PagePadding 20→16` 是所有页共用的令牌，属于全局生效）。
2. **转场观感未录屏验证**：`CtPageTransitions.harmony*` 只有常量单测与编译验证，未录屏比对新旧位移手感。
3. **release 下全文链接暂不可用**：`WEB_BASE_URL` 在 release 是 `https://tool.furina.fun`，该域名备案暂停（DNS NXDOMAIN）；debug（`http://121.196.47.2`）实测 `privacy.html` / `terms.html` 均 HTTP 200。备案恢复后 release 自动可用；在此之前 release 用户点「查看完整版」会走失败兜底（复制链接 + 提示）。
4. **页面转场位移是屏宽比例**（见 §4 偏差 3），大屏比鸿蒙的 28vp 略小；若要严格 28vp 需要把转场包成 `@Composable` 或改 `NavHost` 用法，改动面更大，未做。
5. **`HarmonySkeletonRow` 暂无调用方**（给后续列表加载态预留），不影响构建；如不希望留未用 API 可删。
6. **`CtPageTransitions.pushEnter/pushExit/popEnter/popExit` 仍被预览/DockLogic 使用**，未删除，避免破坏 preview 与既有文档引用。
7. **未改版本号、未加依赖、未碰 key/keystore、未跑 release 构建**（按要求）。
8. 鸿蒙仓 WIP（`HomeScreen.ets` / `HomeToolDigest.ets`）只读，未修改、未提交。
9. 设备上的 release 包 `com.furina.classtools` 保持原样（未覆盖、未卸载重装），debug 包是独立包名 `…debug`。

---

## 9. 回滚

- 纯 UI/文案/网络层小改，**无 DB / 协议 / 版本变更**；回滚 = `git checkout -- <上表文件>`（新增文件删除即可）。
- 若只想回退转场：把 `MainNavHost` 的 `harmonyPushEnter/PushExit/PopEnter/PopExit` 换回 `pushEnter/pushExit/popEnter/popExit`（两套并存）。
- 若只想回退「查看完整版」：移除 `PrivacyDialogs` 的 `dismissButton` 与 `IdentityScreen` 的 `LegalFullTextLinks` 调用即可，摘要不受影响。
