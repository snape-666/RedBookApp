package com.example.redbook.ui.video

import android.annotation.SuppressLint
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.redbook.R
import androidx.core.net.toUri

@SuppressLint("DefaultLocale")
@Composable
fun VideoDetailScreen(
    videoUrl: String,
    title: String,
    authorName: String,
    authorAvatar: Int,
    isFollowed: Boolean,
    likeCount: Int,
    favoriteCount: Int,
    commentCount: Int,
    onBack: () -> Unit,
    onFollowClick: (Boolean) -> Unit,
    onLikeClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp, start = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.arrow_left), "返回",
                Modifier.size(28.dp).clickable(remember { MutableInteractionSource() }, null) { onBack() }, tint = Color.White)
            Spacer(Modifier.weight(1f))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx ->
                VideoView(ctx).apply {
                    try {
                        if (videoUrl.startsWith("/")) setVideoPath(videoUrl)
                        else setVideoURI(videoUrl.toUri())
                    } catch (e: Exception) { setVideoURI(videoUrl.toUri()) }
                    start()
                    setOnPreparedListener { mp ->
                        duration = mp.duration / 1000
                        mp.isLooping = true
                    }
                    setOnErrorListener { _, _, _ -> false }
                }
            }, modifier = Modifier.fillMaxWidth())
        }

        if (duration > 0) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(if (duration > 0) currentPosition.toFloat() / duration else 0f).height(2.dp).background(Color.White))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(authorAvatar), null, Modifier.size(32.dp).clip(CircleShape), tint = Color.Unspecified)
            Spacer(Modifier.width(8.dp))
            Text(authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (duration > 0) Text("${duration / 60}:${String.format("%02d", duration % 60)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 2)

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            ActionItem(R.drawable.favorite_light, likeCount.toString()) { onLikeClick() }
            ActionItem(R.drawable.star, favoriteCount.toString()) { onFavoriteClick() }
            ActionItem(R.drawable.chat, commentCount.toString()) { }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionItem(iconRes: Int, count: String, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, Modifier.size(24.dp), tint = Color.Unspecified)
        Spacer(Modifier.width(4.dp))
        Text(count, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}
