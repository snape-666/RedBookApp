package com.example.redbook.data.model

/**
 * 待编辑的已发布帖子（从详情页"编辑"进入发布页时回填用）。
 * imageUrl 为 posts 表原始 image_url（远端 url，可能带 video: 前缀），
 * 编辑时保持原帖 id，仅 PATCH title/content/image_url/visibility。
 */
data class PostToEdit(
    val postId: String,
    val title: String,
    val content: String,
    val imageUrl: String = "",
    val visibility: String = "public"
)
