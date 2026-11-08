package com.furina.classtools.features.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导出结果：保存成功 / 需要调用方拉起分享 / 失败。 */
sealed interface ExportOutcome {
    data class Saved(val uri: Uri) : ExportOutcome
    data class Share(val intent: Intent) : ExportOutcome
    data class Failed(val message: String) : ExportOutcome
}

/**
 * 导出卡片落盘/分享编排：
 * 1) 后台渲染 Bitmap（android.graphics，不占主线程）；
 * 2) Android 10+ 写入相册 `Pictures/班级工具站`；
 * 3) 无法写相册（<10）时退回分享 Intent（FileProvider）。
 */
object ExportPresenter {

    suspend fun renderAndSave(context: Context, spec: ExportCardSpec): ExportOutcome {
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { ExportCardRenderer().render(spec) }.getOrNull()
        } ?: return ExportOutcome.Failed("生成图片失败")

        val saved = withContext(Dispatchers.IO) {
            runCatching { ExportCardFiles.saveToGallery(context, bitmap, spec.file) }.getOrNull()
        }
        if (saved != null) return ExportOutcome.Saved(saved)

        val intent = withContext(Dispatchers.IO) {
            runCatching { ExportCardFiles.shareIntent(context, bitmap, spec.file) }.getOrNull()
        }
        return if (intent != null) {
            ExportOutcome.Share(intent)
        } else {
            ExportOutcome.Failed("本机无法写入相册，请检查存储空间")
        }
    }
}
