package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ReceiptSync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator,
    private val cloud: ReceiptCloud, private val root: File) {
    private val lock = Mutex()
    private val jobs = db.receiptJobDao()
    private fun directory(account: SyncAccount): File {
        val owner = MessageDigest.getInstance("SHA-256").digest(account.uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(root, "$owner-${account.localUserId}").also { check(it.mkdirs() || it.isDirectory) }
    }
    private suspend fun current(account: SyncAccount, id: String): ReceiptJob = db.withTransaction {
        sync.checkOwner(account)
        requireNotNull(jobs.get(account.uid, id)).also { check(it.selected) { "The receipt choice changed." } }
    }
    private suspend fun store(account: SyncAccount, job: ReceiptJob) = sync.writeForOwner(account) {
        check(jobs.get(account.uid, job.jobId)?.selected == true) { "The receipt choice changed." }
        jobs.put(job)
    }
    private suspend fun clearPartialFiles(account: SyncAccount) {
        sync.checkOwner(account)
        val folder = directory(account).canonicalFile
        val retained = (jobs.jobs(account.uid).mapNotNull { it.sourceLocalPath }
            + jobs.caches(account.uid).mapNotNull { it.localPath }
            + db.expenseDao().getAllForUser(account.localUserId).mapNotNull { it.receiptImageUri })
            .map { File(it).canonicalPath }.toSet()
        // I only removed our incomplete transfer files while no receipt transfer held the lock.
        folder.listFiles()?.filter { it.isFile && (it.name.startsWith("download-") ||
            it.name.matches(Regex("[a-f0-9-]{36}\\.(copy|verify)\\.part"))) && it.name.endsWith(".part") }?.forEach {
            sync.checkOwner(account)
            if (it.canonicalFile.parentFile == folder && it.canonicalPath !in retained) it.delete()
        }
    }

    suspend fun discoverSavedReceipts(account: SyncAccount) = sync.writeForOwner(account) {
        for (row in db.expenseDao().getAllForUser(account.localUserId)) {
            val id = row.cloudId ?: continue
            if (!row.isDeleted && row.receiptObjectPath == null && row.receiptImageUri != null
                && jobs.jobs(account.uid).none { it.expenseCloudId == id }) {
                // I checked saved photos separately because financial import could already be complete.
                ReceiptState.choose(db, account, row, ReceiptChange.REPLACE)
            }
        }
    }

    suspend fun runPending(account: SyncAccount): Boolean = withContext(Dispatchers.IO) { lock.withLock {
        sync.checkOwner(account)
        clearPartialFiles(account)
        discoverSavedReceipts(account)
        var retry = false
        val pending = jobs.jobs(account.uid).filter { it.selected && !it.blocked }
        retry = pending.size > 8
        for (listed in pending.take(8)) {
            try { process(account, listed.jobId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!sync.isAttached(account)) return@withLock false
                sync.writeForOwner(account) {
                    jobs.get(account.uid, listed.jobId)?.takeIf { it.selected }?.let {
                        val transient = receiptTransient(error)
                        retry = retry || transient
                        jobs.put(it.copy(attemptCount = it.attemptCount + 1, blocked = !transient,
                            lastError = receiptFailure(error)))
                    }
                }
            }
        }
        var downloads = 0
        for (row in db.expenseDao().getForUser(account.localUserId).filter { it.receiptObjectPath != null }) {
            val cache = jobs.cached(account.uid, requireNotNull(row.cloudId), requireNotNull(row.receiptObjectPath))
            if (cache?.blocked == true) continue
            if (cache?.localPath != null && File(cache.localPath).isFile) continue
            if (downloads++ >= 8) { retry = true; break }
            try { downloadInside(account, row.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { retry = retry || receiptTransient(error) }
        }
        // I kept abandoned uploads until their publication outcome was known (Google, 2026z).
        for (job in jobs.jobs(account.uid).filter { !it.selected && it.phase != "DONE" }) {
            if (db.syncDao().forRecord(account.uid, "expenses", job.expenseCloudId).isNotEmpty()) continue
            try {
                sync.checkOwner(account)
                if (!job.remove) cloud.retire(job) { sync.isAttached(account) }
                sync.writeForOwner(account) { jobs.put(job.copy(phase = "DONE", lastError = null)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!sync.isAttached(account)) return@withLock false
                retry = retry || receiptTransient(error)
                sync.writeForOwner(account) { jobs.put(job.copy(lastError = "Receipt cleanup is pending. Retry after checking your connection.")) }
            }
        }
        try {
            for (path in cloud.retired(account)) {
                sync.checkOwner(account)
                val parts = path.split('/')
                require(parts.size == 5)
                ReceiptChecks.pathJob(account.uid, parts[3], path)
                cloud.delete(path) { sync.isAttached(account) }
            }
            sync.writeForOwner(account) {
                jobs.jobs(account.uid).filter { it.lastError?.startsWith("Receipt cleanup is pending.") == true }
                    .forEach { jobs.put(it.copy(lastError = null)) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (sync.isAttached(account)) {
                retry = retry || receiptTransient(error)
                // The retained remote retirement document remains the durable cleanup request.
                jobs.jobs(account.uid).lastOrNull { !it.selected && (it.lastError == null || it.lastError.startsWith("Receipt cleanup is pending.")) }?.let { job -> sync.writeForOwner(account) {
                    jobs.get(account.uid, job.jobId)?.let { jobs.put(it.copy(lastError = "Receipt cleanup is pending. Use Refresh to retry.")) }
                } }
            }
        }
        retry
    } }

    private suspend fun process(account: SyncAccount, id: String) {
        var job = current(account, id)
        val expense = db.expenseDao().getByCloudId(account.localUserId, job.expenseCloudId) ?: return
        if (expense.isDeleted) { sync.writeForOwner(account) { jobs.deselect(account.uid, job.expenseCloudId) }; return }
        if (db.syncDao().forRecord(account.uid, "expenses", job.expenseCloudId).isNotEmpty()) return
        val accepted = db.syncDao().record(account.uid, "expenses", job.expenseCloudId) ?: return
        if (accepted.revision == 0L || accepted.isDeleted) return
        val acceptedPath = SyncMapping.decode("expenses", accepted.payload)["receiptObjectPath"] as String?
        if (acceptedPath != job.expectedAcceptedPath || job.phase == "PUBLICATION_PENDING") {
            store(account, job.copy(phase = "CONFLICT", blocked = true,
                lastError = "The accepted receipt changed. Review the server photo and your saved choice."))
            return
        }
        if (job.remove && acceptedPath == null) {
            store(account, job.copy(selected = false, phase = "DONE")); return
        }
        if (!job.remove && job.phase != "UPLOADED") {
            val original = File(requireNotNull(job.sourceLocalPath) { "The saved receipt is missing. Attach it again." })
            val target = File(directory(account), "${job.jobId}.source")
            val info = if (target.exists()) ReceiptChecks.inspect(target) else {
                val checked = ReceiptChecks.inspect(original)
                val partial = File(directory(account), "${job.jobId}.copy.part")
                try {
                    original.inputStream().use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= ReceiptChecks.MAX_BYTES) { "The receipt grew beyond the 10 MiB limit." }
                            output.write(buffer, 0, count)
                        }
                    } }
                    check(ReceiptChecks.inspect(partial) == checked) { "The saved photo changed while copying. Attach it again." }
                    check(partial.renameTo(target)) { "Could not keep a private receipt copy." }
                } finally { partial.delete() }
                checked
            }
            if (job.sourceSha256 != null) check(info.sha256 == job.sourceSha256) { "The saved receipt bytes changed. Attach it again." }
            job = job.copy(sourceLocalPath = target.absolutePath, sourceSha256 = info.sha256, byteCount = info.bytes,
                mimeType = info.mime, phase = "UPLOAD_PENDING")
            store(account, job)
            cloud.reserve(job) { sync.isAttached(account) }
            current(account, id)
            val verification = File(directory(account), "${job.jobId}.verify.part")
            try { cloud.upload(job, target, verification) { sync.isAttached(account) } } finally { verification.delete() }
            job = current(account, id).copy(phase = "UPLOADED", lastError = null)
            store(account, job)
        }
        // I published from the latest saved financial fields after earlier mutations had finished.
        db.withTransaction {
            sync.checkOwner(account)
            job = current(account, id)
            val latest = db.expenseDao().getByCloudId(account.localUserId, job.expenseCloudId) ?: return@withTransaction
            if (latest.isDeleted || db.syncDao().forRecord(account.uid, "expenses", job.expenseCloudId).isNotEmpty()) return@withTransaction
            val record = requireNotNull(db.syncDao().record(account.uid, "expenses", job.expenseCloudId))
            val values = SyncMapping.decode("expenses", record.payload).toMutableMap()
            check(values["receiptObjectPath"] == job.expectedAcceptedPath) { "The server receipt changed. Refresh and review it." }
            values["receiptObjectPath"] = if (job.remove) null else job.targetObjectPath
            val mutation = sync.saveLocalAndEnqueue(account, "expenses", job.expenseCloudId, values) {}
            jobs.put(job.copy(phase = "PUBLICATION_PENDING", publicationMutationId = mutation, lastError = null))
        }
    }

    suspend fun ensureDownloaded(account: SyncAccount, expenseId: Long): String? = withContext(Dispatchers.IO) {
        lock.withLock { clearPartialFiles(account); downloadInside(account, expenseId) }
    }

    private suspend fun downloadInside(account: SyncAccount, expenseId: Long): String? {
        sync.checkOwner(account)
        val row = db.expenseDao().getForUserById(account.localUserId, expenseId) ?: return null
        val id = row.cloudId ?: return row.receiptImageUri
        val path = row.receiptObjectPath ?: return row.receiptImageUri
        ReceiptChecks.pathJob(account.uid, id, path)
        val cached = jobs.cached(account.uid, id, path)
        if (cached?.localPath != null && runCatching { ReceiptChecks.inspect(File(cached.localPath)) }.isSuccess) return cached.localPath
        sync.writeForOwner(account) { jobs.cache(ReceiptCache(account.uid, account.localUserId, id, path, null, "DOWNLOAD_PENDING")) }
        val temp = File.createTempFile("download-", ".part", directory(account))
        var complete: File? = null
        try {
            val info = cloud.download(path, temp) { sync.isAttached(account) }
            complete = File(directory(account), "${path.substringAfterLast('/')}-${info.sha256}.cache")
            if (complete.exists() && runCatching { ReceiptChecks.inspect(complete) == info }.getOrDefault(false)) {
                temp.delete()
            } else {
                if (complete.exists()) check(complete.delete()) { "Could not replace an unreadable receipt cache." }
                check(temp.renameTo(complete)) { "Could not keep the completed receipt download." }
            }
            sync.writeForOwner(account) {
                val latest = db.expenseDao().getForUserById(account.localUserId, expenseId)
                if (latest?.cloudId == id && latest.receiptObjectPath == path) {
                    jobs.cache(ReceiptCache(account.uid, account.localUserId, id, path, complete.absolutePath))
                    if (jobs.selected(account.uid, id) == null) db.expenseDao().updateSynced(latest.copy(receiptImageUri = complete.absolutePath))
                }
            }
            return complete.absolutePath
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (sync.isAttached(account)) sync.writeForOwner(account) {
                if (db.expenseDao().getForUserById(account.localUserId, expenseId)?.receiptObjectPath == path) {
                    jobs.cache(ReceiptCache(account.uid, account.localUserId, id, path, null, "DOWNLOAD_PENDING", receiptFailure(error), !receiptTransient(error)))
                }
            }
            throw error
        } finally { temp.delete() }
    }

    suspend fun retryFailures(account: SyncAccount) = sync.writeForOwner(account) { jobs.retry(account.uid); jobs.retryDownloads(account.uid) }

    suspend fun resolveChoice(account: SyncAccount, expenseId: Long, keepLocal: Boolean, reviewedPath: String?) = sync.writeForOwner(account) {
        val expense = requireNotNull(db.expenseDao().getForUserById(account.localUserId, expenseId))
        check(expense.receiptObjectPath == reviewedPath) { "The receipt changed. Review it again." }
        val job = requireNotNull(jobs.selected(account.uid, requireNotNull(expense.cloudId)))
        check(job.phase == "CONFLICT" && db.syncDao().forRecord(account.uid, "expenses", expense.cloudId).isEmpty()) { "Review the expense sync conflict first." }
        jobs.deselect(account.uid, expense.cloudId)
        if (keepLocal) {
            val id = UUID.randomUUID().toString()
            jobs.put(job.copy(jobId = id, targetObjectPath = receiptObjectPath(account.uid, expense.cloudId, id), expectedAcceptedPath = reviewedPath,
                phase = "WAITING_FOR_EXPENSE", selected = true, blocked = false, publicationMutationId = null, lastError = null))
        } else db.expenseDao().updateSynced(expense.copy(receiptImageUri = reviewedPath?.let { jobs.cached(account.uid, expense.cloudId, it)?.localPath }))
    }
}

internal fun receiptTransient(error: Exception) = isTransient(error) || error is StorageException && error.errorCode in setOf(
    StorageException.ERROR_RETRY_LIMIT_EXCEEDED, StorageException.ERROR_UNKNOWN)

internal fun receiptFailure(error: Exception): String = when {
    receiptTransient(error) -> "Receipt saved locally. Connection unavailable; retry scheduled."
    error is StorageException -> "Receipt transfer unavailable. Check sign-in and protected Storage setup, then retry."
    error is IllegalArgumentException -> error.message ?: "The receipt image is unsupported. Attach it again."
    else -> "Receipt transfer could not finish. Refresh to review the server photo, or attach the saved photo again."
}

internal object ReceiptSyncs {
    @Volatile private var instance: ReceiptSync? = null
    fun get(context: Context): ReceiptSync = instance ?: synchronized(this) {
        instance ?: ReceiptSync(DatabaseProvider.get(context), PocketQuestSync.get(context),
            FirebaseReceiptCloud(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance()),
            File(context.filesDir, "account-receipts")).also { instance = it }
    }
}
