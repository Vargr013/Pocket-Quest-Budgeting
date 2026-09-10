package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, // where the file lives
                AppDatabase::class.java,    // which blueprint to use (YOUR class)
                "pocket-quest.db",      // filename on the phone
            ).addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE users ADD COLUMN categoriesInitialized INTEGER NOT NULL DEFAULT 0")
                }
            }).build().also { instance = it }
        }
    }
}
