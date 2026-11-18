package com.furina.classtools.data.ppt

import com.furina.classtools.data.network.NetJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * manifest 集成映射单测（2026-11-18 迁移优化）：解析真实形态 manifest（OSS 香港 base）后，
 * 播放器/下载/缩略图拿到的媒体地址应全部走 ECS `/pptfiles`（与网页 resolveMediaUrl 同口径）。
 *
 * 只测纯模型层（不依赖 Android UI）；ECS 基址来自 debug BuildConfig（http IP），单测跟 debug 变体一致。
 */
class PptEcsMappingTest {

    private val oss = "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com"
    private val ecsBase = "http://121.196.47.2/pptfiles"

    private val rawManifest = """
        {
          "v": 2,
          "base": "$oss/ppt/a/dmwalrdlgevz90l/c0e5834805/",
          "src": "$oss/ppt/src/dmwalrdlgevz90l/source.pptx",
          "w": 1600, "h": 900,
          "slides": [
            {"thumb": "t1.webp", "steps": [{"img": "s1-1.webp",
              "media": [{"s": "media/m0.mp4", "k": "v", "x": 0.1, "y": 0.2, "w": 0.5, "h": 0.4}]}]}
          ],
          "media": [{"s": "media/m0.mp4", "k": "v", "w": 1280, "h": 720, "dur": 12.4}],
          "video": {"s": "deck.mp4", "w": 1280, "h": 720, "dur": 303.6, "chapters": [{"i": 0, "t": 0}]},
          "files": {
            "compat": {"s": "compat.pptx", "ext": "pptx", "size": 39063744},
            "pdf": {"s": "preview.pdf", "size": 1105861, "pages": 16},
            "source": {"s": "original.pptx", "ext": "pptx", "size": 38016533}
          },
          "media_files": [
            {"s": "media/本草孝心_视频1_第7页.mp4", "kind": "video", "size": 20158796, "dur": 150.92, "slides": [7]}
          ]
        }
    """.trimIndent()

    @Test
    fun `manifest 媒体 URL 全部映射到 ECS pptfiles`() {
        val manifest = PptParsers.parseManifest(NetJson, rawManifest)
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/s1-1.webp", manifest.resolveImage("s1-1.webp"))
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/t1.webp", manifest.thumbOf(manifest.slides[0]))
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/deck.mp4", manifest.videoUrl)
        assertEquals(
            "$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/media/m0.mp4",
            manifest.resolveImage(manifest.slides[0].steps[0].media[0].src),
        )
        assertEquals(
            "$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/media/本草孝心_视频1_第7页.mp4",
            manifest.mediaUrl(manifest.mediaFiles[0]),
        )
        // 下载文件（兼容版/PDF/原始）同样走缓存
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/compat.pptx", manifest.fileUrl(manifest.files!!.compat!!))
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/preview.pdf", manifest.fileUrl(manifest.files!!.pdf!!))
        assertEquals("$ecsBase/ppt/a/dmwalrdlgevz90l/c0e5834805/original.pptx", manifest.fileUrl(manifest.files!!.source!!))
        // 原始文件（source）也映射
        assertEquals("$ecsBase/ppt/src/dmwalrdlgevz90l/source.pptx", manifest.resolveImage(manifest.src))
    }

    @Test
    fun `外域 base 与 data 内嵌资源保持原样（兼容旧 deck 与第三方地址）`() {
        val other = PptParsers.parseManifest(
            NetJson,
            """{"base":"https://cdn.example.com/ppt/a/b/","slides":[{"steps":[{"img":"s.webp"}]}]}""",
        )
        assertEquals("https://cdn.example.com/ppt/a/b/s.webp", other.resolveImage("s.webp"))
        assertEquals(
            "data:image/webp;base64,AAAA",
            other.resolveImage("data:image/webp;base64,AAAA"),
        )
        assertTrue(other.videoUrl.isEmpty())
    }
}
