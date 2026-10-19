package com.example.pocketquestbudgeting.data

import java.util.UUID

internal object ReceiptState {
    suspend fun choose(db: AppDatabase, account: SyncAccount, expense: ExpenseEntity, change: ReceiptChange) {
        val id = requireNotNull(expense.cloudId)
        val jobs = db.receiptJobDao()
        jobs.deselect(account.uid, id)
        val jobId = UUID.randomUUID().toString()
        jobs.put(ReceiptJob(jobId, account.uid, account.localUserId, id, expense.receiptImageUri,
            receiptObjectPath(account.uid, id, jobId), expense.receiptObjectPath, remove = change == ReceiptChange.REMOVE))
    }

    suspend fun acknowledged(db: AppDatabase, sent: SyncOperation, applyReference: Boolean = true) {
        if (sent.recordType != "expenses") return
        val jobs = db.receiptJobDao()
        val expense = db.expenseDao().getByCloudId(sent.userId, sent.recordId) ?: return
        val path = SyncMapping.decode("expenses", sent.payload)["receiptObjectPath"] as String?
        val publication = jobs.jobs(sent.uid).firstOrNull { it.publicationMutationId == sent.mutationId }
        if (publication != null) {
            jobs.put(publication.copy(phase = "PUBLISHED", selected = false, lastError = null, blocked = false))
            if (path != null && publication.sourceLocalPath != null) {
                jobs.cache(ReceiptCache(sent.uid, sent.userId, sent.recordId, path, publication.sourceLocalPath))
            }
            // I rebased later local photo choices only after this exact publication was accepted.
            jobs.selected(sent.uid, sent.recordId)?.takeIf { applyReference }?.let { later ->
                if (later.expectedAcceptedPath == publication.expectedAcceptedPath) jobs.put(later.copy(expectedAcceptedPath = path))
            }
        }
        if (!applyReference) return
        jobs.supersedeDownloads(sent.uid, sent.recordId, if (sent.isDeleted) null else path)
        val selected = jobs.selected(sent.uid, sent.recordId)
        val local = if (selected != null) { if (selected.remove) null else selected.sourceLocalPath }
            else if (path != null) jobs.cached(sent.uid, sent.recordId, path)?.localPath
                ?: expense.receiptImageUri.takeIf { expense.receiptObjectPath == path }
            else expense.receiptImageUri.takeIf { publication == null }
        db.expenseDao().updateSynced(expense.copy(receiptObjectPath = path, receiptImageUri = local))
    }
}

internal fun receiptObjectPath(uid: String, expense: String, job: String) = "users/$uid/receipts/$expense/$job"
