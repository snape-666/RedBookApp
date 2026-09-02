package com.example.redbook.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.R
import com.example.redbook.data.model.Note
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(application: Application, private val userUid: String) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _posts = MutableStateFlow<List<Note>>(emptyList())
    val posts: StateFlow<List<Note>> = _posts.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val arr = repository.getBrowseHistory(userUid)
                val likedIds = try { repository.getLikedPostIds(userUid) } catch (e: Exception) { emptySet() }
                var notes = (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    val postId = p.optString("post_id", "")
                    Note(
                        id = postId,
                        title = p.optString("title", ""),
                        imageRes = R.drawable.test,
                        imageUrl = p.optString("image_url", ""),
                        avatarRes = R.drawable.test,
                        avatarUrl = p.optString("author_avatar", ""),
                        userName = p.optString("author_name", ""),
                        likeCount = p.optInt("like_count", 0),
                        isLiked = likedIds.contains(postId),
                        authorUid = p.optString("author_uid", "")
                    )
                }
                // 备注名替换：作者名替换为"我"对该作者的备注
                notes = applyRemarks(notes)
                _posts.value = notes
            } catch (e: Exception) {
                _posts.value = emptyList()
            }
        }
    }

    private suspend fun applyRemarks(notes: List<Note>): List<Note> {
        if (notes.isEmpty()) return notes
        return try {
            val uids = notes.map { it.authorUid }.filter { it.isNotBlank() }.distinct()
            if (uids.isEmpty()) return notes
            val remarks = repository.getRemarks(userUid, uids)
            if (remarks.isEmpty()) return notes
            notes.map { note ->
                val remark = remarks[note.authorUid]
                if (!remark.isNullOrBlank()) note.copy(userName = remark) else note
            }
        } catch (e: Exception) { notes }
    }

    fun deleteBrowse(postId: String) {
        viewModelScope.launch {
            try { repository.deleteBrowse(userUid, postId) } catch (_: Exception) { }
            load()
        }
    }

    fun deleteBrowses(postIds: Set<String>) {
        viewModelScope.launch {
            postIds.forEach { try { repository.deleteBrowse(userUid, it) } catch (_: Exception) { } }
            load()
        }
    }
}
