package com.furina.classtools.data.notify

import android.app.Notification
import android.os.Build
import androidx.core.app.NotificationCompat

internal fun NotificationCompat.Builder.buildLiveUpdate(): Notification {
    val standard = build()
    if (Build.VERSION.SDK_INT != 36 || standard.hasPromotableCharacteristics()) return standard

    // The initial Android 16 framework requires colorized, later revisions require the opposite.
    // This asks the installed framework about the template; channel and promotion settings stay with System UI.
    val legacy = setColorized(true).build()
    return if (legacy.hasPromotableCharacteristics()) legacy else standard
}
