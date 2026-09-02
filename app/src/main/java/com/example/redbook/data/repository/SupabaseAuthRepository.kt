package com.example.redbook.data.repository

import android.app.Application
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.*
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.redbook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

object SupabaseConfig {
    val url = "https://wsxygiskzjkezoakejri.supabase.co"
    val anonKey = "sb_publishable_WedwYJNF5dqYrX5ERlSXTA_VTJkWkJL"
    val serviceRole get() = BuildConfig.SUPABASE_SERVICE_ROLE
    val resendKey get() = BuildConfig.RESEND_API_KEY
}

class SupabaseAuthRepository(private val app: Application) {

    companion object {
        /** 当前登录用户昵称（全局共享，用于通知 actor_name，由 AppScreen 登录成功后设置） */
        @Volatile
        var currentUserName: String = ""

        /** 当前登录用户头像（全局共享，用于通知 actor_avatar，由 AppScreen 登录成功后设置） */
        @Volatile
        var currentUserAvatar: String = ""
    }

    private val requestQueue by lazy { Volley.newRequestQueue(app) }
    private val timeoutMs = 15_000L

    /** 通知仓库（写入互动通知事件） */
    private val realtimeRepository by lazy { RealtimeRepository(app) }

    suspend fun register(email: String, password: String, account: String, nickname: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val existing = queryRest("users", "select=account&account=eq.$account&limit=1")
                    if (existing.optJSONArray("users")?.length() ?: 0 > 0)
                        throw AppException("该账号名已被占用")

                    val xhsId = generateUniqueXhsId()
                    val body = JSONObject().apply {
                        put("email", email)
                        put("password", password)
                        put("data", JSONObject().apply {
                            put("account", account)
                            put("nickname", nickname ?: account)
                            put("xhs_id", xhsId)
                        })
                    }
                    val response = supabasePost("/auth/v1/signup", body)
                    val uid = response.getJSONObject("user").getString("id")
                    // 插入 users 表（失败不阻断注册，登录时会补全）
                    try { insertUserMapping(uid, account, email, nickname ?: account) } catch (_: Exception) { }
                    // 存储 xhs_id 和 nickname 到 users 表（失败不阻断注册）
                    try {
                        patchUserRow(uid, JSONObject().apply {
                            put("xhs_id", xhsId)
                            if (!nickname.isNullOrBlank()) put("nickname", nickname)
                        })
                    } catch (_: Exception) { }
                    Result.success(uid)
                }
                result ?: Result.failure(Exception("网络连接超时"))
            } catch (e: AppException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(parseError(e)))
            }
        }
    }

    suspend fun login(input: String, password: String): Result<UserData> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val email = if (input.contains("@")) input else {
                        val res = queryRest("users", "select=email&account=eq.$input&limit=1")
                        val users = res.optJSONArray("users")
                        if (users == null || users.length() == 0) throw AppException("账号不存在")
                        users.getJSONObject(0).getString("email")
                    }
                    val body = JSONObject().apply {
                        put("email", email)
                        put("password", password)
                    }
                    val userData = parseUserData(supabasePost("/auth/v1/token?grant_type=password", body)).getOrNull()
                    if (userData == null) {
                        Result.failure(Exception("登录失败"))
                    } else {
                        try {
                            val res = queryRest("users", "select=xhs_id,account,nickname,gender,birthday,avatar_url,background_url&uid=eq.${userData.uid}&limit=1")
                            val arr = res.optJSONArray("users")
                            if (arr != null && arr.length() > 0) {
                                val row = arr.getJSONObject(0)
                                Result.success(userData.copy(
                                    xhsId = row.optString("xhs_id", userData.xhsId),
                                    account = userData.account.ifBlank { row.optString("account", "") },
                                    nickname = row.optString("nickname", "").ifBlank { userData.nickname },
                                    gender = row.optString("gender", ""),
                                    birthday = row.optString("birthday", ""),
                                    avatarUrl = row.optString("avatar_url", ""),
                                    backgroundUrl = row.optString("background_url", "")
                                ))
                            } else {
                                Result.success(userData)
                            }
                        } catch (e: Exception) {
                            Result.success(userData)
                        }
                    }
                }
                result ?: Result.failure(Exception("网络连接超时"))
            } catch (e: AppException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(parseError(e)))
            }
        }
    }

    suspend fun requestResetCode(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val res = queryRest("users", "select=uid&email=eq.$email&limit=1")
                    val users = res.optJSONArray("users")
                    if (users == null || users.length() == 0) throw AppException("该邮箱未注册")
                    val uid = users.getJSONObject(0).getString("uid")

                    val code = String.format("%06d", Random.nextInt(1000000))
                    val expiry = System.currentTimeMillis() + 5 * 60 * 1000

                    patchUserRow(uid, JSONObject().apply {
                        put("reset_code", code)
                        put("reset_code_expiry", expiry)
                    })

                    if (SupabaseConfig.resendKey.isNotBlank()) {
                        sendResendEmail(email, code)
                    }

                    Result.success(code)
                }
                result ?: Result.failure(Exception("网络连接超时"))
            } catch (e: AppException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(parseError(e)))
            }
        }
    }

    suspend fun verifyCodeAndReset(email: String, code: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (SupabaseConfig.serviceRole.isBlank()) throw AppException("请先配置 serviceRole")
                val result = withTimeoutOrNull(timeoutMs) {
                    val res = queryRest("users", "select=uid,reset_code,reset_code_expiry&email=eq.$email&limit=1")
                    val users = res.optJSONArray("users")
                    if (users == null || users.length() == 0) throw AppException("该邮箱未注册")

                    val row = users.getJSONObject(0)
                    val storedCode = row.optString("reset_code", "")
                    val expiry = row.optLong("reset_code_expiry", 0)
                    val uid = row.getString("uid")

                    if (storedCode.isEmpty() || storedCode != code) throw AppException("验证码错误")
                    if (System.currentTimeMillis() > expiry) throw AppException("验证码已过期")

                    val body = JSONObject().apply { put("password", newPassword) }
                    supabaseAdminPut("/auth/v1/admin/users/$uid", body)

                    patchUserRow(uid, JSONObject().apply {
                        put("reset_code", "")
                        put("reset_code_expiry", 0)
                    })
                    Result.success(Unit)
                }
                result ?: Result.failure(Exception("网络连接超时"))
            } catch (e: AppException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(Exception(parseError(e)))
            }
        }
    }

    private suspend fun generateUniqueXhsId(): String {
        var id: String
        do {
            id = (1..9).map { Random.nextInt(10) }.joinToString("")
        } while (xhsIdExists(id))
        return id
    }

    private suspend fun xhsIdExists(xhsId: String): Boolean {
        return try {
            val resp = queryRest("users", "select=xhs_id&xhs_id=eq.$xhsId&limit=1")
            (resp.optJSONArray("users")?.length() ?: 0) > 0
        } catch (e: Exception) { false }
    }

    private suspend fun patchUserRow(uid: String, body: JSONObject) {
        val bodyString = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(
                PATCH, "${SupabaseConfig.url}/rest/v1/users?uid=eq.$uid",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody(): ByteArray = bodyString.toByteArray()
                override fun getBodyContentType(): String = "application/json"
                override fun getHeaders(): Map<String, String> = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    /** 通用 PATCH：更新指定表满足 filter 的行 */
    private suspend fun patchRest(table: String, filter: String, body: JSONObject) {
        val bodyString = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(
                PATCH, "${SupabaseConfig.url}/rest/v1/$table?$filter",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody(): ByteArray = bodyString.toByteArray()
                override fun getBodyContentType(): String = "application/json"
                override fun getHeaders(): Map<String, String> = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    private suspend fun sendResendEmail(to: String, code: String) {
        suspendCancellableCoroutine<JSONObject> { cont ->
            val body = JSONObject().apply {
                put("from", "RedBook <onboarding@resend.dev>")
                put("to", JSONArray().apply { put(to) })
                put("subject", "密码重置验证码")
                put("html", "<p>您的验证码是: <b>$code</b>，5分钟内有效</p>")
            }
            val request = object : JsonObjectRequest(
                POST, "https://api.resend.com/emails", body,
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getHeaders() = mapOf(
                    "Authorization" to "Bearer ${SupabaseConfig.resendKey}",
                    "Content-Type" to "application/json"
                )
            }
            requestQueue.add(request)
        }
    }

    private class AppException(message: String) : Exception(message)

    private fun parseUserData(json: JSONObject): Result<UserData> {
        val user = json.getJSONObject("user")
        val meta = user.optJSONObject("user_metadata") ?: JSONObject()
        return Result.success(UserData(
            uid = user.getString("id"),
            email = user.getString("email"),
            account = meta.optString("account", ""),
            nickname = meta.optString("nickname", ""),
            xhsId = meta.optString("xhs_id", ""),
            emailVerified = true
        ))
    }

    private suspend fun supabasePost(path: String, body: JSONObject): JSONObject {
        return suspendCancellableCoroutine { cont ->
            val request = object : JsonObjectRequest(
                POST, "${SupabaseConfig.url}$path", body,
                { cont.resume(it) },
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

    private suspend fun supabaseAdminPut(path: String, body: JSONObject) {
        suspendCancellableCoroutine<JSONObject> { cont ->
            val request = object : JsonObjectRequest(
                PUT, "${SupabaseConfig.url}$path", body,
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.serviceRole,
                    "Authorization" to "Bearer ${SupabaseConfig.serviceRole}",
                    "Content-Type" to "application/json"
                )
            }
            requestQueue.add(request)
        }
    }

    private suspend fun queryRest(table: String, query: String): JSONObject {
        return suspendCancellableCoroutine { cont ->
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

    private suspend fun insertUserMapping(uid: String, account: String, email: String, nickname: String = "") {
        val body = JSONObject().apply {
            put("uid", uid); put("account", account); put("email", email)
            if (nickname.isNotBlank()) put("nickname", nickname)
        }.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(
                POST, "${SupabaseConfig.url}/rest/v1/users",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody(): ByteArray = body.toByteArray()
                override fun getBodyContentType(): String = "application/json"
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
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

    private fun parseError(e: Exception): String {
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("超时") || msg.contains("timeout") -> "网络连接超时"
            msg.contains("user already registered") || (msg.contains("422") && msg.contains("registered")) -> "该邮箱已被注册"
            msg.contains("invalid login credentials") -> "账号或密码错误"
            msg.contains("password should be at least") -> "密码长度至少需要6位"
            msg.contains("422") -> "注册信息有误，请检查邮箱或密码"
            msg.contains("429") -> "操作太频繁，请稍后再试"
            else -> msg.ifBlank { "操作失败，请重试" }
        }
    }

    suspend fun insertPost(postId: String, title: String, content: String,
                           authorUid: String, authorName: String, authorAvatar: String) {
        val body = JSONObject().apply {
            put("post_id", postId)
            put("title", title)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/posts", body)
    }

    suspend fun uploadImage(uri: android.net.Uri, app: android.content.Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cr = app.contentResolver
                val mime = cr.getType(uri) ?: "image/jpeg"
                val inputStream = cr.openInputStream(uri)
                if (inputStream == null) {
                    android.util.Log.e("RedBook", "uploadImage open failed: $uri")
                    return@withContext null
                }
                val bytes = inputStream.readBytes()
                inputStream.close()
                val isVideo = mime.contains("video")
                val ext = when { isVideo -> "mp4"; mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; else -> "jpg" }
                val prefix = if (isVideo) "video:" else ""
                val fileName = "img_${System.nanoTime()}.$ext"
                uploadToStorage("post-images", fileName, bytes, mime)
                "$prefix${SupabaseConfig.url}/storage/v1/object/public/post-images/$fileName"
            } catch (e: Exception) { 
                android.util.Log.e("RedBook", "uploadImage error: ${e.message}")
                null 
            }
        }
    }

    private suspend fun uploadToStorage(bucket: String, fileName: String, bytes: ByteArray, mime: String) {
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(
                POST, "${SupabaseConfig.url}/storage/v1/object/$bucket/$fileName",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody(): ByteArray = bytes
                override fun getBodyContentType(): String = mime
                override fun getHeaders(): Map<String, String> = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Authorization" to "Bearer ${SupabaseConfig.anonKey}"
                )
            }
            request.retryPolicy = DefaultRetryPolicy(60000, 2, 1f)
            requestQueue.add(request)
        }
    }

    suspend fun publishPost(postId: String, title: String, content: String,
                            authorUid: String, authorName: String, authorXhsId: String, imageUrl: String = "", authorAvatar: String = "") {
        val body = JSONObject().apply {
            put("post_id", postId)
            put("title", title)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_xhs_id", authorXhsId)
            put("author_avatar", authorAvatar)
            put("image_url", imageUrl)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/posts", body)
    }

    suspend fun saveDraft(draftId: String, title: String, content: String,
                          authorUid: String, authorXhsId: String, authorName: String, imageUrl: String = "") {
        val body = JSONObject().apply {
            put("draft_id", draftId)
            put("title", title)
            put("content", content)
            put("author_uid", authorUid)
            put("author_xhs_id", authorXhsId)
            put("author_name", authorName)
            put("image_url", imageUrl)
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/drafts", body)
    }

    suspend fun getDrafts(authorUid: String): JSONArray {
        val resp = queryRest("drafts", "select=*&author_uid=eq.$authorUid&order=updated_at.desc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("drafts") ?: JSONArray()
    }

    suspend fun deleteDraft(draftId: String) {
        val body = JSONObject().apply { put("draft_id", draftId) }
        supabaseDelete("/rest/v1/drafts?draft_id=eq.$draftId")
    }

    suspend fun updateDraft(draftId: String, title: String, content: String, imageUrl: String) {
        val body = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("image_url", imageUrl)
            put("updated_at", System.currentTimeMillis())
        }
        patchDraftRow(draftId, body)
    }

    private suspend fun patchDraftRow(draftId: String, body: JSONObject) {
        val bodyStr = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/drafts?draft_id=eq.$draftId",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody() = bodyStr.toByteArray()
                override fun getBodyContentType() = "application/json"
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey, "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    private suspend fun supabaseDelete(path: String) {
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(DELETE, "${SupabaseConfig.url}$path",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey, "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    suspend fun incrementViewCount(postId: String) {
        val resp = queryRest("posts", "select=view_count&post_id=eq.$postId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: return
        if (arr.length() == 0) return
        val count = arr.getJSONObject(0).optInt("view_count", 0) + 1
        val body = JSONObject().apply { put("view_count", count) }
        patchUserRowByPostId(postId, body)
    }

    suspend fun getPosts(): JSONArray {
        val resp = queryRest("posts", "select=*&order=created_at.desc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
    }

    suspend fun getPostsByViews(): JSONArray {
        val resp = queryRest("posts", "select=*&order=view_count.desc&limit=6")
        return resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
    }

    suspend fun getVideos(): JSONArray {
        val resp = queryRest("video_notes", "select=*&order=created_at.desc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("video_notes") ?: JSONArray()
    }

    suspend fun getVideo(videoId: String): JSONObject? {
        val resp = queryRest("video_notes", "select=*&video_id=eq.$videoId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("video_notes") ?: return null
        return if (arr.length() > 0) arr.getJSONObject(0) else null
    }

    suspend fun publishVideo(videoId: String, title: String, videoUrl: String,
                             authorUid: String, authorName: String, authorXhsId: String, authorAvatar: String = "") {
        val body = JSONObject().apply {
            put("video_id", videoId)
            put("title", title)
            put("video_url", videoUrl)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_xhs_id", authorXhsId)
            put("author_avatar", authorAvatar)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/video_notes", body)
    }

    suspend fun getPost(postId: String): JSONObject? {
        val resp = queryRest("posts", "select=*&post_id=eq.$postId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: return null
        return if (arr.length() > 0) arr.getJSONObject(0) else null
    }

    // 记录点赞/取消点赞（按uid绑定）
    suspend fun recordLike(userUid: String, postId: String, liked: Boolean) {
        if (liked) {
            val body = JSONObject().apply {
                put("like_id", "l_${userUid}_$postId")
                put("user_uid", userUid)
                put("user_xhs_id", "")
                put("post_id", postId)
                put("created_at", System.currentTimeMillis())
            }
            supabasePostBody("/rest/v1/likes", body)
            notifyPostOwner(userUid, postId, "like")
        } else {
            supabaseDelete("/rest/v1/likes?user_uid=eq.$userUid&post_id=eq.$postId")
        }
    }

    // 记录收藏/取消收藏
    suspend fun recordFavorite(userUid: String, postId: String, favorited: Boolean) {
        if (favorited) {
            val body = JSONObject().apply {
                put("fav_id", "f_${userUid}_$postId")
                put("user_uid", userUid)
                put("user_xhs_id", "")
                put("post_id", postId)
                put("created_at", System.currentTimeMillis())
            }
            supabasePostBody("/rest/v1/favorites", body)
            notifyPostOwner(userUid, postId, "favorite")
        } else {
            supabaseDelete("/rest/v1/favorites?user_uid=eq.$userUid&post_id=eq.$postId")
        }
    }

    // 是否已点赞
    suspend fun hasLiked(userUid: String, postId: String): Boolean {
        return try {
            val resp = queryRest("likes", "select=like_id&user_uid=eq.$userUid&post_id=eq.$postId&limit=1")
            (resp.optJSONArray("users")?.length() ?: 0) > 0
        } catch (e: Exception) { false }
    }

    // 是否已收藏
    suspend fun hasFavorited(userUid: String, postId: String): Boolean {
        return try {
            val resp = queryRest("favorites", "select=fav_id&user_uid=eq.$userUid&post_id=eq.$postId&limit=1")
            (resp.optJSONArray("users")?.length() ?: 0) > 0
        } catch (e: Exception) { false }
    }

    // 用户发布的帖子（按小红书id或uid兜底）
    suspend fun getUserPosts(userUid: String, userXhsId: String): JSONArray {
        android.util.Log.d("RedBook", "getUserPosts uid=$userUid xhs=$userXhsId")
        if (userXhsId.isNotBlank()) {
            val resp = queryRest("posts", "select=*&author_xhs_id=eq.$userXhsId&order=created_at.desc")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
            android.util.Log.d("RedBook", "getUserPosts by xhs_id count=${arr.length()}")
            if (arr.length() > 0) return arr
        }
        if (userUid.isNotBlank()) {
            val resp = queryRest("posts", "select=*&author_uid=eq.$userUid&order=created_at.desc")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
            android.util.Log.d("RedBook", "getUserPosts by uid count=${arr.length()}")
            return arr
        }
        return JSONArray()
    }

    // 用户点赞的帖子 id 集合
    suspend fun getLikedPostIds(userUid: String): Set<String> {
        return try {
            val resp = queryRest("likes", "select=post_id&user_uid=eq.$userUid")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("likes") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it).getString("post_id") }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    // 用户点赞的帖子
    suspend fun getUserLikedPosts(userUid: String): JSONArray {
        val resp = queryRest("likes", "select=post_id&user_uid=eq.$userUid")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("likes") ?: JSONArray()
        val postIds = (0 until arr.length()).map { arr.getJSONObject(it).getString("post_id") }
        if (postIds.isEmpty()) return JSONArray()
        val filters = postIds.joinToString(",") { "post_id.eq.$it" }
        val postsResp = queryRest("posts", "select=*&or=($filters)")
        return postsResp.optJSONArray("users") ?: postsResp.optJSONArray("posts") ?: JSONArray()
    }

    // 用户收藏的帖子
    suspend fun getUserFavoritedPosts(userUid: String): JSONArray {
        val resp = queryRest("favorites", "select=post_id&user_uid=eq.$userUid")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("favorites") ?: JSONArray()
        val postIds = (0 until arr.length()).map { arr.getJSONObject(it).getString("post_id") }
        if (postIds.isEmpty()) return JSONArray()
        val filters = postIds.joinToString(",") { "post_id.eq.$it" }
        val postsResp = queryRest("posts", "select=*&or=($filters)")
        return postsResp.optJSONArray("users") ?: postsResp.optJSONArray("posts") ?: JSONArray()
    }

    // 用户的评论和回复
    suspend fun getUserComments(userUid: String, userXhsId: String): JSONArray {
        var arr = JSONArray()
        if (userXhsId.isNotBlank()) {
            val resp = queryRest("comments", "select=*&author_xhs_id=eq.$userXhsId&order=created_at.desc")
            arr = resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: JSONArray()
        }
        if (arr.length() == 0 && userUid.isNotBlank()) {
            val resp = queryRest("comments", "select=*&author_uid=eq.$userUid&order=created_at.desc")
            arr = resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: JSONArray()
        }
        // 先建内存 map，避免 N+1 查询
        val map = mutableMapOf<String, Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            map[c.optString("comment_id")] = c.optString("content") to c.optString("author_name")
        }
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val obj = JSONObject()
            obj.put("comment_id", c.optString("comment_id"))
            obj.put("post_id", c.optString("post_id"))
            obj.put("parent_id", c.optString("parent_id"))
            obj.put("content", c.optString("content"))
            obj.put("author_name", c.optString("author_name"))
            obj.put("created_at", c.optLong("created_at"))
            obj.put("like_count", c.optInt("like_count"))
            obj.put("post_title", c.optString("post_title", ""))
            obj.put("ip_location", c.optString("ip_location", ""))
            obj.put("image_url", c.optString("image_url", ""))
            val parentId = c.optString("parent_id")
            if (parentId.isNotEmpty()) {
                val cached = map[parentId]
                if (cached != null) {
                    obj.put("parent_content", cached.first)
                    obj.put("parent_user", cached.second)
                } else {
                    val pr = queryRest("comments", "select=content,author_name&comment_id=eq.$parentId&limit=1")
                    val parr = pr.optJSONArray("users") ?: pr.optJSONArray("comments") ?: JSONArray()
                    if (parr.length() > 0) {
                        val p = parr.getJSONObject(0)
                        obj.put("parent_content", p.optString("content"))
                        obj.put("parent_user", p.optString("author_name"))
                    }
                }
            }
            result.put(obj)
        }
        return result
    }

    // 用户的草稿
    suspend fun getUserDrafts(userUid: String, userXhsId: String): JSONArray {
        var arr = JSONArray()
        if (userXhsId.isNotBlank()) {
            val resp = queryRest("drafts", "select=*&author_xhs_id=eq.$userXhsId&order=updated_at.desc")
            arr = resp.optJSONArray("users") ?: resp.optJSONArray("drafts") ?: JSONArray()
        }
        if (arr.length() == 0 && userUid.isNotBlank()) {
            val resp = queryRest("drafts", "select=*&author_uid=eq.$userUid&order=updated_at.desc")
            arr = resp.optJSONArray("users") ?: resp.optJSONArray("drafts") ?: JSONArray()
        }
        android.util.Log.d("RedBook", "getUserDrafts uid=$userUid xhs=$userXhsId count=${arr.length()}")
        return arr
    }

    suspend fun getComments(postId: String): JSONArray {
        val resp = queryRest("comments", "select=*&post_id=eq.$postId&order=created_at.asc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: JSONArray()
    }

    /** 批量判断哪些 comment_id 还存在（用于通知列表标记已删除评论） */
    suspend fun getExistingCommentIds(commentIds: Set<String>): Set<String> {
        if (commentIds.isEmpty()) return emptySet()
        return try {
            val filters = commentIds.filter { it.isNotBlank() }.joinToString(",") { "comment_id.eq.$it" }
            if (filters.isBlank()) return emptySet()
            val resp = queryRest("comments", "select=comment_id&or=($filters)")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it).optString("comment_id", "") }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    suspend fun insertComment(commentId: String, postId: String, content: String,
                              authorUid: String, authorName: String, authorAvatar: String,
                              authorXhsId: String, postTitle: String, imageUrl: String = "") {
        val body = JSONObject().apply {
            put("comment_id", commentId)
            put("post_id", postId)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("author_xhs_id", authorXhsId)
            put("post_title", postTitle)
            put("image_url", imageUrl)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
        notifyPostOwner(authorUid, postId, "comment", commentId, content)
    }

    suspend fun insertReply(replyId: String, postId: String, parentId: String, content: String,
                            authorUid: String, authorName: String, authorAvatar: String, authorXhsId: String, postTitle: String, imageUrl: String = "") {
        val body = JSONObject().apply {
            put("comment_id", replyId)
            put("post_id", postId)
            put("parent_id", parentId)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("author_xhs_id", authorXhsId)
            put("post_title", postTitle)
            put("image_url", imageUrl)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
        notifyPostOwner(authorUid, postId, "reply", replyId, content)
    }

    suspend fun deleteComment(commentId: String) {
        supabaseDelete("/rest/v1/comments?comment_id=eq.$commentId")
    }

    // 记录浏览（去重后插入最新）
    suspend fun recordBrowse(userUid: String, postId: String) {
        try { supabaseDelete("/rest/v1/browsing_history?user_uid=eq.$userUid&post_id=eq.$postId") } catch (_: Exception) { }
        val body = JSONObject().apply {
            put("user_uid", userUid)
            put("post_id", postId)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/browsing_history", body)
    }

    // 获取浏览记录（返回帖子）
    suspend fun getBrowseHistory(userUid: String): JSONArray {
        val resp = queryRest("browsing_history", "select=post_id&user_uid=eq.$userUid&order=created_at.desc")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("browsing_history") ?: JSONArray()
        val postIds = (0 until arr.length()).map { arr.getJSONObject(it).getString("post_id") }
        if (postIds.isEmpty()) return JSONArray()
        val filters = postIds.joinToString(",") { "post_id.eq.$it" }
        val postsResp = queryRest("posts", "select=*&or=($filters)")
        return postsResp.optJSONArray("users") ?: postsResp.optJSONArray("posts") ?: JSONArray()
    }

    // 删除浏览记录
    suspend fun deleteBrowse(userUid: String, postId: String) {
        supabaseDelete("/rest/v1/browsing_history?user_uid=eq.$userUid&post_id=eq.$postId")
    }

    // 关注/取消关注（按用户 uid）
    suspend fun follow(followerUid: String, followedUid: String, following: Boolean) {
        if (followerUid.isBlank() || followedUid.isBlank() || followerUid == followedUid) return
        if (following) {
            val body = JSONObject().apply {
                put("follower_uid", followerUid)
                put("followed_uid", followedUid)
                put("created_at", System.currentTimeMillis())
            }
            supabasePostBody("/rest/v1/follows", body)
            notifyFollow(followerUid, followedUid)
        } else {
            supabaseDelete("/rest/v1/follows?follower_uid=eq.$followerUid&followed_uid=eq.$followedUid")
        }
    }

    // 是否已关注
    suspend fun isFollowing(followerUid: String, followedUid: String): Boolean {
        if (followerUid.isBlank() || followedUid.isBlank()) return false
        return try {
            val resp = queryRest("follows", "select=follower_uid&follower_uid=eq.$followerUid&followed_uid=eq.$followedUid&limit=1")
            (resp.optJSONArray("users")?.length() ?: 0) > 0
        } catch (e: Exception) { false }
    }

    /** 按 uid 查用户基础资料（头像/昵称/背景/性别/生日/xhs_id），找不到返回 null */
    suspend fun getUserByUid(uid: String): JSONObject? {
        if (uid.isBlank()) return null
        return try {
            val resp = queryRest("users", "select=uid,nickname,avatar_url,background_url,gender,birthday,xhs_id&uid=eq.$uid&limit=1")
            val arr = resp.optJSONArray("users") ?: JSONArray()
            if (arr.length() > 0) arr.getJSONObject(0) else null
        } catch (e: Exception) { null }
    }

    /** 按昵称或小红书号模糊搜索用户（ilike），排除自己；返回 uid,nickname,xhs_id,avatar_url */
    suspend fun searchUsers(query: String, excludeUid: String = ""): JSONArray {
        val q = query.trim()
        if (q.isBlank()) return JSONArray()
        return try {
            val encoded = java.net.URLEncoder.encode("*$q*", "UTF-8")
            val resp = queryRest(
                "users",
                "select=uid,nickname,xhs_id,avatar_url&or=(nickname.ilike.$encoded,xhs_id.ilike.$encoded)&limit=50"
            )
            val arr = resp.optJSONArray("users") ?: JSONArray()
            if (excludeUid.isBlank()) return arr
            val result = JSONArray()
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                if (u.optString("uid", "") != excludeUid) result.put(u)
            }
            result
        } catch (e: Exception) { JSONArray() }
    }

    /** 批量判断我是否关注了这些人（返回 uid -> followed） */
    suspend fun isFollowingBatch(followerUid: String, targetUids: Collection<String>): Map<String, Boolean> {
        if (followerUid.isBlank()) return emptyMap()
        val targets = targetUids.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) return emptyMap()
        return try {
            val filters = targets.joinToString(",") { "followed_uid.eq.$it" }
            val resp = queryRest("follows", "select=followed_uid&follower_uid=eq.$followerUid&or=($filters)")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()
            val followed = (0 until arr.length()).map { arr.getJSONObject(it).optString("followed_uid", "") }.toSet()
            targets.associateWith { it in followed }
        } catch (e: Exception) { emptyMap() }
    }

    /** 批量查用户头像（uid -> avatar_url），用于评论头像兜底 */
    suspend fun getAvatarsByUids(uids: Set<String>): Map<String, String> {
        if (uids.isEmpty()) return emptyMap()
        return try {
            val filters = uids.filter { it.isNotBlank() }.joinToString(",") { "uid.eq.$it" }
            if (filters.isBlank()) return emptyMap()
            val resp = queryRest("users", "select=uid,avatar_url&or=($filters)")
            val arr = resp.optJSONArray("users") ?: JSONArray()
            (0 until arr.length()).associate {
                val u = arr.getJSONObject(it)
                u.optString("uid", "") to u.optString("avatar_url", "")
            }
        } catch (e: Exception) { emptyMap() }
    }

    /** 我对某人的备注名（存 remarks 表，与关注关系无关） */
    suspend fun getRemark(viewerUid: String, targetUid: String): String {
        if (viewerUid.isBlank() || targetUid.isBlank()) return ""
        return try {
            val resp = queryRest("remarks", "select=remark&viewer_uid=eq.$viewerUid&target_uid=eq.$targetUid&limit=1")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("remarks") ?: JSONArray()
            if (arr.length() > 0) arr.getJSONObject(0).optString("remark", "") else ""
        } catch (e: Exception) { "" }
    }

    /** 批量读取我对多人的备注名（target_uid -> remark），用于列表页统一展示 */
    suspend fun getRemarks(viewerUid: String, targetUids: Collection<String>): Map<String, String> {
        if (viewerUid.isBlank()) return emptyMap()
        val targets = targetUids.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) return emptyMap()
        return try {
            val filters = targets.joinToString(",") { "target_uid.eq.$it" }
            val resp = queryRest("remarks", "select=target_uid,remark&viewer_uid=eq.$viewerUid&or=($filters)")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("remarks") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val row = arr.getJSONObject(i)
                val remark = row.optString("remark", "")
                if (remark.isBlank()) null
                else row.optString("target_uid", "") to remark
            }.toMap()
        } catch (e: Exception) { emptyMap() }
    }

    /** 设置/清除我对某人的备注名（upsert，空则清空备注） */
    suspend fun setRemark(viewerUid: String, targetUid: String, remark: String) {
        if (viewerUid.isBlank() || targetUid.isBlank()) return
        try {
            val body = JSONObject().apply {
                put("viewer_uid", viewerUid)
                put("target_uid", targetUid)
                put("remark", remark)
                put("created_at", System.currentTimeMillis())
            }
            // upsert：依赖 remarks 表的 UNIQUE(viewer_uid, target_uid)
            upsertRest("/rest/v1/remarks?on_conflict=viewer_uid,target_uid", body)
        } catch (_: Exception) { }
    }

    /** POST upsert：Prefer resolution=merge-duplicates，插入或更新冲突行 */
    private suspend fun upsertRest(path: String, body: JSONObject) {
        val bodyString = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(
                POST, "${SupabaseConfig.url}$path",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody(): ByteArray = bodyString.toByteArray()
                override fun getBodyContentType(): String = "application/json"
                override fun getHeaders(): Map<String, String> = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Prefer" to "resolution=merge-duplicates,return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    /** 是否互相关注 */
    suspend fun isMutualFollow(aUid: String, bUid: String): Boolean {
        if (aUid.isBlank() || bUid.isBlank()) return false
        return try {
            val resp = queryRest("follows", "select=follower_uid&follower_uid=eq.$aUid&followed_uid=eq.$bUid&limit=1")
            (resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()).length() > 0
        } catch (e: Exception) { false }
    }

    /** 关注数：我关注了多少人 */
    suspend fun getFollowingCount(userUid: String): Int {
        if (userUid.isBlank()) return 0
        return try {
            val resp = queryRest("follows", "select=follower_uid&follower_uid=eq.$userUid")
            (resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()).length()
        } catch (e: Exception) { 0 }
    }

    /** 粉丝数：多少人关注了我 */
    suspend fun getFansCount(userUid: String): Int {
        if (userUid.isBlank()) return 0
        return try {
            val resp = queryRest("follows", "select=followed_uid&followed_uid=eq.$userUid")
            (resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()).length()
        } catch (e: Exception) { 0 }
    }

    /** 获赞数：我发布的所有帖子/视频的 like_count 总和 */
    suspend fun getLikeCount(userUid: String, userXhsId: String): Int {
        if (userUid.isBlank()) return 0
        var total = 0
        try {
            val postsResp = queryRest("posts", "select=like_count&author_uid=eq.$userUid")
            val postsArr = postsResp.optJSONArray("users") ?: postsResp.optJSONArray("posts") ?: JSONArray()
            for (i in 0 until postsArr.length()) total += postsArr.getJSONObject(i).optInt("like_count", 0)
        } catch (_: Exception) { }
        try {
            val vResp = queryRest("video_notes", "select=like_count&author_uid=eq.$userUid")
            val vArr = vResp.optJSONArray("users") ?: vResp.optJSONArray("video_notes") ?: JSONArray()
            for (i in 0 until vArr.length()) total += vArr.getJSONObject(i).optInt("like_count", 0)
        } catch (_: Exception) { }
        return total
    }

    // 关注我的用户列表（按关注时间倒序），返回 uid,nickname,avatar_url,created_at
    suspend fun getMyFollowers(userUid: String): JSONArray {
        val resp = queryRest("follows", "select=follower_uid,created_at&followed_uid=eq.$userUid&order=created_at.desc")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()
        if (arr.length() == 0) return JSONArray()
        val followerUids = (0 until arr.length()).map { arr.getJSONObject(it).getString("follower_uid") }
        val filters = followerUids.joinToString(",") { "uid.eq.$it" }
        val usersResp = queryRest("users", "select=uid,nickname,avatar_url&or=($filters)")
        val usersArr = usersResp.optJSONArray("users") ?: JSONArray()
        val userMap = (0 until usersArr.length()).associateBy(
            { usersArr.getJSONObject(it).optString("uid", "") },
            { usersArr.getJSONObject(it) }
        )
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            val uid = f.optString("follower_uid", "")
            val u = userMap[uid] ?: continue
            result.put(JSONObject().apply {
                put("uid", uid)
                put("nickname", u.optString("nickname", ""))
                put("avatar_url", u.optString("avatar_url", ""))
                put("created_at", f.optLong("created_at", 0L))
            })
        }
        return result
    }

    // 我已关注的 uid 集合
    suspend fun getFollowingUids(userUid: String): Set<String> {
        return try {
            val resp = queryRest("follows", "select=followed_uid&follower_uid=eq.$userUid")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it).getString("followed_uid") }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /** 我关注的用户完整资料列表（uid,nickname,avatar_url，按关注时间倒序），用于联系人搜索 */
    suspend fun getFollowingUsers(userUid: String): JSONArray {
        if (userUid.isBlank()) return JSONArray()
        try {
            val resp = queryRest("follows", "select=followed_uid,created_at&follower_uid=eq.$userUid&order=created_at.desc")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("follows") ?: JSONArray()
            if (arr.length() == 0) return JSONArray()
            val uids = (0 until arr.length()).map { arr.getJSONObject(it).getString("followed_uid") }
            val filters = uids.joinToString(",") { "uid.eq.$it" }
            val usersResp = queryRest("users", "select=uid,nickname,avatar_url&or=($filters)")
            val usersArr = usersResp.optJSONArray("users") ?: JSONArray()
            val userMap = (0 until usersArr.length()).associateBy(
                { usersArr.getJSONObject(it).optString("uid", "") },
                { usersArr.getJSONObject(it) }
            )
            val result = JSONArray()
            for (i in 0 until arr.length()) {
                val uid = arr.getJSONObject(i).optString("followed_uid", "")
                val u = userMap[uid] ?: continue
                result.put(JSONObject().apply {
                    put("uid", uid)
                    put("nickname", u.optString("nickname", ""))
                    put("avatar_url", u.optString("avatar_url", ""))
                })
            }
            return result
        } catch (e: Exception) { return JSONArray() }
    }

    // 更新用户资料
    suspend fun updateUserProfile(uid: String, nickname: String? = null, backgroundUrl: String? = null, avatarUrl: String? = null, gender: String? = null, birthday: String? = null) {
        val body = JSONObject()
        nickname?.let { body.put("nickname", it) }
        backgroundUrl?.let { body.put("background_url", it) }
        avatarUrl?.let { body.put("avatar_url", it) }
        gender?.let { body.put("gender", it) }
        birthday?.let { body.put("birthday", it) }
        if (body.length() == 0) return
        patchUserRow(uid, body)
    }

    suspend fun updatePostLike(postId: String, delta: Int) {
        val resp = queryRest("posts", "select=like_count&post_id=eq.$postId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: return
        if (arr.length() == 0) return
        val count = arr.getJSONObject(0).optInt("like_count", 0) + delta
        patchUserRowByPostId(postId, JSONObject().apply { put("like_count", count.coerceAtLeast(0)) })
    }

    suspend fun updatePostFav(postId: String, delta: Int) {
        val resp = queryRest("posts", "select=favorite_count&post_id=eq.$postId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: return
        if (arr.length() == 0) return
        val count = arr.getJSONObject(0).optInt("favorite_count", 0) + delta
        patchUserRowByPostId(postId, JSONObject().apply { put("favorite_count", count.coerceAtLeast(0)) })
    }

    suspend fun updateCommentLike(commentId: String, delta: Int) {
        val resp = queryRest("comments", "select=like_count&comment_id=eq.$commentId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: return
        if (arr.length() == 0) return
        val count = arr.getJSONObject(0).optInt("like_count", 0) + delta
        patchCommentRow(commentId, JSONObject().apply { put("like_count", count.coerceAtLeast(0)) })
    }

    private suspend fun supabasePostBody(path: String, body: JSONObject) {
        val bodyStr = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(POST, "${SupabaseConfig.url}$path",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody() = bodyStr.toByteArray()
                override fun getBodyContentType() = "application/json"
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey, "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    private suspend fun patchUserRowByPostId(postId: String, body: JSONObject) {
        val bodyStr = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/posts?post_id=eq.$postId",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody() = bodyStr.toByteArray()
                override fun getBodyContentType() = "application/json"
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey, "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    private suspend fun patchCommentRow(commentId: String, body: JSONObject) {
        val bodyStr = body.toString()
        suspendCancellableCoroutine<String> { cont ->
            val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/comments?comment_id=eq.$commentId",
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
            ) {
                override fun getBody() = bodyStr.toByteArray()
                override fun getBodyContentType() = "application/json"
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey, "Prefer" to "return=minimal"
                )
            }
            requestQueue.add(request)
        }
    }

    // ---------- 互动通知写入 ----------

    /** 查帖子作者并插入"赞/收藏/评论/回复"通知 */
    private suspend fun notifyPostOwner(actorUid: String, postId: String, type: String, commentId: String = "", commentContent: String = "") {
        try {
            if (postId.isBlank()) return
            val post = getPost(postId) ?: return
            val ownerUid = post.optString("author_uid", "")
            if (ownerUid.isBlank() || ownerUid == actorUid) return
            val postTitle = post.optString("title", "")
            val actorName = currentUserName.ifBlank { "用户" }
            val actorAvatar = currentUserAvatar
            val notifId = "n_${actorUid}_${System.nanoTime()}"
            realtimeRepository.insertNotification(
                notifId = notifId,
                recipientUid = ownerUid,
                actorUid = actorUid,
                actorName = actorName,
                actorAvatar = actorAvatar,
                type = type,
                postId = postId,
                postTitle = postTitle,
                commentId = commentId,
                commentContent = commentContent
            )
        } catch (_: Exception) { }
    }

    /** 插入"关注"通知 */
    private suspend fun notifyFollow(followerUid: String, followedUid: String) {
        try {
            val actorName = currentUserName.ifBlank { "用户" }
            val actorAvatar = currentUserAvatar
            val notifId = "n_${followerUid}_${System.nanoTime()}"
            realtimeRepository.insertNotification(
                notifId = notifId,
                recipientUid = followedUid,
                actorUid = followerUid,
                actorName = actorName,
                actorAvatar = actorAvatar,
                type = "follow"
            )
        } catch (_: Exception) { }
    }

    data class UserData(
        val uid: String,
        val email: String,
        val account: String,
        val nickname: String,
        val xhsId: String,
        val emailVerified: Boolean,
        val gender: String = "",
        val birthday: String = "",
        val avatarUrl: String = "",
        val backgroundUrl: String = ""
    )
}
