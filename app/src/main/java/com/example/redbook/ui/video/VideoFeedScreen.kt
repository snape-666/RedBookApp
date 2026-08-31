package com.example.redbook.ui.video

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.model.Comment
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.theme.getOnSurfaceSecondary
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

@Composable
fun VideoFeedScreen(
    userUid: String = "",
    onBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onVideoClick: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    var videos by remember { mutableStateOf<List<FeedVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            // 数据源：posts 表中 image_url 带 video: 前缀的视频（视频统一存 posts 表）
            val merged = mutableListOf<FeedVideo>()
            try {
                val posts = repository.getPosts()
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
            videos = merged
        } catch (_: Exception) { }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 顶部返回 bar
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(top = 24.dp, start = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier.size(28.dp).clickable { onBack() },
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text("视频", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
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
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = videos[page]
                FeedVideoPage(
                    video = video,
                    userUid = userUid,
                    isActive = pagerState.currentPage == page,
                    onUserClick = onUserClick,
                    repository = repository,
                    scope = scope,
                    onVideoClick = { onVideoClick(video.videoId, video.videoUrl) }
                )
            }
        }
    }
}

@Composable
private fun FeedVideoPage(
    video: FeedVideo,
    userUid: String,
    isActive: Boolean,
    onUserClick: (String) -> Unit,
    repository: SupabaseAuthRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onVideoClick: () -> Unit
) {
    var curMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    var paused by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var likeCnt by remember { mutableIntStateOf(0) }
    var favCnt by remember { mutableIntStateOf(0) }
    var commentCount by remember { mutableIntStateOf(0) }
    var followed by remember { mutableStateOf(false) }

    LaunchedEffect(video.videoId, userUid) {
        if (video.videoId.isNotBlank() && userUid.isNotBlank()) {
            try {
                liked = repository.hasLiked(userUid, video.videoId)
                faved = repository.hasFavorited(userUid, video.videoId)
                // 视频统一存 posts 表，用 getPost 查
                val v = repository.getPost(video.videoId)
                if (v != null) {
                    likeCnt = v.optInt("like_count", 0)
                    favCnt = v.optInt("favorite_count", 0)
                    commentCount = v.optInt("comment_count", 0)
                    if (video.authorUid.isNotBlank()) {
                        followed = repository.isFollowing(userUid, video.authorUid)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // 播放/暂停：仅在当前页激活时播放
    LaunchedEffect(isActive, video.videoUrl) {
        if (isActive) {
            paused = false
            delay(50)
            vv?.start()
        } else {
            vv?.pause()
        }
    }

    LaunchedEffect(vv, isActive) {
        while (isActive) {
            delay(100)
            vv?.let { curMs = it.currentPosition }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && isActive) vv?.start()
            if (e == Lifecycle.Event.ON_PAUSE) vv?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val prog = if (durMs > 0) curMs.coerceAtMost(durMs).toFloat() / durMs else 0f
    val left = ((durMs - curMs.coerceAtMost(durMs)) / 1000).coerceAtLeast(0)

    Box(Modifier.fillMaxSize()) {
        // 视频区：点击暂停
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    paused = !paused
                    if (paused) vv?.pause() else vv?.start()
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { ctx -> VideoView(ctx).also { vv = it }.apply {
                try { if (video.videoUrl.startsWith("/")) setVideoPath(video.videoUrl) else setVideoURI(Uri.parse(video.videoUrl)) }
                catch (_: Exception) { setVideoURI(Uri.parse(video.videoUrl)) }
                setOnPreparedListener { mp -> durMs = mp.duration; mp.isLooping = false }
                setOnCompletionListener { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start() }, 1000) }
                setOnErrorListener { _, _, _ -> false }
                if (isActive) start()
            }}, Modifier.fillMaxWidth().fillMaxSize())
            if (durMs == 0 && !paused) {
                CircularProgressIndicator(color = Color.White)
            }
            // 暂停图标
            if (paused) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.video_fill),
                        contentDescription = "暂停",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // 底部信息区
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 16.dp)
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
            Spacer(Modifier.height(8.dp))
            // 进度条
            if (durMs > 0) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(prog).height(2.dp).background(Color.White))
                }
            }
            Spacer(Modifier.height(10.dp))
            // 互动栏
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FeedActIcon(if (liked) R.drawable.favorite_fill else R.drawable.favorite_light, likeCnt, if (liked) Color.Unspecified else Color.White) {
                    liked = !liked
                    likeCnt = (likeCnt + if (liked) 1 else -1).coerceAtLeast(0)
                    if (video.videoId.isNotBlank()) {
                        scope.launch {
                            try {
                                repository.recordLike(userUid, video.videoId, liked)
                                repository.updatePostLike(video.videoId, if (liked) 1 else -1)
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
                                repository.updatePostFav(video.videoId, if (faved) 1 else -1)
                            } catch (_: Exception) { }
                        }
                    }
                }
                FeedActIcon(R.drawable.chat, commentCount, Color.White, onClick = onVideoClick)
            }
        }
    }
}

@Composable
private fun FeedActIcon(iconRes: Int, count: Int, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text("$count", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}
