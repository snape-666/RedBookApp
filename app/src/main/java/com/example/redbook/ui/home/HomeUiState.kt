package com.example.redbook.ui.home


import com.example.redbook.data.model.Note

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val notes: List<Note>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}