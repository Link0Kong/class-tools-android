package com.furina.classtools.data.draw

import android.content.Context
import com.furina.classtools.features.draw.DrawLastResult
import com.furina.classtools.features.draw.DrawLastResultStore
import com.furina.classtools.features.draw.KeyValueDrawLastResultStore

/**
 * 「最近一次抽签结果」的本机持久化（设备级，不进 userdata v6、不随账号同步）。
 *
 * 只用 SharedPreferences（不引入新依赖）；写入用 `apply()` 不阻塞主线程。
 * 跨进程重启仍可读，分组页的「最近抽签」来源因此不再依赖抽签页跳转或内存传递。
 */
class SharedPreferencesDrawLastResultStore(
    context: Context,
    key: String,
) : DrawLastResultStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val delegate = KeyValueDrawLastResultStore(
        key = key,
        read = { name -> prefs.getString(name, null) },
        write = { name, value -> prefs.edit().putString(name, value).apply() },
    )

    override fun load(): DrawLastResult? = delegate.load()

    override fun save(value: DrawLastResult) = delegate.save(value)

    companion object {
        /** 私有偏好文件名（设备级，禁止改名，否则旧用户的最近抽签会丢）。 */
        const val PREFS_NAME: String = "ct_draw_last"

    }
}
