package com.furina.classtools.features.fit

import java.util.Locale
import kotlin.math.roundToLong

/**
 * 体测评分（纯函数，逐函数对齐 `mp-class-tools/utils/fit.js ftCalc()` / 网页 `fit` 实现）。
 *
 * 权重：BMI 15%、肺活量 15%、50m 20%、坐位体前屈 10%、立定跳远 10%、引体/仰卧 10%、长跑 20%（合计 100）；
 * 附加分 ≤20（引体/仰卧、1000/800m 加分表 FTB→FTP）；
 * 等级：≥90 优秀 / ≥80 良好 / ≥60 及格 / <60 不及格；未填项按 0 计并计入 miss。
 */
object FitScoring {

    /** 数值文本解析：空/非法 → null（与 mp `ftNum` 一致）。 */
    fun num(text: String?): Double? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        val v = s.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    /** 长跑「分:秒」→ 秒（两者都空 → null；只有一个时另一个按 0）。 */
    fun sec(minutesText: String?, secondsText: String?): Double? {
        val m = num(minutesText)
        val s = num(secondsText)
        if (m == null && s == null) return null
        return (m ?: 0.0) * 60 + (s ?: 0.0)
    }

    /** 输入合法性：长跑只要求 >0 秒；其它按 [FitTables.FTRANGE]；非法 → null（视为缺项）。 */
    fun valid(key: String, value: Double?): Double? {
        if (value == null) return null
        if (key == "dur") return if (value > 0) value else null
        val range = FitTables.FTRANGE[key] ?: return value
        return if (value in range) value else null
    }

    /** 单项得分：任一阈值命中即返回 FTS[i]；都不命中 → 0（区间内低于最低档）。 */
    fun scoreOf(table: FitTables.FitScoreTable, value: Double?, sex: FitSex, grade: FitGrade): Int? {
        if (value == null) return null
        val column = table.column(sex, grade)
        if (column.isEmpty()) return null
        for (i in column.indices) {
            if (table.hi) {
                if (value >= column[i]) return FitTables.FTS[i]
            } else {
                if (value <= column[i]) return FitTables.FTS[i]
            }
        }
        return 0
    }

    /** 附加分：gap 超过表中阈值则取对应加分（gap≤0 → 0）。 */
    fun plusOf(table: FitTables.FitPlusTable?, gap: Double): Int = table?.plusFor(gap) ?: 0

    /** 秒 → `m'ss"`（与 mp `ftMS` 一致；null → `—`）。 */
    fun ms(seconds: Double?): String {
        if (seconds == null) return "—"
        var m = (seconds / 60).toInt()
        var s = (seconds - m * 60).roundToLong().toInt()
        if (s >= 60) {
            m += 1
            s -= 60
        }
        return "$m'${if (s < 10) "0" else ""}$s\""
    }

    /** 保留 1 位小数（四舍五入，与 mp `Math.round(x*10)/10` 一致）。 */
    fun round10(value: Double): Double = (value * 10).roundToLong() / 10.0

    /** 主计算入口。 */
    fun calc(input: FitInput): FitCalc {
        val sex = input.sex
        val grade = input.grade
        val rows = ArrayList<FitRow>(9)
        var std = 0.0
        var plus = 0.0
        var miss = 0

        fun add(row: FitRow) {
            rows.add(row)
            if (row.score == null) {
                miss += 1
            } else {
                std += row.score * row.weight / 100.0
                plus += row.plus
            }
        }

        fun item(
            name: String,
            unit: String,
            weight: Int,
            raw: String,
            value: Double?,
            table: FitTables.FitScoreTable,
            plusTable: FitTables.FitPlusTable? = null,
            gapOf: ((Double) -> Double)? = null,
            key: String = "",
        ) {
            val validValue = valid(key, value)
            val score = scoreOf(table, validValue, sex, grade)
            val plusPoint = if (score == null || gapOf == null) 0 else plusOf(plusTable, gapOf(validValue!!))
            add(
                FitRow(
                    name = name,
                    sub = unit,
                    weight = weight,
                    value = raw.ifBlank { "—" },
                    score = score,
                    plus = plusPoint,
                ),
            )
        }

        // 1) BMI（15%）
        val h = valid("h", num(input.h))
        val w = valid("w", num(input.w))
        var bmi: Double? = null
        var bmiScore: Int? = null
        var bmiCategory: String? = null
        if (h != null && w != null && h > 0 && w > 0) {
            val rawBmi = w / ((h / 100.0) * (h / 100.0))
            bmi = round10(rawBmi)
            val normal = FitTables.bmiNormal(sex)
            val bmiValue = bmi!!
            when {
                bmiValue < normal.start -> {
                    bmiScore = 80
                    bmiCategory = "低体重"
                }

                bmiValue <= normal.endInclusive -> {
                    bmiScore = 100
                    bmiCategory = "正常"
                }

                bmiValue <= FitTables.BMI_OBESE_THRESHOLD -> {
                    bmiScore = 80
                    bmiCategory = "超重"
                }

                else -> {
                    bmiScore = 60
                    bmiCategory = "肥胖"
                }
            }
        }
        add(
            FitRow(
                name = "体重指数",
                sub = if (bmiCategory != null) "BMI · $bmiCategory" else "BMI",
                weight = 15,
                value = bmi?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "—",
                score = bmiScore,
            ),
        )

        // 2) 肺活量（15%）/ 50m（20%）/ 坐位体前屈（10%）/ 立定跳远（10%）
        item("肺活量", "mL", 15, input.vc, num(input.vc), FitTables.vc, key = "vc")
        item("50 米跑", "秒", 20, input.r50, num(input.r50), FitTables.r50, key = "r50")
        item("坐位体前屈", "cm", 10, input.sit, num(input.sit), FitTables.sit, key = "sit")
        item("立定跳远", "cm", 10, input.jump, num(input.jump), FitTables.jump, key = "jump")

        // 3) 引体向上（男）/ 仰卧起坐（女）（10%，有加分）
        if (sex == FitSex.M) {
            item(
                name = "引体向上",
                unit = "次",
                weight = 10,
                raw = input.pull,
                value = num(input.pull),
                table = FitTables.pull,
                plusTable = FitTables.pullPlus,
                gapOf = { value -> value - FitTables.pull.column(sex, grade).first() },
                key = "pull",
            )
        } else {
            item(
                name = "1 分钟仰卧起坐",
                unit = "次",
                weight = 10,
                raw = input.pull,
                value = num(input.pull),
                table = FitTables.sup,
                plusTable = FitTables.supPlus,
                gapOf = { value -> value - FitTables.sup.column(sex, grade).first() },
                key = "pull",
            )
        }

        // 4) 1000m（男）/ 800m（女）（20%，有加分）
        val durSeconds = valid("dur", sec(input.durM, input.durS))
        val hasDurInput = num(input.durM) != null || num(input.durS) != null
        val durRaw = if (hasDurInput) ms(sec(input.durM, input.durS)) else ""
        if (sex == FitSex.M) {
            item(
                name = "1000 米跑",
                unit = "分·秒",
                weight = 20,
                raw = durRaw,
                value = durSeconds,
                table = FitTables.dur,
                plusTable = FitTables.durMalePlus,
                gapOf = { value -> FitTables.dur.column(sex, grade).first() - value },
                key = "dur",
            )
        } else {
            item(
                name = "800 米跑",
                unit = "分·秒",
                weight = 20,
                raw = durRaw,
                value = durSeconds,
                table = FitTables.dur,
                plusTable = FitTables.durFemalePlus,
                gapOf = { value -> FitTables.dur.column(sex, grade).first() - value },
                key = "dur",
            )
        }

        var wtDone = 0
        rows.forEach { row -> if (row.score != null) wtDone += row.weight }
        val stdRounded = round10(std)
        val plusRounded = round10(plus)
        val total = round10(stdRounded + plusRounded)
        val gradeLabel = when {
            total >= 90 -> "优秀"
            total >= 80 -> "良好"
            total >= 60 -> "及格"
            else -> "不及格"
        }
        return FitCalc(
            rows = rows,
            std = stdRounded,
            plus = plusRounded,
            total = total,
            totalText = String.format(Locale.CHINA, "%.1f", total),
            grade = gradeLabel,
            miss = miss,
            wtDone = wtDone,
            sex = sex,
            gradeBucket = grade,
            h = h,
            w = w,
            bmi = bmi,
            bmiCategory = bmiCategory,
        )
    }

    /** 输入清洗：数字字段只保留数字/小数/负号（坐位体前屈允许负），按小数位截断。 */
    fun sanitizeInput(text: String, spec: FitFieldSpec): String {
        val filtered = buildString {
            text.forEachIndexed { index, ch ->
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' -> if (!contains('.')) append(ch)
                    ch == '-' && spec.allowNegative && isEmpty() -> append(ch)
                }
            }
        }
        if (spec.decimals == 0) return filtered.substringBefore('.')
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered
        return filtered.substring(0, dot + 1) + filtered.substring(dot + 1).take(spec.decimals)
    }
}
