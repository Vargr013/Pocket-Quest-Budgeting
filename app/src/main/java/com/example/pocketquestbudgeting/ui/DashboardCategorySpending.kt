package com.example.pocketquestbudgeting.ui

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.pocketquestbudgeting.data.CategorySpendingTotal
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardCategorySpending(onCategories: () -> Unit, onSummary: () -> Unit) {
    val context = LocalContext.current
    var month by remember { mutableStateOf(historyShortcutRange("month")) }
    var reload by remember { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        month = historyShortcutRange("month")
        reload++
    }

    // I cleared the previous result when the month or refresh changed.
    var categories by remember(month, reload) { mutableStateOf<List<CategorySpendingTotal>>(emptyList()) }
    var totalCents by remember(month, reload) { mutableStateOf(0L) }
    var loading by remember(month, reload) { mutableStateOf(true) }
    var error by remember(month, reload) { mutableStateOf<String?>(null) }

    LaunchedEffect(month, reload) {
        val requestedMonth = month
        val requestedReload = reload
        fun isCurrentLoad() = requestedMonth == month && requestedReload == reload
        try {
            val db = DatabaseProvider.get(context)
            val userId = db.activeUserId()
            val rows = db.categoryDao().getSpendingForUser(userId, requestedMonth.start, requestedMonth.end)
            val total = categorySpendingTotal(rows)
            coroutineContext.ensureActive()
            if (!isCurrentLoad()) return@LaunchedEffect
            categories = rows
            totalCents = total
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            coroutineContext.ensureActive()
            if (!isCurrentLoad()) return@LaunchedEffect
            error = if (failure is ArithmeticException ||
                (failure is SQLiteException && failure.message.orEmpty().contains("integer overflow", ignoreCase = true))) {
                "This month's spending exceeds the supported total. Open Spending summary to choose a shorter period."
            } else {
                "Could not load category spending. Please try again."
            }
            loading = false
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Category spending", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text("${month.start} to ${month.end} • Both dates included",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSummary, modifier = Modifier.heightIn(min = 48.dp)) { Text("Spending summary") }
            TextButton(onClick = onCategories, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage categories") }
        }
        when {
            loading -> SpendingLoadState()
            error != null -> SpendingLoadState(message = error, onRetry = { reload++ })
            else -> {
                SpendingTotalCard(totalCents, "Current month")
                Text("Share of this month's spending", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (categories.isEmpty()) {
                    Text("No categories yet. Add one using Manage categories.")
                } else if (totalCents == 0L) {
                    Text("No spending this month. Your categories are shown below.")
                }
                categories.forEach { category -> CategorySpendingRow(category, totalCents) }
            }
        }
    }
}
