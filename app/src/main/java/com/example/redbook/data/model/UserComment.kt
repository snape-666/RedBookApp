package com.example.redbook.data.model

data class UserComment(
    val commentId: String,
    val postId: String,
    val postTitle: String,
    val content: String,
    val authorName: String,
    val isReply: Boolean,
    val parentUser: String,
    val parentContent: String,
    val likeCount: Int,
    val timestamp: Long,
    val ipLocation: String = "未知",
    val isLiked: Boolean = false
)
