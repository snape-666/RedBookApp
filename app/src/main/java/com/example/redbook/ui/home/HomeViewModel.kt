package com.example.redbook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.HomeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = HomeRepository()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        fetchNotes()
    }

    fun fetchNotes() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            delay(800)
            try {
                val notes = repository.getNotes()
                _uiState.value = HomeUiState.Success(notes)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "加载失败")
            }
        }
    }
    fun toggleLike(noteId: Int) {
        val currentState = _uiState.value
        // 只有当前状态是 Success 时才处理
        if (currentState is HomeUiState.Success) {
            val currentNotes = currentState.notes

            //找到并更新目标笔记
            val updatedNotes = currentNotes.map { note ->
                if (note.id == noteId) {
                    val newLikeCount = if (note.isLiked) note.likeCount - 1 else note.likeCount + 1
                    note.copy(
                        isLiked = !note.isLiked,
                        likeCount = newLikeCount
                    )
                } else {
                    note
                }
            }

            //  将更新后的列表重新包装成 Success 状态，发射出去
            _uiState.value = HomeUiState.Success(updatedNotes)
        }
    }
}