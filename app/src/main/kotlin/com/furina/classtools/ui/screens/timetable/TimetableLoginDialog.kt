@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.furina.classtools.data.timetable.DefaultTimetableLoginRepository
import com.furina.classtools.data.timetable.TtLoginEnqueueResult
import com.furina.classtools.data.timetable.TtLoginFailureReason
import com.furina.classtools.data.timetable.TtLoginPollConfig
import com.furina.classtools.data.timetable.TtLoginPollOutcome
import com.furina.classtools.data.timetable.TtLoginPolling
import com.furina.classtools.data.timetable.TtLoginStatusResult
import com.furina.classtools.data.timetable.TimetableLoginRepository
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.launch

/**
 * 「登录教务」弹窗（契约 §4：云收单 + 本机代理）。
 *
 * 流程：学号（默认本人 sid，可改但仅允许本人）+ 教务密码（[PasswordVisualTransformation]，不落盘不打印）
 * → `rpc/tt_login_enqueue {p_pwd,p_platform:'android'}` → 轮询 `rpc/tt_login_status`（1.5s，≤120s）
 * → done：把 `{c,t}` 交回调用方存 personal ttLocal 并立即刷新课表；
 * 失败按错误码提示（agent_offline/rate/pwd/auth/failed…）。
 *
 * 密码安全：只存在于本次组合的 `password` 状态；提交后立即清空；不在任何地方打印/落盘。
 *
 * @param hasPersonal 已有个人课表时显示「清除我的课表」入口
 * @param onClearPersonal 清除个人课表（调用方写 userdata `tt.ttLocal=null` + 刷新）
 * @param pollConfig 轮询节奏（单测/预览可注入更短超时）
 */
@Composable
fun TimetableLoginDialog(
    sessionSid: String,
    loginRepository: TimetableLoginRepository,
    hasPersonal: Boolean,
    onDismiss: () -> Unit,
    onSuccess: (TtSnapshot) -> Unit,
    onClearPersonal: (suspend () -> Unit)? = null,
    pollConfig: TtLoginPollConfig = TtLoginPollConfig(),
) {
    var sid by remember { mutableStateOf(sessionSid) }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun validate(): String? {
        val sidText = sid.trim()
        if (!DefaultTimetableLoginRepository.STUDENT_ID_REGEX.matches(sidText)) return "请输入 10 位学号"
        if (sessionSid.isNotBlank() && sidText != sessionSid) return "只能登录本人教务账号"
        if (password.isBlank()) return "请输入教务密码"
        return null
    }

    fun submit() {
        if (submitting || clearing) return
        val error = validate()
        if (error != null) {
            errorText = error
            return
        }
        errorText = ""
        submitting = true
        statusText = "提交登录任务…"
        scope.launch {
            when (val enqueue = loginRepository.enqueue(password)) {
                is TtLoginEnqueueResult.Failure -> {
                    password = ""
                    errorText = enqueue.message
                    statusText = ""
                    submitting = false
                }
                is TtLoginEnqueueResult.Success -> {
                    // 密码已交给云端（仅密文落库）；本机立即清空，不保留在状态/日志里。
                    password = ""
                    statusText = "正在登录教务…"
                    val outcome = TtLoginPolling.poll(
                        jobId = enqueue.jobId,
                        fetch = loginRepository::status,
                        config = pollConfig,
                    )
                    submitting = false
                    statusText = ""
                    when (outcome) {
                        is TtLoginPollOutcome.Done -> {
                            onSuccess(outcome.snapshot)
                            onDismiss()
                        }
                        is TtLoginPollOutcome.Failed -> errorText = outcome.message
                        TtLoginPollOutcome.Timeout -> errorText = TtLoginFailureReason.TIMEOUT.message
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            shape = CtShapeTokens.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
        ) {
            TimetableLoginForm(
                sid = sid,
                password = password,
                submitting = submitting,
                statusText = statusText,
                errorText = errorText,
                hasPersonal = hasPersonal,
                clearing = clearing,
                onSidChange = { if (!submitting) { sid = it; errorText = "" } },
                onPasswordChange = { if (!submitting) { password = it; errorText = "" } },
                onSubmit = ::submit,
                onDismiss = { if (!submitting) onDismiss() },
                onClearPersonal = if (onClearPersonal != null && hasPersonal) {
                    {
                        if (!submitting && !clearing) {
                            clearing = true
                            scope.launch {
                                runCatching { onClearPersonal() }
                                clearing = false
                                onDismiss()
                            }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * 登录弹窗内容（无状态，供弹窗与 @Preview 复用）。
 */
@Composable
fun TimetableLoginForm(
    sid: String,
    password: String,
    submitting: Boolean,
    statusText: String,
    errorText: String,
    hasPersonal: Boolean,
    modifier: Modifier = Modifier,
    clearing: Boolean = false,
    onSidChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onClearPersonal: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "登录教务",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "抓取本人课表到本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextField(
            value = sid,
            onValueChange = onSidChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting && !clearing,
            singleLine = true,
            label = { Text("学号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting && !clearing,
            singleLine = true,
            label = { Text("教务密码") },
            supportingText = { Text("不保存 · 不打印") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (statusText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (errorText.isNotBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onClearPersonal != null) {
                TextButton(onClick = onClearPersonal, enabled = !submitting && !clearing) {
                    Text(if (clearing) "清除中…" else "清除我的课表")
                }
            } else if (hasPersonal) {
                Text(
                    text = "已有个人课表",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = onSubmit, enabled = !submitting && !clearing) {
                Text(if (submitting) "登录中…" else "登录")
            }
        }
    }
}

/**
 * 登录弹层预览（弹层内容 + 遮罩背景，供 `android preview render` 出图；
 * 真实运行时由 [Dialog] 承载同一 [TimetableLoginForm]）。
 */
@Preview(name = "210 课表 · 登录教务弹层", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableLoginPreview() {
    ClassToolsTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(915.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 16.dp),
                shape = CtShapeTokens.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                TimetableLoginForm(
                    sid = "2024000001",
                    password = "********",
                    submitting = false,
                    statusText = "",
                    errorText = "",
                    hasPersonal = true,
                    onClearPersonal = {},
                )
            }
        }
    }
}

@Preview(name = "211 课表 · 登录失败提示", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun TimetableLoginErrorPreview() {
    ClassToolsTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(915.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 16.dp),
                shape = CtShapeTokens.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                TimetableLoginForm(
                    sid = "2024000001",
                    password = "",
                    submitting = false,
                    statusText = "",
                    errorText = TtLoginFailureReason.AGENT_OFFLINE.message,
                    hasPersonal = false,
                )
            }
        }
    }
}

