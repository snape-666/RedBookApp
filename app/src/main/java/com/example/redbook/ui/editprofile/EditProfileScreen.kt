package com.example.redbook.ui.editprofile

import android.app.Application
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOutline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun EditProfileScreen(
    userUid: String,
    userName: String,
    userXhsId: String,
    gender: String = "",
    birthday: String = "",
    avatarUrl: String = "",
    backgroundUrl: String = "",
    onBack: () -> Unit,
    onDataChanged: (name: String, gender: String, birthday: String, avatarUrl: String, backgroundUrl: String) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val repository = remember { SupabaseAuthRepository(context.applicationContext as Application) }
    val scope = rememberCoroutineScope()
    var nameText by remember { mutableStateOf(userName) }
    var genderText by remember { mutableStateOf(gender) }
    var birthdayText by remember { mutableStateOf(birthday) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var editingGender by remember { mutableStateOf(false) }
    var editingBirthday by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarUri = uri
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) backgroundUri = uri
    }

    fun save() {
        scope.launch {
            var newAvatar = avatarUrl
            var newBackground = backgroundUrl
            avatarUri?.let { repository.uploadImage(it, context.applicationContext)?.let { u -> newAvatar = u } }
            backgroundUri?.let { repository.uploadImage(it, context.applicationContext)?.let { u -> newBackground = u } }
            repository.updateUserProfile(
                userUid,
                nickname = nameText,
                backgroundUrl = newBackground,
                avatarUrl = newAvatar,
                gender = genderText,
                birthday = birthdayText
            )
            onDataChanged(nameText, genderText, birthdayText, newAvatar, newBackground)
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 36.dp, bottom = 8.dp)) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(28.dp)
                    .clickable { onBack() },
                tint = Color.Unspecified
            )
            Text(
                text = "编辑资料",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "保存",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { save() },
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .clickable { avatarPicker.launch("image/*") }) {
                    when {
                        avatarUri != null -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(avatarUri).crossfade(true).build(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        avatarUrl.isNotBlank() -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        else -> Image(
                            painter = painterResource(R.drawable.test),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(Modifier.align(Alignment.Center).size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.camera),
                            contentDescription = "更换头像",
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)) {
                Spacer(Modifier.height(5.dp))
                Row(Modifier
                    .fillMaxWidth()
                    .clickable { nameInput = nameText; editingName = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("名字", fontSize = 14.sp, color = getOnSurfaceSecondary(), modifier = Modifier.width(80.dp))
                    Text(nameText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOutline())
                }
                Divider()
                EditRow("小红书号", userXhsId)
                Divider()
                Row(Modifier
                    .fillMaxWidth()
                    .clickable { backgroundPicker.launch("image/*") }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("背景图", fontSize = 14.sp, color = getOnSurfaceSecondary(), modifier = Modifier.width(80.dp))
                    when {
                        backgroundUri != null -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(backgroundUri).crossfade(true).build(),
                            contentDescription = "背景图",
                            modifier = Modifier.width(120.dp).height(56.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        backgroundUrl.isNotBlank() -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(backgroundUrl).crossfade(true).build(),
                            contentDescription = "背景图",
                            modifier = Modifier.width(120.dp).height(56.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        else -> Image(
                            painter = painterResource(R.drawable.test2),
                            contentDescription = "背景图",
                            modifier = Modifier.width(120.dp).height(56.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOutline())
                }
                Spacer(Modifier.height(5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Column(Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)) {
                Spacer(Modifier.height(5.dp))
                Row(Modifier
                    .fillMaxWidth()
                    .clickable { editingGender = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("性别", fontSize = 14.sp, color = getOnSurfaceSecondary(), modifier = Modifier.width(80.dp))
                    Text(if (genderText == "不显示" || genderText.isBlank()) "" else genderText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOutline())
                }
                Divider()
                Row(Modifier
                    .fillMaxWidth()
                    .clickable { editingBirthday = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("生日", fontSize = 14.sp, color = getOnSurfaceSecondary(), modifier = Modifier.width(80.dp))
                    Text(if (birthdayText == "不显示") "" else birthdayText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.arrow_right), null, Modifier.size(20.dp), tint = getOutline())
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }

    if (editingName) {
        Dialog(onDismissRequest = { editingName = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Text("修改名字", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editingName = false }) {
                        Text("取消", color = getOnSurfaceSecondary())
                    }
                    TextButton(onClick = {
                        if (nameInput.isNotBlank()) nameText = nameInput
                        editingName = false
                    }) {
                        Text("确定", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (editingGender) {
        Dialog(onDismissRequest = { editingGender = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Text("编辑性别", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                listOf("男", "女", "不显示").forEach { option ->
                    Row(Modifier
                        .fillMaxWidth()
                        .clickable {
                            genderText = option
                            editingGender = false
                        }
                        .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(option, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        if (genderText == option) {
                            Icon(painterResource(R.drawable.choose), null, Modifier.size(20.dp), tint = Color.Unspecified)
                        }
                    }
                }
            }
        }
    }

    if (editingBirthday) {
        val parts = remember(birthdayText) { birthdayText.split("-") }
        var year by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2006) }
        var month by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 6) }
        var day by remember { mutableIntStateOf(parts.getOrNull(2)?.toIntOrNull() ?: 21) }
        Dialog(onDismissRequest = { editingBirthday = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Text("编辑生日", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    WheelColumn((1900..2026).toList(), year) { year = it }
                    WheelColumn((1..12).toList(), month) { month = it }
                    WheelColumn((1..31).toList(), day) { day = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier
                    .fillMaxWidth()
                    .clickable {
                        birthdayText = "不显示"
                        editingBirthday = false
                    }
                    .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("不显示", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    if (birthdayText == "不显示") {
                        Icon(painterResource(R.drawable.choose), null, Modifier.size(20.dp), tint = Color.Unspecified)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editingBirthday = false }) {
                        Text("取消", color = getOnSurfaceSecondary())
                    }
                    TextButton(onClick = {
                        birthdayText = "%04d-%02d-%02d".format(year, month, day)
                        editingBirthday = false
                    }) {
                        Text("确定", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 40.dp.toPx() }

    LaunchedEffect(values, selected) {
        val index = values.indexOf(selected).coerceAtLeast(0)
        listState.scrollToItem(index)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index
        }.distinctUntilChanged().collect { index ->
            if (index != null && index in values.indices) {
                onSelect(values[index])
            }
        }
    }

    Box(Modifier.width(72.dp).height(200.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 80.dp),
            flingBehavior = rememberSnapFlingBehavior(listState)
        ) {
            items(values.size) { index ->
                val v = values[index]
                val isSel = v == selected
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "$v",
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface else getOutline()
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(40.dp)) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(getOutline()))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(getOutline()))
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp)
        .height(0.5.dp)
        .background(getOutline().copy(alpha = 0.6f)))
}

@Composable
private fun EditRow(label: String, value: String) {
    Row(Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = getOnSurfaceSecondary(),
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.arrow_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = getOutline()
        )
    }
}
