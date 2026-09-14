package com.example.pocketquestbudgeting.ui

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.pocketquestbudgeting.data.CategorySpendingTotal
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val Teal = Color(0xFF0F6B5C)

@Composable
fun CategorySpendingSummaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf("all") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var draftStart by rememberSaveable { mutableStateOf("") }
    var draftEnd by rememberSaveable { mutableStateOf("") }
    var rangeError by rememberSaveable { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    fun selectShortcut(shortcut: String) {
        val range = historyShortcutRange(shortcut)
        period = shortcut
        startDate = range.start
        endDate = range.end
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (period == "week" || period == "month") selectShortcut(period)
        reload++
    }

    // I kept each load separate so old totals cannot appear under a new period.
    var categories by remember(period, startDate, endDate, reload) {
        mutableStateOf<List<CategorySpendingTotal>>(emptyList())
    }
    var totalCents by remember(period, startDate, endDate, reload) { mutableStateOf(0L) }
    var loading by remember(period, startDate, endDate, reload) { mutableStateOf(true) }
    var error by remember(period, startDate, endDate, reload) { mutableStateOf<String?>(null) }

    LaunchedEffect(period, startDate, endDate, reload) {
        val requestedPeriod = period
        val requestedStart = startDate
        val requestedEnd = endDate
        val requestedReload = reload
        fun isCurrentSelection() = requestedPeriod == period && requestedStart == startDate &&
            requestedEnd == endDate && requestedReload == reload

        try {
            val db = DatabaseProvider.get(context)
            val userId = db.activeUserId()
            val rows = db.categoryDao().getSpendingForUser(
                userId,
                requestedStart.takeUnless { requestedPeriod == "all" },
                requestedEnd.takeUnless { requestedPeriod == "all" },
            )
            val total = categorySpendingTotal(rows)
            coroutineContext.ensureActive()
            if (!isCurrentSelection()) return@LaunchedEffect
            categories = rows
            totalCents = total
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            coroutineContext.ensureActive()
            if (!isCurrentSelection()) return@LaunchedEffect
            error = if (failure is ArithmeticException ||
                (failure is SQLiteException && failure.message.orEmpty().contains("integer overflow", ignoreCase = true))) {
                "Spending exceeds the supported total. Choose a shorter period and try again."
            } else {
                "Could not load category spending. Please try again."
            }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ScreenBg).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Category Spending Summary", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
        item {
            val label = when (period) {
                "week" -> "Current week"
                "month" -> "Current month"
                "custom" -> "Custom range"
                else -> "All dates"
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Active period: $label", color = TextPrimary)
                if (period != "all") Text("$startDate to $endDate (inclusive)", color = TextSecondary)
                TextButton(onClick = { selectShortcut("week") }) { Text("Current week") }
                TextButton(onClick = { selectShortcut("month") }) { Text("Current month") }
                Text("Custom range (inclusive)", color = TextPrimary)
                ExpenseDateSelector(
                    value = draftStart, onValueChange = { draftStart = it },
                    label = "Start date (YYYY-MM-DD)", modifier = Modifier.fillMaxWidth(),
                )
                ExpenseDateSelector(
                    value = draftEnd, onValueChange = { draftEnd = it },
                    label = "End date (YYYY-MM-DD)", modifier = Modifier.fillMaxWidth(),
                )
                rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = {
                    rangeError = historyRangeError(draftStart, draftEnd)
                    if (rangeError == null) {
                        startDate = draftStart.trim()
                        endDate = draftEnd.trim()
                        period = "custom"
                    }
                }) { Text("Apply custom range") }
                TextButton(onClick = {
                    period = "all"
                    startDate = ""
                    endDate = ""
                    draftStart = ""
                    draftEnd = ""
                    rangeError = null
                    reload++
                }) { Text("Reset to All dates") }
            }
        }
        when {
            loading -> item { Text("Loading category spending...", color = TextSecondary) }
            error != null -> item {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reload++ }) { Text("Retry") }
            }
            else -> {
                item {
                    SpendingCard {
                        Text("Total spent", color = TextSecondary)
                        Text(formatRand(totalCents), style = MaterialTheme.typography.headlineSmall, color = Teal)
                    }
                }
                if (categories.isEmpty()) {
                    item { Text("No categories yet. Create categories from Manage categories.", color = TextSecondary) }
                } else if (totalCents == 0L) {
                    item { Text("No spending in this period.", color = TextSecondary) }
                }
                items(categories, key = { it.categoryId }) { category ->
                    CategorySpendingRow(category, totalCents)
                }
            }
        }
    }
}

@Composable
private fun SpendingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CategorySpendingSummaryScreenPreview() {
    MaterialTheme { CategorySpendingSummaryScreen(onBack = {}) }
}
