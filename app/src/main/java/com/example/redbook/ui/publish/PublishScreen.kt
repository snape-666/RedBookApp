package com.example.redbook.ui.publish


import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

@Composable
fun PublishScreen(
    authorUid: String,
    authorXhsId: String,
    authorName: String,
    editDraft: com.example.redbook.data.model.Draft? = null,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember(authorUid, authorXhsId, authorName, editDraft?.draftId) {
        PublishViewModelBuilder.build(context.applicationContext as android.app.Application, authorUid, authorXhsId, authorName, editDraft)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.addImages(uris)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onPublished()
    }

    val borderColor = getOnSurfaceTertiary().copy(alpha = 0.1f)
    val fillColor = getOnSurfaceTertiary().copy(alpha = 0.05f)
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 36.dp, bottom = 16.dp)) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier.size(28.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            if (viewModel.isVideoMode && state.images.isNotEmpty()) {
                VideoPreview(state.images.first())
                Spacer(Modifier.height(16.dp))
            } else {
            // 图片区
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.images.forEach { uri ->
                    Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp))) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    }
                }
                if (state.images.size < 11) {
                    Box(
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .background(fillColor)
                            .clickable { imagePicker.launch("*/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.add_square), "添加", Modifier.size(32.dp), tint = getOutline())
                    }
                }
            }
            }

            Spacer(Modifier.height(16.dp))

            // 标题
            androidx.compose.foundation.text.BasicTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { inner ->
                    if (state.title.isEmpty()) Text("添加标题", fontSize = 18.sp, color = getOnSurfaceSecondary())
                    inner()
                }
            )

            Spacer(Modifier.height(12.dp))

            if (!viewModel.isVideoMode) {
                androidx.compose.foundation.text.BasicTextField(
                    value = state.content,
                    onValueChange = viewModel::updateContent,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (state.content.isEmpty()) Text("分享你的想法...", fontSize = 15.sp, color = getOnSurfaceSecondary())
                        inner()
                    }
                )
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }

        // 底部发布栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f).height(44.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .clickable { viewModel.saveDraft() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.isSaving && state.savedAsDraft) "保存中..." else "存草稿",
                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier.weight(2f).height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { viewModel.publish() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.isSaving && !state.savedAsDraft) "发布中..." else "发布笔记",
                    fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium
                )
            }
        }
    }

}

object PublishViewModelBuilder {
    fun build(
        app: android.app.Application, uid: String, xhsId: String, name: String,
        editDraft: com.example.redbook.data.model.Draft? = null
    ): PublishViewModel = PublishViewModel(app, uid, xhsId, name, editDraft)
}

@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val (vw, vh) = remember(uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            w to h
        } catch (e: Exception) {
            0 to 0
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }
    val ratio = if (vw > 0 && vh > 0) vw.toFloat() / vh else 16f / 9f

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = maxW * 4f / 3f
        val naturalH = maxW / ratio
        val displayH = naturalH.coerceAtMost(maxH)
        val displayW = displayH * ratio
        val density = LocalDensity.current
        Box(
            Modifier
                .align(Alignment.Center)
                .size(with(density) { displayW.toDp() }, with(density) { displayH.toDp() })
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, _, _ -> false }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
