package com.example.pocketquestbudgeting.data

import org.junit.Assert.*
import org.junit.Test

class SavingsChecksTest {
    private fun row(cents: Long, kind: String = SavingsChecks.ADDITION) = SavingsContributionEntity(
        userId = 1, goalId = 1, businessId = "synthetic", kind = kind, amountCents = cents,
        effectiveDate = "2024-02-29", reversesBusinessId = if (kind == SavingsChecks.REVERSAL) "original" else null,
    )

    @Test fun exactLongBalanceExceedsFloatingPointPrecisionWithoutRounding() {
        assertEquals(9007199254740994L, SavingsChecks.balance(listOf(row(9007199254740993L), row(1))))
        assertEquals(0L, SavingsChecks.balance(emptyList()))
        assertEquals(Long.MAX_VALUE - 1, SavingsChecks.balance(listOf(row(Long.MAX_VALUE), row(1, SavingsChecks.REVERSAL))))
    }

    @Test(expected = ArithmeticException::class) fun overflowingBalanceIsRejected() {
        SavingsChecks.balance(listOf(row(Long.MAX_VALUE), row(1)))
    }

    @Test fun completeHistoryDoesNotDependOnFirestoreDocumentOrder() {
        val additions = listOf(row(Long.MAX_VALUE), row(Long.MAX_VALUE))
        val reversal = row(Long.MAX_VALUE, SavingsChecks.REVERSAL)
        assertEquals(Long.MAX_VALUE, SavingsChecks.balance(additions + reversal))
        assertEquals(Long.MAX_VALUE, SavingsChecks.balance(listOf(reversal) + additions))
    }

    @Test(expected = IllegalArgumentException::class) fun negativeBalanceIsRejected() {
        SavingsChecks.balance(listOf(row(1, SavingsChecks.REVERSAL)))
    }

    @Test fun invalidKindsAmountsDatesAndReservedKeysAreRejected() {
        listOf(row(0), row(-1), row(1, "UNKNOWN"), row(1).copy(effectiveDate = "2025-02-29"),
            row(1).copy(businessId = "opening:reserved"), row(1).copy(reversesBusinessId = "unexpected"),
            row(1).copy(note = " "), row(1).copy(businessId = "unsafe/path")).forEach { invalid ->
            try { SavingsChecks.requireContribution(invalid); fail("Expected rejection: $invalid") } catch (_: IllegalArgumentException) { }
        }
    }
}
