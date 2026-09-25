package com.example.redbook.data.repository

import com.android.volley.AuthFailureError
import com.android.volley.Header
import com.android.volley.Request
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HttpResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Request as OkRequest

/**
 * 用 OkHttp 顶替 Volley 默认的 HttpURLConnection 传输层。
 *
 * 为什么换：这台设备到 Supabase(海外主机) 的链路经常在**建连阶段**就被重置
 * (实测丢包 50%)，而默认传输层每次请求都要现做 TCP+TLS 握手，且没有 HTTP/2。
 * 换成 OkHttp 之后：
 *   - HTTP/2 多路复用：一屏几十个请求压在一两条连接上，握手次数大幅减少；
 *   - 连接池跨请求复用，配合同一个实例被三个 Volley 队列共用（见 [shared]）；
 *   - retryOnConnectionFailure：连接失效时自动换一条连接重试。
 *
 * 只替换传输层，所有请求代码（请求头、body、超时/重试策略）都不用动。
 */
class OkHttpStack(private val client: OkHttpClient) : BaseHttpStack() {

    /**
     * 边缘函数调用要等模型出结果（AI 回答会跑两个模型，最多几十秒），
     * 用短超时会把正常回答掐断；其余请求保持短超时，失败得快、好让重试策略接手。
     * newBuilder() 出来的实例与原实例共享连接池和线程池，不会多开连接。
     */
    private val longTimeoutClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(LONG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    @Throws(IOException::class, AuthFailureError::class)
    override fun executeRequest(
        request: Request<*>,
        additionalHeaders: MutableMap<String, String>
    ): HttpResponse {
        val method = methodName(request.method)
        val builder = OkRequest.Builder().url(request.url)

        // 附加头(重试/缓存要求带的)先加，请求自己的头后加 → 后者优先
        for ((name, value) in additionalHeaders) builder.addHeader(name, value)
        for ((name, value) in request.headers) builder.header(name, value)

        val mediaType = (request.bodyContentType ?: "application/json").toMediaTypeOrNull()
        val bodyBytes = request.body
        if (bodyBytes != null) {
            builder.method(method, bodyBytes.toRequestBody(mediaType))
        } else if (method == "POST" || method == "PUT" || method == "PATCH") {
            // OkHttp 要求这三个方法必须带 body，无参请求补一个空 body
            builder.method(method, ByteArray(0).toRequestBody(mediaType))
        } else {
            builder.method(method, null)
        }

        val callClient = if (request.url.contains("/functions/v1/")) longTimeoutClient else client
        callClient.newCall(builder.build()).execute().use { response ->
            val rawHeaders = response.headers
            val headers = ArrayList<Header>(rawHeaders.size)
            for (i in 0 until rawHeaders.size) {
                headers.add(Header(rawHeaders.name(i), rawHeaders.value(i)))
            }
            return HttpResponse(response.code, headers, response.body?.bytes() ?: ByteArray(0))
        }
    }

    private fun methodName(method: Int): String = when (method) {
        Request.Method.GET -> "GET"
        Request.Method.POST -> "POST"
        Request.Method.PUT -> "PUT"
        Request.Method.DELETE -> "DELETE"
        Request.Method.HEAD -> "HEAD"
        Request.Method.OPTIONS -> "OPTIONS"
        Request.Method.TRACE -> "TRACE"
        Request.Method.PATCH -> "PATCH"
        else -> "GET"
    }

    companion object {
        /** 普通请求的读超时：短一点，卡住了就赶紧失败交给 Volley 重试 */
        private const val READ_TIMEOUT_SECONDS = 25L
        /** 边缘函数(AI 回答)的读超时 */
        private const val LONG_READ_TIMEOUT_SECONDS = 150L

        /**
         * 全局共用同一个实例 —— 三个 Volley 队列(SupabaseAuthRepository / SupabaseAuthHttp /
         * RealtimeRepository)都传它进去，这样它们共享同一个 OkHttp 连接池，
         * 连接复用率最高、握手次数最少。
         */
        val shared: OkHttpStack by lazy {
            OkHttpStack(
                OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    // 写超时只约束"单次写入没人接收"，视频上传要留足
                    .writeTimeout(300, TimeUnit.SECONDS)
                    // 连接失效(被重置/超时)时自动换一条连接重试
                    .retryOnConnectionFailure(true)
                    .build()
            )
        }
    }
}
