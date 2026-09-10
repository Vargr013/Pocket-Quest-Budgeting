package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    suspend fun getForUser(userId: Long): List<CategoryEntity>

    // Check and insert together so two saves cannot create the same name.
    // Kotlin's comparison also handles capitalisation beyond SQLite's ASCII NOCASE.
    @Transaction
    suspend fun create(userId: Long, name: String): Long? {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Enter a category name." }
        if (getForUser(userId).any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return null
        }
        return insert(CategoryEntity(userId = userId, name = trimmed))
    }

    @Transaction
    suspend fun getOrCreate(userId: Long, name: String): Long {
        return create(userId, name) ?: getForUser(userId).first {
            it.name.trim().equals(name.trim(), ignoreCase = true)
        }.id
    }
}
