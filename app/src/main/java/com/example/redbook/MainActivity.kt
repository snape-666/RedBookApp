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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.redbook.ui.browse.BrowseScreen
import com.example.redbook.ui.editprofile.EditProfileScreen
import com.example.redbook.ui.notificationsetting.NotificationSettingScreen
import com.example.redbook.ui.messages.MessagesScreen
import com.example.redbook.ui.messages.ReceivedReactionsScreen
import com.example.redbook.ui.messages.ReceivedCommentsScreen
import com.example.redbook.ui.messages.FollowersScreen
import com.example.redbook.ui.messages.ChatScreen
import com.example.redbook.data.model.Draft
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.R
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            RedBookTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen(
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}
@Composable
fun AppScreen(
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val browseRepo = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var selectedPostId by remember { mutableStateOf("") }
    var selectedVideoId by remember { mutableStateOf("") }
    var selectedVideoUrl by remember { mutableStateOf("") }
    var chatUserName by remember { mutableStateOf("") }
    var chatUserAvatarUrl by remember { mutableStateOf("") }
    var scrollToCommentId by remember { mutableStateOf("") }
    var screenStack by remember { mutableStateOf(listOf<Screen>(Screen.Login)) }
    var userUid by remember { mutableStateOf("") }
    var userXhsId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userAccount by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userGender by remember { mutableStateOf("") }
    var userBirthday by remember { mutableStateOf("") }
    var userAvatarUrl by remember { mutableStateOf("") }
    var userBackgroundUrl by remember { mutableStateOf("") }
    var editingDraft by remember { mutableStateOf<Draft?>(null) }
    var loginResetKey by remember { mutableIntStateOf(0) }

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

    fun recordBrowse(postId: String) {
        if (userUid.isNotBlank() && postId.isNotBlank()) {
            scope.launch { try { browseRepo.recordBrowse(userUid, postId) } catch (_: Exception) { } }
        }
    }

    when (currentScreen) {
        Screen.Login -> {
            LoginScreen(
                resetKey = loginResetKey,
                onLoginSuccess = { userData ->
                    userUid = userData.uid
                    userXhsId = userData.xhsId
                    userName = userData.nickname.ifBlank { userData.account }
                    userAccount = userData.account
                    userEmail = userData.email
                    userGender = userData.gender
                    userBirthday = userData.birthday
                    userAvatarUrl = userData.avatarUrl
                    userBackgroundUrl = userData.backgroundUrl
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
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                },
                onNavigateToSearch = { navigateTo(Screen.Search) },
                onNavigateToPublish = { navigateTo(Screen.Publish) },
                onNavigateToProfile = { navigateTo(Screen.Profile) },
                onNavigateToMessages = { navigateTo(Screen.Messages) },
                onNavigateToVideo = { videoId, videoUrl ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    recordBrowse(videoId)
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
                userAvatarUrl = userAvatarUrl,
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
                authorAvatar = userAvatarUrl,
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
                gender = userGender,
                birthday = userBirthday,
                avatarUrl = userAvatarUrl,
                backgroundUrl = userBackgroundUrl,
                onBack = { goBack() },
                onEditProfile = { navigateTo(Screen.EditProfile) },
                onBottomTabClick = { idx -> if (idx == 0) { screenStack = listOf(Screen.Home); currentScreen = Screen.Home } },
                onPostClick = { postId -> selectedPostId = postId; recordBrowse(postId); navigateTo(Screen.Detail) },
                onVideoClick = { videoId, videoUrl ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    recordBrowse(videoId)
                    navigateTo(Screen.Video)
                },
                onCommentClick = { postId, commentId ->
                    selectedPostId = postId
                    scrollToCommentId = commentId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                },
                onPublish = { navigateTo(Screen.Publish) },
                onDraftClick = { navigateTo(Screen.Draft) },
                onBrowseClick = { navigateTo(Screen.Browse) },
                account = userAccount,
                email = userEmail,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onNotification = { navigateTo(Screen.NotificationSetting) },
                onLogout = {
                    userUid = ""
                    userXhsId = ""
                    userName = ""
                    userAccount = ""
                    userEmail = ""
                    userGender = ""
                    userBirthday = ""
                    userAvatarUrl = ""
                    userBackgroundUrl = ""
                    loginResetKey++
                    screenStack = listOf(Screen.Login)
                    currentScreen = Screen.Login
                }
            )
        }
        Screen.Browse -> {
            BrowseScreen(
                userUid = userUid,
                onBack = { goBack() },
                onPostClick = { postId ->
                    selectedPostId = postId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                },
                onVideoClick = { videoId, videoUrl ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    recordBrowse(videoId)
                    navigateTo(Screen.Video)
                }
            )
        }
        Screen.Messages -> {
            MessagesScreen(
                onBottomTabClick = { idx ->
                    when (idx) {
                        0 -> { screenStack = listOf(Screen.Home); currentScreen = Screen.Home }
                        3 -> navigateTo(Screen.Profile)
                    }
                },
                onPublish = { navigateTo(Screen.Publish) },
                onLikeFavoriteClick = { navigateTo(Screen.ReceivedReactions) },
                onCommentClick = { navigateTo(Screen.ReceivedComments) },
                onFollowClick = { navigateTo(Screen.Followers) },
                onConversationClick = { name, avatar ->
                    chatUserName = name
                    chatUserAvatarUrl = avatar
                    navigateTo(Screen.Chat)
                }
            )
        }
        Screen.ReceivedReactions -> {
            ReceivedReactionsScreen(
                onBack = { goBack() },
                onPostClick = { postId ->
                    selectedPostId = postId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                }
            )
        }
        Screen.ReceivedComments -> {
            ReceivedCommentsScreen(
                onBack = { goBack() },
                onPostClick = { postId ->
                    selectedPostId = postId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                }
            )
        }
        Screen.Chat -> {
            ChatScreen(
                userName = chatUserName,
                avatarUrl = chatUserAvatarUrl,
                onBack = { goBack() }
            )
        }
        Screen.Followers -> {
            FollowersScreen(
                userUid = userUid,
                onBack = { goBack() },
                onSendMessage = { }
            )
        }
        Screen.EditProfile -> {
            EditProfileScreen(
                userUid = userUid,
                userName = userName,
                userXhsId = userXhsId,
                gender = userGender,
                birthday = userBirthday,
                avatarUrl = userAvatarUrl,
                backgroundUrl = userBackgroundUrl,
                onBack = { goBack() },
                onDataChanged = { name, gender, birthday, avatarUrl, backgroundUrl ->
                    userName = name
                    userGender = gender
                    userBirthday = birthday
                    userAvatarUrl = avatarUrl
                    userBackgroundUrl = backgroundUrl
                }
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
                authorAvatarUrl = userAvatarUrl,
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
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                }
            )
        }
        Screen.NotificationSetting -> {
            NotificationSettingScreen(
                onBack = { goBack() }
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
    object Browse : Screen()
    object Messages : Screen()
    object ReceivedReactions : Screen()
    object ReceivedComments : Screen()
    object Followers : Screen()
    object Chat : Screen()
    object EditProfile : Screen()
    object NotificationSetting : Screen()
}
