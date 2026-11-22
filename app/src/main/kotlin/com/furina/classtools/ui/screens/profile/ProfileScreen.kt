package com.furina.classtools.ui.screens.profile

import com.furina.classtools.ui.components.CtSnackbarHost

import androidx.compose.ui.platform.LocalDensity
import com.furina.classtools.ui.components.CtRootHeader
import com.furina.classtools.ui.components.CtStickyHeaderBar
import com.furina.classtools.ui.components.ctStickyHeaderProgress
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

import com.furina.classtools.ui.components.light.ctLightSummaryText
import com.furina.classtools.ui.theme.CtLightSetting
import com.furina.classtools.ui.theme.CtLightIntensity
import kotlinx.coroutines.flow.flowOf
import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.background.CtBackgroundSelection
import com.furina.classtools.data.background.LocalBackgroundStore
import com.furina.classtools.data.settings.AppSettingsStore
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.data.update.UpdateCheckResult
import com.furina.classtools.data.update.UpdateCheckStatus
import com.furina.classtools.data.update.UpdateManager
import com.furina.classtools.data.update.UpdateRuntime
import com.furina.classtools.data.update.UpdateTestVersion
import com.furina.classtools.data.update.UpdateVersion
import com.furina.classtools.ui.adaptive.CtAdaptiveSpec
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.components.update.UpdateDialogHost
import com.furina.classtools.ui.components.update.updateCheckSubtitle
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsBuilder
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.screens.privacy.LegalLinks
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.HarmonyTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.harmonyTint
import com.furina.classtools.ui.theme.rememberHarmonyColors
import com.furina.classtools.ui.theme.rememberProfileHeroColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import com.furina.classtools.ui.components.ctRootPageEdgeFade
import com.furina.classtools.ui.components.ctHeaderCollapseProgress

/**
 * 「我的」（v5 重做：对齐微信小程序 `pages/me` 的信息架构）。
 *
 * 结构（自上而下，手机单栏）：
 * 1. 身份卡：姓名 + 「身份已验证 / 职务 / 徽章 / 管理员」胶囊（**不显示头像/学号**）；
 * 2. 通知权限提示（可选，一次性可关闭）；
 * 3. 「数据与同步」：我的数据 2×2 指标 + 云同步开关 + 数据与隐私 / 清除本机缓存；
 * 4. 「通知与权限」：权限管理 / 提醒设置（副标题实时显示提醒状态）；
 * 5. 「外观」：颜色模式（跟随系统 / 浅色 / 深色）+ 隐藏状态栏开关 + 壁纸取色开关；
 * 6. 「关于与帮助」：反馈建议 / 使用说明 / 用户协议 / 隐私政策 / 版本；
 * 7. 退出登录 + 版本页脚。
 *
 * 平板（≥840dp）双栏：左「身份 + 数据与同步」，右「通知与权限 + 外观 + 关于 + 退出」；
 * 内容限宽 1200dp 居中，页脚/底部留白与浮岛保持一致（120dp）。
 *
 * 视觉：沿用 CtGlassCard(hero) + CtShapeTokens + 品牌色；行式列表、次要文字克制；
 * 不整页换皮、不新增图片资源；所有行高 ≥56dp（点按 ≥48dp）。
 *
 * @param adaptiveSpec 预览/测试注入窗口规格
 * @param appearanceMode 外观模式（预览注入；生产环境读写 [LocalAppSettingsStore]，实时生效）
 * @param hideStatusBar 隐藏系统状态栏（预览注入；生产环境同上）
 * @param initialShowSignOutConfirm 预览：直接打开退出确认
 * @param initialShowClearCacheConfirm 预览：直接打开清除缓存确认
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    session: Session,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    syncEnabled: Boolean = true,
    onSyncEnabledChange: (Boolean) -> Unit = {},
    onClearLocalCache: () -> Unit = {},
    onOpenDataPrivacy: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    showNotificationPermissionBanner: Boolean = false,
    onOpenReminderSettings: () -> Unit = {},
    /** 分享给同学（生成 5:4 班级工具站分享卡，与小程序端「转发给朋友」同口径）。 */
    onShareApp: () -> Unit = {},
    dynamicColorEnabled: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    hideStatusBar: Boolean = true,
    onHideStatusBarChange: (Boolean) -> Unit = {},
    adaptiveSpec: CtAdaptiveSpec? = null,
    meStats: MeStats = MeStats(),
    reminderStatus: ReminderStatus = ReminderStatus(),
    /** OPPO 账号（绑定学号）入口：已配置账号服务 + OPPO 系设备时才展示。 */
    oppoAccountAvailable: Boolean = false,
    /** 「我的」行副标题（如「已绑定 1/3」；空 = 尚未读取）。 */
    oppoAccountLabel: String = "",
    onOpenOppoAccount: () -> Unit = {},
    /** 荣耀账号（绑定学号）入口：已配置 + 荣耀设备时才展示。 */
    honorAccountAvailable: Boolean = false,
    honorAccountLabel: String = "",
    onOpenHonorAccount: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    /** 预览/测试注入更新检查状态；null = 使用进程内 UpdateManager 的实时状态。 */
    updateCheckStatus: UpdateCheckStatus? = null,
    /** 预览/测试注入更新管理器；null = 使用进程内单例 UpdateRuntime（生产）。 */
    updateManager: UpdateManager? = null,
    initialShowSignOutConfirm: Boolean = false,
    initialShowClearCacheConfirm: Boolean = false,
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    // 更新检查：生产环境用进程内单例（与全局 UpdateHost 共享弹窗状态）；
    // 预览（InspectionMode）不创建管理器，避免触碰 SharedPreferences/网络。
    val resolvedUpdateManager = remember(context, updateManager, inspectionMode) {
        updateManager ?: if (inspectionMode) null else UpdateRuntime.manager(context)
    }
    val idleUpdateStatus = remember { mutableStateOf<UpdateCheckStatus>(UpdateCheckStatus.Idle) }
    val updateStatusState: State<UpdateCheckStatus> =
        resolvedUpdateManager?.checkStatus?.collectAsStateWithLifecycle() ?: idleUpdateStatus
    val effectiveUpdateStatus = updateCheckStatus ?: updateStatusState.value
    val updateHostState: State<Boolean> =
        resolvedUpdateManager?.hostAttached?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    var showAgreement by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember(initialShowSignOutConfirm) { mutableStateOf(initialShowSignOutConfirm) }
    var showClearCacheConfirm by remember(initialShowClearCacheConfirm) { mutableStateOf(initialShowClearCacheConfirm) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val spec = adaptiveSpec ?: rememberCtAdaptiveSpec()
    val twoPane = spec.twoPane

    // 外观类设置：生产环境直接读写本机设置存储（LocalAppSettingsStore 由 MainActivity 提供，
    // 写入后主题根即时重组）；预览/孤立组件（null）用入参回退，不依赖 Context。
    // pending* 是本机乐观态：点击后立刻高亮，DataStore 回写到位后清空。
    val appSettings = LocalAppSettingsStore.current
    val backgroundStore = LocalBackgroundStore.current
    val storedBackground by (backgroundStore?.selection?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(CtBackgroundSelection.DEFAULT) })
    val storedAppearanceMode = rememberAppearanceMode(appSettings, appearanceMode)
    val storedHideStatusBar = rememberHideStatusBar(appSettings, hideStatusBar)
    // 沉浸光感（强 / 均衡 / 弱 / 关闭）：DataStore Flow 实时回读，写入后全应用立刻重组。
    val storedImmersiveLight by (appSettings?.immersiveLight ?: flowOf(CtLightSetting.Default))
        .collectAsStateWithLifecycle(initialValue = CtLightSetting.Default)
    var pendingAppearanceMode by remember { mutableStateOf<AppearanceMode?>(null) }
    var pendingHideStatusBar by remember { mutableStateOf<Boolean?>(null) }
    var pendingImmersiveLight by remember { mutableStateOf<CtLightSetting?>(null) }
    LaunchedEffect(storedAppearanceMode) {
        if (pendingAppearanceMode == storedAppearanceMode) pendingAppearanceMode = null
    }
    LaunchedEffect(storedHideStatusBar) {
        if (pendingHideStatusBar == storedHideStatusBar) pendingHideStatusBar = null
    }
    LaunchedEffect(storedImmersiveLight) {
        if (pendingImmersiveLight == storedImmersiveLight) pendingImmersiveLight = null
    }
    val effectiveAppearanceMode = pendingAppearanceMode ?: storedAppearanceMode
    val effectiveHideStatusBar = pendingHideStatusBar ?: storedHideStatusBar
    val effectiveImmersiveLight = pendingImmersiveLight ?: storedImmersiveLight

    fun changeAppearanceMode(mode: AppearanceMode) {
        pendingAppearanceMode = mode
        onAppearanceModeChange(mode)
        appSettings?.let { store -> scope.launch { runCatching { store.setAppearanceMode(mode) } } }
    }

    fun changeHideStatusBar(enabled: Boolean) {
        pendingHideStatusBar = enabled
        onHideStatusBarChange(enabled)
        appSettings?.let { store -> scope.launch { runCatching { store.setHideStatusBar(enabled) } } }
    }

    fun changeImmersiveLight(setting: CtLightSetting) {
        pendingImmersiveLight = setting
        appSettings?.let { store -> scope.launch { runCatching { store.setImmersiveLight(setting) } } }
    }

    fun requestClearCache() {
        showClearCacheConfirm = true
    }

    /** 「检查更新」：手动检查；发现新版由 UpdateHost 弹窗并自动后台下载，其余结果 Snackbar 提示。 */
    fun requestUpdateCheck() {
        val manager = resolvedUpdateManager ?: return
        scope.launch {
            val result = manager.checkNow()
            val message = when (result) {
                is UpdateCheckResult.UpToDate ->
                    "已是最新版本 v${UpdateVersion.normalize(result.currentVersionName)}"

                is UpdateCheckResult.Failed -> "检查更新失败：${result.reason}"
                // 发现新版本：弹窗由全局/局部 UpdateHost 展示，不再额外 Snackbar。
                is UpdateCheckResult.Available -> null
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    // 2026-11-20 页面骨架 v2：滚动内容作为 Haze 源，常驻悬浮玻璃条做真模糊。
    val meScrollState = rememberScrollState()
    val meHazeState = rememberHazeState()
    val meDensity = LocalDensity.current
    var meRefreshing by remember { mutableStateOf(false) }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = meRefreshing,
            onRefresh = {
                meRefreshing = true
                onRefresh()
                scope.launch {
                    kotlinx.coroutines.delay(900)
                    meRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(horizontal = com.furina.classtools.ui.theme.HarmonyTokens.PagePadding),
            ) {
                // 鸿蒙 MeScreen：单列滚动（内容最大宽 720，居中留白）。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(meHazeState)
                        .ctRootPageEdgeFade(meScrollState.value.toFloat())
                        .verticalScroll(meScrollState)
                        .padding(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // 2026-11-20：大标题放进滚动内容 —— 此前它在滚动区外，下拉时始终钉在顶部，
                    // 并与悬浮玻璃条叠字（用户反馈「顶部的字在下拉时还是固定在上面」）。
                    ProfileHeader(collapse = ctHeaderCollapseProgress(meScrollState.value.toFloat()))
                    IdentitySection(session)
                    NotificationPermissionBanner(
                        visible = showNotificationPermissionBanner,
                        onOpenPermissions = onOpenPermissions,
                    )
                    DataSection(
                        stats = meStats,
                        syncEnabled = syncEnabled,
                        onSyncEnabledChange = onSyncEnabledChange,
                        onOpenDataPrivacy = onOpenDataPrivacy,
                        onClearCache = ::requestClearCache,
                    )
                    AccountSection(
                        oppoAvailable = oppoAccountAvailable,
                        oppoLabel = oppoAccountLabel,
                        onOpenOppo = onOpenOppoAccount,
                        honorAvailable = honorAccountAvailable,
                        honorLabel = honorAccountLabel,
                        onOpenHonor = onOpenHonorAccount,
                    )
                    NotifySection(
                        reminderStatus = reminderStatus,
                        onOpenPermissions = onOpenPermissions,
                        onOpenReminderSettings = onOpenReminderSettings,
                        onShareApp = onShareApp,
                    )
                    BackgroundSection(store = backgroundStore, selection = storedBackground)
                    AppearanceSection(
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorChange = onDynamicColorChange,
                        appearanceMode = effectiveAppearanceMode,
                        onAppearanceModeChange = ::changeAppearanceMode,
                        hideStatusBar = effectiveHideStatusBar,
                        onHideStatusBarChange = ::changeHideStatusBar,
                        immersiveLight = effectiveImmersiveLight,
                        onImmersiveLightChange = ::changeImmersiveLight,
                    )
                    AboutSection(
                        onOpenFeedback = onOpenFeedback,
                        onOpenGuide = onOpenGuide,
                        onShowPrivacy = { showPrivacy = true },
                        onShowAgreement = { showAgreement = true },
                        updateStatus = effectiveUpdateStatus,
                        onCheckUpdate = ::requestUpdateCheck,
                    )
                    SignOutSection(
                        version = UpdateVersion.normalize(BuildConfig.VERSION_NAME),
                        onSignOutClick = { showSignOutConfirm = true },
                    )
                }
            }
            // 常驻悬浮玻璃条（滚动后渐变出现）。
            CtStickyHeaderBar(
                title = "我的",
                progress = ctStickyHeaderProgress(meScrollState.value.toFloat(), meDensity.density),
                hazeState = meHazeState,
            )
        }
        }
        // 没有全局 UpdateHost（App 根）时，由「我的」页兜底展示更新弹窗，避免检查到新版却看不到。
        if (resolvedUpdateManager != null && updateCheckStatus == null && !updateHostState.value) {
            UpdateDialogHost(resolvedUpdateManager)
        }
        CtSnackbarHost(
            hostState = snackbar,
            // 底栏浮岛高 64dp + 上下留白：Snackbar 必须在其上方，否则「已是最新版本」会被 dock 盖住。
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showAgreement) UserAgreementDialog(onDismiss = { showAgreement = false })

    if (showClearCacheConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除本机缓存？") },
            text = {
                Text(
                    "将清除本机缓存的名单 / 课表 / 演示等数据；身份与云端数据保留，下次打开会重新同步。",
                    style = CtText.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        onClearLocalCache()
                        scope.launch { snackbar.showSnackbar("已清除本机缓存") }
                    },
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showSignOutConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("退出登录？") },
            text = {
                Text(
                    "将清除本机会话与名单缓存；云端数据（作业完成 / 通知已处理 / 体测历史等）不受影响，重新核验后恢复。",
                    style = CtText.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("取消") }
            },
        )
    }
}

// =============================================================================
// 顶部 / 分组
// =============================================================================

@Composable
private fun ProfileHeader(collapse: Float) {
    CtRootHeader(
        title = "我的",
        collapse = collapse,
        eyebrow = "账号与偏好",
        subtitle = "身份、数据同步、提醒与外观",
    )
}

/**
 * 分组小标题（2026-11-19 对齐鸿蒙 `MeScreen.notifySection`）：
 * **12sp Medium 次级色** + 上 16 / 下 8 间距（此前是 13sp Regular + 上 16 / 下 6）。
 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    CtGlassCard(shape = CtShapeTokens.cardLarge) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
}

// =============================================================================
// 1) 账号与身份
// =============================================================================

@Composable
private fun IdentitySection(session: Session) {
    val reduceMotion = rememberReduceMotion()
    var appeared by remember(reduceMotion) { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) appeared = true
    }
    AnimatedVisibility(
        visible = appeared,
        enter = fadeIn(animationSpec = CtMotion.fadeIn) +
            slideInVertically(animationSpec = CtMotion.spatialSpec()) { height -> height / 10 },
    ) {
        ProfileHeroCard(session)
    }
}

/**
 * 「我的」身份卡（2026-11-17 按鸿蒙 MeScreen 重做）：白卡 18 圆角 + 浅阴影；
 * 56dp 雾青 tint 圆形首字 + 姓名 19 Medium + 角色/身份胶囊（品牌绿小面积）；无渐变/装饰圆。
 *
 * 隐私：不展示头像与完整学号，只用姓名首字 + 姓名/身份胶囊。
 */
@Composable
private fun ProfileHeroCard(session: Session) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberHarmonyColors()
    val initial = session.name.trim().take(1).ifBlank { "班" }
    CtGlassCard(shape = CtShapeTokens.cardLarge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.heroPaper),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = CtText.heading,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(HarmonyTokens.CardPadding))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name.ifBlank { "班级成员" },
                        style = CtText.heading,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "班级工具站" + if (session.isAdmin) " · 管理员" else "",
                        style = CtText.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(HarmonyTokens.ElementGap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeroPill(text = "身份已验证", emphasis = true)
                if (session.role.isNotBlank()) HeroPill(text = session.role)
                if (session.badge.isNotBlank() && session.badge != session.role) HeroPill(text = session.badge)
                if (session.isAdmin) HeroPill(text = "管理员", emphasis = true)
            }
        }
    }
}

/** 身份胶囊（鸿蒙 MeScreen：角色/身份用雾青 12% 底 + 雾青字，中性胶囊浅灰底）。 */
@Composable
private fun HeroPill(text: String, emphasis: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberHarmonyColors()
    val container = if (emphasis) harmonyTint(scheme.primary) else colors.background
    val content = if (emphasis) scheme.primary else colors.textSecondary
    Text(
        text = text,
        style = CtText.caption,
        fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

// =============================================================================
// 2) 数据与同步：我的数据 + 云同步 + 数据与隐私 / 清缓存
// =============================================================================

@Composable
private fun DataSection(
    stats: MeStats,
    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onClearCache: () -> Unit,
) {
    SectionTitle("数据与同步")
    GroupCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "我的数据",
                style = CtText.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CtSpace.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("待交作业", MeStatsBuilder.formatCount(stats.pendingHomework), Modifier.weight(1f))
                MetricCell("已完成作业", MeStatsBuilder.formatCount(stats.doneHomework), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(CtSpace.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("抽签记录", MeStatsBuilder.formatCount(stats.drawRecords), Modifier.weight(1f))
                MetricCell("体测记录", MeStatsBuilder.formatCount(stats.fitRecords), Modifier.weight(1f))
            }
            val extras = MeStatsBuilder.extraMetrics(stats)
            if (extras.isNotEmpty()) {
                Spacer(modifier = Modifier.height(CtSpace.sm))
                Text(
                    text = extras.joinToString(" · ") { (label, value) -> "$label $value" },
                    style = CtText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RowDivider()
        SwitchRow(
            title = "云同步",
            subtitle = "按账号同步作业 / 通知 / 抽签 / 点名 / 电费等",
            checked = syncEnabled,
            onCheckedChange = onSyncEnabledChange,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.Shield,
            title = "数据与隐私",
            subtitle = "查看与设置",
            onClick = onOpenDataPrivacy,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.DeleteOutline,
            title = "清除本机缓存",
            subtitle = "身份保留 · 不影响云端",
            onClick = onClearCache,
        )
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = CtText.title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = CtText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// =============================================================================
// 3) 账号（OPPO 账号绑定）
// =============================================================================

/** 「我的 → 账号」：厂商账号绑定入口（已绑定 N/3；点开底部弹层管理）。 */
@Composable
private fun AccountSection(
    oppoAvailable: Boolean,
    oppoLabel: String,
    onOpenOppo: () -> Unit,
    honorAvailable: Boolean,
    honorLabel: String,
    onOpenHonor: () -> Unit,
) {
    if (!oppoAvailable && !honorAvailable) return
    SectionTitle("账号")
    GroupCard {
        if (oppoAvailable) {
            ActionRow(
                icon = Icons.Rounded.AccountCircle,
                title = "OPPO 账号",
                subtitle = oppoLabel.ifBlank { "—" },
                onClick = onOpenOppo,
            )
        }
        if (oppoAvailable && honorAvailable) RowDivider()
        if (honorAvailable) {
            ActionRow(
                icon = Icons.Rounded.AccountCircle,
                title = "荣耀账号",
                subtitle = honorLabel.ifBlank { "—" },
                onClick = onOpenHonor,
            )
        }
    }
}

// =============================================================================
// 4) 通知与权限
// =============================================================================

@Composable
private fun NotifySection(
    reminderStatus: ReminderStatus,
    onOpenPermissions: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onShareApp: () -> Unit = {},
) {
    SectionTitle("通知与权限")
    GroupCard {
        ActionRow(
            icon = Icons.Rounded.NotificationsActive,
            title = "权限管理",
            subtitle = "通知 / 日历",
            onClick = onOpenPermissions,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.Schedule,
            title = "通知设置",
            subtitle = reminderStatus.label,
            subtitleHighlight = reminderStatus.isWarning,
            onClick = onOpenReminderSettings,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.IosShare,
            title = "分享给同学",
            subtitle = "把班级工具站发给同学（图片 / 链接）",
            onClick = onShareApp,
        )
    }
}

// =============================================================================
// 5) 外观
// =============================================================================

@Composable
private fun AppearanceSection(
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    hideStatusBar: Boolean,
    onHideStatusBarChange: (Boolean) -> Unit,
    immersiveLight: CtLightSetting,
    onImmersiveLightChange: (CtLightSetting) -> Unit,
) {
    SectionTitle("外观")
    GroupCard {
        AppearanceModeRows(mode = appearanceMode, onModeChange = onAppearanceModeChange)
        RowDivider()
        SwitchRow(
            title = "隐藏状态栏",
            subtitle = "沉浸式 · 下滑临时显示",
            leadingIcon = Icons.Rounded.Fullscreen,
            checked = hideStatusBar,
            onCheckedChange = onHideStatusBarChange,
        )
        RowDivider()
        SwitchRow(
            title = "壁纸取色",
            subtitle = "Material You · 跟随系统壁纸",
            leadingIcon = Icons.Rounded.Wallpaper,
            checked = dynamicColorEnabled,
            onCheckedChange = onDynamicColorChange,
        )
        RowDivider()
        ImmersiveLightRows(
            setting = immersiveLight,
            onSettingChange = onImmersiveLightChange,
        )
    }
}

/**
 * 沉浸光感四选一（强 / 均衡 / 弱 / 关闭）。
 *
 * 对应华为「沉浸光感」三档强度 + 关闭；只在设置里选一次，材质档位（ULTRA_THIN…ULTRA_THICK）
 * 由场景自动映射（顶部 ULTRA_THIN / 底部 THIN / 弹层 ULTRA_THICK），页面无需分别适配。
 */
@Composable
private fun ImmersiveLightRows(
    setting: CtLightSetting,
    onSettingChange: (CtLightSetting) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = com.furina.classtools.ui.theme.HarmonyTokens.ElementGap),
            ) {
                Text(
                    text = "沉浸光感卡片",
                    style = CtText.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (setting.enabled) ctLightSummaryText() else "实色卡片 · 更省电、更稳",
                    style = CtText.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = setting.enabled,
                onCheckedChange = { enabled ->
                    onSettingChange(
                        if (enabled) {
                            CtLightSetting(CtLightIntensity.BALANCED, true)
                        } else {
                            CtLightSetting.Off
                        },
                    )
                },
            )
        }
        CtLightSetting.choices.forEach { option ->
            val selected = option == setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSettingChange(option) },
                    )
                    .padding(start = 44.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CtLightSetting.labelOf(option),
                        style = CtText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (option.enabled) option.intensity.hint else "实色材质 · 最省电",
                        style = CtText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

/** 颜色模式三选一（单选行，点按区 ≥48dp，读屏 role=RadioButton）。 */
@Composable
private fun AppearanceModeRows(
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        AppearanceMode.entries.forEach { item ->
            val selected = item == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onModeChange(item) },
                    )
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HarmonyTokens.ElementGap),
            ) {
                Icon(
                    imageVector = appearanceModeIcon(item),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = item.label,
                    style = CtText.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

private fun appearanceModeIcon(mode: AppearanceMode) = when (mode) {
    AppearanceMode.SYSTEM -> Icons.Rounded.BrightnessAuto
    AppearanceMode.LIGHT -> Icons.Rounded.LightMode
    AppearanceMode.DARK -> Icons.Rounded.DarkMode
}

// =============================================================================
// 6) 关于与帮助
// =============================================================================

@Composable
private fun AboutSection(
    onOpenFeedback: () -> Unit,
    onOpenGuide: () -> Unit,
    onShowPrivacy: () -> Unit,
    onShowAgreement: () -> Unit,
    updateStatus: UpdateCheckStatus = UpdateCheckStatus.Idle,
    onCheckUpdate: () -> Unit = {},
) {
    SectionTitle("关于与帮助")
    GroupCard {
        ActionRow(
            icon = Icons.Rounded.Feedback,
            title = "反馈建议",
            subtitle = "问题或建议发给管理员",
            onClick = onOpenFeedback,
        )
        RowDivider()
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = "使用说明",
            subtitle = "各功能用法速览",
            onClick = onOpenGuide,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.Description,
            title = "用户协议",
            // 2026-11-19：摘要弹层 + 「查看完整版」入口（ECS 正式全文 V1.0 · 生效 2026-11-17）。
            subtitle = "摘要 · 完整版 " + LegalLinks.VERSION_LABEL,
            onClick = onShowAgreement,
        )
        RowDivider()
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.Article,
            title = "隐私政策",
            subtitle = "摘要 · 完整版 " + LegalLinks.VERSION_LABEL,
            onClick = onShowPrivacy,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.SystemUpdate,
            title = "检查更新",
            subtitle = updateCheckSubtitle(updateStatus),
            onClick = onCheckUpdate,
        )
        RowDivider()
        ValueRow(
            title = "版本",
            // v4.57.0-test1（测试版本 1）；Debug 的 -debug 后缀不展示。
            value = UpdateTestVersion.versionDisplay(BuildConfig.VERSION_NAME),
        )
    }
}

// =============================================================================
// 7) 退出 + 页脚
// =============================================================================

@Composable
private fun SignOutSection(
    version: String,
    onSignOutClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(CtSpace.xs))
    OutlinedButton(
        onClick = onSignOutClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = CtShapeTokens.pill,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = "退出登录",
            style = CtText.label,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(
        text = "退出后需重新验证姓名 + 学号才能使用班级数据",
        style = CtText.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    Text(
        text = "v$version · 班级工具站",
        style = CtText.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HarmonyTokens.ElementGap, bottom = HarmonyTokens.TextGap),
    )
}

// =============================================================================
// 行组件（行式列表，统一 56dp 起、点按区整行）
// =============================================================================

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleHighlight: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = HarmonyTokens.PagePadding, vertical = HarmonyTokens.ElementGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (subtitleHighlight) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = HarmonyTokens.PagePadding, vertical = HarmonyTokens.ElementGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = HarmonyTokens.PagePadding, vertical = HarmonyTokens.ElementGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = CtText.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = CtText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// =============================================================================
// 外观设置：本机存储读取（生产）/ 入参回退（预览）
// =============================================================================

/** 外观模式：有本机设置存储时读 DataStore Flow（实时生效），预览/孤立组件用入参。 */
@Composable
private fun rememberAppearanceMode(
    store: AppSettingsStore?,
    fallback: AppearanceMode,
): AppearanceMode {
    if (store == null) return fallback
    val value by store.appearanceMode.collectAsStateWithLifecycle(initialValue = fallback)
    return value
}

/** 「隐藏状态栏」开关：同上。 */
@Composable
private fun rememberHideStatusBar(
    store: AppSettingsStore?,
    fallback: Boolean,
): Boolean {
    if (store == null) return fallback
    val value by store.hideStatusBar.collectAsStateWithLifecycle(initialValue = fallback)
    return value
}













