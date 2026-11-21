package com.furina.classtools.ui.screens.express

import com.furina.classtools.ui.components.CtToastBus

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.express.ExpressCatalog
import com.furina.classtools.data.express.ExpressTarget
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 「快递」工具：一键打开拼多多/淘宝/菜鸟/京东的取件码/身份码页面。
 * 只负责打开官方 App 或官方 H5，不读取/保存任何快递单号或个人信息。
 */
@Composable
fun ExpressScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ctTopBarRaw(
                title = "快递",
                onBack = onBack,
                subtitle = "",
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "校区快递站出示身份码 / 取件码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                items(ExpressCatalog.targets, key = { it.id }) { target ->
                    ExpressCard(target = target, onClick = { openExpress(context, target) })
                }
                item {
                    Text(
                        text = "未安装对应 App 时自动改用官方网页；App 不保存任何取件信息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressCard(target: ExpressTarget, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(CtRadius.tile),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = target.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 打开官方 App 页面；未安装时使用官方 H5（都不做业务数据读写）。 */
internal fun openExpress(context: Context, target: ExpressTarget): Boolean {
    val uri = Uri.parse(target.url)
    if (target.mainActivity != null) {
        runCatching {
            val intent = Intent().apply {
                component = ComponentName(target.packageName, target.mainActivity)
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onSuccess { return true }
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(target.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }.onFailure { e ->
        if (e is ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(target.webFallback))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            }
        }
    }
    CtToastBus.show("未找到可用应用，已尝试网页打开")
    return false
}


