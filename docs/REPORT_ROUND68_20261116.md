# Round 68 报告 · v1.0.0-beta1 热修（vc81）

## 1. 退出工具动画（彻底修）
根因：NavHost 根页仍做 pop 淡入；覆盖层只画卡片；快照收回时底层工具页仍参与合成，因此“缩回卡片后还停在工具内部”。
修复：根页 enter/exit/popEnter/popExit 全部 None；反向覆盖层全程不透明；快照下方铺不透明根页底色；pop 与覆盖层同时。抽帧证据 `artifacts/eh3-026..030.png`：帧 26=工具页，帧 28=干净底色+小快照（无工具页），帧 30=全部工具页。无工具内部残留。

## 2. 地图（找到真正原因）
beta1 的 APK 内 `assets/map/campus_final_25d.html` 是旧版（219,034B），缺 `#scene{position:fixed;100vw/100vh}` 与 `innerHeight` resize 修复；所以模拟器/网络版修了但 APK 仍白。已同步当前 224,646B 修复版；新增静态兜底图 + 重试入口。验证截图 `artifacts/map-newasset.png`，离线 asset 正常渲染。

## 3. 活动登录（事实与边界）
- H5 登录页头像上传不是 `<input type=file>`，而是 `wx.chooseImage/wx.uploadImage`（微信 JSSDK）；App 内无 JSSDK，永远挂起。
- 学校账号绑定接口 `POST /basic/user/student/bind` 返回 `openId不能为空` → 必须有微信 openId。
- openId 只能由微信客户端 OAuth 授权 + 学校后端 code 换取；App 没有学校开放平台签名/回调域名，无法合法获取（伪造违反安全红线）。
- 已改为「在微信中打开」主路径 + 受限学校登录说明，并删除误导按钮。**这是能力边界，不是未修**；若要在 App 内完成，需学校提供免微信绑定接口或 App SDK/回调域名。

## 4. 通知固定头
单行 56dp（返回/班级通知+总数/搜索/筛选）；5 张统计卡 + 分类 chips 移入筛选弹层/滚动内容；截图 `artifacts/notices-compact.png`、`artifacts/ui-beta-task72/51_notices_light.png`。

## 5. UI 视觉
8dp 间距、16/20/24 圆角、22sp 标题、卡片 padding/描边统一；全部工具页去整块渐变头；通知权限横幅改浅色玻璃卡；首页 Hero 保持紧凑；深色/360dp/平板截图见 ui-beta-task72。

## 测试
- 单测 **1633/0**（126 类）；Release **4,418,407 B / `E9DA002F…8CBF`**；E2E 见下。
- 更新端点 latest.json vc81 已部署 ECS（`/android/classtools-1.0.0-beta1.apk`）。
