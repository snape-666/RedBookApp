package com.example.redbook.data.repository

import android.app.Application
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.*
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.redbook.data.local.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SupabaseConfig {
    val url = "https://wsxygiskzjkezoakejri.supabase.co"

    /** 可公开的 publishable key：设计上就允许出现在客户端，与 RLS 配合使用 */
    val anonKey = "sb_publishable_WedwYJNF5dqYrX5ERlSXTA_VTJkWkJL"
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

    // 传输层换成 OkHttp（HTTP/2 多路复用 + 连接复用），见 OkHttpStack
    private val requestQueue by lazy { Volley.newRequestQueue(app, OkHttpStack.shared) }
    private val timeoutMs = 15_000L

    /** 上传图片最长边上限（像素），超过则缩放；用于控制文件体积、加快列表加载 */
    private val maxImageDimension = 1080

    /** 通知仓库（写入互动通知事件） */
    private val realtimeRepository by lazy { RealtimeRepository(app) }

    // ---------------- 鉴权 ----------------
    // 所有数据请求都必须带上用户的 access_token：Supabase RLS 靠它解析 auth.uid()，
    // 不带 JWT 的请求会以 anon 角色发出，在开启 RLS 后读会被过滤成空、写直接被拒。
    // 具体实现与续期逻辑集中在 SupabaseAuthHttp，供本类与 RealtimeRepository 共用
    // (refresh_token 单次有效，续期只能有一处实现，否则会互相作废)。

    /** 统一请求头（读操作） */
    private fun authHeaders(): Map<String, String> = SupabaseAuthHttp.headers(app)

    /** 统一请求头（写操作，不需要回传内容） */
    private fun authWriteHeaders(): Map<String, String> = SupabaseAuthHttp.headers(app, write = true)

    /** 带自动续期的请求包装：命中 401 时续期并重试一次 */
    private suspend fun <T> withAuth(block: suspend () -> T): T =
        SupabaseAuthHttp.withAuth(app, block)

    /** 调用 PostgREST RPC(/rest/v1/rpc/<fn>)，用于需要服务端权限的写操作 */
    private suspend fun supabaseRpc(fn: String, params: JSONObject): JSONObject {
        val bodyString = params.toString()
        return withAuth {
            suspendCancellableCoroutine { cont ->
                val request = object : StringRequest(
                    POST, "${SupabaseConfig.url}/rest/v1/rpc/$fn",
                    { response ->
                        try {
                            cont.resume(if (response.isBlank()) JSONObject() else JSONObject(response))
                        } catch (e: Exception) {
                            cont.resume(JSONObject())
                        }
                    },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody(): ByteArray = bodyString.toByteArray()
                    override fun getBodyContentType(): String = "application/json"
                    // 注意：这里不能带 Prefer: return=minimal，
                    // 否则 PostgREST 会返回空响应体，函数返回的 jsonb 就取不到了
                    override fun getHeaders() = authHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    suspend fun register(email: String, password: String, account: String, nickname: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    // 未登录时读不到 users 表(RLS)，账号查重与小红书号生成都走服务端函数
                    val existing = supabaseRpc("account_exists", JSONObject().apply { put("p_account", account) })
                    if (existing.optBoolean("exists", false))
                        throw AppException("该账号名已被占用")

                    val xhsId = supabaseRpc("next_xhs_id", JSONObject()).optString("xhs_id", "")
                    //构造注册请求体,json格式
                    val body = JSONObject().apply {
                        put("email", email)
                        put("password", password)
                        put("data", JSONObject().apply {
                            put("account", account)
                            put("nickname", nickname ?: account)
                            put("xhs_id", xhsId)
                        })
                    }
                    //调用Supbase Auth注册接口
                    val response = supabasePost("/auth/v1/signup", body)
                    val uid = response.getJSONObject("user").getString("id")
                    // 注册响应若带会话(邮箱确认已关闭)，先落库，后续写 users 表才有 JWT 可用
                    AuthSession.save(app, response)
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
                    val isAccountLogin = !input.contains("@")
                    // 「账号名 → 邮箱」在服务端完成，邮箱不会回到客户端
                    // （原先的 resolve_login_email 匿名可调且回传邮箱，等于任何人都能
                    //   拿账号名换出别人的邮箱，已删除）
                    val res = SupabaseAuthHttp.edgeFunction(app, "login", JSONObject().apply {
                        put("input", input)
                        put("password", password)
                    })
                    if (!res.optBoolean("ok", false)) {
                        throw AppException(
                            when (res.optString("reason", "")) {
                                "account_not_found" -> "账号不存在"
                                "too_many_requests" -> "操作太频繁，请稍后再试"
                                else -> "账号或密码错误"
                            }
                        )
                    }
                    val authResponse = res.getJSONObject("session")
                    // 保存 access_token / refresh_token：后续所有请求都靠它通过 RLS
                    AuthSession.save(app, authResponse)
                    val userData = parseUserData(authResponse).getOrNull()
                    // 注册时若没能写入 users 行(例如 signup 未返回会话)，此处补建
                    if (userData != null) {
                        try {
                            ensureUserRow(
                                userData.uid,
                                if (isAccountLogin) input else "",
                                userData.email
                            )
                        } catch (_: Exception) { }
                    }
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
    /**
     * 发送重置验证码。
     *
     * 验证码由服务端生成并通过邮件服务直接发给账号邮箱，客户端全程拿不到 ——
     * 这样任何人都无法「自己指定一个码再立刻用它改密」。
     * 因此本方法不再返回验证码，只表示"已发出"。
     */
    suspend fun requestResetCode(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val res = SupabaseAuthHttp.edgeFunction(app, "reset-password", JSONObject().apply {
                        put("action", "request")
                        put("email", email)
                    })
                    if (!res.optBoolean("ok", false)) {
                        val reason = res.optString("reason", "")
                        // 服务端把邮件服务商的拒绝原因放在 detail 里(如发件邮箱未验证)，打到日志便于定位
                        val detail = res.optString("detail", "")
                        if (detail.isNotBlank()) {
                            android.util.Log.e("RedBookAuth", "reset email rejected: $reason $detail")
                        }
                        throw AppException(
                            when (reason) {
                                "not_registered" -> "该邮箱未注册"
                                "too_soon" -> "验证码已发送，请 1 分钟后再试"
                                "email_failed" -> "验证码邮件发送失败，请稍后重试"
                                else -> "发送失败，请稍后重试"
                            }
                        )
                    }
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
   //校验验证码并重置密码(校验与改密全部在服务端完成)
    suspend fun verifyCodeAndReset(email: String, code: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val res = SupabaseAuthHttp.edgeFunction(app, "reset-password", JSONObject().apply {
                        put("action", "confirm")
                        put("email", email)
                        put("code", code)
                        put("newPassword", newPassword)
                    })
                    if (!res.optBoolean("ok", false)) {
                        val reason = res.optString("reason", "")
                        throw AppException(
                            when (reason) {
                                "not_registered" -> "该邮箱未注册"
                                "no_request" -> "请先获取验证码"
                                "expired" -> "验证码已过期"
                                "too_many_attempts" -> "错误次数过多，请重新获取验证码"
                                "weak_password" -> "密码长度至少需要6位"
                                else -> "验证码错误"
                            }
                        )
                    }
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
    /** 登录后确认 users 表里存在自己的行；缺失则补建(RLS 策略要求 uid = auth.uid()) */
    private suspend fun ensureUserRow(uid: String, account: String, email: String) {
        if (uid.isBlank()) return
        val resp = queryRest("users", "select=uid&uid=eq.$uid&limit=1")
        if ((resp.optJSONArray("users")?.length() ?: 0) > 0) return
        val body = JSONObject().apply {
            put("uid", uid)
            if (email.isNotBlank()) put("email", email)
            if (account.isNotBlank()) put("account", account)
            put("nickname", account)
        }
        upsertRest("/rest/v1/users?on_conflict=uid", body)
        android.util.Log.d("RedBook", "ensureUserRow created row for $uid")
    }

    //更新user表中的数据(指定uid
    private suspend fun patchUserRow(uid: String, body: JSONObject) {
        val bodyString = body.toString()
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(
                    PATCH, "${SupabaseConfig.url}/rest/v1/users?uid=eq.$uid",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody(): ByteArray = bodyString.toByteArray()
                    override fun getBodyContentType(): String = "application/json"
                    override fun getHeaders(): Map<String, String> = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    /** 通用 PATCH：更新指定表满足 filter 的行 */
    private suspend fun patchRest(table: String, filter: String, body: JSONObject) {
        val bodyString = body.toString()
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(
                    PATCH, "${SupabaseConfig.url}/rest/v1/$table?$filter",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody(): ByteArray = bodyString.toByteArray()
                    override fun getBodyContentType(): String = "application/json"
                    override fun getHeaders(): Map<String, String> = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }
    // 邮件发送已移到 Edge Function(reset-password)：
    // 既避免把邮件服务密钥编译进 APK，也让验证码从生成到投递全程不经过客户端。

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

    /** 认证端点(/auth/v1/signup、/auth/v1/token)专用：只带 apikey，不带用户 JWT */
    private suspend fun supabasePost(path: String, body: JSONObject): JSONObject =
        SupabaseAuthHttp.authPost(app, path, body)

    private suspend fun queryRest(table: String, query: String): JSONObject {
        return withAuth {
            suspendCancellableCoroutine { cont ->
                val request = object : StringRequest(
                    GET, "${SupabaseConfig.url}/rest/v1/$table?$query",
                    { response ->
                        try { cont.resume(JSONObject().apply { put("users", JSONArray(response)) }) }
                        catch (e: Exception) { cont.resumeWithException(e) }
                    },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getHeaders() = authHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    private suspend fun insertUserMapping(uid: String, account: String, email: String, nickname: String = "") {
        val body = JSONObject().apply {
            put("uid", uid); put("account", account); put("email", email)
            if (nickname.isNotBlank()) put("nickname", nickname)
        }.toString()
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(
                    POST, "${SupabaseConfig.url}/rest/v1/users",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody(): ByteArray = body.toByteArray()
                    override fun getBodyContentType(): String = "application/json"
                    override fun getHeaders() = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    private fun extractVolleyError(error: VolleyError): String {
        val code = error.networkResponse?.statusCode ?: 0
        val data = error.networkResponse?.data
        val raw = if (data != null) String(data, Charsets.UTF_8) else ""
        val body = if (raw.isNotBlank()) {
            try {
                val json = JSONObject(raw)
                // PostgREST 用 message，GoTrue 用 msg / error_description
                json.optString("message")
                    .ifBlank { json.optString("msg") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { raw }
            } catch (e: Exception) { raw }
        } else error.message ?: "未知错误"
        // RLS / 鉴权类失败单独打点，便于 adb logcat -s RedBookAuth 定位是哪个表被拦
        if (code == 401 || code == 403 || raw.contains("row-level security", ignoreCase = true)) {
            android.util.Log.e("RedBookAuth", "HTTP $code $body")
        }
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

    suspend fun uploadImage(uri: android.net.Uri, app: android.content.Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cr = app.contentResolver
                val mime = cr.getType(uri) ?: inferMime(uri)
                // file:// 本地路径（如保存草稿时缓存到 filesDir）无法通过 contentResolver 打开，直接读文件
                val bytes = if (uri.scheme == "file") {
                    val f = java.io.File(uri.path ?: "")
                    if (!f.exists()) {
                        android.util.Log.e("RedBook", "uploadImage file not found: $uri")
                        return@withContext null
                    }
                    f.readBytes()
                } else {
                    val inputStream = cr.openInputStream(uri)
                    if (inputStream == null) {
                        android.util.Log.e("RedBook", "uploadImage open failed: $uri")
                        return@withContext null
                    }
                    inputStream.use { it.readBytes() }
                }
                val isVideo = mime.contains("video")
                // 图片上传前先缩放压缩，避免几 MB 的原图导致帖子/草稿列表加载缓慢
                val (uploadBytes, uploadMime) = if (isVideo) bytes to mime else compressImage(bytes, mime)
                val ext = when { isVideo -> "mp4"; uploadMime.contains("png") -> "png"; uploadMime.contains("webp") -> "webp"; else -> "jpg" }
                val prefix = if (isVideo) "video:" else ""
                // 按 uid 分目录：storage 写入策略要求对象路径首段等于 auth.uid()，
                // 这样任何账号都无法覆盖或删除别人的文件
                val ownerUid = com.example.redbook.data.local.SessionPrefs.load(app)?.uid.orEmpty()
                val dir = if (ownerUid.isNotBlank()) "$ownerUid/" else ""
                val fileName = "${dir}img_${System.nanoTime()}.$ext"
                uploadToStorage("post-images", fileName, uploadBytes, uploadMime)
                "$prefix${SupabaseConfig.url}/storage/v1/object/public/post-images/$fileName"
            } catch (e: Exception) { 
                android.util.Log.e("RedBook", "uploadImage error: $uri -> ${e.message}")
                null 
            }
        }
    }

    /**
     * 上传前压缩图片：按采样率解码（避免 OOM）→ 依据 EXIF 方向纠正旋转 → 最长边缩放到
     * [maxImageDimension] → 重新编码为 JPEG（含透明通道或原 PNG 用 PNG）。
     * gif 动图不处理；任何异常或压缩后反而更大时回退原始字节，保证上传不被压缩逻辑阻断。
     */
    private fun compressImage(bytes: ByteArray, mime: String): Pair<ByteArray, String> {
        if (mime.contains("gif")) return bytes to mime
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes to mime

            // 采样后仍不小于目标尺寸，尽量贴近 maxImageDimension 再精确缩放
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxImageDimension ||
                bounds.outHeight / (sampleSize * 2) >= maxImageDimension
            ) sampleSize *= 2

            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return bytes to mime

            val orientation = try {
                android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                    .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) { android.media.ExifInterface.ORIENTATION_NORMAL }
            val matrix = android.graphics.Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
                val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val maxSide = maxOf(bitmap.width, bitmap.height)
            if (maxSide > maxImageDimension) {
                val scale = maxImageDimension.toFloat() / maxSide
                val scaled = android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val out = java.io.ByteArrayOutputStream()
            val usePng = mime.contains("png") || bitmap.hasAlpha()
            val format = if (usePng) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG
            bitmap.compress(format, 85, out)
            bitmap.recycle()
            val result = out.toByteArray()
            if (result.isNotEmpty() && result.size < bytes.size) {
                result to (if (usePng) "image/png" else "image/jpeg")
            } else {
                bytes to mime
            }
        } catch (e: Exception) {
            android.util.Log.e("RedBook", "compressImage failed: ${e.message}")
            bytes to mime
        }
    }

    /** 对 file:// URI 兜底推断 MIME（contentResolver.getType 对 file scheme 常返回 null） */
    private fun inferMime(uri: android.net.Uri): String {
        val p = (uri.path ?: "").lowercase()
        return when {
            p.endsWith(".mp4") || p.endsWith(".3gp") || p.endsWith(".webm") ||
                p.endsWith(".mkv") || p.endsWith(".mov") || p.endsWith(".m4v") || p.endsWith(".avi") -> "video/mp4"
            p.endsWith(".png") -> "image/png"
            p.endsWith(".webp") -> "image/webp"
            p.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }

    /** 上传到 Storage：写入策略要求携带用户 JWT，且对象路径首段为 auth.uid() */
    private suspend fun uploadToStorage(bucket: String, fileName: String, bytes: ByteArray, mime: String) {
        withAuth {
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
                        "Authorization" to "Bearer ${AuthSession.access(app)}"
                    )
                }
                request.retryPolicy = DefaultRetryPolicy(60000, 2, 1f)
                requestQueue.add(request)
            }
        }
    }

    suspend fun publishPost(postId: String, title: String, content: String,
                            authorUid: String, authorName: String, authorXhsId: String, imageUrl: String = "", authorAvatar: String = "",
                            ipLocation: String = "", visibility: String = "public") {
        val body = JSONObject().apply {
            put("post_id", postId)
            put("title", title)
            put("content", content)
            put("author_uid", authorUid)
            put("author_name", authorName)
            put("author_xhs_id", authorXhsId)
            put("author_avatar", authorAvatar)
            put("image_url", imageUrl)
            put("visibility", visibility)
            if (ipLocation.isNotBlank()) put("ip_location", ipLocation)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/posts", body)
    }

    /**
     * 帖子是否对 viewerUid 可见：仅自己可见(private)的帖子只有作者本人能看到。
     * 公开(public/空)对所有人生效。帖子数据为空时按可见处理（便于调用方自行兜底）。
     */
    fun isPostVisibleTo(post: org.json.JSONObject?, viewerUid: String): Boolean {
        if (post == null) return true
        val author = post.optString("author_uid", "")
        return when (post.optString("visibility", "public")) {
            "private" -> author.isNotBlank() && author == viewerUid
            else -> true
        }
    }

    /** 过滤掉 viewerUid 不可见的帖子（保留原始顺序） */
    fun filterVisiblePosts(posts: JSONArray, viewerUid: String): JSONArray {
        val result = JSONArray()
        for (i in 0 until posts.length()) {
            val p = posts.getJSONObject(i)
            if (isPostVisibleTo(p, viewerUid)) result.put(p)
        }
        return result
    }

    /** 更新已发布帖子的标题/正文/图片/可见性（保持原帖 id 与时间） */
    suspend fun updatePost(postId: String, title: String, content: String, imageUrl: String, visibility: String) {
        val body = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("image_url", imageUrl)
            put("visibility", visibility)
        }
        patchUserRowByPostId(postId, body)
    }

    /** 设置帖子可见性（public/private） */
    suspend fun setPostVisibility(postId: String, visibility: String) {
        patchUserRowByPostId(postId, JSONObject().apply { put("visibility", visibility) })
    }

    /** 删除帖子（级联删除评论等由数据库外键/触发器处理，这里直接删 posts 行） */
    suspend fun deletePost(postId: String) {
        supabaseDelete("/rest/v1/posts?post_id=eq.$postId")
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
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/drafts?draft_id=eq.$draftId",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody() = bodyStr.toByteArray()
                    override fun getBodyContentType() = "application/json"
                    override fun getHeaders() = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    private suspend fun supabaseDelete(path: String) {
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(DELETE, "${SupabaseConfig.url}$path",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getHeaders() = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    /** 浏览量 +1：帖子可能不是自己的，RLS 不允许直接 PATCH；服务端固定加一，不接受指定增量 */
    suspend fun incrementViewCount(postId: String) {
        supabaseRpc("increment_post_view", JSONObject().apply { put("p_post_id", postId) })
    }

    suspend fun getPosts(): JSONArray {
        val resp = queryRest("posts", "select=*&order=created_at.desc")
        return resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
    }

    suspend fun getPostsByViews(): JSONArray {
        val resp = queryRest("posts", "select=*&order=view_count.desc&limit=6")
        return resp.optJSONArray("users") ?: resp.optJSONArray("posts") ?: JSONArray()
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
        val map = mutableMapOf<String, Triple<String, String, String>>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            map[c.optString("comment_id")] = Triple(c.optString("content"), c.optString("author_name"), c.optString("author_uid"))
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
            obj.put("author_uid", c.optString("author_uid"))
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
                    obj.put("parent_user_id", cached.third)
                } else {
                    val pr = queryRest("comments", "select=content,author_name,author_uid&comment_id=eq.$parentId&limit=1")
                    val parr = pr.optJSONArray("users") ?: pr.optJSONArray("comments") ?: JSONArray()
                    if (parr.length() > 0) {
                        val p = parr.getJSONObject(0)
                        obj.put("parent_content", p.optString("content"))
                        obj.put("parent_user", p.optString("author_name"))
                        obj.put("parent_user_id", p.optString("author_uid"))
                    }
                }
            }
            result.put(obj)
        }
        return result
    }

    // 用户的草稿
    // 历史草稿的 author_xhs_id 可能未回填（与当前 xhs_id 不一致），只用 xhs_id 查会漏掉旧草稿；
    // 因此改为 uid 与 xhs_id 取并集查询（uid 稳定），再统一按更新时间倒序返回。
    suspend fun getUserDrafts(userUid: String, userXhsId: String): JSONArray {
        val filters = mutableListOf<String>()
        if (userUid.isNotBlank()) filters.add("author_uid.eq.$userUid")
        if (userXhsId.isNotBlank()) filters.add("author_xhs_id.eq.$userXhsId")
        if (filters.isEmpty()) return JSONArray()

        val filterExpr = if (filters.size == 1) filters[0] else "or=(${filters.joinToString(",")})"
        val resp = queryRest("drafts", "select=*&$filterExpr&order=updated_at.desc")
        val arr = resp.optJSONArray("users") ?: resp.optJSONArray("drafts") ?: JSONArray()
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
                              authorXhsId: String, postTitle: String, imageUrl: String = "",
                              ipLocation: String = "") {
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
            if (ipLocation.isNotBlank()) put("ip_location", ipLocation)
            put("created_at", System.currentTimeMillis())
        }
        supabasePostBody("/rest/v1/comments", body)
        notifyPostOwner(authorUid, postId, "comment", commentId, content)
    }

    suspend fun insertReply(replyId: String, postId: String, parentId: String, content: String,
                            authorUid: String, authorName: String, authorAvatar: String, authorXhsId: String, postTitle: String, imageUrl: String = "",
                            ipLocation: String = "") {
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
            if (ipLocation.isNotBlank()) put("ip_location", ipLocation)
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

    /** 按 uid 查用户基础资料（头像/昵称/背景/性别/生日/xhs_id/ip），找不到返回 null */
    suspend fun getUserByUid(uid: String): JSONObject? {
        if (uid.isBlank()) return null
        return try {
            val resp = queryRest("users", "select=uid,nickname,avatar_url,background_url,gender,birthday,xhs_id,ip_location&uid=eq.$uid&limit=1")
            val arr = resp.optJSONArray("users") ?: JSONArray()
            if (arr.length() > 0) arr.getJSONObject(0) else null
        } catch (e: Exception) { null }
    }

    /** 写入我的 IP 归属地（users 表），供资料页展示 */
    suspend fun updateUserIpLocation(uid: String, province: String) {
        if (uid.isBlank() || province.isBlank()) return
        try {
            patchUserRow(uid, JSONObject().apply { put("ip_location", province) })
        } catch (_: Exception) { }
    }

    /** 查询用户的 IP 归属地（空则返回空串） */
    suspend fun getUserIpLocation(uid: String): String {
        if (uid.isBlank()) return ""
        return try {
            val resp = queryRest("users", "select=ip_location&uid=eq.$uid&limit=1")
            val arr = resp.optJSONArray("users") ?: JSONArray()
            if (arr.length() > 0) arr.getJSONObject(0).optString("ip_location", "") else ""
        } catch (e: Exception) { "" }
    }

    // ---------- 隐私设置（主页 笔记/评论/收藏/赞过 可见性） ----------

    /** 读取用户主页隐私设置（缺列/查不到时返回默认全开） */
    suspend fun getPrivacySettings(uid: String): com.example.redbook.data.model.PrivacySettings {
        if (uid.isBlank()) return com.example.redbook.data.model.PrivacySettings()
        return try {
            val resp = queryRest(
                "users",
                "select=privacy_posts,privacy_comments,privacy_favorites,privacy_likes,privacy_version&uid=eq.$uid&limit=1"
            )
            val arr = resp.optJSONArray("users") ?: JSONArray()
            if (arr.length() == 0) return com.example.redbook.data.model.PrivacySettings()
            val u = arr.getJSONObject(0)
            val s = com.example.redbook.data.model.PrivacySettings(
                showPosts = if (u.isNull("privacy_posts")) true else u.optBoolean("privacy_posts", true),
                showComments = if (u.isNull("privacy_comments")) true else u.optBoolean("privacy_comments", true),
                showFavorites = if (u.isNull("privacy_favorites")) true else u.optBoolean("privacy_favorites", true),
                showLikes = if (u.isNull("privacy_likes")) true else u.optBoolean("privacy_likes", true),
                version = u.optLong("privacy_version", 0L)
            )
            android.util.Log.d("RedBookPrivacy", "get uid=$uid -> p=${s.showPosts} c=${s.showComments} f=${s.showFavorites} l=${s.showLikes} v=${s.version} raw=$u")
            s
        } catch (e: Exception) {
            android.util.Log.e("RedBookPrivacy", "get FAILED uid=$uid ${e.message}")
            com.example.redbook.data.model.PrivacySettings()
        }
    }

    /** 保存我的主页隐私设置到云端（覆盖写 version 与 4 个开关） */
    suspend fun savePrivacySettings(uid: String, s: com.example.redbook.data.model.PrivacySettings) {
        if (uid.isBlank()) return
        val body = JSONObject().apply {
            put("privacy_posts", s.showPosts)
            put("privacy_comments", s.showComments)
            put("privacy_favorites", s.showFavorites)
            put("privacy_likes", s.showLikes)
            put("privacy_version", s.version)
        }
        try {
            patchUserRow(uid, body)
            android.util.Log.d("RedBookPrivacy", "save ok uid=$uid posts=${s.showPosts} comments=${s.showComments} fav=${s.showFavorites} likes=${s.showLikes} v=${s.version}")
        } catch (e: Exception) {
            android.util.Log.e("RedBookPrivacy", "save FAILED uid=$uid ${e.message}")
        }
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
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(
                    POST, "${SupabaseConfig.url}$path",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody(): ByteArray = bodyString.toByteArray()
                    override fun getBodyContentType(): String = "application/json"
                    override fun getHeaders(): Map<String, String> =
                        authHeaders() + ("Prefer" to "resolution=merge-duplicates,return=minimal")
                }
                requestQueue.add(request.withSupabaseRetry())
            }
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
        return total
    }

    /** 收藏数：我发布的所有帖子/视频的 favorite_count 总和（获赞与收藏弹窗用） */
    suspend fun getFavoriteCount(userUid: String, userXhsId: String): Int {
        if (userUid.isBlank()) return 0
        var total = 0
        try {
            val postsResp = queryRest("posts", "select=favorite_count&author_uid=eq.$userUid")
            val postsArr = postsResp.optJSONArray("users") ?: postsResp.optJSONArray("posts") ?: JSONArray()
            for (i in 0 until postsArr.length()) total += postsArr.getJSONObject(i).optInt("favorite_count", 0)
        } catch (_: Exception) { }
        return total
    }

    // 关注我的用户 uid 集合（用于判断互相关注）
    suspend fun getFollowerUids(userUid: String): Set<String> {
        return try {
            val arr = getMyFollowers(userUid)
            (0 until arr.length()).map { arr.getJSONObject(it).optString("uid", "") }
                .filter { it.isNotBlank() }.toSet()
        } catch (e: Exception) { emptySet() }
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
        // 通知等场景用的是全局的当前昵称/头像，改完立刻跟上，避免之后发出的通知仍是旧名字
        nickname?.takeIf { it.isNotBlank() }?.let { currentUserName = it }
        avatarUrl?.let { currentUserAvatar = it }
        // 把最新署名回填进历史帖子/评论，避免"改名或换头像后新旧内容不一致"
        syncAuthorSnapshot(uid, nickname, avatarUrl)
    }

    /**
     * 把最新的昵称/头像回填到自己历史帖子与评论的冗余列。
     *
     * posts / comments 写入时各自冗余了一份 author_name / author_avatar(避免读时 JOIN)：
     * 改名或换头像不会自动影响这些旧快照，于是出现"前后发布的帖子头像用户名不统一"。
     * 这里按 author_uid 批量改写成 users 表里的最新值，让所有读取路径(首页/详情/视频/
     * 搜索/收藏/主页)拿到的署名一致。RLS 的 posts_update_own / comments_update_own
     * 策略允许改自己的行，故可直接在客户端完成。
     *
     * 单表失败不影响保存资料本身，也不影响另一张表。
     */
    private suspend fun syncAuthorSnapshot(uid: String, nickname: String?, avatarUrl: String?) {
        if (uid.isBlank()) return
        val body = JSONObject()
        nickname?.takeIf { it.isNotBlank() }?.let { body.put("author_name", it) }
        avatarUrl?.let { body.put("author_avatar", it) }
        if (body.length() == 0) return
        val filter = "author_uid=eq.$uid"
        for (table in listOf("posts", "comments")) {
            try {
                patchRest(table, filter, body)
            } catch (e: Exception) {
                android.util.Log.w("RedBook", "syncAuthorSnapshot($table) failed: ${e.message}")
            }
        }
    }

    /**
     * 重算帖子的点赞数与收藏数。
     *
     * 计数完全由 likes / favorites 关系表的行数推导，客户端无法指定增量：
     * 一个人对一个帖子只能留下一条关系记录，所以刷不出虚假数字。
     * 重算是幂等的，历史上若有偏差，下一次交互即自动纠正。
     */
    suspend fun updatePostLike(postId: String) {
        supabaseRpc("sync_post_counts", JSONObject().apply { put("p_post_id", postId) })
    }

    /** 收藏状态变化后重算（同一个服务端函数一次算出点赞与收藏两个数） */
    suspend fun updatePostFav(postId: String) {
        supabaseRpc("sync_post_counts", JSONObject().apply { put("p_post_id", postId) })
    }

    /** 评论点赞数重算：依据 comment_likes 关系表 */
    suspend fun updateCommentLike(commentId: String) {
        supabaseRpc("sync_comment_like_count", JSONObject().apply { put("p_comment_id", commentId) })
    }

    // ---- 评论点赞本机缓存 ----
    // 云端 comment_likes 表未建/不可用时用它兜底，保证同一设备"退出重进"仍记住点赞态；
    // 云表可用时取并集，跨设备也能恢复。
    private fun commentLikePrefs() = app.getSharedPreferences("comment_likes_local", android.content.Context.MODE_PRIVATE)
    private fun commentLikePrefKey(userUid: String) = "u_$userUid"

    private fun localLikedCommentIds(userUid: String): Set<String> =
        if (userUid.isBlank()) emptySet()
        else commentLikePrefs().getStringSet(commentLikePrefKey(userUid), emptySet())?.toSet() ?: emptySet()

    private fun saveLocalCommentLike(userUid: String, commentId: String, liked: Boolean) {
        if (userUid.isBlank() || commentId.isBlank()) return
        val set = localLikedCommentIds(userUid).toMutableSet()
        if (liked) set.add(commentId) else set.remove(commentId)
        commentLikePrefs().edit().putStringSet(commentLikePrefKey(userUid), set).apply()
    }

    // 记录评论(含回复)点赞/取消：先写本机缓存（立即生效），再尽力同步到 comment_likes 云表
    suspend fun recordCommentLike(userUid: String, commentId: String, liked: Boolean) {
        saveLocalCommentLike(userUid, commentId, liked)
        try {
            if (liked) {
                val body = JSONObject().apply {
                    put("like_id", "cl_${userUid}_$commentId")
                    put("user_uid", userUid)
                    put("user_xhs_id", "")
                    put("comment_id", commentId)
                    put("created_at", System.currentTimeMillis())
                }
                supabasePostBody("/rest/v1/comment_likes", body)
            } else {
                supabaseDelete("/rest/v1/comment_likes?user_uid=eq.$userUid&comment_id=eq.$commentId")
            }
        } catch (_: Exception) {
            // 云表不可用则忽略，本机缓存已保证体验
        }
    }

    // 我点赞过的评论 id 集合 = 本机缓存 ∪ 云端（加载评论时用于回填 isLiked）
    suspend fun getLikedCommentIds(userUid: String): Set<String> {
        if (userUid.isBlank()) return emptySet()
        val local = localLikedCommentIds(userUid)
        val cloud = try {
            val resp = queryRest("comment_likes", "select=comment_id&user_uid=eq.$userUid")
            val arr = resp.optJSONArray("users") ?: resp.optJSONArray("comment_likes") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it).getString("comment_id") }.toSet()
        } catch (_: Exception) { emptySet<String>() }
        return local + cloud
    }
//纯写入,不关心返回值,只关心成功失败
    private suspend fun supabasePostBody(path: String, body: JSONObject) {
        val bodyStr = body.toString()
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(POST, "${SupabaseConfig.url}$path",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody() = bodyStr.toByteArray()
                    override fun getBodyContentType() = "application/json"
                    override fun getHeaders() = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
        }
    }

    private suspend fun patchUserRowByPostId(postId: String, body: JSONObject) {
        val bodyStr = body.toString()
        withAuth {
            suspendCancellableCoroutine<String> { cont ->
                val request = object : StringRequest(PATCH, "${SupabaseConfig.url}/rest/v1/posts?post_id=eq.$postId",
                    { cont.resume(it) },
                    { error -> cont.resumeWithException(Exception(extractVolleyError(error))) }
                ) {
                    override fun getBody() = bodyStr.toByteArray()
                    override fun getBodyContentType() = "application/json"
                    override fun getHeaders() = authWriteHeaders()
                }
                requestQueue.add(request.withSupabaseRetry())
            }
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
