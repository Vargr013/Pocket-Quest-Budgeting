package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

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

    @Query(
        "SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1"
    )
    suspend fun findByCredentials(username: String, password: String): UserEntity?

    // I checked the name and saved the account together so login can find the same row.
    @Transaction
    suspend fun register(username: String, password: String): RegisterResult {
        val name = username.trim()
        require(name.isNotBlank()) { "Enter a username." }
        require(password.isNotBlank()) { "Enter a password." }
        if (getByUsername(name) != null) return RegisterResult.USERNAME_TAKEN
        insert(UserEntity(username = name, password = password))
        return RegisterResult.CREATED
    }
}

enum class RegisterResult { CREATED, USERNAME_TAKEN }
