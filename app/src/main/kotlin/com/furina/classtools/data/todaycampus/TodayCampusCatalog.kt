package com.furina.classtools.data.todaycampus

/**
 * 入口图标键（UI 层映射为 Material 图标；数据层不依赖 Android，可直接 JVM 单测）。
 */
enum class TodayCampusGlyph {
    STUDENT_AFFAIRS,
    SCHOLARSHIP,
    WORK_STUDY,
    LEAVE,
    AID,
    DORM,
    MENTAL,
    CARD,
    GRADES,
    UTILITY,
    REPAIR,
    PORTAL,
}

/**
 * 一个「今日校园 / 融合门户」服务入口。
 *
 * @param id 稳定标识（搜索命中、定位与单测用；不参与云端同步）
 * @param name 服务名（入口卡标题；搜索主字段）
 * @param desc 一句说明（入口卡副标题）
 * @param glyph 图标键（UI 映射 Material 图标）
 * @param colorArgb 图标语义色（0xFFRRGGBB；浅/深色都可用作图标 tint，容器背景自动降透明度）
 * @param keywords 搜索补充关键词（中文别名 / 拼音缩写），仅本机过滤用
 */
data class TodayCampusEntry(
    val id: String,
    val name: String,
    val desc: String,
    val glyph: TodayCampusGlyph,
    val colorArgb: Long,
    val keywords: List<String> = emptyList(),
)

/**
 * 「今日校园」→ 学校融合门户（网上办事服务大厅）入口目录与纯逻辑（无 Android 依赖，可 JVM 单测）。
 *
 * ## 已核实（2026-11-15，只读访问公开页面；未登录、未使用任何真实账号）
 * 1. `https://e.fjtcm.edu.cn` → `302 /index.html`，响应头 `Server: wisedu`（金智「网上办事服务大厅 / 一网通办」）；
 * 2. 门户公开接口 `https://e.fjtcm.edu.cn/getLoginHref` 返回
 *    `https://authserver.fjtcm.edu.cn/authserver/login?service=https%3A%2F%2Fe.fjtcm.edu.cn%2Flogin2`；
 * 3. `https://e.fjtcm.edu.cn/login2` 未登录时 302 到同一 CAS 登录页；登录成功后由 CAS 回跳门户；
 * 4. CAS 登录页为 Apereo CAS（`#username` / `#password` / `#login_submit` / `#pwdLoginDiv` /
 *    `#showErrorTip` / `#captchaDiv` / `#captchaImg` / `#sliderCaptchaDiv`），与教务自动登录是
 *    **同一套**统一身份认证页面；账号 = 学号，密码 = 统一身份认证密码
 *    （可与教务共用同一份本机 Keystore 凭据）。
 *
 * ## 实现口径（红线）
 * - **不伪造业务 URL**：未核实到门户子页，所有入口统一打开门户登录入口 `https://e.fjtcm.edu.cn/login2`
 *   （未登录时由门户服务端 302 到 CAS、登录后回跳门户），并在顶部提示「请在门户内手动打开 xxx」；
 * - **不抓取 / 不缓存 / 不上传**任何门户数据，只做 App 内 WebView 容器；
 * - 密码只在内存里交给官方登录页输入框，不保存、不上传、不打日志；验证码 / 失败一律回退人工。
 */
object TodayCampusCatalog {

    // ---- 已核实的官方地址 ---------------------------------------------------

    /** 融合门户主机（`authserver.fjtcm.edu.cn` 的 CNAME 目标即此主机，同一台服务器）。 */
    const val PORTAL_HOST: String = "e.fjtcm.edu.cn"

    /** 融合门户根地址（未登录时 302 到 `/index.html`）。 */
    const val PORTAL_BASE_URL: String = "https://$PORTAL_HOST"

    /** 门户首页（实测 HTTP 200）。 */
    const val PORTAL_HOME_URL: String = "$PORTAL_BASE_URL/index.html"

    /**
     * 门户登录入口（实测 HTTP 302 → CAS 登录页；登录成功后 CAS 回跳门户）。
     *
     * 未登录时直接进入统一身份认证，已登录时由 CAS 直接回跳——比先加载门户 SPA 再等 JS 跳转更稳定。
     */
    const val PORTAL_ENTRY_URL: String = "$PORTAL_BASE_URL/login2"

    /** 学校统一身份认证（CAS）主机。 */
    const val CAS_HOST: String = "authserver.fjtcm.edu.cn"

    /** CAS 账号密码登录页路径前缀（与教务自动登录同一页面）。 */
    const val CAS_LOGIN_PATH: String = "/authserver/login"

    /** 学校根域（门户 / 统一认证 / WebVPN 等子域都在此范围内）。 */
    const val SCHOOL_DOMAIN: String = "fjtcm.edu.cn"

    /** 今日校园 App 包名（Intent 拉起；未安装回退门户）。 */
    const val APP_PACKAGE: String = TodayCampusAppLink.APP_PACKAGE

    // ---- 页面文案（短句；不写长段落） --------------------------------------

    /** 顶栏标题。 */
    const val PAGE_TITLE: String = "今日校园"

    /** 首页副标题（本页唯一一句说明）。 */
    const val SUBTITLE: String = "融合门户 · 统一身份认证"

    /** 搜索框占位。 */
    const val SEARCH_PLACEHOLDER: String = "搜索服务名称"

    /** 顶栏动作：打开今日校园 App。 */
    const val BTN_OPEN_APP: String = "打开今日校园 App"

    /** 顶栏动作：融合门户首页。 */
    const val BTN_PORTAL: String = "融合门户"

    /** WebView 顶栏动作：刷新。 */
    const val BTN_REFRESH: String = "刷新"

    /** 错误态/提示条动作：重试。 */
    const val BTN_RETRY: String = "重试"

    /** 提示条动作：知道了。 */
    const val BTN_KNOW: String = "知道了"

    /** 搜索空态动作：清除搜索。 */
    const val BTN_CLEAR_SEARCH: String = "清除搜索"

    /** 搜索无结果标题。 */
    const val MSG_NO_MATCH_TITLE: String = "没有匹配的服务"

    /** 搜索无结果说明。 */
    const val MSG_NO_MATCH_DESC: String = "换个关键词试试"

    /** 搜索框无障碍描述。 */
    const val A11Y_SEARCH: String = "搜索服务"

    // ---- 服务入口（12 个；需求要求 10–14 个） ------------------------------

    /** 融合门户入口 id（已知子页 = 门户首页，无手动提示）。 */
    const val PORTAL_ENTRY_ID: String = "portal"

    /**
     * 入口网格（2 列）。全部为**静态**目录：只含名称/说明/图标色/打开动作，不含任何伪造业务 URL。
     * 未核实到子页的入口打开门户登录入口（登录后即门户首页），并在 WebView 上方提示手动选择服务。
     */
    val entries: List<TodayCampusEntry> = listOf(
        TodayCampusEntry(
            id = "student-affairs",
            name = "学工服务",
            desc = "学生事务 · 综合办理",
            glyph = TodayCampusGlyph.STUDENT_AFFAIRS,
            colorArgb = 0xFF4E9082,
            keywords = listOf("学工", "学生工作", "xg"),
        ),
        TodayCampusEntry(
            id = "scholarship",
            name = "奖助学金",
            desc = "奖学金 · 助学金",
            glyph = TodayCampusGlyph.SCHOLARSHIP,
            colorArgb = 0xFFB98F63,
            keywords = listOf("奖学金", "助学金", "奖助"),
        ),
        TodayCampusEntry(
            id = "work-study",
            name = "勤工助学",
            desc = "岗位申请 · 工时",
            glyph = TodayCampusGlyph.WORK_STUDY,
            colorArgb = 0xFF5F8F5B,
            keywords = listOf("勤工", "助学", "岗位"),
        ),
        TodayCampusEntry(
            id = "leave",
            name = "请假申请",
            desc = "离校 · 返校请假",
            glyph = TodayCampusGlyph.LEAVE,
            colorArgb = 0xFF6E8CA8,
            keywords = listOf("请假", "离校", "销假"),
        ),
        TodayCampusEntry(
            id = "aid",
            name = "困难认定",
            desc = "家庭经济困难认定",
            glyph = TodayCampusGlyph.AID,
            colorArgb = 0xFFB06A55,
            keywords = listOf("困难", "认定", "资助"),
        ),
        TodayCampusEntry(
            id = "dorm",
            name = "宿舍服务",
            desc = "住宿 · 调寝 · 退宿",
            glyph = TodayCampusGlyph.DORM,
            colorArgb = 0xFF8A6A92,
            keywords = listOf("宿舍", "住宿", "调寝", "退宿"),
        ),
        TodayCampusEntry(
            id = "mental",
            name = "心理健康",
            desc = "测评 · 咨询预约",
            glyph = TodayCampusGlyph.MENTAL,
            colorArgb = 0xFF7C6BB0,
            keywords = listOf("心理", "测评", "咨询"),
        ),
        TodayCampusEntry(
            id = "card",
            name = "一卡通",
            desc = "校园卡 · 充值 · 流水",
            glyph = TodayCampusGlyph.CARD,
            colorArgb = 0xFF4A88A8,
            keywords = listOf("一卡通", "校园卡", "充值"),
        ),
        TodayCampusEntry(
            id = "grades",
            name = "课表成绩",
            desc = "教务课表 · 成绩",
            glyph = TodayCampusGlyph.GRADES,
            colorArgb = 0xFF3E7CA6,
            keywords = listOf("课表", "成绩", "教务"),
        ),
        TodayCampusEntry(
            id = "utility",
            name = "水电查询",
            desc = "宿舍水电 · 用量",
            glyph = TodayCampusGlyph.UTILITY,
            colorArgb = 0xFF3E8E7E,
            keywords = listOf("水电", "电费", "用量"),
        ),
        TodayCampusEntry(
            id = "repair",
            name = "报修/失物",
            desc = "后勤报修 · 失物招领",
            glyph = TodayCampusGlyph.REPAIR,
            colorArgb = 0xFFA65C7A,
            keywords = listOf("报修", "失物", "后勤", "维修"),
        ),
        TodayCampusEntry(
            id = PORTAL_ENTRY_ID,
            name = "融合门户",
            desc = "网上办事服务大厅",
            glyph = TodayCampusGlyph.PORTAL,
            colorArgb = 0xFF5A7FBF,
            keywords = listOf("门户", "办事大厅", "一网通办", "e"),
        ),
    )

    /** 按 id 取入口（未知 id 返回 null）。 */
    fun byId(id: String): TodayCampusEntry? = entries.firstOrNull { it.id == id }

    /** 融合门户入口（无手动提示；打开门户登录入口后即门户首页）。 */
    val portalEntry: TodayCampusEntry
        get() = byId(PORTAL_ENTRY_ID) ?: entries.last()

    /**
     * 入口点开后在 WebView 里加载的官方 URL：统一为已核实的门户登录入口
     * `https://e.fjtcm.edu.cn/login2`（未登录时服务端 302 到 CAS；登录后回跳门户首页）。
     *
     * 不拼接/猜测任何业务子页 URL（不伪造 URL）：服务入口由用户在官方门户里手动选择。
     */
    fun openUrlOf(entry: TodayCampusEntry): String = PORTAL_ENTRY_URL

    /**
     * 未知子页入口打开门户后的一句手动提示（融合门户入口返回 null，因为它本身就是门户）。
     * 不猜业务 URL，只提示用户在官方门户里自己点。
     */
    fun manualHintOf(entry: TodayCampusEntry): String? {
        if (entry.id == PORTAL_ENTRY_ID) return null
        return "请在门户内手动打开「${entry.name}」"
    }
}
