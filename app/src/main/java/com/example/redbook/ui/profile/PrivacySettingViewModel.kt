package com.example.redbook.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.model.PrivacySettings
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.notification.PrivacyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 隐私设置 VM：本地 + 云端双存。
 * 用 viewModelScope 保存，页面退出/切走也不会中断云端写入。
 */
class PrivacySettingViewModel(application: Application, private val userUid: String) :
    AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _settings = MutableStateFlow(PrivacyPrefs.load(userUid, getApplication()))
    val settings: StateFlow<PrivacySettings> = _settings.asStateFlow()

    init { loadFromCloud() }

    /** 拉云端，version 较新则覆盖本地 */
    fun loadFromCloud() {
        if (userUid.isBlank()) return
        viewModelScope.launch {
            try {
                val cloud = repository.getPrivacySettings(userUid)
                val local = _settings.value
                if (cloud.version >= local.version) {
                    _settings.value = cloud
                    PrivacyPrefs.save(userUid, cloud, getApplication())
                }
            } catch (e: Exception) {
                android.util.Log.e("RedBookPrivacy", "load cloud failed ${e.message}")
            }
        }
    }

    /** 更新一个开关并同步本地 + 云端 */
    fun update(transform: (PrivacySettings) -> PrivacySettings) {
        if (userUid.isBlank()) return
        val current = _settings.value
        val next = transform(current)
        // 值没变化则不写
        if (next == current) return
        val merged = next.copy(version = maxOf(next.version, current.version) + 1)
        _settings.value = merged
        PrivacyPrefs.save(userUid, merged, getApplication())
        viewModelScope.launch {
            try {
                repository.savePrivacySettings(userUid, merged)
                // 保存后回读云端校验是否真正落库（防止 PATCH 更新 0 行/列不存在的假成功）
                val cloud = repository.getPrivacySettings(userUid)
                val ok = cloud.showPosts == merged.showPosts &&
                    cloud.showComments == merged.showComments &&
                    cloud.showFavorites == merged.showFavorites &&
                    cloud.showLikes == merged.showLikes
                if (!ok) {
                    android.util.Log.e("RedBookPrivacy", "verify mismatch: local=$merged cloud=$cloud")
                }
            } catch (e: Exception) {
                android.util.Log.e("RedBookPrivacy", "save failed ${e.message}")
            }
        }
    }

    fun setShowPosts(v: Boolean) = update { it.copy(showPosts = v) }
    fun setShowComments(v: Boolean) = update { it.copy(showComments = v) }
    fun setShowFavorites(v: Boolean) = update { it.copy(showFavorites = v) }
    fun setShowLikes(v: Boolean) = update { it.copy(showLikes = v) }
}

class PrivacySettingViewModelFactory(
    private val application: Application,
    private val userUid: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrivacySettingViewModel::class.java)) {
            return PrivacySettingViewModel(application, userUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
