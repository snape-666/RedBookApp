package com.example.redbook.data.repository

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 评论区 AI 小助手：
 *  - 识别“@小助手：问题”格式的用户评论（一级评论）
 *  - 实际的模型调用已迁到 Supabase Edge Function(ai-assistant)：
 *    豆包多模态（火山方舟，OpenAI 兼容）优先、DeepSeek 纯文本兜底的双保险降级链
 *    原样保留在服务端执行，客户端只负责转发请求。
 *  - 这样豆包 / DeepSeek 的密钥不再被编译进 APK（项目未混淆，反编译即可盗用）
 *
 * 小助手的回复也由该函数以 ai_assistant 身份写入 comments 表，
 * 客户端因此不再具备冒充小助手的能力。
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
    private const val MAX_QUESTION_LEN = 300

    fun isAiUser(uid: String): Boolean = uid == UID

    /** 从文本中提取 @小助手： 之后的问题正文 */
    fun parseQuestion(text: String): String? {
        val m = triggerPrefixRegex.find(text) ?: return null
        val q = text.substring(m.range.last + 1).trim()
        return q.ifBlank { null }
    }

    /** 帖子是否为视频帖（image_url 带 video: 前缀或本身就是视频地址） */
    fun isVideoUrl(url: String): Boolean = url.startsWith("video:") || url.startsWith("http") && url.endsWith(".mp4")

    data class AiReply(val replyId: String, val text: String)

    /**
     * 主流程：解析提问 -> 交给服务端生成回答并写入回复 -> 返回结果。
     *
     * @param postTitle 帖子标题（作为上下文，可为空）
     * @param postContent 帖子正文（作为上下文，可为空）
     * @param postImages 帖子图片 URL 列表（多图逗号分隔后传入），视频帖传空
     * @param postVideoUrl 帖子视频地址（带 video: 前缀亦可），纯图片帖传空
     * @return 成功后返回 [AiReply]（含 replyId 与回答文本）；提问格式不对或服务端失败时返回 null。
     */
    suspend fun askAndReply(app: Application, postId: String, parentCommentId: String, rawText: String,
                            postTitle: String = "", postContent: String = "",
                            postImages: List<String> = emptyList(), postVideoUrl: String = ""): AiReply? {
        val question = parseQuestion(rawText) ?: run {
            android.util.Log.w("AiAssistant", "askAndReply skip: 无法解析问题 rawText=[$rawText]")
            return null
        }
        if (question.length > MAX_QUESTION_LEN) {
            android.util.Log.w("AiAssistant", "askAndReply skip: 问题过长 ${question.length}")
            return null
        }

        // 归一化帖子素材：图片只保留 http(s) 可访问地址；视频地址去掉 video: 前缀
        val images = postImages
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
        val video = postVideoUrl.trim().removePrefix("video:")
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()

        return try {
            withContext(Dispatchers.IO) {
                val res = SupabaseAuthHttp.edgeFunction(app, "ai-assistant", JSONObject().apply {
                    put("postId", postId)
                    put("parentCommentId", parentCommentId)
                    put("question", question)
                    put("postTitle", postTitle)
                    put("postContent", postContent)
                    put("images", JSONArray(images))
                    put("video", video)
                })
                if (!res.optBoolean("ok", false)) {
                    android.util.Log.e("AiAssistant", "回答失败: ${res.optString("reason", "unknown")}")
                    return@withContext null
                }
                val answer = res.optString("answer", "")
                val replyId = res.optString("replyId", "")
                if (answer.isBlank() || replyId.isBlank()) null else AiReply(replyId, answer)
            }
        } catch (e: Exception) {
            android.util.Log.e("AiAssistant", "AI 请求异常: ${e.message}")
            null
        }
    }
}
