package com.example.redbook.data.model

/**
 * 通知设置(本地 + 云端 users 表双存)。
 * version 用于冲突合并:取值大的一侧覆盖,避免旧数据覆盖新设置。
 */
data class NotificationSettings(
    val receiveEnabled: Boolean = true,
    val likeFavEnabled: Boolean = true,
    val followEnabled: Boolean = true,
    val commentEnabled: Boolean = true,
    val dmEnabled: Boolean = true,
    val version: Long = 0L
) {
    companion object {
        const val VERSION_DEFAULT: Long = 0L
    }
}
