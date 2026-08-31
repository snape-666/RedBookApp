package com.example.redbook.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.R
import com.example.redbook.data.model.Comment
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: Comment,
    onAvatarClick: (String) -> Unit,
    onUserNameClick: (String) -> Unit,
    onReplyClick: (String,String) -> Unit,
    onLikeClick: (String) -> Unit,
    onLongClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .combinedClickable(
            onClick = {},
            onLongClick = { onLongClick(comment.id) }
        )) {
        // 一级评论
        Row {
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(comment.avatarUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick(comment.userId) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = comment.avatarRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick(comment.userId) },
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.userName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { onUserNameClick(comment.userId) }
                    )
                    if (comment.isAuthor) {
                        Spacer(modifier = Modifier.width(5.dp))
                        AuthorTag()

                    }
                }
                Text(
                    modifier = Modifier.padding(vertical = 2.dp),
                    text = comment.content,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (comment.images.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(comment.images) { uri ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(comment.timestamp),
                        fontSize = 12.sp,
                        color = getOnSurfaceTertiary()
                    )
                    Text(
                        text = " · ${comment.ipLocation}",
                        fontSize = 12.sp,
                        color = getOnSurfaceTertiary()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "回复",
                        fontSize = 12.sp,
                        color = getOnSurfaceTertiary(),
                        modifier = Modifier
                            .clickable {
                                onReplyClick(comment.id,comment.userName)
                            }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onLikeClick(comment.id) }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (comment.isLiked) R.drawable.favorite_fill
                                else R.drawable.favorite_light
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (comment.isLiked) MaterialTheme.colorScheme.primary
                            else getOnSurfaceSecondary()
                        )
                        Text(
                            text = if (comment.likeCount > 0) comment.likeCount.toString() else "",
                            fontSize = 12.sp,
                            color = if (comment.isLiked) MaterialTheme.colorScheme.primary
                            else getOnSurfaceTertiary(),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }

        // 二级回复列表
        if (comment.replies.isNotEmpty()) {
            val displayReplies = if (isExpanded) comment.replies else comment.replies.take(1)
            displayReplies.forEach { reply ->
                ReplyItem(
                    reply = reply,
                    onAvatarClick = onAvatarClick,
                    onUserNameClick = onUserNameClick,
                    onReplyClick = { onReplyClick(reply.id, reply.userName) },
                    onLikeClick = { onLikeClick(reply.id) },
                    onLongClick = { onLongClick(reply.id) },
                    modifier = Modifier.padding(start = 40.dp)
                )
            }

            if (comment.replies.size > 1) {
                Text(
                    text = if (isExpanded) "收起回复" else "展开 ${comment.replies.size} 条回复",
                    fontSize = 13.sp,
                    color = getOnSurfaceSecondary(),
                    modifier = Modifier
                        .padding(start = 40.dp, top = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isExpanded = !isExpanded }
                )
            }
        }
    }
}
