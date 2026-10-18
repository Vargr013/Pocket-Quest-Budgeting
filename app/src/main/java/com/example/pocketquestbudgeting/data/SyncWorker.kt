package com.example.pocketquestbudgeting.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

object SyncWork {
    private fun name(uid: String) = "pocket-sync-$uid"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun input(account: SyncAccount) = workDataOf("uid" to account.uid, "userId" to account.localUserId)

    fun onSignedIn(context: Context, account: SyncAccount) {
        val manager = WorkManager.getInstance(context)
        // I kept periodic recovery for a process exit between Room commit and enqueue (Google, 2026w).
        manager.enqueueUniquePeriodicWork(name(account.uid) + "-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setInputData(input(account))
                .setConstraints(network).addTag(name(account.uid)).build())
        scheduleRetry(context, account)
    }

    fun scheduleRetry(context: Context, account: SyncAccount) {
        // I retained refresh requests made while the previous worker finishes (Google, 2026am).
        WorkManager.getInstance(context).enqueueUniqueWork(name(account.uid), ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInputData(input(account)).setConstraints(network)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(name(account.uid)).build())
    }

    fun onSignedOut(context: Context, account: SyncAccount) {
        WorkManager.getInstance(context).cancelAllWorkByTag(name(account.uid))
    }
}

object PocketQuestSync {
    @Volatile private var instance: PocketQuestSyncCoordinator? = null
    fun get(context: Context): PocketQuestSyncCoordinator = instance ?: synchronized(this) {
        instance ?: PocketQuestSyncCoordinator(DatabaseProvider.get(context), FirestoreSyncCloud(FirebaseFirestore.getInstance()),
            { AccountSessions.get(context).isCurrent(it) }, { SyncWork.scheduleRetry(context, it) })
            .also { instance = it }
    }
}

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val session = AccountSessions.get(applicationContext)
        if (!session.state.value.checked) session.restore().join()
        val account = session.state.value.account ?: return Result.success()
        if (account.uid != inputData.getString("uid") || account.localUserId != inputData.getLong("userId", -1)) return Result.success()
        val coordinator = PocketQuestSync.get(applicationContext)
        return try {
            val expenses = ExpenseSyncs.get(applicationContext)
            val importNeedsReview = expenses.importExisting(account)
            val result = coordinator.retryPending(account)
            if (result == SyncRun.DETACHED) return Result.success()
            if (result == SyncRun.RETRY) return Result.retry()
            val categoryCloud = FirestoreSyncCloud(FirebaseFirestore.getInstance())
            CategorySyncs.get(applicationContext).refresh(account, categoryCloud)
            expenses.refresh(account)
            expenses.finishRecordImport(account)
            val types = DatabaseProvider.get(applicationContext).syncDao().records(account.uid).map { it.recordType }.distinct().filter { it != "categories" && it != "expenses" }
            for (type in types) {
                if (!session.isCurrent(account)) return Result.success()
                coordinator.refreshFromServer(account, type)
            }
            if (importNeedsReview > 0) Result.failure(workDataOf("message" to
                "Some local records need review before import. Correct their category, amount, date, times or description; other valid records can still sync."))
            else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (error: Exception) {
            if (!session.isCurrent(account)) Result.success()
            else if (isTransient(error)) Result.retry()
            else Result.failure(Data.Builder().putString("message", failureMessage(error)).build())
        }
    }
}
