package com.furina.classtools.data.ppt

import com.furina.classtools.BuildConfig
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.NetworkClients
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * 真实 staging 冒烟：PPT 在线媒体映射 ECS `/pptfiles`（2026-11-18，默认跳过）。
 *
 * ```
 * $env:CT_STAGING_SMOKE = "1"
 * $env:JAVA_HOME = "C:\AndroidToolchain\jdk17"
 * .\gradlew.bat :app:testDebugUnitTest --tests "*PptStagingMediaSmokeTest*" --info
 * ```
 *
 * 用 debug 构建（REST = staging）真实执行：
 * 1) 测试账号登录 staging → 读取 staging `ppt_decks` 最新 ready deck 的 manifest；
 * 2) 用 [PptManifest.resolveImage] 解析 media/video/step 图片地址 → 必须映射到
 *    `http://121.196.47.2/pptfiles/ppt/...`（debug WEB_BASE_URL = http IP）；
 * 3) 真实对 ECS `/pptfiles` 发 HEAD（图片）与 Range GET（视频）→ 200 / **206**，content-type 正确。
 *
 * 红线：只连 staging + ECS IP（debug 明文）；只用测试账号；只读；不打印 token/姓名/学号。
 */
class PptStagingMediaSmokeTest {

    private val testSid = "3260799999"
    private val testName = "测试账号"

    @Test
    fun `staging PPT 在线媒体映射 ECS pptfiles（HEAD 200 与 Range 206）`() = runBlocking {
        Assume.assumeTrue(
            "set CT_STAGING_SMOKE=1 to run PPT staging media smoke",
            System.getenv("CT_STAGING_SMOKE") == "1",
        )
        assertEquals("staging", BuildConfig.REST_PROFILE)
        assertTrue(BuildConfig.WEB_BASE_URL, BuildConfig.WEB_BASE_URL == "http://121.196.47.2")

        var token: String? = null
        val api = ApiClient(
            baseUrl = BuildConfig.REST_BASE_URL,
            publishableKey = BuildConfig.PUBLISHABLE_KEY,
            tokenProvider = { token },
        )
        // 1) 测试账号登录（只读）
        val verify = api.post(
            "rpc/class_verify",
            """{"p_sid":"$testSid","p_name":"$testName"}""",
            authenticated = false,
            timeoutMs = 20_000,
        )
        assertTrue("class_verify 失败：$verify", verify is ApiResult.Success)
        token = verifyToken(NetJson.parseToJsonElement((verify as ApiResult.Success).value))
        assertTrue("staging 未返回 token", !token.isNullOrBlank())

        // 2) 取 staging 最近一个 ready deck 的 manifest
        val listResult = api.get(
            "ppt_decks?select=id,title,status,manifest,cover_url&deleted=eq.false&status=eq.ready&order=converted_at.desc&limit=1",
        )
        assertTrue("ppt_decks 读取失败：$listResult", listResult is ApiResult.Success)
        val rows = NetJson.parseToJsonElement((listResult as ApiResult.Success).value) as? JsonArray
        assertTrue("staging ppt_decks 为空（P1 数据未导入？）", rows != null && rows.isNotEmpty())
        val row = rows!!.first().toString()
        val detail = PptParsers.parseDeckDetail(NetJson, "[$row]")
        assertTrue("deck manifest 解析失败", detail != null && detail.manifest.slides.isNotEmpty())
        val manifest = detail!!.manifest

        // 3) 媒体地址必须映射到 ECS /pptfiles（debug 基址 = http IP）
        val ecsPrefix = "http://121.196.47.2/pptfiles/ppt/"
        val images = manifest.slides.flatMap { slide -> slide.steps.map { manifest.resolveImage(it.img) } }
        assertTrue("manifest 应至少有一张分步图", images.isNotEmpty())
        assertTrue("分步图应映射到 ECS：${images.first()}", images.first().startsWith(ecsPrefix))
        val thumb = manifest.thumbOf(manifest.slides.first())
        assertTrue("缩略图应映射到 ECS：$thumb", thumb.startsWith(ecsPrefix))
        val videoUrl = manifest.videoUrl
        assertTrue("视频应映射到 ECS：$videoUrl", videoUrl.startsWith(ecsPrefix))

        // 3b) 封面 cover_url（P4）：列表/详情/播放器首帧同一 helper 映射到 ECS
        val coverUrl = detail.coverDisplayUrl
        assertTrue("staging deck 应有 cover_url", detail.coverUrl.isNotBlank())
        assertTrue("封面应映射到 ECS：$coverUrl", coverUrl.startsWith(ecsPrefix))

        // 4) 真实请求 ECS 缓存：封面/图片 HEAD 200；视频 Range GET 206（边下边播依赖）
        val client = NetworkClients.attachmentClient()
        client.newCall(Request.Builder().url(coverUrl).head().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertTrue("封面 content-type=${resp.header("Content-Type")}", (resp.header("Content-Type") ?: "").startsWith("image/"))
        }
        client.newCall(Request.Builder().url(images.first()).head().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertTrue("图片 content-type=${resp.header("Content-Type")}", (resp.header("Content-Type") ?: "").startsWith("image/"))
        }
        client.newCall(
            Request.Builder().url(videoUrl).header("Range", "bytes=0-1023").build(),
        ).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals(1024L, resp.body.contentLength())
            assertTrue("视频 content-type=${resp.header("Content-Type")}", (resp.header("Content-Type") ?: "").startsWith("video/"))
        }

        // 只打印非敏感指标
        println(
            "PPT_STAGING_MEDIA_SMOKE_OK slides=${manifest.slides.size} " +
                "img=$ecsPrefix… video=$ecsPrefix… cover=$ecsPrefix… range=206 base=" + BuildConfig.WEB_BASE_URL,
        )
    }

    /** class_verify 响应（对象或单元素数组）→ token（不打印）。 */
    private fun verifyToken(element: JsonElement): String? {
        val obj = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            else -> null
        } ?: return null
        return (obj["token"] as? JsonPrimitive)?.contentOrNull
    }
}
