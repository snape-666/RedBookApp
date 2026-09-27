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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.redbook.R
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.theme.getBlueFill
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import com.example.redbook.ui.utils.formatChatTime
import com.example.redbook.util.ImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class ChatMessage(
    val content: String,
    val time: Long,
    val isMine: Boolean,
    val mediaUrl: String = "",
    val messageId: String = ""
)

// 发送/撤回等网络操作不能挂在 rememberCoroutineScope 下：一退出聊天页该 scope 就被取消，
// 图片上传到一半被中断、消息没写进数据库，重进自然就看不到。
private val sendScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userName: String,
    avatarUrl: String = "",
    currentUserUid: String = "",
    peerUid: String = "",
    conversationId: String = "",
    repository: RealtimeRepository? = null,
    myAvatarUrl: String = "",
    scrollToMessageId: String = "",
    onMessageScrolled: () -> Unit = {},
    onBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onUserCardClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val realtimeRepo = repository ?: remember { RealtimeRepository(context.applicationContext as android.app.Application) }
    val authRepo = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var pendingMedia by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val mediaPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        pendingMedia = (pendingMedia + uris).distinct()
    }

    // 长按消息弹出菜单（保存/撤回/删除）
    var menuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var pendingSaveUrl by remember { mutableStateOf("") }

    val savePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingSaveUrl
        pendingSaveUrl = ""
        if (url.isNotBlank()) {
            if (granted) {
                coroutineScope.launch {
                    val ok = ImageSaver.save(context, url)
                    Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 本地已删除的消息 id（“删除”仅本机移除，持久化避免重进又出现）
    val deletedPrefs = remember { context.getSharedPreferences("chat_deleted", android.content.Context.MODE_PRIVATE) }
    fun deletedIds(): MutableSet<String> =
        deletedPrefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun markDeleted(messageId: String) {
        if (messageId.isBlank()) return
        val ids = deletedIds()
        ids.add(messageId)
        deletedPrefs.edit().putStringSet("ids", ids).apply()
    }
    fun removeMessageById(messageId: String): Boolean {
        val idx = messages.indexOfFirst { it.messageId == messageId }
        if (idx >= 0) { messages.removeAt(idx); return true }
        return false
    }

    fun firstImageUrl(msg: ChatMessage): String? =
        msg.mediaUrl.split(",").firstOrNull { it.isNotBlank() && !it.startsWith("video:") }

    fun saveImage(msg: ChatMessage) {
        val url = firstImageUrl(msg) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            coroutineScope.launch {
                val ok = ImageSaver.save(context, url)
                Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingSaveUrl = url
            savePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun recallMessage(msg: ChatMessage) {
        if (msg.messageId.isBlank()) return
        if (System.currentTimeMillis() - msg.time > 2 * 60 * 1000L) {
            Toast.makeText(context, "不能撤回超过两分钟的消息", Toast.LENGTH_SHORT).show()
            return
        }
        removeMessageById(msg.messageId)
        sendScope.launch {
            try { realtimeRepo.deleteMessage(msg.messageId, conversationId) } catch (_: Exception) { }
        }
        Toast.makeText(context, "已撤回", Toast.LENGTH_SHORT).show()
    }

    fun deleteMessageLocal(msg: ChatMessage) {
        if (msg.messageId.isNotBlank()) markDeleted(msg.messageId)
        removeMessageById(msg.messageId)
        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
    }

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
            val deleted = deletedIds()
            messages.clear()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val messageId = m.optString("message_id", "")
                if (messageId in deleted) continue
                messages.add(
                    ChatMessage(
                        content = m.optString("content", ""),
                        time = m.optLong("created_at", 0L),
                        isMine = m.optString("sender_uid") == currentUserUid,
                        mediaUrl = m.optString("media_url", ""),
                        messageId = messageId
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
                        val mediaUrl = record.optString("media_url", "")
                        val messageId = record.optString("message_id", "")
                        if (messageId in deletedIds()) return
                        if (messages.none { it.content == content && it.time == time && !it.isMine }) {
                            messages.add(ChatMessage(content, time, false, mediaUrl, messageId))
                        }
                    }
                }
                override fun onMessageDeleted(record: JSONObject) {
                    val convId = record.optString("conversation_id", "")
                    val messageId = record.optString("message_id", "")
                    if (convId == conversationId && messageId.isNotBlank()) {
                        if (removeMessageById(messageId)) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "对方撤回了一条消息", Toast.LENGTH_SHORT).show()
                            }
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

    var highlightMessageId by remember { mutableStateOf("") }
    var locatedMessageId by remember { mutableStateOf("") }

    // 从聊天记录搜索进入：定位到指定消息并做 0.5s 高亮涟漪
    LaunchedEffect(messages.size, scrollToMessageId) {
        if (scrollToMessageId.isNotBlank() && messages.isNotEmpty()) {
            val idx = messages.indexOfFirst { it.messageId == scrollToMessageId }
            if (idx >= 0) {
                locatedMessageId = scrollToMessageId
                highlightMessageId = scrollToMessageId
                listState.animateScrollToItem(idx)
                kotlinx.coroutines.delay(500)
                highlightMessageId = ""
            }
            onMessageScrolled()
        }
    }

    // 首次加载 / 新消息到来时滚动到底部；内容不足一屏时保持顶部对齐
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && scrollToMessageId.isBlank()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (peerUid.isNotBlank()) onUserClick(peerUid) }) {
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
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (peerUid.isNotBlank()) onUserClick(peerUid) },
                maxLines = 1
            )
            Icon(
                painter = painterResource(id = R.drawable.menu),
                contentDescription = "资料卡",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onUserCardClick() },
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
            items(messages.size) { index ->
                val msg = messages[index]
                val showTime = index == 0 ||
                    msg.time - messages[index - 1].time > 5 * 60 * 1000L
                Column {
                    if (showTime) {
                        ChatTimeDivider(time = msg.time)
                    }
                    ChatBubble(
                        message = msg,
                        avatarUrl = if (msg.isMine) myAvatarUrl else avatarUrl,
                        highlighted = msg.messageId.isNotBlank() && msg.messageId == highlightMessageId,
                        onLongPress = { menuMessage = msg },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
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
            Icon(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "选择图片或视频",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mediaPicker.launch("image/*") },
                tint = getOnSurfaceTertiary()
            )
            Spacer(modifier = Modifier.width(6.dp))
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
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
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
                            if (inputText.isEmpty() && pendingMedia.isEmpty()) {
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
                    .background(
                        if (sending) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!sending) {
                            val time = System.currentTimeMillis()
                            val text = inputText.trim()
                            val media = pendingMedia
                            val messageId = "m_${currentUserUid}_${System.nanoTime()}"
                            // 立即用本地 URI 显示图片消息（上传完成后替换为远程 URL）
                            messages.add(
                                ChatMessage(
                                    content = text,
                                    time = time,
                                    isMine = true,
                                    mediaUrl = media.joinToString(",") { it.toString() },
                                    messageId = messageId
                                )
                            )
                            inputText = ""
                            pendingMedia = emptyList()
                            sendChatMessage(
                                context = context,
                                authRepo = authRepo,
                                realtimeRepo = realtimeRepo,
                                coroutineScope = sendScope,
                                currentUserUid = currentUserUid,
                                peerUid = peerUid,
                                conversationId = conversationId,
                                messageId = messageId,
                                text = text,
                                mediaUris = media,
                                onStart = { sending = true },
                                onDone = { _, _ ->
                                    // 云端已存远程 URL（重进会用）；本地继续显示 content:// 图，
                                    // 不替换 mediaUrl，避免图片重载引起的闪烁
                                    sending = false
                                }
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (sending) "发送中" else "发送",
                    fontSize = 14.sp,
                    color = if (sending) getOnSurfaceTertiary() else MaterialTheme.colorScheme.surface
                )
            }
        }

        // 待发送媒体预览
        if (pendingMedia.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pendingMedia) { index, uri ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.close_ring_fill),
                            contentDescription = "移除",
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    pendingMedia = pendingMedia.filterIndexed { i, _ -> i != index }
                                },
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // 长按消息菜单（保存/撤回/删除）
    val menu = menuMessage
    if (menu != null) {
        val canRecall = menu.isMine && menu.messageId.isNotBlank()
        val hasImage = firstImageUrl(menu) != null
        ModalBottomSheet(onDismissRequest = { menuMessage = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (hasImage) {
                    MenuOption("保存图片") { saveImage(menu); menuMessage = null }
                }
                if (canRecall) {
                    MenuOption("撤回") { recallMessage(menu); menuMessage = null }
                }
                MenuOption("删除", danger = true) { deleteMessageLocal(menu); menuMessage = null }
            }
        }
    }
}

@Composable
private fun MenuOption(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 16.sp,
        color = if (danger) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

private fun sendChatMessage(
    context: android.content.Context,
    authRepo: SupabaseAuthRepository,
    realtimeRepo: RealtimeRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    currentUserUid: String,
    peerUid: String,
    conversationId: String,
    messageId: String,
    text: String,
    mediaUris: List<android.net.Uri>,
    onStart: () -> Unit,
    onDone: (String, Boolean) -> Unit
) {
    val trimmed = text.trim()
    if (trimmed.isBlank() && mediaUris.isEmpty()) return
    if (currentUserUid.isBlank() || peerUid.isBlank()) return
    onStart()
    coroutineScope.launch {
        var sent = false
        try {
            // 并行上传媒体，多个以逗号拼接；聊天图单独降到 720/80 以加快上传
            val mediaUrl = coroutineScope {
                mediaUris.map { uri ->
                    async(Dispatchers.IO) {
                        authRepo.uploadImage(uri, context.applicationContext, maxDimension = 720, quality = 80)
                    }
                }.awaitAll()
            }.filterNotNull().joinToString(",")
            var convId = conversationId
            if (convId.isBlank()) {
                convId = realtimeRepo.getOrCreateConversation(currentUserUid, peerUid)
            }
            if (convId.isNotBlank()) {
                realtimeRepo.sendMessage(
                    messageId,
                    convId, currentUserUid, peerUid, trimmed, mediaUrl
                )
                sent = true
            }
            onDone(mediaUrl, sent)
        } catch (_: Exception) {
            onDone("", sent)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    avatarUrl: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongPress: (ChatMessage) -> Unit = {}
) {
    // 定位涟漪：被定位到的消息行短暂高亮 0.5s
    val rowBg = if (highlighted) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg),
        verticalAlignment = Alignment.Top
    ) {
        if (message.isMine) {
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Top) {
                MessageBubble(message = message, onLongPress = onLongPress)
                Spacer(modifier = Modifier.width(8.dp))
                ChatAvatar(avatarUrl = avatarUrl, size = 32.dp)
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                ChatAvatar(avatarUrl = avatarUrl, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                MessageBubble(message = message, onLongPress = onLongPress)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatTimeDivider(time: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatChatTime(time),
            fontSize = 11.sp,
            color = getOnSurfaceTertiary()
        )
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
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onLongPress: (ChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    // 图片按宽度采样解码，避免整图解码导致本地图发送后半天才显示
    val imageTargetWidth = with(LocalDensity.current) { 240.dp.toPx() }.toInt().coerceAtLeast(1)

    val mediaList = message.mediaUrl.split(",").filter { it.isNotBlank() }
    val hasText = message.content.isNotBlank()
    // 只有媒体无文字时：直接以图片/视频形式展示，不加气泡背景
    if (mediaList.isNotEmpty() && !hasText) {
        Column(
            modifier = modifier
                .widthIn(max = 240.dp)
                .pointerInput(message.messageId) { detectTapGestures(onLongPress = { onLongPress(message) }) }
        ) {
            mediaList.forEach { media ->
                val isVideo = media.startsWith("video:") ||
                    (media.startsWith("content:") && (context.contentResolver.getType(android.net.Uri.parse(media)) ?: "").contains("video"))
                val realUrl = media.removePrefix("video:")
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.video_fill),
                            contentDescription = "视频",
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(realUrl)
                            .size(Size(imageTargetWidth, imageTargetWidth))
                            .placeholder(android.graphics.drawable.ColorDrawable(0x14000000))
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (message.isMine) getBlueFill()
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(message.messageId) { detectTapGestures(onLongPress = { onLongPress(message) }) }
    ) {
        // 媒体(图片/视频)消息
        mediaList.forEach { media ->
            val isVideo = media.startsWith("video:") ||
                (media.startsWith("content:") && (context.contentResolver.getType(android.net.Uri.parse(media)) ?: "").contains("video"))
            val realUrl = media.removePrefix("video:")
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.video_fill),
                        contentDescription = "视频",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(realUrl)
                        .size(Size(imageTargetWidth, imageTargetWidth))
                        .placeholder(android.graphics.drawable.ColorDrawable(0x14000000))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
        if (hasText) {
            Text(
                text = message.content,
                fontSize = 14.sp,
                color = if (message.isMine) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (mediaList.isNotEmpty()) 4.dp else 0.dp)
            )
        }
    }
}
