package com.furina.classtools.data.privacy

import com.furina.classtools.data.sync.UserdataV6

/** 同步方式：与 web/小程序隐私页口径一致。 */
enum class SyncScopeMode(val label: String) {
    /** 随账号同步（云端 userdata v6 / marks，同一账号跨端互通）。 */
    SYNCED("随账号同步"),

    /** 仅本机（token、班级名单缓存等，不上传）。 */
    LOCAL_ONLY("仅本机"),

    /** 设备偏好（提醒/日历/权限设置），不随账号同步。 */
    DEVICE_ONLY("设备偏好"),
}

/** 同步范围清单一项（隐私页「同步范围」卡）。 */
data class SyncScopeItem(
    val key: String,
    val title: String,
    val detail: String,
    val mode: SyncScopeMode,
    /** 对应 userdata v6 section（marks 项与仅本机项为 null）。 */
    val sectionKey: String? = null,
)

/**
 * 同步范围清单（与 `docs/BACKEND_CONTRACT.md` §4 userdata v6、`web/AGENTS.md` §4 统一口径）。
 *
 * 11 个 userdata section + marks 三类（hw / nt / ntdone）+ 仅本机项。
 * 文案简洁，供「数据与隐私」页直接渲染；顺序固定（合并顺序一致），不随云端下发。
 */
object SyncScopeCatalog {

    val items: List<SyncScopeItem> = listOf(
        // ---- marks（独立表，仍是唯一真源） ----
        SyncScopeItem("marks.hw", "作业完成", "勾选作业完成状态（marks）", SyncScopeMode.SYNCED),
        SyncScopeItem("marks.nt", "通知置顶", "通知置顶状态（marks）", SyncScopeMode.SYNCED),
        SyncScopeItem("marks.ntdone", "通知已处理", "通知已处理状态（marks）", SyncScopeMode.SYNCED),

        // ---- userdata v6 11 sections（顺序 = 契约 SECTION_KEYS） ----
        SyncScopeItem("homeTools", "首页常用工具", "首页勾选（≤4）与工具顺序", SyncScopeMode.SYNCED, "homeTools"),
        SyncScopeItem("fit", "体测录入", "性别/年级与最近成绩（最多 2 条）", SyncScopeMode.SYNCED, "fit"),
        SyncScopeItem("draw", "抽签参数", "范围/排除/抽签名单选择", SyncScopeMode.SYNCED, "draw"),
        SyncScopeItem("group", "分组与名单", "分组参数与座号选择", SyncScopeMode.SYNCED, "group"),
        SyncScopeItem("roster", "点名名单与到·假", "点名名单（含派生名单文本）与状态", SyncScopeMode.SYNCED, "roster"),
        SyncScopeItem("hide", "隐藏项", "隐藏的作业/通知", SyncScopeMode.SYNCED, "hide"),
        SyncScopeItem("tt", "课表偏好", "课表视图/开学日（不含课表快照）", SyncScopeMode.SYNCED, "tt"),
        SyncScopeItem("prefs", "通知提醒偏好", "网页/小程序通知提醒偏好", SyncScopeMode.SYNCED, "prefs"),
        SyncScopeItem("nick", "昵称", "自定义昵称", SyncScopeMode.SYNCED, "nick"),
        SyncScopeItem("power", "关注宿舍（电费）", "关注宿舍与低电阈值（不导出）", SyncScopeMode.SYNCED, "power"),
        SyncScopeItem("water", "抄表记录（水量）", "1–6 人间月份抄表记录", SyncScopeMode.SYNCED, "water"),

        // ---- 仅本机 / 设备偏好 ----
        SyncScopeItem("identity", "身份 token", "只存本机，不上传；退出登录即清除", SyncScopeMode.LOCAL_ONLY),
        SyncScopeItem("members", "班级名单缓存", "只存本机（按账号隔离），用于名单/座号展示", SyncScopeMode.LOCAL_ONLY),
        SyncScopeItem("reminder", "提醒与安静时段", "Android 本机提醒设置，不随账号同步", SyncScopeMode.DEVICE_ONLY),
        SyncScopeItem("calendar", "日历写入结果", "只写系统日历，不上传", SyncScopeMode.DEVICE_ONLY),
    )

    /** 11 个 userdata section 的条目（顺序与契约一致）。 */
    val sectionItems: List<SyncScopeItem> get() = items.filter { it.sectionKey != null }

    /** marks 类条目。 */
    val marksItems: List<SyncScopeItem> get() = items.filter { it.key.startsWith("marks.") }

    /** 仅本机/设备偏好条目。 */
    val localItems: List<SyncScopeItem> get() = items.filter { it.mode != SyncScopeMode.SYNCED }

    /** 校验清单覆盖全部 11 个 section 且无重复（供测试/启动断言）。 */
    fun coversAllSections(): Boolean =
        sectionItems.mapNotNull { it.sectionKey }.toSet() == UserdataV6.SECTION_KEYS.toSet() &&
            sectionItems.size == UserdataV6.SECTION_KEYS.size

    fun bySection(sectionKey: String): SyncScopeItem? = items.firstOrNull { it.sectionKey == sectionKey }

    /** 隐私页副标题：与 mp「网页与小程序同一账号下互通；身份 token 与班级名单缓存只在本机」一致。 */
    const val FOOTER: String =
        "网页与小程序同一账号下互通；云端数据只有本人 token 可读。身份 token 与班级名单缓存只在本机。"
}
