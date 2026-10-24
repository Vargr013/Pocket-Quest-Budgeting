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
class GamificationUpgradeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun version11KeepsEveryExistingRowAndAwardWhenAddingGamification() = runBlocking {
        val name = "gamification-v11-${UUID.randomUUID()}"
        val before: Map<String, List<List<String?>>>
        val tables: List<String>
        fixture(name, 11).use { old ->
            seedFinancialRows(old)
            old.execSQL("""INSERT INTO savings_goals (id,userId,cloudId,name,targetCents,note)
                VALUES (91,41,'00000000-0000-0000-0000-000000000091','Retained goal',9007199254740993,'Keep this')""")
            old.execSQL("""INSERT INTO savings_contributions (id,userId,goalId,businessId,kind,amountCents,effectiveDate)
                VALUES (101,41,91,'retained-addition','ADDITION',9007199254740993,'2026-09-10')""")
            old.execSQL("""INSERT INTO earned_achievements
                (id,userId,awardKey,badgeId,criteriaVersion,earnedDate,earnedZoneId,evidenceRecordId)
                VALUES (111,41,'retained-award','first_expense',1,'2026-09-10','Africa/Johannesburg','retained-evidence')""")
            old.execSQL("""INSERT INTO sync_queue
                (sequence,mutationId,uid,userId,recordType,recordId,payload,isDeleted,baseRevision,state,attemptCount,receiptMode)
                VALUES (121,'retained-mutation','fixture-owner',41,'expenses','retained-expense','{}',0,3,'PENDING',2,'KEEP')""")
            old.execSQL("""INSERT INTO receipt_cache (uid,userId,expenseCloudId,objectPath,localPath,state,blocked)
                VALUES ('fixture-owner',41,'retained-expense','retained-object','/private/cached.jpg','READY',0)""")
            tables = old.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            before = snapshot(tables) { old.rawQuery(it, null) }
        }
        var db = open(name)
        try {
            assertEquals(13, db.openHelper.writableDatabase.version)
            assertEquals(before, snapshot(tables) { db.openHelper.writableDatabase.query(it) })
            assertEquals(9007199254740993L, db.savingsDao().savedCents(41, 91))
            assertTrue(db.gamificationDao().getActiveDays(41).isEmpty())
            assertTrue(db.gamificationDao().getCustomItems(41).isEmpty())
            val report = AchievementLoader.load(db, 41, today = "2026-10-20", nowMillis = 500L)
            assertTrue(report.progress.first { it.definition.id == "first_expense" }.earned)
            assertFalse("first_expense" in report.newlyEarned)
            val retained = db.earnedAchievementDao().getByAwardKey(41, "retained-award")!!
            assertEquals("retained-evidence", retained.evidenceRecordId)
            assertNull(retained.earnedAtEpochMillis)
            db.close()
            db = open(name)
            assertEquals(retained, db.earnedAchievementDao().getByAwardKey(41, "retained-award"))
            assertEquals(9007199254740993L, db.savingsDao().savedCents(41, 91))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun standaloneGamificationVersion10KeepsBadgesActivityCustomItemsAndFinancialRows() = runBlocking {
        val name = "gamification-branch-v10-${UUID.randomUUID()}"
        val before: Map<String, List<List<String?>>>
        val retainedTables = listOf("users", "categories", "expenses", "budget_goals", "sync_records", "activity_days", "custom_worth_items")
        fixture(name, 9).use { old ->
            seedFinancialRows(old)
            old.execSQL("""CREATE TABLE earned_achievements (userId INTEGER NOT NULL, achievementId TEXT NOT NULL,
                earnedAtMillis INTEGER NOT NULL, PRIMARY KEY(userId,achievementId))""")
            old.execSQL("CREATE TABLE activity_days (userId INTEGER NOT NULL, date TEXT NOT NULL, PRIMARY KEY(userId,date))")
            old.execSQL("""CREATE TABLE custom_worth_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userId INTEGER NOT NULL, name TEXT NOT NULL, valueCents INTEGER NOT NULL, category TEXT NOT NULL)""")
            old.execSQL("CREATE INDEX index_custom_worth_items_userId ON custom_worth_items(userId)")
            old.execSQL("INSERT INTO earned_achievements VALUES (41,'first_expense',42),(41,'streak_3',43),(52,'first_expense',44)")
            old.execSQL("INSERT INTO activity_days VALUES (41,'2026-10-18'),(41,'2026-10-19'),(41,'2026-10-20')")
            old.execSQL("INSERT INTO custom_worth_items VALUES (7,41,'Bicycle',450000,'CUSTOM')")
            old.execSQL("UPDATE sqlite_sequence SET seq = 99 WHERE name = 'custom_worth_items'")
            old.execSQL("""INSERT INTO sync_queue
                (mutationId,uid,userId,recordType,recordId,payload,isDeleted,baseRevision,state,attemptCount)
                VALUES ('branch-mutation','fixture-owner',41,'expenses','retained-expense','{}',0,0,'PENDING',2)""")
            old.version = 10
            before = snapshot(retainedTables) { old.rawQuery(it, null) }
        }
        var db = open(name)
        try {
            assertEquals(13, db.openHelper.writableDatabase.version)
            assertEquals(before, snapshot(retainedTables) { db.openHelper.writableDatabase.query(it) })
            val badges = db.earnedAchievementDao().getForUser(41)
            assertEquals(setOf("first_expense", "streak_3"), badges.map { it.badgeId }.toSet())
            assertEquals(42L, badges.first { it.badgeId == "first_expense" }.earnedAtEpochMillis)
            badges.forEach { EarnedAchievementChecks.requireValues(it) }
            assertEquals(44L, db.earnedAchievementDao().getForUser(52).single().earnedAtEpochMillis)
            val report = AchievementLoader.load(db, 41, today = "2026-10-20", nowMillis = 500L)
            assertEquals(3, report.currentStreak)
            assertFalse("first_expense" in report.newlyEarned)
            assertFalse("streak_3" in report.newlyEarned)
            assertEquals("FROZEN", db.syncDao().pending("fixture-owner").single().receiptMode)
            assertEquals(2L, db.syncDao().pending("fixture-owner").single().attemptCount)
            assertEquals(100L, db.gamificationDao().insertCustomItem(CustomWorthItemEntity(userId = 41,
                name = "Retained sequence", valueCents = 100, category = "CUSTOM")))
            db.close()
            db = open(name)
            assertEquals(42L, db.earnedAchievementDao().getByAwardKey(41, "gamification:v1:first_expense")?.earnedAtEpochMillis)
            assertEquals(2, db.gamificationDao().getCustomItems(41).size)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun fixture(name: String, version: Int): SQLiteDatabase {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/$version.json").bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        return SQLiteDatabase.openOrCreateDatabase(file, null).also { old ->
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
            old.version = version
        }
    }

    private fun seedFinancialRows(old: SQLiteDatabase) {
        old.execSQL("""INSERT INTO users (id,username,firebaseUid,importPending,categoriesInitialized)
            VALUES (41,'Owner','fixture-owner',0,1),(52,'Other','fixture-other',0,1)""")
        old.execSQL("INSERT INTO categories (id,userId,name,minMonthlyBudget,maxMonthlyBudget) VALUES (61,41,'Retained',0,100000)")
        old.execSQL("""INSERT INTO expenses (id,userId,categoryId,amount,date,startTime,endTime,description,receiptImageUri)
            VALUES (71,41,61,150000,'2026-09-10','09:15','10:45','Retained','/private/photo.jpg')""")
        old.execSQL("""INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate,monthKey)
            VALUES (81,41,0,200000,'2026-09-01','2026-09-30','2026-09')""")
    }

    private fun open(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private fun snapshot(tables: List<String>, query: (String) -> Cursor) = tables.associateWith { table ->
        query("SELECT ${if (table == "custom_worth_items") "id,userId,name,valueCents,category" else "*"} FROM `$table` ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column ->
                if (cursor.isNull(column)) null else "${cursor.getType(column)}:${cursor.getString(column)}"
            }) }
        }
    }
}
