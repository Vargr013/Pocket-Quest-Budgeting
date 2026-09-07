package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BudgetGoalDao {
    @Insert
    suspend fun insert(goal: BudgetGoalEntity): Long

    @Query("SELECT * FROM budget_goals WHERE userId = :userId ORDER BY startDate DESC, id DESC")
    suspend fun getForUser(userId: Long): List<BudgetGoalEntity>
}
