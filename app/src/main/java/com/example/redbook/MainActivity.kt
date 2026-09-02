package com.example.redbook

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.redbook.data.repository.RealtimeRepository
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.notification.NotifClickRouter
import com.example.redbook.notification.NotifPrefs
import com.example.redbook.notification.NotificationService
import com.example.redbook.notification.PendingNotif
import com.example.redbook.ui.PostDetail.DetailScreen
import com.example.redbook.ui.auth.LoginScreen
import com.example.redbook.ui.auth.RegisterScreen
import com.example.redbook.ui.theme.RedBookTheme
import com.example.redbook.ui.home.HomeScreen
import com.example.redbook.ui.search.SearchScreen
import com.example.redbook.ui.publish.PublishScreen
import com.example.redbook.ui.video.VideoDetailScreen
import com.example.redbook.ui.video.VideoFeedScreen
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
                        onToggleDarkTheme = { isDarkTheme = !isDarkTheme },
                        initialIntent = intent
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTapDispatcher?.invoke(intent)
    }

    companion object {
        /** AppScreen 注册的通知点击分发器(Activity 存活期间有效) */
        @Volatile
        var notificationTapDispatcher: ((Intent) -> Unit)? = null
    }
}
@Composable
fun AppScreen(
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    initialIntent: Intent? = null
) {
    val context = LocalContext.current
    val browseRepo = remember { SupabaseAuthRepository(context.applicationContext as android.app.Application) }
    val realtimeRepo = remember { RealtimeRepository(context.applicationContext as android.app.Application) }
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var selectedPostId by remember { mutableStateOf("") }
    var selectedVideoId by remember { mutableStateOf("") }
    var selectedVideoUrl by remember { mutableStateOf("") }
    var chatUserName by remember { mutableStateOf("") }
    var chatUserAvatarUrl by remember { mutableStateOf("") }
    var chatPeerUid by remember { mutableStateOf("") }
    var chatConversationId by remember { mutableStateOf("") }
    var viewProfileUid by remember { mutableStateOf("") }
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

    // ---- 未读角标状态 ----
    var unreadLikesFavs by remember { mutableIntStateOf(0) }
    var unreadFollows by remember { mutableIntStateOf(0) }
    var unreadComments by remember { mutableIntStateOf(0) }
    var unreadMessages by remember { mutableIntStateOf(0) }

    // ---- 通知点击/定位状态 ----
    var pendingNotif by remember { mutableStateOf<PendingNotif?>(null) }
    var highlightActorUid by remember { mutableStateOf("") }

    fun startNotificationService() {
        if (userUid.isBlank()) return
        try {
            context.startForegroundService(Intent(context, NotificationService::class.java))
        } catch (_: Exception) { }
    }

    // 首次进入(登录后)申请系统通知权限
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 授权后重启服务触发“离线未读补发”(此前无权限时补发被跳过且不推进水印)
        if (granted && userUid.isNotBlank()) {
            startNotificationService()
        }
    }
    LaunchedEffect(userUid) {
        if (userUid.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 注册通知点击分发(供 onNewIntent / 冷启动 intent 使用)
    fun handleNotifIntent(intent: Intent?) {
        if (intent?.action == NotifClickRouter.ACTION_NOTIF_TAP) {
            val n = NotifClickRouter.parse(intent)
            if (!n.isBlank) pendingNotif = n
        }
    }
    DisposableEffect(Unit) {
        MainActivity.notificationTapDispatcher = { i -> handleNotifIntent(i) }
        onDispose {
            if (MainActivity.notificationTapDispatcher != null) {
                MainActivity.notificationTapDispatcher = null
            }
        }
    }
    LaunchedEffect(Unit) {
        handleNotifIntent(initialIntent)
    }

    // 通知点击跳转
    fun handleNotificationTap(n: PendingNotif) {
        if (userUid.isBlank()) { pendingNotif = n; return }
        when (n.type) {
            "like", "favorite" -> {
                unreadLikesFavs = 0
                scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("like", "favorite")) } catch (_: Exception) { } }
                highlightActorUid = n.actorUid
                screenStack = listOf(Screen.Home, Screen.ReceivedReactions)
                currentScreen = Screen.ReceivedReactions
            }
            "comment", "reply" -> {
                unreadComments = 0
                scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("comment", "reply")) } catch (_: Exception) { } }
                highlightActorUid = n.actorUid
                screenStack = listOf(Screen.Home, Screen.ReceivedComments)
                currentScreen = Screen.ReceivedComments
            }
            "follow" -> {
                unreadFollows = 0
                scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("follow")) } catch (_: Exception) { } }
                highlightActorUid = n.actorUid
                screenStack = listOf(Screen.Home, Screen.Followers)
                currentScreen = Screen.Followers
            }
            "dm" -> {
                chatUserName = n.actorName.ifBlank { "小红书用户" }
                chatUserAvatarUrl = n.actorAvatar
                chatPeerUid = n.actorUid
                chatConversationId = ""
                screenStack = listOf(Screen.Home, Screen.Chat)
                currentScreen = Screen.Chat
                scope.launch {
                    try {
                        // 通知未带头像(旧通知/查询失败)时按 uid 兜底拉一次
                        if (chatUserAvatarUrl.isBlank()) {
                            browseRepo.getUserByUid(n.actorUid)?.let {
                                val av = it.optString("avatar_url", "")
                                if (av.isNotBlank()) chatUserAvatarUrl = av
                            }
                        }
                    } catch (_: Exception) { }
                    try {
                        val conv = realtimeRepo.getOrCreateConversation(userUid, n.actorUid)
                        chatConversationId = conv
                        unreadMessages = 0
                        realtimeRepo.markConversationRead(conv, userUid)
                    } catch (_: Exception) { }
                }
            }
        }
        pendingNotif = null
    }

    // 登录后:启动通知前台服务 + 合并云端通知设置
    LaunchedEffect(userUid) {
        if (userUid.isNotBlank()) {
            NotifPrefs.setCachedLoginUid(userUid, context)
            startNotificationService()
            // 拉云端通知设置,version 较新则覆盖本地(登录同步)
            try {
                val cloud = realtimeRepo.getNotificationSettings(userUid)
                val local = NotifPrefs.load(userUid, context)
                if (cloud.version >= local.version) {
                    NotifPrefs.save(userUid, cloud, context)
                }
            } catch (_: Exception) { }
        }
    }

    // 通知点击消费:冷启动登录前暂存,登录后 / 热启动 onNewIntent 均会触发跳转
    LaunchedEffect(pendingNotif, userUid) {
        val p = pendingNotif
        if (p != null && !p.isBlank && userUid.isNotBlank()) {
            handleNotificationTap(p)
        }
    }

    // 建立实时连接：登录成功后拉取初始未读数 + 订阅 WebSocket 增量
    LaunchedEffect(userUid) {
        if (userUid.isBlank()) return@LaunchedEffect
        SupabaseAuthRepository.currentUserName = userName
        SupabaseAuthRepository.currentUserAvatar = userAvatarUrl
        // 初始拉取（兜底，断线恢复也能对齐）
        scope.launch {
            try {
                val counts = realtimeRepo.getUnreadCounts(userUid)
                unreadLikesFavs = counts.likesFavs
                unreadFollows = counts.follows
                unreadComments = counts.comments
            } catch (_: Exception) { }
            try {
                unreadMessages = realtimeRepo.getUnreadConversationCount(userUid)
            } catch (_: Exception) { }
        }
        realtimeRepo.connect(userUid, object : RealtimeRepository.RealtimeListener {
            override fun onNotification(record: org.json.JSONObject) {
                when (record.optString("type", "")) {
                    "like", "favorite" -> unreadLikesFavs++
                    "follow" -> unreadFollows++
                    "comment", "reply" -> unreadComments++
                }
            }
            override fun onMessage(record: org.json.JSONObject) {
                unreadMessages++
            }
            override fun onStatus(connected: Boolean) { }
        })
    }
    DisposableEffect(Unit) {
        onDispose { realtimeRepo.disconnect() }
    }

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

    // 账户间桥梁：与某用户建立会话并进入聊天页
    fun openChatWith(peerUid: String, peerName: String, peerAvatar: String) {
        if (peerUid.isBlank() || peerUid == userUid) return
        chatPeerUid = peerUid
        chatUserName = peerName
        chatUserAvatarUrl = peerAvatar
        chatConversationId = ""
        scope.launch {
            try {
                chatConversationId = realtimeRepo.getOrCreateConversation(userUid, peerUid)
            } catch (_: Exception) { }
            navigateTo(Screen.Chat)
        }
    }

    // 进入对方主页
    fun openUserProfile(targetUid: String) {
        if (targetUid.isBlank() || targetUid == userUid) return
        viewProfileUid = targetUid
        navigateTo(Screen.UserProfile)
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
                    SupabaseAuthRepository.currentUserName = userName
                    SupabaseAuthRepository.currentUserAvatar = userData.avatarUrl
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
                unreadMessageCount = unreadMessages,
                onNavigateToDetail = { postId ->
                    selectedPostId = postId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                },
                onNavigateToSearch = { navigateTo(Screen.Search) },
                onNavigateToPublish = { navigateTo(Screen.Publish) },
                onNavigateToProfile = { navigateTo(Screen.Profile) },
                onNavigateToMessages = {
                    // 只导航到消息页，不清零未读；点击具体分组时再清零对应未读
                    navigateTo(Screen.Messages)
                },
                onNavigateToVideoFeed = {
                    navigateTo(Screen.VideoFeed)
                },
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
                onBack = { goBack() },
                onSendMessage = { peerUid, peerName, peerAvatar ->
                    openChatWith(peerUid, peerName, peerAvatar)
                },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
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
        Screen.UserProfile -> {
            ProfileScreen(
                userUid = viewProfileUid,
                userName = "",
                userXhsId = "",
                ipLocation = "未知",
                followCount = 0, fansCount = 0, likeCount = 0,
                onBack = { goBack() },
                onEditProfile = {},
                onBottomTabClick = {},
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
                viewerUid = userUid,
                onSendMessage = { peerUid, peerName, peerAvatar ->
                    openChatWith(peerUid, peerName, peerAvatar)
                }
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
                onBottomTabClick = { idx ->
                    when (idx) {
                        0 -> { screenStack = listOf(Screen.Home); currentScreen = Screen.Home }
                        1 -> navigateTo(Screen.VideoFeed)
                    }
                },
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
                onNavigateToMessages = {
                    // 只导航到消息页，不清零未读；点击具体分组时再清零对应未读
                    navigateTo(Screen.Messages)
                },
                unreadMessageCount = unreadMessages,
                onLogout = {
                    realtimeRepo.disconnect()
                    unreadLikesFavs = 0
                    unreadFollows = 0
                    unreadComments = 0
                    unreadMessages = 0
                    SupabaseAuthRepository.currentUserName = ""
                    SupabaseAuthRepository.currentUserAvatar = ""
                    userUid = ""
                    userXhsId = ""
                    userName = ""
                    userAccount = ""
                    userEmail = ""
                    userGender = ""
                    userBirthday = ""
                    userAvatarUrl = ""
                    userBackgroundUrl = ""
                    chatUserName = ""
                    chatUserAvatarUrl = ""
                    chatPeerUid = ""
                    chatConversationId = ""
                    viewProfileUid = ""
                    pendingNotif = null
                    highlightActorUid = ""
                    loginResetKey++
                    screenStack = listOf(Screen.Login)
                    currentScreen = Screen.Login
                    try {
                        context.stopService(Intent(context, NotificationService::class.java))
                    } catch (_: Exception) { }
                    // 清除缓存 uid,防止 START_STICKY 服务在进程被杀后带旧账号重启
                    NotifPrefs.setCachedLoginUid("", context)
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
                userUid = userUid,
                unreadLikesFavs = unreadLikesFavs,
                unreadFollows = unreadFollows,
                unreadComments = unreadComments,
                unreadMessages = unreadMessages,
                onBottomTabClick = { idx ->
                    when (idx) {
                        0 -> { screenStack = listOf(Screen.Home); currentScreen = Screen.Home }
                        1 -> navigateTo(Screen.VideoFeed)
                        3 -> navigateTo(Screen.Profile)
                    }
                },
                onPublish = { navigateTo(Screen.Publish) },
                onLikeFavoriteClick = {
                    unreadLikesFavs = 0
                    scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("like", "favorite")) } catch (_: Exception) { } }
                    navigateTo(Screen.ReceivedReactions)
                },
                onCommentClick = {
                    unreadComments = 0
                    scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("comment", "reply")) } catch (_: Exception) { } }
                    navigateTo(Screen.ReceivedComments)
                },
                onFollowClick = {
                    unreadFollows = 0
                    scope.launch { try { realtimeRepo.markNotificationsRead(userUid, listOf("follow")) } catch (_: Exception) { } }
                    navigateTo(Screen.Followers)
                },
                onConversationClick = { name, avatar, peerUid ->
                    chatUserName = name
                    chatUserAvatarUrl = avatar
                    chatPeerUid = peerUid
                    chatConversationId = ""
                    scope.launch {
                        try {
                            chatConversationId = realtimeRepo.getOrCreateConversation(userUid, peerUid)
                        } catch (_: Exception) { }
                        navigateTo(Screen.Chat)
                    }
                }
            )
        }
        Screen.ReceivedReactions -> {
            ReceivedReactionsScreen(
                userUid = userUid,
                highlightActorUid = highlightActorUid,
                onHighlightConsumed = { highlightActorUid = "" },
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
                },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
            )
        }
        Screen.ReceivedComments -> {
            ReceivedCommentsScreen(
                userUid = userUid,
                highlightActorUid = highlightActorUid,
                onHighlightConsumed = { highlightActorUid = "" },
                onBack = { goBack() },
                onPostClick = { postId, commentId ->
                    selectedPostId = postId
                    scrollToCommentId = commentId
                    recordBrowse(postId)
                    navigateTo(Screen.Detail)
                },
                onVideoClick = { videoId, videoUrl, commentId ->
                    selectedVideoId = videoId
                    selectedVideoUrl = videoUrl
                    scrollToCommentId = commentId
                    recordBrowse(videoId)
                    navigateTo(Screen.Video)
                },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
            )
        }
        Screen.Chat -> {
            // 进入聊天页：私信未读清零
            LaunchedEffect(Unit) {
                if (chatConversationId.isNotBlank()) {
                    unreadMessages = 0
                    scope.launch { try { realtimeRepo.markConversationRead(chatConversationId, userUid) } catch (_: Exception) { } }
                }
            }
            ChatScreen(
                userName = chatUserName,
                avatarUrl = chatUserAvatarUrl,
                currentUserUid = userUid,
                peerUid = chatPeerUid,
                conversationId = chatConversationId,
                repository = realtimeRepo,
                myAvatarUrl = userAvatarUrl,
                onBack = { goBack() },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
            )
        }
        Screen.Followers -> {
            FollowersScreen(
                userUid = userUid,
                highlightActorUid = highlightActorUid,
                onHighlightConsumed = { highlightActorUid = "" },
                onBack = { goBack() },
                onSendMessage = { peerUid, peerName, peerAvatar ->
                    openChatWith(peerUid, peerName, peerAvatar)
                },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
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
                    SupabaseAuthRepository.currentUserAvatar = avatarUrl
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
        Screen.VideoFeed -> {
            VideoFeedScreen(
                userUid = userUid,
                userAvatarUrl = userAvatarUrl,
                onBack = { goBack() },
                onUserClick = { targetUid -> openUserProfile(targetUid) }
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
                userAvatarUrl = userAvatarUrl,
                authorAvatarUrl = userAvatarUrl,
                onBack = { goBack() },
                onFollowClick = {},
                onLikeClick = {},
                onFavoriteClick = {},
                onSendMessage = { peerUid, peerName, peerAvatar ->
                    openChatWith(peerUid, peerName, peerAvatar)
                },
                onUserClick = { targetUid -> openUserProfile(targetUid) },
                scrollToCommentId = scrollToCommentId
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
                userUid = userUid,
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
    object VideoFeed : Screen()
    object Profile : Screen()
    object UserProfile : Screen()
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
