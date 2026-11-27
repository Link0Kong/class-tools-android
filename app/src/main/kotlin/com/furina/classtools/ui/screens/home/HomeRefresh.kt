package com.furina.classtools.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tasks.TaskRepository
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TtFailureKind
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

internal data class HomeLoadState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal data class HomeLoadResult<T>(
    val data: T? = null,
    val error: String? = null,
    val keepPrevious: Boolean = error != null,
)

/** One request at a time per module. A forced request can replace a cache-first request. */
internal class HomeLoad<T>(
    initial: T? = null,
    private val cached: suspend () -> T? = { null },
    private val load: suspend (force: Boolean) -> HomeLoadResult<T>,
) {
    var state by mutableStateOf(HomeLoadState(initial, loading = true))
        private set

    private var inFlight: Job? = null
    private var inFlightForced = false

    fun request(scope: CoroutineScope, force: Boolean, timeoutMillis: Long): Job {
        val running = inFlight?.takeIf { it.isActive }
        if (running != null && (!force || inFlightForced)) return running
        running?.cancel()
        state = state.copy(loading = true, error = null)
        inFlightForced = force

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome: HomeLoadResult<T> = withTimeoutOrNull(timeoutMillis) {
                    if (state.data == null) {
                        val value = cached()
                        ensureActive()
                        if (value != null) state = state.copy(data = value)
                    }
                    load(force)
                } ?: HomeLoadResult(error = "更新超时，请重试")
                ensureActive()
                val value = outcome.data ?: state.data.takeIf { outcome.keepPrevious }
                state = HomeLoadState(data = value, error = outcome.error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                state = state.copy(error = "更新失败，请重试")
            } finally {
                if (inFlight == coroutineContext.job) {
                    state = state.copy(loading = false)
                    inFlight = null
                }
            }
        }
        inFlight = job
        job.start()
        return job
    }

    fun cancel() { inFlight?.cancel() }
}

/** Account-scoped loader. The pull indicator tracks the whole requested batch. */
internal class HomeRefresh(parent: CoroutineScope, private val timeoutMillis: Long = 45_000L) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    var isRefreshing by mutableStateOf(false)
        private set

    fun prefetch(vararg modules: HomeLoad<*>?) { request(false, modules) }

    fun retry(vararg modules: HomeLoad<*>?) { request(true, modules) }

    fun refresh(vararg modules: HomeLoad<*>?) {
        if (isRefreshing || modules.all { it == null }) return
        isRefreshing = true
        val requests = request(true, modules)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                requests.joinAll()
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun request(force: Boolean, modules: Array<out HomeLoad<*>?>): List<Job> =
        modules.filterNotNull().map { it.request(scope, force, timeoutMillis) }

    fun dispose() { job.cancel() }
}

internal class HomeDataLoads(
    private val controller: HomeRefresh,
    val timetable: HomeLoad<TtSnapshot>?,
    val tasks: HomeLoad<Unit>?,
    val homework: HomeLoad<List<HomeworkRow>>?,
    val notices: HomeLoad<NoticesData>?,
    val marks: HomeLoad<Unit>?,
    val power: HomeLoad<PowerRow>?,
) {
    val isRefreshing: Boolean get() = controller.isRefreshing

    fun refresh() = controller.refresh(timetable, tasks, homework, notices, marks, power)

    fun retry(vararg modules: HomeLoad<*>?) = controller.retry(*modules)

    fun retryNotices() = controller.retry(notices, marks)
}

@Composable
internal fun rememberHomeDataLoads(
    session: Session,
    active: Boolean,
    timetableRepository: TimetableRepository?,
    taskRepository: TaskRepository?,
    homeworkRepository: HomeworkRepository?,
    noticesRepository: NoticesRepository?,
    marksRepository: MarksRepository?,
    powerRepository: PowerRepository?,
    personalTimetable: JsonElement?,
    room: String,
    initialNotices: NoticesData?,
): HomeDataLoads {
    val scope = rememberCoroutineScope()
    val controller = remember(session, scope) { HomeRefresh(scope) }
    DisposableEffect(controller) { onDispose(controller::dispose) }
    val timetable = remember(controller, timetableRepository, personalTimetable) { timetableRepository?.homeLoad() }
    val tasks = remember(controller, taskRepository) { taskRepository?.takeUnless { session.debug }?.homeLoad() }
    val homework = remember(controller, homeworkRepository) { homeworkRepository?.homeLoad() }
    val notices = remember(controller, noticesRepository, initialNotices) { noticesRepository?.homeLoad(initialNotices) }
    val marks = remember(controller, marksRepository) { marksRepository?.homeLoad() }
    val power = remember(controller, powerRepository, room) {
        powerRepository?.takeIf { room.isNotBlank() }?.homeLoad(room)
    }
    DisposableEffect(timetable) { onDispose { timetable?.cancel() } }
    DisposableEffect(power) { onDispose { power?.cancel() } }
    LaunchedEffect(active, controller, tasks, homework, notices, marks) {
        if (active) controller.prefetch(tasks, homework, notices, marks)
    }
    LaunchedEffect(active, controller, timetable) {
        if (active) controller.prefetch(timetable)
    }
    LaunchedEffect(active, controller, power) {
        if (active) controller.prefetch(power)
    }
    return remember(controller, timetable, tasks, homework, notices, marks, power) {
        HomeDataLoads(controller, timetable, tasks, homework, notices, marks, power)
    }
}

private fun TimetableRepository.homeLoad() = HomeLoad(cached = { cached() }) { force ->
    when (val result = load(force)) {
        is TtLoadResult.Success -> HomeLoadResult(result.snapshot, result.refreshError)
        is TtLoadResult.Failure -> if (result.kind == TtFailureKind.EMPTY) HomeLoadResult()
            else HomeLoadResult(error = result.message)
    }
}

private fun TaskRepository.homeLoad() = HomeLoad(initial = Unit) {
    if (syncSnapshot() != null) HomeLoadResult(Unit)
    else HomeLoadResult(Unit, error = syncStatus.value)
}

private fun HomeworkRepository.homeLoad() = HomeLoad(cached = { cachedSnapshot() }) { force ->
    when (val result = load(force)) {
        is HomeworkLoadResult.Success -> HomeLoadResult(result.rows, result.refreshError)
        is HomeworkLoadResult.Failure -> HomeLoadResult(error = result.message)
    }
}

private fun NoticesRepository.homeLoad(initial: NoticesData?) = HomeLoad(initial, cached = { cached() }) { force ->
    when (val result = load(force)) {
        is NoticesLoadResult.Success -> HomeLoadResult(result.data, result.data.refreshError)
        // The repository drops its cache if the newly read index removes or hides an item.
        is NoticesLoadResult.Failure -> HomeLoadResult(error = result.message, keepPrevious = false)
    }
}

private fun MarksRepository.homeLoad() = HomeLoad<Unit> { force ->
    when (val result = refresh(force)) {
        is ApiResult.Success -> HomeLoadResult(Unit)
        is ApiResult.Failure -> HomeLoadResult(error = result.error.displayMessage)
    }
}

private fun PowerRepository.homeLoad(room: String) = HomeLoad(cached = { cachedRow(room) }) { force ->
    when (val result = loadRow(room, force)) {
        is PowerRowResult.Success -> HomeLoadResult(result.row, result.refreshError)
        is PowerRowResult.Failure -> HomeLoadResult(error = result.message)
    }
}
