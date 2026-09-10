package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    // I limited the list to this user and ignored capitals when sorting.
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    suspend fun getForUser(userId: Long): List<CategoryEntity>

    // I kept the check and save together to prevent duplicates from overlapping saves.
    @Transaction
    suspend fun create(userId: Long, name: String): Long? {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Enter a category name." }
        // I used Kotlin for comparison because SQLite's NOCASE only covers ASCII letters.
        if (getForUser(userId).any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return null
        }
        return insert(CategoryEntity(userId = userId, name = trimmed))
    }

    // I reused the category ID for existing names from the expense form.
    @Transaction
    suspend fun getOrCreate(userId: Long, name: String): Long {
        return create(userId, name) ?: getForUser(userId).first {
            it.name.trim().equals(name.trim(), ignoreCase = true)
        }.id
    }
}
