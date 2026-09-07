package com.example.redbook.ui.profile

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
import org.json.JSONArray

/** 关注/粉丝列表模式 */
enum class FollowListMode { FOLLOWING, FANS }

/** 列表中的单个用户项 */
data class FollowItem(
    val uid: String,
    val userName: String,
    val avatarUrl: String,
    /** 我(viewer)是否已关注 TA */
    val followedByMe: Boolean,
    /** TA 是否已关注我 */
    val followsMe: Boolean
) {
    /** 是否互相关注 */
    val isMutual: Boolean get() = followedByMe && followsMe
}

class FollowListViewModel(
    application: Application,
    private val profileUid: String,
    private val viewerUid: String,
    private val mode: FollowListMode
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _items = MutableStateFlow<List<FollowItem>>(emptyList())
    val items: StateFlow<List<FollowItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 解除关注确认弹窗针对的用户 */
    private val _confirmUnfollowUid = MutableStateFlow<String?>(null)
    val confirmUnfollowUid: StateFlow<String?> = _confirmUnfollowUid.asStateFlow()

    init { load() }

    /** 加载关注/粉丝列表（含备注名与互相关注判断） */
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val raw = when (mode) {
                    FollowListMode.FOLLOWING -> repository.getFollowingUsers(profileUid)
                    FollowListMode.FANS -> repository.getMyFollowers(profileUid)
                }
                // 备注名全局替换：我方视角 viewerUid 对列表内用户的备注
                val uids = (0 until raw.length()).map { raw.getJSONObject(it).optString("uid", "") }
                    .filter { it.isNotBlank() }
                val remarks = if (viewerUid.isNotBlank()) {
                    try { repository.getRemarks(viewerUid, uids) } catch (e: Exception) { emptyMap<String, String>() }
                } else emptyMap()

                // 互相关注判断：
                //  FOLLOWING：列表是我关注的人，需知道他们是否关注了我
                //  FANS：列表是关注我的人，需知道我是否关注了他们
                val myFollowers = if (mode == FollowListMode.FOLLOWING) {
                    try { repository.getFollowerUids(profileUid) } catch (e: Exception) { emptySet() }
                } else emptySet()
                val iFollow = if (mode == FollowListMode.FANS) {
                    try { repository.getFollowingUids(viewerUid) } catch (e: Exception) { emptySet() }
                } else emptySet()

                _items.value = (0 until raw.length()).mapNotNull { i ->
                    val u = raw.getJSONObject(i)
                    val uid = u.optString("uid", "")
                    if (uid.isBlank()) return@mapNotNull null
                    val fallback = u.optString("nickname", "").ifBlank { "小红书用户" }
                    FollowItem(
                        uid = uid,
                        userName = remarks[uid].orEmpty().ifBlank { fallback },
                        avatarUrl = u.optString("avatar_url", ""),
                        followedByMe = when (mode) {
                            FollowListMode.FOLLOWING -> true // 关注页里的都是我关注的人
                            FollowListMode.FANS -> uid in iFollow
                        },
                        followsMe = when (mode) {
                            FollowListMode.FOLLOWING -> uid in myFollowers
                            FollowListMode.FANS -> true // 粉丝页里的都是关注我的人
                        }
                    )
                }
            } catch (_: Exception) {
                _items.value = emptyList()
            }
            _loading.value = false
        }
    }

    /** 关注/取消关注（同步云端） */
    fun toggleFollow(uid: String, following: Boolean) {
        if (viewerUid.isBlank() || uid.isBlank() || uid == viewerUid) return
        if (mode == FollowListMode.FOLLOWING && !following) {
            // 关注列表：取消关注后该项直接移除（不再出现在“我关注的人”里）
            _items.value = _items.value.filterNot { it.uid == uid }
        } else {
            // 粉丝列表：取消互关后保留该用户，但按钮回到“回关”
            updateLocal(uid) { it.copy(followedByMe = following) }
        }
        viewModelScope.launch {
            try { repository.follow(viewerUid, uid, following) } catch (_: Exception) { }
        }
    }

    /** 弹窗确认后解除关注 */
    fun confirmUnfollow(uid: String, following: Boolean) {
        _confirmUnfollowUid.value = null
        toggleFollow(uid, following)
    }

    fun setConfirmUnfollow(uid: String?) {
        _confirmUnfollowUid.value = uid
    }

    private fun updateLocal(uid: String, transform: (FollowItem) -> FollowItem) {
        _items.value = _items.value.map { if (it.uid == uid) transform(it) else it }
    }
}

class FollowListViewModelFactory(
    private val application: Application,
    private val profileUid: String,
    private val viewerUid: String,
    private val mode: FollowListMode
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FollowListViewModel::class.java)) {
            return FollowListViewModel(application, profileUid, viewerUid, mode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}