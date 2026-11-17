package com.furina.classtools.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.AppContainer
import com.furina.classtools.data.account.OppoSessionLoginResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.components.update.UpdateHost
import com.furina.classtools.ui.navigation.MainNavHost
import com.furina.classtools.ui.screens.identity.IdentityScreen
import com.furina.classtools.ui.theme.findActivity
import kotlinx.coroutines.launch

/**
 * App 根：根据本地会话状态在「启动页 / 身份门禁 / 主界面」之间切换。
 * 身份门禁是唯一入口（对齐站点红线：先学号+姓名，后加载班级数据）。
 *
 * @param pendingToolId 通知点击带入的工具 id（hw/tt；登录后跳转一次）
 * @param pendingNoticeId OPPO PUSH 通知点击带入的通知 id（登录后进通知页并定位）
 */
@Composable
fun ClassToolsRoot(
    container: AppContainer,
    pendingToolId: String? = null,
    onPendingToolConsumed: () -> Unit = {},
    pendingNoticeId: String? = null,
    onPendingNoticeConsumed: () -> Unit = {},
) {
    val sessionState by container.sessionStore.state.collectAsStateWithLifecycle(
        initialValue = SessionState.Loading,
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // OPPO 账号快捷登录状态（身份页；成功后的会话流程与学号+姓名核验完全一致）。
    var oppoLoginInProgress by remember { mutableStateOf(false) }
    var oppoLoginError by remember { mutableStateOf<String?>(null) }
    val activity = remember(context) { context.findActivity() }

    fun completeLogin(session: Session) {
        container.updateActiveSession(session)
        scope.launch {
            container.sessionStore.save(session)
            runCatching { container.classMembersRepository.load(force = true) }
        }
    }

    // 把会话同步给数据层（tokenProvider / currentSid 读取），并做 29 天本地过期判定。
    LaunchedEffect(sessionState) {
        when (val state = sessionState) {
            is SessionState.SignedIn -> {
                if (state.session.isLikelyValid()) {
                    container.updateActiveSession(state.session)
                    // 先把本机持久化的 userdata section 恢复给 UI（离线/同步关闭时也能看到上次偏好），
                    // 再决定是否开启云同步（Debug 预览会话没有真实 token，关闭推送避免 401 清会话）。
                    runCatching { container.userDataRepository.hydrateFromLocal() }
                    container.userDataRepository.setSyncEnabled(!state.session.debug)
                    container.classMembersRepository.load(force = false)
                    container.warmReminderCaches()
                } else {
                    container.updateActiveSession(null)
                    container.sessionStore.clear()
                }
            }

            SessionState.SignedOut -> {
                container.updateActiveSession(null)
            }
            SessionState.Loading -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = sessionState) {
            SessionState.Loading -> BootSplash()

            SessionState.SignedOut -> IdentityScreen(
                authRepository = container.authRepository,
                onVerified = { session -> completeLogin(session) },
                // OPPO 账号快捷登录：仅已配置账号服务 + OPPO 系设备时展示（未配置则按钮不出现）。
                oppoQuickLoginAvailable = container.oppoAccountManager.quickLoginAvailable,
                oppoLoginInProgress = oppoLoginInProgress,
                oppoLoginError = oppoLoginError,
                onOppoQuickLogin = {
                    val host = activity
                    if (host == null) {
                        oppoLoginError = "当前环境不支持 OPPO 快捷登录"
                    } else if (!oppoLoginInProgress) {
                        oppoLoginError = null
                        oppoLoginInProgress = true
                        scope.launch {
                            val result = runCatching { container.oppoAccountManager.login(host) }
                                .getOrElse {
                                    OppoSessionLoginResult.Failure("unexpected", "OPPO 登录失败，请重试")
                                }
                            oppoLoginInProgress = false
                            when (result) {
                                // 与学号+姓名成功一致：更新 active session → 落盘 → 预热名单。
                                is OppoSessionLoginResult.Success -> completeLogin(result.session)
                                is OppoSessionLoginResult.Unbound -> oppoLoginError = result.message
                                is OppoSessionLoginResult.Failure -> oppoLoginError = result.message
                            }
                        }
                    }
                },
                onDismissOppoError = { oppoLoginError = null },
            )

            is SessionState.SignedIn -> {
                MainNavHost(
                    container = container,
                    session = state.session,
                    initialToolId = pendingToolId,
                    onInitialToolConsumed = onPendingToolConsumed,
                    initialNoticeId = pendingNoticeId,
                    onInitialNoticeConsumed = onPendingNoticeConsumed,
                    onSignOut = {
                        scope.launch {
                            container.authRepository.logout()
                            container.signOut()
                            // M-02：退出登录给一个明确结果提示。
                            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        // App 内更新：宿主放根级 —— 冷启动即自动检查并直接弹窗（登录后首页可见），
        // 不依赖用户去「我的 → 检查更新」；身份门禁/加载页也能看到（未登录也检查）。
        UpdateHost()
    }
}

@Composable
private fun BootSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandMark(size = 72.dp)
            Text(
                text = "班级工具站",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(22.dp),
                strokeWidth = 2.5.dp,
            )
        }
    }
}
