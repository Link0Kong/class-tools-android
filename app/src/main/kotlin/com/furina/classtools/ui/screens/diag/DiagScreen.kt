@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.diag

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.furina.classtools.data.diag.AndroidDeviceProbe
import com.furina.classtools.data.diag.ClientDiagReporter
import com.furina.classtools.data.diag.ClientDiagRow
import com.furina.classtools.data.diag.DiagAgentInfo
import com.furina.classtools.data.diag.DiagAnalysis
import com.furina.classtools.data.diag.DiagAnalysisIndex
import com.furina.classtools.data.diag.DiagCheck
import com.furina.classtools.data.diag.DiagEvaluator
import com.furina.classtools.data.diag.DiagPayload
import com.furina.classtools.data.diag.DiagPollConfig
import com.furina.classtools.data.diag.DiagPollTick
import com.furina.classtools.data.diag.DiagPolling
import com.furina.classtools.data.diag.DiagProbeInput
import com.furina.classtools.data.diag.DiagRepository
import com.furina.classtools.data.diag.DiagResult
import com.furina.classtools.data.diag.DiagSanitizer
import com.furina.classtools.data.diag.DiagStatus
import com.furina.classtools.data.diag.DiagSummary
import com.furina.classtools.data.diag.DiagUiText
import com.furina.classtools.data.diag.FlowStep
import com.furina.classtools.data.diag.GatewayProbe
import com.furina.classtools.data.diag.RepairTask
import com.furina.classtools.data.diag.ServiceHealthGroup
import com.furina.classtools.data.diag.ServiceHealthRow
import com.furina.classtools.data.diag.displayMessage
import com.furina.classtools.data.diag.valueOrNull
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 诊断 v2.1 页面（契约 `_diag_task/CONTRACT_V2_1.md` §A/§B；所有已登录成员可见）。
 *
 * 与 v2 的差异：
 * - **AI 结论下挂**：`diag_analysis.related.health_ids` → 服务健康行下；`related.checks` → 设备检查行下；
 *   无关联的进「其它诊断」极简折叠区（默认收起，只显示条数）；删除独立「AI 分析」大卡与「修复任务」大卡；
 * - **折叠/展开**：折叠一行 = severity 点 + 标题≤22 字 + 状态胶囊 + `›`；点整行展开 cause/建议/flow/
 *   关联修复（`related.repair_id` 或 repair.analysis_id 匹配 repair_tasks）；
 * - **进展 10s**：页面可见（ON_RESUME）时 10s 轮询，进入/恢复前台立即拉；`running`/`fixed` 与 flow `run`
 *   步骤随刷新实时渲染；
 * - **设备信息最小化**：设备检查只展示系统/App/WebView/屏幕/内存/存储/网络/网关/时钟/本地数据，
 *   上报 device 仅白名单字段（见 [DiagPayload]/[DiagProbeInput.deviceJson]）。
 *
 * 分组/折叠/胶囊文案等纯逻辑在 [DiagAnalysisIndex]/[DiagUiText]/[DiagPolling]（可单测）。
 */
@Composable
fun DiagScreen(
    onBack: () -> Unit,
    session: Session,
    repository: DiagRepository,
    modifier: Modifier = Modifier,
    deviceProbe: AndroidDeviceProbe? = null,
    reporter: ClientDiagReporter? = null,
    appVersion: String = "",
    skipLoad: Boolean = false,
    initialGroups: List<ServiceHealthGroup> = emptyList(),
    initialAnalyses: List<DiagAnalysis> = emptyList(),
    initialRepairs: List<RepairTask> = emptyList(),
    initialAgent: DiagAgentInfo? = null,
    initialChecks: List<DiagCheck>? = null,
    initialMyReports: List<ClientDiagRow> = emptyList(),
    initialExpandedIds: Set<String> = emptySet(),
    initialOthersExpanded: Boolean = false,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    pollConfig: DiagPollConfig = DiagPollConfig(),
) {
    val scope = rememberCoroutineScope()
    val spec = rememberCtAdaptiveSpec()

    var loading by remember { mutableStateOf(!skipLoad) }
    var loadError by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(initialGroups) }
    var analyses by remember { mutableStateOf(initialAnalyses) }
    var repairs by remember { mutableStateOf(initialRepairs) }
    var agent by remember { mutableStateOf(initialAgent) }
    var myReports by remember { mutableStateOf(initialMyReports) }
    var summary by remember {
        mutableStateOf(if (initialChecks != null) DiagSummary(initialChecks, nowMillis()) else null)
    }
    var checking by remember { mutableStateOf(false) }
    var lastCheckAt by remember { mutableLongStateOf(summary?.ranAt ?: 0L) }
    var lastCheckNote by remember { mutableStateOf("") }
    var lastUpdatedAt by remember { mutableLongStateOf(0L) }
    var reportingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedIds by remember { mutableStateOf(initialExpandedIds) }
    var othersExpanded by remember { mutableStateOf(initialOthersExpanded) }
    var screenVisible by remember { mutableStateOf(true) }
    var autoRefresh by remember { mutableStateOf(true) }
    // DG-01：默认「简洁」——只保留状态摘要/设备摘要/服务异常行，隐藏代理、其它诊断与我的上报。
    var simpleMode by remember { mutableStateOf(true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { screenVisible = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { screenVisible = false }

    val agentOnline = agent?.online == true

    /** 服务健康行/设备检查行都只挂「注意/异常」行（正常行不显示 AI 行，契约 §A-4）。 */
    val index = remember(analyses, repairs, groups, summary) {
        val anomalyHealthIds = groups.flatMap { it.rows }
            .filter { it.displayStatus == DiagStatus.WARN || it.displayStatus == DiagStatus.FAIL }
            .map { it.id }
            .toSet()
        val anomalyCheckIds = summary?.checks.orEmpty()
            .filter { it.shouldReport }
            .map { it.id }
            .toSet()
        DiagAnalysisIndex.build(
            analyses = analyses,
            repairs = repairs,
            anomalyHealthIds = anomalyHealthIds,
            anomalyCheckIds = anomalyCheckIds,
        )
    }

    val autoFixable = remember(analyses) { DiagUiText.autoFixableCount(analyses) }
    val fixedCount = remember(analyses) { DiagUiText.fixedCount(analyses) }
    val anomalyCount = summary?.anomalies?.size ?: 0
    val lastUpdatedLabel = remember(lastUpdatedAt) { DiagUiText.lastUpdatedText(lastUpdatedAt) }

    fun reportCodeFor(check: DiagCheck, input: DiagProbeInput): String {
        if (check.status != DiagStatus.FAIL) return ""
        return when (check.id) {
            "gateway" -> if (input.gatewayLatencyMs == null) "gateway_unreachable" else "gateway_slow"
            "network" -> "network_offline"
            "memory" -> "memory_low"
            "storage" -> "storage_low"
            "clock" -> "clock_skew"
            "local_data" -> "local_data_unwritable"
            "webview" -> "webview_missing"
            else -> ""
        }
    }

    /** 异常检查上报行（detail.check=检查 id，agent 据此生成 related.checks）。 */
    fun reportRowFor(check: DiagCheck, input: DiagProbeInput): JsonObject = DiagPayload.fromCheck(
        check = check,
        sid = session.sid,
        appVersion = appVersion,
        device = input.deviceJson(),
        now = nowMillis(),
        latencyMs = input.gatewayLatencyMs.takeIf { check.id == "gateway" },
        storagePct = input.storagePct.takeIf { check.id == "storage" },
        clockSkewMs = input.clockSkewMs.takeIf { check.id == "clock" },
        code = reportCodeFor(check, input),
    )

    /** 跑一遍本机设备检查（含网关计时），并自动上报注意/异常项。 */
    suspend fun runDeviceChecks() {
        checking = true
        try {
            val now = nowMillis()
            val base = deviceProbe?.collectBase(now)
                ?: DiagProbeInput(appVersion = appVersion, nowMillis = now)
            val gateway = repository.measureGateway()
            val probe: GatewayProbe? = gateway.valueOrNull()
            val input = base.copy(
                gatewayLatencyMs = probe?.latencyMs,
                gatewayError = if (gateway is DiagResult.Failure) {
                    DiagSanitizer.sanitizeDisplay(gateway.error.displayMessage, 120)
                } else {
                    ""
                },
                serverEpochMs = probe?.serverEpochMs,
            )
            val checks = DiagEvaluator.evaluate(input)
            summary = DiagSummary(checks = checks, ranAt = now)
            lastCheckAt = now
            val anomalies = checks.filter { it.shouldReport }
            var sent = 0
            var queued = 0
            var skipped = 0
            if (reporter != null && session.sid.isNotBlank() && session.token.isNotBlank()) {
                reporter.beginBatch()
                anomalies.take(3).forEach { check ->
                    val row = reportRowFor(check, input)
                    when (reporter.report(row)) {
                        com.furina.classtools.data.diag.DiagReportOutcome.Sent -> sent += 1
                        com.furina.classtools.data.diag.DiagReportOutcome.Queued -> queued += 1
                        com.furina.classtools.data.diag.DiagReportOutcome.Skipped -> skipped += 1
                    }
                }
            }
            lastCheckNote = when {
                anomalies.isEmpty() -> "全部通过"
                sent > 0 -> "已自动上报 $sent 条异常"
                queued > 0 -> "$queued 条异常已排队，联网后补报"
                skipped > 0 -> "异常已存在，稍后自动去重上报"
                else -> ""
            }
        } finally {
            checking = false
        }
    }

    /** 网络刷新：服务健康 / AI 分析 / 修复任务 / agent 状态 / 我的上报。 */
    suspend fun refreshRemote() {
        if (groups.isEmpty()) loading = true
        val errors = ArrayList<String>()
        var anySuccess = false
        repository.loadServiceHealth().let { result ->
            when (result) {
                is DiagResult.Success -> {
                    anySuccess = true
                    groups = com.furina.classtools.data.diag.DiagParser.groupServiceHealth(result.value, nowMillis())
                }
                is DiagResult.Failure -> errors.add(result.error.displayMessage)
            }
        }
        repository.loadAnalyses().let { result ->
            if (result is DiagResult.Success) {
                anySuccess = true
                analyses = result.value
            } else {
                errors.add((result as DiagResult.Failure).error.displayMessage)
            }
        }
        repository.loadRepairs().let { result ->
            if (result is DiagResult.Success) {
                anySuccess = true
                repairs = result.value
            } else {
                errors.add((result as DiagResult.Failure).error.displayMessage)
            }
        }
        repository.agentInfo().let { result ->
            if (result is DiagResult.Success) {
                anySuccess = true
                agent = result.value
            }
        }
        repository.loadMyReports().let { result ->
            if (result is DiagResult.Success) myReports = result.value
        }
        if (anySuccess) lastUpdatedAt = nowMillis()
        if (errors.isNotEmpty() && groups.isEmpty()) {
            loadError = errors.first()
        } else if (errors.isEmpty()) {
            loadError = ""
        }
        loading = false
    }

    /** 进入/恢复前台：立即拉一次；仅可见时每 10s 轮询；隐藏/离开/暂停即停。 */
    LaunchedEffect(skipLoad, screenVisible, autoRefresh, pollConfig.intervalMs) {
        if (skipLoad) {
            loading = false
            return@LaunchedEffect
        }
        if (!screenVisible || !autoRefresh) return@LaunchedEffect
        runCatching { reporter?.drainPending() }
        refreshRemote()
        runCatching { runDeviceChecks() }
        while (true) {
            val tick = DiagPolling.afterTick(visible = true, config = pollConfig)
            val delayMs = (tick as? DiagPollTick.Wait)?.delayMs ?: break
            delay(delayMs)
            if (!screenVisible) break
            runCatching {
                refreshRemote()
                runDeviceChecks()
            }
        }
    }

    fun reportManually(check: DiagCheck) {
        val reporterOrNull = reporter ?: return
        if (session.sid.isBlank() || session.token.isBlank()) {
            CtToastBus.show("未登录，无法上报")
            return
        }
        reportingIds = reportingIds + check.id
        scope.launch {
            val base = deviceProbe?.collectBase(nowMillis())
                ?: DiagProbeInput(appVersion = appVersion, nowMillis = nowMillis())
            val message = when (reporterOrNull.report(reportRowFor(check, base))) {
                com.furina.classtools.data.diag.DiagReportOutcome.Sent -> "已上报"
                com.furina.classtools.data.diag.DiagReportOutcome.Queued -> "网络不可用，已排队补报"
                com.furina.classtools.data.diag.DiagReportOutcome.Skipped -> "10 分钟内已上报过，已去重"
            }
            reportingIds = reportingIds - check.id
            CtToastBus.show(message)
        }
    }

    fun toggleExpanded(id: String) {
        expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding(),
            ) {
                ctTopBarRaw(
                    subtitle = null,
                    title = "诊断",
                    onBack = onBack,
                    actions = {
                        // 顶栏动作（代理胶囊 / 简洁·详细 / 暂停 / 刷新）按 8vp 控件间距排开（2026-11-21）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
                        ) {
                            if (agent?.online == true) {
                                CtPill(
                                    text = "代理在线",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else if (agent != null) {
                                CtPill(
                                    text = "代理离线",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            TextButton(onClick = { simpleMode = !simpleMode }) {
                                Text(if (simpleMode) "详细" else "简洁")
                            }
                            IconButton(onClick = { autoRefresh = !autoRefresh }) {
                                Icon(
                                    imageVector = if (autoRefresh) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (autoRefresh) "暂停自动刷新" else "恢复自动刷新",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                if (skipLoad) return@IconButton
                                scope.launch {
                                    refreshRemote()
                                    runDeviceChecks()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新诊断数据",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )

                if (checking) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                SummaryStrip(
                    anomalyCount = anomalyCount,
                    autoFixable = autoFixable,
                    fixedCount = fixedCount,
                    lastUpdated = lastUpdatedLabel,
                    horizontalPadding = spec.horizontalPadding,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spec.horizontalPadding),
                    // 卡片之间按「卡片间距」取 12vp（原 10vp 不在 4/8 栅格上，2026-11-21）
                    verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
                ) {
                    if (loading) {
                        LoadingRow()
                    }
                    if (loadError.isNotBlank() && groups.isEmpty()) {
                        CtToolCard {
                            Text(
                                text = "加载失败：${DiagSanitizer.sanitizeDisplay(loadError)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    if (!simpleMode) {
                        AgentCard(agent = agent, nowMillis = nowMillis())
                    }
                    DeviceDiagCard(
                        summary = summary,
                        checking = checking,
                        lastCheckAt = lastCheckAt,
                        lastCheckNote = lastCheckNote,
                        index = index,
                        agentOnline = agentOnline,
                        expandedIds = expandedIds,
                        onToggle = ::toggleExpanded,
                        onRecheck = {
                            if (!skipLoad) scope.launch { runDeviceChecks() }
                        },
                        onReport = ::reportManually,
                        reportable = reporter != null && session.sid.isNotBlank(),
                        reportingIds = reportingIds,
                        nowMillis = nowMillis(),
                    )
                    ServiceHealthCard(
                        groups = groups,
                        index = index,
                        agentOnline = agentOnline,
                        expandedIds = expandedIds,
                        onToggle = ::toggleExpanded,
                        nowMillis = nowMillis(),
                        onlyProblems = simpleMode,
                    )
                    if (!simpleMode) {
                        OthersSection(
                            index = index,
                            expanded = othersExpanded,
                            onToggleExpanded = { othersExpanded = !othersExpanded },
                            expandedIds = expandedIds,
                            onToggleAnalysis = ::toggleExpanded,
                            agentOnline = agentOnline,
                            nowMillis = nowMillis(),
                        )
                        MyReportsCard(reports = myReports, nowMillis = nowMillis())
                    }
                    Spacer(modifier = Modifier.height(CtSpace.xxl))
                }
            }
        }
    }
}

// =============================================================================
// 顶部摘要 / 卡片 / 折叠行
// =============================================================================

/** 顶部极简摘要：N 项异常 · 可自动修复 N · 已修复 M ｜ 最后更新 HH:mm:ss。 */
@Composable
private fun SummaryStrip(
    anomalyCount: Int,
    autoFixable: Int,
    fixedCount: Int,
    lastUpdated: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    CtToolCard(
        // 摘要卡与下方列表之间补 12vp：此前两张卡直接贴在一起（2026-11-21）
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = CtSpace.sm),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (anomalyCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$anomalyCount 项异常",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (anomalyCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            val extra = buildList {
                if (autoFixable > 0) add("可自动修复 $autoFixable")
                if (fixedCount > 0) add("已修复 $fixedCount")
            }
            if (extra.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = extra.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (lastUpdated.isNotBlank()) {
            // 主次文本之间留 4vp，此前「最后更新」紧贴上面一行（2026-11-21）
            Spacer(modifier = Modifier.height(CtSpace.xxs))
            Text(
                text = "最后更新 $lastUpdated",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CtType.microSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    CtToolCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = "加载诊断数据…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagCard(
    title: String,
    status: DiagStatus? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CtToolCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 标题 / 状态胶囊 / 右上角操作之间统一 8vp（2026-11-21）
            horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (status != null) StatusPill(status)
            trailing?.invoke()
        }
        // 标题行与正文之间按「标题—正文」留 8vp：此前正文直接贴在标题下（2026-11-21）
        Spacer(modifier = Modifier.height(CtSpace.controlGap))
        Column(verticalArrangement = Arrangement.spacedBy(CtSpace.controlGap)) {
            content()
        }
    }
}

@Composable
private fun StatusPill(status: DiagStatus, label: String = status.label) {
    val (container, content) = statusColors(status)
    CtPill(text = label, containerColor = container, contentColor = content)
}

@Composable
private fun statusColors(status: DiagStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        DiagStatus.PASS -> scheme.primaryContainer to scheme.onPrimaryContainer
        DiagStatus.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DiagStatus.FAIL -> scheme.errorContainer to scheme.onErrorContainer
        DiagStatus.INFO -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

private fun statusLabelOf(status: DiagStatus): String = when (status) {
    DiagStatus.PASS -> "通过"
    DiagStatus.WARN -> "注意"
    DiagStatus.FAIL -> "异常"
    DiagStatus.INFO -> "信息"
}

@Composable
private fun severityDotColor(analysis: DiagAnalysis): Color = when (analysis.severityLevel) {
    com.furina.classtools.data.diag.DiagLevel.ERROR -> MaterialTheme.colorScheme.error
    com.furina.classtools.data.diag.DiagLevel.WARN -> MaterialTheme.colorScheme.tertiary
    com.furina.classtools.data.diag.DiagLevel.INFO -> MaterialTheme.colorScheme.secondary
}

/** 一行 AI 折叠行：severity 点 + 标题≤22 字 + 可自动修复徽标 + 状态胶囊 + `›`；点整行展开。 */
@Composable
private fun AiAnalysisRow(
    analysis: DiagAnalysis,
    repair: RepairTask?,
    expanded: Boolean,
    onToggle: () -> Unit,
    nowMillis: Long,
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "diag-chevron",
    )
    val pill = DiagUiText.pillLabel(analysis)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(severityDotColor(analysis), CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = DiagUiText.collapseTitle(analysis.title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (DiagUiText.autoFixable(analysis)) {
                Text(
                    text = "⚙ 可自动修复",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = CtType.microSize),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            AiStatusPill(label = pill)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronAngle),
            )
        }
        if (expanded) {
            AnalysisDetail(analysis = analysis, repair = repair, nowMillis = nowMillis)
        }
    }
}

@Composable
private fun AiStatusPill(label: String) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (label) {
        "已修复" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "修复中" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "修复失败" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    CtPill(text = label, containerColor = container, contentColor = content)
}

/** 展开态：原因 / 建议（≤4）/ flow 时间线（≤8）/ 关联修复。 */
@Composable
private fun AnalysisDetail(analysis: DiagAnalysis, repair: RepairTask?, nowMillis: Long) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (analysis.cause.isNotBlank()) {
            Text(
                text = DiagSanitizer.sanitizeDisplay(analysis.cause, 400),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        analysis.suggestions.take(4).forEach { suggestion ->
            Text(
                text = "· " + DiagSanitizer.sanitizeDisplay(suggestion, 200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (analysis.flow.isNotEmpty()) {
            FlowTimeline(steps = analysis.flow, nowMillis = nowMillis)
        }
        if (repair != null) {
            RelatedRepairBlock(repair = repair, nowMillis = nowMillis)
        }
    }
}

@Composable
private fun FlowTimeline(steps: List<FlowStep>, nowMillis: Long) {
    Column(
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.take(8).forEach { step ->
            val running = step.running
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = if (running) "◉" else "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DiagSanitizer.sanitizeDisplay(step.text, 200),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    val meta = buildList {
                        val stateText = DiagUiText.flowStateText(step.state)
                        if (stateText.isNotBlank()) add(stateText)
                        if (step.ts > 0) add(relativeTime(step.ts, nowMillis))
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 关联修复：action / 状态 / 结果摘要 / 时间（contract §A-3）。 */
@Composable
private fun RelatedRepairBlock(repair: RepairTask, nowMillis: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "修复 ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = repair.action.ifBlank { "未知动作" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CtPill(text = DiagUiText.repairStatusLabel(repair.status))
        }
        if (repair.result.isNotBlank()) {
            Text(
                text = "结果：" + DiagSanitizer.sanitizeDisplay(repair.result, 200),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val timeText = buildList {
            if (repair.createdAt > 0) add("开始 " + relativeTime(repair.createdAt, nowMillis))
            if (repair.finishedAt > 0) add("完成 " + relativeTime(repair.finishedAt, nowMillis))
        }.joinToString(" · ")
        if (timeText.isNotBlank()) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CtType.microSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/** 异常项还没有 AI 结论时的灰字提示（在线=排队，离线=留存待开机）。 */
@Composable
private fun PendingAiLine(agentOnline: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = DiagUiText.pendingText(agentOnline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** 异常行下的 AI 结论（折叠行列表）或排队提示。 */
@Composable
private fun AiRowsUnderAnomaly(
    analyses: List<DiagAnalysis>,
    index: DiagAnalysisIndex,
    agentOnline: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    nowMillis: Long,
) {
    if (analyses.isEmpty()) {
        PendingAiLine(agentOnline)
    } else {
        analyses.forEach { analysis ->
            AiAnalysisRow(
                analysis = analysis,
                repair = index.repairFor(analysis),
                expanded = analysis.id in expandedIds,
                onToggle = { onToggle(analysis.id) },
                nowMillis = nowMillis,
            )
        }
    }
}

// =============================================================================
// 卡片：代理 / 设备诊断 / 服务健康 / 其它诊断 / 我的上报
// =============================================================================

@Composable
private fun AgentCard(agent: DiagAgentInfo?, nowMillis: Long) {
    DiagCard(
        title = "本机代理",
        status = when {
            agent == null -> DiagStatus.INFO
            agent.online -> DiagStatus.PASS
            else -> DiagStatus.FAIL
        },
    ) {
        when {
            agent == null -> Text(
                text = "状态未知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            agent.online -> Text(
                text = buildString {
                    append("在线")
                    if (agent.seenAt > 0) append(" · 心跳 ").append(relativeTime(agent.seenAt, nowMillis))
                    if (agent.host.isNotBlank()) append(" · ").append(agent.host)
                    if (agent.version.isNotBlank()) append(" · v").append(agent.version)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                text = buildString {
                    append("离线")
                    if (agent.seenAt > 0) append(" · 最后心跳 ").append(relativeTime(agent.seenAt, nowMillis))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceDiagCard(
    summary: DiagSummary?,
    checking: Boolean,
    lastCheckAt: Long,
    lastCheckNote: String,
    index: DiagAnalysisIndex,
    agentOnline: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onRecheck: () -> Unit,
    onReport: (DiagCheck) -> Unit,
    reportable: Boolean,
    reportingIds: Set<String>,
    nowMillis: Long,
) {
    DiagCard(
        title = "设备诊断",
        status = summary?.overall ?: DiagStatus.INFO,
        trailing = {
            TextButton(onClick = onRecheck, enabled = !checking) {
                Text(if (checking) "检测中…" else "重新检测")
            }
        },
    ) {
        if (summary == null) {
            Text(
                text = "尚未检测",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtPill(
                    text = "通过 ${summary.passCount}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                CtPill(
                    text = "注意 ${summary.warnCount}",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                CtPill(
                    text = "异常 ${summary.failCount}",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            summary.checks.forEach { check ->
                DeviceCheckBlock(
                    check = check,
                    linkedAnalyses = index.analysesForCheck(check.id),
                    index = index,
                    agentOnline = agentOnline,
                    expandedIds = expandedIds,
                    onToggle = onToggle,
                    onReport = onReport,
                    reportable = reportable && check.shouldReport,
                    reporting = check.id in reportingIds,
                    nowMillis = nowMillis,
                )
            }
            if (lastCheckAt > 0) {
                Text(
                    text = buildString {
                        append("上次检测 ")
                        append(clockText(lastCheckAt))
                        if (lastCheckNote.isNotBlank()) append(" · ").append(lastCheckNote)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceCheckBlock(
    check: DiagCheck,
    linkedAnalyses: List<DiagAnalysis>,
    index: DiagAnalysisIndex,
    agentOnline: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onReport: (DiagCheck) -> Unit,
    reportable: Boolean,
    reporting: Boolean,
    nowMillis: Long,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColors(check.status).first, CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(check.value)
                        if (check.hint.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(check.hint)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reportable) {
                TextButton(onClick = { onReport(check) }, enabled = !reporting) {
                    Text(if (reporting) "…" else "上报", style = MaterialTheme.typography.labelSmall)
                }
                // 「上报」与状态胶囊是两个相邻控件，留 8vp 避免贴在一起（2026-11-21）
                Spacer(modifier = Modifier.width(CtSpace.controlGap))
            }
            StatusPill(check.status, statusLabelOf(check.status))
        }
        if (check.shouldReport) {
            AiRowsUnderAnomaly(
                analyses = linkedAnalyses,
                index = index,
                agentOnline = agentOnline,
                expandedIds = expandedIds,
                onToggle = onToggle,
                nowMillis = nowMillis,
            )
        }
    }
}

@Composable
private fun ServiceHealthCard(
    groups: List<ServiceHealthGroup>,
    index: DiagAnalysisIndex,
    agentOnline: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    nowMillis: Long,
    onlyProblems: Boolean = false,
) {
    val visibleGroups = if (onlyProblems) {
        groups.mapNotNull { group ->
            val rows = group.rows.filter { it.displayStatus != DiagStatus.PASS }
            if (rows.isEmpty()) null else group.copy(rows = rows)
        }
    } else {
        groups
    }
    DiagCard(
        title = "服务健康",
        status = when {
            visibleGroups.any { it.worstStatus == DiagStatus.FAIL } -> DiagStatus.FAIL
            visibleGroups.any { it.worstStatus == DiagStatus.WARN } -> DiagStatus.WARN
            visibleGroups.isNotEmpty() -> DiagStatus.PASS
            else -> DiagStatus.INFO
        },
    ) {
        if (visibleGroups.isEmpty()) {
            Text(
                text = "暂无服务健康数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visibleGroups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(group.worstStatus, statusLabelOf(group.worstStatus))
                }
                group.rows.forEach { row ->
                    ServiceHealthRowBlock(
                        row = row,
                        linkedAnalyses = index.analysesForHealth(row.id),
                        index = index,
                        agentOnline = agentOnline,
                        expandedIds = expandedIds,
                        onToggle = onToggle,
                        nowMillis = nowMillis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceHealthRowBlock(
    row: ServiceHealthRow,
    linkedAnalyses: List<DiagAnalysis>,
    index: DiagAnalysisIndex,
    agentOnline: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    nowMillis: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name.ifBlank { row.id },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColors(row.displayStatus).second,
                fontWeight = FontWeight.Medium,
            )
        }
        if (row.detail.isNotBlank()) {
            Text(
                text = DiagSanitizer.sanitizeDisplay(row.detail, 240),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CtType.microSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val checkedAt = row.checkedAt.takeIf { it.isNotBlank() }
        if (checkedAt != null) {
            val millis = parseTimeMillisSafe(checkedAt) ?: 0L
            val relative = if (millis > 0) relativeTime(millis, nowMillis) else ""
            Text(
                text = "检查于 " + (relative.ifBlank { clockText(millis) }).ifBlank { checkedAt },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CtType.microSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        val anomaly = row.displayStatus == DiagStatus.WARN || row.displayStatus == DiagStatus.FAIL
        if (anomaly) {
            AiRowsUnderAnomaly(
                analyses = linkedAnalyses,
                index = index,
                agentOnline = agentOnline,
                expandedIds = expandedIds,
                onToggle = onToggle,
                nowMillis = nowMillis,
            )
        }
    }
}

/** 「其它诊断」极简折叠区：无关联 AI 结论 + 历史无主修复；默认收起只显示条数。 */
@Composable
private fun OthersSection(
    index: DiagAnalysisIndex,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    expandedIds: Set<String>,
    onToggleAnalysis: (String) -> Unit,
    agentOnline: Boolean,
    nowMillis: Long,
) {
    if (index.othersCount == 0) return
    CtToolCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .semantics { heading() }
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "其它诊断",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CtPill(
                text = "${index.othersCount}",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                index.others.forEach { analysis ->
                    AiAnalysisRow(
                        analysis = analysis,
                        repair = index.repairFor(analysis),
                        expanded = analysis.id in expandedIds,
                        onToggle = { onToggleAnalysis(analysis.id) },
                        nowMillis = nowMillis,
                    )
                }
                index.orphanRepairs.forEach { repair ->
                    RelatedRepairBlock(repair = repair, nowMillis = nowMillis)
                }
                if (index.others.isEmpty() && index.orphanRepairs.isNotEmpty() && !agentOnline) {
                    Text(
                        text = DiagUiText.pendingText(false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyReportsCard(reports: List<ClientDiagRow>, nowMillis: Long) {
    DiagCard(
        title = "我的上报",
        status = if (reports.isEmpty()) DiagStatus.INFO else DiagStatus.PASS,
    ) {
        if (reports.isEmpty()) {
            Text(
                text = "暂无本机上报记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            reports.take(8).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DiagSanitizer.sanitizeDisplay(row.message, 160).ifBlank { "(无描述)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOf(row.kind, row.level, relativeTime(row.ts, nowMillis))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.status.isNotBlank()) CtPill(text = reportStatusText(row.status))
                }
            }
        }
    }
}

private fun reportStatusText(status: String): String = when (status.trim().lowercase()) {
    "new" -> "待分析"
    "analyzed" -> "已分析"
    "ignored" -> "已忽略"
    else -> status
}

// =============================================================================
// 工具函数
// =============================================================================

internal fun relativeTime(ts: Long, nowMillis: Long): String {
    if (ts <= 0) return ""
    val diff = nowMillis - ts
    return when {
        diff < 0 -> clockText(ts)
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> "${diff / 86_400_000} 天前"
    }
}

internal fun clockText(ts: Long, relative: String = ""): String {
    if (ts <= 0) return relative
    val text = runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }.getOrDefault("")
    return listOf(text, relative).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun parseTimeMillisSafe(text: String): Long? =
    com.furina.classtools.data.diag.DiagParser.parseTimeMillis(text)

// =============================================================================
// 预览（android preview render：折叠态 + 展开态）
// =============================================================================

private const val PREVIEW_NOW: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08

private val previewSession = Session(
    sid = "2024000001",
    name = "开发者",
    token = "preview-token",
)

internal object PreviewDiagRepository : DiagRepository {
    override suspend fun loadServiceHealth(): DiagResult<List<ServiceHealthRow>> =
        DiagResult.Success(previewHealthRows())

    override suspend fun loadAnalyses(): DiagResult<List<DiagAnalysis>> =
        DiagResult.Success(previewAnalyses())

    override suspend fun loadRepairs(): DiagResult<List<RepairTask>> =
        DiagResult.Success(previewRepairs())

    override suspend fun agentInfo(): DiagResult<DiagAgentInfo> =
        DiagResult.Success(DiagAgentInfo(online = true, seenAt = PREVIEW_NOW - 60_000, host = "local-windows", version = "1.0"))

    override suspend fun measureGateway(): DiagResult<GatewayProbe> =
        DiagResult.Success(GatewayProbe(latencyMs = 4126L, serverEpochMs = PREVIEW_NOW + 500))

    override suspend fun reportClientDiag(row: JsonObject): com.furina.classtools.data.network.ApiResult<Unit> =
        com.furina.classtools.data.network.ApiResult.Success(Unit)

    override suspend fun loadMyReports(): DiagResult<List<ClientDiagRow>> =
        DiagResult.Success(previewReports())
}

internal fun previewHealthRows(): List<ServiceHealthRow> = listOf(
    ServiceHealthRow("site_static", "站点资源", "static", "warn", "网页已按备案安排暂停", "", "2026-11-06T09:56:00+08:00"),
    ServiceHealthRow("site_tls", "站点证书", "static", "warn", "网页已按备案安排暂停", "", "2026-11-06T09:56:00+08:00"),
    ServiceHealthRow("dns_domain", "域名解析", "cloud", "warn", "不可解析（已暂停，符合预期）", "{\"paused\":true}", "2026-11-06T09:56:00+08:00"),
    ServiceHealthRow("cloudbase", "云端数据库", "cloud", "ok", "HTTP 200 · 186ms", "{}", "2026-11-06T09:57:00+08:00"),
    ServiceHealthRow("tt_cloud", "课表云端数据", "cloud", "ok", "快照 2 小时前更新", "{}", "2026-11-06T09:57:00+08:00"),
    ServiceHealthRow("cloud_power_sync", "电费云同步", "cloud", "ok", "最近同步 1 小时前", "{}", "2026-11-06T09:57:00+08:00"),
    ServiceHealthRow("cloud_xgj_sync", "学工记云同步", "cloud", "warn", "最近作业 4 小时前", "{}", "2026-11-06T09:57:00+08:00"),
    ServiceHealthRow("cloud_health_probe", "云端巡检", "cloud", "ok", "本轮完成 · 18 行 · 820ms", "{}", "2026-11-06T09:57:00+08:00"),
    ServiceHealthRow("qqflow", "QQ 通知流", "local", "ok", "最近一小时 3 条", "", "2026-11-06T09:56:00+08:00"),
    ServiceHealthRow("jwxt", "教务同步", "local", "ok", "上次同步 6 小时前", "", "2026-11-06T09:56:00+08:00"),
    ServiceHealthRow("collector_host", "采集主机", "local", "warn", "采集已暂停", "", "2026-11-06T09:56:00+08:00"),
)

internal fun previewChecks(): List<DiagCheck> = DiagEvaluator.evaluate(
    DiagProbeInput(
        androidVersion = "17",
        appVersion = "3.0.0",
        webviewVersion = "WebView/137",
        screenWidthPx = 1080,
        screenHeightPx = 2400,
        screenDensity = 2.75f,
        memoryTotalMb = 12_288,
        memoryAvailMb = 1_000,
        storageTotalMb = 222_000,
        storageFreeMb = 86_000,
        networkType = "wifi",
        networkValidated = true,
        gatewayLatencyMs = 4_126,
        serverEpochMs = PREVIEW_NOW + 500,
        nowMillis = PREVIEW_NOW,
        localDataOk = true,
    ),
)

internal fun previewAnalyses(): List<DiagAnalysis> = listOf(
    DiagAnalysis(
        id = "an-sync",
        ts = PREVIEW_NOW - 5 * 60_000,
        platform = "android",
        scope = "cloud",
        severity = "warn",
        title = "学工记云同步延迟，最近一轮作业拉取失败",
        cause = "本机网络拥塞导致云端作业拉取超时；已自动重试并等待本机代理修复。",
        suggestions = listOf("等待本机代理自动重跑同步", "切换 Wi-Fi 后重试"),
        fixable = true,
        action = "sync_xgj",
        status = "running",
        confidence = 0.86,
        flow = listOf(
            FlowStep(PREVIEW_NOW - 6 * 60_000, "检测到作业拉取失败", "done"),
            FlowStep(PREVIEW_NOW - 3 * 60_000, "本机开始执行 sync_xgj", "run"),
        ),
        relatedHealthIds = listOf("cloud_xgj_sync"),
        relatedRepairId = "rt-sync",
    ),
    DiagAnalysis(
        id = "an-gateway",
        ts = PREVIEW_NOW - 20 * 60_000,
        platform = "android",
        scope = "device",
        severity = "warn",
        title = "CloudBase 网关延迟偏高",
        cause = "当前网络到云端网关约 4.1 秒，可能导致同步变慢。",
        suggestions = listOf("切到 Wi-Fi 再观察"),
        fixable = true,
        action = "recheck_health",
        status = "fixed",
        confidence = 0.74,
        flow = listOf(
            FlowStep(PREVIEW_NOW - 21 * 60_000, "检测到网关延迟偏高", "done"),
            FlowStep(PREVIEW_NOW - 19 * 60_000, "本机重测网关", "done"),
            FlowStep(PREVIEW_NOW - 18 * 60_000, "延迟恢复，标记已修复", "fixed"),
        ),
        relatedChecks = listOf("gateway"),
        relatedRepairId = "rt-gateway",
    ),
    DiagAnalysis(
        id = "an-memory",
        ts = PREVIEW_NOW - 9 * 60_000,
        platform = "android",
        scope = "device",
        severity = "warn",
        title = "可用内存偏低",
        cause = "后台进程较多，可用内存占比较低。",
        suggestions = listOf("清理后台应用"),
        fixable = false,
        action = "",
        status = "new",
        confidence = 0.6,
        flow = emptyList(),
        relatedChecks = listOf("memory"),
    ),
    DiagAnalysis(
        id = "an-collector",
        ts = PREVIEW_NOW - 2 * 60_000,
        platform = "android",
        scope = "cloud",
        severity = "warn",
        title = "采集主机已暂停",
        cause = "本机采集脚本处于暂停状态，通知可能延迟。",
        suggestions = listOf("等待本机代理恢复采集"),
        fixable = false,
        action = "",
        status = "new",
        confidence = 0.55,
        flow = emptyList(),
        relatedHealthIds = listOf("collector_host"),
    ),
    DiagAnalysis(
        id = "an-orphan",
        ts = PREVIEW_NOW - 40 * 60_000,
        platform = "android",
        scope = "mixed",
        severity = "error",
        title = "历史诊断：通知附件解析失败",
        cause = "旧版本解析器对压缩包格式不兼容。",
        suggestions = listOf("升级到最新版本"),
        fixable = false,
        action = "",
        status = "failed",
        confidence = 0.4,
        flow = listOf(FlowStep(PREVIEW_NOW - 39 * 60_000, "解析失败", "fail")),
    ),
)

internal fun previewRepairs(): List<RepairTask> = listOf(
    RepairTask(
        id = "rt-sync",
        createdAt = PREVIEW_NOW - 3 * 60_000,
        action = "sync_xgj",
        reason = "学工记云同步延迟",
        status = "running",
        claimedAt = PREVIEW_NOW - 2 * 60_000,
        finishedAt = 0L,
        result = "",
        analysisId = "an-sync",
        node = "local",
    ),
    RepairTask(
        id = "rt-gateway",
        createdAt = PREVIEW_NOW - 19 * 60_000,
        action = "recheck_health",
        reason = "网关延迟偏高",
        status = "done",
        claimedAt = PREVIEW_NOW - 19 * 60_000,
        finishedAt = PREVIEW_NOW - 18 * 60_000,
        result = "重测完成：cloudbase 186ms / gateway ok",
        analysisId = "an-gateway",
        node = "local",
    ),
    RepairTask(
        id = "rt-orphan",
        createdAt = PREVIEW_NOW - 45 * 60_000,
        action = "restart_gateway",
        reason = "历史修复（无关联分析）",
        status = "done",
        claimedAt = PREVIEW_NOW - 44 * 60_000,
        finishedAt = PREVIEW_NOW - 43 * 60_000,
        result = "网关已重启",
        analysisId = "",
        node = "local",
    ),
)

internal fun previewReports(): List<ClientDiagRow> = listOf(
    ClientDiagRow(
        id = "an-demo-row-1",
        platform = "android",
        appVer = "3.0.0",
        kind = "network",
        level = "warn",
        message = "CloudBase 网关延迟：4126 ms",
        fingerprint = "4f2c8a9e1b3d5f70",
        ts = PREVIEW_NOW - 20 * 60_000,
        status = "analyzed",
        detail = JsonObject(emptyMap()),
        device = JsonObject(emptyMap()),
    ),
)

@Composable
private fun DiagV21PreviewFrame(
    dark: Boolean,
    expandedIds: Set<String>,
    othersExpanded: Boolean = false,
) {
    ClassToolsTheme(darkTheme = dark) {
        // 预览直接渲染页面（skipLoad，不联网）；展开态通过 initialExpandedIds 注入。
        DiagScreen(
            onBack = {},
            session = previewSession,
            repository = PreviewDiagRepository,
            skipLoad = true,
            initialGroups = com.furina.classtools.data.diag.DiagParser.groupServiceHealth(
                previewHealthRows(),
                PREVIEW_NOW,
            ),
            initialAnalyses = previewAnalyses(),
            initialRepairs = previewRepairs(),
            initialAgent = DiagAgentInfo(online = true, seenAt = PREVIEW_NOW - 60_000, host = "local-windows", version = "1.0"),
            initialChecks = previewChecks(),
            initialMyReports = previewReports(),
            initialExpandedIds = expandedIds,
            initialOthersExpanded = othersExpanded,
            nowMillis = { PREVIEW_NOW },
            pollConfig = DiagPollConfig(),
        )
    }
}

@Preview(name = "220 诊断 v2.1 · 折叠态（AI 下挂异常行）", showBackground = true, widthDp = 412, heightDp = 2400)
@Composable
private fun DiagV21FoldedLightPreview() {
    DiagV21PreviewFrame(dark = false, expandedIds = emptySet())
}

@Preview(name = "221 诊断 v2.1 · 展开态（原因/建议/flow/关联修复）", showBackground = true, widthDp = 412, heightDp = 2800)
@Composable
private fun DiagV21ExpandedLightPreview() {
    DiagV21PreviewFrame(
        dark = false,
        expandedIds = setOf("an-sync", "an-gateway", "an-orphan"),
        othersExpanded = true,
    )
}

@Preview(name = "222 诊断 v2.1 · 折叠态 · 深色", showBackground = true, widthDp = 412, heightDp = 2400)
@Composable
private fun DiagV21FoldedDarkPreview() {
    DiagV21PreviewFrame(dark = true, expandedIds = emptySet())
}

