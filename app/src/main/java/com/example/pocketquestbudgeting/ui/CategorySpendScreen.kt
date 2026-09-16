package com.example.pocketquestbudgeting.ui

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.pocketquestbudgeting.data.CategorySpendingTotal
import com.example.pocketquestbudgeting.data.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF3F4944)
private val Teal = Color(0xFF0F6B5C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySpendingSummaryScreen(userId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf("all") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var draftStart by rememberSaveable { mutableStateOf("") }
    var draftEnd by rememberSaveable { mutableStateOf("") }
    var rangeError by rememberSaveable { mutableStateOf<String?>(null) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    fun selectShortcut(shortcut: String) {
        val range = historyShortcutRange(shortcut)
        period = shortcut
        startDate = range.start
        endDate = range.end
        customOpen = false
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (period == "week" || period == "month") {
            val range = historyShortcutRange(period)
            startDate = range.start
            endDate = range.end
        }
        reload++
    }

    // I kept each load separate so old totals cannot appear under a new period.
    var categories by remember(period, startDate, endDate, reload) {
        mutableStateOf<List<CategorySpendingTotal>>(emptyList())
    }
    var totalCents by remember(period, startDate, endDate, reload) { mutableStateOf(0L) }
    var loading by remember(period, startDate, endDate, reload) { mutableStateOf(true) }
    var error by remember(period, startDate, endDate, reload) { mutableStateOf<String?>(null) }

    LaunchedEffect(userId, period, startDate, endDate, reload) {
        val requestedPeriod = period
        val requestedStart = startDate
        val requestedEnd = endDate
        val requestedReload = reload
        fun isCurrentSelection() = requestedPeriod == period && requestedStart == startDate &&
                requestedEnd == endDate && requestedReload == reload

        try {
            val db = DatabaseProvider.get(context)

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
                (failure is SQLiteException &&
                        failure.message.orEmpty().contains("integer overflow", ignoreCase = true))
            ) {
                "Spending exceeds the supported total. Choose a shorter period and try again."
            } else {
                "Could not load category spending. Please try again."
            }
            loading = false
        }
    }

    val periodLabel = when (period) {
        "week" -> "Current week"
        "month" -> "Current month"
        "custom" -> "Custom range"
        else -> "All dates"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Back", color = Teal)
                    }
                    Text(
                        text = "Spending summary",
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 72.dp)
                            .semantics { heading() },
                    )
                }
                Text(
                    text = "See where your money goes.",
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            Card(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Choose a period",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "all" to "All dates",
                            "week" to "Current week",
                            "month" to "Current month",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = period == value,
                                onClick = {
                                    if (value == "all") {
                                        period = "all"
                                        startDate = ""
                                        endDate = ""
                                        customOpen = false
                                    } else {
                                        selectShortcut(value)
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { customOpen = !customOpen },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(if (customOpen) "Hide custom dates" else "Choose custom dates")
                    }
                    if (customOpen) {
                        Text(
                            "Choose both dates, then apply. Your current results stay active until then.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        ExpenseDateSelector(
                            value = draftStart,
                            onValueChange = {
                                draftStart = it
                                rangeError = null
                            },
                            label = "Start date (YYYY-MM-DD)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ExpenseDateSelector(
                            value = draftEnd,
                            onValueChange = {
                                draftEnd = it
                                rangeError = null
                            },
                            label = "End date (YYYY-MM-DD)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        rangeError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                        Button(
                            onClick = {
                                rangeError = historyRangeError(draftStart, draftEnd)
                                if (rangeError == null) {
                                    startDate = draftStart.trim()
                                    endDate = draftEnd.trim()
                                    period = "custom"
                                    customOpen = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Apply date range")
                        }
                    }
                    Text(
                        "Active period: $periodLabel",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (period != "all") {
                        Text(
                            "$startDate to $endDate • Both dates included",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    TextButton(
                        onClick = {
                            period = "all"
                            startDate = ""
                            endDate = ""
                            draftStart = ""
                            draftEnd = ""
                            rangeError = null
                            customOpen = false
                            reload++
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Reset to all dates")
                    }
                }
            }
        }
        when {
            loading -> item {
                SpendingLoadState(modifier = Modifier.widthIn(max = 720.dp))
            }
            error != null -> item {
                SpendingLoadState(
                    message = error,
                    onRetry = { reload++ },
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
            else -> {
                item {
                    SpendingTotalCard(
                        totalCents,
                        periodLabel,
                        Modifier.widthIn(max = 720.dp),
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "By category",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "Each percentage is a share of spending in this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        if (categories.isEmpty()) {
                            Text(
                                "No categories yet. Create one from Manage categories.",
                                color = TextSecondary,
                            )
                        } else if (totalCents == 0L) {
                            Text(
                                "No spending in this period. Your categories are shown below.",
                                color = TextSecondary,
                            )
                        }
                    }
                }
                items(categories, key = { it.categoryId }) { category ->
                    CategorySpendingRow(
                        category,
                        totalCents,
                        Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CategorySpendingSummaryScreenPreview() {
    MaterialTheme {
        CategorySpendingSummaryScreen(userId = 0L, onBack = {})
    }
}
