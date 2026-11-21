# `NOTICE_DRAG_TT_IMPL.md` — 通知附件预览 + 全部工具拖动排序 + 课表空数据文案

> task-14 实现说明（工程 `<项目根>`，包 `com.furina.classtools`）
> 契约参考：`web/AGENTS.md` §4b-10（拖动排序 + prefs.toolOrder）、`mp-class-tools/pages/all/all.js`、
> `mp-class-tools/utils/tools.js`/`cloud.js`（toolOrder 归一/同步）、`docs/api/USERDATA_V6.md`（prefs section）、
> `docs/api/NOTICES.md`（items 结构）、`docs/api/ENDPOINTS.md` E24（课表快照）。

---

## 1. 文件清单

| 文件 | 变更 |
|---|---|
| `ui/screens/notices/NoticeAttachmentPreview.kt` | **新增**：`NoticeRawAttachmentLoader`、`ApiNoticeRawAttachmentLoader`、`NoticeAttachmentParser`（纯解析）、`NoticeImageDecoder`（Bitmap 采样解码）、`NoticeAttachmentThumb`/`AttachmentFallback`/`NoticeImageFullDialog` |
| `ui/screens/notices/NoticesScreen.kt` | 新增 `rawAttachmentLoader` 参数；详情里 `img` 附件显示缩略图 + 点击大图；人工附件图片同样支持；解码失败/无 data URI →「无法预览 · 暂不支持预览」；未知 kind 统一「暂不支持预览」 |
| `ui/screens/all/ToolOrderLogic.kt` | **新增**：`ToolOrderLogic`（归一/迁移/移动/2 列网格命中/是否需写入）+ `ToolOrderPrefs`（`prefs.toolOrder` + `at.prefs` 编解码） |
| `ui/screens/all/AllToolsScreen.kt` | 重写为可拖动排序：长按 350ms、ghost 跟手、2 列网格「行 + x 中线」插入、边缘自动滚动、松手落位写 prefs.toolOrder、拖后抑制点击；保留点击打开与工具目录 |
| `data/timetable/TimetableModels.kt` | 新增 `TtFailureKind{NETWORK,NO_TOKEN,EMPTY,FORMAT}`；`TtLoadResult.Failure(message, kind)`（默认 FORMAT 兼容旧调用） |
| `data/timetable/TimetableRepository.kt` | 新增 `TtParseOutcome{Success,Empty,Format}` + `parse()` 三态解析；`ApiError` → 失败语义映射（TokenInvalid/Config→NO_TOKEN，Network/Http→NETWORK，Parse→FORMAT）；空响应/c 为空 → EMPTY |
| `ui/screens/timetable/TimetableViewScreen.kt` | 错误态按 `kind` 显示三态文案；新增预览参数 `initialErrorKind` |
| `ui/preview/NoticeAttachmentDragPreviews.kt` | **新增**：通知图片预览（浅/深）、全部工具拖动中态（浅/深）4 张预览 |
| `ui/preview/TimetablePowerPreviews.kt` | 新增「23b 课表 空数据文案」「23c 课表 网络异常」预览 |
| `app/src/test/.../ui/screens/all/ToolOrderLogicTest.kt` | **新增** 13 用例（归一/迁移/移动/网格命中/prefs 编解码/仅实际修改时写） |
| `app/src/test/.../ui/screens/notices/NoticeAttachmentParserTest.kt` | **新增** 10 用例（行解析/图片序号/人工附件/data URI/MIME/base64/采样） |
| `app/src/test/.../data/timetable/TimetableRepositoryTest.kt` | 更新 + 新增三态/失败语义 4 用例 |

---

## 2. 公开签名 / 接线点

### 2.1 通知附件预览

```kotlin
// UI 参数（默认 null = 降级为元信息占位，不影响现有接线）
fun NoticesScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,
    noticesRepository: NoticesRepository?,
    rawAttachmentLoader: NoticeRawAttachmentLoader? = null,
    modifier: Modifier = Modifier,
    ...
)

/** 按需加载原始附件（rowId + 序号 → data URI）；可注入假实现做预览/测试。 */
fun interface NoticeRawAttachmentLoader {
    suspend fun load(rowId: String, ordinal: Int, manual: Boolean): String?
}

/** 网络实现：GET notices?select=id,body,attachments&id=eq.<rowId>，解析 items/attachments 取 data URI。 */
class ApiNoticeRawAttachmentLoader(api: ApiClient, json: Json = NetJson) : NoticeRawAttachmentLoader
```

MainNavHost 接线（Lead）：

```kotlin
"nt" -> NoticesScreen(
    onBack = { navController.popBackStack() },
    session = session,
    noticesRepository = container.noticesRepository,
    marksRepository = container.marksRepository,
    rawAttachmentLoader = ApiNoticeRawAttachmentLoader(container.apiClient),  // 新增一行
)
```

内存口径：
- 列表/通知模型仍只保留元信息（`NoticeRow.body` 在仓库构建时已清空，不常驻 base64）；
- 详情打开时按需拉单行 → **缩略图 480px / 大图 1800px** 采样解码（RGB_565）；用完随详情关闭释放；
- 解码失败、非 base64 data URI、远程 URL、未接线 loader → 「无法预览 · 暂不支持预览」占位，不崩、不联网图库。

### 2.2 全部工具拖动排序

```kotlin
fun AllToolsScreen(
    onOpenTool: (AppTool) -> Unit,
    modifier: Modifier = Modifier,
    userDataRepository: UserDataRepository? = null,       // 传入则屏幕直接写 prefs.toolOrder
    onToolOrderChange: ((List<String>) -> Unit)? = null,  // 或交给 Lead 写
    initialOrder: List<String> = emptyList(),
    skipRestore: Boolean = false,
    previewDraggingToolId: String? = null,                // 预览/测试注入拖动中态
    previewDragOffset: Offset = Offset(190f, 340f),
)
```

MainNavHost 接线（Lead 只需加参数）：

```kotlin
"all" -> AllToolsScreen(
    onOpenTool = { tool -> navController.navigate(Routes.tool(tool.id)) },
    userDataRepository = container.userDataRepository,
    // 或：onToolOrderChange = { order -> scope.launch { /* ToolOrderPrefs.apply(...) */ } }
)
```

`prefs.toolOrder` 写入语义（与 mp `cloud.js` 一致）：

| 规则 | 实现 |
|---|---|
| 仅用户实际拖动后写 | 松手时 `ToolOrderLogic.shouldPersist(拖动前, 拖动后)` 为 true 才写 |
| 无基线不盲写 | 打开页面只读 `prefs.toolOrder`；无基线显示自然顺序，不写回 |
| 默认自然顺序不写 | 顺序未变化（含首次打开）不写 |
| 时间戳 | `at.prefs = max(旧 at.prefs, now)`（等价 mp 本机 `toolOrderSetAt` 语义，供云端 LWW） |
| 不新增顶层键 | 只改 `prefs.toolOrder` 与 `at.prefs`，其余 section 原样保留（`ToolOrderPrefs.apply`） |
| 归一 | 合法工具 id 去重；未知/新增工具按 ToolCatalog 自然顺序补末尾（同 mp `normalizeToolOrder`） |

### 2.3 课表三态

| `TtFailureKind` | 触发 | UI 标题 / 提示 |
|---|---|---|
| `NETWORK` | DNS/超时/连接失败、HTTP 5xx/4xx（非 401/403） | 「网络异常，点击重试」/ 实际错误信息 |
| `NO_TOKEN` | 401/403（token 失效）、云端未配置 | 「暂无课表数据」/「登录后可查看教务同步的班级课表」 |
| `EMPTY` | 请求成功但空响应 / `c=[]` / body 缺失 | 「暂无课表数据」/「教务快照为空，稍后刷新试试」 |
| `FORMAT` | 坏 JSON / body 非对象 / c 非数组 / 课程全部非法 | 「课表数据格式异常」/「快照内容不符合契约，请联系管理员」 |

有本机缓存时仍优先返回缓存（`fromCache=true`），不显示错误态。

---

## 3. 预览
- `NoticeAttachmentDragPreviews.kt`：40 通知附件图片预览（浅）、41（深）、42 全部工具拖动中（浅）、43（深）。
- `TimetablePowerPreviews.kt`：23b 课表空数据文案、23c 课表网络异常。

## 4. 单测

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT='C:\AndroidToolchain\sdk'
cd <项目根>
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

本轮新增/更新用例（详见任务汇报中的数字）：
- `ToolOrderLogicTest`：归一/补全、旧全量顺序迁移、moveToIndex、2 列网格命中（行 + x 中线/越界）、`shouldPersist`（仅实际修改）、prefs 读写/`at.prefs` 提升/不降级/不新增顶层键/JSON 往返；
- `NoticeAttachmentParserTest`：notices 行解析（数组/对象/坏数据）、自动通知第 N 张图、人工附件下标、data URI 识别/MIME/base64 解码（PNG magic + 尺寸）、采样倍数；
- `TimetableRepositoryTest`：三态解析（空/格式/成功）、空 c → EMPTY、401 → NO_TOKEN、网络异常 → NETWORK、坏 JSON → FORMAT。

## 5. 遗留项
1. **通知附件**：仅支持内联 `data:` 图片；远程 URL 图片、文件/PDF、未知 kind 仍为占位（后续可接 WebView/系统分享）；
   预览是「按需二次请求」单行原始数据（列表阶段不保留 base64），因此需注意弱网下的大图加载时延（已有 loading 态）。
2. **拖动排序**：只做「全部工具目录顺序」；首页常用工具（homeTools）顺序语义未在本轮改变（Lead 后续可让 HomeScreen 按 toolOrder 排序）。
3. **课表**：`EMPTY` 与 `NO_TOKEN` 共用「暂无课表数据」标题（提示语不同）；若产品希望未登录时直接跳身份页，由 Lead 在 401 回调统一处理。
4. **toolOrderSetAt 本机字段**：Android 不落本机 `toolOrderSetAt`，以「实际拖动落位」为写入门槛（语义等价）；远端 `prefs.toolOrder` + `at.prefs` 即基线。
5. 拖动为 Compose 手势实现，未接无障碍自定义动作（TalkBack 用户可点击打开工具；排序建议后续加「移动上/下移」无障碍动作）。
