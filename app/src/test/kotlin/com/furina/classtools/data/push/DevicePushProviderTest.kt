package com.furina.classtools.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePushProviderTest {

    @Test
    fun `已知手机品牌只显示自己的推送`() {
        assertEquals(DevicePushProvider.OPPO, provider(brand = "OPPO", manufacturer = "vivo"))
        assertEquals(DevicePushProvider.VIVO, provider(brand = "vivo"))
        assertEquals(
            DevicePushProvider.VIVO,
            provider(brand = " iQOO ", manufacturer = "vivo", supportsOppoPush = true),
        )
    }

    @Test
    fun `一加和 realme 有对应系统通道时显示 OPPO 推送`() {
        assertEquals(DevicePushProvider.OPPO, provider(brand = "OnePlus", hasColorOsRom = true))
        assertEquals(DevicePushProvider.OPPO, provider(brand = "realme", supportsOppoPush = true))
        assertNull(provider(brand = "OnePlus", manufacturer = "OPPO"))
        assertNull(provider(brand = "realme"))
    }

    @Test
    fun `品牌缺失时检查厂商和系统能力 其他机型隐藏推送入口`() {
        assertEquals(DevicePushProvider.VIVO, provider(brand = null, manufacturer = "VIVO"))
        assertEquals(DevicePushProvider.OPPO, provider(brand = "unknown", hasColorOsRom = true))
        assertNull(provider(brand = "google", manufacturer = "Google"))
        assertNull(provider(brand = "HONOR"))
    }

    private fun provider(
        brand: String?,
        manufacturer: String? = null,
        hasColorOsRom: Boolean = false,
        supportsOppoPush: Boolean = false,
    ) = DevicePushProvider.forDevice(brand, manufacturer, hasColorOsRom, supportsOppoPush)
}
