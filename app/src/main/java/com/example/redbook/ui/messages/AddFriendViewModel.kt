package com.example.redbook.ui.messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 添加好友搜索结果项 */
data class FriendSearchItem(
    val uid: String,
    val userName: String,      // 昵称
    val xhsId: String,         // 小红书号
    val avatarUrl: String,
    var followed: Boolean,     // 我是否已关注
    val remark: String = ""    // 我对该用户的备注（有备注优先展示）
) {
    /** 展示名：备注优先 */
    val displayName: String get() = remark.ifBlank { userName }
}

class AddFriendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _results = MutableStateFlow<List<FriendSearchItem>>(emptyList())
    val results: StateFlow<List<FriendSearchItem>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** 请求序号：丢弃过期搜索结果 */
    private var searchSeq = 0

    fun search(userUid: String, query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        val seq = ++searchSeq
        viewModelScope.launch {
            _searching.value = true
            try {
                val arr = repository.searchUsers(q, excludeUid = userUid)
                if (seq != searchSeq) return@launch
                val uids = (0 until arr.length()).map { arr.getJSONObject(it).optString("uid", "") }
                val followedMap = try { repository.isFollowingBatch(userUid, uids) } catch (e: Exception) { emptyMap<String, Boolean>() }
                val remarks = try { repository.getRemarks(userUid, uids) } catch (e: Exception) { emptyMap<String, String>() }
                if (seq != searchSeq) return@launch
                _results.value = (0 until arr.length()).map { i ->
                    val u = arr.getJSONObject(i)
                    val uid = u.optString("uid", "")
                    FriendSearchItem(
                        uid = uid,
                        userName = u.optString("nickname", "").ifBlank { "小红书用户" },
                        xhsId = u.optString("xhs_id", "").ifBlank { "00000000000" },
                        avatarUrl = u.optString("avatar_url", ""),
                        followed = followedMap[uid] ?: false,
                        remark = remarks[uid].orEmpty()
                    )
                }
            } catch (e: Exception) {
                if (seq == searchSeq) _results.value = emptyList()
            }
            if (seq == searchSeq) _searching.value = false
        }
    }

    /** 关注/取关：乐观更新 UI，并写入云端（全局各页在重新加载时会读到最新状态） */
    fun toggleFollow(userUid: String, targetUid: String, followed: Boolean) {
        _results.value = _results.value.map {
            if (it.uid == targetUid) it.copy(followed = followed) else it
        }
        viewModelScope.launch {
            try {
                repository.follow(userUid, targetUid, followed)
            } catch (_: Exception) {
                // 失败回滚
                _results.value = _results.value.map {
                    if (it.uid == targetUid) it.copy(followed = !followed) else it
                }
            }
        }
    }
}

class AddFriendViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddFriendViewModel::class.java)) {
            return AddFriendViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
