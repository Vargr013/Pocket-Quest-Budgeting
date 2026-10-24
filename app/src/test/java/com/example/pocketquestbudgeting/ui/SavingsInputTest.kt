package com.example.pocketquestbudgeting.ui

import org.junit.Assert.*
import org.junit.Test

class SavingsInputTest {
    @Test fun datesConvertStrictlyWithoutUsingTheTargetAsAnEventDate() {
        assertEquals("2028-02-29", savingsTargetDate("29/02/2028"))
        assertEquals("29/02/2028", savingsDateInput("2028-02-29"))
        assertNull(savingsTargetDate(" "))
        for (date in listOf("29/02/2025", "31/04/2026", "1/2/2026", "2026-10-24")) {
            try { savingsTargetDate(date); fail("Invalid date accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun moneyUsesExactWholeCentsAndCheckedRanges() {
        assertEquals(25050L, savingsAmount("250.50"))
        assertEquals(0L, savingsAmount("", allowZero = true))
        assertEquals(9007199254740993L, savingsAmount("90071992547409.93"))
        for (amount in listOf("-1", "0", "1.001", "1e3", "92233720368547758.08")) {
            try { savingsAmount(amount); fail("Invalid amount accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
