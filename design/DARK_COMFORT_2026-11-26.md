# Android 深色舒适度与玻璃材质验收

2026-11-26 · 本地待人工验收 · 对照基线 `85f21908e61e`（beta86，versionCode 169）。

## 主题如何生效

`AppSettingsStore` 提供本机外观、动态色开关和玻璃档位。启动时先读同步镜像。`BackgroundStore` 从原有表示恢复九选一预设、可选照片和照片模糊值。`ClassToolsThemeHost` 把两者传给主页面和工具窗口共用的 `ClassToolsTheme`。桌面小组件通过 `appColorScheme` 读取相同的本机预设。

`CtPalette` 保管品牌色。`CtDesign` 定义雾青、暖砂、静蓝三组基础深色表面。`resolveThemeColors` 从预设或 Android 12 及以上系统动态色生成一个 Material `ColorScheme`，再提供对应的 `LocalCtColors` 和 Harmony 组件语义。`CtGlassSurface` 在绘制时读取这组最终颜色。骨架屏使用现有三级容器，空态、错误态、加载指示和 Snackbar 继续从相应语义角色取色。

审查时发现，旧深色背景预设与 Material 表面分别取色，照片保护主要依赖固定遮罩，部分组件各自使用半透明填充。新实现把卡片与详情、状态图标与文字、输入边界与弹层背景分别配对，同一预设和动态色贯穿页面切换。大字号工具格保留原列数和拖动顺序，按最长实际内容统一行高，命中区与拖动预览共用测量结果。

品牌种子为 MistTeal `#4E9082`、SlateBlue `#6E8CA8` 和 WarmSand `#B98F63`。默认预设 ID 仍是 `warm_white`，深色外观采用雾青均衡。暖沙和灰蓝分别采用暖砂夜读和静蓝玻璃的表面。其余预设沿用名称、ID、冷暖方向及渐变属性。九个品牌主色各不相同。动态色开启时，以系统提供的色相和容器为起点，按相同对比度目标修正前景。应用新安装仍使用不透明 M3；外观页现有玻璃开关开启时使用均衡，历史已保存的弱、强档仍可读。照片与玻璃均属设备本地外观。

## 实际生效的颜色

下表从 Android 单元测试导出。每个值都是动态色关闭时最终生效的 sRGB 颜色，以每通道八位、六位十六进制显示。默认预设完整的 48 个 Material 角色保存在设计令牌文件中。[默认 Material 令牌](tokens.json) · [九套语义颜色 CSV](../artifacts/dark-comfort-20261126/resolved-colors.csv)

### 背景与表面

| 预设 | 稳定 ID | 背景 | 表面 | 低 | 标准 | 高 | 最高 | 栏 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 暖白 | `warm_white` | #181D20 | #202629 | #232C2E | #283133 | #2D383A | #354043 | #1D2427 |
| 雾青 | `mist_teal` | #1A2123 | #222A2C | #253031 | #293436 | #2E3B3D | #364345 | #1F282A |
| 灰蓝 | `slate_blue` | #171D26 | #202936 | #242E3B | #293544 | #303D4D | #364657 | #1C2430 |
| 暖沙 | `warm_sand` | #1E1C1A | #272420 | #2B2824 | #302D28 | #36322D | #403B35 | #24211E |
| 深空 | `deep_space` | #12171E | #19202A | #1C242E | #202935 | #25303C | #2A3744 | #161C25 |
| 晨雾渐变 | `morning_mist` | #191E22 | #20262A | #232C2F | #273033 | #2C373A | #343E42 | #1D2428 |
| 斜阳渐变 | `sunset_angle` | #23201D | #2C2822 | #302C26 | #35302A | #3B352F | #443E37 | #292520 |
| 青黛渐变 | `teal_indigo` | #191F27 | #202834 | #232D38 | #27323F | #2D3947 | #32404F | #1D242F |
| 夜幕渐变 | `nightfall` | #141921 | #1C242F | #202834 | #242F3C | #2A3644 | #2F3D4C | #19202A |

### 文字与边界

| 预设 | 稳定 ID | 正文 | 次级 | 三级 | 分割线 | 必要边界 |
| --- | --- | --- | --- | --- | --- | --- |
| 暖白 | `warm_white` | #DCE5E3 | #CEDAD7 | #C5D2CF | #3B4A4B | #9CAEAC |
| 雾青 | `mist_teal` | #DCE5E3 | #D2DDDB | #C9D5D3 | #3B4A4B | #9FB0AE |
| 灰蓝 | `slate_blue` | #E0E6EF | #D8E1EA | #CDD9E6 | #405064 | #A4B3C4 |
| 暖沙 | `warm_sand` | #E5DED4 | #DAD1C7 | #D3C9BE | #49423A | #AFA497 |
| 深空 | `deep_space` | #E0E6EF | #C4D1E0 | #B8C8DB | #405064 | #91A2B7 |
| 晨雾渐变 | `morning_mist` | #DCE5E3 | #CCD8D5 | #C2D0CD | #3B4A4B | #99ABA9 |
| 斜阳渐变 | `sunset_angle` | #E5DED4 | #DDD6CC | #D6CEC3 | #49423A | #B2A89B |
| 青黛渐变 | `teal_indigo` | #E0E6EF | #CFDAE6 | #C5D2E1 | #405064 | #9DACBF |
| 夜幕渐变 | `nightfall` | #E0E6EF | #C9D5E2 | #BFCDDE | #405064 | #98A8BC |

### 按钮与选中态

| 预设 | 稳定 ID | 主色 | 主色上文字 | 主色容器 | 其上文字 | 次色容器 | 其上文字 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 暖白 | `warm_white` | #B0D8CE | #162E29 | #3D4D4D | #DEE7E5 | #3E4D51 | #DFE7E5 |
| 雾青 | `mist_teal` | #B7D9D1 | #17302B | #3C4E4E | #DFE8E6 | #3F4F53 | #E3EAE8 |
| 灰蓝 | `slate_blue` | #C5DAEF | #1E3146 | #405062 | #E8EDF3 | #405062 | #E8EDF3 |
| 暖沙 | `warm_sand` | #DDC8A8 | #2E2419 | #4E463B | #E5DFD5 | #494844 | #E7E1D7 |
| 深空 | `deep_space` | #BBCEE3 | #182838 | #3C4754 | #E0E6EF | #3A4655 | #E0E6EF |
| 晨雾渐变 | `morning_mist` | #B0D4D6 | #152C27 | #3D4C4F | #DDE6E4 | #3E4C52 | #DEE6E4 |
| 斜阳渐变 | `sunset_angle` | #E0CDB2 | #32271B | #534A40 | #ECE6DF | #4E4B47 | #ECE6DF |
| 青黛渐变 | `teal_indigo` | #B6D6DE | #1B2C3E | #3D4D5A | #E1E7EF | #3E4D5C | #E2E8F0 |
| 夜幕渐变 | `nightfall` | #B1D0E3 | #182738 | #3B4B59 | #E0E6EF | #3C4B5A | #E0E6EF |

### 错误态

| 预设 | 稳定 ID | 错误 | 错误上文字 | 错误容器 | 其上文字 |
| --- | --- | --- | --- | --- | --- |
| 暖白 | `warm_white` | #FFC3BB | #690005 | #562D35 | #FFD9D8 |
| 雾青 | `mist_teal` | #FFC9C2 | #690005 | #562D35 | #FFD9D8 |
| 灰蓝 | `slate_blue` | #FFD1CB | #690005 | #562D35 | #FFD9D8 |
| 暖沙 | `warm_sand` | #FFBEB6 | #690005 | #562D35 | #FFD9D8 |
| 深空 | `deep_space` | #FFB4AB | #690005 | #562D35 | #FFD9D8 |
| 晨雾渐变 | `morning_mist` | #FFBFB8 | #690005 | #562D35 | #FFD9D8 |
| 斜阳渐变 | `sunset_angle` | #FFC5BE | #690005 | #562D35 | #FFD9D8 |
| 青黛渐变 | `teal_indigo` | #FFC4BC | #690005 | #562D35 | #FFD9D8 |
| 夜幕渐变 | `nightfall` | #FFBEB6 | #690005 | #562D35 | #FFD9D8 |

## 九套预设的对比度

每个 sRGB 通道先归一化。小于等于 0.04045 时除以 12.92，其余按 2.4 次幂线性化。相对亮度采用 0.2126 R + 0.7152 G + 0.0722 B。对比度为 (较亮值 + 0.05) / (较暗值 + 0.05)。半透明层按 Compose 的 sRGB 合成顺序落到实色背景，再计算对比度。测试直接比较未舍入的数值。下表向下截取两位小数，单位为比一。[WCAG 2.2 相对亮度](https://www.w3.org/TR/WCAG22/#dfn-relative-luminance) · [非文本控件对比度](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) · [完整精度 CSV](../artifacts/dark-comfort-20261126/contrast.csv)

正文、次级、三级和控件边界以最高层容器为底。选中列使用 `onSecondaryContainer / secondaryContainer`，错误列使用 `error / surfaceContainerHighest`。按下列表示 `onPrimary` 叠加 16% 状态层后的按钮文字。输入框和必要轮廓以 3 为底线。装饰分割线仅用于分组。正常文字在其余容器和交互状态下至少 4.5。图标及较大文字也沿用相应角色。带截止含义的日期、文件有效期和状态有文字标记。

| 预设 | 正文 | 次级 | 三级 | 主按钮 | 主按钮按下 | 边界 | 选中 | 错误 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 暖白 | 8.32 | 7.44 | 6.86 | 9.30 | 7.03 | 4.61 | 7.00 | 7.00 |
| 雾青 | 7.99 | 7.38 | 6.81 | 9.27 | 7.01 | 4.54 | 7.00 | 7.03 |
| 灰蓝 | 7.70 | 7.31 | 6.75 | 9.25 | 7.01 | 4.52 | 7.01 | 7.01 |
| 暖沙 | 8.30 | 7.34 | 6.79 | 9.33 | 7.03 | 4.52 | 7.04 | 7.01 |
| 深空 | 9.68 | 7.83 | 7.12 | 9.32 | 7.00 | 4.66 | 7.65 | 7.15 |
| 晨雾渐变 | 8.54 | 7.49 | 6.89 | 9.31 | 7.01 | 4.57 | 7.00 | 7.00 |
| 斜阳渐变 | 7.90 | 7.31 | 6.77 | 9.39 | 7.06 | 4.50 | 7.00 | 7.02 |
| 青黛渐变 | 8.44 | 7.47 | 6.90 | 9.25 | 7.00 | 4.58 | 7.03 | 7.00 |
| 夜幕渐变 | 8.84 | 7.44 | 6.86 | 9.37 | 7.05 | 4.57 | 7.13 | 7.02 |

## 玻璃与照片

| 已有档位 | 底色最低不透明度 | 材质模糊参考 |
| --- | ---: | ---: |
| 关闭 | 100% | 0 dp |
| 弱 | 96% | 12 dp |
| 均衡 | 92% | 24 dp |
| 强 | 88% | 32 dp |

卡片采用所在层级的表面色。填充、表面背后的模糊、装饰边线、照片保护遮罩与前景分别绘制。照片的独立滑块仍为 0–40 dp。照片载入时先按白色像素保护，随后在后台求整图各通道上界，按画面安全亮度下调遮罩。未使用照片时依据实际预设的底色和渐变端点计算。

每个玻璃候选先在纯白底上合成，再叠加最重的 16% 状态层。危险操作和选中状态保留配对文字。按钮同时检查黑底与白底。对比度不足会提高底色不透明度。所需不透明度到达 99% 时使用实色并关闭该层模糊。容器自身不满足前景指标时回退到安全低层表面。API 30 及以下、无硬件加速或系统标记为低内存的设备使用实色卡片。弹窗、底部弹层、吸顶栏和输入框直接使用不透明角色。

以默认雾青、纯白照片为例，低层卡片三档实际不透明度为 96.08%、92.16%、90.98%。最后一档从名义值 88% 上调。最高层容器在三档均回退为实色。白色照片的保护遮罩约为 84.96%。这两个百分比属于不同绘制层。所有预设的实测值可单独查看。[玻璃与照片合成数据](../artifacts/dark-comfort-20261126/glass.csv)

## 模拟器截图与验证

API 37 的原始回归包含 63 对同页截图。覆盖 412 dp 手机、800 dp 平板，各用 100% 与 200% 字号。在每种尺寸和字号下，工作台交叉拍摄白色 / 黑色照片、品牌 / 系统动态色、实色 / 均衡玻璃八种组合。手机还拍摄了弱、强两档和静蓝预设。任务列表、任务详情、编辑弹窗、暖砂工具页、PPT 状态与文件有效期使用相同的离线数据。PPT、文件列表使用生产组件和固定的测试行。其余页面从应用入口或卡片进入。四组原图都包含系统栏，系统时间和工作台问候语取自当次拍摄时刻。

[工作台前后对照](../artifacts/dark-comfort-20261126/workbench-comparison.png) · [手机大字号复核图](../artifacts/dark-comfort-20261126/large-text-review.png) · [平板大字号复核图](../artifacts/dark-comfort-20261126/tablet-large-text-review.png) · [改造前原图](../artifacts/dark-comfort-20261126/before/) · [改造后原图](../artifacts/dark-comfort-20261126/after/) · [全部截图、录像及测量记录 ZIP](../artifacts/dark-comfort-20261126/visual-evidence.zip)

**字号与输入**　412 dp、200% 下，工具副标题原有两行限制会提前省略，现按实际内容换行，两列网格与拖动定位保持一致。任务截止时间、PPT 转换失败与重试、文件剩余时长在两种宽度均有可读文字。窄屏的文件信息会换行，任务筛选和编辑器内的标签可横向滚动。平板侧栏、编辑器操作区与手机底栏均保留。

**表单、弹层与转场**　手机另拍 3 对全屏图，含真实体测 Filled 输入与分段选择、Outlined 新建任务 Dialog、背景设置 BottomSheet。合计 66 对。选中项可见勾选，表单边界可辨，弹层正文区为实色，状态栏图标在暗底可读。对改造后的这段导航录像逐帧抽取，共 376 帧。将每帧按面积缩小后，内容区 RGB 三通道均至少 220 的像素占比最高为 0.00%，超过半屏的近白帧为 0。录像只覆盖所列路径。不同厂商的启动动画、OLED 观感与真机显示仍需人工验收。[控件与弹层前后图](../artifacts/dark-comfort-20261126/controls-comparison.png) · [转场录像](../artifacts/dark-comfort-20261126/navigation-after/navigation.mp4) · [逐帧结果](../artifacts/dark-comfort-20261126/navigation-after/frame-review.json)

**滚动**　在暖砂、均衡档工具页预热一次，重置 `gfxinfo` 后往返滑动四轮。相同虚拟机、相同手势，分别测量基线包和当前包。以下为 Android 帧统计。模拟器 37.3.10 使用 WHPX 和 SwiftShader。软件 GPU、宿主调度和系统动画会影响绝对值。

| 宽度 / 字号 | 帧数（前 → 后） | P50 ms | P90 ms | P95 ms | 卡顿帧占比 |
| --- | --- | --- | --- | --- | --- |
| 412dp-1.0 | 183 → 205 | 57 → 23 | 69 → 30 | 77 → 32 | 73.77% → 47.80% |
| 412dp-2.0 | 174 → 174 | 48 → 26 | 65 → 32 | 69 → 36 | 66.67% → 31.03% |
| 800dp-1.0 | 180 → 289 | 53 → 31 | 61 → 38 | 69 → 40 | 73.33% → 12.80% |
| 800dp-2.0 | 172 → 198 | 53 → 26 | 69 → 34 | 73 → 42 | 68.60% → 56.57% |

四组的 P95 均较基线缩短。该结果仅覆盖上述工具列表。持续滑动、大尺寸自选照片、低端真机的帧耗时和功耗仍待测量。

**自动化**　`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 和 `:app:testDebugUnitTest` 本地构建成功。177 个测试类，共 2030 项，2028 通过，2 跳过，0 失败，0 错误。API 37 按系统实际壁纸资源运行主题测试，另有 4 组基线、4 组当前界面采集通过。测试覆盖状态层 0%、8%、10%、12%、16%，所有玻璃档、白黑照片和有色像素、控件轮廓、弹层、选中与错误角色、按下按钮，以及有无模糊能力。单元测试同时输入构造的低对比动态色作为压力样本。[本地构建日志](../artifacts/dark-comfort-20261126/gradle-debug.log) · [单元测试汇总及跳过项](../artifacts/dark-comfort-20261126/unit-results.txt) · [主题测试原始结果](../artifacts/dark-comfort-20261126/dark-comfort-junit.xml) · [API 37 系统动态色测试](../artifacts/dark-comfort-20261126/platform-api37.log)

**Android 11 降级**　API 30、412 dp、100% 字号另采集 18 张原图。系统兼容测试断言关闭及各启用档的最终填充完全不透明、模糊半径为 0。纯白照片上，四档工作台正文区域（824 × 1830 原图中 x=40–779、y=400–1399）逐像素相同。在黑照片上，打开与关闭动态色同样得到品牌配色。实色降级分支另有单元测试。带系统低内存标记或关闭硬件加速的实体设备还需验证。[降级对照图](../artifacts/dark-comfort-20261126/api30/fallback.png) · [API 30 原图](../artifacts/dark-comfort-20261126/api30/412dp-1.0/) · [平台测试](../artifacts/dark-comfort-20261126/api30/platform-api30.log)

**复查方式**　在独立离线模拟器中开启飞行模式，安装所需的 debug 主包与本仓测试包。对基线和改造包分别运行 `python scripts/dark_appearance_capture.py --serial <emulator-serial> --output <directory>`，各自导出四组全屏图和滚动记录。最终版采集器另在 API 37 的 800 dp、200% 字号上重新导出并校验了 15 张原图。主题单元测试从 `DarkComfortTest` 的标准输出导出实际解析值及未舍入的比值。

## 修改文件

下列路径以 Android 仓库为起点。截图和测量原件放在本地 `artifacts/dark-comfort-20261126/`。

| 文件 | 修改原因 |
| --- | --- |
| [app/build.gradle.kts](../app/build.gradle.kts) | 声明运行设备截图与系统动态色验证所需的测试运行器和依赖。 |
| [androidTest/DarkAppearanceCaptureTest.kt](../app/src/androidTest/kotlin/com/furina/classtools/DarkAppearanceCaptureTest.kt) | 在真实窗口采集可重复的页面状态及滚动统计。 |
| [androidTest/PlatformDarkSchemeTest.kt](../app/src/androidTest/kotlin/com/furina/classtools/PlatformDarkSchemeTest.kt) | 从 Android 系统资源取样，测试动态色路径和旧系统回退。 |
| [data/background/BackgroundStore.kt](../app/src/main/kotlin/com/furina/classtools/data/background/BackgroundStore.kt) | 让主题入口复用现有预设及照片表达的读取器。 |
| [ui/ClassToolsThemeHost.kt](../app/src/main/kotlin/com/furina/classtools/ui/ClassToolsThemeHost.kt) | 订阅本地背景，向主窗口和工具窗口传入同一预设。 |
| [ui/components/CtLayout.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/CtLayout.kt) | 统一按钮、Outlined 输入、状态标签的语义颜色和边界。 |
| [ui/components/RosterPickerSheet.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/RosterPickerSheet.kt) | 名单弹层的搜索框使用共享实色输入配色。 |
| [ui/components/background/CtBackgroundLayer.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/background/CtBackgroundLayer.kt) | 统一深色窗口底、渐变与照片保护，提供独立的模糊采样源。 |
| [ui/components/background/CtGlassSurface.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/background/CtGlassSurface.kt) | 落实容器层级、独立填充和边框，向可点击卡片传递交互源。 |
| [ui/components/background/DarkGlassMaterial.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/background/DarkGlassMaterial.kt) | 在合成结果上求安全的不透明度，管理模糊、照片保护以及实色降级。 |
| [ui/components/dock/CtFloatingDock.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/dock/CtFloatingDock.kt) | 底栏采用受保护材质、清晰选中图标及文字。 |
| [ui/components/dock/CtNavigationRail.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/dock/CtNavigationRail.kt) | 平板侧栏与手机使用相同的深色选中角色。 |
| [ui/components/expressive/CtSegmentedButtons.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/expressive/CtSegmentedButtons.kt) | 配对分段选择前景、实色填充、轮廓和勾选标记。 |
| [ui/components/light/CtLight.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/light/CtLight.kt) | 关键场景按角色使用实色，装饰材质读取共享底材。 |
| [ui/components/light/CtLightOverlays.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/light/CtLightOverlays.kt) | 深色对话框和底部弹层固定使用不透明容器。 |
| [ui/components/light/CtLightScenarios.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/light/CtLightScenarios.kt) | 吸顶栏使用主题栏色，深色根页面共用一次背景绘制。 |
| [ui/components/tool/CtToolComponents.kt](../app/src/main/kotlin/com/furina/classtools/ui/components/tool/CtToolComponents.kt) | 共享工具卡、Filled 输入和按钮分别管理底色与状态层。 |
| [ui/screens/all/AllToolCard.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/all/AllToolCard.kt) | 大字号副标题可换行，按压使用 M3 状态层并保留缩放。 |
| [ui/screens/all/AllToolsScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/all/AllToolsScreen.kt) | 大字号网格按实际文字测量统一行高，同步拖动预览与命中区。 |
| [ui/screens/cloudspace/CloudSpaceScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/cloudspace/CloudSpaceScreen.kt) | 文件行复用共享卡片，有效期及传输状态提升至正文层级。 |
| [ui/screens/home/HomeScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/home/HomeScreen.kt) | 提升工作台的关键时间和清单元信息层级。 |
| [ui/screens/home/HomeV5Components.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/home/HomeV5Components.kt) | Hero 日期使用正文语义。 |
| [ui/screens/homework/HomeworkScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/homework/HomeworkScreen.kt) | 为截止信息增加可读前缀并提升文字层级。 |
| [ui/screens/notices/NoticeAdminDialog.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/notices/NoticeAdminDialog.kt) | 管理弹窗输入使用共享深色边界。 |
| [ui/screens/notices/NoticeList.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/notices/NoticeList.kt) | 通知日期采用正文语义。 |
| [ui/screens/notices/NoticeMotion.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/notices/NoticeMotion.kt) | 深色按压与展开状态层置于文字后。 |
| [ui/screens/notices/SignupCountdownControl.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/notices/SignupCountdownControl.kt) | 倒计时编辑和管理弹窗采用同一输入角色。 |
| [ui/screens/ppt/PptDownloadDetailScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/ppt/PptDownloadDetailScreen.kt) | PPT 详情及进度/状态沿用正文语义。 |
| [ui/screens/ppt/PptListScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/ppt/PptListScreen.kt) | 提升 PPT 列表关键信息的文字层级。 |
| [ui/screens/profile/BackgroundSection.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/profile/BackgroundSection.kt) | 背景预设缩略图跟随深浅模式。 |
| [ui/screens/tasks/ClassHomeworkOverview.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/tasks/ClassHomeworkOverview.kt) | 班级作业高层卡与截止/过期文字采用配对角色。 |
| [ui/screens/tasks/TaskActions.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/tasks/TaskActions.kt) | 分组弹窗采用不透明的编辑表面。 |
| [ui/screens/tasks/TaskAiPanel.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/tasks/TaskAiPanel.kt) | AI 输入使用同一套可辨识边界。 |
| [ui/screens/tasks/TaskDetail.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/tasks/TaskDetail.kt) | 任务详情与入口复用同一共享卡片。 |
| [ui/screens/tasks/TaskScreen.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/tasks/TaskScreen.kt) | 搜索、列表行及选中态使用对应的实色输入或卡片材质。 |
| [ui/theme/Color.kt](../app/src/main/kotlin/com/furina/classtools/ui/theme/Color.kt) | 将默认品牌深色 Material 基础色对齐雾青基准。 |
| [ui/theme/CtDesign.kt](../app/src/main/kotlin/com/furina/classtools/ui/theme/CtDesign.kt) | 归集三种基础深色表面与品牌种子，保留 CtColor 和 Harmony 适配入口。 |
| [ui/theme/DarkComfort.kt](../app/src/main/kotlin/com/furina/classtools/ui/theme/DarkComfort.kt) | 按预设或系统色生成 Material / CtColor，校正正文、状态、轮廓和配对前景。 |
| [ui/theme/ImmersiveLight.kt](../app/src/main/kotlin/com/furina/classtools/ui/theme/ImmersiveLight.kt) | 标明现有 sRGB 相对亮度公式的 WCAG 2.2 依据。 |
| [ui/theme/Theme.kt](../app/src/main/kotlin/com/furina/classtools/ui/theme/Theme.kt) | 为应用、小组件、预览提供同一套解析器，读取系统低内存及加速能力。 |
| [app/src/main/res/values-night/colors.xml](../app/src/main/res/values-night/colors.xml) | 使夜间启动窗口的实色底与默认背景一致。 |
| [test/ui/components/background/BackdropContrastTest.kt](../app/src/test/kotlin/com/furina/classtools/ui/components/background/BackdropContrastTest.kt) | 从实际预设和遮罩验证照片及预设背景。 |
| [test/ui/components/background/CtGlassTokensTest.kt](../app/src/test/kotlin/com/furina/classtools/ui/components/background/CtGlassTokensTest.kt) | 用当前解析结果验证历史材质测试中的白底保护。 |
| [test/ui/theme/CtDesignTokensTest.kt](../app/src/test/kotlin/com/furina/classtools/ui/theme/CtDesignTokensTest.kt) | 更新要求变更的颜色常量断言。 |
| [test/ui/theme/DarkComfortTest.kt](../app/src/test/kotlin/com/furina/classtools/ui/theme/DarkComfortTest.kt) | 覆盖九预设、系统输入、玻璃和状态组合，导出原始数值。 |
| [design/DARK_COMFORT_2026-11-26.md](../design/DARK_COMFORT_2026-11-26.md) | 集中记录实际令牌、测试、截图、修改范围及其他端映射。 |
| [design/README.md](../design/README.md) | 登记本次验收入口。 |
| [design/tokens.json](../design/tokens.json) | 同步默认预设 48 个 Material 角色、示例材质及对比度。 |
| [docs/DESIGN_SYSTEM.md](../docs/DESIGN_SYSTEM.md) | 更新日常维护的主题源及深色材质约定。 |
| [scripts/dark_appearance_capture.py](../scripts/dark_appearance_capture.py) | 驱动手机和平板矩阵并还原测试设备显示设置。 |

## 其他端如何接入

| 客户端 | 已有入口 | 建议映射与验证 |
| --- | --- | --- |
| Web | `web/tools/index.html` 的 `data-appearance` 及颜色变量 | 优先映射 `--bg`、`--card`、`--ink`、`--ink2`、`--ink3` 和主按钮。保留首帧主题设置。用浏览器实际合成的白图卡片、焦点和按下状态核验同一组阈值。 |
| 鸿蒙 | `harmony-class-tools/entry/src/main/ets/ui/theme/Tokens.ets`、`Immersive.ets` | 沿用已有三个品牌种子与系统明暗选择。将容器高低层、正文、选中和错误映射至 ArkUI 资源，再按设备模糊能力决定是否使用实色，验证弹层窗口与底栏。 |
| 微信小程序 | `mp-class-tools/app.wxss`、`theme.json`、`utils/appearance.js` | 优先套用 `--md-surface-*`、`--md-on-*` 和原生窗口栏位。优先用实色确保各 WebView 表现一致，实际设备跑白底、200% 字号和选中回归。 |

CSV 可以作为各端本地测试的数据来源。预设 ID、跨端同步字段和用户已有外观偏好继续沿用现有契约。本次交付范围为 Android 本地工作区。
