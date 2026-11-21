# Live Updates polish: transient result notification + availability helper.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$base = Join-Path $root 'app\src\main\kotlin\com\furina\classtools'

# 1) LiveUpdateNotifier: expose availability + transient result notification
$f = Join-Path $base 'data\notify\LiveUpdateNotifier.kt'
$t = Get-Content $f -Raw
$t = $t.Replace("    private const val DEBUG_NOTIFICATION_ID = 7403", "    private const val DEBUG_NOTIFICATION_ID = 7403`n    private const val PPT_RESULT_NOTIFICATION_ID = 7411")
$t = $t.Replace("    private fun canNotify(context: Context): Boolean {", "    /** 供 UI 判断「实时活动是否会显示」；未授权通知时可在页面上给出解释。 */`n    fun isAvailable(context: Context): Boolean = canNotify(context)`n`n    private fun canNotify(context: Context): Boolean {")
$t = $t.Replace("    fun cancelPptUpload(context: Context) {", @"
    /** 上传成功/失败后的短暂结果通知（非实时活动，自动消失）。 */
    fun postPptFinished(context: Context, success: Boolean, message: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(if (success) "演示上传完成" else "演示上传失败")
            .setContentText(message.ifBlank { if (success) "已提交转换，可在演示列表查看进度" else "请稍后重试" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { if (success) "已提交转换，可在演示列表查看进度" else "请稍后重试" }))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openToolIntent(context, "ppt"))
        runCatching {
            NotificationManagerCompat.from(context).notify(PPT_RESULT_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelPptUpload(context: Context) {
"@)
$t = $t.Replace("    fun cancelPptUpload(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PPT_NOTIFICATION_ID) }
    }", "    fun cancelPptUpload(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PPT_NOTIFICATION_ID) }
    }")
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

# 2) AppContainer observer: post transient result on DONE/FAILED
$f = Join-Path $base 'AppContainer.kt'
$t = Get-Content $f -Raw
$old = @"
                    when (progress.phase) {
                        PptUploadPhase.IDLE,
                        PptUploadPhase.DONE,
                        PptUploadPhase.FAILED,
                        PptUploadPhase.CANCELLED,
                        -> LiveUpdateNotifier.cancelPptUpload(appContext)

                        else -> LiveUpdateNotifier.postPptUpload(appContext, progress)
                    }
"@
$new = @"
                    when (progress.phase) {
                        PptUploadPhase.DONE -> {
                            LiveUpdateNotifier.cancelPptUpload(appContext)
                            LiveUpdateNotifier.postPptFinished(appContext, success = true, message = progress.message)
                        }

                        PptUploadPhase.FAILED -> {
                            LiveUpdateNotifier.cancelPptUpload(appContext)
                            LiveUpdateNotifier.postPptFinished(appContext, success = false, message = progress.message)
                        }

                        PptUploadPhase.IDLE,
                        PptUploadPhase.CANCELLED,
                        -> LiveUpdateNotifier.cancelPptUpload(appContext)

                        else -> LiveUpdateNotifier.postPptUpload(appContext, progress)
                    }
"@
if ($t.Contains($old)) { $t = $t.Replace($old, $new); "appcontainer patched" } else { "appcontainer anchor not found" }
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline
"notifier: $((Select-String -Path (Join-Path $base 'data\notify\LiveUpdateNotifier.kt') -Pattern 'postPptFinished|fun isAvailable').Count)"
