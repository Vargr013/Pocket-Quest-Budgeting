package com.example.pocketquestbudgeting.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, CategoryEntity::class, ExpenseEntity::class, BudgetGoalEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetGoalDao(): BudgetGoalDao
}
