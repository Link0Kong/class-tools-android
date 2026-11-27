package com.furina.classtools.ui.screens.guide

/**
 * 使用说明「卡片化」内容数据（**纯 Kotlin，无 Compose / Android 依赖**，可直接单测）。
 *
 * 信息架构与三端共享契约 `web/使用说明_卡片化重构契约_20261112.md` 一致：
 * 5 个分类（入门 / 学习 / 生活 / 协作 / 更多）、23 张卡、一屏一张左右滑动。
 *
 * 文案硬规则（契约 §0）：
 * - 标题 ≤ 8 字；一句话作用 ≤ 20 字；每条步骤 ≤ 24 字；提示 ≤ 30 字；
 * - 步骤固定 3 条、以动词开头；每卡 1 条提示；
 * - 不写实现细节（接口 / 数据表 / 字段 / 版本号 / 测试账号 / 开发者入口 / 统计代码），
 *   数字与规则必须与 Android 当前实现一致；
 * - 一个功能只在一张卡出现；Android 独有能力（提醒 / 写日历 / 权限管理）单独成卡。
 *
 * 维护：改文案只改本文件；改页面结构改 `UserGuideScreen.kt`；改配图改 `GuideScenes.kt`。
 */
object UserGuideContent {

    /** 内容版本（内容较上一版有实质变化时递增；供设置页/关于页展示）。 */
    const val VERSION: String = "2026-11-27.1"

    /** 轮播顺序即本列表顺序（契约 §5.2）。 */
    val cards: List<GuideCard> = listOf(
        GuideCard(
            id = "identity",
            category = GuideCategory.START,
            title = "身份核验",
            subtitle = "姓名 + 学号进门，本机保存 29 天",
            steps = listOf(
                "首次打开输入姓名与学号",
                "通过班级名单校验后进入",
                "换设备重新核验即可同步",
            ),
            tip = "身份只存本机 29 天，过期回身份页",
            scene = GuideScene.SHIELD,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "home",
            category = GuideCategory.START,
            title = "工作台",
            subtitle = "今日课程、作业、通知一页看完",
            steps = listOf(
                "看今日课程与下一节课",
                "点「编辑」勾选常用工具",
                "点右上「全部」进工具目录",
            ),
            tip = "常用工具最多 4 个，顺序随账号同步",
            scene = GuideScene.GRID,
            tone = GuideTone.MINT,
        ),
        GuideCard(
            id = "draw",
            category = GuideCategory.STUDY,
            title = "抽签",
            subtitle = "随机抽号、剔除重抽与结果导出",
            steps = listOf(
                "设范围与人数，可选排除号",
                "结果可逐个剔除或自动补抽",
                "可调整排序并导出抽签图",
            ),
            tip = "抽签结果保存在本机，分组页可选「最近抽签」",
            scene = GuideScene.PICKS,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "group",
            category = GuideCategory.STUDY,
            title = "分组",
            subtitle = "全班 / 最近抽签 / 手动选择",
            steps = listOf(
                "成员来自全班、最近抽签或名单",
                "设组数或每组人数后打乱",
                "结果可复制、导出或重新分组",
            ),
            tip = "分组不依赖抽签，可随时单独打开",
            scene = GuideScene.PEOPLE,
            tone = GuideTone.MINT,
        ),
        GuideCard(
            id = "roster",
            category = GuideCategory.STUDY,
            title = "名册与点名",
            subtitle = "按座号看名单，到 / 假可重置",
            steps = listOf(
                "名册按座号显示班级成员",
                "勾选成员加入点名名单",
                "点「到 / 假」记录并可重置",
            ),
            tip = "点名状态随账号同步，换机可恢复",
            scene = GuideScene.PEOPLE,
            tone = GuideTone.VIOLET,
        ),
        GuideCard(
            id = "hw",
            category = GuideCategory.STUDY,
            title = "任务",
            subtitle = "个人待办与班级作业",
            steps = listOf(
                "输入标题添加任务，点任务编辑日期和步骤",
                "点星标设为重要，勾选后可在已完成中恢复",
                "打开班级作业，可从详情加入我的任务",
            ),
            tip = "截止提醒去「我的 → 提醒设置」开",
            scene = GuideScene.LIST,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "nt",
            category = GuideCategory.STUDY,
            title = "班级通知",
            subtitle = "简报、附件，已处理状态同步",
            steps = listOf(
                "通知按简报与筛选展示",
                "点开看附件图片与文件",
                "标记已处理后会同步",
            ),
            tip = "管理员可置顶或编辑通知",
            scene = GuideScene.LIST,
            tone = GuideTone.ROSE,
        ),
        GuideCard(
            id = "tt",
            category = GuideCategory.STUDY,
            title = "课表",
            subtitle = "周 / 日视图，可写进系统日历",
            steps = listOf(
                "左右箭头切换周次",
                "周 / 日视图一键切换",
                "点「写日程」存入系统日历",
            ),
            tip = "课表来自教务同步，只读不能改",
            scene = GuideScene.GRID,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "fit",
            category = GuideCategory.STUDY,
            title = "体测",
            subtitle = "国标算分，历史与评分表",
            steps = listOf(
                "选性别与年级后录入成绩",
                "填完自动算分与等级",
                "历史最多存 2 条可回填",
            ),
            tip = "长跑按「分 : 秒」填写成绩",
            scene = GuideScene.CHART,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "jw",
            category = GuideCategory.STUDY,
            title = "教务入口",
            subtitle = "卡片速览，官方数据",
            steps = listOf(
                "点「教务」卡片看成绩/考试/选课/公告",
                "8 个官方工具入口 App 内打开",
                "登录后自动拉取，数据只在内存",
            ),
            tip = "密码只存本机；校外打不开先连学校 VPN",
            scene = GuideScene.GRID,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "map",
            category = GuideCategory.STUDY,
            title = "校园地图",
            subtitle = "2.5D 楼栋 · 楼层 · 道路导航",
            steps = listOf(
                "点「地图」看 2.5D 校园全貌",
                "点楼栋看楼层与道路导航",
                "课表页可跳到下一节位置",
            ),
            tip = "需定位权限；双指可缩放地图",
            scene = GuideScene.GRID,
            tone = GuideTone.MINT,
        ),
        GuideCard(
            id = "power",
            category = GuideCategory.LIFE,
            title = "宿舍电费",
            subtitle = "余额与趋势，日更楼栋有标注",
            steps = listOf(
                "点「关注宿舍」按楼栋选房间",
                "看余额与可用电量估算",
                "点或拖趋势看采样点",
            ),
            tip = "13 / 16 号楼日更，估算可能标「参考」",
            scene = GuideScene.CHART,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "water",
            category = GuideCategory.LIFE,
            title = "水量统计",
            subtitle = "4 / 6 人间抄表，水费折算扣电",
            steps = listOf(
                "选关注宿舍与要记的月份",
                "录入 4 / 6 人间水表读数",
                "自动与上次对比算出水费",
            ),
            tip = "水费从电费账户扣，折算成扣减电量",
            scene = GuideScene.DROPLET,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "express",
            category = GuideCategory.LIFE,
            title = "快递",
            subtitle = "取件码 · 身份码一键打开",
            steps = listOf(
                "点「快递」选拼多多/淘宝/菜鸟/京东",
                "打开对应 App 的身份码/取件码",
                "未装 App 自动转官方网页",
            ),
            tip = "仅打开官方页面，不保存取件信息",
            scene = GuideScene.GRID,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "vote",
            category = GuideCategory.TEAM,
            title = "投票",
            subtitle = "限时 / 长时，规则与范围自定",
            steps = listOf(
                "选限时、指定截止或不限时",
                "写标题与 2–20 个选项",
                "设单选、匿名与可见范围",
            ),
            tip = "一人一票；发起人和管理员可管理",
            scene = GuideScene.BALLOT,
            tone = GuideTone.MINT,
        ),
        GuideCard(
            id = "file",
            category = GuideCategory.TEAM,
            title = "文件传输",
            subtitle = "跨设备取文件，打开班级课件",
            steps = listOf(
                "在我的临时文件中上传或下载原件",
                "点文件上的转换按钮获取兼容版和 PDF",
                "点「班级课件」上传、查看或播放共享课件",
            ),
            tip = "个人临时文件保留 3 天，仅同账号可取",
            scene = GuideScene.TRANSFER,
            tone = GuideTone.BLUE,
        ),
        GuideCard(
            id = "reminder",
            category = GuideCategory.MORE,
            title = "提醒设置",
            subtitle = "作业截止与上课提醒",
            steps = listOf(
                "在「我的 → 提醒设置」开总开关",
                "作业可提前 0 / 1 / 3 天提醒",
                "上课可提前 5 / 10 / 15 分钟",
            ),
            tip = "首次开启需允许通知权限",
            scene = GuideScene.LIST,
            tone = GuideTone.NEUTRAL,
        ),
        GuideCard(
            id = "calendar",
            category = GuideCategory.MORE,
            title = "写日程",
            subtitle = "课表 / 通知一键写进系统日历",
            steps = listOf(
                "课表或通知页点「写日程」",
                "授权后直接写入并提醒",
                "未授权改用系统日历确认",
            ),
            tip = "日程只写本机日历，不上传云端",
            scene = GuideScene.GRID,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "permissions",
            category = GuideCategory.MORE,
            title = "权限管理",
            subtitle = "通知与日历权限状态一目了然",
            steps = listOf(
                "点「我的 → 权限管理」看状态",
                "通知与日历权限可分开开",
                "被永久拒绝去系统设置开启",
            ),
            tip = "权限只用于本机功能，不上传内容",
            scene = GuideScene.SHIELD,
            tone = GuideTone.NEUTRAL,
        ),
        GuideCard(
            id = "sort",
            category = GuideCategory.MORE,
            title = "工具与排序",
            subtitle = "长按拖动排序，顺序跟随账号",
            steps = listOf(
                "在「全部工具」长按卡片拖动",
                "松手后自动保存新顺序",
                "首页点「编辑」勾选常用工具",
            ),
            tip = "工具栏顺序随账号同步到各设备",
            scene = GuideScene.GRID,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "privacy",
            category = GuideCategory.MORE,
            title = "数据与隐私",
            subtitle = "同步范围与数据导出 / 清除",
            steps = listOf(
                "点「我的 → 数据与隐私」看范围",
                "看哪些数据随账号同步",
                "按需导出文件或清除数据",
            ),
            tip = "导出不含学号明文与登录凭证",
            scene = GuideScene.SHIELD,
            tone = GuideTone.NEUTRAL,
        ),
        GuideCard(
            id = "feedback",
            category = GuideCategory.MORE,
            title = "反馈建议",
            subtitle = "问题与建议发给管理员",
            steps = listOf(
                "点「我的 → 反馈建议」写内容",
                "正文最多 500 字直接提交",
                "管理员处理后可看状态",
            ),
            tip = "反馈不公开你的学号",
            scene = GuideScene.LIST,
            tone = GuideTone.SAND,
        ),
        GuideCard(
            id = "faq",
            category = GuideCategory.MORE,
            title = "常见问题",
            subtitle = "打不开、不同步、提醒不响先看这里",
            steps = listOf(
                "打不开：查网络与身份是否过期",
                "不同步：手动同步或下拉刷新",
                "提醒不响：查通知权限与省电",
            ),
            tip = "仍未解决就用「反馈建议」联系",
            scene = GuideScene.LIST,
            tone = GuideTone.NEUTRAL,
        ),
    )

    /** 按 id 取卡（找不到返回 null）。 */
    fun cardOf(id: String): GuideCard? = cards.firstOrNull { it.id == id }

    /**
     * 兼容旧调用：旧版本按「分节」查找（[sectionOf]）；卡片化后分节与卡片一一对应。
     */
    fun sectionOf(id: String): GuideCard? = cardOf(id)

    /** 按 id 取轮播下标（找不到返回 -1）。 */
    fun indexOf(id: String): Int = cards.indexOfFirst { it.id == id }

    /** 某分类的全部卡片（保持轮播顺序）。 */
    fun cardsOf(category: GuideCategory): List<GuideCard> =
        cards.filter { it.category == category }

    /** 某分类首张卡在轮播中的下标（分类无卡返回 -1）。 */
    fun firstIndexOf(category: GuideCategory): Int =
        cards.indexOfFirst { it.category == category }

    /** 某分类的卡片数（总览分组标题用）。 */
    fun countOf(category: GuideCategory): Int = cards.count { it.category == category }
}

/** 说明页分类（契约 §1.1：id / 名称与 web、小程序一致）。 */
enum class GuideCategory(val id: String, val label: String) {
    START("start", "入门"),
    STUDY("study", "学习"),
    LIFE("life", "生活"),
    TEAM("team", "协作"),
    MORE("more", "更多"),
}

/**
 * hero 配图场景（契约 §1.3，统一 11 种）。
 *
 * Android 端用 Compose 自绘（见 [GuideSceneArt]），不下载任何图片、不新增依赖。
 */
enum class GuideScene(val id: String) {
    /** 三颗号码球 + 虚线洗牌箭头。 */
    PICKS("picks"),

    /** 编号圆点折线 + 终点小旗。 */
    STEPS("steps"),

    /** 宫格 / 日历，一格高亮。 */
    GRID("grid"),

    /** 三行列表，一行带勾。 */
    LIST("list"),

    /** 折线 + 柱状。 */
    CHART("chart"),

    /** 圆形头像 / 姓名条，两个高亮。 */
    PEOPLE("people"),

    /** 手机 ↔ 云 ↔ 电脑，双向箭头。 */
    TRANSFER("transfer"),

    /** 三条不同长度的选项条 + 勾。 */
    BALLOT("ballot"),

    /** 大水滴 + 用量刻度条。 */
    DROPLET("droplet"),

    /** 盾牌 + 对勾 + 锁点。 */
    SHIELD("shield"),

    /** 层叠幻灯片 + 播放三角。 */
    SLIDES("slides"),
}

/**
 * 配图色调（契约 §1.4：mint / blue / sand / rose / violet；更多分类用 neutral）。
 *
 * Android 映射到本端主题色（primary / secondary / tertiary / error / 中性），
 * 不照搬 web 色值（见 `GuideScenes.kt` 的 [guideSceneColors]）。
 */
enum class GuideTone(val id: String) {
    MINT("mint"),
    BLUE("blue"),
    SAND("sand"),
    ROSE("rose"),
    VIOLET("violet"),
    NEUTRAL("neutral"),
}

/**
 * 一张使用说明卡（契约 §5.1 数据模型）。
 *
 * @param id 稳定标识（与总览条目、测试断言共用；不代表任何后端字段）
 * @param category 所属分类（决定分类胶囊与 chips 高亮）
 * @param title 标题（≤ 8 字）
 * @param subtitle 一句话作用（≤ 20 字）
 * @param steps 上手步骤（固定 3 条，每条 ≤ 24 字，以动词开头）
 * @param tip 提示（1 条，≤ 30 字；UI 自动加 💡）
 * @param scene hero 配图场景
 * @param tone hero 配色
 */
data class GuideCard(
    val id: String,
    val category: GuideCategory,
    val title: String,
    val subtitle: String,
    val steps: List<String>,
    val tip: String,
    val scene: GuideScene,
    val tone: GuideTone,
)
