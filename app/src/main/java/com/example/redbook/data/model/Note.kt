package com.example.redbook.data.model

data class Note(
    val id: String,
    val imageRes: Int = 0,
    val imageUrl: String = "",
    val title: String = "",
    val avatarRes: Int = 0,
    val userName: String = "",
    val likeCount: Int = 0,
    val isLiked: Boolean = false
)
