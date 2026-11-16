# Round 67 报告 · v1.0.0-beta1（11 项反馈 / 测试版本）

## 交付概览
- versionCode 80 / versionName **1.0.0-beta1**；UI 显示「v1.0.0-beta1（测试版本）」；更新提示「发现新版本 · 测试版本」；channel=`beta`；正式使用前都为测试版本。
- 单测 **1624/0**；E2E 见文末；Release **3,562,103 B / SHA-256 `E200C0B2…90ECB3`**；更新 JSON/APK 已部署 ECS。
- 桌面 APK：`C:\Users\Ko_Li\OneDrive\桌面\班级工具站_v1.0.0-beta1_安卓.apk`。

## 11 项逐条
1. **学信网参考 HFUT-Schedule**：研究其 WebView+Cookie 模式（本地 `_research/hfut-schedule`；WebViewFactory.setInitial 的 Cookie 启停/flush、WebViewActivity 承载）；改进为标准 Chrome UA（无 wv）、Cookie 预开启、第三方 Cookie/DOM storage、onPageFinished flush、按域清 Cookie 重登；人工确认/官方入口兜底保留。真实账号仍需本人（验证码/实人）。
2. **返回工具残留内部页面**：改为真实界面快照缩回（View.draw 截图 → pop → 快照缩到卡片），动画中和结束后都不会露出工具内部；失败降级图标卡；录屏抽帧证据 `artifacts/ui-beta-task69/evidence/`。
3. **活动微信登录**：新增微信登录引导（检测三态/复制链接/在微信中打开/明确降级）；说明 openId 只能微信内获得、Cookie 不共享；不伪造登录。真机微信内打开需用户验证。
4. **下一节几千分钟**：新纯函数格式化，>1 天用天/小时，明天/周X HH:mm；单测 11。
5. **地图仍加载不出来**：H5 打包 assets 离线优先（file:///android_asset），onPageFinished 多帧 resize 修 canvas 高 0；CDP canvas 514×988、定位桥正常；**飞行模式截图与在线一致**；ECS/浏览器降级保留。
6. **工具上部 UI 过重**：CtTopBar 默认紧凑 56dp/单行 22sp/隐藏副标题，所有工具页统一瘦身；页内自定义头（活动/快递）未完全动，可继续做。
7. **通知 UI + 最新发布排序**：根因=item time 只有 HH:mm 被按渲染日解析；改 max(行 ts, 组 updatedAt)；卡片/chips/顶部 5 卡收紧、未读点、附件 N。
8. **v1.0.0-beta1**：已改，更新器/弹窗/关于页/服务端 JSON 全按测试版本。
9. **移除 OPPO/无用入口**：Profile 删除 OPPO 推送、Debug 实时活动测试/关闭；保留可用通知权限/提醒；OppoPushManager 服务端逻辑保留但无 UI。
10. **绿色卡片改版**：单行 meta + 22sp 问候 + 34dp 头像 + 紧凑课程块；高度约 190→137dp，去胶囊/暗角；深色/平板不变。截图 `02_home_hero.png`。
11. **课表接入手机（WakeUp/系统日历）**：
   - 课表页右下「课表接入」：系统日历写入（事件+15/30 分钟提醒、选可写日历、幂等清理/清除）、.ics（RRULE 周次/单双周、VALARM、VTIMEZONE）、WakeUp `.wakeup_schedule` 5 行备份文件（可分享/下载，WakeUp「从文件导入」）。
   - 参考：WakeUp 开源 https://github.com/gitleon/WakeupSchedule_Kotlin 、https://github.com/Xtao-Labs/WakeUp2XiaoAi 、HFUT CalendarUtils/AIDL、RFC5545/CalendarContract。
   - 分享口令为 WakeUp 服务端生成，离线无法伪造，故不做；格式按开源 3.612 版，WakeUp 6.x 若改格式需真机验证。
   - 单测 59/59（ICS/WakeUp JSON/周次/权限），真机步骤见任务报告。

## 测试与边界
- 单测 1624/0；E2E **44 步 0 FAIL（1055.5s，导航 10/10，logcat 0 FATAL/ANR）**；真实学信网/学校账号/微信内打开、WakeUp 6.x 导入、系统日历 OEM 行为需用户本人设备验证。
- 已知风险：返回快照对 WebView/SurfaceView 硬件层可能拍不全（最坏退化为图标卡）；工具页内自定义头未全部压缩；E2E 偶发模拟器 OOM/启动失败（非应用崩溃）。
