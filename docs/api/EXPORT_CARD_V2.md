# `docs/api/EXPORT_CARD_V2.md` — 导出卡片 v2（Android Canvas 实现规格）

> 唯一真源：`web/tools/tests/export_card_v2_spec.md`（134 行，网页 `exportCard()` 与小程序 `utils/canvas.js drawCard()` 的共同依据）。
> 本文件把规格转成 Android 端可直接实现的数据模型 + 绘制流程 + 验收口径。
> 标记：`[已核实 CARD:*]` 直接来自规格；`[推断]` 为实现建议。

---

## 1. 硬约束（来自 web/AGENTS.md §2，不得违反）

1. 图内必须包含：**参数配置区** + **斜向平铺水印 `tool.furina.fun`** + **页脚生成时间** + **唯一编号**。
2. 文件命名：`<类型>_<YYYYMMDD-HHMM>_<关键参数>.png`，例：`抽签结果_20261009-2024_范围1-50_抽10个_不重复_按抽取顺序.png`；文件名经 `safeName`（`[\\/:*?"<>|\s]+` → `-`）。
3. **不得出现手机号、学号、宿舍号**。
4. `blocks` 只有三种类型（见 §4）；新导出不能自己写 canvas 逻辑，必须组装这三类块。
5. 网页/小程序/Android 除色板、品牌文字、二维码支持外，**结构、间距、字号必须一致** `[已核实 CARD:133]`。

---

## 2. 画布与坐标

| 常量 | 值 | 说明 |
|---|---|---|
| `W` | 760（逻辑 px） | 输出宽 = `W * DPR` = 1520 px |
| `DPR` | 2 | `Bitmap` 宽 1520，高 = `H*2` |
| `M` | 22 | 主卡内缩 |
| 卡圆角 | 22 | |
| 内容盒 | `X = 22 + 28 = 50`；`RX = 760 - 50 = 710`；`CW = 660` | |
| 阴影 | `shadowBlur=26`、`shadowOffsetY=8`、`shadowColor` 见色板；仅用于卡片填充 | |
| 画布高 | `H = 卡底 + 22`，卡底 = `footerDividerY + 70` | 自适应 |

绘制顺序（`[已核实 CARD:9-101]`）：
浅色渐变底 → 主卡（含阴影）→ 头部（装饰竖条/品牌点+文字/标题/副标题/分隔线）→ 参数配置面板 → 内容块（按 blocks 顺序）→ 二维码块（如有）→ 页脚分隔线 + 页脚 → **裁剪到卡圆角内画水印**。

## 3. 色板（两套）

| token | 网页（Android 亦可用网页版，与 App 品牌一致） | 小程序 |
|---|---|---|
| bgTop / bgBottom | `#EFF4F2` / `#F7F8F8` | `#F2EDE2` / `#F4F0E6` |
| card / border | `#FFFFFF` / `#E6E9E8` | `#FFFFFF` / `#E7DED0` |
| ink / ink2 / ink3 | `#23302E` / `#5A6A67` / `#8C9A97` | `#303A35` / `#5C6860` / `#8B948B` |
| primary / primary2 / sand | `#4E9082` / `#6E8CA8` / `#B98F63` | `#5E8F87` / `#7D9EBB` / `#C6A475` |
| panel / panelBorder | `#F4F8F7` / `#E8EEEC` | `#F7F3EA` / `#E9E2D4` |
| chipBg / chipBorder | `#FFFFFF` / `#E3E9E6` | `#FFFFFF` / `#E7DED0` |
| groupBg | `#FAFCFB` | `#FBF8F1` |
| shadow | `rgba(35,48,46,0.13)` | `rgba(48,58,53,0.13)` |
| watermark | `#23302E` | `#303A35` |

品牌文字：网页 `tool.furina.fun`；小程序 `班级工具站`。`[已核实 CARD:17-39]`
**Android 建议**：沿用网页色板 + 品牌文字 `tool.furina.fun`（与网页导出图一致，便于用户跨端对比）。

---

## 4. 逻辑画布数据模型（Android/Kotlin）

```kotlin
@Serializable
data class ExportCardSpec(
    val title: String,
    val sub: String = "",
    val file: String,                          // 最终文件名（含 .png）
    val rec: String,                           // 唯一编号：PREFIX-YYYYMMDD-HHMM-XXXX
    val config: List<Pair<String, String>> = emptyList(),   // 参数配置 [[k,v],...]
    val blocks: List<ExportBlock> = emptyList(),
    val foot: String = "",                     // 页脚左侧文案（如「生成时间 11-07 22:50」）
    val qr: QrSpec? = null,                    // 仅抽签「剔除重抽」使用
    val qrNote: List<String> = emptyList()
)

@Serializable
sealed interface ExportBlock {
    val label: String
}

/** 1) 文本块（兼容旧 rich runs） */
@Serializable
data class TextBlock(
    override val label: String,
    val lines: List<TextLine>,                 // 每行 = 若干 run 或纯字符串
    val mono: Boolean = false,
    val atomic: Boolean = false                // atomic：分词不换行（抽签结果单模式）
) : ExportBlock

@Serializable
data class TextLine(val runs: List<TextRun> = emptyList(), val plain: String? = null)

@Serializable
data class TextRun(
    val t: String,
    val dim: Boolean = false,                  // 11.5px/600 ink3
    val strong: Boolean = false,               // 14px/800 ink（mono 块内用 primary）
    val glue: Boolean = false                  // 与下一 run 不换行（网页排版用）
)

/** 2) 胶囊块（抽签结果） */
@Serializable
data class ChipsBlock(
    override val label: String,
    val chips: List<ChipItem>
) : ExportBlock

@Serializable
data class ChipItem(val n: Int, val nm: String = "")

/** 3) 分组块（分组结果） */
@Serializable
data class GroupsBlock(
    override val label: String,
    val groups: List<List<ChipItem>>,
    val groupLabel: String? = null             // 缺省「第 N 组」
) : ExportBlock

@Serializable
data class QrSpec(val n: Int)                  // n = 二维码版本模块数（n<37 → qMod=4，否则 3）
```

> 三种块与网页 `exportCard({blocks:[...]})` 一致，`[已核实 CARD:104-111]`。网页 text 行也支持「字符串」或「runs 数组」，Android 用 `TextLine.plain` 兼容纯文本。

---

## 5. 逐区版式算法（逻辑 px，自上而下）

> 以下数值全部来自 CARD:41-101，按绘制顺序给实现要点；`[已核实]`。

### 5.1 头部（相对 `cardTop = 22`）

| 元素 | 位置/样式 |
|---|---|
| 装饰竖条 | `x=cardX+14, y=cardY+28, w=4, h=40, r=2`，primary→primary2 竖向渐变 |
| 品牌行 | 基线 `cardY+34`；3 个圆点 r=3、间距 10、cy=基线-4，色 primary/primary2/sand；品牌文字 `X+34` 起，11px/800 ink3 |
| 标题 | 基线 `cardY+70`，30px/800 ink；超宽逐步降字号 30→22，仍超截断加 `…` |
| 副标题 | 基线 `cardY+94`，13px/500 ink3；超宽降 11.5 再截断 |
| 分隔线 | `cardY+118`，1px border，X→RX |

### 5.2 参数配置块

- 块标题基线 `dividerY + 26 = 166`；标题样式：5×5 圆角方块（r=1.5，primary）在 `(X, 基线-11)`，文字 `X+13` 起 12.5px/800。
- 面板 `top = 标题基线 + 12`，左右贴 X/RX，圆角 14，填充 panel，1px panelBorder，`pad=14`。
- 行：标签 12.5px/500 ink3；值 14px/600 ink2；行高 21（首行基线 = 行顶 + 17）；标签与值间距 6。
- **两列判定**：所有行「标签实测宽 ≤104 且值实测宽 ≤190」→ 两列（列宽 `(CW-28-20)/2 = 306`，列间距 20）；否则单列（标签列宽 `min(186, max(110, 最长标签+6))`）`[已核实 CARD:57-60]`。
- 长值在值列内换行（行高 = `max(1,行数)*21 + 3`）；两列时按行成对、行高取最大；面板高 = `pad*2 + Σ行高`。

### 5.3 内容块（按 blocks 顺序）

- 第一个块标题基线 = 参数面板 bottom + 30；后续块 = 上一块内容 bottom + 26；块内容 top = 标题基线 + 14。
- **文本块** `[已核实 CARD:68-70]`：
  - 普通行 14px/400 ink2，行高 24；`mono` 行 20px/800 primary，行高 30（`mono` 块 strong 用 primary）；首行基线 = 内容 top + 15；
  - run 内 `dim` → 11.5px/600 ink3；`strong` → 14px/800 ink。
- **chips 块**（抽签结果）`[已核实 CARD:71-74]`：
  - chip 高 30、圆角 15、左右 padding 11、chip 间距 7、行距 7；白底 + chipBorder；
  - 数字 13.5px/800 primary；与姓名间距 5；姓名 12px/500 ink2；基线 = chipTop + 20；
  - 无 `nm` 只画数字；整块高 = `行数*37 - 7`。
- **groups 块**（分组结果）`[已核实 CARD:75-81]`：
  - 两列网格，列宽 `(CW-14)/2 = 323`，列间距 14，行间距 14；
  - 组面板：圆角 14、groupBg 底、1px panelBorder、padding 12；
  - 组头：`第 i 组` 12.5px/800 ink（基线 panelTop+20）；右侧 `n 人` 10.5px/500 ink3 右对齐；
  - 成员区 top = panelTop+34：成员横排换行，成员间水平距 10，行高 21；数字 11.5px/600 ink3 + 3px 间距 + 姓名 13.5px/700 ink；无姓名只画数字；
  - 组高 = `34 + 行数*21 + 10`；同排两卡取等高，奇数个时最后一卡占半宽。
- **二维码块**（仅抽签，可选）`[已核实 CARD:82-89]`：
  - 位置在全部内容块之后；`qMod = qr.n < 37 ? 4 : 3`，安静区 4 模块，`qBox = qr.n*qMod + 8*qMod`；
  - 通栏 tinted 面板（宽 CW、高 `qBox+12+20`，圆角 14，panel 底 + panelBorder 描边）；
  - 右侧白底板：`plate = qBox+12`，圆角 10、chipBg、chipBorder，距面板右/上各 10；二维码绘于板内 `6+quiet` 处；
  - 左侧：标题「剔除重抽记录」12.5px/800 primary（面板 top+28）；`qrNote` 前 3 条各 12px/500 ink2（top+52 起、行高 18，超宽截断）；底部说明「扫码查看本次剔除与补抽的完整记录」11px/500 ink3（面板 bottom-14）；
  - 区块 bottom = 面板 top + plate + 20 + 4。
- `内容 bottom = 最后一块内容 bottom`。

### 5.4 页脚与画布高

- `footerDividerY = contentBottom + 26`，X→RX，border 色。
- 左：`o.foot` 11.5px/500 ink3，基线 +24；`编号 <rec>` 11px/500 ink3，基线 +44。
- 右：3 个小圆点（r=2.5，间距 8，primary/primary2/sand）+ 品牌文字 11.5px/800 primary，**右对齐到 RX**，基线与 foot 同基线。
- 卡底 = `footerDividerY + 70`；画布高 `H = 卡底 + 22`。

### 5.5 水印（硬要求）

- 先裁剪到主卡圆角路径，再画；alpha **0.03**，颜色 watermark，15px/700，旋转 **-20°**。
- 以画布中心平铺：x 步长 300、y 步长 140，`textAlign=center`。
- 因裁剪在卡内，页面底色上不会出现半截字。`[已核实 CARD:99-102]`

---

## 6. 各导出入口的 blocks 组装（与网页对齐）

| 入口 | title | sub | blocks |
|---|---|---|---|
| 抽签 STEP1 | 随机抽签结果 | Step 1 | `[{label:"抽签结果", chips:A.result.map { ChipItem(n, name(it)) }}]`（+ 可选 `qr/qrNote`） |
| 分组 STEP2 | 随机分组结果 | Step 2 | `[{label:"分组结果", groups:G.result}]` |
| 结果单 STEP3 | 抽签工作流结果单 | Step 1 + Step 2 | 抽签 chips + 分组 groups（有哪个放哪个） |
| 课表 | 课表（周/日） | — | 文本块（按天分行） |
| 作业 | 作业清单（未完成） | Homework | 文本块（每作业 ≤6 行，label=日期） |
| 体测 | 大学体测成绩 | 国家学生体质健康标准（2014 年修订） | 文本块（单项成绩 + 学年总分 mono 块） |

`[已核实 CARD:113-125; WEB:6441-6457, 6522-6535, 7303-7308, 7657-7676]`

参数配置示例（抽签）：`范围 / 抽取个数 / 允许重复 / 排除序号 / 结果排序 / 可用池 / 参与名单 / 剔除重抽` `[已核实 WEB:6449-6451]`。
文件命名示例：`抽签结果_<stamp>_范围<c.rangeF>_抽<c.count>个_<允许重复|不重复>_<sort>`.png `[已核实 WEB:6447]`。

---

## 7. Android 实现建议

1. **不使用 Compose 直接截屏**，用 `android.graphics.Canvas` + `Bitmap`（与两端 canvas 2D API 一一对应，字体用 `Paint(typeface)`；中文回退 `Noto Sans CJK SC`/系统字体）。
2. **尺寸换算**：逻辑 px → 物理 px 全部乘 `DPR=2`；`Paint.textSize = 逻辑字号 * 2`；`Bitmap.createBitmap(760*2, (H*2).roundToInt(), ARGB_8888)`。
3. **量宽**：`Paint.measureText`；与网页 `ctx.measureText` 语义一致（误差 ±1–2 px 可接受，但**版式判定分支**（两列/单列、截断）必须同规则）。
4. **水印**：`canvas.save(); canvas.clipPath(圆角矩形); canvas.rotate(-20°, cx, cy); for (x,y) drawText("tool.furina.fun", ...)`；Paint alpha = `0.03*255≈8`。
5. **二维码**：抽签导出需要 QR（网页是零依赖 byte 模式 ECC-L mask0 版本 1–6 生成器 `[已核实 WEB:5780-5852]`）。Android 可用 ZXing 生成（**新依赖需 Lead 批准**）；若暂不做二维码，可先只导出无 QR 版本，但抽签页「剔除重抽」记录的扫码能力会缺失 → 建议作为 P2。
6. **保存**：写入 `MediaStore.Images`（`RELATIVE_PATH=Pictures/班级工具站`），`ContentValues` 带 `DISPLAY_NAME=file`、`MIME_TYPE=image/png`；Android 10+ 无需存储权限。分享用 `FileProvider` + `ACTION_SEND`。
7. **验收**：沿用 `tools/tests/export_check.py` 的判据：标题/参数/内容/水印/时间/编号/品牌齐全；无右侧溢出、无底部截断、无空底；图内无学号/宿舍号 `[已核实 CARD:127-134]`。Android 单测可用 Robolectric 渲染成 Bitmap 后断言尺寸/像素抽样，并附人工目检截图。
