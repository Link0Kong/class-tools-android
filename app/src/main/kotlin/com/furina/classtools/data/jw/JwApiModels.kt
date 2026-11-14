package com.furina.classtools.data.jw

/**
 * 教务官方 JSON 接口模型（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md` §2，2026-11-14 实测）。
 *
 * 口径：官方 layui qzTable 分页接口，响应 `{msg, code, data:[...], count}`（`code` 可能是 0 或 "0"）；
 * 字段名严格按官方 JSON（各模型注释标出中文含义），全部带默认值——官方字段缺省/新增时不崩溃。
 *
 * 红线（规格 §0）：本文件只有接口字段模型，**不包含/不保存任何账号、密码、cookie**；
 * 卡片数据只用于 App 内存展示，不落盘、不上传、不打日志。
 */

/** 官方分页响应解析后的内容（`rows` 为空 = 官方 count 0，属正常空态）。 */
data class JwApiPage<T>(
    val rows: List<T>,
    val count: Int,
    val msg: String? = null,
    val code: Int = 0,
)

/** 单次官方接口请求结果。 */
sealed class JwApiResult<out T> {
    /** 请求 + 解析成功（rows 可能为空）。 */
    data class Success<T>(val page: JwApiPage<T>) : JwApiResult<T>()

    /** 接口业务错误 / 网络错误（`code != 0`、HTTP 非 2xx、连接失败）。 */
    data class ApiError(val message: String) : JwApiResult<Nothing>()

    /** 返回 HTML / 登录页：会话失效，需要重新登录后重试（规格 §1）。 */
    data object SessionExpired : JwApiResult<Nothing>()

    /** JSON 结构不可识别（官方改版 / 异常响应）。 */
    data object Malformed : JwApiResult<Nothing>()
}

// ---------------------------------------------------------------------------
// 4 类官方接口行模型（字段名对应官方 JSON key，见各字段注释）
// ---------------------------------------------------------------------------

/**
 * 成绩行（`/jsxsd/kscj/cjcx_list`，规格 §2）。
 *
 * 官方字段：`xnxqid`(开课学期) `kch`(课程编号) `kc_mc`(课程名称) `ksdw`(开课单位) `zcjstr`(成绩)
 * `cjbs` `xf`(学分) `zxs`(总学时) `jd`(绩点) `bcxq`(补重学期)。
 */
data class JwGradeRow(
    val xnxqid: String = "",
    val kch: String = "",
    val kcMc: String = "",
    val ksdw: String = "",
    val zcjstr: String = "",
    val cjbs: String = "",
    val xf: String = "",
    val zxs: String = "",
    val jd: String = "",
    val bcxq: String = "",
)

/**
 * 考试行（`/jsxsd/xsks/xsksap_list`，规格 §2）。
 *
 * 官方字段：`xqmc`(校区) `ksxq`(考试校区) `kssj`(考试时间) `js_mc`(教室) `kch` `kskcmc`(课程名称)
 * `jsxm`(授课教师) `zwh`(座位号) `zkzh`(准考证号) `ksccmc`(考试场次) `bzywmc`(备注)。
 */
data class JwExamRow(
    val xqmc: String = "",
    val ksxq: String = "",
    val kssj: String = "",
    val jsMc: String = "",
    val kch: String = "",
    val kskcmc: String = "",
    val jsxm: String = "",
    val zwh: String = "",
    val zkzh: String = "",
    val ksccmc: String = "",
    val bzywmc: String = "",
)

/**
 * 选课轮次行（`/jsxsd/xsxk/xklc_list_data`，规格 §2）。
 *
 * 官方字段：`xqmc`(学年学期) `xklc_mc`(选课轮次) `xksj`(选课时间)。
 */
data class JwEnrollRow(
    val xqmc: String = "",
    val xklcMc: String = "",
    val xksj: String = "",
)

/**
 * 公告行（`/jsxsd/ggly/ysgg_query?type=list`，规格 §2）。
 *
 * 官方字段：`title`(标题) `gglbname`(公告类别) `cjr`(创建人)。
 */
data class JwNoticeRow(
    val title: String = "",
    val gglbname: String = "",
    val cjr: String = "",
)
