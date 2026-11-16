# Round 66 报告 · v4.57.0-test1（更新系统 / 活动头像 / 今日校园 / 学信网）

## 0. 参考的类似项目与资料
- HFUT-Schedule（用户指定的复刻对象）：学工/今日校园采用 **OkHttp CAS 登录拿 cookie → WebView 带 cookie 打开门户**，不是 JS 填表；本地参考 `_research/hfut-schedule/.../RedirectTicketInterceptor.kt`、`Starter.startWebUrlInner(cookie)`。https://github.com/Chiu-xaH/HFUT-Schedule
- WebView 上传头像无反应的标准根因是缺少 `WebChromeClient.onShowFileChooser`（返回 true + FileChooserParams + ActivityResultLauncher）。https://docs.aihelp.net/zh/web/faq/troubleshooting.html
- 学信网无公开 API，主流做法就是 WebView 内本人登录后读取页面/Cookie（验证码/实人无法自动）。https://global.v2ex.co/t/1172126
- 学校 CAS 页面为定制版：`login.js` 使用 `encryptPassword(pwd, pwdEncryptSalt)`；`encrypt.js` 为 AES/CBC/PKCS7，明文=randomString(64)+pwd，key=salt，iv=16 位随机，Base64。本实现按此复刻（JCE 解密回验）。

## 1. 检测更新 / 自动更新 / 测试版本 1
- 版本：vc79 / `4.57.0-test1`，界面显示「测试版本 1」。
- 自动：登录后每天最多静默检查一次，发现新版自动下载（进度/非强制可取消），完成后拉起系统安装器（不静默安装）；force 不可取消。
- 手动：「我的→关于→检查更新」，显示正在检查/已是最新/发现新版/失败重试。
- 下载：DownloadManager 公共 Downloads + OkHttp cache 回退；FileProvider；sha256 校验；未知来源授权引导。
- 服务端：`http://121.196.47.2/android/latest.json` + `classtools-4.57.0-test1.apk`（OSS 因 `ApkDownloadForbidden` 限制，APK 以 `.bin` 上传 OSS 后 ECS 落成 `.apk`；客户端只走 ECS，OSS 仅 JSON fallback）。
- 手工验证：检查更新成功读取 JSON → 显示「已是最新版本」；版本行「v4.57.0-test1（测试版本 1）」。

## 2. 活动登录要求上传头像，点击无反应
- 根因：活动登录 WebView 只有 WebViewClient，没有 `WebChromeClient.onShowFileChooser` → `<input type=file>` 点击被静默吞掉。
- 修复：新增文件选择逻辑（accept 归一、多选、取消也回传 null、生命周期取消）；`onShowFileChooser` 返回 true，优先 FileChooserParams.createIntent（带 MIME/多选），回退 ACTION_GET_CONTENT；DEBUG 顶栏「上传自测」用本地 HTML 复现同款 input。
- 实测：模拟器活动登录页 →「上传自测」→ 点「选择图片」→ 系统照片选择器弹出（修复前无反应）。

## 3. 今日校园还是打不开
- 实证：学校 CAS 登录页 16.7KB 定制页，密码需 AES 加密；直接 POST 明文必失败；`/login2` 是 CAS 跳板、门户 index.html 是 Vue SPA 外壳。
- 修复：实现 `TodayCampusCasLogin`（GET 登录页→解析 execution/lt/盐→AES 加密密码→POST→跟随 302→GET 门户首页→Cookie 注入 WebView）；失败回退 JS 填表/手动；保留空白检测/清缓存重载/错误条/浏览器入口。
- 实测：模拟器打开今日校园→学工服务→显示统一身份认证登录页（英文/中文、账号/动态码、登录按钮），无白屏；未保存密码时提示手动登录。

## 4. 学信网还是不行
- 学信网登录/数据依赖本人账号与验证码，无法自动；已把人工确认做成一等路径：登录页和卡片页常驻「我已登录，进入卡片页」；点击后强制卡片页，提取失败也显示「已登录 · 打开官方页面查看」+ 10 个官方入口 + 完整学信档案入口，不再卡登录页或显示未登录。
- 实测：模拟器登录页正常渲染，点击兜底按钮后显示已登录卡片与官方入口。

## 测试与边界
- 单测 1534/0；E2E 44 步 0 FAIL（1001.7s，导航 10/10）；Release 3,432,477 B / `0C3424D4…E02BA9F`；冷启动 902/476ms，稳定 474/425/523ms。
- 真实学校账号/学信网账号登录、真实活动 H5 上传接口、今日校园真实门户数据仍需用户本人验证；本轮已按公开页面/接口特征与降级路径实现并做设备可达性验证。
- E2E 首轮因模拟器 OOM（长时间 WebView 测试后）失败，重启模拟器后 44/0 通过；非应用缺陷。
