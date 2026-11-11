package com.furina.classtools.data.ppt

import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PPT 响应解析容错单测（契约 §4/§5）。
 *
 * 覆盖：正常清单、未知字段、缺字段默认、坏 JSON、manifest 为 JSON 字符串、空 step 页过滤、
 * 热点坐标夹取、心跳过期判定、列表数值兼容字符串。
 */
class PptParsersTest {

    private val now = 1_791_590_400_000L

    private val fullManifest = """
        {
          "v": 1,
          "base": "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/ppt/a/dmg2k4x9ab12c/b_abc/",
          "src": "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/ppt/src/dmg2k4x9ab12c/source.pptx",
          "w": 1600, "h": 900,
          "tr_unknown_field": "ignored",
          "slides": [
            {
              "tr": "fade", "trd": 450, "notes": "第一页备注", "thumb": "t1.webp",
              "steps": [
                { "img": "s1-1.webp",
                  "media": [
                    { "s": "m0.mp4", "k": "v", "x": 0.12, "y": 0.15, "w": 0.76, "h": 0.70 }
                  ]
                },
                { "img": "s1-2.webp" }
              ]
            },
            { "tr": "push", "trd": 360,
              "steps": [ { "img": "s2-1.webp" } ]
            }
          ],
          "media": [
            { "s": "m0.mp4", "k": "v", "w": 1280, "h": 720, "dur": 12.4 },
            { "s": "m1.m4a", "k": "a", "dur": 3.2 }
          ]
        }
    """.trimIndent()

    // ------------------------------------------------------------ manifest

    @Test
    fun parseManifest_full() {
        val manifest = PptParsers.parseManifest(NetJson, fullManifest)
        assertEquals(1, manifest.v)
        assertTrue(manifest.base.endsWith("/"))
        assertTrue(manifest.src.contains("/ppt/src/"))
        assertEquals(1600, manifest.width)
        assertEquals(900, manifest.height)
        assertEquals(2, manifest.slides.size)
        assertEquals(2, manifest.stepCounts[0])
        assertEquals(1, manifest.stepCounts[1])
        assertEquals(3, manifest.totalSteps)
        assertEquals("第一页备注", manifest.slides[0].notes)
        assertEquals("t1.webp", manifest.slides[0].thumb)
        assertEquals("fade", manifest.slides[0].tr)
        assertEquals(450, manifest.slides[0].trd)
        assertEquals("push", manifest.slides[1].tr)

        val hotspot = manifest.slides[0].steps[0].media.single()
        assertEquals("m0.mp4", hotspot.src)
        assertEquals(PptMediaKind.VIDEO, hotspot.kind)
        assertEquals(0.12f, hotspot.x, 0.0001f)
        assertEquals(0.76f, hotspot.w, 0.0001f)

        val video = manifest.media["m0.mp4"]!!
        assertEquals(PptMediaKind.VIDEO, video.kind)
        assertEquals(1280, video.width)
        assertEquals(12.4, video.durationSec, 0.0001)
        assertEquals(PptMediaKind.AUDIO, manifest.media["m1.m4a"]!!.kind)
        assertTrue(manifest.isPlayable)
    }

    @Test
    fun parseManifest_emptyOrBadIsNotPlayable() {
        listOf("{}", "", "null", "oops", "[]", """{"slides":[]}""").forEach { raw ->
            val manifest = PptParsers.parseManifest(NetJson, raw)
            assertFalse("raw=$raw 应为空 manifest", manifest.isPlayable)
            assertEquals(0, manifest.totalSteps)
        }
    }

    @Test
    fun parseManifest_missingFieldsUseDefaults() {
        val raw = """{"slides":[{"steps":[{"img":"s1.webp"}]}]}"""
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertEquals(1600, manifest.width)
        assertEquals(900, manifest.height)
        assertEquals("fade", manifest.slides[0].tr)
        assertEquals(450, manifest.slides[0].trd)
        assertEquals("", manifest.slides[0].notes)
        assertEquals("", manifest.slides[0].thumb)
        assertEquals("", manifest.base)
        assertTrue(manifest.media.isEmpty())
        assertTrue(manifest.isPlayable)
    }

    @Test
    fun parseManifest_skipsBadSlidesAndSteps() {
        val raw = """
            {"slides":[
              {"steps":[{"img":""},{"noImg":1}]},
              {"steps":[]},
              {"steps":[{"img":"ok.webp"}]},
              {"noSteps":true}
            ]}
        """.trimIndent()
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertEquals(1, manifest.slides.size)
        assertEquals(1, manifest.totalSteps)
    }

    @Test
    fun parseManifest_acceptsJsonStringAndDatabaseRow() {
        // manifest 为 JSON 字符串（PostgREST 若按 text 返回）→ 正确转义后再解析
        val asString = buildJsonObject { put("manifest", JsonPrimitive(fullManifest)) }.toString()
        assertEquals(2, PptParsers.parseManifest(NetJson, asString).slides.size)

        // 传数据库行（自动取 manifest 字段）
        val row = """[{"manifest":$fullManifest,"title":"班会"}]"""
        assertEquals(2, PptParsers.parseManifest(NetJson, row).slides.size)

        // 直接传 manifest 对象
        assertEquals(2, PptParsers.parseManifest(NetJson, fullManifest).slides.size)
    }

    @Test
    fun parseManifest_hotspotCoordinatesClampedAndDefaults() {
        val raw = """
            {"slides":[{"steps":[{"img":"s.webp","media":[
              {"s":"a.mp4","k":"v","x":0.9,"y":0.9,"w":0.5,"h":0.5},
              {"s":"b.mp4"},
              {"s":"","k":"v"},
              {"s":"c.mp4","k":"a"}
            ]}]}]}
        """.trimIndent()
        val step = PptParsers.parseManifest(NetJson, raw).slides[0].steps[0]
        assertEquals(3, step.media.size) // 空 src 丢弃
        val first = step.media[0]
        assertEquals(0.5f, first.x, 0.0001f)
        assertEquals(0.5f, first.y, 0.0001f)
        val centered = step.media[1]
        assertEquals(0.36f, centered.x, 0.0001f)
        assertEquals(0.16f, centered.h, 0.0001f)
        assertEquals(PptMediaKind.AUDIO, step.media[2].kind)
    }

    @Test
    fun parseManifest_thumbFallbackToFirstStep() {
        val raw = """{"base":"https://x/a/","slides":[{"steps":[{"img":"s.webp"}]}]}"""
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertEquals("https://x/a/s.webp", manifest.thumbOf(manifest.slides[0]))
        val withThumb = raw.replace("\"steps\"", "\"thumb\":\"t.webp\",\"steps\"")
        val manifest2 = PptParsers.parseManifest(NetJson, withThumb)
        assertEquals("https://x/a/t.webp", manifest2.thumbOf(manifest2.slides[0]))
    }

    @Test
    fun resolveImage_absoluteAndRelative() {
        val manifest = PptParsers.parseManifest(NetJson, fullManifest)
        assertEquals(manifest.base + "s1-1.webp", manifest.resolveImage("s1-1.webp"))
        assertEquals("https://cdn/x.webp", manifest.resolveImage("https://cdn/x.webp"))
        assertEquals("data:image/png;base64,AAAA", manifest.resolveImage("data:image/png;base64,AAAA"))
    }

    // ------------------------------------------------------------ 列表

    private val listJson = """
        [{"id":"dmg2k4x9ab12c","title":"班会 PPT","owner_sid":"3260707024","owner_name":"陈子麒",
          "status":"READY","progress":"100","error":"","slide_count":12,"step_count":37,"aspect":"16:9",
          "cover_url":"https://x/c.webp","source_name":"班会.pptx","source_size":"12345678",
          "created_at":1791590400000,"converted_at":1791590500000,"client":"android","unknown":1},
         {"id":"","title":"坏行"},
         {"title":"没有 id"},
         {"id":"dabc123456","status":"queued","progress":null,"slide_count":-3}]
    """.trimIndent()

    @Test
    fun parseDeckList_skipsBadRowsAndNormalizes() {
        val decks = PptParsers.parseDeckList(NetJson, listJson)
        assertEquals(2, decks.size)

        val first = decks[0]
        assertEquals("dmg2k4x9ab12c", first.id)
        assertEquals("班会 PPT", first.title)
        assertEquals("3260707024", first.ownerSid)
        assertEquals("ready", first.status) // 小写归一
        assertEquals(100, first.progress)
        assertEquals(12, first.slideCount)
        assertEquals(37, first.stepCount)
        assertEquals(12345678L, first.sourceSize)
        assertEquals(1791590400000L, first.createdAt)
        assertEquals("android", first.client)

        val second = decks[1]
        assertEquals("dabc123456", second.id)
        assertEquals("queued", second.status)
        assertEquals(0, second.progress) // null → 0
        assertEquals(0, second.slideCount) // 负数 → 0
        assertEquals("", second.title)
    }

    @Test
    fun parseDeckList_badJsonAndEmpty() {
        assertTrue(PptParsers.parseDeckList(NetJson, "oops").isEmpty())
        assertTrue(PptParsers.parseDeckList(NetJson, "[]").isEmpty())
        assertTrue(PptParsers.parseDeckList(NetJson, null).isEmpty())
        assertNull(PptParsers.parseCreatedDeck(NetJson, "[]"))
        assertNotNull(PptParsers.parseCreatedDeck(NetJson, listJson))
    }

    // ------------------------------------------------------------ 播放详情

    @Test
    fun parseDeckDetail_ready() {
        val raw = """[{"title":"班会 PPT","slide_count":2,"step_count":3,"aspect":"16:9",
                       "source_name":"班会.pptx","source_key":"ppt/src/x/source.pptx",
                       "converted_at":1791590500000,"manifest":$fullManifest}]"""
        val detail = PptParsers.parseDeckDetail(NetJson, raw)!!
        assertEquals("班会 PPT", detail.title)
        assertTrue(detail.isPlayable)
        assertEquals(3, detail.stepCount)
        assertTrue(detail.sourceUrl.contains("/ppt/src/"))
    }

    @Test
    fun parseDeckDetail_notReadyOrBad() {
        val emptyManifest = PptParsers.parseDeckDetail(NetJson, """[{"title":"x","manifest":{}}]""")!!
        assertFalse(emptyManifest.isPlayable)
        assertNull(PptParsers.parseDeckDetail(NetJson, "oops"))
        assertNull(PptParsers.parseDeckDetail(NetJson, "[]"))
    }

    // ------------------------------------------------------------ 心跳

    @Test
    fun parseAgentStatus_onlineAndStale() {
        val fresh = """[{"body":{"online":true,"seen_at":${now - 1_000},"queued":2,"version":"1.0","host":"PC"},
                          "updated_at":"2026-11-09T12:00:00+00:00"}]"""
        val status = PptParsers.parseAgentStatus(NetJson, fresh)!!
        assertEquals(2, status.queued)
        assertEquals("1.0", status.version)
        assertTrue(status.isOnline(now))
        assertTrue(status.isOnline(now, offlineAfterMs = 10_000))

        // 超过 180s → 离线
        val stale = """[{"body":{"online":true,"seen_at":${now - 181_000},"queued":0}}]"""
        val staleStatus = PptParsers.parseAgentStatus(NetJson, stale)!!
        assertFalse(staleStatus.isOnline(now))
        assertTrue(staleStatus.seenAt > 0)

        // body 为 JSON 字符串也兼容
        val asString = """[{"body":"{\"online\":false,\"seen_at\":0}"}]"""
        val offline = PptParsers.parseAgentStatus(NetJson, asString)!!
        assertFalse(offline.isOnline(now))
    }

    @Test
    fun parseAgentStatus_missingSeenAtFallsBackToUpdatedAt() {
        val raw = """[{"body":{"online":true,"queued":1},
                       "updated_at":"2026-11-09T12:00:00+00:00"}]"""
        val status = PptParsers.parseAgentStatus(NetJson, raw)!!
        assertTrue(status.seenAt > 0)
        assertTrue(status.isOnline(status.seenAt + 1_000))
        assertFalse(status.isOnline(status.seenAt + 181_000))
    }

    @Test
    fun parseAgentStatus_badData() {
        assertNull(PptParsers.parseAgentStatus(NetJson, "oops"))
        assertNull(PptParsers.parseAgentStatus(NetJson, "[]"))
        assertNull(PptParsers.parseAgentStatus(NetJson, """[{"no_body":1}]"""))
        assertNull(PptParsers.parseAgentStatus(NetJson, """[{"body":"oops"}]"""))
    }

    @Test
    fun jsonHelpers_tolerant() {
        val json = NetJson
        assertEquals("12", PptParsers.stringOf(json.parseToJsonElement("12")))
        assertEquals(12, PptParsers.intOf(json.parseToJsonElement("\"12\""))!!)
        assertEquals(12, PptParsers.intOf(json.parseToJsonElement("12.9"))!!)
        assertNull(PptParsers.intOf(json.parseToJsonElement("null")))
        assertNull(PptParsers.intOf(json.parseToJsonElement("\"abc\"")))
        assertEquals(1_791_590_400_000L, PptParsers.parseIsoMillis("1791590400000"))
        assertEquals(1_794_225_600_000L, PptParsers.parseIsoMillis("2026-11-09T12:00:00Z"))
        assertNull(PptParsers.parseIsoMillis("bad"))
        assertEquals(true, PptParsers.boolOf(json.parseToJsonElement("true")))
        assertEquals(false, PptParsers.boolOf(json.parseToJsonElement("\"FALSE\"")))
        assertNull(PptParsers.boolOf(json.parseToJsonElement("\"maybe\"")))
    }

    @Test
    fun parseIsoMillis_headerFormat() {
        assertNull(PptParsers.parseIsoMillis(null))
        assertNull(PptParsers.parseIsoMillis(""))
    }

    // ------------------------------------------------------------ manifest.video（v1.2 §11.1）

    @Test
    fun parseManifest_video_full() {
        val raw = """
            {"base":"https://x/ppt/a/d1/b/","w":1600,"h":900,
             "slides":[{"steps":[{"img":"s1.webp"}]},{"steps":[{"img":"s2.webp"}]},{"steps":[{"img":"s3.webp"}]}],
             "video":{"s":"deck.mp4","w":1280,"h":720,"dur":342.5,"q":"720p",
                      "chapters":[{"i":0,"t":0.0},{"i":1,"t":8.7},{"i":2,"t":15.2}]}}
        """.trimIndent()
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertTrue(manifest.hasVideo)
        assertTrue(manifest.isPlayable)
        val video = manifest.video!!
        assertEquals("deck.mp4", video.src)
        assertEquals(1280, video.width)
        assertEquals(720, video.height)
        assertEquals(342.5, video.durationSec, 0.0001)
        assertEquals("720p", video.quality)
        assertEquals(listOf(0, 1, 2), video.chapters.map { it.index })
        assertEquals(8.7, video.chapters[1].startSec, 0.0001)
        assertEquals("https://x/ppt/a/d1/b/deck.mp4", manifest.videoUrl)
        // 未知字段忽略
        val extra = raw.replace("\"q\":\"720p\"", "\"q\":\"720p\",\"future\":{\"a\":1}")
        assertTrue(PptParsers.parseManifest(NetJson, extra).hasVideo)
    }

    @Test
    fun parseManifest_video_absentOrInvalidIsNull() {
        listOf(
            """{"slides":[{"steps":[{"img":"s1.webp"}]}]}""",
            """{"slides":[{"steps":[{"img":"s1.webp"}]}],"video":null}""",
            """{"slides":[{"steps":[{"img":"s1.webp"}]}],"video":"oops"}""",
            """{"slides":[{"steps":[{"img":"s1.webp"}]}],"video":{}}""",
            """{"slides":[{"steps":[{"img":"s1.webp"}]}],"video":{"s":""}}""",
        ).forEach { raw ->
            val manifest = PptParsers.parseManifest(NetJson, raw)
            assertNull("raw=$raw 应为无视频", manifest.video)
            assertFalse(manifest.hasVideo)
            assertEquals("", manifest.videoUrl)
            assertTrue("旧 manifest 仍可播放", manifest.isPlayable)
        }
    }

    @Test
    fun parseManifest_video_chaptersTolerant() {
        // 越界页丢弃、重复页保留最早、乱序按页排序、时间单调、非法项忽略
        val raw = """
            {"slides":[{"steps":[{"img":"s1.webp"}]},{"steps":[{"img":"s2.webp"}]},{"steps":[{"img":"s3.webp"}]}],
             "video":{"s":"deck.mp4","dur":100,
              "chapters":[
                {"i":2,"t":20.0},
                {"i":1,"t":9.0},
                {"i":1,"t":8.0},
                {"i":9,"t":5.0},
                {"i":0,"t":0.0},
                {"i":1},
                {"t":3.0},
                {"i":0,"t":-1.0}
              ]}}
        """.trimIndent()
        val video = PptParsers.parseManifest(NetJson, raw).video!!
        assertEquals(listOf(0, 1, 2), video.chapters.map { it.index })
        assertEquals(0.0, video.chapters[0].startSec, 0.0001)
        assertEquals(8.0, video.chapters[1].startSec, 0.0001) // 重复页取最早
        assertEquals(20.0, video.chapters[2].startSec, 0.0001)

        // 非单调时间会被夹成单调不减
        val nonMonotonic = """
            {"slides":[{"steps":[{"img":"a"}]},{"steps":[{"img":"b"}]}],
             "video":{"s":"d.mp4","chapters":[{"i":0,"t":10.0},{"i":1,"t":3.0}]}}
        """.trimIndent()
        val chapters = PptParsers.parseManifest(NetJson, nonMonotonic).video!!.chapters
        assertEquals(10.0, chapters[1].startSec, 0.0001)

        // 无章节/无 duration 也不报错
        val minimal = PptParsers.parseManifest(NetJson, """{"slides":[{"steps":[{"img":"a"}]}],"video":{"s":"d.mp4"}}""")
        assertTrue(minimal.hasVideo)
        assertEquals(0, minimal.video!!.chapters.size)
        assertEquals(0.0, minimal.video!!.durationSec, 0.0001)
        assertEquals(0, minimal.video!!.width)
    }

    @Test
    fun parseDeckDetail_videoSurvivesDbRow() {
        val raw = """[{"title":"班会","slide_count":2,"step_count":2,
            "manifest":{"w":1600,"h":900,
              "slides":[{"steps":[{"img":"s1.webp"}]},{"steps":[{"img":"s2.webp"}]}],
              "video":{"s":"deck.mp4","w":1280,"h":720,"dur":30.0,"chapters":[{"i":0,"t":0.0},{"i":1,"t":10.0}]}}}]"""
        val detail = PptParsers.parseDeckDetail(NetJson, raw)!!
        assertTrue(detail.manifest.hasVideo)
        assertEquals(30.0, detail.manifest.video!!.durationSec, 0.0001)
        assertEquals("deck.mp4", detail.manifest.video!!.src)
    }

    // ------------------------------------------------------------ manifest v2.0 下载文件（§12.2）

    @Test
    fun parseManifest_v2_filesAndCompat() {
        val raw = """
            {"v":2,"base":"https://x/ppt/a/d1/b/","w":960,"h":540,"slides":[],
             "files":{
               "compat":{"s":"compat.pptx","size":18432000,"sha256":"aaa","ext":"pptx","name":"班会 兼容版.pptx"},
               "pdf":{"s":"preview.pdf","size":6291456,"sha256":"bbb","ext":"pdf","name":"班会.pdf","pages":16},
               "source":{"s":"original.pptx","size":17825792,"sha256":"ccc","ext":"pptx","name":"班会.pptx"}
             },
             "compat":{"warnings":["外链视频 1 处未随文件携带（第 7 页）"],"fonts_embedded":2,"converted_from":"ppt"}}
        """.trimIndent()
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertEquals(2, manifest.v)
        assertFalse("compat 模式不再生成分步图", manifest.isPlayable)
        assertTrue(manifest.hasCompatFile)
        assertTrue(manifest.hasPdfFile)
        assertFalse(manifest.isLegacyDeck)
        assertFalse(manifest.hasPreview)
        val files = manifest.files!!
        assertEquals("compat.pptx", files.compat!!.src)
        assertEquals(18_432_000L, files.compat!!.size)
        assertEquals("班会 兼容版.pptx", files.compat!!.name)
        assertEquals(16, files.pdf!!.pages)
        assertEquals("original.pptx", files.source!!.src)
        assertEquals("https://x/ppt/a/d1/b/compat.pptx", manifest.fileUrl(files.compat!!))
        val compat = manifest.compat!!
        assertEquals(1, compat.warnings.size)
        assertTrue(compat.warnings[0].contains("外链视频"))
        assertEquals(2, compat.fontsEmbedded)
        assertEquals("ppt", compat.convertedFrom)
    }

    @Test
    fun parseManifest_v2_filesTolerant() {
        // files 缺失 / 非对象 / 全空项 → null（旧 deck）
        listOf(
            """{"slides":[]}""",
            """{"slides":[],"files":null}""",
            """{"slides":[],"files":"oops"}""",
            """{"slides":[],"files":{}}""",
            """{"slides":[],"files":{"compat":{},"pdf":{"s":""}}}""",
        ).forEach { raw ->
            val manifest = PptParsers.parseManifest(NetJson, raw)
            assertNull("raw=$raw 应无 files", manifest.files)
            assertTrue(manifest.isLegacyDeck)
        }
        // 部分文件 + 非法数值容错
        val partial = PptParsers.parseManifest(
            NetJson,
            """{"slides":[],"files":{"compat":{"s":"c.pptx","size":-5,"pages":"x"}}}""",
        )
        assertEquals(0L, partial.files!!.compat!!.size)
        assertNull(partial.files!!.pdf)
        // compat 区块非法 → null
        assertNull(PptParsers.parseCompat(NetJson.parseToJsonElement("\"oops\"")))
    }

    @Test
    fun parseManifest_v2_compatWarningsTolerant() {
        val raw = """
            {"slides":[],"files":{"compat":{"s":"c.pptx"}},
             "compat":{"warnings":[" 提示1 ", "", 123, " 提示1 ", "提示2"],"fonts_embedded":"3","converted_from":""}}
        """.trimIndent()
        val compat = PptParsers.parseManifest(NetJson, raw).compat!!
        assertEquals(listOf("提示1", "123", "提示2"), compat.warnings)
        assertEquals(3, compat.fontsEmbedded)
        assertNull(compat.convertedFrom)

        // 条数上限 20 + 超长截断
        val many = (0 until 30).joinToString(",") { "\"w$it\"" }
        val long = "x".repeat(500)
        val capped = PptParsers.parseCompat(
            NetJson.parseToJsonElement("""{"warnings":[$many,"$long"]}"""),
        )!!
        assertEquals(20, capped.warnings.size)
    }

    @Test
    fun parseDeckList_v2Tags() {
        val raw = """
            [{"id":"d1","status":"ready","files":{"compat":{"s":"c.pptx"},"pdf":{"s":"p.pdf"}}},
             {"id":"d2","status":"ready","files":{"compat":{"s":"c.pptx"}}},
             {"id":"d3","status":"ready","files":null},
             {"id":"d4","status":"ready","manifest":{"files":{"compat":{"s":"c.pptx"},"pdf":{"s":"p.pdf"}}}},
             {"id":"d5","status":"ready","files":{"compat":{"s":""},"pdf":{}}},
             {"id":"d6","status":"ready","files":"oops"}]
        """.trimIndent()
        val decks = PptParsers.parseDeckList(NetJson, raw).associateBy { it.id }
        assertTrue(decks["d1"]!!.hasCompatFiles)
        assertTrue(decks["d1"]!!.hasPdfFile)
        assertTrue(decks["d2"]!!.hasCompatFiles)
        assertFalse(decks["d2"]!!.hasPdfFile)
        assertFalse(decks["d3"]!!.hasCompatFiles)
        // 完整 manifest 行（建行响应/缓存）也能解析标签
        assertTrue(decks["d4"]!!.hasCompatFiles)
        assertTrue(decks["d4"]!!.hasPdfFile)
        // 空 s / 非对象 → 无标签
        assertFalse(decks["d5"]!!.hasCompatFiles)
        assertFalse(decks["d5"]!!.hasPdfFile)
        assertFalse(decks["d6"]!!.hasCompatFiles)
    }

    @Test
    fun parseDeckDetail_v2StatusFields() {
        val raw = """[{"title":"班会","status":"processing","progress":61,"error":"","owner_name":"陈子麒",
                       "source_size":123456,"slide_count":16,"manifest":{"v":2,"files":{"compat":{"s":"c.pptx"}}}}]"""
        val detail = PptParsers.parseDeckDetail(NetJson, raw)!!
        assertEquals("processing", detail.status)
        assertEquals(61, detail.progress)
        assertEquals("陈子麒", detail.ownerName)
        assertEquals(123456L, detail.sourceSize)
        assertTrue(detail.manifest.hasCompatFile)
        assertFalse(detail.isReady)
        // ready 判定
        val ready = PptParsers.parseDeckDetail(
            NetJson,
            """[{"title":"x","status":"ready","manifest":{"v":2,"files":{"compat":{"s":"c.pptx"}}}}]""",
        )!!
        assertTrue(ready.isReady)
    }

    // ------------------------------------------------------------ manifest.media_files（v2.3 §12.8）

    @Test
    fun parseManifest_mediaFiles_full() {
        val raw = """
            {"v":2,"base":"https://x/ppt/a/d1/b/","w":960,"h":540,"slides":[],
             "files":{"compat":{"s":"compat.pptx","size":100,"ext":"pptx"}},
             "media_files":[
               {"s":"media/deck_视频1_第7页.mp4","name":"deck_视频1_第7页.mp4","size":20158796,
                "sha256":"aaa","kind":"video","dur":150.92,"w":1024,"h":576,"slides":[7],"src":"media1.mp4"},
               {"s":"media/deck_音频1_第3页.m4a","name":"","size":1048576,
                "kind":"audio","dur":45.5,"slides":[3, 12],"src":"media3.m4a"}
             ]}
        """.trimIndent()
        val manifest = PptParsers.parseManifest(NetJson, raw)
        assertTrue(manifest.hasMediaFiles)
        assertEquals(2, manifest.mediaFiles.size)
        val first = manifest.mediaFiles[0]
        assertEquals("media/deck_视频1_第7页.mp4", first.src)
        assertEquals("deck_视频1_第7页.mp4", first.name)
        assertEquals(20_158_796L, first.size)
        assertEquals("aaa", first.sha256)
        assertEquals(com.furina.classtools.data.ppt.PptMediaKind.VIDEO, first.kind)
        assertEquals(150.92, first.durationSec, 0.0001)
        assertEquals(1024, first.width)
        assertEquals(576, first.height)
        assertEquals(listOf(7), first.slides)
        assertEquals("media1.mp4", first.sourceName)
        assertEquals(
            "https://x/ppt/a/d1/b/media/deck_视频1_第7页.mp4",
            manifest.mediaUrl(first),
        )
        val audio = manifest.mediaFiles[1]
        assertEquals(com.furina.classtools.data.ppt.PptMediaKind.AUDIO, audio.kind)
        assertEquals(listOf(3, 12), audio.slides)
        assertEquals("", audio.name)
    }

    @Test
    fun parseMediaFiles_missingOrInvalidIsEmpty() {
        listOf(
            """{"slides":[]}""",
            """{"slides":[],"media_files":null}""",
            """{"slides":[],"media_files":{}}""",
            """{"slides":[],"media_files":"oops"}""",
            """{"slides":[],"media_files":[]}""",
        ).forEach { raw ->
            val manifest = PptParsers.parseManifest(NetJson, raw)
            assertTrue("raw=$raw 应无 media_files", manifest.mediaFiles.isEmpty())
            assertFalse(manifest.hasMediaFiles)
        }
        // 坏项跳过、未知 kind 按扩展名推断、非法数值归零
        val raw = """
            {"slides":[],"media_files":[
              {"s":""},
              {"s":"media/a.mp4","kind":"weird","dur":-5,"w":-1,"h":0,"size":-9,"slides":[0,-1,99999,"x"]},
              {"s":"media/b.m4a"},
              {"s":"media/c.mp4","kind":"audio"},
              "oops"
            ]}
        """.trimIndent()
        val files = PptParsers.parseManifest(NetJson, raw).mediaFiles
        assertEquals(3, files.size)
        assertEquals(com.furina.classtools.data.ppt.PptMediaKind.VIDEO, files[0].kind) // 未知扩展名 → 视频
        assertEquals(0L, files[0].size)
        assertEquals(0.0, files[0].durationSec, 0.0001)
        assertEquals(listOf(0), files[0].slides) // 只保留 0..10000 的整数
        assertEquals(com.furina.classtools.data.ppt.PptMediaKind.AUDIO, files[1].kind) // m4a 推断音频
        assertEquals(com.furina.classtools.data.ppt.PptMediaKind.AUDIO, files[2].kind) // kind=audio 优先
    }

    @Test
    fun parseMediaFiles_capsCountAndSlides() {
        val many = (0 until 60).joinToString(",") { """{"s":"media/f$it.mp4","slides":[${it}]}""" }
        val files = PptParsers.parseManifest(NetJson, """{"slides":[],"media_files":[$many]}""").mediaFiles
        assertEquals(PptParsers.MAX_MEDIA_FILES, files.size)

        val slides = (1..40).joinToString(",") { it.toString() }
        val one = PptParsers.parseManifest(
            NetJson,
            """{"slides":[],"media_files":[{"s":"media/a.mp4","slides":[$slides]}]}""",
        ).mediaFiles.single()
        assertEquals(PptParsers.MAX_MEDIA_SLIDES, one.slides.size)
    }
}
