package com.example.redbook.data.model

import android.net.Uri

data class Comment(
    val id: String,
    val userId: String,
    val userName: String,
    val avatarRes: Int,
    val images: List<Uri> = emptyList(),
    val content: String,
    val timestamp: Long,
    val ipLocation: String,
    val likeCount: Int,
    val isLiked: Boolean,
    val isAuthor: Boolean,             // 是否为帖子作者本人评论
    val avatarUrl: String = "",        // 评论者头像 URL（实时展示用）
    val replies: List<Reply> = emptyList() // 二级回复列表

)
