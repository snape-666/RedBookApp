package com.example.redbook.ui.notificationsetting

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.R
import com.example.redbook.data.model.NotificationSettings
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.notification.NotifHelper
import com.example.redbook.notification.NotifPrefs
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import kotlinx.coroutines.launch

/**
 * 通知设置页:
 *  - 开关状态本地 SharedPreferences + 云端 users 表双存(按 version 取新)
 *  - “接收消息通知”为总开关:关闭时四个分类全部置灰(不弹任何通知)
 *  - 系统通知权限未开启时给出引导入口
 */
@Composable
fun NotificationSettingScreen(
    userUid: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val realtimeRepo = remember { RealtimeRepository(app) }
    val scope = rememberCoroutineScope()

    // 本地优先加载;登录后异步拉云端,version 较新则覆盖
    var settings by remember(userUid) { mutableStateOf(NotifPrefs.load(userUid, context)) }

    LaunchedEffect(userUid) {
        if (userUid.isBlank()) return@LaunchedEffect
        try {
            val cloud = realtimeRepo.getNotificationSettings(userUid)
            if (cloud.version >= settings.version) {
                settings = cloud
                NotifPrefs.save(userUid, cloud, context)
            }
        } catch (_: Exception) { }
    }

    fun persist(next: NotificationSettings) {
        val merged = next.copy(version = maxOf(next.version, settings.version) + 1)
        settings = merged
        if (userUid.isBlank()) return
        NotifPrefs.save(userUid, merged, context)
        scope.launch {
            try { realtimeRepo.saveNotificationSettings(userUid, merged) } catch (_: Exception) { }
        }
    }

    val systemPermissionOk = NotifHelper.hasPostPermission(context)
    val systemEnabled = NotifHelper.areNotificationsEnabled(context)
    val needPermission = !systemPermissionOk || !systemEnabled

    fun openNotificationSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) openNotificationSettings()
    }

    Column(Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 12.dp, end = 12.dp, top = 36.dp, bottom = 16.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text("通知设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically)
        {
            Text("接收消息通知", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = settings.receiveEnabled,
                onCheckedChange = { persist(settings.copy(receiveEnabled = it)) },
                modifier = Modifier.scale(0.6f)
            )
        }

        // 系统通知权限引导
        if (needPermission) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!systemPermissionOk) "未开启系统通知权限,通知无法弹出" else "系统通知已关闭,通知无法弹出",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "去开启",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !systemPermissionOk) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        Text("互动通知", fontSize = 14.sp, color = getOnSurfaceTertiary())

        Spacer(Modifier.height(5.dp))

        val masterOn = settings.receiveEnabled
        Column(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 3.dp, horizontal = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("赞和收藏", fontSize = 14.sp, color = if (masterOn) MaterialTheme.colorScheme.onSurface else getOnSurfaceTertiary())
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = masterOn && settings.likeFavEnabled,
                    onCheckedChange = if (masterOn) {
                        { persist(settings.copy(likeFavEnabled = it)) }
                    } else null,
                    modifier = Modifier.scale(0.6f)
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("新增关注", fontSize = 14.sp, color = if (masterOn) MaterialTheme.colorScheme.onSurface else getOnSurfaceTertiary())
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = masterOn && settings.followEnabled,
                    onCheckedChange = if (masterOn) {
                        { persist(settings.copy(followEnabled = it)) }
                    } else null,
                    modifier = Modifier.scale(0.6f)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("评论", fontSize = 14.sp, color = if (masterOn) MaterialTheme.colorScheme.onSurface else getOnSurfaceTertiary())
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = masterOn && settings.commentEnabled,
                    onCheckedChange = if (masterOn) {
                        { persist(settings.copy(commentEnabled = it)) }
                    } else null,
                    modifier = Modifier.scale(0.6f)
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        Text("私信通知", fontSize = 14.sp, color = getOnSurfaceTertiary())

        Spacer(Modifier.height(5.dp))

        Row(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("私信", fontSize = 14.sp, color = if (masterOn) MaterialTheme.colorScheme.onSurface else getOnSurfaceTertiary())
            Spacer(Modifier.weight(1f))
            Switch(
                checked = masterOn && settings.dmEnabled,
                onCheckedChange = if (masterOn) {
                    { persist(settings.copy(dmEnabled = it)) }
                } else null,
                modifier = Modifier.scale(0.6f)
            )
        }
    }
}
