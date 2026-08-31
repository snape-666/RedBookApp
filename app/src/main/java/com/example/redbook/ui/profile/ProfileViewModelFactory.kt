package com.example.redbook.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ProfileViewModelFactory(
    private val application: Application,
    private val uid: String,
    private val xhsId: String,
    private val viewerUid: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(application, uid, xhsId, viewerUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
