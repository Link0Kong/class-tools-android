# Round 61 报告 · v4.54.0（底部导航黑条修复 + 并行功能整合）

> 用户要求：① 优化底部工具栏的黑色条问题；② 把其他对话未按规范整合的功能整合一下；③ 安装包放桌面。

## 1. 底部导航黑条

### 根因

- targetSdk 37 / Android 15+ 强制 edge-to-edge；3 键导航模式下系统会给导航栏加**对比度蒙层**（半透明黑色条/圆角底），叠在 App 底部工具栏下方；部分 ColorOS 机器上表现为明显黑条。
- 主题里 `navigationBarColor` 虽已是 transparent，但运行时 `isNavigationBarContrastEnforced` 未关闭，且底部安全区没有显式背景兜底。

### 修复

1. `MainActivity.enableEdgeToEdge` 显式传 `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` 控制图标明暗、颜色恒透明；
2. `window.isNavigationBarContrastEnforced = false`（API 29+）+ `window.navigationBarColor = TRANSPARENT`；
3. `RootTabs` 底部增加与 `colorScheme.background` 一致的导航栏安全区背景（`windowInsetsBottomHeight(WindowInsets.navigationBars)`），即使页面内容不足也不会露出黑色窗口底色。
4. 验证：模拟器切 3 键导航，修复前底部导航区出现深色圆角蒙层；修复后为页面底色 + 深色导航图标，无黑条。截图 `artifacts/v454-dock-3button.png`（修复后）、`artifacts/dock-main-3button.png`（修复前对照）。

## 2. 并行功能整合（提交 + 构建 + 验证）

本轮把工作区里多轮并行对话完成但未提交/未交付的功能统一整合进 v4.54.0：

| 功能 | 来源 | 状态 |
|---|---|---|
| 活动（第二课堂） | 并行会话 | 工具卡 + 独立页面：活动列表/详情/各角色名额/报名/取消；学校账号 WebView 手动登录，token 仅本机 DataStore；未登录引导登录 |
| 地图（校园 2.5D） | 并行会话 | 工具卡 + 内置 H5 WebView（ECS `121.196.47.2/map/campus_final_25d.html`，约 231KB，实测 HTTP 200）；定位权限、楼栋/楼层/道路、课表页跳转；cleartext 只放行该 IP |
| 学信网 | 并行会话 v4.53 | 数据卡 + 10 官方入口 + 手动登录；本轮修复「未登录误判已登录」：档案域营销首页（不是登录页 URL）被误判 → 增加页面内容强信号判定，未登录自动跳官方账号登录页；新增 3 项单测 |
| OPPO PUSH | 并行会话 v4.51 | 官方 SDK AAR + Manifest 回调 + `push_tokens` upsert + 设置页状态行；无密钥时完全跳过（当前无真实密钥，未做真实推送验证） |
| 使用说明 | 本轮补齐 | 新增「活动报名」「校园地图」卡（22→24 张，VERSION 2026-11-15.2） |

工具目录 17 项（全部工具页一屏可滚动查看）。活动 / 地图 / 学信网均已接入 `ToolCatalog` + `MainNavHost` 路由；图/页冒烟截图：
`artifacts/v454-activity.png`、`artifacts/v454-map.png`、`artifacts/v454-chsi-login.png`、`artifacts/v454-all-tools.png`。

## 3. 验证

- 单测：**1279 / 0 failures / 0 errors**（100 suites；含新增学信网登录态 3 项）。
- 快速 E2E：**44 步 PASS / 0 WARN / 0 FAIL（1048.1s）**；导航探针 **10/10**；logcat 0 FATAL/ANR；深色组/浅色恢复通过。
- Release v4.54.0：**3,301,273 B** / SHA-256 `B02FDDCE3EE71EDA901059519014A42F5A7C865877A1D39581EF351C4BCCD858`（vc72）；`pm clear` 后 599/404ms，稳定 **395 / 417 / 416ms**；versionName=4.54.0；logcat 0 FATAL/ANR。
- 手工冒烟：3 键/手势导航底部工具栏、活动页、地图页（定位权限弹窗）、学信网未登录→官方登录页、全部工具 17 项均正常；logcat 0 FATAL/ANR。

## 4. 已知边界（如实）

- 活动的学校账号登录、学信网真实账号、OPPO 真实推送到达/点击均**未用真实账号验证**（红线：测试只用测试身份；学信网/学校账号无测试凭据）。
- 地图 H5 依赖 ECS IP 调试入口（备案前的临时方案），域名备案后应改 https 同源地址。
- OPPO 推送需要真实 AppKey/AppSecret（`app/oppo.properties`，不入库）；未配置时设置页显示「未配置」，不影响其它功能。
- 未合并 `feat/background-notify` 旧分支（v3.7.0 时代的后台通知改造，与当前 v4.54 架构差异大且已被现有提醒链路取代），如需单独迁移另行评估。
