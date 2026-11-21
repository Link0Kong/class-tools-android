package com.furina.classtools.features.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.furina.classtools.features.export.ExportCardFiles
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 分享结果（UI 只据此给 Toast 文案，不抛异常）。 */
sealed interface ShareOutcome {
    /** 已拉起系统分享面板。 */
    data object Shared : ShareOutcome

    /** 已保存到相册。 */
    data object Saved : ShareOutcome

    /** 分享面板/保存失败（无可用应用、存储不可写等）。 */
    data class Failed(val message: String) : ShareOutcome
}

/**
 * 分享文件/文案的落盘与 Intent 构造（Android 侧；纯数据见 [ShareSpecs]）。
 *
 * - 图片：`cache/share/<kind>_<stamp>.png` → [FileProvider] `content://…fileprovider/share/…`
 *   （`ct_file_paths.xml` 注册 `share/`）；分享时同时带 `EXTRA_TEXT`（标题 + 链接），
 *   应用只接收纯文本时也有内容；
 * - 保存到相册：复用 [ExportCardFiles.saveToGallery]（Android 10+ MediaStore，免存储权限）；
 * - 文案：`ACTION_SEND text/plain`（微信/QQ/短信都能收），失败时由 UI 复制到剪贴板兜底。
 */
object ShareFiles {

    private const val CACHE_DIR = "share"

    private val stampFormat: SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

    /** 稳定文件名（ASCII；不含姓名/学号等隐私字段）。 */
    fun fileName(view: ShareCardView, nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = stampFormat.format(Date(nowMillis))
        return "share_${view.kind.name.lowercase(Locale.US)}_$stamp.png"
    }

    /** 写入 `cache/share/`（FileProvider 可读）。 */
    fun writeCacheFile(context: Context, bitmap: Bitmap, fileName: String): File? = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }.getOrNull()

    /** 图片分享 Intent（含标题与链接文本）。 */
    fun imageIntent(context: Context, bitmap: Bitmap, view: ShareCardView): Intent? {
        val file = writeCacheFile(context, bitmap, fileName(view)) ?: return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, view.link.title)
            putExtra(Intent.EXTRA_TEXT, view.link.message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 纯文本分享 Intent（分享链接/文案）。 */
    fun textIntent(view: ShareCardView): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, view.link.title)
        putExtra(Intent.EXTRA_TEXT, view.link.message)
    }

    /** 复制分享文案到剪贴板（无分享面板时的兜底）。 */
    fun copyToClipboard(context: Context, view: ShareCardView) {
        runCatching {
            val manager = context.getSystemService(ClipboardManager::class.java) ?: return
            manager.setPrimaryClip(ClipData.newPlainText(view.link.title, view.link.message))
        }
    }
}

/** 分享编排（UI 调用；内部渲染 + 落盘 + 拉起 Intent）。 */
object SharePresenter {

    /** 渲染 5:4 分享卡 → 拉起系统分享面板。 */
    suspend fun shareImage(
        context: Context,
        view: ShareCardView,
        renderer: ShareCardRenderer = ShareCardRenderer(),
    ): ShareOutcome {
        val bitmap = runCatching { renderer.render(view) }.getOrNull()
            ?: return ShareOutcome.Failed("生成分享图失败")
        val intent = ShareFiles.imageIntent(context, bitmap, view)
            ?: return ShareOutcome.Failed("无法写入分享图")
        val started = runCatching {
            context.startActivity(Intent.createChooser(intent, "分享图片"))
            true
        }.getOrDefault(false)
        return if (started) ShareOutcome.Shared else ShareOutcome.Failed("没有可分享的应用")
    }

    /** 渲染 → 保存到相册（Android 10+；低版本降级为分享面板）。 */
    suspend fun saveImage(
        context: Context,
        view: ShareCardView,
        renderer: ShareCardRenderer = ShareCardRenderer(),
    ): ShareOutcome {
        val bitmap = runCatching { renderer.render(view) }.getOrNull()
            ?: return ShareOutcome.Failed("生成分享图失败")
        val uri = runCatching { ExportCardFiles.saveToGallery(context, bitmap, ShareFiles.fileName(view)) }
            .getOrNull()
        if (uri != null) return ShareOutcome.Saved
        // Android 9 及以下：相册写入需要存储权限，改为拉起分享面板（与导出卡口径一致）。
        val intent = ShareFiles.imageIntent(context, bitmap, view)
            ?: return ShareOutcome.Failed("本机无法保存图片")
        val started = runCatching {
            context.startActivity(Intent.createChooser(intent, "分享图片"))
            true
        }.getOrDefault(false)
        return if (started) ShareOutcome.Shared else ShareOutcome.Failed("本机无法保存图片")
    }

    /** 分享文案/链接（无应用接收时返回 false，由 UI 复制兜底）。 */
    fun shareText(context: Context, view: ShareCardView): Boolean = runCatching {
        context.startActivity(Intent.createChooser(ShareFiles.textIntent(view), "分享到"))
        true
    }.getOrDefault(false)
}
