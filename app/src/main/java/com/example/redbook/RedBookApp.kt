package com.example.redbook

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.example.redbook.data.local.AuthSession
import com.example.redbook.notification.NotifHelper

class RedBookApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // 通知渠道只需创建一次,应用启动即建
        NotifHelper.createChannels(this)
        // 预热登录令牌(access_token/refresh_token)，后续请求据此通过 RLS
        AuthSession.load(this)
    }

    // 忽略服务端 Cache-Control：Supabase Storage 的公共图片常不带有效缓存头，
    // 否则每次进聊天都要重新走网络下载，已看过的图也秒变“加载中”。
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
}
