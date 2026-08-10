package com.example.redbook.ui.publish

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PublishViewModel(
    application: Application,
    val authorUid: String,
    val authorXhsId: String,
    val authorName: String
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _uiState = MutableStateFlow(PublishUiState())
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    fun updateTitle(text: String) {
        if (text.length <= 20) _uiState.value = _uiState.value.copy(title = text)
    }

    fun updateContent(text: String) {
        _uiState.value = _uiState.value.copy(content = text)
    }

    fun addImages(uris: List<Uri>) {
        val current = _uiState.value.images
        val available = 11 - current.size
        if (available > 0) {
            _uiState.value = _uiState.value.copy(images = current + uris.take(available))
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(images = _uiState.value.images.filter { it != uri })
    }

    fun saveDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            try {
                repository.saveDraft(
                    "draft_${System.currentTimeMillis()}",
                    state.title, state.content,
                    authorUid, authorXhsId, authorName
                )
                _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun publish() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            try {
                val urls = mutableListOf<String>()
                for (img in state.images) {
                    val url = repository.uploadImage(img, getApplication())
                    if (url != null) urls.add(url)
                }
                if (state.images.isNotEmpty() && urls.isEmpty()) {
                    _uiState.value = state.copy(isSaving = false, error = "图片上传失败")
                    return@launch
                }
                repository.publishPost(
                    "post_${System.currentTimeMillis()}", state.title, state.content,
                    authorUid, authorName, authorXhsId, urls.joinToString(",")
                )
                _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = false)
            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false, error = e.message)
            }
        }
    }

    data class PublishUiState(
        val title: String = "",
        val content: String = "",
        val images: List<Uri> = emptyList(),
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        val savedAsDraft: Boolean = false,
        val error: String? = null
    )
}
