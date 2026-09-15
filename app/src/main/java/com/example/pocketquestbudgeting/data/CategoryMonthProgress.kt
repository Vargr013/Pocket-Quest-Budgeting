package com.example.pocketquestbudgeting.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CategoryMonthProgress(
    val categoryId: Long,
    val name: String,
    val minMonthlyBudget: Long,
    val maxMonthlyBudget: Long,
    val spent: Long,
)

// I used an exclusive end so the last day of the month is included without calculating it.
fun currentMonthBounds(now: Calendar = Calendar.getInstance()): Pair<String, String> {
    val start = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return isoDate(start) to isoDate(end)
}

private fun isoDate(calendar: Calendar): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
