# Round 45 报告 · v4.34.0（2026-11-12）

> v4 深度优化 无障碍：开关控件读屏标签 + 自动化审计。版本 **v4.34.0**（versionCode 50）。

## 1. 审计方法（新增脚本）

- `scripts/a11y_audit_screens.ps1`：uiautomator dump 逐屏解析，统计「clickable=true 但自身 text/content-desc 为空且所有后代也无文本」的节点；
- 覆盖 7 个界面：工作台 / 全部工具 / 我的 / 抽签 / 通知 / 投票 / 课表；Dump 存 `artifacts/a11y-audit/*.xml`。

## 2. 审计发现与修复

| 界面 | 修复前未标注 | 问题 | 修复 |
|---|---|---|---|
| 我的 | 1 | 「云同步」裸 Switch 无标签 | `SettingSwitchRow` 的 Switch 增加 `contentDescription = label` |
| 抽签 | 1 | 「允许重复」裸 Switch 无标签 | 增加 `contentDescription = "允许重复"` |
| （同类） | — | 抽签「剔除后自动重抽」、分组「打乱后再分组」、提醒设置开关、投票发起匿名/多选开关均裸 Switch | 全部补充 `contentDescription`（行标题/参数名） |

修复后同一脚本复测：**7 个界面全部 unlabeled=0**（工作台 15 可点、全部工具 14、我的 8、抽签 13、通知 6、投票 4、课表 4）。

## 3. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 无障碍审计（修复后） | 7 屏 unlabeled=0（证据 `artifacts/a11y-audit/`） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1081.6s）**；导航 10/10、深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.34.0 | **3,026,681 B** / SHA-256 `2EA1362E1CEA651D62E29F58023E50BA67CFE2278D2EC8A5FD88381CFA4CA9AA`（vc50）；冷启动 0.94s / 0.61s；logcat 0 FATAL/ANR |

## 4. 已知边界

- uiautomator 静态检查不等于 TalkBack 真机逐项走查（真机播报顺序/语义仍需人工确认）。
- 底部 dock 真实模糊维持 v4.33.0；页面卡片玻璃化仍未做（性能取舍）。
