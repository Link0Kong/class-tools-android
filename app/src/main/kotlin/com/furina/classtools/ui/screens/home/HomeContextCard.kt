package com.furina.classtools.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.settings.HomeContentSettings
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.features.home.HOME_WEATHER_FRESH_MS
import com.furina.classtools.features.home.HomeContentPreferences
import com.furina.classtools.features.home.HomePhrase
import com.furina.classtools.features.home.HomePhraseBatch
import com.furina.classtools.features.home.HomePhraseContext
import com.furina.classtools.features.home.HomeRegion
import com.furina.classtools.features.home.HomeTopic
import com.furina.classtools.features.home.LocalHomeContextSource
import com.furina.classtools.features.home.instantOrNull
import com.furina.classtools.features.home.loadHomeRegions
import com.furina.classtools.features.home.selectHomePhrase
import com.furina.classtools.features.home.validHomeSource
import com.furina.classtools.ui.components.CtButton
import com.furina.classtools.ui.components.CtButtonStyle
import com.furina.classtools.ui.components.CtCard
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.navigation.navigationAccount
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private data class ShownPhrase(
    val phrase: HomePhrase,
    val date: String,
    val time: HomeTopic,
    val weatherRegion: String?,
    val weatherTopic: HomeTopic?,
    val courseTopic: HomeTopic?,
    val courseOccurrence: Long?,
    val sourceExpiresAt: Long?,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeContextCard(
    session: Session,
    course: HomeCourseContext,
    now: Long,
    zone: ZoneId,
    active: Boolean,
    reduceMotion: Boolean,
    onOpenCourse: (CourseFocus) -> Unit,
) {
    val account = session.navigationAccount
    val store = LocalAppSettingsStore.current
    val settingsFlow = remember(store, account) { store?.homeContent(account) ?: flowOf(HomeContentSettings()) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val preferences = settings?.preferences ?: HomeContentPreferences()
    val source = LocalHomeContextSource.current
    val region = rememberHomeRegion(preferences.regionId)
    val regionId = region?.id
    var readingWeather by remember(source, regionId) { mutableStateOf(source != null) }
    var weather by remember(source, regionId) { mutableStateOf(regionId?.let { source?.cachedWeather(it, now) }) }
    LaunchedEffect(source, regionId, active, settings != null, now / 60_000) {
        if (source != null && regionId != null && active && settings != null) {
            weather = source.weather(regionId, now)
            readingWeather = false
        }
    }
    val facts = weather?.takeIf { regionId != null && it.validFor(regionId, now) }
    LaunchedEffect(facts?.validTime, regionId, active) {
        val expires = instantOrNull(facts?.validTime)?.toEpochMilli()?.plus(HOME_WEATHER_FRESH_MS) ?: return@LaunchedEffect
        if (active && expires > now) {
            delay(expires - now)
            weather = null
        }
    }
    var content by remember(source) { mutableStateOf<HomePhraseBatch?>(null) }
    LaunchedEffect(source, active, settings != null, preferences.onlineContent, preferences.funContent, now / 60_000) {
        if (source != null && active && settings != null && preferences.funContent && preferences.onlineContent) {
            source.cachedContent(now)?.let { content = it }
            content = source.content(now)
        }
    }
    val validContent = content?.takeIf { it.validAt(now) }
    val candidates = validContent?.entries.orEmpty()
    val context = HomePhraseContext.at(now, zone, facts?.topicAt(now), course.topic, facts?.important == true)
    val date = context.date.toString()
    val scopeKey = session.issuedAt
    var savedJson by rememberSaveable(account, scopeKey) { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable(account, scopeKey) { mutableStateOf(false) }
    var showSource by rememberSaveable(account, scopeKey) { mutableStateOf(false) }
    var choice by rememberSaveable(account, scopeKey) { mutableIntStateOf(0) }
    var recorded by rememberSaveable(account, scopeKey) { mutableStateOf<String?>(null) }
    var memorySeen by remember(account, scopeKey) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var onScreen by remember { mutableStateOf(false) }
    val saved = remember(savedJson) {
        savedJson?.let { runCatching { NetJson.decodeFromString<ShownPhrase>(it) }.getOrNull() }
    }
    val history = settings?.seen.orEmpty() + memorySeen
    val seed = (account.hashCode() * 31 + date.hashCode()) * 31 + context.time.ordinal + choice * 17

    fun qualifies(item: ShownPhrase): Boolean {
        val phrase = item.phrase
        if (!context.allows(phrase, preferences) &&
            !(expanded && phrase.topic == HomeTopic.ENGLISH && preferences.english &&
                preferences.funContent && preferences.tone == phrase.tone)) return false
        if (phrase.topic.isWeather && item.weatherRegion != regionId) return false
        if (phrase.topic.isCourse && item.courseOccurrence != course.occurrence) return false
        return !phrase.online || (item.sourceExpiresAt ?: 0) > now && validHomeSource(phrase.sourceUrl)
    }

    fun save(phrase: HomePhrase) {
        val expiry = if (phrase.online) instantOrNull(validContent?.expiresAt)?.toEpochMilli() else null
        val entry = ShownPhrase(phrase, date, context.time, regionId, context.weather, course.topic, course.occurrence, expiry)
        savedJson = NetJson.encodeToString(ShownPhrase.serializer(), entry)
        expanded = false
        showSource = false
    }

    val eligibleSaved = saved?.takeIf(::qualifies)
    val waitingForMatchingFact = saved?.let {
        (it.phrase.topic.isCourse && course.reloading && it.date == date) ||
            (it.phrase.topic.isWeather && it.weatherRegion == regionId && readingWeather &&
                it.date == date && it.time == context.time)
    } == true
    val provisional = remember(context.time, context.date, preferences) {
        selectHomePhrase(context.copy(weather = null, course = null), preferences.copy(english = false, onlineContent = false),
            emptyList(), emptyMap(), now, seed)
    }

    LaunchedEffect(active, settings != null, preferences.funContent, preferences.tone, preferences.english,
        preferences.onlineContent, date, context.time, context.weather, context.course, context.importantWeather,
        regionId, course.occurrence, waitingForMatchingFact, eligibleSaved?.phrase?.id, expanded) {
        if (settings == null || !preferences.funContent || waitingForMatchingFact) return@LaunchedEffect
        val contextChanged = saved?.let {
            (it.date != date || it.time != context.time) ||
                (context.urgentCourse && (it.courseTopic != course.topic || it.courseOccurrence != course.occurrence)) ||
                (context.weather in setOf(HomeTopic.RAIN, HomeTopic.HEAT, HomeTopic.COOLING) &&
                    (it.weatherTopic != context.weather || it.weatherRegion != regionId))
        } == true
        if (eligibleSaved != null && (!contextChanged || expanded)) return@LaunchedEffect
        selectHomePhrase(context, preferences, candidates, history, now, seed, excluding = if (contextChanged) saved.phrase.id else null,
            manual = false)?.let(::save)
    }
    val phrase = if (preferences.funContent) eligibleSaved?.phrase ?: provisional else null
    val visible = active && onScreen
    LaunchedEffect(eligibleSaved?.phrase?.id, visible, store) {
        val id = eligibleSaved?.phrase?.id ?: return@LaunchedEffect
        if (!visible || id == recorded) return@LaunchedEffect
        withFrameNanos { }
        memorySeen = (memorySeen + (id to now)).toList().takeLast(96).toMap()
        recorded = id
        try {
            store?.recordHomePhrase(account, id, now)
        } catch (_: java.io.IOException) {
            // The in-memory history still covers the current visit.
        }
    }

    var tap by remember { mutableIntStateOf(0) }
    var lit by remember { mutableStateOf(false) }
    val bulbScale = remember { Animatable(1f) }
    LaunchedEffect(tap, visible, reduceMotion, preferences.motion, phrase?.id) {
        lit = false
        bulbScale.snapTo(1f)
        if (tap <= 0 || !visible) return@LaunchedEffect
        lit = true
        if (preferences.motion && !reduceMotion) {
            bulbScale.animateTo(1.12f, CtMotion.spatialFast)
            bulbScale.animateTo(1f, CtMotion.spatialFast)
        }
        delay(500)
        lit = false
        tap = 0
    }
    LaunchedEffect(visible, preferences.motion, phrase?.id) {
        if (!visible) tap = 0
    }

    val onSwap = {
        val nextChoice = choice + 1
        selectHomePhrase(context, preferences, candidates, history, now, seed + 17, excluding = phrase?.id)?.let { next ->
            if (next.id != phrase?.id) {
                choice = nextChoice
                tap = 0
                save(next)
            }
        }
        Unit
    }
    val canSwap = settings != null && phrase != null &&
        selectHomePhrase(context, preferences, candidates, history, now, seed + 17, excluding = phrase?.id)
            ?.let { it.id != phrase?.id } == true
    val courseButton = course.action?.takeIf {
        !preferences.funContent || context.urgentCourse ||
            it == "查看明日课表" && phrase?.translation == null && phrase?.online != true
    }
    val primaryText = when {
        courseButton != null && phrase?.translation == null -> courseButton
        phrase?.translation != null -> if (expanded) "收起中文" else "看中文"
        canSwap -> "换一句"
        else -> null
    }
    val openCourse = { course.focus?.let(onOpenCourse); Unit }
    val onPrimary = when {
        courseButton != null && phrase?.translation == null -> openCourse
        phrase?.translation != null -> { { expanded = !expanded } }
        else -> onSwap
    }

    CtCard(modifier = Modifier.testTag("home_context_card").semantics { testTagsAsResourceId = true }) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
            Text(HomeWorkbenchLogic.dateText(context.date), style = CtText.label,
                color = MaterialTheme.colorScheme.onSurface)
            if (phrase != null) {
                Text(phrase.text,
                    modifier = Modifier.testTag("home_context_phrase").onGloballyPositioned {
                        onScreen = it.boundsInWindow().height > 0f
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                if (expanded) phrase.translation?.let {
                    Text(it, style = CtText.body, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (facts != null && region != null) {
                val temperature = facts.temperatureC?.let { it.roundToInt().toString() + " ℃" }
                val time = instantOrNull(facts.validTime)?.atZone(weatherZone)?.format(weatherTime)
                Text(listOfNotNull(region.label, facts.conditionText, temperature, time).joinToString(" · "),
                    style = if (facts.important) CtText.body else CtText.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("home_context_weather"))
                Text((listOf("和风天气") + facts.attributions).distinct().joinToString(" · "),
                    style = CtText.caption, color = MaterialTheme.colorScheme.onSurface)
            }
            if (course.status != null) Text(course.status, style = CtText.label, color = MaterialTheme.colorScheme.onSurface)
            course.current?.let { HomeCourseLine("正在上课", it) }
            course.next?.let { HomeCourseLine(if (course.cached) "缓存中的下节课" else "下一节", it) }
            if (primaryText != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
                    verticalArrangement = Arrangement.spacedBy(CtSpace.xxs),
                ) {
                    CtButton(primaryText, onPrimary, compact = true, fillWidth = false, style = CtButtonStyle.Tonal,
                        modifier = Modifier.semantics {
                            if (phrase?.translation != null) stateDescription = if (expanded) "已展开" else "已收起"
                        })
                    when {
                        canSwap && primaryText != "换一句" -> CtButton("换一句", onSwap, compact = true,
                            fillWidth = false, style = CtButtonStyle.Text)
                        phrase?.online == true && validHomeSource(phrase.sourceUrl) -> CtButton("出处", { showSource = true },
                            compact = true, fillWidth = false, style = CtButtonStyle.Text)
                        phrase != null && primaryText == "换一句" -> IconButton(
                            onClick = { tap += 1 },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                stateDescription = if (lit) "已点亮" else "未点亮"
                            },
                        ) {
                            Icon(Icons.Rounded.Lightbulb, "点亮小灯",
                                tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer { scaleX = bulbScale.value; scaleY = bulbScale.value })
                        }
                    }
                }
            }
        }
    }
    val url = phrase?.sourceUrl
    if (showSource && phrase?.online == true && url != null && validHomeSource(url)) {
        val uriHandler = LocalUriHandler.current
        CtBottomSheet(onDismissRequest = { showSource = false }) {
            CtBottomSheetHeader(title = "文案出处")
            Column(Modifier.padding(horizontal = CtSpace.cardPadding, vertical = CtSpace.sm),
                verticalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                Text("由 DeepSeek 根据公开资料生成。", color = MaterialTheme.colorScheme.onSurface, style = CtText.body)
                Text(phrase.sourceTitle.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = CtText.body)
                CtButton("阅读原文", {
                    runCatching { uriHandler.openUri(url) }
                }, compact = true, style = CtButtonStyle.Tonal)
                CtButton("关闭", { showSource = false }, compact = true, style = CtButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun HomeCourseLine(label: String, session: SessionInfo) {
    val whenText = listOf(session.dayText.takeUnless { it == "今天" }.orEmpty(), session.startText)
        .filter { it.isNotBlank() }.joinToString(" ")
    Text(listOf(label, whenText, session.name).filter { it.isNotBlank() }.joinToString(" · "),
        style = CtText.body, color = MaterialTheme.colorScheme.onSurface)
    session.fullRoom.takeIf { it.isNotBlank() }?.let {
        Text(it, style = CtText.caption, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun rememberHomeRegion(id: String?): HomeRegion? {
    if (id == null || id == HomeRegion.minhou.id) return HomeRegion.minhou
    val assets = LocalContext.current.assets
    val selected by produceState<HomeRegion?>(null, assets, id) {
        value = null
        value = withContext(Dispatchers.IO) { loadHomeRegions(assets).firstOrNull { it.id == id } ?: HomeRegion.minhou }
    }
    return selected?.takeIf { it.id == id || it.id == HomeRegion.minhou.id }
}

private val weatherZone = ZoneId.of("Asia/Shanghai")
private val weatherTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
