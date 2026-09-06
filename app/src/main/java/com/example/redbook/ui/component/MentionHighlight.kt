package com.example.redbook.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.redbook.data.repository.AiAssistant
import com.example.redbook.ui.theme.getBlueFill

/**
 * 把文本中出现的 “@小助手” 片段染成品牌蓝(getBlueFill)，
 * 未命中的片段保持默认(继承 Text 外层 color/style)。
 */
@Composable
fun aiMentionHighlight(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val blue = getBlueFill()
    return buildAnnotatedString {
        appendMentionHighlight(this, text, blue)
    }
}

/** 非 Composable 版(供输入框等在回调/remember 内使用) */
fun appendMentionHighlight(out: AnnotatedString.Builder, text: String, blue: Color) {
    val mention = AiAssistant.MENTION
    if (text.isEmpty()) return
    var searchFrom = 0
    while (true) {
        val idx = text.indexOf(mention, searchFrom)
        if (idx < 0) {
            out.append(text.substring(searchFrom))
            break
        }
        out.append(text.substring(searchFrom, idx))
        out.withStyle(SpanStyle(color = blue, fontWeight = FontWeight.Bold)) { out.append(mention) }
        searchFrom = idx + mention.length
    }
}
