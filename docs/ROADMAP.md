# 路线图与进度

> 每个里程碑交付「可运行 + 可验证」的增量；APK 产物放 `artifacts/`，验收报告放 `docs/`。
> 状态：✅ 完成 / 🟡 进行中 / ⬜ 未开始（更新于 2026-11-07 23:4x）

## M0 · 工程与工具链 ✅

- ✅ 侦察：Android 17 = API 37、Material 3 Expressive、Compose-first、自适应强制等事实（design/README 引来源）
- ✅ 工具链：JDK 21/17、Android SDK（platform 37.0 / build-tools 37.0.0 / platform-tools 37.0.2）、Gradle 9.9.0、版本矩阵三组合实测（docs/TOOLCHAIN.md 编写中）
- ✅ 工程骨架：AGP 9.5.1 built-in Kotlin + Kotlin 插件 2.4.21 + Compose BOM 2026.09.00 + material3 1.4.0
- ✅ 首个 assembleDebug 成功（APK 19.7MB，`artifacts/classtools-1.0.0-debug.apk`）
- ✅ `android preview render` 无设备截图流水线打通（8 张 PNG，`artifacts/previews/`）
- ✅ 包名修正：`fun.furina.classtools` → `com.furina.classtools`（Kotlin 关键字限制）

## M1 · 设计系统与首页 🟡

- 🟡 设计规格（design/ 5 份文档 + tokens.json）已完成；主题 token 落地进行中
- 🟡 自建 expressive 组件 8 个（SegmentedButtons / LoadingIndicator / FloatingActionMenu / BottomSheet / ChipGroup / PressableCard / TopAppBarLarge / AnimatedCounter）编译修复中
- ✅ 自适应导航：compact 底部短导航 3 目的地 + expanded NavigationRail + 工具页全屏 push
- ✅ 工作台（问候/常用工具/今日）、全部工具、我的 三页；浅色/深色 @Preview 验收通过
- ✅ 首页常用工具编辑（homeTools ≤4）+ 全部页长按拖动排序（prefs.toolOrder）+ 通知附件图片预览 + 课表三态文案
- ⬜ 品牌启动页（SplashScreen API）+ 自适应大屏布局打磨

## M2 · 身份 + 网络 + 会话 ✅（核心）

- ✅ ApiClient（OkHttp：Bearer key + X-Class-Token、10s 超时、GET 重试一次/写不重试、no-store、401→TokenInvalid、PostgREST 错误解析）
- ✅ `class_verify` 真实接入（对象/数组响应兼容、reason 展示、30 天 token）
- ✅ token 持久化（DataStore）+ 29 天本地判定 + 401 清会话回身份页
- ✅ 班级名单 `class_members` + 6h 缓存（内存 + 按 sid 落盘）
- ✅ 真实云端冒烟：假身份返回 `{"ok":false,"reason":"姓名或学号不在班级名单中"}`；无 token 返回 200+`[]`（已按契约处理）
- ⬜ 设置页同步开关（数据层接口已就绪）

## M3 · 数据层与同步 ✅（核心）

- ✅ userdata v6 模型/合并：14 顶层键、逐 section LWW、v5 迁移、homeToolsAt/power.at 镜像、脏 section 抢时间戳
- ✅ 同步编排：GET→merge→POST、60s TTL、1200ms 防抖、无基线不盲推、离线 pending/outbox、同步开关、抑制期
- ✅ marks（hw/ntdone）：2min TTL、upsert、取消 val=false、无 DELETE、pending 补发、StateFlow 乐观更新
- ✅ 单测 **79/0**（merge 与 mp/web 夹具逐字节对拍）
- ⬜ notices/homework/fitrecs/power/power_history/site_assets/visits/feedback 仓库（后续增量，契约与 DTO 已备）
- ⬜ 清除云端数据流程编排、token Keystore 加密

## M4 · 核心工具 🟡

- ✅ 抽签（STEP1-3、不重复/排除号/剔除重抽、参数同步 draw section、导出卡片 v2 落盘相册；M0 验收报告 docs/REPORT_M0_20261107.md 已记录真机导出图）
- ✅ 分组（配置→结果/两列组卡/导出 payload/名单 BottomSheet/抽签结果带入 + 接线）
- ✅ 体测（国标评分/录入/历史/导出 payload/云端 fitrecs 读写 + 接线）
- ✅ 作业（列表/完成勾选/筛选/四态/预览 + 接线；截止提醒待做）
- ✅ 通知（结构化原生渲染/已处理/置顶/筛选/四态/附件图片预览 + 接线）

## M5 · 核心工具（第二批）

- ✅ 课表（周/日视图/上一周下一周/下一节/周末空列隐藏 + 接线；导出与提醒待做）
- ✅ 电费（关注宿舍/趋势图/日更慢更新/估算/隐私页脚 + 接线）
- ✅ 水量（月份选择器/12 月网格/4/6 人间/录入与统计/空态引导 + 接线；E2E 待扩展）
- ✅ 名册（名册/点名页签、搜索/筛选/职务徽章、到·假、状态迁移 + 接线；E2E 待扩展）

## M6 · 导出与自检

- ⬜ 导出卡片 v2 原生 Canvas（参数区/水印/页脚/编号/命名）
- ⬜ 保存相册 + 系统分享（FileProvider）
- ⬜ 健康数据导出提示

## M7 · 打磨与验收

- ✅ 数据与隐私页（导出我的数据 JSON/清除云端编排/清除本机/协议回看；合规）

- ✅ 模拟器端到端（E2E 22 PASS/0 FAIL，7 个工具页 + 首页编辑 + 导出相册 + logcat 0 FATAL；artifacts/e2e）
- 🟡 暗色已支持；动态取色开关/无障碍/触控目标/低端机性能待打磨
- ⬜ 单元测试回归脚本 + CI 式自检
- 🟡 Release 签名 APK 已验证（1.75MB / V2 签名 / Android 17 模拟器安装启动；见 docs/RELEASE_BUILD.md）
- ✅ 验收报告（docs/REPORT_ROUND3_20261108.md）+ 签名 APK 已交付桌面（安装说明同目录）

## M8 · 可选（第二阶段）

- ⬜ 通知提醒（作业截止/课表，WorkManager + 本地通知）
- ⬜ 通知提醒（作业截止/课表，WorkManager + 本地通知）
- ⬜ 课表小组件（Glance）
- ✅ 网站 ICP 备案（**闽ICP备2026042939号-2**，2026-11-23 通过；APP 端按决定不展示备案信息行）
- ⬜ 版本检查 `version.json`
