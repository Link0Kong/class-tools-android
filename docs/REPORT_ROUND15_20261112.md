# Round 15 报告 · v4.4.0（2026-11-12）

> v4 深度优化 P1 批次 C：小屏/大字实测 + 无障碍语义第一批。版本 **v4.4.0**（versionCode 20）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 小屏 360dp 实测 | ct-a37-small（720×1280@320dpi）安装 Debug：工作台、全部工具、投票（身份失效四态）、名单（三分类/搜索/筛选）布局正常，无横向溢出；截图 `artifacts/adaptive-small-*.png` |
| 大字 1.3× 实测 | 系统 font_scale=1.30 重启后：工作台、全部工具、名单页正常（三分类/段选/搜索不截断，卡片副标题按设计省略号）；截图 `artifacts/largefont-*.png` |
| 投票选项无障碍 | `VoteOptionRow` 由 clickable 改为 `selectable(selected, enabled, role=RadioButton/Checkbox)`，TalkBack 可读出选中状态与角色 |
| 名单成员无障碍 | 成员行 `semantics(mergeDescendants=true)`，TalkBack 合并读出座号/姓名/职务/勾选状态 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（967.9s）**；名单三分类 segments=True、投票页、电费页、通知横幅、清缓存确认、深色组、导航 10/10、logcat 0 FATAL/ANR |
| 小屏/大字 | 截图核验通过（无截断/溢出） |
| Release v4.4.0 | **2,856,130 B** / SHA-256 `0E7FC2BEEA1DC40EAB0E753FBB204A5E6E6976ADAF36C3CF3F55B35B5119EC3C`（vc20）；两次冷启动 0.81s / 0.38s；logcat 0 FATAL/ANR |

## 3. 待办（批次 C 收尾 + 批次 D）

- 大字模式投票详情/发起、作业/通知长列表复查（需登录真实数据）。
- TalkBack 全量语义走查（77 处装饰性 null 逐处判断）。
- 触控 ≥48dp 逐项核对（自定义 chip/dock/tool 卡片）。
- 批次 D：Baseline Profile、CtAnimatedCounter 绘制阶段化、列表稳定性。
- 真实背景模糊：维持毛玻璃，后续评估 PixelCopy/合成层。
