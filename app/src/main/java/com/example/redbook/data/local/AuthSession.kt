package com.example.redbook.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Supabase 登录会话令牌(access_token / refresh_token)的本机仓库。
 *
 * 与 SessionPrefs 共用同一个 SharedPreferences 文件("login_session"):
 *  - SessionPrefs 存用户资料,本类存令牌,二者生命周期一致;
 *  - SessionPrefs.clear() 会连同令牌一起抹掉,不会残留。
 *
 * 所有云端请求都要带 access_token(RLS 以 auth.uid() 判定身份),
 * access_token 有效期约 1 小时,过期后用 refresh_token 换新的。
 */
object AuthSession {

    private const val PREFS_NAME = "login_session"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"

    /** 令牌剩余不足该毫秒数即视为"即将过期",提前刷新 */
    private const val REFRESH_SKEW_MS = 60_000L

    @Volatile private var loaded = false
    @Volatile private var accessToken = ""
    @Volatile private var refreshTokenValue = ""
    @Volatile private var expiresAtMs = 0L

    /** 刷新彻底失败(refresh_token 失效)时回调,用于把界面踢回登录页 */
    @Volatile var onSessionExpired: (() -> Unit)? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 首次访问时从本地读入内存,避免每次请求都读磁盘 */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            accessToken = p.getString(KEY_ACCESS, "").orEmpty()
            refreshTokenValue = p.getString(KEY_REFRESH, "").orEmpty()
            expiresAtMs = p.getLong(KEY_EXPIRES_AT, 0L)
            loaded = true
        }
    }

    /** 从 /auth/v1/token 或 /auth/v1/signup 的响应中提取并保存令牌 */
    fun save(context: Context, authResponse: JSONObject) {
        val access = authResponse.optString("access_token", "")
        if (access.isBlank()) return
        val refresh = authResponse.optString("refresh_token", "")
        val expiresInSec = authResponse.optLong("expires_in", 3600L)
        synchronized(this) {
            accessToken = access
            if (refresh.isNotBlank()) refreshTokenValue = refresh
            expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
            loaded = true
        }
        prefs(context).edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshTokenValue)
            .putLong(KEY_EXPIRES_AT, expiresAtMs)
            .apply()
    }

    fun access(context: Context): String {
        load(context)
        return accessToken
    }

    fun refreshToken(context: Context): String {
        load(context)
        return refreshTokenValue
    }

    /** 已无 access_token、或已进入过期窗口(含提前量) */
    fun isExpired(context: Context): Boolean {
        if (access(context).isBlank()) return true
        return System.currentTimeMillis() >= expiresAtMs - REFRESH_SKEW_MS
    }

    /** 本地是否留有一份可用来续期的会话 */
    fun hasRefreshToken(context: Context): Boolean = refreshToken(context).isNotBlank()

    fun clear(context: Context) {
        synchronized(this) {
            accessToken = ""
            refreshTokenValue = ""
            expiresAtMs = 0L
            loaded = true
        }
        prefs(context).edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }
}
