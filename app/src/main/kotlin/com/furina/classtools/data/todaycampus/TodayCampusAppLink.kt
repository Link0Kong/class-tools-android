package com.furina.classtools.data.todaycampus

/**
 * 「打开今日校园 App」的纯逻辑（Intent 判定 + 未安装回退判定，无 Android 依赖、可 JVM 单测）。
 *
 * UI 侧只负责：
 * 1. [APP_PACKAGE] 能拿到启动 Intent → 拉起 App；
 * 2. 拿不到（含 Android 11+ 包可见性限制）→ 再尝试一次隐式启动；
 * 3. 仍失败 → 回退 App 内 WebView 打开融合门户（[MSG_NOT_INSTALLED] 提示）。
 *
 * 红线：不查询/上传任何 App 数据，只做包名 Intent 判定与回退。
 */
object TodayCampusAppLink {

    /** 今日校园 App 包名（金智校园 App）。 */
    const val APP_PACKAGE: String = "com.wisedu.cpdaily"

    /** 启动 Intent action（与 `Intent.ACTION_MAIN` 的字符串值一致）。 */
    const val ACTION_MAIN: String = "android.intent.action.MAIN"

    /** 启动 Intent category（与 `Intent.CATEGORY_LAUNCHER` 的字符串值一致）。 */
    const val CATEGORY_LAUNCHER: String = "android.intent.category.LAUNCHER"

    /** 未安装 / 无法拉起时的提示（随后回退融合门户）。 */
    const val MSG_NOT_INSTALLED: String = "未安装今日校园 App，已打开融合门户"

    /** 拉起目标判定。 */
    enum class LaunchTarget {
        /** 已在能拿到启动 Intent 的情况下，直接拉起今日校园 App。 */
        APP,

        /** 未探测到启动入口：尝试隐式启动，失败后回退 App 内融合门户。 */
        PORTAL_FALLBACK,
    }

    /** 根据「是否拿到 App 启动 Intent」判定拉起路径。 */
    fun targetFor(appLaunchable: Boolean): LaunchTarget =
        if (appLaunchable) LaunchTarget.APP else LaunchTarget.PORTAL_FALLBACK

    /** 包名是否可安全用于 Intent（非空、含点、无空白字符）。 */
    fun isLaunchablePackage(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && pkg.contains('.') && pkg.none { it.isWhitespace() }
}
