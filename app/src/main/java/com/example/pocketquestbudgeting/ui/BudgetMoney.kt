package com.example.pocketquestbudgeting.ui

import java.math.BigDecimal
import kotlin.math.abs

internal fun parseBudgetCents(text: String): Long? {
    val amount = text.trim()
    if (!Regex("[0-9]+(\\.[0-9]{1,2})?").matches(amount)) return null
    return try {
        // I converted directly to cents so money is never rounded through Double (Oracle, n.d.-a).
        BigDecimal(amount).movePointRight(2).longValueExact().takeIf { it >= 0 }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun centsToBudgetInput(cents: Long): String {
    val value = abs(cents)
    return "${value / 100}.${(value % 100).toString().padStart(2, '0')}"
}

internal fun formatBudgetAmount(cents: Long): String {
    val negative = cents < 0
    val value = abs(cents)
    val grouped = (value / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val fraction = (value % 100).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}R$grouped,$fraction"
}
