package com.furina.classtools.ui.platform

/**
 * 音量键桥（DESIGN §4.9：安卓音量键 ± 翻页，课堂单手操作最顺）。
 *
 * 音量键在系统层由 Activity 处理，Compose 的 onKeyEvent 收不到；
 * 因此由 [com.furina.classtools.MainActivity.onKeyDown] 转发给当前前台处理者。
 *
 * @param handler 返回 true 表示已消费（播放器翻页）；null/未消费 → 走系统音量。
 *     约定 forward=true 对应「音量+ → 前进」，false 对应「音量− → 后退」。
 */
object VolumeKeyBridge {
    @Volatile
    var handler: ((forward: Boolean) -> Boolean)? = null
}
