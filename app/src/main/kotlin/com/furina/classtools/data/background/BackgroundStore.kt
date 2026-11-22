package com.furina.classtools.data.background

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.edit
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 自定义背景的**本机存储**（SharedPreferences + App 私有目录）。
 *
 * 隐私与数据边界（红线）：
 * - 图片只存在于 `filesDir/backgrounds/`（App 私有），**不上传、不进 userdata 云同步、不进备份导出**；
 * - 偏好只存「预设 id / 本地文件名 / 模糊档」三个值，**不存绝对路径**（避免外部沙盒路径泄漏/失效）；
 * - 删除背景 = 删偏好 + 删私有目录里的图片文件（两个都要做，避免残留占空间）。
 *
 * 压缩：选中图片后解码（inSampleSize 先粗缩）→ 精确缩放到长边 ≤2048 → JPEG 质量 85 落盘；
 * 同时在内存里解码为 [Bitmap] 供全局背景层使用（大图只在背景层持有 1 份）。
 */
class BackgroundStore(context: Context) {

    private val appContext = context.applicationContext

    /** 偏好文件（与外观偏好同层；禁止改名，否则用户背景设置会丢）。 */
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 专属目录 `filesDir/backgrounds/`（私有）。 */
    private val dir: File get() = File(appContext.filesDir, CtBackgroundImage.DIR_NAME)

    private val _selection = MutableStateFlow(load())

    /** 当前选择（Compose collectAsState 即可全局实时生效）。 */
    val selection: StateFlow<CtBackgroundSelection> = _selection.asStateFlow()

    /** 已解码的背景位图（null = 当前不是自定义图片 / 文件丢失）。 */
    private val _bitmap = MutableStateFlow<Bitmap?>(null)

    /** 背景位图（背景层订阅；切换/删除时自动更新）。 */
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    /** 最近一次失败原因（设置页展示；成功清空）。 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // -------------------------------------------------------------------------
    // 读取
    // -------------------------------------------------------------------------

    /** 同步读取（冷启动首帧镜像用）：缺失/非法一律回退默认暖白（不抛异常、不丢其他设置）。 */
    fun load(): CtBackgroundSelection =
        CtBackgroundSelection.fromStorage(prefs.getString(KEY, null))

    /** 同步的首帧位图解码（仅当已选自定义图片且文件存在；在 IO 线程调用）。 */
    suspend fun loadStartupBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val s = _selection.value
        if (!s.hasImage) return@withContext null
        decodeFile(imageFile(s))?.also { _bitmap.value = it }
    }

    /** 自定义图片的本地文件（**只允许**私有目录内的合法文件名）。 */
    fun imageFile(selection: CtBackgroundSelection = _selection.value): File? {
        val name = selection.customImageFile ?: return null
        if (!CtBackgroundImage.isValidFileName(name)) return null
        val root = dir.canonicalFile
        val file = File(root, name)
        return runCatching {
            // 防路径穿越：规范化后必须仍在私有背景目录内
            val canonical = file.canonicalFile
            if (canonical.path.startsWith(root.path + File.separator) && canonical.isFile) canonical else null
        }.getOrNull()
    }

    /** 当前是否有可用的自定义图片（偏好 + 文件双重校验）。 */
    fun hasUsableImage(): Boolean = imageFile() != null

    // -------------------------------------------------------------------------
    // 写入
    // -------------------------------------------------------------------------

    /** 选择预设背景（不动已保存的自定义图片文件，便于用户切回）。 */
    fun selectPreset(preset: CtBackgroundPreset) {
        val next = CtBackgroundSelection.withPreset(_selection.value, preset)
        persist(next)
    }

    /** 调整自定义图片的模糊半径（0..40dp）。 */
    fun setCustomBlur(blur: Int) {
        val next = _selection.value.copy(
            customBlur = blur.coerceIn(CtBackgroundSelection.MIN_CUSTOM_BLUR, CtBackgroundSelection.MAX_CUSTOM_BLUR),
        )
        persist(next)
    }

    /** 滑杆拖动中的实时预览：只改内存，不写盘；松手再由 [setCustomBlur] 持久化。 */
    fun previewCustomBlur(blur: Int) {
        _selection.value = _selection.value.copy(
            customBlur = blur.coerceIn(CtBackgroundSelection.MIN_CUSTOM_BLUR, CtBackgroundSelection.MAX_CUSTOM_BLUR),
        )
    }

    /**
     * 导入用户从系统照片选择器选中的图片：
     * 解码 → 缩放（长边 ≤2048）→ JPEG 85 落盘 → 写入偏好 → 解码位图。
     *
     * @return true = 成功；失败原因见 [lastError]（不抛给 UI）
     */
    suspend fun importImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        _lastError.value = null
        val decoded = runCatching { decodeScaled(uri) }.getOrNull()
        if (decoded == null) {
            _lastError.value = "无法读取这张图片"
            return@withContext false
        }
        val file = runCatching { writeJpeg(decoded.first) }.getOrNull()
        if (file == null) {
            decoded.first.recycle()
            _lastError.value = "保存图片失败（存储空间不足？）"
            return@withContext false
        }
        // 新图替换旧图：删除除新文件外的所有 bg_*.jpg
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != file.name && f.name.startsWith(CtBackgroundImage.FILE_PREFIX)) {
                    f.delete()
                }
            }
        }
        val next = CtBackgroundSelection.withImage(
            current = _selection.value,
            file = file.name,
            blur = _selection.value.customBlur,
        )
        persist(next)
        _bitmap.value = decoded.first
        true
    }

    /** 删除自定义图片 → 恢复默认（暖白）背景，并删除私有目录里的图片。 */
    suspend fun deleteImage() = withContext(Dispatchers.IO) {
        val current = _selection.value
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith(CtBackgroundImage.FILE_PREFIX)) f.delete()
            }
        }
        val next = CtBackgroundSelection.withoutImage(current)
        persist(next)
        _bitmap.value?.recycle()
        _bitmap.value = null
    }

    /**
     * 启动时恢复位图（进入主界面后调用；失败静默回退预设）。
     *
     * @param capability 当前设备光感能力（用于决定是否需要解码；不解码也能显示图片，只是没有模糊）
     */
    suspend fun restoreBitmap() = withContext(Dispatchers.IO) {
        val s = _selection.value
        if (!s.hasImage) {
            _bitmap.value = null
            return@withContext
        }
        decodeFile(imageFile(s))?.let { _bitmap.value = it }
    }

    private fun persist(next: CtBackgroundSelection) {
        _selection.value = next
        prefs.edit { putString(KEY, next.storageValue) }
    }

    // -------------------------------------------------------------------------
    // 解码 / 压缩
    // -------------------------------------------------------------------------

    /** 只读尺寸（不分配整张位图）。 */
    private fun readBounds(uri: Uri): Pair<Int, Int>? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }.getOrNull()

    /** 解码并缩放到长边 ≤ [CtBackgroundImage.MAX_LONG_EDGE]（ARGB_8888，JPEG 目标无透明度）。 */
    private fun decodeScaled(uri: Uri): Pair<Bitmap, Int>? {
        val bounds = readBounds(uri) ?: return null
        var sample = 1
        while (bounds.first / (sample * 2) >= CtBackgroundImage.MAX_LONG_EDGE &&
            bounds.second / (sample * 2) >= CtBackgroundImage.MAX_LONG_EDGE
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val (tw, th) = CtBackgroundImage.targetSize(raw.width, raw.height)
        if (tw <= 0 || th <= 0) {
            raw.recycle()
            return null
        }
        if (tw == raw.width && th == raw.height) return raw to sample
        val scaled = Bitmap.createScaledBitmap(raw, tw, th, true)
        if (scaled !== raw) raw.recycle()
        return scaled to sample
    }

    /** 落盘：JPEG 质量 85（失败返回 null）。 */
    private fun writeJpeg(bitmap: Bitmap): File? = runCatching {
        val targetDir = dir.apply { mkdirs() }
        val temp = File(targetDir, CtBackgroundImage.FILE_PREFIX + "tmp.jpg")
        temp.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, CtBackgroundImage.JPEG_QUALITY, out)
        }
        val finalFile = File(targetDir, CtBackgroundImage.newFileName(System.currentTimeMillis()))
        if (temp.renameTo(finalFile)) finalFile else {
            temp.delete()
            null
        }
    }.getOrNull()

    /** 从私有目录解码（降采样到屏幕需要的尺寸，避免整张 2048 常驻）。 */
    private fun decodeFile(file: File?): Bitmap? {
        if (file == null || !file.isFile) return null
        return runCatching {
            // ARGB_8888：RenderEffect 模糊在 565 上会有色带；背景只持一份，内存可控。
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    companion object {
        /** 偏好文件名（与外观偏好同层，禁止改名）。 */
        const val FILE: String = "ct_background_prefs"

        /** 键名（持久化契约，禁止改名）。 */
        const val KEY: String = "ct_background_selection"


    }
}

/**
 * [BackgroundStore] 的 CompositionLocal：MainActivity 在主题根节点提供。
 *
 * 预览 / 孤立组件为 null → 背景层用「默认暖白」，设置页回退到入参（预览可控）。
 */
val LocalBackgroundStore = staticCompositionLocalOf<BackgroundStore?> { null }
