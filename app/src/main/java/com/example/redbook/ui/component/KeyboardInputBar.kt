package com.example.redbook.ui.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.repository.AiAssistant
import com.example.redbook.ui.theme.getBlueFill

@Composable
fun KeyboardInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    selectedImages: List<Uri>,
    onAddImageClick: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSend: (String, List<Uri>, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    // 回复前缀被删除（用户主动退出“回复 xxx：”状态）时回调，父级应清空回复目标
    onReplyPrefixRemoved: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    // 输入栏出现时自动聚焦弹出键盘，避免“先弹区域再弹键盘”
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 回复前缀：形如 “回复 xxx：” 或 “回复 @xxx：”
    val replyPrefixRegex = Regex("^回复\\s*(@\\S+|[^\\s：]+)\\s*[：:]")
    val prefixEnd = replyPrefixRegex.find(text)?.range?.last?.plus(1) ?: -1

    // AI 前缀 “@小助手：”—— 删除保护(和回复前缀同机制)：由点击提示或手输触发
    val aiPrefix = AiAssistant.MENTION + "："
    // 兼容用户手输半角/全角冒号
    val aiPrefixRegex = Regex("^@小助手\\s*[：:]")
    val hasAiPrefix = aiPrefixRegex.containsMatchIn(text)
    // 是否输入了 “@”（可触发自动提示补全）；若在回复别人、已有 AI 前缀则不再提示
    val showMentionHint =
        !hasAiPrefix && prefixEnd < 0 &&
            text.isNotEmpty() && !text.startsWith("回复") && text.contains("@")

    // 用 TextFieldValue 控制光标：回复模式下光标始终在文本末尾，且前缀内点击无效
    // 值文本为带“@小助手”蓝色高亮的 AnnotatedString，编辑时始终从纯文本重建以保证输入稳定
    val blue = getBlueFill()
    fun buildStyled(text: String): TextFieldValue {
        val styled = buildAnnotatedString {
            appendMentionHighlight(this, text, blue)
        }
        return TextFieldValue(styled, TextRange(styled.length))
    }
    var textFieldValue by remember(text) {
        mutableStateOf(buildStyled(text))
    }
    LaunchedEffect(text) {
        textFieldValue = buildStyled(text)
    }

    fun commitText(newText: String) {
        textFieldValue = buildStyled(newText)
        onTextChange(newText)
    }

    fun applyAiPrefix() {
        if (!hasAiPrefix) commitText(aiPrefix)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() }

    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            Box {
                TextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        // 回复前缀保护：光标始终在文本末尾，不允许把光标移入前缀内部编辑。
                        // 用户可以直接把“回复 xxx：”前缀删掉退出回复状态：
                        // 一旦前缀被破坏，说明删除键已删到前缀，剥离残留前缀后转为普通评论输入。
                        if (prefixEnd >= 0 && text.isNotEmpty()) {
                            if (newText.isEmpty() || !newText.startsWith(text.take(prefixEnd))) {
                                // 前缀被破坏：剥离前缀残骸，退出回复态，保留剩余文本继续普通输入
                                val remainder = newText.replaceFirst(Regex("^回复\\s*(@\\S*|[^\\s：]*)\\s*[：:]?"), "")
                                textFieldValue = TextFieldValue(remainder, TextRange(remainder.length))
                                onTextChange(remainder)
                                onReplyPrefixRemoved()
                                return@TextField
                            }
                            textFieldValue = TextFieldValue(newText, TextRange(newText.length))
                            onTextChange(newText)
                            return@TextField
                        }
                        // AI 前缀保护：@小助手： 前缀可被删除退出 AI 状态
                        // 一旦前缀被破坏，说明删除键已删到前缀，剥离残留前缀后转为普通评论输入
                        if (hasAiPrefix) {
                            if (!newText.startsWith(aiPrefix)) {
                                val remainder = newText.replaceFirst(Regex("^@小助手\\s*[：:]?"), "").trim()
                                textFieldValue = TextFieldValue(remainder, TextRange(remainder.length))
                                onTextChange(remainder)
                                return@TextField
                            }
                            // 前缀后允许输入任何内容
                        }
                        // 从纯文本重建带 @高亮 的 value，保持前缀蓝色
                        textFieldValue = buildStyled(newText)
                        onTextChange(newText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 200.dp)
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    placeholder = { Text("说点什么...", fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = Int.MAX_VALUE
                )

                // 输入 @ 后的自动提示：“@小助手”
                if (showMentionHint) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // 注意：不能用负 padding（会抛 IllegalArgumentException: Padding must be non-negative），用 offset 实现上浮
                            .offset(x = 4.dp, y = (-30).dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { applyAiPrefix() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = AiAssistant.AVATAR_RES),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI 小助手",
                                fontSize = 13.sp,
                                color = getBlueFill()
                            )
                        }
                    }
                }
            }


            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items=selectedImages,   key = { it}
                    ) { imageUri ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .clickable { onRemoveImage(imageUri) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close_ring_fill),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable{
                                            onRemoveImage(imageUri)
                                        },
                                    tint = Color.Unspecified

                                )
                            }
                        }
                    }


                    if (selectedImages.size < 9) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                    .clickable { onAddImageClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add_square),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // @ 小助手（快速提及 AI 助手，点击直接填充“@小助手：”）
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (hasAiPrefix) getBlueFill().copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = !hasAiPrefix) { applyAiPrefix() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = AiAssistant.AVATAR_RES),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasAiPrefix) "小助手" else "@ 小助手",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasAiPrefix) getBlueFill() else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.image),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onAddImageClick() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )


                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (text.isNotBlank() || selectedImages.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable {
                            if (text.isNotBlank() || selectedImages.isNotEmpty()) {
                                onSend(text, selectedImages, hasAiPrefix)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "发送",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (text.isNotBlank() || selectedImages.isNotEmpty())
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}