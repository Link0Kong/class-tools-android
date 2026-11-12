# v4 深度优化：网络调研 / 演示参考与落地清单（Lead，2026-11-11）

> 用户需求：「各处功能和整体还是有数不清的优化和使用问题，请搜集网上相关内容和程序演示，进行深层次优化，同时把班级工具站的近期功能同步一下」。
> 本文是调研结论 + 优化清单；功能同步（投票/名单/电费同期等）见 `docs/api/VOTE_LEAGUE_POWER_IMPL.md`（契约）与 `docs/design/V4_NEW_FEATURES_UI.md`（UI 规格）。

## 1. 调研来源（原文链接）

**系统视觉/光场**
- ColorOS 16「光场设计」：液态玻璃通透材质、边缘光影随操作流动、文件夹「水滴击湖」三段式曲线、壁纸主色浸染图标、夜间亮度曲线降低 12% —— [PChome 报道](https://article.pchome.net/news/7142.html)
- Material 3 Expressive Motion：motion physics 主题化，spatial/effects 两套弹簧，default/fast/slow 三档 —— [Material Design 博客](https://m3.material.io/blog/m3-expressive-motion-theming)

**液态玻璃 / 背景模糊实现**
- Apple Liquid Glass for Android：实时背景模糊 + 折射（放大）+ vibrancy，降采样保证性能 —— [GitHub](https://github.com/SidZadaun02/AppleLiquidGlassForAndroid)
- PrismalAGSL：Compose 版液态玻璃，三级管线（API25–30 磨砂 / API31–32 RenderEffect 模糊 / API33+ AGSL 折射+色散），`PrismalBackdrop` 采样 + `drawPrismalGlass`，内置液态底栏 —— [GitHub](https://github.com/styropyr0/prismalagsl/blob/163376d4449e915bc2ebe8f4d12c00b289647bb4/README.md)
  - JitPack 探测：`com.github.styropyr0.PrismalAGSL:Prismal` 元数据存在但构建产物返回 500（需 JitPack 现场构建），**暂不作为依赖引入**；改用其「GraphicsContext 录层 + RenderEffect 模糊」思路自研（见 §3）

**性能 / 自适应规范**
- Compose 动效性能（phase deferral：动画属性不要在 Composition 读，用 `graphicsLayer`/`offset` lambda；共享元素按可见性挂载）—— [Android Developers](https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/references/animation)
- 自适应布局：按 windowSizeClass/可用空间而非设备尺寸决定布局 —— [Android Developers](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes)

**同类产品参考**
- 班级小管家：作业发布/多形态提交、打卡、在线测验、成绩私密、班级网盘、投票调查、人员分组/权限 —— [官网](https://service.banjixiaoguanjia.com/public/website/bjgl.html)
- 微信投票小程序：实时柱状图、状态图标、倒计时/截止、多选/单选、管理 —— [We投票](https://www.fastadmin.net/store/wevote.html)

## 2. 从调研提炼的优化原则（映射到本 App）

| 原则 | 本 App 落地 |
|---|---|
| **光场层次**：光从上方来，边缘高光+内阴影+柔和外阴影；玻璃材质有厚度/折射 | 底栏真实背景模糊（§3）+ 卡片高光描边 + 弹层/顶栏统一 L2/L4；玻璃层级 ≤2 层/页 |
| **液态动效**：水滴击湖三段式（接触-扩散-回弹）、拖动拉伸/松手吸附 | 底栏药丸已有弹簧；页签/弹层/列表项统一 `CtMotion.spatial*`；投票结果条、电费切档、课表切周加「落点回弹」 |
| **夜间友好**：深色下降低亮度、避免纯白高光 | 玻璃深色 token 已降高光；检查纯白按钮/大面积白色卡片 |
| **M3 Expressive motion**：spatial vs effects 分离，default/fast/slow | 把 `CtMotion` 映射到 `MaterialTheme.motionScheme`（M3 1.4 提供 `defaultSpatialSpec()` 等），页面转场用 default、控件用 fast、大面板用 slow |
| **性能**：不牺牲流畅度 | 审计 composition 内动画读取、列表 key/stability、图片采样；试点baseline profile |
| **班级工具场景**：快、少文字、可离线、误触可恢复 | 所有危险操作确认+撤销；加载/空/错误/离线四态统一；表单即时校验；一键复制/分享 |

## 3. 真实背景模糊（底栏）——自研实现路径

前面 v3.3 已试：Haze 失败；Compose `LocalGraphicsContext` + `GraphicsLayer.record` + `Modifier.graphicsLayer{renderEffect}` 的模糊对 drawLayer 内容作用有限。
v4 改进：把 `BlurEffect` 直接设到**录层 `GraphicsLayer.renderEffect`** 上（而不是外层 modifier），再 `drawLayer()`；预期可得到真正的背景模糊（API31+，旧设备回退磨砂）。
验收：浅/深色截图对比（底栏下内容应模糊且有色散/亮度自适应）、滚动时跟随、无掉帧（adb gfxinfo 帧耗时 <16ms/95th）、E2E dock 断言仍 PASS。
失败则回退 v3.2 毛玻璃（保留代码开关）。

## 4. 待办清单（P0/P1/P2）

### P0（用户可感知、本轮/下轮必须）
1. **功能同步：投票（含可见范围）** —— 数据层（contract task-33）+ UI（design task-34 规格→实现）+ 导航/入口/仪表盘。
2. **功能同步：名单三分类**（班委/团员/全班；点名入口保留）—— 复用 `class_league` RPC；现 RosterTab 只有 名册/点名。
3. **功能同步：电费自然周期同期比较 + 13/16 隐藏 24h** —— 纯逻辑（task-33）+ UI 徽标/档位（design）。
4. **真实背景模糊底栏**（§3）—— Lead。
5. **全局状态一致性**：加载/空/错误/离线/权限五态统一组件；所有网络页可重试、可下拉刷新、有缓存标识。
6. **危险操作**：删除/清空/退出/重置 → 二次确认 + 可撤销（Snackbar action）。
7. **返回与手势**：所有二级页支持系统返回与预测性返回；弹层先关弹层；避免误退出。

### P1
8. 动效统一到 M3 motionScheme（spatial/effects/default/fast/slow）。
9. 无障碍：触控 ≥48dp、contentDescription、字号 1.3 不截断、TalkBack 顺序。
10. 自适应：手机/平板/横屏；平板导航 rail 或双栏（底栏在 ≥840dp 是否需要 rail，评估）。
11. 性能：动画 phase deferral 审计、Lazy 列表 key、图片 downscale、启动耗时（baseline profile）。
12. 表单校验与草稿：投票发起/反馈/体测录入离开保留草稿或提示。
13. 通知/提醒：权限被拒的解释与跳转；通知渠道说明。
14. 课表：真实数据/空态/错误态；滑动切周与系统返回冲突；导出失败提示。
15. PPT：下载/打开/分享失败兜底；视频单独下载入口可发现性。
16. 诊断：异常关联 AI 行折叠可读性；上报成功/失败反馈。

### P2
17. 主题/壁纸取色、图标一致性、启动图。
18. 桌面/平板键盘焦点、外接鼠标 hover 态。
19. 数据导出/导入（userdata JSON）UI 化。
20. 无障碍朗读文案打磨、国际化（暂不做多语言）。

## 5. 验收方式

- 每个 P0 项：单测（逻辑）+ 预览/截图（浅深）+ E2E 断言 + 真机手测（用户）。
- 全局：全量单测 ≥ 现有 994/0 且新增覆盖；快速 E2E 38+ 全绿；Release 冷启动/签名/矩阵复验。
- 文档：本文件 + `V4_UX_AUDIT.md`（design）+ `V4_NEW_FEATURES_UI.md`（design）+ `VOTE_LEAGUE_POWER_IMPL.md`（contract）+ REPORT_ROUND*。

---

## 附：真实背景模糊落地（2026-11-12，v4.33.0）

- 采用 Haze 1.7.3：`HazeState` 由 `RootTabs` 持有，`CtTabPager` 挂 `hazeSource`，底部 `CtFloatingDock` 挂 `hazeEffect` + `HazeDefaults.style(backgroundColor=surface 55%, blurRadius=24dp, noiseFactor=0.02)`。
- 设备实测（ct-api37，swiftshader）：dock 区域采样下方卡片色并模糊，文字不透出；E2E 43/0。
- 旧设备（不支持 RenderEffect）自动降级半透明；页面卡片玻璃化仍为半透明+渐变（性能取舍）。
