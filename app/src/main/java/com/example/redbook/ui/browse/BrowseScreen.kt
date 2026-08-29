package com.example.redbook.ui.browse

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.redbook.data.model.Note
import com.example.redbook.ui.component.VideoThumb
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

@Composable
fun BrowseScreen(
    userUid: String,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    onVideoClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: BrowseViewModel = viewModel(factory = BrowseViewModelFactory(context.applicationContext as android.app.Application, userUid))
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    var deletingPost by remember { mutableStateOf<Note?>(null) }
    var isManaging by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isAllSelected = posts.isNotEmpty() && selectedIds.size == posts.size

    LaunchedEffect(Unit) { viewModel.load() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onPrimary)
                .padding(top = 36.dp, bottom = 8.dp)) {
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = "返回",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(28.dp)
                        .clickable { onBack() },
                    tint = Color.Unspecified
                )
                Text(
                    text = "浏览记录",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isManaging) {
                    Text(
                        text = "完成",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .clickable {
                                isManaging = false
                                selectedIds = emptySet()
                            },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .border(0.3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp))
                            .clickable { isManaging = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.menu), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text("管理", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (posts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("暂无浏览记录", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = if (isManaging) 80.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(posts, key = { it.id }) { note ->
                        BrowseCard(
                            note = note,
                            isManaging = isManaging,
                            isSelected = selectedIds.contains(note.id),
                            onCardClick = {
                                if (!isManaging) {
                                    if (note.imageUrl.startsWith("video:")) {
                                        onVideoClick(note.id, note.imageUrl.removePrefix("video:"))
                                    } else {
                                        onPostClick(note.id)
                                    }
                                }
                            },
                            onDelete = { deletingPost = note },
                            onSelectClick = {
                                selectedIds = if (selectedIds.contains(note.id)) selectedIds - note.id else selectedIds + note.id
                            }
                        )
                    }
                }
            }
        }

        if (isManaging) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.clickable {
                        selectedIds = if (isAllSelected) emptySet() else posts.map { it.id }.toSet()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAllSelected) {
                        Icon(painterResource(R.drawable.choose), null, Modifier.size(24.dp), tint = Color.Unspecified)
                    } else {
                        Box(Modifier
                            .size(20.dp)
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("全选", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (selectedIds.isNotEmpty()) {
                    Box(Modifier
                        .padding(horizontal = 12.dp)
                        .height(16.dp)
                        .width(1.dp)
                        .background(getOutline()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已选中", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("${selectedIds.size}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("篇笔记", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary else getOutline().copy(alpha = 0.3f))
                        .clickable {
                            if (selectedIds.isNotEmpty()) {
                                viewModel.deleteBrowses(selectedIds)
                                selectedIds = emptySet()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "删除",
                        fontSize = 13.sp,
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.surface else getOnSurfaceSecondary()
                    )
                }
            }
        }
    }

    deletingPost?.let { note ->
        Dialog(onDismissRequest = { deletingPost = null }) {
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
                    Text("删除浏览记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("确定删除该浏览记录吗？", fontSize = 14.sp, color = getOnSurfaceSecondary())
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline()))
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { deletingPost = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = getOnSurfaceSecondary())
                    }
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline()))
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable {
                            viewModel.deleteBrowse(note.id)
                            deletingPost = null
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseCard(
    note: Note,
    isManaging: Boolean,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onDelete: () -> Unit,
    onSelectClick: () -> Unit,
    imageRes: Int = R.drawable.test
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { onCardClick() },
                onLongClick = { onDelete() }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            val firstUrl = note.imageUrl.split(",").firstOrNull { it.isNotBlank() } ?: ""
            val isVideo = firstUrl.startsWith("video:")
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                if (isVideo) {
                    val videoPath = firstUrl.removePrefix("video:")
                    VideoThumb(
                        videoUrl = videoPath,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        placeholder = imageRes
                    )
                    if (!isManaging) {
                        Icon(
                            painter = painterResource(R.drawable.video_fill),
                            contentDescription = "视频",
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                            tint = Color.Unspecified
                        )
                    }
                } else if (firstUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(firstUrl)
                            .crossfade(300).placeholder(imageRes).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "浏览记录图片",
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                if (isManaging) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clickable { onSelectClick() }
                    ) {
                        if (isSelected) {
                            Icon(painterResource(R.drawable.choose), null, Modifier.fillMaxSize(), tint = Color.Unspecified)
                        } else {
                            Box(Modifier
                                .fillMaxSize()
                                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = note.title,
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
                Row(modifier = Modifier.weight(1f)) {
                    if (note.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(note.avatarUrl).crossfade(true).build(),
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = note.avatarRes),
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = note.userName,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 14.sp,
                        color = getOnSurfaceTertiary(),
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(if (note.isLiked) R.drawable.favorite_fill else R.drawable.favorite_light),
                        contentDescription = "点赞",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Text(
                        text = note.likeCount.toString(),
                        fontSize = 14.sp,
                        color = getOnSurfaceTertiary(),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
