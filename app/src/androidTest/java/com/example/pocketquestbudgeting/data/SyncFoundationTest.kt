package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncFoundationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "slice4-durable-fixture"
    private lateinit var db: AppDatabase
    private lateinit var cloud: FixtureSyncCloud
    private lateinit var account: SyncAccount
    private var active: SyncAccount? = null
    private var scheduled = 0

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(databaseName)
        db = open()
        account = SyncAccount(db.userDao().createCloudUser("fixture-owner", "Owner"), "fixture-owner", 1)
        active = account
        cloud = FixtureSyncCloud()
    }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).addMigrations(*DatabaseMigrations.ALL).build()
    private fun coordinator() = PocketQuestSyncCoordinator(db, cloud, { it == active }, { scheduled++ })
    private fun fields(name: String) = mapOf("name" to name, "minimumCents" to 0L, "maximumCents" to 9007199254740993L)
    private suspend fun enqueue(name: String = "Local", deleted: Boolean = false): String = coordinator().saveLocalAndEnqueue(
        account, "categories", "stable-record", fields(name), deleted) {}
    @After fun tearDown() { db.close(); context.deleteDatabase(databaseName) }

    @Test fun restartRecoveredSnapshotAndCommittedLocalWriteTogether() = runBlocking {
        coordinator().saveLocalAndEnqueue(account, "categories", "stable-record", fields("Offline")) {
            db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Offline"))
        }
        val before = db.syncDao().pending(account.uid).single()
        db.close()
        db = open()
        assertEquals(before, db.syncDao().pending(account.uid).single())
        assertEquals("Offline", db.categoryDao().getForUser(account.localUserId).single().name)
        assertEquals(SyncRun.COMPLETE, coordinator().retryPending(account))
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertEquals(1L, cloud.revision)
        assertEquals(9007199254740993L, SyncMapping.decode("categories", requireNotNull(cloud.record).payload)["maximumCents"])
    }

    @Test fun failedTransactionRolledBackLocalRowAndQueue() = runBlocking {
        try {
            coordinator().saveLocalAndEnqueue(account, "categories", "stable-record", fields("Rollback")) {
                db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Rollback"))
                error("Synthetic rollback")
            }
            fail("Expected rollback")
        } catch (_: IllegalStateException) { }
        assertTrue(db.categoryDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertTrue(db.syncDao().records(account.uid).isEmpty())
        assertEquals(0, scheduled)
    }

    @Test fun queueConstraintFailureAlsoRolledBackLocalWrite() = runBlocking {
        val id = enqueue()
        val original = db.syncDao().pending(account.uid).single()
        try {
            db.withTransaction {
                db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Must roll back"))
                db.syncDao().insert(original.copy(sequence = 0))
            }
            fail("Duplicate mutation must fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(db.categoryDao().getForUser(account.localUserId).isEmpty())
        assertEquals(id, db.syncDao().pending(account.uid).single().mutationId)
    }

    @Test fun serverSuccessThenLostAcknowledgementReplayedAfterRestartWithoutRegression() = runBlocking {
        enqueue()
        cloud.afterCommit = { throw FirebaseNetworkException("Synthetic lost response") }
        assertEquals(SyncRun.RETRY, coordinator().retryPending(account))
        assertEquals(1L, cloud.revision)
        assertEquals(1, db.syncDao().pending(account.uid).size)
        cloud.afterCommit = null
        cloud.record = requireNotNull(cloud.record).copy(revision = 2, payload = SyncMapping.encode("categories", fields("Other device")), lastMutationId = "other")
        db.close(); db = open()
        coordinator().retryPending(account)
        assertEquals(2L, cloud.revision)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        coordinator().refreshFromServer(account, "categories")
        assertEquals(2L, db.syncDao().record(account.uid, "categories", "stable-record")?.revision)
    }

    @Test fun acknowledgementKeptEditMadeWhileUploadWasInFlight() = runBlocking {
        enqueue("First")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        cloud.beforeSend = { entered.complete(Unit); release.await() }
        val sync = coordinator()
        val request = async { sync.retryPending(account) }
        entered.await()
        val second = enqueue("Second")
        assertNull(db.syncDao().pending(account.uid).last().baseRevision)
        cloud.beforeSend = null
        cloud.afterCommit = { active = null }
        release.complete(Unit)
        request.await()
        // No local acknowledgement after detachment, so restart must recover both snapshots.
        assertEquals(2, db.syncDao().pending(account.uid).size)
        active = account
        cloud.afterCommit = null
        cloud.failureOnSecond = FirebaseNetworkException("Synthetic second request offline")
        sync.retryPending(account)
        val remaining = db.syncDao().pending(account.uid).single()
        assertEquals(second, remaining.mutationId)
        assertEquals(1L, remaining.baseRevision)
        assertEquals("Second", SyncMapping.decode("categories", remaining.payload)["name"])
    }

    @Test fun concurrentRevisionKeptLatestLocalEditForExplicitResolution() = runBlocking {
        enqueue("First"); enqueue("Latest local")
        cloud.record = SyncRecord(account.uid, account.localUserId, "categories", "stable-record", 1,
            SyncMapping.encode("categories", fields("Remote")))
        val sync = coordinator()
        sync.retryPending(account)
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).first().state)
        assertEquals(2, db.syncDao().pending(account.uid).size)
        sync.resolveConflict(account, "categories", "stable-record", true, 1) { fail("Keeping local must not apply server") }
        val resolved = db.syncDao().pending(account.uid).single()
        assertEquals(1L, resolved.baseRevision)
        assertEquals("Latest local", SyncMapping.decode("categories", resolved.payload)["name"])
        sync.retryPending(account)
        assertEquals(2L, cloud.revision)
    }

    @Test fun deletionPreventedResurrectionAndServerChoiceAppliedAtomically() = runBlocking {
        enqueue()
        cloud.record = SyncRecord(account.uid, account.localUserId, "categories", "stable-record", 1,
            SyncMapping.encode("categories", fields("Deleted remotely")), true)
        val sync = coordinator()
        sync.retryPending(account)
        try { sync.resolveConflict(account, "categories", "stable-record", true, 1) {}; fail("Cannot restore tombstone") }
        catch (_: IllegalStateException) { }
        assertEquals(1, db.syncDao().pending(account.uid).size)
        try { sync.resolveConflict(account, "categories", "stable-record", false, 1) { error("Synthetic apply failure") }; fail("Expected rollback") }
        catch (_: IllegalStateException) { }
        assertEquals(1, db.syncDao().pending(account.uid).size)
        var deletedApplied = false
        sync.resolveConflict(account, "categories", "stable-record", false, 1) { deletedApplied = it.isDeleted }
        assertTrue(deletedApplied)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        try { enqueue(); fail("Tombstone must block a new local edit") } catch (_: IllegalStateException) { }
    }

    @Test fun downloadPreservedPendingEditAndNeverAppliedItToFinancialRows() = runBlocking {
        enqueue("Pending")
        cloud.record = SyncRecord(account.uid, account.localUserId, "categories", "stable-record", 2,
            SyncMapping.encode("categories", fields("Downloaded")))
        val sync = coordinator()
        sync.refreshFromServer(account, "categories")
        assertEquals("Pending", SyncMapping.decode("categories", db.syncDao().pending(account.uid).single().payload)["name"])
        assertFalse(sync.applyDownloaded(account, "categories", "stable-record") { fail("Pending edit must block application") })
        assertEquals(2L, db.syncDao().record(account.uid, "categories", "stable-record")?.revision)
        sync.retryPending(account)
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).single().state)
    }

    @Test fun logoutAndNewGenerationRejectedLateDownloadAndSendAcknowledgement() = runBlocking {
        enqueue()
        cloud.afterCommit = { active = account.copy(sessionGeneration = 2) }
        val sync = coordinator()
        assertEquals(SyncRun.DETACHED, sync.retryPending(account))
        assertEquals(1, db.syncDao().pending(account.uid).size)
        assertEquals(0L, db.syncDao().record(account.uid, "categories", "stable-record")?.revision)
        active = account
        cloud.afterDownload = { active = SyncAccount(db.userDao().createCloudUser("other-owner", "Other"), "other-owner", 3) }
        try { sync.refreshFromServer(account, "categories"); fail("Late download must detach") } catch (_: IllegalStateException) { }
        assertEquals(0L, db.syncDao().record(account.uid, "categories", "stable-record")?.revision)
        assertTrue(db.syncDao().pending("other-owner").isEmpty())
    }

    @Test fun permissionFailureStayedActionableAndDidNotBlockOtherRecords() = runBlocking {
        enqueue()
        coordinator().saveLocalAndEnqueue(account, "categories", "other-record", fields("Independent")) {}
        cloud.failureForId = "stable-record"
        val sync = coordinator()
        assertEquals(SyncRun.COMPLETE, sync.retryPending(account))
        val failed = db.syncDao().pending(account.uid).single()
        assertEquals("FAILED", failed.state)
        assertTrue(requireNotNull(failed.lastError).contains("rules"))
        assertEquals("other-record", cloud.record?.recordId)
        cloud.failureForId = null
        sync.retryFailures(account)
        assertEquals("PENDING", db.syncDao().pending(account.uid).single().state)
    }

    @Test fun changedServerRevisionRequiredAnotherExplicitReview() = runBlocking {
        enqueue("Keep for review")
        cloud.record = SyncRecord(account.uid, account.localUserId, "categories", "stable-record", 1,
            SyncMapping.encode("categories", fields("First remote")))
        val sync = coordinator()
        sync.retryPending(account)
        cloud.record = requireNotNull(cloud.record).copy(revision = 2, payload = SyncMapping.encode("categories", fields("Unseen remote")))
        sync.refreshFromServer(account, "categories")
        try { sync.resolveConflict(account, "categories", "stable-record", true, 1) {}; fail("Must review the newer version") }
        catch (_: IllegalStateException) { }
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).single().state)
        sync.resolveConflict(account, "categories", "stable-record", true, 2) {}
        assertEquals(2L, db.syncDao().pending(account.uid).single().baseRevision)
    }

    @Test fun accountChangeInsideLocalWriteRolledBackRowAndPendingSnapshot() = runBlocking {
        try {
            coordinator().saveLocalAndEnqueue(account, "categories", "stable-record", fields("Interrupted")) {
                db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Interrupted"))
                active = account.copy(sessionGeneration = 2)
            }
            fail("Changed session must roll back")
        } catch (_: IllegalStateException) { }
        assertTrue(db.categoryDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertEquals(0, scheduled)
    }

    @Test fun olderDownloadCouldNotRegressADeletionAlreadyReceived() = runBlocking {
        cloud.record = SyncRecord(account.uid, account.localUserId, "categories", "stable-record", 2,
            SyncMapping.encode("categories", fields("Archived")), true)
        val sync = coordinator()
        sync.refreshFromServer(account, "categories")
        cloud.record = requireNotNull(cloud.record).copy(revision = 1, isDeleted = false)
        sync.refreshFromServer(account, "categories")
        val retained = requireNotNull(db.syncDao().record(account.uid, "categories", "stable-record"))
        assertEquals(2L, retained.revision)
        assertTrue(retained.isDeleted)
    }

    @Test fun payloadWithDeviceOnlyFieldsWasRejectedBeforeAnyLocalWrite() = runBlocking {
        try {
            coordinator().saveLocalAndEnqueue(account, "categories", "stable-record", fields("Unsafe") + ("password" to "synthetic")) {
                fail("Invalid payload cannot reach local mutation")
            }
            fail("Extra fields must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertTrue(db.syncDao().records(account.uid).isEmpty())
    }
}

private class FixtureSyncCloud : SyncCloud {
    var record: SyncRecord? = null
    val revision get() = record?.revision ?: 0L
    private val receipts = mutableMapOf<String, Long>()
    var beforeSend: (suspend () -> Unit)? = null
    var afterCommit: (suspend () -> Unit)? = null
    var afterDownload: (suspend () -> Unit)? = null
    var failureOnSecond: Exception? = null
    var failureForId: String? = null
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
        beforeSend?.invoke()
        check(stillCurrent())
        if (operation.recordId == failureForId) throw FirebaseFirestoreException("Synthetic denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        receipts[operation.mutationId]?.let { return SendResult.Accepted(it) }
        if (operation.baseRevision == 1L) failureOnSecond?.let { throw it }
        val old = record?.takeIf { it.recordId == operation.recordId }
        if ((old?.revision ?: 0) != operation.baseRevision || old?.isDeleted == true) return SendResult.Conflict(old)
        val next = requireNotNull(operation.baseRevision) + 1
        record = SyncRecord(operation.uid, operation.userId, operation.recordType, operation.recordId, next,
            operation.payload, operation.isDeleted, lastMutationId = operation.mutationId)
        receipts[operation.mutationId] = next
        afterCommit?.invoke()
        return SendResult.Accepted(next)
    }
    override suspend fun download(account: SyncAccount, type: String): List<SyncRecord> {
        val snapshot = listOfNotNull(record)
        afterDownload?.invoke()
        return snapshot
    }
}
