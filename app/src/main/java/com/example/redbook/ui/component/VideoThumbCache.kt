package com.example.redbook.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 视频封面帧缓存：
 * - 内存 LRU（20MB bitmap）：列表滚动回来看过的卡片即时显示
 * - 磁盘 JPEG（cacheDir/video_covers）：同一视频二次进入不再联网取帧
 * - 取帧后统一降到 720px 宽，减小内存与磁盘占用
 */
object VideoThumbCache {
    private const val MEM_MAX_BYTES = 20 * 1024 * 1024
    private const val TARGET_WIDTH = 720

    private val memCache = object : LruCache<String, Bitmap>(MEM_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(context: Context, url: String): Bitmap? {
        memCache.get(url)?.let { return it }

        val file = coverFile(context, url)
        if (file.exists()) {
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (bmp != null && !bmp.isRecycled) {
                memCache.put(url, bmp)
                return bmp
            }
        }

        val bmp = runCatching { extract(url) }.getOrNull() ?: return null
        val scaled = scaleToWidth(bmp)
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        memCache.put(url, scaled)
        return scaled
    }

    private fun extract(url: String): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            if (url.startsWith("http")) {
                retriever.setDataSource(url, HashMap<String, String>())
            } else {
                retriever.setDataSource(url)
            }
            return retriever.frameAtTime
                ?: throw IllegalStateException("no frame extracted")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleToWidth(src: Bitmap): Bitmap {
        if (src.width <= TARGET_WIDTH) return src
        val h = (src.height.toLong() * TARGET_WIDTH / src.width).toInt()
        val scaled = Bitmap.createScaledBitmap(src, TARGET_WIDTH, h, true)
        if (scaled !== src) src.recycle()
        return scaled
    }

    private fun coverFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "video_covers")
        return File(dir, "${md5(url)}.jpg")
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}