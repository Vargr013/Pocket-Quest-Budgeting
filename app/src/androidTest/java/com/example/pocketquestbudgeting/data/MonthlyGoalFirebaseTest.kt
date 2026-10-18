package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MonthlyGoalFirebaseTest {
    private lateinit var first: Client
    private lateinit var second: Client
    companion object {
        private val apps = mutableListOf<FirebaseApp>()
        private val stores = mutableListOf<FirebaseFirestore>()
        private val auth = mutableListOf<FirebaseAuth>()
        @BeforeClass @JvmStatic fun connect() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            repeat(2) { index ->
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-pocket-quest")
                    .setApplicationId("1:123456789012:android:1234567890abcdef123456")
                    .setApiKey("AIzaSyGoalFixture${index}NotARealKey00000").build(), "goals-$index-${UUID.randomUUID()}")
                apps += app
                auth += FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2", 9099) }
                stores += FirebaseFirestore.getInstance(app).also {
                    it.firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                    it.useEmulator("10.0.2.2", 8080)
                }
            }
        }
        @AfterClass @JvmStatic fun disconnect() = runBlocking {
            auth.forEach { it.signOut() }
            stores.forEach { it.terminate().awaitSync() }
            apps.forEach { it.delete() }
        }
    }
    private class Client(val db: AppDatabase, val account: SyncAccount, val cloud: FirestoreSyncCloud) {
        val sync = PocketQuestSyncCoordinator(db, cloud, { it == account }, {})
        val goals = MonthlyGoalSync(db, sync)
        suspend fun refresh() { goals.refresh(account) }
        suspend fun rows() = db.budgetGoalDao().getForUser(account.localUserId)
        suspend fun save(minimum: Long, maximum: Long) {
            val reviewed = db.budgetGoalDao().getIncludingDeletedForMonth(account.localUserId, "2024-02-01", "2024-02-29")
            assertTrue(goals.saveForMonth(account, "2024-02-01", "2024-02-29", minimum, maximum, reviewed) { true })
        }
        suspend fun send() { assertEquals(SyncRun.COMPLETE, sync.retryPending(account)) }
    }
    @Before fun start() = runBlocking {
        val username = "category-${UUID.randomUUID()}@example.test"
        auth[0].createUserWithEmailAndPassword(username, "Synthetic-emulator-only!").awaitSync()
        auth[1].signInWithEmailAndPassword(username, "Synthetic-emulator-only!").awaitSync()
        val uid = requireNotNull(auth[0].currentUser).uid
        assertEquals(uid, auth[1].currentUser?.uid)
        fun client(index: Int): Client {
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
            return Client(db, SyncAccount(1, uid, 1), FirestoreSyncCloud(stores[index]))
        }
        first = client(0)
        second = client(1)
        assertEquals(1L, first.db.userDao().createCloudUser(uid, "Synthetic"))
        val unrelated = second.db.userDao().createCloudUser("unrelated", "Other local row")
        second.db.budgetGoalDao().insert(BudgetGoalEntity(userId = unrelated, minimumAmount = 0, maximumAmount = 0, startDate = "2024-02-01", endDate = "2024-02-29"))
        val secondId = second.db.userDao().createCloudUser(uid, "Synthetic")
        second = Client(second.db, SyncAccount(secondId, uid, 1), FirestoreSyncCloud(stores[1]))
    }
    @After fun stop() { first.db.close(); second.db.close() }

    @Test fun twoClientsUsedOneMonthWithDifferentLocalIdsAndExactLimitsInBothDirections() = runBlocking {
        first.save(9007199254740993L, Long.MAX_VALUE); first.send()
        repeat(2) { second.refresh() }
        val a = first.rows().single(); val b = second.rows().single()
        assertNotEquals(a.id, b.id); assertNotEquals(a.userId, b.userId)
        assertEquals("2024-02", a.monthKey); assertEquals(a.monthKey, b.monthKey)
        assertEquals(a.minimumAmount, b.minimumAmount); assertEquals(a.maximumAmount, b.maximumAmount)
        assertEquals("2024-02-01", b.startDate); assertEquals("2024-02-29", b.endDate)
        second.save(0, 0); second.send(); first.refresh()
        assertEquals(0L, first.rows().single().minimumAmount); assertEquals(0L, first.rows().single().maximumAmount)
        first.save(123, 456); first.send(); repeat(2) { second.refresh() }
        assertEquals(b.id, second.rows().single().id); assertEquals(123L, second.rows().single().minimumAmount)
        assertEquals(456L, second.rows().single().maximumAmount)
        assertEquals(1, first.cloud.download(first.account, "monthlyGoals").size)
    }

    @Test fun concurrentFirstSavesRequiredServerAcceptanceRatherThanSilentlyReplacingEitherDraft() = runBlocking {
        first.save(1, 2); second.save(3, 4)
        coroutineScope { val a = async { first.send() }; val b = async { second.send() }; a.await(); b.await() }
        val loser = if (first.db.syncDao().pending(first.account.uid).isEmpty()) second else first
        val local = loser.rows().single()
        assertEquals("CONFLICT", loser.db.syncDao().pending(loser.account.uid).single().state)
        loser.refresh(); assertEquals(local, loser.rows().single())
        val remote = loser.db.syncDao().records(loser.account.uid).single()
        loser.sync.resolveConflict(loser.account, "monthlyGoals", "2024-02", false, remote.revision) { loser.goals.applyServer(it) }
        first.refresh(); second.refresh()
        assertEquals(first.rows().single().minimumAmount, second.rows().single().minimumAmount)
        assertEquals(first.rows().single().maximumAmount, second.rows().single().maximumAmount)
        assertEquals(1L, first.cloud.download(first.account, "monthlyGoals").single().revision)
    }

    @Test fun concurrentEditsKeptTheLatestLocalDraftForExplicitLocalResolution() = runBlocking {
        first.save(1, 2); first.send(); second.refresh()
        first.save(10, 20); second.save(30, 40)
        coroutineScope { val a = async { first.send() }; val b = async { second.send() }; a.await(); b.await() }
        val loser = if (first.db.syncDao().pending(first.account.uid).isEmpty()) second else first
        val local = loser.rows().single(); loser.refresh(); assertEquals(local, loser.rows().single())
        val remote = loser.db.syncDao().records(loser.account.uid).single()
        loser.sync.resolveConflict(loser.account, "monthlyGoals", "2024-02", true, remote.revision) {}
        loser.send(); first.refresh(); second.refresh()
        assertEquals(local.minimumAmount, first.rows().single().minimumAmount)
        assertEquals(local.maximumAmount, second.rows().single().maximumAmount)
        assertEquals(3L, first.cloud.download(first.account, "monthlyGoals").single().revision)
        assertTrue(loser.db.syncDao().pending(loser.account.uid).isEmpty())
    }

    @Test fun unmappedLegacyImportEncounteredCloudMonthEvenWithImportFlagFalse() = runBlocking {
        first.save(1, 2); first.send()
        val id = second.db.budgetGoalDao().insert(BudgetGoalEntity(userId = second.account.localUserId,
            minimumAmount = 3, maximumAmount = 4, startDate = "2024-02-01", endDate = "2024-02-29"))
        assertFalse(requireNotNull(second.db.userDao().getById(second.account.localUserId)).importPending)
        repeat(2) { second.goals.importExisting(second.account) }
        val mutation = second.db.syncDao().pending(second.account.uid).single().mutationId
        second.send(); second.refresh()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        assertEquals(mutation, second.db.syncDao().pending(second.account.uid).single().mutationId)
        assertEquals(id, second.rows().single().id); assertEquals(3L, second.rows().single().minimumAmount)
        val remote = second.db.syncDao().records(second.account.uid).single()
        second.sync.resolveConflict(second.account, "monthlyGoals", "2024-02", true, remote.revision) {}
        second.send(); first.refresh(); repeat(2) { second.refresh() }
        assertEquals(4L, first.rows().single().maximumAmount); assertEquals(1, second.rows().size)
    }

    @Test fun acceptedGoalWithLostReplyReplayedWithoutAnotherRevision() = runBlocking {
        first.save(9007199254740993L, Long.MAX_VALUE)
        var loseReply = true
        val transport = object : SyncCloud {
            override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
                val result = first.cloud.send(operation, stillCurrent)
                if (loseReply) { loseReply = false; throw com.google.firebase.FirebaseNetworkException("Synthetic lost response") }
                return result
            }
            override suspend fun download(account: SyncAccount, type: String) = first.cloud.download(account, type)
        }
        val interrupted = PocketQuestSyncCoordinator(first.db, transport, { it == first.account }, {})
        assertEquals(SyncRun.RETRY, interrupted.retryPending(first.account))
        first.send(); second.refresh()
        assertEquals(1L, first.cloud.download(first.account, "monthlyGoals").single().revision)
        assertEquals(Long.MAX_VALUE, second.rows().single().maximumAmount)
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }
}
