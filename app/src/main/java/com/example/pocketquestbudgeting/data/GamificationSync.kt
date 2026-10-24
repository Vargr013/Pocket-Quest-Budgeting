package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID

class GamificationSync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator) {
    private val dao = db.gamificationDao()

    suspend fun markActiveDay(account: SyncAccount, date: String) = db.withTransaction {
        sync.checkOwner(account)
        require(ExpenseChecks.validDate(date))
        dao.markActiveDay(account.localUserId, date)
        enqueueMissing(account, GamificationMapping.ACTIVITY, date, mapOf("date" to date))
        sync.checkOwner(account)
    }

    suspend fun saveItem(account: SyncAccount, draft: CustomWorthItemEntity, reviewed: CustomWorthItemEntity? = null): Long = db.withTransaction {
        sync.checkOwner(account)
        require(draft.userId == account.localUserId && !draft.isDeleted)
        GamificationMapping.requireItem(draft)
        if (reviewed != null) {
            require(reviewed.userId == account.localUserId && reviewed.id == draft.id && (reviewed.cloudId == null || reviewed.cloudId == draft.cloudId))
            check(!reviewed.isDeleted && dao.getCustomItem(account.localUserId, reviewed.id) == reviewed) { "This item changed. Reopen it before saving." }
        } else {
            require(draft.id == 0L)
            dao.getByCloudId(account.localUserId, requireNotNull(draft.cloudId))?.let {
                check(it.copy(id = 0) == draft) { "This item was already saved with different details." }
                importItem(account, it)
                return@withTransaction it.id
            }
        }
        var id = draft.id
        sync.saveLocalAndEnqueue(account, GamificationMapping.ITEMS, requireNotNull(draft.cloudId), GamificationMapping.fields(draft)) {
            if (reviewed == null) id = dao.insertCustomItem(draft) else check(dao.updateCustomItem(draft) == 1)
        }
        id
    }

    suspend fun removeItem(account: SyncAccount, reviewed: CustomWorthItemEntity) = db.withTransaction {
        sync.checkOwner(account)
        require(reviewed.userId == account.localUserId)
        check(dao.getCustomItem(account.localUserId, reviewed.id) == reviewed) { "This item changed. Reopen it before removing it." }
        if (!reviewed.isDeleted) {
            val mapped = importItem(account, reviewed)
            sync.saveLocalAndEnqueue(account, GamificationMapping.ITEMS, requireNotNull(mapped.cloudId), GamificationMapping.fields(mapped), deleted = true) {
                check(dao.deleteCustomItem(account.localUserId, mapped.id) == 1)
            }
        }
    }

    private suspend fun enqueueMissing(account: SyncAccount, type: String, id: String, fields: Map<String, Any?>) {
        val known = db.syncDao().record(account.uid, type, id)
        if (known == null || (known.revision == 0L && db.syncDao().forRecord(account.uid, type, id).isEmpty())) {
            sync.saveLocalAndEnqueue(account, type, id, fields) {}
        }
    }

    private suspend fun importItem(account: SyncAccount, row: CustomWorthItemEntity): CustomWorthItemEntity {
        val mapped = if (row.cloudId == null) row.copy(cloudId = UUID.randomUUID().toString()) else row
        GamificationMapping.requireItem(mapped)
        if (row != mapped) check(dao.updateCustomItem(mapped) == 1)
        val id = requireNotNull(mapped.cloudId)
        val known = db.syncDao().record(account.uid, GamificationMapping.ITEMS, id)
        if (known?.isDeleted == true) {
            if (db.syncDao().forRecord(account.uid, GamificationMapping.ITEMS, id).isEmpty()) applyServer(known)
            return mapped
        }
        enqueueMissing(account, GamificationMapping.ITEMS, id, GamificationMapping.fields(mapped))
        if (mapped.isDeleted && db.syncDao().forRecord(account.uid, GamificationMapping.ITEMS, id).none { it.isDeleted }) {
            sync.saveLocalAndEnqueue(account, GamificationMapping.ITEMS, id, GamificationMapping.fields(mapped), deleted = true) {}
        }
        return mapped
    }

    suspend fun importExisting(account: SyncAccount): Int = db.withTransaction {
        sync.checkOwner(account)
        var invalid = 0
        for (date in dao.getActiveDays(account.localUserId)) {
            if (!ExpenseChecks.validDate(date)) { invalid++; continue }
            enqueueMissing(account, GamificationMapping.ACTIVITY, date, mapOf("date" to date))
        }
        for (item in dao.getAllCustomItems(account.localUserId)) {
            try { GamificationMapping.requireItem(item.copy(cloudId = item.cloudId ?: "00000000-0000-0000-0000-000000000000")) }
            catch (_: IllegalArgumentException) { invalid++; continue }
            importItem(account, item)
        }
        sync.checkOwner(account)
        invalid
    }

    suspend fun applyServer(record: SyncRecord) {
        val fields = SyncMapping.decode(record.recordType, record.payload)
        when (record.recordType) {
            GamificationMapping.ACTIVITY -> {
                check(!record.isDeleted && record.revision == 1L)
                dao.markActiveDay(record.userId, fields["date"] as String)
            }
            GamificationMapping.ITEMS -> {
                val old = dao.getByCloudId(record.userId, record.recordId)
                check(old?.isDeleted != true || record.isDeleted) { "A removed item cannot be restored." }
                val item = GamificationMapping.item(record.userId, record.recordId, fields, record.isDeleted, old?.id ?: 0)
                if (old == null) dao.insertCustomItem(item) else if (old != item) check(dao.updateCustomItem(item) == 1)
            }
            else -> error("Unsupported gamification record.")
        }
    }

    suspend fun refresh(account: SyncAccount) {
        for (type in GamificationMapping.types) sync.refreshFromServer(account, type)
        sync.writeForOwner(account) {
            db.syncDao().records(account.uid).filter { it.recordType in GamificationMapping.types }.forEach { record ->
                sync.applyDownloaded(account, record.recordType, record.recordId) { applyServer(it) }
            }
        }
    }
}

object GamificationSyncs {
    fun get(context: Context) = GamificationSync(DatabaseProvider.get(context), PocketQuestSync.get(context))
}
