package com.example.pocketquestbudgeting.ui

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun todayExpenseDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

internal data class ExpenseValidation(
    val errors: Map<String, String>,
    val amountCents: Long?,
)

internal fun validateExpense(
    date: String,
    startTime: String,
    endTime: String,
    description: String,
    categoryId: Long?,
    amount: String,
): ExpenseValidation {
    val errors = mutableMapOf<String, String>()
    if (!validExpenseDate(date.trim())) errors["date"] = "Enter a valid date as YYYY-MM-DD."
    val timePattern = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")
    val start = startTime.trim()
    val end = endTime.trim()
    if (!timePattern.matches(start)) errors["startTime"] = "Enter a valid start time as HH:mm (24-hour)."
    if (!timePattern.matches(end)) errors["endTime"] = "Enter a valid end time as HH:mm (24-hour)."
    if (timePattern.matches(start) && timePattern.matches(end) && end < start) {
        errors["endTime"] = "End time cannot be earlier than start time on the same day."
    }
    if (description.isBlank()) errors["description"] = "Enter a description."
    if (categoryId == null) errors["category"] = "Select a category."
    val cents = parseExpenseCents(amount)
    if (cents == null) {
        errors["amount"] = "Enter a positive amount using a dot and up to 2 decimal places (e.g. 12.34). Maximum: 92233720368547758.07."
    }
    return ExpenseValidation(errors, cents)
}

internal fun parseExpenseCents(text: String): Long? {
    val amount = text.trim()
    if (!Regex("[0-9]+(\\.[0-9]{1,2})?").matches(amount)) return null
    return try {
        // I converted directly to cents so money is never rounded through Double.
        BigDecimal(amount).movePointRight(2).longValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun validExpenseDate(date: String): Boolean {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)) return false
    val year = date.substring(0, 4).toInt()
    val month = date.substring(5, 7).toInt()
    val day = date.substring(8, 10).toInt()
    if (year == 0 || month !in 1..12) return false
    // I checked the actual month length so impossible dates are rejected, not adjusted.
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = intArrayOf(31, if (leapYear) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    return day in 1..days[month - 1]
}
