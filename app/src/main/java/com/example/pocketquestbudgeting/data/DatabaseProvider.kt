package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        // I shared one database instance across the screens.
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pocket-quest.db",
            ).addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // I added the setup flag without clearing existing data.
                    db.execSQL("ALTER TABLE users ADD COLUMN categoriesInitialized INTEGER NOT NULL DEFAULT 0")
                }
            }).build().also { instance = it }
        }
    }
}
