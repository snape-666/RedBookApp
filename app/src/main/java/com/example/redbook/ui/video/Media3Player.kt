package com.example.redbook.ui.video

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/**
 * 全局视频播放器工厂：
 * - SimpleCache 磁盘缓存（512MB LRU），已看过的视频二次播放秒开
 * - CacheDataSource 透明代理缓存，起播后边播边写盘
 * - 更积极的缓冲策略：缓冲约 1.2s 即可起播，减少转圈等待
 */
object Media3PlayerFactory {
    private const val CACHE_MAX_BYTES = 512L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var mediaSourceFactory: DefaultMediaSourceFactory? = null

    private fun ensure(context: Context) {
        if (cache != null && mediaSourceFactory != null) return
        synchronized(this) {
            if (cache == null) {
                val c = SimpleCache(
                    File(context.cacheDir, "video_cache"),
                    LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES)
                )
                val upstream = DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                )
                val cacheFactory = CacheDataSource.Factory()
                    .setCache(c)
                    .setUpstreamDataSourceFactory(upstream)
                cache = c
                mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory)
            }
        }
    }

    fun create(context: Context): ExoPlayer {
        val app = context.applicationContext
        ensure(app)
        val loadControl = DefaultLoadControl.Builder()
            // 缓冲约 1.2s 就起播，降低首帧等待；重缓冲放宽到 3s
            .setBufferDurationsMs(20_000, 30_000, 1_200, 3_000)
            .build()
        return ExoPlayer.Builder(app)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory!!)
            .build()
    }
}

/**
 * 记住一个与 [url] 绑定的 ExoPlayer：
 * - 创建时立即 prepare()（开始缓冲），由调用方决定何时 play()
 * - 组合销毁时自动 release()，磁盘缓存保留供下次秒开
 * - 本地路径（以 / 开头）自动转成 file Uri
 */
@Composable
fun rememberVideoPlayer(url: String): ExoPlayer? {
    val context = LocalContext.current.applicationContext
    val player = remember(url, context) {
        runCatching {
            Media3PlayerFactory.create(context).apply {
                val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        }.getOrNull()
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    return player
}