package com.example.pocketquestbudgeting.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

internal data class HistoryDateRange(val start: String, val end: String)

internal fun historyRangeError(start: String, end: String): String? = when {
    start.isBlank() || end.isBlank() -> "Enter both start and end dates."
    !validExpenseDate(start.trim()) -> "Enter a valid start date as YYYY-MM-DD."
    !validExpenseDate(end.trim()) -> "Enter a valid end date as YYYY-MM-DD."
    end.trim() < start.trim() -> "End date cannot be earlier than start date."
    else -> null
}

internal fun historyShortcutRange(
    shortcut: String,
    today: Calendar = GregorianCalendar(),
): HistoryDateRange {
    // I used Calendar arithmetic for the week and month boundaries (Oracle, n.d.-b).
    val start = today.clone() as Calendar
    if (shortcut == "week") {
        // I counted back to Monday without using the locale's first weekday.
        val daysSinceMonday = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7
        start.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    } else {
        require(shortcut == "month")
        start.set(Calendar.DAY_OF_MONTH, 1)
    }
    val end = start.clone() as Calendar
    if (shortcut == "week") end.add(Calendar.DAY_OF_MONTH, 6)
    else end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = today.timeZone }
    return HistoryDateRange(format.format(start.time), format.format(end.time))
}
// I moved to the first day of last month so September stays September at month-end.
internal fun previousMonthRange(
    today: Calendar = GregorianCalendar(),
): HistoryDateRange {
    val start = today.clone() as Calendar
    start.set(Calendar.DAY_OF_MONTH, 1)
    start.add(Calendar.MONTH, -1)
    val end = start.clone() as Calendar
    end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = today.timeZone }
    return HistoryDateRange(format.format(start.time), format.format(end.time))
}
// I walked one calendar day at a time so empty days stay on the graph.
internal fun datesInRange(
    start: String,
    end: String,
    today: Calendar = GregorianCalendar(),
): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = today.timeZone }
    val cursor = today.clone() as Calendar
    cursor.time = format.parse(start) ?: return emptyList()
    val dates = mutableListOf<String>()
    while (true) {
        val stamp = format.format(cursor.time)
        if (stamp > end) break
        dates.add(stamp)
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    return dates
}