package com.example.redbook.ui.profile

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.shadow
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
import com.example.redbook.data.model.Note
import com.example.redbook.data.model.UserComment
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.component.BottomBar
import com.example.redbook.ui.component.PostCard
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import com.example.redbook.ui.theme.surfaceVariantLight
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch

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
    gender: String = "",
    birthday: String = "",
    avatarUrl: String = "",
    backgroundUrl: String = "",
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onBottomTabClick: (Int) -> Unit,
    onPostClick: (String) -> Unit,
    onVideoClick: (String, String) -> Unit = { _, _ -> },
    onCommentClick: (String, String) -> Unit = { _, _ -> },
    onPublish: () -> Unit = {},
    onDraftClick: () -> Unit = {},
    onBrowseClick: () -> Unit = {},
    account: String = "",
    email: String = "",
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNotification: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    unreadMessageCount: Int = 0,
    // 对方主页模式：viewerUid 为当前登录者，isSelf=false 时展示对方主页
    viewerUid: String = "",
    onSendMessage: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val isSelf = viewerUid.isBlank() || userUid == viewerUid
    // 用 key 区分自己主页/对方主页，避免两个页面复用同一个 ProfileViewModel 实例
    val viewModel: ProfileViewModel = viewModel(
        key = if (isSelf) "self_profile" else "user_profile_$userUid",
        factory = ProfileViewModelFactory(
            context.applicationContext as android.app.Application,
            userUid, userXhsId, if (isSelf) "" else viewerUid
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val noRipple = remember { MutableInteractionSource() }
    var bottomIndex by remember { mutableIntStateOf(3) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var deletingComment by remember { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var changePasswordVisible by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showRemarkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        if (!isSelf) viewModel.loadUserProfile(userUid)
    }

    // 实时刷新：收到关注/点赞/评论通知时刷新粉丝、获赞等统计
    val realtimeRepo = remember {
        com.example.redbook.data.repository.RealtimeRepository(context.applicationContext as android.app.Application)
    }
    DisposableEffect(userUid) {
        val listener = object : com.example.redbook.data.repository.RealtimeRepository.RealtimeListener {
            override fun onNotification(record: org.json.JSONObject) {
                val type = record.optString("type", "")
                if (type == "follow" || type == "like" || type == "favorite" || type == "comment" || type == "reply") {
                    viewModel.refresh()
                }
            }
            override fun onMessage(record: org.json.JSONObject) { }
            override fun onStatus(connected: Boolean) { }
        }
        realtimeRepo.addListener(listener)
        onDispose { realtimeRepo.removeListener(listener) }
    }

    // 回到页面时重新刷新统计
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, userUid) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (isSelf) {
                    ProfileHeader(
                        userName = userName,
                        userXhsId = userXhsId,
                        ipLocation = ipLocation,
                        followCount = if (state.followCount > 0) state.followCount else followCount,
                        fansCount = if (state.fansCount > 0) state.fansCount else fansCount,
                        likeCount = if (state.likeCount > 0) state.likeCount else likeCount,
                        gender = gender,
                        birthday = birthday,
                        avatarUrl = avatarUrl,
                        backgroundUrl = backgroundUrl,
                        onBack = onBack,
                        onMenuClick = { showDrawer = true },
                        onEditProfile = onEditProfile,
                        onBrowseClick = onBrowseClick,
                        noRipple = noRipple
                    )
                } else {
                    OtherUserHeader(
                        userProfile = userProfile,
                        onBack = onBack,
                        onFollowClick = { viewModel.toggleFollowTarget() },
                        onActionClick = { showActionSheet = true },
                        onSendMessage = {
                            onSendMessage(userProfile.uid, userProfile.userName.ifBlank { userProfile.uid }, userProfile.avatarUrl)
                        },
                        expanded = showActionSheet
                    )
                }

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

                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                                            imageUrl = note.imageUrl,
                                            avatarUrl = note.avatarUrl)
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
                                            imageUrl = note.imageUrl,
                                            avatarUrl = note.avatarUrl)
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
                                            imageUrl = note.imageUrl,
                                            avatarUrl = note.avatarUrl)
                                    }
                            }
                        }
                    }
                }
            }
        }
        }

        if (isSelf) {
            Column(Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()) {
                Box(Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Gray.copy(alpha = 0.2f)))
                BottomBar(
                    titles = listOf("首页", "视频", "消息", "我的"),
                    selectedIndex = bottomIndex,
                    onTitleClick = { idx ->
                        bottomIndex = idx
                        if (idx == 0) onBottomTabClick(0)
                        if (idx == 1) onBottomTabClick(1)
                        if (idx == 2) onNavigateToMessages()
                    },
                    fabIconRes = R.drawable.social_icons, onFabClick = onPublish,
                    unreadCounts = listOf(0, 0, unreadMessageCount, 0)
                )
                Spacer(Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.onPrimary))
            }
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
                        Text("删除评论", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
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

        if (showDrawer) {
            Box(Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { showDrawer = false })
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp)
            ) {
                DrawerContent(
                    draftCount = state.draftCount,
                    account = account,
                    isDarkTheme = isDarkTheme,
                    onDraftClick = { showDrawer = false; onDraftClick() },
                    onBrowseClick = { showDrawer = false; onBrowseClick() },
                    onChangePassword = { showDrawer = false; changePasswordVisible = true },
                    onNotification = { showDrawer = false; onNotification() },
                    onLogout = { showDrawer = false; onLogout() },
                    onToggleDarkTheme = onToggleDarkTheme
                )
            }
        }

        if (changePasswordVisible) {
            ChangePasswordDialog(
                email = email,
                onDismiss = { changePasswordVisible = false }
            )
        }

        // 对方主页：底部操作弹窗（设置备注名 / 取消关注）
        if (showActionSheet && !isSelf) {
            ActionSheet(
                onDismiss = { showActionSheet = false },
                onRemarkClick = {
                    showActionSheet = false
                    showRemarkDialog = true
                },
                onUnfollowClick = {
                    showActionSheet = false
                    viewModel.unfollowTarget()
                }
            )
        }

        // 对方主页：添加备注弹窗
        if (showRemarkDialog && !isSelf) {
            RemarkDialog(
                initialRemark = userProfile.remark,
                onDismiss = { showRemarkDialog = false },
                onConfirm = { remark ->
                    showRemarkDialog = false
                    viewModel.setRemark(remark)
                }
            )
        }
    }
}

/** 对方主页头部：顶部只留返回按钮，信息区与我的主页一致，操作区为关注row + 发私信 */
@Composable
private fun OtherUserHeader(
    userProfile: ProfileViewModel.UserProfileState,
    onBack: () -> Unit,
    onFollowClick: () -> Unit,
    onActionClick: () -> Unit,
    onSendMessage: () -> Unit,
    expanded: Boolean = false
) {
    val onPri = Color.White
    val ageText = remember(userProfile.birthday) { calculateAge(userProfile.birthday) }
    val followText = when {
        userProfile.iFollow && userProfile.heFollowsMe -> "互相关注"
        userProfile.iFollow -> "已关注"
        else -> "关注"
    }
    Box(Modifier.fillMaxWidth()) {
        // 主页背景图
        if (userProfile.backgroundUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(userProfile.backgroundUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter)
        } else {
            Image(
                painterResource(R.drawable.test2),
                null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter)
        }
        Box(Modifier.matchParentSize().background(onPri.copy(alpha = 0.22f)))
        Column(Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 12.dp, end = 12.dp)) {
            // 顶部 row：只有返回按钮
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_left), null, Modifier
                    .size(28.dp)
                    .clickable { onBack() },
                    tint = onPri.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier
                .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                if (userProfile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(userProfile.avatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } else {
                    Image(painterResource(R.drawable.test), null,
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = userProfile.remark.ifBlank { userProfile.userName },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = onPri)
                    Text("小红书号：${userProfile.xhsId.ifBlank { "00000000000" }}", fontSize = 13.sp, color = onPri.copy(alpha = 0.95f))
                    Text("IP：${userProfile.ipLocation}", fontSize = 13.sp, color = onPri.copy(alpha = 0.95f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 统计：关注 / 粉丝 / 获赞
            Row(Modifier
                .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatItem("${userProfile.followCount}", "关注", onPri)
                StatItem("${userProfile.fansCount}", "粉丝", onPri)
                StatItem("${userProfile.likeCount}", "获赞", onPri)
            }

            Spacer(Modifier.height(12.dp))

            // 性别年龄标签
            val genderIcon = when (userProfile.gender) {
                "男" -> R.drawable.male
                "女" -> R.drawable.female
                else -> null
            }
            if (genderIcon != null || ageText > 0) {
                Row(
                    Modifier
                        .border(1.dp, onPri.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (genderIcon != null) {
                        Icon(painterResource(genderIcon), null, Modifier.size(20.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (ageText > 0) {
                        Text("${ageText}岁", fontSize = 13.sp, color = onPri)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 大 Row：[关注 + expand 小 row] + [发私信]，两个 row 高度一致
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // 关注 row：未关注点击直接关注，已关注/互相关注点击弹出底部弹窗；text+icon 居中
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (userProfile.iFollow) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable {
                            if (userProfile.iFollow) onActionClick() else onFollowClick()
                        }
                        .padding(horizontal = 5.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = followText,
                        fontSize = 14.sp,
                        color =MaterialTheme.colorScheme.surface
                    )
                    Spacer(Modifier.width(4.dp))
                    if (userProfile.iFollow ) {
                        Icon(
                            painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint =  MaterialTheme.colorScheme.surface
                        )
                    }
                }
                // 发私信
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha =0.3f ))
                        .clickable { onSendMessage() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "发私信",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
            }

            Spacer(Modifier.height(25.dp))
        }
    }
}

/** 底部操作弹窗：覆盖屏幕底部 20% 高度，带遮罩 */
@Composable
private fun ActionSheet(
    onDismiss: () -> Unit,
    onRemarkClick: () -> Unit,
    onUnfollowClick: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() }
        )
        // 底部弹窗：屏幕高度 20%，占满宽度贴底，不挤压底部内容
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.2f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center

        ) {
            // 第一行：设置备注名
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(10.dp),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onRemarkClick() }
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设置备注名",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = getOutline()
                )
            }
            Spacer(Modifier.height(15.dp))
            // 第二行：取消关注
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(10.dp),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onUnfollowClick() }
                    .padding(horizontal =15.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消关注",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 添加备注弹窗：屏幕中间 */
@Composable
private fun RemarkDialog(
    initialRemark: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var remarkInput by remember { mutableStateOf(initialRemark) }
    Dialog(onDismissRequest = onDismiss) {
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
                Text("添加备注", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text("最多不超过8个字", fontSize = 13.sp, color = getOnSurfaceSecondary())
                Spacer(Modifier.height(12.dp))
                // 输入框 row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(getOutline().copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = remarkInput,
                        onValueChange = { remarkInput = it.take(8) },
                        modifier = Modifier.weight(1f),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                    if (remarkInput.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.close_ring_fill),
                            contentDescription = "清空",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { remarkInput = "" },
                            tint = getOnSurfaceSecondary()
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = getOnSurfaceSecondary())
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline()))
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onConfirm(remarkInput.trim()) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("添加", color = MaterialTheme.colorScheme.primary)
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
    gender: String = "",
    birthday: String = "",
    avatarUrl: String = "",
    backgroundUrl: String = "",
    onBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    onEditProfile: () -> Unit,
    onBrowseClick: () -> Unit = {},
    noRipple: MutableInteractionSource
) {
    val onPri = Color.White
    val ageText = remember(birthday) { calculateAge(birthday) }
    Box(Modifier.fillMaxWidth()) {
        if (backgroundUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(backgroundUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter)
        } else {
            Image(
                painterResource(R.drawable.test2),
                null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter)
        }
        Box(Modifier.matchParentSize().background(onPri.copy(alpha = 0.22f)))
        Column(Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 12.dp, end = 12.dp)) {
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.menu_white), null, Modifier
                    .size(28.dp)
                    .clickable(noRipple, null) { onMenuClick() },
                    tint = onPri.copy(alpha = 0.8f))
                Box(Modifier
                    .border(1.dp, onPri.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable(noRipple, null) { onEditProfile() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.edit_white), null,
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
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } else {
                    Image(painterResource(R.drawable.test), null,
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(userName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onPri)
                    Text("小红书号：$userXhsId", fontSize = 13.sp, color = onPri.copy(alpha = 0.95f))
                    Text("IP：$ipLocation", fontSize = 13.sp, color = onPri.copy(alpha = 0.95f))
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
                    val genderIcon = when (gender) {
                        "男" -> R.drawable.male
                        "女" -> R.drawable.female
                        else -> null
                    }
                    if (genderIcon != null) {
                        Icon(painterResource(genderIcon), null, Modifier.size(20.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(4.dp))
                    }
                    val ageTextVal = if (ageText > 0) "${ageText}岁" else ""
                    if (ageTextVal.isNotBlank()) {
                        Text(ageTextVal, fontSize = 13.sp, color = onPri)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .border(1.dp, onPri.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .background(onPri.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable(noRipple, null) { onBrowseClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.clock_white), null, Modifier.size(18.dp), tint = onPri)
                        Spacer(Modifier.width(6.dp));
                        Text("浏览记录", fontSize = 14.sp, color = onPri)
                    }
                    Row {
                        Text("看过的笔记", fontSize = 13.sp, color = onPri.copy(alpha = 0.9f))
                    }
                }
            }

            Spacer(Modifier.height(25.dp))
        }
    }
}

private fun calculateAge(birthday: String): Int {
    if (birthday.isBlank() || birthday == "不显示") return -1
    return try {
        val parts = birthday.split("-")
        if (parts.size < 3) return -1
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val now = java.util.Calendar.getInstance()
        val cy = now.get(java.util.Calendar.YEAR)
        val cm = now.get(java.util.Calendar.MONTH) + 1
        val cd = now.get(java.util.Calendar.DAY_OF_MONTH)
        var age = cy - year
        if (cm < month || (cm == month && cd < day)) age--
        age
    } catch (e: Exception) { -1 }
}

@Composable
private fun StatItem(num: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 14.sp, color = color.copy(alpha = 0.9f))
    }
}

@Composable
private fun TabRow(selected: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("笔记", "评论", "收藏", "赞过")
    Row(Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .padding(start = 12.dp, end = 12.dp, top =10.dp, bottom = 8.dp), Arrangement.spacedBy(15.dp)) {
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

@Composable
private fun DrawerContent(
    draftCount: Int,
    account: String,
    isDarkTheme: Boolean,
    onDraftClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onChangePassword: () -> Unit,
    onNotification: () -> Unit,
    onLogout: () -> Unit,
    onToggleDarkTheme: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth()
                .clickable { onDraftClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.inbox), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(5.dp))
                Text("草稿箱", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("$draftCount", fontSize = 14.sp, color = getOnSurfaceSecondary())
            }
            Row(Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth()
                .clickable { onBrowseClick() },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.clock_black), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(5.dp))
                Text("浏览记录", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.user_cicrle), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(5.dp))
                Text("账号", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(account, fontSize = 14.sp, color = getOnSurfaceSecondary())
            }
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clickable { onChangePassword() },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.lock_black), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(5.dp))
                Text("更改密码", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOnSurfaceTertiary())
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { onNotification() },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.bell), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(5.dp))
                Text("通知设置", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOnSurfaceTertiary())
            }
        }

        Spacer(Modifier.weight(1f))
        Box(Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .clickable { onLogout() },
            contentAlignment = Alignment.Center) {
            Text("退出登录", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier
            .align(Alignment.End)
            .padding(bottom = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleDarkTheme() },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(getOnSurfaceTertiary().copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(if (isDarkTheme) R.drawable.sun else R.drawable.moon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isDarkTheme) "日间" else "夜间",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChangePasswordDialog(email: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var emailInput by remember { mutableStateOf(email) }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)) {
            Text("修改密码", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (step == 0) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("邮箱") }
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        scope.launch {
                            repository.requestResetCode(emailInput).onSuccess {
                                step = 1
                                error = null
                            }.onFailure { error = it.message }
                        }
                    },
                    contentAlignment = Alignment.Center) {
                    Text("发送验证码", color = Color.White, fontSize = 15.sp)
                }
            } else {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("验证码") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("新密码") }
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        scope.launch {
                            repository.verifyCodeAndReset(emailInput, codeInput, passwordInput).onSuccess {
                                Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.onFailure { error = it.message }
                        }
                    },
                    contentAlignment = Alignment.Center) {
                    Text("确定", color = Color.White, fontSize = 15.sp)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
