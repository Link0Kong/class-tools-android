# 持续活动与设备验收

## Android 用户入口

在「通知工具 → 上课提醒」调整开关或提前时间。课前卡在设定的提前窗口出现，进入最后 10 分钟且有明确跟踪选择时，满足设备要求的通知会请求提升展示。课程详情返回当日课表，继续返回工作台。展开卡优先显示课程名称、教室、本节时段和节次，有数据时显示教师与目标周次。课后可直接在课表查看课程。

在文件传输中上传文件或提交个人 PPT 转换，也可从文件传输进入班级课件。投票在详情选择「关注截止倒计时」。报名在活动详情设置提前时长，通知上的「毫秒倒计时」可进入画中画，计时使用设备时钟。工作台「进行中」列出本账号的活动。系统通知权限关闭时仍可在应用内查看。

## 平台适配矩阵

| 入口 | 代码路径和接入条件 | 验证方法与后续工作 |
| --- | --- | --- |
| Android 标准实时活动 | `ReminderNotifier`、`LiveUpdateNotifier` 和 `LiveCapabilities` 检查全局授权、具体渠道、API 及系统提升资格，使用系统模板。API 36 对特定早期框架保留运行时模板探测。 | 设置的「实时活动通知」读取当前系统记录的提升标志。本次 API 37 实际读到 `FLAG_PROMOTED_ONGOING`。API 36 测试镜像未开放提升权限；框架探针确认普通模板不符合提升条件，既有配色兼容模板符合条件。两套镜像都通过四种权限和休眠组合的到点撤销测试。 |
| OPPO 原生实时活动 | ColorOS 支持的 Android 实时更新路径沿用标准通知。系统决定状态栏最终形态。 | ColorOS 真机检查划掉最近任务后能否保留、息屏跨铃点何时退出、完整房间和教师的折行，以及取消提升后的普通通知。 |
| 小布建议桌面卡 | `XiaobuCourseSync` 使用已配置的 launcher 场景，用户开启后调用原有分享及撤回接口。 | 在具有对应场景配置和系统服务的设备，检查同一课次的桌面撤回与点击分别生效。 |
| OPPO 厂商流体云协议 | 厂商意图信息包含独立的服务标识，需要确认适用场景与服务配置。 | 取得课表或传输场景接入确认后，验证厂商更新、撤回和点击，留存设备型号及系统版本。[OPPO 接入准备](https://open.oppomobile.com/documentation/page/info?id=13572) |
| 小米超级岛／焦点通知 | 需要按应用及场景获取服务权限。当前客户端使用标准 Android 通知。 | 场景获批后验证 OS 版本、用户开关、大/小岛及到点撤销。[小米接入流程](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132) |
| vivo 原子通知 | 厂商通知通道与原子通知场景权限分别申请。 | 资格确认后以对应场景模板测试原子岛与通知中心，含用户移除后的更新。[vivo 接入指导](https://dev.vivo.com.cn/documentCenter/doc/894) |
| HarmonyOS 实况窗 | `LiveActivity.ets` 按本机能力、开关、权益和场景选择 SCHEDULE 或 PROGRESS，普通通知负责降级。 | 1.0.20 签名 HAP 在 API 26 模拟器原地升级通过，普通通知替换、到期撤除和隐藏后回扫通过。获授权真机补测原生胶囊、锁屏、后台跨铃点和场景权益。[华为场景指南](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/liveview-introduction/) |
| 网站 | `web/tools/index.html` 提供文件传输页、课程与投票详情弹窗。 | 已核对页面入口。后台通知仍需网页通知授权和对应对象的激活链接。 |
| 微信小程序 | `app.json` 注册 `pages/tt/tt`、`pages/nt/nt`、`pages/cloudspace/cloudspace` 和 `pages/ppt/pptdl/pptdl`。当前路由表未注册独立投票详情页。 | 已核对发布工程路由。若增加投票提醒，需要可打开的详情路径和微信订阅消息模板。 |
| Windows | `windows-class-tools/src/ClassTools.App/Pages/` 下的 FileDropPage、PptPage、VotePage、TimetablePage 承载对应功能。 | 后续如需 Windows 通知，接入通知激活参数并验证账号切换和返回路径。 |
| Apple | 当前工作区没有 iOS 工程。 | 后续建立 ActivityKit 客户端时，复用活动身份、截止时间、紧凑态优先级和详情目标。[Apple Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities) |

## 系统层核对

使用 9999 段测试身份，记录系统版本、权限、通知渠道、精确闹钟授权和实际提升标志。发布一条短时课前活动，保持应用在后台，等待跨过铃点。到点前后从系统查询通知和前台服务，再回到工作台检查过期活动没有恢复。分别以亮屏、息屏／Doze、划掉最近任务、精确授权开／关执行。非精确闹钟的交付会受到省电策略影响，报告记录实际退出时间。

旧服务曾用 Handler 按剩余时间停止自身。Android Handler 的时基在深度睡眠时暂停，AOSP 通知超时撤销路径排除了前台服务标志。旧版在缺少课表快照时也会跳过对账。当前按本地课次记录、绝对截止时间和版本撤销；具体厂商设备上出现残留的触发顺序需要系统日志确认。[Android 定时机制](https://developer.android.com/develop/background-work/services/alarms) · [AOSP 通知超时实现](https://android.googlesource.com/platform/frameworks/base/+/64eaba03c5db8b431aef8a88d40593631951fe99/services/core/java/com/android/server/notification/NotificationManagerService.java) · [Android 实时活动规则](https://developer.android.com/develop/ui/views/notifications/live-update)
