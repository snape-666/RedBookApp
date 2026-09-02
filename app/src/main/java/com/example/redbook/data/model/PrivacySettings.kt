package com.example.redbook.data.model

/**
 * 隐私设置：控制主页四个内容区(笔记/评论/收藏/赞过)对他人是否可见。
 * 默认全部开启；version 用于本地/云端双存时取新。
 */
data class PrivacySettings(
    val showPosts: Boolean = true,
    val showComments: Boolean = true,
    val showFavorites: Boolean = true,
    val showLikes: Boolean = true,
    val version: Long = 0L
)
