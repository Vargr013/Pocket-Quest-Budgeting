package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // I kept dates in the join so categories without spending still appear (SQLite, 2026b).
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
    @Query("SELECT * FROM categories WHERE userId = :userId AND isArchived = 0 ORDER BY name COLLATE NOCASE, id")
    suspend fun getForUser(userId: Long): List<CategoryEntity>

    // I observed the saved list so category changes refresh the picker (Google, 2026k).
    @Query("SELECT * FROM categories WHERE userId = :userId AND isArchived = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeForUser(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND userId = :userId")
    suspend fun getForUserById(userId: Long, categoryId: Long): CategoryEntity?

    // I kept categories with no expenses using LEFT JOIN (SQLite, 2026b).
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
        WHERE c.userId = :userId AND c.isArchived = 0
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE, c.id
    """)
    suspend fun getMonthProgress(userId: Long, monthStart: String, monthEnd: String): List<CategoryMonthProgress>

    @Query("SELECT * FROM categories WHERE userId = :userId AND cloudId = :cloudId")
    suspend fun getByCloudId(userId: Long, cloudId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    suspend fun getAllForUser(userId: Long): List<CategoryEntity>

    // I kept this write behind CategorySync so row edits and queue entries share a transaction.
    suspend fun updateSynced(category: CategoryEntity): Int = updateForSync(
        category.userId, category.id, category.name, category.minMonthlyBudget,
        category.maxMonthlyBudget, category.cloudId, category.isArchived,
    )

    @Query("""
        UPDATE categories SET name = :name, minMonthlyBudget = :minimum, maxMonthlyBudget = :maximum,
            cloudId = :cloudId, isArchived = :archived
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateForSync(userId: Long, id: Long, name: String, minimum: Long,
        maximum: Long, cloudId: String?, archived: Boolean): Int
}

enum class CategoryChangeResult { SUCCESS, DUPLICATE, NOT_FOUND, IN_USE }

internal fun requireValidBudgets(minMonthlyBudget: Long, maxMonthlyBudget: Long) {
    require(minMonthlyBudget >= 0 && maxMonthlyBudget >= 0) { "Enter amounts that are zero or more." }
    require(minMonthlyBudget <= maxMonthlyBudget) { "Minimum cannot be greater than the maximum." }
}
