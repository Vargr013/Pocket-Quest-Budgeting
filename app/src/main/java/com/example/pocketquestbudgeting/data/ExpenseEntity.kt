package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expenses", indices = [Index("userId"), Index("categoryId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val categoryId: Long,
    // I stored cents as whole numbers to avoid rounding errors.
    val amount: Long,
    // I used YYYY-MM-DD for dates and HH:mm for 24-hour times.
    val date: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val receiptImageUri: String? = null,
)
