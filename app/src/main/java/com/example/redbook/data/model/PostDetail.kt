package com.example.redbook.data.model

data class PostDetail(
    val postId: String,
    val imageRes: Int,                 // 帖子图片资源（本地测试用）
    val title: String,
    val content: String,
    val publishTime: Long,
    val ipLocation: String,            // 发帖 IP
    val likeCount: Int,                // 帖子总点赞数
    val favoriteCount: Int,            // 帖子总收藏数
    val commentCount: Int,             // 帖子总评论数（不含回复）
    val isLiked: Boolean,              // 当前用户是否已点赞帖子
    val isFavorited: Boolean,          // 当前用户是否已收藏帖子
    val isFollowed: Boolean,           // 当前用户是否已关注作者（用于顶部栏）
    val authorId: String,
    val authorName: String,
    val authorAvatar: Int              // 作者头像资源
)