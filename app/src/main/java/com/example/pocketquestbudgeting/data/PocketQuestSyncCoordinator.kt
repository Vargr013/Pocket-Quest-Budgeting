package com.example.pocketquestbudgeting.data

import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class SyncRun { COMPLETE, RETRY, DETACHED }

class PocketQuestSyncCoordinator(
    private val db: AppDatabase,
    private val cloud: SyncCloud,
    private val isCurrent: (SyncAccount) -> Boolean,
    private val schedule: (SyncAccount) -> Unit,
) {
    private val runLock = Mutex()
    private val dao = db.syncDao()

    private suspend fun requireOwner(account: SyncAccount) {
        check(isCurrent(account)) { "Please log in again." }
        check(db.userDao().getById(account.localUserId)?.firebaseUid == account.uid) { "Account mapping changed." }
        check(isCurrent(account)) { "Please log in again." }
    }

    internal fun isAttached(account: SyncAccount) = isCurrent(account)
    internal suspend fun checkOwner(account: SyncAccount) = requireOwner(account)
    internal suspend fun writeForOwner(account: SyncAccount, write: suspend () -> Unit) = db.withTransaction {
        requireOwner(account)
        write()
        requireOwner(account)
    }

    suspend fun saveLocalAndEnqueue(
        account: SyncAccount, type: String, recordId: String,
        fields: Map<String, Any?>, deleted: Boolean = false,
        writeLocal: suspend () -> Unit,
    ): String {
        SyncMapping.checkKey(type, recordId)
        val payload = SyncMapping.encode(type, fields)
        val mutationId = UUID.randomUUID().toString()
        // I committed the local edit and its durable snapshot together (Google, 2026j).
        db.withTransaction {
            requireOwner(account)
            val record = dao.record(account.uid, type, recordId)
                ?: SyncRecord(account.uid, account.localUserId, type, recordId)
            check(!record.isDeleted) { "This record was deleted. Review the server version." }
            var pending = dao.forRecord(account.uid, type, recordId)
            // I replaced only a definite rejection; uncertain uploads kept their original snapshot.
            if ((type == "categories" && pending.firstOrNull()?.lastError == CATEGORY_NAME_TAKEN)
                || (type == "expenses" && pending.firstOrNull()?.lastError == EXPENSE_CATEGORY_UNAVAILABLE)) {
                dao.removeForReview(account.uid, type, recordId)
                pending = emptyList()
            }
            check(pending.none { it.state == "CONFLICT" || it.isDeleted }) { "Review this record before editing again." }
            val base = if (pending.isEmpty()) record.revision else null
            check(base != Long.MAX_VALUE) { "Revision limit reached." }
            writeLocal()
            dao.put(record)
            dao.insert(SyncOperation(mutationId = mutationId, uid = account.uid, userId = account.localUserId,
                recordType = type, recordId = recordId, payload = payload, isDeleted = deleted, baseRevision = base))
            requireOwner(account)
        }
        // Session recovery and periodic work cover interruption before this scheduling call.
        schedule(account)
        return mutationId
    }

    suspend fun retryPending(account: SyncAccount): SyncRun = runLock.withLock {
        if (!isCurrent(account)) return@withLock SyncRun.DETACHED
        val heads = dao.pending(account.uid).groupBy { it.recordType to it.recordId }.values.map { it.first() }.sortedBy { if (it.recordType == "categories") 0 else 1 }
        var retry = false
        for (head in heads) {
            var operation: SyncOperation? = head
            while (operation != null && operation.state == "PENDING") {
                val sent = operation
                if (!isCurrent(account)) return@withLock SyncRun.DETACHED
                try {
                    requireOwner(account)
                    check(sent.userId == account.localUserId && sent.baseRevision != null)
                    if (sent.recordType == "expenses") {
                        val categoryId = SyncMapping.decode("expenses", sent.payload)["categoryId"] as String
                        val dependency = dao.record(account.uid, "categories", categoryId)
                        val pendingCategory = dao.forRecord(account.uid, "categories", categoryId)
                        if (dependency == null || dependency.revision == 0L || pendingCategory.isNotEmpty()) {
                            retry = retry || pendingCategory.any { it.state == "PENDING" }
                            break
                        }
                    }
                    when (val result = cloud.send(sent) { isCurrent(account) }) {
                        is SendResult.Accepted -> acknowledge(account, sent, result.revision)
                        is SendResult.Conflict -> db.withTransaction {
                            requireOwner(account)
                            if (result.server == null) {
                                dao.mark(account.uid, sent.mutationId, "FAILED", "Server record is missing. Ask the project owner to check it; the local edit is retained.")
                            } else {
                                receiveInside(account, result.server)
                                dao.mark(account.uid, sent.mutationId, "CONFLICT", "Server changed. Review the local edit and server version.")
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    if (!isCurrent(account)) return@withLock SyncRun.DETACHED
                    val transient = isTransient(error)
                    db.withTransaction {
                        requireOwner(account)
                        dao.mark(account.uid, sent.mutationId, if (transient) "PENDING" else "FAILED", failureMessage(error))
                    }
                    retry = retry || transient
                    break
                }
                if (!isCurrent(account)) return@withLock SyncRun.DETACHED
                operation = dao.forRecord(account.uid, sent.recordType, sent.recordId).firstOrNull()
                if (operation?.mutationId == sent.mutationId) break
            }
        }
        if (retry) SyncRun.RETRY else SyncRun.COMPLETE
    }

    private suspend fun acknowledge(account: SyncAccount, sent: SyncOperation, revision: Long) = db.withTransaction {
        requireOwner(account)
        check(revision == requireNotNull(sent.baseRevision) + 1)
        // I removed only the exact sent operation, leaving newer edits in the queue.
        if (dao.acknowledge(account.uid, sent.mutationId) == 1) {
            val record = requireNotNull(dao.record(account.uid, sent.recordType, sent.recordId))
            if (record.revision < revision) dao.put(record.copy(revision = revision, payload = sent.payload,
                isDeleted = sent.isDeleted, lastMutationId = sent.mutationId, updatedAtMillis = null, deletedAtMillis = null))
            dao.forRecord(account.uid, sent.recordType, sent.recordId).firstOrNull()?.let { dao.setBase(it.sequence, revision) }
        }
        requireOwner(account)
    }

    suspend fun refreshFromServer(account: SyncAccount, type: String) {
        requireOwner(account)
        val downloaded = cloud.download(account, type)
        db.withTransaction {
            requireOwner(account)
            downloaded.forEach { receiveInside(account, it) }
            requireOwner(account)
        }
    }

    private suspend fun receiveInside(account: SyncAccount, remote: SyncRecord) {
        check(remote.uid == account.uid && remote.userId == account.localUserId)
        val old = dao.record(account.uid, remote.recordType, remote.recordId)
        if (remote.revision < (old?.revision ?: 0)) return
        check(old?.isDeleted != true || remote.isDeleted) { "A server tombstone cannot be restored implicitly." }
        if (remote.revision >= (old?.revision ?: 0)) dao.put(remote)
        // The queue remains the unresolved local snapshot; downloading never overwrites it.
    }

    suspend fun applyDownloaded(account: SyncAccount, type: String, id: String, writeLocal: suspend (SyncRecord) -> Unit): Boolean = db.withTransaction {
        requireOwner(account)
        if (dao.forRecord(account.uid, type, id).isNotEmpty()) return@withTransaction false
        val record = dao.record(account.uid, type, id) ?: return@withTransaction false
        writeLocal(record)
        requireOwner(account)
        true
    }

    suspend fun resolveConflict(account: SyncAccount, type: String, id: String, keepLocal: Boolean, reviewedRevision: Long,
        applyServer: suspend (SyncRecord) -> Unit) {
        db.withTransaction {
            requireOwner(account)
            val queue = dao.forRecord(account.uid, type, id)
            check(queue.firstOrNull()?.state == "CONFLICT") { "No conflict to resolve." }
            val server = requireNotNull(dao.record(account.uid, type, id))
            check(server.revision == reviewedRevision) { "The server version changed. Review it again." }
            check(!keepLocal || !server.isDeleted) { "Deleted records need a new ID; they cannot be restored." }
            if (!keepLocal) applyServer(server)
            dao.removeForReview(account.uid, type, id)
            if (keepLocal) {
                val latest = queue.last()
                dao.insert(latest.copy(sequence = 0, mutationId = UUID.randomUUID().toString(), baseRevision = server.revision,
                    state = "PENDING", attemptCount = 0, lastError = null))
            }
            requireOwner(account)
        }
        schedule(account)
    }

    suspend fun retryFailures(account: SyncAccount) {
        db.withTransaction { requireOwner(account); dao.retryFailures(account.uid) }
        schedule(account)
    }
}

internal fun isTransient(error: Exception): Boolean = error is FirebaseNetworkException || error is FirebaseTooManyRequestsException ||
    error is FirebaseFirestoreException && error.code in setOf(
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)

internal fun failureMessage(error: Exception): String = when {
    isTransient(error) -> "Connection unavailable. Saved locally; retry scheduled."
    error is FirebaseAuthException -> "Session unavailable. Log in again, then retry."
    error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Session expired. Log in again, then retry."
    error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Sync denied. Check account access and rules, then retry."
    error is CategoryNameTaken -> CATEGORY_NAME_TAKEN
    error is ExpenseCategoryUnavailable -> EXPENSE_CATEGORY_UNAVAILABLE
    else -> "Sync data could not be accepted. Review the saved operation and cloud schema before retrying."
}

internal const val CATEGORY_NAME_TAKEN = "Another device already uses this category name. Rename this category, then retry."

internal const val EXPENSE_CATEGORY_UNAVAILABLE = "This expense needs an active category. Edit it and choose a replacement; its entries and local photo are kept."
