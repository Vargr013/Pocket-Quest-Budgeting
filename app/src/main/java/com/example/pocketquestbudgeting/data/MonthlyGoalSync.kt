package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction

object MonthlyGoalMapping {
    fun fields(goal: BudgetGoalEntity): Map<String, Any?> = mapOf(
        "minimumCents" to goal.minimumAmount, "maximumCents" to goal.maximumAmount,
        "startDate" to goal.startDate, "endDate" to goal.endDate,
    )

    fun read(record: SyncRecord, old: BudgetGoalEntity?): BudgetGoalEntity {
        require(record.recordType == "monthlyGoals")
        val fields = SyncMapping.decode(record.recordType, record.payload)
        val start = fields["startDate"] as String
        val end = fields["endDate"] as String
        require(MonthlyGoalChecks.monthKey(start, end) == record.recordId)
        return BudgetGoalEntity(id = old?.id ?: 0, userId = record.userId,
            minimumAmount = fields["minimumCents"] as Long, maximumAmount = fields["maximumCents"] as Long,
            startDate = start, endDate = end, monthKey = record.recordId, isDeleted = record.isDeleted)
    }
}

class MonthlyGoalSync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator) {
    private val goals = db.budgetGoalDao()

    suspend fun saveForMonth(account: SyncAccount, start: String, end: String, minimum: Long, maximum: Long,
        reviewed: BudgetGoalEntity?, stillInMonth: () -> Boolean): Boolean = db.withTransaction {
        sync.checkOwner(account)
        // I checked after acquiring Room's transaction so a waiting draft could not cross a month boundary (Google, 2026j).
        if (!stillInMonth()) return@withTransaction false
        val key = MonthlyGoalChecks.requireValues(minimum, maximum, start, end)
        val old = goals.getIncludingDeletedForMonth(account.localUserId, start, end)
        check(old?.isDeleted != true) { "This month was deleted on the server and cannot be restored." }
        check(old?.id == reviewed?.id && old?.minimumAmount == reviewed?.minimumAmount && old?.maximumAmount == reviewed?.maximumAmount
            && old?.isDeleted == reviewed?.isDeleted) { "These goals changed while editing. Cancel and reopen them; your draft is kept until then." }
        val saved = BudgetGoalEntity(id = old?.id ?: 0, userId = account.localUserId,
            minimumAmount = minimum, maximumAmount = maximum, startDate = start, endDate = end, monthKey = key)
        sync.saveLocalAndEnqueue(account, "monthlyGoals", key, MonthlyGoalMapping.fields(saved), creating = old?.monthKey == null) {
            if (old == null) goals.insert(saved) else check(goals.updateSynced(saved) == 1)
            check(stillInMonth()) { "The month changed. Cancel and reopen these goals." }
        }
        true
    }

    suspend fun importExisting(account: SyncAccount): Int {
        sync.checkOwner(account)
        var needsReview = 0
        // I kept the newest exact-range row effective and retained its older duplicates unchanged.
        val effective = goals.getForUser(account.localUserId).distinctBy { it.startDate to it.endDate }
        for (row in effective) {
            try {
                db.withTransaction {
                    sync.checkOwner(account)
                    val current = requireNotNull(goals.getIncludingDeletedForMonth(account.localUserId, row.startDate, row.endDate))
                    val key = MonthlyGoalChecks.requireValues(current.minimumAmount, current.maximumAmount, current.startDate, current.endDate)
                    if (current.monthKey != null) { check(current.monthKey == key); return@withTransaction }
                    val mapped = current.copy(monthKey = key)
                    // I used revision zero for an unmapped legacy month so an existing cloud month needed explicit review (Google, 2026x).
                    sync.saveLocalAndEnqueue(account, "monthlyGoals", key, MonthlyGoalMapping.fields(mapped), mapped.isDeleted, creating = true) {
                        check(goals.updateSynced(mapped) == 1)
                    }
                }
            } catch (_: IllegalArgumentException) { needsReview++ }
        }
        return needsReview
    }

    suspend fun importReady(account: SyncAccount): Boolean {
        val effective = goals.getForUser(account.localUserId).distinctBy { it.startDate to it.endDate }
        return effective.all { row ->
            val key = try { MonthlyGoalChecks.requireValues(row.minimumAmount, row.maximumAmount, row.startDate, row.endDate) }
                catch (_: IllegalArgumentException) { return@all false }
            row.monthKey == key && (db.syncDao().record(account.uid, "monthlyGoals", key)?.revision ?: 0) > 0
        }
    }

    suspend fun applyServer(record: SyncRecord) {
        val values = SyncMapping.decode("monthlyGoals", record.payload)
        val old = goals.getByMonthKey(record.userId, record.recordId)
            ?: goals.getIncludingDeletedForMonth(record.userId, values["startDate"] as String, values["endDate"] as String)
        // I required import/review before replacing an unmapped local goal.
        check(old == null || old.monthKey != null) { "Import the local month before accepting a download." }
        val downloaded = MonthlyGoalMapping.read(record, old)
        if (old == null) goals.insert(downloaded) else check(goals.updateSynced(downloaded) == 1)
    }

    suspend fun refresh(account: SyncAccount) {
        importExisting(account)
        sync.refreshFromServer(account, "monthlyGoals")
        db.syncDao().records(account.uid).filter { it.recordType == "monthlyGoals" }.forEach { record ->
            sync.writeForOwner(account) {
                val values = SyncMapping.decode("monthlyGoals", record.payload)
                val local = goals.getIncludingDeletedForMonth(account.localUserId, values["startDate"] as String, values["endDate"] as String)
                // I retained invalid unmapped rows while letting other valid months download.
                if (local != null && local.monthKey == null) return@writeForOwner
                sync.applyDownloaded(account, "monthlyGoals", record.recordId) { applyServer(it) }
            }
        }
    }
}

object MonthlyGoalSyncs {
    fun get(context: Context) = MonthlyGoalSync(DatabaseProvider.get(context), PocketQuestSync.get(context))
}
