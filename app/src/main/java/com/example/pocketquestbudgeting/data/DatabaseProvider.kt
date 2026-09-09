package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, // where the file lives
                AppDatabase::class.java,    // which blueprint to use (YOUR class)
                "pocket-quest.db",      // filename on the phone
            ).build().also { instance = it }
        }
    }
}