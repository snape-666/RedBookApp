package com.example.redbook.ui.notificationsetting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceTertiary

@Composable
fun NotificationSettingScreen(onBack: () -> Unit) {
    var receiveEnabled by remember { mutableStateOf(true) }
    var likeFavEnabled by remember { mutableStateOf(true) }
    var followEnabled by remember { mutableStateOf(true) }
    var commentEnabled by remember { mutableStateOf(true) }
    var privateEnabled by remember { mutableStateOf(true) }

    Column(Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 12.dp, end = 12.dp, top = 36.dp, bottom = 16.dp)
    ) {
        Row(Modifier
            .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(10.dp))
            Text("通知设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal=10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically)
        {
            Text("接收消息通知", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Switch(checked = receiveEnabled, onCheckedChange = { receiveEnabled = it }, modifier = Modifier.scale(0.6f))
        }

        Spacer(Modifier.height(15.dp))

        Text("互动通知", fontSize = 14.sp, color = getOnSurfaceTertiary())

        Spacer(Modifier.height(5.dp))

        Column(Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical=3.dp, horizontal = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("赞和收藏", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Switch(checked = likeFavEnabled, onCheckedChange = { likeFavEnabled = it }, modifier = Modifier.scale(0.6f))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("新增关注", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Switch(checked = followEnabled, onCheckedChange = { followEnabled = it }, modifier = Modifier.scale(0.6f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("评论", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Switch(checked = commentEnabled, onCheckedChange = { commentEnabled = it }, modifier = Modifier.scale(0.6f))
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
            .padding(horizontal=10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("私信", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Switch(checked = privateEnabled, onCheckedChange = { privateEnabled = it }, modifier = Modifier.scale(0.6f))
        }
    }
}
