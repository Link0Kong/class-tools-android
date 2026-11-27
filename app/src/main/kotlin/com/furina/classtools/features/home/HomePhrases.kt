package com.furina.classtools.features.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

internal enum class HomeTone(val label: String) {
    SIMPLE("简洁"), GENTLE("温柔"), PLAYFUL("俏皮");

    companion object {
        fun fromStorage(value: String?): HomeTone = entries.firstOrNull { it.name == value } ?: GENTLE
    }
}

internal enum class HomeTopic {
    MORNING, DAY, DUSK, NIGHT,
    CLEAR_DAY, CLEAR_NIGHT, CLOUD, RAIN, HEAT, COOLING,
    UPCOMING, BREAK, ONGOING, ENDED, NO_CLASS,
    ENGLISH, EVERYDAY, STUDY, GENERAL;

    val isWeather: Boolean
        get() = when (this) {
            CLEAR_DAY, CLEAR_NIGHT, CLOUD, RAIN, HEAT, COOLING -> true
            else -> false
        }

    val isCourse: Boolean
        get() = when (this) {
            UPCOMING, BREAK, ONGOING, ENDED, NO_CLASS -> true
            else -> false
        }
}

@Serializable
internal data class HomePhrase(
    val id: String,
    val text: String,
    val topic: HomeTopic,
    val tone: HomeTone,
    val translation: String? = null,
    val sourceTitle: String? = null,
    val sourceUrl: String? = null,
) {
    val online: Boolean get() = topic == HomeTopic.EVERYDAY || topic == HomeTopic.STUDY
}

@Serializable
internal data class HomeContentPreferences(
    val funContent: Boolean = true,
    val onlineContent: Boolean = true,
    val english: Boolean = true,
    val motion: Boolean = true,
    val tone: HomeTone = HomeTone.GENTLE,
    val regionId: String? = null,
)

internal const val HOME_RECENT_MS = 7L * 24 * 60 * 60 * 1000

internal data class HomePhraseContext(
    val date: LocalDate,
    val time: HomeTopic,
    val weather: HomeTopic?,
    val course: HomeTopic?,
    val importantWeather: Boolean = false,
) {
    val urgentCourse: Boolean get() = course in setOf(HomeTopic.UPCOMING, HomeTopic.BREAK, HomeTopic.ONGOING)

    fun allows(phrase: HomePhrase, preferences: HomeContentPreferences): Boolean {
        if (!preferences.funContent || phrase.tone != preferences.tone) return false
        return when {
            phrase.topic == HomeTopic.ENGLISH -> preferences.english && !urgentCourse && !importantWeather
            phrase.online -> preferences.onlineContent && !urgentCourse && !importantWeather
            phrase.topic.isWeather -> !importantWeather && phrase.topic == weather
            phrase.topic.isCourse -> phrase.topic == course
            else -> phrase.topic == time || phrase.topic == HomeTopic.GENERAL
        }
    }

    companion object {
        fun at(now: Long, zone: ZoneId, weather: HomeTopic?, course: HomeTopic?, importantWeather: Boolean): HomePhraseContext {
            val local = Instant.ofEpochMilli(now).atZone(zone)
            val time = when (local.hour) {
                in 5..8 -> HomeTopic.MORNING
                in 9..17 -> HomeTopic.DAY
                in 18..22 -> HomeTopic.DUSK
                else -> HomeTopic.NIGHT
            }
            return HomePhraseContext(local.toLocalDate(), time, weather, course, importantWeather)
        }
    }
}

internal fun selectHomePhrase(
    context: HomePhraseContext,
    preferences: HomeContentPreferences,
    online: List<HomePhrase>,
    history: Map<String, Long>,
    now: Long,
    seed: Int,
    excluding: String? = null,
    local: List<HomePhrase> = localHomePhrases,
    manual: Boolean = excluding != null,
): HomePhrase? {
    val eligible = (local + online).filter { context.allows(it, preferences) }
    val alternatives = eligible.filterNot { it.id == excluding }.ifEmpty { eligible }
    val recent = history.filterValues { it <= now && now - it < HOME_RECENT_MS }
    val contextualWeather = context.weather in setOf(HomeTopic.RAIN, HomeTopic.HEAT, HomeTopic.COOLING)
    val priority = when {
        context.urgentCourse -> alternatives.filter { it.topic == context.course }
        contextualWeather && !manual -> alternatives.filter { it.topic == context.weather }
        else -> emptyList()
    }
    val preferred = priority.ifEmpty {
        val english = alternatives.filter { it.topic == HomeTopic.ENGLISH }
        val others = alternatives.filterNot { it.topic == HomeTopic.ENGLISH }
        if (Math.floorMod(seed, 4) == 0 && english.isNotEmpty()) english else others.ifEmpty { english }
    }
    val unseen = preferred.filterNot { it.id in recent }
    // Keep urgent and weather context when their pools are small, then reuse the oldest eligible line.
    val candidates = unseen.ifEmpty {
        val oldest = preferred.minOfOrNull { recent[it.id] ?: Long.MIN_VALUE }
        preferred.filter { (recent[it.id] ?: Long.MIN_VALUE) == oldest }
    }
    return if (candidates.isEmpty()) null else candidates[Math.floorMod(seed, candidates.size)]
}

internal val localHomePhrases: List<HomePhrase> = buildList {
    fun group(topic: HomeTopic, simple: String, gentle: String, playful: String) {
        listOf(simple, gentle, playful).forEachIndexed { index, line ->
            val tone = HomeTone.entries[index]
            add(HomePhrase("local-" + topic.name.lowercase() + "-" + tone.name.lowercase(), line, topic, tone))
        }
    }

    group(HomeTopic.MORNING, "早晨，先从手边的一件事开始。", "翻开一页，看看今天有什么新发现。", "今天的第一行，留个空位给好奇心。")
    group(HomeTopic.DAY, "读到有意思的地方，可以停一停。", "给一个新想法留点位置。", "遇到有趣的问题，先在脑袋里打个星。")
    group(HomeTopic.DUSK, "把今天值得记的一点写下来。", "给傍晚留一段不着急的时间。", "零散的念头，也有自己的收纳格。")
    group(HomeTopic.NIGHT, "换个安静的节奏。", "把今天的句号写轻一点。", "让脑袋切到夜间模式，亮度调低一点。")
    group(HomeTopic.CLEAR_DAY, "天晴，出门时可以看看天空。", "这一会儿的晴光，刚好够照亮窗边。", "阳光已到岗，今天慢慢展开。")
    group(HomeTopic.CLEAR_NIGHT, "今夜晴朗，留意窗外。", "晴夜适合留一点安静给自己。", "夜空开了晴朗模式。")
    group(HomeTopic.CLOUD, "云多，光线柔和些。", "阴天也有自己的明亮。", "天空调成了柔光模式。")
    group(HomeTopic.RAIN, "下雨了，出门记得带伞。", "听一会儿雨，再继续手边的事。", "今天出门，给雨伞一个上班的机会。")
    group(HomeTopic.HEAT, "天气热，出门可以带上水。", "热意在，走到阴凉处缓一缓。", "给水杯多安排一点出场时间。")
    group(HomeTopic.COOLING, "气温降了，出门可以添一层。", "空气凉了些，留件薄外套在手边。", "气温往下挪，外套可以出场了。")
    group(HomeTopic.UPCOMING, "快到上课时间了。", "给下一节课留一个从容的开头。", "下一站，教室。带上书，也带上好奇心。")
    group(HomeTopic.BREAK, "课间可以起身活动一下。", "课间有片刻，正好看看窗外。", "课间先让脑袋散个步。")
    group(HomeTopic.ONGOING, "课表上的课程正在进行。", "读到感兴趣的一点，可以记在页边。", "给听见的新点子留一行空位。")
    group(HomeTopic.ENDED, "今天课表里的课已结束。", "今天的课程落了笔，可以慢慢换个节奏。", "今天的课表收工啦。")
    group(HomeTopic.NO_CLASS, "今天的课表里没有安排。", "课表空出一天，时间可以慢些走。", "今天的课表留了一整页空白。")
    group(HomeTopic.GENERAL, "可以先记下一个问题。", "给手边的事一点专注，也给自己一点余地。", "脑袋也需要一个放便签的角落。")

    fun english(id: Int, tone: HomeTone, text: String, translation: String) {
        add(HomePhrase("local-en-$id", text, HomeTopic.ENGLISH, tone, translation))
    }
    english(1, HomeTone.SIMPLE, "Start with one clear question.", "先从一个清楚的问题开始。")
    english(2, HomeTone.SIMPLE, "Leave a little room to think.", "留一点空间思考。")
    english(3, HomeTone.SIMPLE, "A short note can save an idea.", "简短的一笔，就能留住一个想法。")
    english(4, HomeTone.GENTLE, "Small steps still move you forward.", "步子虽小，也在向前。")
    english(5, HomeTone.GENTLE, "Make room for one new idea today.", "今天，给一个新想法留点位置。")
    english(6, HomeTone.GENTLE, "Some ideas take a little quiet.", "有些想法需要一点安静。")
    english(7, HomeTone.PLAYFUL, "Give your curiosity a window seat.", "给好奇心留个靠窗的位置。")
    english(8, HomeTone.PLAYFUL, "Let a good question start the conversation.", "让一个好问题来开场。")
    english(9, HomeTone.PLAYFUL, "Your notebook has room for a detour.", "笔记本也容得下一点跑题。")
}
