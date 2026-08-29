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
    val authorName: String,
    val authorAvatar: String = "",
    editDraft: com.example.redbook.data.model.Draft? = null
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)
    private val editDraftId: String? = editDraft?.draftId

    private val _uiState = MutableStateFlow(PublishUiState())
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    init {
        editDraft?.let { draft ->
            _uiState.value = PublishUiState(
                title = draft.title,
                content = draft.content,
                images = parseDraftImages(draft.imageUrl)
            )
        }
    }

    private fun parseDraftImages(imageUrl: String): List<Uri> {
        if (imageUrl.isBlank()) return emptyList()
        return imageUrl.split(",").filter { it.isNotBlank() }.map { url ->
            val cleaned = url.removePrefix("video:")
            if (cleaned.startsWith("/")) Uri.parse("file://$cleaned") else Uri.parse(cleaned)
        }
    }

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
        android.util.Log.d("RedBook", "saveDraft called, images=${state.images.size} isVideo=$isVideoMode title=${state.title}")
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, savedAsDraft = true)
            try {
                val imageUrl = try {
                    if (isVideoMode) {
                        val uri = state.images.first()
                        repository.uploadImage(uri, getApplication()) ?: ""
                    } else {
                        val paths = mutableListOf<String>()
                        for (img in state.images) { val path = cacheImage(img); if (path != null) paths.add(path) }
                        paths.joinToString(",")
                    }
                } catch (e: Exception) { "" }
                android.util.Log.d("RedBook", "saveDraft uid=$authorUid xhs=$authorXhsId imageUrl=$imageUrl")
                if (editDraftId != null) {
                    repository.updateDraft(editDraftId, state.title, state.content, imageUrl)
                } else {
                    repository.saveDraft(
                        "draft_${System.currentTimeMillis()}",
                        state.title, state.content,
                        authorUid, authorXhsId, authorName,
                        imageUrl
                    )
                }
                _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = true)
            } catch (e: Exception) {
                android.util.Log.e("RedBook", "saveDraft failed: ${e.message}")
                _uiState.value = state.copy(isSaving = false, error = e.message)
            }
        }
    }

    val isVideoMode: Boolean get() {
        val uri = _uiState.value.images.firstOrNull() ?: return false
        val path = uri.toString().lowercase()
        val videoExts = listOf(".mp4", ".3gp", ".webm", ".mkv", ".mov", ".avi", ".m4v")
        if (videoExts.any { path.contains(it) }) return true
        return try {
            val mime = getApplication<android.app.Application>().contentResolver.getType(uri)
            mime?.startsWith("video") == true
        } catch (e: Exception) { false }
    }

    fun publish() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, savedAsDraft = false)
            try {
                if (isVideoMode) {
                    val uri = state.images.first()
                    val url = repository.uploadImage(uri, getApplication())
                    if (url == null) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return@launch }
                    repository.publishPost("vid_${System.currentTimeMillis()}", state.title, "",
                        authorUid, authorName, authorXhsId, url, authorAvatar)
                } else {
                    val urls = mutableListOf<String>()
                    for (img in state.images) {
                        val url = repository.uploadImage(img, getApplication())
                        android.util.Log.d("RedBook", "publish uploadImage $img -> $url")
                        if (url != null) urls.add(url)
                    }
                    if (state.images.isNotEmpty() && urls.isEmpty()) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return@launch }
                    android.util.Log.d("RedBook", "publish images=${state.images.size} urls=${urls.size} -> ${urls.joinToString(",")}")
                    repository.publishPost("post_${System.currentTimeMillis()}", state.title, state.content, authorUid, authorName, authorXhsId, urls.joinToString(","), authorAvatar)
                }
                if (editDraftId != null) {
                    try { repository.deleteDraft(editDraftId) } catch (e: Exception) { }
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
            val file = java.io.File(getApplication<android.app.Application>().filesDir, "video_${System.currentTimeMillis()}.mp4")
            file.outputStream().use { input.copyTo(it) }
            input.close()
            file.absolutePath
        } catch (e: Exception) { null }
    }

    private suspend fun cacheImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = getApplication<android.app.Application>().contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val ext = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; mime.contains("gif") -> "gif"; else -> "jpg" }
            val input = cr.openInputStream(uri) ?: return@withContext null
            val file = java.io.File(getApplication<android.app.Application>().filesDir, "img_${System.currentTimeMillis()}.$ext")
            file.outputStream().use { input.copyTo(it) }
            input.close()
            "file://${file.absolutePath}"
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
