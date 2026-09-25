package com.example.redbook.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.model.Note
import com.example.redbook.data.repository.HomeFeedCache
import com.example.redbook.data.repository.HomeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application, private val userUid: String = "") : AndroidViewModel(application) {

    private val repository = HomeRepository(SupabaseAuthRepository(application))

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    /**
     * 只允许最后一次请求写状态。
     *
     * 加载统一由首页的 LaunchedEffect(userUid) 触发，这里不再 init 一次：
     * 两个并发请求里慢到的那次失败会把已经拿到的结果覆盖成"网络异常"，
     * 表现出来就是错误提示闪一下又被内容顶掉。
     */
    private var fetchSeq = 0

    fun fetchNotes() {
        // 冷启动预热数据只消费一次：命中就直接展示，省掉那条容易失败的冷连接首请求
        HomeFeedCache.take(userUid)?.let { cached ->
            _uiState.value = HomeUiState.Success(cached)
            return
        }
        load { repository.getNotes(userUid) }
    }

    /** 关注页：只加载我关注的人发布的帖子 */
    fun fetchFollowingNotes() = load { repository.getFollowingNotes(userUid) }

    private fun load(fetch: suspend () -> List<Note>) {
        val seq = ++fetchSeq
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val notes = fetch()
                if (seq == fetchSeq) _uiState.value = HomeUiState.Success(notes)
            } catch (e: Exception) {
                if (seq == fetchSeq) _uiState.value = HomeUiState.Error(errorMessage(e))
            }
        }
    }

    /** 网络层失败(拿不到响应体、错误码记为 0)给一句人话，不要把原始异常甩给用户 */
    private fun errorMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return if (raw.isBlank() || raw.startsWith("0:")) "网络异常，请重试" else raw
    }

    fun toggleLike(noteId: String) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            val note = currentState.notes.firstOrNull { it.id == noteId } ?: return
            val newLiked = !note.isLiked
            val newCount = note.likeCount + (if (newLiked) 1 else -1)
            _uiState.value = HomeUiState.Success(currentState.notes.map {
                if (it.id == noteId) it.copy(isLiked = newLiked, likeCount = newCount) else it
            })
            if (userUid.isNotBlank()) {
                viewModelScope.launch {
                    try {
                        repository.supabase.recordLike(userUid, noteId, newLiked)
                        repository.supabase.updatePostLike(noteId)
                    } catch (_: Exception) { }
                }
            }
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val notes: List<Note>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
