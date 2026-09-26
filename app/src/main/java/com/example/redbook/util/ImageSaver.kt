package com.example.redbook.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 把消息里的图片保存到系统相册。
 *
 *  - 复用 Coil 的缓存拿 bitmap（图片已经在消息里显示过，通常命中缓存，不用重新走一遍不稳定的网络）；
 *  - Android 10(API 29)+ 走 MediaStore，无需存储权限；
 *  - 更低版本写公共 Pictures 目录，需要 WRITE_EXTERNAL_STORAGE(调用方先申请)。
 */
object ImageSaver {

    /** 保存图片到相册，返回是否成功。须在协程里调用。 */
    suspend fun save(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> null
            }
            if (bitmap == null) {
                Log.e("ImageSaver", "no bitmap for url=$url")
                return@withContext false
            }
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
            val ok = saveToGallery(context, bytes)
            Log.d("ImageSaver", "save result=$ok size=${bytes.size}")
            ok
        } catch (e: Exception) {
            Log.e("ImageSaver", "save exception: ${e.message}")
            false
        }
    }

    private fun saveToGallery(context: Context, bytes: ByteArray): Boolean {
        val name = "chat_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RedBook")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            try {
                val out = resolver.openOutputStream(uri) ?: return false
                out.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "RedBook"
            )
            if (!dir.exists() && !dir.mkdirs()) return false
            val file = File(dir, name)
            file.writeBytes(bytes)
            true
        }
    }
}
