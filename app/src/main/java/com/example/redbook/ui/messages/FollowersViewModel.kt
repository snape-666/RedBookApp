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

data class FollowerItem(
    val uid: String,
    val userName: String,
    val avatarUrl: String,
    val followTime: Long,
    var followed: Boolean
)

class FollowersViewModel(application: Application, private val userUid: String) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _followers = MutableStateFlow<List<FollowerItem>>(emptyList())
    val followers: StateFlow<List<FollowerItem>> = _followers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val followersArr = repository.getMyFollowers(userUid)
                val followingUids = try { repository.getFollowingUids(userUid) } catch (e: Exception) { emptySet() }
                val uids = (0 until followersArr.length()).map { followersArr.getJSONObject(it).optString("uid", "") }
                // 批量读取我对这些人的备注名（remarks: viewer=我,target=对方）
                val remarks = try { repository.getRemarks(userUid, uids) } catch (e: Exception) { emptyMap<String, String>() }
                _followers.value = (0 until followersArr.length()).map { i ->
                    val u = followersArr.getJSONObject(i)
                    val uid = u.optString("uid", "")
                    FollowerItem(
                        uid = uid,
                        userName = remarks[uid].orEmpty().ifBlank { u.optString("nickname", "").ifBlank { "小红书用户" } },
                        avatarUrl = u.optString("avatar_url", ""),
                        followTime = u.optLong("created_at", 0L),
                        followed = followingUids.contains(uid)
                    )
                }
            } catch (e: Exception) {
                _followers.value = emptyList()
            }
            _loading.value = false
        }
    }

    fun toggleFollow(uid: String, followed: Boolean) {
        _followers.value = _followers.value.map {
            if (it.uid == uid) it.copy(followed = followed) else it
        }
        viewModelScope.launch {
            try { repository.follow(userUid, uid, followed) } catch (_: Exception) { }
        }
    }
}

class FollowersViewModelFactory(private val application: Application, private val uid: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FollowersViewModel::class.java)) {
            return FollowersViewModel(application, uid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
