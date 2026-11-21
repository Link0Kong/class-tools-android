package com.furina.classtools

import android.app.Application
import com.furina.classtools.data.push.PushStatusDiag
import kotlinx.serialization.json.JsonObject

class ClassToolsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 诊断 v2：全局未捕获异常 → 脱敏后落本机队列，启动/在线后补报 client_diag（契约 §2）。
        container.installGlobalCrashHandler()
        // OPPO PUSH（官方 SDK V4.0.0）：创建「班级通知」渠道 + 按配置 init/register。
        // 未配置 AppKey/AppSecret 时内部安全跳过（不 init、不注册、不联网），不影响启动。
        runCatching { container.oppoPushManager.onAppCreated() }
        // 推送状态自检（2026-11-21）：登录后把 OPPO/vivo 注册状态 + 错误码上报一条 client_diag
        // （check=push_status，不含 token/学号），用于排查「ColorOS 真机收不到推送」时无需用户截图/连 adb。
        runCatching {
            PushStatusDiag.scheduleReport(
                scope = PushStatusDiag.appScope,
                context = this,
                session = { container.currentSession },
                reporter = container.clientDiagReporter,
                device = {
                    runCatching { container.deviceProbe.collectBase().deviceJson() }
                        .getOrDefault(JsonObject(emptyMap()))
                },
            )
        }
    }
}
