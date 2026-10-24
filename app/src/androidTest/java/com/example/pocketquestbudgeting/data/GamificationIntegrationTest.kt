package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamificationIntegrationTest {
    @Test fun evaluatedBadgesSurviveReopenAndUseTheExistingSyncPathWithDifferentLocalOwners() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "gamification-integration-${UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        val otherDevice = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val owner = db.userDao().createCloudUser("shared-owner", "Owner")
            val unrelated = otherDevice.userDao().createCloudUser("other-owner", "Other")
            val mappedOwner = otherDevice.userDao().createCloudUser("shared-owner", "Owner")
            assertNotEquals(owner, mappedOwner)
            val category = db.categoryDao().insert(CategoryEntity(userId = owner, name = "Food", maxMonthlyBudget = 100_00))
            db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = 50_00,
                date = "2026-09-10", startTime = "09:00", endTime = "09:30", description = "Synthetic"))
            db.gamificationDao().markActiveDay(owner, "2026-10-18")
            db.gamificationDao().markActiveDay(owner, "2026-10-19")
            db.gamificationDao().markActiveDay(owner, "2026-10-20")
            db.gamificationDao().insertCustomItem(CustomWorthItemEntity(userId = owner,
                name = "Bicycle", valueCents = 450000, category = "CUSTOM"))
            val reports = coroutineScope { (1..4).map {
                async { AchievementLoader.load(db, owner, today = "2026-10-20", nowMillis = 42L) }
            }.awaitAll() }
            assertEquals(1, reports.count { it.newlyEarned.isNotEmpty() })
            val earned = db.earnedAchievementDao().getForUser(owner)
            assertTrue(earned.any { it.badgeId == "first_expense" })
            assertTrue(earned.any { it.badgeId == "streak_3" })
            earned.forEach { EarnedAchievementChecks.requireValues(it) }
            db.close()
            db = open()
            assertEquals(earned, db.earnedAchievementDao().getForUser(owner))
            assertTrue(AchievementLoader.load(db, owner, today = "2026-10-20", nowMillis = 500L).newlyEarned.isEmpty())

            val server = mutableMapOf<String, SyncRecord>()
            val cloud = object : SyncCloud {
                override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
                    check(stillCurrent())
                    assertEquals(SavingsMapping.AWARDS, operation.recordType)
                    SavingsMapping.validate(operation.recordType, SyncMapping.decode(operation.recordType, operation.payload))
                    server[operation.recordId] = SyncRecord(operation.uid, operation.userId, operation.recordType,
                        operation.recordId, 1, operation.payload, false)
                    return SendResult.Accepted(1)
                }
                override suspend fun download(account: SyncAccount, type: String) = server.values
                    .filter { it.recordType == type && it.uid == account.uid }.map { it.copy(userId = account.localUserId) }
            }
            val firstAccount = SyncAccount(owner, "shared-owner", 1)
            val firstSync = PocketQuestSyncCoordinator(db, cloud, { it == firstAccount }, {})
            val savings = SavingsSync(db, firstSync)
            savings.importExisting(firstAccount)
            savings.importExisting(firstAccount)
            assertEquals(earned.size, db.syncDao().pending(firstAccount.uid).size)
            assertEquals(SyncRun.COMPLETE, firstSync.retryPending(firstAccount))
            assertTrue(db.syncDao().pending(firstAccount.uid).isEmpty())

            val secondAccount = SyncAccount(mappedOwner, "shared-owner", 1)
            val secondSync = PocketQuestSyncCoordinator(otherDevice, cloud, { it == secondAccount }, {})
            SavingsSync(otherDevice, secondSync).refresh(secondAccount)
            val secondReport = AchievementLoader.load(otherDevice, mappedOwner, today = "2026-10-20", nowMillis = 900L)
            assertTrue(secondReport.newlyEarned.isEmpty())
            assertEquals(earned.map { it.badgeId }.toSet(), secondReport.progress.filter { it.earned }.map { it.definition.id }.toSet())
            assertTrue(otherDevice.earnedAchievementDao().getForUser(unrelated).isEmpty())
            assertTrue(otherDevice.gamificationDao().getActiveDays(mappedOwner).isEmpty())
            assertTrue(otherDevice.gamificationDao().getCustomItems(mappedOwner).isEmpty())
            assertEquals("Bicycle", db.gamificationDao().getCustomItems(owner).single().name)
        } finally { db.close(); otherDevice.close(); context.deleteDatabase(name) }
    }
}
