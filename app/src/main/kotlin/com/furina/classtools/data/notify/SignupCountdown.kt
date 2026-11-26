package com.furina.classtools.data.notify

import com.furina.classtools.data.calendar.NoticeCalendarMapper
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.timetable.TimetableLink
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
internal data class SignupCountdown(
    val owner: String,
    val id: String,
    val title: String,
    val start: Long,
    val end: Long? = null,
    val location: String = "",
    val source: String = "",
    val signupUrl: String = "",
    val leadMinutes: Int = 5,
    val customTime: Boolean = false,
    val opened: Boolean = false,
) {
    val showAt get() = start - leadMinutes * 60_000L
    val expiresAt get() = minOf(start + 120_000L, end ?: Long.MAX_VALUE)
    fun details() = buildList {
        add("报名开始  ${timeText(start)}")
        end?.let { add("报名截止  ${timeText(it)}") }
        if (location.isNotBlank()) add("地点  $location")
        if (source.isNotBlank()) add("来源  $source")
    }.joinToString("\n")

    companion object {
        private val clock = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.CHINA)
            .withZone(TimetableLink.CHINA_ZONE)
        fun timeText(at: Long): String = clock.format(Instant.ofEpochMilli(at))

        fun from(owner: String, group: NoticeGroupDto, items: List<NoticeItemMeta>, now: Long,
            customStart: Long? = null): SignupCountdown? {
            val window = NoticeStatusMachine.nextSignup(group, now, TimetableLink.CHINA_ZONE)
            val start = customStart ?: window?.start ?: return null
            if (owner.isBlank() || group.id.isBlank() || start <= now) return null
            val link = items.firstNotNullOfOrNull { item ->
                val url = when (item) {
                    is NoticeItemMeta.Form -> item.url.takeIf { item.name.contains("报名") }
                    is NoticeItemMeta.Link -> item.url.takeIf { (item.name + item.title).contains("报名") }
                    else -> null
                }
                url?.takeIf { value -> runCatching { URI(value).let {
                    it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null
                } }.getOrDefault(false) }
            }.orEmpty()
            return SignupCountdown(owner, group.id, group.title.trim().take(180).ifBlank { "活动报名" }, start,
                window?.end?.takeIf { it > start && (customStart == null || window.start == start) },
                NoticeCalendarMapper.locationOf(group).take(100), group.source.trim().take(100), link,
                customTime = customStart != null)
        }
    }
}

/** Round coarse stages upward so the UI never says zero before the opening instant. */
internal object SignupClock {
    fun label(left: Long): String = when {
        left <= 0 -> "已到报名时间"
        left > 3_600_000 -> {
            val minutes = (left + 59_999) / 60_000
            "${minutes / 60}小时 ${minutes % 60}分"
        }
        left > 60_000 -> {
            val seconds = (left + 999) / 1000
            "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
        }
        left > 10_000 -> {
            val tenths = (left + 99) / 100
            "${tenths / 10}.${tenths % 10} 秒"
        }
        else -> "%d.%03d 秒".format(Locale.ROOT, left / 1000, left % 1000)
    }

    /** Zero requests the next display frame. Slow stages wake exactly on the next digit change. */
    fun delayMillis(left: Long): Long {
        val step = when {
            left > 3_600_000 -> 60_000L
            left > 60_000 -> 1_000L
            left > 10_000 -> 100L
            else -> return 0L
        }
        return (left - 1) % step + 1
    }
}
