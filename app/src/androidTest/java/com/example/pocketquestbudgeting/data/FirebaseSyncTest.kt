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
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirebaseSyncTest {
    private lateinit var cloud: FirestoreSyncCloud
    private lateinit var account: SyncAccount

    companion object {
        private lateinit var app: FirebaseApp
        private lateinit var firestore: FirebaseFirestore
        private lateinit var auth: FirebaseAuth

        @BeforeClass @JvmStatic fun startBackend() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-pocket-quest")
                .setApplicationId("1:123456789012:android:1234567890abcdef123456")
                .setApiKey("AIzaSySyncFixtureNotARealFirebaseKey00000").build(), "slice4-${UUID.randomUUID()}")
            auth = FirebaseAuth.getInstance(app)
            auth.useEmulator("10.0.2.2", 9099)
            firestore = FirebaseFirestore.getInstance(app)
            // I kept one test client alive for the suite; Auth caches its backend by API key.
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            firestore.useEmulator("10.0.2.2", 8080)
        }

        @AfterClass @JvmStatic fun stopBackend() = runBlocking {
            if (::auth.isInitialized) auth.signOut()
            if (::firestore.isInitialized) firestore.terminate().awaitSync()
            if (::app.isInitialized) app.delete()
        }
    }

    @Before fun setUp() = runBlocking {
        auth.signOut()
        auth.createUserWithEmailAndPassword("fixture-${UUID.randomUUID()}@example.test", "Synthetic-emulator-only!").awaitSync()
        account = SyncAccount(1, requireNotNull(auth.currentUser).uid, 1)
        cloud = FirestoreSyncCloud(firestore)
    }

    private fun operation(base: Long = 0, deleted: Boolean = false, name: String = "Synthetic") = SyncOperation(
        mutationId = UUID.randomUUID().toString(), uid = account.uid, userId = account.localUserId,
        recordType = "categories", recordId = "stable-record", payload = SyncMapping.encode("categories",
            mapOf("name" to name, "minimumCents" to 0L, "maximumCents" to 9007199254740993L)),
        isDeleted = deleted, baseRevision = base)

    @Test fun replayDidNotReapplyOrRegressAfterAnotherAcceptedRevision() = runBlocking {
        val first = operation()
        assertEquals(SendResult.Accepted(1), cloud.send(first) { true })
        assertEquals(SendResult.Accepted(2), cloud.send(operation(1, name = "Newer")) { true })
        assertEquals(SendResult.Accepted(1), cloud.send(first) { true })
        val record = cloud.download(account, "categories").single()
        assertEquals(2L, record.revision)
        assertEquals("Newer", SyncMapping.decode("categories", record.payload)["name"])
        assertEquals(9007199254740993L, SyncMapping.decode("categories", record.payload)["maximumCents"])
        val receipts = firestore.collection("users").document(account.uid).collection("mutations").get().awaitSync()
        assertEquals(2, receipts.size())
        assertNotNull(record.updatedAtMillis)
    }

    @Test fun simultaneousBaseRevisionAcceptedExactlyOneMutation() = runBlocking {
        val results = coroutineScope {
            val first = async { cloud.send(operation(name = "First device")) { true } }
            val second = async { cloud.send(operation(name = "Second device")) { true } }
            listOf(first.await(), second.await())
        }
        assertEquals(1, results.count { it is SendResult.Accepted })
        assertEquals(1, results.count { it is SendResult.Conflict })
        assertEquals(1L, cloud.download(account, "categories").single().revision)
        assertEquals(1, firestore.collection("users").document(account.uid).collection("mutations").get().awaitSync().size())
    }

    @Test fun staleAndCurrentRevisionEditsCouldNotRestoreDeletionMarker() = runBlocking {
        cloud.send(operation()) { true }
        val deletion = operation(1, deleted = true)
        assertEquals(SendResult.Accepted(2), cloud.send(deletion) { true })
        assertTrue(cloud.send(operation(1)) { true } is SendResult.Conflict)
        assertTrue(cloud.send(operation(2)) { true } is SendResult.Conflict)
        assertEquals(SendResult.Accepted(2), cloud.send(deletion) { true })
        val tombstone = cloud.download(account, "categories").single()
        assertTrue(tombstone.isDeleted)
        assertEquals(2L, tombstone.revision)
        assertNotNull(tombstone.deletedAtMillis)
    }

    @Test fun reusedMutationWithDifferentSnapshotWasRejected() = runBlocking {
        val first = operation()
        cloud.send(first) { true }
        val changed = first.copy(isDeleted = true)
        try { cloud.send(changed) { true }; fail("A retained receipt must match the immutable snapshot") }
        catch (_: Exception) { }
        assertEquals(1L, cloud.download(account, "categories").single().revision)
    }

    @Test fun cloudSuccessBeforeRoomAcknowledgementRecoveredWithoutSecondWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val localId = db.userDao().createCloudUser(account.uid, "Fixture")
            account = account.copy(localUserId = localId)
            val coordinator = PocketQuestSyncCoordinator(db, cloud, { it == account }, {})
            coordinator.saveLocalAndEnqueue(account, "categories", "stable-record",
                SyncMapping.decode("categories", operation().payload)) {}
            val pending = db.syncDao().pending(account.uid).single()
            cloud.send(pending) { true }
            assertEquals(1, db.syncDao().pending(account.uid).size)
            assertEquals(SyncRun.COMPLETE, coordinator.retryPending(account))
            assertTrue(db.syncDao().pending(account.uid).isEmpty())
            assertEquals(1L, cloud.download(account, "categories").single().revision)
        } finally { db.close() }
    }
}
