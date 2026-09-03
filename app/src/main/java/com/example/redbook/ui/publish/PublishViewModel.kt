package com.example.redbook.ui.publish

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发布页 VM。
 * 三种模式：
 *  - 新发布（默认）
 *  - 编辑草稿：editDraft 非空，发布成功后删除草稿
 *  - 编辑已发布帖子：editPost 非空，发布改为 PATCH 原帖（保留原 id），
 *    图片列表中的远端 http(s) 图直接保留，仅上传新增的本地图
 */
class PublishViewModel(
    application: Application,
    val authorUid: String,
    val authorXhsId: String,
    val authorName: String,
    val authorAvatar: String = "",
    editDraft: com.example.redbook.data.model.Draft? = null,
    editPost: com.example.redbook.data.model.PostToEdit? = null
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)
    private val editDraftId: String? = editDraft?.draftId
    private val editPostId: String? = editPost?.postId

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
        editPost?.let { post ->
            val isVideoPost = post.imageUrl.trimStart().startsWith("video:")
            _uiState.value = PublishUiState(
                title = post.title,
                content = post.content,
                images = parseDraftImages(post.imageUrl),
                forceVideo = isVideoPost
            )
        }
    }

    /** 编辑帖子模式下要写回的可见性（public/private） */
    var editPostVisibility: String = editPost?.visibility ?: "public"

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
            // 去重：同一 uri 只保留一张（避免重复选择与 LazyRow key 冲突）
            val newUris = uris.filter { it !in current }.take(available)
            _uiState.value = _uiState.value.copy(images = current + newUris)
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(images = _uiState.value.images.filter { it != uri })
    }

    /** 按索引删除图片 */
    fun removeImageAt(index: Int) {
        val list = _uiState.value.images
        if (index in list.indices) {
            _uiState.value = _uiState.value.copy(images = list.filterIndexed { i, _ -> i != index })
        }
    }

    /** 拖拽排序：把 from 位置移动到 to 位置 */
    fun moveImage(from: Int, to: Int) {
        val list = _uiState.value.images
        if (from == to) return
        if (from !in list.indices || to !in list.indices) return
        val mutable = list.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _uiState.value = _uiState.value.copy(images = mutable)
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
        if (_uiState.value.forceVideo) return true
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
            // IP 归属地：缓存命中则直接返回，未命中时定位/公网 IP 解析
            val ipLocation = try {
                com.example.redbook.data.repository.IpLocationProvider.resolveProvince(getApplication()) ?: ""
            } catch (e: Exception) { "" }
            try {
                if (editPostId != null) {
                    publishEditedPost(state)
                    return@launch
                }
                if (isVideoMode) {
                    val uri = state.images.first()
                    val url = repository.uploadImage(uri, getApplication())
                    if (url == null) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return@launch }
                    // 视频存 posts 表，image_url 带 video: 前缀
                    repository.publishPost("vid_${System.currentTimeMillis()}", state.title, "",
                        authorUid, authorName, authorXhsId, url, authorAvatar, ipLocation)
                } else {
                    val urls = mutableListOf<String>()
                    for (img in state.images) {
                        val url = repository.uploadImage(img, getApplication())
                        android.util.Log.d("RedBook", "publish uploadImage $img -> $url")
                        if (url != null) urls.add(url)
                    }
                    if (state.images.isNotEmpty() && urls.isEmpty()) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return@launch }
                    android.util.Log.d("RedBook", "publish images=${state.images.size} urls=${urls.size} -> ${urls.joinToString(",")}")
                    repository.publishPost("post_${System.currentTimeMillis()}", state.title, state.content, authorUid, authorName, authorXhsId, urls.joinToString(","), authorAvatar, ipLocation)
                }
                if (editDraftId != null) {
                    try { repository.deleteDraft(editDraftId) } catch (e: Exception) { }
                }
                _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = false)
            } catch (e: Exception) { _uiState.value = state.copy(isSaving = false, error = e.message) }
        }
    }

    /** 编辑已发布帖子：远端图保留，仅上传新增本地图，最后 PATCH 原帖 */
    private suspend fun publishEditedPost(state: PublishUiState) {
        val finalImageUrl = if (isVideoMode) {
            // 视频帖：替换为新的视频文件（若原本就是远端 url 则去掉 video: 前缀判断）
            val uri = state.images.first()
            if (isRemoteImage(uri)) {
                // 未替换视频：保留原始 url
                "video:${uri.toString().removePrefix("video:")}"
            } else {
                val url = repository.uploadImage(uri, getApplication())
                if (url == null) { _uiState.value = state.copy(isSaving = false, error = "上传失败"); return }
                "video:$url"
            }
        } else {
            val urls = mutableListOf<String>()
            for (img in state.images) {
                if (isRemoteImage(img)) {
                    urls.add(img.toString())
                } else {
                    val url = repository.uploadImage(img, getApplication())
                    if (url != null) urls.add(url)
                }
            }
            if (state.images.isNotEmpty() && urls.isEmpty()) {
                _uiState.value = state.copy(isSaving = false, error = "上传失败")
                return
            }
            urls.joinToString(",")
        }
        repository.updatePost(editPostId!!, state.title, state.content, finalImageUrl, editPostVisibility)
        _uiState.value = state.copy(isSaving = false, saved = true, savedAsDraft = false)
    }

    private fun isRemoteImage(uri: Uri): Boolean {
        val s = uri.toString()
        return s.startsWith("http://") || s.startsWith("https://")
    }

    fun clearImages() { _uiState.value = _uiState.value.copy(images = emptyList()) }

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
        val error: String? = null,
        val forceVideo: Boolean = false
    )
}
