@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.diag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.furina.classtools.data.diag.DiagCheck
import com.furina.classtools.data.diag.DiagEvaluator
import com.furina.classtools.data.diag.DiagPayload
import com.furina.classtools.data.diag.DiagProbeInput
import com.furina.classtools.data.diag.DiagRepository
import com.furina.classtools.data.diag.DiagResult
import com.furina.classtools.data.diag.DiagSanitizer
import com.furina.classtools.data.diag.DiagStatus
import com.furina.classtools.data.diag.DiagSummary
import com.furina.classtools.data.diag.GatewayProbe
import com.furina.classtools.data.diag.RepairTask
import com.furina.classtools.data.diag.ServiceHealthGroup
import com.furina.classtools.data.diag.ServiceHealthRow
import com.furina.classtools.data.diag.displayMessage
import com.furina.classtools.data.diag.valueOrNull
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.SectionHeader
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 诊断 v2 页面（所有已登录成员可见；契约 `_diag_task/CONTRACT.md` §1/§2）。
 *
 * 展示四块：
 * 1. **本机 agent 状态**：`rpc/diag_agent_info`（在线/离线 + 最后心跳 + host/version）；
 * 2. **设备诊断**：本机检查（机型/系统/App/屏幕/语言/内存/存储/网络/CloudBase 延迟/时钟/本地数据）+
 *    「重新检测」；注意/异常项自动上报 `client_diag`（fingerprint 10 分钟去重、每批 ≤3 条），也可手动上报；
 * 3. **AI 分析**：`diag_analysis` 的 severity/title/cause/suggestion/fixable/action/status + flow 时间线；
 * 4. **修复任务 + 服务健康**：`repair_tasks` 记录 + `service_health` 18 行/5 组（detail 显示脱敏）。
 *
 * 轮询：页面可见（ON_RESUME）时每 [pollIntervalMs]（默认 15s）刷新，ON_PAUSE 停止；返回上一页随组合销毁。
 * 数据加载全部走注入的 [DiagRepository]（预览/单测用 Fake）。
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
    nowMillis: () -> Long = { System.currentTimeMillis() },
    pollIntervalMs: Long = 15_000L,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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
    var reportingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var screenVisible by remember { mutableStateOf(true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { screenVisible = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { screenVisible = false }

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
                    val row = DiagPayload.fromCheck(
                        check = check,
                        sid = session.sid,
                        appVersion = appVersion,
                        device = input.deviceMap(),
                        now = nowMillis(),
                    )
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
        repository.loadServiceHealth().let { result ->
            when (result) {
                is DiagResult.Success -> groups = DiagParserGrouping.group(result.value, nowMillis())
                is DiagResult.Failure -> errors.add(result.error.displayMessage)
            }
        }
        repository.loadAnalyses().let { result ->
            if (result is DiagResult.Success) analyses = result.value
            else errors.add((result as DiagResult.Failure).error.displayMessage)
        }
        repository.loadRepairs().let { result ->
            if (result is DiagResult.Success) repairs = result.value
            else errors.add((result as DiagResult.Failure).error.displayMessage)
        }
        repository.agentInfo().let { result ->
            if (result is DiagResult.Success) agent = result.value
        }
        repository.loadMyReports().let { result ->
            if (result is DiagResult.Success) myReports = result.value
        }
        if (errors.isNotEmpty() && groups.isEmpty()) {
            loadError = errors.first()
        } else if (errors.isEmpty()) {
            loadError = ""
        }
        loading = false
    }

    // 首次进入：补报本机队列 + 远程数据 + 设备检查（只在真实网络模式跑）。
    LaunchedEffect(skipLoad) {
        if (skipLoad) {
            loading = false
            return@LaunchedEffect
        }
        runCatching { reporter?.drainPending() }
        refreshRemote()
        runCatching { runDeviceChecks() }
    }

    // 15s 轮询只在页面可见时运行；页面离开组合即取消。
    LaunchedEffect(skipLoad, screenVisible, pollIntervalMs) {
        if (skipLoad || !screenVisible) return@LaunchedEffect
        // 首次数据由上面的 LaunchedEffect 负责，这里只做后续轮询。
        while (true) {
            delay(pollIntervalMs)
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
            scope.launch { snackbar.showSnackbar("未登录，无法上报") }
            return
        }
        reportingIds = reportingIds + check.id
        scope.launch {
            val base = deviceProbe?.collectBase(nowMillis())
                ?: DiagProbeInput(appVersion = appVersion, nowMillis = nowMillis())
            val row = DiagPayload.fromCheck(
                check = check,
                sid = session.sid,
                appVersion = appVersion,
                device = base.deviceMap(),
                now = nowMillis(),
            )
            val message = when (reporterOrNull.report(row)) {
                com.furina.classtools.data.diag.DiagReportOutcome.Sent -> "已上报"
                com.furina.classtools.data.diag.DiagReportOutcome.Queued -> "网络不可用，已排队补报"
                com.furina.classtools.data.diag.DiagReportOutcome.Skipped -> "10 分钟内已上报过，已去重"
            }
            reportingIds = reportingIds - check.id
            snackbar.showSnackbar(message)
        }
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
                CtTopBar(
                    title = "诊断",
                    subtitle = "服务健康 · 设备 · AI 修复",
                    onBack = onBack,
                    actions = {
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
                    },
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spec.horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (loading) {
                        LoadingRow()
                    }
                    if (loadError.isNotBlank() && groups.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CtShapeTokens.toolCard,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "加载失败：${DiagSanitizer.sanitizeDisplay(loadError)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    AgentCard(agent = agent, nowMillis = nowMillis())
                    DeviceDiagCard(
                        summary = summary,
                        checking = checking,
                        lastCheckAt = lastCheckAt,
                        lastCheckNote = lastCheckNote,
                        onRecheck = {
                            if (!skipLoad) scope.launch { runDeviceChecks() }
                        },
                        onReport = ::reportManually,
                        reportable = reporter != null && session.sid.isNotBlank(),
                        reportingIds = reportingIds,
                    )
                    AnalysisCard(analyses = analyses, nowMillis = nowMillis())
                    RepairCard(repairs = repairs, nowMillis = nowMillis())
                    ServiceHealthCard(groups = groups, nowMillis = nowMillis())
                    MyReportsCard(reports = myReports, nowMillis = nowMillis())
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/** 服务健康分组的小封装（page 内调用，便于预览/测试替换）。 */
private object DiagParserGrouping {
    fun group(rows: List<ServiceHealthRow>, now: Long): List<ServiceHealthGroup> =
        com.furina.classtools.data.diag.DiagParser.groupServiceHealth(rows, now)
}

// =============================================================================
// 卡片
// =============================================================================

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

@Composable
private fun DiagCard(
    title: String,
    status: DiagStatus? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (status != null) StatusPill(status)
                trailing?.invoke()
            }
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
private fun statusColors(status: DiagStatus): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    return when (status) {
        DiagStatus.PASS -> scheme.primaryContainer to scheme.onPrimaryContainer
        DiagStatus.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DiagStatus.FAIL -> scheme.errorContainer to scheme.onErrorContainer
        DiagStatus.INFO -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

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
            agent.online -> {
                Text(
                    text = buildString {
                        append("在线")
                        if (agent.seenAt > 0) append(" · 心跳 ").append(relativeTime(agent.seenAt, nowMillis))
                        if (agent.host.isNotBlank()) append(" · ").append(agent.host)
                        if (agent.version.isNotBlank()) append(" · v").append(agent.version)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "AI 分析与自动修复由本机代理执行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Text(
                text = buildString {
                    append("离线")
                    if (agent.seenAt > 0) append(" · 最后心跳 ").append(relativeTime(agent.seenAt, nowMillis))
                    append(" · AI 分析/自动修复可能延迟")
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
    onRecheck: () -> Unit,
    onReport: (DiagCheck) -> Unit,
    reportable: Boolean,
    reportingIds: Set<String>,
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
                DeviceCheckRow(
                    check = check,
                    onReport = onReport,
                    reportable = reportable && check.shouldReport,
                    reporting = check.id in reportingIds,
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
private fun DeviceCheckRow(
    check: DiagCheck,
    onReport: (DiagCheck) -> Unit,
    reportable: Boolean,
    reporting: Boolean,
) {
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (check.shouldReport && reportable) {
            TextButton(onClick = { onReport(check) }, enabled = !reporting) {
                Text(if (reporting) "…" else "上报", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            StatusPill(check.status, statusLabelOf(check.status))
        }
    }
}

private fun statusLabelOf(status: DiagStatus): String = when (status) {
    DiagStatus.PASS -> "通过"
    DiagStatus.WARN -> "注意"
    DiagStatus.FAIL -> "异常"
    DiagStatus.INFO -> "信息"
}

@Composable
private fun AnalysisCard(analyses: List<DiagAnalysis>, nowMillis: Long) {
    DiagCard(
        title = "AI 分析",
        status = when {
            analyses.any { it.severityLevel == com.furina.classtools.data.diag.DiagLevel.ERROR } -> DiagStatus.FAIL
            analyses.any { it.severityLevel == com.furina.classtools.data.diag.DiagLevel.WARN } -> DiagStatus.WARN
            analyses.isNotEmpty() -> DiagStatus.PASS
            else -> DiagStatus.INFO
        },
    ) {
        if (analyses.isEmpty()) {
            Text(
                text = "暂无分析结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            analyses.forEachIndexed { index, analysis ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                }
                AnalysisItem(analysis = analysis, nowMillis = nowMillis)
            }
        }
    }
}

@Composable
private fun AnalysisItem(analysis: DiagAnalysis, nowMillis: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val levelStatus = when (analysis.severityLevel) {
                com.furina.classtools.data.diag.DiagLevel.ERROR -> DiagStatus.FAIL
                com.furina.classtools.data.diag.DiagLevel.WARN -> DiagStatus.WARN
                com.furina.classtools.data.diag.DiagLevel.INFO -> DiagStatus.INFO
            }
            StatusPill(levelStatus, when (analysis.severityLevel) {
                com.furina.classtools.data.diag.DiagLevel.ERROR -> "错误"
                com.furina.classtools.data.diag.DiagLevel.WARN -> "警告"
                com.furina.classtools.data.diag.DiagLevel.INFO -> "提示"
            })
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = DiagSanitizer.sanitizeDisplay(analysis.title).ifBlank { "未命名分析" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = analysis.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (analysis.cause.isNotBlank()) {
            Text(
                text = "原因：" + DiagSanitizer.sanitizeDisplay(analysis.cause),
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
        if (analysis.fixable) {
            Text(
                text = "可自动修复" + if (analysis.action.isNotBlank()) " · ${analysis.action}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (analysis.flow.isNotEmpty()) {
            FlowTimeline(steps = analysis.flow, nowMillis = nowMillis)
        }
        Text(
            text = buildString {
                if (analysis.platform.isNotBlank()) append(analysis.platform)
                if (analysis.scope.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(analysis.scope)
                }
                if (analysis.ts > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(relativeTime(analysis.ts, nowMillis))
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FlowTimeline(
    steps: List<com.furina.classtools.data.diag.FlowStep>,
    nowMillis: Long,
) {
    Column(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.take(8).forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DiagSanitizer.sanitizeDisplay(step.text, 200),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val meta = buildString {
                        if (step.state.isNotBlank()) append(step.state)
                        if (step.ts > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(relativeTime(step.ts, nowMillis))
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairCard(repairs: List<RepairTask>, nowMillis: Long) {
    DiagCard(
        title = "修复任务",
        status = when {
            repairs.isEmpty() -> DiagStatus.INFO
            repairs.any { it.status == "failed" } -> DiagStatus.FAIL
            repairs.any { !it.finished } -> DiagStatus.WARN
            else -> DiagStatus.PASS
        },
    ) {
        if (repairs.isEmpty()) {
            Text(
                text = "暂无修复任务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            repairs.take(8).forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.action.ifBlank { "未知动作" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        CtPill(text = task.statusLabel)
                    }
                    if (task.reason.isNotBlank()) {
                        Text(
                            text = DiagSanitizer.sanitizeDisplay(task.reason, 200),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.result.isNotBlank()) {
                        Text(
                            text = "结果：" + DiagSanitizer.sanitizeDisplay(task.result, 240),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = buildString {
                            if (task.createdAt > 0) append(relativeTime(task.createdAt, nowMillis))
                            if (task.finishedAt > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("完成 ").append(relativeTime(task.finishedAt, nowMillis))
                            }
                            if (task.node.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(task.node)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceHealthCard(groups: List<ServiceHealthGroup>, nowMillis: Long) {
    DiagCard(
        title = "服务健康",
        status = when {
            groups.any { it.worstStatus == DiagStatus.FAIL } -> DiagStatus.FAIL
            groups.any { it.worstStatus == DiagStatus.WARN } -> DiagStatus.WARN
            groups.isNotEmpty() -> DiagStatus.PASS
            else -> DiagStatus.INFO
        },
    ) {
        if (groups.isEmpty()) {
            Text(
                text = "暂无服务健康数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            groups.forEach { group ->
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
                    ServiceHealthRowItem(row = row, nowMillis = nowMillis)
                }
            }
        }
    }
}

@Composable
private fun ServiceHealthRowItem(row: ServiceHealthRow, nowMillis: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name.ifBlank { row.id },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val checkedAt = row.checkedAt.takeIf { it.isNotBlank() }
        if (checkedAt != null) {
            val millis = parseTimeMillisSafe(checkedAt) ?: 0L
            val relative = if (millis > 0) relativeTime(millis, nowMillis) else ""
            Text(
                text = "检查于 " + (relative.ifBlank { clockText(millis) }).ifBlank { checkedAt },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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
// 预览（供 android preview render 出诊断页截图）
// =============================================================================

private val previewSession = Session(
    sid = "3260707024",
    name = "开发者",
    token = "preview-token",
)

private object PreviewDiagRepository : DiagRepository {
    override suspend fun loadServiceHealth(): DiagResult<List<ServiceHealthRow>> =
        DiagResult.Success(previewHealthRows())

    override suspend fun loadAnalyses(): DiagResult<List<DiagAnalysis>> =
        DiagResult.Success(previewAnalyses())

    override suspend fun loadRepairs(): DiagResult<List<RepairTask>> =
        DiagResult.Success(previewRepairs())

    override suspend fun agentInfo(): DiagResult<DiagAgentInfo> =
        DiagResult.Success(DiagAgentInfo(online = true, seenAt = 1_793_929_800_000L, host = "local-windows", version = "1.0"))

    override suspend fun measureGateway(): DiagResult<GatewayProbe> =
        DiagResult.Success(GatewayProbe(latencyMs = 128L, serverEpochMs = 1_793_930_400_500L))

    override suspend fun reportClientDiag(row: kotlinx.serialization.json.JsonObject): com.furina.classtools.data.network.ApiResult<Unit> =
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
        manufacturer = "Xiaomi",
        model = "24129PN74C",
        androidVersion = "17",
        sdkInt = 37,
        appVersion = "3.0.0",
        appVersionCode = 6,
        screenWidthDp = 412,
        screenHeightDp = 915,
        densityDpi = 420,
        language = "zh-CN",
        timezoneId = "Asia/Shanghai",
        timezoneOffsetMinutes = 480,
        memoryTotalMb = 12_288,
        memoryAvailMb = 4_096,
        storageTotalMb = 222_000,
        storageFreeMb = 86_000,
        networkType = "wifi",
        networkValidated = true,
        gatewayLatencyMs = 128,
        serverEpochMs = 1_793_930_400_500L,
        nowMillis = 1_793_930_400_000L,
        localDataOk = true,
    ),
)

internal fun previewAnalyses(): List<DiagAnalysis> = listOf(
    DiagAnalysis(
        id = "an-demo-1",
        ts = 1_793_930_300_000L,
        platform = "android",
        scope = "cloud",
        severity = "warn",
        title = "云同步偶尔延迟",
        cause = "本机网络拥塞导致 userdata 拉取超时，已自动重试。",
        suggestions = listOf("切换 Wi-Fi 后重试", "等待 1 分钟后本机代理重测"),
        fixable = true,
        action = "recheck_health",
        status = "fixed",
        confidence = 0.82,
        flow = listOf(
            com.furina.classtools.data.diag.FlowStep(1_793_930_000_000L, "检测到诊断上报", "done"),
            com.furina.classtools.data.diag.FlowStep(1_793_930_100_000L, "本机代理重测依赖服务", "done"),
            com.furina.classtools.data.diag.FlowStep(1_793_930_300_000L, "延迟恢复，标记已修复", "fixed"),
        ),
    ),
)

internal fun previewRepairs(): List<RepairTask> = listOf(
    RepairTask(
        id = "rt-demo-1",
        createdAt = 1_793_930_050_000L,
        action = "recheck_health",
        reason = "云同步延迟（诊断 an-demo-1）",
        status = "done",
        claimedAt = 1_793_930_060_000L,
        finishedAt = 1_793_930_300_000L,
        result = "重测完成：cloudbase 186ms / tt_cloud ok",
        analysisId = "an-demo-1",
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
        ts = 1_793_930_200_000L,
        status = "analyzed",
        detail = kotlinx.serialization.json.JsonObject(emptyMap()),
        device = kotlinx.serialization.json.JsonObject(emptyMap()),
    ),
)

@Preview(name = "200 诊断页 · 浅色", showBackground = true, widthDp = 412, heightDp = 2200)
@Composable
private fun DiagLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DiagScreen(
            onBack = {},
            session = previewSession,
            repository = PreviewDiagRepository,
            skipLoad = true,
            initialGroups = com.furina.classtools.data.diag.DiagParser.groupServiceHealth(
                previewHealthRows(),
                1_793_930_400_000L,
            ),
            initialAnalyses = previewAnalyses(),
            initialRepairs = previewRepairs(),
            initialAgent = DiagAgentInfo(online = true, seenAt = 1_793_929_800_000L, host = "local-windows"),
            initialChecks = previewChecks(),
            initialMyReports = previewReports(),
            nowMillis = { 1_793_930_400_000L },
        )
    }
}

@Preview(name = "201 诊断页 · 深色", showBackground = true, widthDp = 412, heightDp = 2200)
@Composable
private fun DiagDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DiagScreen(
            onBack = {},
            session = previewSession,
            repository = PreviewDiagRepository,
            skipLoad = true,
            initialGroups = com.furina.classtools.data.diag.DiagParser.groupServiceHealth(
                previewHealthRows(),
                1_793_930_400_000L,
            ),
            initialAnalyses = previewAnalyses(),
            initialRepairs = previewRepairs(),
            initialAgent = DiagAgentInfo(online = false, seenAt = 1_793_920_000_000L),
            initialChecks = previewChecks(),
            initialMyReports = previewReports(),
            nowMillis = { 1_793_930_400_000L },
        )
    }
}
