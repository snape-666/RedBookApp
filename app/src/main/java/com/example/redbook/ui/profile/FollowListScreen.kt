package com.example.redbook.ui.profile

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.theme.getOutline

/**
 * 关注/粉丝列表页
 *  - FOLLOWING：展示我关注的人；按钮：互相关注 / 已关注（点击弹「不再关注作者」确认并同步云端）
 *  - FANS：展示关注我的人；按钮：回关（primary）/ 互相关注
 *  - 点击头像、用户名或整行（除按钮外）跳转对应用户主页
 */
@Composable
fun FollowListScreen(
    profileUid: String,
    userUid: String,
    mode: FollowListMode,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: FollowListViewModel = viewModel(
        key = "followlist_${mode.name}_$profileUid",
        factory = FollowListViewModelFactory(context.applicationContext as android.app.Application, profileUid, userUid, mode)
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val confirmUid by viewModel.confirmUnfollowUid.collectAsStateWithLifecycle()

    val title = when (mode) {
        FollowListMode.FOLLOWING -> "关注"
        FollowListMode.FANS -> "粉丝"
    }
    val noRipple = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部返回栏：距顶 32dp；左 arrow_left，中间标题，上下 5dp、左右 10dp 内边距
        Spacer(modifier = Modifier.fillMaxWidth().height(32.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
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
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
        }
        // 分割线
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(getOutline().copy(alpha = 0.5f)))

        when {
            loading -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = "暂无$title", fontSize = 14.sp, color = getOnSurfaceSecondary())
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(items, key = { it.uid }) { item ->
                    FollowRow(
                        item = item,
                        mode = mode,
                        onUserClick = { onUserClick(item.uid) },
                        onBtnClick = {
                            when (mode) {
                                FollowListMode.FOLLOWING -> viewModel.setConfirmUnfollow(item.uid)
                                FollowListMode.FANS ->
                                    if (item.followedByMe) viewModel.setConfirmUnfollow(item.uid)
                                    else viewModel.toggleFollow(item.uid, true)
                            }
                        }
                    )
                }
            }
        }
    }

    // 不再关注作者确认弹窗
    confirmUid?.let { uid ->
        val ownerName = items.firstOrNull { it.uid == uid }?.userName ?: "该用户"
        Dialog(onDismissRequest = { viewModel.setConfirmUnfollow(null) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("不再关注作者？", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("确定不再关注 $ownerName 吗？", fontSize = 14.sp, color = getOnSurfaceTertiary())
                }
                // row 上面的分割线
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(getOutline().copy(alpha = 0.5f)))
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { viewModel.setConfirmUnfollow(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = getOnSurfaceSecondary())
                    }
                    // 两个按钮中间的分割线
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(getOutline().copy(alpha = 0.5f)))
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { viewModel.confirmUnfollow(uid, false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("不再关注", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 单个关注/粉丝行 */
@Composable
private fun FollowRow(
    item: FollowItem,
    mode: FollowListMode,
    onUserClick: () -> Unit,
    onBtnClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onUserClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：48dp 圆形头像
        if (item.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.avatarUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = com.example.redbook.R.drawable.test),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(10.dp))

        // 中：用户名/备注名
        Text(
            text = item.userName,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(10.dp))

        // 右：关注按钮
        when (mode) {
            FollowListMode.FOLLOWING -> {
                // 关注页：互相关注与已关注均为描边灰按钮，点击进入「不再关注」确认
                BorderedButton(
                    text = if (item.isMutual) "互相关注" else "已关注",
                    borderColor = getOutline(),
                    textColor = getOnSurfaceTertiary(),
                    onCLick = onBtnClick
                )
            }
            FollowListMode.FANS -> {
                if (item.followedByMe) {
                    // 我关注了对方 -> 互相关注（描边灰按钮）
                    BorderedButton(
                        text = "互相关注",
                        borderColor = getOutline(),
                        textColor = getOnSurfaceTertiary(),
                        onCLick = onBtnClick
                    )
                } else {
                    // 我没关注对方 -> 回关（primary 描边）
                    BorderedButton(
                        text = "回关",
                        borderColor = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.primary,
                        onCLick = onBtnClick
                    )
                }
            }
        }
    }
}

/** 描边圆角按钮：无涟漪点击，固定宽度保证所有行按钮等宽 */
@Composable
private fun BorderedButton(
    text: String,
    borderColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onCLick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCLick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = textColor, maxLines = 1)
    }
}