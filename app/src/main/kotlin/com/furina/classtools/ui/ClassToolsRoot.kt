package com.furina.classtools.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import com.furina.classtools.ui.components.CtContainerTransformHost
import com.furina.classtools.ui.components.CtToastHost
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import com.furina.classtools.ui.components.CtToastBus
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
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val sessionState by container.sessionStore.state.collectAsStateWithLifecycle(
        initialValue = SessionState.Loading,
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var preparedSession by remember(container) { mutableStateOf<Session?>(null) }

    // OPPO 账号快捷登录状态（身份页；成功后的会话流程与学号+姓名核验完全一致）。
    var oppoLoginInProgress by remember { mutableStateOf(false) }
    var oppoLoginError by remember { mutableStateOf<String?>(null) }
    // 荣耀账号快捷登录（SDK 是 startActivityForResult 模式，需要 ActivityResultLauncher）。
    var honorLoginInProgress by remember { mutableStateOf(false) }
    var honorLoginError by remember { mutableStateOf<String?>(null) }
    val activity = remember(context) { context.findActivity() }

    fun completeLogin(session: Session) {
        scope.launch { container.sessionStore.save(session) }
    }

    val honorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (honorLoginInProgress) {
            scope.launch {
                val loginResult = runCatching {
                    container.honorAccountManager.login(result.resultCode, result.data)
                }.getOrElse {
                    OppoSessionLoginResult.Failure("unexpected", "荣耀登录失败，请重试")
                }
                honorLoginInProgress = false
                when (loginResult) {
                    is OppoSessionLoginResult.Success -> completeLogin(loginResult.session)
                    is OppoSessionLoginResult.Unbound -> honorLoginError = loginResult.message
                    is OppoSessionLoginResult.Failure -> honorLoginError = loginResult.message
                }
            }
        }
    }

    LaunchedEffect(sessionState) {
        when (val state = sessionState) {
            is SessionState.SignedIn -> {
                if (state.session.isLikelyValid()) {
                    container.prepareSession(state.session)
                    preparedSession = state.session
                } else {
                    container.updateActiveSession(null)
                    container.sessionStore.clear()
                }
            }

            SessionState.SignedOut -> {
                preparedSession = null
                container.updateActiveSession(null)
            }
            SessionState.Loading -> Unit
        }
    }

    val announcement by container.appSettings.remoteAnnouncement.collectAsStateWithLifecycle(null)
    val announcementVersion by container.appSettings.remoteAnnouncementVersion.collectAsStateWithLifecycle(0)
    val dismissedAnnouncementVersion by container.appSettings.dismissedAnnouncementVersion.collectAsStateWithLifecycle(0)
    var announcementClosedVersion by remember { mutableStateOf(0) }
    val signedIn = sessionState is SessionState.SignedIn

    Box(modifier = Modifier.fillMaxSize()) {
        CtContainerTransformHost {
        when (val state = sessionState) {
            SessionState.Loading -> BootSplash(pendingRoute)

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
                                is OppoSessionLoginResult.Success -> completeLogin(result.session)
                                is OppoSessionLoginResult.Unbound -> oppoLoginError = result.message
                                is OppoSessionLoginResult.Failure -> oppoLoginError = result.message
                            }
                        }
                    }
                },
                onDismissOppoError = { oppoLoginError = null },
                // 荣耀账号快捷登录：仅已配置 + 荣耀设备时展示。
                honorQuickLoginAvailable = container.honorAccountManager.quickLoginAvailable,
                honorLoginInProgress = honorLoginInProgress,
                honorLoginError = honorLoginError,
                onHonorQuickLogin = {
                    val host = activity
                    if (host == null) {
                        honorLoginError = "当前环境不支持荣耀快捷登录"
                    } else if (!honorLoginInProgress) {
                        honorLoginError = null
                        val intent = container.honorAccountManager.signInIntent(host)
                        if (intent == null) {
                            honorLoginError = "无法拉起荣耀账号授权，请确认已登录荣耀账号后重试"
                        } else {
                            honorLoginInProgress = true
                            runCatching { honorLauncher.launch(intent) }.onFailure {
                                honorLoginInProgress = false
                                honorLoginError = "无法拉起荣耀账号授权，请重试"
                            }
                        }
                    }
                },
                onDismissHonorError = { honorLoginError = null },
            )

            is SessionState.SignedIn -> {
                if (!state.session.isLikelyValid() ||
                    (preparedSession != state.session && !container.isSessionPrepared(state.session))
                ) {
                    BootSplash(pendingRoute)
                } else {
                    MainNavHost(
                        container = container,
                        session = state.session,
                        pendingRoute = pendingRoute,
                        onRouteConsumed = onRouteConsumed,
                        onSignOut = {
                            scope.launch {
                                container.authRepository.logout()
                                container.signOut()
                                CtToastBus.show("已退出登录")
                            }
                        },
                    )
                }
            }
        }
        }

        // 全局 M3 Snackbar：所有 CtToastBus 消息都在底栏上方显示。
        CtToastHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (signedIn) 96.dp else 24.dp),
        )

        // App 内更新：宿主放根级 —— 冷启动即自动检查并直接弹窗（登录后首页可见），
        // 不依赖用户去「我的 → 检查更新」；身份门禁/加载页也能看到（未登录也检查）。
        UpdateHost()

        // 远程公告（配置热更）：同一版本只弹一次，用户关闭后不再打扰。
        val announcementText = announcement
        val latestDismissed = maxOf(dismissedAnnouncementVersion, announcementClosedVersion)
        if (!announcementText.isNullOrBlank() && announcementVersion > latestDismissed) {
            CtLightAlertDialog(
                title = "公告",
                text = announcementText,
                confirmText = "知道了",
                onConfirm = {
                    scope.launch { container.appSettings.dismissRemoteAnnouncement(announcementVersion) }
                },
                onDismissRequest = {
                    scope.launch { container.appSettings.dismissRemoteAnnouncement(announcementVersion) }
                },
            )
        }
    }
}

@Composable
private fun BootSplash(route: String?) {
    val tool = route?.substringBefore('?')?.removePrefix("tool/")
    val title = when (tool) {
        "hw" -> if (route.contains("pick=true")) "班级作业" else "待办任务"
        "nt" -> "班级通知"
        "tt" -> "班级课表"
        "root" -> "工作台"
        else -> tool?.let { com.furina.classtools.data.tool.ToolCatalog.byId(it)?.name }
    }
    if (title != null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            androidx.compose.material3.LinearProgressIndicator()
        }
        return
    }
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
                text = "枫丹工具箱",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            // 2026-11-20：启动页加载动画同步升级为 M3 Expressive 形变指示器。
            com.furina.classtools.ui.components.expressive.CtLoadingIndicator(
                modifier = Modifier.padding(top = 4.dp),
                size = com.furina.classtools.ui.components.expressive.CtLoadingIndicatorSize.Small,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}





