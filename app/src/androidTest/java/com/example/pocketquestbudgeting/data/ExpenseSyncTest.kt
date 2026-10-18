package com.example.pocketquestbudgeting.data

import android.content.Context
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpenseSyncTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: PocketQuestSyncCoordinator
    private lateinit var account: SyncAccount
    private lateinit var expenses: ExpenseSync
    private var attached = true
    private val cloud = ExpenseMemoryCloud()
    private lateinit var context: Context
    private val databaseName = "expense-recovery-${UUID.randomUUID()}"

    @Before fun start() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        account = SyncAccount(db.userDao().createCloudUser("expense-test", "Synthetic"), "expense-test", 1)
        connect()
    }
    private fun connect() {
        sync = PocketQuestSyncCoordinator(db, cloud, { attached && it == account }, {})
        expenses = ExpenseSync(db, sync)
    }
    @After fun stop() { db.close(); context.deleteDatabase(databaseName) }
    private suspend fun category() = db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Synthetic"))
    private fun draft(category: Long) = ExpenseEntity(userId = account.localUserId, categoryId = category,
        amount = 9007199254740993L, date = "2024-02-29", startTime = "09:15", endTime = "10:30",
        description = "Synthetic", receiptImageUri = "/private/device-only.jpg")

    @Test fun repeatedImportSurvivedDiskReopenWithoutDuplicateMappingsOrQueueEntries() = runBlocking {
        val category = category()
        val id = db.expenseDao().insert(draft(category))
        expenses.importExisting(account)
        val pending = db.syncDao().pending(account.uid)
        assertEquals(listOf("categories", "expenses"), pending.map { it.recordType })
        assertFalse(pending.last().payload.contains("/private/"))
        val mapped = requireNotNull(db.expenseDao().getForUserById(account.localUserId, id))
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build(); connect()
        expenses.importExisting(account)
        assertEquals(pending.map { it.mutationId }, db.syncDao().pending(account.uid).map { it.mutationId })
        assertEquals(mapped.cloudId, db.expenseDao().getForUserById(account.localUserId, id)?.cloudId)
        sync.retryPending(account)
        expenses.importExisting(account)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertEquals(1, db.expenseDao().getForUser(account.localUserId).size)
        assertEquals("/private/device-only.jpg", db.expenseDao().getForUserById(account.localUserId, id)?.receiptImageUri)
    }

    @Test fun atomicSaveRolledBackCategoryMappingExpenseAndQueueTogether() = runBlocking {
        val category = category()
        try {
            db.withTransaction { assertEquals(1, expenses.save(account, draft(category))); error("Interrupted before commit") }
        } catch (_: IllegalStateException) { }
        assertNull(db.categoryDao().getForUserById(account.localUserId, category)?.cloudId)
        assertTrue(db.expenseDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun deletionKeptMappingAndPhotoWhileRemovingExpenseFromTotals() = runBlocking {
        val category = category()
        assertEquals(1, expenses.save(account, draft(category)))
        sync.retryPending(account)
        val row = db.expenseDao().getForUser(account.localUserId).single()
        assertEquals(1, expenses.delete(account, row.id))
        assertTrue(db.expenseDao().getForUser(account.localUserId).isEmpty())
        val tombstone = db.expenseDao().getAllForUser(account.localUserId).single()
        assertTrue(tombstone.isDeleted); assertEquals(row.cloudId, tombstone.cloudId)
        assertEquals(row.receiptImageUri, tombstone.receiptImageUri)
        assertEquals(0L, db.categoryDao().getSpendingForUser(account.localUserId, null, null).single().totalCents)
        assertTrue(db.syncDao().pending(account.uid).single().isDeleted)
    }

    @Test fun invalidDraftAndAnotherOwnersCategoryCouldNotBeSaved() = runBlocking {
        val category = category()
        for (invalid in listOf(draft(category).copy(amount = 0), draft(category).copy(date = "2023-02-29"),
            draft(category).copy(startTime = "24:00"), draft(category).copy(endTime = "09:14"))) {
            try { expenses.save(account, invalid); fail("Invalid draft accepted") } catch (_: IllegalArgumentException) { }
        }
        val other = db.userDao().createCloudUser("other", "Other")
        val otherCategory = db.categoryDao().insert(CategoryEntity(userId = other, name = "Private"))
        assertEquals(-1, expenses.save(account, draft(otherCategory)))
        attached = false
        try { expenses.save(account, draft(category)); fail("Detached account accepted") } catch (_: IllegalStateException) { }
        assertTrue(db.expenseDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun repeatDownloadUsedCategoryMappingKeptLocalPhotoAndPreservedPendingEdit() = runBlocking {
        val category = category()
        expenses.save(account, draft(category)); sync.retryPending(account)
        val old = db.expenseDao().getForUser(account.localUserId).single()
        val categoryCloud = requireNotNull(db.categoryDao().getForUserById(account.localUserId, category)?.cloudId)
        val remote = SyncRecord(account.uid, account.localUserId, "expenses", requireNotNull(old.cloudId), 2,
            SyncMapping.encode("expenses", ExpenseMapping.fields(old.copy(description = "Server edit"), categoryCloud)))
        cloud.rows = listOf(remote)
        repeat(2) { expenses.refresh(account) }
        assertEquals(old.id, db.expenseDao().getForUser(account.localUserId).single().id)
        assertEquals(old.receiptImageUri, db.expenseDao().getForUser(account.localUserId).single().receiptImageUri)
        expenses.save(account, old.copy(description = "Pending local edit"))
        cloud.rows = listOf(remote.copy(revision = 3))
        expenses.refresh(account)
        assertEquals("Pending local edit", db.expenseDao().getForUser(account.localUserId).single().description)
        assertEquals(1, db.syncDao().pending(account.uid).size)
    }

    @Test fun invalidLegacyRowStayedLocalWhileValidRecordsCouldStillSync() = runBlocking {
        val category = category()
        val invalid = db.expenseDao().insert(draft(category).copy(date = "2023-02-29"))
        db.expenseDao().insert(draft(category))
        assertEquals(1, expenses.importExisting(account))
        assertEquals(2, db.syncDao().pending(account.uid).size)
        sync.retryPending(account)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertNull(db.expenseDao().getForUserById(account.localUserId, invalid)?.cloudId)
        assertEquals(2, db.expenseDao().getForUser(account.localUserId).size)
    }

    @Test fun monthlyGoalsKeptTheBroadImportMarkerForTheirOwnIntegration() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE users SET importPending = 1 WHERE id = ?", arrayOf(account.localUserId))
        db.budgetGoalDao().insert(BudgetGoalEntity(userId = account.localUserId, minimumAmount = 0,
            maximumAmount = 12345, startDate = "2026-10-01", endDate = "2026-10-31"))
        expenses.finishRecordImport(account)
        assertTrue(requireNotNull(db.userDao().getById(account.localUserId)).importPending)
    }

    @Test fun anotherAccountCouldNotUpdateOrDeleteTheOriginalExpense() = runBlocking {
        val category = category(); expenses.save(account, draft(category))
        val saved = db.expenseDao().getForUser(account.localUserId).single()
        val owner = account
        val otherId = db.userDao().createCloudUser("other-account", "Other")
        val otherCategory = db.categoryDao().insert(CategoryEntity(userId = otherId, name = "Other"))
        account = SyncAccount(otherId, "other-account", 2)
        assertEquals(0, expenses.save(account, saved.copy(userId = otherId, categoryId = otherCategory, description = "Wrong owner")))
        assertEquals(0, expenses.delete(account, saved.id))
        assertEquals(saved, db.expenseDao().getForUserById(owner.localUserId, saved.id))
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun missingDownloadedCategoryDidNotCreateADanglingExpense() = runBlocking {
        val remote = SyncRecord(account.uid, account.localUserId, "expenses", "remote", 1,
            SyncMapping.encode("expenses", ExpenseMapping.fields(draft(99), "missing-category")))
        cloud.rows = listOf(remote)
        try { expenses.refresh(account); fail("Dangling reference accepted") } catch (_: IllegalStateException) { }
        assertTrue(db.expenseDao().getForUser(account.localUserId).isEmpty())
    }
}

private class ExpenseMemoryCloud : SyncCloud {
    var rows = emptyList<SyncRecord>()
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean) = SendResult.Accepted(requireNotNull(operation.baseRevision) + 1)
    override suspend fun download(account: SyncAccount, type: String) = rows
}
