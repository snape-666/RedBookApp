package com.example.redbook.ui.utils



object Validator {


    private val ACCOUNT_REGEX = Regex("^[a-zA-Z0-9_]{2,8}$")


    private val PASSWORD_REGEX = Regex("^(?=.*[A-Za-z])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,18}$")


    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun isValidAccount(account: String): Boolean {
        return ACCOUNT_REGEX.matches(account)
    }

    fun isValidPassword(password: String): Boolean {
        return PASSWORD_REGEX.matches(password)
    }

    fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }

    fun getAccountError(account: String): String? {
        return when {
            account.isBlank() -> "请输入账号"
            !isValidAccount(account) -> "账号格式：2~8位字母、数字或下划线"
            else -> null
        }
    }

    fun getPasswordError(password: String): String? {
        return when {
            password.isBlank() -> "请输入密码"
            password.length < 8 -> "密码至少8位"
            password.length > 18 -> "密码最多18位"
            !password.matches(Regex(".*[A-Za-z].*")) -> "密码必须包含字母"
            !password.matches(Regex(".*[!@#$%^&*].*")) -> "密码必须包含特殊字符"
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

    fun getVerificationCodeError(code: String): String? {
        return when {
            code.isBlank() -> "请输入验证码"
            code.length != 6 -> "验证码为6位数字"
            else -> null
        }
    }
}