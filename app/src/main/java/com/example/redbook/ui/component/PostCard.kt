package com.example.redbook.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceTertiary

@Composable
fun PostCard(
    imageRes: Int,
    title: String,
    avatarRes: Int,
    userName: String,
    isLiked: Boolean,
    likeCount : String,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    imageUrl: String = ""
) {
    val likeIconRes = if (isLiked) R.drawable.favorite_fill else R.drawable.favorite_light
    Card(
        modifier = modifier
            .width(216.dp)
            .clickable { onCardClick() }
            .clip(RoundedCornerShape(10.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column (modifier=Modifier.fillMaxWidth()){
            val firstUrl = imageUrl.split(",").firstOrNull { it.isNotBlank() } ?: ""
            val isVideo = firstUrl.startsWith("video:")
            if (isVideo) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    val videoPath = firstUrl.removePrefix("video:")
                    val thumb = remember(videoPath) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(videoPath)
                            retriever.frameAtTime
                        } finally { retriever.release() }
                    }
                    if (thumb != null) {
                        Image(bitmap = thumb.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                            contentScale = ContentScale.Crop)
                    } else {
                        Image(painter = painterResource(id = imageRes), contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                            contentScale = ContentScale.Crop)
                    }
                    Icon(
                        painter = painterResource(R.drawable.add_square),
                        contentDescription = "视频",
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                        tint = Color.White
                    )
                }
            } else if (firstUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(firstUrl)
                        .crossfade(300).placeholder(imageRes).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "Post image",
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                    contentScale = ContentScale.Crop
                )
            }


            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }


            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)

            ) {

                Row(
                    modifier = Modifier.weight(1f)

                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = userName,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 14.sp,
                        color = getOnSurfaceTertiary(),
                        maxLines = 1
                    )
                }


                Row(modifier= Modifier, verticalAlignment = Alignment.CenterVertically ) {
                    Icon(
                        painter = painterResource(id = likeIconRes),
                        contentDescription = "Action icon",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified

                    )
                    Text(
                        text=likeCount,
                        fontSize = 14.sp,
                        color = getOnSurfaceTertiary(),
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@Preview(name = "Light Mode", showBackground = true)
@Composable
fun PreviewPostCardLight() {
    MaterialTheme {
        PostCard(
            imageRes = R.drawable.test,
            title = "这就是一款超级好看的红书风格卡片",
            avatarRes = R.drawable.test,
            userName = "小红薯",
            isLiked = true,
            likeCount = "2800",
            modifier = Modifier,

        )
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewPostCardDark() {
    MaterialTheme {
        PostCard(
            imageRes = R.drawable.test,
            title = "暗色模式下的卡片依然清晰",
            avatarRes = R.drawable.test,
            userName = "小红薯",
            isLiked = false,
            likeCount = "2.3万",
            onCardClick = {},

        )
    }
}