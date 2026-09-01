package com.example.redbook.ui.PostDetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R
import com.example.redbook.ui.component.KeyboardInputBar
import com.example.redbook.ui.component.NoteCardBar
import com.example.redbook.ui.component.NoteCardBottomBar
import com.example.redbook.ui.component.CommentInputArea
import com.example.redbook.ui.component.CommentItem
import com.example.redbook.ui.component.PostContent
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOutline
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    postId: String,
    userUid: String = "",
    userXhsId: String = "",
    userName: String = "",
    userAvatarUrl: String = "",
    scrollToCommentId: String = "",
    onCommentScrolled: () -> Unit = {},
    onBack: () -> Unit = {},
    onSendMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onUserClick: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(context.applicationContext as android.app.Application, userUid, userXhsId, userName, userAvatarUrl))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isKeyboardVisible by viewModel.isKeyboardVisible.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val replyTarget by viewModel.replyTarget.collectAsState()
    var deletingComment by remember { mutableStateOf<String?>(null) }
    var highlightCommentId by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 监听真实键盘状态：键盘收起时同步收起输入栏
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) viewModel.setKeyboardVisible(false)
    }

    LaunchedEffect(postId) { viewModel.loadPost(postId) }

    // 回到页面时重新同步关注状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, postId) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshFollowState()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(uiState, scrollToCommentId) {
        if (scrollToCommentId.isNotBlank() && uiState is DetailUiState.Success) {
            val comments = (uiState as DetailUiState.Success).comments
            val idx = comments.indexOfFirst { it.id == scrollToCommentId }
            if (idx >= 0) {
                highlightCommentId = scrollToCommentId
                lazyListState.animateScrollToItem(3 + idx)
                // 高亮 1.5s 后清除
                kotlinx.coroutines.delay(1500)
                highlightCommentId = ""
            }
            onCommentScrolled()
        }
    }
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
                    avatarUrl = post?.authorAvatarUrl ?: "",
                    name = post?.authorName ?: "",
                    onUserClick = {
                        post?.let { onUserClick(it.authorId) }
                    },
                    isFollowed = post?.isFollowed ?: false,
                    onFollowClick = { viewModel.toggleFollowAuthor() },
                    showFollow = post?.authorId != userUid,
                    showMessage = false,
                    onMessageClick = {
                        post?.let {
                            onSendMessage(it.authorId, it.authorName, it.authorAvatarUrl)
                        }
                    }
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
                        likeEnabled = true,
                        favoriteEnabled = true,
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
                            .padding(paddingValues)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    keyboardController?.hide()
                                    viewModel.setKeyboardVisible(false)
                                    focusRequester.freeFocus()
                                }
                            },
                       // contentPadding = PaddingValues(10.dp)
                    ) {

                        item {
                            PostContent(
                                post = post,
                                onAvatarClick = {
                                    onUserClick(post.authorId)
                                },
                              //  modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            HorizontalDivider(
                                color = getOutline().copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "共${comments.size}条评论",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.sort),
                                    contentDescription = "排序",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            CommentInputArea(
                                avatarRes = R.drawable.test,
                                avatarUrl = userAvatarUrl,
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

                                onAvatarClick = { uid -> onUserClick(uid) },
                                onUserNameClick = { uid -> onUserClick(uid) },
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
                                },
                                onLongClick = { commentId ->
                                    deletingComment = commentId
                                },
                                highlight = comment.id == highlightCommentId
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
                            .clickable(enabled = false) { }
                    )
            }

            deletingComment?.let { commentId ->
                Dialog(onDismissRequest = { deletingComment = null }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("删除评论", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text("确定删除该评论吗？", fontSize = 14.sp, color = getOnSurfaceSecondary())
                        }
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))
                        Row(
                            Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { deletingComment = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("取消", color = getOnSurfaceSecondary())
                            }
                            Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline()))
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable {
                                    viewModel.deleteComment(commentId)
                                    deletingComment = null
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("确认", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

}