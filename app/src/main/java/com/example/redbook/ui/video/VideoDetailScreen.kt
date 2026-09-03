package com.example.redbook.ui.video

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.model.Comment
import com.example.redbook.data.model.Reply
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.component.AuthorBottomSheetOverlay
import com.example.redbook.ui.component.AuthorPanel
import com.example.redbook.ui.component.CommentItem
import com.example.redbook.ui.component.EditPostActionPanel
import com.example.redbook.ui.component.KeyboardInputBar
import com.example.redbook.ui.component.PostEditAreaContent
import com.example.redbook.ui.component.PostPermissionPanel
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOutline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

private val videoComments = mutableMapOf<String, MutableList<Comment>>()

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDetailScreen(
    videoUrl: String, title: String, authorName: String, authorAvatar: Int,
    isFollowed: Boolean, likeCount: Int, favoriteCount: Int, commentCount: Int,
    videoId: String = "",
    userUid: String = "",
    userXhsId: String = "",
    userAvatarUrl: String = "",
    authorAvatarUrl: String = "",
    onBack: () -> Unit, onFollowClick: (Boolean) -> Unit,
    onLikeClick: (Int) -> Unit, onFavoriteClick: (Int) -> Unit,
    onSendMessage: (String, String, String) -> Unit = { _, _, _ -> },
    onUserClick: (String) -> Unit = {},
    scrollToCommentId: String = "",
    // 作者模式（从我的笔记进入自己视频时编辑底栏）
    editMode: Boolean = false,
    refreshKey: Int = 0,
    onEditPost: (com.example.redbook.data.model.PostToEdit) -> Unit = {},
    onVideoDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    var curMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    var paused by remember { mutableStateOf(false) }
    var followed by remember { mutableStateOf(isFollowed) }
    var authorUid by remember { mutableStateOf("") }
    var realAuthorName by remember { mutableStateOf(authorName) }
    var realAuthorAvatar by remember { mutableStateOf(authorAvatarUrl) }
    var realTitle by remember { mutableStateOf(title) }
    var visibility by remember { mutableStateOf("public") }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var likeCnt by remember { mutableIntStateOf(likeCount) }
    var favCnt by remember { mutableIntStateOf(favoriteCount) }
    var content by remember { mutableStateOf("") }
    var publishTime by remember { mutableStateOf(0L) }
    var ipLocation by remember { mutableStateOf("未知") }
    var viewCount by remember { mutableIntStateOf(0) }
    var showCmt by remember { mutableStateOf(false) }
    var cmtText by remember { mutableStateOf("") }
    var kbVisible by remember { mutableStateOf(false) }
    var selUris = remember { mutableStateListOf<Uri>() }
    val focusReq = remember { FocusRequester() }
    val cmts = remember { videoComments.getOrPut(videoUrl) { mutableStateListOf() } }
    var replyTgt by remember { mutableStateOf<Pair<String, String>?>(null) }
    val cmtListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var highlightCommentId by remember { mutableStateOf("") }

    // 作者模式：底部面板状态 + 删除确认
    var authorPanel by remember(videoId) { mutableStateOf(AuthorPanel.None) }
    var confirmDeletePost by remember(videoId) { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { u ->
        u.forEach { if (it !in selUris) selUris.add(it) }
    }

    // 监听真实键盘状态：键盘收起时同步收起评论输入栏
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) kbVisible = false
    }

    LaunchedEffect(vv) { while (true) { delay(50); vv?.let { curMs = it.currentPosition } } }
    LaunchedEffect(videoId, userUid, refreshKey) {
        if (videoId.isNotBlank() && userUid.isNotBlank()) {
            try {
                liked = repository.hasLiked(userUid, videoId)
                faved = repository.hasFavorited(userUid, videoId)
                // 视频统一存 posts 表（image_url 带 video: 前缀）
                val p = repository.getPost(videoId)
                if (p != null) {
                    likeCnt = p.optInt("like_count", 0)
                    favCnt = p.optInt("favorite_count", 0)
                    authorUid = p.optString("author_uid", "")
                    realAuthorName = p.optString("author_name", "").ifBlank { realAuthorName }
                    realAuthorAvatar = p.optString("author_avatar", "").ifBlank { realAuthorAvatar }
                    realTitle = p.optString("title", "").ifBlank { realTitle }
                    visibility = p.optString("visibility", "public")
                    content = p.optString("content", "")
                    publishTime = p.optLong("created_at", 0)
                    ipLocation = p.optString("ip_location", "未知")
                    viewCount = p.optInt("view_count", 0)
                    if (authorUid.isNotBlank()) {
                        followed = repository.isFollowing(userUid, authorUid)
                        // 备注名全局替换：作者名替换为"我"对该作者的备注
                        try {
                            val remark = repository.getRemark(userUid, authorUid)
                            if (remark.isNotBlank()) realAuthorName = remark
                        } catch (_: Exception) { }
                    }
                }
                // 加载云端评论（评论存 comments 表，post_id = videoId）
                loadVideoComments(repository, videoId, authorUid, userUid, cmts)
                // 从通知跳转时定位评论，高亮 0.5s
                if (scrollToCommentId.isNotBlank()) {
                    val idx = cmts.indexOfFirst { it.id == scrollToCommentId }
                        .takeIf { it >= 0 }
                        ?: cmts.indexOfFirst { parent -> parent.replies.any { it.id == scrollToCommentId } }
                    if (idx >= 0) {
                        showCmt = true
                        highlightCommentId = cmts[idx].id
                        cmtListState.scrollToItem(idx)
                        kotlinx.coroutines.delay(500)
                        highlightCommentId = ""
                    }
                }
            } catch (_: Exception) { }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, videoId, userUid) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                vv?.start()
                // 回到页面时重新同步关注状态（可能在其他页面已关注/取关）
                if (videoId.isNotBlank() && userUid.isNotBlank()) {
                    scope.launch {
                        try {
                            val uid = repository.getPost(videoId)?.optString("author_uid", "") ?: ""
                            if (uid.isNotBlank()) {
                                authorUid = uid
                                followed = repository.isFollowing(userUid, uid)
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val prog = if (durMs > 0) curMs.coerceAtMost(durMs).toFloat() / durMs else 0f
    val left = ((durMs - curMs.coerceAtMost(durMs)) / 1000).coerceAtLeast(0)

    // 主容器：父容器全屏背景 onSurface
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface)) {

        // 视频区域（进度条上方）+ 底部 bar
        Column(Modifier.fillMaxSize()) {
            // 视频区域：视频在最底层，撑满；横屏宽撑满高自适应居中，竖屏填满
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        paused = !paused
                        if (paused) vv?.pause() else vv?.start()
                    },
                Alignment.Center
            ) {
                AndroidView(factory = { ctx -> VideoView(ctx).also { vv = it }.apply {
                    try { if (videoUrl.startsWith("/")) setVideoPath(videoUrl) else setVideoURI(Uri.parse(videoUrl)) }
                    catch (_: Exception) { setVideoURI(Uri.parse(videoUrl)) }
                    setOnPreparedListener { mp -> durMs = mp.duration; mp.isLooping = false }
                    setOnCompletionListener { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start() }, 1000) }
                    setOnErrorListener { _, _, _ -> false }; start()
                }}, Modifier.fillMaxWidth().wrapContentHeight())
                if (durMs == 0 && !paused) {
                    CircularProgressIndicator(color = Color.White)
                }
                // 暂停图标
                if (paused) {
                    Icon(
                        painter = painterResource(R.drawable.video_fill),
                        contentDescription = "暂停",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }
                // 底部信息（叠在视频上，背景透明，不影响视频展示）
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 作者
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (realAuthorAvatar.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(realAuthorAvatar).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { if (authorUid.isNotBlank()) onUserClick(authorUid) },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(painterResource(authorAvatar), null, Modifier.size(32.dp).clip(CircleShape).clickable { if (authorUid.isNotBlank()) onUserClick(authorUid) }, contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(realAuthorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { if (authorUid.isNotBlank()) onUserClick(authorUid) })
                        Spacer(Modifier.width(5.dp))
                        if (authorUid != userUid) {
                            if (followed) {
                                Box(Modifier.border(1.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp).clickable {
                                    followed = false
                                    onFollowClick(false)
                                    if (authorUid.isNotBlank()) scope.launch { try { repository.follow(userUid, authorUid, false) } catch (_: Exception) { } }
                                }) {
                                    Text("已关注", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary) }
                            } else {
                                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary).clickable {
                                    followed = true
                                    onFollowClick(true)
                                    if (authorUid.isNotBlank()) scope.launch { try { repository.follow(userUid, authorUid, true) } catch (_: Exception) { } }
                                }.padding(horizontal = 10.dp, vertical = 2.dp)) {
                                    Text("关注", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (durMs > 0) Text("${left / 60}:${String.format("%02d", left % 60)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    // 标题
                    Text(realTitle.ifBlank { title }, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }

            // 底部 bar（单独区域，背景透明，距底 16dp）：进度条 + 输入框 + 点赞收藏评论
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
                // 进度条
                if (durMs > 0) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(prog).height(2.dp).background(Color.White))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 作者模式（自己的视频笔记）：展示编辑底栏（黑底适配，第一行用 surface 色）
                val isAuthorMode = editMode && authorUid.isNotBlank() && authorUid == userUid
                if (isAuthorMode) {
                    val isPublic = visibility != "private"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            PostEditAreaContent(
                                isPublic = isPublic,
                                isOnDark = true,
                                onAreaClick = { authorPanel = AuthorPanel.Actions }
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // 右边：点赞收藏评论
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            ActIcon(if (liked) R.drawable.favorite_fill else R.drawable.favorite_light, likeCnt, if (liked) Color.Unspecified else Color.White) {
                                liked = !liked
                                likeCnt = (likeCnt + if (liked) 1 else -1).coerceAtLeast(0)
                                onLikeClick(likeCnt)
                                if (videoId.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            repository.recordLike(userUid, videoId, liked)
                                            repository.updatePostLike(videoId, if (liked) 1 else -1)
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            ActIcon(if (faved) R.drawable.star_fill else R.drawable.star, favCnt, if (faved) Color.Unspecified else Color.White) {
                                faved = !faved
                                favCnt = (favCnt + if (faved) 1 else -1).coerceAtLeast(0)
                                onFavoriteClick(favCnt)
                                if (videoId.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            repository.recordFavorite(userUid, videoId, faved)
                                            repository.updatePostFav(videoId, if (faved) 1 else -1)
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            ActIcon(R.drawable.chat, cmts.size) { showCmt = true }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // 左边：评论区输入框（适配黑底的深色半透明，小红书风格）
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable { kbVisible = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.edit_grey), null, Modifier.size(20.dp), tint = Color(0xFF9A9A9A))
                                Spacer(Modifier.width(5.dp))
                                Text("说点什么...", fontSize = 14.sp, color = Color(0xFF9A9A9A))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // 右边：点赞收藏评论，均匀分布
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            ActIcon(if (liked) R.drawable.favorite_fill else R.drawable.favorite_light, likeCnt, if (liked) Color.Unspecified else Color.White) {
                                liked = !liked
                                likeCnt = (likeCnt + if (liked) 1 else -1).coerceAtLeast(0)
                                onLikeClick(likeCnt)
                                if (videoId.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            repository.recordLike(userUid, videoId, liked)
                                            repository.updatePostLike(videoId, if (liked) 1 else -1)
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            ActIcon(if (faved) R.drawable.star_fill else R.drawable.star, favCnt, if (faved) Color.Unspecified else Color.White) {
                                faved = !faved
                                favCnt = (favCnt + if (faved) 1 else -1).coerceAtLeast(0)
                                onFavoriteClick(favCnt)
                                if (videoId.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            repository.recordFavorite(userUid, videoId, faved)
                                            repository.updatePostFav(videoId, if (faved) 1 else -1)
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            ActIcon(R.drawable.chat, cmts.size) { showCmt = true }
                        }
                    }
                }
            }
        }

        // 顶部返回栏（最顶层，透明背景，始终可见，不被视频覆盖）
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 36.dp, start = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.arrow_left), null, Modifier.size(28.dp).clickable { onBack() }, tint = Color.White)
        }

        // 评论区浮层
        if (showCmt) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCmt = false })
            Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("评论 ${cmts.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            Text("×", fontSize = 18.sp, color = Color.Gray, modifier = Modifier.clickable { showCmt = false }.padding(4.dp))
                        }
                        LazyColumn(state = cmtListState, modifier = Modifier.weight(1f).pointerInput(Unit) {
                            detectTapGestures {
                                kbVisible = false
                                cmtText = ""
                                replyTgt = null
                            }
                        }) {
                            if (cmts.isEmpty()) { item { Text("暂无评论", Modifier.padding(16.dp), color = Color.Gray, fontSize = 14.sp) } }
                            items(cmts.toList(), key = { it.id }) { c ->
                                CommentItem(comment = c,
                                    onReplyClick = { cid, name -> replyTgt = cid to name; cmtText = "回复 @$name："; kbVisible = true },
                                    onLikeClick = { cid -> toggleLike(cid, cmts) },
                                    onAvatarClick = { uid -> if (uid.isNotBlank() && uid != "me") onUserClick(uid) },
                                    onUserNameClick = { uid -> if (uid.isNotBlank() && uid != "me") onUserClick(uid) },
                                    highlight = c.id == highlightCommentId)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (userAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(userAvatarUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(painterResource(R.drawable.test), null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { kbVisible = true }.padding(horizontal = 16.dp), Alignment.CenterStart) {
                                Text("说点什么...", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // 键盘输入（遮罩 + KeyboardInputBar 同时弹出）
        if (kbVisible) {
            // 遮罩层：点击空白收起键盘
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable {
                        kbVisible = false
                        cmtText = ""
                        selUris.clear()
                        replyTgt = null
                    }
            )
            Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
                KeyboardInputBar(
                    text = cmtText, onTextChange = { cmtText = it },
                    selectedImages = selUris.toList(), onAddImageClick = { picker.launch("image/*") },
                    onRemoveImage = { selUris.remove(it) },
                    onSend = { t, imgs ->
                        val hasContent = t.isNotBlank() || imgs.isNotEmpty()
                        if (hasContent) {
                            val ct = t.replace(Regex("^回复 @\\S+："), "").trim()
                            if (ct.isNotBlank() || imgs.isNotEmpty()) {
                                val rt = replyTgt
                                if (rt != null) {
                                    val i = cmts.indexOfFirst { it.id == rt.first }
                                    if (i >= 0) cmts[i] = cmts[i].copy(replies = cmts[i].replies + Reply("r${System.currentTimeMillis()}", "me", "我", R.drawable.test, imgs, ct, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                    replyTgt = null
                                    scope.launch {
                                        try {
                                            val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(context.applicationContext) ?: ""
                                            repository.insertReply("r${System.currentTimeMillis()}", videoId, rt.first, ct, userUid, "我", userAvatarUrl, userXhsId, title, "", ip)
                                        } catch (_: Exception) { }
                                    }
                                } else {
                                    cmts.add(Comment("c${System.currentTimeMillis()}", "me", "我", R.drawable.test, imgs, ct, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                    scope.launch {
                                        try {
                                            val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(context.applicationContext) ?: ""
                                            repository.insertComment("c${System.currentTimeMillis()}", videoId, ct, userUid, "我", userAvatarUrl, userXhsId, title, "", ip)
                                        } catch (_: Exception) { }
                                    }
                                }
                                cmtText = ""; selUris.clear()
                            }
                        }
                        kbVisible = false
                    },
                    onClose = { kbVisible = false; cmtText = ""; selUris.clear(); replyTgt = null },
                    focusRequester = focusReq,
                    modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                )
            }
        }

        // 删除视频笔记确认（作者模式）
        if (confirmDeletePost) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { confirmDeletePost = false }) {
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
                        Text("删除笔记", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text("确定删除该视频笔记吗？删除后不可恢复。", fontSize = 14.sp, color = getOnSurfaceSecondary())
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { confirmDeletePost = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = getOnSurfaceSecondary())
                        }
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline()))
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable {
                                confirmDeletePost = false
                                authorPanel = AuthorPanel.None
                                if (videoId.isNotBlank()) {
                                    scope.launch {
                                        try { repository.deletePost(videoId) } catch (_: Exception) { }
                                    }
                                }
                                onVideoDeleted()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("确认", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // ===== 作者模式底部浮层面板（最高层，遮罩 + 顶部16圆角卡片） =====
        val isAuthorOverlay = editMode && authorUid.isNotBlank() && authorUid == userUid && authorPanel != AuthorPanel.None
        if (isAuthorOverlay) {
            val panelPublic = visibility != "private"
            when (authorPanel) {
                AuthorPanel.Actions -> AuthorBottomSheetOverlay(
                    onDismiss = { authorPanel = AuthorPanel.None }
                ) {
                    EditPostActionPanel(
                        onClose = { authorPanel = AuthorPanel.None },
                        onEditClick = {
                            authorPanel = AuthorPanel.None
                            onEditPost(
                                com.example.redbook.data.model.PostToEdit(
                                    postId = videoId,
                                    title = realTitle.ifBlank { title },
                                    content = content,
                                    imageUrl = if (videoUrl.startsWith("video:")) videoUrl else "video:$videoUrl",
                                    visibility = visibility
                                )
                            )
                        },
                        onPermissionClick = { authorPanel = AuthorPanel.Permission },
                        onDeleteClick = { confirmDeletePost = true }
                    )
                }
                AuthorPanel.Permission -> AuthorBottomSheetOverlay(
                    onDismiss = { authorPanel = AuthorPanel.None }
                ) {
                    PostPermissionPanel(
                        isPublic = panelPublic,
                        onSelect = { public ->
                            val v = if (public) "public" else "private"
                            visibility = v
                            if (videoId.isNotBlank()) {
                                scope.launch {
                                    try { repository.setPostVisibility(videoId, v) } catch (_: Exception) { }
                                }
                            }
                        },
                        onClose = { authorPanel = AuthorPanel.None }
                    )
                }
                AuthorPanel.None -> { }
            }
        }
    }
}

private fun toggleLike(cid: String, cmts: MutableList<Comment>) {
    for (i in cmts.indices) {
        val c = cmts[i]
        if (c.id == cid) { cmts[i] = c.copy(isLiked = !c.isLiked, likeCount = c.likeCount + if (c.isLiked) -1 else 1); return }
        for (j in c.replies.indices) {
            if (c.replies[j].id == cid) {
                val r = c.replies[j]
                cmts[i] = c.copy(replies = c.replies.toMutableList().also { it[j] = r.copy(isLiked = !r.isLiked, likeCount = r.likeCount + if (r.isLiked) -1 else 1) })
                return
            }
        }
    }
}

/** 加载云端评论到本地列表（viewerUid 视角做备注名替换） */
private suspend fun loadVideoComments(
    repository: SupabaseAuthRepository,
    postId: String,
    postAuthorUid: String,
    viewerUid: String,
    cmts: MutableList<Comment>
) {
    if (postId.isBlank()) return
    try {
        val arr = repository.getComments(postId)
        val raw = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val parentId = c.optString("parent_id", "")
            parentId to Comment(
                id = c.optString("comment_id", ""),
                userId = c.optString("author_uid", ""),
                userName = c.optString("author_name", ""),
                avatarRes = R.drawable.test,
                avatarUrl = c.optString("author_avatar", ""),
                content = c.optString("content", ""),
                timestamp = c.optLong("created_at", 0),
                ipLocation = c.optString("ip_location", "未知"),
                likeCount = c.optInt("like_count", 0),
                isLiked = false,
                isAuthor = c.optString("author_uid") == postAuthorUid,
                replies = emptyList()
            )
        }
        val replies = raw.filter { it.first.isNotEmpty() }
        var comments = raw.filter { it.first.isEmpty() }.map { (_, comment) ->
            comment.copy(
                replies = replies.filter { it.first == comment.id }.map { (_, r) ->
                    Reply(
                        id = r.id,
                        userId = r.userId,
                        userName = r.userName,
                        avatarRes = r.avatarRes,
                        avatarUrl = r.avatarUrl,
                        content = r.content,
                        timestamp = r.timestamp,
                        ipLocation = r.ipLocation,
                        likeCount = r.likeCount,
                        isLiked = r.isLiked,
                        isAuthor = r.isAuthor
                    )
                }
            )
        }
        // 备注名替换：评论/回复作者名替换为"我"对他们的备注
        if (viewerUid.isNotBlank() && comments.isNotEmpty()) {
            try {
                val uids = buildSet {
                    comments.forEach { c ->
                        if (c.userId.isNotBlank()) add(c.userId)
                        c.replies.forEach { r -> if (r.userId.isNotBlank()) add(r.userId) }
                    }
                }
                val remarks = repository.getRemarks(viewerUid, uids)
                if (remarks.isNotEmpty()) {
                    val displayName = { uid: String, fallback: String -> remarks[uid].orEmpty().ifBlank { fallback } }
                    comments = comments.map { c ->
                        c.copy(
                            userName = displayName(c.userId, c.userName),
                            replies = c.replies.map { r -> r.copy(userName = displayName(r.userId, r.userName)) }
                        )
                    }
                }
            } catch (_: Exception) { }
        }
        cmts.clear()
        cmts.addAll(comments)
    } catch (_: Exception) { }
}

@Composable
private fun ActIcon(iconRes: Int, count: Int, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.width(4.dp)); Text("$count", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}
