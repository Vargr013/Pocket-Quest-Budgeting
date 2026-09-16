package com.example.pocketquestbudgeting.data

import androidx.room.withTransaction

suspend fun AppDatabase.initializeUserCategories(userId: Long) = withTransaction {
    val user = requireNotNull(userDao().getById(userId)) { "Account unavailable. Please log in again." }
    if (!user.categoriesInitialized) {
        // I saved this account's defaults and flag together so deleted defaults stay deleted.
        for (name in listOf("Groceries", "Transport", "Entertainment", "Rent")) {
            categoryDao().getOrCreate(userId, name)
        }
        userDao().markCategoriesInitialized(userId)
    }
}
