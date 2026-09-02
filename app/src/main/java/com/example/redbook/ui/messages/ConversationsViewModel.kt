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

data class ConversationItem(
    val conversationId: String,
    val peerUid: String,
    val peerName: String,
    val peerAvatar: String,
    val lastMessage: String,
    val lastTime: Long,
    val unreadCount: Int,
    val peerRemark: String = ""
) {
    /** 展示名：有备注用备注，否则用昵称 */
    val displayName: String
        get() = peerRemark.ifBlank { peerName }
}

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RealtimeRepository(application)

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(userUid: String) {
        if (userUid.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val arr = repository.getConversations(userUid)
                _conversations.value = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    ConversationItem(
                        conversationId = c.optString("conversation_id", ""),
                        peerUid = c.optString("peer_uid", ""),
                        peerName = c.optString("peer_name", "").ifBlank { "小红书用户" },
                        peerAvatar = c.optString("peer_avatar", ""),
                        lastMessage = c.optString("last_message", ""),
                        lastTime = c.optLong("last_time", 0L),
                        unreadCount = c.optInt("unread_count", 0),
                        peerRemark = c.optString("peer_remark", "")
                    )
                }
            } catch (e: Exception) {
                _conversations.value = emptyList()
            }
            _loading.value = false
        }
    }
}

class ConversationsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationsViewModel::class.java)) {
            return ConversationsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
