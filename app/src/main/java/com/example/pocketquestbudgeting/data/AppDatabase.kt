package com.example.pocketquestbudgeting.data

import androidx.room.Database
import androidx.room.RoomDatabase

// I registered the entities and DAOs through Room (Google, 2026g).
@Database(
    entities = [UserEntity::class, CategoryEntity::class, ExpenseEntity::class, BudgetGoalEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetGoalDao(): BudgetGoalDao
}
