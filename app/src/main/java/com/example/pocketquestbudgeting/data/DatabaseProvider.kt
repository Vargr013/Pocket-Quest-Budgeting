package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
            ).addMigrations(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // I added the setup flag without clearing existing data.
                        db.execSQL("ALTER TABLE users ADD COLUMN categoriesInitialized INTEGER NOT NULL DEFAULT 0")
                    }
                },
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE users ADD COLUMN password TEXT NOT NULL DEFAULT ''")
                    }
                },
                object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // I added the monthly limits without clearing existing categories.
                        db.execSQL("ALTER TABLE categories ADD COLUMN minMonthlyBudget INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE categories ADD COLUMN maxMonthlyBudget INTEGER NOT NULL DEFAULT 0")
                    }
                },
            ).build().also { instance = it }
        }
    }
}
