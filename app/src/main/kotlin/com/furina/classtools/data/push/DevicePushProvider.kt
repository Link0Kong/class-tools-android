package com.furina.classtools.data.push

/** 当前手机的系统推送入口，与推送注册状态独立。 */
internal enum class DevicePushProvider {
    OPPO,
    VIVO;

    companion object {
        fun forDevice(
            brand: String?,
            manufacturer: String?,
            hasColorOsRom: Boolean,
            supportsOppoPush: Boolean,
        ): DevicePushProvider? {
            val hasOppoService = hasColorOsRom || supportsOppoPush
            for (name in listOf(brand, manufacturer)) {
                when (name?.trim()?.lowercase()) {
                    "vivo", "iqoo" -> return VIVO
                    "oppo", "oplus" -> return OPPO
                    // 一加、realme 的固件可能没有 HeyTap 推送服务。
                    "oneplus", "realme" -> break
                }
            }
            return OPPO.takeIf { hasOppoService }
        }
    }
}
