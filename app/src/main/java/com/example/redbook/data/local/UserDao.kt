package com.example.redbook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE account = :account")
    suspend fun getUserByAccount(account: String): UserEntity?

    @Query("SELECT * FROM users WHERE account = :account AND password = :password")
    suspend fun login(account: String, password: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE account = :account")
    suspend fun accountExists(account: String): Int

    @Query("UPDATE users SET nickname = :nickname WHERE userId = :userId")
    suspend fun updateNickname(userId: Long, nickname: String)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: Long)
}