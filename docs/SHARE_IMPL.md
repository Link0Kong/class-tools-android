# 分享功能（对齐微信小程序端：5:4 分享卡 + 分享/保存/复制）

> 状态：**已交付**（2026-11-21）。用户要求：「参考微信小程序端对安卓端的分享功能进行增加和优化」。
> 目标：把小程序端 `utils/share.js` + `utils/sharecard.js` + `utils/shareflow.js` 的能力搬到安卓：
> 不再只有「导出图片 / 分享文件」，而是**统一分享弹层**：5:4 卡片预览 + 分享图片 / 保存相册 / 分享链接 / 复制文案。

---

## 1. 与小程序端的对应关系

| 小程序端 | 安卓端（本版） |
|---|---|
| `share.js`：`share.message/timeline/enable`（标题 + path + 5:4 封面） | `ShareSpecs.link/toolUrl`（标题 + 一句话 + 网页深链）+ 系统分享面板 |
| `sharecard.js`：`buildDrawView/buildGroupView/buildNoticeView/buildVoteView/…` + 500×400 画布 | `ShareCardView` + `ShareSpecs.*` + `ShareCardPainter`（逻辑 500×400，导出 1000×800） |
| `shareflow.js`：`open/close/shareImage` 弹层与「发送给朋友 / 保存到相册」 | `ShareSheet`（Compose 底部弹层）+ `SharePresenter.shareImage/saveImage/shareText` |
| 页面 `_shareOpenOpts()` 提供标题与视图 | 各入口直接构造 `ShareCardView`（导出卡自动派生 / 通知与投票专用映射） |
| 隐私口径：投票卡不展示结果、名单不含学号 | 同：`ShareCardView.maskedResults` + `VoteShareInput` 结构上无票数字段 |

---

## 2. 分享入口（本版接线）

| 入口 | 卡片类型 | 内容 |
|---|---|---|
| 抽签结果「导出/分享」 | `DRAW` | 标题 + 参数（范围/抽取数）+ 结果数 + 前 10 个结果 + 编号页脚 |
| 分组结果「导出/分享」 | `GROUP` | 组数 / 人数 + 前 3 组名单 + 参数 |
| 体测成绩「导出/分享」 | `FITNESS` | 总评/等级/加分 + 各项成绩行 |
| 课表「导出/分享」 | `TIMETABLE` | 周次 + 课程数 + 前 6 门课（周几/节次/教室/教师） |
| 通知详情底栏「分享」 | `NOTICE` | 标题 / 分类 / 状态 / 摘要 / 至多 3 条要点 / 时间·地点等至多 2 个字段 / 来源 / 发布者 / 附件数 |
| 投票详情顶栏「分享」 | `VOTE` | 标题 / 状态与规则 / 发起人 / 截止 / 说明 / 至多 4 个选项文字；**固定不显示票数与百分比** |
| 我的 → 分享给同学 | `APP` | 品牌介绍 + 工具列表 + 网页链接 |

所有图片分享都同时携带 `EXTRA_TEXT`（标题 + 链接）；选「分享链接」走纯文本，接收方无分享应用时自动复制到剪贴板。

---

## 3. 卡片规格（`ShareCardPainter`）

- 逻辑画布 **500×400（5:4）**，`ShareCardRenderer` 默认 ×2 → **1000×800 PNG**（与小程序导出一致）；
- 布局：顶栏品牌色 + 三点 + 右上徽标 → 标题（≤2 行，26px/700）→ 副标题（13px）→ 状态胶囊（≤2）→
  统计块（≤3，值 20px + 说明 10px）→ 关键字段（≤2）→ 信息行（≤6，标签 10px + 正文 13px）→ 备注 → 页脚（分隔线 y=350）。
- 绘制与布局分离：`ShareCardPainter` 只依赖 `ShareCardSurface` 接口（可单测），Android 实现把逻辑坐标 × scale 画到 `Canvas`。
- 超长文本按字符换行（中文逐字量宽）并补省略号；超出容量的统计/行数直接截断（不溢出页脚）。

---

## 4. 文件与 Intent

- 图片：`cache/share/share_<kind>_<yyyyMMdd-HHmm>.png` → `FileProvider`（`ct_file_paths.xml` 新增 `share/`）
  → `ACTION_SEND image/png`（含 `EXTRA_STREAM` + `EXTRA_SUBJECT/EXTRA_TEXT` + 读权限）；
- 保存到相册：复用 `ExportCardFiles.saveToGallery`（Android 10+ MediaStore `Pictures/班级工具站`，免存储权限）；
  Android 9 及以下降级为分享面板（与导出卡口径一致）；
- 文案：`ACTION_SEND text/plain`（标题 + 简介 + 链接）；
- 剪贴板：`ClipboardManager` 纯文本；
- 不联网、不上传；文件名不含姓名/学号等隐私字段。

---

## 5. 链接（网页深链）

- 基址：`BuildConfig.WEB_BASE_URL`（debug `http://121.196.47.2`，release `https://tool.furina.fun`）；
- 工具锚点：`<base>/tools/index.html#tab=<toolId>`（与网页 `gotoTab` 的 tab id 一致：hw/nt/tt/vote/draw/…）；
- `ShareKind` 自带 `toolId`；「班级工具站」分享落到工具页首页。

---

## 6. 公开 API

```kotlin
// 模型
enum class ShareKind { DRAW, GROUP, SUM, HOMEWORK, FITNESS, TIMETABLE, ROSTER, NOTICE, VOTE, APP }
data class ShareCardView(kind, title, badge, subtitle, pills, stats, lines, fields, note,
                         footerLeft, footerRight, maskedResults, link)
data class ShareLink(title, text, url) { val message: String }
sealed interface ShareOutcome { Shared; Saved; Failed(message) }

// 构建
ShareSpecs.fromExportCard(spec: ExportCardSpec, kind, baseUrl, nowMillis, timeLabel): ShareCardView
ShareSpecs.noticeCard(input: NoticeShareInput, baseUrl): ShareCardView
ShareSpecs.voteCard(input: VoteShareInput, baseUrl): ShareCardView   // maskedResults = true
ShareSpecs.appCard(baseUrl): ShareCardView
ShareSpecs.link/toolUrl/defaultTitle
ShareMappers.notice(group: NoticeGroupDto, attachmentCount, now, zone): NoticeShareInput
ShareMappers.vote(poll: Poll, detail: PollDetail?, now, zone): VoteShareInput

// 绘制 / 落盘 / 分享
object ShareCardPainter { fun paint(view, surface): Float; fun wrap(...); fun clip(...) }
interface ShareCardSurface { text/roundRect/line/circle/measure }
class ShareCardRenderer(scale = 2f) { fun render(view): Bitmap }
class AndroidShareCardSurface(canvas, scale)
object ShareFiles { fileName/writeCacheFile/imageIntent/textIntent/copyToClipboard }
object SharePresenter { suspend fun shareImage(...); suspend fun saveImage(...); fun shareText(...) }
```

---

## 7. 测试（`ShareSpecsTest`，15 用例）

- 链接：`#tab=` 锚点、斜杠归一、文案拼接；
- 导出卡派生：抽签（结果数/内容行/页脚/链接）、分组（组数人数/组行）、文本块（≤6 行）；
- 通知卡：字段优先级（时间/地点优先、最多 2）、要点最多 3、发布者/附件数/来源、映射自 `NoticeGroupDto`；
- 投票卡：`maskedResults=true`、不出现 `%`/「票数」/「N 票」、选项最多 4、提示「结果请打开投票页查看」；
  映射层即使 `PollDetail.counts` 有值也不进入分享输入；
- 绘制层：记录型 `ShareCardSurface` 断言投票卡无结果、通知卡含标题/分类/状态/来源/时间、统计与行数截断、长标题 ≤2 行；
- 文件名：`share_<kind>_<stamp>.png`，不含敏感字段。
