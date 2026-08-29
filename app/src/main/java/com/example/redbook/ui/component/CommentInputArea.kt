package com.example.redbook.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.redbook.R
import com.example.redbook.ui.theme.getOnSurfaceSecondary

@Composable
fun CommentInputArea(
    avatarRes: Int,
    avatarUrl: String = "",
    onInputFocus: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onInputFocus() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row( modifier = Modifier
            .weight(1f)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal =10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically){
            Text(
                text = "说点什么...",
                fontSize = 14.sp,
                color = getOnSurfaceSecondary(),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.image),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onImageClick() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}