package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    @Query("UPDATE users SET categoriesInitialized = 1 WHERE id = :userId")
    suspend fun markCategoriesInitialized(userId: Long)

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): UserEntity?
}
