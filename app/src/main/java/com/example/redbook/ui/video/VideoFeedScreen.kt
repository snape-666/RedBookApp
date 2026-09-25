package com.example.redbook.ui.video

import android.net.Uri
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.model.Comment
import com.example.redbook.data.model.Reply
import com.example.redbook.data.repository.AiAssistant
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.component.CommentItem
import com.example.redbook.ui.component.KeyboardInputBar
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class FeedVideo(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val authorUid: String,
    val authorName: String,
    val authorAvatar: String
)

// 评论回复目标：(被回复id, 所属一级评论id, 对方名字, 是否回复小助手)
private data class FeedReplyTarget(val targetId: String, val parentCommentId: String, val name: String, val toAi: Boolean)

private val feedComments = mutableMapOf<String, MutableList<Comment>>()

@Composable
fun VideoFeedScreen(
    userUid: String = "",
    userName: String = "",
    userAvatarUrl: String = "",
    onBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    var videos by remember { mutableStateOf<List<FeedVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
//数据加载
    LaunchedEffect(Unit) {
        try {
            // 数据源：posts 表中 image_url 带 video: 前缀的视频（视频统一存 posts 表）
            val merged = mutableListOf<FeedVideo>()
            try {
                val posts = repository.filterVisiblePosts(repository.getPosts(), userUid)
                for (i in 0 until posts.length()) {
                    val p = posts.getJSONObject(i)
                    val imageUrl = p.optString("image_url", "")
                    if (imageUrl.startsWith("video:")) {
                        merged.add(
                            FeedVideo(
                                videoId = p.optString("post_id", ""),
                                videoUrl = imageUrl.removePrefix("video:"),
                                title = p.optString("title", ""),
                                authorUid = p.optString("author_uid", ""),
                                authorName = p.optString("author_name", "").ifBlank { "小红书用户" },
                                authorAvatar = p.optString("author_avatar", "")
                            )
                        )
                    }
                }
            } catch (_: Exception) { }
            // 备注名全局替换：作者名替换为"我"对该作者的备注
            videos = if (userUid.isNotBlank()) {
                try {
                    val uids = merged.map { it.authorUid }.filter { it.isNotBlank() }.distinct()
                    if (uids.isEmpty()) merged
                    else {
                        val remarks = repository.getRemarks(userUid, uids)
                        if (remarks.isEmpty()) merged
                        else merged.map { v ->
                            val remark = remarks[v.authorUid]
                            if (!remark.isNullOrBlank()) v.copy(authorName = remark) else v
                        }
                    }
                } catch (_: Exception) { merged }
            } else merged
        } catch (_: Exception) { }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (videos.isEmpty()) {
            Text(
                "暂无视频",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { videos.size })
            VerticalPager(
                state = pagerState,
                // 预加载相邻页：上/下一页进入组合即开始缓冲，滑动时秒开
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = videos[page]
                FeedVideoPage(
                    video = video,
                    userUid = userUid,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    isActive = pagerState.currentPage == page,
                    onUserClick = onUserClick,
                    repository = repository,
                    scope = scope
                )
            }
        }
        // 顶部返回 bar（最顶层，每个视频页都显示，透明背景，不被视频覆盖）
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier.size(28.dp).clickable { onBack() },
                tint = Color.White
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedVideoPage(
    video: FeedVideo,
    userUid: String,
    userName: String,
    userAvatarUrl: String,
    isActive: Boolean,
    onUserClick: (String) -> Unit,
    repository: SupabaseAuthRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val appContext = LocalContext.current.applicationContext
    // 当前用户的身份：优先真实 uid/名（备注名由上层处理好），未登录时回退占位，避免把真人评论写成“我”
    val myUid = userUid.ifBlank { "me" }
    val myName = userName.ifBlank { "我" }
    var curMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    val player = rememberVideoPlayer(video.videoUrl)
    // 视频宽高比（0<ratio<1 为竖屏）：竖屏 cover 铺满视频区域，横屏保持等比自适应
    var videoAspect by remember { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var likeCnt by remember { mutableIntStateOf(0) }
    var favCnt by remember { mutableIntStateOf(0) }
    var followed by remember { mutableStateOf(false) }
    var showCmt by remember { mutableStateOf(false) }
    var cmtText by remember { mutableStateOf("") }
    var kbVisible by remember { mutableStateOf(false) }
    var selUris = remember { mutableStateListOf<Uri>() }
    val focusReq = remember { FocusRequester() }
    val cmts = remember { feedComments.getOrPut(video.videoId) { mutableStateListOf() } }
    var replyTgt by remember { mutableStateOf<FeedReplyTarget?>(null) }
    // 长按要删的评论/回复 id：只有自己发的才会被放进来
    var deletingComment by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { u ->
        u.forEach { if (it !in selUris) selUris.add(it) }
    }

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) kbVisible = false
    }

    LaunchedEffect(video.videoId, userUid) {
        if (video.videoId.isNotBlank() && userUid.isNotBlank()) {
            try {
                liked = repository.hasLiked(userUid, video.videoId)
                faved = repository.hasFavorited(userUid, video.videoId)
                val v = repository.getPost(video.videoId)
                if (v != null) {
                    likeCnt = v.optInt("like_count", 0)
                    favCnt = v.optInt("favorite_count", 0)
                    if (video.authorUid.isNotBlank()) {
                        followed = repository.isFollowing(userUid, video.authorUid)
                    }
                }
                // 加载云端评论（post_id = videoId）
                loadFeedComments(repository, video.videoId, video.authorUid, userUid, cmts)
            } catch (_: Exception) { }
        }
    }

    // 播放/暂停：仅在当前页激活时播放（player 进入页面即 prepare 缓冲，切到时秒开）
    LaunchedEffect(isActive, video.videoUrl) {
        if (isActive) {
            paused = false
            delay(50)
            player?.play()
        } else {
            player?.pause()
        }
    }

    LaunchedEffect(player, isActive) {
        while (isActive) {
            delay(100)
            player?.let {
                curMs = it.currentPosition.toInt()
                val d = it.duration
                if (d > 0) durMs = d.toInt()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && isActive) player?.play()
            if (e == Lifecycle.Event.ON_PAUSE) player?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val prog = if (durMs > 0) curMs.coerceAtMost(durMs).toFloat() / durMs else 0f
    val left = ((durMs - curMs.coerceAtMost(durMs)) / 1000).coerceAtLeast(0)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxSize()) {
            // 视频区域（进度条上方）：视频 + 底部信息
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        paused = !paused
                        if (paused) player?.pause() else player?.play()
                    },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            // 竖屏视频：宽度占满、高度居中裁剪填满视频区域；横屏/方形：等比自适应不裁剪
                            fun applyVideoSize(videoSize: VideoSize) {
                                if (videoSize.width > 0 && videoSize.height > 0) {
                                    videoAspect = videoSize.width.toFloat() / videoSize.height
                                    resizeMode = if (videoSize.width < videoSize.height) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            }
                            // prepare 可能已在监听器注册前上报尺寸，先立即同步应用一次
                            applyVideoSize(player?.videoSize ?: VideoSize.UNKNOWN)
                            player?.addListener(object : Player.Listener {
                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    applyVideoSize(videoSize)
                                }
                            })
                        }
                    },
                    modifier = if (videoAspect > 0f && videoAspect < 1f) {
                        // 占满进度条上方视频区域，裁剪溢出，绝不允许压到底部栏
                        Modifier.fillMaxSize().clipToBounds()
                    } else {
                        Modifier.fillMaxWidth().wrapContentHeight()
                    }
                )
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

                // 底部信息（叠在视频区域底部，背景透明）
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 作者行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video.authorAvatar.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(video.authorAvatar).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { if (video.authorUid.isNotBlank()) onUserClick(video.authorUid) },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(painterResource(R.drawable.test), null, Modifier.size(32.dp).clip(CircleShape).clickable { if (video.authorUid.isNotBlank()) onUserClick(video.authorUid) }, contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(video.authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { if (video.authorUid.isNotBlank()) onUserClick(video.authorUid) })
                        Spacer(Modifier.width(8.dp))
                        if (video.authorUid != userUid) {
                            if (followed) {
                                Box(Modifier.border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp).clickable {
                                    followed = false
                                    if (video.authorUid.isNotBlank()) scope.launch { try { repository.follow(userUid, video.authorUid, false) } catch (_: Exception) { } }
                                }) {
                                    Text("已关注", fontSize = 12.sp, color = Color.White) }
                            } else {
                                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Red).clickable {
                                    followed = true
                                    if (video.authorUid.isNotBlank()) scope.launch { try { repository.follow(userUid, video.authorUid, true) } catch (_: Exception) { } }
                                }.padding(horizontal = 10.dp, vertical = 2.dp)) {
                                    Text("关注", fontSize = 12.sp, color = Color.White) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (durMs > 0) Text("${left / 60}:${String.format("%02d", left % 60)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    // 标题
                    Text(video.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                }
            }

            // 底部 bar（独立一块，距底 16dp）：进度条 + 输入框 + 点赞收藏评论
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 16.dp)
            ) {
                // 进度条
                if (durMs > 0) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(prog).height(2.dp).background(Color.White))
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
                        FeedActIcon(if (liked) R.drawable.favorite_fill else R.drawable.favorite_light, likeCnt, if (liked) Color.Unspecified else Color.White) {
                            liked = !liked
                            likeCnt = (likeCnt + if (liked) 1 else -1).coerceAtLeast(0)
                            if (video.videoId.isNotBlank()) {
                                scope.launch {
                                    try {
                                        repository.recordLike(userUid, video.videoId, liked)
                                        repository.updatePostLike(video.videoId)
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        FeedActIcon(if (faved) R.drawable.star_fill else R.drawable.star, favCnt, if (faved) Color.Unspecified else Color.White) {
                            faved = !faved
                            favCnt = (favCnt + if (faved) 1 else -1).coerceAtLeast(0)
                            if (video.videoId.isNotBlank()) {
                                scope.launch {
                                    try {
                                        repository.recordFavorite(userUid, video.videoId, faved)
                                        repository.updatePostFav(video.videoId)
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        FeedActIcon(R.drawable.chat, cmts.size) { showCmt = true }
                    }
                }
            }
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
                        LazyColumn(Modifier.weight(1f).pointerInput(Unit) {
                            detectTapGestures {
                                kbVisible = false
                                cmtText = ""
                                replyTgt = null
                            }
                        }) {
                            if (cmts.isEmpty()) { item { Text("暂无评论", Modifier.padding(16.dp), color = Color.Gray, fontSize = 14.sp) } }
                            items(cmts.toList(), key = { it.id }) { c ->
                                CommentItem(comment = c,
                                    onReplyClick = { cid, parentId, name, toAi ->
                                        replyTgt = FeedReplyTarget(cid, parentId, name, toAi)
                                        cmtText = if (toAi) "回复 @$name：" else "回复 @$name："
                                        kbVisible = true
                                    },
                                    onLikeClick = { cid ->
                                        val nowLiked = toggleFeedLike(cid, cmts)
                                        // 持久化评论点赞：本机缓存 + 云端关系表 + 计数（与视频详情页同一套）。
                                        // 不写的话，退出页面再进来点赞态和数字都会丢。
                                        if (userUid.isNotBlank()) {
                                            scope.launch {
                                                try { repository.recordCommentLike(userUid, cid, nowLiked) } catch (_: Exception) { }
                                                try { repository.updateCommentLike(cid) } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    onAvatarClick = { uid -> if (uid.isNotBlank() && uid != "me" && !AiAssistant.isAiUser(uid)) onUserClick(uid) },
                                    onUserNameClick = { uid -> if (uid.isNotBlank() && uid != "me" && !AiAssistant.isAiUser(uid)) onUserClick(uid) },
                                    onLongClick = { cid, authorUid ->
                                        // 只能删自己发的：别人的评论/回复不弹删除确认
                                        if (authorUid == myUid) deletingComment = cid
                                    })
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
                    onSend = { t, imgs, hasAiPrefix ->
                        val hasContent = t.isNotBlank() || imgs.isNotEmpty()
                        if (hasContent) {
                            val rt = replyTgt
                            if (rt != null) {
                                // 回复他人：剥离 “回复 @名字：” 前缀，追加到所属一级评论下
                                val ct = t.replace(Regex("^回复 @\\S+："), "").trim()
                                if (ct.isNotBlank() || imgs.isNotEmpty()) {
                                    val parentIdx = cmts.indexOfFirst { it.id == rt.parentCommentId }
                                    if (parentIdx >= 0) {
                                        val parent = cmts[parentIdx]
                                        if (rt.toAi) {
                                            // 回复小助手：追问作为线程内的一条回复，再触发 AI 回一轮
                                            // 显示与存储都带“回复 @小助手：”前缀，方便识别同一线程追问
                                            val displayContent = "回复 @${AiAssistant.NAME}：" + ct
                                            cmts[parentIdx] = parent.copy(replies = parent.replies + Reply("r${System.currentTimeMillis()}", myUid, myName, R.drawable.test, imgs, displayContent, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                            val pendingId = "ai_pending_${System.currentTimeMillis()}"
                                            cmts[parentIdx] = cmts[parentIdx].copy(replies = cmts[parentIdx].replies + Reply(pendingId, AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), "小助手正在思考中…", System.currentTimeMillis(), "", 0, false, false, ""))
                                            scope.launch {
                                                try {
                                                    val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(appContext) ?: ""
                                                    repository.insertReply("r${System.currentTimeMillis()}", video.videoId, rt.parentCommentId, displayContent, myUid, myName, userAvatarUrl, "", video.title, "", ip)
                                                    val aiCtx = parent.replies.filter { AiAssistant.isAiUser(it.userId) }.maxByOrNull { it.timestamp }
                                                    val aiQuestion = "@${AiAssistant.NAME}: " + (aiCtx?.let { "（小助手上一轮回答：${it.content}）" } ?: "") + ct
                                                    val aiResult = AiAssistant.askAndReply(appContext as android.app.Application, video.videoId, rt.parentCommentId, aiQuestion, postTitle = video.title, postVideoUrl = video.videoUrl)
                                                    val pi = cmts.indexOfFirst { it.id == rt.parentCommentId }
                                                    if (pi >= 0) {
                                                        val done = if (aiResult != null) Reply(aiResult.replyId, AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), aiResult.text, System.currentTimeMillis(), "", 0, false, false, "")
                                                        else Reply("ai_fail_${System.currentTimeMillis()}", AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), "小助手暂时无法回答，请稍后再试", System.currentTimeMillis(), "", 0, false, false, "")
                                                        cmts[pi] = cmts[pi].copy(replies = cmts[pi].replies.filterNot { it.id == pendingId } + done)
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        } else {
                                            // 普通回复（回复一级或二级都挂到该一级评论线程）
                                            cmts[parentIdx] = parent.copy(replies = parent.replies + Reply("r${System.currentTimeMillis()}", myUid, myName, R.drawable.test, imgs, ct, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                            scope.launch {
                                                try {
                                                    val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(appContext) ?: ""
                                                    repository.insertReply("r${System.currentTimeMillis()}", video.videoId, rt.parentCommentId, ct, myUid, myName, userAvatarUrl, "", video.title, "", ip)
                                                } catch (_: Exception) { }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 新发一级评论：普通 or @小助手提问
                                val aiQ = if (hasAiPrefix) AiAssistant.parseQuestion(t) else null
                                val ct = t.trim()
                                if (ct.isNotBlank() || imgs.isNotEmpty()) {
                                    if (aiQ != null) {
                                        val myId = "c${System.currentTimeMillis()}"
                                        val pendingId = "ai_pending_${System.currentTimeMillis()}"
                                        // 提问评论 + 挂在提问下的“思考中…”占位
                                        cmts.add(Comment(myId, myUid, myName, R.drawable.test, imgs, ct, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                        val qi = cmts.indexOfFirst { it.id == myId }
                                        if (qi >= 0) cmts[qi] = cmts[qi].copy(replies = listOf(Reply(pendingId, AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), "小助手正在思考中…", System.currentTimeMillis(), "", 0, false, false, "")))
                                        scope.launch {
                                            try {
                                                val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(appContext) ?: ""
                                                repository.insertComment(myId, video.videoId, ct, myUid, myName, userAvatarUrl, "", video.title, "", ip)
                                                val aiResult = AiAssistant.askAndReply(appContext as android.app.Application, video.videoId, myId, t, postTitle = video.title, postVideoUrl = video.videoUrl)
                                                val i2 = cmts.indexOfFirst { it.id == myId }
                                                if (i2 >= 0) {
                                                    val done = if (aiResult != null) Reply(aiResult.replyId, AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), aiResult.text, System.currentTimeMillis(), "", 0, false, false, "")
                                                    else Reply("ai_fail_${System.currentTimeMillis()}", AiAssistant.UID, AiAssistant.NAME, AiAssistant.AVATAR_RES, emptyList(), "小助手暂时无法回答，请稍后再试", System.currentTimeMillis(), "", 0, false, false, "")
                                                    cmts[i2] = cmts[i2].copy(replies = cmts[i2].replies.filterNot { it.id == pendingId } + done)
                                                }
                                            } catch (_: Exception) { }
                                        }
                                    } else {
                                        cmts.add(Comment("c${System.currentTimeMillis()}", myUid, myName, R.drawable.test, imgs, ct, System.currentTimeMillis(), "未知", 0, false, false, userAvatarUrl))
                                        scope.launch {
                                            try {
                                                val ip = com.example.redbook.data.repository.IpLocationProvider.resolveProvince(appContext) ?: ""
                                                repository.insertComment("c${System.currentTimeMillis()}", video.videoId, ct, myUid, myName, userAvatarUrl, "", video.title, "", ip)
                                            } catch (_: Exception) { }
                                        }
                                    }
                                }
                            }
                            cmtText = ""; selUris.clear(); replyTgt = null
                        }
                        kbVisible = false
                    },
                    onClose = { kbVisible = false; cmtText = ""; selUris.clear(); replyTgt = null },
                    onReplyPrefixRemoved = { cmtText = ""; replyTgt = null },
                    focusRequester = focusReq,
                    modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                )
            }
        }

        // 删除评论/回复确认（与照片详情同一套样式；只有作者本人能走到这里）
        // 注意：必须放在 if (kbVisible) 外面 —— 长按评论时键盘是收起的，
        // 放在里面就等于这段代码永远不会被组合，表现为"长按毫无反应"。
        deletingComment?.let { cid ->
            androidx.compose.ui.window.Dialog(onDismissRequest = { deletingComment = null }) {
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
                        Text("确定删除该评论吗？", fontSize = 14.sp, color = getOnSurfaceTertiary())
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline().copy(alpha = 0.5f)))
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
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline().copy(alpha = 0.5f)))
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable {
                                deletingComment = null
                                // 本地先移除（一级评论，或它下面的某条回复），云端再删一次；
                                // 服务端 RLS(comments_delete_own) 才是真正兜底
                                val top = cmts.indexOfFirst { it.id == cid }
                                if (top >= 0) {
                                    cmts.removeAt(top)
                                } else {
                                    for (i in cmts.indices) {
                                        val c = cmts[i]
                                        if (c.replies.any { it.id == cid }) {
                                            cmts[i] = c.copy(replies = c.replies.filterNot { it.id == cid })
                                        }
                                    }
                                }
                                scope.launch { try { repository.deleteComment(cid) } catch (_: Exception) { } }
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

/** 本地先切点赞态并返回新状态，调用方据此写云端（与视频详情页的 toggleLike 一致） */
private fun toggleFeedLike(cid: String, cmts: MutableList<Comment>): Boolean {
    for (i in cmts.indices) {
        val c = cmts[i]
        if (c.id == cid) {
            val liked = !c.isLiked
            cmts[i] = c.copy(isLiked = liked, likeCount = (c.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
            return liked
        }
        for (j in c.replies.indices) {
            if (c.replies[j].id == cid) {
                val r = c.replies[j]
                val liked = !r.isLiked
                cmts[i] = c.copy(replies = c.replies.toMutableList().also {
                    it[j] = r.copy(isLiked = liked, likeCount = (r.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
                })
                return liked
            }
        }
    }
    return false
}

/** 加载云端评论到本地列表（viewerUid 视角做备注名替换） */
private suspend fun loadFeedComments(
    repository: SupabaseAuthRepository,
    postId: String,
    postAuthorUid: String,
    viewerUid: String,
    cmts: MutableList<Comment>
) {
    if (postId.isBlank()) return
    try {
        val arr = repository.getComments(postId)
        // 我点赞过的评论 id，用于回填 isLiked —— 不回填的话退出重进点赞态就丢了
        val likedCommentIds = try { repository.getLikedCommentIds(viewerUid) } catch (_: Exception) { emptySet<String>() }
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
                isLiked = likedCommentIds.contains(c.optString("comment_id", "")),
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
                        isLiked = likedCommentIds.contains(r.id),
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
                    // AI 小助手固定展示“小助手”，不允许被备注名覆盖
                    val displayName = { uid: String, fallback: String ->
                        if (AiAssistant.isAiUser(uid)) AiAssistant.NAME
                        else remarks[uid].orEmpty().ifBlank { fallback }
                    }
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
private fun FeedActIcon(iconRes: Int, count: Int, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text("$count", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}
