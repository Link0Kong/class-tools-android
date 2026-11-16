package com.furina.classtools.features.chsi

/**
 * 学信网入口模型与静态目录（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §1/§3）。
 *
 * 纯 Kotlin，无 Compose/Android 依赖，可 JVM 单测。
 *
 * 红线（规格 §0）：学信网账号密码**不预填、不保存、不上传**；学信网有短信/验证码/实人核验，
 * App 只打开官方页面由用户本人手动登录；提取的数据仅内存展示，不落盘/不同步/不打日志。
 */

/** 一次官方页面提取的原始数据（由注入 JS 返回，Kotlin 只做内存解析）。 */
data class ChsiPagePayload(
    val url: String = "",
    val title: String = "",
    val text: String = "",
    val links: List<ChsiLink> = emptyList(),
)

/** 官方页面中的链接（仅保留 chsi.com.cn 且文本 ≤20 字的链接，规格 §3）。 */
data class ChsiLink(
    val text: String = "",
    val href: String = "",
)

/** 官方工具入口（规格 §1 实测 10 项）。 */
data class ChsiOfficialTool(
    val id: String,
    val title: String,
    val desc: String,
    val url: String,
    val needsLogin: Boolean = false,
)

/** 4 张数据卡的类别。 */
enum class ChsiDataCardKind(val title: String) {
    /** 学籍信息（学校/专业/学籍状态/入学时间等）。 */
    ENROLLMENT("学籍信息"),

    /** 学历信息（院校/专业/学历/毕结业结论等）。 */
    EDUCATION("学历信息"),

    /** 学位信息（学位/专业/授予时间等）。 */
    DEGREE("学位信息"),

    /** 在线验证报告（有无报告/状态）。 */
    VERIFY_REPORT("在线验证报告"),
}

/** 数据卡展示状态：有摘要 / 提取不到（空态 + 打开官方页面查看）。 */
enum class ChsiCardStatus {
    READY,
    EMPTY,
}

/** 单张数据卡的 UI 数据（纯数据，可单测；不含任何账号/密码/cookie）。 */
data class ChsiDataCardUi(
    val kind: ChsiDataCardKind,
    val status: ChsiCardStatus,
    val lines: List<String> = emptyList(),
    val targetUrl: String,
)

/** 学信网静态目录：官方 URL / 10 个工具入口 / UA 常量（规格 §1/§3）。 */
object ChsiCatalog {

    /** 学信档案（wap 版，登录后进入；用于进入即加载并判断登录态）。 */
    const val ARCHIVE_ENTRY: String = "https://my.chsi.com.cn/archive/wap/index.jsp"

    /** 学信档案完整页（「打开完整学信档案」）。 */
    const val ARCHIVE_HOME: String = "https://my.chsi.com.cn/archive/index.jsp"

    /** 学信网账号登录页（用户手动登录；App 不预填/不保存）。 */
    const val LOGIN_URL: String = "https://account.chsi.com.cn/passport/login"

    /** 学信网主页。 */
    const val HOME_URL: String = "https://www.chsi.com.cn/"

    /**
     * 桌面版 UA（WebView 顶栏「桌面版」切换用）：
     * 学信网对手机 UA 自带 wap 自适应，个别页面需桌面版查看。
     */
    const val DESKTOP_UA: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 手机版 UA（「手机版」切换用；默认进入使用系统 WebView 的移动 UA）。 */
    const val MOBILE_UA: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** 规格 §1：10 个官方工具入口（顺序与实测清单一致）。 */
    val officialTools: List<ChsiOfficialTool> = listOf(
        ChsiOfficialTool(
            id = "archive",
            title = "学信档案",
            desc = "学籍 · 学历 · 学位",
            url = "https://my.chsi.com.cn/archive/index.jsp",
            needsLogin = true,
        ),
        ChsiOfficialTool(
            id = "education-query",
            title = "学籍/学历查询",
            desc = "公开查询",
            url = "https://www.chsi.com.cn/xlcx/lscx.jsp",
        ),
        ChsiOfficialTool(
            id = "degree-query",
            title = "学位查询",
            desc = "公开查询",
            url = "https://www.chsi.com.cn/xwcx/index.jsp",
        ),
        ChsiOfficialTool(
            id = "verify-report",
            title = "在线验证报告",
            desc = "申请 · 查看报告",
            url = "https://my.chsi.com.cn/archive/bab/index.jsp",
            needsLogin = true,
        ),
        ChsiOfficialTool(
            id = "report-verify",
            title = "报告在线验证",
            desc = "用人单位核验",
            url = "https://www.chsi.com.cn/xlcx/bgcx.jsp",
        ),
        ChsiOfficialTool(
            id = "education-auth",
            title = "学历认证报告",
            desc = "认证服务",
            url = "https://www.chsi.com.cn/xlrz/index.jsp",
        ),
        ChsiOfficialTool(
            id = "photo-check",
            title = "照片校对",
            desc = "校对学历照片",
            url = "https://my.chsi.com.cn/archive/xlzp/index.jsp",
            needsLogin = true,
        ),
        ChsiOfficialTool(
            id = "yz",
            title = "研招网",
            desc = "考研报名 · 成绩",
            url = "https://yz.chsi.com.cn/",
        ),
        ChsiOfficialTool(
            id = "xz",
            title = "学职平台",
            desc = "就业 · 职业发展",
            url = "https://xz.chsi.com.cn/",
        ),
        ChsiOfficialTool(
            id = "help",
            title = "帮助中心",
            desc = "常见问题",
            url = "https://www.chsi.com.cn/help/",
        ),
    )

    /** 4 张数据卡顺序（窄屏 2 列 / 宽屏 3 列）。 */
    val dataCards: List<ChsiDataCardKind> = ChsiDataCardKind.entries.toList()

    /** 数据卡对应的官方页面（空态时「打开官方页面查看」用）。 */
    fun dataCardUrl(kind: ChsiDataCardKind): String = when (kind) {
        ChsiDataCardKind.ENROLLMENT -> ARCHIVE_ENTRY
        ChsiDataCardKind.EDUCATION -> "https://www.chsi.com.cn/xlcx/lscx.jsp"
        ChsiDataCardKind.DEGREE -> "https://www.chsi.com.cn/xwcx/index.jsp"
        ChsiDataCardKind.VERIFY_REPORT -> "https://my.chsi.com.cn/archive/bab/index.jsp"
    }
}

/** 学信网页面固定文案（规格 §3）。 */
object ChsiTexts {
    const val PAGE_TITLE: String = "学信网"
    const val SECTION_DATA: String = "数据"
    const val SECTION_TOOLS: String = "官方工具"

    /** 状态条：已登录 + 数据更新时间。 */
    const val STATUS_READY_PREFIX: String = "已登录学信网 · 数据更新 "

    /** 未登录 / 会话过期。 */
    const val STATUS_NOT_LOGGED_IN: String = "未登录或会话过期"

    /** 正在读取官方页面。 */
    const val STATUS_EXTRACTING: String = "正在读取学信档案…"

    /** 手动登录提示（App 不预填、不保存账号密码）。 */
    const val HINT_MANUAL_LOGIN: String = "请在下方官方页面手动登录学信网（App 不保存账号密码）"

    /** 卡片页（未强制跳登录页）的登录提示。 */
    const val HINT_LOGIN_FROM_CARDS: String = "未登录学信网：点「登录学信网」在官方页面手动登录，登录后自动回卡片页"

    /** 未登录时数据卡占位（点击直达官方登录页）。 */
    const val MSG_NEED_LOGIN: String = "未登录 · 去登录查看"

    /** 卡片页在后台加载档案页判断登录态时的状态条文案。 */
    const val STATUS_CHECKING: String = "正在检查学信网登录状态…"

    /** 卡片页「登录学信网」按钮。 */
    const val BTN_LOGIN_ACTION: String = "登录学信网"

    /** 登录页返回卡片页按钮。 */
    const val BTN_BACK_TO_CARDS: String = "返回卡片"

    /** LOGIN 模式兜底：用户已登录但自动判定失败时，手动进入卡片页。 */
    const val BTN_ENTER_CARDS: String = "我已登录，进入卡片页"

    /** 登录页兜底提示（真实登录页可能没有「退出登录」等强信号）。 */
    const val HINT_ENTER_CARDS_FALLBACK: String =
        "已完成登录？若页面仍停留在此，点右侧按钮直接进入卡片页（提取不到数据也保留官方入口）"

    /** 提取不到时的空态兜底（卡片可点开官方页面）。 */
    const val MSG_EMPTY_FALLBACK: String = "已登录 · 打开官方页面查看"

    /** 网页加载失败。 */
    const val MSG_LOAD_FAILED: String = "网页加载失败，请检查网络后重试"

    /** 提取失败（非崩溃，卡片展示空态）。 */
    const val MSG_EXTRACT_FAILED: String = "读取学信档案失败，可打开官方页面查看"

    const val BTN_LOGIN: String = "去登录"
    const val BTN_REFRESH: String = "刷新"
    const val BTN_OPEN_ARCHIVE: String = "打开完整学信档案"
    const val BTN_LOGOUT: String = "退出学信网登录"
    const val BTN_DESKTOP: String = "桌面版"
    const val BTN_MOBILE: String = "手机版"
    const val BTN_OPEN_OFFICIAL: String = "打开官方页面查看"

    const val LOGOUT_CONFIRM_TITLE: String = "退出学信网登录"
    const val LOGOUT_CONFIRM_TEXT: String =
        "将清除本机保存的学信网登录状态（Cookie / 网页缓存）并回到登录页；不会影响你的学信网账号。"
    const val MSG_LOGGED_OUT: String = "已退出学信网登录"

    /** 卡片类别（数据卡标题常量）。 */
    const val CARD_ENROLLMENT: String = "学籍信息"
    const val CARD_EDUCATION: String = "学历信息"
    const val CARD_DEGREE: String = "学位信息"
    const val CARD_VERIFY_REPORT: String = "在线验证报告"
}
