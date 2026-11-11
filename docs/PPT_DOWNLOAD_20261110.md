# 「演示」v2.0 · 安卓下载中心（兼容性处理 + 多端下载）实现说明（2026-11-10）

> 契约唯一真源：`web/ppt/CONTRACT.md` **§12**（v2.0 功能重定义，2026-11-10 22:05）；
> 版本：**v3.5.0 / versionCode 11**；仅改 `android-class-tools/**`，未新增依赖、未 git/部署。
>
> **新定位**：上传 PPT → 转换机自动做**不降质**的兼容性处理（`.ppt→.pptx`、包修复、字体嵌入、PDF 副本、外链媒体如实告警）
> → 网页/小程序/安卓都能**下载处理后的文件**，用 PowerPoint/WPS/PDF 查看器打开。在线播放降级为旧 deck 的可选「预览」入口。

## 1. 安卓端职责与交互

### 1.1 列表（下载中心入口）

- 卡片点击 = **打开「下载详情」**（不再直接进播放器）；上传/轮询/删除/重试/离线提示保持 v1.2 行为；
- 有兼容处理产物的 deck 显示小标签「兼容版」/「PDF」（不写长句）；
- 就绪卡片不再显示封面「▶」（避免暗示在线播放）；「继续」预览徽标保留（旧 deck 预览用）；
- 标签数据来源：列表查询用 PostgREST **jsonb 路径**只取 `manifest->files` 轻量子对象（不拉完整 manifest，
  避免污染 §4.4 轻字段约定），解析出 `compat`/`pdf` 是否存在。

### 1.2 下载详情页（新增路由 `pptDetail/{deckId}`）

- 拉单 deck：`GET ppt_decks?select=manifest,title,status,progress,error,owner_name,slide_count,step_count,aspect,cover_url,source_name,source_key,source_size,converted_at&id=eq.<id>`
  （契约 §12.4.1 要求拉 manifest；这里额外取状态/进度用于「完成前不显示下载」）；
- **三行下载**（固定顺序，显示大小）：
  - `兼容版 PPTX`（size；扩展名 PPTX）
  - `PDF`（pages 页 · size）
  - `原始文件`（size）
- 每行「下载」→ `DownloadManager` 入队（系统下载通知）→ 轮询进度（百分比）→ 完成自动 `ACTION_VIEW`（FileProvider）
  用 PowerPoint/WPS/PDF 查看器打开；完成后该行变为「打开 / 分享」（分享走 `ACTION_SEND`）；
- `manifest.compat.warnings` → 橙色「兼容提示」区**逐条**展示（外链媒体未随文件携带、字体未嵌入等，来自转换机，客户端不删改内容）；
- 「在线预览」次级按钮：仅当该 deck 仍有旧版 `slides` 或 `manifest.video` 时显示（旧播放器 v1.1/v1.2 完整保留）；
- **旧 deck（无 `files`）**：「兼容版未生成」+「在线预览」（有旧预览时）+「重新处理」（`retryDeck` 触发服务端重转 compat）
  + 原始文件下载（旧 `manifest.src` 仍可下载）；
- **状态非 ready**：只显示状态与进度（转换中显示进度条、失败显示 error +「重新处理」），完成前不显示下载。

## 2. 代码结构

```
data/ppt/
  PptModels.kt          + PptFileInfo / PptFiles / PptCompat；PptManifest.files/compat + hasCompatFile/hasPdfFile/isLegacyDeck/hasPreview；
                        PptDeck.hasCompatFiles/hasPdfFile（列表标签）；PptDeckDetail.status/progress/error/ownerName/sourceSize
  PptParsers.kt         + parseFiles/parseFileInfo/parseCompat（容错：缺字段默认、坏项跳过、warning 去重/截断/上限 20）；
                        parseDeckRow 解析 jsonb 子路径 files（也兼容完整 manifest 行）；parseDeckDetail 补状态字段
  PptRepository.kt      + PptDetailResult + loadDownloadDetail()（下载详情）；loadPlayer 改为基于它（旧预览：slides 或 video 才可播）；
                        LIST_SELECT 增加 manifest->files jsonb 子路径；DETAIL_SELECT
  PptFileDownloader.kt  + AndroidPptFileDownloader（DownloadManager 入队/轮询进度 Flow/取消；同名文件去重）；
                        下载保存到应用外部下载目录（不申请存储权限，红线），FileProvider 打开/分享
features/ppt/
  PptDownloadLogic.kt   纯逻辑：三行下载 rows（大小/文件名/MIME/URL）、旧 deck 判定、兼容提示清洗、列表标签、
                        文件名清洗/去重/扩展名、查看器 MIME、状态文案（全部 JVM 单测）
ui/screens/ppt/
  PptDownloadDetailScreen.kt  下载详情页（live + 无副作用 Content 供 @Preview）
  PptFileIntents.kt           FileProvider Uri → ACTION_VIEW 打开 / ACTION_SEND 分享（无查看器不崩溃）
ui/preview/PptPreviews.kt    + 67 下载详情（三行+大小）、68 兼容提示、69 旧 deck；列表预览加「兼容版/PDF」标签
```

注册：`Routes.PPT_DETAIL`（`pptDetail/{deckId}`）+ `MainNavHost` 新 composition；列表卡片点击 → 该路由；
`AppContainer.pptFileDownloader` 注入；FileProvider `res/xml/ct_file_paths.xml` 增加 `external-files-path name="ppt_downloads" path="Download/"`。

## 3. 契约 §12 对应表

| 契约 | Android 实现 |
|---|---|
| §12.2 `manifest.files`（compat/pdf/source） | `PptFileInfo` 容错解析（`s` 必填，size/sha256/ext/name/pages 默认值）；`files` 全空 → 视为旧 deck |
| §12.2 `manifest.compat.warnings` | `PptCompat.warnings`（去重、单条 ≤300 字、≤20 条），详情页橙色逐条；`fonts_embedded/converted_from` 解析保留 |
| §12.4.1 列表标签 | 列表 jsonb 子路径 `manifest->files` → `hasCompatFiles/hasPdfFile` → 「兼容版」/「PDF」小标签 |
| §12.4.1 点击卡片=详情 | 列表 `onOpenDetail` → `Routes.PPT_DETAIL` |
| §12.4.1 三行下载+大小 | `PptDownloadLogic.rows`；大小用 `PptLimits.formatBytes`；PDF 显示页数 |
| §12.4.1 在线预览仅旧版内容 | `PptDownloadLogic.canPreview`（slides 非空或 video 存在）；旧播放器路由保留 |
| §12.4.1 旧 deck | 无 files → 「兼容版未生成」+「在线预览」+「重新处理」+ 原始下载（`manifest.src`） |
| §12.4.1 状态非 ready | 详情页只显示状态/进度/error；失败/排队可「重新处理」 |
| §12.4.2 安卓下载方式 | `DownloadManager`（系统下载通知）+ 轮询进度 + 完成 `ACTION_VIEW`（FileProvider）+ 分享 Intent；不整文件进内存 |
| §12.6 红线 | 客户端只读 manifest/OSS；兼容处理由转换机完成，安卓不做任何转码/删内容；旧播放器代码不删 |

## 4. 已知限制 / 说明（如实）

1. **下载保存位置**：受本项目红线「不申请存储读取权限」+ Android scoped storage 限制，`DownloadManager` 保存到
   **应用外部下载目录**（`Android/data/com.furina.classtools/files/Download/`），系统下载通知可见，完成后通过 FileProvider
   打开/分享；**不写入公共「下载」目录**（写入公共目录需存储权限，与红线冲突）。用户可用查看器/WPS 打开或分享到聊天/网盘。
2. **打开查看器**：需设备已安装 PowerPoint/WPS/PDF 查看器；无查看器时提示「已下载，未找到可打开的应用」，不崩溃。
3. **在线预览**：仅旧 deck（有 `slides`/`video`）；compat 模式新 deck 无预览入口（符合 §12.2「不再生成分步图」）。
4. **`files.source` 可选**：旧 deck 回退旧 `manifest.src`；两者都没有时「原始文件」行显示「未生成」。
5. **文件名**：优先服务端 `files.*.name`，缺失时按标题+扩展名兜底；同名文件自动加 ` (1)` 后缀不覆盖。
6. **下载进度**：系统 DownloadManager 轮询（600ms），大文件不读入内存；取消能力保留在下载器接口（UI 未露出取消按钮，系统通知可取消）。
7. **列表标签依赖 jsonb 路径查询**：`manifest->files` 已在真实网关匿名探测 200（语法可用）；若某网关不支持，解析失败时标签不显示、其余功能不受影响。
8. 版本 3.5.0/versionCode 11；旧 v1.1/v1.2 播放器（分步/原样视频/续播/模式偏好）代码全部保留，仅入口降级为详情页「在线预览」。

## 5. 测试与构建证据（2026-11-10）

### 5.1 单元测试

```
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat :app:testDebugUnitTest --console=plain
BUILD SUCCESSFUL
全量 982 tests / 0 failures / 0 errors / 0 skipped（79 suites）
```

v2.0 新增 **28 项**（原有 954 基线无回退）：

| 测试类 | 新增 | 覆盖 |
|---|---|---|
| `features/ppt/PptDownloadLogicTest`（新） | 16 | 三行下载/大小/MIME/URL、缺失文件、旧 deck 回退原始、文件名清洗/补扩展名/去重、兼容提示清洗、列表标签、状态文案、查看器 MIME |
| `data/ppt/PptParsersTest` | 5 | manifest.files/compat 完整与容错、warnings 去重/上限、列表 jsonb files 标签、详情状态字段 |
| `data/ppt/PptRepositoryTest` | 7 | `loadDownloadDetail`（files/状态/请求字段）、非 ready、token 失效、空 id、compat deck 不可预览、旧 deck 仍可预览 |

### 5.2 Release 构建

```
$env:JAVA_HOME=''C:\AndroidToolchain\jdk17''; $env:ANDROID_HOME=''C:\AndroidToolchain\sdk''
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat assembleRelease --console=plain
BUILD SUCCESSFUL in 1m 45s（R8 + 资源压缩 + lintVitalRelease 通过）
```

| 项 | 结果 |
|---|---|
| 构建产物 | `app/build/outputs/apk/release/app-release.apk` = **2,741,438 B** |
| 交付副本 | `artifacts/classtools-3.5.0-release-signed.apk` |
| APK SHA-256 | **`C45070ACB6C91745F27DD28D5C28075B64688999A50B1F4DFB5D92406B947647`** |
| 签名 | `apksigner verify --verbose --print-certs` → **Verifies**；APK Signature Scheme v2 = true；1 signer（同一 keystore） |
| 新增依赖 | **无**（DownloadManager/FileProvider 为系统/AndroidX 现有能力） |

### 5.3 Compose @Preview 渲图

- `67 演示下载详情 · 三行+大小`（兼容版 PPTX / PDF 16 页 / 原始文件，各自大小）
- `68 演示下载详情 · 兼容提示`（橙色逐条：外链媒体、字体许可）
- `69 演示下载详情 · 旧 deck`（兼容版未生成 + 在线预览 + 重新处理 + 原始文件下载）
- 更新列表预览：就绪卡片显示「兼容版」「PDF」标签
- 渲染：README 的 `android preview render`（干净副本），PNG 放 `android-class-tools/artifacts/`。

### 5.4 最终记录（2026-11-10 22:21）

| 验收项 | 实际命令/证据 | 结果 |
|---|---|---|
| 单测全绿 | `.\gradlew.bat :app:testDebugUnitTest --console=plain` | **BUILD SUCCESSFUL；982 tests / 0 failures / 0 errors / 0 skipped（79 suites）** |
| Release 构建 | `.\gradlew.bat assembleRelease --console=plain` | **BUILD SUCCESSFUL in 1m45s**；APK **2,741,438 B**；`artifacts/classtools-3.5.0-release-signed.apk`（SHA-256 `C45070AC…B947647`） |
| 签名 | `apksigner verify --verbose --print-certs` | Verifies；V2 = true；1 signer（同一证书） |
| 预览渲图 | `android preview render`（10 个 Ppt FQN，干净副本） | 10 张 PNG 全部渲出；新增下载详情/兼容提示/旧 deck 已人工查看正常 |
| 版本 | `app/build.gradle.kts` | versionName 3.5.0 / versionCode 11 |
| 依赖 | `libs.versions.toml` 未改 | 无新增依赖 |

预览 PNG（`android-class-tools/artifacts/`）：
- `ppt-download-detail.png`（下载详情：三行 + 大小 17.6MB/6.0MB/17.0MB + 兼容版/PDF 标签）
- `ppt-download-warnings.png`（橙色「兼容提示」逐条：外链媒体、字体许可）
- `ppt-download-legacy.png`（旧 deck：兼容版未生成 + 在线预览 + 重新处理 + 原始文件下载）
- `ppt-list-states.png`（列表「兼容版」「PDF」标签；上传/排队/转换中/失败状态）
- `ppt-list-empty.png`、`ppt-player-169/43/dark.png`、`ppt-player-video-mode.png`、`ppt-player-steps-hotspot.png`（保留均正常）
原始输出目录：`artifacts/previews_ppt/`。

## 6. v2.3 增量（2026-11-11）：视频/音频单独下载

> 契约 §12.8：教室电脑/旧 PowerPoint/WPS 打不开内嵌视频时，可把视频/音频单独下载到本机用任意播放器播放。
> 版本 **v3.6.0 / versionCode 12**；无新增依赖。

### 6.1 数据格式（manifest.media_files）

```jsonc
"media_files": [
  {"s":"media/xxx_视频1_第7页.mp4", "name":"xxx_视频1_第7页.mp4", "size":20158796, "sha256":"…",
   "kind":"video", "dur":150.92, "w":1024, "h":576, "slides":[7], "src":"media1.mp4"}
]
```

Android 容错解析（`PptParsers.parseMediaFiles`）：

- 缺失/非数组 → 空列表（旧 deck 不显示分组，不报错）；
- `s` 为空的坏项跳过；`kind` 未知时按文件名/扩展名推断（m4a/mp3/aac/wav… → 音频，其它 → 视频）；
- `size/dur/w/h` 非法 → 0；`slides` 只保留 0..10000 整数、去重升序、单媒体上限 20；
- 条数上限 50（转换机上限 20，客户端防坏数据）；`mediaUrl = manifest.base + s`（绝对 http(s) 直连）。

### 6.2 详情页 UI

- 在「兼容版 PPTX / PDF / 原始文件」三行**之后**新增分组 **「视频单独下载」**（`manifest.media_files` 为空时不显示）；
- 分组标题右侧显示计数（`2 个视频 · 1 个音频`）；
- 每行：`视频1 · 第 7 页`（同类型从 1 编号；多页 `第7、12页`）+ 副标题 `02:30 · 1024×576 · 19.2 MB`
  （音频只显示时长；未知部分省略）+ `下载` 按钮；
- 点下载 → 复用 `AndroidPptFileDownloader`（DownloadManager，系统下载通知 + 进度百分比）；
  完成自动 `ACTION_VIEW`（MIME：mp4→`video/mp4`、mov→`video/quicktime`、m4a→`audio/mp4` 等，交给系统播放器/WPS），
  行变「打开 / 分享」（`ACTION_SEND`）；失败 toast、可重试；按钮点按 ≥48dp，图标带 `contentDescription`；
- 文件名优先 manifest `name`，缺失按 `<标题>_视频N_第X页.mp4` / `_音频N_第X页.m4a` 兜底（清洗非法字符、补扩展名、同名不覆盖）；
- 旧 deck（无 `media_files`）不显示该分组，兼容 v2.0 行为。

### 6.3 代码结构

| 文件 | 内容 |
|---|---|
| `data/ppt/PptModels.kt` | `PptMediaFile` + `PptManifest.mediaFiles/hasMediaFiles/mediaUrl` |
| `data/ppt/PptParsers.kt` | `parseMediaFiles`（容错解析 + 上限） |
| `features/ppt/PptMediaDownloadLogic.kt` | 纯逻辑：行模型（`视频N · 第X页`/时长/分辨率/大小/MIME/文件名/URL）、分组计数 |
| `ui/screens/ppt/PptDownloadDetailScreen.kt` | 下载详情页新增「视频单独下载」分组；下载状态统一为 String-key map（文件行 + 媒体行） |
| `ui/preview/PptPreviews.kt` | 详情预览 fixture 加入 2 视频 + 1 音频（分组可见） |

### 6.4 测试与构建证据（2026-11-11）

```
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'; $env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat :app:testDebugUnitTest --console=plain
BUILD SUCCESSFUL
全量 994 tests / 0 failures / 0 errors / 0 skipped（80 suites）
```

v2.3 新增 **12 项**（原 982 基线无回退）：
- `features/ppt/PptMediaDownloadLogicTest`（新，9）：行模型/视频音频编号与标题/时长分辨率/大小/MIME/URL/绝对地址/空列表/页号去重与 0 兜底/文件名清洗补扩展名兜底/分组计数；
- `data/ppt/PptParsersTest` +3：media_files 完整解析、缺失/非法/未知 kind 推断、条数与页数上限。

### 6.5 Release 构建

```
$env:JAVA_HOME=''C:\AndroidToolchain\jdk17''; $env:ANDROID_HOME=''C:\AndroidToolchain\sdk''
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
.\gradlew.bat assembleRelease --console=plain
BUILD SUCCESSFUL in 2m 20s（R8 + 资源压缩 + lintVitalRelease 通过）
```

| 项 | 结果 |
|---|---|
| 构建产物 | `app/build/outputs/apk/release/app-release.apk` = **2,757,826 B** |
| 交付副本 | `artifacts/classtools-3.6.0-release-signed.apk` |
| APK SHA-256 | **`514527F08641889EF83FCC9CCEED55E2979FF147D50D146C828885A5BF615E1E`** |
| 签名 | `apksigner verify --verbose --print-certs` → **Verifies**；APK Signature Scheme v2 = true；1 signer（同一 keystore） |
| 版本 | versionName **3.6.0** / versionCode **12** |
| 新增依赖 | **无**（复用 DownloadManager / FileProvider / 系统播放器） |

### 6.6 预览证据（2026-11-11）

- `android preview render`（10 个 Ppt FQN，干净副本）全部渲出；`artifacts/ppt-download-detail.png` 已人工查看：
  「视频单独下载」分组显示 `视频1 · 第7页 02:30 · 1024×576 · 19.2 MB`、`视频2 · 第12页 03:45 · 1280×720 · 13.0 MB`、
  `音频1 · 第3页 00:45 · 1.0 MB`，标题右侧「2 个视频 · 1 个音频」；三行文件下载与「在线预览」保持 v2.0 行为。
- 另：`ppt-download-warnings.png`（兼容提示）、`ppt-download-legacy.png`（旧 deck）、`ppt-list-states.png`（列表标签）等保留正常。
- 原始输出目录：`artifacts/previews_ppt/`。