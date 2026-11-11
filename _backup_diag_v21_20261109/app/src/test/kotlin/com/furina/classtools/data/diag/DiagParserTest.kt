package com.furina.classtools.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断响应解析单测：service_health 5 组/状态/巡检过期降级、diag_analysis/flow、repair_tasks、
 * rpc/diag_agent_info（对象/单元素数组）、client_diag 行。
 */
class DiagParserTest {

    private val healthRaw = """
        [
          {"id":"site_static","name":"站点资源","kind":"static","status":"warn","detail":"网页已按备案安排暂停","meta":{"paused":true},"checked_at":"2026-11-08T13:30:00+08:00"},
          {"id":"site_tls","name":"站点证书","kind":"static","status":"warn","detail":"网页已按备案安排暂停","meta":{},"checked_at":"2026-11-08T13:30:00+08:00"},
          {"id":"dns_domain","name":"域名解析","kind":"cloud","status":"ok","detail":"已暂停（符合预期）","meta":{},"checked_at":"2026-11-08T13:30:00+08:00"},
          {"id":"cloudbase","name":"云端数据库","kind":"cloud","status":"ok","detail":"HTTP 200 · 186ms","meta":{},"checked_at":"2026-11-08T13:31:00+08:00"},
          {"id":"tt_cloud","name":"课表云端数据","kind":"cloud","status":"ok","detail":"快照 2 小时前更新","meta":{},"checked_at":"2026-11-08T13:31:00+08:00"},
          {"id":"cloud_health_probe","name":"云端巡检","kind":"cloud","status":"ok","detail":"本轮完成","meta":{},"checked_at":"2026-11-08T13:31:00+08:00"},
          {"id":"qqflow","name":"QQ 通知流","kind":"local","status":"down","detail":"连接失败 C:\\briefdesk\\logs\\qq.log 10.2.3.4","meta":{},"checked_at":"2026-11-08T13:30:00+08:00"},
          {"id":"unknown_row","name":"未知服务","kind":"local","status":"weird","detail":"","meta":{},"checked_at":""}
        ]
    """.trimIndent()

    /** 巡检行 checked_at 对应时刻（测试内直接用解析结果，避免手算时区误差）。 */
    private val probeAt: Long =
        requireNotNull(DiagParser.parseTimeMillis("2026-11-08T13:31:00+08:00"))

    @Test
    fun parseServiceHealth_readsRowsAndStatusMapping() {
        val rows = DiagParser.parseServiceHealth(healthRaw)
        assertEquals(8, rows.size)
        assertEquals("site_static", rows[0].id)
        assertEquals(DiagStatus.WARN, rows[0].displayStatus)
        assertEquals(DiagStatus.PASS, rows[3].displayStatus)
        assertEquals(DiagStatus.FAIL, rows.first { it.id == "qqflow" }.displayStatus)
        assertEquals(DiagStatus.INFO, rows.first { it.id == "unknown_row" }.displayStatus)
        // meta 兼容对象 → 紧凑 JSON 文本
        assertEquals("{\"paused\":true}", rows[0].meta)
    }

    @Test
    fun groupServiceHealth_ordersFixedGroupsAndOther() {
        val rows = DiagParser.parseServiceHealth(healthRaw)
        val groups = DiagParser.groupServiceHealth(rows, nowMillis = probeAt + 60_000)
        val titles = groups.map { it.title }
        assertEquals(listOf("站点与网页", "云端后台", "信息采集", "其它"), titles)
        assertEquals(3, groups[0].rows.size)
        assertEquals(3, groups[1].rows.size)
        assertEquals(1, groups[2].rows.size)
        assertEquals(1, groups[3].rows.size)
        assertEquals(DiagStatus.WARN, groups[0].worstStatus)
        assertEquals(DiagStatus.FAIL, groups[2].worstStatus)
    }

    @Test
    fun groupServiceHealth_staleCloudProbeDowngradesCloudRowsExceptCloudbase() {
        // 巡检 checked_at 比 now 早 16 分钟 → 云端行 ok→warn（cloudbase 除外）
        val rows = DiagParser.parseServiceHealth(healthRaw)
        val groups = DiagParser.groupServiceHealth(rows, nowMillis = probeAt + 16L * 60 * 1000)
        val cloud = groups.first { it.title == "云端后台" }
        val cloudbase = cloud.rows.first { it.id == "cloudbase" }
        val ttCloud = cloud.rows.first { it.id == "tt_cloud" }
        assertEquals(DiagStatus.PASS, cloudbase.displayStatus)
        assertEquals(DiagStatus.WARN, ttCloud.displayStatus)
        assertTrue(ttCloud.detail.contains("巡检未更新"))
    }

    @Test
    fun groupServiceHealth_freshCloudProbeKeepsOk() {
        val rows = DiagParser.parseServiceHealth(healthRaw)
        val groups = DiagParser.groupServiceHealth(rows, nowMillis = probeAt + 60_000)
        val cloud = groups.first { it.title == "云端后台" }
        assertEquals(DiagStatus.PASS, cloud.rows.first { it.id == "tt_cloud" }.displayStatus)
    }

    @Test
    fun parseServiceHealth_toleratesBadInput() {
        assertTrue(DiagParser.parseServiceHealth("oops").isEmpty())
        assertTrue(DiagParser.parseServiceHealth("[]").isEmpty())
        assertTrue(DiagParser.parseServiceHealth("").isEmpty())
    }

    @Test
    fun parseAnalyses_readsSuggestionFlowAndConfidence() {
        val raw = """
            [{"id":"an-1","ts":1760000000000,"platform":"android","scope":"cloud","severity":"error",
              "title":"云端写入失败","cause":"RLS 拒绝","suggestion":["检查 token","稍后重试"],
              "fixable":true,"action":"recheck_health","status":"fixed","confidence":0.87,
              "flow":[{"t":1760000000000,"text":"检测到上报","st":"done"},
                      {"t":1760000001000,"text":"本机重测","st":"done"}]}]
        """.trimIndent()
        val analyses = DiagParser.parseAnalyses(raw)
        val analysis = analyses.single()
        assertEquals("an-1", analysis.id)
        assertEquals(DiagLevel.ERROR, analysis.severityLevel)
        assertEquals("云端写入失败", analysis.title)
        assertEquals(listOf("检查 token", "稍后重试"), analysis.suggestions)
        assertTrue(analysis.fixable)
        assertEquals("recheck_health", analysis.action)
        assertEquals("fixed", analysis.status)
        assertEquals(0.87, analysis.confidence, 0.0001)
        assertEquals(2, analysis.flow.size)
        assertEquals("检测到上报", analysis.flow[0].text)
    }

    @Test
    fun parseAnalyses_toleratesJsonbStringColumns() {
        val raw = """
            [{"id":"an-2","ts":1,"severity":"warn","title":"t","cause":"c",
              "suggestion":"[\"a\",\"b\"]","flow":"[{\"text\":\"步骤\",\"st\":\"fixed\"}]","confidence":"0.5"}]
        """.trimIndent()
        val analysis = DiagParser.parseAnalyses(raw).single()
        assertEquals(listOf("a", "b"), analysis.suggestions)
        assertEquals("步骤", analysis.flow.single().text)
        assertEquals(0.5, analysis.confidence, 0.0001)
    }

    @Test
    fun parseRepairTasks_readsRecords() {
        val raw = """
            [{"id":"rt-1","created_at":1760000000000,"action":"recheck_health","reason":"延迟",
              "status":"done","claimed_at":1760000001000,"finished_at":1760000002000,
              "result":"重测完成","analysis_id":"an-1","node":"local"}]
        """.trimIndent()
        val task = DiagParser.parseRepairTasks(raw).single()
        assertEquals("rt-1", task.id)
        assertEquals("recheck_health", task.action)
        assertEquals("已完成", task.statusLabel)
        assertTrue(task.finished)
        assertEquals("重测完成", task.result)
        assertEquals("an-1", task.analysisId)
    }

    @Test
    fun parseAgentInfo_objectAndSingleElementArray() {
        val objectRaw = """{"ok":true,"online":true,"seen_at":1760000000000,"host":"local-windows","version":"1.0"}"""
        val info = DiagParser.parseAgentInfo(objectRaw)
        assertNotNull(info)
        assertTrue(info!!.online)
        assertEquals("local-windows", info.host)

        val arrayRaw = """[{"ok":true,"online":false,"seen_at":0,"host":"","version":""}]"""
        val offline = DiagParser.parseAgentInfo(arrayRaw)
        assertNotNull(offline)
        assertFalse(offline!!.online)

        assertNull(DiagParser.parseAgentInfo("""{"ok":false,"reason":"auth"}"""))
        assertNull(DiagParser.parseAgentInfo("oops"))
    }

    @Test
    fun parseClientDiagRows_readsJsonbDetailAndDevice() {
        val raw = """
            [{"id":"an-1","platform":"android","app_ver":"3.0.0","kind":"crash","level":"error",
              "message":"NullPointerException","fingerprint":"fp","ts":1760000000000,"status":"new",
              "detail":{"exception":"java.lang.NullPointerException"},
              "device":{"platform":"android","model":"x"}}]
        """.trimIndent()
        val row = DiagParser.parseClientDiagRows(raw).single()
        assertEquals("crash", row.kind)
        assertEquals("java.lang.NullPointerException", (row.detail["exception"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("android", (row.device["platform"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun parseTimeMillis_variants() {
        assertEquals(1760000000000L, DiagParser.parseTimeMillis("1760000000000"))
        assertEquals(1_791_536_000_000L, DiagParser.parseTimeMillis("2026-10-09T08:53:20Z"))
        assertNotNull(DiagParser.parseTimeMillis("2026-11-08T13:30:00+08:00"))
        assertNull(DiagParser.parseTimeMillis("not-a-time"))
    }

    @Test
    fun parseRows_handlesArrayObjectAndBadJson() {
        assertEquals(1, DiagParser.parseRows("""{"id":"a"}""")?.size)
        assertEquals(2, DiagParser.parseRows("""[{"id":"a"},{"id":"b"}]""")?.size)
        assertNull(DiagParser.parseRows("oops"))
        assertNull(DiagParser.parseRows(""))
    }
}
