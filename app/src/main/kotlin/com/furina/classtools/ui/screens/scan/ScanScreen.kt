package com.furina.classtools.ui.screens.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.furina.classtools.AppContainer
import com.furina.classtools.data.draw.DrawProofVerified
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.scan.QrLoginResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.scan.QrPayload
import com.furina.classtools.features.scan.QrPayloadParser
import com.furina.classtools.ui.components.ctTopBarRaw
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

private sealed interface ScanState {
    data object Scanning : ScanState
    data class LoginConfirm(val login: QrPayload.Login) : ScanState
    data class Busy(val text: String) : ScanState
    data class LoginDone(val name: String) : ScanState
    data class VerifyReady(val verified: DrawProofVerified) : ScanState
    data class Failed(val title: String, val message: String) : ScanState
    data class Unknown(val text: String) : ScanState
}

/**
 * 扫一扫：一个入口，两种用途。
 * - 扫描网页登录二维码（`#qr=<ticket>`）→ 给电脑授权登录（App 不接收 token）；
 * - 扫描抽签存证二维码（`#verify=<id>`）→ 云端取回存证并用 SHA-256 + draw-v1 在手机本地复算。
 *
 * 相机权限只在进入本页时申请；拒绝只影响扫码，不阻塞其它功能。
 */
@Composable
fun ScanScreen(
    container: AppContainer,
    session: Session,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var permissionAsked by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        permissionAsked = true
    }

    LaunchedEffect(Unit) {
        if (!granted && !permissionAsked) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    BackHandler {
        if (state is ScanState.Scanning) onBack() else state = ScanState.Scanning
    }

    fun handleText(raw: String) {
        if (state !is ScanState.Scanning) return
        when (val payload = QrPayloadParser.parse(raw)) {
            is QrPayload.Login -> state = ScanState.LoginConfirm(payload)
            is QrPayload.DrawVerify -> {
                state = ScanState.Busy("正在读取存证…")
                scope.launch {
                    state = when (val result = container.drawProofRepository.verify(payload.recordId)) {
                        is ApiResult.Success -> ScanState.VerifyReady(result.value)
                        is ApiResult.Failure -> ScanState.Failed("核验失败", result.error.displayMessage)
                    }
                }
            }
            is QrPayload.Unknown -> state = ScanState.Unknown(payload.text)
        }
    }

    fun confirmLogin(login: QrPayload.Login) {
        state = ScanState.Busy("正在确认…")
        scope.launch {
            val result: QrLoginResult = container.qrLoginRepository.authorize(
                ticket = login.ticket,
                harmony = login.harmony,
                classToken = session.token,
            )
            state = if (result.ok) {
                ScanState.LoginDone(result.name)
            } else {
                ScanState.Failed("登录确认失败", result.message.ifBlank { "请稍后重试" })
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state is ScanState.Scanning) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        when (val current = state) {
            is ScanState.Scanning -> {
                if (granted) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onDecoded = ::handleText,
                        onCamera = { camera = it },
                        onError = { message -> state = ScanState.Failed("无法使用相机", message) },
                    )
                } else {
                    PermissionPanel(
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            }
                        },
                    )
                }
                ScannerOverlay(
                    torchOn = torchOn,
                    torchAvailable = camera != null,
                    onTorch = {
                        torchOn = !torchOn
                        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
                    },
                    onClose = onBack,
                )
            }

            is ScanState.LoginConfirm -> ResultPage(title = "确认登录", onBack = onBack) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("允许这台电脑登录班级工具？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (current.login.harmony) "鸿蒙扫码登录" else "通用扫码登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { state = ScanState.Scanning }) { Text("取消") }
                            Button(
                                onClick = { confirmLogin(current.login) },
                                modifier = Modifier.weight(1f),
                            ) { Text("确认登录") }
                        }
                    }
                }
            }

            is ScanState.Busy -> ResultPage(title = "扫一扫", onBack = onBack) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(current.text, style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ScanState.LoginDone -> ResultPage(title = "扫码登录", onBack = onBack) {
                StatusCard(
                    ok = true,
                    title = "已允许电脑登录",
                    message = current.name.ifBlank { "电脑端将自动进入班级" },
                    primary = "完成",
                    onPrimary = onBack,
                    secondary = "继续扫码",
                    onSecondary = { state = ScanState.Scanning },
                )
            }

            is ScanState.VerifyReady -> VerifyResult(
                verified = current.verified,
                onAgain = { state = ScanState.Scanning },
                onBack = onBack,
            )

            is ScanState.Failed -> ResultPage(title = "扫一扫", onBack = onBack) {
                StatusCard(
                    ok = false,
                    title = current.title,
                    message = current.message,
                    primary = "重新扫描",
                    onPrimary = { state = ScanState.Scanning },
                    secondary = "关闭",
                    onSecondary = onBack,
                )
            }

            is ScanState.Unknown -> ResultPage(title = "扫一扫", onBack = onBack) {
                StatusCard(
                    ok = false,
                    title = "无法识别的二维码",
                    message = current.text.ifBlank { "请扫描班级工具网页二维码或抽签核验二维码" },
                    primary = "重新扫描",
                    onPrimary = { state = ScanState.Scanning },
                    secondary = "关闭",
                    onSecondary = onBack,
                )
            }
        }
    }
}

@Composable
private fun ResultPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ctTopBarRaw(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun StatusCard(
    ok: Boolean,
    title: String,
    message: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(46.dp),
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onSecondary) { Text(secondary) }
                Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primary) }
            }
        }
    }
}

@Composable
private fun VerifyResult(
    verified: DrawProofVerified,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val record = verified.record
    val timeText = remember(record.atMillis) {
        runCatching {
            Instant.ofEpochMilli(record.atMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA))
        }.getOrDefault("—")
    }
    val resultText = record.result.joinToString("、").ifBlank { "—" }
    val params = buildString {
        if (record.params.rangeEnd > 0) append("范围 ${record.params.rangeStart}–${record.params.rangeEnd} · ")
        append("抽 ${record.params.count} 个")
        append(if (record.params.allowDuplicates) " · 允许重复" else " · 不重复")
        if (record.params.rosterN > 0) append(" · 名单 ${record.params.rosterN} 人")
    }
    ResultPage(title = "存证核验", onBack = onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("存证记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                VerifyLine("存证编号", record.id)
                VerifyLine("存证时间", "$timeText（服务器）")
                VerifyLine("抽签人", buildString {
                    append(record.operatorName.ifBlank { "—" })
                    record.operatorSeat?.let { append("（座号 ").append(it).append("）") }
                })
                VerifyLine("今日抽签", "第 ${record.seqDay} 次（全班共 ${record.todayCount} 次）")
                VerifyLine("参数", params)
                VerifyLine("初始结果", resultText)
                VerifyLine("剔除重抽", "${record.eventCount} 次")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerifyBadge("哈希", verified.hashOk)
                    VerifyBadge("复算", verified.replayOk)
                }
                Text(
                    "哈希 ${record.hash.take(16)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onBack) { Text("完成") }
                    Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("继续扫码") }
                }
            }
        }
    }
}

@Composable
private fun VerifyLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.width(76.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun VerifyBadge(label: String, ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Text(
            "$label ${if (ok) "✓ 通过" else "✗ 不一致"}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("需要相机权限", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "仅用于扫描二维码，不拍照、不上传",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) { Text("授权相机") }
            TextButton(onClick = onOpenSettings) {
                Text("去设置", color = Color.White)
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    torchOn: Boolean,
    torchAvailable: Boolean,
    onTorch: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
            }
            Text(
                "扫一扫",
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onTorch, enabled = torchAvailable) {
                Icon(
                    if (torchOn) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                    contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                    tint = if (torchAvailable) Color.White else Color.White.copy(alpha = 0.35f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                ),
        )
        Text(
            "对准二维码",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp),
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onDecoded: (String) -> Unit,
    onCamera: (Camera?) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnError by rememberUpdatedState(onError)
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnCamera(null)
            executor.shutdown()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                {
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(1280, 720))
                            .build()
                        analysis.setAnalyzer(executor) { image ->
                            try {
                                if (!handled.get()) {
                                    val text = decodeQr(image, reader)
                                    if (!text.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                        previewView.post { currentOnDecoded(text) }
                                    }
                                }
                            } catch (_: Throwable) {
                                // 单帧解码失败不打断预览
                            } finally {
                                image.close()
                            }
                        }
                        provider.unbindAll()
                        val cam = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        currentOnCamera(cam)
                    } catch (e: Throwable) {
                        currentOnError(e.message ?: "相机启动失败")
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}

private fun decodeQr(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val data = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width) {
        buffer.rewind()
        buffer.get(data, 0, minOf(data.size, buffer.remaining()))
    } else {
        val row = ByteArray(rowStride)
        buffer.rewind()
        for (y in 0 until height) {
            val pos = y * rowStride
            if (pos >= buffer.limit()) break
            buffer.position(pos)
            val len = minOf(rowStride, buffer.limit() - pos)
            buffer.get(row, 0, len)
            System.arraycopy(row, 0, data, y * width, minOf(width, len))
        }
    }
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } finally {
        reader.reset()
    }
}
