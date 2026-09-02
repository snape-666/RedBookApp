package com.example.redbook.notification

import android.content.Context
import com.example.redbook.data.model.PrivacySettings

/**
 * 隐私设置本地缓存(SharedPreferences，按 uid 隔离)。
 * version 用于与云端合并时取新，避免旧值覆盖新设置。
 */
object PrivacyPrefs {

    private const val PREFS_NAME = "privacy_settings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(uid: String, s: PrivacySettings, context: Context) {
        if (uid.isBlank()) return
        prefs(context).edit()
            .putBoolean("${uid}_posts", s.showPosts)
            .putBoolean("${uid}_comments", s.showComments)
            .putBoolean("${uid}_favorites", s.showFavorites)
            .putBoolean("${uid}_likes", s.showLikes)
            .putLong("${uid}_version", s.version)
            .apply()
    }

    fun load(uid: String, context: Context): PrivacySettings {
        if (uid.isBlank()) return PrivacySettings()
        val p = prefs(context)
        return PrivacySettings(
            showPosts = p.getBoolean("${uid}_posts", true),
            showComments = p.getBoolean("${uid}_comments", true),
            showFavorites = p.getBoolean("${uid}_favorites", true),
            showLikes = p.getBoolean("${uid}_likes", true),
            version = p.getLong("${uid}_version", 0L)
        )
    }

    fun removeUid(uid: String, context: Context) {
        if (uid.isBlank()) return
        prefs(context).edit()
            .remove("${uid}_posts")
            .remove("${uid}_comments")
            .remove("${uid}_favorites")
            .remove("${uid}_likes")
            .remove("${uid}_version")
            .apply()
    }
}
