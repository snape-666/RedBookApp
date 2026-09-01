package com.example.redbook.ui.draft

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.redbook.data.model.Draft
import com.example.redbook.ui.component.VideoThumb
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DraftScreen(
    userUid: String,
    userXhsId: String,
    onBack: () -> Unit,
    onEditDraft: (Draft) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: DraftViewModel = viewModel(factory = DraftViewModelFactory(context.applicationContext as android.app.Application, userUid, userXhsId))
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var deletingDraft by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 36.dp, bottom = 8.dp)) {
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = "返回",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(28.dp)
                        .clickable { onBack() },
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "本地草稿",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (drafts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("暂无草稿", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(drafts, key = { it.draftId }) { draft ->
                        DraftCard(
                            title = draft.title,
                            imageUrl = draft.imageUrl,
                            createdAt = draft.createdAt,
                            onDelete = { deletingDraft = draft },
                            onCardClick = { onEditDraft(draft) }
                        )
                    }
                }
            }
        }
    }

    deletingDraft?.let { draft ->
        Dialog(onDismissRequest = { deletingDraft = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("删除草稿", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("确定删除该草稿吗？", fontSize = 14.sp, color = getOnSurfaceSecondary())
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { deletingDraft = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = getOnSurfaceSecondary())
                    }
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline()))
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable {
                            viewModel.deleteDraft(draft.draftId)
                            deletingDraft = null
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("确认", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    title: String,
    imageUrl: String,
    createdAt: Long,
    onDelete: () -> Unit,
    onCardClick: () -> Unit = {},
    imageRes: Int = R.drawable.test
) {
    val timeText = remember(createdAt) {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        now.timeInMillis = createdAt
        val year = now.get(Calendar.YEAR)
        val pattern = if (year == currentYear) "MM-dd" else "yyyy-MM-dd"
        SimpleDateFormat(pattern, Locale.getDefault()).format(now.time)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            val firstUrl = imageUrl.split(",").firstOrNull { it.isNotBlank() } ?: ""
            val isVideo = firstUrl.startsWith("video:")
            if (isVideo) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    val videoPath = firstUrl.removePrefix("video:")
                    VideoThumb(
                        videoUrl = videoPath,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        placeholder = imageRes
                    )
                    Icon(
                        painter = painterResource(R.drawable.video_fill),
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
                    contentDescription = "草稿图片",
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
                    .padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeText,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = getOnSurfaceTertiary(),
                    maxLines = 1
                )
                Icon(
                    painter = painterResource(R.drawable.trash),
                    contentDescription = "删除",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDelete() },
                    tint = Color.Unspecified
                )
            }
        }
    }
}
