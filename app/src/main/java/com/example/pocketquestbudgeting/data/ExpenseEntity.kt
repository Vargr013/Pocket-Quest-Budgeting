package com.example.pocketquestbudgeting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expenses", indices = [Index("userId"), Index("categoryId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val categoryId: Long,
    // Store whole cents so money does not lose precision through floating-point rounding.
    val amount: Long,
    // Use YYYY-MM-DD for dates and 24-hour HH:mm for times.
    val date: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val receiptImageUri: String? = null,
)
