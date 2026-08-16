package com.example.redbook.ui.profile

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.model.UserComment
import com.example.redbook.ui.component.BottomBar
import com.example.redbook.ui.component.PostCard
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userUid: String,
    userName: String,
    userXhsId: String,
    ipLocation: String,
    followCount: Int,
    fansCount: Int,
    likeCount: Int,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onBottomTabClick: (Int) -> Unit,
    onPostClick: (String) -> Unit,
    onVideoClick: (String, String) -> Unit = { _, _ -> },
    onCommentClick: (String, String) -> Unit = { _, _ -> },
    onPublish: () -> Unit = {},
    onDraftClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context.applicationContext as android.app.Application, userUid, userXhsId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val noRipple = remember { MutableInteractionSource() }
    var bottomIndex by remember { mutableIntStateOf(3) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var deletingComment by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ProfileHeader(
                userName = userName,
                userXhsId = userXhsId,
                ipLocation = ipLocation,
                followCount = followCount,
                fansCount = fansCount,
                likeCount = likeCount,
                onBack = onBack,
                onEditProfile = onEditProfile,
                noRipple = noRipple
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-8).dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTab, onTabSelected = { selectedTab = it })

                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 80.dp)
                        ) {
                            when (selectedTab) {
                                0 -> {
                                    if (state.draftCount > 0) {
                                        item { DraftBox(state.latestDraftImage, state.draftCount, onDraftClick) }
                                    }
                                    items(state.posts, key = { it.id }) { note ->
                                        PostCard(
                                            imageRes = note.imageRes,
                                            title = note.title,
                                            avatarRes = note.avatarRes,
                                            userName = note.userName,
                                            isLiked = note.isLiked,
                                            likeCount = note.likeCount.toString(),
                                            onCardClick = {
                                                if (note.imageUrl.startsWith("video:")) onVideoClick(note.id, note.imageUrl.removePrefix("video:"))
                                                else onPostClick(note.id)
                                            },
                                            imageUrl = note.imageUrl)
                                    }
                                }
                                1 -> {
                                    if (state.comments.isEmpty()) {
                                        item(span = StaggeredGridItemSpan.FullLine) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp), Alignment.Center) {
                                                Text("暂无评论", color = Color.Gray, fontSize = 14.sp)
                                            }
                                        }
                                    } else {
                                        item(span = StaggeredGridItemSpan.FullLine) {
                                            Column {
                                                state.comments.forEach { comment ->
                                                    ProfileCommentItem(
                                                        comment = comment,
                                                        onCommentClick = onCommentClick,
                                                        onLongClick = { deletingComment = it }
                                                    )
                                                }
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 10.dp),
                                                    Alignment.Center
                                                ) {
                                                    Text("-到底了-", fontSize = 12.sp, color = getOnSurfaceSecondary())
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    items(state.favoritedPosts, key = { it.id }) { note ->
                                        PostCard(
                                            imageRes = note.imageRes,
                                            title = note.title,
                                            avatarRes = note.avatarRes,
                                            userName = note.userName,
                                            isLiked = note.isLiked,
                                            likeCount = note.likeCount.toString(),
                                            onCardClick = {
                                                if (note.imageUrl.startsWith("video:")) onVideoClick(note.id, note.imageUrl.removePrefix("video:"))
                                                else onPostClick(note.id)
                                            },
                                            imageUrl = note.imageUrl)
                                    }
                                }
                                3 -> {
                                    items(state.likedPosts, key = { it.id }) { note ->
                                        PostCard(
                                            imageRes = note.imageRes,
                                            title = note.title,
                                            avatarRes = note.avatarRes,
                                            userName = note.userName,
                                            isLiked = note.isLiked,
                                            likeCount = note.likeCount.toString(),
                                            onCardClick = {
                                                if (note.imageUrl.startsWith("video:")) onVideoClick(note.id, note.imageUrl.removePrefix("video:"))
                                                else onPostClick(note.id)
                                            },
                                            imageUrl = note.imageUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()) {
            Box(Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Gray.copy(alpha = 0.2f)))
            BottomBar(
                titles = listOf("首页", "阅读", "消息", "我的"),
                selectedIndex = bottomIndex,
                onTitleClick = { idx ->
                    bottomIndex = idx
                    if (idx == 0) onBottomTabClick(0)
                },
                fabIconRes = R.drawable.social_icons, onFabClick = onPublish
            )
            Spacer(Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(MaterialTheme.colorScheme.onPrimary))
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

@Composable
private fun ProfileHeader(
    userName: String,
    userXhsId: String,
    ipLocation: String,
    followCount: Int,
    fansCount: Int,
    likeCount: Int,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    noRipple: MutableInteractionSource
) {
    val onPri = Color.White
    Box(Modifier.fillMaxWidth()) {
        Image(
            painterResource(R.drawable.test2),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize().background(onPri.copy(alpha = 0.22f)))
        Column(Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 12.dp, end = 12.dp)) {
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_left), null, Modifier
                    .size(28.dp)
                    .clickable(noRipple, null) { onBack() },
                    tint = onPri.copy(alpha = 0.8f))
                Box(Modifier
                    .border(1.dp, onPri.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable(noRipple, null) { onEditProfile() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.edit_grey), null,
                            Modifier.size(16.dp), tint = onPri)
                        Spacer(Modifier.width(4.dp));
                        Text("编辑主页", fontSize = 13.sp, color = onPri)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier
                .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.test), null,
                    Modifier
                    .size(84.dp)
                    .clip(CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(userName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onPri)
                    Text("小红书号：$userXhsId", fontSize = 13.sp, color = onPri.copy(alpha = 0.7f))
                    Text("IP：$ipLocation", fontSize = 13.sp, color = onPri.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier
                .fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                StatItem("$followCount", "关注", onPri)
                StatItem("$fansCount", "粉丝", onPri)
                StatItem("$likeCount", "获赞", onPri)
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier
                .border(1.dp, onPri.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.female), null, Modifier.size(20.dp), tint = Color.Unspecified)
                    Spacer(Modifier.width(4.dp)); Text("24岁", fontSize = 13.sp, color = onPri)
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .border(1.dp, onPri.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.clock_white), null, Modifier.size(18.dp), tint = onPri)
                        Spacer(Modifier.width(6.dp)); Text("浏览记录", fontSize = 14.sp, color = onPri)
                    }
                    Row {
                        Text("看过的笔记", fontSize = 13.sp, color = onPri.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(25.dp))
        }
    }
}

@Composable
private fun StatItem(num: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 14.sp, color = color.copy(alpha = 0.8f))
    }
}

@Composable
private fun TabRow(selected: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("笔记", "评论", "收藏", "赞过")
    Row(Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 8.dp), Arrangement.spacedBy(15.dp)) {
        tabs.forEachIndexed { idx, name ->
            val isSel = selected == idx
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onTabSelected(idx) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (idx != 0) {
                        Icon(painterResource(R.drawable.lock_black), null, Modifier.size(12.dp), tint = Color.Gray)
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(name, fontSize = 15.sp, color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Gray)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent))
            }
        }
    }
}

@Composable
private fun DraftBox(draftImage: String, draftCount: Int, onDraftClick: () -> Unit = {}) {
    val firstUrl = draftImage.split(",").firstOrNull { it.isNotBlank() } ?: ""
    val isVideo = firstUrl.startsWith("video:")

    Box(Modifier
        .fillMaxWidth()
        .aspectRatio(2f)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFFF0F0F0))
        .clickable { onDraftClick() }) {
        when {
            isVideo -> {
                val videoPath = firstUrl.removePrefix("video:")
                val thumb = remember(videoPath) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(videoPath)
                        retriever.frameAtTime
                    } catch (e: Exception) {
                        null
                    } finally {
                        try { retriever.release() } catch (e: Exception) { }
                    }
                }
                if (thumb != null) {
                    Image(bitmap = thumb.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            firstUrl.isNotBlank() -> {
                AsyncImage(
                    model = ImageRequest
                        .Builder(LocalContext.current)
                        .data(firstUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
        }
        if (firstUrl.isNotBlank()) {
            Box(Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.12f)))
        }
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.inbox), null, Modifier.size(16.dp), tint = Color.Unspecified)
                Spacer(Modifier.width(4.dp))
                Text("草稿箱·$draftCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCommentItem(comment: UserComment, onCommentClick: (String, String) -> Unit, onLongClick: (String) -> Unit = {}) {
    val locale = LocalLocale.current.platformLocale
    val timeText = remember(comment.timestamp) {
        SimpleDateFormat("yyyy-MM-dd", locale).format(comment.timestamp)
    }
    val displayContent = remember(comment.content, comment.isReply) {
        if (comment.isReply) {
            comment.content.replaceFirst(Regex("^回复\\s*\\S+\\s*[:：]\\s*"), "")
        } else comment.content
    }
    Row(Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .combinedClickable(
            onClick = { onCommentClick(comment.postId, comment.commentId) },
            onLongClick = { onLongClick(comment.commentId) }
        )
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Image(
            painter = painterResource(R.drawable.test),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(Modifier
            .weight(1f)
            .padding(start = 8.dp)) {
            Text(comment.authorName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(displayContent, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
            if (comment.isReply && comment.parentUser.isNotBlank()) {
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(getOutline()))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "@${comment.parentUser}：${comment.parentContent}",
                        fontSize = 12.sp,
                        color = getOnSurfaceSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "来自笔记·${comment.postTitle}",
                fontSize = 12.sp,
                color = getOnSurfaceSecondary(),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onCommentClick(comment.postId, comment.commentId) }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$timeText · ${comment.ipLocation}", fontSize = 12.sp, color = getOnSurfaceTertiary())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.favorite_light),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = getOnSurfaceSecondary()
                    )
                    Text(
                        text = if (comment.likeCount > 0) comment.likeCount.toString() else "",
                        fontSize = 12.sp,
                        color = getOnSurfaceTertiary(),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}
