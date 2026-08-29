package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.component.BottomBar
import com.example.redbook.ui.theme.getBlueBackground
import com.example.redbook.ui.theme.getBlueFill
import com.example.redbook.ui.theme.getGreenBackground
import com.example.redbook.ui.theme.getGreenFill
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getRedBackground
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class Conversation(
    val userName: String,
    val avatarUrl: String,
    val lastMessage: String,
    val lastTime: Long
)

private val mockConversations = listOf(
    Conversation("小红薯", "", "在吗？这条笔记不错", System.currentTimeMillis() - 1000L * 60 * 5),
    Conversation("用户A", "", "你好，我想问下这条笔记的拍摄方法", System.currentTimeMillis() - 1000L * 60 * 60 * 3),
    Conversation("用户B", "", "哈哈哈太逗了", System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 2),
    Conversation("用户C", "", "收藏了你的笔记，谢谢分享", System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30 * 6)
)

@Composable
fun MessagesScreen(
    onBottomTabClick: (Int) -> Unit = {},
    onPublish: () -> Unit = {},
    onLikeFavoriteClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onConversationClick: (String, String) -> Unit = { _, _ -> }
) {
    var bottomIndex by remember { mutableIntStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().height(24.dp).background(MaterialTheme.colorScheme.surface))

        MessagesTopBar()

        ReactionGroupRow(
            onLikeFavoriteClick = onLikeFavoriteClick,
            onCommentClick = onCommentClick,
            onFollowClick = onFollowClick
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 5.dp)
        ) {
            items(mockConversations, key = { it.userName }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.userName, conversation.avatarUrl) },
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = 0.2f)))
            BottomBar(
                titles = listOf("首页", "阅读", "消息", "我的"),
                selectedIndex = bottomIndex,
                onTitleClick = { idx ->
                    bottomIndex = idx
                    onBottomTabClick(idx)
                },
                fabIconRes = R.drawable.social_icons,
                onFabClick = onPublish
            )
            Spacer(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surface))
        }
    }
}

@Composable
private fun MessagesTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp)
    ) {
        Text(
            text = "消息",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "搜索",
                modifier = Modifier.size(24.dp),
                tint = getOnSurfaceTertiary()
            )
            Spacer(modifier = Modifier.width(5.dp))
            Icon(
                painter = painterResource(id = R.drawable.user_add),
                contentDescription = "添加好友",
                modifier = Modifier.size(24.dp),
                tint = getOnSurfaceTertiary()
            )
        }
    }
}

@Composable
private fun ReactionGroupRow(
    onLikeFavoriteClick: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GroupItem(
            bgColor = getRedBackground(),
            iconRes = R.drawable.favorite_fill,
            shadowColor = MaterialTheme.colorScheme.primary,
            label = "赞和收藏",
            onClick = onLikeFavoriteClick
        )
        GroupItem(
            bgColor = getBlueBackground(),
            iconRes = R.drawable.user_alt_fill,
            shadowColor = getBlueFill(),
            label = "新增关注",
            onClick = onFollowClick
        )
        GroupItem(
            bgColor = getGreenBackground(),
            iconRes = R.drawable.chat_fill,
            shadowColor = getGreenFill(),
            label = "新增评论",
            onClick = onCommentClick
        )
    }
}

@Composable
private fun GroupItem(bgColor: Color, iconRes: Int, shadowColor: Color, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(50),
                        clip = false,
                        ambientColor = shadowColor.copy(alpha = 0.25f),
                        spotColor = shadowColor.copy(alpha = 0.25f)
                    ),
                tint = Color.Unspecified
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConversationItem(conversation: Conversation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(CircleShape)) {
            if (conversation.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(conversation.avatarUrl)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.userName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatConversationDate(conversation.lastTime),
                    fontSize = 12.sp,
                    color = getOnSurfaceSecondary()
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            val lastMessage = if (conversation.lastMessage.length > 18) {
                conversation.lastMessage.take(18) + "…"
            } else {
                conversation.lastMessage
            }
            Text(
                text = lastMessage,
                fontSize = 13.sp,
                color = getOnSurfaceSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatConversationDate(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val pattern = if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
}
