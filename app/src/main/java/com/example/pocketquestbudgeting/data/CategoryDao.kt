package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // I kept dates in the join so categories without spending still appear (SQLite, 2026).
    // I used SUM to total the stored cents for each category (SQLite, 2025).
    @Query("""
        SELECT c.id AS categoryId, c.name AS categoryName,
            COALESCE(SUM(e.amount), 0) AS totalCents
        FROM categories AS c
        LEFT JOIN expenses AS e ON e.categoryId = c.id AND e.userId = :userId
            AND (:startDate IS NULL OR e.date >= :startDate)
            AND (:endDate IS NULL OR e.date <= :endDate)
        WHERE c.userId = :userId
        GROUP BY c.id, c.name
        ORDER BY c.name COLLATE NOCASE, c.id
    """)
    suspend fun getSpendingForUser(
        userId: Long,
        startDate: String?,
        endDate: String?,
    ): List<CategorySpendingTotal>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    // I limited the list to this user and ignored capitals when sorting.
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    suspend fun getForUser(userId: Long): List<CategoryEntity>

    // I observed the saved list so category changes refresh the picker (Google, 2026k).
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    fun observeForUser(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND userId = :userId")
    suspend fun getForUserById(userId: Long, categoryId: Long): CategoryEntity?

    // I kept categories with no expenses using LEFT JOIN (SQLite, 2026).
    // I totalled the month with SUM and treated a missing sum as zero (SQLite, 2025).
    @Query("""
        SELECT
            c.id AS categoryId,
            c.name AS name,
            c.minMonthlyBudget AS minMonthlyBudget,
            c.maxMonthlyBudget AS maxMonthlyBudget,
            COALESCE(SUM(e.amount), 0) AS spent
        FROM categories AS c
        LEFT JOIN expenses AS e
            ON e.categoryId = c.id
           AND e.userId = c.userId
           AND e.date >= :monthStart
           AND e.date < :monthEnd
        WHERE c.userId = :userId
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE, c.id
    """)
    suspend fun getMonthProgress(userId: Long, monthStart: String, monthEnd: String): List<CategoryMonthProgress>

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

    @Query("""
        UPDATE categories
        SET minMonthlyBudget = :minMonthlyBudget, maxMonthlyBudget = :maxMonthlyBudget
        WHERE id = :categoryId AND userId = :userId
    """)
    suspend fun updateBudgets(
        userId: Long,
        categoryId: Long,
        minMonthlyBudget: Long,
        maxMonthlyBudget: Long,
    ): Int

    @Transaction
    suspend fun setBudgets(
        userId: Long,
        categoryId: Long,
        minMonthlyBudget: Long,
        maxMonthlyBudget: Long,
    ): CategoryChangeResult {
        requireValidBudgets(minMonthlyBudget, maxMonthlyBudget)
        if (getForUserById(userId, categoryId) == null) return CategoryChangeResult.NOT_FOUND
        return if (updateBudgets(userId, categoryId, minMonthlyBudget, maxMonthlyBudget) == 1) {
            CategoryChangeResult.SUCCESS
        } else {
            CategoryChangeResult.NOT_FOUND
        }
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

    // I kept the check and save in one transaction to prevent overlapping saves (Google, 2026j).
    @Transaction
    suspend fun create(
        userId: Long,
        name: String,
        minMonthlyBudget: Long = 0,
        maxMonthlyBudget: Long = 0,
    ): Long? {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Enter a category name." }
        requireValidBudgets(minMonthlyBudget, maxMonthlyBudget)
        // I used Kotlin for comparison because SQLite's NOCASE only covers ASCII letters.
        if (getForUser(userId).any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return null
        }
        return insert(
            CategoryEntity(
                userId = userId,
                name = trimmed,
                minMonthlyBudget = minMonthlyBudget,
                maxMonthlyBudget = maxMonthlyBudget,
            ),
        )
    }

    // I reused existing categories when setting up each account's defaults.
    @Transaction
    suspend fun getOrCreate(userId: Long, name: String): Long {
        return create(userId, name) ?: getForUser(userId).first {
            it.name.trim().equals(name.trim(), ignoreCase = true)
        }.id
    }
}

enum class CategoryChangeResult { SUCCESS, DUPLICATE, NOT_FOUND, IN_USE }

private fun requireValidBudgets(minMonthlyBudget: Long, maxMonthlyBudget: Long) {
    require(minMonthlyBudget >= 0 && maxMonthlyBudget >= 0) { "Enter amounts that are zero or more." }
    require(minMonthlyBudget <= maxMonthlyBudget) { "Minimum cannot be greater than the maximum." }
}
