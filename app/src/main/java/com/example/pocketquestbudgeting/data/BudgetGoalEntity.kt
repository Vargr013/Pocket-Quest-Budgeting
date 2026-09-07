package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "budget_goals", indices = [Index("userId")])
data class BudgetGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    // Both limits are stored in whole cents, matching expense amounts.
    val minimumAmount: Long,
    val maximumAmount: Long,
    // The goal covers this date range, using YYYY-MM-DD.
    val startDate: String,
    val endDate: String,
)
