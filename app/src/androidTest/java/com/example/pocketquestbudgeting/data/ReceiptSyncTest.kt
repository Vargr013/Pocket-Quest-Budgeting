package com.example.pocketquestbudgeting.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "receipt-recovery-${UUID.randomUUID()}"
    private val root = File(context.filesDir, name)
    private lateinit var db: AppDatabase
    private lateinit var account: SyncAccount
    private lateinit var sync: PocketQuestSyncCoordinator
    private lateinit var expenses: ExpenseSync
    private lateinit var photos: ReceiptSync
    private val cloud = ReceiptMemoryCloud()
    private var attached = true
    private var category = 0L

    @Before fun setup() = runBlocking {
        root.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        account = SyncAccount(db.userDao().createCloudUser("receipt-test", "Synthetic"), "receipt-test", 1)
        category = db.categoryDao().insert(CategoryEntity(userId = account.localUserId, name = "Synthetic"))
        connect()
    }
    private fun connect() {
        sync = PocketQuestSyncCoordinator(db, cloud, { attached && it == account }, {})
        expenses = ExpenseSync(db, sync)
        photos = ReceiptSync(db, sync, cloud, File(root, "private"))
    }
    @After fun finish() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    private fun image(label: String, color: Int = android.graphics.Color.RED, png: Boolean = true): File {
        val file = File(root, "$label.jpg")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }
    private suspend fun row() = db.expenseDao().getForUser(account.localUserId).single()
    private suspend fun save(file: File? = image("first")) {
        expenses.save(account, ExpenseEntity(userId = account.localUserId, categoryId = category, amount = 9007199254740993L,
            date = "2024-02-29", paymentTime = "09:00", description = "Synthetic", receiptImageUri = file?.absolutePath))
        sync.retryPending(account)
    }
    private suspend fun publish() { photos.runPending(account); sync.retryPending(account) }

    @Test fun firstPhotoPublishedOnceAndDownloadedAsValidatedExactBytes() = runBlocking {
        val source = image("first"); save(source)
        assertNull(row().receiptObjectPath)
        val job = db.receiptJobDao().jobs(account.uid).single()
        repeat(2) { photos.discoverSavedReceipts(account) }
        assertEquals(1, db.receiptJobDao().jobs(account.uid).size)
        publish()
        assertEquals(job.targetObjectPath, row().receiptObjectPath)
        assertEquals(9007199254740993L, row().amount)
        val before = cloud.sent.size
        repeat(2) { publish() }
        assertEquals(before, cloud.sent.size)
        assertEquals(ReceiptChecks.inspect(source), ReceiptChecks.inspect(File(requireNotNull(photos.ensureDownloaded(account, row().id)))))
    }
    @Test fun replacementRetainedAcceptedReferenceUntilPublication() = runBlocking {
        save(); publish(); val old = row(); val next = image("next", android.graphics.Color.BLUE)
        expenses.save(account, old.copy(receiptImageUri = next.absolutePath))
        assertEquals(old.receiptObjectPath, row().receiptObjectPath)
        sync.retryPending(account)
        photos.runPending(account)
        assertEquals(old.receiptObjectPath, row().receiptObjectPath)
        sync.retryPending(account)
        assertNotEquals(old.receiptObjectPath, row().receiptObjectPath)
        assertTrue(File(requireNotNull(old.receiptImageUri)).exists())
    }
    @Test fun financialEditsDuringUploadSurvivedAndKeptTheAcceptedPhoto() = runBlocking {
        save(); publish(); val old = row()
        expenses.save(account, old.copy(receiptImageUri = image("replacement").absolutePath)); sync.retryPending(account)
        cloud.afterUpload = {
            val latest = row()
            expenses.save(account, latest.copy(description = "Later financial edit", amount = Long.MAX_VALUE))
        }
        photos.runPending(account)
        assertEquals(old.receiptObjectPath, row().receiptObjectPath)
        sync.retryPending(account); publish()
        assertEquals("Later financial edit", row().description)
        assertEquals(Long.MAX_VALUE, row().amount)
        assertNotEquals(old.receiptObjectPath, row().receiptObjectPath)
    }
    @Test fun laterPhotoChoiceSurvivedAnEarlierPublicationAcknowledgement() = runBlocking {
        save(); photos.runPending(account)
        val first = db.receiptJobDao().jobs(account.uid).single()
        val next = image("later", android.graphics.Color.GREEN)
        expenses.save(account, row().copy(receiptImageUri = next.absolutePath))
        sync.retryPending(account)
        val selected = requireNotNull(db.receiptJobDao().selected(account.uid, requireNotNull(row().cloudId)))
        assertEquals(next.absolutePath, row().receiptImageUri)
        assertEquals(first.targetObjectPath, selected.expectedAcceptedPath)
        publish()
        assertEquals(selected.targetObjectPath, row().receiptObjectPath)
        assertEquals(ReceiptChecks.inspect(next).sha256, cloud.hashes[row().receiptObjectPath])
    }
    @Test fun lostUploadReplyAndDiskReopenReusedJobPathAndBytes() = runBlocking {
        save(); val id = db.receiptJobDao().jobs(account.uid).single().jobId
        cloud.loseUpload = true
        assertTrue(photos.runPending(account))
        db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, name).build(); connect()
        publish()
        assertEquals(id, db.receiptJobDao().jobs(account.uid).single().jobId)
        assertEquals(1, cloud.files.size)
        assertNotNull(row().receiptObjectPath)
    }
    @Test fun lostPublicationReplyKeptImmutableMutationUntilExactAcknowledgement() = runBlocking {
        save(); photos.runPending(account)
        val queued = db.syncDao().pending(account.uid).single()
        cloud.loseSend = true
        assertEquals(SyncRun.RETRY, sync.retryPending(account))
        assertEquals(queued.payload, db.syncDao().pending(account.uid).single().payload)
        sync.retryPending(account)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertNotNull(row().receiptObjectPath)
        assertEquals(3, cloud.sent.count { it.recordType == "categories" || it.recordType == "expenses" })
        assertEquals(1, cloud.sent.count { it.recordType == GamificationMapping.ACTIVITY })
        assertEquals(4, cloud.receipts.size)
    }
    @Test fun explicitRemovalAndRemoteOnlyKeepHadDifferentMeaning() = runBlocking {
        save(); publish(); val before = row()
        db.expenseDao().updateSynced(before.copy(receiptImageUri = null))
        expenses.save(account, row().copy(description = "Keep remote photo"), ReceiptChange.KEEP)
        sync.retryPending(account)
        assertEquals(before.receiptObjectPath, row().receiptObjectPath)
        expenses.save(account, row().copy(receiptImageUri = null), ReceiptChange.REMOVE, before.receiptObjectPath)
        sync.retryPending(account); publish()
        assertNull(row().receiptObjectPath)
    }
    @Test fun invalidSourceFailedOnlyPhotoAndLeftFinancialSaveAvailable() = runBlocking {
        val bad = File(root, "bad.jpg").apply { writeText("not an image") }
        save(bad); photos.runPending(account)
        assertTrue(db.receiptJobDao().jobs(account.uid).single().blocked)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
        assertTrue(bad.exists()); assertEquals(9007199254740993L, row().amount)
    }
    @Test fun detachedUploadDidNotPublishOrClearTheSavedPhotoChoice() = runBlocking {
        save(); cloud.afterUpload = { attached = false }
        photos.runPending(account)
        assertNull(row().receiptObjectPath)
        assertTrue(db.receiptJobDao().jobs(account.uid).single().selected)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }
    @Test fun legacyDiscoveryIgnoredCompletedFinancialImportAndSkippedDownloadedCaches() = runBlocking {
        save(null)
        val file = image("legacy")
        db.expenseDao().updateSynced(row().copy(receiptImageUri = file.absolutePath))
        repeat(3) { photos.discoverSavedReceipts(account) }
        assertEquals(1, db.receiptJobDao().jobs(account.uid).size)
        publish(); repeat(3) { photos.discoverSavedReceipts(account) }
        assertEquals(1, db.receiptJobDao().jobs(account.uid).size)
    }
    @Test fun rollbackDidNotLeaveASavedPhotoJobOrOutboxMutation() = runBlocking {
        try {
            db.withTransaction { save(); throw IllegalStateException("Synthetic rollback") }
        } catch (_: IllegalStateException) { }
        assertTrue(db.expenseDao().getForUser(account.localUserId).isEmpty())
        assertTrue(db.receiptJobDao().jobs(account.uid).isEmpty())
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }
    @Test fun imageBytesAndDimensionsWereCheckedInsteadOfFilenameOrPickerMime() {
        assertEquals("image/png", ReceiptChecks.inspect(image("png-in-jpg")).mime)
        assertEquals("image/jpeg", ReceiptChecks.inspect(image("jpeg", png = false)).mime)
        for (size in listOf(0L, ReceiptChecks.MAX_BYTES + 1)) {
            val file = File(root, "invalid-$size"); java.io.RandomAccessFile(file, "rw").use { it.setLength(size) }
            try { ReceiptChecks.inspect(file); fail("Invalid image size accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun corruptAndMissingCacheRedownloadedWithoutDuplicateJobs() = runBlocking {
        save(); publish()
        val first = requireNotNull(photos.ensureDownloaded(account, row().id))
        File(first).writeText("corrupt")
        val repaired = requireNotNull(photos.ensureDownloaded(account, row().id))
        assertTrue(File(repaired).length() > 7)
        File(repaired).delete()
        assertNotNull(photos.ensureDownloaded(account, row().id))
        assertEquals(1, db.receiptJobDao().jobs(account.uid).size)
    }
    @Test fun downloadBeforeAcknowledgementStillCompletedTheExactPublicationJob() = runBlocking {
        save(); photos.runPending(account)
        cloud.afterSend = { operation -> if (operation.recordType == "expenses") sync.refreshFromServer(account, "expenses") }
        sync.retryPending(account)
        assertFalse(db.receiptJobDao().jobs(account.uid).single().selected)
        assertEquals("PUBLISHED", db.receiptJobDao().jobs(account.uid).single().phase)
        assertNotNull(row().receiptObjectPath)
    }
    @Test fun newerServerDownloadWasNotRegressedByAnOlderPublicationAcknowledgement() = runBlocking {
        save(); photos.runPending(account)
        cloud.afterSend = { operation ->
            val key = "expenses" to operation.recordId
            val record = requireNotNull(cloud.rows[key])
            cloud.rows[key] = record.copy(revision = record.revision + 1,
                payload = SyncMapping.encode("expenses", SyncMapping.decode("expenses", record.payload) + ("description" to "Newer server edit")))
            sync.refreshFromServer(account, "expenses")
        }
        sync.retryPending(account)
        assertEquals(3L, db.syncDao().record(account.uid,"expenses",requireNotNull(row().cloudId))?.revision)
        expenses.refresh(account)
        assertEquals("Newer server edit", row().description)
        assertFalse(db.receiptJobDao().jobs(account.uid).single().selected)
    }
    @Test fun failedCleanupRetriedWithoutDeletingTheCurrentPublishedImage() = runBlocking {
        save(); publish(); val old = requireNotNull(row().receiptObjectPath)
        expenses.save(account,row().copy(receiptImageUri=image("replace",android.graphics.Color.BLUE).absolutePath));sync.retryPending(account);publish()
        val current = requireNotNull(row().receiptObjectPath)
        cloud.failDelete = true
        assertTrue(photos.runPending(account))
        assertTrue(cloud.files.containsKey(old));assertTrue(cloud.files.containsKey(current))
        photos.runPending(account)
        assertFalse(cloud.files.containsKey(old));assertTrue(cloud.files.containsKey(current))
    }
    @Test fun partialDownloadNeverBecameTheRoomReceiptAndCouldRetry() = runBlocking {
        save();publish()
        val original = row()
        File(requireNotNull(original.receiptImageUri)).delete()
        db.expenseDao().updateSynced(original.copy(receiptImageUri=null))
        cloud.failDownload=true
        try { photos.ensureDownloaded(account,original.id);fail("Partial download installed") }catch(_:FirebaseNetworkException){}
        assertNull(row().receiptImageUri)
        assertTrue(File(root,"private").walkTopDown().none{it.name.endsWith(".part")})
        assertNotNull(photos.ensureDownloaded(account,original.id))
    }
    @Test fun remotePhotoConflictPreservedLocalChoiceUntilExplicitServerReview() = runBlocking {
        save();publish()
        expenses.save(account,row().copy(receiptImageUri=image("losing").absolutePath));sync.retryPending(account)
        val key="expenses" to requireNotNull(row().cloudId)
        val record=requireNotNull(cloud.rows[key])
        cloud.rows[key]=record.copy(revision=record.revision+1,payload=SyncMapping.encode("expenses",SyncMapping.decode("expenses",record.payload)+("receiptObjectPath" to null)))
        expenses.refresh(account)
        assertEquals("CONFLICT",db.receiptJobDao().selected(account.uid,key.second)?.phase)
        assertNotNull(row().receiptImageUri)
        photos.resolveChoice(account,row().id,false,null)
        assertNull(row().receiptImageUri)
        assertNull(db.receiptJobDao().selected(account.uid,key.second))
    }
    @Test fun keepLocalReceiptReviewAllocatedANewImmutableJobIdentity() = runBlocking {
        save();publish()
        expenses.save(account,row().copy(receiptImageUri=image("local").absolutePath));sync.retryPending(account)
        val id=requireNotNull(row().cloudId);val selected=requireNotNull(db.receiptJobDao().selected(account.uid,id))
        db.receiptJobDao().put(selected.copy(phase="CONFLICT",blocked=true))
        photos.resolveChoice(account,row().id,true,row().receiptObjectPath)
        val next=requireNotNull(db.receiptJobDao().selected(account.uid,id))
        assertNotEquals(selected.jobId,next.jobId);assertEquals(selected.sourceLocalPath,next.sourceLocalPath)
        publish();assertEquals(next.targetObjectPath,row().receiptObjectPath)
    }
    @Test fun keepFromAnOpenEditorDidNotDetachANewlyDownloadedLocalCache() = runBlocking {
        save();publish();val cached=row()
        expenses.save(account,cached.copy(receiptImageUri=null,description="Editor without a cache"),ReceiptChange.KEEP)
        assertEquals(cached.receiptImageUri,row().receiptImageUri)
        assertEquals(cached.receiptObjectPath,row().receiptObjectPath)
    }
    @Test fun staleReviewedPhotoChoiceRolledBackFinancialFieldsAndReceiptJob() = runBlocking {
        save();publish();val before=row();val count=db.receiptJobDao().jobs(account.uid).size
        try {
            expenses.save(account,before.copy(amount=1,receiptImageUri=image("stale").absolutePath),ReceiptChange.REPLACE,null)
            fail("Stale photo review accepted")
        }catch(_:ReceiptReviewRequired){}
        assertEquals(before,row());assertEquals(count,db.receiptJobDao().jobs(account.uid).size)
        assertTrue(db.syncDao().pending(account.uid).isEmpty())
    }
    @Test fun acceptedRemovalStoppedRetryingAnOlderFailedDownload() = runBlocking {
        save();publish();val original=row()
        File(requireNotNull(original.receiptImageUri)).delete()
        cloud.failDownload=true
        try{photos.ensureDownloaded(account,original.id)}catch(_:FirebaseNetworkException){}
        assertNotNull(db.receiptJobDao().caches(account.uid).single().lastError)
        expenses.save(account,row().copy(receiptImageUri=null),ReceiptChange.REMOVE,original.receiptObjectPath)
        sync.retryPending(account);publish()
        assertTrue(db.receiptJobDao().caches(account.uid).all{it.state=="SUPERSEDED" && it.lastError==null})
    }
}

private class ReceiptMemoryCloud : SyncCloud, ReceiptCloud {
    val rows = mutableMapOf<Pair<String,String>, SyncRecord>()
    val receipts = mutableMapOf<String,Long>()
    val sent = mutableListOf<SyncOperation>()
    val files = mutableMapOf<String,ByteArray>()
    val hashes = mutableMapOf<String,String>()
    val guards = mutableMapOf<String,String>()
    var afterUpload: (suspend () -> Unit)? = null
    var afterSend: (suspend (SyncOperation) -> Unit)? = null
    var loseUpload = false
    var loseSend = false
    var failDelete = false
    var failDownload = false
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
        receipts[operation.mutationId]?.let { return SendResult.Accepted(it) }
        val key = operation.recordType to operation.recordId
        val old = rows[key]
        if ((old?.revision ?: 0) != operation.baseRevision) return SendResult.Conflict(old)
        val revision = requireNotNull(operation.baseRevision) + 1
        if(operation.recordType=="expenses") {
            val oldPath=old?.let{SyncMapping.decode("expenses",it.payload)["receiptObjectPath"] as String?}
            val newPath=SyncMapping.decode("expenses",operation.payload)["receiptObjectPath"] as String?
            if(oldPath!=newPath || operation.isDeleted) {
                oldPath?.let{guards[it]="RETIRED"}
                if(!operation.isDeleted)newPath?.let{guards[it]="PUBLISHED"}
            }
        }
        rows[key] = SyncRecord(operation.uid,operation.userId,operation.recordType,operation.recordId,revision,operation.payload,operation.isDeleted)
        receipts[operation.mutationId] = revision; sent += operation
        afterSend?.also { afterSend=null }?.invoke(operation)
        if (loseSend) { loseSend = false; throw FirebaseNetworkException("Synthetic lost reply") }
        return SendResult.Accepted(revision)
    }
    override suspend fun download(account: SyncAccount, type: String) = rows.values.filter { it.recordType == type }
    override suspend fun reserve(job: ReceiptJob, attached: () -> Boolean) { check(attached());guards.putIfAbsent(job.targetObjectPath,"STAGED") }
    override suspend fun upload(job: ReceiptJob, file: File, verification: File, attached: () -> Boolean) {
        files.putIfAbsent(job.targetObjectPath,file.readBytes()); hashes[job.targetObjectPath]=ReceiptChecks.inspect(file).sha256
        afterUpload?.also { afterUpload = null }?.invoke()
        if (loseUpload) { loseUpload=false;throw FirebaseNetworkException("Synthetic lost upload reply") }
    }
    override suspend fun download(path: String, file: File, attached: () -> Boolean): ReceiptInfo {
        if(failDownload){failDownload=false;file.writeText("partial");throw FirebaseNetworkException("Synthetic interrupted download")}
        file.writeBytes(requireNotNull(files[path])); return ReceiptChecks.inspect(file)
    }
    override suspend fun retire(job: ReceiptJob, attached: () -> Boolean) { check(attached());if(guards[job.targetObjectPath]=="STAGED")guards[job.targetObjectPath]="RETIRED" }
    override suspend fun retired(account: SyncAccount) = guards.filterValues{it=="RETIRED"}.keys.toList()
    override suspend fun delete(path: String, attached: () -> Boolean) {
        if(failDelete){failDelete=false;throw FirebaseNetworkException("Synthetic cleanup failure")}
        check(guards[path]=="RETIRED");files.remove(path)
    }
}
