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
class GamificationMigrationTest {
    @Test fun version9UpgradeAddedLocalGamificationTablesAndKeptFinancialRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "gamification-migration-fixture"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.pocketquestbudgeting.data.AppDatabase/9.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO users (id, username, firebaseUid, importPending, categoriesInitialized) VALUES (41, 'Owner', 'fixture-owner', 0, 1)")
            old.execSQL("INSERT INTO categories (id, userId, name, minMonthlyBudget, maxMonthlyBudget) VALUES (61, 41, 'Preserved', 0, 100000)")
            old.execSQL("""INSERT INTO expenses (id, userId, categoryId, amount, date, startTime, endTime, description)
                VALUES (71, 41, 61, 150000, '2026-09-10', '09:15', '10:45', 'Synthetic')""")
            old.execSQL("INSERT INTO budget_goals (id,userId,minimumAmount,maximumAmount,startDate,endDate,monthKey) VALUES (81,41,0,200000,'2026-09-01','2026-09-30','2026-09')")
            old.version = 9
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertEquals(12, db.openHelper.writableDatabase.version)
            assertEquals(150000L, db.expenseDao().getForUserById(41, 71)?.amount)
            assertEquals("2026-09", db.budgetGoalDao().getByMonthKey(41, "2026-09")?.monthKey)

            val gamification = db.gamificationDao()
            val awards = db.earnedAchievementDao()
            assertTrue(awards.getForUser(41).isEmpty())
            gamification.markActiveDay(41, "2026-10-20")
            gamification.markActiveDay(41, "2026-10-20")
            assertEquals(listOf("2026-10-20"), gamification.getActiveDays(41))

            val report = AchievementLoader.load(db, 41, today = "2026-10-20", nowMillis = 5L)
            assertEquals(500_00L, report.totalSavedCents)
            assertTrue(awards.getForUser(41).any { it.badgeId == "worth_coffee" && it.earnedAtEpochMillis == 5L })
            AchievementLoader.load(db, 41, today = "2026-10-20", nowMillis = 9L)
            assertEquals(5L, awards.getForUser(41).first { it.badgeId == "worth_coffee" }.earnedAtEpochMillis)

            val itemId = gamification.insertCustomItem(CustomWorthItemEntity(userId = 41, name = "Bicycle", valueCents = 4_500_00, category = "CUSTOM"))
            assertEquals("Bicycle", gamification.getCustomItems(41).single().name)
            assertEquals(0, gamification.deleteCustomItem(42, itemId))
            assertEquals(1, gamification.deleteCustomItem(41, itemId))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
