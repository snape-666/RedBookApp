package com.example.redbook.data.repository

import android.app.Application
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.POST
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.redbook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 评论区 AI 小助手：
 *  - 识别“@小助手：问题”格式的用户评论（一级评论）
 *  - 调用豆包多模态模型（火山方舟，OpenAI 兼容）生成回答，支持直接传帖子图片/视频 URL 分析画面
 *  - 未配置 VISION_API_KEY 时自动降级为 DeepSeek 纯文本分析（标题+正文）
 *  - 以“小助手”身份在该评论下追加一条二级回复（parent_id = 该评论 id），并存云端 comments 表
 *
 * 仅当前登录用户在客户端主动发布一条 @小助手 提问时，才触发回答（无轮询/监听其他用户提问），
 * 因此不存在一个提问被多人重复触发的“重复回答”问题。
 */
object AiAssistant {

    // 小助手固定身份（与云端 comments 表 author_uid 对应）
    const val UID = "ai_assistant"
    const val NAME = "小助手"
    // 注意：资源 id 不能是 const(避免 Kotlin FIR 常量解释器跨类解析 R 字段崩溃)，保持为运行时常量
    val AVATAR_RES: Int = com.example.redbook.R.drawable.ic_ai_assistant
    // 小助手发的评论不带 ip 展示（避免被当成真人 IP）
    const val IP = ""

    const val MENTION = "@小助手"
    /** 只要文本中出现 “@小助手：” 就视为提问（回复小助手时 “回复 @小助手：” 同样触发） */
    private val triggerPrefixRegex = Regex("@小助手\\s*[：:]")
    private const val MAX_ANSWER_LEN = 500
    private const val REQUEST_TIMEOUT_MS = 60_000L

    // 文本分析降级模型（DeepSeek）：仅标题+正文文字
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/chat/completions"
    private const val DEEPSEEK_MODEL = "deepseek-chat"

    // 视觉分析模型（豆包/火山方舟）：由 supabase.properties 的 VISION_* 注入
    private val visionBaseUrl: String
        get() = BuildConfig.VISION_BASE_URL.ifBlank {
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        }
    private val visionModel: String
        get() = BuildConfig.VISION_MODEL.ifBlank { "ep-XXXXXXXX" }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun isAiUser(uid: String): Boolean = uid == UID

    /** 从文本中提取 @小助手： 之后的问题正文 */
    fun parseQuestion(text: String): String? {
        val m = triggerPrefixRegex.find(text) ?: return null
        val q = text.substring(m.range.last + 1).trim()
        return q.ifBlank { null }
    }

    /** 是否已配置视觉模型（豆包）Api Key */
    val visionEnabled: Boolean
        get() = BuildConfig.VISION_API_KEY.isNotBlank()

    /** 帖子是否为视频帖（image_url 带 video: 前缀或本身就是视频地址） */
    fun isVideoUrl(url: String): Boolean = url.startsWith("video:") || url.startsWith("http") && url.endsWith(".mp4")

    /**
     * 主流程：解析提问 -> 调视觉模型（带图片/视频URL）或 DeepSeek 文本 -> 以小助手身份插入二级回复。
     * @param postTitle 帖子标题（作为上下文，可为空）
     * @param postContent 帖子正文（作为上下文，可为空）
     * @param postImages 帖子图片 URL 列表（多图逗号分隔后传入），视频帖传空
     * @param postVideoUrl 帖子视频地址（带 video: 前缀亦可），纯图片帖传空
     * @return 成功后返回 [AiReply]（含 replyId 与回答文本）；未配置任何 key 或提问格式不对时返回 null。
     */
    suspend fun askAndReply(app: Application, postId: String, parentCommentId: String, rawText: String,
                            postTitle: String = "", postContent: String = "",
                            postImages: List<String> = emptyList(), postVideoUrl: String = ""): AiReply? {
        val keyOk = BuildConfig.VISION_API_KEY.isNotBlank() || BuildConfig.DEEPSEEK_API_KEY.isNotBlank()
        if (!keyOk) {
            android.util.Log.w("AiAssistant", "askAndReply skip: VISION/DEEPSEEK API_KEY 均未配置")
            return null
        }
        val question = parseQuestion(rawText) ?: run {
            android.util.Log.w("AiAssistant", "askAndReply skip: 无法解析问题 rawText=[$rawText]")
            return null
        }
        if (question.length > 300) {
            android.util.Log.w("AiAssistant", "askAndReply skip: 问题过长 ${question.length}")
            return null
        }

        // 归一化帖子素材：图片列表只保留 http(s) 可访问地址；视频地址去掉 video: 前缀
        val images = postImages
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
        val video = postVideoUrl.trim().removePrefix("video:")
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
        // 视觉模型开启且帖子有图片/视频时走多模态；否则走纯文本
        val visionKeyOk = BuildConfig.VISION_API_KEY.isNotBlank()
        val textKeyOk = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()
        val useVision = visionKeyOk && (images.isNotEmpty() || video.isNotBlank())

        // 双保险：按优先级尝试，任一成功即用
        //   有媒体时：豆包(视觉) 优先 -> 失败降级 DeepSeek(文本)
        //   无媒体时：DeepSeek(文本) 优先 -> 失败降级豆包(纯文本)
        val attempts: List<Pair<String, suspend () -> String?>> = buildList {
            if (useVision) add("豆包视觉" to { requestVision(question, postTitle, postContent, images, video) })
            if (textKeyOk) add("DeepSeek文本" to { requestText(question, postTitle, postContent) })
            if (!useVision && visionKeyOk) add("豆包文本" to { requestVision(question, postTitle, postContent, emptyList(), "") })
        }
        val answer = try {
            withContext(Dispatchers.IO) {
                var result: String? = null
                for ((name, block) in attempts) {
                    val r = try {
                        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { block() }
                    } catch (e: Exception) {
                        android.util.Log.w("AiAssistant", "$name 请求失败: ${e.message}")
                        null
                    }
                    if (!r.isNullOrBlank()) { result = r; break }
                }
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("AiAssistant", "AI 请求异常: ${e.message}")
            null
        }
        if (answer.isNullOrBlank()) {
            android.util.Log.e("AiAssistant", "模型返回空/超时 question=[$question]")
            return null
        }

        val replyId = "ai_${System.currentTimeMillis()}"
        try {
            insertAiReply(app, replyId, postId, parentCommentId, answer)
        } catch (e: Exception) {
            android.util.Log.e("AiAssistant", "云端插入 AI 回复失败: ${e.message}")
            return null
        }
        return AiReply(replyId, answer)
    }

    data class AiReply(val replyId: String, val text: String)

    /** 调豆包多模态 /chat/completions：text + image_url + video_url 直传分析画面 */
    private suspend fun requestVision(question: String, postTitle: String, postContent: String,
                                      images: List<String>, video: String): String {
        val userContent = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", question))
            if (video.isNotBlank()) {
                put(JSONObject().put("type", "video_url").put("video_url", JSONObject().put("url", video)))
            }
            images.forEach { img ->
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", img)))
            }
        }
        val bodyJson = JSONObject()
            .put("model", visionModel)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", buildSystemPrompt(postTitle, postContent, video.isNotBlank())))
                put(JSONObject().put("role", "user").put("content", userContent))
            })
            .put("max_tokens", 800)
            .put("temperature", 0.8)
            .put("stream", false)

        val httpRequest = Request.Builder()
            .url(visionBaseUrl)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer ${BuildConfig.VISION_API_KEY}")
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        val result = execute(httpRequest)
        return normalizeAnswer(result)
    }

    /** 调 DeepSeek /chat/completions：纯文本降级 */
    private suspend fun requestText(question: String, postTitle: String, postContent: String): String {
        val bodyJson = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", buildSystemPrompt(postTitle, postContent, false)))
                put(JSONObject().put("role", "user").put("content", question))
            })
            .put("max_tokens", 700)
            .put("temperature", 0.8)
            .put("stream", false)

        val httpRequest = Request.Builder()
            .url(DEEPSEEK_BASE_URL)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        val result = execute(httpRequest)
        return normalizeAnswer(result)
    }

    private suspend fun execute(httpRequest: Request): String =
        suspendCancellableCoroutine { cont ->
            httpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            if (cont.isActive) cont.resumeWithException(Exception("HTTP ${it.code} body=${it.body?.string()?.take(200)}"))
                            return
                        }
                        try {
                            val json = JSONObject(it.body?.string() ?: "{}")
                            val content = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            if (cont.isActive) cont.resume(content)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(Exception("响应解析失败: ${e.message}", e))
                        }
                    }
                }
            })
        }

    private fun normalizeAnswer(raw: String): String =
        raw.trim().replace(Regex("^[\\n\\s]+", RegexOption.MULTILINE), "").take(MAX_ANSWER_LEN)

    /** 以“小助手”身份把回答写入 comments 表（parent_id = 提问评论 id），等待写入完成 */
    private suspend fun insertAiReply(app: Application, replyId: String, postId: String, parentCommentId: String, answer: String) {
        val url = "${SupabaseConfig.url}/rest/v1/comments"
        val body = JSONObject().apply {
            put("comment_id", replyId)
            put("post_id", postId)
            put("parent_id", parentCommentId)
            put("content", answer)
            put("author_uid", UID)
            put("author_name", NAME)
            put("author_avatar", "")
            put("author_xhs_id", "")
            put("post_title", "")
            put("image_url", "")
            put("ip_location", IP)
            put("created_at", System.currentTimeMillis())
        }
        val queue = Volley.newRequestQueue(app)
        suspendCancellableCoroutine<Unit> { cont ->
            val req = object : StringRequest(POST, url,
                {
                    cont.resume(Unit)
                },
                { error: VolleyError ->
                    if (cont.isActive) cont.resumeWithException(Exception("HTTP ${error.networkResponse?.statusCode ?: error.message}"))
                }
            ) {
                override fun getBodyContentType(): String = "application/json; charset=utf-8"
                override fun getBody(): ByteArray = body.toString().toByteArray(Charsets.UTF_8)
                override fun getHeaders(): Map<String, String> = mapOf(
                    "apikey" to SupabaseConfig.anonKey,
                    "Authorization" to "Bearer ${SupabaseConfig.anonKey}",
                    "Content-Type" to "application/json; charset=utf-8",
                    "Prefer" to "return=minimal"
                )
            }
            req.retryPolicy = DefaultRetryPolicy(10_000, 1, 1f)
            cont.invokeOnCancellation { queue.cancelAll { it.tag?.toString()?.startsWith("ai_insert") == true } }
            req.tag = "ai_insert_${System.currentTimeMillis()}"
            queue.add(req)
        }
    }

    private fun buildSystemPrompt(postTitle: String, postContent: String, hasMedia: Boolean): String {
        val ctx = buildString {
            append("你叫「小助手」，是小红书 App 评论区里的 AI 助手。")
            val mediaNote = if (hasMedia) "用户已附上帖子的图片/视频内容，请基于画面内容分析帖子。" else ""
            if (mediaNote.isNotBlank()) append(mediaNote)
            if (postTitle.isNotBlank()) {
                append("\n帖子标题：「${postTitle}」")
            }
            val content = postContent.trim()
            if (content.isNotBlank()) {
                val brief = if (content.length > 300) content.take(300) + "…" else content
                append("\n帖子内容：「${brief}」")
            }
            append("\n请结合以上帖子内容回答用户对帖子的提问。用简体中文、口语化、简洁地回答问题，不要超过 200 字，不要使用 Markdown/表情符号。")
        }
        return ctx
    }
}