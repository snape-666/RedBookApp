package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.component.VideoThumb
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

@Composable
fun ReceivedCommentsScreen(
    userUid: String = "",
    onBack: () -> Unit = {},
    onPostClick: (String, String) -> Unit = { _, _ -> },
    onVideoClick: (String, String) -> Unit = { _, _ -> },
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: NotificationsViewModel = viewModel(
        factory = NotificationsViewModelFactory(
            context.applicationContext as android.app.Application,
            NotificationKind.COMMENT
        )
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(userUid) { viewModel.load(userUid) }

    // 实时同步：收到新的评论/回复通知时刷新列表
    val realtimeRepo = remember {
        com.example.redbook.data.repository.RealtimeRepository(context.applicationContext as android.app.Application)
    }
    DisposableEffect(userUid) {
        val listener = object : com.example.redbook.data.repository.RealtimeRepository.RealtimeListener {
            override fun onNotification(record: org.json.JSONObject) {
                val type = record.optString("type", "")
                if (type == "comment" || type == "reply") {
                    viewModel.load(userUid)
                }
            }
            override fun onMessage(record: org.json.JSONObject) { }
            override fun onStatus(connected: Boolean) { }
        }
        realtimeRepo.addListener(listener)
        onDispose { realtimeRepo.removeListener(listener) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier.weight(1f).padding(bottom = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "新增评论",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))

        when {
            loading && items.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "暂无评论", fontSize = 14.sp, color = getOnSurfaceSecondary())
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(items, key = { it.actorName + it.time }) { item ->
                    CommentRow(
                        item = item,
                        onPostClick = { postId, commentId -> onPostClick(postId, commentId) },
                        onVideoClick = onVideoClick,
                        onUserClick = { onUserClick(item.actorUid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    item: NotificationItem,
    onPostClick: (String, String) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onUserClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVideo = item.postImage.startsWith("video:")
    val cover = item.postImage.removePrefix("video:")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isVideo) onVideoClick(item.postId, cover)
                else onPostClick(item.postId, if (item.deleted) "" else item.commentId)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(CircleShape).clickable { onUserClick() }) {
            if (item.actorAvatar.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.actorAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.test),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.actorName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onUserClick() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.type == "reply") "回复了你的笔记" else "评论了你的笔记",
                    fontSize = 13.sp,
                    color = getOnSurfaceSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(item.time),
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary()
                )
            }
            if (item.commentContent.isNotBlank() || item.deleted) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.deleted) "该评论已删除" else "\"${item.commentContent}\"",
                    fontSize = 13.sp,
                    color = if (item.deleted) getOnSurfaceTertiary().copy(alpha = 0.6f) else getOnSurfaceTertiary(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            if (isVideo) {
                VideoThumb(
                    videoUrl = cover,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = R.drawable.test
                )
            } else if (cover.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                    contentDescription = "帖子封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.test),
                    contentDescription = "帖子封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

private fun formatRelativeTime(time: Long): String {
    if (time <= 0) return ""
    val diff = System.currentTimeMillis() - time
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        days >= 30 -> "${days / 30}个月前"
        days >= 1 -> "${days}天前"
        hours >= 1 -> "${hours}小时前"
        minutes >= 1 -> "${minutes}分钟前"
        else -> "刚刚"
    }
}
