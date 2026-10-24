package com.example.pocketquestbudgeting.data

import android.database.Cursor
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
class SavingsMigrationTest {
    @Test fun version10PreservesEveryOldTableAndReopensWithEmptyFeatureTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "savings-migration-${UUID.randomUUID()}"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/10.json").bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        val tables = mutableListOf<String>()
        val before: Map<String, List<List<String?>>>
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                tables.add(table)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO users (id,username,firebaseUid,importPending,categoriesInitialized) VALUES (41,'Synthetic owner','fixture-owner',1,1),(52,'Synthetic other','fixture-other',0,0)")
            old.execSQL("INSERT INTO categories (id,userId,name,minMonthlyBudget,maxMonthlyBudget,cloudId,isArchived) VALUES (61,41,'Retained',123,9007199254740993,'category-id',1)")
            old.execSQL("""INSERT INTO expenses (id,userId,categoryId,amount,date,startTime,endTime,description,receiptImageUri,cloudId,isDeleted,receiptObjectPath)
                VALUES (71,41,61,9007199254740993,'2024-02-29','09:15','10:45','Retained','/private/photo.jpg','expense-id',1,'users/fixture-owner/receipts/expense-id/accepted')""")
            old.execSQL("INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate,monthKey,isDeleted) VALUES (81,41,9007199254740993,9223372036854775807,'2024-02-01','2024-02-29','2024-02',1)")
            old.execSQL("""INSERT INTO sync_records (uid,userId,recordType,recordId,revision,payload,isDeleted,updatedAtMillis,deletedAtMillis,lastMutationId)
                VALUES ('fixture-owner',41,'expenses','expense-id',3,'{"amount":9007199254740993}',1,123,124,'retained-mutation')""")
            old.execSQL("""INSERT INTO sync_queue (sequence,mutationId,uid,userId,recordType,recordId,payload,isDeleted,baseRevision,state,attemptCount,lastError,receiptMode)
                VALUES (91,'retained-mutation','fixture-owner',41,'expenses','expense-id','{"amount":9007199254740993}',1,3,'CONFLICT',2,'Retained error','FROZEN')""")
            old.execSQL("""INSERT INTO receipt_jobs (jobId,uid,userId,expenseCloudId,sourceLocalPath,targetObjectPath,expectedAcceptedPath,remove,selected,phase,sourceSha256,byteCount,mimeType,publicationMutationId,attemptCount,lastError,blocked)
                VALUES ('job-id','fixture-owner',41,'expense-id','/private/replacement.jpg','users/fixture-owner/receipts/expense-id/job-id','users/fixture-owner/receipts/expense-id/accepted',0,1,'UPLOADED','retained-hash',321,'image/jpeg','retained-mutation',2,'Retained error',1)""")
            old.execSQL("""INSERT INTO receipt_cache (uid,userId,expenseCloudId,objectPath,localPath,state,lastError,blocked)
                VALUES ('fixture-owner',41,'expense-id','users/fixture-owner/receipts/expense-id/accepted','/private/cached.jpg','READY','Retained error',1)""")
            // I retained deleted ID high-water marks as well as the remaining rows.
            old.execSQL("UPDATE sqlite_sequence SET seq = 999 WHERE name = 'users'")
            tables.add("sqlite_sequence")
            before = snapshot(tables) { sql -> old.rawQuery(sql, null) }
            old.version = 10
        }
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(12, db.openHelper.writableDatabase.version)
            assertEquals(before, snapshot(tables) { sql -> db.openHelper.writableDatabase.query(sql) })
            assertTrue(db.savingsDao().getForUser(41, true).isEmpty())
            assertTrue(db.savingsDao().contributions(41, 1).isEmpty())
            assertTrue(db.earnedAchievementDao().getForUser(41).isEmpty())
            assertEquals(1000L, db.userDao().insert(UserEntity(username = "Synthetic new owner")))
            val goalId = db.savingsDao().createGoal(SavingsGoalEntity(userId = 41, cloudId = UUID.randomUUID().toString(), name = "New goal", targetCents = 100), 77, "2024-02-29")
            db.earnedAchievementDao().recordEarned(EarnedAchievementEntity(userId = 41, awardKey = "synthetic", badgeId = "synthetic", criteriaVersion = 1,
                earnedDate = "2024-02-29", earnedZoneId = "Africa/Johannesburg", evidenceRecordId = "synthetic-record"))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
            assertEquals(77L, db.savingsDao().savedCents(41, goalId))
            assertEquals(1, db.earnedAchievementDao().getForUser(41).size)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun snapshot(tables: List<String>, query: (String) -> Cursor): Map<String, List<List<String?>>> = tables.associateWith { table ->
        query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column ->
                    if (cursor.isNull(column)) null else "${cursor.getType(column)}:${cursor.getString(column)}"
                })
            }
        }
    }
}
