# 架构设计 — 班级工具站 Android 原生 App

> 目标：**真原生**（非 WebView 套壳）+ Android 17 设计语言 + 与既有 CloudBase 数据层互通。
> 本文是实现的唯一架构口径；数据细节以 `docs/BACKEND_CONTRACT.md` 为准。

## 1. 设计原则

1. **原生优先**：所有核心页面用 Compose 原生实现；仅「通知附件/富文本预览」等确实无法结构化的内容保留 WebView 兜底。
2. **单一真源**：身份、`marks`、`userdata v6` 全部走云端；本机只做缓存与离线兜底。云端契约与网页/小程序完全一致，不新增顶层键、不改变合并语义。
3. **慢网优先**：Android 手机网络波动大，所有请求有超时、GET 一次重试、写请求不重试；读缓存先显示再刷新（SWR）。
4. **隐私默认最小化**：不采集、不上传通讯录/定位；学号/姓名只用于核验；日志与导出不暴露隐私字段。
5. **可验证**：每个页面都有 `@Preview`（无设备渲染截图）+ 可自动化验证的状态机。

## 2. 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| UI | Jetpack Compose + Material 3 `1.4.0`（Expressive 自建层 `ui/components/expressive/`） | Compose-first 是 Android 17 官方方向；1.4.0 stable 不含 M3 Expressive 公共组件，故自建 |
| 导航 | `androidx.navigation:navigation-compose` + 自适应外壳 | compact 底部短导航（工作台/全部/我的）；≥600dp NavigationRail；工具页全屏 push + 预测返回 |
| 状态 | ViewModel + `StateFlow` + `collectAsStateWithLifecycle` | 单向数据流 |
| 序列化 | `kotlinx.serialization-json` | userdata v6 动态 blob、REST 模型、导出卡 spec |
| 网络 | OkHttp 5 + 手写轻量 PostgREST 封装（`data/network/ApiClient`） | Bearer 公开 key + `X-Class-Token`；GET 重试 1 次、写不重试；401/403 → TokenInvalid → 清会话 |
| 本地 | DataStore Preferences（会话/设置）+ 文件 JSON（名单/ userdata 基线） | 按 sid 隔离；后续按需引入 Room 只用于大数据量缓存 |
| 并发 | Kotlin Coroutines / Flow | 同步防抖 1200ms、离线 outbox、TTL 60s/2min/6h |
| 导出 | `android.graphics.Canvas` 原生渲染（`features/export/`） | 760 逻辑宽 × DPR2；参数区+水印+页脚+编号；MediaStore 保存 / FileProvider 分享 |
| 构建 | AGP 9.5.1 built-in Kotlin + Gradle 9.9.0 + JDK 17 | compose/serialization 插件 2.4.21；compileSdk/targetSdk 37；minSdk 26 |

> 不引入 Hilt/Koin：本项目依赖关系简单，手工 `AppContainer` 足够，编译更快、APK 更小。
> 不引入第三方 QR/图片库：二维码为 P2；如需再评估 ZXing（需 Lead 批准）。

## 3. 分层与目录

```
data/
  session/        Session 模型 + DataStore 会话存储（token/sid/name/expiresAt/debug）
  auth/           AuthRepository（class_verify RPC）
  network/        ApiClient（OkHttp + PostgREST 助手 + 错误映射 + 401 拦截）
  sync/           UserDataRepository（userdata v6 合并/防抖/离线队列）+ WaterNormalizer
  member/         班级名单（rpc/class_members + 6h 按 sid 缓存）
  marks/          marks（hw/ntdone 读写、乐观更新、pending 补发）
  homework/       作业仓库（GET homework，TTL 10min）
  notices/        通知仓库（两级签名拉取、CLASS_NOTICE_V1 解析、override/hidden）
  timetable/      课表仓库（site_assets id=tt，6h TTL，周次/下一节纯函数）
  power/          电费仓库（power / power_history，2min/5min 缓存，慢频统计）+ 宿舍目录仓库（E21，5min 缓存；不硬编码房号）
  fitrecs/        体测历史云端读写（最多 2 条、arch 软删、pending 补发）
  privacy/        数据与隐私（导出 JSON 脱敏/清除云端与本机）
  settings/       AppSettingsStore（壁纸取色等本机外观偏好）
  reminder/       本机提醒（设置/规划纯函数/WorkManager/通知渠道/去重/BootReceiver）
  calendar/       写系统日历（CalendarContract + ACTION_INSERT 兜底 + 课表/通知 Mapper）
  permission/     运行时权限四态判定 + 请求/设置跳转（通知/日历）
  tool/           工具目录（id/名称/图标/分类，与 homeTools 同步契约对齐）
features/
  draw/           抽签领域层（引擎/校验/状态机/编解码/导出 payload）
  group/          分组领域层（均衡分组/状态机/编解码/导出 payload）
  fit/            体测领域层（国标评分/BMI/等级/fitrecs 编解码/导出 payload）
  home/           首页常用工具编辑（userdata homeTools 适配）
  export/         导出卡 v2 原生 Canvas 渲染 + MediaStore/FileProvider + payload 映射
ui/
  theme/          设计令牌（颜色/字体/形状/动效/主题/Tokens）
  adaptive/       自适应断点/内容限宽/双栏 helper（compact/medium/expanded）
  components/     品牌标记、顶栏、工具卡、状态视图、表达性背景
  components/dock/ ColorOS 17 浮岛底栏 + 三 Tab Pager + push 转场
  components/expressive/  自建 M3 Expressive 组件（8 个）
  screens/        identity / home / all / profile / draw / group / fit / homework / notices / timetable / power / water / roster / data / settings
  navigation/     根页 Pager（工作台/工具/我的）+ push 工具页转场
  preview/        全部 @Preview（android preview render / IDE 共同使用）
```

## 4. 身份门禁流程（对齐网页/小程序）

```
启动 → 读 DataStore 会话
  ├─ 无 token → 身份页：学号(10 位数字) + 姓名 → POST rpc/class_verify
  │     成功：保存 {token,sid,name,role,badge,is_admin,issuedAt} → 进入首页
  │     失败：展示云端 reason；网络错误给通用文案
  └─ 有 token 且 now-issuedAt < 29 天 → 直接进入首页（后台静默刷新）
        任一数据请求 401 → 清除会话 → 回身份页（Android 端新增的健壮性）
```

请求头：
- `Authorization: Bearer <PUBLISHABLE_KEY>`（公开 key，与网页/小程序相同，可进 APK）
- `X-Class-Token: <班级 token>`（服务端同时认 `X-Device-Id`，Android 统一用前者）
- `Content-Type: application/json`；需要返回行时 `Prefer: return=representation`

## 5. 同步设计（userdata v6 + marks）

- `userdata`：`id=sid` 单行 blob；顶层 14 键 `v, at, homeTools, fit, draw, group, roster, hide, tt, prefs, nick, power, water, homeToolsAt`；**逐 section LWW**（`remote.at[k] >= local.at[k]` 云端赢）。
- 推送防抖 1200 ms；拉取 TTL 60 s；离线变更入队，回前台/网络恢复补发；用户可关闭云同步。
- `marks`：`hw`（作业完成）/`ntdone`（通知已处理）唯一真源；TTL 2 min；回前台/切页强拉（5 s 防抖）。
- 冲突策略：逐字段 LWW 与网页/小程序保持一致，**不做自定义合并**；新增同步类别必须先改契约再改三端。
- 本机缓存按 `<sid>` 隔离；退出登录清除主数据桶（对齐网页 H5-3 合规要求）。

## 6. 网络与错误处理

| 场景 | 处理 |
|---|---|
| GET 失败 | 重试 1 次（~350 ms），仍失败显示缓存 + 轻提示 |
| 写失败 | 不自动重试（防重复写入）；入离线队列，下次同步补发 |
| HTTP 401 / RLS 拒绝 | 视为 token 失效：清除会话 → 身份页 |
| 非 2xx | 解析 `{code,message,requestId}`，展示可读错误 |
| 超时 | 连接/读 10 s（写 10 s）；全部请求禁 HTTP 缓存 |

## 7. Android 17 / Material 3 Expressive 落地

- **颜色**：品牌色作为种子（雾青 `#4E9082` / 灰蓝 `#6E8CA8` / 暖沙 `#B98F63` / 暖白 `#F7F8F8`），明暗两套完整 M3 色板；设置中可选「跟随壁纸动态取色」。
- **品牌图标**：启动图标与应用内 BrandMark 统一为网页 favicon / 微信小程序 `app-icon-1024.png` 的三圆点几何。
- **浮岛式导航（v2）**：ColorOS 17 风格半透明胶囊悬浮离底（导航栏 insets 之上 10dp），选中项弹性药丸跟手（滑动中拉伸 8% / 压扁 2%，spring 吸附），按压柔性反馈；平板限宽 480dp。
- **页面切换（v2）**：根页 工作台/工具/我的 横向 Pager（8% 位移 + 淡出 + 0.98 缩放跟手）；工具页 push 转场（slide+fade+scale spring，360ms）。
- **字体**：系统 Roboto Flex（拉丁）+ Noto Sans CJK（中文）；中文行高整体为 1.5–1.6×，标题使用中等字重避免中文过粗。
- **形状**：大圆角与「超级椭圆」语义；工具卡 26dp、英雄卡 30dp+ 异形圆角、胶囊 50%。
- **动效**：弹簧为主（spatial / effects / emphasized 三档），页面转场 320/220 ms；按压时卡片轻微缩放 + 形状回弹。
- **自适应（v2）**：`ui/adaptive` 断点 compact <600 / medium 600–839 / expanded ≥840；平板内容限宽 1200dp 居中、工作台双栏、工具网格 2/3/4 列。
- **预测式返回**：Manifest `enableOnBackInvokedCallback=true` + Compose 返回处理。
- **克制玻璃感**：用径向渐变与层次色（无真实模糊 API 依赖）模拟 Android 17 的柔和层级，低端机不牺牲性能；仅弹层可评估 `Modifier.blur`（API 31+）。
- **无障碍**：可点区 ≥48dp、对比度 ≥4.5:1、全部图标有 contentDescription；全部工具 TalkBack「上移/下移」排序。

## 8. 导出卡片 v2（原生）

- 数据模型：`ExportCardSpec(title, subtitle, params, blocks[], watermark, footer, recordId)`；blocks 三种：文本 / 胶囊(chips) / 两列组卡(groups)。
- 绘制：`Canvas` + `drawText(TextMeasurer)`（Compose 文本排版）→ `ImageBitmap` → `MediaStore` 保存相册 / `FileProvider` 分享；不依赖 WebView。
- 命名与内容规范以 `docs/api/EXPORT_CARD_V2.md` 为准（参数区 + 斜向水印 + 生成时间 + 唯一编号）。
- 体测等健康数据导出前弹窗提示「含健康数据，谨慎分享」。

## 9. 验证策略

| 层级 | 手段 | 频率 |
|---|---|---|
| 编译 | `gradlew assembleDebug` / `assembleRelease` | 每次改动 |
| 单元测试 | `gradlew :app:testDebugUnitTest`（数据层/领域层/编解码；当前 342 用例） | 每次改动 |
| UI 视觉 | `android preview render`（@Preview → PNG，`scripts/render_previews.ps1`） | 每个页面 |
| 模拟器 E2E | `scripts/e2e_smoke.ps1`（文本驱动点击 + 每步截图 + logcat/相册断言） | 每个里程碑 |
| Release | R8 混淆 + 签名 + 安装启动 + `apksigner verify`（docs/RELEASE_BUILD.md） | 发布前 |
| 契约 | 用测试身份对 CloudBase 实测（只读为主，写操作谨慎） | M2/M3 |
| 回归 | 与网页/小程序同数据（同一账号）对照 | 每个工具完成时 |

## 10. 关键约束（来自 web/AGENTS.md）

- 不引入 npm/前端构建链改造 H5；Android 工程独立，不反向影响网页。
- 不触碰 `me.furina.fun` 站点。
- 班级名单（70 人真实名单）只来自云端 `class_members`，不硬编码进 APK。
- 导出/分享不暴露手机号、学号、宿舍号；电费页仅展示本人关注宿舍的楼栋房间号和电量，且不导出。
