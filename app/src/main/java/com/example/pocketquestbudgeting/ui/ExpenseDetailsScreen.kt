package com.example.pocketquestbudgeting.ui

import androidx.activity.compose.BackHandler
import com.example.pocketquestbudgeting.data.CategorySyncs
import com.example.pocketquestbudgeting.data.ExpenseSyncs
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExpenseDetailsScreen(userId: Long, expenseId: Long?, onBack: () -> Unit, onEdit: (Long) -> Unit = {}) {
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

    LaunchedEffect(userId, expenseId, reload) {
        if (deleting) return@LaunchedEffect
        loading = true
        error = null
        expense = null
        try {
            if (expenseId != null && expenseId > 0) {
                val db = DatabaseProvider.get(context)

                db.invalidationTracker.createFlow("expenses", "categories").collect {
                    // I loaded by both IDs so another user's expense cannot open here.
                    val loaded = db.expenseDao().getForUserById(userId, expenseId)
                    categoryName = loaded?.let {
                        db.categoryDao().getForUserById(userId, it.categoryId)?.name
                    } ?: "Category unavailable"
                    expense = loaded
                    loading = false
                }
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketQuestBackButton(onBack, enabled = !deleting, contentDescription = "Back to History")
            Text("Expense details", style = MaterialTheme.typography.headlineSmall)
        }
        if (deleteTarget == null) deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val loaded = expense
        when {
            loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("Loading expense…")
            }
            error != null -> {
                Text(error!!)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            loaded == null -> Text("Expense unavailable. It may have been removed or may not belong to the current user.")
            else -> {
                ExpenseDetailsContent(
                    expense = loaded,
                    categoryName = categoryName,
                    enabled = !deleting,
                    onEdit = { onEdit(loaded.id) },
                    onDelete = {
                        deleteError = null
                        // I kept the confirmed expense separate from screen reloads.
                        deleteTarget = loaded
                    },
                    receipt = { SyncedExpenseReceipt(loaded, enabled = !deleting, thumbnailSize = 120.dp) },
                )
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
                                val count = ExpenseSyncs.get(context).delete(CategorySyncs.account(context, userId), target.id)
                                check(count in 0..1)
                                count == 1
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

@Composable
private fun ExpenseDetailsContent(
    expense: ExpenseEntity,
    categoryName: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    receipt: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Amount spent", style = MaterialTheme.typography.labelLarge)
                Text(formatRand(expense.amount), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(categoryName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        ExpenseDetailsCard("Date & time") {
            ExpenseDetailField("Date", expense.date)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExpenseDetailField("Start time", expense.startTime, Modifier.weight(1f))
                ExpenseDetailField("End time", expense.endTime, Modifier.weight(1f))
            }
        }
        ExpenseDetailsCard("Description") {
            Text(expense.description.ifBlank { "No description added." },
                style = MaterialTheme.typography.bodyLarge)
        }
        ExpenseDetailsCard("Receipt") { receipt() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Edit expense") }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete expense") }
        }
    }
}

@Composable
private fun ExpenseDetailsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ExpenseDetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun ExpenseDetailsPreview() {
    PocketQuestTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PocketQuestBackButton({})
                Text("Expense details", style = MaterialTheme.typography.headlineSmall)
            }
            ExpenseDetailsContent(
                expense = ExpenseEntity(userId = 1, categoryId = 1, amount = 45890,
                    date = "2026-10-26", startTime = "10:00", endTime = "10:30",
                    description = "Weekly groceries and household essentials"),
                categoryName = "Groceries", enabled = true, onEdit = {}, onDelete = {},
                receipt = { Text("No receipt") },
            )
        }
    }
}
