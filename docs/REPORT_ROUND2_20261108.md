# Round 2 增量验收报告 · Android 原生 App（2026-11-08）

> 目标不变：「对班级工具站进行底层重构，以安卓17的设计语言和思路为基础，完成班级工具站安卓app的开发」。
> 本轮（goal round 2）在 M0 垂直切片基础上，把 **6 个新工具**做到原生可运行并接线，打通 Release 签名与自动化 E2E。
> 结论：**7 个工具原生可运行（抽签/分组/体测/作业/通知/课表/电费）**，全量单测 **342/0**，@Preview **48 张**，模拟器 E2E **22 PASS / 0 FAIL**，Release 签名 APK 2.06MB 可安装启动。整体目标继续（水量/名册、云端写链路、发布交付未完成）。

## 1. 本轮新增/交付

| 类别 | 内容 | 来源 |
|---|---|---|
| 作业 | `data/homework/`（TTL 10min、附件元信息、到期/prune）+ `HomeworkScreen`（筛选/完成勾选 marks hw/四态） | contract |
| 通知 | `data/notices/`（两级签名、CLASS_NOTICE_V1 解析、override 白名单、hidden 过滤、状态机/相对日期）+ `NoticesScreen`（置顶/未处理筛选、marks ntdone、原生卡片渲染） | contract |
| 课表 | `data/timetable/`（site_assets id=tt、jsonb/字符串兼容、6h TTL、周次/单双周/下一节纯函数）+ `TimetableViewScreen`（周/日视图、上一周/下一周、今日高亮、周末无课隐藏列、≥600dp 双栏） | design |
| 电费 | `data/power/`（power/power_history、2min/5min 缓存、慢频 13/16 号楼 slowStats、估算守卫）+ `PowerScreen`（关注宿舍 BottomSheet 写 userdata、电量环、24h/7d/30d Canvas 趋势、点击/拖动命中最真实采样点、固定右上角气泡、慢更新提示、隐私页脚） | design |
| 分组 | `features/group/`（均衡分组、可注入 seed、状态机、userdata.group 编解码、GroupsBlock 导出）+ `GroupScreen`（配置→结果、两列组卡、名单 BottomSheet） | design |
| 体测 | `features/fit/`（国标 2014 大学组评分、BMI 四档、加分/缺项/等级、userdata.fit + fitrecs.data 编解码、历史 ≤2）+ `FitScreen`（9 项录入、实时评分、历史/国标表 BottomSheet） | design |
| 首页 | 「今日」接真实下一节课（TtCalendar.plan）+ 待完成作业（homework + marks hw）；「编辑」常用工具 ≤4 写 userdata v6 homeTools（勾选描边+计数） | Lead |
| 我的 | 云同步开关（UserDataRepository.setSyncEnabled）、清除本机名单缓存、脱敏账号信息 | Lead |
| 导航 | `draw/group/fit/hw/nt/tt/power` 七个工具全部接线真实 Screen；回前台 ON_RESUME 刷新；统一导出 `launchExport`（相册/分享） | Lead |
| 发布 | Release 签名配置（本机 keystore + keystore.properties，gitignore）、R8 混淆、`assembleRelease` 成功、apksigner V2 校验、模拟器安装启动；`docs/RELEASE_BUILD.md` | Lead |
| 验收 | 模拟器 E2E 脚本 `scripts/e2e_smoke.ps1`（文本驱动点击、每步截图、logcat/相册断言）+ `docs/E2E_SMOKE.md` | toolchain |

## 2. 验证证据（本轮）

| 项 | 结果 |
|---|---|
| Debug 构建 | `assembleDebug` BUILD SUCCESSFUL，APK **20.87 MB**（`artifacts/classtools-1.0.0-debug.apk`） |
| Release 构建 | `assembleRelease` BUILD SUCCESSFUL（R8 + lintVital），APK **2.06 MB**（`artifacts/classtools-1.0.0-release-signed.apk`），apksigner V2 通过，模拟器安装启动正常 |
| 单元测试 | **342 tests / 0 failures / 0 errors**（27 个测试类；数据层 79 + 抽签 69 + 导出 6 + 作业通知 68 + 课表电费 59 + 分组体测 61） |
| @Preview | **48 张** PNG（`artifacts/previews/`）：全部页面浅/深色、课表周/日/周末有课、电费 ready/慢更/空/错误、通知/作业列表与空态、分组/体测、expressive 组件、导出卡 |
| 模拟器 E2E | **22 PASS / 0 FAIL / 164.3s**（`artifacts/e2e/`，17 张 1080×2400 截图 + summary/logcat/server 日志） |
| E2E 覆盖 | 身份页→工作台→全部工具→抽签 STEP1/2/3→导出相册→返回；作业/通知/课表/电费/水量/分组/体测 七个工具页逐页打开断言；首页常用工具编辑；logcat 无 FATAL/ANR；相册确有新导出文件 |
| 云端实测 | `class_verify` 假身份 200/ok:false；无班级 token 端点 200+[]（与契约一致） |

E2E 截图（节选，全部在 `artifacts/e2e/`）：
`01_identity.png`、`02_workbench.png`、`03_all_tools.png`、`04_draw_step1.png`、`05_draw_step2.png`、`06_draw_step3.png`、`07_export.png`、`11_hw.png`、`12_nt.png`、`13_tt.png`、`14_power.png`、`15_water.png`、`16_group.png`、`17_fit.png`、`18_home_edit.png`。

## 3. 关键实现说明

1. **数据层扩展**：新增 homework/notices/timetable/power 四个仓库，全部复用 `ApiClient`（Bearer 公开 key + X-Class-Token、GET 重试/写不重试、no-store、401→清会话），并保持契约语义（TTL、空数组合法、失败降级缓存）。
2. **电费与课表口径与 web/mp 对齐**：电费纯算法逐函数对齐 `mp utils/power.js`（异常扣减、区间统计、估算守卫、慢更新、13/16 号楼 slowStats、最近采样点）；课表周次/单双周/下一节与网页同源规则；周末两天无课隐藏列。
3. **通知原生渲染**：CLASS_NOTICE_V1 结构化 JSON 解析（items 元信息不存 base64）、override 白名单、hidden 过滤、状态机（报名>截止>活动>无时间，recurrence 不判过期）；附件图片预览/未知 kind 兜底留待后续。
4. **首页联动**：`TtCalendar.plan` 计算下一节课（今天/明天/周X + 时段 + 课程名），homework + marks 计算待完成数；点击今日卡直达课表/作业。
5. **Release 签名**：keystore/密码只存本机且已 gitignore；发布包无 Debug「开发预览」入口，真实业务链路需真实班级身份（测试身份凭据由用户提供）。

## 4. 已知问题 / 下一轮（Round 3）

| # | 项 | 说明 |
|---|---|---|
| 1 | 水量 / 名册 仍为占位页 | contract 已 claim task-11，摸清 UserDataRepository/PowerRooms 结构，等 GO 后落码 |
| 2 | fitrecs 云端读写未实现 | FitScreen 已产出记录，`onSave` 目前只本地提示；需 fitrecs GET/POST/PATCH(arch) 仓库 + 接线 |
| 3 | 抽签 STEP2 分组 / STEP3 结果单 | 分组引擎已就绪（features/group），后续把抽签结果导入分组 + 结果单导出 |
| 4 | 通知附件图片预览 / 未知 kind 兜底 | 当前列表只显示附件元信息；后续接 FileProvider/图片加载或 WebView 兜底 |
| 5 | 全部工具长按拖动排序（prefs.toolOrder 云同步） | 首页已支持勾选编辑；全部页拖动排序未做 |
| 6 | 真实身份端到端 | 需旅行者本人或测试身份（学号+姓名）实测登录 + 云端数据；当前 E2E 用 Debug 开发预览（无云数据） |
| 7 | 发布交付 | 备案号页、隐私政策页、版本检查、桌面交付 APK（待整体完成后） |
| 8 | 桌面/平板横屏细节 | 部分页面 ≥600dp 已有双栏，平板实机视觉未逐页复核 |
| 9 | E2E 发现「底部导航偶发点击丢失」 | toolchain 报告：全部页点工作台偶发无反应。Lead 手工复测（同一 APK/模拟器）可正常导航；初步判断为 headless 模拟器 + 高频 uiautomator dump 下 adb tap 丢失（P3），非 App 逻辑缺陷。下一轮用更稳的自动化（点击后断言+重试）与手工复测确认 |
| 10 | 课表空数据文案 | Debug 无 token 时课表显示「课表数据格式异常」（实际是空响应被归类为格式错）；真实登录后正常。后续统一为「暂无课表数据」并区分网络/鉴权/空数据 |

## 5. 文档索引（本轮新增）

- `docs/api/HW_NOTICES_IMPL.md`（作业/通知类清单/接线/68 用例）
- `docs/api/TT_POWER_IMPL.md`（课表/电费类清单/API/口径对照/59 用例）
- `docs/api/GROUP_FIT_IMPL.md`（分组/体测类清单/评分表/61 用例）
- `docs/E2E_SMOKE.md`（E2E 脚本用法/覆盖/22 PASS 记录/扩展指南）
- `docs/RELEASE_BUILD.md`（签名/构建/R8/安全红线）
- `docs/ARCHITECTURE.md`、`docs/ROADMAP.md`、`README.md`（本轮已同步）
