package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC, startTime DESC, id DESC")
    suspend fun getForUser(userId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId AND userId = :userId")
    suspend fun getForUserById(userId: Long, expenseId: Long): ExpenseEntity?

    // I checked both IDs so this cannot update another user's expense.
    @Query("""
        UPDATE expenses SET categoryId = :categoryId, amount = :amount, date = :date,
            startTime = :startTime, endTime = :endTime, description = :description,
            receiptImageUri = :receiptImageUri
        WHERE id = :expenseId AND userId = :userId
    """)
    suspend fun updateForUser(
        userId: Long,
        expenseId: Long,
        categoryId: Long,
        amount: Long,
        date: String,
        startTime: String,
        endTime: String,
        description: String,
        receiptImageUri: String?,
    ): Int
}
