package com.furina.classtools.features.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体测评分单测（对齐 mp/utils/fit.js ftCalc；国标 2014 大学组）。
 */
class FitScoringTest {

    /** 男大一大二满分输入（BMI 正常 + 各单项满分档 + 加分项）。 */
    private fun perfectMaleInput(): FitInput = FitInput(
        sex = FitSex.M,
        grade = FitGrade.G12,
        h = "175",
        w = "65",
        vc = "6000",
        r50 = "6",
        sit = "30",
        jump = "300",
        pull = "30",
        durM = "2",
        durS = "30",
    )

    // ---- 单项评分 -----------------------------------------------------------

    @Test
    fun score_vcBoundaries() {
        val table = FitTables.vc.column(FitSex.M, FitGrade.G12)
        assertEquals(100, FitScoring.scoreOf(FitTables.vc, 5040.0, FitSex.M, FitGrade.G12))
        assertEquals(95, FitScoring.scoreOf(FitTables.vc, 4920.0, FitSex.M, FitGrade.G12))
        assertEquals(10, FitScoring.scoreOf(FitTables.vc, 2300.0, FitSex.M, FitGrade.G12))
        assertEquals(0, FitScoring.scoreOf(FitTables.vc, 2299.0, FitSex.M, FitGrade.G12))
        assertEquals(100, FitScoring.scoreOf(FitTables.vc, table.first(), FitSex.M, FitGrade.G12))
    }

    @Test
    fun score_50mIsLowerBetter() {
        assertEquals(100, FitScoring.scoreOf(FitTables.r50, 6.7, FitSex.M, FitGrade.G12))
        assertEquals(95, FitScoring.scoreOf(FitTables.r50, 6.8, FitSex.M, FitGrade.G12))
        assertEquals(10, FitScoring.scoreOf(FitTables.r50, 10.1, FitSex.M, FitGrade.G12))
        assertEquals(0, FitScoring.scoreOf(FitTables.r50, 11.0, FitSex.M, FitGrade.G12))
    }

    @Test
    fun score_durIsLowerBetter() {
        assertEquals(100, FitScoring.scoreOf(FitTables.dur, 197.0, FitSex.M, FitGrade.G12))
        assertEquals(95, FitScoring.scoreOf(FitTables.dur, 202.0, FitSex.M, FitGrade.G12))
        assertEquals(10, FitScoring.scoreOf(FitTables.dur, 372.0, FitSex.M, FitGrade.G12))
        assertEquals(0, FitScoring.scoreOf(FitTables.dur, 400.0, FitSex.M, FitGrade.G12))
    }

    @Test
    fun score_selectsSexGradeColumn() {
        // 5000 mL：男大一大二 ≥4920 → 95；男大三大四 ≥4900 → 90；女大一大二 ≥3400 → 100
        assertEquals(95, FitScoring.scoreOf(FitTables.vc, 5000.0, FitSex.M, FitGrade.G12))
        assertEquals(90, FitScoring.scoreOf(FitTables.vc, 5000.0, FitSex.M, FitGrade.G34))
        assertEquals(100, FitScoring.scoreOf(FitTables.vc, 3400.0, FitSex.F, FitGrade.G12))
    }

    @Test
    fun score_negativeSitIsZeroNotMissing() {
        val score = FitScoring.scoreOf(FitTables.sit, -10.0, FitSex.M, FitGrade.G12)
        assertNotNull(score)
        assertEquals(0, score)
    }

    @Test
    fun score_nullValueIsMissing() {
        assertNull(FitScoring.scoreOf(FitTables.vc, null, FitSex.M, FitGrade.G12))
    }

    // ---- BMI ---------------------------------------------------------------

    @Test
    fun bmi_normal() {
        val calc = FitScoring.calc(FitInput(h = "175", w = "68"))
        assertEquals("22.2", calc.rows[0].value)
        assertEquals(100, calc.rows[0].score)
        assertTrue(calc.rows[0].sub.contains("正常"))
        assertEquals(22.2, calc.bmi!!, 0.001)
    }

    @Test
    fun bmi_lowWeightOverweightObese() {
        val low = FitScoring.calc(FitInput(h = "175", w = "50"))
        assertEquals(80, low.rows[0].score)
        assertTrue(low.rows[0].sub.contains("低体重"))

        val over = FitScoring.calc(FitInput(h = "175", w = "80"))
        assertEquals(80, over.rows[0].score)
        assertTrue(over.rows[0].sub.contains("超重"))

        val obese = FitScoring.calc(FitInput(h = "175", w = "90"))
        assertEquals(60, obese.rows[0].score)
        assertTrue(obese.rows[0].sub.contains("肥胖"))
    }

    @Test
    fun bmi_missingWhenHeightOrWeightAbsent() {
        val calc = FitScoring.calc(FitInput(w = "68"))
        assertNull(calc.rows[0].score)
        assertEquals("—", calc.rows[0].value)
    }

    // ---- 缺项 / 权重 --------------------------------------------------------

    @Test
    fun blankInput_allMissing() {
        val calc = FitScoring.calc(FitInput())
        assertEquals(7, calc.rows.size)
        assertEquals(0, calc.wtDone)
        assertEquals(7, calc.miss)
        assertEquals(0.0, calc.total, 0.0001)
        assertEquals("不及格", calc.grade)
    }

    @Test
    fun partialInput_countsMissAndWtDone() {
        val calc = FitScoring.calc(FitInput(h = "175", w = "68", vc = "5040"))
        assertEquals(15 + 15, calc.wtDone) // BMI 15% + 肺活量 15%
        assertEquals(5, calc.miss)
    }

    @Test
    fun outOfRangeValueCountsAsMissingButKeepsRaw() {
        val calc = FitScoring.calc(FitInput(r50 = "20"))
        val row = calc.rows.first { it.name.startsWith("50") }
        assertEquals("20", row.value)
        assertNull(row.score)
    }

    // ---- 长跑 / 加分 --------------------------------------------------------

    @Test
    fun durParsingAndDisplay() {
        val calc = FitScoring.calc(FitInput(sex = FitSex.M, grade = FitGrade.G12, durM = "3", durS = "20"))
        val row = calc.rows.first { it.name.contains("1000") }
        assertEquals("3'20\"", row.value)
        assertEquals(95, row.score) // 200s ≤ 202 → 95
        assertEquals(200.0, FitScoring.sec("3", "20")!!, 0.001)
        assertNull(FitScoring.sec("", ""))
        assertEquals(59.0, FitScoring.sec("", "59")!!, 0.001)
    }

    @Test
    fun durPlusForFastRun() {
        val calc = FitScoring.calc(FitInput(sex = FitSex.M, grade = FitGrade.G12, durM = "2", durS = "40"))
        val row = calc.rows.first { it.name.contains("1000") }
        assertEquals(100, row.score)
        assertEquals(10, row.plus) // 197 - 160 = 37 ≥ 35 → +10
    }

    @Test
    fun pullPlusMale() {
        val calc = FitScoring.calc(FitInput(sex = FitSex.M, grade = FitGrade.G12, pull = "29"))
        val row = calc.rows.first { it.name == "引体向上" }
        assertEquals(100, row.score)
        assertEquals(10, row.plus) // 29 - 19 = 10 → +10
    }

    @Test
    fun supFemale() {
        val calc = FitScoring.calc(FitInput(sex = FitSex.F, grade = FitGrade.G12, pull = "69"))
        val row = calc.rows.first { it.name.contains("仰卧起坐") }
        assertEquals(100, row.score)
        assertEquals(10, row.plus) // 69 - 56 = 13 → +10
    }

    @Test
    fun itemNamesFollowSex() {
        val male = FitScoring.calc(FitInput(sex = FitSex.M))
        val female = FitScoring.calc(FitInput(sex = FitSex.F))
        assertTrue(male.rows.any { it.name == "引体向上" })
        assertTrue(male.rows.any { it.name == "1000 米跑" })
        assertTrue(female.rows.any { it.name == "1 分钟仰卧起坐" })
        assertTrue(female.rows.any { it.name == "800 米跑" })
    }

    // ---- 总分 / 等级 --------------------------------------------------------

    @Test
    fun perfectInput_scores120AndExcellent() {
        val calc = FitScoring.calc(perfectMaleInput())
        assertEquals(0, calc.miss)
        assertEquals(100, calc.wtDone)
        assertEquals(100.0, calc.std, 0.001)
        assertEquals(20.0, calc.plus, 0.001)
        assertEquals(120.0, calc.total, 0.001)
        assertEquals("120.0", calc.totalText)
        assertEquals("优秀", calc.grade)
    }

    @Test
    fun lowScore_gradeFail() {
        val calc = FitScoring.calc(FitInput(h = "175", w = "68", vc = "3100"))
        // BMI 100*15% + 肺活量 60*15% = 24
        assertEquals(24.0, calc.total, 0.001)
        assertEquals("不及格", calc.grade)
    }

    @Test
    fun round10AndFormatting() {
        assertEquals(22.3, FitScoring.round10(22.25), 0.0001)
        assertEquals(120.0, FitScoring.round10(120.0), 0.0001)
        assertEquals("—", FitScoring.ms(null))
        assertEquals("3'20\"", FitScoring.ms(200.0))
        assertEquals("6'12\"", FitScoring.ms(372.0))
        assertEquals("0'59\"", FitScoring.ms(59.0))
    }

    // ---- 输入清洗 -----------------------------------------------------------

    @Test
    fun sanitizeInput_rules() {
        val h = FitFieldSpec("h", "身高", "cm", 1)
        assertEquals("175.2", FitScoring.sanitizeInput("17a5.234", h))
        assertEquals("175", FitScoring.sanitizeInput("175.9", FitFieldSpec("vc", "肺活量", "mL", 0)))
        val sit = FitFieldSpec("sit", "坐位体前屈", "cm", 1, allowNegative = true)
        assertEquals("-12.3", FitScoring.sanitizeInput("-12.34", sit))
        assertEquals("12.3", FitScoring.sanitizeInput("a12.34b", sit))
        assertEquals("12.3", FitScoring.sanitizeInput("12.345", h))
    }
}

