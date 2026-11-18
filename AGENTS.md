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
- 本机计划任务 `ClassToolsRepoAutoSync` 每 10 分钟兜底：工作区有改动且空闲 ≥5 分钟时自动提交并推双端。
  但 agent 仍应在完成改动时**主动执行上面命令**，不要依赖兜底。
- 核对状态：`sync-repos.cmd --check`（三行 SHA 一致才算完成）。

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
