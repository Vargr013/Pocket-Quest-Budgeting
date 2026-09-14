package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.activeUserId
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CancellationException
@Composable
fun HistoryScreen(onBack: () -> Unit, onExpenseSelected: (Long) -> Unit = {}) {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf<List<ExpenseEntity>>(emptyList()) }
    var categoryNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    // I reloaded on return so the list shows edited expenses.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload++ }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            val db = DatabaseProvider.get(context)
            // I reused the save flow's user lookup for both expenses and category names.
            val userId = db.activeUserId()
            val loadedExpenses = db.expenseDao().getForUser(userId)
            val loadedCategories = db.categoryDao().getForUser(userId)
            expenses = loadedExpenses
            categoryNames = loadedCategories.associate { it.id to it.name }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not load History. Please try again."
        } finally {
            loading = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
        Text("History", style = MaterialTheme.typography.titleLarge)
        when {
            loading -> Text("Loading expenses...")
            error != null -> {
                Text(error!!)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            expenses.isEmpty() -> Text("No expenses yet. Saved expenses will appear here.")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(expenses, key = { it.id }) { expense ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onExpenseSelected(expense.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatRand(expense.amount), style = MaterialTheme.typography.titleMedium)
                            Text(expense.date)
                            Text(expense.description)
                            Text(categoryNames[expense.categoryId] ?: "Category unavailable")
                        }
                        ExpenseReceipt(expense.receiptImageUri)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme {
        HistoryScreen(onBack = {})
    }
}