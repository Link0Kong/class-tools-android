package com.furina.classtools.features.fit

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.features.export.TextBlock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体测编解码 + 历史记录合并 + 导出 payload 单测。
 */
class FitCodecHistoryTest {

    private val now = 1_794_016_800_000L

    private fun record(
        id: String,
        ts: Long,
        sex: String = "m",
        gr: String = "12",
        fields: FitFields = FitFields(h = "175", w = "65"),
        name: String = "",
        total: Double? = 100.0,
        grade: String = "优秀",
    ) = FitRecord(
        id = id,
        ts = ts,
        name = name,
        sex = sex,
        gradeBucket = gr,
        fields = fields,
        total = total,
        grade = grade,
    )

    // ---- userdata.fit -------------------------------------------------------

    @Test
    fun input_jsonRoundTrip() {
        val input = FitInput(
            sex = FitSex.F, grade = FitGrade.G34,
            h = "165", w = "52", vc = "3000", r50 = "8.5", sit = "12.5",
            jump = "180", pull = "40", durM = "3", durS = "50",
        )
        val json = FitCodec.toJson(input)
        assertEquals("f", (json["sex"] as JsonPrimitive).content)
        assertEquals("34", (json["grade"] as JsonPrimitive).content)
        assertEquals(11, json.size)
        assertEquals(input, FitCodec.inputFromJson(json))
    }

    @Test
    fun input_missingFieldsUseDefaults() {
        val parsed = FitCodec.inputFromJson(null)
        assertEquals(FitSex.M, parsed.sex)
        assertEquals(FitGrade.G12, parsed.grade)
        assertFalse(parsed.hasAny())
        val partial = FitCodec.inputFromJson(
            kotlinx.serialization.json.JsonObject(mapOf("sex" to JsonPrimitive("f"))),
        )
        assertEquals(FitSex.F, partial.sex)
        assertEquals("", partial.h)
    }

    // ---- fitrecs.data -------------------------------------------------------

    @Test
    fun record_dataRoundTrip() {
        val record = record(id = "fit-1", ts = now, name = "期中体测")
        val data = FitCodec.recordToData(record)
        assertTrue(data.contains("\"n\":\"期中体测\""))
        assertTrue(data.contains("\"gr\":\"12\""))
        val parsed = FitCodec.recordFromData(data)
        assertNotNull(parsed)
        assertEquals(record.id, parsed!!.id)
        assertEquals(record.name, parsed.name)
        assertEquals(record.total, parsed.total)
        assertEquals(record.fields, parsed.fields)
    }

    @Test
    fun record_parseBadDataReturnsNull() {
        assertNull(FitCodec.recordFromData(null))
        assertNull(FitCodec.recordFromData("not-json"))
        assertNull(FitCodec.recordFromData("""{"ts":1,"n":"x"}""")) // 缺 id
    }

    @Test
    fun record_displayHelpers() {
        val male = record(id = "a", ts = now, name = "", fields = FitFields(pull = "12", durM = "3", durS = "5"))
        assertTrue(male.displayName().startsWith("体测 "))
        assertTrue(male.detailText().contains("引体 12次"))
        assertTrue(male.detailText().contains("长跑 3'05\""))
        assertEquals("100.0", male.totalText())

        val female = record(id = "b", ts = now, sex = "f", fields = FitFields(pull = "40"))
        assertTrue(female.detailText().contains("仰卧 40次"))
        assertEquals("大一大二", female.gradeEnum.short)
    }

    // ---- 历史合并 -----------------------------------------------------------

    @Test
    fun subset_rules() {
        val old = FitFields(h = "175", w = "65")
        assertTrue(FitHistory.isSubset(old, FitFields(h = "175", w = "65", vc = "4000")))
        assertFalse(FitHistory.isSubset(old, FitFields(h = "176", w = "65")))
        assertFalse(FitHistory.isSubset(FitFields(), FitFields(h = "175")))
        assertFalse(FitHistory.isSubset(old, FitFields(h = "175")))
    }

    @Test
    fun findMergeIndex_matchesSexGradeAndSubset() {
        val list = listOf(
            record("a", 1, sex = "m", gr = "12", fields = FitFields(h = "175", w = "65")),
            record("b", 2, sex = "f", gr = "12", fields = FitFields(h = "165", w = "52")),
        )
        assertEquals(0, FitHistory.findMergeIndex(list, FitFields(h = "175", w = "65", vc = "4000"), FitSex.M, FitGrade.G12))
        assertEquals(-1, FitHistory.findMergeIndex(list, FitFields(h = "170", w = "60"), FitSex.M, FitGrade.G12))
        assertEquals(1, FitHistory.findMergeIndex(list, FitFields(h = "165", w = "52", vc = "3000"), FitSex.F, FitGrade.G12))
    }

    @Test
    fun save_mergesSubsetRecordAndMovesToFront() {
        val input = FitInput(sex = FitSex.M, grade = FitGrade.G12, h = "175", w = "65", vc = "4000", r50 = "7.0")
        val calc = FitScoring.calc(input)
        val existing = record("old", 1, name = "期中体测", fields = FitFields(h = "175", w = "65"), total = 80.0)
        val saved = FitHistory.save(listOf(existing), input, calc, id = "new", now = now)
        assertTrue(saved.merged)
        assertEquals(1, saved.records.size)
        assertEquals("old", saved.records[0].id) // 合并沿用旧 id
        assertEquals("期中体测", saved.records[0].name)
        assertEquals(now, saved.records[0].ts)
        assertEquals(calc.total, saved.records[0].total!!, 0.0001)
        assertTrue(saved.dropped.isEmpty())
    }

    @Test
    fun save_newRecordKeepsAtMostTwoAndDropsOldest() {
        val r1 = record("r1", 1, fields = FitFields(h = "170", w = "60"))
        val r2 = record("r2", 2, sex = "f", fields = FitFields(h = "165", w = "52"))
        val input = FitInput(sex = FitSex.M, grade = FitGrade.G34, h = "180", w = "70", vc = "5000")
        val calc = FitScoring.calc(input)
        val saved = FitHistory.save(listOf(r1, r2), input, calc, id = "r3", now = now)
        assertFalse(saved.merged)
        assertEquals(2, saved.records.size)
        assertEquals("r3", saved.records[0].id)
        // 只有一条被挤出（旧 r2 保留，r1 在合并检测中 sex/grade 不同也不会被合并）
        assertEquals(1, saved.dropped.size)
        assertEquals(FitRecord.MAX_RECORDS, 2)
    }

    @Test
    fun deleteRenameRestore() {
        val list = listOf(
            record("a", 1, name = "期中"),
            record("b", 2, name = "补测"),
        )
        assertEquals(1, FitHistory.deleteAt(list, 0).size)
        assertEquals("b", FitHistory.deleteAt(list, 0)[0].id)
        assertTrue(FitHistory.deleteAt(list, 9) === list)

        val renamed = FitHistory.renameAt(list, 1, "  期末体测  ")
        assertEquals("期末体测", renamed[1].name)
        assertEquals("补测", list[1].name) // 不可变
        assertTrue(FitHistory.renameAt(list, 0, "  ") === list)

        val restored = FitHistory.restoreAt(list, 0)
        assertNotNull(restored)
        assertEquals("175", restored!!.h)
        assertNull(FitHistory.restoreAt(list, 5))
    }

    // ---- 导出 ---------------------------------------------------------------

    @Test
    fun export_buildsTextBlockAndConfig() {
        val calc = FitScoring.calc(FitScoringTestInputs.perfectMale())
        val payload = FitExportFactory.create(calc, nowMillis = now, recordSuffix = "WXYZ")
        assertEquals("体测成绩", payload.title)
        assertTrue(payload.subtitle.contains("男"))
        assertEquals(5, payload.config.size)
        assertEquals("各项得分", payload.block.label)
        assertEquals(7, payload.block.lines.size)
        assertTrue(payload.fileName.startsWith("体测成绩_"))
        assertTrue(payload.fileName.contains("总分120.0"))
        assertTrue(payload.recordId.startsWith("FIT-"))
        assertTrue(payload.recordId.endsWith("WXYZ"))

        val spec = payload.toCardSpec()
        assertEquals(1, spec.blocks.size)
        assertTrue(spec.blocks.first() is TextBlock)
        assertEquals(5, spec.config.size)
    }

    @Test
    fun export_copyTextContainsSummary() {
        val calc = FitScoring.calc(FitInput(h = "175", w = "68"))
        val text = FitExportFactory.copyText(calc)
        assertTrue(text.contains("体测成绩"))
        assertTrue(text.contains("总评"))
        assertTrue(text.contains("体重指数"))
    }
}

/** 测试共用输入（避免重复构造）。 */
object FitScoringTestInputs {
    fun perfectMale(): FitInput = FitInput(
        sex = FitSex.M, grade = FitGrade.G12,
        h = "175", w = "65", vc = "6000", r50 = "6", sit = "30", jump = "300",
        pull = "30", durM = "2", durS = "30",
    )
}

