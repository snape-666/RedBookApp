package com.example.redbook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val userId: Long = 0,
    val account: String,           // 登录账号（唯一）
    val password: String,          // 密码
    val nickname: String? = null,  // 用户设置的名字（可为空）
    val xhsId: String,             // 8位小红书ID（不可变）
    val email: String,             // 邮箱
    val createdAt: Long = System.currentTimeMillis()
)