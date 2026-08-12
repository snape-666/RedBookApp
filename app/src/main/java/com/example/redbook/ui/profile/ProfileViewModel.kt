package com.example.redbook.ui.profile

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

class ProfileViewModel(application: Application, private val userXhsId: String) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val posts = repository.getUserPosts(userXhsId)
                val drafts = repository.getUserDrafts(userXhsId)
                val liked = repository.getUserLikedPosts(userXhsId)
                val favorited = repository.getUserFavoritedPosts(userXhsId)
                val comments = repository.getUserComments(userXhsId)
                _uiState.value = ProfileUiState(
                    posts = parsePosts(posts),
                    draftCount = drafts.length(),
                    likedPosts = parsePosts(liked),
                    favoritedPosts = parsePosts(favorited),
                    commentCount = comments.length(),
                    latestPostImage = parsePosts(posts).firstOrNull()?.imageUrl ?: ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy()
            }
        }
    }

    private fun parsePosts(arr: org.json.JSONArray): List<Note> {
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            Note(
                id = p.optString("post_id", ""),
                title = p.optString("title", ""),
                imageRes = R.drawable.test,
                imageUrl = p.optString("image_url", ""),
                avatarRes = R.drawable.test,
                userName = p.optString("author_name", ""),
                likeCount = p.optInt("like_count", 0)
            )
        }
    }

    data class ProfileUiState(
        val posts: List<Note> = emptyList(),
        val draftCount: Int = 0,
        val likedPosts: List<Note> = emptyList(),
        val favoritedPosts: List<Note> = emptyList(),
        val commentCount: Int = 0,
        val latestPostImage: String = ""
    )
}
