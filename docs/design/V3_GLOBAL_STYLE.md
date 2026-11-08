# V3_GLOBAL_STYLE.md — 班级工具站 v3 全局视觉规划（ColorOS 17 液态玻璃 × 品牌）

> v3.0.0 设计基线｜对应 task-28｜落地组件：`ui/components/glass/CtGlass.kt`、课表页 `ui/screens/timetable/`
> 参考：ColorOS 17 Floating Island / 液态玻璃（2026-08）、Material 3 Expressive、`design/tokens.json`
> 本轮只落地「Glass 组件 + 课表页」；其余页面按 §8 优先级在后续轮次改造（避免一次性重写）。

---

## 1. 用户反馈 → 设计命题

| 反馈 | 设计命题 | 本轮落地 |
|---|---|---|
| ① 手机暗色模式未适配 | 深色不只要「反色」：玻璃/描边/高光/品牌色都要有暗色对应；页面级色板与系统栏一致 | Glass 浅/深双 token；课表 16 色深色变体；系统栏由 Lead 统一 |
| ② 底栏图标偏移、没有液态玻璃 | 浮岛底栏必须有「玻璃填充 + 顶亮边 + 折射高光 + 悬浮离底」，图标垂直光学居中 | `CtGlassDockSpec` + `ctDockGlass`（本层外观；Haze 模糊由 Lead 叠） |
| ③ 通知系统异常/不全 | 属功能缺陷，另任务（task-29） | — |
| ④ 课表比例与网页功能 | 课表是高频页：**比例自适应 + 滑动切周 + 16 色 + 详情/导出** | 本轮完整重构（见 V3_TIMETABLE_WEB_PARITY.md） |
| ⑤ 全局风格重新规划 | ColorOS 17「液态玻璃 + 流体」× 班级工具站「三圆点/暖白/雾青/灰蓝/暖沙」 | 本文件（规范 + 优先级） |

---

## 2. 品牌与色彩

### 2.1 品牌资产（不可替换）
- **三圆点**：`BrandMark`（雾青/灰蓝/暖沙三色圆点）——应用图标与页头品牌符号；
- **暖白** `#F7F8F8`：页面底色与玻璃底色（浅色）；
- **雾青** `#4E9082`：品牌主色（强调/选中/图形）；
- **灰蓝** `#6E8CA8`：次强调（信息/学习类）；
- **暖沙** `#B98F63`：点缀（作业/提醒/水费类），**禁止大面积铺色**。

### 2.2 角色色使用规则
| 用途 | 用色 | 禁止 |
|---|---|---|
| 主按钮/选中态 | `primary`（雾青 tone-40 #24695C）+ `onPrimary` | 品牌雾青 #4E9082 直接做小正文（对比 3.53:1） |
| 玻璃卡文字 | `onSurface` / `onSurfaceVariant` | 半透明玻璃上用低 alpha 文字（<0.7） |
| 状态 | 成功=success、警示=warning、危险=error | 自造第四种红/绿 |
| 课程/分类 | `TimetablePalette` 16 色（浅/深各 16，按名稳定） | 随机色、渐变彩虹 |
| 图表/趋势 | primary + secondary + tertiary | 纯品牌三色混用导致语义混乱 |

### 2.3 深色模式
- 深色不是简单反色：**表面亮一层（#111414 → #191C1C → #282A2B）**、文字保持 `onSurface #E1E3E3`；
- 品牌色在深色用「亮变体」：雾青 #90D3C4、暖沙 #EDBE8E、灰蓝 #ABCAE8（见 `Color.kt`）；
- 玻璃在深色：填充透明度略降、描边/高光 alpha 降低（避免发灰）；
- **页面验收**：每页浅/深各一张 @Preview；关键文字组合对比度 ≥4.5:1（大字号/图形 ≥3:1）。

---

## 3. 液态玻璃体系（Liquid Glass）

### 3.1 层级（L0→L4）
| 层 | 用途 | 材质 | 模糊 | 圆角 |
|---|---|---|---|---|
| L0 页面底 | 页面背景 | 纯色/品牌微渐变 | 0 | — |
| L1 平面卡 | 列表/工具卡 | `surfaceContainerLow` 不透明 | 0 | 16dp |
| L2 玻璃卡 | 工作台模块/课表外壳/我的卡组 | 半透明填充 + 折射高光 | 24dp（浅）/20dp（深） | 24–32dp |
| L3 浮岛 | 底部工具栏 | 高透明玻璃 + 强顶高光 + 悬浮阴影 | 24dp | 32dp（全胶囊） |
| L4 弹层 | BottomSheet/Dialog | 不透明 surfaceContainerLow + 顶部拖拽条 | 0 | 30/28dp |

> 原则：**一层页面最多 2 个玻璃层级**（L2 + L3），避免「玻璃套玻璃」糊成一片。

### 3.2 玻璃参数表（`CtGlass` token）
| 参数 | 浅色 | 深色 |
|---|---|---|
| 填充 start/end alpha | 80% / 60%（暖白系） | 70% / 55%（深青灰） |
| 顶部高光（specular） | 白 60% | 白 35% |
| 1dp 描边 | 上白 90% → 下白 20% | 上白 40% → 下白 8% |
| 底部内阴影 | `#1D2B28` 10% | 黑 40% |
| 品牌薄涂层 | 雾青 8% | 亮雾青 9% |
| 阴影 | 12dp | 10dp |
| 模糊半径 | 24dp | 20dp |

底栏专用（`CtGlassDockSpec`）：填充 78%/72%、高光 55%/42%、折射边 30%/24%、描边 55%/40%、圆角 32dp、阴影 12/10dp。

### 3.3 折射高光绘制（`Modifier.ctGlassRefraction`）
1. **顶部 0–42%**：白→透明 vertical gradient（强度 = specular alpha）；0–14% 保留更亮的「顶光」；
2. **顶部 1dp 亮边**：沿圆角 path 的 stroke，左右边缘渐隐（模拟玻璃边缘折射）；
3. **底部 0.68–1.0**：内阴影渐变（厚度感）；
4. **品牌薄涂层**：顶部 60% 高度、8–9% 透明，随色相融入背景；
5. 所有图层 `clipPath(圆角 path)`，不溢出。

> 性能：纯 Canvas 渐变 + 描边，无 RenderEffect；需要真实背景模糊时由页面在最底层放 Haze 层（Lead 负责依赖），本组件保持半透明即可透出。

---

## 4. 圆角体系
| token | 值 | 用途 |
|---|---|---|
| `hero` | 32dp | 大卡/工作台模块/我的卡组 |
| `toolCard` | 16dp | 工具卡/列表卡 |
| `toolCardPressed` | 12dp | 按压形变 |
| `pill` | 全圆 | 胶囊/按钮/底栏药丸 |
| `sheet` | 30dp | 底部弹层 |
| `dialog` | 28dp | 对话框 |
| `listItem` | 12dp | 列表行 |
| `input` | 4dp | 输入框 |

玻璃组件默认 `hero`（32dp）；底栏 32dp；列表内玻璃卡用 16dp。

---

## 5. 字体层级
- 中文行高：`title/body` 行高 ≥1.35；数字用 tabular（课表时间/电量）；
- 玻璃卡标题 `titleMedium/titleLarge`（600–700），正文 `bodyMedium`，脚注 `labelSmall`（≥11sp）；
- 课表：手机课程名 10–11sp（2 行内），平板 11sp（3 行）；节次号 `labelSmall`、时间 9sp；
- 大字体 `fontScale 1.3`：玻璃卡文字不得单行硬截断（关键信息允许 2 行）。

---

## 6. 动效原则（流体/弹性）
| 场景 | 规格 | token |
|---|---|---|
| 底栏药丸吸附 | spring d=0.6 k=800（轻 overshoot） | `CtMotion.spatialFast` |
| 底栏拖动拉伸 | 水平 ×(1+0.08·|offset|)、垂直 ×(1−0.04·|offset|) | `CtDockLogic.pillScale` |
| 切周（课表） | 跟手 → 150ms 飞出 + 230ms `cubic-bezier(.22,.8,.3,1)` 飞入 | `TimetableSwipe` |
| Tab 滑动 | 8% 位移 + 0.72 淡出 + 0.98 缩放（跟手插值） | `CtDockLogic.pageTransform` |
| 卡片按压 | 0.96 缩放 + 形状 32→28dp | `CtMotion.effects` |
| 玻璃高光 | 静态（不随滚动闪烁）；滚动态可降低 specular 20% | 性能优先 |
| 无障碍 | 系统「减少动画」时禁用飞出/飞入，直接切换 | 由页面读 `Settings.Global.ANIMATOR_DURATION_SCALE` 或 Compose 动画时长倍率 |

---

## 7. 组件清单与状态
| 组件 | 状态 | 说明 |
|---|---|---|
| `CtGlassSurface` / `ctGlassSurface` | ✅ 本轮 | 玻璃卡外观层（填充/描边/折射/内阴影/阴影） |
| `CtGlassDockSpec` / `ctDockGlass` | ✅ 本轮 | 底栏玻璃参数（供 Lead 叠加 Haze） |
| 浮岛底栏（图标/药丸/对齐） | ⏳ Lead | 现有 `CtFloatingDock` 换用 `ctDockGlass` + 图标光学居中修复 |
| 课表（比例/滑动/16 色/导出） | ✅ 本轮 | `ui/screens/timetable/` |
| 工作台玻璃卡 | ⏳ 下一轮 | 用 `CtGlassSurface` 替换 `surfaceContainerLow` 容器（P1） |
| 全部工具/我的 玻璃化 | ⏳ 下一轮 | 仅页头/英雄区玻璃化，列表保持平面（可读性） |
| 通知/权限/提醒 | ⏳ 下一轮 | 状态卡可用浅玻璃（P2） |
| 系统栏/深色窗口 | ⏳ Lead | `ClassToolsTheme` 的 systemBars 处理 |

---

## 8. 页面改造优先级
**P0（用户点名，本轮/下一轮必须）**
1. 浮岛底栏：玻璃材质 + 图标垂直光学居中 + 3 项等距（Lead 接线 `ctDockGlass`）；
2. 课表页：比例/滑动切周/16 色/详情/导出（本轮完成）；
3. 暗色模式全页检查（至少：工作台/全部/我的/课表/权限/提醒 浅深两套截图）。

**P1（v3 内）**
4. 工作台：模块玻璃化（今日课程/电费/作业/通知 → L2 玻璃卡），保持信息密度；
5. 全部工具：页头玻璃化 + 工具卡保持平面（16 色 accent 图标不变）；
6. 我的：卡组玻璃化 + 平板双栏（已双栏，材质升级）。

**P2（v3.x）**
7. 通知/权限/提醒：状态卡浅玻璃、弹层统一 L4；
8. 抽签/分组/体测/水量/名册：仅页头与主卡玻璃化，避免重写交互；
9. 动效统一：所有页面 tab/卡片转场走 §6 token。

---

## 9. 验收清单（每页/每轮）
- [ ] 浅/深两套 @Preview（手机 + 平板）；
- [ ] 关键文字对比度 ≥4.5:1（大字号/图形 ≥3:1），玻璃上文字 alpha ≥0.7；
- [ ] 玻璃层级 ≤2 层/页；无「玻璃套玻璃」；
- [ ] 浮岛底栏与手势条 ≥8dp 间距、图标与文字基线对齐；
- [ ] 动效符合 §6（spring 参数/时长），无 tween 硬切；
- [ ] 大字体 1.3 不裁切关键信息；
- [ ] 深色下玻璃不发灰、不抢内容。

## 10. 本轮落地与接线
- 组件：`ui/components/glass/CtGlass.kt`（`CtGlassSurface`、`CtGlass`、`CtGlassColorTokens`、`CtGlassDockSpec`、`Modifier.ctGlassSurface/ctGlassRefraction/ctDockGlass`）；
- 预览：`ui/preview/V3GlassPreviews.kt`（110–116）；
- 课表：见 `docs/design/V3_TIMETABLE_WEB_PARITY.md`；
- **Lead 接线**：底栏换 `ctDockGlass` + Haze 背景层；系统栏/深色窗口；导出回调 `onExportTimetable`。
