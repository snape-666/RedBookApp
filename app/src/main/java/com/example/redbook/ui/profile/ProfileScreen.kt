package com.example.redbook.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.model.Note
import com.example.redbook.ui.component.BottomBar
import com.example.redbook.ui.component.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
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
    onPublish: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(context.applicationContext as android.app.Application, userXhsId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onPri = Color.White
    val noRipple = remember { MutableInteractionSource() }
    var bottomIndex by remember { mutableIntStateOf(3) }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.test2), null, Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f), contentScale = ContentScale.Crop)
        Box(Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f)
            .background(onPri.copy(alpha = 0.22f)))

        Column(Modifier
            .fillMaxSize()
            .padding(top = 36.dp)) {
            Row(Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_left), null, Modifier
                    .size(28.dp)
                    .clickable(noRipple, null) { onBack() }, tint = onPri.copy(alpha = 0.8f))
                Box(Modifier
                    .border(1.dp, onPri.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable(noRipple, null) { onEditProfile() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.edit_grey), null, Modifier.size(16.dp), tint = onPri)
                        Spacer(Modifier.width(4.dp)); Text("编辑主页", fontSize = 13.sp, color = onPri)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.test), null, Modifier
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
                .fillMaxWidth()
                .padding(horizontal = 12.dp), Arrangement.spacedBy(12.dp)) {
                StatItem("$followCount", "关注", onPri)
                StatItem("$fansCount", "粉丝", onPri)
                StatItem("$likeCount", "获赞", onPri)
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier
                .padding(horizontal = 12.dp)
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
                    .padding(start = 12.dp)
                    .border(1.dp, onPri.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.clock_white), null, Modifier.size(18.dp), tint = onPri)
                        Spacer(Modifier.width(6.dp)); Text("浏览记录", fontSize = 14.sp, color = onPri)
                    }
                    Text("看过的笔记", fontSize = 12.sp, color = onPri.copy(alpha = 0.7f), modifier = Modifier.padding(start = 24.dp, top = 2.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = 10.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface)) {
                ProfileTabs(
                    state = state,
                    onRefresh = { viewModel.load() },
                    onPostClick = onPostClick,
                    isRefreshing = false
                )
            }

            Box(Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Gray.copy(alpha = 0.2f)))
            BottomBar(
                titles = listOf("首页", "阅读", "消息", "我的"),
                selectedIndex = 3, onTitleClick = onBottomTabClick,
                fabIconRes = R.drawable.social_icons, onFabClick = onPublish
            )
            Spacer(Modifier.height(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTabs(
    state: ProfileViewModel.ProfileUiState,
    onRefresh: () -> Unit,
    onPostClick: (String) -> Unit,
    isRefreshing: Boolean
) {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("笔记", "评论", "收藏", "赞过")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp), Arrangement.spacedBy(15.dp)) {
            tabs.forEachIndexed { idx, name ->
                val isSel = selected == idx
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selected = idx }) {
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

        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when (selected) {
                0 -> NotesGrid(state.posts, state.draftCount, state.latestPostImage, onPostClick)
                1 -> CommentsTab(state.commentCount)
                2 -> NotesGrid(state.favoritedPosts, 0, "", onPostClick)
                3 -> NotesGrid(state.likedPosts, 0, "", onPostClick)
            }
        }
    }
}

@Composable
private fun NotesGrid(posts: List<Note>, draftCount: Int, draftImage: String, onPostClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(10.dp))) {
                    if (draftImage.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest
                                .Builder(LocalContext.current)
                                .data(draftImage).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F0)))
                    }
                    Box(Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.TopStart)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                        ),
                        contentAlignment = Alignment.CenterStart) {
                        Row(
                            Modifier
                                .padding(horizontal=8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.inbox), null,
                                Modifier.size(16.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(4.dp));
                            Text("草稿箱·$draftCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        items(posts, key = { it.id }) { note ->
            PostCard(imageRes = note.imageRes, title = note.title, avatarRes = note.avatarRes,
                userName = note.userName, isLiked = note.isLiked, likeCount = note.likeCount.toString(),
                onCardClick = { onPostClick(note.id) }, imageUrl = note.imageUrl)
        }
    }
}

@Composable
private fun CommentsTab(commentCount: Int) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(if (commentCount > 0) "$commentCount 条评论" else "暂无评论", color = Color.Gray, fontSize = 14.sp)
    }
}
