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
class ExpenseFirebaseTest {
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
                    .setApiKey("AIzaSyExpenseFixture${index}NotARealKey00000").build(), "expenses-$index-${UUID.randomUUID()}")
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
        val expenses = ExpenseSync(db, sync)
        suspend fun refresh() {
            categories.refresh(account, cloud)
            expenses.refresh(account)
        }
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
        val unrelated = second.db.userDao().createCloudUser("unrelated", "Other local row")
        second.db.categoryDao().insert(CategoryEntity(userId = unrelated, name = "Unrelated"))
        val secondId = second.db.userDao().createCloudUser(uid, "Synthetic")
        second = Client(second.db, SyncAccount(secondId, uid, 1), FirestoreSyncCloud(stores[1]))
    }
    @After fun stop() { first.db.close(); second.db.close() }

    private fun draft(client: Client, category: Long) = ExpenseEntity(userId = client.account.localUserId,
        categoryId = category, amount = 9007199254740993L, date = "2024-02-29", startTime = "09:15", endTime = "10:30",
        description = "Café / synthetic expense", receiptImageUri = "/private/this-device-only.jpg")

    @Test fun twoClientsRoundTrippedEveryFieldExactCentsAndDifferentNumericReferences() = runBlocking {
        first.refresh(); second.refresh()
        val category = first.rows().first()
        first.expenses.save(first.account, draft(first, category.id)); first.send()
        val local = first.db.expenseDao().getForUser(first.account.localUserId).single()
        repeat(2) { second.refresh() }
        val remote = second.db.expenseDao().getForUser(second.account.localUserId).single()
        assertNotEquals(local.categoryId, remote.categoryId)
        assertEquals(category.cloudId, second.db.categoryDao().getForUserById(second.account.localUserId, remote.categoryId)?.cloudId)
        assertEquals(9007199254740993L, remote.amount)
        assertEquals("2024-02-29", remote.date); assertEquals("09:15", remote.startTime); assertEquals("10:30", remote.endTime)
        assertEquals(local.description, remote.description); assertNull(remote.receiptImageUri)
        assertEquals(local.cloudId, remote.cloudId)
        val cloudFields = SyncMapping.decode("expenses", first.cloud.download(first.account, "expenses").single().payload)
        assertEquals(category.cloudId, cloudFields["categoryId"]); assertNull(cloudFields["receiptObjectPath"])
        assertFalse(cloudFields.toString().contains("/private/"))
        val replacement = second.rows().first { it.id != remote.categoryId }
        second.expenses.save(second.account, remote.copy(categoryId = replacement.id, amount = 12345,
            date = "2026-10-18", startTime = "12:00", endTime = "12:45", description = "Changed on second client"))
        second.send(); first.refresh()
        val changed = first.db.expenseDao().getForUser(first.account.localUserId).single()
        assertEquals(local.id, changed.id); assertEquals(12345L, changed.amount)
        assertEquals("2026-10-18", changed.date); assertEquals("12:00", changed.startTime); assertEquals("12:45", changed.endTime)
        assertEquals("Changed on second client", changed.description)
        assertEquals(local.receiptImageUri, changed.receiptImageUri)
        assertEquals(replacement.cloudId, first.db.categoryDao().getForUserById(first.account.localUserId, changed.categoryId)?.cloudId)
    }

    @Test fun archiveVersusUnsentExpenseRequiredReplacementAndKeptItsDraftAndPhoto() = runBlocking {
        first.refresh(); second.refresh()
        val category = first.rows().first()
        val matching = second.rows().first { it.cloudId == category.cloudId }
        second.expenses.save(second.account, draft(second, matching.id))
        first.categories.archive(first.account, category.id); first.send()
        second.send(); second.refresh()
        val pending = second.db.syncDao().pending(second.account.uid).single()
        assertEquals("FAILED", pending.state); assertEquals(EXPENSE_CATEGORY_UNAVAILABLE, pending.lastError)
        second.sync.retryFailures(second.account)
        assertEquals("FAILED", second.db.syncDao().pending(second.account.uid).single().state)
        val retained = second.db.expenseDao().getForUser(second.account.localUserId).single()
        assertEquals(9007199254740993L, retained.amount); assertEquals("/private/this-device-only.jpg", retained.receiptImageUri)
        assertEquals(-1, second.expenses.save(second.account, retained))
        val replacement = second.rows().first()
        assertEquals(1, second.expenses.save(second.account, retained.copy(categoryId = replacement.id)))
        second.send(); first.refresh()
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
        assertEquals(9007199254740993L, first.db.expenseDao().getForUser(first.account.localUserId).single().amount)
    }

    @Test fun existingExpenseCouldKeepArchivedReferenceAndDeletionDidNotResurrect() = runBlocking {
        first.refresh(); second.refresh()
        val category = first.rows().first()
        first.expenses.save(first.account, draft(first, category.id)); first.send(); second.refresh()
        first.categories.archive(first.account, category.id); first.send(); second.refresh()
        val secondRow = second.db.expenseDao().getForUser(second.account.localUserId).single()
        assertEquals(1, second.expenses.save(second.account, secondRow.copy(description = "Kept old archived reference")))
        second.send(); first.refresh()
        second.expenses.save(second.account, secondRow.copy(description = "Pending edit"))
        val firstRow = first.db.expenseDao().getForUser(first.account.localUserId).single()
        first.expenses.delete(first.account, firstRow.id); first.send()
        second.send(); second.refresh()
        assertEquals("CONFLICT", second.db.syncDao().pending(second.account.uid).single().state)
        assertEquals("Pending edit", second.db.expenseDao().getForUser(second.account.localUserId).single().description)
        val server = second.db.syncDao().records(second.account.uid).single { it.recordType == "expenses" }
        second.sync.resolveConflict(second.account, "expenses", server.recordId, false, server.revision) { second.expenses.applyServer(it) }
        assertTrue(second.db.expenseDao().getForUser(second.account.localUserId).isEmpty())
        assertTrue(second.db.expenseDao().getAllForUser(second.account.localUserId).single().isDeleted)
        assertEquals(0L, second.db.categoryDao().getSpendingForUser(second.account.localUserId, null, null).sumOf { it.totalCents })
        val stale = SyncOperation(mutationId = UUID.randomUUID().toString(), uid = second.account.uid,
            userId = second.account.localUserId, recordType = "expenses", recordId = server.recordId,
            payload = server.payload, isDeleted = false, baseRevision = server.revision)
        assertTrue(second.cloud.send(stale) { true } is SendResult.Conflict)
        assertTrue(second.cloud.download(second.account, "expenses").single().isDeleted)
    }

    @Test fun legacyImportRepeatedBeforeAndAfterAcknowledgementWithoutDuplicatingData() = runBlocking {
        first.db.openHelper.writableDatabase.execSQL("UPDATE users SET importPending = 1 WHERE id = ?", arrayOf(first.account.localUserId))
        val localCategory = first.db.categoryDao().insert(CategoryEntity(userId = first.account.localUserId, name = "Legacy travel",
            minMonthlyBudget = 123, maxMonthlyBudget = 45678))
        val localId = first.db.expenseDao().insert(draft(first, localCategory))
        first.expenses.importExisting(first.account)
        val before = first.db.syncDao().pending(first.account.uid).map { it.mutationId }
        first.expenses.importExisting(first.account); first.expenses.finishRecordImport(first.account)
        assertEquals(before, first.db.syncDao().pending(first.account.uid).map { it.mutationId })
        assertTrue(requireNotNull(first.db.userDao().getById(first.account.localUserId)).importPending)
        first.send(); first.refresh(); second.refresh()
        first.expenses.importExisting(first.account); first.expenses.finishRecordImport(first.account)
        assertFalse(requireNotNull(first.db.userDao().getById(first.account.localUserId)).importPending)
        assertEquals(1, first.rows().size); assertEquals(1, second.rows().size)
        assertEquals(localId, first.db.expenseDao().getForUser(first.account.localUserId).single().id)
        assertEquals(1, first.cloud.download(first.account, "expenses").size)
        assertEquals(1, second.db.expenseDao().getForUser(second.account.localUserId).size)
        assertEquals(123L, second.rows().single().minMonthlyBudget)
        assertEquals(45678L, second.rows().single().maxMonthlyBudget)
    }

    @Test fun simultaneousArchiveAndNewExpenseKeptAValidHistoryOrActionableDraft() = runBlocking {
        first.refresh(); second.refresh()
        val category = first.rows().first()
        val localCategory = second.rows().first { it.cloudId == category.cloudId }
        second.expenses.save(second.account, draft(second, localCategory.id))
        first.categories.archive(first.account, category.id)
        coroutineScope { val archive = async { first.send() }; val expense = async { second.send() }; archive.await(); expense.await() }
        second.refresh()
        val pending = second.db.syncDao().pending(second.account.uid).singleOrNull()
        if (pending == null) assertEquals(1, second.cloud.download(second.account, "expenses").size)
        else {
            assertEquals("FAILED", pending.state)
            assertEquals(EXPENSE_CATEGORY_UNAVAILABLE, pending.lastError)
            assertTrue(second.cloud.download(second.account, "expenses").isEmpty())
        }
        assertEquals(1, second.db.expenseDao().getForUser(second.account.localUserId).size)
        assertTrue(requireNotNull(second.db.categoryDao().getForUserById(second.account.localUserId, localCategory.id)).isArchived)
    }

    @Test fun deletingARejectedReassignmentUsedTheAcceptedExpenseSnapshot() = runBlocking {
        first.refresh(); second.refresh()
        val category = first.rows().first()
        val otherCategory = first.rows().first { it.id != category.id }
        first.expenses.save(first.account, draft(first, category.id)); first.send(); second.refresh()
        first.categories.archive(first.account, otherCategory.id); first.send()
        val row = second.db.expenseDao().getForUser(second.account.localUserId).single()
        val staleChoice = second.rows().first { it.cloudId == otherCategory.cloudId }
        second.expenses.save(second.account, row.copy(categoryId = staleChoice.id, description = "Rejected reassignment"))
        second.send()
        assertEquals(EXPENSE_CATEGORY_UNAVAILABLE, second.db.syncDao().pending(second.account.uid).single().lastError)
        second.expenses.delete(second.account, row.id); second.send(); first.refresh()
        assertTrue(second.db.syncDao().pending(second.account.uid).isEmpty())
        assertTrue(first.db.expenseDao().getForUser(first.account.localUserId).isEmpty())
        val cloud = first.cloud.download(first.account, "expenses").single()
        assertTrue(cloud.isDeleted)
        assertEquals(category.cloudId, SyncMapping.decode("expenses", cloud.payload)["categoryId"])
        assertEquals(row.description, SyncMapping.decode("expenses", cloud.payload)["description"])
    }

    @Test fun acceptedExpenseWithLostReplyReplayedWithoutAnotherWrite() = runBlocking {
        first.refresh(); second.refresh()
        first.expenses.save(first.account, draft(first, first.rows().first().id))
        var loseReply = true
        val transport = object : SyncCloud {
            override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
                val result = first.cloud.send(operation, stillCurrent)
                if (loseReply) { loseReply = false; throw com.google.firebase.FirebaseNetworkException("Synthetic lost reply") }
                return result
            }
            override suspend fun download(account: SyncAccount, type: String) = first.cloud.download(account, type)
        }
        val interrupted = PocketQuestSyncCoordinator(first.db, transport, { it == first.account }, {})
        assertEquals(SyncRun.RETRY, interrupted.retryPending(first.account))
        assertEquals(1, first.db.syncDao().pending(first.account.uid).size)
        first.send(); second.refresh()
        assertEquals(1L, first.cloud.download(first.account, "expenses").single().revision)
        assertTrue(first.db.syncDao().pending(first.account.uid).isEmpty())
        assertEquals(1, second.db.expenseDao().getForUser(second.account.localUserId).size)
    }

    @Test fun concurrentEditsKeptTheLosingDraftForExplicitReview() = runBlocking {
        first.refresh(); second.refresh()
        first.expenses.save(first.account, draft(first, first.rows().first().id)); first.send(); second.refresh()
        val a = first.db.expenseDao().getForUser(first.account.localUserId).single()
        val b = second.db.expenseDao().getForUser(second.account.localUserId).single()
        first.expenses.save(first.account, a.copy(description = "First client draft"))
        second.expenses.save(second.account, b.copy(description = "Second client draft"))
        coroutineScope { val sent = async { first.send() }; val other = async { second.send() }; sent.await(); other.await() }
        val loser = if (first.db.syncDao().pending(first.account.uid).isNotEmpty()) first else second
        val issue = loser.db.syncDao().pending(loser.account.uid).single()
        assertEquals(issue.lastError, "CONFLICT", issue.state)
        val draft = loser.db.expenseDao().getForUser(loser.account.localUserId).single()
        loser.refresh()
        assertEquals(draft.description, loser.db.expenseDao().getForUser(loser.account.localUserId).single().description)
        val server = loser.db.syncDao().records(loser.account.uid).single { it.recordType == "expenses" }
        loser.sync.resolveConflict(loser.account, "expenses", server.recordId, true, server.revision) {}
        loser.send(); first.refresh(); second.refresh()
        assertEquals(draft.description, first.db.expenseDao().getForUser(first.account.localUserId).single().description)
        assertEquals(draft.description, second.db.expenseDao().getForUser(second.account.localUserId).single().description)
        assertEquals(3L, first.cloud.download(first.account, "expenses").single().revision)
    }
}
