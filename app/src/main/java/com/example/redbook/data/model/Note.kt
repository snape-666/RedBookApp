package com.example.redbook.data.model

data class Note(
    val id: Int,
    val imageRes: Int = 0,      // 临时给个默认值 0
    val title: String = "",
    val avatarRes: Int = 0,
    val userName: String = "",
    val likeCount: Int = 0,
    val isLiked: Boolean = false
)
