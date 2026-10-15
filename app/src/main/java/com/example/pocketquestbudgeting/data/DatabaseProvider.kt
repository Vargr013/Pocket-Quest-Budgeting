package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        // I shared one database instance across the screens (Google, 2026g).
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pocket-quest.db",
            ).addMigrations(*DatabaseMigrations.ALL).build().also { instance = it }
        }
    }
}
