package com.furina.classtools.data.ppt

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.furina.classtools.features.ppt.PptModeChooser
import com.furina.classtools.features.ppt.PptNetworkQuality

/** 网络质量提供者（`PptModeChooser` 纯逻辑的 Android 数据源）。 */
fun interface PptNetworkQualityProvider {
    fun current(): PptNetworkQuality
}

/**
 * ConnectivityManager 实现（契约 §11.3.1/§11.6）：
 * - WiFi / Ethernet → GOOD；
 * - 数据节省模式（Data Saver）→ SLOW；
 * - 计量移动网络且下行带宽 < 1500kbps → SLOW；其它 → GOOD；
 * - 无活动网络 → OFFLINE。
 *
 * 只读网络能力，不申请额外权限（INTERNET/ACCESS_NETWORK_STATE 已在 Manifest）。
 */
class AndroidNetworkQualityProvider(context: Context) : PptNetworkQualityProvider {

    private val appContext = context.applicationContext

    override fun current(): PptNetworkQuality {
        val manager = runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return PptNetworkQuality.GOOD
        val network = runCatching { manager.activeNetwork }.getOrNull()
            ?: return PptNetworkQuality.OFFLINE
        val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
            ?: return PptNetworkQuality.OFFLINE
        val wifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val downstreamKbps = caps.linkDownstreamBandwidthKbps
        val dataSaver = runCatching {
            manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)
        return PptModeChooser.classifyNetwork(
            hasNetwork = true,
            isWifiOrEthernet = wifiOrEthernet,
            isMetered = metered,
            downstreamKbps = downstreamKbps,
            dataSaverEnabled = dataSaver,
        )
    }
}
