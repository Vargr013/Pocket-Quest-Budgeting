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
class CategoryFirebaseTest {
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
                    .setApiKey("AIzaSyCategoryFixture${index}NotARealKey00000").build(), "categories-$index-${UUID.randomUUID()}")
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
        val categories = CategorySync(db, sync)
        suspend fun refresh() = categories.refresh(account, cloud)
        suspend fun rows() = db.categoryDao().getForUser(account.localUserId)
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
        second.db.userDao().createCloudUser("unrelated", "Other local row")
        val secondId = second.db.userDao().createCloudUser(uid, "Synthetic")
        second = Client(second.db, SyncAccount(secondId, uid, 1), FirestoreSyncCloud(stores[1]))
    }
    @After fun stop() { first.db.close(); second.db.close() }

    @Test fun twoClientsSharedDefaultsRenamesAndExactLimitsWithoutDuplicateDownloads() = runBlocking {
        coroutineScope { val a = async { first.refresh() }; val b = async { second.refresh() }; a.await(); b.await() }
        assertEquals(4, first.rows().size)
        assertEquals(first.rows().map { it.cloudId }.toSet(), second.rows().map { it.cloudId }.toSet())
        val original = first.rows().first { it.name == "Groceries" }
        val remoteId = second.rows().first { it.cloudId == original.cloudId }.id
        first.categories.rename(first.account, original.id, "Food shopping")
        first.categories.setBudgets(first.account, original.id, 123, 9007199254740993L)
        first.send()
        repeat(2) { second.refresh() }
        val downloaded = second.rows().first { it.cloudId == original.cloudId }
        assertEquals(remoteId, downloaded.id)
        assertEquals("Food shopping", downloaded.name)
        assertEquals(123L, downloaded.minMonthlyBudget)
        assertEquals(9007199254740993L, downloaded.maxMonthlyBudget)
        second.categories.setBudgets(second.account, remoteId, 0, 0)
        second.send()
        first.refresh()
        assertEquals(0L, first.rows().first { it.id == original.id }.maxMonthlyBudget)
        assertEquals(4, first.rows().size)
    }

    @Test fun archivedCategoryKeptOtherClientsUnsentExpenseAndConflictingEdit() = runBlocking {
        first.refresh(); second.refresh()
        val original = first.rows().first()
        val remote = second.rows().first { it.cloudId == original.cloudId }
        val expense = second.db.expenseDao().insert(ExpenseEntity(userId = second.account.localUserId,
            categoryId = remote.id, amount = 123, date = "2026-10-17", startTime = "09:15", endTime = "09:30", description = "Unsent synthetic expense"))
        second.categories.rename(second.account, remote.id, "Offline draft")
        first.categories.archive(first.account, original.id); first.send()
        second.send()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        second.refresh()
        assertEquals("Offline draft", second.db.categoryDao().getForUserById(second.account.localUserId, remote.id)?.name)
        val server = requireNotNull(second.db.syncDao().record(second.account.uid, "categories", requireNotNull(original.cloudId)))
        second.sync.resolveConflict(second.account, "categories", server.recordId, false, server.revision) { second.categories.applyServer(it) }
        assertTrue(requireNotNull(second.db.categoryDao().getForUserById(second.account.localUserId, remote.id)).isArchived)
        assertFalse(second.rows().any { it.id == remote.id })
        assertEquals(remote.id, second.db.expenseDao().getForUserById(second.account.localUserId, expense)?.categoryId)
        assertTrue(second.db.categoryDao().getSpendingForUser(second.account.localUserId, null, null).any { it.categoryId == remote.id && it.totalCents == 123L })
    }

    @Test fun establishedEmptyAccountStayedEmptyAfterBothClientsRefreshed() = runBlocking {
        first.refresh()
        for (category in first.rows()) first.categories.archive(first.account, category.id)
        first.send(); second.refresh(); first.refresh()
        assertTrue(first.rows().isEmpty()); assertTrue(second.rows().isEmpty())
        assertEquals(4, second.db.categoryDao().getAllForUser(second.account.localUserId).size)
        assertTrue(first.cloud.download(first.account, "categories").all { it.isDeleted })
    }

    @Test fun legacyLocalStateSuppressedDefaultsAndRepeatedSetupDidNotReseed() = runBlocking {
        first.db.categoryDao().insert(CategoryEntity(userId = first.account.localUserId, name = "Existing local"))
        first.refresh(); second.refresh()
        assertEquals("Existing local", first.rows().single().name)
        assertTrue(second.rows().isEmpty())
        assertTrue(first.cloud.download(first.account, "categories").isEmpty())
    }

    @Test fun duplicateNameFromOtherClientCouldBeRenamedAndRetried() = runBlocking {
        first.refresh(); second.refresh()
        first.categories.create(first.account, "Shared name", 0, 100)
        val id = requireNotNull(second.categories.create(second.account, "SHARED NAME", 0, 100))
        first.send(); second.send()
        assertEquals("FAILED", second.db.syncDao().pending(second.account.uid).single().state)
        second.categories.rename(second.account, id, "Different name")
        second.send(); second.refresh(); first.refresh()
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
        assertEquals(first.rows().map { it.name }.toSet(), second.rows().map { it.name }.toSet())
    }

    @Test fun rejectedDuplicateCouldBeArchivedWithoutRemovingTheOtherReservation() = runBlocking {
        first.refresh(); second.refresh()
        first.categories.create(first.account, "Shared", 0, 0)
        val duplicate = requireNotNull(second.categories.create(second.account, "SHARED", 0, 0))
        first.send(); second.send()
        second.categories.archive(second.account, duplicate)
        second.send(); first.refresh(); second.refresh()
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
        assertEquals(1, second.rows().count { it.name.equals("shared", true) })
        assertTrue(requireNotNull(second.db.categoryDao().getForUserById(second.account.localUserId, duplicate)).isArchived)
    }
}
