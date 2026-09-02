package com.example.redbook.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 资料卡数据（全部来自云端 users 表） */
data class UserCardData(
    val uid: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val xhsId: String = "",
    val gender: String = "",
    val birthday: String = "",
    val ipLocation: String = ""
)

class UserCardViewModel(application: Application) : ViewModel() {
    private val repository = SupabaseAuthRepository(application)

    private val _data = MutableStateFlow(UserCardData())
    val data: StateFlow<UserCardData> = _data.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 从云端拉取指定 uid 的最新资料 */
    fun load(targetUid: String) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val u = repository.getUserByUid(targetUid)
                if (u != null) {
                    val ip = u.optString("ip_location", "")
                    _data.value = UserCardData(
                        uid = targetUid,
                        userName = u.optString("nickname", "").ifBlank { "小红书用户" },
                        avatarUrl = u.optString("avatar_url", ""),
                        xhsId = u.optString("xhs_id", ""),
                        gender = u.optString("gender", ""),
                        birthday = u.optString("birthday", ""),
                        ipLocation = if (ip.isBlank() || ip == "未知") "" else ip
                    )
                }
            } catch (_: Exception) { }
            _loading.value = false
        }
    }
}

class UserCardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserCardViewModel::class.java)) {
            return UserCardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
