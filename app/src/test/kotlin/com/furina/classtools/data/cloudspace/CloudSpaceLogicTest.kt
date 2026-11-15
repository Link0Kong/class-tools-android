package com.furina.classtools.data.cloudspace

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯逻辑单测：分片规划、大小/时间/TTL 文案、文件名、文件类型、事件合并。 */
class CloudSpaceLogicTest {

    private fun file(
        id: String,
        uploadedAt: Long,
        name: String = "$id.bin",
        sizeBytes: Long = 1024L,
        expired: Boolean = false,
        downloadUrl: String? = null,
    ): CloudSpaceFile = CloudSpaceFile(
        id = id,
        name = name,
        sizeBytes = sizeBytes,
        mimeType = "application/octet-stream",
        downloadUrl = downloadUrl ?: if (expired) "" else "https://example.invalid/drop/file/$id",
        expired = expired,
        uploadedAt = uploadedAt,
    )

    // ---------------------------------------------------------------- 分片

    @Test
    fun `默认分片为 8MiB`() {
        assertEquals(8 * 1024 * 1024, CloudSpaceDefaults.CHUNK_SIZE)
        assertEquals(259_200_000L, CloudSpaceDefaults.FILE_TTL_MS)
    }

    @Test
    fun `整倍数大小分片`() {
        val plan = CloudSpaceLogic.chunkPlan(16L * 1024 * 1024)

        assertEquals(2, plan.totalChunks)
        assertEquals(8_388_608, plan.chunkSize)
        assertEquals(2, plan.chunks.size)
        assertEquals(0L, plan.chunks[0].start)
        assertEquals(8_388_608L, plan.chunks[0].endExclusive)
        assertEquals(8_388_608L, plan.chunks[0].size)
        assertEquals(8_388_608L, plan.chunks[1].start)
        assertEquals(16_777_216L, plan.chunks[1].endExclusive)
    }

    @Test
    fun `带余数大小最后一片可小于分片`() {
        val plan = CloudSpaceLogic.chunkPlan(10L, chunkSize = 4)

        assertEquals(3, plan.totalChunks)
        assertEquals(listOf(0, 1, 2), plan.chunks.map { it.index })
        assertEquals(listOf(4L, 4L, 2L), plan.chunks.map { it.size })
        assertEquals(listOf(0L, 4L, 8L), plan.chunks.map { it.start })
        assertEquals(listOf(4L, 8L, 10L), plan.chunks.map { it.endExclusive })
    }

    @Test
    fun `空文件按 1 个 0 字节分片`() {
        val plan = CloudSpaceLogic.chunkPlan(0L)

        assertEquals(1, plan.totalChunks)
        assertEquals(1, plan.chunks.size)
        assertEquals(0L, plan.chunks[0].size)
    }

    @Test
    fun `非法分片大小回退默认值`() {
        val plan = CloudSpaceLogic.chunkPlan(1024L, chunkSize = 0)

        assertEquals(CloudSpaceDefaults.CHUNK_SIZE, plan.chunkSize)
        assertEquals(1, plan.totalChunks)
    }

    @Test
    fun `负数大小按 0 处理`() {
        val plan = CloudSpaceLogic.chunkPlan(-100L)

        assertEquals(1, plan.totalChunks)
        assertEquals(0L, plan.chunks[0].size)
    }

    @Test
    fun `512GiB 内分片数不超过服务端上限`() {
        val maxChunks = CloudSpaceDefaults.MAX_UPLOAD_CHUNKS
        val size = CloudSpaceDefaults.CHUNK_SIZE.toLong() * (maxChunks - 1) + 1

        val plan = CloudSpaceLogic.chunkPlan(size)

        assertEquals(maxChunks, plan.totalChunks)
    }

    // ---------------------------------------------------------------- 文案

    @Test
    fun `文件大小格式化`() {
        assertEquals("0 B", CloudSpaceLogic.fmtSize(0))
        assertEquals("512 B", CloudSpaceLogic.fmtSize(512))
        assertEquals("1.0 KB", CloudSpaceLogic.fmtSize(1024))
        assertEquals("1.5 MB", CloudSpaceLogic.fmtSize(1_572_864))
        assertEquals("12 MB", CloudSpaceLogic.fmtSize(12_582_912))
        assertEquals("0 B", CloudSpaceLogic.fmtSize(-5))
    }

    @Test
    fun `时间格式化按本地时区`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val epoch = LocalDateTime.of(2026, 11, 6, 10, 5).atZone(zone).toInstant().toEpochMilli()

        assertEquals("11-06 10:05", CloudSpaceLogic.fmtTime(epoch, zone))
        assertEquals("", CloudSpaceLogic.fmtTime(0))
    }

    @Test
    fun `剩余 TTL 文案`() {
        val uploadedAt = 1_000_000_000L

        // 刚上传：剩 3 天
        assertEquals("剩 3 天", CloudSpaceLogic.ttlText(uploadedAt, uploadedAt))
        // 已过 47h → 剩 25h → 1 天 1 小时
        assertEquals(
            "剩 1 天 1 小时",
            CloudSpaceLogic.ttlText(uploadedAt, uploadedAt + 47L * 3_600_000L),
        )
        // 剩 5h
        assertEquals(
            "剩 5 小时",
            CloudSpaceLogic.ttlText(uploadedAt, uploadedAt + 67L * 3_600_000L),
        )
        // 刚好到期 / 已过期
        assertEquals("已过期", CloudSpaceLogic.ttlText(uploadedAt, uploadedAt + 72L * 3_600_000L))
        assertEquals("已过期", CloudSpaceLogic.ttlText(uploadedAt, uploadedAt + 100L * 3_600_000L))
        assertEquals("已过期", CloudSpaceLogic.ttlText(0L, uploadedAt))
    }

    @Test
    fun `上传百分比最多 99 直到完成`() {
        assertEquals(0, CloudSpaceLogic.uploadPercent(0, 100))
        assertEquals(50, CloudSpaceLogic.uploadPercent(50, 100))
        assertEquals(99, CloudSpaceLogic.uploadPercent(100, 100))
        assertEquals(0, CloudSpaceLogic.uploadPercent(10, 0))
        assertEquals(0, CloudSpaceLogic.uploadPercent(-1, 100))
    }

    @Test
    fun `过期提示文案`() {
        assertEquals(
            "2 个文件已过期（上传超过 72 小时，已自动删除）",
            CloudSpaceLogic.expiredHint(2),
        )
    }

    // ---------------------------------------------------------------- 文件名

    @Test
    fun `文件名清洗非法字符与路径`() {
        assertEquals("a_b_c.pdf", CloudSpaceLogic.safeName("a/b:c.pdf"))
        assertEquals("x_y_.zip", CloudSpaceLogic.safeName("x\ny\u0001.zip"))
        assertEquals("file", CloudSpaceLogic.safeName(null))
        assertEquals("file", CloudSpaceLogic.safeName("   "))
        // 首尾的「.」被去掉，避免生成隐藏文件
        assertEquals("hidden", CloudSpaceLogic.safeName(".hidden.."))
        // 全部是非法字符/点 → 回退 file
        assertEquals("file", CloudSpaceLogic.safeName("..."))
    }

    @Test
    fun `超长文件名截断仍保留扩展名`() {
        val longName = "a".repeat(200) + ".pptx"

        val safe = CloudSpaceLogic.safeName(longName)

        assertEquals(120, safe.length)
        assertTrue(safe.endsWith(".pptx"))
    }

    @Test
    fun `同名文件去重追加序号`() {
        assertEquals("a.txt", CloudSpaceLogic.uniqueName(emptySet(), "a.txt"))
        assertEquals("a (1).txt", CloudSpaceLogic.uniqueName(setOf("a.txt"), "a.txt"))
        assertEquals(
            "a (2).txt",
            CloudSpaceLogic.uniqueName(setOf("a.txt", "a (1).txt"), "a.txt"),
        )
        // 无扩展名
        assertEquals("report (1)", CloudSpaceLogic.uniqueName(setOf("report"), "report"))
    }

    // ---------------------------------------------------------------- 文件类型

    @Test
    fun `文件类型按 MIME 优先扩展名兜底`() {
        assertEquals(
            CloudSpaceLogic.FileKind.IMAGE,
            CloudSpaceLogic.fileKind("image/jpeg", "photo.bin"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.VIDEO,
            CloudSpaceLogic.fileKind("application/octet-stream", "clip.mp4"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.AUDIO,
            CloudSpaceLogic.fileKind("application/octet-stream", "voice.M4A"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.ARCHIVE,
            CloudSpaceLogic.fileKind("application/zip", "pack.zip"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.PDF,
            CloudSpaceLogic.fileKind("application/pdf", "a.pdf"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.DOCUMENT,
            CloudSpaceLogic.fileKind("", "讲义.pptx"),
        )
        assertEquals(
            CloudSpaceLogic.FileKind.OTHER,
            CloudSpaceLogic.fileKind("", "data.unknownext"),
        )
    }

    // ---------------------------------------------------------------- 事件合并

    @Test
    fun `全量事件按上传时间倒序`() {
        val events = listOf(
            CloudSpaceEvent.FileAdded(file("a", uploadedAt = 1000L), seq = 1),
            CloudSpaceEvent.FileAdded(file("b", uploadedAt = 3000L), seq = 2),
            CloudSpaceEvent.FileAdded(file("c", uploadedAt = 2000L), seq = 3),
        )

        val merged = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), events)

        assertEquals(listOf("b", "c", "a"), merged.files.map { it.id })
        assertEquals(0, merged.expiredCount)
    }

    @Test
    fun `filedel 移除文件且同 id 后续 file 事件重新加入`() {
        val events = listOf(
            CloudSpaceEvent.FileAdded(file("a", uploadedAt = 1000L), seq = 1),
            CloudSpaceEvent.FileDeleted("a", seq = 2),
            CloudSpaceEvent.FileAdded(file("a", uploadedAt = 2000L), seq = 3),
        )

        val merged = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), events)

        assertEquals(1, merged.files.size)
        assertEquals(2000L, merged.files[0].uploadedAt)
    }

    @Test
    fun `过期文件事件不进列表但计入过期数`() {
        val events = listOf(
            CloudSpaceEvent.FileAdded(file("live", uploadedAt = 3000L), seq = 1),
            CloudSpaceEvent.FileAdded(file("old", uploadedAt = 1000L, expired = true), seq = 2),
        )

        val merged = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), events)

        assertEquals(listOf("live"), merged.files.map { it.id })
        assertEquals(1, merged.expiredCount)
        assertEquals(setOf("old"), merged.expiredIds)
    }

    @Test
    fun `先过期再手动删除不再计入过期数`() {
        val events = listOf(
            CloudSpaceEvent.FileAdded(file("old", uploadedAt = 1000L, expired = true), seq = 1),
            CloudSpaceEvent.FileDeleted("old", seq = 2),
        )

        val merged = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), events)

        assertEquals(0, merged.files.size)
        assertEquals(0, merged.expiredCount)
    }

    @Test
    fun `增量合并保留已有文件并刷新同 id 下载链接`() {
        val existing = listOf(file("a", uploadedAt = 1000L, downloadUrl = "https://old/a"))
        val events = listOf(
            CloudSpaceEvent.FileAdded(
                file("a", uploadedAt = 1000L, downloadUrl = "https://new/a"),
                seq = 5,
            ),
            CloudSpaceEvent.FileAdded(file("b", uploadedAt = 2000L), seq = 6),
        )

        val merged = CloudSpaceLogic.applyEvents(existing, emptySet(), events)

        assertEquals(listOf("b", "a"), merged.files.map { it.id })
        assertEquals("https://new/a", merged.files.first { it.id == "a" }.downloadUrl)
    }

    @Test
    fun `增量合并响应中的过期事件再次出现不重复计数`() {
        val first = listOf(
            CloudSpaceEvent.FileAdded(file("old", uploadedAt = 1000L, expired = true), seq = 1),
        )
        val mergedOnce = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), first)
        val mergedTwice = CloudSpaceLogic.applyEvents(
            mergedOnce.files,
            mergedOnce.expiredIds,
            listOf(CloudSpaceEvent.FileAdded(file("old", uploadedAt = 1000L, expired = true), seq = 1)),
        )

        assertEquals(1, mergedTwice.expiredCount)
        assertFalse(mergedTwice.files.any { it.id == "old" })
    }

    @Test
    fun `同一时间上传按 id 稳定排序`() {
        val events = listOf(
            CloudSpaceEvent.FileAdded(file("b", uploadedAt = 1000L), seq = 1),
            CloudSpaceEvent.FileAdded(file("a", uploadedAt = 1000L), seq = 2),
        )

        val merged = CloudSpaceLogic.applyEvents(emptyList(), emptySet(), events)

        assertEquals(listOf("a", "b"), merged.files.map { it.id })
    }
}
