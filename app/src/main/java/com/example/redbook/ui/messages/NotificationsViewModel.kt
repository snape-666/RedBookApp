package com.example.redbook.ui.messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationItem(
    val actorName: String,
    val actorAvatar: String,
    val actorUid: String = "",
    val type: String,
    val postId: String,
    val postTitle: String,
    val commentContent: String,
    val time: Long,
    val postImage: String = "",
    val commentId: String = "",
    val deleted: Boolean = false
)

/** 通知列表类型：LIKE_FAV（赞和收藏）、COMMENT（评论/回复） */
enum class NotificationKind { LIKE_FAV, COMMENT }

class NotificationsViewModel(
    application: Application,
    private val kind: NotificationKind
) : AndroidViewModel(application) {

    private val repository = RealtimeRepository(application)
    private val authRepository = SupabaseAuthRepository(application)

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(userUid: String) {
        if (userUid.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val arr = repository.getNotifications(userUid)
                val allowedTypes = when (kind) {
                    NotificationKind.LIKE_FAV -> setOf("like", "favorite")
                    NotificationKind.COMMENT -> setOf("comment", "reply")
                }
                val rawItems = (0 until arr.length()).mapNotNull { i ->
                    val n = arr.getJSONObject(i)
                    val type = n.optString("type", "")
                    if (type !in allowedTypes) return@mapNotNull null
                    val actorUid = n.optString("actor_uid", "")
                    var actorAvatar = n.optString("actor_avatar", "")
                    // 历史通知可能没存头像：按 uid 兜底查用户表
                    if (actorAvatar.isBlank() && actorUid.isNotBlank()) {
                        try {
                            val u = authRepository.getUserByUid(actorUid)
                            if (u != null) actorAvatar = u.optString("avatar_url", "")
                        } catch (_: Exception) { }
                    }
                    // 查帖子封面图（通知表没存，按 postId 实时查）
                    var postImage = ""
                    val postId = n.optString("post_id", "")
                    if (postId.isNotBlank()) {
                        try {
                            val p = authRepository.getPost(postId)
                            if (p != null) {
                                postImage = p.optString("image_url", "")
                                    .split(",").firstOrNull()?.trim() ?: ""
                            }
                        } catch (_: Exception) { }
                    }
                    NotificationItem(
                        actorName = n.optString("actor_name", "").ifBlank { "小红书用户" },
                        actorAvatar = actorAvatar,
                        actorUid = actorUid,
                        type = type,
                        postId = postId,
                        postTitle = n.optString("post_title", "").ifBlank { "你的笔记" },
                        commentContent = n.optString("comment_content", ""),
                        time = n.optLong("created_at", 0L),
                        postImage = postImage,
                        commentId = n.optString("comment_id", "")
                    )
                }
                // 按 actor_uid 批量替换为我对该用户的备注名（有备注优先）
                val actorUids = rawItems.map { it.actorUid }.filter { it.isNotBlank() }.distinct()
                val remarks = try { authRepository.getRemarks(userUid, actorUids) } catch (e: Exception) { emptyMap<String, String>() }
                val itemsWithRemark = rawItems.map { item ->
                    val remark = remarks[item.actorUid]
                    if (!remark.isNullOrBlank()) item.copy(actorName = remark) else item
                }
                // 评论类型：批量查 comment_id 是否仍存在，标记已删除
                if (kind == NotificationKind.COMMENT) {
                    val commentIds = itemsWithRemark.map { it.commentId }.filter { it.isNotBlank() }.toSet()
                    val existing = authRepository.getExistingCommentIds(commentIds)
                    _items.value = itemsWithRemark.map { item ->
                        if (item.commentId.isNotBlank() && item.commentId !in existing) {
                            item.copy(deleted = true)
                        } else item
                    }
                } else {
                    _items.value = itemsWithRemark
                }
            } catch (e: Exception) {
                _items.value = emptyList()
            }
            _loading.value = false
        }
    }
}

class NotificationsViewModelFactory(
    private val application: Application,
    private val kind: NotificationKind
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            return NotificationsViewModel(application, kind) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
