package com.furina.classtools.ui.screens.profile

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import com.furina.classtools.ui.theme.rememberProfileHeroColors
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    session: Session,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    syncEnabled: Boolean = true,
    onSyncEnabledChange: (Boolean) -> Unit = {},
    onClearLocalCache: () -> Unit = {},
    onOpenDataPrivacy: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    showNotificationPermissionBanner: Boolean = false,
    onOpenReminderSettings: () -> Unit = {},
    dynamicColorEnabled: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    onAppearanceModeChange: (AppearanceMode) -> Unit = {},
    hideStatusBar: Boolean = true,
    onHideStatusBarChange: (Boolean) -> Unit = {},
    adaptiveSpec: CtAdaptiveSpec? = null,
    meStats: MeStats = MeStats(),
    reminderStatus: ReminderStatus = ReminderStatus(),
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
    val storedAppearanceMode = rememberAppearanceMode(appSettings, appearanceMode)
    val storedHideStatusBar = rememberHideStatusBar(appSettings, hideStatusBar)
    var pendingAppearanceMode by remember { mutableStateOf<AppearanceMode?>(null) }
    var pendingHideStatusBar by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(storedAppearanceMode) {
        if (pendingAppearanceMode == storedAppearanceMode) pendingAppearanceMode = null
    }
    LaunchedEffect(storedHideStatusBar) {
        if (pendingHideStatusBar == storedHideStatusBar) pendingHideStatusBar = null
    }
    val effectiveAppearanceMode = pendingAppearanceMode ?: storedAppearanceMode
    val effectiveHideStatusBar = pendingHideStatusBar ?: storedHideStatusBar

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

    fun requestClearCache() {
        showClearCacheConfirm = true
    }

    /** 「检查更新」：手动检查（不受每日节流限制）；发现新版由 UpdateHost 弹窗，其余结果 Snackbar 提示。 */
    fun requestUpdateCheck() {
        val manager = resolvedUpdateManager ?: return
        scope.launch {
            val result = manager.checkNow(manual = true)
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

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(horizontal = spec.horizontalPadding),
            ) {
                ProfileHeader()
                if (twoPane) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.45f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NotifySection(
                                reminderStatus = reminderStatus,
                                onOpenPermissions = onOpenPermissions,
                                onOpenReminderSettings = onOpenReminderSettings,
                            )
                            AppearanceSection(
                                dynamicColorEnabled = dynamicColorEnabled,
                                onDynamicColorChange = onDynamicColorChange,
                                appearanceMode = effectiveAppearanceMode,
                                onAppearanceModeChange = ::changeAppearanceMode,
                                hideStatusBar = effectiveHideStatusBar,
                                onHideStatusBarChange = ::changeHideStatusBar,
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
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                        NotifySection(
                            reminderStatus = reminderStatus,
                            onOpenPermissions = onOpenPermissions,
                            onOpenReminderSettings = onOpenReminderSettings,
                        )
                        AppearanceSection(
                            dynamicColorEnabled = dynamicColorEnabled,
                            onDynamicColorChange = onDynamicColorChange,
                            appearanceMode = effectiveAppearanceMode,
                            onAppearanceModeChange = ::changeAppearanceMode,
                            hideStatusBar = effectiveHideStatusBar,
                            onHideStatusBarChange = ::changeHideStatusBar,
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
            }
        }
        // 没有全局 UpdateHost（App 根）时，由「我的」页兜底展示更新弹窗，避免检查到新版却看不到。
        if (resolvedUpdateManager != null && updateCheckStatus == null && !updateHostState.value) {
            UpdateDialogHost(resolvedUpdateManager)
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showAgreement) UserAgreementDialog(onDismiss = { showAgreement = false })

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除本机缓存？") },
            text = {
                Text(
                    "将清除本机缓存的名单 / 课表 / 演示等数据；身份与云端数据保留，下次打开会重新同步。",
                    style = MaterialTheme.typography.bodyMedium,
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
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("退出登录？") },
            text = {
                Text(
                    "将清除本机会话与名单缓存；云端数据（作业完成 / 通知已处理 / 体测历史等）不受影响，重新核验后恢复。",
                    style = MaterialTheme.typography.bodyMedium,
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
private fun ProfileHeader() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 32.dp)
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    CtGlassCard(shape = CtShapeTokens.cardLarge) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
 * 「我的」顶部身份 hero（v5，参考 xiaoying 渐变 banner + 重叠圆形标识的层级感；不复制样式/素材）。
 *
 * 隐私：不展示头像与学号，只用姓名首字圆形标识 + 姓名/身份胶囊；右上角放品牌三圆点（BrandMark）呼应启动图标。
 * 配色：浅色保持既有深青→灰蓝固定渐变 + 白字；深色改为中性 tonal 深色 + onSurface 文字
 * （品牌绿只出现在胶囊/图标的小面积 accent），见 [rememberProfileHeroColors]。
 */
@Composable
private fun ProfileHeroCard(session: Session) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalCtDarkTheme.current
    val hero = rememberProfileHeroColors()
    val initial = session.name.trim().take(1).ifBlank { "班" }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 152.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(hero.start, hero.end))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 只保留一层极轻柔光（2026-11-16：去掉第二层装饰圆，减少重装饰）。
            drawCircle(
                color = if (dark) scheme.primary.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.94f, size.height * 0.10f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (dark) {
                                scheme.surfaceContainerHighest
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (dark) {
                                scheme.outlineVariant.copy(alpha = 0.55f)
                            } else {
                                Color.White.copy(alpha = 0.26f)
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = hero.content,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "班级工具站",
                        style = MaterialTheme.typography.labelMedium,
                        color = hero.content.copy(alpha = if (dark) 0.72f else 0.74f),
                    )
                    Text(
                        text = session.name.ifBlank { "班级成员" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = hero.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BrandMark(size = 38.dp, container = hero.innerFill)
            }
            Spacer(modifier = Modifier.height(8.dp))
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

/** hero 胶囊：浅色为白字白底半透明（原视觉）；深色强调胶囊用品牌绿小面积点缀，普通胶囊用中性面。 */
@Composable
private fun HeroPill(text: String, emphasis: Boolean = false) {
    val dark = LocalCtDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val container = when {
        dark && emphasis -> scheme.primary.copy(alpha = 0.18f)
        dark -> scheme.onSurface.copy(alpha = 0.08f)
        emphasis -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.13f)
    }
    val content = when {
        dark && emphasis -> scheme.primary
        dark -> scheme.onSurfaceVariant
        else -> Color.White
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("待交作业", MeStatsBuilder.formatCount(stats.pendingHomework), Modifier.weight(1f))
                MetricCell("已完成作业", MeStatsBuilder.formatCount(stats.doneHomework), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("抽签记录", MeStatsBuilder.formatCount(stats.drawRecords), Modifier.weight(1f))
                MetricCell("体测记录", MeStatsBuilder.formatCount(stats.fitRecords), Modifier.weight(1f))
            }
            val extras = MeStatsBuilder.extraMetrics(stats)
            if (extras.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = extras.joinToString(" · ") { (label, value) -> "$label $value" },
                    style = MaterialTheme.typography.labelSmall,
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// =============================================================================
// 3) 通知与权限
// =============================================================================

@Composable
private fun NotifySection(
    reminderStatus: ReminderStatus,
    onOpenPermissions: () -> Unit,
    onOpenReminderSettings: () -> Unit,
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
            title = "提醒设置",
            subtitle = reminderStatus.label,
            subtitleHighlight = reminderStatus.isWarning,
            onClick = onOpenReminderSettings,
        )
    }
}

// =============================================================================
// 4) 外观
// =============================================================================

@Composable
private fun AppearanceSection(
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    hideStatusBar: Boolean,
    onHideStatusBarChange: (Boolean) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    style = MaterialTheme.typography.bodyLarge,
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
// 5) 关于与帮助
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
            onClick = onShowAgreement,
        )
        RowDivider()
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.Article,
            title = "隐私政策",
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
// 6) 退出 + 页脚
// =============================================================================

@Composable
private fun SignOutSection(
    version: String,
    onSignOutClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(4.dp))
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(
        text = "退出后需重新验证姓名 + 学号才能使用班级数据",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    Text(
        text = "v$version · 班级工具站",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    style = MaterialTheme.typography.bodySmall,
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
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    style = MaterialTheme.typography.bodySmall,
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
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
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
