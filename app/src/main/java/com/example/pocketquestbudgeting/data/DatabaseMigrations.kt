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
    val FROM_5_TO_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I added separate sync tables without rewriting financial rows (Google, 2026u).
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_records (
                uid TEXT NOT NULL, userId INTEGER NOT NULL, recordType TEXT NOT NULL, recordId TEXT NOT NULL,
                revision INTEGER NOT NULL, payload TEXT NOT NULL, isDeleted INTEGER NOT NULL,
                updatedAtMillis INTEGER, deletedAtMillis INTEGER, lastMutationId TEXT,
                PRIMARY KEY(uid, recordType, recordId))""")
            db.execSQL("CREATE INDEX index_sync_records_userId ON sync_records(userId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS sync_queue (
                sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mutationId TEXT NOT NULL,
                uid TEXT NOT NULL, userId INTEGER NOT NULL, recordType TEXT NOT NULL, recordId TEXT NOT NULL,
                payload TEXT NOT NULL, isDeleted INTEGER NOT NULL, baseRevision INTEGER, state TEXT NOT NULL,
                attemptCount INTEGER NOT NULL, lastError TEXT)""")
            db.execSQL("CREATE UNIQUE INDEX index_sync_queue_mutationId ON sync_queue(mutationId)")
            db.execSQL("CREATE INDEX index_sync_queue_uid_recordType_recordId ON sync_queue(uid, recordType, recordId)")
        }
    }
    val FROM_6_TO_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I kept the numeric IDs and expense references during the mapping upgrade (Google, 2026u).
            db.execSQL("ALTER TABLE categories ADD COLUMN cloudId TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE UNIQUE INDEX index_categories_userId_cloudId ON categories(userId, cloudId)")
        }
    }
    val FROM_7_TO_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I kept existing expense IDs and local photos while adding the cloud mapping (Google, 2026u).
            db.execSQL("ALTER TABLE expenses ADD COLUMN cloudId TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expenses ADD COLUMN receiptObjectPath TEXT")
            db.execSQL("CREATE UNIQUE INDEX index_expenses_userId_cloudId ON expenses(userId, cloudId)")
        }
    }
    val FROM_8_TO_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I retained every legacy row; only the effective month row receives a mapping (Google, 2026u).
            db.execSQL("ALTER TABLE budget_goals ADD COLUMN monthKey TEXT")
            db.execSQL("ALTER TABLE budget_goals ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE UNIQUE INDEX index_budget_goals_userId_monthKey ON budget_goals(userId, monthKey)")
        }
    }
    val FROM_9_TO_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I kept old queue snapshots frozen so a retry still used the original bytes (Google, 2026u).
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN receiptMode TEXT NOT NULL DEFAULT 'FROZEN'")
            db.execSQL("""CREATE TABLE IF NOT EXISTS receipt_jobs (
                jobId TEXT NOT NULL PRIMARY KEY, uid TEXT NOT NULL, userId INTEGER NOT NULL,
                expenseCloudId TEXT NOT NULL, sourceLocalPath TEXT, targetObjectPath TEXT NOT NULL,
                expectedAcceptedPath TEXT, remove INTEGER NOT NULL, selected INTEGER NOT NULL,
                phase TEXT NOT NULL, sourceSha256 TEXT, byteCount INTEGER, mimeType TEXT,
                publicationMutationId TEXT, attemptCount INTEGER NOT NULL, lastError TEXT, blocked INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX index_receipt_jobs_uid_expenseCloudId ON receipt_jobs(uid, expenseCloudId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS receipt_cache (
                uid TEXT NOT NULL, userId INTEGER NOT NULL, expenseCloudId TEXT NOT NULL,
                objectPath TEXT NOT NULL, localPath TEXT, state TEXT NOT NULL, lastError TEXT, blocked INTEGER NOT NULL,
                PRIMARY KEY(uid, expenseCloudId, objectPath))""")
        }
    }
    val FROM_10_TO_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I added empty feature tables and retained the existing financial and sync rows (Google, 2026u).
            db.execSQL("""CREATE TABLE savings_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL,
                cloudId TEXT NOT NULL, name TEXT NOT NULL, targetCents INTEGER NOT NULL,
                targetDate TEXT, note TEXT, isDeleted INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX index_savings_goals_userId_cloudId ON savings_goals(userId, cloudId)")
            db.execSQL("CREATE UNIQUE INDEX index_savings_goals_userId_id ON savings_goals(userId, id)")
            db.execSQL("""CREATE TABLE savings_contributions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, goalId INTEGER NOT NULL,
                businessId TEXT NOT NULL, kind TEXT NOT NULL, amountCents INTEGER NOT NULL,
                effectiveDate TEXT NOT NULL, reversesBusinessId TEXT, note TEXT,
                FOREIGN KEY(userId, goalId) REFERENCES savings_goals(userId, id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX index_savings_contributions_userId_businessId ON savings_contributions(userId, businessId)")
            db.execSQL("CREATE INDEX index_savings_contributions_userId_goalId ON savings_contributions(userId, goalId)")
            db.execSQL("CREATE UNIQUE INDEX index_savings_contributions_userId_reversesBusinessId ON savings_contributions(userId, reversesBusinessId)")
            db.execSQL("""CREATE TABLE earned_achievements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL,
                awardKey TEXT NOT NULL, badgeId TEXT NOT NULL, criteriaVersion INTEGER NOT NULL,
                earnedAtEpochMillis INTEGER, earnedDate TEXT, earnedZoneId TEXT, evidenceRecordId TEXT NOT NULL,
                FOREIGN KEY(userId) REFERENCES users(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX index_earned_achievements_userId_awardKey ON earned_achievements(userId, awardKey)")
            db.execSQL("CREATE INDEX index_earned_achievements_userId_badgeId ON earned_achievements(userId, badgeId)")
        }
    }
    val FROM_11_TO_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I added the local comparison and activity tables, keeping the existing award and financial rows.
            db.execSQL("""CREATE TABLE IF NOT EXISTS activity_days (
                userId INTEGER NOT NULL, date TEXT NOT NULL, PRIMARY KEY(userId, date))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS custom_worth_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL,
                name TEXT NOT NULL, valueCents INTEGER NOT NULL, category TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_worth_items_userId ON custom_worth_items(userId)")
        }
    }
    val FROM_10_TO_12 = object : Migration(10, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Kian's standalone v10 used the number already assigned to receipts on main.
            val localBadges = db.query("PRAGMA table_info(earned_achievements)").use { columns ->
                var found = false
                while (columns.moveToNext()) {
                    if (columns.getString(columns.getColumnIndexOrThrow("name")) == "achievementId") found = true
                }
                found
            }
            if (localBadges) {
                db.execSQL("ALTER TABLE earned_achievements RENAME TO gamification_awards_v10")
                FROM_9_TO_10.migrate(db)
            }
            FROM_10_TO_11.migrate(db)
            if (localBadges) {
                // I copied the original badge IDs and first-earned instants into the shared model.
                db.execSQL("""INSERT INTO earned_achievements
                    (userId, awardKey, badgeId, criteriaVersion, earnedAtEpochMillis, evidenceRecordId)
                    SELECT userId, 'gamification:v1:' || achievementId, achievementId, 1,
                        earnedAtMillis, 'gamification:v1:' || achievementId FROM gamification_awards_v10""")
                db.execSQL("DROP TABLE gamification_awards_v10")
            }
            FROM_11_TO_12.migrate(db)
        }
    }
    val ALL = arrayOf(FROM_1_TO_2, FROM_2_TO_3, FROM_3_TO_4, FROM_4_TO_5, FROM_5_TO_6, FROM_6_TO_7,
        FROM_7_TO_8, FROM_8_TO_9, FROM_9_TO_10, FROM_10_TO_11, FROM_11_TO_12, FROM_10_TO_12)
}
