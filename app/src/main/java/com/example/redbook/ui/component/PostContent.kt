package com.example.redbook.ui.detail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.redbook.data.model.PostDetail
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale

@Composable
fun PostContent(
    post: PostDetail,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {

        val urls = post.imageUrl.split(",").filter { it.isNotBlank() }
        // 视频笔记：image_url 可能带 video: 前缀，直接展示视频占位
        val videoUrls = urls.filter { it.startsWith("video:") }
        if (videoUrls.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(com.example.redbook.R.drawable.video_fill),
                    contentDescription = "视频",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (urls.size > 1) {
            val pagerState = rememberPagerState(pageCount = { urls.size })
            val ratios = remember { mutableStateMapOf<Int, Float>() }
            val currentRatio = ratios[pagerState.currentPage] ?: (3f / 4f)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().aspectRatio(currentRatio)
            ) { index ->
                AdaptiveImage(urls[index], onRatioChanged = { ratios[index] = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(urls.size) { i ->
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        } else if (urls.size == 1) {
            AdaptiveImage(urls[0])
        } else {
            Image(
                painter = painterResource(id = post.imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentScale = ContentScale.Crop
            )
        }

        Text(text = post.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 10.dp, end = 10.dp))

        Text(text = post.content, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp, start = 10.dp, end = 10.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Text(text = SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(post.publishTime),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = " · ${post.ipLocation}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${post.viewCount}次浏览", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdaptiveImage(
    url: String,
    defaultRatio: Float = 3f / 4f,
    onRatioChanged: (Float) -> Unit = {}
) {
    var ratio by remember(url) { mutableStateOf(defaultRatio) }
    var failed by remember(url) { mutableStateOf(false) }
    if (failed || url.isBlank() || url.startsWith("video:")) {
        Image(
            painter = painterResource(com.example.redbook.R.drawable.test),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            contentScale = ContentScale.Crop
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = null,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val s = state.painter.intrinsicSize
                    if (s.width > 0f && s.height > 0f) {
                        val r = s.width / s.height
                        if (r > 0f) { ratio = r; onRatioChanged(r) }
                    }
                } else if (state is AsyncImagePainter.State.Error) {
                    failed = true
                }
            },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
        )
    }
}
