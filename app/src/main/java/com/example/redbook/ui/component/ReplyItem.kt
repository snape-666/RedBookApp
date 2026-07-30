package com.example.redbook.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import com.example.redbook.data.model.Reply
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ReplyItem(
    reply: Reply,
    onAvatarClick: () -> Unit,
    onUserNameClick: () -> Unit,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Image(
            painter = painterResource(id = reply.avatarRes),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onAvatarClick() },
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onUserNameClick() }
                )
                if (reply.isAuthor) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AuthorTag()
                }
            }
            Text(
                text = reply.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (reply.images.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(reply.images) { uri ->
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
                    text = SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(reply.timestamp),
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary()
                )
                Text(
                    text = " · ${reply.ipLocation}",
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary()
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "回复",
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary(),
                    modifier = Modifier.clickable { onReplyClick() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        painter = painterResource(
                            if (reply.isLiked) R.drawable.favorite_fill
                            else R.drawable.favorite_light
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (reply.isLiked) MaterialTheme.colorScheme.primary
                        else getOnSurfaceSecondary()
                    )
                    Text(
                        text = if (reply.likeCount > 0) reply.likeCount.toString() else "",
                        fontSize = 12.sp,
                        color = if (reply.isLiked) MaterialTheme.colorScheme.primary
                        else getOnSurfaceTertiary(),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}