package com.furina.classtools.data.marks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** marks 三种 kind（契约 docs/BACKEND_CONTRACT.md §5）：作业完成 / 通知置顶 / 自动通知已处理。 */
enum class MarkKind(val wire: String) {
    HW("hw"),
    NT("nt"),
    NT_DONE("ntdone"),
    ;

    companion object {
        fun fromWire(wire: String?): MarkKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `public.marks` 一行。
 *
 * `id = "<sid>|<kind>|<ref>"`；`seat` = 学号（服务端 RLS 校验本人）；取消只写 `val=false`，绝不 DELETE。
 * 注意：`value` 的 JSON 字段名是 **`val`**（SQL 保留字规避），不要改。
 */
@Serializable
data class MarkRow(
    val id: String,
    val seat: String,
    val kind: String,
    val ref: String,
    @SerialName("val") val value: Boolean = false,
    val ts: Long = 0L,
) {
    companion object {
        fun idOf(sid: String, kind: MarkKind, ref: String): String = "$sid|${kind.wire}|$ref"
    }
}

/** 本机 marks 视图（只保留 true 的项，等价 web `HW.marks` / `NT.marks` / `NT.done`）。 */
data class MarksSnapshot(
    val hwDone: Map<String, Boolean> = emptyMap(),
    val ntPin: Map<String, Boolean> = emptyMap(),
    val ntDone: Map<String, Boolean> = emptyMap(),
) {
    fun isDone(kind: MarkKind, ref: String): Boolean = when (kind) {
        MarkKind.HW -> hwDone[ref] == true
        MarkKind.NT -> ntPin[ref] == true
        MarkKind.NT_DONE -> ntDone[ref] == true
    }

    fun with(kind: MarkKind, ref: String, value: Boolean): MarksSnapshot = when (kind) {
        MarkKind.HW -> copy(hwDone = hwDone.updated(ref, value))
        MarkKind.NT -> copy(ntPin = ntPin.updated(ref, value))
        MarkKind.NT_DONE -> copy(ntDone = ntDone.updated(ref, value))
    }

    /** 整表覆盖（服务端下发）；只收 true，缺少即未做。 */
    fun replaceRows(rows: List<MarkRow>): MarksSnapshot {
        var hw = emptyMap<String, Boolean>()
        var nt = emptyMap<String, Boolean>()
        var done = emptyMap<String, Boolean>()
        for (row in rows) {
            if (row.ref.isBlank()) continue
            when (MarkKind.fromWire(row.kind)) {
                MarkKind.HW -> hw = hw.updated(row.ref, row.value)
                MarkKind.NT -> nt = nt.updated(row.ref, row.value)
                MarkKind.NT_DONE -> done = done.updated(row.ref, row.value)
                null -> Unit
            }
        }
        return MarksSnapshot(hwDone = hw, ntPin = nt, ntDone = done)
    }

    private fun Map<String, Boolean>.updated(key: String, value: Boolean): Map<String, Boolean> =
        if (value) this + (key to true) else this - key
}
