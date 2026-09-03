package com.example.redbook.ui.profile

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R

/**
 * 隐私设置页：控制主页 笔记/评论/收藏/赞过 四个内容区对他人是否可见。
 * 保存走 ViewModel(viewModelScope)：即使离开页面也会把设置同步到云端。
 */
@Composable
fun PrivacySettingScreen(
    userUid: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PrivacySettingViewModel = viewModel(
        key = "privacy_$userUid",
        factory = PrivacySettingViewModelFactory(
            context.applicationContext as android.app.Application,
            userUid
        )
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
            Text("隐私设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(15.dp))

        // 每行独立成卡：15dp 圆角 + 阴影；行与行间隔 10dp
        Column(Modifier.fillMaxWidth()) {
            PrivacySwitchRow(
                text = "展示笔记",
                checked = settings.showPosts,
                onCheckedChange = { viewModel.setShowPosts(it) }
            )
            Spacer(Modifier.height(10.dp))
            PrivacySwitchRow(
                text = "展示评论",
                checked = settings.showComments,
                onCheckedChange = { viewModel.setShowComments(it) }
            )
            Spacer(Modifier.height(10.dp))
            PrivacySwitchRow(
                text = "展示收藏",
                checked = settings.showFavorites,
                onCheckedChange = { viewModel.setShowFavorites(it) }
            )
            Spacer(Modifier.height(10.dp))
            PrivacySwitchRow(
                text = "展示赞过",
                checked = settings.showLikes,
                onCheckedChange = { viewModel.setShowLikes(it) }
            )
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.6f)
        )
    }
}
