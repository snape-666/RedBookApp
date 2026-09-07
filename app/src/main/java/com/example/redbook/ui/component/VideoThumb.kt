package com.example.redbook.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoThumb(
    videoUrl: String,
    modifier: Modifier = Modifier,
    placeholder: Int
) {
    val appContext = LocalContext.current.applicationContext
    // 走 VideoThumbCache：内存 LRU + 磁盘缓存，滚动回来/二次进入无需重新联网取帧
    val thumb = produceState<Bitmap?>(initialValue = null, videoUrl) {
        value = withContext(Dispatchers.IO) { VideoThumbCache.get(appContext, videoUrl) }
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