package com.example.pocketquestbudgeting.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthlyGoalMigrationTest {
    @Test fun version8UpgradeRetainedEveryGoalAndFinancialRowWithoutReconciliation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "goal-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/8.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO users (id, username, firebaseUid, importPending, categoriesInitialized) VALUES (41, 'Owner', 'fixture-owner', 1, 1)")
            old.execSQL("INSERT INTO categories (id, userId, name, minMonthlyBudget, maxMonthlyBudget) VALUES (61, 41, 'Preserved', 123, 9007199254740993)")
            old.execSQL("""INSERT INTO expenses (id, userId, categoryId, amount, date, startTime, endTime, description, receiptImageUri)
                VALUES (71, 41, 61, 9007199254740993, '2024-02-29', '09:15', '10:45', 'Synthetic', '/private/retained.jpg')""")
            old.execSQL("""INSERT INTO sync_queue (mutationId, uid, userId, recordType, recordId, payload, isDeleted, baseRevision, state, attemptCount)
                VALUES ('retained', 'fixture-owner', 41, 'monthlyGoals', '2026-10', '{}', 0, 0, 'PENDING', 2)""")
            old.execSQL("INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate) VALUES (81,41,1,2,'2024-02-01','2024-02-29')")
            old.execSQL("INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate) VALUES (82,41,9007199254740993,9223372036854775807,'2024-02-01','2024-02-29')")
            old.execSQL("INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate) VALUES (83,41,-1,2,'2026-10-02','2026-10-31')")
            old.version = 8
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(14, db.openHelper.writableDatabase.version)
            val all = db.budgetGoalDao().getForUser(41)
            assertEquals(setOf(81L, 82L, 83L), all.map { it.id }.toSet())
            assertTrue(all.all { it.monthKey == null && !it.isDeleted })
            val effective = requireNotNull(db.budgetGoalDao().getForMonth(41, "2024-02-01", "2024-02-29"))
            assertEquals(82L, effective.id); assertEquals(9007199254740993L, effective.minimumAmount)
            assertEquals(Long.MAX_VALUE, effective.maximumAmount)
            assertEquals(-1L, all.first { it.id == 83L }.minimumAmount)
            assertEquals("2026-10-02", all.first { it.id == 83L }.startDate)
            assertEquals("/private/retained.jpg", db.expenseDao().getForUserById(41, 71)?.receiptImageUri)
            assertEquals(61L, db.expenseDao().getForUserById(41, 71)?.categoryId)
            assertEquals(9007199254740993L, db.expenseDao().getForUserById(41, 71)?.amount)
            assertEquals(9007199254740993L, db.categoryDao().getForUserById(41, 61)?.maxMonthlyBudget)
            assertEquals(2L, db.syncDao().pending("fixture-owner").single().attemptCount)
            db.budgetGoalDao().updateSynced(effective.copy(monthKey = "2024-02"))
            try { db.budgetGoalDao().insert(effective.copy(id = 0, monthKey = "2024-02")); fail("Duplicate mapped month") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(3, db.budgetGoalDao().getForUser(41).size)
            assertEquals(82L, db.budgetGoalDao().getByMonthKey(41, "2024-02")?.id)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
