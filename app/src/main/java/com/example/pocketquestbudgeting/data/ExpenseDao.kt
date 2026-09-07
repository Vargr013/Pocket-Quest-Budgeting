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
}
