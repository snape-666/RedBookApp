package com.example.redbook.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.redbook.R

/**
 * 系统通知构建/发送。所有通知只由前台服务发出,避免双弹。
 */
object NotifHelper {

    const val CHANNEL_INTERACTIONS = "redbook_interactions" // 赞收藏/评论/关注
    const val CHANNEL_MESSAGES = "redbook_messages"         // 私信

    /** 常驻前台服务通知 id(低优先级) */
    const val SERVICE_NOTIF_ID = 1001
    /** 互动通知 id 起点(防止与消息通知 id 冲突) */
    const val INTERACT_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INTERACTIONS,
                "互动通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "赞和收藏、新增关注、评论通知"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "私信通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "新私信消息"
            }
        )
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun buildServiceNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_INTERACTIONS)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("正在接收互动与私信通知")
            .setSmallIcon(R.drawable.bell)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    /**
     * 弹一条通知。标题=对方用户名,正文=动作文本。
     * @param eventId 事件唯一 id(notif_id/message_id),用作通知 id 与去重依据
     * @return 是否真正弹出了通知(权限/系统开关满足时)
     */
    fun show(
        context: Context,
        eventId: String,
        n: PendingNotif,
        title: String,
        text: String
    ): Boolean {
        val channel = when (n.type) {
            "dm" -> CHANNEL_MESSAGES
            else -> CHANNEL_INTERACTIONS
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.bell)
            .setAutoCancel(true)
            .setContentIntent(NotifClickRouter.buildContentIntent(context, n))
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        return try {
            nm.notify(notificationId(eventId), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, eventId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(eventId))
        } catch (_: Exception) { }
    }

    /** 稳定映射事件 id -> 通知 id,避免重复通知互相覆盖 */
    fun notificationId(eventId: String): Int =
        INTERACT_BASE + (eventId.hashCode() and 0x7fffffff) % 1000
}
