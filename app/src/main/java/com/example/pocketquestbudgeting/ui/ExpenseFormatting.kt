package com.example.pocketquestbudgeting.ui

import java.math.BigDecimal

fun formatRand(cents: Long): String {
    // I kept the cents exact, including amounts too large for Double.
    return "R${BigDecimal.valueOf(cents, 2).toPlainString()}"
}
