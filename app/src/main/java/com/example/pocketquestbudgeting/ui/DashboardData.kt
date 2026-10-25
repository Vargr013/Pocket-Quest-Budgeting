package com.example.pocketquestbudgeting.ui

import androidx.room.withTransaction
import com.example.pocketquestbudgeting.data.AppDatabase
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.SavingsGoalEntity

internal data class DashboardSavingGoal(val goal: SavingsGoalEntity, val savedCents: Long)

internal data class DashboardData(
    val recentExpenses: List<ExpenseEntity>,
    val categoryNames: Map<Long, String>,
    val savings: List<DashboardSavingGoal>,
)

internal suspend fun loadDashboardData(db: AppDatabase, userId: Long): DashboardData = db.withTransaction {
    require(userId > 0)
    val savings = db.savingsDao()
    DashboardData(
        recentExpenses = db.expenseDao().getForUser(userId).take(3),
        categoryNames = db.categoryDao().getAllForUser(userId).associate { it.id to it.name },
        savings = savings.getForUser(userId).sortedWith(compareBy({ it.name.lowercase() }, { it.id })).take(3)
            .map { DashboardSavingGoal(it, savings.savedCents(userId, it.id)) },
    )
}
