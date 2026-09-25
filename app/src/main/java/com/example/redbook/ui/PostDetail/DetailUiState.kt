package com.example.redbook.ui.PostDetail


import com.example.redbook.data.model.Comment
import com.example.redbook.data.model.PostDetail

//密封类封装了三种状态
sealed class DetailUiState {
    //加载中
    object Loading : DetailUiState()
    //加载成功
    data class Success(
        val post: PostDetail,
        val comments: List<Comment>
    ) : DetailUiState()
    //加载失败
    data class Error(val message: String) : DetailUiState()
}