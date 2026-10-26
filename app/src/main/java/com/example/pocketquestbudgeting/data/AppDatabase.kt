package com.example.pocketquestbudgeting.data

import androidx.room.Database
import androidx.room.RoomDatabase

// I registered the entities and DAOs through Room (Google, 2026g).
@Database(
    entities = [UserEntity::class, CategoryEntity::class, ExpenseEntity::class, BudgetGoalEntity::class, SyncRecord::class, SyncOperation::class,
        ReceiptJob::class, ReceiptCache::class, SavingsGoalEntity::class, SavingsContributionEntity::class,
        EarnedAchievementEntity::class, ActivityDayEntity::class, CustomWorthItemEntity::class],
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetGoalDao(): BudgetGoalDao
    abstract fun syncDao(): SyncDao
    abstract fun receiptJobDao(): ReceiptJobDao
    abstract fun savingsDao(): SavingsDao
    abstract fun earnedAchievementDao(): EarnedAchievementDao
    abstract fun gamificationDao(): GamificationDao
}
