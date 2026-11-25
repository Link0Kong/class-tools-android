package com.furina.classtools.data.member

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.furina.classtools.data.network.ApiResult

/**
 * 班级名单一行（`POST rpc/class_members` → `[{seat,sid,name,role,badge,is_admin}]`）。
 *
 * 契约：docs/api/ENDPOINTS.md E2；服务端已过滤 `is_test` 测试身份，班级名单约 70 人。
 * 注意：`sid` 只用于云端内部键与个人数据归属，**页面不显示学号**（web/mp 约定）。
 */
@Serializable
data class ClassMember(
    /** 座号 1..70（名单选择/抽签/点名都用座号数组）。 */
    val seat: Int = 0,
    val sid: String = "",
    val name: String = "",
    /** 班委职务（非空 = 班委）。 */
    val role: String = "",
    val badge: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false,
) {
    /** 是否班委：契约里「role 非空」即班委（badge=班委 是展示徽章）。 */
    val isCommittee: Boolean
        get() = role.isNotBlank()
}

/** 名单加载结果：失败时也带回旧缓存，保证离线可用（契约：空数组视为失败、保留旧缓存）。 */
sealed interface ClassMembersResult {
    data class Success(
        val members: List<ClassMember>,
        /** true = 来自本机缓存（TTL 内或网络失败时的降级）。 */
        val fromCache: Boolean,
    ) : ClassMembersResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ClassMembersResult
}

/**
 * 班级名单仓库。
 *
 * 缓存策略（契约 docs/BACKEND_CONTRACT.md §3、§7.7）：
 * - 内存 L1 + 按 sid 隔离的落盘缓存（文件，见 [FileClassMemberCache]）；
 * - TTL 6 小时；TTL 内 0 请求；
 * - 网络失败/空数组 → 返回旧缓存（[ClassMembersResult.Success.fromCache]=true），不覆盖缓存。
 */
interface ClassMembersRepository {
    /** @param force true = 跳过 TTL 强制刷新（下拉刷新/手动同步）。 */
    suspend fun load(force: Boolean = false): ClassMembersResult

    suspend fun searchSeats(query: String): ApiResult<Set<Int>>

    /** 只读本机内存/文件缓存，不发请求。 */
    suspend fun cached(): List<ClassMember>

    /** 退出身份时清空（调用方负责）；只清本 sid 的缓存。 */
    suspend fun clear()
}
