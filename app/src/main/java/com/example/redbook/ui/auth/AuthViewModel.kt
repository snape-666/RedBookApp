package com.example.redbook.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.repository.SupabaseAuthRepository
import com.example.redbook.ui.utils.Validator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SupabaseAuthRepository(application)

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    fun login() {
        val currentState = _loginUiState.value

        val input = currentState.email.trim()
        val inputError = if (input.contains("@")) Validator.getEmailError(input) else Validator.getAccountError(input)
        if (inputError != null) {
            _loginUiState.value = currentState.copy(
                emailError = inputError,
                passwordError = null,
                loginError = null
            )
            return
        }

        val passwordError = Validator.getPasswordError(currentState.password)
        if (passwordError != null) {
            _loginUiState.value = currentState.copy(
                passwordError = passwordError,
                emailError = null,
                loginError = null
            )
            return
        }

        viewModelScope.launch {
            _loginUiState.value = currentState.copy(isLoading = true)
            val result = repository.login(currentState.email, currentState.password)
            result.onSuccess { userData ->
                _loginUiState.value = currentState.copy(
                    isLoading = false,
                    loginSuccess = true,
                    loggedInUser = userData,
                    emailError = null,
                    passwordError = null,
                    loginError = null
                )
            }.onFailure { error ->
                _loginUiState.value = currentState.copy(
                    isLoading = false,
                    loginError = error.message ?: "登录失败"
                )
            }
        }
    }

    fun register() {
        val currentState = _registerUiState.value

        val accountError = Validator.getAccountError(currentState.account)
        if (accountError != null) {
            _registerUiState.value = currentState.copy(
                accountError = accountError,
                passwordError = null,
                emailError = null,
                registerError = null
            )
            return
        }

        val passwordError = Validator.getPasswordError(currentState.password)
        if (passwordError != null) {
            _registerUiState.value = currentState.copy(
                passwordError = passwordError,
                accountError = null,
                emailError = null,
                registerError = null
            )
            return
        }

        val emailError = Validator.getEmailError(currentState.email)
        if (emailError != null) {
            _registerUiState.value = currentState.copy(
                emailError = emailError,
                accountError = null,
                passwordError = null,
                registerError = null
            )
            return
        }

        viewModelScope.launch {
            _registerUiState.value = currentState.copy(isLoading = true)
            val result = repository.register(
                email = currentState.email,
                password = currentState.password,
                account = currentState.account,
                nickname = currentState.nickname.takeIf { it.isNotBlank() }
            )
            result.onSuccess {
                _registerUiState.value = currentState.copy(
                    isLoading = false,
                    registerSuccess = true,
                    registerError = null
                )
            }.onFailure { error ->
                _registerUiState.value = currentState.copy(
                    isLoading = false,
                    registerError = error.message ?: "注册失败"
                )
            }
        }
    }

    private val _passwordResetState = MutableStateFlow(PasswordResetState())
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

    fun updateResetEmail(email: String) {
        _passwordResetState.value = PasswordResetState(email = email)
    }

    fun sendResetCode() {
        val state = _passwordResetState.value
        val emailError = Validator.getEmailError(state.email)
        if (emailError != null) {
            _passwordResetState.value = state.copy(emailError = emailError)
            return
        }
        viewModelScope.launch {
            _passwordResetState.value = state.copy(isLoading = true)
            repository.requestResetCode(state.email).onSuccess { code ->
                _passwordResetState.value = _passwordResetState.value.copy(
                    isLoading = false, codeSent = true, generatedCode = code
                )
            }.onFailure { error ->
                _passwordResetState.value = state.copy(
                    isLoading = false, error = error.message ?: "发送失败"
                )
            }
        }
    }

    fun doResetPassword() {
        val state = _passwordResetState.value
        if (state.verificationCode != state.generatedCode) {
            _passwordResetState.value = state.copy(error = "验证码错误")
            return
        }
        val passwordError = Validator.getPasswordError(state.newPassword)
        if (passwordError != null) {
            _passwordResetState.value = state.copy(passwordError = passwordError)
            return
        }
        viewModelScope.launch {
            _passwordResetState.value = state.copy(isLoading = true)
            repository.verifyCodeAndReset(state.email, state.verificationCode, state.newPassword)
                .onSuccess {
                    _passwordResetState.value = PasswordResetState(resetSuccess = true)
                }.onFailure { error ->
                    _passwordResetState.value = state.copy(
                        isLoading = false, error = error.message ?: "重置失败"
                    )
                }
        }
    }

    fun updateResetCode(code: String) {
        _passwordResetState.value = _passwordResetState.value.copy(verificationCode = code)
    }

    fun updateResetPassword(password: String) {
        _passwordResetState.value = _passwordResetState.value.copy(newPassword = password)
    }

    fun clearPasswordResetState() {
        _passwordResetState.value = PasswordResetState()
    }

    fun updateLoginEmail(email: String) {
        _loginUiState.value = _loginUiState.value.copy(
            email = email,
            emailError = null,
            loginError = null
        )
    }

    fun updateLoginPassword(password: String) {
        _loginUiState.value = _loginUiState.value.copy(
            password = password,
            passwordError = null,
            loginError = null
        )
    }

    fun updateRegisterAccount(account: String) {
        _registerUiState.value = _registerUiState.value.copy(
            account = account,
            accountError = null,
            registerError = null
        )
    }

    fun updateRegisterPassword(password: String) {
        _registerUiState.value = _registerUiState.value.copy(
            password = password,
            passwordError = null,
            registerError = null
        )
    }

    fun updateRegisterEmail(email: String) {
        _registerUiState.value = _registerUiState.value.copy(
            email = email,
            emailError = null,
            registerError = null
        )
    }

    fun updateRegisterNickname(nickname: String) {
        _registerUiState.value = _registerUiState.value.copy(
            nickname = nickname,
            registerError = null
        )
    }

    fun clearLoginError() {
        _loginUiState.value = _loginUiState.value.copy(loginError = null)
    }

    fun clearRegisterSuccess() {
        _registerUiState.value = _registerUiState.value.copy(registerSuccess = false)
    }

    data class LoginUiState(
        val email: String = "",
        val password: String = "",
        val emailError: String? = null,
        val passwordError: String? = null,
        val loginError: String? = null,
        val isLoading: Boolean = false,
        val loginSuccess: Boolean = false,
        val loggedInUser: SupabaseAuthRepository.UserData? = null
    )

    data class RegisterUiState(
        val account: String = "",
        val password: String = "",
        val email: String = "",
        val nickname: String = "",
        val accountError: String? = null,
        val passwordError: String? = null,
        val emailError: String? = null,
        val registerError: String? = null,
        val isLoading: Boolean = false,
        val registerSuccess: Boolean = false
    )

    data class PasswordResetState(
        val email: String = "",
        val emailError: String? = null,
        val error: String? = null,
        val isLoading: Boolean = false,
        val codeSent: Boolean = false,
        val generatedCode: String = "",
        val verificationCode: String = "",
        val newPassword: String = "",
        val passwordError: String? = null,
        val resetSuccess: Boolean = false
    )
}
