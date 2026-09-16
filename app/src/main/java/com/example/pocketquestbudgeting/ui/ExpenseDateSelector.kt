package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseDateSelector(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Text(label)
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { "Select date" })
        }
    }
    // I used a DatePickerDialog and remembered picker state for date selection (Google, 2026c).
    if (open && enabled) {
        // I kept the date formatter in UTC so the selected day cannot shift with the device timezone (Oracle, n.d.-c).
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val initialDate = value.takeIf { validExpenseDate(it) } ?: todayExpenseDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = format.parse(initialDate)?.time,
            yearRange = 1..9999,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(format.format(it)) }
                        open = false
                    },
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}
