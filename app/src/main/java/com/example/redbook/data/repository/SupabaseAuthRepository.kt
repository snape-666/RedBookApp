package com.example.redbook.data.repository

import android.app.Application
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

                    val xhsId = (1..11).map { Random.nextInt(10) }.joinToString("")
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
                    parseUserData(supabasePost("/auth/v1/token?grant_type=password", body))
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

    suspend fun getPost(postId: String): JSONObject? {
        val resp = queryRest("posts", "select=*&post_id=eq.$postId&limit=1")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: return null
        return if (arr.length() > 0) arr.getJSONObject(0) else null
    }

    suspend fun getComments(postId: String): JSONArray {
        val resp = queryRest("comments", "select=*&post_id=eq.$postId&order=created_at.asc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("comments") ?: JSONArray()
    }

    suspend fun insertComment(commentId: String, postId: String, content: String,
                              authorUid: String, authorName: String, authorAvatar: String) {
        val body = JSONObject().apply {
            put("comment_id", commentId)
            put("post_id", postId)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
    }

    suspend fun insertReply(replyId: String, postId: String, parentId: String, content: String,
                            authorUid: String, authorName: String, authorAvatar: String) {
        val body = JSONObject().apply {
            put("comment_id", replyId)
            put("post_id", postId)
            put("parent_id", parentId)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_avatar", authorAvatar)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
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
