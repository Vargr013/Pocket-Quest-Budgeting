package com.example.pocketquestbudgeting.data

import androidx.room.withTransaction

// I kept the demo user lookup here until login is connected.
suspend fun AppDatabase.activeUserId(): Long = withTransaction {
    val user = userDao().getByUsername("demo")
    val userId = user?.id ?: userDao().insert(UserEntity(username = "demo"))
    if (user?.categoriesInitialized != true) {
        // I saved the defaults and flag together to avoid half-finished setup.
        // I used the flag to avoid restoring deleted defaults.
        for (name in listOf("Groceries", "Transport", "Entertainment", "Rent")) {
            categoryDao().getOrCreate(userId, name)
        }
        userDao().markCategoriesInitialized(userId)
    }
    userId
}
