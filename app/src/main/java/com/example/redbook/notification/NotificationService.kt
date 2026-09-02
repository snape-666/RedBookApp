package com.example.redbook.notification

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 通知前台服务:
 *  - 登录成功后由 MainActivity 启动,登出时停止
 *  - 持有 RealtimeRepository 的 WebSocket 连接,后台/息屏也能收到事件
 *  - 唯一负责弹系统通知的地方(Activity 只更新角标,不弹)
 *  - 账号切换时自动重连;启动时补发“离线期间未读”的互动/私信通知
 */
class NotificationService : Service() {

    private var repository: RealtimeRepository? = null
    private val authRepository by lazy { SupabaseAuthRepository(application) }
    private var scope: CoroutineScope? = null
    /** 会话内缓存的对方资料(昵称+头像),避免每条私信都查一次 */
    private val peerInfoCache = HashMap<String, PeerInfo>()
    private var uid: String = ""
    private val seenEvents = ArrayDeque<String>()

    private data class PeerInfo(val name: String, val avatar: String)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cachedUid = NotifPrefs.getCachedLoginUid(this)
        if (cachedUid.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        startAsForeground()

        if (uid != cachedUid) {
            // 账号切换(如 A 退出登 B):换 uid 重建连接,并补发离线期间该账号的未读
            uid = cachedUid
            repository?.disconnect()
            repository = RealtimeRepository(application)
            repository?.connect(uid, listener)
            scope?.launch { catchUpOffline(uid) }
        } else if (repository == null) {
            // 进程被杀后 START_STICKY 重启:重建连接 + 补发漏掉的通知
            repository = RealtimeRepository(application)
            repository?.connect(uid, listener)
            scope?.launch { catchUpOffline(uid) }
        }
        return START_STICKY
    }

    private val listener = object : RealtimeRepository.RealtimeListener {
        override fun onNotification(record: JSONObject) {
            handleNotificationRecord(record)
        }

        override fun onMessage(record: JSONObject) {
            handleMessageRecord(record)
        }

        override fun onStatus(connected: Boolean) { }
    }

    private fun startAsForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotifHelper.SERVICE_NOTIF_ID,
                    NotifHelper.buildServiceNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    NotifHelper.SERVICE_NOTIF_ID,
                    NotifHelper.buildServiceNotification(this)
                )
            }
        } catch (_: Exception) {
            // 权限被关或系统限制时退回普通前台通知,保证服务仍能跑
            try {
                startForeground(
                    NotifHelper.SERVICE_NOTIF_ID,
                    NotifHelper.buildServiceNotification(this)
                )
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        repository?.disconnect()
        repository = null
        peerInfoCache.clear()
        try {
            scope?.cancel()
            scope = null
        } catch (_: Exception) { }
        try {
            (getSystemService(NotificationManager::class.java))?.cancel(NotifHelper.SERVICE_NOTIF_ID)
        } catch (_: Exception) { }
        super.onDestroy()
    }

    // ---------------- 离线补发 ----------------

    /**
     * 补发自上次已通知水印之后仍未读的互动与私信。
     * WebSocket 只在连接建立后推增量,离线/换号期间的事件收不到,必须靠这里补。
     *  - 无系统通知权限:整段不推进水印,授权后重新补发,不丢;
     *  - 有权限:扫描完把水印推进到本次终点(被分类开关拦截的视为用户不想要,不重试)。
     */
    private suspend fun catchUpOffline(currentUid: String) {
        val repo = repository ?: return
        if (currentUid.isBlank()) return
        val canPost = NotifHelper.areNotificationsEnabled(this)
        val watermark = NotifPrefs.loadWatermark(currentUid, this)
        val from = if (watermark > 0L) watermark else System.currentTimeMillis() - 24 * 3600_000L
        val maxTs = System.currentTimeMillis()
        try {
            val notifs = repo.getNotificationsSince(currentUid, from)
            for (i in 0 until notifs.length()) {
                handleNotificationRecord(notifs.getJSONObject(i))
            }
            val msgs = repo.getUnreadMessagesSince(currentUid, from)
            for (i in 0 until msgs.length()) {
                handleMessageRecordSync(msgs.getJSONObject(i))
            }
            if (canPost) {
                NotifPrefs.saveWatermark(currentUid, maxTs, this)
            }
        } catch (_: Exception) { }
    }

    // ---------------- 实时事件 ----------------

    /**
     * 处理一条互动通知(实时与补发共用);返回是否弹出了通知。
     * 实时弹出成功后会把水印推进到事件时间,避免重启后把它当离线事件重复补发。
     */
    private fun handleNotificationRecord(record: JSONObject): Boolean {
        val notifId = record.optString("notif_id", "")
        if (notifId.isBlank() || !markSeen(notifId)) return false
        val type = record.optString("type", "")
        val settings = NotifPrefs.load(uid, this)
        if (!settings.receiveEnabled) return false

        val actorName = record.optString("actor_name", "").ifBlank { "小红书用户" }
        val actorAvatar = record.optString("actor_avatar", "")
        val postId = record.optString("post_id", "")
        val commentId = record.optString("comment_id", "")
        val commentContent = record.optString("comment_content", "")
        val eventTs = record.optLong("created_at", 0L)
        val text: String
        val pendingType: String
        when (type) {
            "like" -> {
                if (!settings.likeFavEnabled) return false
                text = "赞了你的笔记"
                pendingType = "like"
            }
            "favorite" -> {
                if (!settings.likeFavEnabled) return false
                text = "收藏了你的笔记"
                pendingType = "favorite"
            }
            "follow" -> {
                if (!settings.followEnabled) return false
                text = "关注了你"
                pendingType = "follow"
            }
            "comment" -> {
                if (!settings.commentEnabled) return false
                text = "评论了你的笔记${if (commentContent.isNotBlank()) ":$commentContent" else ""}"
                pendingType = "comment"
            }
            "reply" -> {
                if (!settings.commentEnabled) return false
                text = "回复了你的笔记${if (commentContent.isNotBlank()) ":$commentContent" else ""}"
                pendingType = "reply"
            }
            else -> return false
        }
        val n = PendingNotif(
            type = pendingType,
            actorUid = record.optString("actor_uid", ""),
            actorName = actorName,
            actorAvatar = actorAvatar,
            postId = postId,
            commentId = commentId
        )
        if (n.isBlank) return false
        val posted = NotifHelper.show(this, notifId, n, actorName, text)
        if (posted && eventTs > 0) bumpWatermark(eventTs)
        return posted
    }

    /** 实时私信:资料缓存命中直接弹;未命中异步查完再弹。弹出后推进水印 */
    private fun handleMessageRecord(record: JSONObject) {
        val messageId = record.optString("message_id", "")
        if (messageId.isBlank() || !markSeen(messageId)) return
        val senderUid = record.optString("sender_uid", "")
        val receiverUid = record.optString("receiver_uid", "")
        if (senderUid.isBlank() || receiverUid != uid || senderUid == uid) return

        val settings = NotifPrefs.load(uid, this)
        if (!settings.receiveEnabled || !settings.dmEnabled) return

        val content = record.optString("content", "")
        val mediaUrl = record.optString("media_url", "")
        val text = if (content.isNotBlank()) content else if (mediaUrl.isNotBlank()) "[图片]" else "发来了一条私信"
        val conversationId = record.optString("conversation_id", "")
        val eventTs = record.optLong("created_at", 0L)

        val cached = peerInfoCache[senderUid]
        if (cached != null) {
            val posted = showDmNotification(messageId, senderUid, conversationId, text, cached)
            if (posted && eventTs > 0) bumpWatermark(eventTs)
            return
        }
        scope?.launch {
            val info = resolvePeerInfo(senderUid)
            peerInfoCache[senderUid] = info
            val posted = showDmNotification(messageId, senderUid, conversationId, text, info)
            if (posted && eventTs > 0) bumpWatermark(eventTs)
        }
    }

    /** 补发私信:同步查资料后弹(补发跑在 IO 协程,查完再弹,水印推进不遗漏) */
    private suspend fun handleMessageRecordSync(record: JSONObject) {
        val messageId = record.optString("message_id", "")
        if (messageId.isBlank() || !markSeen(messageId)) return
        val senderUid = record.optString("sender_uid", "")
        val receiverUid = record.optString("receiver_uid", "")
        if (senderUid.isBlank() || receiverUid != uid || senderUid == uid) return

        val settings = NotifPrefs.load(uid, this)
        if (!settings.receiveEnabled || !settings.dmEnabled) return

        val content = record.optString("content", "")
        val mediaUrl = record.optString("media_url", "")
        val text = if (content.isNotBlank()) content else if (mediaUrl.isNotBlank()) "[图片]" else "发来了一条私信"
        val conversationId = record.optString("conversation_id", "")

        val cached = peerInfoCache[senderUid]
        val info = cached ?: run {
            val resolved = resolvePeerInfo(senderUid)
            peerInfoCache[senderUid] = resolved
            resolved
        }
        showDmNotification(messageId, senderUid, conversationId, text, info)
    }

    /** 同步查对方资料(昵称+头像;调用方需处于协程上下文) */
    private suspend fun resolvePeerInfo(senderUid: String): PeerInfo {
        var name = "小红书用户"
        var avatar = ""
        try {
            authRepository.getUserByUid(senderUid)?.let {
                val nick = it.optString("nickname", "").ifBlank { it.optString("account", "") }
                if (nick.isNotBlank()) name = nick
                avatar = it.optString("avatar_url", "")
            }
        } catch (_: Exception) { }
        return PeerInfo(name, avatar)
    }

    private fun showDmNotification(
        messageId: String,
        senderUid: String,
        conversationId: String,
        text: String,
        info: PeerInfo
    ): Boolean {
        val n = PendingNotif(
            type = "dm",
            actorUid = senderUid,
            actorName = info.name,
            actorAvatar = info.avatar,
            conversationId = conversationId,
            contentText = text
        )
        if (n.isBlank) return false
        return NotifHelper.show(this@NotificationService, messageId, n, info.name, text)
    }

    /** 单调推进该账号水印到事件时间 */
    private fun bumpWatermark(ts: Long) {
        if (ts <= 0) return
        val cur = NotifPrefs.loadWatermark(uid, this)
        if (ts > cur) NotifPrefs.saveWatermark(uid, ts, this)
    }

    /** 轻量去重:最近事件 id 超过阈值则丢弃旧记录 */
    private fun markSeen(eventId: String): Boolean {
        if (seenEvents.contains(eventId)) return false
        if (seenEvents.size >= 100) seenEvents.removeFirst()
        seenEvents.addLast(eventId)
        return true
    }
}
