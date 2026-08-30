package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.ui.theme.getBlueFill
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class ChatMessage(
    val content: String,
    val time: Long,
    val isMine: Boolean
)

@Composable
fun ChatScreen(
    userName: String,
    avatarUrl: String = "",
    currentUserUid: String = "",
    peerUid: String = "",
    conversationId: String = "",
    repository: RealtimeRepository? = null,
    myAvatarUrl: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val realtimeRepo = repository ?: remember { RealtimeRepository(context.applicationContext as android.app.Application) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 初始加载历史消息；conversationId 为空时先尝试创建
    LaunchedEffect(conversationId, currentUserUid, peerUid) {
        if (currentUserUid.isBlank() || peerUid.isBlank()) return@LaunchedEffect
        var convId = conversationId
        if (convId.isBlank()) {
            convId = try { realtimeRepo.getOrCreateConversation(currentUserUid, peerUid) } catch (_: Exception) { "" }
        }
        if (convId.isBlank()) return@LaunchedEffect
        loading = true
        try {
            val arr = realtimeRepo.getMessages(convId)
            messages.clear()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                messages.add(
                    ChatMessage(
                        content = m.optString("content", ""),
                        time = m.optLong("created_at", 0L),
                        isMine = m.optString("sender_uid") == currentUserUid
                    )
                )
            }
        } catch (_: Exception) { }
        loading = false
    }

    // 实时接收对方新消息：挂到全局连接上，仅处理当前会话的消息
    DisposableEffect(conversationId, currentUserUid, peerUid) {
        val listener = if (conversationId.isNotBlank() && currentUserUid.isNotBlank() && peerUid.isNotBlank()) {
            object : RealtimeRepository.RealtimeListener {
                override fun onNotification(record: JSONObject) { }
                override fun onMessage(record: JSONObject) {
                    val convId = record.optString("conversation_id", "")
                    val sender = record.optString("sender_uid", "")
                    if (convId == conversationId && sender != currentUserUid) {
                        // 去重（避免与初始加载重复）
                        val content = record.optString("content", "")
                        val time = record.optLong("created_at", 0L)
                        if (messages.none { it.content == content && it.time == time && !it.isMine }) {
                            messages.add(ChatMessage(content, time, false))
                        }
                    }
                }
                override fun onStatus(connected: Boolean) { }
            }.also { realtimeRepo.addListener(it) }
        } else null
        onDispose {
            listener?.let { realtimeRepo.removeListener(it) }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        Spacer(modifier = Modifier.size(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.test),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = userName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(
                painter = painterResource(id = R.drawable.menu),
                contentDescription = "菜单",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
        ) {
            items(messages, key = { "${it.time}_${it.content}_${it.isMine}" }) { msg ->
                ChatBubble(
                    message = msg,
                    avatarUrl = if (msg.isMine) myAvatarUrl else avatarUrl,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp)
                .padding(bottom = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(getOutline().copy(alpha = 0.5f))
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "发消息...",
                                    fontSize = 14.sp,
                                    color = getOnSurfaceTertiary()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val text = inputText.trim()
                        if (text.isNotBlank() && currentUserUid.isNotBlank() && peerUid.isNotBlank()) {
                            val time = System.currentTimeMillis()
                            messages.add(ChatMessage(text, time, true))
                            inputText = ""
                            val messageId = "m_${currentUserUid}_${System.nanoTime()}"
                            coroutineScope.launch {
                                try {
                                    var convId = conversationId
                                    if (convId.isBlank()) {
                                        convId = realtimeRepo.getOrCreateConversation(currentUserUid, peerUid)
                                    }
                                    if (convId.isNotBlank()) {
                                        realtimeRepo.sendMessage(messageId, convId, currentUserUid, peerUid, text)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "发送",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, avatarUrl: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.isMine) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(0.85f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MessageBubble(message = message)
                    Spacer(modifier = Modifier.width(8.dp))
                    ChatAvatar(avatarUrl = avatarUrl, size = 32.dp)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(0.85f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChatAvatar(avatarUrl = avatarUrl, size = 32.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    MessageBubble(message = message)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatAvatar(avatarUrl: String, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape)) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.test),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (message.isMine) getBlueFill()
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = message.content,
            fontSize = 14.sp,
            color = if (message.isMine) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
