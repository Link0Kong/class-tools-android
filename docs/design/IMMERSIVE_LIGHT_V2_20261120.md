# 沉浸光感 v2 · 设计规范与实现说明（2026-11-20）

> 目标：按华为 HarmonyOS「沉浸光感（Immersive Light）」设计语言，重做安卓端 UI / 页面布局 /
> 按钮 / 图标 / 加载动画，并同时覆盖浅色与深色。本文是实现口径（数值唯一来源见代码）。

## 1. 设计依据（官方）

| 主题 | 官方要点 | 本次落地 |
| --- | --- | --- |
| 理念 | 「消除界面与内容之间的视觉割裂」；材质是「具备光学扩散与动态透光特性的数字介质」 | 全局**光场**做底，材质压在其上透光 |
| 5 档材质 | ULTRA_THIN / THIN / REGULAR / THICK / ULTRA_THICK（超薄→超厚，模糊与不透明度递增） | `CtGlassTable`（`ui/theme/Glass.kt`）5 档 × 3 强度 × 明暗 = 30 组参数 |
| 3 档强度 | 强（EXQUISITE）/ 均衡（GENTLE，默认）/ 弱（SMOOTH），底层自动映射 | 沿用既有「强 / 均衡 / 弱 / 关闭」，映射为填充 alpha、模糊半径、折射与高光系数 |
| 布局规范 | 顶部悬浮 = ULTRA_THIN + **渐变模糊**；底部悬浮 = THIN + 渐变蒙层；弹出 = THICK；半模态/弹窗 = ULTRA_THICK | 顶栏/悬浮条、底部浮岛、菜单、弹窗各自取档 |
| 视觉特性 | 通透材质、渐变模糊、按压弹性反馈、按压点光源、材质流光、智能反色 | 6 项全部实现（见 §3） |
| 功耗红线 | 材质是「稀缺资源」：控制面积与层数、禁止嵌套、禁止叠加背景模糊、不要压视频 | 卡片不做真模糊（只透光）；真模糊只用于顶栏/悬浮条；见 §5 |

参考链接：
- [沉浸光感 · 设计规范](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/immersivelight-0000002612101053)
- [沉浸光感 · 空间化最佳实践](https://developer.huawei.com/consumer/cn/doc/doccenter-advanced-features/bpta-spatiality-immersive)
- [沉浸光感简介（ArkUI，5 档与三种空间动效的唯一定义源）](https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/arkts-immersive-light-sense-overview)

## 2. 光场（Light Field，一切的底）

`ui/theme/LightField.kt` + `ui/components/background/CtAuroraField.kt`

- 结构：中性底 + **4 团品牌柔光斑**（雾青 / 灰蓝 / 暖沙 / 薄荷）+ 斜向流光 + 暗角；
  浅色板近白冷调、深色板近墨并改用 `BlendMode.Screen` 让光斑「发光」。
- 动效：光斑 28–46s 周期漂移 12–24dp，8fps 时间戳推进（只重绘不重组）；`reduce-motion` 时静态。
- **全局唯一实例**：在 App 根（`CtImmersiveStatusBar`）用 `drawBehind` 绘制一份，从屏幕最顶端到底端连续；
  页面级 `ExpressiveBackground` 通过 `LocalCtRootAuroraActive` 自动跳过重复绘制。
  （这样才不会出现「状态栏留白条 → 页面」的接缝 —— 这是 v1 的最大观感问题。）

## 3. 材质引擎（Glass Engine）

`ui/components/glass/CtGlassEngine.kt`，入口 `Modifier.ctGlass(...)`，参数表在 `ui/theme/Glass.kt`。

自下而上六层：

1. **外投影**：浅色墨色柔影 / 深色极淡黑影（靠亮边分层）；
2. **材质滤镜**（可选真模糊）：`Haze` 采样同窗口内容，API 31+ 走 RenderEffect；
3. **介质底色**：上亮下暗的白色/深青灰渐变 + 极淡品牌赋色；
4. **点光源高光**：左上柔白径向，按下时**跟随手指**（`CtGlassPressState`）；
5. **折射亮边**：上沿 1.2dp 亮线 + 下沿暗线 + 左右极淡侧边；
6. **底部内阴影**：塑造「玻璃有厚度」。

强度语义：强 = 更透 + 更大模糊 + 更亮高光；弱 = 更实 + 更小模糊 + 高光收敛。
可读性：承载正文的材质不透明度下限 `MIN_BODY_ALPHA`（浅 0.38 / 深 0.54），实测正文对比度 ≥ 9:1。

**真模糊的取舍**：卡片自己就是内容，做 Haze 会采样到自己（而且违反官方「控制面积」红线），
所以卡片 = 半透明 + 高光 + 亮边（光场本身是低频渐变，视觉等价于「糊过的背景」）；
顶栏 / 悬浮条才做真模糊（`LocalCtHazeState`）。

## 4. 组件与页面

| 组件 | 位置 | 说明 |
| --- | --- | --- |
| `CtRootHeader` | `ui/components/CtPageHeader.kt` | 页面大标题（34sp Bold）+ 眉标 + 副标题 + 玻璃动作按钮 |
| `CtStickyHeaderBar` | 同上 | 常驻悬浮玻璃条：滚过 12dp 后由全透明 → 材质+模糊+顶部融合带（渐变模糊） |
| `CtGlassIconButton` | 同上 | 44dp 圆形玻璃按钮（按压弹性 0.92 + 点光源 + 可选角标） |
| `CtButton` | `ui/components/CtLayout.kt` | 四档（Filled/Tonal/Outlined/Text）：Filled = 品牌渐变，**按下时胶囊→12dp 圆角**（M3 Expressive pressed shape）+ 点光源；Tonal = 玻璃材质 + 主色文字 |
| `CtGlassSurface` | `ui/components/background/CtGlassSurface.kt` | 所有卡片/横幅的唯一材质入口（THIN / ULTRA_THIN） |
| `CtFloatingDock` | `ui/components/dock/` | 底部浮岛 = ULTRA_THICK 玻璃 + 弹性药丸指示器 + 触摸流光 |
| `CtLoadingIndicator` | `ui/components/expressive/` | M3 Expressive 形变加载：7 形状闭环、650ms 节拍、弹簧 0.6/200、每次 +90°、4666ms 匀速自转（官方常量），非 `CircularProgressIndicator` |

页面骨架（工作台 / 工具 / 我的）：`光场背景 → 大标题区（随内容滚动）→ 玻璃卡片流 → 悬浮玻璃条 → 底部浮岛`。
首页/工具页/我的页均已有 haze 源 + 悬浮条；工具卡图标全部来自 Material Icons（`androidx.compose.material.icons` 标准库）。

## 5. 性能与红线

- 卡片不做 Haze；真模糊只出现在悬浮层（同屏 ≤ 2 层）；
- 光场单实例、8fps 漂移、`reduce-motion` 静态；
- 浮动条/浮岛不压在视频上（播放器页强制深底 `baseColorOverride`）；
- 关闭光感（外观设置）→ 全部材质退化为实色规范（保留描边与投影），不渲染任何模糊层。

## 6. 验证

```
.\gradlew.bat testDebugUnitTest          # 1733 个用例通过（含新增 8 个加载动画几何用例）
.\gradlew.bat :app:assembleDebug
tools\dev\shot.ps1 -Tab home            # 模拟器实测截图（浅色）
tools\dev\shot.ps1 -Tab home -Dark      # 深色
```

实拍（1080×2400 / Android 17 模拟器）见 `docs/design/shots/v2/`：首页浅/深、工具页浅/深、我的浅/深、悬浮条。
