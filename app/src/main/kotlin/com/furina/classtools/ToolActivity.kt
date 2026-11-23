package com.furina.classtools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.ClassToolsThemeHost
import com.furina.classtools.ui.components.CtToastHost
import com.furina.classtools.ui.motion.SharedViewMotion
import com.furina.classtools.ui.navigation.MainNavHost
import com.furina.classtools.ui.platform.enableClassToolsEdgeToEdge
import kotlinx.coroutines.flow.first

/**
 * 工具页独立宿主（只由金标 SharedView 的「工具卡 → 工具页」路径启动）。
 *
 * 为什么不是一个普通页面：SharedView Kit 的 API 是 ActivityOptions，只能跨 Activity
 * 做系统级一镜到底。这里让目标 Activity 首屏直接是 `tool/{id}`，返回时由
 * [SharedViewMotion.finishWithSharedView] 把落点 override 回 MainActivity 的原工具卡。
 *
 * 页面内容、数据容器、主题与 MainActivity 完全复用；Activity 不导出，Session 由显式
 * Intent 从已登录的 MainActivity 传入，不落盘、不写日志。
 */
class ToolActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableClassToolsEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launch = ToolLaunchArgs.fromIntent(intent)
        if (launch == null) {
            // 参数不完整/工具 id 非法：不在空白页停留，直接回主界面。
            finish()
            return
        }
        val container = (application as ClassToolsApp).container
        setContent {
            val session by produceState(
                initialValue = container.currentSession?.takeIf {
                    it.sid == launch.session.sid && it.isLikelyValid() && container.isSessionPrepared(it)
                },
            ) {
                if (value != null) return@produceState
                // 从进程重建恢复工具时，以本机保存的有效会话为准。
                val saved = (container.sessionStore.state.first() as? SessionState.SignedIn)?.session
                if (saved == null || !saved.isLikelyValid() || saved.sid != launch.session.sid) {
                    finish()
                } else {
                    container.prepareSession(saved)
                    value = saved
                }
            }
            ClassToolsThemeHost(container) {
                Box(Modifier.fillMaxSize()) {
                    session?.let { restored ->
                        MainNavHost(
                            container = container,
                            session = restored,
                            onSignOut = { finish() },
                            startToolId = launch.toolId,
                            startToolPick = launch.pick,
                            standaloneTool = true,
                            onCloseStandaloneTool = { finishWithSharedViewMotion() },
                        )
                    }
                    CtToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
                }
            }
        }
    }

    private fun finishWithSharedViewMotion() {
        if (isFinishing) return
        SharedViewMotion.finishWithSharedView(this)
    }

    companion object {
        /**
         * 显式 Intent：Session 只在本进程/本任务内传给不导出的工具宿主。
         * 不把 token 写日志、不落盘（MainActivity 的 DataStore 才是唯一持久化点）。
         */
        fun createIntent(context: Context, toolId: String, pick: Boolean, session: Session): Intent =
            Intent(context, ToolActivity::class.java).apply {
                putExtra(ToolExtras.TOOL_ID, toolId)
                putExtra(ToolExtras.TOOL_PICK, pick)
                putExtra(ToolExtras.SID, session.sid)
                putExtra(ToolExtras.NAME, session.name)
                putExtra(ToolExtras.TOKEN, session.token)
                putExtra(ToolExtras.ROLE, session.role)
                putExtra(ToolExtras.BADGE, session.badge)
                putExtra(ToolExtras.IS_ADMIN, session.isAdmin)
                putExtra(ToolExtras.DEBUG, session.debug)
                putExtra(ToolExtras.ISSUED_AT, session.issuedAt)
                putExtra(ToolExtras.EXPIRES_AT, session.expiresAt)
            }
    }
}

/** ToolActivity Intent 的 key 集中定义，避免发送/解析两处字符串漂移。 */
private object ToolExtras {
    const val TOOL_ID = "com.furina.classtools.extra.TOOL_ID"
    const val TOOL_PICK = "com.furina.classtools.extra.TOOL_PICK"
    const val SID = "com.furina.classtools.extra.SID"
    const val NAME = "com.furina.classtools.extra.NAME"
    const val TOKEN = "com.furina.classtools.extra.TOKEN"
    const val ROLE = "com.furina.classtools.extra.ROLE"
    const val BADGE = "com.furina.classtools.extra.BADGE"
    const val IS_ADMIN = "com.furina.classtools.extra.IS_ADMIN"
    const val DEBUG = "com.furina.classtools.extra.DEBUG"
    const val ISSUED_AT = "com.furina.classtools.extra.ISSUED_AT"
    const val EXPIRES_AT = "com.furina.classtools.extra.EXPIRES_AT"
}

/** 独立工具宿主的 Intent 参数解析（纯逻辑，单测覆盖非法 id/缺字段）。 */
internal data class ToolLaunchArgs(
    val toolId: String,
    val pick: Boolean,
    val session: Session,
) {
    companion object {
        fun fromIntent(intent: Intent?): ToolLaunchArgs? {
            if (intent == null) return null
            val toolId = intent.getStringExtra(ToolExtras.TOOL_ID).orEmpty()
            if (ToolCatalog.byId(toolId) == null) return null
            val sid = intent.getStringExtra(ToolExtras.SID).orEmpty()
            val token = intent.getStringExtra(ToolExtras.TOKEN).orEmpty()
            val debug = intent.getBooleanExtra(ToolExtras.DEBUG, false)
            // 与 SessionStore 的恢复判定一致：正式会话必须有 token；Debug 预览会话允许空 token。
            if (sid.isBlank() || (token.isBlank() && !debug)) return null
            return ToolLaunchArgs(
                toolId = toolId,
                pick = intent.getBooleanExtra(ToolExtras.TOOL_PICK, false),
                session = Session(
                    sid = sid,
                    name = intent.getStringExtra(ToolExtras.NAME).orEmpty(),
                    token = token,
                    role = intent.getStringExtra(ToolExtras.ROLE).orEmpty(),
                    badge = intent.getStringExtra(ToolExtras.BADGE).orEmpty(),
                    isAdmin = intent.getBooleanExtra(ToolExtras.IS_ADMIN, false),
                    debug = debug,
                    issuedAt = intent.getLongExtra(
                        ToolExtras.ISSUED_AT,
                        System.currentTimeMillis(),
                    ),
                    expiresAt = intent.getLongExtra(ToolExtras.EXPIRES_AT, 0L),
                ),
            )
        }
    }
}
