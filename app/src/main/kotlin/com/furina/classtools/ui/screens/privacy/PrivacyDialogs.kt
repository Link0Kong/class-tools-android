package com.furina.classtools.ui.screens.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtText

/**
 * 隐私政策 / 用户协议弹层（摘要版 + 「查看完整版」入口）。
 *
 * 2026-11-19 用户要求：**不破坏现有摘要**的前提下，增加完整版入口。
 * - 弹层正文仍是端内摘要 [PrivacyTexts]（离线可用、任何网络状态下都有内容）；
 * - 副标题显示摘要与全文的版本口径（[LegalLinks.VERSION_LABEL]）；
 * - 「查看完整版」用系统浏览器打开 ECS 上的正式全文（[LegalLinks]）；
 * - **失败兜底**：没有浏览器或 Intent 被拒时复制链接 + Toast，并在弹层里显示可复制的链接说明，
 *   摘要不被替换、也不影响关闭。
 */
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    PolicyDialog(
        title = "隐私政策",
        body = PrivacyTexts.PRIVACY_POLICY,
        url = LegalLinks.privacyUrl(),
        onDismiss = onDismiss,
    )
}

@Composable
fun UserAgreementDialog(onDismiss: () -> Unit) {
    PolicyDialog(
        title = "用户协议",
        body = PrivacyTexts.USER_AGREEMENT,
        url = LegalLinks.termsUrl(),
        onDismiss = onDismiss,
    )
}

@Composable
private fun PolicyDialog(
    title: String,
    body: String,
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 兜底提示：打开失败后把链接显示在弹层里（可长按复制），避免用户「点了没反应」。
    var fallbackNote by remember { mutableStateOf("") }
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "摘要 · 完整版 " + LegalLinks.VERSION_LABEL,
                    style = CtText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 4.dp),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fallbackNote.isNotBlank()) {
                    Text(
                        text = fallbackNote,
                        style = CtText.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Text(
                    text = "全文：" + LegalLinks.displayPath(url),
                    style = CtText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val opened = LegalLinkOpener.openOrCopy(context, url)
                    fallbackNote = if (opened) "" else "未能打开浏览器，链接已复制：" + url
                },
            ) {
                Text("查看完整版")
            }
        },
    )
}

/**
 * 「完整版：用户协议 · 隐私政策」文字入口（身份页协议勾选处用；12sp 次级文字，不抢主按钮）。
 *
 * 点击直接用系统浏览器打开正式全文；打不开时复制链接 + Toast（[LegalLinkOpener.openOrCopy]）。
 */
@Composable
fun LegalFullTextLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "完整版",
            style = CtText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "用户协议",
            style = CtText.caption,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { LegalLinkOpener.openOrCopy(context, LegalLinks.termsUrl()) },
        )
        Text(
            text = "·",
            style = CtText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "隐私政策",
            style = CtText.caption,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { LegalLinkOpener.openOrCopy(context, LegalLinks.privacyUrl()) },
        )
    }
}
