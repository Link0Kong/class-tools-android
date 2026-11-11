package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptCompat
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptFileInfo
import com.furina.classtools.data.ppt.PptFiles
import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.ppt.PptMediaDef
import com.furina.classtools.data.ppt.PptSlide
import com.furina.classtools.data.ppt.PptStep
import com.furina.classtools.data.ppt.PptVideoChapter
import com.furina.classtools.data.ppt.PptVideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.0 下载中心纯逻辑（契约 §12.4）：三行下载/文件名/大小/MIME/兼容提示/旧 deck/列表标签。 */
class PptDownloadLogicTest {

    private val base = "https://oss.example/ppt/a/dmg2k4x9ab12c/b_build/"

    private fun files(
        compat: PptFileInfo? = PptFileInfo("compat.pptx", 18_432_000L, "sha1", "pptx", "班会 兼容版.pptx"),
        pdf: PptFileInfo? = PptFileInfo("preview.pdf", 6_291_456L, "sha2", "pdf", "班会.pdf", pages = 16),
        source: PptFileInfo? = PptFileInfo("original.pptx", 17_825_792L, "sha3", "pptx", "班会.pptx"),
    ) = PptFiles(compat = compat, pdf = pdf, source = source)

    private fun manifest(
        v: Int = 2,
        files: PptFiles? = files(),
        compat: PptCompat? = PptCompat(warnings = listOf("外链视频 1 处未随文件携带（第 7 页）"), fontsEmbedded = 2),
        slides: List<PptSlide> = emptyList(),
        src: String = "",
        video: PptVideoInfo? = null,
    ) = PptManifest(
        v = v,
        base = base,
        src = src,
        width = 960,
        height = 540,
        slides = slides,
        video = video,
        files = files,
        compat = compat,
        media = emptyMap<String, PptMediaDef>(),
    )

    private fun detail(
        manifest: PptManifest = manifest(),
        title: String = "班会",
        status: String = PptDeck.STATUS_READY,
        sourceName: String = "班会.pptx",
        sourceSize: Long = 17_825_792L,
    ) = PptDeckDetail(
        title = title,
        manifest = manifest,
        status = status,
        sourceName = sourceName,
        sourceSize = sourceSize,
    )

    // ------------------------------------------------------------ 三行下载

    @Test
    fun rows_newDeck_threeRowsWithSizesAndUrls() {
        val rows = PptDownloadLogic.rows(detail())
        assertEquals(
            listOf(PptDownloadLogic.Kind.COMPAT, PptDownloadLogic.Kind.PDF, PptDownloadLogic.Kind.SOURCE),
            rows.map { it.kind },
        )
        assertTrue(rows.all { it.available })
        val compat = rows[0]
        assertEquals("兼容版 PPTX", compat.kind.label)
        assertEquals(base + "compat.pptx", compat.url)
        assertEquals("17.6 MB", compat.sizeText) // 18,432,000 B
        assertEquals("PPTX", compat.subtitle)
        assertEquals(PptDownloadLogic.MIME_PPTX, compat.mimeType)
        assertEquals("班会 兼容版.pptx", compat.fileName)

        val pdf = rows[1]
        assertEquals(base + "preview.pdf", pdf.url)
        assertEquals("6.0 MB", pdf.sizeText)
        assertEquals("16 页", pdf.subtitle)
        assertEquals(PptDownloadLogic.MIME_PDF, pdf.mimeType)

        val source = rows[2]
        assertEquals(base + "original.pptx", source.url)
        assertEquals("17.0 MB", source.sizeText)
        assertEquals("PPTX", source.subtitle)
    }

    @Test
    fun rows_missingFilesUnavailable() {
        val rows = PptDownloadLogic.rows(detail(manifest = manifest(files = null)))
        assertEquals(3, rows.size)
        assertFalse(rows[0].available)
        assertFalse(rows[1].available)
        assertEquals("—", rows[0].sizeText)
        assertEquals("", rows[0].url)
    }

    @Test
    fun rows_partialFiles() {
        val rows = PptDownloadLogic.rows(
            detail(manifest = manifest(files = PptFiles(compat = PptFileInfo("c.pptx", 100, ext = "pptx")))),
        )
        assertTrue(rows[0].available)
        assertFalse(rows[1].available)
        assertFalse(rows[2].available)
        // size=100 → "100 B"（>0 即显示）
        assertEquals("100 B", rows[0].sizeText)
        // 无 name → 标题+扩展名兜底
        assertEquals("班会.pptx", rows[0].fileName)
    }

    @Test
    fun rows_absoluteUrlKept() {
        val absolute = "https://cdn.example/x/compat.pptx"
        val rows = PptDownloadLogic.rows(
            detail(manifest = manifest(files = PptFiles(compat = PptFileInfo(absolute, 10, ext = "pptx")))),
        )
        assertEquals(absolute, rows[0].url)
    }

    // ------------------------------------------------------------ 旧 deck

    @Test
    fun legacyDeck_flagsAndOriginalFallback() {
        val legacyManifest = manifest(
            files = null,
            compat = null,
            src = "source.pptx",
            slides = listOf(PptSlide(steps = listOf(PptStep("s1.webp")))),
        )
        val legacy = detail(manifest = legacyManifest)
        assertTrue(PptDownloadLogic.isLegacyDeck(legacy))
        assertTrue(PptDownloadLogic.canPreview(legacy))
        val rows = PptDownloadLogic.rows(legacy)
        assertFalse(rows[0].available) // 兼容版未生成
        assertFalse(rows[1].available) // PDF 未生成
        assertTrue(rows[2].available) // 原始文件仍可下载
        assertEquals(base + "source.pptx", rows[2].url)
        assertEquals("原始文件", rows[2].kind.label)
        assertEquals("班会.pptx", rows[2].fileName) // sourceName 兜底
    }

    @Test
    fun legacyDeck_withoutSource_allUnavailable() {
        val legacy = detail(manifest = manifest(files = null, compat = null, src = ""))
        assertTrue(PptDownloadLogic.isLegacyDeck(legacy))
        assertFalse(PptDownloadLogic.hasAnyDownload(legacy))
        assertFalse(PptDownloadLogic.canPreview(legacy))
    }

    @Test
    fun newDeck_withPreservedPreview() {
        val d = detail(
            manifest = manifest(
                slides = listOf(PptSlide(steps = listOf(PptStep("s1.webp")))),
            ),
        )
        assertFalse(PptDownloadLogic.isLegacyDeck(d))
        assertTrue(PptDownloadLogic.canPreview(d))
        assertTrue(PptDownloadLogic.hasAnyDownload(d))
    }

    @Test
    fun canPreview_videoOnly() {
        val video = PptVideoInfo(
            src = "deck.mp4",
            width = 1280,
            height = 720,
            durationSec = 10.0,
            chapters = listOf(PptVideoChapter(0, 0.0)),
        )
        val d = detail(manifest = manifest(video = video))
        assertTrue(PptDownloadLogic.canPreview(d))
    }

    // ------------------------------------------------------------ 文件名 / MIME

    @Test
    fun suggestedName_usesServerNameAndFallbacks() {
        val d = detail(title = "班会：期中总结")
        val compat = PptFileInfo("compat.pptx", 10, ext = "pptx", name = "自定义 名字.pptx")
        assertEquals(
            "自定义 名字.pptx",
            PptDownloadLogic.suggestedFileName(PptDownloadLogic.Kind.COMPAT, compat, d, "pptx"),
        )
        val noName = PptFileInfo("compat.pptx", 10)
        assertEquals(
            "班会：期中总结.pptx",
            PptDownloadLogic.suggestedFileName(PptDownloadLogic.Kind.COMPAT, noName, d, "pptx"),
        )
        // 服务器 name 缺扩展名 → 补
        val nameNoExt = PptFileInfo("compat.pptx", 10, name = "兼容版")
        assertEquals(
            "兼容版.pptx",
            PptDownloadLogic.suggestedFileName(PptDownloadLogic.Kind.COMPAT, nameNoExt, d, "pptx"),
        )
    }

    @Test
    fun sanitizeFileName_illegalChars() {
        assertEquals("a_b_c", PptDownloadLogic.sanitizeFileName("a/b\\c"))
        assertEquals("x_y", PptDownloadLogic.sanitizeFileName("x<>y"))
        assertEquals("文件名", PptDownloadLogic.sanitizeFileName("  文件名  "))
        assertEquals("多 空格", PptDownloadLogic.sanitizeFileName("多   空格"))
        assertEquals("", PptDownloadLogic.sanitizeFileName(null))
        val long = "啊".repeat(200)
        assertEquals(120, PptDownloadLogic.sanitizeFileName(long).length)
    }

    @Test
    fun extensionHelpers() {
        assertEquals("pptx", PptDownloadLogic.extensionOf("a/b/班会.PPTX"))
        assertEquals("", PptDownloadLogic.extensionOf("无扩展"))
        assertEquals("", PptDownloadLogic.extensionOf("a."))
        assertEquals("", PptDownloadLogic.extensionOf(null))
        assertEquals("a.pptx", PptDownloadLogic.ensureExtension("a", "pptx"))
        assertEquals("a.PPTX", PptDownloadLogic.ensureExtension("a.PPTX", "pptx")) // 已有扩展名不改写大小写
        assertEquals("a.pdf", PptDownloadLogic.ensureExtension("a", "pdf"))
        assertEquals("a", PptDownloadLogic.ensureExtension("a", ""))
    }

    @Test
    fun mimeTypes() {
        assertEquals(PptDownloadLogic.MIME_PDF, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.PDF, "pdf"))
        assertEquals(PptDownloadLogic.MIME_PDF, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.SOURCE, "pdf"))
        assertEquals(PptDownloadLogic.MIME_PPTX, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.COMPAT, "pptx"))
        assertEquals(PptDownloadLogic.MIME_PPT, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.COMPAT, "ppt"))
        assertEquals(PptDownloadLogic.MIME_BINARY, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.COMPAT, ""))
        assertEquals(PptDownloadLogic.MIME_BINARY, PptDownloadLogic.mimeTypeFor(PptDownloadLogic.Kind.SOURCE, "zip"))
    }

    @Test
    fun nextAvailableFileName() {
        assertEquals("a.pptx", PptDownloadLogic.nextAvailableFileName(emptySet(), "a.pptx"))
        assertEquals("a (1).pptx", PptDownloadLogic.nextAvailableFileName(setOf("a.pptx"), "a.pptx"))
        assertEquals(
            "a (2).pptx",
            PptDownloadLogic.nextAvailableFileName(setOf("a.pptx", "a (1).pptx"), "a.pptx"),
        )
        assertEquals(
            "无扩展 (1)",
            PptDownloadLogic.nextAvailableFileName(setOf("无扩展"), "无扩展"),
        )
    }

    // ------------------------------------------------------------ 兼容提示 / 标签 / 状态

    @Test
    fun warnings_trimAndDistinct() {
        val d = detail(
            manifest = manifest(
                compat = PptCompat(
                    warnings = listOf(" 外链视频 1 处 ", "字体未嵌入", " 外链视频 1 处 ", ""),
                ),
            ),
        )
        assertEquals(listOf("外链视频 1 处", "字体未嵌入"), PptDownloadLogic.warnings(d))
        // 无 compat → 空
        assertTrue(PptDownloadLogic.warnings(detail(manifest = manifest(compat = null))).isEmpty())
    }

    @Test
    fun listTags_fromDeckFlags() {
        val both = PptDeck(id = "d1", hasCompatFiles = true, hasPdfFile = true)
        assertEquals(listOf("兼容版", "PDF"), PptDownloadLogic.listTags(both))
        assertEquals(listOf("兼容版"), PptDownloadLogic.listTags(both.copy(hasPdfFile = false)))
        assertEquals(listOf("PDF"), PptDownloadLogic.listTags(both.copy(hasCompatFiles = false)))
        assertTrue(PptDownloadLogic.listTags(PptDeck(id = "d2")).isEmpty())
    }

    @Test
    fun statusLine_readyAndPending() {
        assertEquals("已完成", PptDownloadLogic.statusLine(detail()))
        val processing = detail(status = PptDeck.STATUS_PROCESSING).copy(progress = 61)
        assertEquals("转换中 61%", PptDownloadLogic.statusLine(processing))
        val queued = detail(status = PptDeck.STATUS_QUEUED)
        assertEquals("排队转换", PptDownloadLogic.statusLine(queued))
        val failed = detail(status = PptDeck.STATUS_FAILED)
        assertEquals("转换失败", PptDownloadLogic.statusLine(failed))
    }

    @Test
    fun viewerMimeAndShare() {
        val rows = PptDownloadLogic.rows(detail())
        assertEquals(PptDownloadLogic.MIME_PDF, PptDownloadLogic.viewerMime(rows[1]))
        assertTrue(PptDownloadLogic.canShare(rows[0]))
        val unavailable = PptDownloadLogic.rows(detail(manifest = manifest(files = null)))
        assertFalse(PptDownloadLogic.canShare(unavailable[0]))
    }

    /** 媒体表字段存在不干扰下载逻辑（v1.2 回归保障）。 */
    @Test
    fun mediaDoesNotAffectFiles() {
        val manifest = manifest().copy(media = mapOf("m0.mp4" to PptMediaDef("m0.mp4", PptMediaKind.VIDEO)))
        assertTrue(PptDownloadLogic.rows(detail(manifest = manifest)).all { it.url.startsWith(base) })
    }
}
