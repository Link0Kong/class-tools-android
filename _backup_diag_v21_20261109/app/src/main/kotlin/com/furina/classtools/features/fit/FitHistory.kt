package com.furina.classtools.features.fit

/**
 * 体测历史记录逻辑（纯函数，对齐 mp `pages/ft/ft.js saveRec/findMergeIndex/fitSubset`）：
 *
 * - 本机/云端最多保留最近 [FitRecord.MAX_RECORDS] 条；
 * - 「补填项目」不新增记录：旧记录的非空项都与新输入**完全一致**且至少有一项非空 → 合并更新旧记录；
 * - 新记录插到最前；超出 2 条的旧记录由调用方 arch=true 软删除（本轮 UI/Lead 接线负责）。
 */
object FitHistory {

    /** 旧记录的非空项是否都是新记录同项的子集（且旧记录至少有一项非空）。 */
    fun isSubset(oldFields: FitFields, newFields: FitFields): Boolean {
        var any = false
        FitInput.KEYS.forEach { key ->
            val oldValue = levelValue(oldFields, key)
            val newValue = levelValue(newFields, key)
            if (oldValue.isNotBlank()) {
                any = true
                if (oldValue != newValue) return false
            }
        }
        return any
    }

    /** 在历史里找可合并记录下标（同 sex/grade 且旧字段是新字段子集）；找不到 → -1。 */
    fun findMergeIndex(
        records: List<FitRecord>,
        newFields: FitFields,
        sex: FitSex,
        grade: FitGrade,
    ): Int {
        records.forEachIndexed { index, record ->
            if (record.sexEnum != sex || record.gradeEnum != grade) return@forEachIndexed
            if (isSubset(record.fields, newFields)) return index
        }
        return -1
    }

    /**
     * 保存一条记录：
     * - 可合并 → 更新旧记录（ts/total/grade 更新，名称保留，移到最前）；
     * - 不可合并 → 新建记录插到最前；
     * - 结果截断到最多 2 条（被挤出的记录由调用方软删除）。
     *
     * @param id 新记录 id（合并时沿用旧 id）；测试可注入
     */
    fun save(
        records: List<FitRecord>,
        input: FitInput,
        calc: FitCalc,
        id: String,
        now: Long,
        name: String = "",
    ): SavedHistory {
        val fields = input.toFields()
        val mergeAt = findMergeIndex(records, fields, input.sex, input.grade)
        val working = records.toMutableList()
        val merged: FitRecord
        val isMerge: Boolean
        if (mergeAt >= 0) {
            val old = working.removeAt(mergeAt)
            merged = old.copy(
                ts = now,
                name = old.name.ifBlank { name },
                sex = input.sex.wire,
                gradeBucket = input.grade.wire,
                fields = fields,
                total = calc.total,
                grade = calc.grade,
                syncPending = true,
            )
            isMerge = true
        } else {
            merged = FitRecord(
                id = id,
                ts = now,
                name = name,
                sex = input.sex.wire,
                gradeBucket = input.grade.wire,
                fields = fields,
                total = calc.total,
                grade = calc.grade,
                syncPending = true,
            )
            isMerge = false
        }
        working.add(0, merged)
        val kept = working.take(FitRecord.MAX_RECORDS)
        val dropped = working.drop(FitRecord.MAX_RECORDS)
        return SavedHistory(records = kept, dropped = dropped, merged = isMerge, record = merged)
    }

    /** 删除第 [index] 条（越界返回原列表）。 */
    fun deleteAt(records: List<FitRecord>, index: Int): List<FitRecord> {
        if (index !in records.indices) return records
        val list = records.toMutableList()
        list.removeAt(index)
        return list
    }

    /** 重命名第 [index] 条（名称去空白、≤24 字；空名返回原列表）。 */
    fun renameAt(records: List<FitRecord>, index: Int, newName: String): List<FitRecord> {
        if (index !in records.indices) return records
        val name = newName.trim().take(24)
        if (name.isEmpty()) return records
        val list = records.toMutableList()
        list[index] = list[index].copy(name = name)
        return list
    }

    /** 回填第 [index] 条为输入（sex/grade/9 项成绩）。 */
    fun restoreAt(records: List<FitRecord>, index: Int): FitInput? {
        val record = records.getOrNull(index) ?: return null
        return FitInput.fromFields(record.sexEnum, record.gradeEnum, record.fields)
    }

    private fun levelValue(fields: FitFields, key: String): String = when (key) {
        "h" -> fields.h
        "w" -> fields.w
        "vc" -> fields.vc
        "r50" -> fields.r50
        "sit" -> fields.sit
        "jump" -> fields.jump
        "pull" -> fields.pull
        "durM" -> fields.durM
        "durS" -> fields.durS
        else -> ""
    }
}

/** 保存结果：保留的 ≤2 条 + 被挤出需软删的旧记录 + 是否合并 + 最终记录。 */
data class SavedHistory(
    val records: List<FitRecord>,
    val dropped: List<FitRecord>,
    val merged: Boolean,
    val record: FitRecord,
)
