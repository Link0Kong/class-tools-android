# 金标联盟 SharedView Kit 一镜到底（Android）

> 需求：按金标联盟（ITGSA）示例 [SharedViewDemo](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo)
> 配置荣耀 / OPPO / vivo / 小米等「金标联盟手机」的应用内一镜到底转场。
>
> 版本：`1.0.0-beta19`（vc102）起。

## 1. 能力与边界

| 项 | 值 |
|---|---|
| Maven 坐标 | `gsai.sdk:sharedview-kit:1.0.0.5`（钉版本，Nexus 当前最新 1.0.0.5） |
| 仓库 | `http://nexus.itgsa.com:5566/repository/sharedViewKit/`（内测期 HTTP，仅放行 `gsai.sdk`） |
| 运行时判断 | `ViewAnimationManager.isViewAnimationSupported()`：`>0` 支持并返回协议版本；`0` 不支持 |
| 目标设备 | 荣耀、OPPO、vivo、小米等已内置 SharedView 能力的金标联盟机型/ROM |
| 最低 SDK | Kit 要求 26；本 App `minSdk 26`，无需调整 |
| 混淆 | AAR 自带 consumer proguard 规则（public API + ROM 回调 adapter），App 不另加 keep |

**不按品牌/系统版本硬编码**：是否启用只看运行时 `isViewAnimationSupported()`。同一份 APK 在支持机型上走
系统级共享元素，在 AOSP/模拟器/旧 ROM 上自动回退。

## 2. 为什么多了一个 ToolActivity

Kit 的公开 API 是 `ActivityOptions`，只能做 **Activity 之间** 的系统级共享元素；本 App 其余页面都在
`MainActivity` 的单 Activity Compose `NavHost` 里。因此只把最匹配示例的「工具卡 → 工具页」提升为
`ToolActivity`：

```
MainActivity（工作台/全部工具，卡片仍留在原位）
    │ 点击工具卡
    ├─ Kit 支持：Bitmap 源 + ActivityOptions → ToolActivity（首屏直接 tool/{id}）
    │      返回：ToolActivity.overrideViewAnimationTransition(原卡片 bounds) → finish()
    │            Kit 把页面收回原卡片 → 露出 MainActivity
    └─ Kit 不支持 / 半遮卡片 / 截图失败：原 Compose 容器变换（ToolTransitionOverlay）
```

- `ToolActivity` **不导出**，只接受 `MainActivity` 的显式 Intent；Session 通过 Intent 传入，
  不落盘、不写日志。
- 目标 Activity 的首屏就是 `tool/{id}`（`MainNavHost.startToolId`），不会先渲染根页再跳转。
- 工具页内部再 push 的页面（如 课表 → 地图）仍在 ToolActivity 内，使用统一 `CtPageTransitions`；
  返回逐层 pop，最后一层退出 Activity 并回卡片。
- 根页/通知/deeplink 仍走 `MainActivity`，不受影响。

## 3. 参数口径（照官方 README）

| 参数 | 做法 |
|---|---|
| 源 | **Bitmap 源**：把 Compose 工具卡区域画进 Bitmap，bounds + 半径显式传入。View 源会把整棵 Compose 根视图当源，不可用 |
| 半径 | 卡片真实 dp × density = **px**；全部工具卡 24dp，首页快捷卡 14dp。传 dp 会被当 px 消费，OPEN/CLOSE 圆角跳闪 |
| color | **不传**。App 有动态壁纸/玻璃背景，没有稳定底色；传错会在返回收尾出现色圈 |
| bounds 坐标系 | 读 `getViewAnimationExtras()[EXTRA_BOUNDS]` 第二段：`WINDOW` 用 `getLocationInWindow`，其余用 `getLocationOnScreen`。不按机型硬编码 |
| 半遮卡片 | 不启动 Kit（Bitmap 源无法表达裁切），回退 Compose 覆盖层；避免落点/裁切错位 |
| fallbackAnimType | 仅当设备声明支持且支持集合含 `VIEW_ANIM_EXPLODE_OUT` 时写入，作为目标丢失/截图异常的兜底 |
| 返回 override | `finish()` 前调用；源 Activity 已销毁或屏幕尺寸变化（旋屏/分屏）时不 override，走系统默认返回，绝不硬套旧坐标 |

## 4. 代码位置

| 文件 | 职责 |
|---|---|
| `ui/motion/SharedViewMotion.kt` | 能力检测、Bitmap 截图、ActivityOptions、返回 override、降级 |
| `ui/motion/SharedViewBounds.kt` | 坐标系解析、px bounds、可见性/复用判定（JVM 单测） |
| `ToolActivity.kt` | 独立工具宿主、Intent 参数、`MainNavHost` 接线 |
| `ui/navigation/ToolTransition.kt` | 卡片锚点 + 现有 Compose 容器变换（降级路径） |
| `ui/navigation/MainNavHost.kt` | `startToolId` / `standaloneTool` / 独立返回栈 |
| `ui/ClassToolsThemeHost.kt` | MainActivity 与 ToolActivity 共用主题/背景/系统栏，避免两端视觉跳变 |
| `settings.gradle.kts`、`gradle/libs.versions.toml` | ITGSA Nexus 仓库与依赖版本 |

## 5. 测试与验收

### 已自动验证（本机，AOSP API 37 模拟器）

- `:app:testDebugUnitTest`：**1802 用例 / 0 失败 / 2 skipped**（新增 7 个 bounds 用例）。
- `:app:compileDebugKotlin`、`:app:assembleDebug` 成功。
- AOSP 模拟器 `isViewAnimationSupported() == 0`：
  - 卡片点击 → `SharedViewMotion: SharedView capability protocol=0`；
  - 页面仍通过 Compose 容器变换打开/返回（截图 `shots/ct_open_220ms.png`、`ct_back_done.png`）；
  - 独立 ToolActivity 通过 `adb am start` 验证：首屏直接工具页、课表→地图可 push、返回逐层 pop、
    最后一层回到 MainActivity（截图 `shots/toolactivity_*.png`）。
- **未验证**：真实金标联盟机型上的系统动画（本机没有荣耀/OPPO/vivo/小米 ROM，模拟器不支持 Kit）。

### 真机验收步骤（需 1 台支持机型）

1. 安装 `1.0.0-beta19`，登录测试身份（学号 9999 段 / 3260799999）。
2. 工作台 → 全部工具 → 点任一工具卡：
   - 期望：卡片从点击位置连续长成工具页，工具页顶栏/背景直接出现；无白屏/闪屏/两重动画。
3. 工具页返回：页面收回原卡片，卡片下方列表位置不变；连续快速返回不残留黑色/白色遮罩。
4. 工具内再进一层（课表 → 地图）→ 返回：先回课表，再返回才回卡片。
5. 旋转屏幕后再返回：不崩、不飞向错误位置（尺寸变化时走系统默认返回）。
6. 半遮卡片（列表滚动使卡片只露一半）点击：应回退 Compose 覆盖层，不出现裁切错位。
7. 与「移除动画」系统设置/省电模式组合：无动画时直接打开/返回，不卡死。
8. 抓 `adb logcat -s SharedViewMotion`：支持机型应出现 `capability protocol=<n>` 与
   `animation start/end mode=...`；不支持机型只有 `protocol=0`。

## 6. 已知限制

- **单 Activity 架构**：只有工具卡 → 工具页这一条路径接入了 Kit；投票（独立 `Routes.VOTE`）仍走
  Compose 覆盖层，因为无法映射到 `tool/{id}` 目标。
- **目标 Activity 启动耗时**：Compose 首帧 + Application 初始化在低端机上可能 >300ms；若真机观察到
  「先闪启动图再展开」，优先检查目标页是否被数据初始化阻塞，而不是加大动画时长。
- **Kit 版本**：当前钉 `1.0.0.5`；升级时改 `sharedviewKit` 版本并跑
  `.\gradlew.bat :app:assembleDebug --refresh-dependencies`，再回归真机动画。
- **HTTP 仓库**：内测期 Nexus 仅 HTTP，`settings.gradle.kts` 只对 `gsai.sdk` 开
  `isAllowInsecureProtocol = true`；正式 HTTPS 后应删除该开关。
- **证据边界**：模拟器只能证明降级路径与独立宿主正确，不能证明系统共享元素动画本身；真机验收前
  不要把「模拟器通过」当作动画已完成。

## 7. 官方资料

- 示例仓库：<https://gitee.com/itgsa_sharedviewkit/SharedViewDemo>
- 能力说明：<https://www.itgsa.com/home/union/openness>
- 发布报道（四厂商联合推动）：<https://news.17173.com/content/11012026/073058815.shtml>、
  <https://site.qudong.com/article/528151.html>
