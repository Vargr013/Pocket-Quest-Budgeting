package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID

object ExpenseMapping {
    fun fields(expense: ExpenseEntity, categoryCloudId: String): Map<String, Any?> = mapOf(
        "categoryId" to categoryCloudId, "amountCents" to expense.amount,
        "date" to expense.date, "startTime" to expense.startTime, "endTime" to expense.endTime,
        "description" to expense.description, "receiptObjectPath" to expense.receiptObjectPath,
    )

    fun read(record: SyncRecord, categoryId: Long, old: ExpenseEntity?): ExpenseEntity {
        require(record.recordType == "expenses")
        val values = SyncMapping.decode("expenses", record.payload)
        val objectPath = values["receiptObjectPath"] as String?
        require(objectPath == null || objectPath.matches(Regex("users/${Regex.escape(record.uid)}/receipts/${Regex.escape(record.recordId)}/[a-f0-9-]{36}")))
        // I kept this device's photo path separate from the cloud reference (Google, 2026v).
        val localPhoto = old?.receiptImageUri?.takeIf { old.receiptObjectPath == objectPath }
        return ExpenseEntity(id = old?.id ?: 0, userId = record.userId, categoryId = categoryId,
            amount = values["amountCents"] as Long, date = values["date"] as String,
            startTime = values["startTime"] as String, endTime = values["endTime"] as String,
            description = values["description"] as String, receiptImageUri = localPhoto,
            cloudId = record.recordId, isDeleted = record.isDeleted, receiptObjectPath = objectPath)
    }
}

class ExpenseSync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator) {
    private val expenses = db.expenseDao()
    private val categories = CategorySync(db, sync)

    suspend fun save(account: SyncAccount, draft: ExpenseEntity, receiptChange: ReceiptChange? = null,
        reviewedReceiptPath: String? = null): Int = db.withTransaction {
        sync.checkOwner(account)
        require(draft.userId == account.localUserId)
        ExpenseChecks.requireValues(draft.amount, draft.date, draft.startTime, draft.endTime, draft.description)
        val old = if (draft.id == 0L) null else expenses.getForUserById(account.localUserId, draft.id)
            ?: return@withTransaction 0
        val category = db.categoryDao().getForUserById(account.localUserId, draft.categoryId)
            ?: return@withTransaction -1
        val failure = old?.cloudId?.let { db.syncDao().forRecord(account.uid, "expenses", it).firstOrNull()?.lastError }
        if (category.isArchived && (old?.categoryId != category.id || failure == EXPENSE_CATEGORY_UNAVAILABLE)) return@withTransaction -1
        val mappedCategory = categories.ensureMapped(account, category.id)
        val change = receiptChange ?: if (draft.receiptImageUri == old?.receiptImageUri) ReceiptChange.KEEP
            else if (draft.receiptImageUri == null) ReceiptChange.REMOVE else ReceiptChange.REPLACE
        if (receiptChange != null && change != ReceiptChange.KEEP) {
            if (old?.receiptObjectPath != reviewedReceiptPath) throw ReceiptReviewRequired()
        }
        val saved = draft.copy(cloudId = old?.cloudId ?: UUID.randomUUID().toString(), isDeleted = false,
            receiptObjectPath = old?.receiptObjectPath,
            receiptImageUri = if (change == ReceiptChange.KEEP) old?.receiptImageUri ?: draft.receiptImageUri else draft.receiptImageUri)
        sync.saveLocalAndEnqueue(account, "expenses", requireNotNull(saved.cloudId),
            ExpenseMapping.fields(saved, requireNotNull(mappedCategory.cloudId)), receiptMode = "KEEP") {
            if (old == null) expenses.insert(saved) else check(expenses.updateSynced(saved) == 1)
            if (change != ReceiptChange.KEEP) ReceiptState.choose(db, account, saved, change)
        }
        GamificationSync(db, sync).markActiveDay(account, todayIsoDate())
        1
    }

    suspend fun delete(account: SyncAccount, id: Long): Int = db.withTransaction {
        sync.checkOwner(account)
        val old = expenses.getForUserById(account.localUserId, id) ?: return@withTransaction 0
        val category = categories.ensureMapped(account, old.categoryId)
        val saved = old.copy(cloudId = old.cloudId ?: UUID.randomUUID().toString(), isDeleted = true)
        val record = db.syncDao().record(account.uid, "expenses", requireNotNull(saved.cloudId))
        val failedReference = db.syncDao().forRecord(account.uid, "expenses", saved.cloudId).firstOrNull()?.lastError == EXPENSE_CATEGORY_UNAVAILABLE
        // I used the accepted snapshot when discarding a definitively rejected category change.
        val fields = if (failedReference && record != null && record.revision > 0) SyncMapping.decode("expenses", record.payload)
            else ExpenseMapping.fields(saved, requireNotNull(category.cloudId))
        sync.saveLocalAndEnqueue(account, "expenses", saved.cloudId, fields, true, receiptMode = "KEEP") {
            check(expenses.updateSynced(saved) == 1)
            db.receiptJobDao().deselect(account.uid, saved.cloudId)
        }
        1
    }

    suspend fun importExisting(account: SyncAccount): Int {
        sync.checkOwner(account)
        var needsReview = 0
        // I persisted each mapping with its queue entry so an interrupted import could resume (Google, 2026j).
        db.categoryDao().getAllForUser(account.localUserId).forEach {
            try { categories.ensureMapped(account, it.id) }
            catch (_: IllegalArgumentException) { needsReview++ }
        }
        expenses.getAllForUser(account.localUserId).forEach { row ->
            try {
                db.withTransaction {
                    sync.checkOwner(account)
                    val current = expenses.getIncludingDeleted(account.localUserId, row.id) ?: return@withTransaction
                    if (current.cloudId != null) return@withTransaction
                    ExpenseChecks.requireValues(current.amount, current.date, current.startTime, current.endTime, current.description)
                    val category = categories.ensureMapped(account, current.categoryId)
                    val mapped = current.copy(cloudId = UUID.randomUUID().toString())
                    sync.saveLocalAndEnqueue(account, "expenses", requireNotNull(mapped.cloudId),
                        ExpenseMapping.fields(mapped, requireNotNull(category.cloudId)), mapped.isDeleted) {
                        check(expenses.updateSynced(mapped) == 1)
                    }
                }
            } catch (_: IllegalArgumentException) { needsReview++ }
        }
        return needsReview
    }

    suspend fun finishRecordImport(account: SyncAccount) = sync.writeForOwner(account) {
        if (!MonthlyGoalSync(db, sync).importReady(account)) return@writeForOwner
        if (!SavingsSync(db, sync).importReady(account)) return@writeForOwner
        if (db.syncDao().pending(account.uid).any { it.recordType in listOf("categories", "expenses", "monthlyGoals") }) return@writeForOwner
        val categoriesReady = db.categoryDao().getAllForUser(account.localUserId).all {
            it.cloudId != null && (db.syncDao().record(account.uid, "categories", it.cloudId)?.revision ?: 0) > 0
        }
        val expensesReady = expenses.getAllForUser(account.localUserId).all {
            it.cloudId != null && (db.syncDao().record(account.uid, "expenses", it.cloudId)?.revision ?: 0) > 0
        }
        if (categoriesReady && expensesReady) db.userDao().finishRecordImport(account.localUserId, account.uid)
    }

    suspend fun applyServer(record: SyncRecord) {
        val fields = SyncMapping.decode("expenses", record.payload)
        val category = db.categoryDao().getByCloudId(record.userId, fields["categoryId"] as String)
            ?: error("Download the expense's category before applying it.")
        val old = expenses.getByCloudId(record.userId, record.recordId)
        var downloaded = ExpenseMapping.read(record, category.id, old)
        db.receiptJobDao().supersedeDownloads(record.uid, record.recordId, if (record.isDeleted) null else downloaded.receiptObjectPath)
        val job = db.receiptJobDao().selected(record.uid, record.recordId)
        if (job != null) {
            if (record.isDeleted) db.receiptJobDao().deselect(record.uid, record.recordId)
            else {
                downloaded = downloaded.copy(receiptImageUri = if (job.remove) null else job.sourceLocalPath)
                if (downloaded.receiptObjectPath != job.expectedAcceptedPath && downloaded.receiptObjectPath != job.targetObjectPath) {
                    db.receiptJobDao().put(job.copy(phase = "CONFLICT", blocked = true,
                        lastError = "The server receipt changed. Review both choices before publishing."))
                }
            }
        } else if (downloaded.receiptObjectPath != null) {
            downloaded = downloaded.copy(receiptImageUri = db.receiptJobDao()
                .cached(record.uid, record.recordId, downloaded.receiptObjectPath)?.localPath ?: downloaded.receiptImageUri)
        }
        if (old == null) expenses.insert(downloaded) else check(expenses.updateSynced(downloaded) == 1)
    }

    suspend fun refresh(account: SyncAccount) {
        sync.refreshFromServer(account, "expenses")
        db.syncDao().records(account.uid).filter { it.recordType == "expenses" }.forEach { record ->
            sync.applyDownloaded(account, "expenses", record.recordId) { applyServer(it) }
        }
    }
}

object ExpenseSyncs {
    fun get(context: Context) = ExpenseSync(DatabaseProvider.get(context), PocketQuestSync.get(context))
}

internal class ReceiptReviewRequired : IllegalStateException("The receipt changed. Reopen this expense before changing its photo.")
