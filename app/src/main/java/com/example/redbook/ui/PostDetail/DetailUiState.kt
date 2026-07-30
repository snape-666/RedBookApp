package com.example.redbook.ui.PostDetail


import com.example.redbook.data.model.Comment
import com.example.redbook.data.model.PostDetail


sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val post: PostDetail,
        val comments: List<Comment>
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}