package com.example.redbook.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R
import com.example.redbook.ui.component.PostCard
import com.example.redbook.ui.theme.getOnSurfaceSecondary
import com.example.redbook.ui.theme.getOnSurfaceTertiary

private val OutlineLight = Color.Gray.copy(alpha = 0.5f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchViewModel =
        viewModel(factory = SearchViewModelFactory(context.applicationContext as android.app.Application))
    val state by viewModel.uiState.collectAsState()
    val history = state.historyList
    var isDeleteMode by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除全部历史记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllHistory()
                    showClearDialog = false
                }) { Text("确认", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = Color(0xFF999999))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(bottom = 10.dp)) {
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = "返回",
                    modifier = Modifier.size(28.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.clearSearch(); onBack() },
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f).height(40.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, OutlineLight, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = state.query,
                                onValueChange = viewModel::updateQuery,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.search(
                                        state.query
                                    ); focusManager.clearFocus()
                                }),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (state.query.isEmpty()) {
                                            Text(
                                                "搜索你感兴趣的内容",
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.height(20.dp).width(1.dp).background(OutlineLight))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "搜索",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { viewModel.search(state.query); focusManager.clearFocus() }
                        )
                    }
                }
            }
        }

        if (state.isSearching) {
            if (state.searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    items(state.searchResults) { note ->
                        PostCard(
                            imageRes = note.imageRes,
                            title = note.title,
                            avatarRes = note.avatarRes,
                            userName = note.userName,
                            isLiked = note.isLiked,
                            likeCount = note.likeCount.toString(),
                            onCardClick = { onNavigateToDetail(note.id) }
                        )
                    }
                }
            }
            } else {
                if (history.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("历史记录", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isDeleteMode) {
                            Text(
                                "全部删除", fontSize = 13.sp, color = getOnSurfaceTertiary(),
                                modifier = Modifier.clickable { showClearDialog = true })
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(Modifier.height(16.dp).width(1.dp).background(OutlineLight))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "完成", fontSize = 13.sp, color = getOnSurfaceTertiary(),
                                modifier = Modifier.clickable { isDeleteMode = false })
                        } else {
                            Icon(
                                painterResource(R.drawable.trash),
                                contentDescription = "删除",
                                modifier = Modifier.size(22.dp).clickable { isDeleteMode = true },
                                tint = Color.Unspecified
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        history.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, OutlineLight, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .clickable {
                                        if (isDeleteMode) viewModel.deleteHistory(item)
                                        else viewModel.search(item)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (item.length > 8) item.take(8) + "…" else item,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isDeleteMode) {
                                        Icon(
                                            painter = painterResource(R.drawable.close_round),
                                            contentDescription = "删除",
                                            modifier = Modifier.size(18.dp),
                                            tint = getOnSurfaceSecondary()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "猜你想搜", modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.suggestions) { title ->
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.search(title) }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

