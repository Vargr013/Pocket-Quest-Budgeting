package com.example.pocketquestbudgeting.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN categoriesInitialized INTEGER NOT NULL DEFAULT 0")
        }
    }
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN password TEXT NOT NULL DEFAULT ''")
        }
    }
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE categories ADD COLUMN minMonthlyBudget INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN maxMonthlyBudget INTEGER NOT NULL DEFAULT 0")
        }
    }
    val FROM_4_TO_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I rebuilt only users and kept their IDs so financial rows stayed attached (Google, 2026u).
            // I scrubbed deleted table cells; this does not erase old backups or journals (SQLite, 2026a).
            db.query("PRAGMA secure_delete = ON").use { result ->
                check(result.moveToFirst() && result.getInt(0) == 1)
            }
            var lastUserId = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'users'").use { rows ->
                if (rows.moveToFirst()) rows.getLong(0) else 0L
            }
            db.execSQL("""
                CREATE TABLE users_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    username TEXT NOT NULL, categoriesInitialized INTEGER NOT NULL DEFAULT 0,
                    firebaseUid TEXT, legacyPasswordSalt TEXT, legacyPasswordHash TEXT,
                    importPending INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.query("SELECT id, username, categoriesInitialized, password FROM users").use { rows ->
                while (rows.moveToNext()) {
                    lastUserId = maxOf(lastUserId, rows.getLong(0))
                    val password = rows.getString(3)
                    val salt = if (password.isNotEmpty()) LegacyPassword.newSalt() else null
                    val hash = salt?.let { LegacyPassword.hash(password, it) }
                    db.execSQL(
                        "INSERT INTO users_new (id, username, categoriesInitialized, legacyPasswordSalt, legacyPasswordHash) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any?>(rows.getLong(0), rows.getString(1), rows.getLong(2), salt, hash),
                    )
                }
            }
            db.execSQL("DROP TABLE users")
            db.execSQL("ALTER TABLE users_new RENAME TO users")
            // I kept the old ID high-water mark so deleted owners could not be assigned again (SQLite, 2024).
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'users'")
            db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('users', ?)", arrayOf(lastUserId))
            db.execSQL("CREATE UNIQUE INDEX index_users_firebaseUid ON users(firebaseUid)")
        }
    }
    val ALL = arrayOf(FROM_1_TO_2, FROM_2_TO_3, FROM_3_TO_4, FROM_4_TO_5)
}
