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
import org.json.JSONObject

/** 联系人搜索结果：我关注的人（可能同时是聊天对象） */
data class ContactSearchItem(
    val uid: String,
    val userName: String,      // 原始昵称
    val remark: String,        // 备注（可空）
    val avatarUrl: String
) {
    /** 展示名：有备注用备注，否则用昵称 */
    val displayName: String get() = remark.ifBlank { userName }
}

/** 聊天记录搜索结果 */
data class ChatMessageSearchItem(
    val conversationId: String,
    val messageId: String,
    val peerUid: String,
    val peerName: String,
    val peerRemark: String,
    val peerAvatar: String,
    val content: String,
    val time: Long,
    val isMine: Boolean
) {
    /** 对方展示名：有备注优先备注 */
    val peerDisplayName: String get() = peerRemark.ifBlank { peerName }
}

class MessageSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = SupabaseAuthRepository(application)
    private val realtimeRepository = RealtimeRepository(application)

    private val _followingUsers = MutableStateFlow<List<ContactSearchItem>>(emptyList())
    val followingUsers: StateFlow<List<ContactSearchItem>> = _followingUsers.asStateFlow()

    private val _messageResults = MutableStateFlow<List<ChatMessageSearchItem>>(emptyList())
    val messageResults: StateFlow<List<ChatMessageSearchItem>> = _messageResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** 请求序号：用于丢弃过期搜索结果 */
    private var searchSeq = 0

    /** 加载我关注的用户（联系人搜索数据源），只加载一次 */
    fun loadFollowing(userUid: String) {
        if (userUid.isBlank()) return
        viewModelScope.launch {
            try {
                val arr = authRepository.getFollowingUsers(userUid)
                val uids = (0 until arr.length()).map { arr.getJSONObject(it).optString("uid", "") }
                val remarks = try { authRepository.getRemarks(userUid, uids) } catch (e: Exception) { emptyMap<String, String>() }
                _followingUsers.value = (0 until arr.length()).map { i ->
                    val u = arr.getJSONObject(i)
                    val uid = u.optString("uid", "")
                    ContactSearchItem(
                        uid = uid,
                        userName = u.optString("nickname", "").ifBlank { "小红书用户" },
                        remark = remarks[uid].orEmpty(),
                        avatarUrl = u.optString("avatar_url", "")
                    )
                }
            } catch (e: Exception) {
                _followingUsers.value = emptyList()
            }
        }
    }

    /** 按关键字搜索聊天记录；query 为空时清空结果 */
    fun searchMessages(userUid: String, query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _messageResults.value = emptyList()
            _searching.value = false
            return
        }
        val requestSeq = ++searchSeq
        viewModelScope.launch {
            _searching.value = true
            try {
                val arr = realtimeRepository.searchMyMessages(userUid, q)
                // 只采纳最后一次请求的结果，避免旧响应覆盖新输入
                if (requestSeq != searchSeq) return@launch
                _messageResults.value = (0 until arr.length()).mapNotNull { i ->
                    val m = arr.getJSONObject(i)
                    if (m.optString("peer_uid", "").isBlank()) return@mapNotNull null
                    ChatMessageSearchItem(
                        conversationId = m.optString("conversation_id", ""),
                        messageId = m.optString("message_id", ""),
                        peerUid = m.optString("peer_uid", ""),
                        peerName = m.optString("peer_name", ""),
                        peerRemark = m.optString("peer_remark", ""),
                        peerAvatar = m.optString("peer_avatar", ""),
                        content = m.optString("content", ""),
                        time = m.optLong("created_at", 0L),
                        isMine = m.optString("sender_uid", "") == userUid
                    )
                }
            } catch (e: Exception) {
                if (requestSeq == searchSeq) _messageResults.value = emptyList()
            }
            if (requestSeq == searchSeq) _searching.value = false
        }
    }
}

class MessageSearchViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageSearchViewModel::class.java)) {
            return MessageSearchViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
