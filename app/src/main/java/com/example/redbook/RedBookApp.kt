package com.example.redbook

import android.app.Application
import com.example.redbook.notification.NotifHelper

class RedBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知渠道只需创建一次,应用启动即建
        NotifHelper.createChannels(this)
    }
}
