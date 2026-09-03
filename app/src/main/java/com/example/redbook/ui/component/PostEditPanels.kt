package com.example.redbook.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOutline

/**
 * 帖子详情页(作者模式)的编辑底部浮层：
 *  - EditPostActionPanel：编辑 / 权限设置 / 删除 三个圆形操作
 *  - PostPermissionPanel：公开可见 / 仅自己可见 切换
 * 两者都通过 AuthorBottomSheetOverlay(全屏遮罩 + 最高层)呈现为
 * "顶部16dp圆角 + 阴影 + surface" 的底部浮层卡片，内容底部带16dp内边距。
 */

/** 当前作者面板类型 */
enum class AuthorPanel { None, Actions, Permission }

/** 遮罩 + 底部浮层容器：浮层置于最上层(盖住卡片详情)，点击遮罩收起 */
@Composable
fun AuthorBottomSheetOverlay(
    onDismiss: () -> Unit,
    sheet: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩层
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
        // 底部浮层（最顶层）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            sheet()
        }
    }
}

/** 底部浮层卡片外壳：顶部16dp圆角 + 阴影 + background 背景；内部左右10dp、上5dp、底部16dp */
@Composable
private fun BottomSheetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                clip = false,
                ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
            )
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 26.dp)
    ) {
        content()
    }
}

/** 详情页底部"编辑区域"内容（未展开面板时，替换评论输入框显示在左权重区） */
@Composable
fun PostEditAreaContent(
    isPublic: Boolean,
    onAreaClick: () -> Unit,
    isOnDark: Boolean = false
) {
    // 视频页黑底上第一行用 surface(白)；普通页用 onSurface。
    // 第二行普通页用 secondary；视频页用 surface 半透明白。
    val firstColor = if (isOnDark) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondColor = if (isOnDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    } else {
        getOnSurfaceSecondary()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onAreaClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (isPublic) R.drawable.lock_open else R.drawable.lock_black),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = firstColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isPublic) "公开可见" else "仅自己可见",
                fontSize = 13.sp,
                color = firstColor
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "编辑和权限设置",
                fontSize = 13.sp,
                color = secondColor
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = secondColor
            )
        }
    }
}

/** 面板1：编辑 / 权限设置 / 删除 */
@Composable
fun EditPostActionPanel(
    onClose: () -> Unit,
    onEditClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomSheetCard(modifier) {
        // 标题行：文字居中，右侧关闭按钮
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = "设置",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp,bottom=15.dp)
            )
            Icon(
                painter = painterResource(R.drawable.close_round),
                contentDescription = "收起",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() },
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 三个操作均匀分布
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AuthorActionCircle(
                iconRes = R.drawable.edit_black,
                label = "编辑",
                onClick = onEditClick
            )
            AuthorActionCircle(
                iconRes = R.drawable.edit_black,
                label = "权限设置",
                onClick = onPermissionClick
            )
            AuthorActionCircle(
                iconRes = R.drawable.trash,
                label = "删除",
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun AuthorActionCircle(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        // 48dp 圆形，surface + 阴影，内部 24dp icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 面板2：权限设置（公开可见 / 仅自己可见） */
@Composable
fun PostPermissionPanel(
    isPublic: Boolean,
    onSelect: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomSheetCard(modifier) {
        // 标题行：文字居中，右侧 trash 图标收起
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = "权限设置",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            Icon(
                painter = painterResource(R.drawable.close_round),
                contentDescription = "收起",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() },
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(15.dp))

        // 选项 column：surface 背景 + 阴影 + 圆角，上下5dp 左右10dp padding；宽度为总宽度的 80% 并居中
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
            PermissionRow(
                iconRes = R.drawable.lock_open,
                text = "公开可见",
                selected = isPublic,
                onClick = { onSelect(true) }
            )
            HorizontalDivider(
                color = getOutline().copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
            PermissionRow(
                iconRes = R.drawable.lock_black,
                text = "仅自己可见",
                selected = !isPublic,
                onClick = { onSelect(false) }
            )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    iconRes: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // 最右侧预留勾选图标位置；选中时显示
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.choose),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
            }
        }
    }
}
