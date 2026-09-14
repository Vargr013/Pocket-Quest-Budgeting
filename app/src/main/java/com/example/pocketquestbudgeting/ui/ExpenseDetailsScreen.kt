package com.example.pocketquestbudgeting.ui

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

@Composable
fun ExpenseDetailsScreen(expenseId: Long?, onBack: () -> Unit, onEdit: (Long) -> Unit = {}) {
    val context = LocalContext.current
    var expense by remember(expenseId) { mutableStateOf<ExpenseEntity?>(null) }
    var categoryName by remember(expenseId) { mutableStateOf("Category unavailable") }
    var loading by remember(expenseId) { mutableStateOf(true) }
    var error by remember(expenseId) { mutableStateOf<String?>(null) }
    var reload by remember(expenseId) { mutableStateOf(0) }

    // I reloaded on return so saved edits show here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload++ }

    LaunchedEffect(expenseId, reload) {
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
        TextButton(onClick = onBack) { Text("Back to History") }
        Text("Expense details", style = MaterialTheme.typography.titleLarge)
        val loaded = expense
        when {
            loading -> Text("Loading expense…")
            error != null -> {
                Text(error!!)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            loaded == null -> Text("Expense unavailable. It may have been removed or may not belong to the current user.")
            else -> {
                TextButton(onClick = { onEdit(loaded.id) }) { Text("Edit") }
                Text(formatRand(loaded.amount), style = MaterialTheme.typography.titleLarge)
                Text("Date: ${loaded.date}")
                Text("Start time: ${loaded.startTime}")
                Text("End time: ${loaded.endTime}")
                Text("Description: ${loaded.description}")
                Text("Category: $categoryName")
                ExpenseReceipt(loaded.receiptImageUri)
            }
        }
    }
}
