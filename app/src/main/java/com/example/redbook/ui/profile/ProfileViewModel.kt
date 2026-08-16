package com.example.redbook.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.R
import com.example.redbook.data.model.Note
import com.example.redbook.data.model.UserComment
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application, private val userUid: String, private val userXhsId: String) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val posts = safe { repository.getUserPosts(userUid, userXhsId) }
            val drafts = safe { repository.getUserDrafts(userUid, userXhsId) }
            val liked = safe { repository.getUserLikedPosts(userUid) }
            val favorited = safe { repository.getUserFavoritedPosts(userUid) }
            val comments = safe { repository.getUserComments(userUid, userXhsId) }
            val likedIds = try { repository.getLikedPostIds(userUid) } catch (e: Exception) { emptySet() }
            val parsedPosts = parsePosts(posts, likedIds)
            val latestDraftImage = if (drafts.length() > 0) drafts.getJSONObject(0).optString("image_url", "") else ""
            _uiState.value = ProfileUiState(
                posts = parsedPosts,
                draftCount = drafts.length(),
                likedPosts = parsePosts(liked, likedIds),
                favoritedPosts = parsePosts(favorited, likedIds),
                commentCount = comments.length(),
                comments = parseUserComments(comments),
                latestPostImage = parsedPosts.firstOrNull()?.imageUrl ?: "",
                latestDraftImage = latestDraftImage
            )
            _refreshing.value = false
        }
    }

    private suspend fun safe(block: suspend () -> org.json.JSONArray): org.json.JSONArray =
        try { block() } catch (e: Exception) { org.json.JSONArray() }

    private fun parseUserComments(arr: org.json.JSONArray): List<UserComment> {
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val parentId = c.optString("parent_id", "")
            UserComment(
                commentId = c.optString("comment_id", ""),
                postId = c.optString("post_id", ""),
                postTitle = c.optString("post_title", ""),
                content = c.optString("content", ""),
                authorName = c.optString("author_name", ""),
                isReply = parentId.isNotEmpty(),
                parentUser = c.optString("parent_user", ""),
                parentContent = c.optString("parent_content", ""),
                likeCount = c.optInt("like_count", 0),
                timestamp = c.optLong("created_at", 0)
            )
        }
    }

    private fun parsePosts(arr: org.json.JSONArray, likedIds: Set<String> = emptySet()): List<Note> {
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val postId = p.optString("post_id", "")
            Note(
                id = postId,
                title = p.optString("title", ""),
                imageRes = R.drawable.test,
                imageUrl = p.optString("image_url", ""),
                avatarRes = R.drawable.test,
                userName = p.optString("author_name", ""),
                likeCount = p.optInt("like_count", 0),
                isLiked = likedIds.contains(postId)
            )
        }
    }

    data class ProfileUiState(
        val posts: List<Note> = emptyList(),
        val draftCount: Int = 0,
        val likedPosts: List<Note> = emptyList(),
        val favoritedPosts: List<Note> = emptyList(),
        val commentCount: Int = 0,
        val comments: List<UserComment> = emptyList(),
        val latestPostImage: String = "",
        val latestDraftImage: String = ""
    )
}
