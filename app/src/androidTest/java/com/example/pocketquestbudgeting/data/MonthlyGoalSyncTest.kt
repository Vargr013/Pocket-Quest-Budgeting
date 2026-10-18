package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MonthlyGoalSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "goal-recovery-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private lateinit var account: SyncAccount
    private lateinit var sync: PocketQuestSyncCoordinator
    private lateinit var goals: MonthlyGoalSync
    private val cloud = GoalMemoryCloud()
    private var attached = true
    @Before fun start() = runBlocking {
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        account = SyncAccount(db.userDao().createCloudUser("goal-owner", "Synthetic"), "goal-owner", 1)
        connect()
    }
    private fun connect() {
        sync = PocketQuestSyncCoordinator(db, cloud, { attached && it == account }, {})
        goals = MonthlyGoalSync(db, sync)
    }
    @After fun stop() { db.close(); context.deleteDatabase(name) }
    private suspend fun row(month: String = "2026-10") = db.budgetGoalDao().getIncludingDeletedForMonth(account.localUserId, "$month-01", "$month-31")
    private suspend fun save(min: Long = 123, max: Long = 9007199254740993L, month: String = "2026-10") =
        goals.saveForMonth(account, "$month-01", "$month-31", min, max, row(month)) { true }
    private fun legacy(min: Long = 123, max: Long = 456) = BudgetGoalEntity(userId = account.localUserId,
        minimumAmount = min, maximumAmount = max, startDate = "2026-10-01", endDate = "2026-10-31")

    @Test fun firstAndRepeatedSavesUsedOneRowAndExactLimitsWithOtherMonthsAndOwners() = runBlocking {
        assertNull(row()); assertTrue(save()); val first = requireNotNull(row())
        assertTrue(save(9007199254740993L, Long.MAX_VALUE)); assertEquals(first.id, row()?.id)
        assertTrue(save(0, 0, "2026-12")); assertTrue(save(0, 0, "2027-01"))
        assertEquals(3, db.budgetGoalDao().getForUser(account.localUserId).size)
        sync.retryPending(account)
        val fields = SyncMapping.decode("monthlyGoals", cloud.rows.getValue("2026-10").payload)
        assertEquals(9007199254740993L, fields["minimumCents"]); assertEquals(Long.MAX_VALUE, fields["maximumCents"])
        val original = account
        account = SyncAccount(db.userDao().createCloudUser("other-goal-owner", "Other"), "other-goal-owner", 2)
        assertTrue(save(0, 0)); assertEquals("2026-10", row()?.monthKey)
        assertNotEquals(first.id, row()?.id)
        assertEquals(Long.MAX_VALUE, db.budgetGoalDao().getForMonth(original.localUserId, "2026-10-01", "2026-10-31")?.maximumAmount)
    }

    @Test fun repeatImportAndOfflineDiskReopenKeptIdentityMutationAndLegacyDuplicates() = runBlocking {
        val older = db.budgetGoalDao().insert(legacy(1, 2))
        val newest = db.budgetGoalDao().insert(legacy(3, 4))
        assertFalse(requireNotNull(db.userDao().getById(account.localUserId)).importPending)
        assertEquals(0, goals.importExisting(account))
        val queued = db.syncDao().pending(account.uid).single()
        db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, name).build(); connect()
        repeat(2) { assertEquals(0, goals.importExisting(account)) }
        assertEquals(queued, db.syncDao().pending(account.uid).single())
        assertEquals(newest, row()?.id); assertEquals(3L, row()?.minimumAmount)
        val history = db.budgetGoalDao().getForUser(account.localUserId).first { it.id == older }
        assertNull(history.monthKey); assertEquals(1L, history.minimumAmount)
        sync.retryPending(account); goals.refresh(account)
        assertEquals(2, db.budgetGoalDao().getForUser(account.localUserId).size)
        assertEquals(1, cloud.rows.size)
    }

    @Test fun invalidEffectiveLegacyValuesAndRangesStayedAvailableAndBlockedImportCompletion() = runBlocking {
        db.budgetGoalDao().insert(legacy())
        db.budgetGoalDao().insert(legacy(-1, 2))
        db.budgetGoalDao().insert(legacy().copy(startDate = "2026-09-02", endDate = "2026-09-30"))
        db.budgetGoalDao().insert(legacy().copy(startDate = "2026-12-01", endDate = "2026-12-31"))
        assertEquals(2, goals.importExisting(account))
        assertEquals(listOf("2026-12"), db.syncDao().pending(account.uid).map { it.recordId })
        assertEquals(4, db.budgetGoalDao().getForUser(account.localUserId).size)
        sync.retryPending(account); assertFalse(goals.importReady(account))
    }

    @Test fun atomicRollbackKeptRowMappingAndQueueTogether() = runBlocking {
        try { db.withTransaction { save(); error("Synthetic interruption") }; fail("No rollback") }
        catch (_: IllegalStateException) { }
        assertNull(row()); assertTrue(db.syncDao().pending(account.uid).isEmpty()); assertTrue(db.syncDao().records(account.uid).isEmpty())
    }

    @Test fun refreshUsedOneRowAndDidNotOverwriteFailedOrPendingDrafts() = runBlocking {
        save(); sync.retryPending(account); val first = requireNotNull(row())
        cloud.rows["2026-10"] = cloud.rows.getValue("2026-10").copy(revision = 2,
            payload = SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(first.copy(minimumAmount = 77))))
        repeat(2) { goals.refresh(account) }; assertEquals(first.id, row()?.id); assertEquals(77L, row()?.minimumAmount)
        save(88, 99)
        val queued = db.syncDao().pending(account.uid).single()
        db.syncDao().mark(account.uid, queued.mutationId, "FAILED", "Synthetic failure")
        goals.refresh(account); assertEquals(88L, row()?.minimumAmount)
        assertEquals(1, db.budgetGoalDao().getForUser(account.localUserId).size)
    }

    @Test fun importCompletionWaitedForEveryGoalAcknowledgementAndUnresolvedWork() = runBlocking {
        db.openHelper.writableDatabase.execSQL("UPDATE users SET importPending = 1 WHERE id = ?", arrayOf(account.localUserId))
        db.budgetGoalDao().insert(legacy()); goals.importExisting(account)
        val expenses = ExpenseSync(db, sync)
        expenses.finishRecordImport(account); assertTrue(requireNotNull(db.userDao().getById(account.localUserId)).importPending)
        sync.retryPending(account); save(1, 2)
        expenses.finishRecordImport(account); assertTrue(requireNotNull(db.userDao().getById(account.localUserId)).importPending)
        sync.retryPending(account); expenses.finishRecordImport(account)
        assertFalse(requireNotNull(db.userDao().getById(account.localUserId)).importPending)
    }

    @Test fun lostAcknowledgementReplayedSameMutationAfterDiskReopen() = runBlocking {
        save(); val queued = db.syncDao().pending(account.uid).single()
        cloud.loseReply = true; assertEquals(SyncRun.RETRY, sync.retryPending(account))
        db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, name).build(); connect()
        assertEquals(queued.mutationId, db.syncDao().pending(account.uid).single().mutationId)
        goals.importExisting(account); sync.retryPending(account); goals.refresh(account)
        assertEquals(1L, cloud.rows.getValue("2026-10").revision)
        assertEquals(1, db.budgetGoalDao().getForUser(account.localUserId).size)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun inFlightAcknowledgementKeptLaterSnapshotAndRebasedIt() = runBlocking {
        save(1, 2)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        cloud.before = { entered.complete(Unit); release.await() }
        val request = async { sync.retryPending(account) }; entered.await()
        save(3, 4); val later = db.syncDao().pending(account.uid).last()
        assertNull(later.baseRevision)
        cloud.before = null; cloud.failSecond = true; release.complete(Unit); request.await()
        val remaining = db.syncDao().pending(account.uid).single()
        assertEquals(later.mutationId, remaining.mutationId); assertEquals(1L, remaining.baseRevision)
        assertEquals(3L, row()?.minimumAmount)
        cloud.failSecond = false; sync.retryPending(account); goals.refresh(account)
        assertEquals(3L, row()?.minimumAmount); assertEquals(2L, cloud.rows.getValue("2026-10").revision)
    }

    @Test fun existingCloudMonthAndUnmappedLocalMonthRequiredExplicitServerReview() = runBlocking {
        val local = legacy(3, 4); db.budgetGoalDao().insert(local)
        cloud.rows["2026-10"] = SyncRecord(account.uid, account.localUserId, "monthlyGoals", "2026-10", 5,
            SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(local.copy(minimumAmount = 1, maximumAmount = 2))))
        // I covered a downloaded metadata record too; an unmapped import must still start at revision zero.
        sync.refreshFromServer(account, "monthlyGoals"); goals.importExisting(account)
        assertEquals(0L, db.syncDao().pending(account.uid).single().baseRevision)
        sync.retryPending(account); goals.refresh(account)
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).single().state); assertEquals(3L, row()?.minimumAmount)
        sync.resolveConflict(account, "monthlyGoals", "2026-10", false, 5) { goals.applyServer(it) }
        assertEquals(1L, row()?.minimumAmount); assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertEquals(1, db.budgetGoalDao().getForUser(account.localUserId).size)
    }

    @Test fun tombstoneDidNotRevealAnOlderDuplicateOrAllowRestoration() = runBlocking {
        db.budgetGoalDao().insert(legacy()); db.budgetGoalDao().insert(legacy(2, 3))
        goals.importExisting(account); sync.retryPending(account)
        cloud.rows["2026-10"] = cloud.rows.getValue("2026-10").copy(revision = 2, isDeleted = true)
        goals.refresh(account)
        assertNull(db.budgetGoalDao().getForMonth(account.localUserId, "2026-10-01", "2026-10-31"))
        assertEquals(2, db.budgetGoalDao().getForUser(account.localUserId).size)
        try { save(); fail("Restored tombstone") } catch (_: IllegalStateException) { }
    }

    @Test fun staleMonthDraftAndDetachedOwnerCouldNotWrite() = runBlocking {
        assertFalse(goals.saveForMonth(account, "2026-10-01", "2026-10-31", 1, 2, null) { false })
        attached = false
        try { save(); fail("Detached save") } catch (_: IllegalStateException) { }
        assertNull(row()); assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun monthAndSessionWereRecheckedAfterWaitingForTransaction() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val lock = async { db.withTransaction { entered.complete(Unit); release.await() } }; entered.await()
        var sameMonth = true
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { goals.saveForMonth(account, "2026-10-01", "2026-10-31", 1, 2, null) { sameMonth } }
        sameMonth = false; release.complete(Unit); lock.await(); assertFalse(waiting.await())
        assertNull(row())
        val held = CompletableDeferred<Unit>(); val unlock = CompletableDeferred<Unit>()
        val secondLock = async { db.withTransaction { held.complete(Unit); unlock.await() } }; held.await()
        val saving = async(start = CoroutineStart.UNDISPATCHED) { try { save(); false } catch (_: IllegalStateException) { true } }
        attached = false; unlock.complete(Unit); secondLock.await(); assertTrue(saving.await())
        assertNull(row())
    }

    @Test fun openEditorDraftCouldNotOverwriteAnAcceptedDownload() = runBlocking {
        save(); sync.retryPending(account); val reviewed = row()
        cloud.rows["2026-10"] = cloud.rows.getValue("2026-10").copy(revision = 2,
            payload = SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(requireNotNull(reviewed).copy(minimumAmount = 2))))
        goals.refresh(account)
        try { goals.saveForMonth(account, "2026-10-01", "2026-10-31", 3, 4, reviewed) { true }; fail("Stale editor saved") }
        catch (_: IllegalStateException) { }
        assertEquals(2L, row()?.minimumAmount); assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun emptyRefreshDidNotSeedAZeroGoal() = runBlocking {
        repeat(2) { goals.refresh(account) }
        assertTrue(db.budgetGoalDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        save(0, 0); sync.retryPending(account); goals.refresh(account)
        assertEquals(0L, row()?.minimumAmount); assertEquals(0L, row()?.maximumAmount)
    }

    @Test fun unmappedMonthAgainstKnownServerTombstoneEnteredReviewAndCouldOnlyAcceptServer() = runBlocking {
        val local = legacy(); db.budgetGoalDao().insert(local)
        cloud.rows["2026-10"] = SyncRecord(account.uid, account.localUserId, "monthlyGoals", "2026-10", 2,
            SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(local)), isDeleted = true)
        sync.refreshFromServer(account, "monthlyGoals"); goals.importExisting(account); sync.retryPending(account)
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).single().state)
        try { sync.resolveConflict(account, "monthlyGoals", "2026-10", true, 2) {}; fail("Restored month") }
        catch (_: IllegalStateException) { }
        sync.resolveConflict(account, "monthlyGoals", "2026-10", false, 2) { goals.applyServer(it) }
        assertTrue(requireNotNull(row()).isDeleted)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun invalidLocalMonthWasNotOverwrittenAndDidNotBlockOtherDownloads() = runBlocking {
        val invalid = legacy(-1, 2); db.budgetGoalDao().insert(invalid)
        val valid = legacy().copy(minimumAmount = 5, maximumAmount = 6)
        cloud.rows["2026-10"] = SyncRecord(account.uid, account.localUserId, "monthlyGoals", "2026-10", 1,
            SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(valid)))
        cloud.rows["2026-12"] = cloud.rows.getValue("2026-10").copy(recordId = "2026-12",
            payload = SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(valid.copy(startDate = "2026-12-01", endDate = "2026-12-31"))))
        assertEquals(1, goals.importExisting(account)); goals.refresh(account)
        assertEquals(-1L, row()?.minimumAmount); assertNull(row()?.monthKey)
        assertEquals(5L, row("2026-12")?.minimumAmount)
        assertFalse(goals.importReady(account))
        save(7, 8); sync.retryPending(account)
        assertEquals("CONFLICT", db.syncDao().pending(account.uid).single().state)
    }

    @Test fun failedServerAcceptanceRolledBackGoalApplicationAndQueueResolutionTogether() = runBlocking {
        save(3, 4)
        cloud.rows["2026-10"] = SyncRecord(account.uid, account.localUserId, "monthlyGoals", "2026-10", 1,
            SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(legacy(1, 2))))
        sync.retryPending(account)
        val before = db.syncDao().pending(account.uid).single()
        try {
            sync.resolveConflict(account, "monthlyGoals", "2026-10", false, 1) {
                goals.applyServer(it); error("Synthetic interruption")
            }; fail("Expected rollback")
        } catch (_: IllegalStateException) { }
        assertEquals(3L, row()?.minimumAmount); assertEquals(before, db.syncDao().pending(account.uid).single())
        sync.resolveConflict(account, "monthlyGoals", "2026-10", false, 1) { goals.applyServer(it) }
        assertEquals(1L, row()?.minimumAmount); assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun capturedOwnerWasRecheckedWhenDownloadApplicationWaitedForRoom() = runBlocking {
        cloud.rows["2026-10"] = SyncRecord(account.uid, account.localUserId, "monthlyGoals", "2026-10", 1,
            SyncMapping.encode("monthlyGoals", MonthlyGoalMapping.fields(legacy())))
        sync.refreshFromServer(account, "monthlyGoals")
        val held = CompletableDeferred<Unit>(); val unlock = CompletableDeferred<Unit>()
        val lock = async { db.withTransaction { held.complete(Unit); unlock.await() } }; held.await()
        val applying = async(start = CoroutineStart.UNDISPATCHED) {
            try { sync.applyDownloaded(account, "monthlyGoals", "2026-10") { goals.applyServer(it) }; false }
            catch (_: IllegalStateException) { true }
        }
        attached = false; unlock.complete(Unit); lock.await(); assertTrue(applying.await())
        assertNull(row()); assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }

    @Test fun invalidMonthIdentityAndFractionalPayloadCouldNotEnterSync() = runBlocking {
        for (id in listOf("2026-1", "0000-01", "2026-13")) {
            try { SyncMapping.checkKey("monthlyGoals", id); fail("Invalid identity") } catch (_: IllegalArgumentException) { }
        }
        try { sync.saveLocalAndEnqueue(account, "monthlyGoals", "2026-11", MonthlyGoalMapping.fields(legacy())) {}; fail("Mismatched identity") }
        catch (_: IllegalArgumentException) { }
        try { SyncMapping.decode("monthlyGoals", """{"minimumCents":0.5,"maximumCents":1,"startDate":"2026-10-01","endDate":"2026-10-31"}"""); fail("Fraction accepted") }
        catch (_: Exception) { }
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }
}

private class GoalMemoryCloud : SyncCloud {
    val rows = mutableMapOf<String, SyncRecord>()
    private val receipts = mutableMapOf<String, Long>()
    var loseReply = false
    var failSecond = false
    var before: (suspend () -> Unit)? = null
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
        before?.invoke()
        receipts[operation.mutationId]?.let { return SendResult.Accepted(it) }
        val base = requireNotNull(operation.baseRevision)
        if (failSecond && base > 0) throw FirebaseNetworkException("Synthetic offline later edit")
        val remote = rows[operation.recordId]
        if ((remote?.revision ?: 0) != base || remote?.isDeleted == true) return SendResult.Conflict(remote?.copy(userId = operation.userId))
        rows[operation.recordId] = SyncRecord(operation.uid, operation.userId, operation.recordType, operation.recordId,
            base + 1, operation.payload, operation.isDeleted)
        receipts[operation.mutationId] = base + 1
        if (loseReply) { loseReply = false; throw FirebaseNetworkException("Synthetic lost response") }
        return SendResult.Accepted(base + 1)
    }
    override suspend fun download(account: SyncAccount, type: String) = rows.values.filter { it.uid == account.uid }
        .map { it.copy(userId = account.localUserId) }
}
