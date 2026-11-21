package com.furina.classtools.ui.components.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.share.ShareCardRenderer
import com.furina.classtools.features.share.ShareCardView
import com.furina.classtools.features.share.ShareFiles
import com.furina.classtools.features.share.ShareOutcome
import com.furina.classtools.features.share.SharePresenter
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一分享弹层（对齐微信小程序端 `components/share-sheet`）：
 *
 * - 顶部 5:4 卡片预览（[ShareCardRenderer] 后台渲染；渲染失败不阻塞，只隐藏预览）；
 * - 两个主选项：**分享图片**（系统分享面板，`image/png` + 标题链接）与**保存到相册**；
 * - 次选项：**分享链接/文案**（纯文本）与**复制文案**（无分享应用时的兜底）；
 * - 投票卡固定提示「结果请打开投票页查看」，不展示票数/百分比。
 *
 * 全部为本机操作：文件写在 `cache/share/`，只在该用户主动分享时拉起系统面板，不联网、不上传。
 */
@Composable
fun ShareSheet(
    view: ShareCardView,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember(view) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var rendering by remember(view) { mutableStateOf(true) }
    var busy by remember(view) { mutableStateOf(false) }

    LaunchedEffect(view) {
        rendering = true
        preview = withContext(Dispatchers.Default) {
            runCatching { ShareCardRenderer().render(view) }.getOrNull()
        }
        rendering = false
    }

    fun toast(outcome: ShareOutcome) {
        when (outcome) {
            ShareOutcome.Shared -> onMessage("已打开系统分享面板")
            ShareOutcome.Saved -> onMessage("已保存到相册：${ShareFiles.fileName(view)}")
            is ShareOutcome.Failed -> onMessage(outcome.message)
        }
    }

    CtBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "分享",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                view.link.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            // 5:4 预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 4f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = preview
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "分享卡片预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    rendering -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    else -> Text(
                        "预览图生成失败，仍可直接分享链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (view.maskedResults) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "分享内容不显示票数与百分比，结果请打开投票页查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            val outcome = SharePresenter.shareImage(context, view)
                            busy = false
                            toast(outcome)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("分享图片")
                }
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            val outcome = SharePresenter.saveImage(context, view)
                            busy = false
                            toast(outcome)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("保存图片")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        val shared = SharePresenter.shareText(context, view)
                        if (!shared) {
                            ShareFiles.copyToClipboard(context, view)
                            onMessage("没有可分享的应用，已复制分享文案")
                        } else {
                            onMessage("已打开系统分享面板")
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("分享链接") }
                TextButton(
                    onClick = {
                        ShareFiles.copyToClipboard(context, view)
                        onMessage("已复制分享文案")
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("复制文案")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
