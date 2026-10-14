package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.CategoryEntity
import kotlinx.coroutines.CancellationException

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val GridLine = Color(0xFFE8E8E8)
private val Teal = Color(0xFF0F6B5C)

private val Rent = Color(0xFFC4A35A)
private val Groceries = Color(0xFF0F6B5C)
private val Transport = Color(0xFF7A9E7E)
private val Entertainment = Color(0xFFE07A5F)
private val Utilities = Color(0xFF3D7EA6)

private val CardShape = RoundedCornerShape(20.dp)

private data class SpendSegment(
    val name: String,
    val cents: Long,
    val color: Color,
)

private data class DaySpend(
    val label: String,
    val segments: List<SpendSegment>,
) {
    val totalCents: Long get() = segments.sumOf { it.cents }
}

private val CategoryColors = listOf(Rent, Groceries, Transport, Entertainment, Utilities)

@Composable
fun DailySpendingGraphScreen(userId: Long, onBack: () -> Unit) {
    // I saved the selected period so rotation does not reset the graph.
    var period by rememberSaveable { mutableStateOf("month") }
    val range = when (period) {
        "week" -> historyShortcutRange("week")
        "last_month" -> previousMonthRange()
        else -> historyShortcutRange("month")
    }
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    // I reloaded on resume so a new expense appears after returning from Add expense.
    var reload by remember { mutableStateOf(0) }
    var expenses by remember(userId, period, reload) {
        mutableStateOf<List<ExpenseEntity>>(emptyList())
    }
    var categories by remember(userId, period, reload) {
        mutableStateOf<List<CategoryEntity>>(emptyList())
    }
    var loading by remember(userId, period, reload) { mutableStateOf(!inspecting) }
    var error by remember(userId, period, reload) { mutableStateOf<String?>(null) }
    

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        reload++
    }

    // I keyed the loading effect to the account, period and reload value.
    LaunchedEffect(userId, period, reload) {
        if (inspecting) return@LaunchedEffect
        val requestedPeriod = period
        val requestedReload = reload
        try {
            val db = DatabaseProvider.get(context)

            val loadRange = when (requestedPeriod) {
                "week" -> historyShortcutRange("week")
                "last_month" -> previousMonthRange()
                else -> historyShortcutRange("month")
            }

            val rows = db.expenseDao().getForUserInRange(
                userId,
                loadRange.start,
                loadRange.end,
            )

            // I loaded category names in the same read so bar labels match the expense category ids.
            val loadedCategories = db.categoryDao().getForUser(userId)

            if (requestedPeriod != period || requestedReload != reload) {
                return@LaunchedEffect
            }

            expenses = rows
            categories = loadedCategories
            error = null
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (requestedPeriod != period || requestedReload != reload) {
                return@LaunchedEffect
            }
            error = "Could not load the graph. Please try again."
            loading = false
        }
    }

    // only rebuild when expenses or names or dates change
    val days = remember(expenses, categories, range, period) {
        buildChartDays(expenses, categories, range, period)
    }
    val maxY = (days.maxOfOrNull { it.totalCents } ?: 1L).toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            // I added Back so this screen can return to the menu.
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("Back", color = Teal)
            }
            Text(
                text = "Daily Spending Graph",
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilterChip(
                selected = period == "month",
                onClick = { period = "month" },
                label = { Text("This month") },
            )
            FilterChip(
                selected = period == "last_month",
                onClick = { period = "last_month" },
                label = { Text("Last month") },
            )
            FilterChip(
                selected = period == "week",
                onClick = { period = "week" },
                label = { Text("This week") },
            )
        }

        Text("Selected: $period", color = TextSecondary)
        Text("From ${range.start} to ${range.end}", color = TextSecondary)

        when {
            loading -> Text("Loading graph...", color = TextSecondary)
            error != null -> {
                Text(error ?: "", color = Color(0xFFD64545))
                TextButton(onClick = { reload++ }) {
                    Text("Retry")
                }
            }

            expenses.isEmpty() -> Text("No spending in this period.", color = TextSecondary)
            else -> {
                Text("Expenses in this period: ${expenses.size}", color = TextSecondary)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val chartScroll = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(52.dp)
                                    .padding(end = 4.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End,
                            ) {
                                val top = days.maxOfOrNull { it.totalCents } ?: 0L
                                listOf(top, top * 2 / 3, top / 3, 0L).forEach { cents ->
                                    Text(
                                        text = formatBudgetAmount(cents),
                                        fontSize = 10.sp,
                                        color = Color.Black,
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(1.dp, GridLine),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    repeat(4) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(GridLine),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .horizontalScroll(chartScroll),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    days.forEach { day ->
                                        StackedBar(
                                            day = day,
                                            maxY = maxY,
                                            modifier = Modifier
                                                .width(28.dp)
                                                .fillMaxHeight(),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp)
                                .horizontalScroll(chartScroll),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            days.forEach { day ->
                                Text(
                                    text = day.label,
                                    fontSize = 10.sp,
                                    color = Color.Black,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(28.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                LegendItem("Rent", Rent)
                                LegendItem("Entertainment", Entertainment)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                LegendItem("Groceries", Groceries)
                                LegendItem("Utilities", Utilities)
                            }
                            LegendItem("Transport", Transport)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Total this Week:",
                                fontSize = 14.sp,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "R2,290",
                                fontSize = 20.sp,
                                color = Color.Black,
                            )
                        }
                    }
                }

            }
        }
    }
}

private fun buildChartDays(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    range: HistoryDateRange,
    period: String,
): List<DaySpend> {
    val nameById = categories.associate { it.id to it.name }
    val names = expenses
        .map { nameById[it.categoryId] ?: "Unknown" }
        .distinct()
        .sorted()
    val colorByName = names.mapIndexed { index, name ->
        name to CategoryColors[index % CategoryColors.size]
    }.toMap()
    // Group rows with same date together
    val byDate = expenses.groupBy { it.date }
    // java.text means no extra imports needed
    val weekLabel = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
    val parseDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return datesInRange(range.start, range.end).map { date ->
        val label = if (period == "week") {
            val parsed = parseDate.parse(date)
            if (parsed != null) weekLabel.format(parsed) else date
        } else {
            date.takeLast(2).trimStart('0').ifEmpty { "0" }
        }
        val dayExpenses = byDate[date].orEmpty()
        val segments = dayExpenses
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val name = nameById[categoryId] ?: "Unknown"
                SpendSegment(
                    name = name,
                    // If same category is multiple times on one day, then add amounts in single block
                    cents = list.sumOf { it.amount },
                    color = colorByName[name] ?: CategoryColors[0],
                )
            }
            .filter { it.cents > 0 }
        DaySpend(label = label, segments = segments)
    }
}

@Composable
private fun StackedBar(
    day: DaySpend,
    maxY: Float,
    modifier: Modifier = Modifier,
) {
    val parts = day.segments.filter { it.cents > 0 }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (parts.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            val emptyWeight = ((maxY - day.totalCents.toFloat()).coerceAtLeast(0f)) / maxY
            if (emptyWeight > 0f) {
                Spacer(modifier = Modifier.weight(emptyWeight))
            }
            parts.asReversed().forEach { segment ->
                val w = (segment.cents.toFloat() / maxY).coerceAtLeast(0.001f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(w)
                        .background(segment.color)
                        .border(0.2.dp, Color.White),
                    )
                }

            }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = Color.Black)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DailySpendingGraphScreenPreview() {
    MaterialTheme {
        DailySpendingGraphScreen(userId = 0L, onBack = {})
    }
}