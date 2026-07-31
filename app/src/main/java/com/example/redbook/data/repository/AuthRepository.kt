package com.example.redbook.data.repository

import com.example.redbook.data.local.AppDatabase
import com.example.redbook.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random

class AuthRepository(private val database: AppDatabase) {

    suspend fun register(
        account: String,
        password: String,
        nickname: String?,
        email: String
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val exists = database.userDao().accountExists(account)
                if (exists > 0) {
                    return@withContext Result.failure(Exception("账号已存在"))
                }
                val xhsId = generateXhsId()
                val user = UserEntity(
                    account = account,
                    password = password,
                    nickname = nickname,
                    xhsId = xhsId,
                    email = email
                )
                val userId = database.userDao().insertUser(user)
                Result.success(userId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun login(account: String, password: String): Result<UserEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val user = database.userDao().login(account, password)
                if (user != null) {
                    Result.success(user)
                } else {
                    val exists = database.userDao().accountExists(account)
                    if (exists == 0) {
                        Result.failure(Exception("账号不存在，请先注册"))
                    } else {
                        Result.failure(Exception("密码错误"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun generateXhsId(): String {
        val chars = "0123456789"
        return (1..8)
            .map { chars[Random().nextInt(chars.length)] }
            .joinToString("")
    }
}
