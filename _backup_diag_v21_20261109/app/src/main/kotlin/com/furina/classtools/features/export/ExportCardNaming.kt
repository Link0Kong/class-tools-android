package com.furina.classtools.features.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 导出卡片命名与编号（对齐 web/AGENTS.md §2）：
 * - 文件名：`<类型>_<YYYYMMDD-HHMM>_<关键参数>.png`；
 * - 安全化：`[\\/:*?"<>|\s]+` → `-`；
 * - 唯一编号：`<PREFIX>-<YYYYMMDD>-<HHMM>-<4位随机>`。
 */
object ExportCardNaming {

    private val unsafe = Regex("[\\\\/:*?\"<>|\\s]+")

    /** 文件名安全化（含中文保留）。 */
    fun safeName(raw: String): String =
        unsafe.replace(raw.trim(), "-").trim('-', '.').ifEmpty { "导出" }

    fun stamp(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now))

    fun datePart(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(now))

    fun timePart(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HHmm", Locale.ROOT).format(Date(now))

    /** 唯一编号：`PREFIX-YYYYMMDD-HHMM-XXXX`（默认 `CT` 前缀）。 */
    fun recordId(
        prefix: String = "CT",
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default,
    ): String {
        val p = prefix.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.ifEmpty { "CT" }.take(6)
        val rand = buildString {
            repeat(4) { append("ABCDEFGHJKMNPQRSTUVWXYZ23456789".random(random)) }
        }
        return "$p-${datePart(now)}-${timePart(now)}-$rand"
    }

    /**
     * 生成最终文件名：`<类型>_<stamp>_<参数...>.png`。
     * 参数按顺序拼接，空参数跳过；总长限制 120 字符（避免文件系统/分享失败）。
     */
    fun fileName(
        kind: ExportKind,
        params: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): String {
        val parts = buildList {
            add(kind.label)
            add(stamp(now))
            addAll(params.filter { it.isNotBlank() })
        }.joinToString("_") { safeName(it) }
        val clipped = parts.take(120)
        return "$clipped.png"
    }

    /** 页脚生成时间：`生成时间 MM-dd HH:mm`。 */
    fun footText(now: Long = System.currentTimeMillis()): String =
        "生成时间 " + SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(now))
}
