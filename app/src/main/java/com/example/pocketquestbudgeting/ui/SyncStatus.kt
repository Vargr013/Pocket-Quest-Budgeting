package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.example.pocketquestbudgeting.data.MonthlyGoalSyncs
import com.example.pocketquestbudgeting.data.SavingsSyncs
import com.example.pocketquestbudgeting.data.SavingsMapping
import com.example.pocketquestbudgeting.data.ExpenseSyncs
import com.example.pocketquestbudgeting.data.CategorySyncs
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.PocketQuestSync
import com.example.pocketquestbudgeting.data.SyncAccount
import com.example.pocketquestbudgeting.data.SyncRecord
import com.example.pocketquestbudgeting.data.SyncWork
import com.example.pocketquestbudgeting.data.ReceiptSyncs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SyncStatus(account: SyncAccount) {
    val context = LocalContext.current.applicationContext
    val db = remember(context) { DatabaseProvider.get(context) }
    val operations by remember(account.uid) { db.syncDao().observe(account.uid) }.collectAsState(emptyList())
    val receiptJobs by remember(account.uid) { db.receiptJobDao().observe(account.uid) }.collectAsState(emptyList())
    val receiptDownloads by remember(account.uid) { db.receiptJobDao().observeCaches(account.uid) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val work by remember(account.uid) { androidx.work.WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("pocket-sync-${account.uid}") }.collectAsState(emptyList())
    var review by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(emptyList<SyncRecord>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val issues = operations.filter { it.state != "PENDING" }
    LaunchedEffect(review, operations) { if (review) server = db.syncDao().records(account.uid) }
    Column {
        Text(if (operations.isEmpty()) "Saved changes are queued automatically."
            else "Saved locally: ${operations.size} pending; ${issues.size} need review.")
        val pendingPhotos = receiptJobs.count { it.selected } + receiptDownloads.count { it.state == "DOWNLOAD_PENDING" }
        val photoErrors = receiptJobs.count { it.lastError != null } + receiptDownloads.count { it.lastError != null }
        if (pendingPhotos > 0 || photoErrors > 0) Text("Receipts: $pendingPhotos pending; $photoErrors need attention. Open the expense for its photo status.")
        Row {
            TextButton(onClick = { SyncWork.scheduleRetry(context, account) }) { Text("Refresh") }
            if (issues.isNotEmpty() || photoErrors > 0) {
                TextButton(onClick = { review = true }) { Text("Review sync") }
                TextButton(onClick = { scope.launch {
                    try {
                        ReceiptSyncs.get(context).retryFailures(account)
                        PocketQuestSync.get(context).retryFailures(account)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Could not retry. Please check your account and try again." }
                } }) { Text("Retry failed") }
            }
        }
    }
    work.lastOrNull()?.outputData?.getString("message")?.let { Text(it) }
    message?.let { Text(it) }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Saved sync issues") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            issues.forEach { issue ->
                val latest = operations.last { it.recordType == issue.recordType && it.recordId == issue.recordId }
                val remote = server.firstOrNull { it.recordType == issue.recordType && it.recordId == issue.recordId }
                Text("${issue.recordType}/${issue.recordId}: ${issue.lastError}\nLatest local edit: ${latest.payload}")
                if (issue.state == "CONFLICT") {
                    Text("Server revision ${remote?.revision}: ${remote?.payload}\nDeleted on server: ${remote?.isDeleted}")
                    if (remote != null && issue.recordType in listOf("categories", "expenses", "monthlyGoals") + SavingsMapping.types) TextButton(onClick = { scope.launch {
                        try {
                            PocketQuestSync.get(context).resolveConflict(account, issue.recordType, issue.recordId, false, remote.revision) {
                                if (issue.recordType == "categories") CategorySyncs.get(context).applyServer(it)
                                else if (issue.recordType == "expenses") ExpenseSyncs.get(context).applyServer(it)
                                else if (issue.recordType == "monthlyGoals") MonthlyGoalSyncs.get(context).applyServer(it)
                                else SavingsSyncs.get(context).applyServer(it)
                            }
                            review = false
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { message = "The record changed. Review it again before retrying." }
                    } }) { Text("Use reviewed server record") }
                    if (remote != null && !remote.isDeleted && !SavingsMapping.immutable(issue.recordType)) TextButton(onClick = { scope.launch {
                        try {
                            PocketQuestSync.get(context).resolveConflict(account, issue.recordType, issue.recordId, true, remote.revision) {}
                            review = false
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { message = "The record changed. Review it again before retrying." }
                    } }) { Text("Use reviewed local edit") }
                }
            }
            Text("Unresolved local edits stay saved. Deleted server records cannot be restored here.")
        } }, confirmButton = { TextButton(onClick = { review = false }) { Text("Close") } })
}
