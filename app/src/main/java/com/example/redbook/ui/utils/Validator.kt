package com.example.redbook.ui.utils

object Validator {

    private val ACCOUNT_REGEX = Regex("^[a-zA-Z0-9]{8,18}$")

    private val PASSWORD_REGEX = Regex("^(?=.*[A-Za-z])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,18}$")

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun isValidAccount(account: String): Boolean = ACCOUNT_REGEX.matches(account)

    fun isValidPassword(password: String): Boolean = PASSWORD_REGEX.matches(password)

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

    fun getAccountError(account: String): String? {
        return when {
            account.isBlank() -> "请输入账号"
            account.length < 8 -> "账号至少8位"
            account.length > 18 -> "账号最多18位"
            account.any { !it.isLetterOrDigit() } -> "账号只能包含英文字母和数字"
            !isValidAccount(account) -> "账号格式：8~18位英文字母和数字"
            else -> null
        }
    }

    fun getPasswordError(password: String): String? {
        return when {
            password.isBlank() -> "请输入密码"
            password.length < 8 -> "密码至少8位"
            password.length > 18 -> "密码最多18位"
            !password.any { it.isLetter() } -> "密码必须包含字母"
            !password.any { it in "!@#\$%^&*" } -> "密码必须包含特殊字符"
            else -> null
        }
    }

    fun getEmailError(email: String): String? {
        return when {
            email.isBlank() -> "请输入邮箱"
            !isValidEmail(email) -> "请输入正确的邮箱格式"
            else -> null
        }
    }
}
