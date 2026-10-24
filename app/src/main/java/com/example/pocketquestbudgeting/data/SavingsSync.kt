package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction

class SavingsSync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator) {
    private val goals = db.savingsDao()

    suspend fun createGoal(account: SyncAccount, goal: SavingsGoalEntity, openingCents: Long = 0, openingDate: String? = null): Long = db.withTransaction {
        sync.checkOwner(account)
        require(goal.userId == account.localUserId)
        val id = goals.createGoal(goal, openingCents, openingDate)
        importExisting(account)
        id
    }

    suspend fun editGoal(account: SyncAccount, reviewed: SavingsGoalEntity, name: String, targetCents: Long, targetDate: String?, note: String?) = db.withTransaction {
        sync.checkOwner(account)
        require(reviewed.userId == account.localUserId)
        check(goals.getForUserById(account.localUserId, reviewed.id) == reviewed) { "The goal changed. Reopen it before saving." }
        val changed = reviewed.copy(name = name, targetCents = targetCents, targetDate = targetDate, note = note)
        SavingsChecks.requireGoal(changed)
        importExisting(account)
        sync.saveLocalAndEnqueue(account, SavingsMapping.GOALS, reviewed.cloudId, SavingsMapping.fields(changed)) {
            check(goals.editGoal(account.localUserId, reviewed.id, name, targetCents, targetDate, note) == 1)
        }
    }

    suspend fun updateGoalWithContribution(account: SyncAccount, reviewed: SavingsGoalEntity, name: String,
        targetCents: Long, targetDate: String?, note: String?, addition: SavingsContributionEntity? = null) = db.withTransaction {
        sync.checkOwner(account)
        require(reviewed.userId == account.localUserId)
        val changed = reviewed.copy(name = name, targetCents = targetCents, targetDate = targetDate, note = note)
        SavingsChecks.requireGoal(changed)
        if (addition != null) {
            SavingsChecks.requireContribution(addition)
            require(addition.userId == account.localUserId && addition.goalId == reviewed.id && addition.kind == SavingsChecks.ADDITION)
            // I checked an already saved addition first so retrying the same button press cannot add it twice.
            goals.contribution(account.localUserId, addition.businessId)?.let { old ->
                check(old.copy(id = 0) == addition) { "This addition was already saved with different values." }
                check(goals.getForUserById(account.localUserId, reviewed.id) == changed) {
                    "This addition is already saved. Reopen the goal to review its latest details."
                }
                importExisting(account)
                return@withTransaction
            }
        }
        val current = requireNotNull(goals.getForUserById(account.localUserId, reviewed.id))
        check(!current.isDeleted && (current == reviewed || (addition == null && current == changed))) {
            "The goal changed. Reopen it before saving."
        }
        if (current != changed) editGoal(account, current, name, targetCents, targetDate, note)
        if (addition != null) recordContribution(account, addition)
        importExisting(account)
        sync.checkOwner(account)
    }

    suspend fun removeGoal(account: SyncAccount, reviewed: SavingsGoalEntity) = db.withTransaction {
        sync.checkOwner(account)
        require(reviewed.userId == account.localUserId)
        check(goals.getForUserById(account.localUserId, reviewed.id) == reviewed)
        importExisting(account)
        if (!reviewed.isDeleted) sync.saveLocalAndEnqueue(account, SavingsMapping.GOALS, reviewed.cloudId, SavingsMapping.fields(reviewed), deleted = true) {
            check(goals.removeGoal(account.localUserId, reviewed.id) == 1)
        }
    }

    suspend fun recordContribution(account: SyncAccount, row: SavingsContributionEntity): Long = db.withTransaction {
        sync.checkOwner(account)
        require(row.userId == account.localUserId)
        val id = goals.recordContribution(row)
        importExisting(account)
        id
    }

    suspend fun recordEarned(account: SyncAccount, award: EarnedAchievementEntity): Long = db.withTransaction {
        sync.checkOwner(account)
        require(award.userId == account.localUserId)
        val id = db.earnedAchievementDao().recordEarned(award)
        enqueueMissing(account, SavingsMapping.AWARDS, award.awardKey, SavingsMapping.fields(award))
        id
    }

    private suspend fun enqueueMissing(account: SyncAccount, type: String, id: String, fields: Map<String, Any?>) {
        if (db.syncDao().record(account.uid, type, id) == null) {
            sync.saveLocalAndEnqueue(account, type, id, fields) { }
        }
    }

    suspend fun importExisting(account: SyncAccount): Int = db.withTransaction {
        sync.checkOwner(account)
        for (goal in goals.getForUser(account.localUserId, true)) {
            // I sent retained history before its goal tombstone, without restoring a removed local goal.
            enqueueMissing(account, SavingsMapping.GOALS, goal.cloudId, SavingsMapping.fields(goal))
            val known = db.syncDao().record(account.uid, SavingsMapping.GOALS, goal.cloudId)
            if (!goal.isDeleted && known != null && known.revision > 0 && !known.isDeleted
                && known.payload != SyncMapping.encode(SavingsMapping.GOALS, SavingsMapping.fields(goal))
                && db.syncDao().forRecord(account.uid, SavingsMapping.GOALS, goal.cloudId).isEmpty()) {
                sync.saveLocalAndEnqueue(account, SavingsMapping.GOALS, goal.cloudId, SavingsMapping.fields(goal)) { }
            }
            for (row in goals.contributions(account.localUserId, goal.id)) {
                enqueueMissing(account, SavingsMapping.CONTRIBUTIONS, row.businessId, SavingsMapping.fields(row, goal.cloudId))
            }
            if (goal.isDeleted) {
                val accepted = db.syncDao().record(account.uid, SavingsMapping.GOALS, goal.cloudId)
                val pending = db.syncDao().forRecord(account.uid, SavingsMapping.GOALS, goal.cloudId)
                if (accepted?.isDeleted != true && pending.none { it.isDeleted }) {
                    sync.saveLocalAndEnqueue(account, SavingsMapping.GOALS, goal.cloudId, SavingsMapping.fields(goal), deleted = true) { }
                }
            }
        }
        db.earnedAchievementDao().getForUser(account.localUserId).forEach {
            enqueueMissing(account, SavingsMapping.AWARDS, it.awardKey, SavingsMapping.fields(it))
        }
        sync.checkOwner(account)
        0
    }

    suspend fun importReady(account: SyncAccount): Boolean {
        if (db.syncDao().pending(account.uid).any { it.recordType in SavingsMapping.types }) return false
        for (goal in goals.getForUser(account.localUserId, true)) {
            if ((db.syncDao().record(account.uid, SavingsMapping.GOALS, goal.cloudId)?.revision ?: 0) == 0L) return false
            for (row in goals.contributions(account.localUserId, goal.id)) {
                if ((db.syncDao().record(account.uid, SavingsMapping.CONTRIBUTIONS, row.businessId)?.revision ?: 0) == 0L) return false
            }
        }
        return db.earnedAchievementDao().getForUser(account.localUserId).all {
            (db.syncDao().record(account.uid, SavingsMapping.AWARDS, it.awardKey)?.revision ?: 0) > 0
        }
    }

    suspend fun applyServer(record: SyncRecord) {
        val fields = SyncMapping.decode(record.recordType, record.payload)
        when (record.recordType) {
            SavingsMapping.GOALS -> goals.acceptServerGoal(SavingsMapping.goal(record.userId, record.recordId, fields, record.isDeleted))
            SavingsMapping.CONTRIBUTIONS -> {
                check(!record.isDeleted)
                val goal = requireNotNull(goals.getByCloudId(record.userId, fields["goalCloudId"] as String))
                goals.acceptServerHistory(listOf(SavingsMapping.contribution(record.userId, goal.id, record.recordId, fields)), reviewed = true)
            }
            SavingsMapping.AWARDS -> {
                check(!record.isDeleted)
                db.earnedAchievementDao().acceptServer(SavingsMapping.award(record.userId, record.recordId, fields))
            }
            else -> error("Unsupported savings record.")
        }
    }

    suspend fun refresh(account: SyncAccount) {
        importExisting(account)
        for (type in listOf(SavingsMapping.GOALS, SavingsMapping.CONTRIBUTIONS, SavingsMapping.AWARDS)) sync.refreshFromServer(account, type)
        sync.writeForOwner(account) {
            val records = db.syncDao().records(account.uid)
            records.filter { it.recordType == SavingsMapping.GOALS }.forEach { record ->
                sync.applyDownloaded(account, record.recordType, record.recordId) { applyServer(it) }
            }
            // I validated the complete downloaded history together; Firestore document order is not event order.
            val unresolvedGoals = db.syncDao().pending(account.uid).mapNotNull {
                when (it.recordType) {
                    SavingsMapping.GOALS -> it.recordId
                    SavingsMapping.CONTRIBUTIONS -> SyncMapping.decode(it.recordType, it.payload)["goalCloudId"] as String
                    else -> null
                }
            }.toSet()
            val rows = records.filter { it.recordType == SavingsMapping.CONTRIBUTIONS }.mapNotNull { record ->
                if (db.syncDao().forRecord(account.uid, record.recordType, record.recordId).isNotEmpty()) return@mapNotNull null
                check(!record.isDeleted)
                val fields = SyncMapping.decode(record.recordType, record.payload)
                // I retained the complete local draft while a contribution still needed publication or review.
                if (fields["goalCloudId"] in unresolvedGoals) return@mapNotNull null
                val goal = requireNotNull(goals.getByCloudId(account.localUserId, fields["goalCloudId"] as String))
                SavingsMapping.contribution(account.localUserId, goal.id, record.recordId, fields)
            }
            goals.acceptServerHistory(rows)
            records.filter { it.recordType == SavingsMapping.AWARDS }.forEach { record ->
                sync.applyDownloaded(account, record.recordType, record.recordId) { applyServer(it) }
            }
        }
    }
}

object SavingsSyncs {
    fun get(context: Context) = SavingsSync(DatabaseProvider.get(context), PocketQuestSync.get(context))
}
