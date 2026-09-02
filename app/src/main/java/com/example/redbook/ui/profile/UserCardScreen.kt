package com.example.redbook.ui.profile

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.example.redbook.ui.theme.getOutline

/**
 * 资料卡：从云端实时拉取目标用户(ID/性别/生日/地区)并展示。
 * 页面水平 10dp / 顶部 28dp / 底部 16dp padding；顶部标题 row；
 * 卡片信息区从屏幕高度 25% 处开始。
 */
@Composable
fun UserCardScreen(
    targetUid: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    // 按 uid 绑定，切换目标用户时重建 ViewModel 拉取最新数据
    val viewModel: UserCardViewModel = viewModel(
        key = "user_card_$targetUid",
        factory = UserCardViewModelFactory(context.applicationContext as android.app.Application)
    )
    val data by viewModel.data.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(targetUid) { viewModel.load(targetUid) }

    val startY = LocalConfiguration.current.screenHeightDp.dp * 0.15f
    // 信息卡宽度 = 屏幕宽度的 90%
    val cardWidth = LocalConfiguration.current.screenWidthDp.dp * 0.8f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 10.dp, end = 10.dp, top = 28.dp)
    ) {
        // 第一个 row：左返回 + 居中标题"资料卡"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_left),
                contentDescription = "返回",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "资料卡",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
            Spacer(Modifier.size(24.dp))
        }

        // 信息区从 15% 屏幕高度开始
        Spacer(Modifier.height(startY))

        if (loading && data.uid.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像：82dp 圆形居中
                Box(modifier = Modifier.size(82.dp).clip(CircleShape)) {
                    if (data.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(data.avatarUrl)
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
                Spacer(Modifier.height(12.dp))
                // 用户名
                Text(
                    text = data.userName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(25.dp))
                // 信息卡片：16 圆角 surface 背景，宽度为屏幕 90%，内部左右 10dp，行间分割线
                val rows = listOf(
                    "ID" to data.xhsId,
                    "性别" to data.gender,
                    "生日" to data.birthday,
                    "地区" to data.ipLocation
                )
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            clip = false,
                            ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    rows.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .height(0.5.dp)
                                    .background(getOutline().copy(alpha = 0.3f))
                            )
                        }
                        UserCardRow(
                            label = label,
                            value = value,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 卡片内的信息行：label 居左；value 精确从父宽度 30% 处开始居左 */
@Composable
private fun UserCardRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 第一列占父宽 30%，label 放最左
        Box(
            modifier = Modifier.weight(0.3f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = getOnSurfaceSecondary(),
                maxLines = 1
            )
        }
        // 第二列从 30% 位置开始，value 居左
        Box(
            modifier = Modifier.weight(0.7f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value.ifBlank { "未填写" },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
