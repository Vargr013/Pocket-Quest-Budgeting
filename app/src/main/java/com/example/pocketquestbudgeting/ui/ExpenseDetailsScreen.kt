package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.BackHandler
import androidx.room.withTransaction
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExpenseDetailsScreen(expenseId: Long?, onBack: () -> Unit, onEdit: (Long) -> Unit = {}) {
    val context = LocalContext.current
    var expense by remember(expenseId) { mutableStateOf<ExpenseEntity?>(null) }
    var categoryName by remember(expenseId) { mutableStateOf("Category unavailable") }
    var loading by remember(expenseId) { mutableStateOf(true) }
    var error by remember(expenseId) { mutableStateOf<String?>(null) }
    var reload by remember(expenseId) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var deleteTarget by remember(expenseId) { mutableStateOf<ExpenseEntity?>(null) }
    var deleting by remember(expenseId) { mutableStateOf(false) }
    var deleteError by remember(expenseId) { mutableStateOf<String?>(null) }

    BackHandler {
        if (!deleting) {
            if (deleteTarget != null) deleteTarget = null else onBack()
        }
    }

    // I reloaded on return so saved edits show here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload++ }

    LaunchedEffect(expenseId, reload) {
        if (deleting) return@LaunchedEffect
        loading = true
        error = null
        expense = null
        try {
            if (expenseId != null && expenseId > 0) {
                val db = DatabaseProvider.get(context)
                val userId = db.activeUserId()
                // I loaded by both IDs so another user's expense cannot open here.
                val loaded = db.expenseDao().getForUserById(userId, expenseId)
                categoryName = loaded?.let {
                    db.categoryDao().getForUserById(userId, it.categoryId)?.name
                } ?: "Category unavailable"
                expense = loaded
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not load this expense. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, enabled = !deleting) { Text("Back to History") }
        Text("Expense details", style = MaterialTheme.typography.titleLarge)
        if (deleteTarget == null) deleteError?.let { Text(it) }
        val loaded = expense
        when {
            loading -> Text("Loading expense…")
            error != null -> {
                Text(error!!)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            loaded == null -> Text("Expense unavailable. It may have been removed or may not belong to the current user.")
            else -> {
                TextButton(onClick = { onEdit(loaded.id) }, enabled = !deleting) { Text("Edit") }
                TextButton(onClick = {
                    deleteError = null
                    // I kept the confirmed expense separate from screen reloads.
                    deleteTarget = loaded
                }, enabled = !deleting) { Text("Delete") }
                Text(formatRand(loaded.amount), style = MaterialTheme.typography.titleLarge)
                Text("Date: ${loaded.date}")
                Text("Start time: ${loaded.startTime}")
                Text("End time: ${loaded.endTime}")
                Text("Description: ${loaded.description}")
                Text("Category: $categoryName")
                ExpenseReceipt(loaded.receiptImageUri, enabled = !deleting)
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            properties = DialogProperties(
                dismissOnBackPress = !deleting,
                dismissOnClickOutside = !deleting,
            ),
            title = { Text("Delete expense?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.description)
                    Text(formatRand(target.amount))
                    Text("Date: ${target.date}")
                    Text("This cannot be undone.")
                    if (deleting) Text("Deleting expense…")
                    deleteError?.let { Text(it) }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !deleting) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        if (deleting) return@TextButton
                        deleting = true
                        deleteError = null
                        scope.launch {
                            val deleted = try {
                                val db = DatabaseProvider.get(context)
                                db.withTransaction {
                                    // I used the owner from the scoped load and checked the row count.
                                    val count = db.expenseDao().deleteForUser(target.userId, target.id)
                                    check(count in 0..1)
                                    count == 1
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                deleteError = "Could not delete this expense. Please try again or cancel."
                                deleting = false
                                return@launch
                            }
                            if (deleted) {
                                // I retained receipts so shared files cannot be removed by this action.
                                expense = null
                                deleteTarget = null
                                onBack()
                            } else {
                                expense = null
                                deleteTarget = null
                                deleteError = "Expense unavailable. It may already have been deleted. Return to History."
                                reload++
                                deleting = false
                            }
                        }
                    },
                ) { Text("Delete") }
            },
        )
    }
}
