package com.furina.classtools

import android.app.Application

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
    }
}
