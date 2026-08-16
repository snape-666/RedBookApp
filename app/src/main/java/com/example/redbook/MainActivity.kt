package com.example.redbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.redbook.ui.PostDetail.DetailScreen
import com.example.redbook.ui.auth.LoginScreen
import com.example.redbook.ui.auth.RegisterScreen
import com.example.redbook.ui.theme.RedBookTheme
import com.example.redbook.ui.home.HomeScreen
import com.example.redbook.ui.search.SearchScreen
import com.example.redbook.ui.publish.PublishScreen
import com.example.redbook.ui.video.VideoDetailScreen
import com.example.redbook.ui.profile.ProfileScreen
import com.example.redbook.ui.draft.DraftScreen
import com.example.redbook.data.model.Draft
import com.example.redbook.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RedBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
}
@Composable
fun AppScreen() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var selectedPostId by remember { mutableStateOf("") }
    var selectedVideoId by remember { mutableStateOf("") }
    var selectedVideoUrl by remember { mutableStateOf("") }
    var scrollToCommentId by remember { mutableStateOf("") }
    var screenStack by remember { mutableStateOf(listOf<Screen>(Screen.Login)) }
    var userUid by remember { mutableStateOf("") }
    var userXhsId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var editingDraft by remember { mutableStateOf<Draft?>(null) }

    fun navigateTo(screen: Screen) {
        screenStack = screenStack + screen
        currentScreen = screen
    }

    fun goBack() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
            currentScreen = screenStack.last()
        }
    }

    when (currentScreen) {
        Screen.Login -> {
            LoginScreen(
                onLoginSuccess = { userData ->
                    userUid = userData.uid
                    userXhsId = userData.xhsId
                    userName = userData.nickname.ifBlank { userData.account }
                    screenStack = listOf(Screen.Home)
                    currentScreen = Screen.Home
                },
                onNavigateToRegister = { navigateTo(Screen.Register) }
            )
        }
        Screen.Register -> {
            RegisterScreen(
                onRegisterSuccess = { goBack() },
                onNavigateToLogin = { goBack() }
            )
        }
        Screen.Home -> {
            HomeScreen(
                userUid = userUid,
                onNavigateToDetail = { postId ->
                    selectedPostId = postId
                    navigateTo(Screen.Detail)
                },
                onNavigateToSearch = { navigateTo(Screen.Search) },
                onNavigateToPublish = { navigateTo(Screen.Publish) },
                onNavigateToProfile = { navigateTo(Screen.Profile) },
                onNavigateToVideo = { videoId, videoUrl ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    navigateTo(Screen.Video)
                }
            )
        }
        Screen.Detail -> {
            DetailScreen(
                postId = selectedPostId,
                userUid = userUid,
                userXhsId = userXhsId,
                userName = userName,
                scrollToCommentId = scrollToCommentId,
                onCommentScrolled = { scrollToCommentId = "" },
                onBack = { goBack() }
            )
        }
        Screen.Publish -> {
            PublishScreen(
                authorUid = userUid,
                authorXhsId = userXhsId,
                authorName = userName,
                editDraft = editingDraft,
                onBack = { editingDraft = null; goBack() },
                onPublished = { editingDraft = null; goBack() }
            )
        }
        Screen.Profile -> {
            ProfileScreen(
                userUid = userUid,
                userName = userName.ifBlank { "用户" },
                userXhsId = userXhsId.ifBlank { "00000000000" },
                ipLocation = "未知",
                followCount = 0, fansCount = 0, likeCount = 0,
                onBack = { goBack() },
                onEditProfile = { },
                onBottomTabClick = { idx -> if (idx == 0) { screenStack = listOf(Screen.Home); currentScreen = Screen.Home } },
                onPostClick = { postId -> selectedPostId = postId; navigateTo(Screen.Detail) },
                onVideoClick = { videoId, videoUrl ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    navigateTo(Screen.Video)
                },
                onCommentClick = { postId, commentId ->
                    selectedPostId = postId
                    scrollToCommentId = commentId
                    navigateTo(Screen.Detail)
                },
                onPublish = { navigateTo(Screen.Publish) },
                onDraftClick = { navigateTo(Screen.Draft) }
            )
        }
        Screen.Draft -> {
            DraftScreen(
                userUid = userUid,
                userXhsId = userXhsId,
                onBack = { goBack() },
                onEditDraft = { draft ->
                    editingDraft = draft
                    navigateTo(Screen.Publish)
                }
            )
        }
        Screen.Video -> {
            VideoDetailScreen(
                videoUrl = selectedVideoUrl.ifBlank { "test" },
                title = "视频",
                authorName = userName.ifBlank { "作者" },
                authorAvatar = R.drawable.test,
                isFollowed = false,
                likeCount = 0, favoriteCount = 0, commentCount = 0,
                videoId = selectedVideoId,
                userUid = userUid,
                userXhsId = userXhsId,
                onBack = { goBack() },
                onFollowClick = {},
                onLikeClick = {},
                onFavoriteClick = {}
            )
        }
        Screen.Search -> {
            SearchScreen(
                onBack = { goBack() },
                onNavigateToDetail = { postId ->
                    selectedPostId = postId
                    navigateTo(Screen.Detail)
                }
            )
        }
    }
}

// 定义页面类型
sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object Home : Screen()
    object Detail : Screen()
    object Search : Screen()
    object Publish : Screen()
    object Video : Screen()
    object Profile : Screen()
    object Draft : Screen()
}
