package com.example.redbook.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.R
import com.example.redbook.data.model.Note
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application, private val userUid: String = "") : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)
    private val prefs = application.getSharedPreferences("search_history", 0)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init { loadHistory(); loadSuggestions() }

    fun updateQuery(text: String) {
        _uiState.value = _uiState.value.copy(query = text, isSearching = false)
    }

    fun search(query: String) {
        val q = query.ifBlank { _uiState.value.query }.ifBlank { return }
        saveHistory(q)
        _uiState.value = _uiState.value.copy(query = q, isSearching = true, searchResults = emptyList(), historyList = getHistory())
        viewModelScope.launch {
            try {
                val posts = repository.getPosts()
                var results = (0 until posts.length()).map { i ->
                    val p = posts.getJSONObject(i)
                    Note(
                        id = p.optString("post_id", ""),
                        title = p.optString("title", ""),
                        imageRes = R.drawable.test,
                        avatarRes = R.drawable.test,
                        avatarUrl = p.optString("author_avatar", ""),
                        imageUrl = p.optString("image_url", ""),
                        userName = p.optString("author_name", ""),
                        likeCount = p.optInt("like_count", 0),
                        authorUid = p.optString("author_uid", "")
                    )
                }.filter { it.title.contains(q, ignoreCase = true) }
                // 备注名替换：作者名替换为"我"对该作者的备注
                if (userUid.isNotBlank() && results.isNotEmpty()) {
                    try {
                        val uids = results.map { it.authorUid }.filter { it.isNotBlank() }.distinct()
                        if (uids.isNotEmpty()) {
                            val remarks = repository.getRemarks(userUid, uids)
                            if (remarks.isNotEmpty()) {
                                results = results.map { note ->
                                    val remark = remarks[note.authorUid]
                                    if (!remark.isNullOrBlank()) note.copy(userName = remark) else note
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                _uiState.value = _uiState.value.copy(searchResults = results)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(searchResults = emptyList())
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            query = "", isSearching = false, searchResults = emptyList(),
            historyList = getHistory()
        )
    }

    fun getHistory(): List<String> {
        return prefs.getStringSet("history", emptySet())?.toList()?.distinct() ?: emptyList()
    }

    fun loadHistory() {
        _uiState.value = _uiState.value.copy(historyList = getHistory())
    }

    private fun saveHistory(query: String) {
        val set = getHistory().toMutableSet()
        set.add(query)
        prefs.edit().putStringSet("history", set).apply()
    }

    fun deleteHistory(item: String) {
        val set = getHistory().toMutableSet()
        set.remove(item)
        prefs.edit().putStringSet("history", set).apply()
        _uiState.value = _uiState.value.copy(historyList = getHistory())
    }

    fun clearAllHistory() {
        prefs.edit().remove("history").apply()
        _uiState.value = _uiState.value.copy(historyList = emptyList())
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            try {
                val posts = repository.getPostsByViews()
                val titles = (0 until posts.length()).map { i ->
                    extractKeyContent(posts.getJSONObject(i).optString("title", ""))
                }.filter { it.isNotBlank() }
                _uiState.value = _uiState.value.copy(suggestions = titles)
            } catch (_: Exception) { }
        }
    }

    private fun extractKeyContent(title: String): String {
        val clean = title
            .replace(Regex("这是第\\d+篇"), "")
            .replace(Regex("的笔记|的帖子|风格"), "")
            .trim()
        return if (clean.length <= 15) clean else clean.take(15) + "…"
    }

    data class SearchUiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val searchResults: List<Note> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val historyList: List<String> = emptyList()
    )
}
