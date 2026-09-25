package com.example.redbook

import android.app.Application
import com.example.redbook.data.local.AuthSession
import com.example.redbook.notification.NotifHelper

class RedBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知渠道只需创建一次,应用启动即建
        NotifHelper.createChannels(this)
        // 预热登录令牌(access_token/refresh_token)，后续请求据此通过 RLS
        AuthSession.load(this)
    }
}
