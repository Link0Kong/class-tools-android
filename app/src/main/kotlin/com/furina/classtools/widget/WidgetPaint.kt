package com.furina.classtools.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.furina.classtools.R
import com.furina.classtools.ui.theme.appColorScheme

/** Resolve against the app's appearance even when the launcher uses the opposite mode. */
internal class WidgetPaint(context: Context, palette: WidgetPalette, dark: Boolean, monet: Boolean, followSystem: Boolean) {
    private val local = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    })
    private val scheme = appColorScheme(local, dark, monet)
    private val automatic = palette == WidgetPalette.SYSTEM
    private val resourceColors = automatic && monet && followSystem && android.os.Build.VERSION.SDK_INT >= 31
    val accent = if (automatic) scheme.primary.toArgb() else local.getColor(palette.accent)
    val ink = if (automatic) scheme.onSurface.toArgb() else local.getColor(palette.ink)
    private val paper = if (automatic) scheme.surfaceContainerLow.toArgb() else local.getColor(when (palette) {
        WidgetPalette.BLUE -> R.color.widget_blue_bg
        WidgetPalette.AMBER -> R.color.widget_amber_bg
        WidgetPalette.LILAC -> R.color.widget_lilac_bg
        WidgetPalette.SYSTEM -> R.color.widget_paper
    })
    val tones = if (automatic) listOf(accent, scheme.tertiary.toArgb(), scheme.secondary.toArgb(), scheme.onSurfaceVariant.toArgb())
        else listOf(accent, ColorUtils.blendARGB(accent, ink, .22f), ColorUtils.blendARGB(accent, ink, .45f), ink)
    private val priorityTones = listOf(scheme.error.toArgb(),
        if (dark) 0xFFF1BC78.toInt() else 0xFF86551F.toInt(),
        if (dark) 0xFFA4C7F1.toInt() else 0xFF345E84.toInt(),
        if (dark) 0xFFA5CFB4.toInt() else 0xFF416D50.toInt())
    fun quadrant(index: Int) = ColorUtils.blendARGB(priorityTones[index], accent, .18f)
    fun fill(tone: Int): Int = ColorUtils.blendARGB(paper, tone, .12f)
    fun lane(line: WidgetLine, kind: WidgetKind): Int = when {
        line.priority <= 1 && (line.destination == "hw" || line.destination == "class-homework") -> scheme.error.toArgb()
        line.starred || line.destination == "class-homework" -> tones[1]
        line.destination == "nt" || kind == WidgetKind.NOTICES -> tones[2]
        else -> accent
    }
    fun text(view: RemoteViews, id: Int, color: Int) {
        val resource = when (color) {
            ink -> R.color.widget_wallpaper_ink
            accent -> R.color.widget_wallpaper_accent
            else -> 0
        }
        if (resourceColors && resource != 0 && android.os.Build.VERSION.SDK_INT >= 31) view.setColor(id, "setTextColor", resource)
        else view.setTextColor(id, color)
    }
    fun paintPaper(view: RemoteViews) {
        if (resourceColors && android.os.Build.VERSION.SDK_INT >= 31) view.setColor(R.id.widget_backplate, "setColorFilter", R.color.widget_wallpaper_paper)
        else view.setInt(R.id.widget_backplate, "setColorFilter", paper)
    }
    fun band(view: RemoteViews, background: Int, rule: Int, tone: Int) {
        view.setInt(background, "setColorFilter", fill(tone))
        view.setInt(rule, "setColorFilter", tone)
    }
}
