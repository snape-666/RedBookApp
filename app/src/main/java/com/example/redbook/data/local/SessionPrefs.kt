package com.example.redbook.data.local

import android.content.Context
import com.example.redbook.data.repository.SupabaseAuthRepository

/**
 * 登录会话本地持久化(SharedPreferences)。
 * 登录成功后保存当前用户资料,进程被杀/冷启动后据此恢复登录态;
 * 用户主动退出登录时清除,即可回到登录页(重装 App 也会清空)。
 */
object SessionPrefs {

    private const val PREFS_NAME = "login_session"
    private const val KEY_UID = "uid"
    private const val KEY_XHS_ID = "xhs_id"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_EMAIL = "email"
    private const val KEY_GENDER = "gender"
    private const val KEY_BIRTHDAY = "birthday"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_BACKGROUND_URL = "background_url"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 保存当前登录用户(登录成功后调用) */
    fun save(user: SupabaseAuthRepository.UserData, context: Context) {
        if (user.uid.isBlank()) return
        prefs(context).edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_XHS_ID, user.xhsId)
            .putString(KEY_NICKNAME, user.nickname)
            .putString(KEY_ACCOUNT, user.account)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_GENDER, user.gender)
            .putString(KEY_BIRTHDAY, user.birthday)
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .putString(KEY_BACKGROUND_URL, user.backgroundUrl)
            .apply()
    }

    /** 是否有已保存的登录会话 */
    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getString(KEY_UID, "").orEmpty().isNotBlank()

    /** 读取已保存的登录用户;无会话返回 null */
    fun load(context: Context): SupabaseAuthRepository.UserData? {
        val p = prefs(context)
        val uid = p.getString(KEY_UID, "").orEmpty()
        if (uid.isBlank()) return null
        return SupabaseAuthRepository.UserData(
            uid = uid,
            email = p.getString(KEY_EMAIL, "").orEmpty(),
            account = p.getString(KEY_ACCOUNT, "").orEmpty(),
            nickname = p.getString(KEY_NICKNAME, "").orEmpty(),
            xhsId = p.getString(KEY_XHS_ID, "").orEmpty(),
            emailVerified = true,
            gender = p.getString(KEY_GENDER, "").orEmpty(),
            birthday = p.getString(KEY_BIRTHDAY, "").orEmpty(),
            avatarUrl = p.getString(KEY_AVATAR_URL, "").orEmpty(),
            backgroundUrl = p.getString(KEY_BACKGROUND_URL, "").orEmpty()
        )
    }

    /** 退出登录:清除会话 */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
