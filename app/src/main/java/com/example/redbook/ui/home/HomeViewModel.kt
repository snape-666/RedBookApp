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

    fun toggleLike(noteId: String) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = HomeUiState.Success(currentState.notes.map { note ->
                if (note.id == noteId) {
                    val delta = if (note.isLiked) -1 else 1
                    note.copy(isLiked = !note.isLiked, likeCount = note.likeCount + delta)
                } else note
            })
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val notes: List<Note>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
