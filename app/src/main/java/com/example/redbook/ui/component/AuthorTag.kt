package com.example.redbook.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redbook.ui.theme.getRedBackground


@Composable
fun AuthorTag(
    modifier: Modifier = Modifier
){
    Text(
        text = "作者",
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(
                getRedBackground().copy(alpha = 0.6f),
                shape = RoundedCornerShape(15.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

