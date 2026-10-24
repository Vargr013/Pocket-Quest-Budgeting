package com.example.pocketquestbudgeting.data

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
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
        if (SyncMapping.immutable(operation.recordType)) return sendImmutableRecord(firestore, operation, stillCurrent)
        val base = requireNotNull(operation.baseRevision)
        require(base >= 0 && base < Long.MAX_VALUE)
        SyncMapping.checkKey(operation.recordType, operation.recordId)
        val fields = SyncMapping.decode(operation.recordType, operation.payload)
        SyncMapping.checkIdentity(operation.recordType, operation.recordId, fields)
        val fingerprint = SyncMapping.fingerprint(operation)
        val owner = firestore.collection("users").document(operation.uid)
        val record = owner.collection(operation.recordType).document(operation.recordId)
        val acknowledgement = owner.collection("mutations").document(operation.mutationId)
        val namesRef = owner.collection("categoryNames").document("index")
        // I checked the revision and wrote the retained receipt in one transaction (Google, 2026x).
        return try { firestore.runTransaction { transaction ->
            check(stillCurrent()) { "Session changed." }
            val receipt = transaction.get(acknowledgement)
            val current = transaction.get(record)
            val nameIndex = if (operation.recordType == "categories") transaction.get(namesRef) else null
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
                    if (operation.recordType == "expenses") {
                        // I checked the category inside the expense transaction so an archive could not race a new assignment (Google, 2026x).
                        val category = transaction.get(owner.collection("categories").document(fields["categoryId"] as String))
                        val retained = current.exists() && current.getString("categoryId") == fields["categoryId"]
                        if (!category.exists() || (category.getBoolean("isDeleted") == true && !operation.isDeleted && !retained)) {
                            throw ExpenseCategoryUnavailable()
                        }
                    }
                    val oldPhoto = if (operation.recordType == "expenses") current.getString("receiptObjectPath") else null
                    val newPhoto = if (operation.recordType == "expenses") fields["receiptObjectPath"] as String? else null
                    val photoChanges = operation.recordType == "expenses" && (oldPhoto != newPhoto || operation.isDeleted)
                    val photoGuards = if (photoChanges) listOfNotNull(oldPhoto, newPhoto.takeUnless { operation.isDeleted })
                        .distinct().associateWith { path ->
                            val jobId = ReceiptChecks.pathJob(operation.uid, operation.recordId, path)
                            val reference = owner.collection("receiptObjects").document(jobId)
                            reference to transaction.get(reference)
                        } else emptyMap()
                    photoGuards.forEach { (path, pair) ->
                        check(pair.second.exists()) { "Receipt state is missing. Review this photo before retrying." }
                        check(pair.second.getString("state") == if (path == oldPhoto) "PUBLISHED" else "STAGED") {
                            "The receipt state changed. Review this photo before retrying."
                        }
                    }
                    check(stillCurrent()) { "Session changed." }
                    val data = fields + mapOf(
                        "schemaVersion" to 1L, "revision" to base + 1, "lastMutationId" to operation.mutationId,
                        "updatedAt" to FieldValue.serverTimestamp(), "isDeleted" to operation.isDeleted,
                        "deletedAt" to if (operation.isDeleted) FieldValue.serverTimestamp() else null,
                    )
                    if (nameIndex != null) {
                        val names = categoryNames(nameIndex).toMutableMap()
                        val newKey = categoryNameKey(fields["name"] as String)
                        val oldKey = current.getString("name")?.let { categoryNameKey(it) }
                        if (!operation.isDeleted && names[newKey] != null && names[newKey] != operation.recordId) throw CategoryNameTaken()
                        if (oldKey != null && names[oldKey] == operation.recordId) names.remove(oldKey)
                        if (!operation.isDeleted) names[newKey] = operation.recordId
                        transaction.set(namesRef, mapOf("names" to names, "recordId" to operation.recordId,
                            "lastMutationId" to operation.mutationId))
                    }
                    transaction.set(record, data)
                    photoGuards.forEach { (path, pair) ->
                        transaction.update(pair.first, mapOf("state" to if (path == oldPhoto) "RETIRED" else "PUBLISHED",
                            "updatedAt" to FieldValue.serverTimestamp(), "lastExpenseMutationId" to operation.mutationId))
                    }
                    transaction.set(acknowledgement, mapOf(
                        "recordType" to operation.recordType, "recordId" to operation.recordId,
                        "baseRevision" to base, "revision" to base + 1, "fingerprint" to fingerprint,
                    ))
                    SendResult.Accepted(base + 1)
                }
            }
        }.awaitSync()
        } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (error.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            check(stillCurrent()) { "Session changed." }
            // I distinguished a raced revision from a schema/access failure using server reads (Google, 2026x).
            val receipt = acknowledgement.get(Source.SERVER).awaitSync()
            if (receipt.exists() && receipt.getString("fingerprint") == fingerprint && receipt.getLong("revision") == base + 1) {
                check(stillCurrent()) { "Session changed." }
                SendResult.Accepted(base + 1)
            } else {
                val current = record.get(Source.SERVER).awaitSync()
                check(stillCurrent()) { "Session changed." }
                val remote = if (current.exists()) current.toSyncRecord(operation.uid, operation.userId, operation.recordType) else null
                if (remote != null && (remote.revision != base || remote.isDeleted)) SendResult.Conflict(remote)
                else {
                    if (operation.recordType == "expenses") {
                        val category = owner.collection("categories").document(fields["categoryId"] as String).get(Source.SERVER).awaitSync()
                        check(stillCurrent()) { "Session changed." }
                        val retained = current.exists() && current.getString("categoryId") == fields["categoryId"]
                        if (!category.exists() || (category.getBoolean("isDeleted") == true && !operation.isDeleted && !retained)) {
                            throw ExpenseCategoryUnavailable()
                        }
                    }
                    throw error
                }
            }
        }
    }

    suspend fun initializeCategories(account: SyncAccount, username: String, localEmpty: Boolean, stillCurrent: () -> Boolean) {
        val owner = firestore.collection("users").document(account.uid)
        // I read the server first; an offline-empty cache cannot decide account defaults (Google, 2026l).
        val existing = owner.collection("categories").get(Source.SERVER).awaitSync()
        check(stillCurrent()) { "Session changed." }
        val operations = defaultCategories.map { (id, name) ->
            SyncOperation(mutationId = UUID.randomUUID().toString(), uid = account.uid, userId = account.localUserId,
                recordType = "categories", recordId = id, payload = SyncMapping.encode("categories",
                    mapOf("name" to name, "minimumCents" to 0L, "maximumCents" to 0L)), isDeleted = false, baseRevision = 0)
        }
        val indexRef = owner.collection("categoryNames").document("index")
        // I made setup and all four reserved defaults atomic so concurrent devices reuse one set (Google, 2026x).
        try { firestore.runTransaction { transaction ->
            check(stillCurrent()) { "Session changed." }
            val setup = transaction.get(owner)
            val index = transaction.get(indexRef)
            val defaults = operations.map { transaction.get(owner.collection("categories").document(it.recordId)) }
            val seed = localEmpty && existing.isEmpty && !index.exists() && setup.getBoolean("categoriesInitialized") != true
                && defaults.none { it.exists() }
            if (!index.exists()) {
                check(existing.isEmpty) { "Existing cloud categories need their name index reconciled before setup." }
                transaction.set(indexRef, mapOf("names" to if (seed) defaultCategories.entries.associate { categoryNameKey(it.value) to it.key } else emptyMap<String, String>(),
                    "recordId" to if (seed) "defaults" else "initialized", "lastMutationId" to ""))
            }
            if (seed) operations.forEach { operation ->
                transaction.set(owner.collection("categories").document(operation.recordId), SyncMapping.decode("categories", operation.payload) + mapOf(
                    "schemaVersion" to 1L, "revision" to 1L, "lastMutationId" to operation.mutationId,
                    "updatedAt" to FieldValue.serverTimestamp(), "isDeleted" to false, "deletedAt" to null))
                transaction.set(owner.collection("mutations").document(operation.mutationId), mapOf(
                    "recordType" to "categories", "recordId" to operation.recordId, "baseRevision" to 0L, "revision" to 1L,
                    "fingerprint" to SyncMapping.fingerprint(operation)))
            }
            check(stillCurrent()) { "Session changed." }
            transaction.set(owner, mapOf("schemaVersion" to 1L, "username" to (setup.getString("username") ?: username),
                "categoriesInitialized" to true))
        }.awaitSync()
        } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (error.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            check(stillCurrent()) { "Session changed." }
            // I checked completed setup after a competing initializer won; stale default writes stay denied.
            val completed = owner.get(Source.SERVER).awaitSync().getBoolean("categoriesInitialized") == true
            val indexed = indexRef.get(Source.SERVER).awaitSync().exists()
            if (!completed || !indexed) throw error
            check(stillCurrent()) { "Session changed." }
        }
    }

    override suspend fun download(account: SyncAccount, type: String): List<SyncRecord> {
        SyncMapping.checkType(type)
        // I requested server data including tombstones; an empty cache cannot prove absence (Google, 2026l).
        return firestore.collection("users").document(account.uid).collection(type).get(Source.SERVER).awaitSync()
            .documents.map { it.toSyncRecord(account.uid, account.localUserId, type) }
    }
}

internal fun DocumentSnapshot.toSyncRecord(uid: String, userId: Long, type: String): SyncRecord {
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
    SyncMapping.checkIdentity(type, id, business)
    return SyncRecord(uid, userId, type, id, revision, SyncMapping.encode(type, business), deleted,
        updated.toDate().time, deletedAt?.toDate()?.time, mutationId)
}

internal suspend fun <T> Task<T>.awaitSync(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Cloud request failed."))
    }
}

private fun categoryNames(snapshot: DocumentSnapshot): Map<String, String> {
    if (!snapshot.exists()) return emptyMap()
    val names = snapshot.get("names") as? Map<*, *> ?: error("Invalid category name index.")
    return names.entries.associate { (key, value) -> (key as String) to (value as String) }
}

internal class CategoryNameTaken : IllegalStateException("Category name is already in use.")

internal val defaultCategories = linkedMapOf(
    "default-groceries" to "Groceries", "default-transport" to "Transport",
    "default-entertainment" to "Entertainment", "default-rent" to "Rent",
)

// I prefixed the lowercase key so display names could keep reserved punctuation (Google, 2020).
internal fun categoryNameKey(name: String) = "n:" + name.lowercase(Locale.ROOT)

internal class ExpenseCategoryUnavailable : IllegalStateException("Expense needs an active category.")
