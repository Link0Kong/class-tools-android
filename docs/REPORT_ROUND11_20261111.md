# Round 11 报告 · v4.0.0（2026-11-11）

> 用户新需求：「各处功能和整体还是有数不清的优化和使用问题，请搜集网上相关内容和程序演示，进行深层次优化，同时把班级工具站的近期功能同步一下」。
> 本轮 = **深度调研 + 系统审计 + 功能同步第一批（投票/名单/电费）+ 全局 P0 修复**。版本 **v4.0.0**（versionCode 13）。

## 1. 调研与审计

| 文档 | 内容 |
|---|---|
| `docs/design/V4_RESEARCH_AND_DEMOS.md` | ColorOS 16 光场/液态玻璃、M3 Expressive Motion、Compose 性能规范、Prismal/AppleLiquidGlass 实现、班级小管家/投票 UI 参考、自适应规范 + 优化原则；真实背景模糊尝试记录（Haze/GraphicsContext 均失败，回退毛玻璃） |
| `docs/design/V4_UX_AUDIT.md` | 逐页审计 **112 条问题（P0 14 / P1 52 / P2 46）**，每页证据/影响/建议/验收；含修复批次与决策点 |
| `docs/design/V4_NEW_FEATURES_UI.md` | 投票全套 UI 规格、名单三分类融合方案、电费同期/隐藏 24h 状态机 |
| `docs/design/V4_MOTION_LIGHT.md` | 光场 + 水滴击湖 + specular 跟随 + M3 spring → CtMotion 映射、无障碍降级、落地顺序 |
| `docs/api/VOTE_LEAGUE_POWER_IMPL.md` | 投票/名单/电费数据层公开 API、契约映射、错误表、接线点、风险 |

## 2. 功能同步（对齐网页/小程序现状）

### 投票（全新）
- 数据层：`data/vote`（5 个 RPC：list/detail/create/submit/manage；audience=all/committee/league/custom+座号；单选/多选/匿名/结果可见性/服务端截止/编辑仅 0 票/软删；模型无 sid；`VoteLogic` 倒计时/可见性/校验/幂等草稿；`VoteError` 中文文案）。
- UI：`ui/screens/vote/`（列表：进行中/已结束/我发起、每秒倒计时、我已投、下拉刷新、四态、平板两列；详情：投票/改票、结果条+实名投票人、隐藏结果、15s 轮询、管理：编辑 0 票/提前结束/延长 30·60·120/删除二次确认；发起：2–20 选项+说明+配图压缩、单选多选+上限、匿名、改票、结果可见性、可见范围四类+姓名搜索成员多选无学号、限时快捷/指定截止/不限时、即时校验、失败幂等重试）。
- 接线：`AppContainer.voteRepository`、ToolCatalog「投票」、`Routes.VOTE/VOTE_DETAIL/VOTE_CREATE(?pollId)`、`MainNavHost` 三路由 + `ContainerVoteMemberSource`（class_members 班委/全班 + class_league 团员，仅 seat/name/badge）。

### 名单三分类
- `data/member`：`ClassLeagueRepository`（class_league RPC，6h 缓存、失败降级）、`MemberCategory`（班委/团员/全班）、`MemberCategoryBuilder`（角色同口径/计数/搜索/无学号）。
- UI：名册内三分类段（计数/搜索/仅看已加入点名/下拉刷新；团员懒加载失败降级提示）；点名逻辑不变。

### 电费
- `features/power/PowerCompareLogic`：自然周期同期比较（今日 vs 昨日同时刻、本周 vs 上周、本月 vs 上月；覆盖 ≥35% 才可比较；不插值）、13/16 号楼隐藏 24h 的 showRange/normRange/visibleRanges。
- UI：13/16 隐藏 24h 档并自动落 7d、切回实时房恢复前档位、内部 24h 照常拉取用于慢频判定；tile 同期徽标（较昨日/上周/上月同期 +x%，耗电增加 warning / 减少 positive，带符号文字，不可比隐藏，点击说明时段）。

### 已有对齐（上一版 3.6.0 已同步）
PPT 演示（兼容下载/视频/单独音视频）、诊断 v2.1、通知附件 URL、教务登录、课表/通知单条提醒等。

## 3. 全局 P0 修复

1. 通知权限关闭时：工作台/我的/提醒设置显示可关闭横幅 + 一键去权限页（本会话状态，不重复弹窗）。
2. 「清除本机缓存」二次确认（危险色）+ 成功 Snackbar。
3. 体测评分色改用 `GradeColors` 浅/深语义 token（去掉硬编码浅色）。
4. 课表 7 处关键字段 maxLines=1 → 2（周次/下一节/日视图/全部课程），大字体不截断关键信息。
5. PPT 固定 72dp 行高 → `heightIn(min=72.dp)`，大字体不裁切。

## 4. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065 / 0 failures**（85 类；基线 994，+71 数据层用例） |
| Debug 构建 | BUILD SUCCESSFUL；投票/名单/电费/横幅可进入（未登录投票页四态正常） |
| Release | **2,839,742 B** / SHA-256 `0A788D3511A49ACD098B1D48216A46D89E83B54A50FCE6432DBED2FD83085783` / v4.0.0 vc13；apksigner V2 通过；冷启动 0.66s、logcat 0 FATAL/ANR |
| 预览 | 投票 17 张（140–156）、名单/电费/横幅等 11 张（160–170） |
| 待补 | toolchain task-37：完整 E2E（原 40 + vote/roster）、快速 38、Release 复验、深色/底栏断言、文档 §14/§13 |

## 5. 已知边界 / 下一轮（P1）

- **电费**：现网历史约 6 天 → 「较上周/上月同期」通常不可用；14d/70d 隐藏窗口补拉需仓储扩展（已记录 P-01 遗留）。
- **投票**：图片未处理 EXIF 旋转；成员选择懒加载团员计数首屏为 0；真实云端投票/可见范围/结果可见性需测试身份实测。
- **深色/真实模糊**：底栏真实背景模糊仍未实现（Haze/GraphicsContext 录层+RenderEffect 均失败，回退毛玻璃，见研究文档）。
- **下一轮 P1**：全局撤销机制、更多页面下拉刷新、M3 motionScheme 统一动效、无障碍/平板复核、剩余审计条目（P1 52 条）分批修。

## 6. 文档

- 本报告 + `docs/design/V4_*` + `docs/api/VOTE_LEAGUE_POWER_IMPL.md`
- toolchain 追加：`docs/E2E_SMOKE.md §14`、`docs/RELEASE_BUILD.md §13`

## 7. v4.0.1 修复（2026-11-11 22:50）

toolchain 首次 v4.0.0 E2E 发现「roster category segments missing」：`RosterScreen` 在 `members.isEmpty()`（测试身份/同步失败）时把整块内容替换为空态，导致名册 tab 三分类与搜索不可见。修复：名册 tab 即使名单为空也保留三分类/搜索（空态在列表区展示），点名 tab 空名单仍提示同步；版本 **4.0.1 / vc14**（APK 2,839,746 B / SHA-256 `9DCED3F5…E92F`），待 toolchain 复验。

## 8. v4.0.2 收尾（2026-11-11 23:40）

- 工具「名册」更名「名单」（与网页/小程序一致，副标题「班委/团员/全班 · 点名」）。
- Release **4.0.2 / vc15**：2,839,746 B / SHA-256 `17239BFF20A16CE54394C460E5C5987C97421049E6FCF4D84609B021179A210E`；Lead 冷启动 0.42s、logcat 0 FATAL/ANR；单测 1065/0。
- 完整 E2E：toolchain 运行时卡在「宿舍水量」步骤（脚本/环境问题，failure.png 为空），已中断并改为 Lead 目标抽查（工作台通知权限横幅、投票页、名单页、电费页均可进入；Release 冷启动/版本/logcat 已验）。完整 E2E/矩阵回归留待下一轮重跑。
