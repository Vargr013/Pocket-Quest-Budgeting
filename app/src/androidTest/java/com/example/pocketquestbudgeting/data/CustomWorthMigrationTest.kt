package com.example.pocketquestbudgeting.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomWorthMigrationTest {
    @Test fun version12KeepsOldColumnsSequencesAndStableMappingsAfterReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "custom-v12-${UUID.randomUUID()}"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/12.json").bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            old.execSQL("INSERT INTO users (id,username,firebaseUid,importPending,categoriesInitialized) VALUES (41,'Owner','owner',0,1),(52,'Other','other',0,1)")
            old.execSQL("INSERT INTO custom_worth_items VALUES (7,41,'Bicycle',9007199254740993,'CUSTOM'),(8,52,'Bicycle',450000,'CUSTOM')")
            old.execSQL("UPDATE sqlite_sequence SET seq = 99 WHERE name = 'custom_worth_items'")
            old.execSQL("INSERT INTO activity_days VALUES (41,'2024-02-29')")
            old.version = 12
        }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        var db = open()
        try {
            assertEquals(13, db.openHelper.writableDatabase.version)
            val row = db.gamificationDao().getCustomItems(41).single()
            assertEquals(7L, row.id)
            assertEquals(9007199254740993L, row.valueCents)
            assertFalse(row.isDeleted)
            GamificationMapping.requireItem(row)
            assertNotEquals(row.cloudId, db.gamificationDao().getCustomItems(52).single().cloudId)
            assertEquals(listOf("2024-02-29"), db.gamificationDao().getActiveDays(41))
            db.close(); db = open()
            assertEquals(row, db.gamificationDao().getCustomItems(41).single())
            assertEquals(100L, db.gamificationDao().insertCustomItem(row.copy(id = 0, cloudId = UUID.randomUUID().toString())))
            db.gamificationDao().deleteCustomItem(41, 7)
            assertTrue(db.gamificationDao().getCustomItem(41, 7)!!.isDeleted)
            assertEquals(9007199254740993L, db.gamificationDao().getCustomItem(41, 7)!!.valueCents)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
