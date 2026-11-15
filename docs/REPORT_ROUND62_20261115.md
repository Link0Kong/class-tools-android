# Round 62 报告 · v4.55.0（用户 12 项反馈修复 + 云空间同步）

> 用户 2026-11-15 晚反馈 12 项问题。本轮用 5 个并行子任务修复，Lead 集成、构建、E2E 与交付。

## 逐项结果

1. **活动提示「未找到对应编号的学校信息」** — 根因：登录 WebView 打开无 hash 的 `rcm.fjtcm.edu.cn/wechat/`，非微信环境把 `localStorage.schoolNo` 写成 "undefined"，H5 请求 `third_party?schoolNo=undefined` → 500。修复：改用 `#/fjtcm/login` 入口、schoolNo 兜底、多结构 lgInfo 解析（顶层 id+studentFile.classId / CAS userId+classId / 嵌套 user）、错误带服务器原文。
2. **工具拖动异常** — 实测修复 7 项：网格缺 key 导致重排取消拖动、滚动后坐标重复计算、长按阈值 500→350ms、拖动时页头增高、边缘自动滚动只触发一次、点击抑制不足、重启顺序丢失。副标 2 行、卡高 96dp。
3. **状态栏不隐藏** — 全局沉浸式隐藏 statusBars（下滑临时显示），「我的 → 外观」可关闭；刘海/安全区兜底，顶栏不遮挡。
4. **地图打不开** — H5 正常但 WebView canvas 白屏；修复：显式 viewport/硬件层、加载后 canvas 健康检查+强制重绘、失败自动软件层/无缓存重试、错误条「重试/浏览器打开」、Android 定位 JS 桥。
5. **深浅色/跟随系统** — 「我的 → 外观」新增 跟随系统 / 浅色 / 深色（本机持久化、实时生效、冷启动保持），保留壁纸取色。
6. **教务非校园网先 WebVPN** — TCP 探测 210.34.74.77 不通时自动打开 `https://webvpn.fjtcm.edu.cn:4433/login`，用本机 Keystore 密码自动填充、二次验证/失败回退手动；门户自动点「本科教务」；提供「直接打开教务 / 走 WebVPN」手动入口。
7. **学信网自动登录 + 卡片** — 点卡片默认进卡片页（10 官方工具卡始终可见），后台自动恢复登录并提取数据卡；未登录显示「去登录」，登录成功自动回卡片刷新，不再强制全屏登录页。
8. **电费对齐网页** — 同期比较移到「区间用电」tile 内独立一行完整显示（自然周期同期、14d/70d 补拉、35% 覆盖，与网页同口径）；同步「充值后趋势可读」：折算剩余曲线 + 充值竖直虚线 + 「已折算充值」+ y 轴折算范围 + 图例充值度数；通用异常扣减剔除。
9. **工具描述被折叠** — 副标最多 2 行（12sp/16sp），卡高 96dp，常见描述完整显示；拖动几何同步。
10. **云空间同步 + 未同步功能审计** — 新增「文件传输 · 临时云空间」：同班级身份跨设备、任意大小 8MiB 分片上传、列表/下载/删除/清空、72h 保留，对接 ECS drop v2.2；工具 18 项、使用说明 25 卡。审计：小程序「统计（管理员）」「大屏互联」仍为管理员/网页专用，未提供（其余工具已对齐）。
11. **深色绿色 hero 观感** — 工作台/我的/使用说明顶部大面积绿色渐变在深色下改中性 tonal 深色，品牌绿只小面积点缀；浅色不变。
12. **通知实时全部加载** — 首屏成功后自动后台连续加载全部剩余页，显示「正在加载全部…（剩余 N 条）」；失败自动重试一次并保留已加载；无需用户点按钮。

## 验证

- 全量单测：**1412 / 0 failures / 0 errors**（109 suites；新增/更新：ActivityWebSession 15、ChsiNavigationLogic 10、JwWebVpn 11、AllToolsDragGeometry 12、PowerStats/Compare/Repo 59、NoticesAutoLoader 6、CloudSpace 59、AppearanceMode 9 等）。
- 快速 E2E：**45 步 PASS / 0 WARN / 0 FAIL（1057.9s）**，导航探针 **10/10**，logcat 0 FATAL/ANR；深色组/浅色恢复通过。
- Release：**3,366,813 B** / SHA-256 `7A4A6A382D69E45C3680FA92E655C1AE17F16673D70F4E60D69374AF41AD9B9E`（vc73 / 4.55.0）；冷启动 477/420ms，稳定 419/463/369ms；logcat 0 FATAL/ANR。
- 手工冒烟截图：`artifacts/v455-all-tools.png`（描述两行、18 项）、`v455-all-tools-bottom.png`、`v455-appearance.png`（外观三选一+隐藏状态栏）、`v455-dark-home.png`（深色中性 hero）、`v455-cloudspace.png`（临时云空间，预览态缺 token 属预期）、`v455-release-identity.png`（状态栏已隐藏）。
- 子任务证据：`artifacts/task55/`（活动/地图/WebVPN/学信网根因与验证）、`artifacts/power_notices_20261115/`、`artifacts/theme-preview/`、`artifacts/e2e-drag58/`。

## 已知边界（如实）

- 活动学校账号、学信网真实账号、WebVPN 真实登录均需用户本人账号/短信/TOTP 验证（红线：测试只用测试身份）；本轮用匿名复现/页面证据与合成单测覆盖，未用真实账号。
- 电费 14d/70d 同期与充值折算需要云端历史/真实充值数据，现网历史约 6 天，上周/上月同期待数据积累后自动出现；未做真实云端电费截图。
- 通知自动加载使用 FakeTransport 单测（真机开发预览无云端 token）；真实账号下会自动加载到全部完成。
- 云空间未用真实 token 做端到端上传/下载/删除（建议测试身份手工冒烟后 POST /close 清理）；下载落 App 私有 Downloads，不申请公共存储权限。
- 小程序「统计（管理员）」「大屏互联」未移植到 App（管理员/网页专用），其余工具已对齐。
- 仅模拟器验证，ColorOS 真机状态栏/刘海/WebVPN 行为未逐项真机验证。
