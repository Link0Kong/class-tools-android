package com.furina.classtools.data.activity

/**
 * 活动 H5（`rcm.fjtcm.edu.cn/wechat/`）登录事实与双主路径模型（2026-11-16 task-73 实测结论）。
 *
 * ## 实测事实（emulator-5560，2026-11-16）
 *
 * 1. 登录页是「学校账号登录」：上传真实头像（`Qiniu` 组件）+ 身份证后六位 + 学号，另有「我是教师」切换。
 * 2. **头像上传不属于 `<input type=file>`**：组件点击走 WeChat JSSDK `wx.chooseImage` →
 *    `wx.uploadImage` → 学校后端 `qiniuFetch`（`S.a.qiniuFetch(r, no, "wx/2ndclass/...", serverId)`）。
 *    App 内 WebView 没有微信客户端 JSSDK 桥，点击头像**无任何反应**（实测：无 file input、无
 *    onShowFileChooser 回调、无请求；CDP 里 `toongine`/`AEJSBridge` 都不是微信原生桥）。
 * 3. 不传头像点登录 → 客户端提示「请上传头像」；设置一个 http 头像 URL 后再登录 →
 *    `POST https://stmapi.fjtcm.edu.cn/basic/user/student/bind` 返回 **HTTP 500「openId不能为空」**。
 *    即**学校账号绑定也强制要求微信 openId**（教师登录同理传 openId 参数）。
 * 4. openId 来源（H5 源码证据）：`codePath = https://open.weixin.qq.com/connect/oauth2/authorize`
 *    `?appid={APPID}&redirect_uri={REDIRECT_URI}&response_type=code&scope=snsapi_userinfo&state=dskt`；
 *    H5 前端用学校后端下发的 `wechatConfig.appId` + 微信回调的 `code` 调后端换 `access_token/openId`。
 *    **微信 oauth2 授权只能在微信客户端内完成**；App 没有学校开放平台签名/回调域名，不能合法取得 openId。
 *
 * ## 结论：双主路径（诚实降级，禁止伪造）
 *
 * - **主路径 1（推荐）**：在微信内打开活动 H5 —— 微信内可用微信身份，也可用学校账号
 *   （头像上传 + 身份证后六位 + 学号）完成登录；
 * - **主路径 2（受限）**：App 内打开官方登录页仅用于查看/备用；该页依赖微信 openId 与微信头像上传，
 *   App 内无法完成（会提示「openId不能为空」/「请上传头像」），UI 必须明确说明，禁止伪造 openId/登录态。
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

    // ---- 双主路径（UI 文案；避免任何「App 内可微信登录」的误导）----------------

    /** 主路径 1：在微信内打开活动页（推荐）。 */
    const val TITLE_OPEN_IN_WECHAT: String = "在微信内打开活动页（推荐）"

    /** 主路径 1 说明：微信内两种登录方式都能完成。 */
    const val DESC_OPEN_IN_WECHAT: String =
        "微信内可用微信身份登录，也可用学校账号（上传头像 + 身份证后六位 + 学号）登录"

    /** 主路径 2：App 内打开官方登录页（受限，仅查看/备用）。 */
    const val TITLE_SCHOOL_LOGIN_LIMITED: String = "学校账号登录（App 内打开登录页）"

    /** 主路径 2 说明：明确 App 内无法完成登录，避免用户反复尝试。 */
    const val DESC_SCHOOL_LOGIN_LIMITED: String =
        "该登录页依赖微信 openId 与微信头像上传，App 内无法提供（会提示「openId不能为空」/「请上传头像」）；" +
            "请优先用「在微信内打开」完成登录"

    /** 微信身份（openId）限制说明（实测结论，不伪造）。 */
    const val HINT_OPENID_LIMIT: String =
        "活动登录需要微信 openId：openId 只能在微信客户端内由学校 H5 的微信授权（oauth2 authorize + code 换取）取得，" +
            "App 无法合法获取"

    /** 明确降级：微信内登录与 App 不共享身份/Cookie。 */
    const val HINT_DEGRADED: String =
        "微信内完成登录后与 App 的 Cookie 不共享：回到 App 不会自动变成已登录，App 也不会伪造登录态"

    const val BTN_OPEN_IN_WECHAT: String = "在微信中打开"
    const val BTN_COPY_LINK: String = "复制链接"
    const val BTN_OPEN_SCHOOL_LOGIN: String = "打开学校登录页"

    const val MSG_LINK_COPIED: String = "活动链接已复制，可在微信里粘贴打开"
    const val MSG_COPY_FAILED: String = "复制链接失败，请手动打开微信搜索学校活动页"
    const val MSG_WECHAT_OPENED: String = "正在打开微信：把已复制的链接粘贴到微信中打开即可"
    const val MSG_WECHAT_MISSING: String = "未能唤起微信：链接已复制，请手动在微信中粘贴打开"
    const val MSG_WECHAT_UNKNOWN_AFTER_FAIL: String =
        "无法确认/唤起微信：链接已复制，请在微信中粘贴打开"

    /** 微信 OAuth 页面出现时的提示（保持原有兜底语义）。 */
    const val MSG_OAUTH_ONLY_IN_WECHAT: String =
        "该授权需要微信客户端：请点「在微信中打开」，在微信里完成登录；App 内不会假装已登录"

    /** 登录页出现「需要微信身份/头像」提示时的 App 内引导（实测错误：openId不能为空 / 请上传头像）。 */
    const val MSG_PAGE_REQUIRES_WECHAT: String =
        "该登录页需要微信身份/头像（App 内无法提供），请点「在微信中打开」在微信里完成登录"

    /**
     * 只读检测登录页是否出现「需要微信能力」的提示：
     * - `openid`：服务端返回「openId不能为空」（学校账号绑定也要求微信身份）；
     * - `avatar`：客户端提示「请上传头像」（头像上传依赖微信 JSSDK，App 内无反应）。
     * 用于 App 内 UI 给出「去微信内打开」的明确引导，不做任何自动提交。
     */
    const val LOGIN_REQUIREMENT_JS: String =
        "(function(){try{var t=(document.body&&document.body.innerText)||'';" +
            "if(t.indexOf('openId不能为空')>=0){return 'openid';}" +
            "if(t.indexOf('请上传头像')>=0){return 'avatar';}" +
            "return '';}catch(e){return '';}})()"
}
