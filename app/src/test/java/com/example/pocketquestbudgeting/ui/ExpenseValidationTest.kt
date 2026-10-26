package com.example.pocketquestbudgeting.ui

import org.junit.Assert.*
import org.junit.Test

class ExpenseValidationTest {
    @Test fun paymentTimeIsRequiredAndUsesTwentyFourHourTime() {
        for (time in listOf("00:00", "23:59", " 09:15 ")) {
            assertTrue(validateExpense("2026-10-26", time, "Payment", 1, "12.34").errors.isEmpty())
        }
        for (time in listOf("", "24:00", "09:60", "9:15", "09:15:00")) {
            val result = validateExpense("2026-10-26", time, "Payment", 1, "12.34")
            assertEquals(setOf("paymentTime"), result.errors.keys)
        }
    }
}
