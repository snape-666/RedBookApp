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
    // 当前页面状态
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var selectedPostId by remember { mutableStateOf("") }

    when (currentScreen) {
        Screen.Login -> {
            LoginScreen(
                onLoginSuccess = { userData ->
                    currentScreen = Screen.Home
                },
                onNavigateToRegister = {
                    currentScreen = Screen.Register
                }
            )
        }
        Screen.Register -> {
            RegisterScreen(
                onRegisterSuccess = {
                    currentScreen = Screen.Login
                },
                onNavigateToLogin = {
                    currentScreen = Screen.Login
                }
            )
        }
        Screen.Home -> {
            HomeScreen(
                onNavigateToDetail = { postId ->
                    selectedPostId = postId
                    currentScreen = Screen.Detail
                }
            )
        }
        Screen.Detail -> {
            DetailScreen(
                postId = selectedPostId,
                onBack = {
                    currentScreen = Screen.Home
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
}
