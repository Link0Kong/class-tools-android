# Android App 隐私与合规实现清单（对齐 web/AGENTS.md 红线）

> 目标：班级工具站 Android 原生 App 在**最小必要**原则下采集/展示数据，并提供隐私政策、用户协议、同意门禁与数据管理入口。
> 相关契约：`web/AGENTS.md` §0（身份门禁/隐私）、§4b；compliance 文档：`web/_compliance/implementation_plan.md`。

## 1. 已实现（本轮）

| 项 | 实现 | 位置 |
|---|---|---|
| 首次同意门禁 | 身份页必须勾选「我已阅读并同意《用户协议》《隐私政策》」才能提交；两协议可点开为应用内弹层（静态文本、零外部请求） | `ui/screens/identity/IdentityScreen.kt` |
| 隐私政策 / 用户协议 | 内嵌静态文本（版本 2026-11-08）：收集范围、用途、存储与同步、导出分享、未成年人、联系方式、协议变更 | `ui/screens/privacy/PrivacyTexts.kt`、`PrivacyDialogs.kt` |
| 协议回看 | 「我的 → 关于」提供「用户协议」「隐私政策」查看入口 | `ui/screens/profile/ProfileScreen.kt` |
| 身份脱敏展示 | 学号仅脱敏展示（`32***24` 形式），token 不打印、不写日志；`allowBackup=false` 不进系统云备份 | `data/session/*`、`AndroidManifest.xml` |
| 最小权限 | 只申请 `INTERNET` / `ACCESS_NETWORK_STATE`；不申请定位/通讯录/存储读取；分享走 FileProvider + 系统分享 | `AndroidManifest.xml`、`features/export/ExportCardFiles.kt` |
| 云同步可关闭 | 「我的 → 同步 → 云同步」开关（关闭后仅本机使用） | `ProfileScreen` + `UserDataRepository.setSyncEnabled` |
| 本机数据清理 | 「我的 → 数据与隐私 → 清除本机缓存」（名单等可重建缓存） | `AppContainer.clearLocalCaches` |
| 导出隐私 | 导出图不含手机号/学号/宿舍号；电费页仅显示本人关注宿舍的楼栋/房间/电量，且不导出；体测导出前由页面提示健康数据 | `features/export/*`、`ui/screens/fit/*` |
| 通知/作业无隐私字段 | 通知只显示标题/摘要/来源角色；附件仅元信息；不显示发送人实名 | `data/notices/*`、`ui/screens/notices/*` |

## 2. 待办（发布前必须）

| # | 项 | 说明 |
|---|---|---|
| ~~1~~ | ✅ 「清除云端数据」编排 | 已实现：`data/privacy/PrivacyRepository.clearCloudData()`（userdata 空 v6 upsert、marks 全部 true → val=false、fitrecs 逐条 arch=true，无 DELETE）+ `DataPrivacyScreen` 二次确认；E2E 待验证 |
| ~~2~~ | ✅ 导出我的数据 JSON | 已实现：脱敏 sid、白名单 9 section、power 电费/tt 课表/roster 名单原文不导出；写 cache/exports + FileProvider 分享 |
| 3 | 备案信息 | 「关于」页预留备案号位置（上线填真实备案号并链接工信部备案系统） |
| 4 | 隐私政策/用户协议正式文本 | 当前为内嵌草稿，需法务/管理员确认后替换；建议增加生效日期与变更通知 |
| 5 | 删除账号/数据删除请求渠道 | 目前依赖班级管理员人工处理；若上架商店需明确入口（邮件/群公告） |
| 6 | Release 包合规 | `assembleRelease` 已签名；上架前补隐私合规检测（无第三方 SDK 采集、无广告） |
| 7 | 未成年人提示 | 隐私政策已含一行；如需更严格可加首启额外提示 |
| 8 | 数据传输安全 | 仅 HTTPS（CloudBase 网关）；证书校验使用系统默认；不校验证书固定 |

## 3. 与网页/小程序口径一致性

- 身份门禁：学号 + 姓名 → `rpc/class_verify` → 30 天 token；测试身份 `private.class_ids.is_test=true` 被 `class_members` 过滤（不在名单/统计显示）。
- 同步：userdata v6 14 键逐 section LWW；marks 为作业完成/通知已处理唯一真源；本机退出登录清除会话。
- 电费展示例外：仅本人关注宿舍（楼栋+房间+电量），不导出、不分享（与 web/AGENTS.md 例外条款一致）。
- 名册：显示姓名/座号/职务，**不显示学号**，不导出名单。
