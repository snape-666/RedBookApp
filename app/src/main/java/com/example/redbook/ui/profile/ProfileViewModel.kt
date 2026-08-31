package com.example.redbook.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.R
import com.example.redbook.data.model.Note
import com.example.redbook.data.model.UserComment
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    application: Application,
    private val profileUid: String,
    private val profileXhsId: String,
    private val viewerUid: String = ""
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 对方主页状态：资料、关注关系、备注 */
    private val _userProfile = MutableStateFlow<UserProfileState>(UserProfileState())
    val userProfile: StateFlow<UserProfileState> = _userProfile.asStateFlow()

    /** 是否为查看自己的主页 */
    val isSelf: Boolean = profileUid == viewerUid || viewerUid.isBlank()

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            lateinit var posts: org.json.JSONArray
            lateinit var drafts: org.json.JSONArray
            lateinit var liked: org.json.JSONArray
            lateinit var favorited: org.json.JSONArray
            lateinit var comments: org.json.JSONArray
            var likedIds: Set<String> = emptySet()
            var followCount = 0
            var fansCount = 0
            var likeCount = 0
            kotlinx.coroutines.coroutineScope {
                val d1 = async { safe { repository.getUserPosts(profileUid, profileXhsId) } }
                val d2 = async { safe { repository.getUserDrafts(profileUid, profileXhsId) } }
                val d3 = async { safe { repository.getUserLikedPosts(profileUid) } }
                val d4 = async { safe { repository.getUserFavoritedPosts(profileUid) } }
                val d5 = async { safe { repository.getUserComments(profileUid, profileXhsId) } }
                val d6 = async { try { repository.getLikedPostIds(profileUid) } catch (e: Exception) { emptySet() } }
                val d7 = async { try { repository.getFollowingCount(profileUid) } catch (e: Exception) { 0 } }
                val d8 = async { try { repository.getFansCount(profileUid) } catch (e: Exception) { 0 } }
                val d9 = async { try { repository.getLikeCount(profileUid, profileXhsId) } catch (e: Exception) { 0 } }
                posts = d1.await()
                drafts = d2.await()
                liked = d3.await()
                favorited = d4.await()
                comments = d5.await()
                likedIds = d6.await()
                followCount = d7.await()
                fansCount = d8.await()
                likeCount = d9.await()
            }
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
                latestDraftImage = latestDraftImage,
                followCount = followCount,
                fansCount = fansCount,
                likeCount = likeCount
            )
            _refreshing.value = false
        }
    }

    /** 加载对方主页：资料 + 关注关系 + 备注 */
    fun loadUserProfile(targetUid: String) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            try {
                val u = repository.getUserByUid(targetUid)
                if (u == null) {
                    _userProfile.value = UserProfileState()
                    return@launch
                }
                // 我是否已关注对方
                val iFollow = if (viewerUid.isNotBlank()) repository.isFollowing(viewerUid, targetUid) else false
                // 对方是否已关注我（用于互相关注状态）
                val heFollowsMe = if (viewerUid.isNotBlank()) repository.isFollowing(targetUid, viewerUid) else false
                // 备注与关注关系无关，总是读取
                val remark = if (viewerUid.isNotBlank()) repository.getRemark(viewerUid, targetUid) else ""
                // 对方主页统计（关注/粉丝/获赞）
                val followCount = try { repository.getFollowingCount(targetUid) } catch (_: Exception) { 0 }
                val fansCount = try { repository.getFansCount(targetUid) } catch (_: Exception) { 0 }
                val likeCount = try { repository.getLikeCount(targetUid, u.optString("xhs_id", "")) } catch (_: Exception) { 0 }
                _userProfile.value = UserProfileState(
                    uid = targetUid,
                    userName = u.optString("nickname", "").ifBlank { "小红书用户" },
                    avatarUrl = u.optString("avatar_url", ""),
                    backgroundUrl = u.optString("background_url", ""),
                    xhsId = u.optString("xhs_id", ""),
                    gender = u.optString("gender", ""),
                    birthday = u.optString("birthday", ""),
                    ipLocation = "未知",
                    iFollow = iFollow,
                    heFollowsMe = heFollowsMe,
                    remark = remark,
                    followCount = followCount,
                    fansCount = fansCount,
                    likeCount = likeCount
                )
            } catch (_: Exception) {
                _userProfile.value = UserProfileState()
            }
        }
    }

    /** 关注/取消关注对方 */
    fun toggleFollowTarget() {
        val cur = _userProfile.value
        if (cur.uid.isBlank() || viewerUid.isBlank()) return
        val newFollow = !cur.iFollow
        _userProfile.value = cur.copy(iFollow = newFollow)
        viewModelScope.launch {
            try { repository.follow(viewerUid, cur.uid, newFollow) } catch (_: Exception) { }
        }
    }

    /** 取消关注（从弹窗触发） */
    fun unfollowTarget() {
        val cur = _userProfile.value
        if (cur.uid.isBlank() || viewerUid.isBlank()) return
        _userProfile.value = cur.copy(iFollow = false)
        viewModelScope.launch {
            try { repository.follow(viewerUid, cur.uid, false) } catch (_: Exception) { }
        }
    }

    /** 设置备注（空则清空） */
    fun setRemark(remark: String) {
        val cur = _userProfile.value
        if (cur.uid.isBlank() || viewerUid.isBlank()) return
        _userProfile.value = cur.copy(remark = remark)
        viewModelScope.launch {
            try { repository.setRemark(viewerUid, cur.uid, remark) } catch (_: Exception) { }
        }
    }

    fun deleteComment(commentId: String) {
        val current = _uiState.value
        _uiState.value = current.copy(comments = current.comments.filter { it.commentId != commentId })
        viewModelScope.launch {
            try { repository.deleteComment(commentId) } catch (_: Exception) { }
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
                timestamp = c.optLong("created_at", 0),
                ipLocation = c.optString("ip_location", "").ifBlank { "未知" }
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
                avatarUrl = p.optString("author_avatar", ""),
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
        val latestDraftImage: String = "",
        val followCount: Int = 0,
        val fansCount: Int = 0,
        val likeCount: Int = 0
    )

    data class UserProfileState(
        val uid: String = "",
        val userName: String = "",
        val avatarUrl: String = "",
        val backgroundUrl: String = "",
        val xhsId: String = "",
        val gender: String = "",
        val birthday: String = "",
        val ipLocation: String = "未知",
        val iFollow: Boolean = false,
        val heFollowsMe: Boolean = false,
        val remark: String = "",
        val followCount: Int = 0,
        val fansCount: Int = 0,
        val likeCount: Int = 0
    )
}
