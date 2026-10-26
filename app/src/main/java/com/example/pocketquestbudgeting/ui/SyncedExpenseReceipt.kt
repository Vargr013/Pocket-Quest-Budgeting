package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.data.CategorySyncs
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.ReceiptSyncs
import com.example.pocketquestbudgeting.data.SyncWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SyncedExpenseReceipt(expense: ExpenseEntity, enabled: Boolean = true, thumbnailSize: Dp = 56.dp) {
    val context = LocalContext.current
    val db = remember(context) { DatabaseProvider.get(context) }
    val scope = rememberCoroutineScope()
    val account = runCatching { CategorySyncs.account(context, expense.userId) }.getOrNull() ?: return
    val jobs by remember(account.uid) { db.receiptJobDao().observe(account.uid) }.collectAsState(emptyList())
    val job = jobs.firstOrNull { it.expenseCloudId == expense.cloudId && it.selected }
    var downloaded by remember(account.uid, expense.id, expense.receiptObjectPath) { mutableStateOf<String?>(null) }
    var message by remember(account.uid, expense.id, expense.receiptObjectPath) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(account, expense.id, expense.receiptObjectPath, retry) {
        if (expense.receiptObjectPath != null) {
            downloading = true
            try {
                downloaded = ReceiptSyncs.get(context).ensureDownloaded(account, expense.id)
                message = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Receipt unavailable offline or while Storage is unavailable. Retry when connected." }
            finally { downloading = false }
        }
    }
    Column {
        val local = if (job != null) expense.receiptImageUri else downloaded ?: expense.receiptImageUri
        if (local != null) ExpenseReceipt(local, enabled, thumbnailSize)
        else if (expense.receiptObjectPath == null && job == null) Text("No receipt")
        else Text(if (downloading) "Downloading receipt…" else "Receipt saved; photo download pending.")
        if (job != null) Text(job.lastError ?: if (job.remove) "Receipt removal pending." else "Photo saved locally; transfer pending.")
        message?.let { Text(it) }
        if (job != null && downloaded != null && downloaded != local) {
            Text("Current online receipt")
            ExpenseReceipt(downloaded, enabled, thumbnailSize)
        }
        if (job?.phase == "CONFLICT") {
            if (downloaded == null) Text(if (expense.receiptObjectPath == null) "No server photo" else "Download the server photo before choosing it.")
            listOf(false to "Use reviewed server photo", true to "Use saved local photo").forEach { (keep, label) ->
                TextButton(enabled = enabled && (keep || expense.receiptObjectPath == null || downloaded != null), onClick = { scope.launch {
                    try {
                        ReceiptSyncs.get(context).resolveChoice(account, expense.id, keep, expense.receiptObjectPath)
                        SyncWork.scheduleRetry(context, account)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "The expense changed. Refresh and review its sync conflict first." }
                } }) { Text(label) }
            }
        }
        if (message != null || job?.lastError != null) TextButton(enabled = enabled, onClick = { scope.launch {
            ReceiptSyncs.get(context).retryFailures(account)
            SyncWork.scheduleRetry(context, account)
            retry++
        } }) { Text("Retry receipt") }
    }
}
