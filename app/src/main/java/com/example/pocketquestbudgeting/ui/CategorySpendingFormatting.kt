package com.example.pocketquestbudgeting.ui

import com.example.pocketquestbudgeting.data.CategorySpendingTotal
import java.math.BigDecimal
import java.math.RoundingMode

internal fun categorySpendingTotal(categories: List<CategorySpendingTotal>): Long =
    // I checked each addition so an oversized total fails instead of wrapping.
    categories.fold(0L) { total, category -> Math.addExact(total, category.totalCents) }

internal fun categorySpendingPercentage(cents: Long, totalCents: Long): String {
    if (totalCents == 0L || cents == 0L) return "0%"
    // I used BigDecimal for the percentage to avoid multiplying a Long by 100 (Oracle, n.d.-a).
    return BigDecimal.valueOf(cents).movePointRight(2)
        .divide(BigDecimal.valueOf(totalCents), 1, RoundingMode.HALF_UP)
        .toPlainString() + "%"
}

internal fun categorySpendingFraction(cents: Long, totalCents: Long): Float {
    if (totalCents <= 0L) return 0f
    // I only used floating point for the bar, keeping the displayed money exact.
    return (cents.toDouble() / totalCents.toDouble()).toFloat().coerceIn(0f, 1f)
}
