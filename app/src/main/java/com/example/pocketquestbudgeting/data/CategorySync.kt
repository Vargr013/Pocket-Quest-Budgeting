package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID

object CategoryMapping {
    fun fields(category: CategoryEntity): Map<String, Any?> = mapOf(
        "name" to category.name,
        "minimumCents" to category.minMonthlyBudget,
        "maximumCents" to category.maxMonthlyBudget,
    )

    fun read(record: SyncRecord, localId: Long = 0): CategoryEntity {
        require(record.recordType == "categories")
        val values = SyncMapping.decode("categories", record.payload)
        return CategoryEntity(id = localId, userId = record.userId, cloudId = record.recordId,
            name = values["name"] as String, minMonthlyBudget = values["minimumCents"] as Long,
            maxMonthlyBudget = values["maximumCents"] as Long, isArchived = record.isDeleted)
    }
}

class CategorySync(private val db: AppDatabase, private val sync: PocketQuestSyncCoordinator) {
    private val categories = db.categoryDao()

    suspend fun create(account: SyncAccount, name: String, minimum: Long, maximum: Long): Long? = db.withTransaction {
        val draft = CategoryEntity(userId = account.localUserId, name = name.trim(),
            minMonthlyBudget = minimum, maxMonthlyBudget = maximum, cloudId = UUID.randomUUID().toString())
        validate(draft)
        if (duplicate(draft)) return@withTransaction null
        var localId = 0L
        sync.saveLocalAndEnqueue(account, "categories", requireNotNull(draft.cloudId), CategoryMapping.fields(draft)) {
            localId = categories.insert(draft)
        }
        localId
    }

    suspend fun rename(account: SyncAccount, id: Long, name: String): CategoryChangeResult = change(account, id) {
        it.copy(name = name.trim())
    }

    suspend fun setBudgets(account: SyncAccount, id: Long, minimum: Long, maximum: Long): CategoryChangeResult = change(account, id) {
        it.copy(minMonthlyBudget = minimum, maxMonthlyBudget = maximum)
    }

    suspend fun archive(account: SyncAccount, id: Long): CategoryChangeResult = change(account, id) {
        // I retained the row because another device can still have an expense pointing to it.
        it.copy(isArchived = true)
    }

    private suspend fun change(account: SyncAccount, id: Long, edit: (CategoryEntity) -> CategoryEntity): CategoryChangeResult = db.withTransaction {
        val old = categories.getForUserById(account.localUserId, id)
            ?.takeUnless { it.isArchived } ?: return@withTransaction CategoryChangeResult.NOT_FOUND
        val draft = edit(old).copy(cloudId = old.cloudId ?: UUID.randomUUID().toString())
        validate(draft)
        if (duplicate(draft)) return@withTransaction CategoryChangeResult.DUPLICATE
        sync.saveLocalAndEnqueue(account, "categories", requireNotNull(draft.cloudId), CategoryMapping.fields(draft), draft.isArchived) {
            check(categories.updateSynced(draft) == 1)
        }
        CategoryChangeResult.SUCCESS
    }

    private fun validate(category: CategoryEntity) {
        require(category.name.isNotBlank()) { "Enter a category name." }
        requireValidBudgets(category.minMonthlyBudget, category.maxMonthlyBudget)
    }

    private suspend fun duplicate(draft: CategoryEntity) = !draft.isArchived && categories.getForUser(draft.userId).any {
        it.id != draft.id && it.name.trim().equals(draft.name, ignoreCase = true)
    }

    suspend fun ensureMapped(account: SyncAccount, id: Long): CategoryEntity = db.withTransaction {
        sync.checkOwner(account)
        val old = requireNotNull(db.categoryDao().getForUserById(account.localUserId, id)) { "Category unavailable." }
        if (old.cloudId != null && db.syncDao().record(account.uid, "categories", old.cloudId) != null) return@withTransaction old
        val mapped = old.copy(cloudId = old.cloudId ?: UUID.randomUUID().toString())
        sync.saveLocalAndEnqueue(account, "categories", requireNotNull(mapped.cloudId), CategoryMapping.fields(mapped), mapped.isArchived) {
            check(db.categoryDao().updateSynced(mapped) == 1)
        }
        mapped
    }

    suspend fun applyServer(record: SyncRecord) {
        val old = categories.getByCloudId(record.userId, record.recordId)
        val downloaded = CategoryMapping.read(record, old?.id ?: 0)
        if (old == null) categories.insert(downloaded) else check(categories.updateSynced(downloaded) == 1)
    }

    suspend fun refresh(account: SyncAccount, cloud: FirestoreSyncCloud) {
        val local = db.withTransaction {
            check(db.userDao().getById(account.localUserId)?.firebaseUid == account.uid)
            val user = requireNotNull(db.userDao().getById(account.localUserId))
            !user.categoriesInitialized && !user.importPending && categories.getAllForUser(account.localUserId).isEmpty()
        }
        sync.checkOwner(account)
        cloud.initializeCategories(account, requireNotNull(db.userDao().getById(account.localUserId)).username, local) {
            sync.isAttached(account)
        }
        sync.refreshFromServer(account, "categories")
        applyAvailable(account)
        sync.writeForOwner(account) { db.userDao().markCategoriesInitialized(account.localUserId) }
    }

    suspend fun applyAvailable(account: SyncAccount) {
        db.syncDao().records(account.uid).filter { it.recordType == "categories" }.forEach { record ->
            // I reused the stable mapping and left unresolved local edits alone (Google, 2026j).
            sync.applyDownloaded(account, "categories", record.recordId) { applyServer(it) }
        }
    }
}

object CategorySyncs {
    fun get(context: Context) = CategorySync(DatabaseProvider.get(context), PocketQuestSync.get(context))
    fun account(context: Context, userId: Long): SyncAccount = requireNotNull(AccountSessions.get(context).state.value.account)
        .also { check(it.localUserId == userId) { "Account changed. Please log in again." } }
}
