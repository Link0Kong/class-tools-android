package com.furina.classtools.data.water

import com.furina.classtools.data.network.*
import com.furina.classtools.features.water.*
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SharedWaterValue(val persons: Int, val reading: Double? = null)

@Serializable
data class WaterEdit(val key: String, val before: SharedWaterValue?, val target: SharedWaterValue?,
    val known: Boolean, val attempted: Boolean = false)

@Serializable
data class WaterLedgerState(
    val rows: Map<String, SharedWaterValue>? = null,
    val peopleKnown: Boolean = false,
    val people: SharedWaterValue? = null,
    val pending: Map<String, WaterEdit> = emptyMap(),
    val denied: Boolean = false,
) {
    fun value(key: String) = if (key == PEOPLE) people else rows?.get(key)
    fun known(key: String) = if (key == PEOPLE) peopleKnown else rows != null
    fun room() = if (denied) WaterRoomState() else WaterRoomState(people?.persons ?: 4,
        rows.orEmpty().toSortedMap().map { (m, v) -> WaterRecord(m, v.reading!!) })
    companion object { const val PEOPLE = "persons" }
}

/** All durable state belongs to one authenticated account and one selected room. */
class SharedWaterLedger(val room: String, private val api: ApiClient, private val isOwner: () -> Boolean,
    restore: () -> String?, private val persist: (String) -> Unit) {
    private val lock = Mutex()
    private val generation = AtomicLong()
    private val mutable = MutableStateFlow(restore()?.let {
        runCatching { NetJson.decodeFromString(WaterLedgerState.serializer(), it) }.getOrNull()
    } ?: WaterLedgerState())
    val state = mutable.asStateFlow()
    private val feedback = MutableStateFlow("正在核对共享账本")
    val status = feedback.asStateFlow()
    private val rv = URLEncoder.encode(room, "UTF-8")

    @Volatile private var active = true
    fun enter() { active = true }
    fun leave() { active = false; generation.incrementAndGet() }
    private fun valid(ticket: Long) = active && isOwner() && ticket == generation.get()
    private fun save(next: WaterLedgerState, ticket: Long) {
        if (!valid(ticket)) return
        persist(NetJson.encodeToString(WaterLedgerState.serializer(), next))
        mutable.value = next
    }

    suspend fun edit(key: String, target: SharedWaterValue?) {
        if (key != WaterLedgerState.PEOPLE && WaterCodec.normalizeMonth(key) != key) return
        if (key == WaterLedgerState.PEOPLE && (target == null || target.reading != null)) return
        if (target != null && (target.persons !in 1..6 || (key != WaterLedgerState.PEOPLE &&
                (target.reading == null || !target.reading.isFinite() || target.reading < 0)))) return
        val ticket = generation.get()
        lock.withLock {
            if (!valid(ticket) || mutable.value.denied) return
            val old = mutable.value
            save(old.copy(pending = old.pending + (key to WaterEdit(key, old.value(key), target, old.known(key)))), ticket)
        }
        refresh()
    }

    suspend fun discard(key: String) = lock.withLock {
        val old = mutable.value
        save(old.copy(pending = old.pending - key), generation.get())
    }

    private class Denied : Exception()
    private fun response(result: ApiResult<String>): String = when (result) {
        is ApiResult.Success -> result.value
        is ApiResult.Failure -> {
            val error = result.error
            if (error is ApiError.TokenInvalid || error is ApiError.Http && error.status in setOf(401, 403)) throw Denied()
            throw IllegalStateException(error.displayMessage)
        }
    }

    private suspend fun readRows(table: String): Map<String, SharedWaterValue> {
        val monthly = table == "water"
        val fields = if (monthly) "roomverify,month,reading,persons" else "roomverify,persons"
        val order = if (monthly) "&order=month.asc" else ""
        val values = linkedMapOf<String, SharedWaterValue>()
        while (true) {
            val raw = response(api.get("$table?select=$fields&roomverify=eq.$rv$order&limit=120&offset=" + values.size))
            val page = NetJson.parseToJsonElement(raw).jsonArray
            if (page.isEmpty()) return values
            for (row in page) {
                val obj = row.jsonObject
                check(obj["roomverify"]?.jsonPrimitive?.content == room)
                val persons = obj.getValue("persons").jsonPrimitive.int
                check(persons in 1..6)
                val key = if (monthly) obj.getValue("month").jsonPrimitive.content else WaterLedgerState.PEOPLE
                check(!values.containsKey(key) && (!monthly || WaterCodec.normalizeMonth(key) == key))
                val reading = if (monthly) obj.getValue("reading").jsonPrimitive.double else null
                check(reading == null || reading.isFinite() && reading >= 0)
                values[key] = SharedWaterValue(persons, reading?.let { kotlin.math.round(it * 100) / 100 })
            }
        }
    }

    private suspend fun readPart(table: String): Map<String, SharedWaterValue>? = try { readRows(table) }
        catch (e: CancellationException) { throw e } catch (e: Denied) { throw e } catch (_: Exception) { null }

    suspend fun refresh() {
        val ticket = generation.incrementAndGet()
        lock.withLock {
            if (!valid(ticket)) return
            try {
                val rows = readPart("water")
                val people = readPart("water_room")
                val auth = NetJson.parseToJsonElement(response(api.post("rpc/auth_ok", "{}", notifyTokenInvalid = false)))
                if (auth == JsonPrimitive(false)) throw Denied()
                check(auth == JsonPrimitive(true))
                if (!valid(ticket)) return
                var data = mutable.value.copy(rows = rows ?: mutable.value.rows,
                    people = if (people != null) people[WaterLedgerState.PEOPLE] else mutable.value.people,
                    peopleKnown = people != null || mutable.value.peopleKnown, denied = false)
                save(data, ticket)
                for (edit in data.pending.values.toList()) {
                    if (!valid(ticket)) return
                    if (if (edit.key == WaterLedgerState.PEOPLE) people == null else rows == null) continue
                    if (data.value(edit.key) != edit.target) {
                        if (!edit.known || edit.attempted || data.value(edit.key) != edit.before) continue
                        data = data.copy(pending = data.pending + (edit.key to edit.copy(attempted = true)))
                        save(data, ticket)
                        send(edit)
                        if (!valid(ticket)) return
                        val confirmed = readPart(if (edit.key == WaterLedgerState.PEOPLE) "water_room" else "water") ?: continue
                        val stillAuthorized = NetJson.parseToJsonElement(response(api.post("rpc/auth_ok", "{}", notifyTokenInvalid = false)))
                        if (stillAuthorized == JsonPrimitive(false)) throw Denied()
                        check(stillAuthorized == JsonPrimitive(true))
                        if (edit.key == WaterLedgerState.PEOPLE) data = data.copy(people = confirmed[edit.key], peopleKnown = true)
                        else data = data.copy(rows = confirmed)
                        save(data, ticket)
                        if (confirmed[edit.key] != edit.target) continue
                    }
                    data = data.copy(pending = data.pending - edit.key)
                    save(data, ticket)
                }
                if (valid(ticket)) feedback.value = when {
                    rows == null || people == null -> "部分共享数据未确认"
                    data.pending.isNotEmpty() -> "有待确认修改"
                    else -> "共享账本已核对"
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Denied) {
                save(mutable.value.copy(rows = null, peopleKnown = false, people = null, denied = true), ticket)
                if (valid(ticket)) feedback.value = "当前身份无权查看共享账本"
            } catch (_: Exception) {
                if (valid(ticket)) feedback.value = "本次未确认，显示上次共享值；已录修改保留待核对"
            }
        }
    }

    private suspend fun send(edit: WaterEdit) {
        val target = edit.target
        if (target == null) {
            response(api.delete("water?roomverify=eq.$rv&month=eq." + URLEncoder.encode(edit.key, "UTF-8")))
            return
        }
        val monthly = edit.key != WaterLedgerState.PEOPLE
        val path = if (monthly) "water?on_conflict=roomverify,month" else "water_room?on_conflict=roomverify"
        val body = buildJsonObject {
            put("roomverify", room)
            put("persons", target.persons)
            if (monthly) { put("month", edit.key); put("reading", target.reading!!) }
        }
        response(api.post(path, body.toString(), prefer = "resolution=merge-duplicates,return=minimal", notifyTokenInvalid = false))
    }
}
