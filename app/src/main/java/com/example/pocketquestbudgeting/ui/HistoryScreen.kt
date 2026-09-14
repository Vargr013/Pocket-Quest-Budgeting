package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.room.withTransaction
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@Composable
fun HistoryScreen(onBack: () -> Unit, onExpenseSelected: (Long) -> Unit = {}) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("all") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var draftStart by rememberSaveable { mutableStateOf("") }
    var draftEnd by rememberSaveable { mutableStateOf("") }
    var rangeError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    fun selectShortcut(shortcut: String) {
        val range = historyShortcutRange(shortcut)
        filter = shortcut
        startDate = range.start
        endDate = range.end
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (filter == "week" || filter == "month") selectShortcut(filter)
        reload++
    }

    // I kept each load separate so old rows cannot appear under new controls.
    var expenses by remember(filter, startDate, endDate, categoryId, search, reload) { mutableStateOf<List<ExpenseEntity>>(emptyList()) }
    var categories by remember(reload) { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var categoriesLoaded by remember(reload) { mutableStateOf(false) }
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }
    var hasExpenses by remember(filter, startDate, endDate, categoryId, search, reload) { mutableStateOf(false) }
    var loading by remember(filter, startDate, endDate, categoryId, search, reload) { mutableStateOf(true) }
    var error by remember(filter, startDate, endDate, categoryId, search, reload) { mutableStateOf<String?>(null) }

    LaunchedEffect(filter, startDate, endDate, categoryId, search, reload) {
        val requestedFilter = filter
        val requestedStart = startDate
        val requestedEnd = endDate
        val requestedCategory = categoryId
        val requestedSearch = search
        val requestedReload = reload

        fun isCurrentSelection() = requestedFilter == filter && requestedStart == startDate &&
            requestedEnd == endDate && requestedCategory == categoryId &&
            requestedSearch == search && requestedReload == reload

        try {
            val db = DatabaseProvider.get(context)
            val userId = db.activeUserId()
            val (loadedExpenses, anyExpenses, loadedCategories) = db.withTransaction {
                val dao = db.expenseDao()
                val currentCategories = db.categoryDao().getForUser(userId)
                val selectedCategory = requestedCategory?.takeIf { id -> currentCategories.any { it.id == id } }
                val rows = dao.getForUserFiltered(
                    userId, requestedStart.takeUnless { requestedFilter == "all" },
                    requestedEnd.takeUnless { requestedFilter == "all" }, selectedCategory, requestedSearch,
                )
                // I checked existence in the same read so the empty message matches the results.
                Triple(rows, rows.isNotEmpty() || dao.hasForUser(userId), currentCategories)
            }
            coroutineContext.ensureActive()
            // I checked the selection again in case controls changed before cancellation.
            if (!isCurrentSelection()) return@LaunchedEffect
            categories = loadedCategories
            categoriesLoaded = true
            if (categoryId != null && loadedCategories.none { it.id == categoryId }) {
                // I changed the selection before publishing rows for All categories.
                categoryId = null
                categoryNotice = "The previously selected category is no longer available. Showing All categories."
                return@LaunchedEffect
            }
            expenses = loadedExpenses
            hasExpenses = anyExpenses
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            if (!isCurrentSelection()) return@LaunchedEffect
            error = "Could not load History. Please try again."
            loading = false
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text("History", style = MaterialTheme.typography.titleLarge)
        }
        item {
            val label = when (filter) {
                "week" -> "Current week"
                "month" -> "Current month"
                "custom" -> "Custom range"
                else -> "All dates"
            }
            Text("Active filter: $label")
            if (filter != "all") Text("$startDate to $endDate (inclusive)")
            Row {
                TextButton(onClick = { selectShortcut("week") }) { Text("Current week") }
                TextButton(onClick = { selectShortcut("month") }) { Text("Current month") }
            }
            Text("Custom range (inclusive)")
            ExpenseDateSelector(
                value = draftStart, onValueChange = { draftStart = it },
                label = "Start date (YYYY-MM-DD)",
                modifier = Modifier.fillMaxWidth(),
            )
            ExpenseDateSelector(
                value = draftEnd, onValueChange = { draftEnd = it },
                label = "End date (YYYY-MM-DD)",
                modifier = Modifier.fillMaxWidth(),
            )
            rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row {
                TextButton(onClick = {
                    rangeError = historyRangeError(draftStart, draftEnd)
                    if (rangeError == null) {
                        startDate = draftStart.trim()
                        endDate = draftEnd.trim()
                        filter = "custom"
                    }
                }) { Text("Apply") }
                TextButton(onClick = {
                    filter = "all"
                    startDate = ""
                    endDate = ""
                    draftStart = ""
                    draftEnd = ""
                    rangeError = null
                    categoryId = null
                    search = ""
                    categoryNotice = null
                    categoryMenuOpen = false
                    reload++
                }) { Text("Reset") }
            }
        }
        item {
            Text("Category")
            Box {
                OutlinedButton(
                    onClick = { categoryMenuOpen = true },
                    enabled = categoriesLoaded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (categoryId == null) "All categories"
                    else categoryNames[categoryId]
                        ?: if (error != null) "Category unavailable to load" else "Loading category...")
                }
                DropdownMenu(expanded = categoryMenuOpen && categoriesLoaded, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("All categories") }, onClick = {
                        categoryId = null
                        categoryNotice = null
                        categoryMenuOpen = false
                    })
                    categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name) }, onClick = {
                            categoryId = category.id
                            categoryNotice = null
                            categoryMenuOpen = false
                        })
                    }
                }
            }
            categoryNotice?.let { Text(it) }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search descriptions") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            loading -> item { Text("Loading expenses...") }
            error != null -> item {
                Text(error!!)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            expenses.isEmpty() -> item {
                Text(if (hasExpenses) "No expenses match these filters."
                else "No expenses yet. Saved expenses will appear here.")
            }
            else -> items(expenses, key = { it.id }) { expense ->
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme { HistoryScreen(onBack = {}) }
}
