package com.example.pocketquestbudgeting.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.pocketquestbudgeting.data.AccountSession
import com.example.pocketquestbudgeting.data.AccountSessions
import com.example.pocketquestbudgeting.data.ExpenseChecks
import com.example.pocketquestbudgeting.data.SavingsGoalEntity
import com.example.pocketquestbudgeting.data.SyncAccount
import com.example.pocketquestbudgeting.data.SavingsMapping
import com.example.pocketquestbudgeting.data.SyncMapping
import org.json.JSONObject

internal fun savingGoalSnapshot(goal: SavingsGoalEntity): String = JSONObject()
    .put("id", goal.id).put("cloudId", goal.cloudId).put("isDeleted", goal.isDeleted)
    .put("fields", SyncMapping.encode(SavingsMapping.GOALS, SavingsMapping.fields(goal))).toString()

internal fun restoreSavingGoal(userId: Long, snapshot: String): SavingsGoalEntity {
    val json = JSONObject(snapshot)
    return SavingsMapping.goal(userId, json.getString("cloudId"), SyncMapping.decode(SavingsMapping.GOALS, json.getString("fields")),
        json.getBoolean("isDeleted")).copy(id = json.getLong("id"))
}

internal fun savingsTargetDate(text: String): String? {
    if (text.isBlank()) return null
    require(Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}").matches(text.trim())) { "Enter the target date as dd/MM/yyyy." }
    val parts = text.trim().split('/')
    val date = "${parts[2]}-${parts[1]}-${parts[0]}"
    require(ExpenseChecks.validDate(date)) { "Enter a valid target date." }
    return date
}

internal fun savingsDateInput(date: String?) = date?.split('-')?.let { "${it[2]}/${it[1]}/${it[0]}" } ?: ""

internal fun savingsAmount(text: String, allowZero: Boolean = false): Long {
    val cents = if (allowZero && text.isBlank()) 0L else parseBudgetCents(text)
    require(cents != null && (cents > 0 || (allowZero && cents == 0L))) { "Enter ${if (allowZero) "a nonnegative" else "a positive"} amount, like 250 or 250.50." }
    return cents
}

internal fun screenAccount(context: Context, userId: Long, session: AccountSession? = null): SyncAccount {
    val current = session ?: AccountSessions.get(context)
    return requireNotNull(current.state.value.account?.takeIf { it.localUserId == userId && current.isCurrent(it) }) {
        "Please log in again before saving."
    }
}

@Composable
internal fun SavingGoalSelector(goals: List<SavingsGoalEntity>, selected: SavingsGoalEntity?, loading: Boolean = false, onSelect: (SavingsGoalEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, enabled = goals.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { "${it.name} — ${formatBudgetAmount(it.targetCents)} target" } ?: if (loading) "Loading saving goals..." else "Select a saving goal")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            goals.forEach { goal ->
                DropdownMenuItem(text = { Text("${goal.name} — ${formatBudgetAmount(goal.targetCents)} target") },
                    onClick = { expanded = false; onSelect(goal) })
            }
        }
        if (!loading && goals.isEmpty()) Text("No active saving goals yet.")
    }
}
