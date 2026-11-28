package com.furina.classtools.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.settings.HomeContentSettings
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.features.home.HOME_WEATHER_FRESH_MS
import com.furina.classtools.features.home.HomeContentPreferences
import com.furina.classtools.features.home.HomePhrase
import com.furina.classtools.features.home.HomePhraseBatch
import com.furina.classtools.features.home.HomePhraseContext
import com.furina.classtools.features.home.HomeRegion
import com.furina.classtools.features.home.HomeTopic
import com.furina.classtools.features.home.LocalHomeContextSource
import com.furina.classtools.features.home.homeAtmosphere
import com.furina.classtools.features.home.instantOrNull
import com.furina.classtools.features.home.loadHomeRegions
import com.furina.classtools.features.home.selectHomePhrase
import com.furina.classtools.features.home.validHomeSource
import com.furina.classtools.ui.components.CtCard
import com.furina.classtools.ui.navigation.navigationAccount
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import java.net.URI
import java.time.Instant
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
    val openedAt: Long = 0,
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
) {
    val account = session.navigationAccount
    val app = LocalContext.current.applicationContext as? ClassToolsApp
    val openings = app?.openedAt ?: remember { flowOf(0L) }
    val openedAt by openings.collectAsStateWithLifecycle(initialValue = app?.openedAt?.value ?: 0L)
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
    var readCachedContent by remember(source) { mutableStateOf(source == null) }
    LaunchedEffect(source, active, settings != null, preferences.onlineContent, preferences.funContent, now / 60_000) {
        if (source != null && active && settings != null && preferences.funContent && preferences.onlineContent) {
            source.cachedContent(now)?.let { content = it }
            readCachedContent = true
            content = source.content(now)
        }
    }
    val validContent = content?.takeIf { it.validAt(now) }
    val candidates = validContent?.entries.orEmpty()
    val context = HomePhraseContext.at(now, zone, facts?.topicAt(now), course.topic, facts?.important == true)
    val date = context.date.toString()
    val scopeKey = session.issuedAt
    var savedJson by rememberSaveable(account, scopeKey) { mutableStateOf<String?>(null) }
    var recorded by rememberSaveable(account, scopeKey) { mutableStateOf<String?>(null) }
    var memorySeen by remember(account, scopeKey) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var onScreen by remember { mutableStateOf(false) }
    val saved = remember(savedJson) {
        savedJson?.let { runCatching { NetJson.decodeFromString<ShownPhrase>(it) }.getOrNull() }
    }
    val history = settings?.seen.orEmpty() + memorySeen
    val seed = (account.hashCode() * 31 + date.hashCode()) * 31 + context.time.ordinal

    fun qualifies(item: ShownPhrase): Boolean {
        val phrase = item.phrase
        if (!context.allows(phrase, preferences)) return false
        if (phrase.topic.isWeather && item.weatherRegion != regionId) return false
        if (phrase.topic.isCourse && item.courseOccurrence != course.occurrence) return false
        return !phrase.online || (item.sourceExpiresAt ?: 0) > now && validHomeSource(phrase.sourceUrl)
    }

    fun save(phrase: HomePhrase) {
        val expiry = if (phrase.online) instantOrNull(validContent?.expiresAt)?.toEpochMilli() else null
        val entry = ShownPhrase(
            phrase, date, context.time, regionId, context.weather, course.topic, course.occurrence, expiry, openedAt,
        )
        savedJson = NetJson.encodeToString(ShownPhrase.serializer(), entry)
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
        regionId, course.occurrence, waitingForMatchingFact, eligibleSaved?.phrase?.id, openedAt, readCachedContent) {
        if (!active || settings == null || !preferences.funContent || waitingForMatchingFact ||
            preferences.onlineContent && !readCachedContent) return@LaunchedEffect
        val contextChanged = saved?.let {
            (it.date != date || it.time != context.time) ||
                (context.urgentCourse && (it.courseTopic != course.topic || it.courseOccurrence != course.occurrence)) ||
                (context.weather in setOf(HomeTopic.RAIN, HomeTopic.HEAT, HomeTopic.COOLING) &&
                    (it.weatherTopic != context.weather || it.weatherRegion != regionId))
        } == true
        val refreshed = saved?.openedAt != openedAt
        if (eligibleSaved != null && !contextChanged && !refreshed) return@LaunchedEffect
        val previous = saved?.phrase?.id ?: history.maxByOrNull { it.value }?.key
        selectHomePhrase(context, preferences, candidates, history, now, seed,
            excluding = previous.takeIf { contextChanged || refreshed }, manual = false)?.let(::save)
    }
    val phrase = if (preferences.funContent) eligibleSaved?.phrase ?: provisional else null
    val visible = active && onScreen
    LaunchedEffect(eligibleSaved, visible, store, openedAt) {
        val entry = eligibleSaved ?: return@LaunchedEffect
        val id = entry.phrase.id
        val record = "$openedAt/$id"
        if (!visible || entry.openedAt != openedAt || record == recorded) return@LaunchedEffect
        withFrameNanos { }
        memorySeen = (memorySeen + (id to now)).toList().takeLast(96).toMap()
        recorded = record
        try {
            store?.recordHomePhrase(account, id, now)
        } catch (_: java.io.IOException) {
            // The in-memory history still covers the current visit.
        }
    }

    val scene = if (preferences.funContent) homeAtmosphere(facts, now, context.time) else null
    val response = rememberHomeAtmosphereResponse(scene, account, scopeKey, active, preferences.motion && !reduceMotion)
    val greeting = HomeWorkbenchLogic.greeting(Instant.ofEpochMilli(now).atZone(zone).hour)
    val name = session.name.trim()

    CtCard(modifier = Modifier.testTag("home_context_card").semantics { testTagsAsResourceId = true }) {
        Column(verticalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CtSpace.xs)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CtSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(CtSpace.xxs),
                    ) {
                        Text(HomeWorkbenchLogic.dateText(context.date), style = CtText.label,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("home_context_date"))
                        Text(if (name.isEmpty()) greeting else "$greeting，$name", style = CtText.label,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_context_greeting"))
                    }
                    if (phrase != null) {
                        Text(phrase.text,
                            modifier = Modifier.testTag("home_context_phrase").onGloballyPositioned {
                                onScreen = it.boundsInWindow().height > 0f
                            },
                            style = if (phrase.translation != null && preferences.onlineContent &&
                                validContent?.englishTypography == "extraLarge"
                            ) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        phrase.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                            Text(translation, style = CtText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("home_context_translation"))
                        }
                    }
                }
                if (scene != null && response != null) {
                    HomeAtmosphereIcon(scene, response)
                }
            }
            if (facts != null && region != null) {
                if (phrase != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val temperature = facts.temperatureC?.let { it.roundToInt().toString() + " ℃" }
                val time = instantOrNull(facts.validTime)?.atZone(weatherZone)?.format(weatherTime)
                Text(listOfNotNull(region.label, facts.conditionText, temperature, time).joinToString(" · "),
                    style = if (facts.important) CtText.body else CtText.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("home_context_weather"))
                val creditColor = MaterialTheme.colorScheme.onSurfaceVariant
                val credit = remember(facts.attributions, creditColor) { weatherCredit(facts.attributions, creditColor) }
                Text(credit, style = CtText.caption, color = creditColor)
            }
            if (response?.showFeedback == true && scene != null) {
                Text(scene.feedback, style = CtText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home_context_feedback").semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

private fun weatherCredit(attributions: List<String>, color: Color): AnnotatedString = buildAnnotatedString {
    val names = (listOf("和风天气") + attributions.filterNot { "://" in it }).distinct()
    val styles = TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.None))
    val links = attributions.distinct().mapNotNull { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        uri?.takeIf { it.scheme.equals("https", ignoreCase = true) && it.userInfo == null && it.host != null }
    }
    val qweather = links.firstOrNull { it.host == "qweather.com" || it.host.endsWith(".qweather.com") }
    if (qweather == null) {
        append(names.joinToString(" · "))
    } else {
        withLink(LinkAnnotation.Url(qweather.toString(), styles)) { append(names.joinToString(" · ")) }
    }
    links.filterNot { it == qweather }.forEach { link ->
        append(" · ")
        withLink(LinkAnnotation.Url(link.toString(), styles)) { append(link.host) }
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
