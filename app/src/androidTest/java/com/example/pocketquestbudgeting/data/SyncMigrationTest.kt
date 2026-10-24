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
class SyncMigrationTest {
    @Test fun version5UpgradePreservedMappedOwnersAndFinancialRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "slice4-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/5.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            old.execSQL("INSERT INTO budget_goals (id, userId, minimumAmount, maximumAmount, startDate, endDate) VALUES (81, 41, 100, 10000, '2024-02-01', '2024-02-29')")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(13, db.openHelper.writableDatabase.version)
            assertEquals("fixture-owner", db.userDao().getById(41)?.firebaseUid)
            assertTrue(requireNotNull(db.userDao().getById(41)).importPending)
            assertTrue(requireNotNull(db.userDao().getById(41)).categoriesInitialized)
            assertEquals(9007199254740993L, db.categoryDao().getForUserById(41, 61)?.maxMonthlyBudget)
            val expense = requireNotNull(db.expenseDao().getForUserById(41, 71))
            assertEquals(9007199254740993L, expense.amount)
            assertEquals("2024-02-29", expense.date)
            assertEquals("09:15", expense.startTime)
            assertEquals("10:45", expense.endTime)
            assertEquals("/private/retained.jpg", expense.receiptImageUri)
            assertEquals(10000L, db.budgetGoalDao().getForUser(41).single().maximumAmount)
            assertTrue(db.syncDao().pending("fixture-owner").isEmpty())
            assertTrue(db.syncDao().records("fixture-owner").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
