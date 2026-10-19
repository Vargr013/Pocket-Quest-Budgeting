package com.example.pocketquestbudgeting.data

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import kotlinx.coroutines.CancellationException

internal interface ReceiptCloud {
    suspend fun reserve(job: ReceiptJob, attached: () -> Boolean)
    suspend fun upload(job: ReceiptJob, file: File, verification: File, attached: () -> Boolean)
    suspend fun download(path: String, file: File, attached: () -> Boolean): ReceiptInfo
    suspend fun retire(job: ReceiptJob, attached: () -> Boolean)
    suspend fun retired(account: SyncAccount): List<String>
    suspend fun delete(path: String, attached: () -> Boolean)
}

internal class FirebaseReceiptCloud(private val firestore: FirebaseFirestore, private val storage: FirebaseStorage) : ReceiptCloud {
    private fun owner(uid: String) = firestore.collection("users").document(uid)
    override suspend fun reserve(job: ReceiptJob, attached: () -> Boolean) {
        val guard = owner(job.uid).collection("receiptObjects").document(job.jobId)
        firestore.runTransaction { transaction ->
            check(attached()) { "Please log in again." }
            val existing = transaction.get(guard)
            val expense = transaction.get(owner(job.uid).collection("expenses").document(job.expenseCloudId))
            check(expense.exists() && expense.getBoolean("isDeleted") == false) { "The expense is no longer available." }
            if (existing.exists()) {
                check(existing.getString("objectPath") == job.targetObjectPath && existing.getString("state") == "STAGED") {
                    "The receipt state changed. Review the photo before retrying."
                }
            } else transaction.set(guard, mapOf("schemaVersion" to 1L, "expenseCloudId" to job.expenseCloudId,
                "objectPath" to job.targetObjectPath, "state" to "STAGED", "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(), "lastExpenseMutationId" to null))
        }.awaitSync()
        check(attached()) { "Please log in again." }
    }

    override suspend fun upload(job: ReceiptJob, file: File, verification: File, attached: () -> Boolean) {
        val reference = storage.reference.child(job.targetObjectPath)
        val existing = try { reference.metadata.awaitSync() } catch (error: StorageException) {
            if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
            null
        }
        check(attached()) { "Please log in again." }
        if (existing == null) {
            // I reused the same object after an interrupted reply instead of overwriting it (Google, 2026at).
            val task = reference.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType(job.mimeType).build())
            try { task.awaitSync() } catch (cancelled: CancellationException) { task.cancel(); throw cancelled }
            catch (error: Exception) {
                if (!attached()) throw error
                try { reference.metadata.awaitSync() } catch (_: Exception) { throw error }
            }
        }
        val info = download(job.targetObjectPath, verification, attached)
        check(info.sha256 == job.sourceSha256 && info.bytes == job.byteCount && info.mime == job.mimeType) {
            "The uploaded photo does not match this saved receipt. Attach it again."
        }
    }

    override suspend fun download(path: String, file: File, attached: () -> Boolean): ReceiptInfo {
        check(attached()) { "Please log in again." }
        val reference = storage.reference.child(path)
        val metadata = reference.metadata.awaitSync()
        require(metadata.sizeBytes in 1..ReceiptChecks.MAX_BYTES && metadata.contentType in listOf("image/jpeg", "image/png")) {
            "The remote receipt has an unsupported size or type."
        }
        check(attached()) { "Please log in again." }
        // I downloaded into a private temporary file before exposing a complete receipt (Google, 2026r).
        val task = reference.getFile(file)
        try { task.awaitSync() } catch (cancelled: CancellationException) { task.cancel(); throw cancelled }
        check(attached()) { "Please log in again." }
        val info = ReceiptChecks.inspect(file)
        require(info.bytes == metadata.sizeBytes && info.mime == metadata.contentType) { "The downloaded receipt did not match its metadata." }
        return info
    }

    override suspend fun retire(job: ReceiptJob, attached: () -> Boolean) {
        val guard = owner(job.uid).collection("receiptObjects").document(job.jobId)
        firestore.runTransaction { transaction ->
            check(attached()) { "Please log in again." }
            val current = transaction.get(guard)
            val expense = transaction.get(owner(job.uid).collection("expenses").document(job.expenseCloudId))
            if (current.exists() && current.getString("state") == "STAGED") {
                check(expense.getString("receiptObjectPath") != job.targetObjectPath) { "The receipt is still referenced." }
                transaction.update(guard, mapOf("state" to "RETIRED", "updatedAt" to FieldValue.serverTimestamp(), "lastExpenseMutationId" to null))
            }
        }.awaitSync()
    }

    override suspend fun retired(account: SyncAccount): List<String> = owner(account.uid).collection("receiptObjects")
        .whereEqualTo("state", "RETIRED").get(Source.SERVER).awaitSync().documents.map { requireNotNull(it.getString("objectPath")) }

    override suspend fun delete(path: String, attached: () -> Boolean) {
        check(attached()) { "Please log in again." }
        try { storage.reference.child(path).delete().awaitSync() }
        catch (error: StorageException) { if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error }
    }
}
