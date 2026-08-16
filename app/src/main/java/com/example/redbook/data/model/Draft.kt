package com.example.redbook.data.model

data class Draft(
    val draftId: String,
    val title: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val createdAt: Long = 0L
)
