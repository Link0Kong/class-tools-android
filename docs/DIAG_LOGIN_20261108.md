# 诊断 v2 + 课表登录（2026-11-08）

> 用户需求（2026-11-08）：诊断页加入设备诊断、异常自动上传、AI 扫描原因、云端易修复问题由本机在线时自动修复并展示流程、设备端给建议、诊断全员可见；课表新增登录入口（用自己的教务账号获取个人课表）。三端同做，本文记 Android 侧。云契约见 `C:\Users\Ko_Li\.openclaw\workspace\_diag_task\CONTRACT.md`。

## A. 诊断 v2（新工具页「诊断」，全部已登录成员可见）
- `data/diag/`：`DiagSanitizer`（IP / `C:\`路径 / URL+查询串 / token/pwd/cookie/Authorization/40+ token 形状；300 字截断）、`DiagPayload`（契约字段白名单 + 稳定 sha256 指纹，sid 只在行顶层）、`DiagParser`（service_health 5 组/15 分钟巡检降级、diag_analysis suggestion/flow、repair_tasks、diag_agent_info、client_diag）、`DiagEvaluator`（内存/存储/网络/网关/时钟/本地数据阈值）、`ClientDiagRateLimiter`（同指纹 10 分钟 1 条、每批 ≤3、失败回滚）、`CrashDiagRecorder` + `installCrashHandler`（异常类名+脱敏 message/thread/device，不含堆栈；启动/登录后补报）。
- `ui/screens/diag/DiagScreen.kt`：本机 agent 在线、设备卡（通过/注意/异常 + 重新检测 + 上报）、AI 原因/建议/fixable/action/status + flow 时间线、修复任务、服务健康（显示脱敏）、我的上报；15s 可见轮询。
- `ApiResponse.headers` + `ApiClient.getTimed()`：网关延迟计时 + 用 Date 头算时钟偏差。

## B. 课表登录（个人 ttLocal）
- `data/timetable/{TimetableLoginModels, TimetableLoginRepository, TtLocal, TimetablePersonalStore}.kt`：`TtLoginParser`、`TtLoginPolling`（1.5s / 120s，终态即停、网络类可重试）、`ttLocal` codec、写入 userdata v6 全量 sections 的 personal store。
- Timetable 页：登录图标入口 + 弹窗（学号默认 session.sid 可改、`PasswordVisualTransformation`、提交后立即清空、错误码中文提示）+「个人课表」banner / 清除回退；仓库优先级 **个人 ttLocal > 公共 site_assets > 缓存**。

## C. 验证（真实数字）
- `gradlew test --no-build-cache`：**63 suites / 779 tests / 0 failures / 0 errors**（新增 93 个用例：脱敏 9 / payload 9 / parser 12 / repository 10 / 设备检查 11 / 上报限频 10 / 登录 14 / ttLocal 12 / 计时 6）。
- `gradlew assembleDebug --no-build-cache` 成功；`gradlew assembleRelease` 成功（签名）`app/build/outputs/apk/release/app-release.apk` **2,577,485 B** · sha256 `59BEE20FEA099B2F0CE0A4F0A8A51C2BE2226442FEF4940A56A468785EFA6BF9`。
- 截图：`artifacts/emulator_diag.png`、`artifacts/emulator_tt_login_dialog.png`；preview：`artifacts/previews_diag_login/.../{DiagLightPreview_0,DiagDarkPreview_0,TimetableLoginPreview_0,TimetableLoginErrorPreview_0}.png`；重渲染脚本 `scripts/render_diag_login_previews.ps1`。模拟器实测诊断页真实加载（agent 在线/11 通过·1 注意/网关 46ms），logcat 无 FATAL/ANR。

## D. 已知限制
1. 登录弹窗学号可编辑但只允许本人（云端 enqueue 的 sid 来自 token，不支持代同学登录；改他人学号会提示）。
2. 崩溃上报不含堆栈（防路径/URL 泄漏）；崩溃行落本机队列，登录后/进诊断页限频补报。
3. 时钟偏差依赖网关 Date 头；缺失时显示「不可校准」INFO 不误报。
4. 诊断页 AI/修复/服务健康需真实班级 token；Debug 预览会话仅设备检查与本机 agent 信息（公开）。
5. 15s 轮询仅页面可见（ON_RESUME）；未做完整 E2E（按任务要求）。

## 诊断 v2.1 增量（2026-11-09，用户要求：AI 下挂异常项/折叠展开/10s 进展/设备信息最小化）

- **AI 下挂**：`DiagScreen` 服务健康异常行/设备检查行下方直接渲染 AI 折叠行（按 `diag_analysis.related.health_ids` / `related.checks`，后者由 `client_diag.detail.check` 推导）；删除独立 AI/修复卡；无关联进「其它诊断」（默认收起只显示条数）；修复记录在展开态按 `related.repair_id` 显示 action/status/result/时间。
- **折叠/展开**：折叠一行（severity 点+标题≤22 字+「可自动修复/建议」徽标+状态胶囊 待处理/修复中/已修复/修复失败+`›`）；点击展开 cause/建议/flow 时间线/关联修复；异常行无 AI：代理在线→「AI 分析排队中…」，离线→「本机离线，已留存待开机修复」；顶部摘要 `可自动修复 N · 已修复 M`。
- **进展 10s**：诊断轮询 15s→10s（仅 ON_RESUME 可见时），进入/恢复立即拉；running/fixed 与 flow `run` 实时渲染。
- **设备信息最小化（§B）**：`DiagPayload`/`DeviceDiagnostics` 的 `client_diag.device` 只留 platform/osVersion/appVer/webview/screen{w,h,dpr}/memoryMB/storagePct/network/clockSkewMs；`detail` 只留 check/latency_ms/storage_pct/clock_skew_ms/err_count/code；设备检查展示去掉品牌/型号/语言。
- **测试与构建（本机实跑）**：`gradlew test --no-build-cache` **66 suites / 817 tests / 0 failures**（新增 38 用例：分组映射/折叠文案/胶囊/白名单 payload/related.checks 解析/10s 轮询/旧数据过滤等）；`assembleDebug` 24,188,528 B、`assembleRelease` **2,593,869 B**（新构建）；preview 截图 `artifacts/previews_diag_v21/.../DiagV21FoldedLightPreview_0.png`、`DiagV21ExpandedLightPreview_0.png`、`DiagV21FoldedDarkPreview_0.png`。
- **已知限制**：未做完整 E2E（模拟器真机联调按任务要求仅 preview）；诊断页依赖真实班级 token；其它字段/历史数据展示端过滤。
