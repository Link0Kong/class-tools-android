package com.furina.classtools.data.auth

import com.furina.classtools.data.session.Session

/** 身份核验结果。 */
sealed interface VerifyResult {
    data class Success(val session: Session) : VerifyResult
    data class Failure(val code: String, val message: String) : VerifyResult
}

/**
 * 学号 + 姓名 → 云端班级 token。
 *
 * 具体端点/字段以 `docs/BACKEND_CONTRACT.md` 为准；目前是占位实现，
 * 契约确认后替换为 [ApiAuthRepository]。
 */
interface AuthRepository {
    suspend fun verify(studentId: String, name: String): VerifyResult

    suspend fun logout(): Unit = Unit
}

/**
 * 占位实现：后端契约确认前，线上构建不允许绕过身份。
 * Debug 构建由界面提供「开发预览」入口，不经过这里。
 */
class StubAuthRepository : AuthRepository {
    override suspend fun verify(studentId: String, name: String): VerifyResult =
        VerifyResult.Failure(
            code = "not_wired",
            message = "身份服务尚未接入，请在 Debug 构建使用开发预览。",
        )
}
