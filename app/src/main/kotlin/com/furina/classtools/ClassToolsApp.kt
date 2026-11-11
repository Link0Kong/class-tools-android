package com.furina.classtools

import android.app.Application
import com.furina.classtools.data.reminder.ReminderAlarmRuntime
import com.furina.classtools.data.reminder.ReminderChannelInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassToolsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 诊断 v2：全局未捕获异常 → 脱敏后落本机队列，启动/在线后补报 client_diag（契约 §2）。
        container.installGlobalCrashHandler()
        // 通知可靠性 v3.7（用户反馈第 12 条）：
        // 1) 启动即提前创建全部通知渠道（避免首个通知因渠道缺失不显示）；
        // 2) 重排单条提醒的 AlarmManager 精确闹钟（闹钟不跨重启/强制停止；WorkManager 任务自身持久化）。
        runCatching { ReminderChannelInstaller.ensureCreated(this) }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ReminderAlarmRuntime.rearmPending(applicationContext) }
        }
    }
}
