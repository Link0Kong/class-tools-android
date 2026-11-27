package com.furina.classtools.features.home

import android.content.res.AssetManager
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

internal const val HOME_WEATHER_REFRESH_MS = 15L * 60 * 1000
internal const val HOME_WEATHER_FRESH_MS = 45L * 60 * 1000

internal data class HomeRegion(val id: String, val name: String, val parent: String = "") {
    val label: String get() = if (id == minhou.id) "福州市闽侯县" else listOf(parent, name).filter(String::isNotBlank).joinToString(" · ")

    companion object {
        val minhou = HomeRegion("minhou", "闽侯县", "福建省 · 福州市")
    }
}

internal fun loadHomeRegions(assets: AssetManager): List<HomeRegion> {
    val others = runCatching {
        assets.open("home_regions.tsv").bufferedReader().useLines { rows ->
            rows.filterNot { it.startsWith("#") }.mapNotNull { row ->
                val fields = row.split('\t')
                if (fields.size != 5 || fields[0] == HomeRegion.minhou.id) null
                else HomeRegion(fields[0], fields[1], fields[2])
            }.toList()
        }
    }.getOrDefault(emptyList())
    return listOf(HomeRegion.minhou) + others
}

@Serializable
internal data class HomeWeather(
    val regionId: String,
    val validTime: String,
    val conditionCode: Int,
    val conditionText: String,
    val temperatureC: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val cooling: Boolean = false,
    val attributions: List<String> = emptyList(),
) {
    private val severe: Boolean get() = conditionCode in severeCodes
    val important: Boolean get() = severe

    fun validFor(id: String, nowMillis: Long): Boolean {
        val time = instantOrNull(validTime)?.toEpochMilli() ?: return false
        return regionId == id && time > 0 && nowMillis >= time && nowMillis - time < HOME_WEATHER_FRESH_MS &&
            conditionCode in 100..999 && conditionCode != 999 && conditionText.isNotBlank() &&
            (temperatureC == null || temperatureC.isFinite() && temperatureC in -90.0..65.0)
    }

    fun topicAt(nowMillis: Long): HomeTopic? {
        if (severe) return null
        return when {
            conditionCode in rainCodes -> HomeTopic.RAIN
            conditionCode == 900 || (temperatureC ?: 0.0) >= 35.0 -> HomeTopic.HEAT
            cooling -> HomeTopic.COOLING
            conditionCode == 100 -> daylightTopic(nowMillis)
            conditionCode in 101..104 -> HomeTopic.CLOUD
            else -> null
        }
    }

    internal fun daylightTopic(nowMillis: Long): HomeTopic? {
        val now = Instant.ofEpochMilli(nowMillis)
        val rise = instantOrNull(sunrise) ?: return null
        val set = instantOrNull(sunset) ?: return null
        val regionZone = ZoneId.of("Asia/Shanghai")
        val date = now.atZone(regionZone).toLocalDate()
        if (set <= rise || rise.atZone(regionZone).toLocalDate() != date ||
            set.atZone(regionZone).toLocalDate() != date) return null
        return if (now >= rise && now < set) HomeTopic.CLEAR_DAY else HomeTopic.CLEAR_NIGHT
    }

    companion object {
        private val rainCodes = setOf(300, 305, 306, 309, 314, 399)
        private val severeCodes = setOf(301, 302, 303, 304, 307, 308, 310, 311, 312, 313, 315, 316, 317, 318, 402, 403, 410, 507, 508, 514, 515)
    }
}

internal enum class HomeAtmosphere { RAIN, SUN, NIGHT }

internal fun homeAtmosphere(weather: HomeWeather?, now: Long, time: HomeTopic): HomeAtmosphere? {
    val code = weather?.conditionCode ?: 0
    if (code in 300..318 || code == 350 || code == 351 || code == 399) return HomeAtmosphere.RAIN
    if (weather?.important == true) return null
    val night = time == HomeTopic.DUSK || time == HomeTopic.NIGHT
    if (code in 150..153) return HomeAtmosphere.NIGHT
    if (weather?.conditionCode == 100) {
        return when (weather.daylightTopic(now)) {
            HomeTopic.CLEAR_DAY -> HomeAtmosphere.SUN
            HomeTopic.CLEAR_NIGHT -> HomeAtmosphere.NIGHT
            else -> if (night) HomeAtmosphere.NIGHT else HomeAtmosphere.SUN
        }
    }
    return HomeAtmosphere.NIGHT.takeIf { night }
}

@Serializable
internal data class HomePhraseBatch(
    val generatedAt: String,
    val expiresAt: String,
    val entries: List<HomePhrase>,
) {
    fun validAt(now: Long): Boolean {
        val from = instantOrNull(generatedAt)?.toEpochMilli() ?: return false
        val to = instantOrNull(expiresAt)?.toEpochMilli() ?: return false
        return from <= now && now < to && to - from in 1..(72L * 60 * 60 * 1000)
    }
}

internal fun instantOrNull(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() }
