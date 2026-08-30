package com.example.redbook.ui.messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.RealtimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationItem(
    val actorName: String,
    val actorAvatar: String,
    val type: String,
    val postId: String,
    val postTitle: String,
    val commentContent: String,
    val time: Long
)

/** 通知列表类型：LIKE_FAV（赞和收藏）、COMMENT（评论/回复） */
enum class NotificationKind { LIKE_FAV, COMMENT }

class NotificationsViewModel(
    application: Application,
    private val kind: NotificationKind
) : AndroidViewModel(application) {

    private val repository = RealtimeRepository(application)

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
                _items.value = (0 until arr.length()).mapNotNull { i ->
                    val n = arr.getJSONObject(i)
                    val type = n.optString("type", "")
                    if (type !in allowedTypes) return@mapNotNull null
                    NotificationItem(
                        actorName = n.optString("actor_name", "").ifBlank { "小红书用户" },
                        actorAvatar = n.optString("actor_avatar", ""),
                        type = type,
                        postId = n.optString("post_id", ""),
                        postTitle = n.optString("post_title", "").ifBlank { "你的笔记" },
                        commentContent = n.optString("comment_content", ""),
                        time = n.optLong("created_at", 0L)
                    )
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
