package com.example.pocketquestbudgeting.data

object MonthlyGoalChecks {
    fun validMonth(month: String): Boolean = Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(month) && month.take(4) != "0000"

    fun monthKey(start: String, end: String): String {
        val month = start.take(7)
        require(validMonth(month) && start == "$month-01" && ExpenseChecks.validDate(end)) { "Use a complete calendar month." }
        val year = month.take(4).toInt()
        val number = month.takeLast(2).toInt()
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val last = if (number == 2) { if (leap) 29 else 28 } else if (number in listOf(4, 6, 9, 11)) 30 else 31
        require(end == "$month-$last") { "Use that month's final calendar date." }
        return month
    }

    fun requireValues(minimum: Long, maximum: Long, start: String, end: String): String {
        require(minimum >= 0 && maximum >= minimum) { "Use nonnegative whole-cent limits with minimum no greater than maximum." }
        return monthKey(start, end)
    }
}
