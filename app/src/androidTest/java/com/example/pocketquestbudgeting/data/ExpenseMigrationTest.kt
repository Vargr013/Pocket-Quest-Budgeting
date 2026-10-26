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
class ExpenseMigrationTest {
    @Test fun version13UpgradeReplacedTimeColumnsAndRetainedDeletedRowsAndFrozenMutations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "payment-time-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/13.json").bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        val legacy = SyncMapping.encode("expenses", mapOf("categoryId" to "category", "amountCents" to 9007199254740993L,
            "date" to "2024-02-29", "startTime" to "09:15", "endTime" to "10:45",
            "description" to "Legacy", "receiptObjectPath" to null))
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("""INSERT INTO expenses
                (id,userId,categoryId,amount,date,startTime,endTime,description,receiptImageUri,cloudId,isDeleted,receiptObjectPath)
                VALUES (71,41,61,9007199254740993,'2024-02-29','09:15','10:45','Legacy','/private/kept.jpg','expense',1,'cloud-photo')""")
            old.execSQL("UPDATE sqlite_sequence SET seq = 1000 WHERE name = 'expenses'")
            old.execSQL("""INSERT INTO sync_queue
                (mutationId,uid,userId,recordType,recordId,payload,isDeleted,baseRevision,state,attemptCount,receiptMode)
                VALUES ('frozen','owner',41,'expenses','expense',?,1,2,'PENDING',3,'FROZEN')""", arrayOf(legacy))
            old.execSQL("""INSERT INTO sync_records (uid,userId,recordType,recordId,revision,payload,isDeleted)
                VALUES ('owner',41,'expenses','expense',2,?,0)""", arrayOf(legacy))
            old.version = 13
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val row = requireNotNull(db.expenseDao().getIncludingDeleted(41, 71))
            assertEquals("09:15", row.paymentTime)
            assertEquals(9007199254740993L, row.amount)
            assertEquals("/private/kept.jpg", row.receiptImageUri)
            assertEquals("cloud-photo", row.receiptObjectPath)
            assertTrue(row.isDeleted)
            assertEquals("expense", row.cloudId)
            assertEquals(legacy, db.syncDao().pending("owner").single().payload)
            assertEquals(legacy, db.syncDao().record("owner", "expenses", "expense")?.payload)
            db.openHelper.writableDatabase.query("PRAGMA table_info(expenses)").use { columns ->
                val names = mutableListOf<String>()
                while (columns.moveToNext()) names += columns.getString(columns.getColumnIndexOrThrow("name"))
                assertTrue("paymentTime" in names)
                assertFalse("startTime" in names); assertFalse("endTime" in names)
            }
            assertEquals(1001L, db.expenseDao().insert(row.copy(id = 0, cloudId = "another")))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun version7UpgradeKeptFinancialIdsQueueAndLocalPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "expense-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/7.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            old.version = 7
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(14, db.openHelper.writableDatabase.version)
            val category = requireNotNull(db.categoryDao().getForUserById(41, 61))
            assertNull(category.cloudId); assertFalse(category.isArchived)
            assertEquals(9007199254740993L, category.maxMonthlyBudget)
            assertEquals(123L, category.minMonthlyBudget)
            assertEquals(61L, db.expenseDao().getForUserById(41, 71)?.categoryId)
            val expense = requireNotNull(db.expenseDao().getForUserById(41, 71))
            assertNull(expense.cloudId); assertFalse(expense.isDeleted); assertNull(expense.receiptObjectPath)
            assertEquals(9007199254740993L, expense.amount)
            assertEquals("2024-02-29", expense.date); assertEquals("09:15", expense.paymentTime)
            db.expenseDao().updateSynced(expense.copy(cloudId = "stable-expense"))
            try { db.expenseDao().insert(expense.copy(id = 0, cloudId = "stable-expense")); fail("Duplicate expense mapping accepted") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(71L, db.expenseDao().getByCloudId(41, "stable-expense")?.id)
            assertEquals("/private/retained.jpg", db.expenseDao().getForUserById(41, 71)?.receiptImageUri)
            assertEquals(2L, db.syncDao().pending("fixture-owner").single().attemptCount)
            db.categoryDao().updateSynced(category.copy(cloudId = "stable"))
            try {
                db.categoryDao().insert(category.copy(id = 0, cloudId = "stable")); fail("Duplicate cloud mapping accepted")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(61L, db.categoryDao().getByCloudId(41, "stable")?.id)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
