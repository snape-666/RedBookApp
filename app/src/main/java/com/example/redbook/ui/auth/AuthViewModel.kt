package com.example.redbook.ui.auth

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redbook.data.local.AppDatabase
import com.example.redbook.data.local.UserEntity
import com.example.redbook.data.repository.AuthRepository
import com.example.redbook.ui.utils.Validator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Random

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(AppDatabase.getInstance(application))

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _registerUiState = MutableStateFlow(RegisterUiState())
    val registerUiState: StateFlow<RegisterUiState> = _registerUiState.asStateFlow()

    private val _verificationCodeState = MutableStateFlow(VerificationCodeState())
    val verificationCodeState: StateFlow<VerificationCodeState> = _verificationCodeState.asStateFlow()

    private var generatedCode: String? = null
    private var codeTimestamp: Long = 0

    fun login() {
        val currentState = _loginUiState.value
        val accountError = Validator.getAccountError(currentState.account)
        if (accountError != null) {
            _loginUiState.value = currentState.copy(
                accountError = accountError as String?,
                passwordError = null,
                loginError = null
            )
            return
        }

        val passwordError = Validator.getPasswordError(currentState.password)
        if (passwordError != null) {
            _loginUiState.value = currentState.copy(
                passwordError = passwordError as String?,
                accountError = null,
                loginError = null
            )
            return
        }

        viewModelScope.launch {
            _loginUiState.value = currentState.copy(isLoading = true)
            val result = repository.login(currentState.account, currentState.password)
            result.onSuccess { user ->
                _loginUiState.value = currentState.copy(
                    isLoading = false,
                    loginSuccess = true,
                    loggedInUser = user,
                    accountError = null,
                    passwordError = null,
                    loginError = null
                )
            }.onFailure { error ->
                _loginUiState.value = currentState.copy(
                    isLoading = false,
                    loginSuccess = false,
                    loginError = error.message ?: "登录失败，请重试"
                )
            }
        }
    }

    fun register() {
        val currentState = _registerUiState.value

        val accountError = Validator.getAccountError(currentState.account)
        if (accountError != null) {
            _registerUiState.value = currentState.copy(
                accountError = accountError as String?,
                passwordError = null,
                emailError = null,
                codeError = null,
                registerError = null
            )
            return
        }

        val passwordError = Validator.getPasswordError(currentState.password)
        if (passwordError != null) {
            _registerUiState.value = currentState.copy(
                passwordError = passwordError as String?,
                accountError = null,
                emailError = null,
                codeError = null,
                registerError = null
            )
            return
        }

        val emailError = Validator.getEmailError(currentState.email)
        if (emailError != null) {
            _registerUiState.value = currentState.copy(
                emailError = emailError as String?,
                accountError = null,
                passwordError = null,
                codeError = null,
                registerError = null
            )
            return
        }

        val codeError = Validator.getVerificationCodeError(currentState.verificationCode)
        if (codeError != null) {
            _registerUiState.value = currentState.copy(
                codeError = codeError as String?,
                accountError = null,
                passwordError = null,
                emailError = null,
                registerError = null
            )
            return
        }

        if (!isCodeValid(currentState.verificationCode)) {
            _registerUiState.value = currentState.copy(
                codeError = "验证码错误或已过期，请重新获取",
                registerError = null
            )
            return
        }

        viewModelScope.launch {
            _registerUiState.value = currentState.copy(isLoading = true)
            val result = repository.register(
                account = currentState.account,
                password = currentState.password,
                nickname = currentState.nickname.takeIf { it.isNotBlank() },
                email = currentState.email
            )
            result.onSuccess { userId ->
                _registerUiState.value = currentState.copy(
                    isLoading = false,
                    registerSuccess = true,
                    registerError = null
                )
                clearVerificationCode()
            }.onFailure { error ->
                _registerUiState.value = currentState.copy(
                    isLoading = false,
                    registerSuccess = false,
                    registerError = error.message ?: "注册失败，请重试"
                )
            }
        }
    }

    @SuppressLint("DefaultLocale")
    fun sendVerificationCode() {
        val currentState = _verificationCodeState.value
        if (currentState.isSending) return

        val email = _registerUiState.value.email
        if (!Validator.isValidEmail(email)) {
            _registerUiState.value = _registerUiState.value.copy(
                emailError = Validator.getEmailError(email)
            )
            return
        }

        viewModelScope.launch {
            _verificationCodeState.value = currentState.copy(isSending = true)
            generatedCode = String.format("%06d", Random().nextInt(1000000))
            codeTimestamp = System.currentTimeMillis()

            var remainingSeconds = 60
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                _verificationCodeState.value = _verificationCodeState.value.copy(
                    remainingSeconds = remainingSeconds,
                    isSending = remainingSeconds > 0
                )
            }
            _verificationCodeState.value = _verificationCodeState.value.copy(isSending = false)
        }
    }

    private fun isCodeValid(inputCode: String): Boolean {
        return generatedCode == inputCode &&
                System.currentTimeMillis() - codeTimestamp < 60 * 1000
    }

    private fun clearVerificationCode() {
        generatedCode = null
        codeTimestamp = 0
        _verificationCodeState.value = VerificationCodeState()
    }

    fun updateLoginAccount(account: String) {
        _loginUiState.value = _loginUiState.value.copy(
            account = account,
            accountError = null,
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

    fun updateRegisterVerificationCode(code: String) {
        _registerUiState.value = _registerUiState.value.copy(
            verificationCode = code,
            codeError = null,
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
        val account: String = "",
        val password: String = "",
        val accountError: String? = null,
        val passwordError: String? = null,
        val loginError: String? = null,
        val isLoading: Boolean = false,
        val loginSuccess: Boolean = false,
        val loggedInUser: UserEntity? = null
    )

    data class RegisterUiState(
        val account: String = "",
        val password: String = "",
        val email: String = "",
        val nickname: String = "",
        val verificationCode: String = "",
        val accountError: String? = null,
        val passwordError: String? = null,
        val emailError: String? = null,
        val codeError: String? = null,
        val registerError: String? = null,
        val isLoading: Boolean = false,
        val registerSuccess: Boolean = false
    )

    data class VerificationCodeState(
        val remainingSeconds: Int = 0,
        val isSending: Boolean = false
    )
}
