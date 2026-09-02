package com.example.redbook.notification

import android.content.Context
import com.example.redbook.data.model.NotificationSettings

/**
 * 通知设置本地存储(SharedPreferences)。
 * 键以 uid 前缀隔离,避免多账号串台;version 与云端合并时用于取新。
 */
object NotifPrefs {

    private const val PREFS_NAME = "notification_settings"
    private const val KEY_LAST_UID = "last_login_uid"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(uid: String, s: NotificationSettings, context: Context) {
        if (uid.isBlank()) return
        prefs(context).edit()
            .putBoolean("${uid}_receive", s.receiveEnabled)
            .putBoolean("${uid}_like_fav", s.likeFavEnabled)
            .putBoolean("${uid}_follow", s.followEnabled)
            .putBoolean("${uid}_comment", s.commentEnabled)
            .putBoolean("${uid}_dm", s.dmEnabled)
            .putLong("${uid}_version", s.version)
            .apply()
    }

    fun load(uid: String, context: Context): NotificationSettings {
        if (uid.isBlank()) return NotificationSettings()
        val p = prefs(context)
        return NotificationSettings(
            receiveEnabled = p.getBoolean("${uid}_receive", true),
            likeFavEnabled = p.getBoolean("${uid}_like_fav", true),
            followEnabled = p.getBoolean("${uid}_follow", true),
            commentEnabled = p.getBoolean("${uid}_comment", true),
            dmEnabled = p.getBoolean("${uid}_dm", true),
            version = p.getLong("${uid}_version", 0L)
        )
    }

    fun removeUid(uid: String, context: Context) {
        if (uid.isBlank()) return
        prefs(context).edit()
            .remove("${uid}_receive")
            .remove("${uid}_like_fav")
            .remove("${uid}_follow")
            .remove("${uid}_comment")
            .remove("${uid}_dm")
            .remove("${uid}_version")
            .apply()
    }

    fun setCachedLoginUid(uid: String, context: Context) {
        prefs(context).edit().putString(KEY_LAST_UID, uid).apply()
    }

    fun getCachedLoginUid(context: Context): String =
        prefs(context).getString(KEY_LAST_UID, "") ?: ""

    /** 已通知水印:记录该账号“已弹过通知”的最大时间戳,离线补发据此跳过已发过的 */
    fun saveWatermark(uid: String, ts: Long, context: Context) {
        if (uid.isBlank()) return
        prefs(context).edit().putLong("${uid}_last_notified", ts).apply()
    }

    fun loadWatermark(uid: String, context: Context): Long {
        if (uid.isBlank()) return 0L
        return prefs(context).getLong("${uid}_last_notified", 0L)
    }
}
