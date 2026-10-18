package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

// I used Room DAO queries with bound parameters for expense reads and changes (Google, 2026b).
@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE userId = :userId AND isDeleted = 0 ORDER BY date DESC, startTime DESC, id DESC")
    suspend fun getForUser(userId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND isDeleted = 0 AND date >= :startDate AND date <= :endDate ORDER BY date DESC, startTime DESC, id DESC")
    suspend fun getForUserInRange(userId: Long, startDate: String, endDate: String): List<ExpenseEntity>

    // I used instr so punctuation and spaces stay literal; lower handles ASCII capitals.
    @Query("""
        SELECT * FROM expenses
        WHERE userId = :userId AND isDeleted = 0
        AND (:startDate IS NULL OR date >= :startDate)
        AND (:endDate IS NULL OR date <= :endDate)
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:search = '' OR instr(lower(description), lower(:search)) > 0)
        ORDER BY date DESC, startTime DESC, id DESC
    """)
    suspend fun getForUserFiltered(
        userId: Long,
        startDate: String?,
        endDate: String?,
        categoryId: Long?,
        search: String,
    ): List<ExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE userId = :userId AND isDeleted = 0)")
    suspend fun hasForUser(userId: Long): Boolean

    @Query("SELECT * FROM expenses WHERE id = :expenseId AND userId = :userId AND isDeleted = 0")
    suspend fun getForUserById(userId: Long, expenseId: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY id")
    suspend fun getAllForUser(userId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND id = :id")
    suspend fun getIncludingDeleted(userId: Long, id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE userId = :userId AND cloudId = :cloudId")
    suspend fun getByCloudId(userId: Long, cloudId: String): ExpenseEntity?

    suspend fun updateSynced(expense: ExpenseEntity): Int = updateForSync(
        expense.userId, expense.id, expense.categoryId, expense.amount, expense.date,
        expense.startTime, expense.endTime, expense.description, expense.receiptImageUri,
        expense.cloudId, expense.isDeleted, expense.receiptObjectPath,
    )

    @Query("""
        UPDATE expenses SET categoryId = :categoryId, amount = :amount, date = :date,
            startTime = :startTime, endTime = :endTime, description = :description,
            receiptImageUri = :receiptImageUri, cloudId = :cloudId,
            isDeleted = :deleted, receiptObjectPath = :receiptObjectPath
        WHERE id = :id AND userId = :userId
    """)
    suspend fun updateForSync(userId: Long, id: Long, categoryId: Long, amount: Long,
        date: String, startTime: String, endTime: String, description: String,
        receiptImageUri: String?, cloudId: String?, deleted: Boolean, receiptObjectPath: String?): Int
}
