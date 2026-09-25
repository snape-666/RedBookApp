package com.example.redbook.data.repository

import android.app.Application
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Request.Method.POST
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.redbook.data.local.AuthSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Supabase 鉴权公用的 HTTP 支撑，被 SupabaseAuthRepository 与 RealtimeRepository 共用。
 *
 *  - 统一构造带用户 JWT 的请求头：RLS 靠 Authorization 解析 auth.uid()，
 *    不带 JWT 的请求会以 anon 角色发出，开启 RLS 后读为空、写被拒。
 *  - 命中 401 时用 refresh_token 续期并重试一次。
 *  - 续期只保留这一处实现：Supabase 的 refresh_token 是单次有效的，
 *    若两个仓库各自续期，会互相把对方的 refresh_token 作废，导致随机掉登录。
 */
/**
 * 给请求套上统一的重试策略。
 *
 * Volley 默认的 DefaultRetryPolicy 只用 2.5 秒超时、且不重试：Supabase 是海外主机，
 * 冷连接要现做 DNS+TCP+TLS，或者复用的长连接恰好被服务端回收，首包稍慢就直接超时。
 * 这类失败没有响应体，错误码被记为 0，界面表现为「0: java.io.InterruptedIOException: timeout」，
 * 退出页面再进一次(连接已预热)又正常 —— 也就是"切回主页报 0，重新切才展示"。
 * 统一放宽到 8 秒并重试 2 次，覆盖超时/连接被重置这类瞬时失败。
 */
fun <T> Request<T>.withSupabaseRetry(): Request<T> {
    // 重试 2 次：这条链路经常在建立连接时被重置(SocketException: Connection reset)，
    // 这种失败是**立刻**返回的、不占超时，所以多试两次几乎不增加等待，
    // 却能把这"半次成半次败"的抖动挡掉大半。写请求用的是确定性主键(comment_id/like_id…),
    // 万一重复提交也只会在主键上冲突，不会写出重复数据。
    retryPolicy = DefaultRetryPolicy(8_000, 2, 1f)
    return this
}

object SupabaseAuthHttp {

    @Volatile private var appRef: Application? = null
    // 与另外两个队列共用同一个 OkHttp 传输层实例，共享连接池
    private val queue by lazy { Volley.newRequestQueue(appRef!!, OkHttpStack.shared) }
    private val refreshMutex = Mutex()

    /** 续期最多试几次(瞬时网络失败不值得直接判死)；见 refresh() */
    private const val REFRESH_ATTEMPTS = 3

    private fun ensureInit(app: Application) {
        if (appRef == null) appRef = app
    }

    /** 统一请求头；write=true 时附带 Prefer: return=minimal */
    fun headers(app: Application, write: Boolean = false): Map<String, String> {
        val headers = mutableMapOf(
            "apikey" to SupabaseConfig.anonKey,
            "Content-Type" to "application/json"
        )
        val token = AuthSession.access(app)
        if (token.isNotBlank()) headers["Authorization"] = "Bearer $token"
        if (write) headers["Prefer"] = "return=minimal"
        return headers
    }

    fun isAuthError(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.startsWith("401") ||
            msg.contains("JWT expired", ignoreCase = true) ||
            msg.contains("invalid JWT", ignoreCase = true)
    }

    /**
     * 续期结果。
     *
     * 必须把"没续上"拆成两种：会话真失效要回登录页重新登录；
     * 而超时/连接被重置这类只是这一次没成功，令牌还留着，下次还能续 ——
     * 后者若也按失效处理，链路一抖就把正在用的人踢下线。
     */
    enum class RefreshResult { Refreshed, SessionExpired, TransientFailure }

    /** 从 describe() 拼出的 "状态码: 内容" 里取回状态码；纯网络错误拿不到，返回 0 */
    private fun statusCodeOf(message: String?): Int =
        message?.substringBefore(':')?.trim()?.toIntOrNull() ?: 0

    /**
     * 用 refresh_token 换新的 access_token。
     *
     * [staleToken] 是触发本次续期时用的那个已失效令牌：
     * 拿到锁后若发现内存里的令牌已经变了，说明别的协程刚续过期，
     * 直接放行即可，避免重复消费 refresh_token。
     *
     * 注意不能简单地"本地没到期就当不用续"：服务端撤销会话、时钟偏差等情况
     * 都会出现本地认为有效但请求已被 401 拒绝，那样会拿同一个令牌空转一次。
     *
     * 只有服务端明确拒绝这个 refresh_token(400/401/403)才判定会话失效并清空本地令牌；
     * 拿不到状态码(超时、连接被重置 → 0)、429、5xx 都只当这次没成功，令牌留着下次再试。
     */
    suspend fun refresh(app: Application, staleToken: String = ""): RefreshResult {
        ensureInit(app)
        return refreshMutex.withLock {
            val current = AuthSession.access(app)
            if (staleToken.isNotBlank() && current.isNotBlank() && current != staleToken) {
                return@withLock RefreshResult.Refreshed
            }
            val refreshToken = AuthSession.refreshToken(app)
            // 本地压根没有 refresh_token，确实无法恢复会话
            if (refreshToken.isBlank()) return@withLock RefreshResult.SessionExpired

            // 瞬时失败(连接被重置、超时)在国内网络下很常见，而一次失败就会让整个 App
            // 变成"网络异常"且重试也没用 —— 所以这里连试几次再下结论，500ms 间隔即可。
            var last: Exception? = null
            for (attempt in 0 until REFRESH_ATTEMPTS) {
                try {
                    val resp = authPost(
                        app,
                        "/auth/v1/token?grant_type=refresh_token",
                        JSONObject().apply { put("refresh_token", refreshToken) }
                    )
                    AuthSession.save(app, resp)
                    android.util.Log.d("RedBookAuth", "access token refreshed")
                    return@withLock RefreshResult.Refreshed
                } catch (e: Exception) {
                    last = e
                    val code = statusCodeOf(e.message)
                    // 服务端明确说这个 refresh_token 不认，重试也是白费
                    if (code == 400 || code == 401 || code == 403) {
                        android.util.Log.e("RedBookAuth", "refresh failed(session expired): ${e.message}")
                        AuthSession.clear(app)
                        return@withLock RefreshResult.SessionExpired
                    }
                    android.util.Log.w(
                        "RedBookAuth",
                        "refresh attempt ${attempt + 1}/$REFRESH_ATTEMPTS failed: ${e.message}"
                    )
                    if (attempt < REFRESH_ATTEMPTS - 1) delay(500)
                }
            }
            android.util.Log.e("RedBookAuth", "refresh failed(transient): ${last?.message}")
            RefreshResult.TransientFailure
        }
    }

    /** 令牌已过期(或即将过期)时续期，供需要长连接的场景(WebSocket 重连)主动调用 */
    suspend fun refreshIfExpired(app: Application): RefreshResult {
        if (!AuthSession.isExpired(app)) return RefreshResult.Refreshed
        return refresh(app, AuthSession.access(app))
    }

    /**
     * 带自动续期的请求包装：命中 401 时续期并重试一次。
     *
     * 只有确认会话失效才回调回登录页；只是这次续期没成功(网络/服务端)时，
     * 保留令牌、把失败抛给调用方显示，绝不借机把用户踢下线。
     */
    suspend fun <T> withAuth(app: Application, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (!isAuthError(e)) throw e
            // 带上触发本次 401 的旧令牌，便于判断是否已被别的协程续过期
            val stale = AuthSession.access(app)
            when (refresh(app, stale)) {
                RefreshResult.Refreshed -> block()
                RefreshResult.SessionExpired -> {
                    AuthSession.onSessionExpired?.invoke()
                    throw e
                }
                RefreshResult.TransientFailure -> {
                    // 别把原始 401 甩给界面：它只是"令牌过期 + 这次没续上"，
                    // 对用户来说就是一次失败的网络请求
                    android.util.Log.w("RedBookAuth", "refresh transient failure, session kept: ${e.message}")
                    throw Exception("网络异常，请重试")
                }
            }
        }
    }

    /** 认证端点(/auth/v1/signup、/auth/v1/token)专用：只带 apikey，不带用户 JWT */
    suspend fun authPost(app: Application, path: String, body: JSONObject): JSONObject {
        ensureInit(app)
        return suspendCancellableCoroutine { cont ->
            val request = object : JsonObjectRequest(
                POST, "${SupabaseConfig.url}$path", body,
                { cont.resume(it) },
                { error -> cont.resumeWithException(Exception(describe(error))) }
            ) {
                override fun getHeaders() = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Content-Type" to "application/json"
                )
            }
            queue.add(request.withSupabaseRetry())
        }
    }

    /**
     * 调用 Edge Function。
     *
     * 第三方密钥（Brevo / DeepSeek / 豆包）都已搬到函数里，客户端只负责转发请求，
     * 因此 APK 中不再包含任何可被逆向盗用的凭证。
     *
     * 未登录时用 anonKey 充当 Authorization：函数以 --no-verify-jwt 部署，
     * 网关不做校验，真正的把关在函数内部（ai-assistant 会自行校验用户 JWT）。
     *
     * 超时给到 120 秒且不自动重试 —— AI 回答要走两个模型的降级链，
     * 而重试一次就是再花一次模型额度。
     *
     * 必须用 JsonObjectRequest 而不是 StringRequest：函数里的中文回答(小助手的回复)
     * 是在 JsonObjectRequest 下才能正确解码的。StringRequest 在响应头没带 charset 时
     * 会按 ISO-8859-1 解码，中文立刻变乱码；而退出重进时评论是走 PostgREST 查询回来的
     * (响应头带 charset=utf-8)，所以那时又恢复正常 —— 这正是"回答是乱码、重进又好了"的原因。
     * JsonObjectRequest 缺省按 UTF-8 解码，与本文件的 authPost 一致。
     */
    suspend fun edgeFunction(app: Application, name: String, body: JSONObject): JSONObject {
        ensureInit(app)
        return withAuth(app) {
            suspendCancellableCoroutine { cont ->
                val bearer = AuthSession.access(app).ifBlank { SupabaseConfig.anonKey }
                val request = object : JsonObjectRequest(
                    POST, "${SupabaseConfig.url}/functions/v1/$name", body,
                    { json -> cont.resume(json) },
                    { error -> cont.resumeWithException(Exception(describe(error))) }
                ) {
                    override fun getHeaders(): Map<String, String> = mapOf(
                        "apikey" to SupabaseConfig.anonKey,
                        "Authorization" to "Bearer $bearer",
                        "Content-Type" to "application/json"
                    )
                }
                request.retryPolicy = DefaultRetryPolicy(120_000, 0, 1f)
                queue.add(request)
            }
        }
    }

    private fun describe(error: VolleyError): String {
        val code = error.networkResponse?.statusCode ?: 0
        val raw = error.networkResponse?.data?.let { String(it, Charsets.UTF_8) } ?: ""
        val body = if (raw.isNotBlank()) {
            try {
                val json = JSONObject(raw)
                json.optString("message")
                    .ifBlank { json.optString("msg") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { raw }
            } catch (e: Exception) { raw }
        } else error.message ?: "未知错误"
        return "$code: $body"
    }
}
