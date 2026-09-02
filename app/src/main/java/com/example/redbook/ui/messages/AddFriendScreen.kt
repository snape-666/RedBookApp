package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

/**
 * 添加好友页：输入用户名或小红书号搜索用户，右侧关注/已关注按钮。
 * 关注状态乐观更新并写入云端；各页面重新加载时会读云端最新关注状态。
 */
@Composable
fun AddFriendScreen(
    userUid: String = "",
    onBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AddFriendViewModel = viewModel(
        factory = AddFriendViewModelFactory(context.applicationContext as android.app.Application)
    )
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    // 300ms 防抖搜索
    LaunchedEffect(query, userUid) {
        val q = query.trim()
        if (q.isNotEmpty() && userUid.isNotBlank()) {
            kotlinx.coroutines.delay(300)
            viewModel.search(userUid, q)
        } else {
            viewModel.search(userUid, "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        MessageTopSearchBar(
            query = query,
            onQueryChange = { query = it },
            onCancel = { onBack() },
            placeholder = "添加好友"
        )

        if (query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (searching && results.isEmpty()) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
                    }
                }
                items(results, key = { it.uid }) { user ->
                    FriendResultRow(
                        user = user,
                        onFollowClick = { viewModel.toggleFollow(userUid, user.uid, !user.followed) },
                        onUserClick = { onUserClick(user.uid) }
                    )
                }
                if (!searching && results.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未找到相关用户", fontSize = 13.sp, color = getOnSurfaceTertiary())
                        }
                    }
                }
            }
        }
    }
}

/** 搜索结果项：左 48dp 头像 + 昵称/小红书号 + 右侧关注按钮 */
@Composable
private fun FriendResultRow(
    user: FriendSearchItem,
    onFollowClick: () -> Unit,
    onUserClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像 48dp，点击可进主页
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onUserClick() }
        ) {
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user.avatarUrl)
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
                text = user.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "小红书ID：${user.xhsId}",
                fontSize = 12.sp,
                color = getOnSurfaceTertiary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        FollowChip(followed = user.followed, onClick = onFollowClick)
    }
}

/** 关注按钮：宽度固定（72dp），文本居中，文字长短变化不影响按钮宽度 */
@Composable
private fun FollowChip(followed: Boolean, onClick: () -> Unit) {
    val borderColor = if (followed) getOutline() else MaterialTheme.colorScheme.primary
    val textColor = if (followed) getOutline() else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (followed) "已关注" else "关注",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}
