package com.example.redbook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R
import com.example.redbook.ui.component.BottomBar
import com.example.redbook.ui.component.HomeTopBar
import com.example.redbook.ui.component.PostCard
import com.example.redbook.ui.utils.formatCount

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(1) }
    var selectedBottomIndex by remember {  mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface)
        )


        HomeTopBar(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            titles = listOf("关注", "发现"),
            selectedIndex = selectedTabIndex,
            onTabClick = { index ->
                selectedTabIndex = index

            },
            onActionClick = onNavigateToSearch,
            actionIconRes = R.drawable.search
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (uiState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is HomeUiState.Success -> {
                val notes = (uiState as HomeUiState.Success).notes
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize()

                ) {
                    items(notes, key = { it.id }) { note ->
                        PostCard(
                            imageRes = note.imageRes,
                            title = note.title,
                            avatarRes = note.avatarRes,
                            userName = note.userName,
                            isLiked = note.isLiked,
                            likeCount = formatCount(note.likeCount),
                            onCardClick = { onNavigateToDetail(note.id) }
                        )
                    }
                }
            }

            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = (uiState as HomeUiState.Error).message)
                }
            }
        }
        }
        BottomBar(
            modifier = Modifier
                .fillMaxWidth(),
            titles = listOf("首页","阅读","消息","我的"),
            selectedIndex = selectedBottomIndex,
            onTitleClick = {index->
                selectedBottomIndex=index
            },
            fabIconRes = R.drawable.social_icons,
            onFabClick = {},
            indicatorHeight =3,
            indicatorWidth = 30,
        )

        Box(modifier = Modifier
            .height(16.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface))

    }
}