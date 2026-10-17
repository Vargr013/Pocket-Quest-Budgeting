package com.example.pocketquestbudgeting.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorySyncTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: PocketQuestSyncCoordinator
    private lateinit var categories: CategorySync
    private lateinit var account: SyncAccount
    private val cloud = CategoryTestCloud()

    @Before fun start() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        account = SyncAccount(db.userDao().createCloudUser("category-test", "Test"), "category-test", 1)
        sync = PocketQuestSyncCoordinator(db, cloud, { it == account }, {})
        categories = CategorySync(db, sync)
    }
    @After fun stop() { db.close() }

    @Test fun centsZeroLimitsRenameAndCaseValidationUsedOneStableId() = runBlocking {
        val id = requireNotNull(categories.create(account, " Travel ", 123L, 9007199254740993L))
        val saved = requireNotNull(db.categoryDao().getForUserById(account.localUserId, id))
        assertNotNull(saved.cloudId)
        assertNull(categories.create(account, "tRaVeL", 0, 0))
        assertEquals(CategoryChangeResult.SUCCESS, categories.rename(account, id, "TRAVEL"))
        assertEquals(CategoryChangeResult.SUCCESS, categories.setBudgets(account, id, 0, 0))
        val other = requireNotNull(categories.create(account, "Other", 0, 0))
        assertEquals(CategoryChangeResult.DUPLICATE, categories.rename(account, other, "travel"))
        val queue = db.syncDao().forRecord(account.uid, "categories", requireNotNull(saved.cloudId))
        assertEquals(3, queue.size)
        assertEquals(9007199254740993L, SyncMapping.decode("categories", queue.first().payload)["maximumCents"])
        assertEquals(0L, SyncMapping.decode("categories", queue.last().payload)["maximumCents"])
        assertEquals(saved.cloudId, db.categoryDao().getForUserById(account.localUserId, id)?.cloudId)
    }

    @Test fun invalidRangesDidNotSaveRowsOrQueueEntries() = runBlocking {
        for ((minimum, maximum) in listOf(-1L to 0L, 0L to -1L, 1L to 0L, 200L to 100L)) {
            try { categories.create(account, "Invalid", minimum, maximum); fail("Range accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(db.categoryDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun repeatedDownloadUpdatedTheSameNumericRowAndPendingEditSurvived() = runBlocking {
        val remote = SyncRecord(account.uid, account.localUserId, "categories", "stable", 1,
            SyncMapping.encode("categories", mapOf("name" to "Remote", "minimumCents" to 101L, "maximumCents" to 9007199254740993L)))
        cloud.rows = listOf(remote)
        sync.refreshFromServer(account, "categories")
        categories.applyAvailable(account)
        val id = db.categoryDao().getForUser(account.localUserId).single().id
        categories.applyAvailable(account)
        assertEquals(id, db.categoryDao().getForUser(account.localUserId).single().id)
        categories.rename(account, id, "Local pending")
        cloud.rows = listOf(remote.copy(revision = 2, payload = remote.payload.replace("Remote", "New remote")))
        sync.refreshFromServer(account, "categories")
        categories.applyAvailable(account)
        assertEquals("Local pending", db.categoryDao().getForUserById(account.localUserId, id)?.name)
        assertEquals(1, db.syncDao().pending(account.uid).size)
    }

    @Test fun archiveKeptExactLimitsAndHistoryWhileHidingNewAssignments() = runBlocking {
        val id = requireNotNull(categories.create(account, "Travel", 123, 9007199254740993L))
        val expense = db.expenseDao().insert(ExpenseEntity(userId = account.localUserId, categoryId = id,
            amount = 101, date = "2026-10-17", startTime = "09:15", endTime = "09:30", description = "Synthetic"))
        sync.retryPending(account)
        assertEquals(CategoryChangeResult.SUCCESS, categories.archive(account, id))
        val archived = requireNotNull(db.categoryDao().getForUserById(account.localUserId, id))
        assertTrue(archived.isArchived)
        assertEquals(9007199254740993L, archived.maxMonthlyBudget)
        assertTrue(db.categoryDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.categoryDao().getMonthProgress(account.localUserId, "2026-10-01", "2026-11-01").isEmpty())
        assertEquals(id, db.expenseDao().getForUserById(account.localUserId, expense)?.categoryId)
        assertEquals(101L, db.categoryDao().getSpendingForUser(account.localUserId, null, null).single().totalCents)
        assertTrue(db.syncDao().pending(account.uid).single().isDeleted)
    }

    @Test fun enclosingFailureRolledBackTheCategoryMappingAndQueueTogether() = runBlocking {
        try {
            db.withTransaction {
                categories.create(account, "Rollback", 0, 100)
                error("Simulated interruption before outer commit")
            }
        } catch (_: IllegalStateException) { }
        assertTrue(db.categoryDao().getAllForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertTrue(db.syncDao().records(account.uid).isEmpty())
    }

    @Test fun staleAccountAndAnotherOwnersRowCouldNotBeChanged() = runBlocking {
        val id = requireNotNull(categories.create(account, "Private", 0, 100))
        val otherId = db.userDao().createCloudUser("other-owner", "Other")
        val other = SyncAccount(otherId, "other-owner", 2)
        assertEquals(CategoryChangeResult.NOT_FOUND, categories.rename(other, id, "Leaked"))
        try { categories.create(other, "Wrong", 0, 0); fail("Detached owner accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(db.categoryDao().getForUser(otherId).isEmpty())
        assertEquals("Private", db.categoryDao().getForUserById(account.localUserId, id)?.name)
    }
}

private class CategoryTestCloud : SyncCloud {
    var rows = emptyList<SyncRecord>()
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult = SendResult.Accepted(requireNotNull(operation.baseRevision) + 1)
    override suspend fun download(account: SyncAccount, type: String) = rows
}
