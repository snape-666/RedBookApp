package com.example.redbook.ui.draft

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DraftViewModelFactory(private val application: Application, private val uid: String, private val xhsId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DraftViewModel::class.java)) {
            return DraftViewModel(application, uid, xhsId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
