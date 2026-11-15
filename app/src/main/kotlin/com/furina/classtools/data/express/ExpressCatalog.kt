package com.furina.classtools.data.express

/**
 * 快递工具目标（对齐 HFUT-Schedule 的「快递」窗口）：只负责打开各家 App 的取件码/身份码页面，
 * 不读取、不保存任何快递单号或个人信息。
 */
data class ExpressTarget(
    val id: String,
    val name: String,
    val subtitle: String,
    val packageName: String,
    /** 首选 App 内页面 URL（部分 App 用 deeplink，部分用成分 Activity + data URL）。 */
    val url: String,
    val mainActivity: String? = null,
    /** 未安装时的浏览器兜底（官方 H5，不伪造业务 URL）。 */
    val webFallback: String,
)

object ExpressCatalog {

    const val PDD_PACKAGE = "com.xunmeng.pinduoduo"
    const val TAOBAO_PACKAGE = "com.taobao.taobao"
    const val CAINIAO_PACKAGE = "com.cainiao.wireless"
    const val JD_PACKAGE = "com.jingdong.app.mall"

    val targets: List<ExpressTarget> = listOf(
        ExpressTarget(
            id = "pdd",
            name = "拼多多身份码",
            subtitle = "校区快递站出示身份码",
            packageName = PDD_PACKAGE,
            url = "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE",
            mainActivity = "$PDD_PACKAGE.ui.activity.MainFrameActivity",
            webFallback = "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE",
        ),
        ExpressTarget(
            id = "taobao",
            name = "淘宝身份码",
            subtitle = "校区快递站出示取件码",
            packageName = TAOBAO_PACKAGE,
            url = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code",
            webFallback = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code",
        ),
        ExpressTarget(
            id = "cainiao",
            name = "菜鸟裹裹",
            subtitle = "查件 · 取件码 · 驿站",
            packageName = CAINIAO_PACKAGE,
            url = "https://m.cainiao.com/",
            webFallback = "https://m.cainiao.com/",
        ),
        ExpressTarget(
            id = "jd",
            name = "京东快递",
            subtitle = "查件 · 取件码",
            packageName = JD_PACKAGE,
            url = "https://m.jd.com/",
            webFallback = "https://m.jd.com/",
        ),
    )

    fun byId(id: String): ExpressTarget? = targets.firstOrNull { it.id == id }
}
