package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "budget_goals", indices = [Index("userId")])
data class BudgetGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    // I kept both limits in cents, matching expenses.
    val minimumAmount: Long,
    val maximumAmount: Long,
    // I used YYYY-MM-DD for both ends of the goal's date range.
    val startDate: String,
    val endDate: String,
)
