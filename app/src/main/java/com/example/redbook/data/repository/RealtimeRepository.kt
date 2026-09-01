package com.example.redbook.data.repository

import android.app.Application
import com.android.volley.Request.Method.DELETE
import com.android.volley.Request.Method.GET
import com.android.volley.Request.Method.PATCH
import com.android.volley.Request.Method.POST
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

/**
 * 实时通知 + 私信仓库：
 *  - REST 查询/写入 notifications / conversations / messages
 *  - OkHttp WebSocket 订阅 Supabase Realtime，推送增量事件
 */
class RealtimeRepository(private val app: Application) {

    private val requestQueue by lazy { Volley.newRequestQueue(app) }
    private val timeoutMs = 15_000L

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    // ---------------- 通知（赞/收藏/评论/回复/关注） ----------------

    data class UnreadCounts(
        val likesFavs: Int,
        val follows: Int,
        val comments: Int,
        val total: Int
    )

    suspend fun getUnreadCounts(uid: String): UnreadCounts {
        if (uid.isBlank()) return UnreadCounts(0, 0, 0, 0)
        return withContext(Dispatchers.IO) {
            try {
                val resp = queryRest("notifications", "select=type&recipient_uid=eq.$uid&is_read=eq.false")
                val arr = resp.optJSONArray("users") ?: resp.optJSONArray("notifications") ?: JSONArray()
                var likesFavs = 0
                var follows = 0
                var comments = 0
                for (i in 0 until arr.length()) {
                    when (arr.getJSONObject(i).optString("type", "")) {
                        "like", "favorite" -> likesFavs++
                        "follow" -> follows++
                        "comment", "reply" -> comments++
                    }
                }
                UnreadCounts(likesFavs, follows, comments, arr.length())
            } catch (e: Exception) {
                UnreadCounts(0, 0, 0, 0)
            }
        }
    }

    suspend fun getNotifications(uid: String): JSONArray {
        val resp = queryRest("notifications", "select=*&recipient_uid=eq.$uid&order=created_at.desc&limit=100")
        return resp.optJSONArray("users") ?: resp.optJSONArray("notifications") ?: JSONArray()
    }

    suspend fun markNotificationsRead(uid: String, types: List<String>? = null) {
        if (uid.isBlank()) return
        val body = JSONObject().apply { put("is_read", true) }
        val filter = if (types.isNullOrEmpty()) {
            "recipient_uid=eq.$uid&is_read=eq.false"
        } else {
            val or = types.joinToString(",") { "type.eq.$it" }
            "recipient_uid=eq.$uid&is_read=eq.false&or=($or)"
        }
        patchRest("notifications", filter, body)
    }

    /** 写入一条互动通知（调用方保证 actor != recipient） */
    suspend fun insertNotification(
        notifId: String,
        recipientUid: String,
        actorUid: String,
        actorName: String,
        actorAvatar: String = "",
        type: String,
        postId: String = "",
        postTitle: String = "",
        commentId: String = "",
        commentContent: String = ""
    ) {
        if (recipientUid.isBlank() || actorUid.isBlank() || recipientUid == actorUid) return
        val body = JSONObject().apply {
            put("notif_id", notifId)
            put("recipient_uid", recipientUid)
            put("actor_uid", actorUid)
            put("actor_name", actorName)
            put("actor_avatar", actorAvatar)
            put("type", type)
            put("post_id", postId)
            put("post_title", postTitle)
            put("comment_id", commentId)
            put("comment_content", commentContent)
            put("created_at", System.currentTimeMillis())
            put("is_read", false)
        }
        postRest("/rest/v1/notifications", body)
    }

    // ---------------- 私信 ----------------

    /** 获取或创建会话，返回 conversation_id */
    suspend fun getOrCreateConversation(uidA: String, uidB: String): String {
        if (uidA.isBlank() || uidB.isBlank() || uidA == uidB) return ""
        val (a, b) = if (uidA < uidB) uidA to uidB else uidB to uidA
        val convId = "c_${a}_$b"
        return withContext(Dispatchers.IO) {
            try {
                val resp = queryRest("conversations", "select=conversation_id&conversation_id=eq.$convId&limit=1")
                val arr = resp.optJSONArray("users") ?: resp.optJSONArray("conversations") ?: JSONArray()
                if (arr.length() > 0) {
                    arr.getJSONObject(0).optString("conversation_id", convId)
                } else {
                    val body = JSONObject().apply {
                        put("conversation_id", convId)
                        put("user_a_uid", a)
                        put("user_b_uid", b)
                        put("last_message", "")
                        put("last_time", 0L)
                    }
                    try { postRest("/rest/v1/conversations", body) } catch (_: Exception) { }
                    convId
                }
            } catch (e: Exception) { convId }
        }
    }

    suspend fun sendMessage(messageId: String, conversationId: String, senderUid: String, receiverUid: String, content: String, mediaUrl: String = "") {
        val body = JSONObject().apply {
            put("message_id", messageId)
            put("conversation_id", conversationId)
            put("sender_uid", senderUid)
            put("receiver_uid", receiverUid)
            put("content", content)
            // media_url 仅在有媒体时写入，避免旧表没有该字段导致纯文本消息发送失败
            if (mediaUrl.isNotBlank()) put("media_url", mediaUrl)
            put("created_at", System.currentTimeMillis())
            put("is_read", false)
        }
        postRest("/rest/v1/messages", body)
        // 更新会话 last_message / last_time
        try {
            val resp = queryRest("conversations", "select=last_message&conversation_id=eq.$conversationId&limit=1")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("conversations") ?: JSONArray()
            val lastMsg = if (arr.length() > 0) arr.getJSONObject(0).optString("last_message", "") else ""
            val display = if (content.isNotBlank()) content else if (mediaUrl.isNotBlank()) "[图片]" else ""
            val updated = if (lastMsg == display) lastMsg else display
            patchRest("conversations", "conversation_id=eq.$conversationId", JSONObject().apply {
                put("last_message", updated)
                put("last_time", System.currentTimeMillis())
            })
        } catch (_: Exception) { }
    }

    suspend fun getMessages(conversationId: String): JSONArray {
        val resp = queryRest("messages", "select=*&conversation_id=eq.$conversationId&order=created_at.asc&limit=200")
        return resp.optJSONArray("users") ?: resp.optJSONArray("messages") ?: JSONArray()
    }

    suspend fun markConversationRead(conversationId: String, uid: String) {
        patchRest("messages", "conversation_id=eq.$conversationId&receiver_uid=eq.$uid&is_read=eq.false", JSONObject().apply { put("is_read", true) })
    }

    suspend fun getUnreadConversationCount(uid: String): Int {
        if (uid.isBlank()) return 0
        return withContext(Dispatchers.IO) {
            try {
                val resp = queryRest("messages", "select=message_id&receiver_uid=eq.$uid&is_read=eq.false")
                val arr = resp.optJSONArray("users") ?: resp.optJSONArray("messages") ?: JSONArray()
                arr.length()
            } catch (e: Exception) { 0 }
        }
    }

    /** 会话列表（对方昵称/头像 + lastMessage + 未读数），按 last_time 倒序 */
    suspend fun getConversations(uid: String): JSONArray {
        if (uid.isBlank()) return JSONArray()
        return withContext(Dispatchers.IO) {
            try {
                val resp = queryRest("conversations", "select=*&or=(user_a_uid.eq.$uid,user_b_uid.eq.$uid)&order=last_time.desc")
                val arr = resp.optJSONArray("users") ?: resp.optJSONArray("conversations") ?: JSONArray()
                if (arr.length() == 0) return@withContext JSONArray()

                val result = JSONArray()
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val userA = c.optString("user_a_uid", "")
                    val userB = c.optString("user_b_uid", "")
                    // 跳过脏数据：自己对自己的会话
                    if (userA.isBlank() || userB.isBlank() || userA == userB) continue
                    val peerUid = if (userA == uid) userB else userA
                    val convId = c.optString("conversation_id", "")
                    val lastMessage = c.optString("last_message", "")
                    val lastTime = c.optLong("last_time", 0L)

                    // 未读数
                    var unread = 0
                    try {
                        val mresp = queryRest("messages", "select=message_id&conversation_id=eq.$convId&receiver_uid=eq.$uid&is_read=eq.false")
                        val marr = mresp.optJSONArray("users") ?: mresp.optJSONArray("messages") ?: JSONArray()
                        unread = marr.length()
                    } catch (_: Exception) { }

                    val peer = JSONObject().apply {
                        put("conversation_id", convId)
                        put("peer_uid", peerUid)
                        put("peer_name", "")
                        put("peer_avatar", "")
                        put("last_message", lastMessage)
                        put("last_time", lastTime)
                        put("unread_count", unread)
                    }
                    // 查对方用户资料
                    try {
                        val uresp = queryRest("users", "select=nickname,avatar_url&uid=eq.$peerUid&limit=1")
                        val uarr = uresp.optJSONArray("users") ?: JSONArray()
                        if (uarr.length() > 0) {
                            val u = uarr.getJSONObject(0)
                            peer.put("peer_name", u.optString("nickname", ""))
                            peer.put("peer_avatar", u.optString("avatar_url", ""))
                        }
                    } catch (_: Exception) { }
                    result.put(peer)
                }
                result
            } catch (e: Exception) { JSONArray() }
        }
    }

    // ---------------- Realtime WebSocket ----------------

    interface RealtimeListener {
        fun onNotification(record: JSONObject)
        fun onMessage(record: JSONObject)
        fun onStatus(connected: Boolean)
    }

    private var webSocket: WebSocket? = null
    private var currentUid: String = ""
    @Volatile private var closing = false
    private var refCounter = 0
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<RealtimeListener>()

    /** 建立/保持全局连接并注册一个监听器（同 uid 下复用同一连接） */
    fun connect(uid: String, listener: RealtimeListener) {
        if (uid.isBlank()) return
        if (currentUid != uid) {
            // 切换用户：清掉旧 listener 和旧连接
            listeners.clear()
            listeners.add(listener)
            closeSocket()
            currentUid = uid
            closing = false
            establishConnection()
            return
        }
        listeners.add(listener)
        if (webSocket == null && !closing) {
            closing = false
            establishConnection()
        }
    }

    private fun establishConnection() {
        if (currentUid.isBlank()) return
        val uid = currentUid
        val wsUrl = "${SupabaseConfig.url.replace("https://", "wss://")}/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
        try {
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    subscribeNotifications()
                    subscribeMessages()
                    listeners.forEach { it.onStatus(true) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONObject(text)
                        if (msg.optString("event") == "postgres_changes") {
                            val payload = msg.optJSONObject("payload")
                            val record = payload?.optJSONObject("record") ?: return
                            when (payload.optString("table", "")) {
                                "notifications" -> {
                                    // 自己产生的通知不回推给自己
                                    if (record.optString("actor_uid") != uid) {
                                        listeners.forEach { it.onNotification(record) }
                                    }
                                }
                                "messages" -> {
                                    listeners.forEach { it.onMessage(record) }
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closing) scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!closing) scheduleReconnect()
                }
            })
        } catch (_: Exception) { }
    }

    /** 页面级监听器：注册/注销，不触碰底层连接 */
    fun addListener(listener: RealtimeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeListener) {
        listeners.remove(listener)
    }

    private var reconnectRunnable: Runnable? = null

    private fun scheduleReconnect() {
        if (listeners.isEmpty()) return
        listeners.forEach { it.onStatus(false) }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!closing && currentUid.isNotBlank()) {
                establishConnection()
            }
        }
        reconnectRunnable = r
        handler.postDelayed(r, 3000)
    }

    fun disconnect() {
        closing = true
        reconnectRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        reconnectRunnable = null
        closeSocket()
        listeners.clear()
        currentUid = ""
    }

    private fun closeSocket() {
        try { webSocket?.close(1000, "bye") } catch (_: Exception) { }
        webSocket = null
    }

    private fun nextRef(): String = (++refCounter).toString()

    private fun subscribeNotifications() {
        val payload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("postgres_changes", JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", "notifications")
                        put("filter", "recipient_uid=eq.$currentUid")
                    })
                })
            })
        }
        sendJoin("realtime:notifications", payload)
    }

    private fun subscribeMessages() {
        val payload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("postgres_changes", JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", "messages")
                        put("filter", "receiver_uid=eq.$currentUid")
                    })
                })
            })
        }
        sendJoin("realtime:messages", payload)
    }

    private fun sendJoin(topic: String, payload: JSONObject) {
        try {
            val body = JSONObject().apply {
                put("topic", topic)
                put("event", "phx_join")
                put("payload", payload)
                put("ref", nextRef())
            }
            webSocket?.send(body.toString())
        } catch (_: Exception) { }
    }

    // ---------------- 基础 REST 工具 ----------------

    private suspend fun queryRest(table: String, query: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val request = object : StringRequest(
                        GET, "${SupabaseConfig.url}/rest/v1/$table?$query",
                        { response ->
                            try { cont.resume(JSONObject().apply { put("users", JSONArray(response)) }) }
                            catch (e: Exception) { cont.resumeWithException(e) }
                        },
                        { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                    ) {
                        override fun getHeaders() = mapOf(
                            "apikey" to SupabaseConfig.anonKey,
                            "Content-Type" to "application/json"
                        )
                    }
                    requestQueue.add(request)
                }
            }
            result ?: throw Exception("网络连接超时")
        }
    }

    private suspend fun postRest(path: String, body: JSONObject) {
        val bodyStr = body.toString()
        withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<String> { cont ->
                    val request = object : StringRequest(POST, "${SupabaseConfig.url}$path",
                        { cont.resume(it) },
                        { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                    ) {
                        override fun getBody(): ByteArray = bodyStr.toByteArray()
                        override fun getBodyContentType(): String = "application/json"
                        override fun getHeaders() = mapOf(
                            "apikey" to SupabaseConfig.anonKey,
                            "Prefer" to "return=minimal"
                        )
                    }
                    requestQueue.add(request)
                }
            }
            result ?: throw Exception("网络连接超时")
        }
    }

    private suspend fun patchRest(table: String, filter: String, body: JSONObject) {
        val bodyStr = body.toString()
        withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<String> { cont ->
                    val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/$table?$filter",
                        { cont.resume(it) },
                        { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                    ) {
                        override fun getBody(): ByteArray = bodyStr.toByteArray()
                        override fun getBodyContentType(): String = "application/json"
                        override fun getHeaders() = mapOf(
                            "apikey" to SupabaseConfig.anonKey,
                            "Prefer" to "return=minimal"
                        )
                    }
                    requestQueue.add(request)
                }
            }
            result ?: throw Exception("网络连接超时")
        }
    }

    private fun extractVolleyError(error: VolleyError): String {
        val code = error.networkResponse?.statusCode ?: 0
        val data = error.networkResponse?.data
        val body = if (data != null) {
            try { JSONObject(String(data, Charsets.UTF_8)).optString("msg", String(data, Charsets.UTF_8)) }
            catch (e: Exception) { String(data, Charsets.UTF_8) }
        } else error.message ?: "未知错误"
        return "$code: $body"
    }
}
