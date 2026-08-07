package com.example.redbook.ui.PostDetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R
import com.example.redbook.ui.component.KeyboardInputBar
import com.example.redbook.ui.component.NoteCardBar
import com.example.redbook.ui.component.NoteCardBottomBar
import com.example.redbook.ui.component.CommentInputArea
import com.example.redbook.ui.component.CommentItem
import com.example.redbook.ui.detail.components.PostContent
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    postId: String,
    viewModel: DetailViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isKeyboardVisible by viewModel.isKeyboardVisible.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val replyTarget by viewModel.replyTarget.collectAsState()

    LaunchedEffect(postId) { viewModel.loadPost(postId) }
    // 图片选择器（多选）
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> viewModel.addSelectedImage(uri) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                val post = (uiState as? DetailUiState.Success)?.post
                Spacer(modifier=Modifier.height(42.dp))
                NoteCardBar(
                    backIconRes = R.drawable.arrow_left,
                    onBackIconClick = onBack,
                    avatarRes = post?.authorAvatar ?: R.drawable.test,
                    name = post?.authorName ?: "",
                    onUserClick = { /* 跳转作者主页 */ },
                    isFollowed = post?.isFollowed ?: false,
                    onFollowClick = { viewModel.toggleFollowAuthor() }
                )
            }
        },
        bottomBar = {
                if (!isKeyboardVisible) {
                    val post = (uiState as? DetailUiState.Success)?.post
                    val comments = (uiState as? DetailUiState.Success)?.comments
                    NoteCardBottomBar(
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = 16.dp, start = 10.dp, end = 10.dp),
                        initialLikeCount = post?.likeCount ?: 0,
                        initialIsLiked = post?.isLiked ?: false,
                        initialFavoriteCount = post?.favoriteCount ?: 0,
                        initialIsFavorited = post?.isFavorited ?: false,
                        initialCommentCount = comments?.size ?: 0,
                        onCommentInputClick = {
                            viewModel.setKeyboardVisible(true)
                            focusRequester.requestFocus()
                        },
                        onLikeClick = { viewModel.togglePostLike() },
                        onFavoriteClick = { viewModel.togglePostFavorite() },
                        onCommentIconClick = {
                            coroutineScope.launch {
                                // 0: PostContent, 1: CommentInputArea, 所以评论从索引 2 开始
                                lazyListState.animateScrollToItem(1)
                            }
                        }
                    )
                }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is DetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is DetailUiState.Success -> {
                    val post = (uiState as DetailUiState.Success).post
                    val comments = (uiState as DetailUiState.Success).comments

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                       // contentPadding = PaddingValues(10.dp)
                    ) {

                        item {
                            PostContent(
                                post = post,
                                onAvatarClick = {  },
                              //  modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            CommentInputArea(
                                avatarRes = R.drawable.test,
                                onInputFocus = {
                                    viewModel.setKeyboardVisible(true)
                                    focusRequester.requestFocus()
                                },
                                onImageClick = {
                                    imagePickerLauncher.launch("image/*")
                                },
                            )
                        }

                        items(comments, key = { it.id }) { comment ->
                            CommentItem(
                                comment = comment,

                                onAvatarClick = { /* 跳转用户主页 */ },
                                onUserNameClick = { /* 跳转用户主页 */ },
                                onReplyClick = {  commentId,userName ->
                                    viewModel.setReplyTarget(
                                        DetailViewModel.ReplyTarget(
                                            commentId,
                                            userName
                                        )
                                    )
                                    viewModel.updateCommentText("回复 ${userName}：")
                                    viewModel.setKeyboardVisible(true)
                                    focusRequester.requestFocus()
                                },
                                onLikeClick = { commentId ->
                                    viewModel.toggleCommentLike(commentId)
                                }
                            )
                        }
                    }
                }

                is DetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = (uiState as DetailUiState.Error).message)
                    }
                }
            }


            if (isKeyboardVisible) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            // 点击遮罩关闭键盘
                            viewModel.setKeyboardVisible(false)
                            focusRequester.freeFocus()
                        }
                ) {
                    // 输入栏放在底部，跟随键盘
                    KeyboardInputBar(
                        text = commentText,
                        onTextChange = { viewModel.updateCommentText(it) },
                        selectedImages = selectedImages,
                        onAddImageClick = { imagePickerLauncher.launch("image/*") },
                        onRemoveImage = { uri -> viewModel.removeSelectedImage(uri) },
                        onSend = { content, images ->
                            if (replyTarget != null) {//评论
                                viewModel.addReply(replyTarget!!.commentId, content, images)
                                viewModel.setReplyTarget(null)
                            } else {
                                // 是普通评论
                                viewModel.addComment(content, images)
                            }
                            viewModel.setKeyboardVisible(false)
                        },
                        onClose = {
                            viewModel.setKeyboardVisible(false)
                            focusRequester.freeFocus()
                        },
                        focusRequester = focusRequester,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding()              // 键盘顶起
                            .navigationBarsPadding()   // 避开底部手势栏
                            .clickable(enabled = false) { } // 阻止点击穿透到遮罩
                    )
                }
            }
        }
    }

}