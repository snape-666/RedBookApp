package com.example.redbook.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.model.Note
import com.example.redbook.data.repository.HomeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application, private val userUid: String = "") : AndroidViewModel(application) {

    private val repository = HomeRepository(SupabaseAuthRepository(application))

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init { fetchNotes() }

    fun fetchNotes() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val notes = repository.getNotes(userUid)
                _uiState.value = HomeUiState.Success(notes)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /** 关注页：只加载我关注的人发布的帖子 */
    fun fetchFollowingNotes() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val notes = repository.getFollowingNotes(userUid)
                _uiState.value = HomeUiState.Success(notes)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun toggleLike(noteId: String) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            val note = currentState.notes.firstOrNull { it.id == noteId } ?: return
            val newLiked = !note.isLiked
            val newCount = note.likeCount + (if (newLiked) 1 else -1)
            _uiState.value = HomeUiState.Success(currentState.notes.map {
                if (it.id == noteId) it.copy(isLiked = newLiked, likeCount = newCount) else it
            })
            if (userUid.isNotBlank()) {
                viewModelScope.launch {
                    try {
                        repository.supabase.recordLike(userUid, noteId, newLiked)
                        repository.supabase.updatePostLike(noteId, if (newLiked) 1 else -1)
                    } catch (_: Exception) { }
                }
            }
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val notes: List<Note>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
