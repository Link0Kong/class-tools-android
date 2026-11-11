package com.furina.classtools.features.ppt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分片计划 / 上限 / 标题 / 文件名（契约 §3.2/§4.2/§8.1）。 */
class PptLimitsTest {

    @Test
    fun chunks_fixed512k_lastChunkMayBeShort() {
        val size = 3L * PptLimits.CHUNK_BYTES + 123L
        val chunks = PptLimits.chunks(size)
        assertEquals(4, chunks.size)
        assertEquals(0L, chunks[0].offset)
        assertEquals(PptLimits.CHUNK_BYTES, chunks[0].length)
        assertEquals(3L * PptLimits.CHUNK_BYTES, chunks[3].offset)
        assertEquals(123, chunks[3].length)
        assertEquals(4, PptLimits.chunkCount(size))
        // 每一片长度累加 == 文件大小
        assertEquals(size, chunks.sumOf { it.length.toLong() })
        // idx 从 0 连续
        assertEquals(listOf(0, 1, 2, 3), chunks.map { it.idx })
    }

    @Test
    fun chunks_exactMultiple_noEmptyTail() {
        val size = 2L * PptLimits.CHUNK_BYTES
        val chunks = PptLimits.chunks(size)
        assertEquals(2, chunks.size)
        assertEquals(PptLimits.CHUNK_BYTES, chunks.last().length)
    }

    @Test
    fun chunks_emptyOrNegative() {
        assertTrue(PptLimits.chunks(0L).isEmpty())
        assertTrue(PptLimits.chunks(-5L).isEmpty())
    }

    @Test
    fun chunks_customChunkSizeForTests() {
        val chunks = PptLimits.chunks(10L, chunkBytes = 4)
        assertEquals(listOf(4, 4, 2), chunks.map { it.length })
        assertEquals(listOf(0L, 4L, 8L), chunks.map { it.offset })
    }

    @Test
    fun limits_64MiB() {
        assertEquals(64L * 1024 * 1024, PptLimits.MAX_SOURCE_BYTES)
        assertFalse(PptLimits.exceedsLimit(PptLimits.MAX_SOURCE_BYTES))
        assertTrue(PptLimits.exceedsLimit(PptLimits.MAX_SOURCE_BYTES + 1))
        assertTrue(PptLimits.isValidSize(1))
        assertFalse(PptLimits.isValidSize(0))
        assertFalse(PptLimits.isValidSize(PptLimits.MAX_SOURCE_BYTES + 1))
    }

    @Test
    fun acceptedFileNames() {
        assertTrue(PptLimits.isAcceptedFileName("班会.pptx"))
        assertTrue(PptLimits.isAcceptedFileName("A.PPT"))
        assertFalse(PptLimits.isAcceptedFileName("班会.pdf"))
        assertFalse(PptLimits.isAcceptedFileName("pptx"))
        assertFalse(PptLimits.isAcceptedFileName(""))
        assertFalse(PptLimits.isAcceptedFileName("x."))
        assertFalse(PptLimits.isAcceptedFileName(".pptx")) // 只有扩展名、无主名
    }

    @Test
    fun rejectReason_orderAndText() {
        assertEquals("文件为空，无法上传", PptLimits.rejectReason(0L, "a.pptx"))
        assertEquals("文件超过 64 MB，请压缩后再上传", PptLimits.rejectReason(PptLimits.MAX_SOURCE_BYTES + 1, "a.pptx"))
        assertEquals("仅支持 .ppt / .pptx 文件", PptLimits.rejectReason(100L, "a.doc"))
        assertNull(PptLimits.rejectReason(100L, "a.pptx"))
    }

    @Test
    fun sanitizeTitle_trimsAndCapsAt80() {
        assertEquals("班会 PPT", PptLimits.sanitizeTitle("  班会   PPT  "))
        assertEquals("未命名演示", PptLimits.sanitizeTitle("   "))
        assertEquals("未命名演示", PptLimits.sanitizeTitle(null))
        val long = "啊".repeat(200)
        assertEquals(80, PptLimits.sanitizeTitle(long).length)
        assertEquals("a b", PptLimits.sanitizeTitle("a\nb"))
    }

    @Test
    fun titleFromFileName_stripsExtension() {
        assertEquals("班会", PptLimits.titleFromFileName("班会.pptx"))
        assertEquals("PPT 总结", PptLimits.titleFromFileName("PPT 总结.PPT"))
        assertEquals("无扩展", PptLimits.titleFromFileName("无扩展"))
        assertEquals("未命名演示", PptLimits.titleFromFileName(""))
        assertEquals("未命名演示", PptLimits.titleFromFileName(".pptx"))
    }

    @Test
    fun formatBytes() {
        assertEquals("512 B", PptLimits.formatBytes(512))
        assertEquals("1.0 KB", PptLimits.formatBytes(1024))
        assertEquals("1.5 KB", PptLimits.formatBytes(1536))
        assertEquals("1.0 MB", PptLimits.formatBytes(1024L * 1024))
        assertEquals("—", PptLimits.formatBytes(-1))
    }

    @Test
    fun deckId_formatAndRoundTrip() {
        val now = 1_791_590_400_000L
        val tsBase36 = java.lang.Long.toString(now, 36)
        val id = PptIds.newDeckId(now, kotlin.random.Random(42))
        assertTrue(id.startsWith("d"))
        assertEquals("d$tsBase36", id.dropLast(6))
        assertEquals(1 + tsBase36.length + 6, id.length)
        assertTrue(id.takeLast(6).all { it in PptIds.ALPHABET })
        assertTrue(PptIds.isDeckId(id))
        assertFalse(PptIds.isDeckId(null))
        assertFalse(PptIds.isDeckId(""))
        assertFalse(PptIds.isDeckId("x1234567"))
        assertFalse(PptIds.isDeckId("dABC"))
    }

    @Test
    fun deckId_randomSourceDeterministic() {
        val a = PptIds.newDeckId(1_000L, kotlin.random.Random(7))
        val b = PptIds.newDeckId(1_000L, kotlin.random.Random(7))
        assertEquals(a, b)
        assertEquals('d', a.first())
    }
}
