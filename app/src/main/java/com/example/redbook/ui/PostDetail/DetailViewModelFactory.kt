package com.example.redbook.ui.PostDetail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DetailViewModelFactory(
    private val application: Application,
    private val userUid: String,
    private val userXhsId: String,
    private val userName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            return DetailViewModel(application, userUid, userXhsId, userName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
