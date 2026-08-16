package com.example.redbook.ui.draft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.model.Draft
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DraftViewModel(application: Application, private val userUid: String, private val userXhsId: String) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _drafts = MutableStateFlow<List<Draft>>(emptyList())
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val arr = repository.getUserDrafts(userUid, userXhsId)
                _drafts.value = (0 until arr.length()).map { i ->
                    val d = arr.getJSONObject(i)
                    Draft(
                        draftId = d.optString("draft_id", ""),
                        title = d.optString("title", ""),
                        content = d.optString("content", ""),
                        imageUrl = d.optString("image_url", ""),
                        createdAt = d.optLong("created_at", 0)
                    )
                }
            } catch (e: Exception) {
                _drafts.value = emptyList()
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            try {
                repository.deleteDraft(draftId)
            } catch (e: Exception) { }
            load()
        }
    }
}
