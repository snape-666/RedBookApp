package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

@Composable
fun FollowersScreen(
    userUid: String,
    onBack: () -> Unit = {},
    onSendMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: FollowersViewModel = viewModel(factory = FollowersViewModelFactory(context.applicationContext as android.app.Application, userUid))
    val followers by viewModel.followers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

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
                    text = "新增关注",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))

        when {
            loading -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            followers.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = "暂无新增关注", fontSize = 14.sp, color = getOnSurfaceSecondary())
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(followers, key = { it.uid }) { item ->
                    FollowerRow(
                        item = item,
                        onFollowClick = { viewModel.toggleFollow(item.uid, true) },
                        onUnfollowClick = { viewModel.toggleFollow(item.uid, false) },
                        onSendMessage = { onSendMessage(item.uid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowerRow(
    item: FollowerItem,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(CircleShape)) {
            if (item.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.avatarUrl)
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
                text = item.userName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "关注了你",
                    fontSize = 13.sp,
                    color = getOnSurfaceSecondary()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFollowTime(item.followTime),
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary()
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (item.followed) {
            Box(
                modifier = Modifier
                    .border(1.dp, getOutline(), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onUnfollowClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "发私信",
                    fontSize = 13.sp,
                    color = getOnSurfaceSecondary()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onFollowClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "关注",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatFollowTime(time: Long): String {
    if (time <= 0) return ""
    val diff = System.currentTimeMillis() - time
    val days = diff / 86_400_000L
    return when {
        days >= 30 -> "${days / 30}个月前"
        days >= 7 -> "${days / 7}周前"
        days >= 1 -> "${days}天前"
        else -> "刚刚"
    }
}
