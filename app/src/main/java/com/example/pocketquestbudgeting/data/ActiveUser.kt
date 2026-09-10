package com.example.pocketquestbudgeting.data

import androidx.room.withTransaction

// Login is still a demo. Replace this lookup with the signed-in user's ID later.
suspend fun AppDatabase.activeUserId(): Long = withTransaction {
    val user = userDao().getByUsername("demo")
    val userId = user?.id ?: userDao().insert(UserEntity(username = "demo"))
    if (user?.categoriesInitialized != true) {
        // The flag survives empty lists and app restarts. Keep it in the same transaction
        // as the defaults so an interrupted setup can safely retry.
        for (name in listOf("Groceries", "Transport", "Entertainment", "Rent")) {
            categoryDao().getOrCreate(userId, name)
        }
        userDao().markCategoriesInitialized(userId)
    }
    userId
}
