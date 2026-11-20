# AGENTS.md — 安卓端仓库（class-tools-android）

本仓库是「班级工具站」的 Android 原生客户端（Kotlin + Compose）。
五个端各一个仓库：**site（网页/部署）· android（本仓）· mp（小程序）· harmony · windows**。
**不要再按功能拆新仓库**；功能改动落在所属端的仓库里。

## 提交规则（硬性，2026-11-18 用户要求）

任何改动完成后，在仓库根目录执行：

```cmd
sync-repos.cmd "feat: 说明本次改了什么"
```

- 它会做安全检查 → `git add -A` → 提交 → **同时推 GitHub `origin` + Gitee `gitee`** → 核对两端 SHA。
- 只推一个远端 / 不写说明 / 写「更新」「修改」「同步」这类笼统说明 = **未完成交付**。
- 两个远端都是**私有仓库**，不要改为公开。
- **没有计划任务、没有后台守护**（用户 2026-11-18 要求）：改完必须**主动执行**上面命令；
  不要等定时任务、不要依赖任何兜底。
- 核对状态：`sync-repos.cmd --check`（三行 SHA 一致才算完成）。

## 发版规则（硬性，2026-11-20 用户要求「以后有更新都自动推送」）

**任何用户可见的改动完成后，必须立刻自己发版到更新渠道，不要等用户再提一遍。**

顺序固定：先按上面「提交规则」推完两个远端，再在仓库根目录执行：

```cmd
.\gradlew.bat :app:assembleRelease --project-prop "ct.updateChannel=beta" --project-prop "ct.updateNotes=一句话说明本次改了什么"
```

- `assembleRelease` 由 `app/build.gradle.kts` 的 `publishUpdateChannel` 任务**自动**调用
  `web/deploy/android_release.py`：上传 APK + `latest.json` 到 ECS 与 OSS，并回读校验 sha256。
- 发布前先确认版本号已递增（`versionCode` +1、`versionName` 升一位，如 `1.0.0-beta14`），
  否则渠道清单不会被认为是新版本。
- 只用 `--project-prop` 传参（`-Pct.xxx=…` 在 Windows PowerShell 下会被拆成任务名而失败）。
- **发布成功判据**：日志出现 `ECS_ANDROID_OK … <sha256>` 与两行 `latest.json HTTP 200`，
  再回读校验（应为新版本号）：

  ```powershell
  Invoke-RestMethod -Uri 'http://121.196.47.2/android/latest.json' | ConvertTo-Json -Depth 4
  ```

- 用户明确说「这次不发版」时才可跳过；否则**没发版 = 未完成交付**。
- 仍然**没有计划任务、没有后台守护**：每次改完主动执行，不要等定时任务。
- 只想编译不发版：加 `--project-prop "ct.skipUpdateChannel=true"`（仅限本机调试）。

## 禁止

- 提交密钥 / 签名 / 证书：`*.jks`、`*.keystore`、`keystore.properties`、`local.properties`、`*.pem`、`*.key`、`*.env`。
- 提交构建产物：`build/`、`.gradle/`、`artifacts/`、`*.apk`、`*.aab`（`.gitignore` 已排除，不要 `git add -f`）。
- 改动其他端仓库；跨端契约改动先看 `web/AGENTS.md` 与对应 `CONTRACT.md`。

## 验证

```cmd
.\gradlew.bat testDebugUnitTest
```

构建/签名说明见 `README.md` 与 `docs/`。测试一律使用测试身份（学号 9999 段 / 3260799999 测试账号），
不要登录真实同学或管理员账号。

## 目录提示

- `app/src/main/kotlin/…` 源码；`app/src/test/…` 单测；`docs/` 验收与报告。
- 第二个工作副本 `..\android-class-tools-notify` 与本仓共用同一远端，分支为 `feat/background-notify`；
  只在明确需要时使用，主开发在 master。
