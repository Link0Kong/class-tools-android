package com.furina.classtools.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.furina.classtools.data.permission.PermissionType
import com.furina.classtools.data.reminder.ReminderSettings
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportOutcome
import com.furina.classtools.features.export.ExportPresenter
import com.furina.classtools.features.export.toCardSpec
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.adaptive.CtAdaptiveContent
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.dock.CtDockDestination
import com.furina.classtools.ui.components.dock.CtFloatingDock
import com.furina.classtools.ui.components.dock.CtPageTransitions
import com.furina.classtools.ui.components.dock.CtTabPager
import com.furina.classtools.ui.components.dock.rememberCtTabPagerState
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.data.DataPrivacyScreen
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.screens.feedback.FeedbackScreen
import com.furina.classtools.ui.screens.guide.UserGuideScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.me.MeStats
import com.furina.classtools.ui.screens.me.MeStatsBuilder
import com.furina.classtools.ui.screens.me.ReminderStatus
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.ui.screens.notices.ApiNoticeRawAttachmentLoader
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.screens.settings.PermissionSettingsScreen
import com.furina.classtools.ui.screens.settings.ReminderSettingsScreen
import com.furina.classtools.ui.screens.timetable.TimetableViewScreen
import com.furina.classtools.ui.screens.tools.ToolPlaceholderScreen
import com.furina.classtools.ui.screens.water.WaterScreen
import kotlinx.coroutines.launch
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
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 抽签结果 → 分组页的携带数据（共享状态，不放进导航参数）。
    var drawGroupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    // 本机外观偏好：壁纸取色开关（Profile → 外观）。
    val dynamicColor by container.appSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    // 常用工具来自同步的 userdata v6 homeTools（原始值：空列表 = 小程序同款空态引导）。
    val sections by container.userDataRepository.currentSections.collectAsStateWithLifecycle()
    val quickToolIds = remember(sections) {
        val array = sections["homeTools"] as? JsonArray
        array
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { ToolCatalog.byId(it) != null }
            .orEmpty()
    }

    // 云同步开关状态（我的页展示/切换）。
    var syncEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        syncEnabled = runCatching { container.isSyncEnabled() }.getOrDefault(true)
    }

    // 启动主界面时后台刷新：名单 / marks / userdata（TTL 内不重复请求）。
    LaunchedEffect(Unit) {
        runCatching { container.refreshCoreData() }
    }
    // 回前台刷新（marks 强拉由 refreshCoreData 内部 TTL/节流控制）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { runCatching { container.refreshCoreData() } }
    }

    // 根页 Pager 状态 hoist 到 NavHost 外层：进入工具页再返回时保持当前 Tab。
    val pagerState = rememberCtTabPagerState(pageCount = TOP_LEVEL_COUNT)

    // 通知点击带入的工具（hw/tt）：登录/进入主界面后消费一次。
    LaunchedEffect(initialToolId) {
        if (!initialToolId.isNullOrBlank()) {
            navController.navigate(Routes.tool(initialToolId))
            onInitialToolConsumed()
        }
    }

    fun selectTab(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index.coerceIn(0, TOP_LEVEL_COUNT - 1)) }
    }

    // 轻量 Toast 反馈（导出/同步结果）。
    fun toast(target: android.content.Context, message: String) {
        android.widget.Toast.makeText(target, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 打开工具；「设置」落到「我的」Tab（与小程序一致，不设独立占位页）。 */
    fun openTool(tool: AppTool) {
        if (tool.id == "settings") {
            selectTab(2)
        } else {
            navController.navigate(Routes.tool(tool.id))
        }
    }

    // 统一导出：渲染 → 保存相册（Android 10+）/ 分享兜底，Toast 反馈。
    fun launchExport(spec: ExportCardSpec) {
        scope.launch {
            when (val outcome = ExportPresenter.renderAndSave(context, spec)) {
                is ExportOutcome.Saved -> android.widget.Toast.makeText(
                    context,
                    "已保存到相册：${spec.file}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()

                is ExportOutcome.Share -> runCatching {
                    context.startActivity(Intent.createChooser(outcome.intent, "分享导出卡片"))
                }

                is ExportOutcome.Failed -> android.widget.Toast.makeText(
                    context,
                    "导出失败：${outcome.message}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** 写系统日历：有权限直接插入；无权限/无账号退回系统 ACTION_INSERT 界面。 */
    fun writeCalendarEvent(event: CalendarEvent?) {
        if (event == null) {
            toast(context, "该内容没有可写入的时间信息")
            return
        }
        scope.launch {
            when (val result = container.calendarWriter.write(event)) {
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
    }

    NavHost(
        navController = navController,
        startDestination = Routes.ROOT,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.ROOT) {
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
                dynamicColorEnabled = dynamicColor,
                onDynamicColorChange = { enabled ->
                    scope.launch { runCatching { container.appSettings.setDynamicColor(enabled) } }
                },
                onSelectTab = ::selectTab,
                onOpenTool = ::openTool,
                onHomeToolsChange = { ids ->
                    scope.launch {
                        val saved = runCatching {
                            container.userDataRepository.updateCurrent(
                                HomeToolsEditor.apply(sections, ids),
                            )
                        }.isSuccess
                        if (saved) toast(context, "首页快捷已保存")
                    }
                },
            )
        }

        composable(
            route = Routes.TOOL,
            arguments = listOf(navArgument(Routes.TOOL_ARG) { type = NavType.StringType }),
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
        ) { entry ->
            val toolId = entry.arguments?.getString(Routes.TOOL_ARG).orEmpty()
            when (toolId) {
                "draw" -> DrawScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    userDataRepository = container.userDataRepository,
                    onExport = { payload -> launchExport(payload.toCardSpec()) },
                    onGroup = { result ->
                        // 抽签结果（座号+姓名）→ 分组页「上一步抽签序号」来源
                        drawGroupMembers = result.entries.map { entry ->
                            GroupMember(seat = entry.number, name = entry.name.orEmpty())
                        }
                        navController.navigate(Routes.tool("group"))
                    },
                    onExportSheet = { payload -> launchExport(payload.toCardSpec()) },
                )

                "group" -> GroupScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    userDataRepository = container.userDataRepository,
                    prevMembers = drawGroupMembers,
                    onExport = { payload -> launchExport(payload.toCardSpec()) },
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
                        onBack = { navController.popBackStack() },
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
                        onExport = { payload -> launchExport(payload.toCardSpec()) },
                    )
                }

                "hw" -> HomeworkScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    marksRepository = container.marksRepository,
                    homeworkRepository = container.homeworkRepository,
                )

                "nt" -> NoticesScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    marksRepository = container.marksRepository,
                    noticesRepository = container.noticesRepository,
                    rawAttachmentLoader = ApiNoticeRawAttachmentLoader(container.apiClient),
                    onAddToCalendar = { group ->
                        if (NoticeCalendarMapper.eligible(group)) {
                            writeCalendarEvent(NoticeCalendarMapper.eventFor(group))
                        } else {
                            toast(context, "该通知没有可写入日程的时间")
                        }
                    },
                )

                "tt" -> TimetableViewScreen(
                    onBack = { navController.popBackStack() },
                    repository = container.timetableRepository,
                    onAddToCalendar = { course, week, termStart ->
                        writeCalendarEvent(CourseCalendarMapper.eventFor(course, week, termStart))
                    },
                )

                "power" -> PowerScreen(
                    onBack = { navController.popBackStack() },
                    repository = container.powerRepository,
                    userDataRepository = container.userDataRepository,
                    session = session,
                )

                "water" -> WaterScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    userDataRepository = container.userDataRepository,
                )

                "roster" -> RosterScreen(
                    onBack = { navController.popBackStack() },
                    session = session,
                    classMembersRepository = container.classMembersRepository,
                    userDataRepository = container.userDataRepository,
                )

                else -> {
                    val tool = ToolCatalog.byId(toolId) ?: ToolCatalog.allEntry
                    ToolPlaceholderScreen(
                        tool = tool,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable(
            route = Routes.DATA_PRIVACY,
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
        ) {
            DataPrivacyScreen(
                onBack = { navController.popBackStack() },
                session = session,
                repository = container.privacyRepository,
            )
        }

        composable(
            route = Routes.PERMISSIONS,
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
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
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
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
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
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
            enterTransition = { CtPageTransitions.pushEnter() },
            exitTransition = { CtPageTransitions.pushExit() },
            popEnterTransition = { CtPageTransitions.popEnter() },
            popExitTransition = { CtPageTransitions.popExit() },
        ) {
            UserGuideScreen(onBack = { navController.popBackStack() })
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
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onSelectTab: (Int) -> Unit,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit,
) {
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
        homeworkEnabled = reminderSettings.homeworkEnabled,
        courseEnabled = reminderSettings.courseEnabled,
        homeworkLeadDays = reminderSettings.homeworkLeadDays,
        courseLeadMinutes = reminderSettings.courseLeadMinutes,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            CtFloatingDock(
                destinations = destinations,
                selectedIndex = pagerState.currentPage,
                onSelect = onSelectTab,
                pagerOffsetFraction = pagerState.currentPageOffsetFraction,
            )
        },
    ) { innerPadding ->
        CtTabPager(
            pagerState = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
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
                    isPageActive = pagerState.currentPage == 0,
                    onHomeToolsChange = onHomeToolsChange,
                )

                1 -> CtAdaptiveContent(spec = spec) {
                    AllToolsScreen(
                        onOpenTool = onOpenTool,
                        userDataRepository = container.userDataRepository,
                    )
                }

                else -> CtAdaptiveContent(spec = spec) {
                    ProfileScreen(
                        session = session,
                        onSignOut = onSignOut,
                        syncEnabled = syncEnabled,
                        onSyncEnabledChange = onSyncEnabledChange,
                        onClearLocalCache = onClearLocalCache,
                        onOpenDataPrivacy = onOpenDataPrivacy,
                        onOpenPermissions = onOpenPermissions,
                        onOpenReminderSettings = onOpenReminderSettings,
                        meStats = meStats,
                        reminderStatus = reminderStatus,
                        onOpenFeedback = onOpenFeedback,
                        onOpenGuide = onOpenGuide,
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorChange = onDynamicColorChange,
                    )
                }
            }
        }
    }
}
