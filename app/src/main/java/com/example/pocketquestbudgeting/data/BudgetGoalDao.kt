package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface BudgetGoalDao {
    @Insert suspend fun insert(goal: BudgetGoalEntity): Long
    @Update suspend fun updateSynced(goal: BudgetGoalEntity): Int

    @Query("SELECT * FROM budget_goals WHERE userId = :userId ORDER BY startDate DESC, id DESC")
    suspend fun getForUser(userId: Long): List<BudgetGoalEntity>

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND startDate = :startDate AND endDate = :endDate ORDER BY id DESC LIMIT 1")
    suspend fun getIncludingDeletedForMonth(userId: Long, startDate: String, endDate: String): BudgetGoalEntity?

    // I selected the newest row before hiding a tombstone so older duplicates could not reappear.
    @Query("SELECT * FROM budget_goals WHERE id = (SELECT id FROM budget_goals WHERE userId = :userId AND startDate = :startDate AND endDate = :endDate ORDER BY id DESC LIMIT 1) AND isDeleted = 0")
    suspend fun getForMonth(userId: Long, startDate: String, endDate: String): BudgetGoalEntity?

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND monthKey = :month")
    suspend fun getByMonthKey(userId: Long, month: String): BudgetGoalEntity?
}
