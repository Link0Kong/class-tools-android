package com.furina.classtools.data.ppt

import android.content.Context
import com.furina.classtools.features.ppt.PPT_RESUME_TTL_MS
import com.furina.classtools.features.ppt.PptPlayMode
import com.furina.classtools.features.ppt.PptResumeCodec
import com.furina.classtools.features.ppt.PptResumeRecord
import com.furina.classtools.features.ppt.PptResumeLogic

/**
 * 演示功能的本机偏好（契约 §11.3.1 模式偏好 + §11.3.4 续播）：
 *
 * - 续播：按 deckId 存 `{mode,page,step,t,at}` JSON；7 天有效；列表徽标 + 打开自动续播；
 * - 播放模式偏好：用户手动切换「原样/分步」后记住（设备级，不随账号同步）。
 *
 * 只用 SharedPreferences（不引入 DataStore 新依赖），线程安全；写入用 apply() 不阻塞主线程。
 */
class PptLocalStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ 续播

    fun saveResume(deckId: String, record: PptResumeRecord) {
        val id = deckId.trim()
        if (id.isEmpty()) return
        prefs.edit().putString(resumeKey(id), PptResumeCodec.encode(record)).apply()
    }

    /** 读取续播（7 天外自动清理并返回 null）。 */
    fun loadResume(deckId: String, nowMs: Long = clock()): PptResumeRecord? {
        val id = deckId.trim()
        if (id.isEmpty()) return null
        val key = resumeKey(id)
        val record = PptResumeCodec.decode(prefs.getString(key, null))
        if (record == null) {
            prefs.edit().remove(key).apply()
            return null
        }
        if (nowMs - record.updatedAtMs > PPT_RESUME_TTL_MS) {
            prefs.edit().remove(key).apply()
            return null
        }
        return record
    }

    fun clearResume(deckId: String) {
        val id = deckId.trim()
        if (id.isEmpty()) return
        prefs.edit().remove(resumeKey(id)).apply()
    }

    /** 列表批量读「继续」徽标（只返回 7 天内有效的记录）。 */
    fun resumeRecords(deckIds: Collection<String>, nowMs: Long = clock()): Map<String, PptResumeRecord> {
        val out = LinkedHashMap<String, PptResumeRecord>()
        for (id in deckIds) {
            val key = id.trim()
            if (key.isEmpty()) continue
            val record = PptResumeCodec.decode(prefs.getString(resumeKey(key), null)) ?: continue
            if (nowMs - record.updatedAtMs > PPT_RESUME_TTL_MS) {
                prefs.edit().remove(resumeKey(key)).apply()
                continue
            }
            out[key] = record
        }
        return out
    }

    /** 清理过期的续播记录（列表加载时顺带调用，避免 prefs 膨胀）。 */
    fun pruneResumes(nowMs: Long = clock()) {
        val edit = prefs.edit()
        var dirty = false
        for ((key, value) in prefs.all) {
            if (!key.startsWith(RESUME_PREFIX)) continue
            val record = PptResumeCodec.decode(value as? String)
            if (record == null || nowMs - record.updatedAtMs > PPT_RESUME_TTL_MS) {
                edit.remove(key)
                dirty = true
            }
        }
        if (dirty) edit.apply()
    }

    // ------------------------------------------------------------ 播放模式偏好

    /** 上次用户手动选择的模式（null = 没选过，按网络自动判定）。 */
    fun playModePreference(): PptPlayMode? = when (prefs.getString(KEY_PLAY_MODE, null)) {
        "video" -> PptPlayMode.VIDEO
        "steps" -> PptPlayMode.STEPS
        else -> null
    }

    fun setPlayModePreference(mode: PptPlayMode) {
        prefs.edit().putString(KEY_PLAY_MODE, if (mode == PptPlayMode.VIDEO) "video" else "steps").apply()
    }

    fun clearPlayModePreference() {
        prefs.edit().remove(KEY_PLAY_MODE).apply()
    }

    private fun resumeKey(deckId: String): String = RESUME_PREFIX + deckId

    companion object {
        const val PREFS_NAME: String = "ppt_local"
        const val RESUME_PREFIX: String = "resume_"
        const val KEY_PLAY_MODE: String = "play_mode"
    }
}
