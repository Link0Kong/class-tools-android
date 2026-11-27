package com.furina.classtools.data.applink

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 跨端链接用例（契约 docs/app-links/CONTRACT.md，用例 docs/app-links/cases.json）。
 *
 * 工作区里能读到共享用例时逐条比对；读不到时跳过该用例，下面的具名用例始终执行，
 * 保证单仓检出也能挡住主要回归。
 */
class AppLinkTest {

    @Test
    fun sharedContractCases() {
        val file = casesFile()
        assumeTrue("工作区内找不到 docs/app-links/cases.json，跳过共享用例", file != null)
        val payload = Json.parseToJsonElement(file!!.readText()).jsonObject
        val valid = payload.getValue("valid").jsonArray
        val invalid = payload.getValue("invalid").jsonArray
        assertTrue("共享用例为空", valid.size + invalid.size >= 30)

        for (element in valid) {
            val item = element.jsonObject
            val url = item["url"]?.jsonPrimitive?.content ?: buildUrl(item["build"]!!.jsonObject)
            val expect = item.getValue("expect").jsonObject
            val name = item["name"]?.jsonPrimitive?.content.orEmpty()
            when (val type = expect.getValue("type").jsonPrimitive.content) {
                "draw" -> {
                    val result = AppLink.parse(url)
                    assertTrue("$name: $result", result is AppLinkResult.Draw)
                    assertEquals(name, expect.getValue("id").jsonPrimitive.content, (result as AppLinkResult.Draw).recordId)
                }
                "notice" -> {
                    val result = AppLink.parse(url)
                    assertTrue("$name: $result", result is AppLinkResult.Notice)
                    assertEquals(name, expect.getValue("id").jsonPrimitive.content, (result as AppLinkResult.Notice).noticeId)
                }
                "file" -> {
                    val result = AppLink.parse(url)
                    assertTrue("$name: $result", result is AppLinkResult.File)
                    assertEquals(name, expect.getValue("id").jsonPrimitive.content, (result as AppLinkResult.File).fileId)
                }
                "course" -> {
                    val result = AppLink.parse(url)
                    assertTrue("$name: $result", result is AppLinkResult.Course)
                    val course = result as AppLinkResult.Course
                    assertEquals(name, expect.getValue("date").jsonPrimitive.content, course.date)
                    assertEquals(name, expect.getValue("start").jsonPrimitive.content.toInt(), course.start)
                    assertEquals(name, expect.getValue("end").jsonPrimitive.content.toInt(), course.end)
                    expect["name"]?.jsonPrimitive?.content?.let { assertEquals(name, it, course.name) }
                }
                "task-share" -> {
                    val result = AppLink.parse(url)
                    assertTrue("$name: $result", result is AppLinkResult.TaskShare)
                    assertEquals(name, expect.getValue("payload").jsonPrimitive.content, (result as AppLinkResult.TaskShare).payload)
                }
                else -> throw AssertionError("$name: 未覆盖的类型 $type")
            }
        }

        for (element in invalid) {
            val item = element.jsonObject
            val url = item["url"]?.jsonPrimitive?.content ?: buildUrl(item["build"]!!.jsonObject)
            val name = item["name"]?.jsonPrimitive?.content.orEmpty()
            val reason = item.getValue("reason").jsonPrimitive.content
            val result = AppLink.parse(url)
            if (reason == "not_link") {
                // 共享用例把"不是内容链接"也列入 invalid；客户端用独立分支表达（保持浏览器行为）
                assertEquals(name, AppLinkResult.NotLink, result)
            } else {
                assertTrue("$name: 期望 Invalid($reason)，实际 $result", result is AppLinkResult.Invalid)
                assertEquals(name, reason, (result as AppLinkResult.Invalid).reason)
            }
        }
    }

    @Test
    fun onlyTheContentPrefixIsTakenOver() {
        for (url in listOf(
            "https://tool.furina.fun/",
            "https://tool.furina.fun/showcase/",
            "https://tool.furina.fun/legal/web/terms.html",
            "https://tool.furina.fun/api/status",
            "https://tool.furina.fun/.well-known/assetlinks.json",
            "https://tool.furina.fun/android/latest.json",
            "https://www.tool.furina.fun/l/draw/d1234abcd",
            "https://evil.example/l/draw/d1234abcd",
            "http://tool.furina.fun/l/draw/d1234abcd",
            "classtools://com.furina.classtools/notice",
            "https://tool.furina.fun:8443/l/draw/d1234abcd",
            "https://user:pass@tool.furina.fun/l/draw/d1234abcd",
        )) {
            assertEquals(url, AppLinkResult.NotLink, AppLink.parse(url))
        }
    }

    @Test
    fun drawIdsAreNormalizedAndBounded() {
        assertEquals(AppLinkResult.Draw("d1234abcd"), AppLink.parse("https://tool.furina.fun/l/draw/D1234ABCD"))
        assertEquals(AppLinkResult.Draw("d1234abcd"), AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd?from=qr&r=abc"))
        assertEquals(AppLinkResult.Invalid("bad_id"), AppLink.parse("https://tool.furina.fun/l/draw/d1"))
        assertEquals(AppLinkResult.Invalid("bad_id"), AppLink.parse("https://tool.furina.fun/l/draw/d1234%2Fabcd"))
        assertEquals(AppLinkResult.Invalid("bad_path"), AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd/"))
    }

    @Test
    fun versionAndQueryRules() {
        assertEquals(AppLinkResult.Draw("d1234abcd"), AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd?v=1"))
        assertEquals(AppLinkResult.Invalid("bad_version"), AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd?v=2"))
        assertEquals(AppLinkResult.Invalid("bad_version"), AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd?v=x"))
        // 同名参数取第一个；未知参数忽略
        assertEquals(
            AppLinkResult.Draw("d1234abcd"),
            AppLink.parse("https://tool.furina.fun/l/draw/d1234abcd?v=1&v=9&utm=x"),
        )
    }

    @Test
    fun taskShareKeepsSnapshotInTheFragment() {
        val payload = "%7B%22t%22%3A%22%E5%A4%8D%E4%B9%A0%22%7D"
        assertEquals(
            AppLinkResult.TaskShare(payload),
            AppLink.parse("https://tool.furina.fun/l/task-share#s=$payload"),
        )
        assertEquals(AppLinkResult.Invalid("bad_payload"), AppLink.parse("https://tool.furina.fun/l/task-share"))
        assertEquals(AppLinkResult.Invalid("bad_payload"), AppLink.parse("https://tool.furina.fun/l/task-share#s="))
        assertEquals(AppLinkResult.Invalid("bad_path"), AppLink.parse("https://tool.furina.fun/l/task-share/x#s=$payload"))
    }

    @Test
    fun courseLinksCarryDatePeriodsAndOptionalHint() {
        assertEquals(
            AppLinkResult.Course("2026-11-27", 3, 4, null),
            AppLink.parse("https://tool.furina.fun/l/course/2026-11-27/3-4"),
        )
        assertEquals(
            AppLinkResult.Course("2026-11-27", 1, 12, "高等数学"),
            AppLink.parse("https://tool.furina.fun/l/course/2026-11-27/1-12?name=%E9%AB%98%E7%AD%89%E6%95%B0%E5%AD%A6"),
        )
        assertEquals(AppLinkResult.Invalid("bad_date"), AppLink.parse("https://tool.furina.fun/l/course/2026-02-30/1-2"))
        assertEquals(AppLinkResult.Invalid("bad_range"), AppLink.parse("https://tool.furina.fun/l/course/2026-11-27/4-3"))
        assertEquals(AppLinkResult.Invalid("bad_range"), AppLink.parse("https://tool.furina.fun/l/course/2026-11-27/1-13"))
    }

    private fun casesFile(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val current = dir ?: return null
            val candidate = File(current, "docs/app-links/cases.json")
            if (candidate.isFile) return candidate
            dir = current.parentFile
        }
        return null
    }

    private fun buildUrl(spec: kotlinx.serialization.json.JsonObject): String {
        val prefix = spec.getValue("prefix").jsonPrimitive.content
        val repeatChar = spec.getValue("repeat").jsonPrimitive.content
        val count = spec.getValue("count").jsonPrimitive.content.toInt()
        return prefix + repeatChar.repeat(count)
    }
}
