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
class CategoryMigrationTest {
    @Test fun version6UpgradeKeptFinancialIdsQueueAndExactLimits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "category-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/6.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
            old.version = 6
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(10, db.openHelper.writableDatabase.version)
            val category = requireNotNull(db.categoryDao().getForUserById(41, 61))
            assertNull(category.cloudId); assertFalse(category.isArchived)
            assertEquals(9007199254740993L, category.maxMonthlyBudget)
            assertEquals(123L, category.minMonthlyBudget)
            assertEquals(61L, db.expenseDao().getForUserById(41, 71)?.categoryId)
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
