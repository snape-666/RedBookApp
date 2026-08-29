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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.example.redbook.ui.component.CommentItem
import com.example.redbook.ui.component.KeyboardInputBar
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val videoComments = mutableMapOf<String, MutableList<Comment>>()

@SuppressLint("DefaultLocale")
@Composable
fun VideoDetailScreen(
    videoUrl: String, title: String, authorName: String, authorAvatar: Int,
    isFollowed: Boolean, likeCount: Int, favoriteCount: Int, commentCount: Int,
    videoId: String = "",
    userUid: String = "",
    userXhsId: String = "",
    authorAvatarUrl: String = "",
    onBack: () -> Unit, onFollowClick: (Boolean) -> Unit,
    onLikeClick: (Int) -> Unit, onFavoriteClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    var curMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    var vv by remember { mutableStateOf<VideoView?>(null) }
    var followed by remember { mutableStateOf(isFollowed) }
    var authorUid by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    var faved by remember { mutableStateOf(false) }
    var likeCnt by remember { mutableIntStateOf(likeCount) }
    var favCnt by remember { mutableIntStateOf(favoriteCount) }
    var showCmt by remember { mutableStateOf(false) }
    var cmtText by remember { mutableStateOf("") }
    var kbVisible by remember { mutableStateOf(false) }
    var selUris = remember { mutableStateListOf<Uri>() }
    val focusReq = remember { FocusRequester() }
    val cmts = remember { videoComments.getOrPut(videoUrl) { mutableStateListOf() } }
    var replyTgt by remember { mutableStateOf<Pair<String, String>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { u ->
        u.forEach { if (it !in selUris) selUris.add(it) }
    }

    LaunchedEffect(vv) { while (true) { delay(50); vv?.let { curMs = it.currentPosition } } }
    LaunchedEffect(videoId, userUid) {
        if (videoId.isNotBlank() && userUid.isNotBlank()) {
            try {
                liked = repository.hasLiked(userUid, videoId)
                faved = repository.hasFavorited(userUid, videoId)
                val p = repository.getPost(videoId)
                if (p != null) {
                    likeCnt = p.optInt("like_count", 0)
                    favCnt = p.optInt("favorite_count", 0)
                    authorUid = p.optString("author_uid", "")
                    if (authorUid.isNotBlank()) {
                        followed = repository.isFollowing(userUid, authorUid)
                    }
                }
            } catch (_: Exception) { }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vv?.start() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val prog = if (durMs > 0) curMs.coerceAtMost(durMs).toFloat() / durMs else 0f
    val left = ((durMs - curMs.coerceAtMost(durMs)) / 1000).coerceAtLeast(0)

    // 主容器
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 视频+信息+底部栏
        Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
            // 返回按钮
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp)) {
                Icon(painterResource(R.drawable.arrow_left), null, Modifier.size(28.dp).clickable { onBack() }, tint = getOnSurfaceSecondary())
            }
            // 视频
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                AndroidView(factory = { ctx -> VideoView(ctx).also { vv = it }.apply {
                    try { if (videoUrl.startsWith("/")) setVideoPath(videoUrl) else setVideoURI(Uri.parse(videoUrl)) }
                    catch (_: Exception) { setVideoURI(Uri.parse(videoUrl)) }
                    setOnPreparedListener { mp -> durMs = mp.duration; mp.isLooping = false }
                    setOnCompletionListener { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start() }, 1000) }
                    setOnErrorListener { _, _, _ -> false }; start()
                }}, Modifier.fillMaxWidth())
                if (durMs == 0) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            // 作者
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (authorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(authorAvatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(painterResource(authorAvatar), null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(5.dp))
                Text(authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1)
            // 进度条
            if (durMs > 0) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(prog).height(2.dp).background(Color.White))
                }
            }
            // 底部互动栏
            Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { showCmt = true }.padding(horizontal = 15.dp), Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.edit_grey), null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.5f))
                        Spacer(Modifier.width(6.dp)); Text("说点什么...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Spacer(Modifier.width(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Spacer(Modifier.height(16.dp))
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
                        LazyColumn(Modifier.weight(1f)) {
                            if (cmts.isEmpty()) { item { Text("暂无评论", Modifier.padding(16.dp), color = Color.Gray, fontSize = 14.sp) } }
                            items(cmts.toList(), key = { it.id }) { c ->
                                CommentItem(comment = c,
                                    onReplyClick = { cid, name -> replyTgt = cid to name; cmtText = "回复 @$name："; kbVisible = true },
                                    onLikeClick = { cid -> toggleLike(cid, cmts) },
                                    onAvatarClick = {}, onUserNameClick = {})
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.test), null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { kbVisible = true }.padding(horizontal = 16.dp), Alignment.CenterStart) {
                                Text("说点什么...", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // 键盘输入
        if (kbVisible) {
            Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
            KeyboardInputBar(
                text = cmtText, onTextChange = { cmtText = it },
                selectedImages = selUris.toList(), onAddImageClick = { picker.launch("image/*") },
                onRemoveImage = { selUris.remove(it) },
                onSend = { t, _ ->
                    if (t.isNotBlank()) {
                        val ct = t.replace(Regex("^回复 @\\S+："), "").trim()
                        if (ct.isNotBlank()) {
                            val rt = replyTgt
                            if (rt != null) {
                                val i = cmts.indexOfFirst { it.id == rt.first }
                                if (i >= 0) cmts[i] = cmts[i].copy(replies = cmts[i].replies + Reply("r${System.currentTimeMillis()}", "me", "我", R.drawable.test, selUris.toList(), ct, System.currentTimeMillis(), "未知", 0, false, false))
                                replyTgt = null
                            } else {
                                cmts.add(Comment("c${System.currentTimeMillis()}", "me", "我", R.drawable.test, selUris.toList(), ct, System.currentTimeMillis(), "未知", 0, false, false))
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

@Composable
private fun ActIcon(iconRes: Int, count: Int, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.width(4.dp)); Text("$count", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}
