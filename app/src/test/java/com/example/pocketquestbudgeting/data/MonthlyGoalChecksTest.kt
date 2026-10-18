package com.example.pocketquestbudgeting.data

import com.example.pocketquestbudgeting.ui.parseBudgetCents
import org.junit.Assert.*
import org.junit.Test

class MonthlyGoalChecksTest {
    @Test fun calendarMonthsIncludedLeapCenturiesAndYearBoundaries() {
        for ((month, last) in listOf("0001-01" to "31", "1900-02" to "28", "2000-02" to "29",
            "2024-02" to "29", "2026-04" to "30", "2026-12" to "31", "2027-01" to "31", "9999-12" to "31")) {
            assertEquals(month, MonthlyGoalChecks.monthKey("$month-01", "$month-$last"))
        }
    }
    @Test fun malformedMonthKeysWereRejected() {
        for (month in listOf("", "0000-01", "2026-00", "2026-13", "2026-1", "2026-10Z", "20260-01", " 2026-10"))
            assertFalse(month, MonthlyGoalChecks.validMonth(month))
    }
    @Test fun partialAndMismatchedRangesWereRejected() {
        for ((start, end) in listOf("2024-02-01" to "2024-02-28", "2023-02-01" to "2023-02-29",
            "1900-02-01" to "1900-02-29", "2026-04-01" to "2026-04-31", "2026-10-02" to "2026-10-31",
            "2026-12-01" to "2027-01-31", "2026-1-01" to "2026-01-31")) {
            try { MonthlyGoalChecks.monthKey(start, end); fail("Invalid range accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun bothLimitsRetainedLongPrecisionAndExplicitZero() {
        for ((minimum, maximum) in listOf(0L to 0L, 9007199254740993L to Long.MAX_VALUE))
            assertEquals("2026-10", MonthlyGoalChecks.requireValues(minimum, maximum, "2026-10-01", "2026-10-31"))
        for ((minimum, maximum) in listOf(-1L to 0L, 0L to -1L, 2L to 1L)) {
            try { MonthlyGoalChecks.requireValues(minimum, maximum, "2026-10-01", "2026-10-31"); fail("Invalid limits") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun budgetInputKeptWholeCentsAndRejectedBlankFractionalCentsAndOverflow() {
        assertEquals(9007199254740993L, parseBudgetCents("90071992547409.93"))
        assertEquals(Long.MAX_VALUE, parseBudgetCents("92233720368547758.07"))
        assertEquals(0L, parseBudgetCents("0.00"))
        for (text in listOf("", " ", "-1", "1.001", "1,25", "NaN", "92233720368547758.08", "1e10")) assertNull(text, parseBudgetCents(text))
    }
}
