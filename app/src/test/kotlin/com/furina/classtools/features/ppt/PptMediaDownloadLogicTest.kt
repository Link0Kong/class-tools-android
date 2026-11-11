package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaFile
import com.furina.classtools.data.ppt.PptMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.3 §12.8 视频/音频单独下载行模型（纯逻辑）。 */
class PptMediaDownloadLogicTest {

    private val base = "https://oss.example/ppt/a/dmg2k4x9ab12c/b_build/"

    private fun manifest(mediaFiles: List<PptMediaFile>, baseUrl: String = base): PptManifest =
        PptManifest(v = 2, base = baseUrl, mediaFiles = mediaFiles)

    private fun video(
        src: String = "media/a_视频1_第7页.mp4",
        name: String = "班会_视频1_第7页.mp4",
        size: Long = 20_158_796L,
        dur: Double = 150.92,
        w: Int = 1024,
        h: Int = 576,
        slides: List<Int> = listOf(7),
    ) = PptMediaFile(
        src = src,
        name = name,
        size = size,
        kind = PptMediaKind.VIDEO,
        durationSec = dur,
        width = w,
        height = h,
        slides = slides,
    )

    private fun audio(
        src: String = "media/a_音频1_第3页.m4a",
        name: String = "",
        size: Long = 1_048_576L,
        dur: Double = 45.5,
        slides: List<Int> = listOf(3),
    ) = PptMediaFile(
        src = src,
        name = name,
        size = size,
        kind = PptMediaKind.AUDIO,
        durationSec = dur,
        slides = slides,
    )

    // ------------------------------------------------------------ 行模型

    @Test
    fun rows_videoAndAudioLabelsAndDetails() {
        val rows = PptMediaDownloadLogic.rows(
            manifest(listOf(video(), video(src = "media/b.mp4", name = "", slides = listOf(12)), audio())),
            deckTitle = "班会",
        )
        assertEquals(3, rows.size)

        val first = rows[0]
        assertEquals("video_1", first.key)
        assertEquals(PptMediaKind.VIDEO, first.kind)
        assertEquals(1, first.index)
        assertEquals("视频1 · 第7页", first.title)
        assertEquals("02:30 · 1024×576", first.detail)
        assertEquals("19.2 MB", first.sizeText)
        assertEquals("video/mp4", first.mimeType)
        assertEquals(base + "media/a_视频1_第7页.mp4", first.url)
        assertEquals("班会_视频1_第7页.mp4", first.fileName)

        val second = rows[1]
        assertEquals("video_2", second.key)
        assertEquals("视频2 · 第12页", second.title)
        // 无 name → 标题_视频2_第12页.mp4 兜底
        assertEquals("班会_视频2_第12页.mp4", second.fileName)

        val third = rows[2]
        assertEquals("audio_1", third.key)
        assertEquals(PptMediaKind.AUDIO, third.kind)
        assertEquals("音频1 · 第3页", third.title)
        // 音频只显示时长（无分辨率）
        assertEquals("00:45", third.detail)
        assertEquals("audio/mp4", third.mimeType)
        assertEquals("班会_音频1_第3页.m4a", third.fileName)
    }

    @Test
    fun rows_emptyOrMissing_returnsEmpty() {
        assertTrue(PptMediaDownloadLogic.rows(PptManifest(), "班会").isEmpty())
        assertFalse(PptMediaDownloadLogic.hasRows(PptManifest()))
        assertFalse(PptMediaDownloadLogic.shouldShowGroup(PptManifest()))
        var m = manifest(emptyList())
        assertTrue(PptMediaDownloadLogic.rows(m, "班会").isEmpty())
        m = m.copy(mediaFiles = emptyList())
        assertFalse(PptMediaDownloadLogic.hasRows(m))
    }

    @Test
    fun rows_absoluteUrlAndBlankBase() {
        val absolute = "https://cdn.example/media/x.mp4"
        val rows = PptMediaDownloadLogic.rows(
            manifest(listOf(video(src = absolute))),
            "班会",
        )
        assertEquals(absolute, rows[0].url)

        val noBase = PptMediaDownloadLogic.rows(
            manifest(listOf(video(src = "media/rel.mp4")), baseUrl = ""),
            "班会",
        )
        assertEquals("media/rel.mp4", noBase[0].url)
    }

    @Test
    fun rows_sizeAndDurationUnknown() {
        val rows = PptMediaDownloadLogic.rows(
            manifest(listOf(video(size = 0L, dur = 0.0, w = 0, h = 0))),
            "班会",
        )
        assertEquals("—", rows[0].sizeText)
        assertEquals("", rows[0].detail)
    }

    // ------------------------------------------------------------ 页号 / 标题

    @Test
    fun pageNumbers_dedupSortAndZeroFallback() {
        assertEquals(listOf(3, 7, 12), PptMediaDownloadLogic.pageNumbers(listOf(12, 3, 7, 3)))
        assertEquals(listOf(1), PptMediaDownloadLogic.pageNumbers(listOf(0, -1)))
        assertTrue(PptMediaDownloadLogic.pageNumbers(emptyList()).isEmpty())

        assertEquals("第7页", PptMediaDownloadLogic.pageLabel(listOf(7)))
        assertEquals("第7、12页", PptMediaDownloadLogic.pageLabel(listOf(12, 7)))
        assertEquals("", PptMediaDownloadLogic.pageLabel(emptyList()))
        assertEquals("视频3", PptMediaDownloadLogic.titleOf(PptMediaKind.VIDEO, 3, emptyList()))
        assertEquals("音频2 · 第9页", PptMediaDownloadLogic.titleOf(PptMediaKind.AUDIO, 2, listOf(9)))
    }

    @Test
    fun kindLabelsAndSummary() {
        assertEquals("视频", PptMediaDownloadLogic.kindLabel(PptMediaKind.VIDEO))
        assertEquals("音频", PptMediaDownloadLogic.kindLabel(PptMediaKind.AUDIO))
        val rows = PptMediaDownloadLogic.rows(manifest(listOf(video(), video(), audio())), "班会")
        assertEquals("2 个视频 · 1 个音频", PptMediaDownloadLogic.summaryText(rows))
        assertTrue(PptMediaDownloadLogic.summaryText(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------ MIME / 文件名

    @Test
    fun mimeTypes() {
        assertEquals("video/mp4", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.VIDEO, "mp4"))
        assertEquals("video/quicktime", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.VIDEO, "mov"))
        assertEquals("video/webm", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.VIDEO, "webm"))
        assertEquals("audio/mp4", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.AUDIO, "m4a"))
        assertEquals("audio/mpeg", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.AUDIO, "mp3"))
        // 未知扩展名按 kind 兜底
        assertEquals("video/mp4", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.VIDEO, ""))
        assertEquals("audio/mp4", PptMediaDownloadLogic.mimeTypeOf(PptMediaKind.AUDIO, "unknown"))
    }

    @Test
    fun extensionAndFilenames() {
        assertEquals("mp4", PptMediaDownloadLogic.extensionOf("a.b.MP4"))
        assertEquals("", PptMediaDownloadLogic.extensionOf("noext"))
        assertEquals("", PptMediaDownloadLogic.extensionOf(null))

        // manifest name 优先 + 清洗
        val v = video(name = "班会<bad>:视频1.mp4")
        val rows = PptMediaDownloadLogic.rows(manifest(listOf(v)), "班会")
        assertEquals("班会_bad_视频1.mp4", rows[0].fileName)

        // manifest name 缺扩展名 → 补
        val v2 = video(name = "兼容视频")
        assertEquals(
            "兼容视频.mp4",
            PptMediaDownloadLogic.rows(manifest(listOf(v2)), "班会")[0].fileName,
        )

        // 标题为空 → 兜底
        val v3 = video(name = "", slides = emptyList())
        assertEquals(
            "演示文件_视频1.mp4",
            PptMediaDownloadLogic.rows(manifest(listOf(v3)), deckTitle = "")[0].fileName,
        )
    }

    @Test
    fun shouldShowGroup_matchesMediaFiles() {
        assertFalse(PptMediaDownloadLogic.shouldShowGroup(PptManifest()))
        assertTrue(PptMediaDownloadLogic.shouldShowGroup(manifest(listOf(audio()))))
    }
}
