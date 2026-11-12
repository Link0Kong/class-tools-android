# Round 50 报告 · v4.39.0（2026-11-12）

> v4 深度优化 性能维护：应用级 Baseline Profile 刷新到最新代码。版本 **v4.39.0**（versionCode 55）。

## 1. 背景与改动

- v4.22 首次接入应用级 Baseline Profile（18,166 条规则）；此后 17 个版本新增了实时活动、无障碍、模糊等大量代码/依赖，旧 profile 已含 526 条失效规则。
- 本轮在最新代码上重新执行 `:app:generateBaselineProfile`（nonMinifiedRelease + baseline-profile 采集，API 37 模拟器）：
  - 规则：**18,166 → 17,649**（清理 526 条失效、新增 9 条、97% 保留）；
  - 二进制 `assets/dexopt/baseline.prof`：**11,885B → 12,327B**（含 startup profile）；
  - 生成产物已签入 `app/src/release/generated/baselineProfiles/`，随 APK 下发并由 ProfileInstaller 首启安装。

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 冷启动（强制 `speed-profile` 编译，模拟后台 AOT 完成） | 5 次：287/282/271/269/288ms，**中位 282ms** |
| Release 常规两次冷启动 | **0.30s / 0.28s** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（974.1s）**；导航 10/10；logcat 0 FATAL/ANR |
| Release v4.39.0 | **3,026,717 B** / SHA-256 `8855DA3C8117F5EB8C0985BA1E5C850F5238FB53089EE42506274F2CF2D40738`（vc55）；logcat 0 FATAL/ANR |

## 3. 说明

- 本次未做 v4.38 vs v4.39 的同机 A/B（同一 emulator 状态波动大）；收益主要体现在清理失效规则与覆盖新代码，profile 机制与收益说明见 `docs/BASELINE_PROFILE.md`。
- 冷启动绝对数值随模拟器负载波动，仅作同环境参考；真机收益依赖后台编译时机。

## 4. 待办

- TalkBack 真机逐项走查（静态审计 12 屏 0 未标注）
- ColorOS 16 真机验证流体云呈现
- 页面级玻璃化（性能取舍）
