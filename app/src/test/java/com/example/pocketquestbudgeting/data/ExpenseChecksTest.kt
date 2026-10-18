package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class ExpenseChecksTest {
    @Test fun actualCalendarDatesWereChecked() {
        for (date in listOf("2000-02-29", "2024-02-29", "0001-01-01", "9999-12-31")) assertTrue(date, ExpenseChecks.validDate(date))
        for (date in listOf("1900-02-29", "2023-02-29", "2024-04-31", "0000-01-01", "2024-13-01", "2024-1-01")) assertFalse(date, ExpenseChecks.validDate(date))
    }
    @Test fun positiveLongBoundaryAndEqualTimesWereAccepted() {
        ExpenseChecks.requireValues(Long.MAX_VALUE, "2026-10-18", "00:00", "00:00", "Synthetic")
        ExpenseChecks.requireValues(9007199254740993L, "2024-02-29", "00:00", "23:59", "Café / synthetic")
    }
    @Test fun invalidAmountsAndTimesWereRejected() {
        for ((amount, start, end) in listOf(Triple(0L,"09:00","10:00"), Triple(-1L,"09:00","10:00"),
            Triple(1L,"24:00","24:00"), Triple(1L,"09:00","08:59"), Triple(1L,"9:00","10:00"))) {
            try { ExpenseChecks.requireValues(amount,"2026-10-18",start,end,"Synthetic"); fail("Invalid expense accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun BlankAndUntrimmedDescriptionsWereRejected() {
        for (description in listOf("", "   ", " untrimmed")) {
            try { ExpenseChecks.requireValues(1,"2026-10-18","09:00","10:00",description); fail("Invalid description accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
