package com.furina.classtools.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.furina.classtools.AppContainer
import com.furina.classtools.data.fitrecs.FitRecsLoadResult
import com.furina.classtools.data.fitrecs.FitRecsWriteResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.ExportOutcome
import com.furina.classtools.features.export.ExportPresenter
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.export.toCardSpec
import com.furina.classtools.ui.screens.fit.FitScreen
import com.furina.classtools.ui.screens.group.GroupScreen
import com.furina.classtools.ui.components.CtBottomNav
import com.furina.classtools.ui.components.CtNavigationRail
import com.furina.classtools.ui.screens.all.AllToolsScreen
import com.furina.classtools.ui.screens.data.DataPrivacyScreen
import com.furina.classtools.ui.screens.draw.DrawScreen
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.ui.screens.notices.ApiNoticeRawAttachmentLoader
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.power.PowerScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.screens.timetable.TimetableViewScreen
import com.furina.classtools.ui.screens.water.WaterScreen
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.screens.tools.ToolPlaceholderScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 自适应导航：
 * - compact（<600dp）：底部 NavigationBar（工作台 / 全部 / 我的）
 * - expanded（>=600dp）：左侧 NavigationRail
 * - 工具页全屏 push，不显示导航栏（支持系统预测返回）
 */
@Composable
fun MainNavHost(
    container: AppContainer,
    session: Session,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 抽签结果 → 分组页的携带数据（共享状态，不放进导航参数；方案对比见 docs/api/FITRECS_DRAW_FLOW_IMPL.md §导航方案）
    var drawGroupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    // 本机外观偏好：壁纸取色开关（Profile → 外观）。
    val dynamicColor by container.appSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)

    // 轻量 Toast 反馈（导出/同步结果）。
    fun toast(target: android.content.Context, message: String) {
        android.widget.Toast.makeText(target, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 打开工具；「设置」不是独立页面，落到「我的」底部页签（避免占位页）。 */
    fun openTool(tool: AppTool) {
        if (tool.id == "settings") {
            navController.navigate(Routes.PROFILE) {
                launchSingleTop = true
            }
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isExpanded = LocalConfiguration.current.screenWidthDp >= 600
    val showNavigation = currentRoute in Routes.topLevel
    val destinations = TopLevelDestination.entries

    // 启动主界面时后台刷新：名单 / marks / userdata（TTL 内不重复请求）。
    LaunchedEffect(Unit) {
        runCatching { container.refreshCoreData() }
    }

    // 云同步开关状态（设置页展示用）。
    var syncEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        syncEnabled = runCatching { container.isSyncEnabled() }.getOrDefault(true)
    }

    // 回前台刷新（marks 强拉由 refreshCoreData 内部 TTL/节流控制）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { runCatching { container.refreshCoreData() } }
    }

    // 常用工具来自同步的 userdata v6 homeTools；未同步时回落默认 4 个。
    val sections by container.userDataRepository.currentSections.collectAsStateWithLifecycle()
    val quickToolIds = remember(sections) {
        val array = sections["homeTools"] as? JsonArray
        val ids = array
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { ToolCatalog.byId(it) != null }
            .orEmpty()
        ids.ifEmpty { ToolCatalog.defaultQuickIds }
    }

    fun navigateTopLevel(dest: TopLevelDestination) {
        // 注意：不要用组合期捕获的 currentRoute（Scaffold bottomBar 槽可能持有旧 lambda，
        // 导致「在全部页点工作台」被误判为当前页而静默吞掉）。点击时实时读取当前路由。
        val liveRoute = navController.currentBackStackEntry?.destination?.route
        if (liveRoute == dest.route) return
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showNavigation && !isExpanded) {
                CtBottomNav(
                    currentRoute = currentRoute,
                    destinations = destinations,
                    onSelect = ::navigateTopLevel,
                )
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNavigation && isExpanded) {
                CtNavigationRail(
                    currentRoute = currentRoute,
                    destinations = destinations,
                    onSelect = ::navigateTopLevel,
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        session = session,
                        quickToolIds = quickToolIds,
                        onOpenTool = ::openTool,
                        homeworkRepository = container.homeworkRepository,
                        timetableRepository = container.timetableRepository,
                        marksRepository = container.marksRepository,
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

                composable(Routes.ALL) {
                    AllToolsScreen(
                        onOpenTool = ::openTool,
                        userDataRepository = container.userDataRepository,
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        session = session,
                        onSignOut = onSignOut,
                        syncEnabled = syncEnabled,
                        onSyncEnabledChange = { enabled ->
                            syncEnabled = enabled
                            scope.launch { runCatching { container.setSyncEnabled(enabled) } }
                        },
                        onClearLocalCache = {
                            scope.launch { runCatching { container.clearLocalCaches() } }
                        },
                        onOpenDataPrivacy = { navController.navigate(Routes.DATA_PRIVACY) },
                        dynamicColorEnabled = dynamicColor,
                        onDynamicColorChange = { enabled ->
                            scope.launch {
                                runCatching { container.appSettings.setDynamicColor(enabled) }
                            }
                        },
                    )
                }

                composable(Routes.DATA_PRIVACY) {
                    DataPrivacyScreen(
                        onBack = { navController.popBackStack() },
                        session = session,
                        repository = container.privacyRepository,
                    )
                }

                composable(
                    route = Routes.TOOL,
                    arguments = listOf(navArgument(Routes.TOOL_ARG) { type = NavType.StringType }),
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
                        )

                        "tt" -> TimetableViewScreen(
                            onBack = { navController.popBackStack() },
                            repository = container.timetableRepository,
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
            }
        }
    }
}