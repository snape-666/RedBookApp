package com.example.redbook.data.model

data class PostDetail(
    val postId: String,
    val imageRes: Int,
    val imageUrl: String = "",
    val title: String,
    val content: String,
    val publishTime: Long,
    val ipLocation: String,
    val viewCount: Int = 0,
    val likeCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,
    val isFavorited: Boolean,
    val isFollowed: Boolean,
    val authorId: String,
    val authorName: String,
    val authorAvatar: Int,
    val authorAvatarUrl: String = ""
)