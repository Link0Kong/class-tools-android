package com.furina.classtools.features.ppt

/** 演示播放模式（契约 §11.3.1）。 */
enum class PptPlayMode {
    /** 原样播放：高保真视频（含动画/切换/内嵌视频声音）。 */
    VIDEO,

    /** 分步浏览：现有图片 step + 热点。 */
    STEPS,
}

/** 网络质量分类（Android 由 ConnectivityManager 映射；纯逻辑层消费）。 */
enum class PptNetworkQuality { GOOD, SLOW, OFFLINE }

/**
 * 默认模式选择（契约 §11.3.1）：
 * - 无 video → 分步浏览；
 * - 有设备偏好 → 优先用户选择（即使当前网络差；视频失败会按 §11.3.1 自动回退分步）；
 * - 无偏好 → 网络良好用原样播放，否则分步浏览。
 */
object PptModeChooser {

    fun initialMode(
        hasVideo: Boolean,
        network: PptNetworkQuality,
        preference: PptPlayMode?,
    ): PptPlayMode {
        if (!hasVideo) return PptPlayMode.STEPS
        if (preference != null) return preference
        return when (network) {
            PptNetworkQuality.GOOD -> PptPlayMode.VIDEO
            PptNetworkQuality.SLOW, PptNetworkQuality.OFFLINE -> PptPlayMode.STEPS
        }
    }

    /** Android ConnectivityManager 能力 → 网络质量（纯函数，可单测）。 */
    fun classifyNetwork(
        hasNetwork: Boolean,
        isWifiOrEthernet: Boolean,
        isMetered: Boolean,
        downstreamKbps: Int,
        dataSaverEnabled: Boolean,
    ): PptNetworkQuality {
        if (!hasNetwork) return PptNetworkQuality.OFFLINE
        if (dataSaverEnabled) return PptNetworkQuality.SLOW
        if (isWifiOrEthernet) return PptNetworkQuality.GOOD
        if (isMetered) {
            // 移动网络的速率兜底：<1500kbps 视为慢网（契约 §11.3.1「计量/慢网」）。
            if (downstreamKbps in 1..1_499) return PptNetworkQuality.SLOW
            return PptNetworkQuality.GOOD
        }
        return PptNetworkQuality.GOOD
    }
}
