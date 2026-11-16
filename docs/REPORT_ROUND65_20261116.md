# Round 65 报告 · v4.56.4（2.5D 地图白屏修复）

## 根因（WebView DevTools 实测，非猜测）
- 地图 H5 在桌面 Chromium 正常，但 Android WebView 截图一直只有背景 + 底部卡片。
- 通过 `adb forward` + WebView DevTools 读取页面状态：`innerWidth=411, innerHeight=790`，但 `canvas.clientWidth=411, clientHeight=0`；`html/body/#app` 计算高度均为 0，`getImageData` 像素和 286358（说明画布已绘制，只是 CSS 高 0 不可见）。
- 因此不是网络/数据问题，也不是 canvas 未绘制，而是 WebView 中 `height:100%` 链无法撑开（html/body 高度 0），且页面只在启动时 resize 一次。

## 修复
- H5 `map/campus_final_25d.html`（已更新 OSS + ECS）：
  1. `#scene` 改为 `position:fixed; left:0; top:0; width:100vw; height:100vh`，脱离 0 高度的 body 链；
  2. `resize()` 用 `innerWidth/innerHeight` 兜底，并显式写 `canvas.style.width/height`；
  3. 初始布局未完成时 40/120/300/700ms 多帧重试 resize，并监听 window resize；
  4. 移动端 DPR 上限 1.25，降低 WebView 合成/内存压力。
- Android：`MAP_VERSION` 升到 `202611160800` 强制刷新缓存；保留软件层渲染兜底。

## 验证
- DevTools：canvas `514×988`、`getImageData` 非空；模拟器截图 `artifacts/map-fixed-css.png` 显示完整 2.5D 校园地图（自强楼、宋慈湖、正大门等）。
- 单测 **1478/0**；Release **3,399,577 B / SHA-256 `EAC29685…B9ED`**（vc78 / 4.56.4），冷启动 367/302ms，稳定 305/289/306ms；logcat 0 FATAL/ANR。
- 网页端：H5 已上传 OSS 并同步 ECS `/var/www/tool/map/`；web 仓库 commit `d8d6352` 已推 Gitee（GitHub 当时网络不可达，待恢复后重推；OSS/ECS 已是修复版）。

## 边界
- 真机 WebView 版本差异未逐机型验证；若仍白屏，App 顶栏「浏览器」可打开同一 H5（HTML 已修复，浏览器应正常）。
- 地图数据/交互未改，只修渲染尺寸/时机。
