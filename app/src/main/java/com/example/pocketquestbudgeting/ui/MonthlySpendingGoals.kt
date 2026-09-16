package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.room.withTransaction
import com.example.pocketquestbudgeting.data.BudgetGoalEntity
import com.example.pocketquestbudgeting.data.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val RowBg = Color(0xFFE8EEEC)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
internal fun MonthlySpendingGoals(userId: Long) {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(historyShortcutRange("month")) }
    var reload by remember { mutableStateOf(0) }
    var goal by remember(month, reload) { mutableStateOf<BudgetGoalEntity?>(null) }
    var loading by remember(month, reload) { mutableStateOf(!inspecting) }
    var loadError by remember(month, reload) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftStart by rememberSaveable { mutableStateOf("") }
    var draftEnd by rememberSaveable { mutableStateOf("") }
    var minimum by rememberSaveable { mutableStateOf("") }
    var maximum by rememberSaveable { mutableStateOf("") }
    var expired by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun refreshMonth() {
        val current = historyShortcutRange("month")
        if (editing && draftStart != current.start) expired = true
        month = current
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshMonth()
        reload++
    }
    LaunchedEffect(userId, month, reload) {
        if (inspecting) return@LaunchedEffect
        val requestedMonth = month
        val requestedReload = reload
        try {
            val db = DatabaseProvider.get(context)
            val saved = db.budgetGoalDao().getForMonth(userId, requestedMonth.start, requestedMonth.end)
            coroutineContext.ensureActive()
            if (month != requestedMonth || reload != requestedReload) return@LaunchedEffect
            goal = saved
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            if (month != requestedMonth || reload != requestedReload) return@LaunchedEffect
            loadError = "Could not load monthly spending goals."
            loading = false
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Budgets", fontSize = 20.sp, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().background(RowBg, CardShape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Monthly spending goals", fontSize = 20.sp, color = TextPrimary)
            Text("Month: ${month.start.take(7)}", fontSize = 15.sp, color = TextSecondary)
            when {
                loading -> Text("Loading goals...", color = TextSecondary)
                loadError != null -> {
                    Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refreshMonth(); reload++ }) { Text("Retry") }
                }
                else -> {
                    val saved = goal
                    if (saved == null) {
                        Text("No monthly spending goals set", color = TextSecondary)
                    } else {
                        Text("Minimum: ${formatBudgetAmount(saved.minimumAmount)}", fontSize = 20.sp, color = TextPrimary)
                        Text("Maximum: ${formatBudgetAmount(saved.maximumAmount)}", fontSize = 20.sp, color = TextPrimary)
                    }
                    TextButton(onClick = {
                        val current = historyShortcutRange("month")
                        if (current != month) {
                            month = current
                            reload++
                        } else {
                            draftStart = month.start
                            draftEnd = month.end
                            minimum = saved?.let { centsToBudgetInput(it.minimumAmount) }.orEmpty()
                            maximum = saved?.let { centsToBudgetInput(it.maximumAmount) }.orEmpty()
                            saveError = null
                            expired = false
                            editing = true
                        }
                    }) { Text(if (saved == null) "Set goals" else "Edit goals") }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!saving) editing = false },
            title = { Text("Monthly spending goals") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Month: ${draftStart.take(7)}")
                    OutlinedTextField(
                        value = minimum,
                        onValueChange = { minimum = it; saveError = null },
                        label = { Text("Minimum monthly spending") },
                        singleLine = true,
                        enabled = !saving && !expired,
                        isError = saveError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = maximum,
                        onValueChange = { maximum = it; saveError = null },
                        label = { Text("Maximum monthly spending") },
                        singleLine = true,
                        enabled = !saving && !expired,
                        isError = saveError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Text("Use a dot for decimals, e.g. 12.34. Zero is allowed.")
                    if (expired) {
                        Text("The month changed. Cancel and reopen the goals for the current month.", color = MaterialTheme.colorScheme.error)
                    }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving && !expired, onClick = {
                    if (saving) return@TextButton
                    refreshMonth()
                    if (expired) return@TextButton
                    val minCents = parseBudgetCents(minimum)
                    val maxCents = parseBudgetCents(maximum)
                    saveError = when {
                        minimum.isBlank() || maximum.isBlank() -> "Enter both minimum and maximum amounts."
                        minCents == null || maxCents == null -> "Enter amounts that are zero or more, using a dot and up to 2 decimal places."
                        minCents > maxCents -> "Minimum cannot be greater than the maximum."
                        else -> null
                    }
                    if (saveError != null || minCents == null || maxCents == null) return@TextButton
                    saving = true
                    val start = draftStart
                    val end = draftEnd
                    scope.launch {
                        try {
                            val db = DatabaseProvider.get(context)

                            // I kept the month check and goal save in one transaction (Google, 2026j).
                            val saved = db.withTransaction {
                                // I checked again after waiting for the database so an old draft stays in its month.
                                if (historyShortcutRange("month").start != start) return@withTransaction false
                                db.budgetGoalDao().saveForMonth(userId, start, end, minCents, maxCents)
                                true
                            }
                            if (saved) {
                                editing = false
                                refreshMonth()
                                reload++
                            } else {
                                expired = true
                                refreshMonth()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            saveError = "Could not save the goals. Your amounts are kept. Please try again."
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "Saving..." else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }, enabled = !saving) { Text("Cancel") }
            },
        )
    }
}
