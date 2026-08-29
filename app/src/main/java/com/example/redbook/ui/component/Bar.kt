package com.example.redbook.ui.component

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.theme.RedBookTheme
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary
import com.example.redbook.ui.utils.formatCount


@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    titles: List<String>,
    selectedIndex: Int,
    onTitleClick: (Int) -> Unit,
    fabIconRes: Int,
    onFabClick: () -> Unit,
    indicatorHeight: Int = 2,
    indicatorWidth: Int = 30,

) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        titles.subList(0, 2).forEachIndexed { index, title ->
            TextItem(
                title = title,
                isSelected = selectedIndex == index,
                primaryColor = primaryColor,
                indicatorHeight = indicatorHeight,
                indicatorWidth = indicatorWidth,
                index = index,
                onItemClick = onTitleClick
            )
        }


        Box(
            modifier = Modifier
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onFabClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    painter = painterResource(id = fabIconRes),
                    contentDescription = "发布",
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = Color.Unspecified
                )
            }
        }


        titles.subList(2, 4).forEachIndexed { index, title ->
            val realIndex = index + 2
            TextItem(
                title = title,
                isSelected = selectedIndex == realIndex,
                primaryColor = primaryColor,
                indicatorHeight = indicatorHeight,
                indicatorWidth = indicatorWidth,
                index = realIndex,
                onItemClick = onTitleClick
            )
        }
    }
}



@Composable
private fun TextItem(
    title: String,
    isSelected: Boolean,
    primaryColor: Color,
    indicatorHeight: Int,
    indicatorWidth: Int,
    index: Int,
    onItemClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onItemClick(index)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val frontSelected = MaterialTheme.colorScheme.onSurface
            val frontUnselected = getOnSurfaceSecondary()

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) frontSelected else frontUnselected
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .height(indicatorHeight.dp)
                        .width(indicatorWidth.dp)
                        .background(primaryColor)
                )
            } else {

                Box(
                    modifier = Modifier
                        .height(indicatorHeight.dp)
                        .width(indicatorWidth.dp)
                )
            }
        }
    }
}



@Composable
fun HomeTopBar(
    modifier: Modifier = Modifier,
    titles: List<String>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
    onActionClick: () -> Unit,
    actionIconRes: Int,
    indicatorHeight: Int = 3,
    indicatorWidth: Int = 30,

) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Box(modifier = Modifier.width(24.dp))


        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(
                space = 24.dp,
                alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index

                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabClick(index)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else getOnSurfaceSecondary()
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .height(indicatorHeight.dp)
                                    .width(indicatorWidth.dp)
                                    .background(primaryColor)
                            )
                        } else {

                            Box(
                                modifier = Modifier
                                    .height(indicatorHeight.dp)
                                    .width(indicatorWidth.dp)
                            )
                        }
                    }
                }
            }
        }

        Icon(
            painter = painterResource(id = actionIconRes),
            contentDescription = "搜索",
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onActionClick()
                },
            tint = Color.Unspecified
        )
    }
}



@Preview(
    name = "亮色 - 选中首页",
    showBackground = true
)
@Composable
fun PreviewBottomBarLightHome() {
    RedBookTheme {
        BottomBar(
            titles = listOf("首页", "阅读", "消息", "我的"),
            selectedIndex = 0,
            onTitleClick = {},
            fabIconRes = R.drawable.social_icons,
            onFabClick = {},
        )
    }
}

@Preview(
    name = "暗色 - 选中我的",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewBottomBarDarkProfile() {
    RedBookTheme {
        BottomBar(
            titles = listOf("首页", "阅读", "消息", "我的"),
            selectedIndex = 3,
            onTitleClick = {},
            fabIconRes = R.drawable.social_icons,
            onFabClick = {},
        )
    }
}



@Preview(
    name = "亮色 - 选中关注",
    showBackground = true
)
@Composable
fun PreviewHomeTopBarFollow() {
    RedBookTheme {
        HomeTopBar(
            titles = listOf("关注", "发现"),
            selectedIndex = 0,
            onTabClick = {},
            onActionClick = {},
            actionIconRes = R.drawable.search,
        )
    }
}

@Preview(
    name = "暗色 - 选中发现",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewHomeTopBarDiscover() {
    RedBookTheme {
        HomeTopBar(
            titles = listOf("关注", "发现"),
            selectedIndex = 1,
            onTabClick = {},
            onActionClick = {},
            actionIconRes = R.drawable.search,
            indicatorHeight = 3,
            indicatorWidth = 30
        )
    }
}

@Composable
fun NoteCardBar(
    modifier: Modifier= Modifier,
    backIconRes:Int,
    onBackIconClick:()-> Unit,
    avatarRes:Int,
    avatarUrl: String = "",
    name:String,
    onUserClick:()-> Unit,
    isFollowed: Boolean,
    onFollowClick:(Boolean)-> Unit,
    showFollow: Boolean = true,
){
    val primaryColor= MaterialTheme.colorScheme.primary
    val borderColor = if (isFollowed) getOnSurfaceSecondary() else primaryColor
    val textColor = if (isFollowed) getOnSurfaceTertiary() else primaryColor
 Row(
     modifier = modifier
         .fillMaxWidth()
         .defaultMinSize(minHeight = 56.dp)
         .background(MaterialTheme.colorScheme.surface)
         .padding(horizontal = 10.dp, vertical = 5.dp ),
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.SpaceBetween
 ) {

     Row(
         modifier= Modifier
             .wrapContentWidth()
             .clickable(
                 interactionSource = remember{MutableInteractionSource()},
                 indication = null
             ){
                 onUserClick()
             },
         horizontalArrangement = Arrangement.Center,
         verticalAlignment = Alignment.CenterVertically,

     ) {
         Icon(
             painter = painterResource(id = backIconRes),
             tint= MaterialTheme.colorScheme.onSurface,
             contentDescription = "返回",
             modifier = Modifier
                 .size(30.dp)
                 .clickable(
                     interactionSource = remember { MutableInteractionSource() },
                     indication = null
                 ){
                     onBackIconClick()
                 }
         )

         Spacer(modifier = Modifier.width(5.dp))
         if (avatarUrl.isNotBlank()) {
             AsyncImage(
                 model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                 contentDescription = "头像",
                 modifier = Modifier
                     .size(32.dp)
                     .clip(CircleShape),
                 contentScale = ContentScale.Crop
             )
         } else {
             Image(
                 painter = painterResource(id = avatarRes),
                 contentDescription = "头像",
                 modifier = Modifier
                     .size(32.dp)
                     .clip(CircleShape),
                 contentScale = ContentScale.Crop
             )
         }
         Spacer(modifier = Modifier.width(5.dp))

         Text(
             text = name,
             fontSize=14.sp,
             fontWeight=FontWeight.Medium,
             color = MaterialTheme.colorScheme.onSurface,
             maxLines=1
         )

     }

     if (showFollow) {
         Box(
             modifier= Modifier
                 .clip(RoundedCornerShape(20.dp))
                 .border(
                     width = 1.dp,
                     color = borderColor,
                     shape = RoundedCornerShape(20.dp)
                 )
                 .padding(horizontal = 15.dp, vertical = 5.dp)
                 .clickable(
                     interactionSource = remember { MutableInteractionSource() },
                     indication = null
                 ){
                     onFollowClick(!isFollowed)
                 },
                 contentAlignment = Alignment.Center
             ){
             Text(
                 text = if (isFollowed)"已关注" else "关注",
                 fontSize = 12.sp,
                 fontWeight = FontWeight.Medium,
                 color = textColor
             )
         }
     }

 }
}
@Preview(
    name = "未关注状态",
    showBackground = true
)
@Composable
fun PreviewNoteCardBarUnfollowed() {
    RedBookTheme {
        NoteCardBar(
            backIconRes = R.drawable.arrow_left,
            onBackIconClick = {},
            avatarRes = R.drawable.test,
            name = "咕咕嘎嘎",
            onUserClick = {},
            isFollowed = false,
            onFollowClick = {}
        )
    }
}

@Preview(
    name = "已关注状态",
    showBackground = true
)
@Composable
fun PreviewNoteCardBarFollowed() {
    RedBookTheme {
        NoteCardBar(
            backIconRes = R.drawable.arrow_left,
            onBackIconClick = {},
            avatarRes = R.drawable.test,
            name = "咕咕嘎嘎",
            onUserClick = {},
            isFollowed = true,
            onFollowClick = {}
        )
    }
}

@Preview(
    name = "暗色模式 - 未关注",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewNoteCardBarDarkUnfollowed() {
    RedBookTheme {
        NoteCardBar(
            backIconRes = R.drawable.arrow_left,
            onBackIconClick = {},
            avatarRes = R.drawable.test,
            name = "咕咕嘎嘎",
            onUserClick = {},
            isFollowed = false,
            onFollowClick = {}
        )
    }
}

@Composable
fun NoteCardBottomBar(
    modifier: Modifier= Modifier,
    initialLikeCount: Int = 0,
    initialIsLiked: Boolean = false,
    initialFavoriteCount: Int = 0,
    initialIsFavorited: Boolean = false,
    initialCommentCount: Int = 0,
    likeEnabled: Boolean = true,
    favoriteEnabled: Boolean = true,
    onCommentInputClick: () -> Unit,
    onLikeClick: (Int) -> Unit,
    onFavoriteClick: (Int) -> Unit,
    onCommentIconClick: () -> Unit,
){

    var isLiked by remember(initialIsLiked) { mutableStateOf(initialIsLiked) }
    var likeCount by remember(initialLikeCount) { mutableIntStateOf(initialLikeCount) }
    var favoriteCount by remember(initialFavoriteCount) { mutableIntStateOf(initialFavoriteCount) }
    var isFavorited by remember(initialIsFavorited) { mutableStateOf(initialIsFavorited) }

    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier=modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCommentInputClick() }
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.edit_grey),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = getOnSurfaceSecondary()
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "说点什么...",
                    fontSize = 14.sp,
                    color = getOnSurfaceSecondary(),
                    maxLines = 1
                )
            }
        }
            Spacer(modifier = Modifier.width(36.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionItem(
                    iconRes = if (isLiked) R.drawable.favorite_fill else R.drawable.favorite_light,
                    count = likeCount,
                    isActive = isLiked,
                    activeColor = primaryColor,
                    onActionClick = {
                        if (likeEnabled) {
                            isLiked = !isLiked
                            val newCount = if (isLiked) likeCount + 1 else likeCount - 1
                            likeCount = newCount
                            onLikeClick(newCount)
                        }
                    }
                )

                ActionItem(
                    iconRes = if (isFavorited) R.drawable.star_fill else R.drawable.star,
                    count = favoriteCount,
                    isActive = isFavorited,
                    activeColor = primaryColor,
                    onActionClick = {
                        if (favoriteEnabled) {
                            isFavorited = !isFavorited
                            val newCount = if (isFavorited) favoriteCount + 1 else favoriteCount - 1
                            favoriteCount = newCount
                            onFavoriteClick(newCount)
                        }
                    }
                )

                ActionItem(
                    iconRes = R.drawable.chat,
                    count = initialCommentCount,
                    isActive = false,
                    activeColor = primaryColor,
                    onActionClick = {
                        onCommentIconClick()
                    }
                )
            }


    }
}

@Composable
private fun ActionItem(
    iconRes: Int,
    count: Int,
    isActive: Boolean,
    activeColor: Color,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onActionClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
        )
        Text(
            text = formatCount(count),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color =getOnSurfaceTertiary()
        )
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewNoteCardBottomBarLight() {
    RedBookTheme {
        NoteCardBottomBar (
            onCommentInputClick = {},
            onLikeClick = {},
            onFavoriteClick = {},
            onCommentIconClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewPostActionBarDark() {
    RedBookTheme {
        NoteCardBottomBar(
            onCommentInputClick = {},
            onLikeClick = {},
            onFavoriteClick = {},
            onCommentIconClick = {}
        )
    }
}
