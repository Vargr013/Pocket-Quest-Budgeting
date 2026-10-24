package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsFirebaseTest {
    private lateinit var first: Client
    private lateinit var second: Client
    companion object {
        private val apps = mutableListOf<FirebaseApp>()
        private val stores = mutableListOf<FirebaseFirestore>()
        private val auth = mutableListOf<FirebaseAuth>()
        private val context get() = ApplicationProvider.getApplicationContext<Context>()
        @BeforeClass @JvmStatic fun connect() {
            repeat(2) { index ->
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-pocket-quest")
                    .setApplicationId("1:123456789012:android:1234567890abcdef123456")
                    .setApiKey("AIzaSySavingsFixture${index}NotARealKey000").build(), "savings-$index-${UUID.randomUUID()}")
                apps += app
                auth += FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2", 9099) }
                stores += FirebaseFirestore.getInstance(app).also {
                    it.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                    it.useEmulator("10.0.2.2", 8080)
                }
            }
        }
        @AfterClass @JvmStatic fun disconnect() = runBlocking {
            auth.forEach { it.signOut() }; stores.forEach { it.terminate().awaitSync() }; apps.forEach { it.delete() }
        }
        private fun open(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
    }

    private class Client(val name: String, val db: AppDatabase, val account: SyncAccount, val cloud: FirestoreSyncCloud) {
        var attached = true
        var loseReply = false
        val sync = PocketQuestSyncCoordinator(db, object : SyncCloud {
            override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
                val result = cloud.send(operation, stillCurrent)
                if (loseReply && operation.recordType == SavingsMapping.CONTRIBUTIONS) { loseReply = false; throw FirebaseNetworkException("Synthetic lost contribution reply") }
                return result
            }
            override suspend fun download(account: SyncAccount, type: String) = cloud.download(account, type)
        }, { attached && it == account }, {})
        val savings = SavingsSync(db, sync)
        suspend fun row() = db.savingsDao().getForUser(account.localUserId, true).single()
        suspend fun balance() = db.savingsDao().savedCents(account.localUserId, row().id)
        suspend fun refresh() = savings.refresh(account)
        suspend fun flush() {
            repeat(4) {
                val result = sync.retryPending(account)
                assertNotEquals(SyncRun.DETACHED, result)
                if (result != SyncRun.RETRY) return
            }
            fail("Pending work should have finished or requested review")
        }
    }

    @Before fun start() = runBlocking {
        val email = "savings-${UUID.randomUUID()}@example.test"
        auth[0].createUserWithEmailAndPassword(email, "Synthetic-emulator-only!").awaitSync()
        auth[1].signInWithEmailAndPassword(email, "Synthetic-emulator-only!").awaitSync()
        val uid = requireNotNull(auth[0].currentUser).uid
        fun client(index: Int): Client {
            val name = "savings-cloud-${UUID.randomUUID()}"
            val db = open(name)
            return Client(name, db, SyncAccount(1, uid, 1), FirestoreSyncCloud(stores[index]))
        }
        first = client(0); second = client(1)
        first.db.userDao().createCloudUser(uid, "Synthetic")
        val unrelated = second.db.userDao().createCloudUser("unrelated", "Synthetic other")
        second.db.savingsDao().createGoal(goal(unrelated))
        val secondId = second.db.userDao().createCloudUser(uid, "Synthetic")
        second = Client(second.name, second.db, SyncAccount(secondId, uid, 1), second.cloud)
    }
    @After fun finish() {
        first.db.close(); second.db.close(); context.deleteDatabase(first.name); context.deleteDatabase(second.name)
    }
    private fun goal(owner: Long = first.account.localUserId, key: String = UUID.randomUUID().toString()) = SavingsGoalEntity(
        userId = owner, cloudId = key, name = "Synthetic goal", targetCents = Long.MAX_VALUE, targetDate = "2028-02-29",
    )
    private suspend fun create(cents: Long = 0) {
        first.savings.createGoal(first.account, goal(), cents, if (cents > 0) "2024-02-29" else null)
        first.flush(); second.refresh()
    }
    private suspend fun event(client: Client, cents: Long = 123, key: String = UUID.randomUUID().toString()) = SavingsContributionEntity(
        userId = client.account.localUserId, goalId = client.row().id, businessId = key,
        kind = SavingsChecks.ADDITION, amountCents = cents, effectiveDate = "2024-02-29",
    )
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected rejected operation") } catch (_: IllegalStateException) { } catch (_: IllegalArgumentException) { }
    }

    @Test fun evaluatedGamificationBadgesReachTheOtherDeviceThroughExistingAwardRules() = runBlocking {
        val owner = first.account.localUserId
        val category = first.db.categoryDao().insert(CategoryEntity(userId = owner, name = "Synthetic food"))
        first.db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = 50_00,
            date = "2026-09-10", startTime = "09:00", endTime = "09:30", description = "Synthetic"))
        for (day in listOf("2026-10-18", "2026-10-19", "2026-10-20")) first.db.gamificationDao().markActiveDay(owner, day)
        val firstReport = AchievementLoader.load(first.db, owner, today = "2026-10-20", nowMillis = 42L)
        assertEquals(setOf("first_expense", "streak_3"), firstReport.newlyEarned.toSet())
        first.savings.importExisting(first.account)
        first.flush()
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
        second.refresh()
        val secondReport = AchievementLoader.load(second.db, second.account.localUserId, today = "2026-10-20", nowMillis = 500L)
        assertTrue(secondReport.newlyEarned.isEmpty())
        assertEquals(setOf("first_expense", "streak_3"), secondReport.progress.filter { it.earned }.map { it.definition.id }.toSet())
        assertTrue(second.db.earnedAchievementDao().getForUser(second.account.localUserId).all { it.earnedAtEpochMillis == 42L })
    }

    @Test fun updateAndAdditionCommitTogetherAndAnExactRetryDoesNotAppendAgain() = runBlocking {
        create(25000)
        val reviewed = first.row()
        val addition = event(first, 10000)
        repeat(2) { first.savings.updateGoalWithContribution(first.account, reviewed, "Updated goal", 200000, null, "Retained note", addition) }
        assertEquals(35000L, first.balance())
        try {
            first.savings.updateGoalWithContribution(first.account, reviewed, "Different retry", 300000, null, null, addition)
            fail("Changed metadata reused a saved intent")
        } catch (_: IllegalStateException) { }
        assertEquals("Updated goal", first.row().name)
        assertEquals(2, first.db.savingsDao().contributions(first.account.localUserId, reviewed.id).size)
        first.flush(); second.refresh()
        assertEquals(35000L, second.balance()); assertEquals("Updated goal", second.row().name)
    }

    @Test fun failingAdditionRollsBackTheMetadataAndQueuedEdit() = runBlocking {
        create(Long.MAX_VALUE)
        val reviewed = first.row()
        try {
            first.savings.updateGoalWithContribution(first.account, reviewed, "Must roll back", reviewed.targetCents, null, null, event(first, 1))
            fail("Overflow accepted")
        } catch (_: ArithmeticException) { }
        assertEquals(reviewed, first.row())
        assertEquals(Long.MAX_VALUE, first.balance())
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }

    @Test fun independentlyEvaluatedBadgeEvidenceStaysLocalUntilReviewedServerAcceptance() = runBlocking {
        suspend fun evaluate(client: Client, now: Long): AchievementReport {
            val owner = client.account.localUserId
            val category = client.db.categoryDao().insert(CategoryEntity(userId = owner, name = "Synthetic food"))
            client.db.expenseDao().insert(ExpenseEntity(userId = owner, categoryId = category, amount = 100,
                date = "2026-09-10", startTime = "09:00", endTime = "09:30", description = "Synthetic"))
            return AchievementLoader.load(client.db, owner, today = "2026-10-20", nowMillis = now)
        }
        evaluate(first, 42); evaluate(second, 99)
        first.savings.importExisting(first.account); first.flush()
        second.savings.importExisting(second.account); second.flush(); second.refresh()
        assertEquals(99L, second.db.earnedAchievementDao().getForUser(second.account.localUserId).single().earnedAtEpochMillis)
        val issue = second.db.syncDao().pending(second.account.uid).single()
        assertEquals("CONFLICT", issue.state)
        val remote = second.db.syncDao().record(second.account.uid, issue.recordType, issue.recordId)!!
        second.sync.resolveConflict(second.account, issue.recordType, issue.recordId, false, remote.revision) { second.savings.applyServer(it) }
        val report = AchievementLoader.load(second.db, second.account.localUserId, today = "2026-10-20", nowMillis = 500)
        assertEquals(42L, report.progress.first { it.definition.id == "first_expense" }.earnedAtMillis)
        assertTrue(report.newlyEarned.isEmpty())
    }

    @Test fun twoNamedClientsUseStableGoalIdentityWithDifferentLocalIdsAndExactOpening() = runBlocking {
        create(9007199254740993L)
        repeat(2) { second.refresh() }
        assertNotEquals(first.row().id, second.row().id)
        assertNotEquals(first.row().userId, second.row().userId)
        assertEquals(first.row().cloudId, second.row().cloudId)
        assertEquals(9007199254740993L, second.balance())
        second.savings.recordContribution(second.account, event(second, 1)); second.flush(); first.refresh()
        assertEquals(9007199254740994L, first.balance())
        assertEquals(2, first.db.savingsDao().contributions(first.account.localUserId, first.row().id).size)
    }

    @Test fun independentConcurrentDeviceAdditionsBothSurvive() = runBlocking {
        create(10)
        first.savings.recordContribution(first.account, event(first, 101))
        second.savings.recordContribution(second.account, event(second, 202))
        coroutineScope { val a = async { first.flush() }; val b = async { second.flush() }; a.await(); b.await() }
        first.refresh(); second.refresh()
        assertEquals(313L, first.balance()); assertEquals(313L, second.balance())
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
    }

    @Test fun lostReplyDiskRestartAndNewTransportMutationDoNotDuplicateTheBusinessEvent() = runBlocking {
        create(); val row = event(first, 777)
        first.savings.recordContribution(first.account, row); first.loseReply = true
        assertEquals(SyncRun.RETRY, first.sync.retryPending(first.account))
        val queued = first.db.syncDao().pending(first.account.uid).single()
        first.db.close(); first = Client(first.name, open(first.name), first.account, first.cloud)
        assertEquals(queued.mutationId, first.db.syncDao().pending(first.account.uid).single().mutationId)
        assertEquals(SendResult.Accepted(1), first.cloud.send(queued.copy(mutationId = UUID.randomUUID().toString())) { true })
        first.flush(); second.refresh()
        assertEquals(777L, second.balance())
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }

    @Test fun sameBusinessIntentOnBothClientsIsAcknowledgedOnce() = runBlocking {
        create(); val key = UUID.randomUUID().toString()
        first.savings.recordContribution(first.account, event(first, 55, key))
        second.savings.recordContribution(second.account, event(second, 55, key))
        coroutineScope { val a = async { first.flush() }; val b = async { second.flush() }; a.await(); b.await() }
        first.refresh(); second.refresh()
        assertEquals(55L, first.balance()); assertEquals(55L, second.balance())
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
    }

    @Test fun differentValuesUnderOneContributionIdentityRequireReviewedServerAcceptance() = runBlocking {
        create(); val key = UUID.randomUUID().toString()
        first.savings.recordContribution(first.account, event(first, 55, key)); first.flush()
        second.savings.recordContribution(second.account, event(second, 66, key)); second.flush()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        second.refresh(); assertEquals(66L, second.balance())
        val remote = requireNotNull(second.db.syncDao().record(second.account.uid, SavingsMapping.CONTRIBUTIONS, key))
        rejects { second.sync.resolveConflict(second.account, remote.recordType, key, true, remote.revision) {} }
        second.sync.resolveConflict(second.account, remote.recordType, key, false, remote.revision) { second.savings.applyServer(it) }
        assertEquals(55L, second.balance()); assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
    }

    @Test fun concurrentGoalEditsRetainDraftsAndAllowExplicitReviewedLocalResolution() = runBlocking {
        create()
        val a = first.row(); val b = second.row()
        first.savings.editGoal(first.account, a, "First edit", 100, null, null)
        second.savings.editGoal(second.account, b, "Second edit", 200, null, null)
        first.flush(); second.flush()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        second.refresh(); assertEquals("Second edit", second.row().name)
        val remote = requireNotNull(second.db.syncDao().record(second.account.uid, SavingsMapping.GOALS, b.cloudId))
        second.sync.resolveConflict(second.account, remote.recordType, remote.recordId, true, remote.revision) {}
        second.flush(); first.refresh(); second.refresh()
        assertEquals("Second edit", first.row().name); assertEquals(200L, first.row().targetCents)
        assertEquals(a.id, first.row().id)
    }

    @Test fun conflictingFirstGoalClaimsCannotPublishTheirOpeningBeforeReview() = runBlocking {
        val row = goal()
        first.savings.createGoal(first.account, row, 10, "2024-02-29")
        second.savings.createGoal(second.account, row.copy(userId = second.account.localUserId, name = "Other claim"), 20, "2024-02-29")
        first.flush(); second.flush()
        val pending = second.db.syncDao().pending(second.account.uid)
        assertEquals("CONFLICT", pending.single { it.recordType == SavingsMapping.GOALS }.state)
        assertEquals("FAILED", pending.single { it.recordType == SavingsMapping.CONTRIBUTIONS }.state)
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
        assertEquals(20L, second.balance())
        val goalServer = requireNotNull(second.db.syncDao().record(second.account.uid, SavingsMapping.GOALS, row.cloudId))
        second.sync.resolveConflict(second.account, goalServer.recordType, goalServer.recordId, false, goalServer.revision) { second.savings.applyServer(it) }
        second.sync.retryFailures(second.account); second.flush()
        val openingKey = "opening:${row.cloudId}"
        val eventServer = requireNotNull(second.db.syncDao().record(second.account.uid, SavingsMapping.CONTRIBUTIONS, openingKey))
        second.sync.resolveConflict(second.account, eventServer.recordType, eventServer.recordId, false, eventServer.revision) { second.savings.applyServer(it) }
        assertEquals(10L, second.balance()); assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
    }

    @Test fun anUnreviewedParentClaimDoesNotImportServerHistoryUnderItsLocalDraft() = runBlocking {
        val row = goal()
        first.savings.createGoal(first.account, row, 10, "2024-02-29"); first.flush()
        second.savings.createGoal(second.account, row.copy(userId = second.account.localUserId, name = "Unreviewed claim"))
        second.flush(); second.refresh()
        assertEquals("Unreviewed claim", second.row().name); assertEquals(0L, second.balance())
        val remote = requireNotNull(second.db.syncDao().record(second.account.uid, SavingsMapping.GOALS, row.cloudId))
        second.sync.resolveConflict(second.account, remote.recordType, remote.recordId, false, remote.revision) { second.savings.applyServer(it) }
        second.refresh(); assertEquals("Synthetic goal", second.row().name); assertEquals(10L, second.balance())
    }

    @Test fun fullReversalAndOutOfOrderDownloadsKeepOneRetainedHistory() = runBlocking {
        create(Long.MAX_VALUE)
        val original = first.db.savingsDao().contributions(first.account.localUserId, first.row().id).single()
        first.savings.recordContribution(first.account, event(first, Long.MAX_VALUE, "z-reversal").copy(
            kind = SavingsChecks.REVERSAL, reversesBusinessId = original.businessId))
        first.savings.recordContribution(first.account, event(first, Long.MAX_VALUE, "a-later-addition"))
        first.flush(); second.refresh()
        assertEquals(Long.MAX_VALUE, second.balance())
        assertEquals(3, second.db.savingsDao().contributions(second.account.localUserId, second.row().id).size)
        repeat(2) { second.refresh() }; assertEquals(Long.MAX_VALUE, second.balance())
    }

    @Test fun offlineRemovedGoalImportsItsHistoryBeforePublishingTheTombstone() = runBlocking {
        val row = goal()
        val id = first.db.savingsDao().createGoal(row, 50, "2024-02-29")
        first.db.savingsDao().removeGoal(first.account.localUserId, id)
        first.savings.importExisting(first.account); first.flush(); second.refresh()
        assertTrue(second.row().isDeleted)
        assertTrue(second.db.savingsDao().getForUser(second.account.localUserId).isEmpty())
        assertEquals(50L, second.balance())
        assertTrue(first.savings.importReady(first.account))
    }

    @Test fun serverGoalRemovalRejectsAnOfflineContributionWithoutDroppingTheLocalIntent() = runBlocking {
        create(10); val pending = event(second, 5)
        second.savings.recordContribution(second.account, pending)
        first.savings.removeGoal(first.account, first.row()); first.flush(); second.flush()
        assertEquals("FAILED", second.db.syncDao().pending(second.account.uid).single().state)
        second.refresh(); assertTrue(second.row().isDeleted); assertEquals(15L, second.balance())
        assertNotNull(second.db.savingsDao().contribution(second.account.localUserId, pending.businessId))
        assertFalse(second.savings.importReady(second.account))
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
    }

    @Test fun awardsRetainCallerEvidenceAcrossConcurrentRecordingAndConflictingReevaluation() = runBlocking {
        val award = EarnedAchievementEntity(userId = first.account.localUserId, awardKey = "synthetic:2024-02", badgeId = "synthetic", criteriaVersion = 1,
            earnedDate = "2024-02-29", earnedZoneId = "Africa/Johannesburg", evidenceRecordId = "synthetic-source")
        first.savings.recordEarned(first.account, award)
        second.savings.recordEarned(second.account, award.copy(userId = second.account.localUserId))
        coroutineScope { val a = async { first.flush() }; val b = async { second.flush() }; a.await(); b.await() }
        first.refresh(); second.refresh()
        assertNull(second.db.earnedAchievementDao().getForUser(second.account.localUserId).single().earnedAtEpochMillis)
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.AWARDS).size)
        val changed = SavingsMapping.fields(award).toMutableMap().apply { this["earnedDate"] = "2024-03-01" }
        val operation = SyncOperation(mutationId = UUID.randomUUID().toString(), uid = first.account.uid, userId = first.account.localUserId,
            recordType = SavingsMapping.AWARDS, recordId = award.awardKey, payload = SyncMapping.encode(SavingsMapping.AWARDS, changed), isDeleted = false, baseRevision = 0)
        assertTrue(first.cloud.send(operation) { true } is SendResult.Conflict)
        assertEquals("2024-02-29", second.db.earnedAchievementDao().getForUser(second.account.localUserId).single().earnedDate)
    }

    @Test fun competingReversalIntentsRetainTheRejectedDraftWithoutDoubleApplyingServerHistory() = runBlocking {
        create(123)
        val opening = first.db.savingsDao().contributions(first.account.localUserId, first.row().id).single().businessId
        first.savings.recordContribution(first.account, event(first).copy(kind = SavingsChecks.REVERSAL, reversesBusinessId = opening))
        second.savings.recordContribution(second.account, event(second).copy(kind = SavingsChecks.REVERSAL, reversesBusinessId = opening))
        coroutineScope { val a = async { first.flush() }; val b = async { second.flush() }; a.await(); b.await() }
        val loser = if (first.db.syncDao().pending(first.account.uid).isEmpty()) second else first
        assertEquals("FAILED", loser.db.syncDao().pending(loser.account.uid).single().state)
        first.refresh(); second.refresh()
        assertEquals(0L, first.balance()); assertEquals(0L, second.balance())
        assertEquals(2, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
        assertFalse(loser.savings.importReady(loser.account))
    }

    @Test fun concurrentTotalsBeyondLongRangeFailForReviewWithoutDroppingEitherLocalIntent() = runBlocking {
        create()
        val a = event(first, Long.MAX_VALUE); val b = event(second, Long.MAX_VALUE)
        first.savings.recordContribution(first.account, a); second.savings.recordContribution(second.account, b)
        coroutineScope { val x = async { first.flush() }; val y = async { second.flush() }; x.await(); y.await() }
        val loser = if (first.db.syncDao().pending(first.account.uid).isEmpty()) second else first
        assertEquals("FAILED", loser.db.syncDao().pending(loser.account.uid).single().state)
        first.refresh(); second.refresh()
        assertEquals(Long.MAX_VALUE, first.balance()); assertEquals(Long.MAX_VALUE, second.balance())
        assertNotNull(first.db.savingsDao().contribution(first.account.localUserId, a.businessId))
        assertNotNull(second.db.savingsDao().contribution(second.account.localUserId, b.businessId))
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
    }

    @Test fun discoveryIsRepeatSafeAndImportCompletionWaitsForSavingsAndAwards() = runBlocking {
        val id = first.db.savingsDao().createGoal(goal(), 5, "2024-02-29")
        repeat(2) { first.savings.importExisting(first.account) }
        assertEquals(2, first.db.syncDao().pending(first.account.uid).size)
        assertFalse(first.savings.importReady(first.account))
        first.flush(); assertTrue(first.savings.importReady(first.account))
        val prior = requireNotNull(first.db.savingsDao().getForUserById(first.account.localUserId, id))
        first.db.savingsDao().editGoal(first.account.localUserId, id, "Direct DAO draft", prior.targetCents, null, null)
        first.savings.importExisting(first.account); first.flush(); second.refresh()
        assertEquals("Direct DAO draft", second.row().name)
        assertEquals(5L, second.balance())
    }

    @Test fun serverRulesEnforceExactLongOverflowEvenForABypassedClient() = runBlocking {
        create(Long.MAX_VALUE)
        val id = UUID.randomUUID().toString(); val mutation = UUID.randomUUID().toString()
        val fields = SavingsMapping.fields(event(first, 1, id), first.row().cloudId)
        val owner = stores[0].collection("users").document(first.account.uid)
        val data = fields + mapOf("schemaVersion" to 1L, "revision" to 1L, "lastMutationId" to mutation,
            "updatedAt" to FieldValue.serverTimestamp(), "isDeleted" to false, "deletedAt" to null)
        val batch = stores[0].batch()
        batch.set(owner.collection(SavingsMapping.CONTRIBUTIONS).document(id), data)
        batch.set(owner.collection("savingsBalances").document(first.row().cloudId), mapOf("schemaVersion" to 1L, "balanceCents" to Long.MAX_VALUE,
            "lastContributionId" to id, "lastMutationId" to mutation, "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(owner.collection("mutations").document(mutation), mapOf("recordType" to SavingsMapping.CONTRIBUTIONS, "recordId" to id,
            "baseRevision" to 0L, "revision" to 1L, "fingerprint" to "a".repeat(64)))
        try { batch.commit().awaitSync(); fail("Overflow must be rejected") } catch (error: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
        }
        assertEquals(1, first.cloud.download(first.account, SavingsMapping.CONTRIBUTIONS).size)
        assertEquals(Long.MAX_VALUE, second.balance())
    }

    @Test fun capturedOwnerIsCheckedAfterWaitingForTheRoomTransaction() = runBlocking {
        val held = CompletableDeferred<Unit>(); val unlock = CompletableDeferred<Unit>()
        val lock = async { first.db.withTransaction { held.complete(Unit); unlock.await() } }; held.await()
        val writing = async(start = CoroutineStart.UNDISPATCHED) {
            try { first.savings.createGoal(first.account, goal()); false } catch (_: IllegalStateException) { true }
        }
        first.attached = false; unlock.complete(Unit); lock.await(); assertTrue(writing.await())
        assertTrue(first.db.savingsDao().getForUser(first.account.localUserId, true).isEmpty())
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }
}
