package com.example.redbook.ui.component

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoThumb(
    videoUrl: String,
    modifier: Modifier = Modifier,
    placeholder: Int
) {
    val thumb = produceState<Bitmap?>(initialValue = null, videoUrl) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (videoUrl.startsWith("http")) {
                    retriever.setDataSource(videoUrl, HashMap<String, String>())
                } else {
                    retriever.setDataSource(videoUrl)
                }
                retriever.frameAtTime
            } catch (e: Exception) {
                null
            } finally {
                try { retriever.release() } catch (e: Exception) { }
            }
        }
    }
    val bitmap = thumb.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(placeholder),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
