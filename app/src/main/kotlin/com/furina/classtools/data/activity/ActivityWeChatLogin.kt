package com.furina.classtools.data.activity

/**
 * 活动 H5（`rcm.fjtcm.edu.cn/wechat/`）的微信登录引导（2026-11-16 用户反馈）。
 *
 * ## 根因
 *
 * 学校第二课堂 H5 是「微信内应用」：它启动时若 `localStorage.wxUser` 缺失/无微信身份，会跳
 * `open.weixin.qq.com/connect/oauth2/authorize` 做微信授权（openId 由微信客户端在 OAuth
 * 回调里提供）。App 内 WebView **没有微信客户端身份，也拿不到 openId**，只能靠
 * [ActivityWebSession.SEED_WX_SESSION_JS] 的空 openId 环境桩让 H5 回落到学校统一身份认证登录页。
 *
 * 因此这里只做**诚实的引导 + 降级**：
 * - 检测本机是否（可能）安装了微信（Android 11+ 有应用可见性限制 → 允许「无法确认」）；
 * - 提供「在微信中打开」：复制活动 H5 链接并尝试唤起微信客户端（有版本支持时直接把链接交给
 *   微信内置浏览器），用户在微信里用微信身份完成 H5 登录；
 * - 提供「复制链接」：手动粘贴到微信打开；
 * - **明确说明**：微信与 App 的 Cookie/身份不共享，微信内登录后回到 App 不会自动变成已登录，
 *   仍需在 App 内学校登录页完成登录；App **不会伪造/假装已登录**（`lgInfo` 仍只认 H5 真实会话）。
 *
 * 本文件不依赖 Android（检测/拉起 Intent 在 `ActivityScreen` 内完成），可 JVM 单测。
 */
object ActivityWeChatLogin {

    /** 微信客户端包名（`getLaunchIntentForPackage` / 显式 Intent 用）。 */
    const val WECHAT_PACKAGE: String = "com.tencent.mm"

    /** 微信主客户端 scheme（仅在无法直接打开 H5 时用于拉起微信）。 */
    const val WEIXIN_SCHEME: String = "weixin://"

    /** 本机微信客户端可见性（Android 11+ 可能无法确认）。 */
    enum class Presence {
        /** 系统能解析到微信客户端。 */
        INSTALLED,

        /** 系统明确解析不到微信客户端（Android 10 及以下 / 已声明可见性）。 */
        NOT_INSTALLED,

        /** Android 11+ 应用可见性限制下无法直接确认（不代表未安装）。 */
        UNKNOWN,
    }

    /**
     * 由「系统是否解析到微信」+ 系统 API 级别推出可见性。
     *
     * Android 11（API 30）起 package visibility 会限制 `resolveActivity` / `getPackageInfo`
     * 等查询（未声明 `<queries>` 时看不到微信），因此「解析不到」不能断言未安装。
     */
    fun presence(wechatResolved: Boolean, sdkInt: Int): Presence = when {
        wechatResolved -> Presence.INSTALLED
        sdkInt < 30 -> Presence.NOT_INSTALLED
        else -> Presence.UNKNOWN
    }

    /** 微信检测状态文案（不夸大：UNKNOWN 时明确说无法确认）。 */
    fun statusText(presence: Presence): String = when (presence) {
        Presence.INSTALLED -> "已检测到微信客户端"
        Presence.NOT_INSTALLED -> "未检测到微信客户端"
        Presence.UNKNOWN -> "无法直接确认是否安装微信（系统限制），点按钮会尝试打开"
    }

    /** 微信内打开用的活动 H5 地址（与 App 内 WebView 同一个带学校编号的登录入口）。 */
    fun shareUrl(): String = ActivityWebSession.SPA_LOGIN_URL

    // ---- 文案（界面保持简短；明确降级，不宣称已登录）----------------------------

    /** 微信登录入口标题。 */
    const val TITLE_WECHAT_LOGIN: String = "微信登录"

    /** 微信身份（openId）限制说明。 */
    const val HINT_OPENID_LIMIT: String =
        "活动 H5 的微信身份（openId）只能在微信客户端内获取，App 内无法直接获得"

    /** 明确降级：微信内登录后不会把身份/Cookie 带回 App。 */
    const val HINT_DEGRADED: String =
        "微信内完成登录后与 App 的 Cookie 不共享：回到 App 仍需用学校账号登录，不会自动变成已登录"

    const val BTN_OPEN_IN_WECHAT: String = "在微信中打开"
    const val BTN_COPY_LINK: String = "复制链接"

    const val MSG_LINK_COPIED: String = "活动链接已复制，可在微信里粘贴打开"
    const val MSG_COPY_FAILED: String = "复制链接失败，请手动打开微信搜索学校活动页"
    const val MSG_WECHAT_OPENED: String = "正在打开微信：把已复制的链接粘贴到微信中打开即可"
    const val MSG_WECHAT_MISSING: String = "未能唤起微信：链接已复制，请手动在微信中粘贴打开"
    const val MSG_WECHAT_UNKNOWN_AFTER_FAIL: String =
        "无法确认/唤起微信：链接已复制，请在微信中粘贴打开"

    /** 微信 OAuth 页面出现时的提示（保持原有兜底语义）。 */
    const val MSG_OAUTH_ONLY_IN_WECHAT: String =
        "该授权需要微信客户端：可点「在微信中打开」，微信内登录后回 App 再用学校账号登录"
}
