package com.furina.classtools.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "ct_session")

/**
 * 班级 token 的本地保存（字段对齐网页 localStorage.zq_class_identity：token/sid/name/role/badge/is_admin/ts）。
 *
 * 安全性说明：数据保存在应用私有目录（DataStore 文件），不进入云备份（Manifest allowBackup=false），
 * 不写日志。后续可选：用 Android Keystore 加密再落盘。
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val sid = stringPreferencesKey("sid")
        val name = stringPreferencesKey("name")
        val token = stringPreferencesKey("token")
        val role = stringPreferencesKey("role")
        val badge = stringPreferencesKey("badge")
        val isAdmin = booleanPreferencesKey("is_admin")
        val debug = booleanPreferencesKey("debug")
        val issuedAt = longPreferencesKey("issued_at")
        val expiresAt = longPreferencesKey("expires_at")
    }

    val state: Flow<SessionState> = context.sessionDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val sid = prefs[Keys.sid].orEmpty()
            val token = prefs[Keys.token].orEmpty()
            val isDebug = prefs[Keys.debug] ?: false
            if (sid.isBlank() || (token.isBlank() && !isDebug)) {
                SessionState.SignedOut
            } else {
                SessionState.SignedIn(
                    Session(
                        sid = sid,
                        name = prefs[Keys.name].orEmpty(),
                        token = token,
                        role = prefs[Keys.role].orEmpty(),
                        badge = prefs[Keys.badge].orEmpty(),
                        isAdmin = prefs[Keys.isAdmin] ?: false,
                        debug = isDebug,
                        issuedAt = prefs[Keys.issuedAt] ?: System.currentTimeMillis(),
                        expiresAt = prefs[Keys.expiresAt] ?: 0L,
                    ),
                )
            }
        }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.sid] = session.sid
            prefs[Keys.name] = session.name
            prefs[Keys.token] = session.token
            prefs[Keys.role] = session.role
            prefs[Keys.badge] = session.badge
            prefs[Keys.isAdmin] = session.isAdmin
            prefs[Keys.debug] = session.debug
            prefs[Keys.issuedAt] = session.issuedAt
            prefs[Keys.expiresAt] = session.expiresAt
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
