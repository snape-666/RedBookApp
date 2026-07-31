package com.example.redbook.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.redbook.R

@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    size: Int = 120,
    iconSize: Int = 60,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Int = 20
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.appicon),
            contentDescription = "App Logo",
            modifier = Modifier.size(iconSize.dp),
            tint = Color.Unspecified
        )
    }
}