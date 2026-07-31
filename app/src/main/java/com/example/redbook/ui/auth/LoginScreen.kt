package com.example.redbook.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.redbook.R
import com.example.redbook.data.local.UserEntity
import com.example.redbook.ui.component.AppIcon
import com.example.redbook.ui.component.AuthInputRow

@Composable
fun LoginScreen(
    onLoginSuccess: (UserEntity) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(context.applicationContext as android.app.Application)
    )
    val loginState by viewModel.loginUiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(loginState.loginSuccess) {
        if (loginState.loginSuccess) {
            loginState.loggedInUser?.let { user ->
                onLoginSuccess(user)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(120.dp))
            AppIcon()

            Spacer(modifier = Modifier.height(54.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .shadow(4.dp, RoundedCornerShape(15.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(15.dp))
                    .background(Color.White, RoundedCornerShape(15.dp))
                    .padding(5.dp)
            ) {
                Column {
                    AuthInputRow(
                        label = "账号",
                        value = loginState.account,
                        onValueChange = viewModel::updateLoginAccount,
                        isError = loginState.accountError != null,
                        placeholder = "请输入账号"
                    )

                    HorizontalDivider(
                        color = Color.Black.copy(alpha = 0.1f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 15.dp)
                    )

                    AuthInputRow(
                        label = "密码",
                        value = loginState.password,
                        onValueChange = viewModel::updateLoginPassword,
                        isError = loginState.passwordError != null,
                        placeholder = "请输入密码",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    painter = painterResource(
                                        if (passwordVisible) R.drawable.unvisibility
                                        else R.drawable.visibility
                                    ),
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    )
                }
            }

            if (loginState.accountError != null) {
                Text(
                    text = loginState.accountError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (loginState.passwordError != null) {
                Text(
                    text = loginState.passwordError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (loginState.loginError != null) {
                Text(
                    text = loginState.loginError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(100.dp))

            Button(
                onClick = viewModel::login,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                enabled = !loginState.isLoading,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (loginState.isLoading) "登录中..." else "登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    text = "注册",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
