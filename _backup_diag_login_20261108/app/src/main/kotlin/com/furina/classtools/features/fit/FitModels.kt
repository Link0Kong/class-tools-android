package com.furina.classtools.features.fit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 体测领域模型（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 对齐契约：
 * - `docs/BACKEND_CONTRACT.md` §7.3（国标 2014 大学组；权重/BMI/加分/等级）；
 * - `docs/api/USERDATA_V6.md`：`userdata.fit = {sex, grade, h, w, vc, r50, sit, jump, pull, durM, durS}`
 *   （11 键，值多为字符串）；
 * - `docs/api/ENDPOINTS.md` E14/E15/E16：`fitrecs.data` 为 JSON **字符串**：
 *   `{id, ts, n(名称), sex, gr(年级), fields{h..durS}, total, grade(等级)}`，最多保留最近 2 条。
 */

/** 性别。 */
enum class FitSex(val wire: String, val label: String) {
    M("m", "男"),
    F("f", "女"),
    ;

    companion object {
        fun fromWire(wire: String?): FitSex = if (wire?.trim() == "f") F else M
    }
}

/** 年级档（大学组：大一/大二、大三/大四）。 */
enum class FitGrade(val wire: String, val label: String, val short: String) {
    G12("12", "大一 / 大二", "大一大二"),
    G34("34", "大三 / 大四", "大三大四"),
    ;

    companion object {
        fun fromWire(wire: String?): FitGrade = if (wire?.trim() == "34") G34 else G12
    }
}

/**
 * 表单输入（与 `userdata.fit` 的 11 个键一一对应，值保留字符串以便原样回填）。
 */
data class FitInput(
    val sex: FitSex = FitSex.M,
    val grade: FitGrade = FitGrade.G12,
    val h: String = "",
    val w: String = "",
    val vc: String = "",
    val r50: String = "",
    val sit: String = "",
    val jump: String = "",
    val pull: String = "",
    val durM: String = "",
    val durS: String = "",
) {
    /** 按字段 key 读值（h/w/vc/r50/sit/jump/pull/durM/durS）。 */
    fun valueOf(key: String): String = when (key) {
        "h" -> h
        "w" -> w
        "vc" -> vc
        "r50" -> r50
        "sit" -> sit
        "jump" -> jump
        "pull" -> pull
        "durM" -> durM
        "durS" -> durS
        else -> ""
    }

    /** 按字段 key 写值（未知 key 原样返回）。 */
    fun withValue(key: String, value: String): FitInput = when (key) {
        "h" -> copy(h = value)
        "w" -> copy(w = value)
        "vc" -> copy(vc = value)
        "r50" -> copy(r50 = value)
        "sit" -> copy(sit = value)
        "jump" -> copy(jump = value)
        "pull" -> copy(pull = value)
        "durM" -> copy(durM = value)
        "durS" -> copy(durS = value)
        else -> this
    }

    /** 是否有任意一项已填写（决定「空态 / 实时出分」）。 */
    fun hasAny(): Boolean = KEYS.any { valueOf(it).isNotBlank() }

    /** 转历史记录 fields（9 项）。 */
    fun toFields(): FitFields = FitFields(
        h = h, w = w, vc = vc, r50 = r50, sit = sit, jump = jump, pull = pull, durM = durM, durS = durS,
    )

    companion object {
        /** `userdata.fit` 的固定 9 个成绩键（sex/grade 另有）。 */
        val KEYS: List<String> = listOf("h", "w", "vc", "r50", "sit", "jump", "pull", "durM", "durS")

        fun blank(sex: FitSex = FitSex.M, grade: FitGrade = FitGrade.G12): FitInput =
            FitInput(sex = sex, grade = grade)

        fun fromFields(sex: FitSex, grade: FitGrade, fields: FitFields): FitInput = FitInput(
            sex = sex,
            grade = grade,
            h = fields.h, w = fields.w, vc = fields.vc, r50 = fields.r50, sit = fields.sit,
            jump = fields.jump, pull = fields.pull, durM = fields.durM, durS = fields.durS,
        )
    }
}

/** 表单字段元数据（UI 渲染 + 输入清洗用；durM/durS 单独成对展示）。 */
data class FitFieldSpec(
    val key: String,
    val label: String,
    val unit: String,
    /** 小数位（0 = 整数）。 */
    val decimals: Int,
    /** 是否允许负数（坐位体前屈）。 */
    val allowNegative: Boolean = false,
)

/** 单项字段（身高~引体/仰卧；长跑拆 durM/durS）。 */
val FIT_INPUT_FIELDS: List<FitFieldSpec> = listOf(
    FitFieldSpec("h", "身高", "cm", 1),
    FitFieldSpec("w", "体重", "kg", 1),
    FitFieldSpec("vc", "肺活量", "mL", 0),
    FitFieldSpec("r50", "50 米跑", "秒", 2),
    FitFieldSpec("sit", "坐位体前屈", "cm", 1, allowNegative = true),
    FitFieldSpec("jump", "立定跳远", "cm", 0),
    FitFieldSpec("pull", "引体 / 仰卧起坐", "次", 0),
)

/** 一项评分结果。 */
data class FitRow(
    /** 项目名（体重指数 / 肺活量 / 1000 米跑…）。 */
    val name: String,
    /** 单位或备注（mL / 秒 / BMI · 正常…）。 */
    val sub: String,
    /** 权重（百分比整数，总分 100）。 */
    val weight: Int,
    /** 原始输入展示（未填 `—`）。 */
    val value: String,
    /** 等级分（null = 缺项或非法）。 */
    val score: Int?,
    /** 加分（引体/仰卧/长跑）。 */
    val plus: Int = 0,
)

/** 一次完整计算。 */
data class FitCalc(
    val rows: List<FitRow>,
    /** 标准分（Σ 单项分×权重/100，1 位小数）。 */
    val std: Double,
    /** 加分合计（1 位小数）。 */
    val plus: Double,
    /** 总评 = 标准分 + 加分（1 位小数）。 */
    val total: Double,
    val totalText: String,
    /** 等级：优秀/良好/及格/不及格。 */
    val grade: String,
    /** 缺项数。 */
    val miss: Int,
    /** 已填项权重合计。 */
    val wtDone: Int,
    val sex: FitSex,
    val gradeBucket: FitGrade,
    val h: Double?,
    val w: Double?,
    val bmi: Double?,
    val bmiCategory: String?,
)

/** 历史记录里的成绩字段（9 项字符串，与 fitrecs.data.fields 一致）。 */
@Serializable
data class FitFields(
    val h: String = "",
    val w: String = "",
    val vc: String = "",
    val r50: String = "",
    val sit: String = "",
    val jump: String = "",
    val pull: String = "",
    val durM: String = "",
    val durS: String = "",
) {
    fun isEmpty(): Boolean = listOf(h, w, vc, r50, sit, jump, pull, durM, durS).all { it.isBlank() }
}

/**
 * 一条体测历史记录（= `fitrecs.data` JSON 解析后的结构；本机/云端最多 2 条）。
 *
 * 序列化字段：`id, ts, n, sex, gr, fields, total, grade`；未知字段忽略。
 */
@Serializable
data class FitRecord(
    val id: String = "",
    val ts: Long = 0L,
    @SerialName("n") val name: String = "",
    val sex: String = "m",
    @SerialName("gr") val gradeBucket: String = "12",
    val fields: FitFields = FitFields(),
    val total: Double? = null,
    val grade: String = "",
    /** 本机待同步标记（不参与云端 JSON；encode 时保留兼容）。 */
    @SerialName("_sync") val syncPending: Boolean = false,
) {
    val sexEnum: FitSex get() = FitSex.fromWire(sex)
    val gradeEnum: FitGrade get() = FitGrade.fromWire(gradeBucket)

    /** 展示名称：自定义名或「体测 MM-dd HH:mm」。 */
    fun displayName(): String = name.ifBlank { "体测 ${timeText()}" }

    /** `MM-dd HH:mm`（本地时区）。 */
    fun timeText(): String {
        if (ts <= 0L) return "—"
        val zoned = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
        return String.format(
            java.util.Locale.CHINA,
            "%02d-%02d %02d:%02d",
            zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute,
        )
    }

    /** 成绩明细（与 mp `recDetail` 文案一致）。 */
    fun detailText(): String {
        val parts = mutableListOf<String>()
        if (fields.h.isNotBlank()) parts.add("身高 ${fields.h}cm")
        if (fields.w.isNotBlank()) parts.add("体重 ${fields.w}kg")
        if (fields.vc.isNotBlank()) parts.add("肺活量 ${fields.vc}mL")
        if (fields.r50.isNotBlank()) parts.add("50米 ${fields.r50}s")
        if (fields.sit.isNotBlank()) parts.add("坐位 ${fields.sit}cm")
        if (fields.jump.isNotBlank()) parts.add("跳远 ${fields.jump}cm")
        if (fields.pull.isNotBlank()) {
            parts.add(if (sexEnum == FitSex.F) "仰卧 ${fields.pull}次" else "引体 ${fields.pull}次")
        }
        if (fields.durM.isNotBlank() || fields.durS.isNotBlank()) {
            val m = fields.durM.ifBlank { "0" }
            val s = fields.durS.ifBlank { "0" }.padStart(2, '0')
            parts.add("长跑 $m'$s\"")
        }
        return parts.joinToString(" · ").ifBlank { "（无成绩明细）" }
    }

    fun totalText(): String = total?.let { String.format(java.util.Locale.CHINA, "%.1f", it) } ?: "—"

    companion object {
        const val MAX_RECORDS: Int = 2
    }
}
