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

    private val requestQueue by lazy { Volley.newRequestQueue(app) }
    private val timeoutMs = 15_000L

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
                    insertUserMapping(uid, account, email)
                    // 存储 xhs_id 到 users 表（失败不阻断注册）
                    try { patchUserRow(uid, JSONObject().apply { put("xhs_id", xhsId) }) } catch (_: Exception) { }
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
                    } else if (userData.xhsId.isBlank()) {
                        val res = queryRest("users", "select=xhs_id,account,nickname&uid=eq.${userData.uid}&limit=1")
                        val arr = res.optJSONArray("users")
                        if (arr != null && arr.length() > 0) {
                            val row = arr.getJSONObject(0)
                            Result.success(userData.copy(
                                xhsId = row.optString("xhs_id", ""),
                                account = userData.account.ifBlank { row.optString("account", "") },
                                nickname = userData.nickname.ifBlank { row.optString("nickname", "") }
                            ))
                        } else {
                            Result.success(userData)
                        }
                    } else {
                        Result.success(userData)
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

    private suspend fun insertUserMapping(uid: String, account: String, email: String) {
        val body = JSONObject().apply {
            put("uid", uid); put("account", account); put("email", email)
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
            msg.contains("user already registered") -> "该邮箱已被注册"
            msg.contains("invalid login credentials") -> "账号或密码错误"
            msg.contains("password should be at least") -> "密码长度至少需要6位"
            msg.contains("422") -> msg.removePrefix("422: ").ifBlank { "请求格式不正确" }
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
                            authorUid: String, authorName: String, authorXhsId: String, imageUrl: String = "") {
        val body = JSONObject().apply {
            put("post_id", postId)
            put("title", title)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_xhs_id", authorXhsId)
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

    suspend fun publishVideo(videoId: String, title: String, videoUrl: String,
                             authorUid: String, authorName: String, authorXhsId: String) {
        val body = JSONObject().apply {
            put("video_id", videoId)
            put("title", title)
            put("video_url", videoUrl)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_xhs_id", authorXhsId)
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
                put("post_id", postId)
                put("created_at", System.currentTimeMillis())
            }
            supabasePostBody("/rest/v1/likes", body)
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
                put("post_id", postId)
                put("created_at", System.currentTimeMillis())
            }
            supabasePostBody("/rest/v1/favorites", body)
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
            val parentId = c.optString("parent_id")
            if (parentId.isNotEmpty()) {
                val pr = queryRest("comments", "select=content,author_name&comment_id=eq.$parentId&limit=1")
                val parr = pr.optJSONArray("users") ?: pr.optJSONArray("comments") ?: JSONArray()
                if (parr.length() > 0) {
                    val p = parr.getJSONObject(0)
                    obj.put("parent_content", p.optString("content"))
                    obj.put("parent_user", p.optString("author_name"))
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

    suspend fun insertComment(commentId: String, postId: String, content: String,
                              authorUid: String, authorName: String, authorAvatar: String,
                              authorXhsId: String, postTitle: String) {
        val body = JSONObject().apply {
            put("comment_id", commentId)
            put("post_id", postId)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("author_xhs_id", authorXhsId)
            put("post_title", postTitle)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
    }

    suspend fun insertReply(replyId: String, postId: String, parentId: String, content: String,
                            authorUid: String, authorName: String, authorAvatar: String, authorXhsId: String, postTitle: String) {
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
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
    }

    suspend fun deleteComment(commentId: String) {
        supabaseDelete("/rest/v1/comments?comment_id=eq.$commentId")
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

    data class UserData(
        val uid: String,
        val email: String,
        val account: String,
        val nickname: String,
        val xhsId: String,
        val emailVerified: Boolean
    )
}
