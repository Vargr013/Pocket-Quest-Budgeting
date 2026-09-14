package com.example.pocketquestbudgeting.data

data class CategorySpendingTotal(
    val categoryId: Long,
    val categoryName: String,
    val totalCents: Long,
)
