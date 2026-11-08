package com.furina.classtools.data.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.furina.classtools.data.permission.PermissionResolver.combine

/** 应用需要的运行时权限分组（Manifest 声明由 Lead 维护）。 */
enum class PermissionType {
    /** 通知权限（Android 13+ POST_NOTIFICATIONS；低版本看系统通知开关）。 */
    NOTIFICATIONS,

    /** 写日程（READ_CALENDAR + WRITE_CALENDAR 同时授予）。 */
    CALENDAR,
    ;

    val manifestPermissions: List<String>
        get() = when (this) {
            NOTIFICATIONS -> listOf(Manifest.permission.POST_NOTIFICATIONS)
            CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        }

    val label: String
        get() = when (this) {
            NOTIFICATIONS -> "通知"
            CALENDAR -> "日历"
        }
}

/**
 * 权限状态（四态）：
 * - [GRANTED]：已授予；
 * - [REQUESTABLE]：从未请求过，可直接弹系统授权框；
 * - [DENIED]：拒绝过，但系统仍允许再次请求（应先展示说明理由）；
 * - [PERMANENTLY_DENIED]：拒绝且不再弹窗（只能去系统设置开启）。
 */
enum class PermissionState {
    GRANTED,
    REQUESTABLE,
    DENIED,
    PERMANENTLY_DENIED,
    ;

    val isGranted: Boolean get() = this == GRANTED

    /** 是否还能通过系统授权框请求（首次 / 拒绝过但未永久拒绝）。 */
    val canRequest: Boolean get() = this == REQUESTABLE || this == DENIED

    /** 是否必须跳系统设置页。 */
    val needsSettings: Boolean get() = this == PERMANENTLY_DENIED
}

/** 权限快照（页面一次性读取，避免每个组合多次检查）。 */
data class PermissionSnapshot(
    val notifications: PermissionState = PermissionState.REQUESTABLE,
    val calendar: PermissionState = PermissionState.REQUESTABLE,
) {
    fun stateOf(type: PermissionType): PermissionState = when (type) {
        PermissionType.NOTIFICATIONS -> notifications
        PermissionType.CALENDAR -> calendar
    }

    val notificationsReady: Boolean get() = notifications.isGranted
    val calendarReady: Boolean get() = calendar.isGranted
}

/**
 * 纯函数权限判定（无 Android 依赖，可单测）。
 *
 * Android 的 `shouldShowRequestPermissionRationale` 在“从未请求”和“永久拒绝”时都返回 false，
 * 因此必须结合“是否请求过”的状态位才能区分 [PermissionState.REQUESTABLE] 与
 * [PermissionState.PERMANENTLY_DENIED]。
 */
object PermissionResolver {

    fun resolve(
        granted: Boolean,
        requestedBefore: Boolean,
        showRationale: Boolean,
    ): PermissionState = when {
        granted -> PermissionState.GRANTED
        !requestedBefore -> PermissionState.REQUESTABLE
        showRationale -> PermissionState.DENIED
        else -> PermissionState.PERMANENTLY_DENIED
    }

    /**
     * 多权限合并（如日历需要 READ + WRITE 同时授予）：
     * 全部授予 → GRANTED；任一永久拒绝 → PERMANENTLY_DENIED；任一仍需理由 → DENIED；否则 REQUESTABLE。
     */
    fun combine(states: List<PermissionState>): PermissionState {
        if (states.isEmpty()) return PermissionState.REQUESTABLE
        if (states.all { it == PermissionState.GRANTED }) return PermissionState.GRANTED
        if (states.any { it == PermissionState.PERMANENTLY_DENIED }) return PermissionState.PERMANENTLY_DENIED
        if (states.any { it == PermissionState.DENIED }) return PermissionState.DENIED
        return PermissionState.REQUESTABLE
    }
}

/** 单权限授予检查（可注入测试）。 */
fun interface PermissionGrantChecker {
    fun isGranted(permission: String): Boolean
}

/** 是否应展示系统“请求理由”（可注入测试）。 */
fun interface PermissionRationaleChecker {
    fun shouldShowRationale(permission: String): Boolean
}

/** 是否请求过（按权限类型持久化；用于区分首次请求与永久拒绝）。 */
interface PermissionRequestStore {
    fun wasRequested(type: PermissionType): Boolean

    fun markRequested(type: PermissionType)
}

/** SharedPreferences 实现（本机偏好，不进云端 userdata）。 */
class SharedPrefsPermissionRequestStore(context: Context) : PermissionRequestStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun wasRequested(type: PermissionType): Boolean =
        prefs.getBoolean(keyOf(type), false)

    override fun markRequested(type: PermissionType) {
        prefs.edit().putBoolean(keyOf(type), true).apply()
    }

    private fun keyOf(type: PermissionType): String = "requested_${type.name.lowercase()}"

    private companion object {
        const val PREFS_NAME = "ct_permission"
    }
}

/**
 * 权限控制器：封装运行时权限检查 + 请求理由 + 跳系统设置。
 *
 * 使用约定（Lead 接线）：
 * - UI 用 `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`；
 *   请求前调用 [markRequested]，请求后重新读取 [state]；
 * - [permissionsToRequest] 给出应请求的 Manifest 权限（已授予的自动过滤）；
 * - 无系统弹窗可用（永久拒绝）时引导用户点 [settingsIntent]。
 *
 * 注意：构造时应使用 **Activity Context**（`shouldShowRequestPermissionRationale` 需要 Activity）；
 * 用非 Activity Context 构造时理由检查恒为 false，仅适合后台 Worker/测试。
 */
interface PermissionController {
    fun state(type: PermissionType): PermissionState

    fun snapshot(): PermissionSnapshot

    /** 请求系统权限前调用（记录“已请求过”，用于永久拒绝判定）。 */
    fun markRequested(type: PermissionType)

    /** 仍需弹系统框的 Manifest 权限（已授予的不含）。 */
    fun permissionsToRequest(type: PermissionType): Array<String>

    /** 系统设置中的本应用详情页（永久拒绝后引导）。 */
    fun settingsIntent(): Intent

    /** 系统通知开关是否开启（低版本无 POST_NOTIFICATIONS 时用于判断通知可用性）。 */
    fun notificationsEnabled(): Boolean
}

class AndroidPermissionController(
    context: Context,
    private val store: PermissionRequestStore = SharedPrefsPermissionRequestStore(context),
    private val grantChecker: PermissionGrantChecker = PermissionGrantChecker { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    private val rationaleChecker: PermissionRationaleChecker = PermissionRationaleChecker { permission ->
        (context as? android.app.Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        } ?: false
    },
    private val apiLevel: Int = Build.VERSION.SDK_INT,
) : PermissionController {

    private val appContext: Context = context.applicationContext

    override fun state(type: PermissionType): PermissionState {
        if (type == PermissionType.NOTIFICATIONS && apiLevel < Build.VERSION_CODES.TIRAMISU) {
            // Android 12 及以下通知无运行时权限：只能反映系统通知总开关。
            return if (notificationsEnabled()) PermissionState.GRANTED else PermissionState.PERMANENTLY_DENIED
        }
        val requestedBefore = store.wasRequested(type)
        val states = type.manifestPermissions.map { permission ->
            PermissionResolver.resolve(
                granted = grantChecker.isGranted(permission),
                requestedBefore = requestedBefore,
                showRationale = rationaleChecker.shouldShowRationale(permission),
            )
        }
        return combine(states)
    }

    override fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        notifications = state(PermissionType.NOTIFICATIONS),
        calendar = state(PermissionType.CALENDAR),
    )

    override fun markRequested(type: PermissionType) {
        store.markRequested(type)
    }

    override fun permissionsToRequest(type: PermissionType): Array<String> =
        type.manifestPermissions.filterNot { grantChecker.isGranted(it) }.toTypedArray()

    override fun settingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", appContext.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
}
