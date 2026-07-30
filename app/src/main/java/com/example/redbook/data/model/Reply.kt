package com.example.redbook.data.model

import android.net.Uri

data class Reply(
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
    val isAuthor: Boolean              // 是否为帖子作者本人回复
)