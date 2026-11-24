package com.furina.classtools.ui.navigation


import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.BoxWithConstraints

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import android.Manifest
import android.graphics.Bitmap
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.furina.classtools.AppContainer
import com.furina.classtools.data.calendar.CalendarEvent
import com.furina.classtools.data.calendar.CalendarIntentFactory
import com.furina.classtools.data.calendar.CalendarWriteResult
import com.furina.classtools.data.calendar.CourseCalendarMapper
import com.furina.classtools.data.calendar.NoticeCalendarMapper
import com.furina.classtools.data.fitrecs.FitRecsLoadResult
import com.furina.classtools.data.fitrecs.FitRecsWriteResult
import com.furina.classtools.data.member.ClassLeagueRepository
import com.furina.classtools.data.member.ClassLeagueResult
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.ClassMembersResult
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberCategoryBuilder
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.privacy.LocalDataStats
import com.furina.classtools.data.reminder.OneShotPlannedTime
import com.furina.classtools.data.reminder.OneShotScheduleResult
import com.furina.classtools.data.reminder.ReminderOneShot
import com.furina.classtools.data.reminder.ReminderOneShotFactory
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.account.OppoAccountText
import com.furina.classtools.data.account.OppoBindResult
import com.furina.classtools.data.account.OppoBindingsUiState
import com.furina.classtools.data.account.OppoUnbindResult
import com.furina.classtools.data.push.OppPushClick
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
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
import com.furina.classtools.ui.screens.jw.JwEntryScreen
import com.furina.classtools.ui.screens.map.CampusMapScreen
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsBuilder
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.screens.homework.HomeworkScreen
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
import com.furina.classtools.ui.screens.vote.VoteCreateScreen
import com.furina.classtools.ui.screens.vote.VoteDetailScreen
import com.furina.classtools.ui.screens.vote.VoteListScreen
import com.furina.classtools.ui.screens.vote.VoteMemberSource
import com.furina.classtools.ui.screens.tools.ToolPlaceholderScreen
import com.furina.classtools.ui.screens.water.WaterScreen
import com.furina.classtools.ui.theme.findActivity
import kotlinx.coroutines.launch
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    initialToolId: String? = null,
    onInitialToolConsumed: () -> Unit = {},
    /** OPPO PUSH 通知点击带入的通知 id（登录/进入主界面后进通知页并定位一次）。 */
    initialNoticeId: String? = null,
    onInitialNoticeConsumed: () -> Unit = {},
    /**
     * 独立工具页启动（金标 SharedView 的 Activity 目标）：直接把 NavHost 起点设为该工具，
     * 不再先渲染根页。为空 = 正常 MainActivity 根导航。
     */
    startToolId: String? = null,
    startToolPick: Boolean = false,
    /** true = ToolActivity 独立宿主：不渲染根 Tab、不显示容器变换覆盖层、返回起点工具时结束 Activity。 */
    standaloneTool: Boolean = false,
    /** 独立宿主从起点工具返回时调用（由 ToolActivity 执行 SharedView override + finish）。 */
    onCloseStandaloneTool: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()

    // 本机外观偏好：壁纸取色开关（Profile → 外观）。
    // 常用工具来自同步的 userdata v6 homeTools（原始值：空列表 = 小程序同款空态引导）。
    // 2026-11-22：先把跨端同步 id 换回本机 id（ft→fit、committee/bz→roster），
    // 否则网页/小程序写入的体测、名单会被 byId 过滤掉，换端后首页少两个工具。
    val sections by container.userDataRepository.currentSections.collectAsStateWithLifecycle()
    val sectionsQuickToolIds = remember(sections) {
        val array = sections["homeTools"] as? JsonArray
        array
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.mapNotNull { ToolCatalog.bySyncId(it)?.id }
            .orEmpty()
    }
    // 本机乐观值：AllTools/Home 编辑后立即同步到「工作台」，不等待 updateCurrent 回写 Flow。
    var optimisticQuickToolIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(sectionsQuickToolIds) {
        optimisticQuickToolIds = sectionsQuickToolIds
    }
    val quickToolIds = optimisticQuickToolIds ?: sectionsQuickToolIds

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

    // 工具卡 → 工具页的**共享容器变换**状态（M3 Expressive container transform）。
    val toolTransitionAnchors = remember { ToolTransitionAnchors() }
    var transitionTool by remember { mutableStateOf<AppTool?>(null) }
    var transitionStart by remember { mutableStateOf<Rect?>(null) }
    var transitionRoute by remember { mutableStateOf<String?>(null) }
    // 返回：覆盖层从全屏经同一弹簧缩回卡片（reverse）。pop 在动画开始前完成，
    // 底层始终是干净的根页；不再捕获界面快照（旧「快照遮底」补丁已移除）。
    var transitionReverse by remember { mutableStateOf(false) }
    var transitionPendingPop by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()

    // 通知点击带入的工具（hw/tt）：登录/进入主界面后消费一次。
    // 独立工具宿主已经直接以目标工具为起点，不能再 navigate 一次（会叠一层工具页）。
    LaunchedEffect(initialToolId, standaloneTool) {
        if (!standaloneTool && !initialToolId.isNullOrBlank()) {
            navController.navigate(Routes.tool(initialToolId))
            onInitialToolConsumed()
        }
    }

    // OPPO PUSH 通知点击：进入通知页并定位到该通知（NoticesScreen 消费后清空）。
    var pendingFocusNoticeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialNoticeId, standaloneTool) {
        if (standaloneTool) return@LaunchedEffect
        val noticeId = initialNoticeId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        pendingFocusNoticeId = noticeId
        val targetTool = OppPushClick.targetToolId(noticeId) ?: OppPushClick.NOTICE_TOOL_ID
        navController.navigate(Routes.tool(targetTool))
        onInitialNoticeConsumed()
    }

    fun selectTab(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index.coerceIn(0, TOP_LEVEL_COUNT - 1)) }
    }

    // 轻量 Toast 反馈（导出/同步结果）。
    fun toast(target: android.content.Context, message: String) {
        CtToastBus.show(message)
    }

    /** 打开工具；「设置」工具卡已移除，设置入口统一在「我的」页。 */
    fun openTool(tool: AppTool) {
        val route = when (tool.id) {
            "vote" -> Routes.VOTE
            else -> Routes.tool(tool.id)
        }
        // 有卡片锚点且未移除动画时，走容器变换「卡片长成页面」；否则直接导航。
        val anchor = toolTransitionAnchors.get(tool.id)
        if (!reduceMotion && anchor != null && transitionTool == null) {
            // 金标 SharedView：仅支持「工具卡 → 工具页」这种可直接映射到 ToolActivity 的路径。
            // 投票是独立 Routes.VOTE 页面，映射不到 tool/{id}，继续走现有 Compose 容器变换。
            val sharedViewStarted = tool.id != "vote" && SharedViewMotion.tryLaunchTool(
                activity = context.findActivity(),
                rootView = rootView,
                tool = tool,
                anchor = anchor,
                session = session,
            )
            if (sharedViewStarted) return
            transitionStart = anchor.bounds
            transitionTool = tool
            transitionRoute = route
            transitionReverse = false
            transitionPendingPop = false
        } else {
            navController.navigate(route)
        }
    }

    /**
     * 工具页返回：容器变换「页面收回卡片」。
     *
     * 顺序：1) 立即 `popBackStack()` 回根页（此后底层一直是干净的根页）；
     * 2) 覆盖层从全屏经同一弹簧缩回卡片 bounds 并淡出，结束撤层。
     *
     * 与旧实现（捕获界面快照再缩小）相比：不做 View.draw 快照，无「遮底」层，
     * WebView/SurfaceView 页面也不会出现快照空白。
     * 无锚点/移除动画/已有转场进行中时直接 pop。
     */
    fun popTool(toolId: String) {
        // 独立 ToolActivity：起点工具没有上一页，直接结束 Activity（由 SharedView override 回卡片）；
        // 工具内部再打开的页面（如 课表 → 地图）仍有上一页，正常 pop。
        if (standaloneTool) {
            if (navController.previousBackStackEntry == null) {
                onCloseStandaloneTool()
            } else {
                navController.popBackStack()
            }
            return
        }
        val tool = ToolCatalog.byId(toolId)
        val anchor = toolTransitionAnchors.get(toolId)
        if (reduceMotion || tool == null || anchor == null || transitionTool != null) {
            navController.popBackStack()
            return
        }
        // 先 pop：底层回到根页；pendingPop 仅作 onDone 兜底（pop 失败时强制再 pop 一次）。
        val popped = runCatching { navController.popBackStack() }.getOrDefault(false)
        transitionPendingPop = !popped
        transitionStart = anchor.bounds
        transitionTool = tool
        transitionRoute = null
        transitionReverse = true
    }

    // 独立 ToolActivity 的返回兜底：
    // - 工具内部 push 了其它页面（如 课表 → 地图）→ 先 pop；
    // - 已在起点工具 → 结束 Activity（SharedView override 回卡片）；
    // - 弹层/对话框自己的 BackHandler 后注册，优先消费，不会走到这里。
    if (standaloneTool) {
        BackHandler {
            if (navController.previousBackStackEntry == null) {
                onCloseStandaloneTool()
            } else {
                navController.popBackStack()
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
                    toast(context, CalendarIntentFactory.fallbackHint())
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
                startDestination = if (standaloneTool && !startToolId.isNullOrBlank()) {
                    Routes.tool(startToolId, startToolPick)
                } else {
                    Routes.ROOT
                },
                modifier = modifier.fillMaxSize(),
            ) {
        composable(
            route = Routes.ROOT,
            // 根页不参与 NavHost 的进出场：工具页的进入/返回统一由容器变换覆盖层负责
            // （覆盖层与目标页之间靠淡出交接），根页再叠加位移会出现两重动画。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            RootTabs(
                container = container,
                session = session,
                onSignOut = onSignOut,
                pagerState = pagerState,
                quickToolIds = quickToolIds,
                sections = sections,
                syncEnabled = syncEnabled,
                onSyncEnabledChange = { enabled ->
                    syncEnabled = enabled
                    scope.launch { runCatching { container.setSyncEnabled(enabled) } }
                },
                onClearLocalCache = {
                    scope.launch { runCatching { container.clearLocalCaches() } }
                },
                onOpenDataPrivacy = { navController.navigate(Routes.DATA_PRIVACY) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onOpenReminderSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
                onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
                onSelectTab = ::selectTab,
                onOpenTool = ::openTool,
                onHomeToolsChange = { ids ->
                    optimisticQuickToolIds = ids
                    scope.launch {
                        val saved = runCatching {
                            container.userDataRepository.updateCurrent(
                                HomeToolsEditor.apply(sections, ids),
                            )
                        }.isSuccess
                        if (saved) toast(context, "首页快捷已保存")
                    }
                },
                onOpenPowerPicker = { navController.navigate(Routes.tool("power", pick = true)) },
                onShareCard = { card -> shareCard = card },
                onScan = { navController.navigate(Routes.SCAN) },
            )
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
                navArgument(Routes.MAP_ROOM_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            // MainActivity 内：进入/返回由 ToolTransitionOverlay 负责，NavHost 不再叠加位移。
            // ToolActivity 独立宿主：起点工具没有栈内转场；工具内部 push（如 课表 → 地图）用统一页间转场。
            enterTransition = {
                if (standaloneTool) CtPageTransitions.harmonyPushEnter() else EnterTransition.None
            },
            exitTransition = {
                if (standaloneTool) CtPageTransitions.harmonyPushExit() else ExitTransition.None
            },
            popEnterTransition = {
                if (standaloneTool) CtPageTransitions.harmonyPopEnter() else EnterTransition.None
            },
            popExitTransition = {
                if (standaloneTool) CtPageTransitions.harmonyPopExit() else ExitTransition.None
            },
        ) { entry ->
            val toolId = entry.arguments?.getString(Routes.TOOL_ARG).orEmpty()
            val pickInTool = entry.arguments?.getBoolean(Routes.TOOL_PICK_ARG) == true
            // 工具页系统返回也播放「收回卡片」退场动画（屏幕内弹层自己的 BackHandler 后注册，优先处理）。
            BackHandler { popTool(toolId) }
            when (toolId) {
                "draw" -> {
                    val loadMembers: suspend () -> List<ClassMember> = remember(container) {
                        {
                            container.classMembersRepository.cached().ifEmpty {
                                (container.classMembersRepository.load() as? ClassMembersResult.Success)?.members.orEmpty()
                            }
                        }
                    }
                    DrawScreen(
                        onBack = { popTool(toolId) },
                        session = session,
                        userDataRepository = container.userDataRepository,
                        onExport = { payload -> launchExport(payload.toCardSpec(), com.furina.classtools.features.share.ShareKind.DRAW) },
                        lastResultStore = container.drawLastResultStore,
                        loadMembers = loadMembers,
                        onScan = { navController.navigate(Routes.SCAN) },
                    )
                }

                "group" -> {
                    // 班级名单：先读缓存；缓存为空时再触发一次加载（独立工具页可直接进入，不依赖先抽签）。
                    var roster by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
                    LaunchedEffect(Unit) {
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
                    GroupScreen(
                        onBack = { popTool(toolId) },
                        session = session,
                        userDataRepository = container.userDataRepository,
                        rosterMembers = roster,
                        lastResultStore = container.drawLastResultStore,
                        onExport = { payload -> launchExport(payload.toCardSpec(), com.furina.classtools.features.share.ShareKind.GROUP) },
                    )
                }

                "map" -> CampusMapScreen(
                    onBack = { popTool(toolId) },
                    onOpenTimetable = {
                        if (navController.previousBackStackEntry?.arguments?.getString(Routes.TOOL_ARG) == "tt") {
                            popTool(toolId)
                        } else {
                            navController.navigate(Routes.tool("tt"))
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
                        onBack = { popTool(toolId) },
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
                    HomeworkScreen(
                        onBack = { popTool(toolId) },
                        session = session,
                        marksRepository = container.marksRepository,
                        homeworkRepository = container.homeworkRepository,
                        onRemindHomework = { row -> remindTarget = row },
                    )
                    remindTarget?.let { row ->
                        HomeworkReminderDialog(
                            row = row,
                            onDismiss = { remindTarget = null },
                            onPick = { planned ->
                                remindTarget = null
                                val scheduler = oneShotScheduler
                                if (scheduler == null) {
                                    toast(context, "提醒服务不可用")
                                } else {
                                    scope.launch {
                                        val label = row.subject.ifBlank {
                                            row.content.lineSequence().firstOrNull().orEmpty()
                                        }.ifBlank { "作业" }
                                        when (
                                            val result = scheduler.scheduleHomeworkReminder(
                                                rowId = row.id,
                                                fireAtMillis = planned.fireAtMillis,
                                                label = label,
                                                targetAtMillis = planned.targetAtMillis,
                                            )
                                        ) {
                                            is OneShotScheduleResult.Scheduled -> toast(
                                                context,
                                                "已设置提醒：" + formatReminderTime(result.fireAtMillis),
                                            )
                                            OneShotScheduleResult.PermissionMissing ->
                                                toast(context, "请先开启通知权限")
                                            is OneShotScheduleResult.TooLate ->
                                                toast(context, "提醒时间已过，请选择更晚的时间")
                                            is OneShotScheduleResult.LimitReached ->
                                                toast(context, "提醒数量已达上限（${result.limit} 条）")
                                            OneShotScheduleResult.EmptyTarget ->
                                                toast(context, "该作业没有可提醒的时间")
                                            is OneShotScheduleResult.Failed ->
                                                toast(context, "设置失败：${result.message}")
                                        }
                                    }
                                }
                            },
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
                        onBack = { popTool(toolId) },
                        session = session,
                        marksRepository = container.marksRepository,
                        noticesRepository = container.noticesRepository,
                        rawAttachmentLoader = ApiNoticeRawAttachmentLoader(container.apiClient),
                        // v3 附件外置：图片 https 签名 URL 复用 data/ppt 的 OkHttp+BitmapFactory+LRU 加载器
                        imageLoader = container.pptImageLoader,
                        // OPPO PUSH 点击带入的通知：加载完成后定位并展开该通知详情
                        initialFocusNoticeId = pendingFocusNoticeId,
                        onFocusConsumed = { pendingFocusNoticeId = null },
                        onAddToCalendar = { group ->
                            if (NoticeCalendarMapper.eligible(group)) {
                                writeCalendarEvent(NoticeCalendarMapper.eventFor(group))
                            } else {
                                toast(context, "该通知没有可写入日程的时间")
                            }
                        },
                        onRemindNotice = { group -> remindTarget = group },
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
                        NoticeReminderDialog(
                            group = group,
                            onDismiss = { remindTarget = null },
                            onPick = { planned ->
                                remindTarget = null
                                val scheduler = oneShotScheduler
                                if (scheduler == null) {
                                    toast(context, "提醒服务不可用")
                                } else {
                                    scope.launch {
                                        when (
                                            val result = scheduler.scheduleNoticeReminder(
                                                groupId = group.id,
                                                fireAtMillis = planned.fireAtMillis,
                                                label = group.title,
                                                targetAtMillis = planned.targetAtMillis,
                                            )
                                        ) {
                                            is OneShotScheduleResult.Scheduled -> toast(
                                                context,
                                                "已设置提醒：" + formatReminderTime(result.fireAtMillis),
                                            )
                                            OneShotScheduleResult.PermissionMissing ->
                                                toast(context, "请先开启通知权限")
                                            is OneShotScheduleResult.TooLate ->
                                                toast(context, "提醒时间已过，请选择更晚的时间")
                                            is OneShotScheduleResult.LimitReached ->
                                                toast(context, "提醒数量已达上限（${result.limit} 条）")
                                            OneShotScheduleResult.EmptyTarget ->
                                                toast(context, "该通知没有可提醒的时间")
                                            is OneShotScheduleResult.Failed ->
                                                toast(context, "设置失败：${result.message}")
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                "tt" -> TimetableViewScreen(
                    onBack = { popTool(toolId) },
                    onOpenMap = { room -> navController.navigate(Routes.campusMap(room)) },
                    repository = container.timetableRepository,
                    loginRepository = container.timetableLoginRepository,
                    sessionSid = session.sid,
                    onAddToCalendar = { course, week, termStart ->
                        writeCalendarEvent(CourseCalendarMapper.eventFor(course, week, termStart))
                    },
                    onExportTimetable = { spec -> launchExport(spec, com.furina.classtools.features.share.ShareKind.TIMETABLE) },
                )

                "power" -> PowerScreen(
                    onBack = { popTool(toolId) },
                    repository = container.powerRepository,
                    directoryRepository = container.powerDirectoryRepository,
                    userDataRepository = container.userDataRepository,
                    session = session,
                    initialPickerOpen = pickInTool,
                )

                "water" -> WaterScreen(
                    onBack = { popTool(toolId) },
                    session = session,
                    userDataRepository = container.userDataRepository,
                    directoryRepository = container.powerDirectoryRepository,
                )

                "roster" -> RosterScreen(
                    onBack = { popTool(toolId) },
                    session = session,
                    classMembersRepository = container.classMembersRepository,
                    userDataRepository = container.userDataRepository,
                    classLeagueRepository = container.classLeagueRepository,
                )

                "roll" -> RollCallScreen(
                    onBack = { popTool(toolId) },
                    session = session,
                    classMembersRepository = container.classMembersRepository,
                    userDataRepository = container.userDataRepository,
                )

                "diag" -> DiagScreen(
                    onBack = { popTool(toolId) },
                    session = session,
                    repository = container.diagRepository,
                    deviceProbe = container.deviceProbe,
                    reporter = container.clientDiagReporter,
                    appVersion = com.furina.classtools.BuildConfig.VERSION_NAME,
                )

                "stats" -> StatsScreen(
                    onBack = { popTool(toolId) },
                    isAdmin = session.isAdmin,
                    repository = container.visitsRepository,
                )

                "ppt" -> PptListScreen(
                    onBack = { popTool(toolId) },
                    onOpenDetail = { deck -> navController.navigate(Routes.pptDetail(deck.id)) },
                    repository = container.pptRepository,
                    imageLoader = container.pptImageLoader,
                    session = session,
                    localStore = container.pptLocalStore,
                )

                "jw" -> JwEntryScreen(
                    sid = session.sid,
                    onBack = { popTool(toolId) },
                )

                "file" -> CloudSpaceScreen(
                    onBack = { popTool(toolId) },
                    repository = container.cloudSpaceRepository,
                )

                "express" -> ExpressScreen(
                    onBack = { popTool(toolId) },
                )

                else -> {
                    val tool = ToolCatalog.byId(toolId) ?: ToolCatalog.allEntry
                    ToolPlaceholderScreen(
                        tool = tool,
                        onBack = { popTool(toolId) },
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
            ReminderSettingsScreen(
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
        ) {
            VoteListScreen(
                repository = container.voteRepository,
                onBack = { navController.popBackStack() },
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
                memberSource = remember {
                    ContainerVoteMemberSource(
                        classMembersRepository = container.classMembersRepository,
                        classLeagueRepository = container.classLeagueRepository,
                    )
                },
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
            ToolTransitionOverlay(
                tool = transitionTool,
                startBounds = transitionStart,
                reverse = transitionReverse,
                onHalfway = {
                    if (transitionPendingPop) {
                        transitionPendingPop = false
                        navController.popBackStack()
                    } else {
                        transitionRoute?.let { route ->
                            transitionRoute = null
                            navController.navigate(route)
                        }
                    }
                },
                onDone = {
                    // 兜底：若开始返回时 pop 未成功（当前仍在工具页），动画结束后强制 pop，绝不把工具内部界面留在屏上。
                    if (transitionPendingPop) {
                        transitionPendingPop = false
                        runCatching { navController.popBackStack() }
                    }
                    transitionTool = null
                    transitionStart = null
                    transitionRoute = null
                    transitionReverse = false
                },
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
    onHomeToolsChange: (List<String>) -> Unit,
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
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    session = session,
                    quickToolIds = quickToolIds,
                    onOpenTool = onOpenTool,
                    homeworkRepository = container.homeworkRepository,
                    timetableRepository = container.timetableRepository,
                    marksRepository = container.marksRepository,
                    powerRepository = container.powerRepository,
                    noticesRepository = container.noticesRepository,
                    userDataRepository = container.userDataRepository,
                    onOpenAll = { onSelectTab(1) },
                    showNotificationPermissionBanner = showNotificationPermissionBanner,
                    onOpenPermissions = onOpenPermissions,
                    isPageActive = pagerState.currentPage == 0,
                    onHomeToolsChange = onHomeToolsChange,
                    onOpenPowerPicker = onOpenPowerPicker,
                    onScan = onScan,
                )

                1 -> AllToolsScreen(
                    onOpenTool = onOpenTool,
                    isAdmin = session.isAdmin,
                    userDataRepository = container.userDataRepository,
                    quickToolIds = quickToolIds,
                    onQuickToolsChange = onHomeToolsChange,
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

/** 单条通知提醒：选择提醒时间（截止/活动前）并交回 Lead 排程。 */
@Composable
private fun NoticeReminderDialog(
    group: com.furina.classtools.data.notices.NoticeGroupDto,
    onDismiss: () -> Unit,
    onPick: (OneShotPlannedTime) -> Unit,
) {
    val options = remember(group) { ReminderOneShot.noticePlannedTimes(group) }
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒我") },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text("该通知没有可提醒的时间（无截止/活动时间）")
                } else {
                    Text("选择提醒时间（本机通知）：")
                    options.forEach { planned ->
                        TextButton(onClick = { onPick(planned) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${planned.label} · ${formatReminderTime(planned.fireAtMillis)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 单条作业提醒：选择提醒时间（截止前）并交回 Lead 排程。 */
@Composable
private fun HomeworkReminderDialog(
    row: com.furina.classtools.data.homework.HomeworkRow,
    onDismiss: () -> Unit,
    onPick: (OneShotPlannedTime) -> Unit,
) {
    val options = remember(row) { ReminderOneShot.homeworkPlannedTimes(row) }
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒我") },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text("该作业没有可提醒的截止时间")
                } else {
                    Text("选择提醒时间（本机通知）：")
                    options.forEach { planned ->
                        TextButton(onClick = { onPick(planned) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${planned.label} · ${formatReminderTime(planned.fireAtMillis)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatReminderTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

/** 投票「指定成员」数据源：班委/全班来自 class_members，团员来自 class_league；仅 seat/name/badge。 */
private class ContainerVoteMemberSource(
    private val classMembersRepository: ClassMembersRepository,
    private val classLeagueRepository: ClassLeagueRepository,
) : VoteMemberSource {
    override suspend fun load(category: MemberCategory): List<MemberEntry> = when (category) {
        MemberCategory.COMMITTEE -> (classMembersRepository.load() as? ClassMembersResult.Success)
            ?.members?.let(MemberCategoryBuilder::committee).orEmpty()

        MemberCategory.LEAGUE -> (classLeagueRepository.load() as? ClassLeagueResult.Success)
            ?.members?.let(MemberCategoryBuilder::league).orEmpty()

        MemberCategory.ALL -> (classMembersRepository.load() as? ClassMembersResult.Success)
            ?.members?.let(MemberCategoryBuilder::all).orEmpty()
    }
}






