package com.example.redbook.ui.publish

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    val isVideoMode: Boolean get() {
        val uri = _uiState.value.images.firstOrNull() ?: return false
        return try {
            val mime = getApplication<android.app.Application>().contentResolver.getType(uri)
            mime?.startsWith("video") == true
        } catch (e: Exception) { false }
    }

    fun publish() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            try {
                if (isVideoMode) {
                    val uri = state.images.first()
                    val path = cacheVideo(uri) ?: uri.toString()
                    repository.publishPost("vid_${System.currentTimeMillis()}", state.title, "",
                        authorUid, authorName, authorXhsId, "video:$path")
                } else {
                    val urls = mutableListOf<String>()
                    for (img in state.images) { val url = repository.uploadImage(img, getApplication()); if (url != null) urls.add(url) }
                    if (state.images.isNotEmpty() && urls.isEmpty()) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return@launch }
                    repository.publishPost("post_${System.currentTimeMillis()}", state.title, state.content, authorUid, authorName, authorXhsId, urls.joinToString(","))
                }
                _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = false)
            } catch (e: Exception) { _uiState.value = state.copy(isSaving = false, error = e.message) }
        }
    }

    fun clearImages() { _uiState.value = _uiState.value.copy(images = emptyList()) }

    private suspend fun cacheVideo(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = getApplication<android.app.Application>().contentResolver
            val input = cr.openInputStream(uri) ?: return@withContext null
            val file = java.io.File(getApplication<android.app.Application>().cacheDir, "video_${System.currentTimeMillis()}.mp4")
            file.outputStream().use { input.copyTo(it) }
            input.close()
            file.absolutePath
        } catch (e: Exception) { null }
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
