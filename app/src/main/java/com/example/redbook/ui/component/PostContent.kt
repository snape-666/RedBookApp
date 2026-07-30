package com.example.redbook.ui.detail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.data.model.PostDetail
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale

@Composable
fun PostContent(
    post: PostDetail,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier
        .fillMaxWidth()
        ) {


        Image(
            painter = painterResource(id = post.imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(),
            contentScale = ContentScale.Crop
        )


        Text(
            text = post.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 10.dp, end = 10.dp)
        )


        Text(
            text = post.content,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp, start = 10.dp, end = 10.dp)
        )


        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal= 10.dp)
        ) {
            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(post.publishTime),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " · ${post.ipLocation}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}