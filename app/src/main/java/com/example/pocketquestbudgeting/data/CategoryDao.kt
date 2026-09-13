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

    @Query("UPDATE categories SET name = :name WHERE id = :categoryId AND userId = :userId")
    suspend fun updateName(userId: Long, categoryId: Long, name: String): Int

    @Transaction
    suspend fun rename(userId: Long, categoryId: Long, name: String): CategoryChangeResult {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Enter a category name." }
        val categories = getForUser(userId)
        if (categories.none { it.id == categoryId }) return CategoryChangeResult.NOT_FOUND
        // I excluded this category so keeping its name or changing capitals is allowed.
        if (categories.any { it.id != categoryId && it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return CategoryChangeResult.DUPLICATE
        }
        // I only changed the name; expenses still point to the same category ID.
        return if (updateName(userId, categoryId, trimmed) == 1) CategoryChangeResult.SUCCESS
        else CategoryChangeResult.NOT_FOUND
    }

    // I put the expense check in the DELETE itself so other callers cannot skip it.
    // Any reference blocks removal, but only the active user's category can be deleted.
    @Query("""
        DELETE FROM categories WHERE id = :categoryId AND userId = :userId
        AND NOT EXISTS (SELECT 1 FROM expenses WHERE categoryId = :categoryId)
    """)
    suspend fun deleteUnused(userId: Long, categoryId: Long): Int

    @Transaction
    suspend fun delete(userId: Long, categoryId: Long): CategoryChangeResult {
        if (getForUser(userId).none { it.id == categoryId }) return CategoryChangeResult.NOT_FOUND
        return if (deleteUnused(userId, categoryId) == 1) CategoryChangeResult.SUCCESS
        else CategoryChangeResult.IN_USE
    }

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

enum class CategoryChangeResult { SUCCESS, DUPLICATE, NOT_FOUND, IN_USE }
