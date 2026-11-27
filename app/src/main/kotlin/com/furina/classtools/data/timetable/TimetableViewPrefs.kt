package com.furina.classtools.data.timetable

import android.content.Context
import android.content.SharedPreferences

/**
 * 课表视图模式（周/日只由顶部按钮切换，不参与左右滑动）。
 *
 * [storageValue] 是本机偏好的持久化值（只认 `week` / `day`，脏值按未保存处理）。
 */
enum class TimetableViewMode(val storageValue: String) {
    WEEK("week"),
    DAY("day"),
    ;

    companion object {
        /** 持久化值 → 枚举；未知/空值返回 null（调用方用默认视图）。 */
        fun fromStorage(raw: String?): TimetableViewMode? =
            entries.firstOrNull { it.storageValue == raw }
    }
}

/**
 * 课表「周/日视图」本机偏好（SharedPreferences）。
 *
 * 语义（2026-11-27 用户要求）：
 * - 用户在顶部 周/日 分段切换后记住选择，下次进入课表直接按上次视图显示；
 * - 只存本机、不进云同步 userdata（`tt.ttView` 仍归网页端，避免跨端覆盖）；
 * - 深链/工作台卡片临时定位到某天不写偏好（见 [TimetableViewScreen] 的 focus 处理）。
 */
class TimetableViewPrefs(
    context: Context,
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
) {

    /** 读取已保存的视图；从未保存/脏值返回 null。 */
    fun read(): TimetableViewMode? = TimetableViewMode.fromStorage(prefs.getString(KEY, null))

    /** 保存用户显式选择；写入失败不影响本次显示。 */
    fun write(mode: TimetableViewMode) {
        runCatching { prefs.edit().putString(KEY, mode.storageValue).apply() }
    }

    companion object {
        /** SharedPreferences 文件名（只存视图偏好，禁止改名）。 */
        const val FILE_NAME: String = "timetable_view_v1"

        /** 偏好键：`week` / `day`。 */
        const val KEY: String = "view"
    }
}
