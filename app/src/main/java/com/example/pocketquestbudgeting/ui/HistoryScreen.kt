package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.room.withTransaction
import com.example.pocketquestbudgeting.data.CategoryEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.ExpenseEntity
import com.example.pocketquestbudgeting.data.activeUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val CardWhite = Color(0xFFFFFFFF)
private val RowBg = Color(0xFFE8EEEC)
private val FieldBgSoft = Color(0x69E8EEEC)
private val Teal = Color(0xFF0F6B5C)
private val ButtonTeal = Color(0xFF2A9D8F)
private val ErrorRed = Color(0xFFD64545)
private val CardShape = RoundedCornerShape(20.dp)

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
    var expenses by remember(filter, startDate, endDate, categoryId, search, reload) {
        mutableStateOf<List<ExpenseEntity>>(emptyList())
    }
    var categories by remember(reload) { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var categoriesLoaded by remember(reload) { mutableStateOf(false) }
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }
    var hasExpenses by remember(filter, startDate, endDate, categoryId, search, reload) {
        mutableStateOf(false)
    }
    var loading by remember(filter, startDate, endDate, categoryId, search, reload) {
        mutableStateOf(true)
    }
    var error by remember(filter, startDate, endDate, categoryId, search, reload) {
        mutableStateOf<String?>(null)
    }

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
                    userId,
                    requestedStart.takeUnless { requestedFilter == "all" },
                    requestedEnd.takeUnless { requestedFilter == "all" },
                    selectedCategory,
                    requestedSearch,
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
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("Back", color = Teal, fontSize = 16.sp)
                }
                Text(
                    text = "History",
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                )
            }
        }

        item {
            HistoryCard {
                val label = when (filter) {
                    "week" -> "Current week"
                    "month" -> "Current month"
                    "custom" -> "Custom range"
                    else -> "All dates"
                }
                Text("Active filter: $label", fontSize = 15.sp, color = TextPrimary)
                if (filter != "all") {
                    Text(
                        "$startDate to $endDate (inclusive)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipButton(
                        text = "Current week",
                        selected = filter == "week",
                        onClick = { selectShortcut("week") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChipButton(
                        text = "Current month",
                        selected = filter == "month",
                        onClick = { selectShortcut("month") },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Custom range (inclusive)", fontSize = 12.sp, color = TextPrimary)

                // Keep your date selector logic; wrap visually
                ExpenseDateSelector(
                    value = draftStart,
                    onValueChange = { draftStart = it },
                    label = "Start date (YYYY-MM-DD)",
                    modifier = Modifier.fillMaxWidth(),
                )
                ExpenseDateSelector(
                    value = draftEnd,
                    onValueChange = { draftEnd = it },
                    label = "End date (YYYY-MM-DD)",
                    modifier = Modifier.fillMaxWidth(),
                )
                rangeError?.let {
                    Text(it, fontSize = 12.sp, color = ErrorRed)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            rangeError = historyRangeError(draftStart, draftEnd)
                            if (rangeError == null) {
                                startDate = draftStart.trim()
                                endDate = draftEnd.trim()
                                filter = "custom"
                            }
                        },
                        shape = CardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonTeal,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("Apply") }

                    Button(
                        onClick = {
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
                        },
                        shape = CardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RowBg,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("Reset") }
                }
            }
        }

        item {
            HistoryCard {
                Text("Category", fontSize = 15.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(35.dp)
                            .background(FieldBgSoft, CardShape)
                            .clickable(enabled = categoriesLoaded) { categoryMenuOpen = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (categoryId == null) "All categories"
                            else categoryNames[categoryId]
                                ?: if (error != null) "Category unavailable to load"
                                else "Loading category...",
                            fontSize = 12.sp,
                            color = TextPrimary,
                        )
                        Text(
                            text = "▼",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen && categoriesLoaded,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("All categories") },
                            onClick = {
                                categoryId = null
                                categoryNotice = null
                                categoryMenuOpen = false
                            },
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryNotice = null
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
                categoryNotice?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        item {
            HistoryCard {
                Text("Search descriptions", fontSize = 12.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                PlainField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search descriptions",
                    background = FieldBgSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when {
            loading -> item {
                Text("Loading expenses...", color = TextSecondary)
            }
            error != null -> item {
                HistoryCard {
                    Text(error!!, color = ErrorRed)
                    TextButton(onClick = { reload++ }) {
                        Text("Retry", color = Teal)
                    }
                }
            }
            expenses.isEmpty() -> item {
                HistoryCard {
                    Text(
                        text = if (hasExpenses) "No expenses match these filters."
                        else "No expenses yet. Saved expenses will appear here.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> items(expenses, key = { it.id }) { expense ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RowBg, CardShape)
                        .clickable { onExpenseSelected(expense.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatRand(expense.amount),
                            fontSize = 16.sp,
                            color = TextPrimary,
                        )
                        Text(expense.date, fontSize = 12.sp, color = TextSecondary)
                        Text(expense.description, fontSize = 14.sp, color = TextPrimary)
                        Text(
                            categoryNames[expense.categoryId] ?: "Category unavailable",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                    ExpenseReceipt(expense.receiptImageUri)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
private fun FilterChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = CardShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ButtonTeal else RowBg,
            contentColor = TextPrimary,
        ),
        modifier = modifier.height(36.dp),
    ) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    background: Color = FieldBgSoft,
    height: Dp = 35.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(background, CardShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 12.sp, color = TextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 12.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(Teal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryScreenPreview() {
    MaterialTheme { HistoryScreen(onBack = {}) }
}