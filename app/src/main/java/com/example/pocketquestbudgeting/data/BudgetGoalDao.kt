package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BudgetGoalDao {
    @Insert
    suspend fun insert(goal: BudgetGoalEntity): Long

    @Query("SELECT * FROM budget_goals WHERE userId = :userId ORDER BY startDate DESC, id DESC")
    suspend fun getForUser(userId: Long): List<BudgetGoalEntity>

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND startDate = :startDate AND endDate = :endDate ORDER BY id DESC LIMIT 1")
    suspend fun getForMonth(userId: Long, startDate: String, endDate: String): BudgetGoalEntity?

    @Query("""
        UPDATE budget_goals SET minimumAmount = :minimumAmount, maximumAmount = :maximumAmount
        WHERE id = :goalId AND userId = :userId AND startDate = :startDate AND endDate = :endDate
    """)
    suspend fun updateForMonth(
        userId: Long, goalId: Long, startDate: String, endDate: String,
        minimumAmount: Long, maximumAmount: Long,
    ): Int

    @Transaction
    suspend fun saveForMonth(
        userId: Long, startDate: String, endDate: String,
        minimumAmount: Long, maximumAmount: Long,
    ) {
        require(minimumAmount >= 0 && maximumAmount >= minimumAmount)
        // I kept the lookup and save in one transaction so repeated saves update the same month (Google, 2026j).
        val existing = getForMonth(userId, startDate, endDate)
        if (existing == null) {
            insert(BudgetGoalEntity(
                userId = userId, minimumAmount = minimumAmount, maximumAmount = maximumAmount,
                startDate = startDate, endDate = endDate,
            ))
        } else {
            check(updateForMonth(userId, existing.id, startDate, endDate, minimumAmount, maximumAmount) == 1)
        }
    }
}
