package com.furina.classtools.ui.screens.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.auth.AuthRepository
import com.furina.classtools.data.auth.VerifyResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.BrandMark
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.privacy.PrivacyPolicyDialog
import com.furina.classtools.ui.screens.privacy.UserAgreementDialog
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.launch

private sealed interface IdentityUiState {
    data object Idle : IdentityUiState
    data object Verifying : IdentityUiState
    data class Error(val message: String) : IdentityUiState
}

/**
 * 身份门禁（唯一入口）：
 * 学号 + 姓名 → 云端核验 → 拿到班级 token 后才进入班级数据。
 * 未通过前不渲染任何班级内容。
 */
@Composable
fun IdentityScreen(
    authRepository: AuthRepository,
    onVerified: (Session) -> Unit,
    modifier: Modifier = Modifier,
) {
    var studentId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var uiState by remember { mutableStateOf<IdentityUiState>(IdentityUiState.Idle) }
    var consentAccepted by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showAgreement by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 360×640dp 等矮屏：压缩顶部品牌区与卡片间距，保证「进入工具站」首屏可见（P2 适配）。
    val compactHeight = LocalConfiguration.current.screenHeightDp < 720

    val verifying = uiState is IdentityUiState.Verifying
    val canSubmit = studentId.length == 10 && name.isNotBlank() && consentAccepted && !verifying

    fun submit() {
        if (!canSubmit) return
        uiState = IdentityUiState.Verifying
        scope.launch {
            uiState = when (val result = authRepository.verify(studentId.trim(), name.trim())) {
                is VerifyResult.Success -> {
                    onVerified(result.session)
                    IdentityUiState.Idle
                }

                is VerifyResult.Failure -> IdentityUiState.Error(result.message)
            }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = if (compactHeight) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(if (compactHeight) 18.dp else 56.dp))

            BrandMark(size = if (compactHeight) 46.dp else 68.dp)

            Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 20.dp))

            Text(
                text = "班级工具站",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "学号 + 姓名核验后使用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!compactHeight) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CtPill(text = "原生 Android 客户端")
                }
            }

            Spacer(modifier = Modifier.height(if (compactHeight) 12.dp else 28.dp))

            Surface(
                modifier = Modifier.widthIn(max = 520.dp),
                shape = CtShapeTokens.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (compactHeight) 14.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 14.dp),
                ) {
                    Text(
                        text = "身份核验",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { input ->
                            studentId = input.filter { it.isDigit() }.take(10)
                            if (uiState is IdentityUiState.Error) uiState = IdentityUiState.Idle
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("学号") },
                        placeholder = { Text("10 位学号") },
                        singleLine = true,
                        shape = CtShapeTokens.input,
                        isError = studentId.isNotEmpty() && studentId.length != 10,
                        supportingText = {
                            if (studentId.isNotEmpty() && studentId.length != 10) {
                                Text("学号应为 10 位数字")
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { input ->
                            name = input.take(20)
                            if (uiState is IdentityUiState.Error) uiState = IdentityUiState.Idle
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("姓名") },
                        placeholder = { Text("请输入姓名") },
                        singleLine = true,
                        shape = CtShapeTokens.input,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )

                    if (uiState is IdentityUiState.Error) {
                        Text(
                            text = (uiState as IdentityUiState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // 小屏（360dp）适配：勾选说明与两个协议链接分两行，避免逐字竖排/溢出。
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = consentAccepted,
                                onCheckedChange = { consentAccepted = it },
                            )
                            Text(
                                text = "我已阅读并同意",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            TextButton(onClick = { showAgreement = true }) {
                                Text("《用户协议》", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showPrivacy = true }) {
                                Text("《隐私政策》", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Button(
                        onClick = { submit() },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = CtShapeTokens.pill,
                    ) {
                        if (verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = "进入工具站",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    // I-03：按钮不可点时说明缺少什么，避免用户不知道为什么点不了。
                    val disabledReason = when {
                        verifying -> null
                        !consentAccepted -> "请先勾选同意《用户协议》《隐私政策》"
                        studentId.isBlank() || name.isBlank() -> "请填写 10 位学号与姓名"
                        studentId.length != 10 -> "学号应为 10 位数字"
                        else -> null
                    }
                    if (!canSubmit && disabledReason != null) {
                        Text(
                            text = disabledReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 520.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "仅用于班级名单核验",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        uiState = IdentityUiState.Idle
                        onVerified(
                            Session(
                                sid = studentId.ifBlank { "3260707024" },
                                name = name.ifBlank { "开发预览" },
                                token = "",
                                debug = true,
                            ),
                        )
                    },
                ) {
                    Text("开发预览：跳过核验（仅 Debug 构建）")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME} · Android 17 设计语言",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showAgreement) UserAgreementDialog(onDismiss = { showAgreement = false })
}
