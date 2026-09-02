package com.example.redbook.ui.messages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline
import com.example.redbook.ui.utils.formatRelativeTime

/**
 * 消息搜索页：搜索聊天记录 / 好友。
 * 联系人分类 = 我已关注的用户（备注名优先展示）；
 * 聊天记录分类 = 命中消息内容的会话，点击跳转聊天页并定位到该消息。
 */
@Composable
fun MessageSearchScreen(
    userUid: String = "",
    onBack: () -> Unit = {},
    onContactChatClick: (String, String, String) -> Unit = { _, _, _ -> },
    onMessageResultClick: (String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: MessageSearchViewModel = viewModel(
        factory = MessageSearchViewModelFactory(context.applicationContext as android.app.Application)
    )
    val followingUsers by viewModel.followingUsers.collectAsStateWithLifecycle()
    val messageResults by viewModel.messageResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(userUid) {
        if (userUid.isNotBlank()) viewModel.loadFollowing(userUid)
    }

    // 输入内容即触发本地分类搜索：联系人模糊匹配(备注/昵称) + 聊天记录模糊匹配(300ms 防抖)
    LaunchedEffect(query, userUid) {
        val q = query.trim()
        if (q.isNotEmpty() && userUid.isNotBlank()) {
            kotlinx.coroutines.delay(300)
            viewModel.searchMessages(userUid, q)
        } else {
            viewModel.searchMessages(userUid, "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp)
    ) {
        // 搜索 row 距顶部 24dp；结果列表底部留 16dp
        Spacer(modifier = Modifier.height(24.dp))
        SearchInputRow(
            query = query,
            onQueryChange = { query = it },
            onCancel = { onBack() }
        )

        val q = query.trim()
        if (q.isNotEmpty()) {
            // 联系人：匹配备注名或用户名（我已关注的用户）
            val contacts = followingUsers.filter {
                it.displayName.contains(q, ignoreCase = true) || it.userName.contains(q, ignoreCase = true)
            }
            // 聊天记录：显示服务端模糊匹配到的消息
            val records = messageResults

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                if (contacts.isNotEmpty()) {
                    item(key = "section_contacts") {
                        Text(
                            text = "联系人",
                            fontSize = 13.sp,
                            color = getOnSurfaceTertiary(),
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                    items(contacts, key = { "contact_${it.uid}" }) { contact ->
                        ContactRow(
                            contact = contact,
                            query = q,
                            onChatClick = { onContactChatClick(contact.uid, contact.displayName, contact.avatarUrl) }
                        )
                    }
                }

                if (records.isNotEmpty()) {
                    if (contacts.isNotEmpty()) {
                        item(key = "spacer_between") { Spacer(modifier = Modifier.height(10.dp)) }
                    }
                    item(key = "section_records") {
                        Text(
                            text = "聊天记录",
                            fontSize = 13.sp,
                            color = getOnSurfaceTertiary(),
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                    items(records, key = { "record_${it.messageId}" }) { record ->
                        MessageRecordRow(
                            record = record,
                            query = q,
                            onClick = {
                                onMessageResultClick(
                                    record.conversationId,
                                    record.messageId,
                                    record.peerDisplayName,
                                    record.peerAvatar,
                                    record.peerUid,
                                    record.content
                                )
                            }
                        )
                    }
                    if (searching) {
                        item(key = "searching") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部搜索 row：输入框(15dp 圆角,background 底色) + 5dp + 取消 */
@Composable
private fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "搜索",
                modifier = Modifier.size(18.dp),
                tint = getOnSurfaceSecondary()
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索聊天记录/好友",
                                    fontSize = 14.sp,
                                    color = getOnSurfaceSecondary()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            // 有输入时末尾显示清空按钮
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.close_ring_fill),
                    contentDescription = "清空",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onQueryChange("") },
                    tint = getOnSurfaceSecondary()
                )
            }
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "取消",
            fontSize = 14.sp,
            color = getOnSurfaceSecondary(),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCancel() }
        )
    }
}

/** 联系人搜索项：左 48dp 头像，中间备注/昵称，右侧"去聊天"描边按钮 */
@Composable
private fun ContactRow(
    contact: ContactSearchItem,
    query: String,
    onChatClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像 48dp
        Box(modifier = Modifier.size(48.dp).clip(CircleShape)) {
            if (contact.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.avatarUrl)
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
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (contact.remark.isNotBlank()) {
                // 有备注：第一行备注名(加粗)，第二行"原昵称:用户名"
                Text(
                    text = highlightKeyword(contact.remark, query),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = highlightKeyword("原昵称：${contact.userName}", query),
                    fontSize = 12.sp,
                    color = getOnSurfaceTertiary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = highlightKeyword(contact.userName, query),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        // 去聊天按钮：透明背景，outline 边框，16dp 圆角
        Box(
            modifier = Modifier
                .border(1.dp, getOutline(), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onChatClick() }
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "去聊天",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 聊天记录搜索项：结构与消息列表相同（48dp 头像 + 名称/备注 + 时间 + 消息内容） */
@Composable
private fun MessageRecordRow(
    record: ChatMessageSearchItem,
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape)) {
            if (record.peerAvatar.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(record.peerAvatar)
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
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = highlightKeyword(record.peerDisplayName, query),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(record.time),
                    fontSize = 12.sp,
                    color = getOnSurfaceSecondary()
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = highlightKeyword(record.content, query),
                fontSize = 13.sp,
                color = getOnSurfaceSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 将命中的关键词片段用 primary 高亮（不区分大小写，多处命中都高亮） */
@Composable
private fun highlightKeyword(text: String, keyword: String): AnnotatedString {
    val k = keyword.trim()
    if (k.isEmpty()) return AnnotatedString(text)
    val primary = MaterialTheme.colorScheme.primary
    val result = buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val idx = text.indexOf(k, startIndex = start, ignoreCase = true)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(color = primary)) {
                append(text.substring(idx, idx + k.length))
            }
            start = idx + k.length
        }
    }
    return result
}
