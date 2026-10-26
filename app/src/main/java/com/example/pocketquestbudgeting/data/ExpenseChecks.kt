package com.example.pocketquestbudgeting.data

object ExpenseChecks {
    fun validDate(date: String): Boolean {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)) return false
        val year = date.substring(0, 4).toInt()
        val month = date.substring(5, 7).toInt()
        val day = date.substring(8, 10).toInt()
        if (year == 0 || month !in 1..12) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return day in 1..days[month - 1]
    }

    fun requireValues(amount: Long, date: String, paymentTime: String, description: String) {
        require(amount > 0) { "Enter a positive whole-cent amount." }
        require(validDate(date)) { "Enter a valid date as YYYY-MM-DD." }
        val time = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")
        require(time.matches(paymentTime)) { "Enter time of payment as HH:mm (24-hour)." }
        require(description.isNotBlank() && description == description.trim()) { "Enter a description." }
    }
}
