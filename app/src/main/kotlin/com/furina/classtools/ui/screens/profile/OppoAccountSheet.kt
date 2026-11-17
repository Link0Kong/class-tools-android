package com.furina.classtools.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.account.OppoAccountText
import com.furina.classtools.data.account.OppoBinding
import com.furina.classtools.data.account.OppoBindingsUiState
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 「我的 → OPPO 账号」底部弹层：已绑定列表（昵称 + 绑定日期 + 解绑）+ 绑定当前 OPPO 账号。
 *
 * 数据/网络全部由调用方（MainNavHost → OppoAccountManager）处理；本弹层只做展示与二次确认，
 * 不展示 openid/unionid/手机号等 OPPO 隐私标识。
 *
 * @param bindingsState 绑定列表状态（Idle/Loading/Ready/Error）
 * @param busy 绑定/解绑进行中（禁用按钮，防止重复拉起 OPPO 授权）
 * @param maxBindings 服务端返回的单学号上限（默认 3）
 * @param onRefresh 重新拉取列表（失败态「重试」/空态刷新）
 * @param onBind 绑定当前 OPPO 账号（调用方拉起 OAuth）
 * @param onUnbind 解绑指定记录（调用方完成网络请求与刷新）
 */
@Composable
internal fun OppoAccountSheet(
    bindingsState: OppoBindingsUiState,
    busy: Boolean,
    maxBindings: Int = OppoAccountText.DEFAULT_MAX_BINDINGS,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onBind: () -> Unit,
    onUnbind: (OppoBinding) -> Unit,
) {
    var pendingUnbind by remember { mutableStateOf<OppoBinding?>(null) }
    val page = (bindingsState as? OppoBindingsUiState.Ready)?.page
    val subtitle = when (bindingsState) {
        is OppoBindingsUiState.Loading -> "读取中…"
        is OppoBindingsUiState.Ready -> OppoAccountText.bindingsLabel(
            count = bindingsState.page.count,
            max = bindingsState.page.max,
        )

        is OppoBindingsUiState.Error -> "读取失败"
        OppoBindingsUiState.Idle -> "—"
    }
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(title = "OPPO 账号", subtitle = subtitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (bindingsState) {
                is OppoBindingsUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("读取中…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                is OppoBindingsUiState.Error -> {
                    Text(
                        text = bindingsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text("重试")
                    }
                }

                is OppoBindingsUiState.Ready -> {
                    if (bindingsState.page.items.isEmpty()) {
                        Text(
                            text = "暂未绑定 OPPO 账号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        bindingsState.page.items.forEach { binding ->
                            OppoBindingRow(
                                binding = binding,
                                busy = busy,
                                onUnbind = { pendingUnbind = binding },
                            )
                        }
                    }
                    if (bindingsState.page.isFull) {
                        Text(
                            text = "已达上限 ${bindingsState.page.max}/${bindingsState.page.max}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OppoBindingsUiState.Idle -> {
                    Text(
                        text = "暂未绑定 OPPO 账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBind,
                enabled = !busy && page?.isFull != true,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = CtShapeTokens.pill,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "处理中…" else "绑定 OPPO 账号")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("关闭")
            }
        }
    }

    // 解绑前确认（不写长说明）。
    pendingUnbind?.let { binding ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingUnbind = null },
            title = { Text("解绑 OPPO 账号？") },
            text = {
                Text(
                    text = binding.nickname.ifBlank { "该 OPPO 账号" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingUnbind = null
                        onUnbind(binding)
                    },
                ) { Text("解绑") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingUnbind = null }) { Text("取消") }
            },
        )
    }
}

/** 一条已绑定 OPPO 账号：昵称 + 绑定日期 + 解绑。 */
@Composable
private fun OppoBindingRow(
    binding: OppoBinding,
    busy: Boolean,
    onUnbind: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = binding.nickname.ifBlank { "OPPO 账号" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = binding.createdDateText.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onUnbind,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("解绑", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
