package com.furina.classtools.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新功能纯逻辑单测（JVM，无 android.*）：
 * JSON 解析容错 / 版本比较 / URL fallback 链 / 冷启动自动检查闸门 / 强制更新规则 /
 * 下载状态机 / 进度 / sha256 校验 / 文件名 / 安装动作判定。
 */
class UpdateLogicTest {

    // ---------------------------------------------------------------- JSON 解析

    private val fullJson = """
        {
          "versionCode": 79,
          "versionName": "4.57.0-test1",
          "apkUrl": "http://121.196.47.2/android/classtools-4.57.0-test1.apk",
          "notes": "1. 新增检查更新\n2. 测试版本 1",
          "force": false,
          "channel": "test",
          "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        }
    """.trimIndent()

    @Test
    fun `parse 完整字段`() {
        val info = UpdateJsonParser.parse(fullJson)
        assertNotNull(info)
        info!!
        assertEquals(79, info.versionCode)
        assertEquals("4.57.0-test1", info.versionName)
        assertEquals("http://121.196.47.2/android/classtools-4.57.0-test1.apk", info.apkUrl)
        assertEquals("1. 新增检查更新\n2. 测试版本 1", info.notes)
        assertFalse(info.force)
        assertEquals("test", info.channel)
        assertTrue(info.hasSha256)
    }

    @Test
    fun `parse 容错 versionCode 字符串与 force 0-1`() {
        val info = UpdateJsonParser.parse(
            """{"versionCode":"80","apkUrl":"https://example.com/a.apk","force":1}""",
        )
        assertNotNull(info)
        assertEquals(80, info!!.versionCode)
        assertTrue(info.force)
        assertEquals("80", info.versionName) // 缺省用 versionCode 兜底
        assertEquals(UpdateInfo.DEFAULT_CHANNEL, info.channel)
    }

    @Test
    fun `parse notes 支持字符串数组`() {
        val info = UpdateJsonParser.parse(
            """{"versionCode":80,"apkUrl":"https://example.com/a.apk","notes":["第一条","","第二条"]}""",
        )
        assertEquals("第一条\n第二条", info?.notes)
    }

    @Test
    fun `parse 拒绝坏数据`() {
        assertNull(UpdateJsonParser.parse(null))
        assertNull(UpdateJsonParser.parse(""))
        assertNull(UpdateJsonParser.parse("not json"))
        assertNull(UpdateJsonParser.parse("[]"))
        // 缺 versionCode
        assertNull(UpdateJsonParser.parse("""{"apkUrl":"https://example.com/a.apk"}"""))
        // versionCode <= 0
        assertNull(UpdateJsonParser.parse("""{"versionCode":0,"apkUrl":"https://example.com/a.apk"}"""))
        // 缺 apkUrl / 非 http(s)
        assertNull(UpdateJsonParser.parse("""{"versionCode":80}"""))
        assertNull(UpdateJsonParser.parse("""{"versionCode":80,"apkUrl":"ftp://example.com/a.apk"}"""))
        assertNull(UpdateJsonParser.parse("""{"versionCode":80,"apkUrl":""}"""))
    }

    // ---------------------------------------------------------------- 版本比较

    @Test
    fun `isNewer 只看 versionCode 严格大于`() {
        val info = UpdateInfo(79, "4.57.0-test1", "https://example.com/a.apk")
        assertTrue(UpdateVersion.isNewer(info, currentVersionCode = 78))
        assertFalse(UpdateVersion.isNewer(info, currentVersionCode = 79))
        assertFalse(UpdateVersion.isNewer(info, currentVersionCode = 80))
    }

    @Test
    fun `compareVersionNames 数值段与预发布语义`() {
        assertTrue(UpdateVersion.compareVersionNames("4.57.0-test1", "4.56.4") > 0)
        assertTrue(UpdateVersion.compareVersionNames("4.10.0", "4.9.9") > 0)
        assertTrue(UpdateVersion.compareVersionNames("4.57.0-test2", "4.57.0-test1") > 0)
        assertTrue(UpdateVersion.compareVersionNames("4.57.0", "4.57.0-test1") > 0)
        assertTrue(UpdateVersion.compareVersionNames("4.57", "4.57.0") == 0)
        assertEquals(0, UpdateVersion.compareVersionNames("4.57.0", "v4.57.0"))
    }

    @Test
    fun `版本展示 带测试版本标签且去掉 debug 后缀`() {
        assertEquals("v4.57.0-test1（测试版本）", UpdateTestVersion.versionDisplay("4.57.0-test1"))
        assertEquals(
            "v4.57.0-test1（测试版本）",
            UpdateTestVersion.versionDisplay("4.57.0-test1-debug"),
        )
        assertEquals(10, UpdateTestVersion.version)
        assertEquals("测试版本", UpdateTestVersion.label)
        assertEquals("发现新版本", UpdateTestVersion.dialogTitle())
    }

    // ---------------------------------------------------------------- URL fallback

    @Test
    fun `端点链顺序 主 IP 在前 OSS 兜底`() {
        val chain = UpdateEndpoints.chain()
        assertEquals(2, chain.size)
        assertEquals("http://121.196.47.2/android/latest.json", chain[0])
        assertEquals(
            "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/android/latest.json",
            chain[1],
        )
        assertEquals(UpdateEndpoints.ALL, chain)
    }

    @Test
    fun `端点链去空白去重保持顺序`() {
        val chain = UpdateEndpoints.chain("https://a.example/latest.json", " https://a.example/latest.json ")
        assertEquals(listOf("https://a.example/latest.json"), chain)
        assertEquals(
            listOf("https://a.example/x.json", "https://b.example/x.json"),
            UpdateEndpoints.chain("https://a.example/x.json", "https://b.example/x.json"),
        )
    }

    // ---------------------------------------------------------------- 冷启动自动检查闸门

    @Test
    fun `冷启动自动检查每次进程只触发一次`() {
        val gate = UpdateAutoCheckGate()
        // 冷启动首次：允许自动检查（去掉每日节流后每次启动都检查）。
        assertTrue(gate.tryBegin())
        // 同一进程内重复调用（重组/回首页）：不再自动检查。
        assertFalse(gate.tryBegin())
        assertFalse(gate.tryBegin())
    }

    @Test
    fun `自动检查失败不重置闸门 下次冷启动重新检查`() {
        // 失败退避：同一进程内自动检查只登记一次，失败后也不会自动重试。
        val currentProcess = UpdateAutoCheckGate()
        assertTrue(currentProcess.tryBegin())
        assertFalse(currentProcess.tryBegin())
        // 下一次冷启动 = 新进程/新闸门：重新允许自动检查。
        val nextColdStart = UpdateAutoCheckGate()
        assertTrue(nextColdStart.tryBegin())
    }

    // ---------------------------------------------------------------- 强制更新/自动下载

    @Test
    fun `强制更新不可取消 非强制可稍后`() {
        val normal = UpdateInfo(80, "4.58.0", "https://example.com/a.apk")
        val forced = normal.copy(force = true)
        assertTrue(UpdateDialogPolicy.isDismissible(normal))
        assertTrue(UpdateDialogPolicy.canCancelDownload(normal))
        assertFalse(UpdateDialogPolicy.isDismissible(forced))
        assertFalse(UpdateDialogPolicy.canCancelDownload(forced))
    }

    @Test
    fun `parse sizeBytes 支持数字与数字字符串`() {
        val number = UpdateJsonParser.parse(
            """{"versionCode":86,"apkUrl":"https://example.com/a.apk","sizeBytes":6120000}""",
        )
        assertEquals(6_120_000L, number!!.sizeBytes)
        val asString = UpdateJsonParser.parse(
            """{"versionCode":86,"apkUrl":"https://example.com/a.apk","sizeBytes":"6120000"}""",
        )
        assertEquals(6_120_000L, asString!!.sizeBytes)
    }

    @Test
    fun `parse sizeBytes 缺省或非法为 null`() {
        assertNull(
            UpdateJsonParser.parse(
                """{"versionCode":86,"apkUrl":"https://example.com/a.apk"}""",
            )!!.sizeBytes,
        )
        assertNull(
            UpdateJsonParser.parse(
                """{"versionCode":86,"apkUrl":"https://example.com/a.apk","sizeBytes":"abc"}""",
            )!!.sizeBytes,
        )
        assertNull(
            UpdateJsonParser.parse(
                """{"versionCode":86,"apkUrl":"https://example.com/a.apk","sizeBytes":0}""",
            )!!.sizeBytes,
        )
    }

    @Test
    fun `更新弹窗文案 标题 版本递进 包大小 渠道`() {
        val info = UpdateInfo(versionCode = 86, versionName = "1.0.0-beta3", apkUrl = "https://example.com/a.apk", channel = "test", sizeBytes = 6_120_000L)
        assertEquals("发现新版本", UpdateDialogText.title())
        assertEquals("当前 v1.0.0-beta2 → 新版本 v1.0.0-beta3", UpdateDialogText.subtitle("1.0.0-beta2", info))
        assertEquals("新版本 v1.0.0-beta3", UpdateDialogText.subtitle("  ", info))
        assertTrue(UpdateDialogText.sizeText(info)!!.contains("MB"))
        assertEquals("渠道：test", UpdateDialogText.channelText(info))
        assertNull(UpdateDialogText.sizeText(info.copy(sizeBytes = null)))
        assertNull(UpdateDialogText.channelText(info.copy(channel = " ")))
        assertEquals("下载完成，点击「立即安装」。", UpdateDialogText.readyText(false))
        assertTrue(UpdateDialogText.readyText(true).contains("系统安装器"))
    }

    // ---------------------------------------------------------------- 下载状态

    @Test
    fun `下载状态机 进度到完成`() {
        var state: UpdateDownloadState = UpdateDownloadState.Idle
        state = UpdateDownloadReducer.reduce(
            state,
            UpdateDownloadEvent.Progress(downloadedBytes = 50L, totalBytes = 200L),
        )
        assertTrue(state is UpdateDownloadState.Downloading)
        assertEquals(25, (state as UpdateDownloadState.Downloading).progress.percent)

        val apk = DownloadedApk(
            fileName = "classtools-4.57.0-test1.apk",
            filePath = "/data/user/0/pkg/cache/updates/classtools-4.57.0-test1.apk",
            contentUri = null,
            source = UpdateDownloadSource.IN_APP_OKHTTP,
        )
        state = UpdateDownloadReducer.reduce(state, UpdateDownloadEvent.Done(apk))
        assertEquals(UpdateDownloadState.Ready(apk), state)
    }

    @Test
    fun `下载状态机 失败与重试`() {
        var state: UpdateDownloadState = UpdateDownloadState.Downloading(
            UpdateDownloadProgress(10L, 100L, 10),
        )
        state = UpdateDownloadReducer.reduce(state, UpdateDownloadEvent.Failed("网络不可用"))
        assertEquals(UpdateDownloadState.Failed("网络不可用"), state)
        // 重试：重新收到进度事件回到 Downloading。
        state = UpdateDownloadReducer.reduce(
            state,
            UpdateDownloadEvent.Progress(20L, 100L),
        )
        assertTrue(state is UpdateDownloadState.Downloading)
    }

    @Test
    fun `进度百分比边界`() {
        assertEquals(0, UpdateProgressLogic.percent(0L, 0L))
        assertEquals(0, UpdateProgressLogic.percent(-1L, 100L))
        assertEquals(0, UpdateProgressLogic.percent(10L, 0L))
        assertEquals(1, UpdateProgressLogic.percent(1L, 100L))
        assertEquals(50, UpdateProgressLogic.percent(50L, 100L))
        assertEquals(100, UpdateProgressLogic.percent(200L, 100L))
        val progress = UpdateProgressLogic.progress(-5L, -1L)
        assertEquals(0L, progress.downloadedBytes)
        assertEquals(0L, progress.totalBytes)
        assertEquals(0, progress.percent)
    }

    @Test
    fun `字节格式化`() {
        assertEquals("0 B", UpdateFormat.bytes(0L))
        assertEquals("512 B", UpdateFormat.bytes(512L))
        assertEquals("1.0 KB", UpdateFormat.bytes(1024L))
        assertEquals("1.5 MB", UpdateFormat.bytes(1_572_864L))
        assertTrue(UpdateFormat.bytes(2L * 1024L * 1024L * 1024L).endsWith("GB"))
    }

    // ---------------------------------------------------------------- sha256 / 文件名 / 安装判定

    @Test
    fun `sha256 校验忽略大小写 缺省跳过 非法失败`() {
        val hex = "a".repeat(64)
        assertTrue(UpdateDigest.matches("", "anything"))
        assertTrue(UpdateDigest.matches(hex.uppercase(), hex))
        assertTrue(UpdateDigest.matches("sha256:$hex", hex))
        assertFalse(UpdateDigest.matches(hex, "b".repeat(64)))
        assertFalse(UpdateDigest.matches("not-a-sha", hex))
        assertFalse(UpdateDigest.matches(hex, ""))
        // 已知向量：空字节的 sha256。
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateDigest.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `APK 文件名安全且以 apk 结尾`() {
        val info = UpdateInfo(
            versionCode = 79,
            versionName = "4.57.0-test1",
            apkUrl = "https://example.com/a.apk",
        )
        assertEquals("classtools-4.57.0-test1.apk", UpdateFileNames.apkFileName(info))
        assertEquals("ClassTools", UpdateFileNames.PUBLIC_DOWNLOAD_SUB_DIR)
        assertEquals("updates", UpdateFileNames.CACHE_SUB_DIR)
        val weird = info.copy(versionName = "4.57.0 test/1")
        assertTrue(UpdateFileNames.apkFileName(weird).endsWith(".apk"))
        assertFalse(UpdateFileNames.apkFileName(weird).contains("/"))
        assertFalse(UpdateFileNames.apkFileName(weird).contains(" "))
    }

    @Test
    fun `安装动作判定`() {
        assertEquals(
            UpdateInstallAction.INSTALL,
            UpdateInstallPolicy.nextAction(hasInstaller = true, canRequestPackageInstalls = true),
        )
        assertEquals(
            UpdateInstallAction.NEED_UNKNOWN_SOURCES_PERMISSION,
            UpdateInstallPolicy.nextAction(hasInstaller = true, canRequestPackageInstalls = false),
        )
        assertEquals(
            UpdateInstallAction.NO_HANDLER,
            UpdateInstallPolicy.nextAction(hasInstaller = false, canRequestPackageInstalls = true),
        )
    }
}
