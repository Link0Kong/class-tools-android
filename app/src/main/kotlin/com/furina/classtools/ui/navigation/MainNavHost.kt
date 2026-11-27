package com.furina.classtools.ui.navigation


import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.BoxWithConstraints

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.furina.classtools.data.tasks.importedHomeworkIds
import com.furina.classtools.data.tasks.TaskAiClient
import com.furina.classtools.data.tasks.objects
import com.furina.classtools.data.tasks.text
import com.furina.classtools.data.reminder.OneShotTargetKind
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.furina.classtools.AppContainer
import com.furina.classtools.data.calendar.CalendarEvent
import com.furina.classtools.data.calendar.ReminderCalendar
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.reminder.calendarKey
import com.furina.classtools.ui.screens.settings.UserReminderDialog
import com.furina.classtools.data.calendar.CalendarIntentFactory
import com.furina.classtools.data.calendar.CalendarWriteResult
import com.furina.classtools.data.calendar.CourseCalendarMapper
import com.furina.classtools.data.calendar.NoticeCalendarMapper
import com.furina.classtools.data.fitrecs.FitRecsLoadResult
import com.furina.classtools.data.fitrecs.FitRecsWriteResult
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.member.ClassMembersResult
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.privacy.LocalDataStats
import com.furina.classtools.data.reminder.ReminderOneShot
import com.furina.classtools.data.reminder.ReminderOneShotFactory
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.account.OppoAccountText
import com.furina.classtools.data.account.OppoBindResult
import com.furina.classtools.data.account.OppoBindingsUiState
import com.furina.classtools.data.account.OppoUnbindResult
import com.furina.classtools.data.push.DevicePushProvider
import com.furina.classtools.data.push.OppPushClick
import com.furina.classtools.data.push.PushProviderStatus
import com.furina.classtools.data.push.PushTokenPayload
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.data.timetable.TimetableViewPrefs
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.toCardSpec
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.adaptive.CtAdaptiveContent
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.furina.classtools.ui.components.dock.CtDockDestination
import com.furina.classtools.ui.components.dock.CtFloatingDock
import com.furina.classtools.ui.components.dock.CtPageTransitions
import com.furina.classtools.ui.components.dock.CtTabPager
import com.furina.classtools.ui.components.dock.rememberCtTabPagerState
import com.furina.classtools.ui.motion.SharedViewMotion
import com.furina.classtools.ui.BootSplash
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.cloudspace.CloudSpaceScreen
import com.furina.classtools.ui.screens.data.DataPrivacyScreen
import com.furina.classtools.ui.screens.diag.DiagScreen
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.screens.feedback.FeedbackScreen
import com.furina.classtools.ui.screens.express.ExpressScreen
import com.furina.classtools.ui.screens.guide.UserGuideScreen
import com.furina.classtools.ui.screens.stats.StatsScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.home.HomeToolsEditDialogs
import com.furina.classtools.ui.screens.home.HomeToolsEditSession
import com.furina.classtools.ui.screens.home.rememberHomeToolsEditSession
import com.furina.classtools.ui.screens.jw.JwEntryScreen
import com.furina.classtools.ui.screens.map.CampusMapScreen
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsBuilder
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.ui.screens.tasks.TaskScreen
import com.furina.classtools.ui.screens.notices.ApiNoticeRawAttachmentLoader
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.ppt.PptDownloadDetailScreen
import com.furina.classtools.ui.screens.ppt.PptListScreen
import com.furina.classtools.ui.screens.ppt.PptPlayerScreen
import com.furina.classtools.ui.screens.profile.OppoAccountSheet
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.screens.roster.RollCallScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.screens.scan.ScanScreen
import com.furina.classtools.ui.screens.settings.PermissionSettingsScreen
import com.furina.classtools.ui.screens.settings.ReminderSettingsScreen
import com.furina.classtools.ui.screens.timetable.TimetableViewScreen
import com.furina.classtools.ui.screens.timetable.TimetableFocusRequest
import com.furina.classtools.ui.screens.vote.VoteCreateScreen
import com.furina.classtools.ui.screens.vote.VoteDetailScreen
import com.furina.classtools.ui.screens.vote.VoteListScreen
import com.furina.classtools.ui.screens.tools.ToolPlaceholderScreen
import com.furina.classtools.ui.screens.water.WaterScreen
import com.furina.classtools.ui.theme.findActivity
import kotlinx.coroutines.launch
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.serialization.json.JsonObject
import com.furina.classtools.data.member.ClassRosterSource

/**
 * v2 导航（ColorOS 17 浮岛式）：
 * - 根页 = 三 Tab 横向 Pager（工作台 / 工具 / 我的），跟手滑动 + 浮岛底栏药丸联动；
 * - 工具页 / 数据与隐私 / 权限与提醒 = NavHost push（8% slide + fade + 0.98 scale spring）；
 * - 系统预测返回由 NavHost 处理；底栏只在根页显示。
 * - 窗口宽度自适应：各页面用 CtAdaptiveContent / rememberCtAdaptiveSpec 限宽或双栏。
 */
@Composable
fun MainNavHost(
    container: AppContainer,
    session: Session,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    incoming: IncomingNavigation? = null,
    onRouteConsumed: (String) -> Unit = {},
    /**
     * 独立工具页启动（金标 SharedView 的 Activity 目标）：直接把 NavHost 起点设为该工具，
     * 不再先渲染根页。为空 = 正常 MainActivity 根导航。
     */
    startToolId: String? = null,
    startToolPick: Boolean = false,
    /** ToolActivity 已有下层主宿主，到起点工具时交还给该宿主。 */
    standaloneTool: Boolean = false,
    /** 独立宿主从起点工具返回时调用（由 ToolActivity 执行 SharedView override + finish）。 */
    onCloseStandaloneTool: () -> Unit = {},
) {
    val navController = rememberNavController()
    val lastResultStore = remember(container, session.sid, session.debug) { container.drawLastResultStore(session) }
    val memberSource = remember(container, session.sid) {
        ClassRosterSource(container.classMembersRepository, container.classLeagueRepository)
    }
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()

    val sections by container.userDataRepository.currentSections.collectAsStateWithLifecycle()
    val quickToolIds = remember(sections) { HomeToolsEditor.read(sections) }
    val homeToolsEdit = rememberHomeToolsEditSession(session, container.userDataRepository)

    // 云同步开关状态（我的页展示/切换）。
    var syncEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        syncEnabled = runCatching { container.isSyncEnabled() }.getOrDefault(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, session.sid, standaloneTool) {
        if (!standaloneTool) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                runCatching { container.refreshCoreData() }.onFailure {
                    if (it is CancellationException) throw it
                }
            }
        }
    }

    // 根页 Pager 状态 hoist 到 NavHost 外层：进入工具页再返回时保持当前 Tab。
    val pagerState = rememberCtTabPagerState(pageCount = TOP_LEVEL_COUNT)

    val currentEntry by navController.currentBackStackEntryAsState()
    val toolTransitionAnchors = remember { ToolTransitionAnchors() }
    var cardReturn by remember { mutableStateOf<ToolCardReturn?>(null) }
    var cardAnimation by remember { mutableStateOf<ToolCardAnimation?>(null) }
    val reduceMotion by rememberUpdatedState(rememberReduceMotion())
    val activity = context.findActivity()

    val startRoute = if (standaloneTool && startToolId != null) Routes.tool(startToolId, startToolPick) else Routes.ROOT
    var startupRoute by remember { mutableStateOf(incoming?.takeIf { it.isFor(session) }?.route) }

    fun isWorkbenchCurrent() = navController.currentDestination?.route == Routes.ROOT

    fun fromWorkbench(action: () -> Unit) {
        if (!isWorkbenchCurrent() || SharedViewMotion.isToolOpeningFrom(activity)) return
        homeToolsEdit.requestLeave { if (isWorkbenchCurrent()) action() }
    }

    LaunchedEffect(incoming?.id, standaloneTool) {
        startupRoute = null
        if (standaloneTool) return@LaunchedEffect
        navController.repairWorkbenchParent()
        val request = incoming ?: return@LaunchedEffect
        if (request.isFor(session)) {
            homeToolsEdit.requestLeave(replacePending = true) {
                cardReturn = null
                cardAnimation = null
                val target = request.route
                when (target) {
                    Routes.ROOT -> {
                        navController.showWorkbench()
                        pagerState.requestScrollToPage(0)
                    }
                    Routes.tool("tt") -> {
                        navController.openIncomingTimetable(request)
                        pagerState.requestScrollToPage(0)
                    }
                    else -> {
                        if (target.startsWith("voteDetail/") || target.startsWith("pptDetail/")) {
                            val parent = if (target.startsWith("voteDetail/")) Routes.VOTE else Routes.tool("ppt")
                            if (navController.currentDestination?.route == Routes.VOTE_DETAIL &&
                                Routes.voteDetail(navController.currentBackStackEntry?.arguments?.getString(Routes.VOTE_DETAIL_ARG).orEmpty()) == target ||
                                navController.currentDestination?.route == Routes.PPT_DETAIL &&
                                Routes.pptDetail(navController.currentBackStackEntry?.arguments?.getString(Routes.PPT_DECK_ARG).orEmpty()) == target) return@requestLeave
                            if (runCatching { navController.getBackStackEntry(parent) }.isSuccess) {
                                navController.popBackStack(parent, inclusive = false)
                            } else {
                                navController.navigate(parent) { launchSingleTop = true }
                            }
                            navController.navigate(target) { launchSingleTop = true }
                            return@requestLeave
                        }
                        val current = navController.currentBackStackEntry
                        navController.navigate(target) {
                            if (current?.destination?.route == Routes.TOOL &&
                                current.arguments?.getString(Routes.TOOL_ARG) == target.substringBefore('?').removePrefix("tool/")) {
                                popUpTo(current.destination.id) { inclusive = true }
                            }
                            // Tool ids are arguments of one destination. Preserve different tool entries.
                            launchSingleTop = current?.destination?.route != Routes.TOOL
                        }
                    }
                }
            }
        } else {
            request.activityParent?.let { parent ->
                homeToolsEdit.requestLeave(replacePending = true) {
                    navController.showWorkbench()
                    navController.navigate(parent)
                }
            }
            CtToastBus.show("入口对应的登录已失效，请在当前账号中查看")
        }
        onRouteConsumed(request.id)
    }

    fun selectTab(index: Int) {
        if (index !in 0 until TOP_LEVEL_COUNT || pagerState.settledPage == index) return
        fromWorkbench { scope.launch { pagerState.animateScrollToPage(index) } }
    }

    // 轻量 Toast 反馈（导出/同步结果）。
    fun toast(target: android.content.Context, message: String) {
        CtToastBus.show(message)
    }

    /** 打开工具；「设置」工具卡已移除，设置入口统一在「我的」页。 */
    fun openTool(tool: AppTool) {
        val origin = toolTransitionAnchors.openingCard(tool.id)
        val page = pagerState.settledPage
        fromWorkbench {
            val rootId = navController.currentBackStackEntry?.id ?: return@fromWorkbench
            if (page != pagerState.settledPage || pagerState.isScrollInProgress) return@fromWorkbench
            val anchor = origin?.takeIf { !reduceMotion && toolTransitionAnchors.hasSameAnchor(it) }
            if (anchor != null && tool.id != "vote" && !standaloneTool && SharedViewMotion.tryLaunchTool(
                    activity, rootView, tool, anchor, toolTransitionAnchors, session,
                )) return@fromWorkbench
            val route = if (tool.id == "vote") Routes.VOTE else Routes.tool(tool.id)
            cardAnimation = anchor?.let { ToolCardAnimation(tool, it, reverse = false) }
            navController.navigate(route) { launchSingleTop = true }
            cardReturn = navController.currentBackStackEntry?.id?.let { targetId ->
                anchor?.let { ToolCardReturn(rootId, targetId, page, tool, it) }
            }
        }
    }

    fun popTool(entryId: String) {
        if (navController.currentBackStackEntry?.id != entryId) return
        val parent = navController.previousBackStackEntry
        if (parent == null) {
            if (standaloneTool) onCloseStandaloneTool() else navController.showWorkbench()
            return
        }
        val origin = cardReturn?.takeIf { it.targetId == entryId }
        if (origin != null) cardReturn = null
        cardAnimation = null
        navController.popBackStack()
        if (origin != null && origin.rootId == parent.id && !reduceMotion) {
            scope.launch {
                // The workbench can be recomposed after the pop. Require the same placed card.
                withFrameNanos { }
                withFrameNanos { }
                if (navController.currentBackStackEntry?.id == origin.rootId && !reduceMotion &&
                    pagerState.settledPage == origin.page && !pagerState.isScrollInProgress &&
                    toolTransitionAnchors.hasSameAnchor(origin.anchor)) {
                    cardAnimation = ToolCardAnimation(origin.tool, origin.anchor, reverse = true)
                }
            }
        }
    }

    // 统一分享：所有「导出/分享」入口先弹出 5:4 分享卡（分享图片 / 保存相册 / 分享链接）。
    var shareCard by remember { mutableStateOf<com.furina.classtools.features.share.ShareCardView?>(null) }
    val shareTimeLabel = remember {
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    /** 把既有导出卡（抽签/分组/体测/课表）转成 5:4 分享卡并打开分享层。 */
    fun launchExport(spec: ExportCardSpec, kind: com.furina.classtools.features.share.ShareKind) {
        shareCard = com.furina.classtools.features.share.ShareSpecs.fromExportCard(
            spec = spec,
            kind = kind,
            baseUrl = com.furina.classtools.BuildConfig.WEB_BASE_URL,
            timeLabel = shareTimeLabel,
        )
    }

    /** 写系统日历：有权限直接插入；无权限先请求权限，再退回系统 ACTION_INSERT 界面。 */
    fun handleCalendarWriteResult(event: CalendarEvent, result: CalendarWriteResult) {
        when (result) {
            is CalendarWriteResult.Success -> toast(context, "已加入系统日历")
            CalendarWriteResult.NeedsPermission,
            CalendarWriteResult.NoCalendarAccount,
            -> {
                val intent = CalendarIntentFactory.insertIntent(event)
                if (intent != null) {
                    runCatching { context.startActivity(intent) }
                        .onSuccess { toast(context, if (event.sourceKey.startsWith("reminder:")) "请在系统日历确认「准时提醒」并保存" else CalendarIntentFactory.fallbackHint()) }
                        .onFailure { toast(context, "没有可用的系统日历") }
                } else {
                    toast(context, "没有可用的系统日历")
                }
            }
            is CalendarWriteResult.Invalid -> toast(context, result.reason)
            is CalendarWriteResult.Failed -> toast(context, "写入日历失败：" + result.message)
        }
    }

    var pendingCalendarEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val event = pendingCalendarEvent
        pendingCalendarEvent = null
        if (event != null) {
            scope.launch {
                val result = container.calendarWriter.write(event)
                if (result is CalendarWriteResult.Success || granted.values.any { it }) {
                    handleCalendarWriteResult(event, result)
                } else {
                    handleCalendarWriteResult(event, CalendarWriteResult.NeedsPermission)
                }
            }
        }
    }

    fun writeCalendarEvent(event: CalendarEvent?) {
        if (event == null) {
            toast(context, "该内容没有可写入的时间信息")
            return
        }
        scope.launch {
            when (val result = container.calendarWriter.write(event)) {
                CalendarWriteResult.NeedsPermission -> {
                    pendingCalendarEvent = event
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                }
                else -> handleCalendarWriteResult(event, result)
            }
        }
    }

    // 沉浸光感：全应用统一「光感背板」（品牌柔光斑，强度随用户设置）+ 模糊层记账宿主；
    // 工具页/详情页/弹层的材质都建立在这块背板之上（官方「光在层级间柔和漫溢」）。
    Box(modifier = Modifier.fillMaxSize()) {
        com.furina.classtools.ui.components.light.CtLightBackdrop(modifier = Modifier.fillMaxSize())
        CompositionLocalProvider(LocalToolTransitionAnchors provides toolTransitionAnchors) {
            NavHost(
                navController = navController,
                // 独立 ToolActivity 直接以工具页为起点，首帧就是目标内容，SharedView 才不会捕到根页。
                startDestination = startRoute,
                modifier = modifier.fillMaxSize(),
            ) {
        composable(
            route = Routes.ROOT,
            // 根页不做位移/缩放（避免和容器变换覆盖层叠成两重动画），但被工具页覆盖时
            // 必须在首帧隐藏：工具页底色是半透明光感背板，根页留着会从空隙透出工作台内容。
            enterTransition = { EnterTransition.None },
            exitTransition = { CtPageTransitions.rootCoveredExit() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { entry ->
            val coldTarget = startupRoute
            if (coldTarget != null && coldTarget != Routes.ROOT) {
                BootSplash(coldTarget)
                return@composable
            }
            val rootActive = currentEntry?.id == entry.id
            BackHandler(enabled = rootActive && homeToolsEdit.isEditing(pagerState.settledPage)) {
                homeToolsEdit.requestLeave()
            }
            RootTabs(
                container = container,
                session = session,
                onSignOut = { fromWorkbench(onSignOut) },
                pagerState = pagerState,
                quickToolIds = quickToolIds,
                homeToolsEdit = homeToolsEdit,
                rootActive = rootActive,
                sections = sections,
                syncEnabled = syncEnabled,
                onSyncEnabledChange = { enabled ->
                    syncEnabled = enabled
                    scope.launch { runCatching { container.setSyncEnabled(enabled) } }
                },
                onClearLocalCache = {
                    scope.launch { runCatching { container.clearLocalCaches() } }
                },
                onOpenDataPrivacy = { fromWorkbench { navController.navigate(Routes.DATA_PRIVACY) } },
                onOpenPermissions = { fromWorkbench { navController.navigate(Routes.PERMISSIONS) } },
                onOpenReminderSettings = { fromWorkbench { navController.navigate(Routes.REMINDER_SETTINGS) } },
                onOpenFeedback = { fromWorkbench { navController.navigate(Routes.FEEDBACK) } },
                onOpenGuide = { fromWorkbench { navController.navigate(Routes.GUIDE) } },
                onSelectTab = ::selectTab,
                onOpenTool = ::openTool,
                onCardOpen = { tool, focus ->
                    fromWorkbench {
                        navController.navigate(Routes.tool(if (tool == "class-homework") "hw" else tool,
                            pick = tool == "class-homework", focus = focus)) { launchSingleTop = true }
                    }
                },
                onOpenPowerPicker = { fromWorkbench { navController.navigate(Routes.tool("power", pick = true)) } },
                onShareCard = { card -> shareCard = card },
                onScan = { fromWorkbench { navController.navigate(Routes.SCAN) } },
            )
            if (rootActive) HomeToolsEditDialogs(homeToolsEdit)
        }

        composable(Routes.SCAN) {
            ScanScreen(
                container = container,
                session = session,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TOOL,
            arguments = listOf(
                navArgument(Routes.TOOL_ARG) { type = NavType.StringType },
                navArgument(Routes.TOOL_PICK_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Routes.CREATE_TASK_ARG) { type = NavType.BoolType; defaultValue = false },
                navArgument(Routes.FOCUS_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.MAP_ROOM_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = {
                if (reduceMotion || cardAnimation != null) EnterTransition.None else CtPageTransitions.harmonyPushEnter()
            },
            exitTransition = {
                if (reduceMotion || cardAnimation != null) ExitTransition.None else CtPageTransitions.harmonyPushExit()
            },
            popEnterTransition = {
                if (reduceMotion) EnterTransition.None else CtPageTransitions.harmonyPopEnter()
            },
            popExitTransition = {
                if (reduceMotion) ExitTransition.None else CtPageTransitions.harmonyPopExit()
            },
        ) { entry ->
            val toolId = entry.arguments?.getString(Routes.TOOL_ARG).orEmpty()
            val pickInTool = entry.arguments?.getBoolean(Routes.TOOL_PICK_ARG) == true
            val focus = entry.arguments?.getString(Routes.FOCUS_ARG)
            val active = currentEntry?.id == entry.id
            fun back() { popTool(entry.id) }
            BackHandler(enabled = active, onBack = ::back)
            if (ToolCatalog.byId(toolId)?.adminOnly == true && !session.isAdmin) {
                LaunchedEffect(toolId, active) {
                    if (active) { CtToastBus.show("当前账号无法访问此工具"); back() }
                }
                return@composable
            }
            when (toolId) {
                "draw" -> {
                    val loadMembers: suspend () -> List<ClassMember> = remember(container) {
                        {
                            container.classMembersRepository.cached().ifEmpty {
                                (container.classMembersRepository.load() as? ClassMembersResult.Success)?.members.orEmpty()
                            }
                        }
                    }
                    androidx.compose.runtime.key(session.sid, session.debug) {
                        DrawScreen(
                            memberSource = memberSource,
                            publishProof = if (session.debug) null else { result -> container.submitDrawRecord(session, result) },
                            onBack = ::back,
                            session = session,
                            userDataRepository = container.userDataRepository,
                            onExport = { payload ->
                                val publicRecords = android.net.Uri.parse(com.furina.classtools.BuildConfig.REST_BASE_URL).path
                                    ?.trimEnd('/') == "/v1/rdb/rest"
                                val share = if (publicRecords) payload else payload.copy(receipt = null)
                                launchExport(share.toCardSpec(), com.furina.classtools.features.share.ShareKind.DRAW)
                            },
                            lastResultStore = lastResultStore,
                            loadMembers = loadMembers,
                            onScan = { navController.navigate(Routes.SCAN) },
                        )
                    }
                }

                "group" -> {
                    // 班级名单：先读缓存；缓存为空时再触发一次加载（独立工具页可直接进入，不依赖先抽签）。
                    var roster by remember(session.sid) { mutableStateOf<List<GroupMember>>(emptyList()) }
                    LaunchedEffect(session.sid) {
                        val cached = runCatching { container.classMembersRepository.cached() }
                            .getOrDefault(emptyList())
                        val members = cached.ifEmpty {
                            (runCatching { container.classMembersRepository.load() }.getOrNull()
                                as? ClassMembersResult.Success)?.members.orEmpty()
                        }
                        roster = members
                            .filter { it.seat > 0 }
                            .map { GroupMember(seat = it.seat, name = it.name) }
                    }
                    androidx.compose.runtime.key(session.sid, session.debug) {
                        GroupScreen(
                            memberSource = memberSource,
                            onBack = ::back,
                            session = session,
                            userDataRepository = container.userDataRepository,
                            rosterMembers = roster,
                            lastResultStore = lastResultStore,
                            onExport = { payload -> launchExport(payload.toCardSpec(), com.furina.classtools.features.share.ShareKind.GROUP) },
                        )
                    }
                }

                "map" -> CampusMapScreen(
                    onBack = ::back,
                    isPageActive = active,
                    onOpenTimetable = {
                        if (navController.currentBackStackEntry?.id == entry.id) {
                            if (navController.previousBackStackEntry?.arguments?.getString(Routes.TOOL_ARG) == "tt") back()
                            else navController.navigate(Routes.tool("tt"))
                        }
                    },
                    room = entry.arguments?.getString(Routes.MAP_ROOM_ARG),
                )

                "fit" -> {
                    // 体测历史：首屏从 fitrecs 云端读（2min TTL，失败降级本机缓存），保存/改名/删除写回云端
                    var fitHistory by remember { mutableStateOf<List<FitRecord>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        runCatching {
                            (container.fitRecsRepository.load(force = false) as? FitRecsLoadResult.Success)?.records
                        }.getOrNull()?.let { records -> fitHistory = records }
                        runCatching { container.fitRecsRepository.flushPending() }
                    }
                    FitScreen(
                        onBack = ::back,
                        session = session,
                        userDataRepository = container.userDataRepository,
                        initialHistory = fitHistory,
                        onSave = { record, kept, dropped ->
                            scope.launch {
                                val outcome = container.fitRecsRepository.save(record, kept, dropped)
                                fitHistory = kept.take(FitRecord.MAX_RECORDS)
                                toast(
                                    context,
                                    if (outcome is FitRecsWriteResult.Success && outcome.synced) {
                                        "已保存并同步"
                                    } else {
                                        "已保存本机，联网后补发"
                                    },
                                )
                            }
                        },
                        onRename = { record ->
                            scope.launch {
                                val outcome = container.fitRecsRepository.rename(record)
                                toast(
                                    context,
                                    if (outcome is FitRecsWriteResult.Success && outcome.synced) {
                                        "已改名并同步"
                                    } else {
                                        "已改名（本机），联网后补发"
                                    },
                                )
                            }
                        },
                        onDelete = { record ->
                            scope.launch {
                                val outcome = container.fitRecsRepository.archive(record.id)
                                fitHistory = fitHistory.filterNot { it.id == record.id }
                                toast(
                                    context,
                                    if (outcome is FitRecsWriteResult.Success && outcome.synced) {
                                        "已删除并同步"
                                    } else {
                                        "已删除（本机），联网后补发"
                                    },
                                )
                            }
                        },
                        onExport = { payload -> launchExport(payload.toCardSpec(), com.furina.classtools.features.share.ShareKind.FITNESS) },
                    )
                }

                "hw" -> {
                    val oneShotScheduler = remember {
                        runCatching {
                            ReminderOneShotFactory.create(context, container.permissionController)
                        }.getOrNull()
                    }
                    var remindTarget by remember {
                        mutableStateOf<com.furina.classtools.data.homework.HomeworkRow?>(null)
                    }
                    val tasks = remember(session) { container.tasksFor(session) }
                    var showHomework by androidx.compose.runtime.saveable.rememberSaveable(session.sid) {
                        mutableStateOf(pickInTool)
                    }
                    var detailHomeworkId by androidx.compose.runtime.saveable.rememberSaveable(session.sid, focus) {
                        mutableStateOf(focus.takeIf { pickInTool })
                    }
                    var openTaskId by androidx.compose.runtime.saveable.rememberSaveable(session.sid, focus) {
                        mutableStateOf(focus.takeUnless { pickInTool })
                    }
                    var reminderRevision by remember(session.sid) { mutableIntStateOf(0) }
                    val taskState = rememberSaveableStateHolder()
                    if (!showHomework) taskState.SaveableStateProvider(session.sid) {
                        var revision by remember(session.sid) { mutableIntStateOf(0) }
                        val brief = com.furina.classtools.ui.screens.tasks.rememberHomeworkBrief(
                            container.homeworkRepository, container.marksRepository, tasks, session.sid, revision,
                        )
                        val taskAiClient = remember(session) { TaskAiClient { session.token } }
                        com.furina.classtools.ui.screens.tasks.TaskCacheRecovery(tasks)
                        TaskScreen(tasks, sessionSid = session.sid, createFromWidget = entry.arguments?.getBoolean(Routes.CREATE_TASK_ARG) == true, openTaskId = openTaskId, onTaskOpened = { openTaskId = null }, taskAiClient = taskAiClient,
                            onCloseFocused = focus?.takeUnless { pickInTool }?.let { { back() } },
                            onBack = ::back, onHomework = { showHomework = true },
                            onSource = { task ->
                                val notice = (task["sourceNoticeId"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                                if (notice.isNotEmpty()) {
                                    navController.navigate(Routes.tool("nt", focus = notice))
                                } else {
                                    detailHomeworkId = (task["sourceHwId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    showHomework = true
                                }
                            },
                            onShare = { launchExport(it, com.furina.classtools.features.share.ShareKind.HOMEWORK) },
                            onReminderSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
                            onReminderCalendar = { task ->
                                writeCalendarEvent(ReminderCalendar.task(session.reminderOwner, task))
                            },
                            onRefreshHomework = { revision++ },
                            homeworkOverview = if (brief.pending.isEmpty()) null else ({
                                com.furina.classtools.ui.screens.tasks.ClassHomeworkOverview(brief,
                                    onOpen = { id -> detailHomeworkId = id; showHomework = true },
                                    onRemind = { remindTarget = it },
                                    onSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
                                    onImport = { rows ->
                                        val count = rows.count { tasks.importHomework(it) }
                                        toast(context, if (count > 0) "已加入 $count 项任务" else "作业已在个人任务中")
                                    })
                            }))
                    } else {
                        val taskSnapshot by tasks.snapshot.collectAsState()
                        val reminderTimes by produceState(emptyMap<String, List<Long>>(), oneShotScheduler, reminderRevision) {
                            val pending = oneShotScheduler?.listPending().orEmpty()
                            value = pending.filter { it.key.targetKind == OneShotTargetKind.HOMEWORK }
                                .groupBy({ it.key.targetId }, { it.fireAtMillis })
                        }
                        HomeworkScreen(
                            initialDetailId = detailHomeworkId,
                            onBack = { if (pickInTool) back() else { detailHomeworkId = null; showHomework = false } },
                            importedHomework = taskSnapshot.importedHomeworkIds(),
                            reminderTimes = reminderTimes,
                            onAddTask = { row ->
                                val added = tasks.importHomework(row)
                                val target = tasks.snapshot.value.objects("items").find { it.text("sourceHwId") == row.id }
                                if (target != null) {
                                    openTaskId = target.text("id")
                                    detailHomeworkId = null
                                    showHomework = false
                                    if (added) toast(context, "已加入个人任务")
                                }
                            },
                            session = session,
                            marksRepository = container.marksRepository,
                            homeworkRepository = container.homeworkRepository,
                            onRemindHomework = { row -> remindTarget = row },
                        )
                    }
                    BackHandler(enabled = active && showHomework && remindTarget == null) { if (pickInTool) back() else { detailHomeworkId = null; showHomework = false } }
                    remindTarget?.let { row ->
                        val title = row.subject.ifBlank { row.content.lineSequence().firstOrNull().orEmpty() }.ifBlank { "作业" }
                        UserReminderDialog(
                            OneShotTargetKind.HOMEWORK, row.id, title, remember(row) { ReminderOneShot.homeworkPlannedTimes(row) },
                            oneShotScheduler, onDismiss = { remindTarget = null },
                            onChange = { reminderRevision++ }, onPermissions = { navController.navigate(Routes.PERMISSIONS) },
                            onCalendar = { key, at, description -> writeCalendarEvent(
                                ReminderCalendar.event(key.calendarKey(session.reminderOwner), title, at, description)
                            ) },
                        )
                    }
                }

                "nt" -> {
                    val oneShotScheduler = remember {
                        runCatching {
                            ReminderOneShotFactory.create(context, container.permissionController)
                        }.getOrNull()
                    }
                    var remindTarget by remember {
                        mutableStateOf<com.furina.classtools.data.notices.NoticeGroupDto?>(null)
                    }
                    NoticesScreen(
                        onBack = ::back,
                        session = session,
                        marksRepository = container.marksRepository,
                        noticesRepository = container.noticesRepository,
                        rawAttachmentLoader = ApiNoticeRawAttachmentLoader(container.apiClient),
                        // v3 附件外置：图片 https 签名 URL 复用 data/ppt 的 OkHttp+BitmapFactory+LRU 加载器
                        imageLoader = container.pptImageLoader,
                        // OPPO PUSH 点击带入的通知：加载完成后定位并展开该通知详情
                        initialFocusNoticeId = focus,
                        onCloseFocused = { back() },
                        onAddToCalendar = { group ->
                            if (NoticeCalendarMapper.eligible(group)) {
                                writeCalendarEvent(NoticeCalendarMapper.eventFor(group))
                            } else {
                                toast(context, "该通知没有可写入日程的时间")
                            }
                        },
                        onRemindNotice = { group -> remindTarget = group },
                        onAddTask = { id, title, summary, deadline, category ->
                            val added = container.tasksFor(session).importNotice(id, title, summary, deadline, category)
                            toast(context, if (added) "已加入我的任务" else "这条通知已在任务中")
                        },
                        onShareNotice = { group, attachmentCount ->
                            shareCard = com.furina.classtools.features.share.ShareSpecs.noticeCard(
                                input = com.furina.classtools.features.share.ShareMappers.notice(
                                    group = group,
                                    attachmentCount = attachmentCount,
                                ),
                                baseUrl = com.furina.classtools.BuildConfig.WEB_BASE_URL,
                            )
                        },
                    )
                    remindTarget?.let { group ->
                        UserReminderDialog(
                            OneShotTargetKind.NOTICE, group.id, group.title, remember(group) { ReminderOneShot.noticePlannedTimes(group) },
                            oneShotScheduler, onDismiss = { remindTarget = null }, onPermissions = { navController.navigate(Routes.PERMISSIONS) },
                            onCalendar = { key, at, description -> writeCalendarEvent(
                                ReminderCalendar.event(key.calendarKey(session.reminderOwner), group.title, at, description,
                                    NoticeCalendarMapper.locationOf(group))
                            ) },
                        )
                    }
                }

                "tt" -> {
                    val delivery by entry.savedStateHandle.getStateFlow<Bundle?>(TIMETABLE_DELIVERY, null).collectAsState()
                    val focusRequest = delivery?.getString("id")?.let { TimetableFocusRequest(it, delivery?.getString("focus")) }
                        ?: focus?.let { TimetableFocusRequest(entry.id, it) }
                    // 周/日视图记忆：进页读本机偏好，用户显式切换时写回（深链定位不写）。
                    val timetablePrefs = remember(context) { TimetableViewPrefs(context) }
                    val storedViewMode = remember { timetablePrefs.read() }
                    TimetableViewScreen(
                        focusRequest = focusRequest,
                        isPageActive = active,
                        initialViewMode = storedViewMode,
                        onViewModeChange = { timetablePrefs.write(it) },
                        onBack = ::back,
                        onOpenMap = { room ->
                            if (navController.currentBackStackEntry?.id == entry.id) {
                                navController.navigate(Routes.campusMap(room))
                            }
                        },
                        repository = container.timetableRepository,
                        loginRepository = container.timetableLoginRepository,
                        sessionSid = session.sid,
                        onAddToCalendar = { course, week, termStart ->
                            writeCalendarEvent(CourseCalendarMapper.eventFor(course, week, termStart))
                        },
                        onExportTimetable = { spec -> launchExport(spec, com.furina.classtools.features.share.ShareKind.TIMETABLE) },
                    )
                }

                "power" -> PowerScreen(
                    onBack = ::back,
                    repository = container.powerRepository,
                    directoryRepository = container.powerDirectoryRepository,
                    userDataRepository = container.userDataRepository,
                    session = session,
                    initialPickerOpen = pickInTool,
                )

                "water" -> androidx.compose.runtime.key(session.sid, session.token) {
                    WaterScreen(
                        onBack = ::back,
                        session = session,
                        userDataRepository = container.userDataRepository,
                        directoryRepository = container.powerDirectoryRepository,
                        sharedLedger = remember(container, session) { container::sharedWater },
                    )
                }

                "roster" -> RosterScreen(
                    memberSource = memberSource,
                    onBack = ::back,
                    session = session,
                    classMembersRepository = container.classMembersRepository,

                    classLeagueRepository = container.classLeagueRepository,
                )

                "roll" -> RollCallScreen(
                    memberSource = memberSource,
                    onBack = ::back,
                    session = session,
                    classMembersRepository = container.classMembersRepository,
                    userDataRepository = container.userDataRepository,
                )

                "diag" -> DiagScreen(
                    onBack = ::back,
                    session = session,
                    repository = container.diagRepository,
                    deviceProbe = container.deviceProbe,
                    reporter = container.clientDiagReporter,
                    appVersion = com.furina.classtools.BuildConfig.VERSION_NAME,
                )

                "stats" -> StatsScreen(
                    onBack = ::back,
                    isAdmin = session.isAdmin,
                    repository = container.visitsRepository,
                )

                "ppt" -> PptListScreen(
                    onBack = ::back,
                    onOpenDetail = { deck -> navController.navigate(Routes.pptDetail(deck.id)) },
                    repository = container.pptRepository,
                    imageLoader = container.pptImageLoader,
                    session = session,
                    localStore = container.pptLocalStore,
                )

                "jw" -> JwEntryScreen(
                    sid = session.sid,
                    onBack = ::back,
                )

                "file" -> androidx.compose.runtime.key(container.cloudSpaceRepository) {
                    var consumedFile by androidx.compose.runtime.saveable.rememberSaveable(focus) { mutableStateOf(false) }
                    CloudSpaceScreen(
                        focusId = focus.takeUnless { consumedFile },
                        onFocusConsumed = { consumedFile = true },
                        onBack = ::back,
                        onOpenClassDecks = {
                            if (navController.currentBackStackEntry?.arguments?.getString(Routes.TOOL_ARG) == "file") {
                                navController.navigate(Routes.tool("ppt"))
                            }
                        },
                        repository = container.cloudSpaceRepository,
                    )
                }

                "express" -> ExpressScreen(
                    onBack = ::back,
                )

                else -> {
                    val tool = ToolCatalog.byId(toolId) ?: ToolCatalog.allEntry
                    ToolPlaceholderScreen(
                        tool = tool,
                        onBack = ::back,
                    )
                }
            }
        }

        composable(
            route = Routes.PPT_DETAIL,
            arguments = listOf(navArgument(Routes.PPT_DECK_ARG) { type = NavType.StringType }),
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) { entry ->
            PptDownloadDetailScreen(
                deckId = entry.arguments?.getString(Routes.PPT_DECK_ARG).orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPreview = { deckId -> navController.navigate(Routes.pptPlayer(deckId)) },
                repository = container.pptRepository,
                imageLoader = container.pptImageLoader,
                downloader = container.pptFileDownloader,
            )
        }

        composable(
            route = Routes.PPT_PLAYER,
            arguments = listOf(navArgument(Routes.PPT_DECK_ARG) { type = NavType.StringType }),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)) },
        ) { entry ->
            PptPlayerScreen(
                deckId = entry.arguments?.getString(Routes.PPT_DECK_ARG).orEmpty(),
                onClose = { navController.popBackStack() },
                repository = container.pptRepository,
                imageLoader = container.pptImageLoader,
                localStore = container.pptLocalStore,
                networkQuality = container.pptNetworkQuality,
            )
        }

        composable(
            route = Routes.DATA_PRIVACY,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) {
            // v2.1.1：本机数据快照（同步范围清单 + 8 计数），只读本机缓存，不联网。
            val sections by container.userDataRepository.currentSections.collectAsStateWithLifecycle()
            val marks by container.marksRepository.marks.collectAsStateWithLifecycle()
            var memberCount by remember { mutableStateOf(0) }
            var fitCount by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                memberCount = runCatching {
                    container.classMembersRepository.cached().size
                }.getOrDefault(0)
                fitCount = runCatching {
                    (container.fitRecsRepository.load(force = false) as? FitRecsLoadResult.Success)
                        ?.records?.size ?: 0
                }.getOrDefault(0)
            }
            val localSnapshot = remember(sections, marks, memberCount, fitCount) {
                LocalDataStats.from(
                    userdata = sections,
                    marks = marks,
                    fitRecords = fitCount,
                    classMembers = memberCount,
                    pending = 0,
                )
            }
            DataPrivacyScreen(
                onBack = { navController.popBackStack() },
                session = session,
                repository = container.privacyRepository,
                localSnapshot = localSnapshot,
            )
        }

        composable(
            route = Routes.PERMISSIONS,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) {
            val permissionController = remember {
                com.furina.classtools.data.permission.AndroidPermissionController(context)
            }
            PermissionSettingsScreen(
                onBack = { navController.popBackStack() },
                controller = permissionController,
                onOpenReminderSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
                showColorOsLiveUpdates = remember(container) {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && container.hasColorOsRom()
                },
            )
        }
        composable(
            route = Routes.REMINDER_SETTINGS,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) {
            val permissionController = remember {
                com.furina.classtools.data.permission.AndroidPermissionController(context)
            }
            val pushProvider = remember(container) {
                DevicePushProvider.forDevice(
                    brand = Build.BRAND,
                    manufacturer = Build.MANUFACTURER,
                    hasColorOsRom = container.hasColorOsRom(),
                    supportsOppoPush = container.oppoPushManager.isDeviceSupported,
                )
            }
            val pushStatus = when (pushProvider) {
                DevicePushProvider.OPPO -> {
                    val status by container.oppoPushManager.status.collectAsStateWithLifecycle()
                    PushProviderStatus(
                        provider = PushTokenPayload.PROVIDER_OPPO,
                        state = status.state.name,
                        errorCode = status.errorCode,
                        registrationTail = status.registrationId.takeIf { it.length >= 4 }?.takeLast(4).orEmpty(),
                    )
                }
                DevicePushProvider.VIVO -> {
                    val status by container.vivoPushManager.status.collectAsStateWithLifecycle()
                    PushProviderStatus(
                        provider = PushTokenPayload.PROVIDER_VIVO,
                        state = status.state.name,
                        errorCode = status.errorCode,
                        registrationTail = status.registrationId.takeLast(4),
                    )
                }
                null -> null
            }
            ReminderSettingsScreen(
                pushStatus = pushStatus,
                onRetryPush = {
                    when (pushProvider) {
                        DevicePushProvider.OPPO -> container.oppoPushManager.retry()
                        DevicePushProvider.VIVO -> container.vivoPushManager.retry()
                        null -> Unit
                    }
                },
                onBack = { navController.popBackStack() },
                settingsStore = container.reminderSettingsStore,
                permissionController = permissionController,
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onSettingsChanged = { settings ->
                    if (settings.anySectionEnabled) container.rescheduleReminders()
                    else container.cancelReminders()
                },
            )
        }

        composable(
            route = Routes.FEEDBACK,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) {
            var members by remember {
                mutableStateOf<List<com.furina.classtools.data.member.ClassMember>>(emptyList())
            }
            LaunchedEffect(Unit) {
                members = runCatching { container.classMembersRepository.cached() }
                    .getOrDefault(emptyList())
            }
            FeedbackScreen(
                onBack = { navController.popBackStack() },
                session = session,
                repository = container.feedbackRepository,
                nameOfSid = { sid -> members.firstOrNull { it.sid == sid }?.name },
            )
        }

        composable(
            route = Routes.GUIDE,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) {
            UserGuideScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.VOTE,
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) { entry ->
            fun back() { popTool(entry.id) }
            BackHandler(enabled = currentEntry?.id == entry.id, onBack = ::back)
            VoteListScreen(
                repository = container.voteRepository,
                onBack = ::back,
                onOpenDetail = { id -> navController.navigate(Routes.voteDetail(id)) },
                onCreate = { navController.navigate(Routes.voteCreate()) },
            )
        }

        composable(
            route = Routes.VOTE_CREATE,
            arguments = listOf(
                navArgument(Routes.VOTE_CREATE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) { entry ->
            VoteCreateScreen(
                repository = container.voteRepository,
                editingPollId = entry.arguments?.getString(Routes.VOTE_CREATE_ARG),
                memberSource = memberSource,
                onBack = { navController.popBackStack() },
                onFinished = { _, _ -> navController.popBackStack() },
            )
        }

        composable(
            route = Routes.VOTE_DETAIL,
            arguments = listOf(navArgument(Routes.VOTE_DETAIL_ARG) { type = NavType.StringType }),
            enterTransition = { CtPageTransitions.harmonyPushEnter() },
            exitTransition = { CtPageTransitions.harmonyPushExit() },
            popEnterTransition = { CtPageTransitions.harmonyPopEnter() },
            popExitTransition = { CtPageTransitions.harmonyPopExit() },
        ) { entry ->
            VoteDetailScreen(
                owner = session.reminderOwner,
                pollId = entry.arguments?.getString(Routes.VOTE_DETAIL_ARG).orEmpty(),
                repository = container.voteRepository,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.voteCreate(id)) },
                onShare = { poll, detail ->
                    shareCard = com.furina.classtools.features.share.ShareSpecs.voteCard(
                        input = com.furina.classtools.features.share.ShareMappers.vote(poll, detail),
                        baseUrl = com.furina.classtools.BuildConfig.WEB_BASE_URL,
                    )
                },
            )
        }
            }
        }
        if (!standaloneTool) {
            val animation = cardAnimation
            ToolTransitionOverlay(
                tool = animation?.tool,
                startBounds = animation?.anchor?.bounds,
                reverse = animation?.reverse == true,
                onDone = { if (cardAnimation === animation) cardAnimation = null },
            )
        }

        // 统一分享层（5:4 分享卡预览 + 分享图片 / 保存相册 / 分享链接 / 复制文案）。
        shareCard?.let { view ->
            com.furina.classtools.ui.components.share.ShareSheet(
                view = view,
                onDismiss = { shareCard = null },
                onMessage = { message -> toast(context, message) },
            )
        }
    }
}

private const val TOP_LEVEL_COUNT = 3

private data class ToolCardReturn(
    val rootId: String, val targetId: String, val page: Int, val tool: AppTool, val anchor: ToolTransitionAnchor,
)

private data class ToolCardAnimation(val tool: AppTool, val anchor: ToolTransitionAnchor, val reverse: Boolean)

/**
 * 根页三 Tab：浮岛底栏 + 横向 Pager。
 *
 * 平板/宽屏使用 CtAdaptiveContent 限宽（不拉满），工具页 push 后底栏自然消失。
 */
@Composable
private fun RootTabs(
    container: AppContainer,
    session: Session,
    onSignOut: () -> Unit,
    pagerState: PagerState,
    quickToolIds: List<String>,
    homeToolsEdit: HomeToolsEditSession,
    rootActive: Boolean,
    sections: JsonObject,
    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    onClearLocalCache: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenGuide: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onOpenTool: (AppTool) -> Unit,
    onCardOpen: (String, String?) -> Unit,
    onOpenPowerPicker: () -> Unit,
    /** 根页「扫一扫」：电脑扫码登录 / 抽签存证核验。 */
    onScan: () -> Unit = {},
    /** 根页发起的分享（我的 → 分享给同学）：交给宿主弹出统一分享层。 */
    onShareCard: (com.furina.classtools.features.share.ShareCardView) -> Unit = {},
) {
    val hazeState = remember { HazeState() }
    val spec = rememberCtAdaptiveSpec()
    val destinations = remember {
        listOf(
            CtDockDestination("home", "工作台", Icons.Rounded.Home),
            CtDockDestination("tools", "工具", Icons.Rounded.GridView),
            CtDockDestination("me", "我的", Icons.Rounded.Person),
        )
    }

    // 「我的 → 我的数据」聚合（只读本机缓存，进入「我的」时刷新一次；不新增网络请求）。
    var meStats by remember { mutableStateOf(MeStats()) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 2) {
            val homeworkRows = runCatching { container.homeworkRepository.cached() }
                .getOrDefault(emptyList())
            val marks = container.marksRepository.marks.value
            val fitCount = runCatching {
                (container.fitRecsRepository.load(force = false) as? FitRecsLoadResult.Success)
                    ?.records?.size ?: 0
            }.getOrDefault(0)
            val memberCount = runCatching {
                container.classMembersRepository.cached().size
            }.getOrNull()
            meStats = MeStatsBuilder.fromCached(
                homeworkRows = homeworkRows,
                marks = marks,
                fitRecords = fitCount,
                drawRecords = 0,
            ).copy(classMembers = memberCount)
        }
    }
    val reminderSettings by container.reminderSettingsStore.settings.collectAsStateWithLifecycle(
        initialValue = ReminderSettings.DEFAULT,
    )
    val notificationGranted = remember(pagerState.currentPage) {
        runCatching {
            container.permissionController.state(PermissionType.NOTIFICATIONS).isGranted
        }.getOrDefault(false)
    }
    val reminderStatus = ReminderStatus(
        enabled = reminderSettings.enabled,
        notificationPermission = notificationGranted,
        enabledModuleCount = reminderSettings.enabledCount(),
        totalModuleCount = 10,
        leadMinutes = reminderSettings.leadMinutes,
        classLeadMinutes = reminderSettings.classLeadMinutes,
    )

    // OPPO PUSH 状态不再在「我的」页展示（用户要求移除 OPPO 通知配置入口）；
    // 服务端推送仍由 AppContainer 内的 OppoPushManager 在启动/会话变化时注册，UI 无入口。

    // OPPO 账号（绑定学号 ≤3 个）：入口仅在已配置账号服务 + **严格 ColorOS 设备**时展示。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val oppoAccountManager = container.oppoAccountManager
    val oppoBindingsState by oppoAccountManager.bindingsState.collectAsStateWithLifecycle()
    var showOppoAccountSheet by remember { mutableStateOf(false) }
    var oppoAccountBusy by remember { mutableStateOf(false) }
    val oppoAccountLabel = when (val state = oppoBindingsState) {
        is OppoBindingsUiState.Ready -> OppoAccountText.bindingsLabel(state.page.count, state.page.max)
        is OppoBindingsUiState.Loading, is OppoBindingsUiState.Error -> "读取中…"
        OppoBindingsUiState.Idle -> ""
    }

    /** 拉取 OPPO 绑定列表（进入「我的」/打开弹层时；失败静默，列表内有「重试」）。 */
    fun refreshOppoBindings() {
        if (!oppoAccountManager.bindingsAvailable || session.token.isBlank()) return
        scope.launch { runCatching { oppoAccountManager.refreshBindings(session.token) } }
    }

    LaunchedEffect(pagerState.currentPage, showOppoAccountSheet) {
        // 进入「我的」页或打开账号弹层时刷新一次（1 个轻量请求；失败静默）。
        if (pagerState.currentPage == 2 || showOppoAccountSheet) refreshOppoBindings()
    }

    /** 绑定：拉起 OPPO 授权 → `POST /oppo/bind`（带当前班级 token）；结果 Toast（简洁，不写长文）。 */
    fun bindOppoAccount() {
        val host = activity ?: run {
            CtToastBus.show("当前环境不支持 OPPO 账号授权")
            return
        }
        if (oppoAccountBusy) return
        oppoAccountBusy = true
        scope.launch {
            val result = runCatching { oppoAccountManager.bind(host, session.token) }
                .getOrElse { OppoBindResult.Failure("unexpected", "绑定失败，请重试") }
            oppoAccountBusy = false
            val message = when (result) {
                is OppoBindResult.Success -> "已绑定 OPPO 账号"
                is OppoBindResult.Limit -> result.message
                is OppoBindResult.BoundElsewhere -> "该 OPPO 账号已绑定其他学号"
                is OppoBindResult.Failure -> result.message
            }
            CtToastBus.show(message)
        }
    }

    /** 解绑：`POST /oppo/unbind`；成功后列表自动刷新。 */
    fun unbindOppoAccount(bindingId: Long) {
        if (oppoAccountBusy) return
        oppoAccountBusy = true
        scope.launch {
            val result = runCatching { oppoAccountManager.unbind(session.token, bindingId) }
                .getOrElse { OppoUnbindResult.Failure("unexpected", "解绑失败，请重试") }
            oppoAccountBusy = false
            val message = when (result) {
                is OppoUnbindResult.Success -> "已解绑"
                is OppoUnbindResult.Failure -> result.message
            }
            CtToastBus.show(message)
        }
    }

    // ---- 荣耀账号（与 OPPO 同一套绑定/登录契约，路径 /honor/*；授权走 Activity 结果） ----
    val honorAccountManager = container.honorAccountManager
    val honorBindingsState by honorAccountManager.bindingsState.collectAsStateWithLifecycle()
    var showHonorAccountSheet by remember { mutableStateOf(false) }
    var honorAccountBusy by remember { mutableStateOf(false) }
    val honorAccountLabel = when (val state = honorBindingsState) {
        is OppoBindingsUiState.Ready -> OppoAccountText.bindingsLabel(state.page.count, state.page.max)
        is OppoBindingsUiState.Loading, is OppoBindingsUiState.Error -> "读取中…"
        OppoBindingsUiState.Idle -> ""
    }

    fun refreshHonorBindings() {
        if (!honorAccountManager.bindingsAvailable || session.token.isBlank()) return
        scope.launch { runCatching { honorAccountManager.refreshBindings(session.token) } }
    }

    LaunchedEffect(pagerState.currentPage, showHonorAccountSheet) {
        if (pagerState.currentPage == 2 || showHonorAccountSheet) refreshHonorBindings()
    }

    val honorBindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!honorAccountBusy) return@rememberLauncherForActivityResult
        scope.launch {
            val bindResult = runCatching {
                honorAccountManager.bind(result.resultCode, result.data, session.token)
            }.getOrElse { OppoBindResult.Failure("unexpected", "绑定失败，请重试") }
            honorAccountBusy = false
            val message = when (bindResult) {
                is OppoBindResult.Success -> "已绑定荣耀账号"
                is OppoBindResult.Limit -> bindResult.message
                is OppoBindResult.BoundElsewhere -> "该荣耀账号已绑定其他学号"
                is OppoBindResult.Failure -> bindResult.message
            }
            CtToastBus.show(message)
        }
    }

    fun bindHonorAccount() {
        val host = activity ?: run {
            CtToastBus.show("当前环境不支持荣耀账号授权")
            return
        }
        if (honorAccountBusy) return
        val intent = honorAccountManager.signInIntent(host)
        if (intent == null) {
            CtToastBus.show("无法拉起荣耀账号授权")
            return
        }
        honorAccountBusy = true
        runCatching { honorBindLauncher.launch(intent) }.onFailure {
            honorAccountBusy = false
            CtToastBus.show("无法拉起荣耀账号授权")
        }
    }

    fun unbindHonorAccount(bindingId: Long) {
        if (honorAccountBusy) return
        honorAccountBusy = true
        scope.launch {
            val result = runCatching { honorAccountManager.unbind(session.token, bindingId) }
                .getOrElse { OppoUnbindResult.Failure("unexpected", "解绑失败，请重试") }
            honorAccountBusy = false
            val message = when (result) {
                is OppoUnbindResult.Success -> "已解绑"
                is OppoUnbindResult.Failure -> result.message
            }
            CtToastBus.show(message)
        }
    }

    val showNotificationPermissionBanner = !notificationGranted && reminderSettings.enabled
    // 沉浸光感是否启用（dock 的玻璃实现二选一：CtLight 材质 or 旧 Haze）。
    val ctLightEnabled = com.furina.classtools.ui.theme.LocalCtLight.current.enabled
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 600.dp
        Row(modifier = Modifier.fillMaxSize()) {
            if (wideLayout) {
                com.furina.classtools.ui.components.dock.CtNavigationRail(
                    destinations = destinations,
                    selectedIndex = pagerState.currentPage,
                    onSelect = onSelectTab,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        CtTabPager(
            pagerState = pagerState,
            userScrollEnabled = !homeToolsEdit.isEditing(pagerState.settledPage),
            onBlockedSwipe = { direction -> onSelectTab(pagerState.settledPage + direction) },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    taskRepository = remember(session) { container.tasksFor(session) },
                    session = session,
                    quickToolIds = quickToolIds,
                    onOpenTool = onOpenTool,
                    onCardOpen = onCardOpen,
                    timetableRepository = container.timetableRepository,
                    marksRepository = container.marksRepository,
                    powerRepository = container.powerRepository,
                    noticesRepository = container.noticesRepository,
                    homeworkRepository = container.homeworkRepository,
                    userDataRepository = container.userDataRepository,
                    onOpenAll = { onSelectTab(1) },
                    showNotificationPermissionBanner = showNotificationPermissionBanner,
                    onOpenPermissions = onOpenPermissions,
                    isPageActive = rootActive && pagerState.settledPage == 0 && !pagerState.isScrollInProgress,
                    editSession = homeToolsEdit,
                    onOpenPowerPicker = onOpenPowerPicker,
                    onScan = onScan,
                )

                1 -> AllToolsScreen(
                    onOpenTool = onOpenTool,
                    isAdmin = session.isAdmin,
                    userDataRepository = container.userDataRepository.boundTo(session),
                    quickToolIds = quickToolIds,
                    editSession = homeToolsEdit,
                    isPageActive = rootActive && pagerState.settledPage == 1 && !pagerState.isScrollInProgress,
                    onRefresh = {
                        scope.launch {
                            runCatching { container.userDataRepository.pull(force = true) }
                        }
                    },
                )

                else -> ProfileScreen(
                    session = session,
                        onSignOut = onSignOut,
                        onRefresh = {
                            scope.launch {
                                runCatching { container.userDataRepository.pull(force = true) }
                                runCatching { container.marksRepository.refresh(force = true) }
                            }
                        },
                        syncEnabled = syncEnabled,
                        onSyncEnabledChange = onSyncEnabledChange,
                        onClearLocalCache = onClearLocalCache,
                        onOpenDataPrivacy = onOpenDataPrivacy,
                        onOpenPermissions = onOpenPermissions,
                        showNotificationPermissionBanner = showNotificationPermissionBanner,
                        onOpenReminderSettings = onOpenReminderSettings,
                        onShareApp = {
                            onShareCard(
                                com.furina.classtools.features.share.ShareSpecs.appCard(
                                    baseUrl = com.furina.classtools.BuildConfig.WEB_BASE_URL,
                                ),
                            )
                        },
                        meStats = meStats,
                        reminderStatus = reminderStatus,
                        oppoAccountAvailable = oppoAccountManager.bindingsAvailable,
                        oppoAccountLabel = oppoAccountLabel,
                        onOpenOppoAccount = { showOppoAccountSheet = true },
                        honorAccountAvailable = honorAccountManager.bindingsAvailable,
                        honorAccountLabel = honorAccountLabel,
                        onOpenHonorAccount = { showHonorAccountSheet = true },
                        onOpenFeedback = onOpenFeedback,
                        onOpenGuide = onOpenGuide,
                    )
            }
        }
            }
        }

        // 厂商账号绑定管理弹层（列表/绑定/解绑；入口在「我的 → 账号」）。
        if (showOppoAccountSheet) {
            OppoAccountSheet(
                bindingsState = oppoBindingsState,
                busy = oppoAccountBusy,
                maxBindings = (oppoBindingsState as? OppoBindingsUiState.Ready)?.page?.max
                    ?: OppoAccountText.DEFAULT_MAX_BINDINGS,
                onDismiss = { if (!oppoAccountBusy) showOppoAccountSheet = false },
                onRefresh = { refreshOppoBindings() },
                onBind = { bindOppoAccount() },
                onUnbind = { binding -> unbindOppoAccount(binding.id) },
            )
        }
        if (showHonorAccountSheet) {
            OppoAccountSheet(
                bindingsState = honorBindingsState,
                busy = honorAccountBusy,
                maxBindings = (honorBindingsState as? OppoBindingsUiState.Ready)?.page?.max
                    ?: OppoAccountText.DEFAULT_MAX_BINDINGS,
                onDismiss = { if (!honorAccountBusy) showHonorAccountSheet = false },
                onRefresh = { refreshHonorBindings() },
                onBind = { bindHonorAccount() },
                onUnbind = { binding -> unbindHonorAccount(binding.id) },
                providerLabel = "荣耀",
            )
        }

        // 底部安全区：2026-11-20 起**不再画不透明底色** —— App 根已把光场铺到屏幕最底端，
        // 这一条灰底会把光场横向切断（用户反馈「只有工作台背景是全屏的」）。
        // 系统导航栏本身是透明 + 无对比度蒙层（MainActivity 已设置），无需再垫底色。

        // M3 NavigationBar 自带容器分层，不再叠加底部渐变蒙层（会把内容压暗/产生灰带）。
        if (!wideLayout) {
            CtFloatingDock(
                destinations = destinations,
                selectedIndex = pagerState.currentPage,
                onSelect = onSelectTab,
                pagerOffsetFraction = pagerState.currentPageOffsetFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
                glassModifier = null,
                hazeState = null,
            )
        }
    }
}
