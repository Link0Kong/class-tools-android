package com.furina.classtools.data.visits

import android.content.Context
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.session.Session
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 安卓访问上报（唯一口径：`web/统计多端_详细统计_契约_20261120.md` §1/§5 安卓）：
 *
 * ```
 * POST {REST}visits
 * Prefer: resolution=ignore-duplicates,return=minimal
 * [{"id":"<sid>|<slot>|android","slot":"YYYY-MM-DD-HH","sid":"<sid>","name":"<姓名>",
 *   "did":"<16 位小写十六进制>","client":"android",
 *   "ver":"<版本短标签 ≤32>","dev":"<设备短标签 ≤48>","ts":<epoch 毫秒>}]
 * ```
 *
 * - `client` 固定 `android`；`id` 含端代码，避免同一小时换端被主键/去重吞掉；
 * - `ver` 取 [versionProvider]（默认 `BuildConfig.VERSION_NAME`），去空白/去 `v` 前缀、截断 32；
 * - `dev` 取 [deviceLabelProvider]（默认 [androidDeviceLabel]），去重品牌、合并空白、截断 48；
 *   两个 provider 失败或为空时写空串（列有默认值），**不阻断上报**；
 * - 同一进程内同一 `sid` + `slot` 成功上报后不再重复（进程重启后由服务端主键 +
 *   `resolution=ignore-duplicates` 兜底）；失败不记 slot，下次会话变化/重启可重试；
 * - 失败静默返回 `false`，不抛异常、不影响登录与首屏；
 * - **不请求 representation**（历史 401 教训：returning 要求新行同时可 SELECT）。
 *
 * 线程安全：实例由 AppContainer 单例持有，[reportOnce] 可在任意线程并发调用。
 *
 * @param versionProvider 版本短标签来源（测试注入固定值；默认应用版本名）
 * @param deviceLabelProvider 设备短标签来源（测试注入固定值；默认 [androidDeviceLabel]）
 */
class VisitReporter(
    private val api: ApiClient,
    private val currentSession: () -> Session?,
    private val deviceIdProvider: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val versionProvider: () -> String = { com.furina.classtools.BuildConfig.VERSION_NAME },
    private val deviceLabelProvider: () -> String = { androidDeviceLabel() },
) {
    private val lock = Any()

    /** sid → 最近一次成功上报的 slot（仅本进程）。 */
    private val reportedSlots = HashMap<String, String>()

    /** 正在上报的 `sid|slot`（防并发重复 POST）。 */
    private val inFlight = HashSet<String>()

    suspend fun reportOnce(): Boolean {
        val session = runCatching { currentSession() }.getOrNull() ?: return false
        if (session.debug) return false
        val sid = session.sid.trim()
        val name = session.name.trim()
        if (sid.isEmpty() || name.isEmpty()) return false

        val now = clock()
        val slot = slotOf(now, zoneId)
        val key = "$sid|$slot"
        synchronized(lock) {
            if (reportedSlots[sid] == slot) return false
            if (!inFlight.add(key)) return false
        }

        val did = runCatching { deviceIdProvider().trim() }.getOrDefault("")
        if (!isValidDeviceId(did)) {
            synchronized(lock) { inFlight.remove(key) }
            return false
        }

        // 版本/设备只是附加信息：provider 抛异常或为空都写空串（列有默认值），不阻断上报。
        val ver = normalizeVersion(safeProvider(versionProvider))
        val dev = normalizeDeviceLabel(safeProvider(deviceLabelProvider))

        val body = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "$sid|$slot|$CLIENT")
                    put("slot", slot)
                    put("sid", sid)
                    put("name", name)
                    put("did", did)
                    put("client", CLIENT)
                    put("ver", ver)
                    put("dev", dev)
                    put("ts", now)
                },
            )
        }.toString()

        val sent = try {
            when (api.post(path = PATH, body = body, prefer = PREFER, authenticated = true)) {
                is ApiResult.Success -> true
                is ApiResult.Failure -> false
            }
        } catch (cancelled: CancellationException) {
            synchronized(lock) { inFlight.remove(key) }
            throw cancelled
        } catch (_: Throwable) {
            false
        }

        synchronized(lock) {
            inFlight.remove(key)
            if (sent) reportedSlots[sid] = slot
        }
        return sent
    }

    companion object {
        /** 登录端代码（契约 §1 固定小写取值）。 */
        const val CLIENT: String = "android"

        /** PostgREST 资源路径（不带 representation）。 */
        const val PATH: String = "visits"

        /** 幂等去重 + 不返回行；写 visits 不能要求 representation。 */
        const val PREFER: String = "resolution=ignore-duplicates,return=minimal"

        /** 契约 §1：ver ≤ 32 字符。 */
        const val MAX_VERSION_LENGTH: Int = 32

        /** 契约 §1：dev ≤ 48 字符。 */
        const val MAX_DEVICE_LENGTH: Int = 48

        private val WHITESPACE: Regex = Regex("\\s+")

        private val SLOT_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH", Locale.ROOT)

        /** 访问小时槽：本地时区 `YYYY-MM-DD-HH`（与网页 `vsSlot` / 小程序同口径）。 */
        fun slotOf(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
            SLOT_FORMAT.format(Instant.ofEpochMilli(nowMillis).atZone(zoneId))

        /** did 契约：16 位小写十六进制（8~64 hex 内，安卓固定 16 位）。 */
        fun isValidDeviceId(value: String): Boolean =
            value.length == 16 && value.all { it in '0'..'9' || it in 'a'..'f' }

        /**
         * 版本短标签（契约 §5）：去空白、去 `v`/`V` 前缀、截断 32。
         *
         * 例：`" v1.0.0-beta14 "` → `1.0.0-beta14`。
         */
        fun normalizeVersion(raw: String?): String = raw.orEmpty()
            .filterNot { it.isWhitespace() }
            .removePrefix("v")
            .removePrefix("V")
            .take(MAX_VERSION_LENGTH)

        /**
         * 设备短标签（契约 §5）：去首尾空白、连续空白合并为一个空格、截断 48。
         *
         * 例：`" Xiaomi   14 ·\tAndroid 15 "` → `Xiaomi 14 · Android 15`。
         */
        fun normalizeDeviceLabel(raw: String?): String = raw.orEmpty()
            .trim()
            .replace(WHITESPACE, " ")
            .take(MAX_DEVICE_LENGTH)
    }
}

/** provider 抛异常时退回空串（新字段失败不阻断上报）。 */
private fun safeProvider(provider: () -> String): String =
    runCatching { provider() }.getOrDefault("")

/**
 * 安卓设备短标签（契约 §5 安卓行）：`<品牌> <机型> · Android <版本>`，截断 48。
 *
 * - 只读 `Build.MANUFACTURER/MODEL/VERSION.RELEASE`，**不含**任何账号/设备标识；
 * - 型号已以品牌开头时不再重复品牌（如 `Xiaomi` + `Xiaomi 14` → `Xiaomi 14`）；
 * - 参数可注入：纯 JVM 单测不触碰 `android.os.Build`（默认值才读 Build）。
 */
fun androidDeviceLabel(
    manufacturer: String? = runCatching { android.os.Build.MANUFACTURER }.getOrNull(),
    model: String? = runCatching { android.os.Build.MODEL }.getOrNull(),
    release: String? = runCatching { android.os.Build.VERSION.RELEASE }.getOrNull(),
): String {
    fun clean(value: String?): String =
        value?.trim().orEmpty().takeUnless { it.equals("unknown", ignoreCase = true) }.orEmpty()

    val brand = clean(manufacturer)
    val modelName = clean(model)
    val device = when {
        modelName.isEmpty() -> brand
        brand.isEmpty() -> modelName
        modelName.startsWith(brand, ignoreCase = true) -> modelName
        else -> "$brand $modelName"
    }
    val system = clean(release).let { version -> if (version.isEmpty()) "Android" else "Android $version" }
    return VisitReporter.normalizeDeviceLabel(
        listOf(device, system).filter { it.isNotBlank() }.joinToString(" · "),
    )
}

/**
 * 本机访问去重设备码（did）。
 *
 * - 随机 16 位小写十六进制，首次生成后持久化到**私有** SharedPreferences；
 * - 不读取 ANDROID_ID / IMEI / 序列号等任何设备标识，不含个人信息，仅用于访问去重；
 * - 存储异常时退回进程内随机值（仍满足格式），不阻断上报；下次正常时再持久化。
 *
 * 存储抽象成 [Store]，纯 JVM 单测可用内存实现覆盖「持久化 + 修复非法值」。
 */
class VisitDeviceId(
    private val store: Store,
    private val randomHex: () -> String = { randomHex16() },
) {
    interface Store {
        fun read(): String?
        fun write(value: String)
    }

    @Volatile
    private var cached: String? = null

    fun get(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val existing = runCatching { store.read() }.getOrNull()?.trim()?.lowercase().orEmpty()
            val value = if (isValidDeviceId(existing)) existing else randomValid()
            if (value != existing) runCatching { store.write(value) }
            cached = value
            return value
        }
    }

    private fun randomValid(): String {
        repeat(GENERATE_ATTEMPTS) {
            val candidate = runCatching { randomHex().trim().lowercase() }.getOrDefault("")
            if (isValidDeviceId(candidate)) return candidate
        }
        // 理论不可达（随机源连续异常/非法）：保底合法值，上报仍可用。
        return FALLBACK_DID
    }

    companion object {
        /** SharedPreferences 私有文件名（设备级，不参与云端 userdata 同步）。 */
        const val PREFS_NAME: String = "class_tools_visits"

        /** 持久化键。 */
        const val KEY_DID: String = "did"

        private const val GENERATE_ATTEMPTS: Int = 8
        private const val FALLBACK_DID: String = "0000000000000000"
        private const val HEX: String = "0123456789abcdef"
        private val RANDOM: SecureRandom = SecureRandom()

        fun isValidDeviceId(value: String): Boolean =
            VisitReporter.isValidDeviceId(value)

        /** 16 位小写十六进制（8 随机字节 → 16 字符）。 */
        fun randomHex16(): String {
            val bytes = ByteArray(8)
            RANDOM.nextBytes(bytes)
            val out = StringBuilder(16)
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
            }
            return out.toString()
        }

        /** 生产装配：私有 SharedPreferences（`class_tools_visits` / `did`）。 */
        fun from(context: Context): VisitDeviceId = VisitDeviceId(
            store = SharedPreferencesStore(context.applicationContext),
        )
    }
}

/** SharedPreferences 实现：只存一个 did 字符串，无任何个人信息。 */
private class SharedPreferencesStore(context: Context) : VisitDeviceId.Store {
    private val prefs = context.getSharedPreferences(
        VisitDeviceId.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): String? = prefs.getString(VisitDeviceId.KEY_DID, null)

    override fun write(value: String) {
        prefs.edit().putString(VisitDeviceId.KEY_DID, value).apply()
    }
}
