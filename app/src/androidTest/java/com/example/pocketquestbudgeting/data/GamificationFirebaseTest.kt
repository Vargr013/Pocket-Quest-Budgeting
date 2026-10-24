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
import com.google.firebase.firestore.FirebaseFirestore
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
class GamificationFirebaseTest {
    companion object {
        private val context get() = ApplicationProvider.getApplicationContext<Context>()
        private val apps = mutableListOf<FirebaseApp>()
        private val stores = mutableListOf<FirebaseFirestore>()
        private val auth = mutableListOf<FirebaseAuth>()
        @BeforeClass @JvmStatic fun connect() {
            repeat(2) { index ->
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-pocket-quest")
                    .setApplicationId("1:123456789012:android:1234567890abcdef123456")
                    .setApiKey("AIzaSyGamificationFixture${index}NotReal000").build(), "gamification-$index-${UUID.randomUUID()}")
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
                if (loseReply) { loseReply = false; throw FirebaseNetworkException("Synthetic lost reply") }
                return result
            }
            override suspend fun download(account: SyncAccount, type: String) = cloud.download(account, type)
        }, { attached && it == account }, {})
        val feature = GamificationSync(db, sync)
        suspend fun item() = db.gamificationDao().getCustomItems(account.localUserId).single()
        suspend fun flush() { assertEquals(SyncRun.COMPLETE, sync.retryPending(account)) }
    }
    private lateinit var first: Client
    private lateinit var second: Client
    @Before fun start() = runBlocking {
        val email = "gamification-${UUID.randomUUID()}@example.test"
        auth[0].createUserWithEmailAndPassword(email, "Synthetic-emulator-only!").awaitSync()
        auth[1].signInWithEmailAndPassword(email, "Synthetic-emulator-only!").awaitSync()
        val uid = requireNotNull(auth[0].currentUser).uid
        suspend fun client(index: Int): Client {
            val name = "gamification-cloud-${UUID.randomUUID()}"
            val db = open(name)
            if (index == 1) {
                val other = db.userDao().createCloudUser("other", "Other")
                db.gamificationDao().insertCustomItem(CustomWorthItemEntity(userId = other, name = "Private", valueCents = 100, category = "CUSTOM"))
            }
            val owner = db.userDao().createCloudUser(uid, "Owner")
            return Client(name, db, SyncAccount(owner, uid, 1), FirestoreSyncCloud(stores[index]))
        }
        first = client(0); second = client(1)
    }
    @After fun finish() { first.db.close(); second.db.close(); context.deleteDatabase(first.name); context.deleteDatabase(second.name) }
    private fun draft() = CustomWorthItemEntity(userId = first.account.localUserId, name = "Bicycle", valueCents = 450000,
        category = "CUSTOM", cloudId = UUID.randomUUID().toString())
    private suspend fun create() {
        first.feature.saveItem(first.account, draft()); first.flush(); second.feature.refresh(second.account)
    }

    @Test fun sameDayAndIndependentOfflineDaysConvergeWithoutDuplicateQueueEntries() = runBlocking {
        repeat(3) { first.feature.markActiveDay(first.account, "2026-10-20") }
        second.feature.markActiveDay(second.account, "2026-10-20")
        first.feature.markActiveDay(first.account, "2026-10-19")
        second.feature.markActiveDay(second.account, "2026-10-21")
        assertEquals(2, first.db.syncDao().pending(first.account.uid).size)
        coroutineScope { val a = async { first.flush() }; val b = async { second.flush() }; a.await(); b.await() }
        first.feature.refresh(first.account); second.feature.refresh(second.account)
        assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-21"), second.db.gamificationDao().getActiveDays(second.account.localUserId))
        assertEquals(3, first.cloud.download(first.account, GamificationMapping.ACTIVITY).size)
        repeat(2) { first.feature.importExisting(first.account) }
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }

    @Test fun emptyClientDownloadsMappedItemsAndEditsRetainLocalIdentity() = runBlocking {
        create()
        val a = first.item(); val b = second.item()
        assertNotEquals(a.userId, b.userId); assertNotEquals(a.id, b.id); assertEquals(a.cloudId, b.cloudId)
        second.feature.saveItem(second.account, b.copy(name = "Bicycle fund", valueCents = 500000), b)
        second.flush(); first.feature.refresh(first.account)
        assertEquals(a.id, first.item().id); assertEquals(500000L, first.item().valueCents)
        assertEquals("Private", second.db.gamificationDao().getCustomItems(1).single().name)
    }

    @Test fun lostActivityReplyAndDiskRestartKeepTheDateAndMutationIdentity() = runBlocking {
        first.feature.markActiveDay(first.account, "2026-10-20")
        val operation = first.db.syncDao().pending(first.account.uid).single()
        first.loseReply = true
        assertEquals(SyncRun.RETRY, first.sync.retryPending(first.account))
        first.db.close(); first = Client(first.name, open(first.name), first.account, first.cloud)
        assertEquals(operation.mutationId, first.db.syncDao().pending(first.account.uid).single().mutationId)
        first.flush(); second.feature.refresh(second.account)
        assertEquals(listOf("2026-10-20"), second.db.gamificationDao().getActiveDays(second.account.localUserId))
        assertEquals(1, first.cloud.download(first.account, GamificationMapping.ACTIVITY).size)
    }

    @Test fun retainedRemovalPublishesAfterCreationAndStaleEditCannotResurrectIt() = runBlocking {
        create()
        val stale = second.item()
        first.feature.removeItem(first.account, first.item()); first.flush()
        second.feature.saveItem(second.account, stale.copy(valueCents = 600000), stale); second.flush()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        second.feature.refresh(second.account)
        assertEquals(600000L, second.item().valueCents)
        val remote = requireNotNull(second.db.syncDao().record(second.account.uid, GamificationMapping.ITEMS, stale.cloudId!!))
        assertTrue(remote.isDeleted)
        try { second.sync.resolveConflict(second.account, remote.recordType, remote.recordId, true, remote.revision) {}; fail("Restored a tombstone") }
        catch (_: IllegalStateException) { }
        second.sync.resolveConflict(second.account, remote.recordType, remote.recordId, false, remote.revision) { second.feature.applyServer(it) }
        assertTrue(second.db.gamificationDao().getCustomItems(second.account.localUserId).isEmpty())
        assertTrue(second.db.gamificationDao().getAllCustomItems(second.account.localUserId).single().isDeleted)

        val id = first.feature.saveItem(first.account, draft())
        first.feature.removeItem(first.account, first.db.gamificationDao().getCustomItem(first.account.localUserId, id)!!)
        first.flush(); second.feature.refresh(second.account)
        assertTrue(second.db.gamificationDao().getCustomItems(second.account.localUserId).isEmpty())
    }

    @Test fun reviewedRemovalUsesTheAcceptedEditedValuesAndConcurrentEditsCanRebase() = runBlocking {
        create()
        val stale = second.item()
        first.feature.saveItem(first.account, first.item().copy(name = "Accepted edit"), first.item()); first.flush()
        second.feature.removeItem(second.account, stale); second.flush(); second.feature.refresh(second.account)
        var issue = second.db.syncDao().pending(second.account.uid).single()
        var remote = second.db.syncDao().record(second.account.uid, issue.recordType, issue.recordId)!!
        second.sync.resolveConflict(second.account, issue.recordType, issue.recordId, true, remote.revision) {}
        second.flush(); first.feature.refresh(first.account)
        assertTrue(first.db.gamificationDao().getCustomItems(first.account.localUserId).isEmpty())
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())

        first.feature.saveItem(first.account, draft()); first.flush(); second.feature.refresh(second.account)
        val a = first.item(); val b = second.item()
        first.feature.saveItem(first.account, a.copy(valueCents = 500000), a); first.flush()
        second.feature.saveItem(second.account, b.copy(valueCents = 600000), b); second.flush(); second.feature.refresh(second.account)
        issue = second.db.syncDao().pending(second.account.uid).single()
        remote = second.db.syncDao().record(second.account.uid, issue.recordType, issue.recordId)!!
        second.sync.resolveConflict(second.account, issue.recordType, issue.recordId, true, remote.revision) {}
        second.flush(); first.feature.refresh(first.account)
        assertEquals(600000L, first.item().valueCents)
    }

    @Test fun legacyImportKeepsDuplicateNamesDistinctAndSkipsInvalidRows() = runBlocking {
        val good = draft().copy(cloudId = null)
        first.db.gamificationDao().insertCustomItem(good)
        first.db.gamificationDao().insertCustomItem(good)
        first.db.gamificationDao().insertCustomItem(good.copy(name = "Invalid", valueCents = 0))
        first.db.gamificationDao().markActiveDay(first.account.localUserId, "invalid")
        assertEquals(2, first.feature.importExisting(first.account))
        val keys = first.db.gamificationDao().getCustomItems(first.account.localUserId).filter { it.valueCents > 0 }.map { it.cloudId }
        assertEquals(2, keys.toSet().size)
        first.feature.importExisting(first.account)
        assertEquals(2, first.db.syncDao().pending(first.account.uid).size)
        first.flush(); second.feature.refresh(second.account)
        assertEquals(2, second.db.gamificationDao().getCustomItems(second.account.localUserId).size)
        val invalid = first.db.gamificationDao().getCustomItems(first.account.localUserId).first { it.valueCents == 0L }
        first.feature.saveItem(first.account, invalid.copy(valueCents = 100, cloudId = UUID.randomUUID().toString()), invalid)
        first.flush(); second.feature.refresh(second.account)
        assertEquals(3, second.db.gamificationDao().getCustomItems(second.account.localUserId).size)
    }

    @Test fun accountDetachWhileWaitingForRoomRollsBackBothFactAndQueue() = runBlocking {
        val held = CompletableDeferred<Unit>(); val unlock = CompletableDeferred<Unit>()
        val holding = async { first.db.withTransaction { held.complete(Unit); unlock.await() } }; held.await()
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            try { first.feature.markActiveDay(first.account, "2026-10-20"); false } catch (_: IllegalStateException) { true }
        }
        first.attached = false; unlock.complete(Unit); holding.await(); assertTrue(write.await())
        assertTrue(first.db.gamificationDao().getActiveDays(first.account.localUserId).isEmpty())
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
    }
}
