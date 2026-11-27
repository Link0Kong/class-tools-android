# 工作台情境卡本地验收

2026 年 11 月 27 日 · Android · 本地验收版

[调试 APK](../releases/ClassTools-home-context-debug.apk) · [截图目录](../artifacts/home-context/screenshots/) · [首批人工复核文案](../artifacts/home-context/published-review/content.json) · [服务与联调说明](../../web/deploy/home_context/README.md)

## 工作台体验

原工作台在 Compose 页面头部显示固定问候，下方已有今日课程、班级通知、待办任务及常用工具。课表仓库已经统一处理个人课表优先、学期周次和课程铃点。Android 原先没有共用的天气入口。本次在「工作台」标题下放入一张复用 `CtCard` 的情境卡，沿用现有主题、玻璃表面、按钮和底部面板。

- 日期和当前文案在上方。天气与课表分别展示数据。课表区区分读取中、未配置、读取失败、已有缓存和已确认今日无课。进入特定课次或明天日视图时由 `CourseFocus` 传递目标。
- 内置 57 句原创文案，其中时段 12 句、天气 18 句、课表 15 句、通用 3 句、英语 9 句。英语与中文译文使用同一内容 ID。当天的课前、课间和上课情境优先，降雨、炎热或已确认的降温可影响选句。候选范围内优先避开近七天展示过的句子。
- 「换一句」在本机完成，换句后收起译文。普通刷新和从课表返回时恢复有效文案及译文状态。实际展示后才记录 ID。「我的 → 外观 → 首页内容」保存趣味／简洁模式、语气、联网文案、英语、装饰动效及天气地区。设置和展示历史按账号保存。
- 免定位。默认查询福州市闽侯县，坐标北纬 26.15°、东经 119.15°。手选后立即移除原地区天气。自动查询每地区至少间隔 15 分钟。观测满 45 分钟退出展示。晴天主题需要当日日出、日落佐证。天气不可用时，卡片仍提供本地文案和可用课表。 [闽侯气象站公示](https://www.fuzhou.gov.cn/zgfzzt/shbj/xxgk/spgs/202405/P020240531377910247910.pdf) · [和风实时天气](https://dev.qweather.com/docs/api/weather/weather-current/) · [日出日落](https://dev.qweather.com/docs/api/astronomy/sunrise-sunset/)

| 代码入口 | 内容 |
| --- | --- |
| [HomeContextCard.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/home/HomeContextCard.kt) | 卡片、换句、译文、出处面板和小灯。 |
| [HomeCourseContext.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/home/HomeCourseContext.kt) | 从现有课表生成可定位的信息。 |
| [HomePhrases.kt](../app/src/main/kotlin/com/furina/classtools/features/home/HomePhrases.kt) · [HomeContextRepository.kt](../app/src/main/kotlin/com/furina/classtools/features/home/HomeContextRepository.kt) | 内容库和选句规则、天气与文案请求及缓存。 |
| [HomeContentSettingsSheet.kt](../app/src/main/kotlin/com/furina/classtools/ui/screens/profile/HomeContentSettingsSheet.kt) · [AppSettingsStore.kt](../app/src/main/kotlin/com/furina/classtools/data/settings/AppSettingsStore.kt) | 首页设置和按账号保存的展示历史。 |
| [home_context_app.py](../../web/deploy/home_context_app.py) | 两条只读路径和每日文案生成。 |

## DeepSeek 首批联调

本机使用现有 DeepSeek 凭据，完成两次检索、两次读取、JSON 生成和开启 `safety_check: high` 的逐句审核。真实接口要求 JSON 生成与内容审核分开请求。服务只把公开素材片段和生成要求发给 DeepSeek。systemd 模板设定北京时间 06:00 触发，脚本按上海日期记录生成尝试。搜索、生成或审核失败时继续使用最近一个有效批次，有效期 72 小时。Android 按批次时间刷新磁盘缓存，换句时读取本机候选。 [DeepSeek 搜索与读取](https://api-docs.deepseek.com/guides/search/) · [内容审核](https://api-docs.deepseek.com/guides/safety/)

模型返回 12 句，自动校验和模型审核保留 8 句。再逐条对照原文，本地验收批次保留生活 2 句和学习 3 句。首批主题覆盖日常观察、笔记和概念图复习，语气以温柔为主，包含一条俏皮。使用校验优先的部分批次策略，未用其他来源或无出处内容补满 12 句。

| 短句 | 人工复核 |
| --- | --- |
| 日常里的反常，常像那只冰壶，要拆成许多小动作来看。 | 将冰壶运动的多因素分析转为日常比喻。 |
| 把一件反复出现的小事记下来，过几天再看它的轨迹。 | 将观察轨迹的做法转为生活记录。 |
| 合上笔记后，试着只用线条和词说明它们怎样发生关系。 | 对应概念图用连线和文字说明概念间联系的做法。 |
| 复习时先不排序，让重要的想法慢慢浮到纸面中央。 | 对应先收集主要想法、再组织关系的步骤。 |
| 画完图才发现，难点原来藏在两个词之间的那根线上。 | 对应借助概念连线定位还没理解的关系。 |

淘汰的三句中，两句将科学解释外推成过强的生活判断，一句把学习建议写成绝对做法。素材为中国科学院的冰壶科普和 Cornell Learning Strategies Center 的概念图指南。 [破解冰壶滑行之谜](https://www.cas.cn/kj/202602/t20260224_5102802.shtml) · [Concept Mapping](https://lsc.cornell.edu/how-to-study/concept-maps/)

## 构建和验收范围

| 检查 | 结果 |
| --- | --- |
| Android 编译和单元测试 | 2,092 项通过、2 项跳过。覆盖文案 ID、英语关闭、近期去重、课表状态、天气有效期、错误地区数据和 HTTP 请求间隔。 |
| Python 服务及原有任务 AI | 34 项通过。覆盖搜索无结果、来源编号不匹配、无效链接、模型拒绝、重复尝试、旧批次保留和 72 小时过期。 |
| 调试 APK | Android Debug v2 签名通过，最低 Android 8.0（API 26）。APK 包名为 `com.furina.classtools.debug`。安装包权限列表无定位权限。 |
| 模拟器工作台 | 5 条竖屏用例通过。覆盖默认闽侯、切换地区时撤下旧天气、恢复默认、装饰动效设置、只读文案接口、出处面板、换句不重新查询天气、译文恢复及课表导航。 |
| 工作台原有入口 | 2 条回归用例通过。检查待办同步和通知轮播时的列表位置，以及今日课程进入课表、地图后返回工作台的滚动恢复。 |
| 和风实况 | 本机缺少 API Host 和凭据。用测试响应验证实时天气和日出日落解析，用可控模拟器数据验证天气显示与地区切换。未完成生产凭据联调。 |
| 视觉范围 | 浅色与深色玻璃、411 dp 标准字号。320 dp 和系统 200% 字号、914 dp 横屏的补充验收均通过。OLED、厂商色彩、TalkBack 和性能仍待真机检查。 |

课表用例使用测试账号和测试课程。明日入口通过系统返回及页内返回回到工作台，测试比较了原句与滚动位置。界面重建恢复展开的英文译文。只读服务通过 ADB 反向转发接入实测文案，离线场景使用内置内容。小灯保留短暂亮灭反馈，装饰动效关掉时跳过缩放。

## 截图

天气画面中的小雨和 18 ℃ 来自测试数据。课表及「出处」面板画面使用可重复注入的测试内容。

| 场景 | 实际截图 |
| --- | --- |
| 浅色、闽侯天气 | [天气卡](../artifacts/home-context/screenshots/weather-light.png) |
| 深色玻璃、即将上课 | [课前卡](../artifacts/home-context/screenshots/course-dark-glass.png) |
| 展开英文译文 | [译文卡](../artifacts/home-context/screenshots/english-expanded.png) |
| 联网文案出处面板 | [出处与原文入口](../artifacts/home-context/screenshots/online-source-fixture.png) |
| 320 dp、200% 字号 | [天气](../artifacts/home-context/screenshots/weather-narrow-large-font.png) · [长课程名](../artifacts/home-context/screenshots/course-narrow-large-font.png) · [断网与未配置课表](../artifacts/home-context/screenshots/offline-narrow-large-font.png) |
| 914 dp 横屏 | [译文卡](../artifacts/home-context/screenshots/english-landscape.png) |

对合成截图取样，浅色正文及天气约 11.5∶1，深色玻璃主句和课程约 11.3∶1，深色主按钮约 7.1∶1，展开译文约 11.5∶1。此处为现有背景下的局部截图取样。

## 复现

Android 仓使用 JDK 17 构建。调试 APK 的服务地址指向本地 8097 端口。可直接安装体验离线内容，接本地服务后可读取人工复核批次。工作区内待验收的深色与玻璃改动保留。ECS 启用、源码同步和内测更新渠道发布安排在本地验收之后。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --project-prop 'ct.homeContextBaseUrl=http://127.0.0.1:8097'
```

在 web 仓运行后端验证。

```powershell
python -B -m pytest -q -p no:cacheprovider deploy/tests/test_home_context.py deploy/tests/test_task_ai.py
```

APK SHA-256 为 `E5D24376949F1D5A74E257840112E20193C8FB88A17E77743C7033EC63F256D0`。

自动审批拦截了删除 `artifacts/home-context/qa-emulator` 的清理命令。该目录仍有约 4.1 GiB 的临时模拟器文件，交付使用独立的 APK 和截图目录。

