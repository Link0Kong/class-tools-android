package com.furina.classtools.data.network

import com.furina.classtools.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * 真实 staging 冒烟（P3；默认跳过，P1 staging 就绪后手动执行）：
 *
 * ```
 * $env:CT_STAGING_SMOKE = "1"
 * $env:JAVA_HOME = "C:\AndroidToolchain\jdk17"
 * .\gradlew.bat :app:testDebugUnitTest --tests "*StagingSmokeTest*" --info
 * ```
 *
 * 用 debug 构建（REST base = staging）真实登录 + 读接口：
 * - POST rpc/class_verify（测试账号 3260799999 / 测试账号，HTTP 明文仅 debug 放行）；
 * - POST rpc/class_members（带 X-Class-Token）；
 * - GET notices / homework / site_assets（RLS 只读）。
 *
 * 红线：只连 staging（断言 REST_PROFILE=staging 且 base 为 121.196.47.2 影子路由）；
 * 只用官方测试账号；不写任何业务数据；不打印 token / 姓名 / 学号。
 */
class StagingSmokeTest {

    private val testSid = "3260799999"
    private val testName = "测试账号"

    @Test
    fun `staging 真实登录 + 读接口冒烟（测试账号，只读）`() = runBlocking {
        Assume.assumeTrue(
            "set CT_STAGING_SMOKE=1 to run staging smoke test",
            System.getenv("CT_STAGING_SMOKE") == "1",
        )
        // 安全闸门：只允许 staging（debug）地址，绝不误连生产/CloudBase
        assertEquals("staging", BuildConfig.REST_PROFILE)
        assertTrue(BuildConfig.REST_BASE_URL, BuildConfig.REST_BASE_URL.startsWith("http://121.196.47.2/v1/rdb/rest-next/"))

        var token: String? = null
        val client = ApiClient(
            baseUrl = BuildConfig.REST_BASE_URL,
            publishableKey = BuildConfig.PUBLISHABLE_KEY,
            tokenProvider = { token },
        )
        val timings = LinkedHashMap<String, Long>()

        // 1) 登录：POST rpc/class_verify（真实网络；不使用真实账号）
        val t0 = System.nanoTime()
        val verify = client.post(
            "rpc/class_verify",
            """{"p_sid":"$testSid","p_name":"$testName"}""",
            authenticated = false,
            timeoutMs = 20_000,
        )
        timings["class_verify"] = (System.nanoTime() - t0) / 1_000_000L
        assertTrue("class_verify 失败：$verify", verify is ApiResult.Success)
        val verifyBody = parseObject((verify as ApiResult.Success).value)
        assertEquals(true, verifyBody["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        token = verifyBody["token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        assertTrue("class_verify 未返回 token", !token.isNullOrBlank())

        // 2) 名单：POST rpc/class_members（带 X-Class-Token）
        val t1 = System.nanoTime()
        val members = client.post("rpc/class_members", "{}")
        timings["class_members"] = (System.nanoTime() - t1) / 1_000_000L
        assertTrue("class_members 失败：$members", members is ApiResult.Success)
        val memberList = parseArray((members as ApiResult.Success).value)
        assertTrue("class_members 应返回非空名单", memberList.isNotEmpty())

        // 3) 只读表：通知 / 作业 / 课表快照（RLS 按测试身份下发）
        val reads = listOf(
            "notices" to "notices?select=id,ts&limit=3",
            "homework" to "homework?select=id,ts&limit=3",
            "site_assets" to "site_assets?select=id,meta&id=eq.tt",
        )
        reads.forEach { (name, path) ->
            val t = System.nanoTime()
            val r = client.get(path)
            timings[name] = (System.nanoTime() - t) / 1_000_000L
            assertTrue("$name 读取失败：$r", r is ApiResult.Success)
        }

        // 只打印耗时与布尔结果（不含 token/姓名/学号/正文）
        println("STAGING_SMOKE_OK base=${BuildConfig.REST_BASE_URL} members=${memberList.size} timingsMs=$timings")
    }

    private fun parseObject(raw: String): JsonObject {
        val element = NetJson.parseToJsonElement(raw)
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.first().jsonObject
            else -> error("unexpected response shape")
        }
    }

    private fun parseArray(raw: String): JsonArray {
        val element = NetJson.parseToJsonElement(raw)
        return when (element) {
            is JsonArray -> element
            else -> error("unexpected response shape")
        }
    }
}
