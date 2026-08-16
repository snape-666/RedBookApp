package com.example.redbook.ui.PostDetail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.R
import com.example.redbook.data.model.Comment
import com.example.redbook.data.model.PostDetail
import com.example.redbook.data.model.Reply
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailViewModel(
    application: Application,
    private val userUid: String,
    private val userXhsId: String,
    private val userName: String
) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()


    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()

    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

    private val _replyTarget = MutableStateFlow<ReplyTarget?>(null)
    val replyTarget: StateFlow<ReplyTarget?> = _replyTarget.asStateFlow()

    data class ReplyTarget(
        val commentId: String,
        val userName: String
    )

    init { }

    fun setReplyTarget(target: ReplyTarget?) {
        _replyTarget.value = target
    }

    fun loadPost(postId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val p = repository.getPost(postId)
                if (p != null) {
                    val post = PostDetail(
                        postId = p.optString("post_id", ""),
                        imageRes = R.drawable.test,
                        imageUrl = p.optString("image_url", ""),
                        title = p.optString("title", ""),
                        content = p.optString("content", ""),
                        publishTime = p.optLong("created_at", 0),
                        ipLocation = p.optString("ip_location", "未知"),
                        viewCount = p.optInt("view_count", 0) + 1,
                        likeCount = p.optInt("like_count", 0),
                        favoriteCount = p.optInt("favorite_count", 0),
                        commentCount = p.optInt("comment_count", 0),
                        isLiked = false, isFavorited = false, isFollowed = false,
                        authorId = p.optString("author_uid", ""),
                        authorName = p.optString("author_name", ""),
                        authorAvatar = R.drawable.test
                    )
                    val cloudComments = repository.getComments(post.postId)
                    val comments = (0 until cloudComments.length()).map { i ->
                        val c = cloudComments.getJSONObject(i)
                        Comment(
                            id = c.optString("comment_id", ""),
                            userId = c.optString("author_uid", ""),
                            userName = c.optString("author_name", ""),
                            avatarRes = R.drawable.test,
                            content = c.optString("content", ""),
                            timestamp = c.optLong("created_at", 0),
                            ipLocation = c.optString("ip_location", "未知"),
                            likeCount = c.optInt("like_count", 0),
                            isLiked = false,
                            isAuthor = c.optString("author_uid") == post.authorId,
                            replies = emptyList()
                        )
                    }
                    val isLiked = repository.hasLiked(userUid, post.postId)
                    val isFavorited = repository.hasFavorited(userUid, post.postId)
                    val finalPost = post.copy(isLiked = isLiked, isFavorited = isFavorited)
                    _uiState.value = DetailUiState.Success(finalPost, comments)
                    repository.incrementViewCount(postId)
                    return@launch
                }
            } catch (_: Exception) { }

            val mockPost = PostDetail(
                postId = "1",
                imageRes = R.drawable.test,
                title = "这是帖子标题",
                content = "这是帖子正文内容，可以很长很长，展示完整的文字描述。",
                publishTime = System.currentTimeMillis() - 3600000,
                ipLocation = "广东",
                likeCount = 128,
                favoriteCount = 56,
                commentCount = 23,
                isLiked = false,
                isFavorited = false,
                isFollowed = false,
                authorId = "user_123",
                authorName = "小红薯",
                authorAvatar = R.drawable.test
            )
            val mockComments = List(5) { index ->
                val userId = if (index == 0) "user_123" else "user_$index"
                Comment(
                    id = "c$index",
                    userId =userId,
                    userName = if (index == 0) "小红薯" else "用户$index",
                    avatarRes = R.drawable.test,
                    content = "这是第 ${index + 1} 条评论内容",
                    timestamp = System.currentTimeMillis() - (index + 1) * 60000L,
                    ipLocation = "广东",
                    likeCount = (0..10).random(),
                    isLiked = false,
                    isAuthor = userId == mockPost.authorId,
                    replies = if (index == 0) {
                        listOf(
                            Reply(
                                id = "r1",
                                userId = "user_99",
                                userName = "回复者A",
                                avatarRes = R.drawable.test,
                                content = "这是回复内容",
                                timestamp = System.currentTimeMillis() - 30000,
                                ipLocation = "广东",
                                likeCount = 3,
                                isLiked = false,
                                isAuthor = "user_99" == mockPost.authorId
                            ),
                            Reply(
                                id = "r2",
                                userId = "user_123",
                                userName = "小红薯",
                                avatarRes = R.drawable.test,
                                content = "这是作者本人的回复",
                                timestamp = System.currentTimeMillis() - 20000,
                                ipLocation = "广东",
                                likeCount = 5,
                                isLiked = false,
                                isAuthor = "user_123" == mockPost.authorId,
                            ),
                            Reply(
                                id = "r3",
                                userId = "user_88",
                                userName = "回复者B",
                                avatarRes = R.drawable.test,
                                content = "这是第二条回复内容，可以很长一点来测试显示效果",
                                timestamp = System.currentTimeMillis() - 10000,
                                ipLocation = "广东",
                                likeCount = 1,
                                isLiked = false,
                                isAuthor = "user_88" == mockPost.authorId
                            )
                        )
                    } else emptyList()
                )
            }
            _uiState.value = DetailUiState.Success(mockPost, mockComments)
        }
    }

    fun togglePostLike() {
        val currentState = _uiState.value
        if (currentState is DetailUiState.Success) {
            val post = currentState.post
            val newLiked = !post.isLiked
            val newCount = (if (newLiked) post.likeCount + 1 else post.likeCount - 1).coerceAtLeast(0)
            _uiState.value = currentState.copy(post = post.copy(isLiked = newLiked, likeCount = newCount))
            viewModelScope.launch {
                try {
                    repository.recordLike(userUid, post.postId, newLiked)
                    repository.updatePostLike(post.postId, if (newLiked) 1 else -1)
                } catch (_: Exception) { }
            }
        }
    }

    fun togglePostFavorite() {
        val currentState = _uiState.value
        if (currentState is DetailUiState.Success) {
            val post = currentState.post
            val newFavorited = !post.isFavorited
            val newCount = (if (newFavorited) post.favoriteCount + 1 else post.favoriteCount - 1).coerceAtLeast(0)
            _uiState.value = currentState.copy(post = post.copy(isFavorited = newFavorited, favoriteCount = newCount))
            viewModelScope.launch {
                try {
                    repository.recordFavorite(userUid, post.postId, newFavorited)
                    repository.updatePostFav(post.postId, if (newFavorited) 1 else -1)
                } catch (_: Exception) { }
            }
        }
    }

    fun toggleFollowAuthor() {
        updatePost { post ->
            post.copy(isFollowed = !post.isFollowed)
        }
    }


    fun toggleCommentLike(commentId: String) {
        val currentState = _uiState.value
        if (currentState is DetailUiState.Success) {
            val updatedComments = currentState.comments.map { comment ->
                // 检查一级评论
                if (comment.id == commentId) {
                    val newLiked = !comment.isLiked
                    val newCount = if (newLiked) comment.likeCount + 1 else comment.likeCount - 1
                    comment.copy(isLiked = newLiked, likeCount = newCount)
                } else {
                    // 检查回复
                    val updatedReplies = comment.replies.map { reply ->
                        if (reply.id == commentId) {
                            val newLiked = !reply.isLiked
                            val newCount = if (newLiked) reply.likeCount + 1 else reply.likeCount - 1
                            reply.copy(isLiked = newLiked, likeCount = newCount)
                        } else reply
                    }
                    comment.copy(replies = updatedReplies)
                }
            }
            _uiState.value = currentState.copy(comments = updatedComments)
        }
    }

    fun addComment(content: String, images: List<Uri>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DetailUiState.Success) {
                val post = currentState.post
                val name = userName.ifBlank { "我" }
                val isAuthor = userUid == post.authorId
                val newComment = Comment(
                    id = "new_${System.currentTimeMillis()}",
                    userId = userUid,
                    userName = name,
                    avatarRes = R.drawable.test,
                    images = images,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    ipLocation = "未知",
                    likeCount = 0,
                    isLiked = false,
                    isAuthor = isAuthor,
                    replies = emptyList()
                )
                // 添加到列表顶部
                val updatedComments = listOf(newComment) + currentState.comments
                val updatedPost = currentState.post.copy(commentCount = updatedComments.size)
                _uiState.value = currentState.copy(comments = updatedComments, post = updatedPost)

                repository.insertComment(newComment.id, post.postId, content, userUid, name, "", userXhsId, post.title)

                _commentText.value = ""
                clearSelectedImages()
            }
        }
    }


    fun addReply(parentCommentId: String, content: String,images: List<Uri> = emptyList()) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DetailUiState.Success) {
                val post = currentState.post
                val name = userName.ifBlank { "我" }
                val isAuthor = userUid == post.authorId
                val newReply = Reply(
                    id = "reply_${System.currentTimeMillis()}",
                    userId = userUid,
                    userName = name,
                    avatarRes = R.drawable.test,
                    images = images,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    ipLocation = "未知",
                    likeCount = 0,
                    isLiked = false,
                    isAuthor = isAuthor
                )
                val updatedComments = currentState.comments.map { comment ->
                    if (comment.id == parentCommentId) {
                        comment.copy(replies = comment.replies + newReply)
                    } else comment
                }
                _uiState.value = currentState.copy(comments = updatedComments)

                repository.insertReply(newReply.id, post.postId, parentCommentId, content, userUid, name, "", userXhsId, post.title)
            }
        }
    }


    fun updateCommentText(text: String) {
        _commentText.value = text
    }

    fun setKeyboardVisible(visible: Boolean) {
        _isKeyboardVisible.value = visible
    }

    fun addSelectedImage(uri: Uri) {
        _selectedImages.update { currentList ->
            // 去重，防止重复添加
            if (currentList.any { it.toString() == uri.toString() }) {
                currentList
            } else {
                currentList + uri
            }
        }
    }


    fun removeSelectedImage(uri: Uri) {
        _selectedImages.update { currentList ->
            currentList.filter { it.toString() != uri.toString() }
        }
    }
    fun clearSelectedImages() {
        _selectedImages.value = emptyList()
    }


    private fun updatePost(transform: (PostDetail) -> PostDetail) {
        val currentState = _uiState.value
        if (currentState is DetailUiState.Success) {
            val updatedPost = transform(currentState.post)
            _uiState.value = currentState.copy(post = updatedPost)
        }
    }
}