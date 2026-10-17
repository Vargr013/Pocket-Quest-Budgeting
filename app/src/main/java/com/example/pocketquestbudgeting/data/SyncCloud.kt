package com.example.pocketquestbudgeting.data

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SendResult {
    data class Accepted(val revision: Long) : SendResult
    data class Conflict(val server: SyncRecord?) : SendResult
}

interface SyncCloud {
    suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult
    suspend fun download(account: SyncAccount, type: String): List<SyncRecord>
}

class FirestoreSyncCloud(private val firestore: FirebaseFirestore) : SyncCloud {
    override suspend fun send(operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
        val base = requireNotNull(operation.baseRevision)
        require(base >= 0 && base < Long.MAX_VALUE)
        SyncMapping.checkKey(operation.recordType, operation.recordId)
        val fields = SyncMapping.decode(operation.recordType, operation.payload)
        val fingerprint = SyncMapping.fingerprint(operation)
        val owner = firestore.collection("users").document(operation.uid)
        val record = owner.collection(operation.recordType).document(operation.recordId)
        val acknowledgement = owner.collection("mutations").document(operation.mutationId)
        // I checked the revision and wrote the retained receipt in one transaction (Google, 2026x).
        return firestore.runTransaction { transaction ->
            check(stillCurrent()) { "Session changed." }
            val receipt = transaction.get(acknowledgement)
            val current = transaction.get(record)
            if (receipt.exists()) {
                check(receipt.getString("fingerprint") == fingerprint && receipt.getLong("revision") == base + 1) {
                    "Mutation receipt does not match the queued operation."
                }
                SendResult.Accepted(base + 1)
            } else {
                val remote = if (current.exists()) current.toSyncRecord(operation.uid, operation.userId, operation.recordType) else null
                if ((remote?.revision ?: 0) != base || remote?.isDeleted == true) {
                    SendResult.Conflict(remote)
                } else {
                    check(stillCurrent()) { "Session changed." }
                    val data = fields + mapOf(
                        "schemaVersion" to 1L, "revision" to base + 1, "lastMutationId" to operation.mutationId,
                        "updatedAt" to FieldValue.serverTimestamp(), "isDeleted" to operation.isDeleted,
                        "deletedAt" to if (operation.isDeleted) FieldValue.serverTimestamp() else null,
                    )
                    transaction.set(record, data)
                    transaction.set(acknowledgement, mapOf(
                        "recordType" to operation.recordType, "recordId" to operation.recordId,
                        "baseRevision" to base, "revision" to base + 1, "fingerprint" to fingerprint,
                    ))
                    SendResult.Accepted(base + 1)
                }
            }
        }.awaitSync()
    }

    override suspend fun download(account: SyncAccount, type: String): List<SyncRecord> {
        SyncMapping.checkKey(type, "check")
        // I requested server data including tombstones; an empty cache cannot prove absence (Google, 2026l).
        return firestore.collection("users").document(account.uid).collection(type).get(Source.SERVER).awaitSync()
            .documents.map { it.toSyncRecord(account.uid, account.localUserId, type) }
    }
}

private fun DocumentSnapshot.toSyncRecord(uid: String, userId: Long, type: String): SyncRecord {
    SyncMapping.checkKey(type, id)
    val data = requireNotNull(data)
    check(data["schemaVersion"] == 1L)
    val revision = data["revision"] as? Long ?: error("Invalid cloud revision.")
    check(revision > 0)
    val deleted = data["isDeleted"] as? Boolean ?: error("Invalid deletion marker.")
    val mutationId = data["lastMutationId"] as? String ?: error("Missing mutation ID.")
    val updated = data["updatedAt"] as? Timestamp ?: error("Missing server timestamp.")
    val deletedAt = data["deletedAt"] as? Timestamp
    check(data.containsKey("deletedAt") && (data["deletedAt"] == null || data["deletedAt"] is Timestamp))
    check(deleted == (deletedAt != null))
    val business = data - setOf("schemaVersion", "revision", "lastMutationId", "updatedAt", "isDeleted", "deletedAt")
    return SyncRecord(uid, userId, type, id, revision, SyncMapping.encode(type, business), deleted,
        updated.toDate().time, deletedAt?.toDate()?.time, mutationId)
}

internal suspend fun <T> Task<T>.awaitSync(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Cloud request failed."))
    }
}
