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
    }
}
