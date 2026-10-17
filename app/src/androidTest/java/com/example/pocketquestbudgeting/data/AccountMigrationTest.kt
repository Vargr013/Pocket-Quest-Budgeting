package com.example.pocketquestbudgeting.data

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun preservedVersion1() = verifyUpgrade(1)
    @Test fun preservedVersion2() = verifyUpgrade(2)
    @Test fun preservedVersion3() = verifyUpgrade(3)
    @Test fun preservedVersion4() = verifyUpgrade(4)

    private fun verifyUpgrade(version: Int): Unit = runBlocking {
        val name = "slice3-migration-$version"
        context.deleteDatabase(name)
        // I reconstructed v3 from v2 and the migration in commit 6d641e0; no v3 export was present.
        val old = createLegacyDatabase(name, if (version == 3) 2 else version)
        if (version == 3) {
            old.execSQL("ALTER TABLE users ADD COLUMN password TEXT NOT NULL DEFAULT ''")
            old.version = 3
        }
        if (version >= 3) old.execSQL("INSERT INTO users (id, username, password) VALUES (999, 'Deleted synthetic owner', '')")
        else old.execSQL("INSERT INTO users (id, username) VALUES (999, 'Deleted synthetic owner')")
        old.execSQL("DELETE FROM users WHERE id = 999")
        if (version >= 3) {
            old.execSQL("INSERT INTO users (id, username, password, categoriesInitialized) VALUES (41, 'Legacy', 'Synthetic-local-only!', 1)")
            old.execSQL("INSERT INTO users (id, username, password, categoriesInitialized) VALUES (51, 'NoProof', '', 0)")
        } else {
            old.execSQL("INSERT INTO users (id, username) VALUES (41, 'Legacy')")
            old.execSQL("INSERT INTO users (id, username) VALUES (51, 'NoProof')")
            if (version == 2) old.execSQL("UPDATE users SET categoriesInitialized = 1 WHERE id = 41")
        }
        old.execSQL("INSERT INTO categories (id, userId, name) VALUES (61, 41, 'Owned category')")
        old.execSQL("INSERT INTO categories (id, userId, name) VALUES (62, 51, 'Other category')")
        if (version == 4) old.execSQL("UPDATE categories SET minMonthlyBudget = 123, maxMonthlyBudget = 9999999999 WHERE id = 61")
        old.execSQL("""INSERT INTO expenses (id, userId, categoryId, amount, date, startTime, endTime, description, receiptImageUri)
            VALUES (71, 41, 61, 9007199254740993, '2024-02-29', '09:15', '10:45', 'Synthetic expense', '/private/synthetic-receipt.jpg')""")
        old.execSQL("""INSERT INTO budget_goals (id, userId, minimumAmount, maximumAmount, startDate, endDate)
            VALUES (81, 41, 123, 9999999999, '2024-02-01', '2024-02-29')""")
        old.close()

        // I opened the exported fixture through Room's real migration and schema validator (Google, 2026u).
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val migrated = db.openHelper.writableDatabase
            assertEquals(6, migrated.version)
            migrated.query("PRAGMA table_info(users)").use { columns ->
                val names = mutableListOf<String>()
                while (columns.moveToNext()) names += columns.getString(1)
                assertFalse(names.contains("password"))
            }
            val owner = requireNotNull(db.userDao().getById(41))
            assertEquals("Legacy", owner.username)
            assertEquals(version >= 2, owner.categoriesInitialized)
            assertNull(owner.firebaseUid)
            assertFalse(owner.importPending)
            if (version >= 3) assertTrue(LegacyPassword.matches("Synthetic-local-only!", owner.legacyPasswordSalt, owner.legacyPasswordHash))
            else assertNull(owner.legacyPasswordHash)
            assertNull(db.userDao().getById(51)?.legacyPasswordHash)
            assertEquals(2, db.userDao().getUnlinked().size)
            val expense = requireNotNull(db.expenseDao().getForUserById(41, 71))
            assertEquals(61L, expense.categoryId)
            assertEquals(9007199254740993L, expense.amount)
            assertEquals("2024-02-29", expense.date)
            assertEquals("09:15", expense.startTime)
            assertEquals("10:45", expense.endTime)
            assertEquals("Synthetic expense", expense.description)
            assertEquals("/private/synthetic-receipt.jpg", expense.receiptImageUri)
            assertNull(db.expenseDao().getForUserById(51, 71))
            val category = requireNotNull(db.categoryDao().getForUserById(41, 61))
            assertEquals(if (version == 4) 123L else 0L, category.minMonthlyBudget)
            assertEquals(if (version == 4) 9999999999L else 0L, category.maxMonthlyBudget)
            val goal = db.budgetGoalDao().getForUser(41).single()
            assertEquals(81L, goal.id)
            assertEquals(123L, goal.minimumAmount)
            assertEquals(9999999999L, goal.maximumAmount)
            assertEquals("2024-02-01", goal.startDate)
            assertEquals("2024-02-29", goal.endDate)
            val next = db.userDao().createCloudUser("synthetic-new-uid", "New")
            assertTrue(next > 999)
            assertEquals(next, db.userDao().createCloudUser("synthetic-new-uid", "Ignored name"))
            assertThrows(Exception::class.java) { runBlocking { db.userDao().insert(UserEntity(username = "Duplicate", firebaseUid = "synthetic-new-uid")) } }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createLegacyDatabase(name: String, version: Int): SQLiteDatabase {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("com.example.pocketquestbudgeting.data.AppDatabase/$version.json").use {
            JSONObject(it.bufferedReader().readText()).getJSONObject("database")
        }
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(path, null)
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            if (indices != null) for (item in 0 until indices.length()) {
                database.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) database.execSQL(setup.getString(index))
        database.version = version
        return database
    }
}
