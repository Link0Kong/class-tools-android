package com.furina.classtools.data.push

import android.content.Context
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.diag.ClientDiagReporter
import com.furina.classtools.data.diag.DiagPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 推送状态诊断（本机读取 + 可选上报）。
 *
 * 背景（2026-11-21）：ColorOS 真机（OnePlus PYB110 / Android 17）登录后云端 `push_tokens`
 * 仍没有 `provider=oppo` 行，说明**客户端注册或上传**这一步失败，但用户看不到错误码、
 * 我们也没有手机日志。本对象做两件事：
 *
 * 1. 从 OPPO/vivo 的本机状态存储（`oppo_push` / `vivo_push` SharedPreferences）读**上次注册状态 + 错误码**，
 *    在「通知设置」页展示（用户能直接看到「注册失败(33)」这类信息）；
 * 2. 启动后（已登录、非 debug 会话）向 `client_diag` 上报一条 `check=push_status` 的诊断行，
 *    服务端可按 sid/机型查询，不必依赖用户截图或 adb。
 *
 * 隐私：只上报**状态枚举 + 错误码**，不含 RegistrationID、Token、学号、姓名。
 */
data class PushProviderStatus(
    val provider: String,
    /** NOT_CONFIGURED / UNSUPPORTED / REGISTERING / REGISTERED / FAILED / UNKNOWN。 */
    val state: String,
    val errorCode: Int = 0,
    /** RegistrationID 末尾 4 位（用于人工核对，不泄露完整 token）。 */
    val registrationTail: String = "",
) {
    val isRegistered: Boolean get() = state == "REGISTERED"

    /** 设置页展示文案。 */
    val label: String
        get() = when (state) {
            "NOT_CONFIGURED" -> "未配置密钥"
            "UNSUPPORTED" -> "本机不支持（非 ColorOS / 无推送服务）"
            "REGISTERING" -> "注册中…"
            "REGISTERED" -> "已注册" + if (registrationTail.isNotBlank()) "（…$registrationTail）" else ""
            "FAILED" -> "注册失败" + if (errorCode != 0) "（错误码 $errorCode）" else ""
            else -> "未知"
        }

    /** 一行诊断摘要：`oppo=FAILED:33`。 */
    val diagToken: String get() = "$provider=$state:$errorCode"
}

/** 一次推送状态快照（OPPO / vivo 两家；未接的厂商也在，状态为 NOT_CONFIGURED）。 */
data class PushStatusSnapshot(
    val oppo: PushProviderStatus,
    val vivo: PushProviderStatus,
) {
    /** 是否有任一厂商「看起来已注册」——用于设置页标题提示。 */
    val anyRegistered: Boolean get() = oppo.isRegistered || vivo.isRegistered

    /** 诊断 message：`推送状态 oppo=REGISTERED:0 vivo=NOT_CONFIGURED:0`。 */
    fun diagMessage(): String = "推送状态 ${oppo.diagToken} ${vivo.diagToken}"

    /** 诊断 detail：只放白名单键（check/code）。 */
    fun diagCode(): String = "${oppo.state}/${oppo.errorCode} ${vivo.state}/${vivo.errorCode}"
}

/** 推送状态读取 / 上报（纯逻辑 + 一个启动协程入口）。 */
object PushStatusDiag {

    /** 从本机存储读取状态（同步；只读 SharedPreferences，无网络）。 */
    fun snapshot(context: Context): PushStatusSnapshot = PushStatusSnapshot(
        oppo = readStatus(context, provider = "oppo"),
        vivo = readStatus(context, provider = "vivo"),
    )

    private fun readStatus(context: Context, provider: String): PushProviderStatus {
        val prefs = context.applicationContext.getSharedPreferences(
            if (provider == "oppo") "oppo_push" else "vivo_push",
            Context.MODE_PRIVATE,
        )
        val state = prefs.getString("state", null)?.trim()?.takeIf { it.isNotEmpty() } ?: "UNKNOWN"
        val errorCode = runCatching { prefs.getInt("error_code", 0) }.getOrDefault(0)
        val tail = prefs.getString("registration_id", null)
            ?.trim()
            ?.takeIf { it.length >= 4 }
            ?.takeLast(4)
            .orEmpty()
        return PushProviderStatus(provider, state, errorCode, tail)
    }

    /** 构造一条 `client_diag` 行（kind=manual、check=push_status）；无会话时返回 null。 */
    fun buildDiagRow(
        snapshot: PushStatusSnapshot,
        sid: String,
        appVersion: String,
        device: JsonObject,
        nowMillis: Long,
    ): JsonObject? {
        val session = sid.trim()
        if (session.isEmpty()) return null
        return DiagPayload.manualRow(
            sid = session,
            appVersion = appVersion,
            message = snapshot.diagMessage(),
            detail = mapOf("check" to "push_status", "code" to snapshot.diagCode()),
            device = device,
            now = nowMillis,
        )
    }

    /**
     * 启动后上报一次（等登录完成；最多等 2 分钟）。
     *
     * 只在**非 debug 会话**上报（测试身份不污染统计）；失败静默（诊断链路本身不阻塞业务）。
     */
    fun scheduleReport(
        scope: CoroutineScope,
        context: Context,
        session: () -> com.furina.classtools.data.session.Session?,
        reporter: ClientDiagReporter,
        device: () -> JsonObject,
        now: () -> Long = System::currentTimeMillis,
        attempts: Int = 12,
        intervalMillis: Long = 10_000L,
    ) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Default) {
            repeat(attempts.coerceAtLeast(1)) {
                val current = session()
                val sid = current?.sid?.trim().orEmpty()
                if (current != null && !current.debug && sid.isNotEmpty()) {
                    val row = runCatching {
                        buildDiagRow(
                            snapshot = snapshot(appContext),
                            sid = sid,
                            appVersion = BuildConfig.VERSION_NAME,
                            device = device(),
                            nowMillis = now(),
                        )
                    }.getOrNull()
                    if (row != null) {
                        runCatching { reporter.report(row) }
                    }
                    return@launch
                }
                delay(intervalMillis)
            }
        }
    }

    /** 供 Application 使用的默认协程作用域（进程级，不随页面销毁）。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 设置页展示用的 `服务商=文案` 列表。 */
    fun summaryLines(snapshot: PushStatusSnapshot): List<String> = listOf(
        "OPPO 推送：${snapshot.oppo.label}",
        "vivo 推送：${snapshot.vivo.label}",
    )

    /** 诊断 code 里的 state 是否属于已知枚举（防脏数据）。 */
    fun isKnownState(state: String): Boolean = state in setOf(
        "NOT_CONFIGURED", "UNSUPPORTED", "REGISTERING", "REGISTERED", "FAILED", "UNKNOWN",
    )

    @Suppress("unused")
    private val keepJsonPrimitive = JsonPrimitive("")
}
