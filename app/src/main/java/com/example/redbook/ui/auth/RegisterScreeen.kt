package com.example.redbook.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.example.redbook.R
import com.example.redbook.ui.component.AppIcon
import com.example.redbook.ui.component.AuthInputRow

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(context.applicationContext as android.app.Application)
    )
    val registerState by viewModel.registerUiState.collectAsStateWithLifecycle()
    val codeState by viewModel.verificationCodeState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(registerState.registerSuccess) {
        if (registerState.registerSuccess) {
            onRegisterSuccess()
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
                .padding(horizontal = 32.dp)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "注册",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            AppIcon()

            Spacer(modifier = Modifier.height(54.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(15.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(15.dp))
                    .background(Color.White, RoundedCornerShape(15.dp))
                    .padding(5.dp)
            ) {
                Column {

                    AuthInputRow(
                        label = "账号",
                        value = registerState.account,
                        onValueChange = viewModel::updateRegisterAccount,
                        isError = registerState.accountError != null,
                        placeholder = "请输入账号"
                    )


                    HorizontalDivider(
                        color = Color.Black.copy(alpha = 0.1f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 15.dp)
                    )


                    AuthInputRow(
                        label = "密码",
                        value = registerState.password,
                        onValueChange = viewModel::updateRegisterPassword,
                        isError = registerState.passwordError != null,
                        placeholder = "请输入密码",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
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

                    HorizontalDivider(
                        color = Color.Black.copy(alpha = 0.1f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 15.dp)
                    )


                    AuthInputRow(
                        label = "邮箱",
                        value = registerState.email,
                        onValueChange = viewModel::updateRegisterEmail,
                        isError = registerState.emailError != null,
                        placeholder = "请输入邮箱",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    HorizontalDivider(
                        color = Color.Black.copy(alpha = 0.1f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 15.dp)
                    )


                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = registerState.verificationCode,
                            onValueChange = viewModel::updateRegisterVerificationCode,
                            placeholder = { Text("验证码", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            isError = registerState.codeError != null,
                            singleLine = true
                        )

                        Button(
                            onClick = viewModel::sendVerificationCode,
                            modifier = Modifier
                                .height(40.dp)
                                .padding(start = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (codeState.isSending)
                                    Color.Gray
                                else
                                    MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            enabled = !codeState.isSending,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (codeState.isSending)
                                    "${codeState.remainingSeconds}s"
                                else
                                    "获取验证码",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 错误提示
            if (registerState.accountError != null) {
                Text(
                    text = registerState.accountError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (registerState.passwordError != null) {
                Text(
                    text = registerState.passwordError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (registerState.emailError != null) {
                Text(
                    text = registerState.emailError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (registerState.codeError != null) {
                Text(
                    text = registerState.codeError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (registerState.registerError != null) {
                Text(
                    text = registerState.registerError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // 注册按钮
            Button(
                onClick = viewModel::register,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                enabled = !registerState.isLoading,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (registerState.isLoading) "注册中..." else "注册",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 已有账号？去登录
            Text(
                text = "已有账号？去登录",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNavigateToLogin() }
            )
        }
    }
}